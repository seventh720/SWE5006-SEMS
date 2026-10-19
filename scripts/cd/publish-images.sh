#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
: "${GITHUB_SHA:?Run publication in GitHub Actions}"
: "${GITHUB_REPOSITORY:?}"
: "${GITHUB_ACTOR:?}"
: "${REGISTRY_TOKEN:?}"
registry="ghcr.io/$(printf '%s' "$GITHUB_REPOSITORY" | tr '[:upper:]' '[:lower:]')"
auth=$(mktemp -d)
trap 'rm -rf "$auth"' EXIT
printf '%s' "$REGISTRY_TOKEN" | docker --config "$auth" login ghcr.io -u "$GITHUB_ACTOR" --password-stdin
for service in backend frontend postgres; do
    source="sems5006-$service:$GITHUB_SHA"
    test "$(docker image inspect "$source" --format '{{.Id}}')" = "$(cat "image-reports/$service.id")"
    ref="$registry/$service:$GITHUB_SHA"
    docker tag "$source" "$ref"
    docker --config "$auth" push "$ref"
done
python3 - "$registry" "$GITHUB_SHA" <<'PY'
import json,subprocess,sys
from pathlib import Path
registry,sha=sys.argv[1:]
services={}
for name in ['backend','frontend','postgres']:
    repo=f'{registry}/{name}'
    image=json.loads(subprocess.check_output(['docker','image','inspect',f'{repo}:{sha}']))[0]
    digest=next(d for d in image['RepoDigests'] if d.startswith(repo+'@sha256:'))
    services[name]={'image':digest}
Path('image-reports/compose.release.json').write_text(json.dumps({'x-release-commit':sha,'services':services},indent=2)+'\n')
PY
