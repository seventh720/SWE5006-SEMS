#!/usr/bin/env python3
"""Fail on medium/high findings or incomplete baseline output; preserve all alerts."""
import json
from pathlib import Path
import sys


def summarize(directory):
    report = json.loads((directory / 'zap.json').read_text())
    coverage = json.loads((directory / 'coverage.json').read_text())
    if len(coverage) != 5 or any(item['status'] != 200 for item in coverage):
        raise ValueError('Public page/API seed coverage is incomplete')
    sites = report.get('site', [])
    if not sites:
        raise ValueError('ZAP report contains no scanned site')
    alerts = [alert for site in sites for alert in site.get('alerts', [])]
    counts = {name: 0 for name in ('High', 'Medium', 'Low', 'Informational')}
    names = {3: 'High', 2: 'Medium', 1: 'Low', 0: 'Informational'}
    lines = ['# DAST baseline', '', 'Unauthenticated public pages and GET APIs; passive scan.', '']
    for alert in alerts:
        risk = int(alert['riskcode'])
        counts[names[risk]] += 1
        lines.append(f"- {names[risk]} · {alert['pluginid']} · {alert['name']}")
    lines.extend(['', 'Alert types: ' + ', '.join(f'{key}={value}' for key, value in counts.items()),
                  '', 'Gate: fail on High/Medium or scanner/coverage errors. Low/Informational remain visible.', ''])
    (directory / 'summary.md').write_text('\n'.join(lines))
    print('\n'.join(lines))
    return 1 if counts['High'] or counts['Medium'] else 0


if __name__ == '__main__':
    sys.exit(summarize(Path(sys.argv[1])))
