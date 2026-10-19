#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
sha=${GITHUB_SHA:-$(git rev-parse HEAD)}
mkdir -p image-reports
python3 scripts/security/check-spring-scope.py
docker build --pull --target ci -t "sems5006-backend:$sha" backend
docker build --pull --target ci -t "sems5006-frontend:$sha" frontend
docker build --pull -t "sems5006-postgres:$sha" deployment/postgres
python3 - "$sha" <<'PY'
import json, sys
from pathlib import Path
sha=sys.argv[1]
Path('image-reports/compose.images.json').write_text(json.dumps({'services': {
    'backend': {'image': f'sems5006-backend:{sha}'},
    'frontend': {'image': f'sems5006-frontend:{sha}'},
    'postgres': {'image': f'sems5006-postgres:{sha}'},
}},indent=2)+'\n')
PY
result=0
for service in backend frontend postgres; do
    ref="sems5006-$service:$sha"
    docker image inspect "$ref" --format '{{.Id}}' > "image-reports/$service.id"
    if docker run --rm -v /var/run/docker.sock:/var/run/docker.sock \
        -v "$PWD/image-reports:/reports" \
        -v "$PWD/scripts/security/trivyignore.yaml:/config/trivyignore.yaml:ro" \
        -v sems5006-trivy-cache:/root/.cache/trivy aquasec/trivy:0.75.0 image \
        --ignorefile /config/trivyignore.yaml --show-suppressed \
        --scanners vuln,secret --severity HIGH,CRITICAL --exit-code 1 \
        --format json --output "/reports/$service.json" "$ref"; then
        echo "$service: PASS"
    else
        result=1
        echo "$service: FAIL (see image report)"
    fi
done
exit "$result"
