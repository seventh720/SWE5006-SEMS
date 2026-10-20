import http from 'k6/http';
import { check, sleep } from 'k6';
import { SharedArray } from 'k6/data';
import { Counter, Rate } from 'k6/metrics';
import execution from 'k6/execution';

// One legitimate application-issued JWT per synthetic user, prepared at low rate.
const accounts = new SharedArray('accounts', () => JSON.parse(open(__ENV.K6_CREDENTIALS)).accounts);
const events = new SharedArray('events', () => JSON.parse(open(__ENV.K6_CREDENTIALS)).events);
const config = new SharedArray('config', () => {
  const data = JSON.parse(open(__ENV.K6_CREDENTIALS));
  return [{ base: data.base, marker: data.marker }];
});
const fixture = config[0];
const vus = Number(__ENV.CAPACITY_VUS || 50);
const hold = __ENV.CAPACITY_HOLD || '30s';
const mode = __ENV.CAPACITY_MODE || 'read';
const latencyAbort = __ENV.CAPACITY_LATENCY_ABORT !== 'false';
const runId = __ENV.CAPACITY_RUN_ID ? `-${__ENV.CAPACITY_RUN_ID}` : '';
const failures = new Rate('unexpected_failure');
const successfulBookings = new Counter('bookings_created');
const soldOut = new Counter('sold_out');
if (!['read', 'mixed', 'contention'].includes(mode) || vus > accounts.length || vus < 1) {
  throw new Error('Invalid mode/VUs or insufficient distinct accounts');
}
export const options = {
  scenarios: mode === 'contention' ? {
    load: { executor: 'per-vu-iterations', vus, iterations: 1, maxDuration: '60s', gracefulStop: '10s' },
  } : {
    load: { executor: 'ramping-vus', startVUs: 0,
      stages: [{ target: vus, duration: '30s' }, { target: vus, duration: hold }], gracefulRampDown: '10s', gracefulStop: '10s' },
  },
  thresholds: {
    unexpected_failure: [{ threshold: 'rate<0.01', abortOnFail: true, delayAbortEval: '15s' }],
    http_req_duration: [{ threshold: 'p(95)<3000', abortOnFail: latencyAbort, delayAbortEval: '15s' }],
    checks: ['rate==1'],
    ...(mode === 'contention' ? {} : {
      'http_req_duration{phase:hold}': ['p(95)<3000'],
      'unexpected_failure{phase:hold}': ['rate<0.01'],
      ...Object.fromEntries(['events', 'event-detail', 'me', 'orders', ...(mode === 'mixed' ? ['create-booking', 'tickets', 'cancel-booking'] : [])]
        .map(name => [`http_req_duration{name:${name},phase:hold}`, ['p(95)<3000']])),
    }),
  },
  systemTags: ['status', 'method', 'name', 'scenario', 'expected_response'],
  discardResponseBodies: false,
};

function request(method, path, name, account, data, expected = [200], extra = {}) {
  const phase = mode === 'contention' || Date.now() - execution.scenario.startTime >= 30000 ? 'hold' : 'ramp';
  const response = http.request(method, fixture.base + path, data == null ? null : JSON.stringify(data), {
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${account.token}`, ...extra },
    tags: { name, phase, mode, target_vus: String(vus) }, timeout: '8s',
    responseCallback: http.expectedStatuses(...expected),
  });
  const ok = expected.includes(response.status);
  check(response, { 'expected status': () => ok });
  return { response, ok, phase };
}

function body(result, predicate) {
  let value = null;
  let ok = false;
  try { value = result.response.json(); ok = result.ok && predicate(value); } catch (_) { /* counted below */ }
  check(result.response, { 'expected response content': () => ok });
  failures.add(!ok, { phase: result.phase, mode, target_vus: String(vus) });
  return ok ? value : null;
}

export default function () {
  const account = accounts[__VU - 1];
  const event = events[mode === 'contention' ? 10 : (__VU - 1) % 10];
  if (mode !== 'contention') {
    body(request('GET', '/api/v1/events?page=0&size=10', 'events', account), x => Array.isArray(x.items));
    body(request('GET', '/api/v1/events/' + event.id, 'event-detail', account), x => x.id === event.id);
    body(request('GET', '/api/v1/auth/me', 'me', account), x => x.id === account.id);
    body(request('GET', '/api/v1/bookings?page=0&size=10', 'orders', account), x => Array.isArray(x.items));
  }
  // Mixed mode: one booking/cancel cycle every fifth iteration, max 60 per VU.
  // Keep write metrics separate so a browsing majority cannot hide write failures.
  if (mode === 'contention' || (mode === 'mixed' && __ITER % 5 === 0 && __ITER < 300)) {
    const booking = request('POST', '/api/v1/bookings', 'create-booking', account,
      { eventId: event.id, ticketTypeId: event.ticketTypeId, quantity: 1 },
      mode === 'contention' ? [201, 409] : [201],
      { 'Idempotency-Key': `${fixture.marker}${runId}-${mode}-${vus}-${__VU}-${__ITER}` });
    if (booking.response.status === 409 && mode === 'contention') {
      const rejected = body(booking, x => typeof x.detail === 'string' && /not enough|insufficient|sold out/i.test(x.detail));
      if (rejected) soldOut.add(1);
    } else {
      const order = body(booking, x => typeof x.id === 'string' && x.status === 'CONFIRMED' && x.quantity === 1);
      if (order) {
        successfulBookings.add(1);
        if (mode === 'mixed') {
          body(request('POST', `/api/v1/bookings/${order.id}/tickets`, 'tickets', account, {}),
            x => Array.isArray(x) && x.length === 1 && x[0].status === 'VALID');
          body(request('POST', `/api/v1/bookings/${order.id}/cancel`, 'cancel-booking', account, {}), x => x.status === 'CANCELLED');
        }
      }
    }
  }
  if (mode !== 'contention') sleep(1);
}
