#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
target=${1:-https://swe5006.seventh720.com}
report_dir=${DAST_REPORT_DIR:-dast-reports}
mkdir -p "$report_dir"
report_dir=$(cd "$report_dir" && pwd)
rm -f "$report_dir"/{coverage.json,run.json,scan.log,summary.md,zap.html,zap.json,zap.md,image-digests.json}
workspace=$(mktemp -d)
cleanup() {
    local result=$?
    cp -R "$workspace/." "$report_dir/"
    rm -rf "$workspace"
    exit "$result"
}
trap cleanup EXIT
# The image runs as zap (uid 1000); only temporary public scan output is writable.
chmod 777 "$workspace"
cp scripts/security/dast-hooks.py "$workspace/hooks.py"
image=ghcr.io/zaproxy/zaproxy:2.17.0@sha256:5ccfff9a411743ab2615136a2edd7416bec9ce41572fa94e935df38c14adb2b6
docker_options=(--rm)
if [ -n "${DAST_DOCKER_NETWORK:-}" ]; then
    docker_options+=(--network "$DAST_DOCKER_NETWORK")
fi
python3 - "$target" "$workspace" <<'PY'
import datetime, json, pathlib, subprocess, sys
pathlib.Path(sys.argv[2], 'run.json').write_text(json.dumps({
    'target': sys.argv[1],
    'startedAt': datetime.datetime.now(datetime.timezone.utc).isoformat(),
    'configurationCommit': subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip(),
    'workingTreeDirty': bool(subprocess.check_output(['git', 'status', '--porcelain'], text=True).strip()),
    'scope': 'Unauthenticated public pages and GET APIs; passive baseline, no active attack scan',
    'image': 'ghcr.io/zaproxy/zaproxy:2.17.0@sha256:5ccfff9a411743ab2615136a2edd7416bec9ce41572fa94e935df38c14adb2b6',
}, indent=2) + '\n')
PY
status=0
# Avoid scanner-owned cache directories that a different Linux runner UID cannot remove.
docker run "${docker_options[@]}" -e PYTHONDONTWRITEBYTECODE=1 \
    -v "$workspace:/zap/wrk:rw" "$image" \
    zap-baseline.py -t "$target" -m 1 -T 5 --autooff \
    --hook=/zap/wrk/hooks.py -r zap.html -J zap.json -w zap.md \
    > "$workspace/scan.log" 2>&1 || status=$?
cat "$workspace/scan.log"
# Packaged baseline exits 2 for warnings. Evaluate severity from the full JSON;
# startup, connectivity and other scanner errors must still fail the job.
if [ "$status" -ne 0 ] && [ "$status" -ne 2 ]; then
    echo "ZAP failed (exit $status); see scan.log." >&2
    exit "$status"
fi
docker image inspect "$image" --format '{{json .RepoDigests}}' > "$workspace/image-digests.json"
python3 scripts/security/dast-summary.py "$workspace"
