import http from 'k6/http';
import { check, sleep } from 'k6';

// Provision a dedicated attendee; never put a bearer token in the command line or Git.
const credentials = JSON.parse(open(__ENV.K6_CREDENTIALS));
export const options = {
  vus: Number(__ENV.BASELINE_VUS || 1),
  duration: __ENV.BASELINE_DURATION || '20s',
  thresholds: {
    http_req_failed: [{ threshold: 'rate<0.01', abortOnFail: true, delayAbortEval: '10s' }],
    http_req_duration: [{ threshold: 'p(95)<3000', abortOnFail: true, delayAbortEval: '10s' }],
    checks: ['rate==1'],
    'http_req_duration{name:events}': ['p(95)<3000'],
    'http_req_duration{name:me}': ['p(95)<3000'],
    'http_req_duration{name:orders}': ['p(95)<3000'],
  },
};

export default function () {
  for (const [name, path] of [
    ['events', '/api/v1/events?page=0&size=10'],
    ['me', '/api/v1/auth/me'],
    ['orders', '/api/v1/bookings?page=0&size=10'],
  ]) {
    const response = http.get(credentials.base + path, {
      headers: name === 'events' ? {} : { Authorization: `Bearer ${credentials.token}` },
      tags: { name }, timeout: '10s',
    });
    check(response, {
      'HTTP 200': r => r.status === 200,
      'expected JSON shape': r => r.status === 200 && (name === 'me'
        ? r.json('id') === credentials.attendeeId : Array.isArray(r.json('items'))),
    });
  }
  sleep(1);
}
