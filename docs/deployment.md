# Staging deployment and maintenance

[English README](../README.md) | [中文 README](../README.zh-CN.md) | [Delivery checklist](remaining-work.zh-CN.md)

Verified business baseline on 20 October 2026 (SGT): `270c2b8`, deployed successfully by [run 39311200552](https://github.com/seventh720/SWE5006-SEMS/actions/runs/39311200552). Public URL: <https://swe5006.seventh720.com>. The running backend uses `SEMS_JWT_TTL=PT1H`.

## Release flow

1. PR and main builds run lint, unit/integration tests, frontend build and static security checks.
2. The image job packages the tested JAR/dist using Dockerfile `ci` targets, scans backend/frontend/PostgreSQL, and exercises the images against an isolated database.
3. Main publishes SHA-tagged GHCR images and the digest-pinned `release-images/compose.release.json` artifact. PRs do not publish or deploy.
4. `Deploy · Staging` consumes the same run's manifest, verifies the commit and image names, and sends committed deployment files to the host over SSH.
5. The host serializes deployments, pulls images, creates and checks a PostgreSQL backup, runs Compose health checks, restarts frontend Nginx to refresh backend DNS, and checks public HTTPS endpoints.
6. Only a successful deployment updates `current` and `deployed-sha`. Superseded main runs are skipped before deployment begins.
7. After deployment, `Security · DAST Baseline` passively scans public HTTPS pages/APIs and uploads `dast-report`. High/Medium findings or scanner errors fail the workflow; Low/Informational findings remain visible. This post-deployment check does not automatically roll back containers or data. See the [DAST scope and results](dast.zh-CN.md).

Implementation: [workflow](../.github/workflows/ci.yml), [runner script](../scripts/cd/remote-deploy.sh), [host script](../scripts/cd/deploy.sh), [manifest validation](../scripts/cd/validate-release.py).

### GitHub configuration

The `staging` environment allows only `main`:

| Kind | Name | Purpose |
|---|---|---|
| Environment secret | `STAGING_SSH_KEY` | Dedicated SWE5006 deployment private key |
| Environment secret | `STAGING_KNOWN_HOSTS` | Trusted SSH host fingerprint |
| Environment variable | `STAGING_HOST` | Deployment host |
| Repository secret, optional | `TELEGRAM_TOKEN` | Notification bot token |
| Repository secret, optional | `TELEGRAM_TO` | Notification recipient/chat ID |

The deployment key authenticates as `ubuntu` with Docker/sudo access. It is a separate key from SWE5001's but is not a separate operating-system security boundary. Do not commit keys or runtime environment files. Telegram notification failure is non-blocking; missing Telegram secrets skip the notification.

Images currently allow anonymous pull from GHCR. If package visibility becomes private, configure registry authentication before deploying; the host script does not currently log in to GHCR.

### Reports and image verification

Actions uploads lint, coverage, SAST, dependency/secret, image scan/smoke, and post-deployment DAST reports. Release manifests are retained for 30 days; image security and DAST reports for 7 days. Preserve evidence needed for the presentation before artifacts expire.

[Scoped Trivy exceptions](../scripts/security/trivyignore.yaml) have documented applicability checks and expiry dates. A passing scan does not mean every finding was absent. Image smoke tests verify registration, login, `expiresIn=3600`, public APIs and proxy headers; they are not DAST or a full browser acceptance test.

Local image verification, after building current application artifacts:

```bash
RUN_CONTAINER_TESTS=true mvn -f backend/pom.xml clean verify
npm --prefix frontend ci
npm --prefix frontend run build
bash scripts/cd/images.sh
bash scripts/cd/image-smoke.sh
```

The smoke script creates its own Compose project, random credentials, database volume and dynamic local port, and removes only those temporary resources on exit.

## Host layout and shared proxy

SWE5006 uses its own project name `swe5006`, database volume, runtime credentials and JWT signing secret. Docker Compose **2.24.4+** is required for the cloud overlay.

| Path under `/home/ubuntu/swe5006` | Purpose |
|---|---|
| `.env` | Host-owned runtime settings; CD does not overwrite them |
| `releases/<sha>-<run>-<attempt>/` | Committed deployment files and pinned image manifest |
| `current` / `previous` | Latest and preceding successful releases |
| `deployed-sha` | Commit of the last successful deployment |
| `backups/pre-<sha>-<timestamp>.dump` | Pre-upgrade PostgreSQL custom-format backup |
| `.deploy.lock` | Serializes manual and Actions deployments |

The cloud overlay applies restart policies, log rotation and memory limits (PostgreSQL 512 MiB, backend 768 MiB, frontend 96 MiB). Frontend and backend publish no host ports. PostgreSQL binds only to `127.0.0.1:15432`.

SWE5001's Caddy owns ports 80/443. It routes `swe5006.seventh720.com` to `sems5006-web:80` on `swe5006-edge`; only the SWE5006 frontend joins that edge network. Preserve both applications' routes and existing SWE5001 Compose settings. The host-owned `/home/ubuntu/swe5001/shared-proxy/Caddyfile` and `shared-proxy.compose.yaml` survive release replacement through the merged [SWE5001 PR #16](https://github.com/seventh720/SWE5001-SEMS/pull/16).

On 21 October 2026 (SGT), the shared Caddy memory limit was raised from 128 to 256 MiB using `services.caddy.mem_limit: 256m` in the host-owned `shared-proxy.compose.yaml`. Only Caddy was recreated, preserving its image, routes, certificate volumes and networks; both systems' health checks passed. The server plan and SWE5006 container limits were unchanged. Keep this override when applying future SWE5001 releases.

Repository [proxy examples](../deployment/proxy/) are references for initial setup, not instructions to overwrite the live Caddy configuration. Do not start a second proxy on 80/443. Retain the trusted proxy boundary when using forwarded HTTPS headers.

The frontend image uses `frontend/nginx-main.conf` to set 4096 connections and 8192 open files per Nginx worker. The current host hard file limit is 524288, so the worker limit can rise above the inherited soft limit of 1024. Client and upstream sockets both consume connection slots. The isolated image smoke test keeps and reuses 1200 frontend connections under the inherited 1024 soft limit; deployment and runtime worker limits have been verified. The [latest cloud run](acceptance/capacity-2026-10-21/README.md) passes 1000-VU reads; mixed traffic and inventory contention retain latency and connection reliability limits.

### Runtime changes and initial setup

Edit the private host `.env` for runtime changes. Next deployment recreates affected containers with those values; changing a file alone does not change a running container. Existing JWTs keep their original expiry even when the TTL setting changes.

The CD script upgrades an existing instance and deliberately fails if its database cannot be backed up. On a fresh host, first provision private `.env`, the database and shared proxy using the base/cloud Compose files and a verified release manifest. Then initialize the first administrator and clear its bootstrap password. The current host is already initialized; do not repeat bootstrap for routine deployments.

## Database inspection

Create a PostgreSQL connection with an SSH tunnel in Navicat or DBeaver:

| Setting | Value |
|---|---|
| SSH host / port | `18.139.212.35` / `22` |
| SSH user | `ubuntu` |
| SSH authentication | Authorized Lightsail private key on your own device |
| PostgreSQL host / port, through SSH | `127.0.0.1` / `15432` |
| Database | `sems` |
| Database user | `sems5006_viewer` |
| Password | Maintainer's private `.secrets/cloud-staging/navicat.json`; never commit it |

The viewer can SELECT existing public tables and future tables created by `sems_user`, but has no business-data write privileges. Use schema changes through Flyway, not this account. SWE5001 retains `127.0.0.1:5432`; do not expose SWE5006's 15432 in the Lightsail firewall. Teammates need an authorized private connection configuration; Git clones do not include it.

## Failed deployment and recovery

A failed update retains the backup and last successful version marker. Containers may already have changed, so an unchanged `deployed-sha` does **not** prove automatic rollback. There is no automatic container/database rollback.

1. Read the failed Actions step, container health and the retained release manifest.
2. Identify the last successful `current`, any attempted release, and the pre-upgrade backup.
3. Check Flyway migration compatibility before restoring an older application image. Image rollback does not undo database migrations.
4. Validate a backup by restoring it into a separate temporary database before planning a live restore. Listing the archive is only a readability check, not a recovery rehearsal.
5. Recheck HTTPS, login and the affected business flow after recovery. Never use `down -v` on the real environment to upgrade or recover it.

Phone QR scanning was confirmed working by the maintainer on 20 October; see the [acceptance record](acceptance/sprint4-2026-10-20/README.md). The latest main DAST job passed. A real dump containing bookings, tickets and audit history was restored into a disposable database on 20 October, with schema, constraints, migration history and row counts checked; both temporary databases were removed. A small 1/5/10-VU read baseline was also measured. See the [actual evidence and limits](acceptance/sprint4-2026-10-20/README.md); these establish database-level recovery only. The deployed proxy connection pool and current 500/1000-VU results are documented in the [latest capacity report](acceptance/capacity-2026-10-21/README.md).


## Repeat the isolated restore check

On the SWE5006 host, run the [restore checker](../scripts/acceptance/restore-check.py) with an existing private dump and an output report path:

```bash
python3 scripts/acceptance/restore-check.py /home/ubuntu/swe5006/backups/selected.dump /home/ubuntu/swe5006/acceptance/restore.json
```

Use a real selected backup filename. The script uses the existing deployment operator's `sudo docker` access, creates fresh databases, restores with `--exit-on-error --single-transaction`, compares the live schema after a server parse round trip, checks constraints/migration history/counts, and removes only its temporary databases. A live database can change after a backup, so count differences must be assessed against the backup time. The dump remains private on the host; commit only validation metadata.
