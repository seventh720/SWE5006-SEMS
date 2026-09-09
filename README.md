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

Use this sequence for every task: **update `main` → create your own task branch → develop and test locally → push → open a PR → request 1 reviewer → address feedback and pass checks → merge → update local `main`**. All changes go through a PR; do not push directly to `main`.

### Create a branch for each task

Complete the environment setup above first. Run Git commands from the repository root. Commit or stash unfinished changes (`git stash`) before switching branches so your working tree is clean.

```bash
git switch main
git pull --ff-only origin main
git switch -c event-list-alice
```

A branch name only needs to indicate the general task and developer name, such as `event-list-alice` or `login-bob`; no fixed prefix or task number is required. Replace the example with your own task and name. Each member uses a separate short-lived branch for each task.

### Develop, test and push

Use Option B above for daily development. Manually verify the affected behavior and run the relevant checks. Start each command block from the repository root:

```bash
cd backend
mvn verify
# For database or persistence changes, start Docker and also run:
RUN_CONTAINER_TESTS=true mvn test
```

```bash
cd frontend
npm ci
npm run typecheck
npm test
npm run build
```

A database-changing PR should include a new Flyway migration, corresponding application code, automated tests and database documentation. Never edit an applied migration.

Return to the repository root, inspect the changes and stage only files belonging to the task. Replace the example paths below:

```bash
git status
git diff
git add path/to/changed-file path/to/another-file
git diff --cached
git commit -m "feat: add event list"
git push -u origin event-list-alice
```

Do not commit `.env`, passwords, secrets or local generated files. Use descriptive commit messages such as `feat: add event list`, `fix: validate login input` or `docs: clarify local setup`.

### Open a PR and request review

1. Create a GitHub PR with **base: `main`** and **compare: your task branch**.
2. Complete the PR template with the task or issue, changes, test commands and results. Include screenshots for UI changes and teammate setup steps for database or configuration changes.
3. Request **1 other team member** under **Reviewers**, preferably someone familiar with the affected module. Use a Draft PR for unfinished work, then mark it Ready for review.
4. Merging requires **1 approval from another team member**. A comment or review request is not an approval.
5. Reviewers check task requirements, code clarity, error handling, authorization boundaries, tests and documentation.

The current CI runs when a PR is opened or updated and after pushes to `main`. Pushing a task branch without opening a PR does not trigger the current CI.

### Address feedback and merge

If `main` has advanced, merge it into your task branch with a clean working tree:

```bash
git fetch origin
git merge origin/main
```

Resolve conflicting files, stage them with `git add`, then run `git commit` to finish the merge. To cancel a conflicted merge, use `git merge --abort`. Rerun the relevant checks and `git push` after merging. Ask reviewers to review new changes so approvals cover the latest code.

The PR author or designated merger uses **Squash and merge** only when:

- The PR is ready, targets `main` and has no merge conflicts.
- The `backend`, `backend-integration` and `frontend` CI checks all pass.
- 1 other team member has approved, feedback is addressed and all review conversations are resolved.

Delete the merged task branch on GitHub and update your local checkout:

```bash
git switch main
git pull --ff-only origin main
git fetch --prune
```

Start the next task on a new branch from the updated `main`. After squash merging, `git branch -d` may refuse to delete the old local branch because its original commits are not ancestors of `main`. Keep it until you verify that the PR was merged and there are no unique changes to retain.

### Repository administrator setup

These are team conventions; documentation does not enable GitHub enforcement. Configure branch protection or a ruleset for `main` to require PRs, at least 1 approval, renewed approval after new changes, resolved conversations, and the `backend`, `backend-integration` and `frontend` status checks. Block force pushes and deletion of `main`.

Telegram notification is not a required quality check for merging.
