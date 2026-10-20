#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
root=$PWD
workspace=$(mktemp -d)
project="sems5006-smoke-$$"
compose=(docker compose --env-file "$workspace/runtime.env" -p "$project" -f "$root/deployment/compose.yaml" -f "$root/image-reports/compose.images.json" -f "$workspace/ports.yaml")
cleanup() {
    "${compose[@]}" logs --no-color > "$root/image-reports/smoke-containers.log" 2>&1 || true
    "${compose[@]}" down -v --remove-orphans >/dev/null 2>&1 || true
    rm -rf "$workspace"
}
trap cleanup EXIT
python3 - "$workspace" <<'PY'
from pathlib import Path
import json,secrets,subprocess,sys
p=Path(sys.argv[1])
(p/'runtime.env').write_text('POSTGRES_PASSWORD='+secrets.token_hex(20)+'\nSEMS_JWT_SECRET='+secrets.token_hex(32)+'\n')
(p/'ports.yaml').write_text('''services:
  postgres:
    ports: !reset []
  backend:
    ports: !reset []
    environment:
      SERVER_FORWARD_HEADERS_STRATEGY: framework
  frontend:
    ports: !override
      - "127.0.0.1::80"
''')
config=json.loads(subprocess.check_output(['docker','compose','--env-file',str(p/'runtime.env'),'-p','swe5006','-f','deployment/compose.yaml','-f','deployment/compose.cloud.yaml','config','--format','json']))
assert all(not config['services'][name].get('ports') for name in ('backend', 'frontend'))
ports=config['services']['postgres']['ports']
assert len(ports) == 1 and ports[0]['host_ip'] == '127.0.0.1' and str(ports[0]['published']) == '15432' and ports[0]['target'] == 5432, 'Database must only publish on the SSH loopback port'
assert set(config['services']['backend']['networks']) == {'default'}
assert set(config['services']['postgres']['networks']) == {'default'}
assert 'edge' in config['services']['frontend']['networks']
print('PASS cloud port and network isolation')
PY
"${compose[@]}" up --no-build -d --wait --wait-timeout 180
endpoint=$("${compose[@]}" port frontend 80)
python3 scripts/cd/smoke.py "http://$endpoint" | tee image-reports/smoke.txt
frontend_image=$(python3 -c 'import json; print(json.load(open("image-reports/compose.images.json"))["services"]["frontend"]["image"])')
python3 scripts/cd/test-proxy-keepalive.py "$frontend_image" | tee image-reports/proxy-keepalive.txt
