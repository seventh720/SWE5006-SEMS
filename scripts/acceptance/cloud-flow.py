#!/usr/bin/env python3
"""Explicit synthetic-account cloud acceptance; private state must stay outside Git."""
import argparse
from datetime import datetime, timedelta, timezone
import json
from pathlib import Path
import secrets
import urllib.error
import urllib.request
import uuid

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('phase', choices=['seed', 'prepare', 'verify', 'demote', 'confirm-disabled'])
parser.add_argument('--state', type=Path, required=True)
parser.add_argument('--report', type=Path, required=True)
parser.add_argument('--base', default='https://swe5006.seventh720.com')
parser.add_argument('--already-checked-in', action='store_true')
args = parser.parse_args()
state = json.loads(args.state.read_text()) if args.state.exists() else {'base': args.base, 'accounts': {}}
report = json.loads(args.report.read_text()) if args.report.exists() else {'startedAt': datetime.now(timezone.utc).isoformat(), 'steps': []}


def save():
    for path, data in [(args.state, state), (args.report, report)]:
        path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
        path.write_text(json.dumps(data, indent=2) + '\n')
        path.chmod(0o600)


def call(method, path, data=None, who=None, expected=200, headers=None):
    request_headers = {'Content-Type': 'application/json'}
    if who:
        request_headers['Authorization'] = 'Bearer ' + state['accounts'][who]['token']
    request_headers.update(headers or {})
    request = urllib.request.Request(state['base'] + path, method=method, headers=request_headers,
                                     data=None if data is None else json.dumps(data).encode())
    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            status, raw = response.status, response.read()
    except urllib.error.HTTPError as error:
        status, raw = error.code, error.read()
    accepted = [expected] if isinstance(expected, int) else expected
    assert status in accepted, f'{method} {path}: expected {accepted}, got {status}'
    report['steps'].append({'phase': args.phase, 'method': method, 'path': path, 'actor': who or 'anonymous', 'status': status})
    save()
    return None if not raw else json.loads(raw)


def login(who):
    account = state['accounts'][who]
    result = call('POST', '/api/v1/auth/login', {'email': account['email'], 'password': account['password']})
    assert result['expiresIn'] == 3600
    account.update(token=result['accessToken'], id=result['user']['id'])
    save()


def note(name):
    report.setdefault('assertions', []).append(name)
    save()
    print('PASS', name)


def event_path():
    return '/api/v1/organizer/events/' + state['event']['id']


if args.phase == 'seed':
    assert not state['accounts'], 'Use new state/report paths for a new run'
    state['marker'] = 'accept' + datetime.now(timezone.utc).strftime('%Y%m%d%H%M%S')
    for role in ('admin', 'organizer', 'attendee', 'staff'):
        username = state['marker'] + '_' + role
        account = {'username': username, 'email': username + '@example.test', 'password': secrets.token_hex(8)}
        user = call('POST', '/api/v1/auth/register', account, expected=201)
        account['id'] = user['id']
        state['accounts'][role] = account
        login(role)
    note('Four synthetic accounts registered; one-hour JWT issued')
    print('Synthetic administrator setup ID:', state['accounts']['admin']['id'])

elif args.phase == 'prepare':
    # One temporary ADMIN grant is provisioned by the host operator to this new account.
    login('admin')
    for who, role in [('organizer', 'ORGANIZER'), ('staff', 'STAFF')]:
        call('PUT', '/api/v1/admin/users/' + state['accounts'][who]['id'] + '/roles',
             {'roles': ['ATTENDEE', role]}, 'admin')
        login(who)
    note('Role assignment through application API; fresh logins')
    start = datetime.now(timezone.utc) + timedelta(minutes=12)
    event = {'title': 'Acceptance ' + state['marker'], 'description': 'Synthetic cloud acceptance. Will be cancelled after verification.',
             'location': 'Acceptance test location', 'startsAt': start.isoformat(),
             'endsAt': (start + timedelta(hours=1)).isoformat(), 'capacity': 10,
             'illustration': 'TECH', 'bookingRequirements': {}}
    state['event'] = call('POST', '/api/v1/organizer/events', event, 'organizer', 201)
    state['ticketType'] = call('POST', event_path() + '/ticket-types/default-free', {}, 'organizer')
    state['event'] = call('GET', event_path(), who='organizer')
    state['event'] = call('POST', event_path() + '/publish', {'version': state['event']['version']}, 'organizer')
    eid = state['event']['id']
    call('PUT', f'/api/v1/staff/events/{eid}/assignments/' + state['accounts']['staff']['id'], {}, 'organizer', 204)
    booking = call('POST', '/api/v1/bookings', {'eventId': eid, 'ticketTypeId': state['ticketType']['id'], 'quantity': 2},
                   'attendee', 201, {'Idempotency-Key': str(uuid.uuid4())})
    state['bookingId'] = booking['id']
    state['tickets'] = call('POST', '/api/v1/bookings/' + booking['id'] + '/tickets', {}, 'attendee')
    assert len(state['tickets']) == 2 and len({t['id'] for t in state['tickets']}) == 2
    save()
    note('Owned event, free quota, publication, staff assignment, 2-ticket booking and issuance')

