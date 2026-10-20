# Smart Event Management and Ticketing System · SEMS

[English](README.md) | [简体中文](README.zh-CN.md) | [Documentation](docs/README.md) | [Live system](https://swe5006.seventh720.com)

SWE5006 Team 10's event management and free-ticketing application, built with React, TypeScript, Spring Boot and PostgreSQL. It covers event publication, registration, electronic tickets, staff check-in, cancellation, notifications and reporting.

## Delivery status

Verified on **20 October 2026 (SGT)**: business baseline [`270c2b8`](https://github.com/seventh720/SWE5006-SEMS/commit/270c2b87322f89555d54222b825da82e0694fa54) passed [CI/CD](https://github.com/seventh720/SWE5006-SEMS/actions/runs/39311200552) and is deployed over HTTPS. The backend's runtime JWT lifetime is `PT1H`.

The application is ready for **feature freeze**. Cloud multi-role acceptance, DAST, an isolated database restore and a small read-performance baseline are complete; see the [acceptance evidence](docs/acceptance/sprint4-2026-10-20/README.md). Presentation/Agile materials, backup recordings, rehearsals and the final presentation tag remain. Real-device QR scanning was confirmed working by the maintainer on 20 October; see the [acceptance record](docs/acceptance/sprint4-2026-10-20/README.md). These are tracked in the [delivery checklist](docs/remaining-work.zh-CN.md). Presentation: **27 October 2026, 10:30 SGT**.

The current **500 VU** capacity target passed cloud reads, mixed booking/ticket/cancellation traffic and inventory contention after the proxy connection pool was deployed. Steady-state p95 was 1.94 s for reads and 1.96 s for mixed traffic, with zero request errors. The latest 1000-VU read run completes a 180-second hold with p95 2.85 s, but 41 HTTP 502 responses (0.043%) make the result fail. No container restarts. Nginx connection-limit warnings are reproduced locally; a connection/file-limit fix passes the isolated regression and awaits deployment. The 1000-VU mixed scenario remains untested. See the [latest capacity report](docs/acceptance/capacity-2026-10-21/README.md).

## Features and roles

| Role | Implemented workflow |
|---|---|
| Guest | Search published events by title, location, SGT dates and event phase |
| `ATTENDEE` | Save booking details, pre-register, book free tickets, view orders and QR tickets, cancel eligible bookings |
| `ORGANIZER` | Manage owned events, ticket quotas, registration windows, staff assignments, orders and reports |
| `STAFF` | Verify and check in tickets only for assigned events; view check-in records |
| `ADMIN` | Search/paginate users, manage roles, view system-wide reports and manage personally owned events |

Accounts may hold multiple roles. Public registration grants `ATTENDEE`; staff access also requires an event assignment. Other features include profile avatars, event illustrations and in-app notifications.

**Scope:** tickets are free. Payments, refunds, seat maps, coupons, email/SMS delivery and self-service password/email changes are outside the current delivery scope.

### Business rules

- Each booking contains one ticket type and 1–10 tickets. Idempotency keys prevent duplicate orders; event locks and quota checks protect inventory. Confirmation issues one electronic ticket per admission.
- Pre-registration saves information without reserving inventory or automatically placing an order. Registration closes no later than the event start.
- Check-in is allowed from 15 minutes before the start until 15 minutes after it, or the event end if earlier. Duplicate check-in is rejected.
- Cancellation updates bookings, inventory, tickets and notifications transactionally. Checked-in bookings cannot be individually cancelled; event cancellation preserves check-in history while voiding tickets.
- Ended published events cannot be reopened by changing their dates. Use **Copy to new draft**; historical orders and check-ins stay with the original event.
- JWTs last one hour. The frontend handles expiry/401 and checks changed roles on focus, every 30 seconds and after 403. Backend role revocation takes effect on subsequent requests; new permissions require a fresh login. Actual role changes create inbox notifications.
- New registration/bootstrap passwords contain **8–16 ASCII letters or digits**. Existing passwords remain usable at login. The last active administrator is protected against concurrent removal.

## Architecture

```text
backend/             Spring Boot API, domain modules, Flyway migrations and tests
frontend/            React/TypeScript application and component tests
deployment/          Local/cloud Compose, PostgreSQL image and proxy examples
scripts/             Lint, security, image verification and deployment automation
.github/workflows/   CI quality checks and staging CD
docs/                Design decisions, API contracts, testing and presentation evidence
```

The backend is a modular monolith with `identity`, `event`, `ticketing`, `booking`, `checkin`, `notification` and `reporting`. Check-in and reporting have documented JDBC access to shared tables. See the [architecture decision](docs/adr/0001-modular-monolith.md).

Design patterns map to concrete code: **Observer** for synchronous transactional business events, **State** for ticket lifecycle transitions, and **Strategy** for booking-information validation. Pricing Strategy is deferred with paid tickets; booking-information validation provides the current Strategy demonstration. A shared injected `Clock` supplies business boundaries, JWT validation, notifications and JPA audit timestamps. See the [pattern and presentation evidence](docs/presentation.zh-CN.md).

## Quick start

Use JDK **21**, Maven **3.9+**, Node.js **22**, npm **10+**, Docker and Compose v2. Cloud overlays require Compose **2.24.4+**. PostgreSQL **17** runs in Docker.

Run commands from the repository root. On first setup, create `.env`; if it already exists, edit it instead of overwriting it:

```bash
cp .env.example .env
```

Set your own `POSTGRES_PASSWORD`, matching `DB_PASSWORD`, and a random `SEMS_JWT_SECRET` of at least 32 characters. Keep `DB_URL`/`DB_USERNAME` aligned with `POSTGRES_DB`/`POSTGRES_USER`. Default JWT TTL is `PT1H`. `.env` and `.secrets/` are ignored by Git.

### Run everything with Docker

```bash
docker compose --env-file .env -f deployment/compose.yaml up --build
```

Open [localhost:3000](http://localhost:3000). Backend health: [localhost:8080/actuator/health](http://localhost:8080/actuator/health). Stop while retaining the database:

```bash
docker compose --env-file .env -f deployment/compose.yaml down
```

### Local development

Use this instead of the full Docker application to avoid port conflicts. Start Docker and the database:

```bash
docker compose --env-file .env -f deployment/compose.yaml up -d postgres
```

Start the backend in Bash/Zsh (or WSL):

```bash
set -a
source .env
set +a
mvn -f backend/pom.xml spring-boot:run
```

In another terminal:

```bash
npm --prefix frontend ci
npm --prefix frontend run dev
```

Open [localhost:5173](http://localhost:5173). Vite proxies `/api` and `/actuator` to port `8080`; local PostgreSQL uses `5432`. A fresh database contains role definitions but no demo events or accounts.

### First administrator

Before backend startup, set `SEMS_BOOTSTRAP_ADMIN_USERNAME`, `SEMS_BOOTSTRAP_ADMIN_EMAIL` and your own **8–16 character alphanumeric** `SEMS_BOOTSTRAP_ADMIN_PASSWORD` in private `.env`. Blank email/password disables initialization. An existing account with that email receives `ADMIN`; otherwise a new account is created. Clear the bootstrap password after initialization.

The account-password rule does not limit database passwords or JWT signing secrets.

## Tests and CI/CD

Run the checks relevant to a change from the repository root:

```bash
npm --prefix frontend ci
bash scripts/ci/lint.sh
mvn -f backend/pom.xml verify
RUN_CONTAINER_TESTS=true mvn -f backend/pom.xml verify
npm --prefix frontend run test:coverage
npm --prefix frontend run build
```

Docker must be running for the lint script's actionlint container and PostgreSQL integration tests. Without `RUN_CONTAINER_TESTS=true`, container tests are skipped. `build` includes frontend type checking. Coverage reports: `backend/target/site/jacoco/index.html` and `frontend/coverage/index.html`; percentages are reported without a minimum coverage gate. Current regression counts and coverage: [testing record](docs/testing.zh-CN.md).

| Stage | Purpose |
|---|---|
| Lint | ESLint, Checkstyle, workflow/shell checks and release-manifest validation |
| Backend / Frontend | Unit tests, PostgreSQL integration tests, coverage and frontend build |
| Security | SAST, dependency and secret scans |
| Images | Build from tested artifacts, scan three runtime images, run isolated registration/login smoke tests |
| Deploy · Staging | Main only: publish/pull pinned digests, back up PostgreSQL, deploy and check public HTTPS |
| Security · DAST Baseline | Main only, after deployment: passive public HTTPS scan; High/Medium or scan errors fail the workflow |
| Notify · Telegram | Report results; notification failure does not invalidate the quality checks |

PRs run checks without deployment. Main publishes tested images and deploys through the GitHub `staging` environment. Reports are uploaded as Actions artifacts. Scoped security exceptions are tracked in [trivyignore.yaml](scripts/security/trivyignore.yaml); ZAP DAST baseline scripts and post-deployment workflow are included; see the [scan results and scope](docs/dast.zh-CN.md). The latest main deployment and cloud scan passed, with zero High/Medium/Low findings. See the [deployment and recovery guide](docs/deployment.md) for secrets, server layout and rollback limitations.

## Database access

For local Navicat/DBeaver connections, use `localhost:5432` and the `POSTGRES_*` values from `.env`. For cloud inspection, use an SSH tunnel and the `sems5006_viewer` read-only account; [connection settings](docs/deployment.md#database-inspection) are documented separately.

All schema changes use a new `VyyyyMMddHHmm__description.sql` file in `backend/src/main/resources/db/migration`. Never edit an applied migration. Flyway migrates on startup; Hibernate validates the schema. See the [database reference](docs/database/README.md).

## Team workflow and documentation

Create a task branch from updated `main`, run relevant checks, open a PR and obtain team review. Use **Create a merge commit**, not squash. Main merges trigger CD. Full instructions: [CONTRIBUTING.md](CONTRIBUTING.md).

- [Documentation index](docs/README.md): API, architecture and database references.
- [Deployment guide](docs/deployment.md): CI/CD, shared proxy, database access and recovery.
- [Testing record](docs/testing.zh-CN.md): current results, commands and verification limits.
- [Delivery checklist](docs/remaining-work.zh-CN.md): feature-freeze and final acceptance work.
- [Demo walkthrough](docs/sprint-4-demo.zh-CN.md) and [presentation guide](docs/presentation.zh-CN.md): the multi-role demo and course evidence.
