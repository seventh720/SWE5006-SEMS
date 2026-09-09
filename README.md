# Smart Event Management and Ticketing System

[English](README.md) | [简体中文](README.zh-CN.md)

SEMS is a modular event management and ticketing application. The current Sprint 1 implementation provides a complete authentication and authorization path: registration, password hashing, login, JWT validation, current-user lookup, role-based endpoint authorization and administrator role management.

The repository contains a React frontend, a Spring Boot modular-monolith backend, PostgreSQL database migrations, Docker Compose configuration and an initial GitHub Actions pipeline.

## Current roles and accounts

The system has **four roles**, not only user and administrator:

| Code | Identity | Intended access |
|---|---|---|
| `ATTENDEE` | Attendee | Browse events, make bookings and view own tickets in later sprints |
| `ORGANIZER` | Event Organizer | Create and manage authorized events |
| `STAFF` | Event Staff | Verify tickets and perform event check-in |
| `ADMIN` | System Administrator | Manage users, roles and system-wide administration |

A user account can have more than one role. Every public registration receives `ATTENDEE`. An administrator account is created only when `SEMS_BOOTSTRAP_ADMIN_EMAIL` and `SEMS_BOOTSTRAP_ADMIN_PASSWORD` are configured. No real user or default administrator password is committed to Git.

At repository initialization there are therefore four role definitions, but zero persistent accounts until somebody registers or the optional bootstrap administrator is enabled.

## Project structure

```text
backend/                  Spring Boot API and Flyway migrations
frontend/                 React and TypeScript web application
deployment/compose.yaml   PostgreSQL and full application runtime
docs/                     Architecture and database decisions
.github/workflows/        Continuous integration
```

The backend already reserves modules for `identity`, `event`, `ticketing`, `booking`, `attendance`, `notification` and `reporting`. Sprint 1 implements only `identity`; later modules should expose public application APIs instead of sharing repositories or JPA entities.

## Programming environment

Use these versions as the team development baseline:

| Tool | Required/recommended version | Purpose |
|---|---:|---|
| Git | 2.40 or newer | Version control and pull requests |
| Java JDK | 21 LTS | Backend compilation and runtime |
| Maven | 3.9 or newer | Backend build and tests |
| Node.js | 22 LTS | Frontend toolchain |
| npm | 10 or newer | Frontend dependency management |
| Docker Desktop/Engine | 24 or newer | PostgreSQL and container execution |
| Docker Compose | v2 or newer | Local multi-container environment |
| PostgreSQL | 17 container image | Relational database |
| Navicat | Optional | Database inspection and SQL development |

The Maven compiler targets Java 21. A newer local JDK may compile the project, but all members and CI should use JDK 21 to prevent environment differences.

Default development ports:

| Service | Port |
|---|---:|
| React development server | 5173 |
| Docker frontend | 3000 |
| Spring Boot API | 8080 |
| PostgreSQL | 5432 |

## First-time setup

Clone the repository and create a private environment file:

```bash
cp .env.example .env
```

Edit `.env` before starting. `POSTGRES_PASSWORD`, `DB_PASSWORD` and `SEMS_JWT_SECRET` must be changed. The JWT secret must contain at least 32 characters. `.env` is ignored by Git.

### Option A Run the whole system with Docker

```bash
docker compose --env-file .env -f deployment/compose.yaml up --build
```

Open:

- Frontend: `http://localhost:3000`
- Backend health: `http://localhost:8080/actuator/health`

Stop containers without deleting local database data:

```bash
docker compose --env-file .env -f deployment/compose.yaml down
```

### Option B Run services during development

Start only PostgreSQL:

```bash
docker compose --env-file .env -f deployment/compose.yaml up -d postgres
```

Load the local environment and start the backend:

```bash
set -a
source .env
set +a
cd backend
mvn spring-boot:run
```

In another terminal, start the frontend:

```bash
cd frontend
npm install
npm run dev
```

Vite proxies `/api` and `/actuator` to `http://localhost:8080`.

## Navicat connection

After PostgreSQL is running, create a PostgreSQL connection in Navicat:

