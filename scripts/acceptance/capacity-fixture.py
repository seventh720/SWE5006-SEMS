#!/usr/bin/env python3
"""Create only synthetic capacity fixtures; private state contains credentials."""
import argparse
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime, timedelta, timezone
import json
from pathlib import Path
import secrets
import ssl
import threading
import time
import urllib.error
import urllib.request

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('phase', choices=['seed', 'prepare', 'cancel'])
parser.add_argument('--state', required=True, type=Path)
parser.add_argument('--stop-file', required=True, type=Path)
parser.add_argument('--base', default='https://swe5006.seventh720.com')
parser.add_argument('--count', type=int, default=1000)
args = parser.parse_args()
context = ssl.create_default_context(cafile='/etc/ssl/cert.pem')
lock = threading.RLock()
state = json.loads(args.state.read_text()) if args.state.exists() else {
    'base': args.base, 'marker': 'load' + datetime.now(timezone.utc).strftime('%Y%m%d%H%M%S'),
    'accounts': [], 'events': [], 'startedAt': datetime.now(timezone.utc).isoformat(),
}


def save():
    with lock:
        args.state.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
        temporary = args.state.with_suffix('.tmp')
        temporary.touch(mode=0o600, exist_ok=True)
        temporary.write_text(json.dumps(state, indent=2) + '\n')
        temporary.replace(args.state)
        args.state.chmod(0o600)


def call(method, path, data=None, token=None, expected=200):
    if args.phase != 'cancel' and args.stop_file.exists():
        raise RuntimeError('Stopped by health guard')
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    req = urllib.request.Request(state['base'] + path, method=method, headers=headers,
                                 data=None if data is None else json.dumps(data).encode())
    try:
        with urllib.request.urlopen(req, timeout=15, context=context) as response:
            status, raw = response.status, response.read()
    except urllib.error.HTTPError as error:
        status, raw = error.code, error.read()
    if status != expected:
        raise RuntimeError(f'{method} {path}: expected {expected}, got {status}')
    return json.loads(raw) if raw else None


def login(account):
    result = call('POST', '/api/v1/auth/login', {'email': account['email'], 'password': account['password']})
    assert result['expiresIn'] == 3600
    with lock:
        account.update(id=result['user']['id'], token=result['accessToken'])
        save()


def seed(account):
    if 'id' not in account:
        result = call('POST', '/api/v1/auth/register',
                      {k: account[k] for k in ['username', 'email', 'password']}, expected=201)
        with lock:
            account['id'] = result['id']
            save()
    login(account)
    with lock:
        completed = sum('token' in item for item in state['accounts'])
        if completed % 50 == 0 or completed == len(state['accounts']):
            print(f'Seeded/logged in {completed}/{len(state["accounts"])} synthetic accounts', flush=True)


if args.phase == 'seed':
    if not state['accounts']:
        for i in range(args.count + 1):
            username = state['marker'] + ('_organizer' if i == 0 else f'_{i:04}')
            state['accounts'].append({'username': username, 'email': username + '@example.test',
                                      'password': secrets.token_hex(8)})
        save()
    # At most 4 workers, with a global rate of 3 new accounts/second. Registration
    # and login are setup, not part of the measured authenticated workload.
    with ThreadPoolExecutor(max_workers=4) as pool:
        pending = []
        for account in state['accounts']:
            if args.stop_file.exists():
                break
            pending.append(pool.submit(seed, account))
            time.sleep(1 / 3)
        for i, future in enumerate(as_completed(pending), 1):
            future.result()
            if i % 50 == 0 or i == len(pending):
                print(f'Seeded/logged in {i}/{len(pending)} synthetic accounts', flush=True)
    assert all('token' in account for account in state['accounts'])

elif args.phase == 'prepare':
    organizer = state['accounts'][0]
    login(organizer)
    me = call('GET', '/api/v1/auth/me', token=organizer['token'])
    assert 'ORGANIZER' in me['roles'], 'Operator must grant only this new synthetic account ORGANIZER first'
    for i in range(len(state['events']), 11):
        start = datetime.now(timezone.utc) + timedelta(hours=3)
        capacity = 100 if i == 10 else 10000
        event = call('POST', '/api/v1/organizer/events', {
            'title': f'LOAD TEST {state["marker"]} {i:02} - please do not book',
            'description': 'Temporary synthetic performance fixture; will be cancelled and removed after measurement.',
            'location': 'Synthetic load test', 'startsAt': start.isoformat(),
            'endsAt': (start + timedelta(hours=1)).isoformat(), 'capacity': capacity,
            'illustration': 'TECH', 'bookingRequirements': {},
        }, organizer['token'], 201)
        state['events'].append({'id': event['id'], 'capacity': capacity})
        save()
        path = '/api/v1/organizer/events/' + event['id']
        ticket = call('POST', path + '/ticket-types/default-free', {}, organizer['token'])
        state['events'][-1]['ticketTypeId'] = ticket['id']
        save()
        event = call('GET', path, token=organizer['token'])
        call('POST', path + '/publish', {'version': event['version']}, organizer['token'])
    credentials = {'base': state['base'], 'marker': state['marker'], 'events': state['events'],
                   'accounts': [{'id': a['id'], 'token': a['token']} for a in state['accounts'][1:]]}
    destination = args.state.parent / 'k6-credentials.json'
    destination.touch(mode=0o600, exist_ok=True)
    destination.write_text(json.dumps(credentials) + '\n')
    print('Prepared 10 distributed events and one 100-ticket contention event; no ADMIN used', flush=True)

elif args.phase == 'cancel':
    organizer = state['accounts'][0]
    login(organizer)
    for item in state['events']:
        path = '/api/v1/organizer/events/' + item['id']
        event = call('GET', path, token=organizer['token'])
        assert event['title'].startswith('LOAD TEST ' + state['marker'])
        if event['status'] == 'PUBLISHED':
            call('POST', path + '/cancel', {'version': event['version']}, organizer['token'])
        print('Cancelled synthetic event', item['id'], flush=True)
save()
