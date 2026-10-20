#!/usr/bin/env python3
"""Verify real nginx connection reuse and per-request forwarding on an isolated network."""
import argparse
import http.client
import json
from pathlib import Path
import subprocess
import tempfile
import time
import uuid

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('image')
parser.add_argument('--configuration', type=Path, default=Path('frontend/nginx.conf'))
parser.add_argument('--expected-connections', type=int, default=1)
args = parser.parse_args()
suffix = uuid.uuid4().hex[:10]
network = 'sems5006-proxy-test-' + suffix
backend = network + '-backend'
frontend = network + '-frontend'


def docker(*command):
    return subprocess.check_output(['docker', *command], text=True, timeout=30).strip()


with tempfile.TemporaryDirectory() as directory:
    fixture = Path(directory) / 'probe.conf'
    fixture.write_text('''events {}
http {
    access_log off;
    server {
        listen 8080;
        add_header X-Probe-Connection $connection always;
        add_header X-Probe-Protocol $server_protocol always;
        add_header X-Probe-Uri $request_uri always;
        add_header X-Probe-Host $http_host always;
        add_header X-Probe-Authorization $http_authorization always;
        add_header X-Probe-Origin $http_origin always;
        add_header X-Probe-Proto $http_x_forwarded_proto always;
        add_header X-Probe-Forwarded-Host $http_x_forwarded_host always;
        location / { return 200 '{"ok":true}'; }
    }
}
''')
    try:
        docker('network', 'create', network)
        docker('run', '--rm', '-d', '--name', backend, '--network', network, '--network-alias', 'backend',
               '--entrypoint', 'nginx', '-v', str(fixture) + ':/tmp/probe.conf:ro',
               args.image, '-c', '/tmp/probe.conf', '-g', 'daemon off;')
        docker('run', '--rm', '-d', '--name', frontend, '--network', network,
               '-p', '127.0.0.1::80', '-v', str(args.configuration.resolve()) + ':/etc/nginx/conf.d/default.conf:ro',
               '--entrypoint', 'nginx', args.image, '-g', 'daemon off;')
        port = int(docker('port', frontend, '80').rsplit(':', 1)[1])
        for attempt in range(40):
            try:
                probe = http.client.HTTPConnection('127.0.0.1', port, timeout=2)
                probe.request('GET', '/healthz')
                response = probe.getresponse()
                assert response.status == 200
                response.read()
                probe.close()
                break
            except (OSError, http.client.HTTPException):
                if attempt == 39:
                    raise
                time.sleep(0.25)
        connections = set()
        client = http.client.HTTPConnection('127.0.0.1', port, timeout=5)
        try:
            for index in range(20):
                path = ('/api/probe' if index % 2 == 0 else '/actuator/health') + '?iteration=' + str(index)
                authorization = 'Bearer synthetic-probe-' + str(index)
                client.request('GET', path, headers={'Host': 'events.example.test:8443',
                    'Authorization': authorization, 'Origin': 'https://events.example.test:8443',
                    'X-Forwarded-Proto': 'https', 'X-Forwarded-Host': 'untrusted.example.test'})
                response = client.getresponse()
                assert response.status == 200
                assert json.loads(response.read()) == {'ok': True}
                assert response.getheader('X-Probe-Protocol') == 'HTTP/1.1'
                assert response.getheader('X-Probe-Uri') == path
                assert response.getheader('X-Probe-Host') == 'events.example.test:8443'
                assert response.getheader('X-Probe-Authorization') == authorization
                assert response.getheader('X-Probe-Origin') == 'https://events.example.test:8443'
                assert response.getheader('X-Probe-Proto') == 'https'
                assert response.getheader('X-Probe-Forwarded-Host') == 'events.example.test:8443'
                assert response.getheader('Strict-Transport-Security') == 'max-age=31536000'
                connections.add(response.getheader('X-Probe-Connection'))
        finally:
            client.close()
        assert None not in connections
        assert len(connections) == args.expected_connections, f'Expected {args.expected_connections} upstream connections, got {len(connections)}'
        print(f'PASS 20 API/health requests, {len(connections)} upstream connection(s); URI, per-request authorization and origin preserved')
    finally:
        for name in [frontend, backend]:
            subprocess.run(['docker', 'rm', '-f', name], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=30)
        subprocess.run(['docker', 'network', 'rm', network], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=30)
