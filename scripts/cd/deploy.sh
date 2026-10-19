#!/usr/bin/env bash
# Runs on Lightsail as ubuntu. Secrets remain in the host-owned .env.
set -euo pipefail
umask 077
root=/home/ubuntu/swe5006
release=$(cd "$(dirname "$0")/../.." && pwd -P)
sha=${1:?Expected commit SHA}
[[ "$sha" =~ ^[0-9a-f]{40}$ ]] || exit 1
[[ "$release" == "$root/releases/"* ]] || { echo 'Deploy from a release directory'; exit 1; }
python3 "$release/scripts/cd/validate-release.py" "$release/compose.release.json" "$sha"
exec 9>"$root/.deploy.lock"
flock -w 900 9
if [[ -f "$root/deployed-sha" ]] && [[ $(cat "$root/deployed-sha") == "$sha" ]]; then
    echo 'This commit is already deployed.'
    exit 0
fi
[[ -f "$root/.env" ]] || { echo 'Missing host runtime environment'; exit 1; }
compose=(sudo -n docker compose --env-file "$root/.env" -p swe5006 -f "$release/deployment/compose.yaml" -f "$release/deployment/compose.cloud.yaml" -f "$release/compose.release.json")
"${compose[@]}" config --quiet
"${compose[@]}" pull
mkdir -p "$root/backups"
backup="$root/backups/pre-${sha}-$(date -u +%Y%m%dT%H%M%SZ).dump"
# Fail closed if the existing database cannot be backed up. This is an upgrade path.
# Backups are intentionally owned by ubuntu, not root.
# shellcheck disable=SC2024
sudo -n docker exec swe5006-postgres-1 sh -c 'exec pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > "$backup.partial"
# shellcheck disable=SC2024
sudo -n docker exec -i swe5006-postgres-1 pg_restore --list < "$backup.partial" > /dev/null
mv "$backup.partial" "$backup"
echo "Database backup created: $(basename "$backup")"
"${compose[@]}" up -d --no-build --wait --wait-timeout 240
# Nginx resolves the backend name on startup; reload it after any backend replacement.
"${compose[@]}" restart frontend
"${compose[@]}" up -d --no-build --wait --wait-timeout 90
for path in / /events /healthz /actuator/health /api/v1/events; do
    curl --fail --silent --show-error --retry 5 --retry-delay 3 --retry-all-errors --max-time 20 "https://swe5006.seventh720.com$path" > /dev/null
done
curl --fail --silent --show-error --max-time 20 https://swe5006.seventh720.com/actuator/health | python3 -c 'import json,sys; assert json.load(sys.stdin)["status"] == "UP"'
# Only mark a release successful after public HTTPS checks pass.
if [[ -L "$root/current" ]]; then
    ln -sfn "$(readlink "$root/current")" "$root/previous"
fi
ln -sfn "$release" "$root/current.next"
mv -Tf "$root/current.next" "$root/current"
printf '%s\n' "$sha" > "$root/deployed-sha.next"
mv "$root/deployed-sha.next" "$root/deployed-sha"
echo "PASS deployed $sha; public HTTPS health checks passed"