elif args.phase == 'verify':
    eid, booking = state['event']['id'], state['bookingId']
    code = {'code': 'sems:ticket:' + state['tickets'][0]['id']}
    verification = call('POST', f'/api/v1/staff/events/{eid}/verify', code, 'staff')
    if args.already_checked_in:
        assert verification['status'] == 'CHECKED_IN'
        note('Browser check-in persisted on the server')
    else:
        assert verification['status'] == 'VALID'
        call('POST', f'/api/v1/staff/events/{eid}/check-ins', code, 'staff')
    call('POST', f'/api/v1/staff/events/{eid}/check-ins', code, 'staff', 409)
    call('POST', '/api/v1/bookings/' + booking + '/cancel', {}, 'attendee', 409)
    call('POST', '/api/v1/bookings/' + booking + '/tickets', {}, 'admin', 404)
    audit = call('GET', f'/api/v1/staff/events/{eid}/check-ins', who='staff')
    assert len(audit) == 1 and audit[0]['operatorId'] == state['accounts']['staff']['id']
    stats = call('GET', event_path() + '/statistics', who='organizer')
    assert stats['validReservedTickets'] == 2 and stats['checkedInTickets'] == 1
    note('Duplicate and checked-in cancellation denied; ownership and one audit record verified')
    state['event'] = call('GET', event_path(), who='organizer')
    edit = {k: state['event'].get(k) for k in ['title', 'description', 'location', 'startsAt', 'endsAt', 'capacity', 'version', 'illustration', 'bookingRequirements', 'registrationClosesAt', 'registrationOpensAt']}
    edit['location'] = 'Acceptance updated location'
    state['event'] = call('PUT', event_path(), edit, 'organizer')
    notices = call('GET', '/api/v1/notifications?size=50', who='staff')['items']
    assert any(n['type'] == 'EVENT_UPDATED' and n['eventId'] == eid for n in notices)
    sid = state['accounts']['staff']['id']
    call('DELETE', f'/api/v1/staff/events/{eid}/assignments/{sid}', who='organizer', expected=204)
    call('POST', f'/api/v1/staff/events/{eid}/verify', code, 'staff', 403)
    call('PUT', f'/api/v1/staff/events/{eid}/assignments/{sid}', {}, 'organizer', 204)
    note('Event update notification and assignment revocation enforced')
    call('PUT', '/api/v1/admin/users/' + sid + '/roles', {'roles': ['ATTENDEE']}, 'admin')
    call('GET', '/api/v1/staff/events', who='staff', expected=403)
    notices = call('GET', '/api/v1/notifications?size=50', who='staff')['items']
    assert any(n['type'] == 'ROLES_CHANGED' for n in notices)
    call('PUT', '/api/v1/admin/users/' + sid + '/roles', {'roles': ['ATTENDEE', 'STAFF']}, 'admin')
    login('staff')
    note('Role revocation rejects old bearer permissions; inbox notice and fresh login verified')
    extra = call('POST', '/api/v1/bookings', {'eventId': eid, 'ticketTypeId': state['ticketType']['id'], 'quantity': 1},
                 'attendee', 201, {'Idempotency-Key': str(uuid.uuid4())})
    call('POST', '/api/v1/bookings/' + extra['id'] + '/cancel', {}, 'attendee')
    state['event'] = call('GET', event_path(), who='organizer')
    state['event'] = call('POST', event_path() + '/cancel', {'version': state['event']['version']}, 'organizer')
    tickets = call('POST', '/api/v1/bookings/' + booking + '/tickets', {}, 'attendee')
    assert all(t['status'] == 'VOID' for t in tickets) and tickets[0]['checkedInAt']
    call('POST', f'/api/v1/staff/events/{eid}/verify', code, 'staff', 409)
    call('GET', '/api/v1/events/' + eid, expected=404)
    stats = call('GET', event_path() + '/statistics', who='organizer')
    assert (stats['validReservedTickets'], stats['cancelledTickets'], stats['checkedInTickets']) == (0, 3, 0)
    audit = call('GET', f'/api/v1/staff/events/{eid}/check-ins', who='staff')
    assert len(audit) == 1
    report.update(status='PASS', finishedAt=datetime.now(timezone.utc).isoformat(), eventId=eid, finalStatistics=stats)
    note('Unused-order cancellation, event cancellation, VOID tickets, retained audit and final 0/3/0 report')

elif args.phase == 'demote':
    for who in ('organizer', 'staff', 'admin'):
        call('PUT', '/api/v1/admin/users/' + state['accounts'][who]['id'] + '/roles', {'roles': ['ATTENDEE']}, 'admin')
    note('All temporary privileged roles removed')
elif args.phase == 'confirm-disabled':
    for who in state['accounts']:
        call('GET', '/api/v1/auth/me', who=who, expected=401)
    report['testAccountsDisabled'] = True
    note('All synthetic accounts disabled; existing bearer tokens rejected')
save()
