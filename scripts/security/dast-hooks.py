"""Seed the SPA's public entry points and API responses without submitting forms."""
import json
from pathlib import Path


def zap_started(zap, target):
    zap.spider.set_option_process_form(False)
    zap.spider.set_option_post_form(False)
    coverage = []
    for path in ('/', '/login', '/events', '/api/v1/events?page=0&size=10', '/actuator/health'):
        url = target.rstrip('/') + path
        messages = zap.core.send_request(
            f'GET {url} HTTP/1.1\r\nAccept: */*\r\n\r\n', followredirects=False)
        response = messages[0]['responseHeader']
        status = int(response.splitlines()[0].split()[1])
        coverage.append({'url': url, 'status': status})
        Path('/zap/wrk/coverage.json').write_text(json.dumps(coverage, indent=2) + '\n')
        if status != 200:
            raise RuntimeError(f'DAST seed did not return 200: {url} ({status})')
