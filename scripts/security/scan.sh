#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
# Scan a clean source snapshot, never mount local .env, credentials or node_modules.
scan_root=$(mktemp -d)
trap 'rm -rf "$scan_root"' EXIT
mkdir -p security-reports "$scan_root/reports"
rm -f security-reports/semgrep.json security-reports/trivy-source.json security-reports/trivy-java.json
python3 scripts/security/check-spring-scope.py
python3 - "$scan_root" <<'PY'
import pathlib, shutil, subprocess, sys
root = pathlib.Path(sys.argv[1])
paths = subprocess.check_output(['git', 'ls-files', '-z', '--cached', '--others', '--exclude-standard']).decode().split('\0')
for name in set(filter(None, paths)):
    source = pathlib.Path(name)
    if source.is_symlink():
        raise SystemExit(f'Symlink is not supported in scan snapshot: {name}')
    if source.is_file():
        target = root / name
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, target)
jars = list(pathlib.Path('backend/target').glob('sems-backend-*.jar'))
if len(jars) != 1:
    raise SystemExit('Build backend first: mvn -f backend/pom.xml clean verify')
(root / 'artifacts').mkdir()
shutil.copyfile(jars[0], root / 'artifacts' / jars[0].name)
PY
result=0
run_scan() {
    if "$@"; then return 0; else result=1; fi
}
run_scan docker run --rm -v "$scan_root:/src" -w /src semgrep/semgrep:1.181.0 \
    semgrep scan --config p/java --config p/typescript --error --strict --metrics=off \
    --json --output reports/semgrep.json backend/src/main frontend/src
run_scan docker run --rm -v "$scan_root:/src" -v sems5006-trivy-cache:/root/.cache/trivy -w /src aquasec/trivy:0.75.0 fs \
    --scanners vuln,secret --severity HIGH,CRITICAL --exit-code 1 --offline-scan \
    --skip-dirs artifacts --skip-dirs reports --skip-files '**/pom.xml' \
    --format json --output reports/trivy-source.json .
# Inspect shipped nested dependencies, not just Maven's declared direct dependencies.
run_scan docker run --rm -v "$scan_root:/src" -v sems5006-trivy-cache:/root/.cache/trivy -w /src aquasec/trivy:0.75.0 rootfs \
    --ignorefile scripts/security/trivyignore.yaml --show-suppressed \
    --scanners vuln --severity HIGH,CRITICAL --exit-code 1 --offline-scan \
    --format json --output reports/trivy-java.json artifacts
cp "$scan_root"/reports/*.json security-reports/
echo "Security reports saved in security-reports/ (exit $result)."
exit "$result"
