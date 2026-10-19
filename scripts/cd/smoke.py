#!/usr/bin/env python3
"""Exercise the built web proxy and backend, including HTTPS origin handling."""
import json
import secrets
import sys
import urllib.request

base = sys.argv[1]

def request(path, data=None, token=None):
    headers = {'Host': 'swe5006.seventh720.com', 'Origin': 'https://swe5006.seventh720.com',
               'X-Forwarded-Proto': 'https', 'Content-Type': 'application/json'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    req = urllib.request.Request(base + path, headers=headers,
                                 data=None if data is None else json.dumps(data).encode())
    with urllib.request.urlopen(req, timeout=20) as response:
        return response.status, response.read()

for path in ['/', '/events', '/healthz', '/actuator/health', '/api/v1/events']:
    status, body = request(path)
    assert status == 200, path
    if path == '/actuator/health':
        assert json.loads(body)['status'] == 'UP'
    print('PASS', path)

suffix = secrets.token_hex(5)
account = {'username': 'smoke' + suffix, 'email': suffix + '@example.test',
           'password': secrets.token_hex(8)}
status, _ = request('/api/v1/auth/register', account)
assert status in (200, 201)
status, body = request('/api/v1/auth/login', {'email': account['email'], 'password': account['password']})
assert status == 200
payload = json.loads(body)
assert payload['expiresIn'] == 3600, 'JWT lifetime must be one hour'
assert payload.get('accessToken'), 'Login must issue an access token'
status, me = request('/api/v1/auth/me', token=payload['accessToken'])
assert status == 200 and json.loads(me)['email'] == account['email']
print('PASS registration and login through HTTPS-origin proxy headers')