```text
Host: localhost
Port: 5432
Database: value of POSTGRES_DB, normally sems
Username: value of POSTGRES_USER, normally sems_user
Password: value of POSTGRES_PASSWORD in your local .env
```

Navicat is used to inspect tables, review data and test queries. Database structure must be changed through a new file in `backend/src/main/resources/db/migration`, not only through the Navicat interface.

Flyway runs automatically when the backend starts. Hibernate uses `ddl-auto: validate`, so Java mappings are checked against the migrations but do not silently alter the database.

Migration file convention:

```text
VyyyyMMddHHmm__short_description.sql
```

Never modify an applied migration. Add a forward migration instead and commit it in the same pull request as the corresponding Java entity, test and ERD update.

## Optional bootstrap administrator

To create the first administrator, set these values only in the local `.env` or deployment secret store:

```dotenv
SEMS_BOOTSTRAP_ADMIN_USERNAME=admin
SEMS_BOOTSTRAP_ADMIN_EMAIL=admin@example.com
SEMS_BOOTSTRAP_ADMIN_PASSWORD=replace-with-a-strong-password
```

The account is created on backend startup if the email does not exist. If it already exists, the bootstrap configuration grants it the `ADMIN` role. Clear the bootstrap password after the first controlled deployment.

## API implemented in Sprint 1

| Method | Endpoint | Access |
|---|---|---|
| `POST` | `/api/v1/auth/register` | Public |
| `POST` | `/api/v1/auth/login` | Public |
| `GET` | `/api/v1/auth/me` | Authenticated |
| `GET` | `/api/v1/admin/users` | `ADMIN` |
| `PUT` | `/api/v1/admin/users/{userId}/roles` | `ADMIN` |
| `GET` | `/api/v1/demo/organizer` | `ORGANIZER` or `ADMIN` |
| `GET` | `/api/v1/demo/staff` | `STAFF` or `ADMIN` |

Example registration:

```bash
curl -i http://localhost:8080/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","email":"alice@example.com","password":"Password123"}'
```

Example login:

```bash
curl -s http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"alice@example.com","password":"Password123"}'
```

The returned token is sent on protected requests as `Authorization: Bearer <token>`. The browser stores the short-lived token in `sessionStorage`, so closing the tab/session removes it. Backend authorization remains the security source of truth.

## Tests and quality checks

Backend unit tests:

```bash
cd backend
mvn test
```

PostgreSQL integration tests require a running Docker daemon and are enabled explicitly:

```bash
cd backend
RUN_CONTAINER_TESTS=true mvn test
```

Frontend checks:

```bash
cd frontend
npm run typecheck
npm test
npm run build
```

`mvn verify` writes the backend JaCoCo report to `backend/target/site/jacoco/index.html`. Backend line coverage is reported for visibility and does not currently block a build.

GitHub Actions runs backend unit tests, PostgreSQL integration tests and frontend checks. Automatic dependency-upgrade pull requests are disabled; dependency versions are updated manually when the team decides an upgrade is needed.

### Telegram CI notifications

The final CI job uses `appleboy/telegram-action@v1.0.1` to send the backend result and line coverage, PostgreSQL integration-test result, frontend result, commit information and a link to the GitHub Actions run. If Telegram is unavailable, the notification step does not change the build result.

1. Create a bot with `@BotFather` in Telegram and keep its token private.
2. Send the bot a message, or add it to the target group and send a message there.
3. Call `https://api.telegram.org/bot<TOKEN>/getUpdates` and read `message.chat.id` to obtain the chat ID. Group chat IDs are normally negative numbers.
4. In GitHub, open **Settings > Secrets and variables > Actions** and add these repository secrets:
   - `TELEGRAM_TOKEN`: token issued by BotFather.
   - `TELEGRAM_TO`: target private-chat or group-chat ID.

Never add either Telegram value to `.env`, workflow YAML, source code or Git history. When the two secrets are absent, CI prints a skip message and continues normally.

## Git workflow

Use short-lived branches such as:

```text
feature/us-01-registration
feature/us-02-jwt-login
feature/us-03-rbac
```

Protect `main`, require a passing CI run and at least one review before merging. A database-changing pull request should contain the Flyway migration, corresponding application code, automated tests and database documentation together.
