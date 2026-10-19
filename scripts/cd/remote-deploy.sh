#!/usr/bin/env bash
# GitHub Actions runner: package only committed deployment code and tested digests.
set -euo pipefail
cd "$(dirname "$0")/../.."
: "${STAGING_HOST:?Missing staging host}"
: "${STAGING_SSH_KEY:?Missing staging SSH key}"
: "${STAGING_KNOWN_HOSTS:?Missing trusted server host key}"
: "${GITHUB_SHA:?Missing commit}"
: "${GITHUB_RUN_ID:?Missing run ID}"
: "${GITHUB_RUN_ATTEMPT:?Missing run attempt}"
[[ "$GITHUB_SHA" =~ ^[0-9a-f]{40}$ && "$GITHUB_RUN_ID" =~ ^[0-9]+$ && "$GITHUB_RUN_ATTEMPT" =~ ^[0-9]+$ ]] || exit 1
[[ "$STAGING_HOST" =~ ^[a-zA-Z0-9.-]+$ ]] || exit 1
python3 scripts/cd/validate-release.py image-reports/compose.release.json "$GITHUB_SHA"
umask 077
workspace=$(mktemp -d)
trap 'rm -rf "$workspace"' EXIT
printf '%s\n' "$STAGING_SSH_KEY" > "$workspace/key"
printf '%s\n' "$STAGING_KNOWN_HOSTS" > "$workspace/known_hosts"
unset STAGING_SSH_KEY STAGING_KNOWN_HOSTS
mkdir "$workspace/package"
git archive "$GITHUB_SHA" deployment scripts/cd | tar -x -C "$workspace/package"
cp image-reports/compose.release.json "$workspace/package/compose.release.json"
tar -czf "$workspace/release.tgz" -C "$workspace/package" .
options=(-i "$workspace/key" -o IdentitiesOnly=yes -o BatchMode=yes -o StrictHostKeyChecking=yes -o "UserKnownHostsFile=$workspace/known_hosts" -o ConnectTimeout=20 -o ServerAliveInterval=30)
release="/home/ubuntu/swe5006/releases/$GITHUB_SHA-$GITHUB_RUN_ID-$GITHUB_RUN_ATTEMPT"
# Validated release identifiers intentionally expand on the runner.
# shellcheck disable=SC2029
ssh "${options[@]}" "ubuntu@$STAGING_HOST" "umask 077; mkdir -p '$release'"
scp "${options[@]}" "$workspace/release.tgz" "ubuntu@$STAGING_HOST:$release/release.tgz"
# Validated release identifiers intentionally expand on the runner.
# shellcheck disable=SC2029
ssh "${options[@]}" "ubuntu@$STAGING_HOST" "tar -xzf '$release/release.tgz' -C '$release' && rm '$release/release.tgz' && bash '$release/scripts/cd/deploy.sh' '$GITHUB_SHA'"
