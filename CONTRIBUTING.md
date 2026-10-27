# Contributing to SEMS

Set up the project using the [README](README.md#quick-start). Use a short-lived task branch, open a PR against `main`, obtain one other reviewer's approval and merge with **Create a merge commit** after required checks pass. These are repository conventions; documentation does not imply that a GitHub ruleset enforces them.

## Checks

Run relevant checks from the repository root:

```bash
bash scripts/ci/lint.sh
RUN_CONTAINER_TESTS=true mvn -f backend/pom.xml verify
npm --prefix frontend run test:coverage
npm --prefix frontend run build
```

Docker is required for container checks. Without `RUN_CONTAINER_TESTS=true`, database integration tests are skipped. See [testing](docs/testing.zh-CN.md) for detailed commands and coverage limits.

Include the change, validation results and any setup impact in the PR. Database changes require a new Flyway migration and updated schema documentation; never edit an applied migration. Keep credentials, database dumps and local generated reports out of Git.

PRs run lint, backend unit/integration tests, frontend tests/build, security checks and image scanning/smoke tests. Main additionally publishes and deploys tested artifacts, then runs HTTPS and DAST checks. Telegram notification is not a required quality gate. See [deployment](docs/deployment.md).

The application remains in feature freeze. Focus changes on reproducible defects, security/deployment issues and accurate documentation; defer new business scope and broad refactors.
