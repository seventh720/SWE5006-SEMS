# Contributing to SEMS

[Project README](README.md) | [项目说明](README.zh-CN.md)


Use this sequence for every task: **update `main` → create your own task branch → develop and test locally → push → open a PR → request 1 reviewer → address feedback and pass checks → merge → update local `main`**. All changes go through a PR; do not push directly to `main`.

### Create a branch for each task

Complete the [README environment setup](README.md#quick-start) first. Run Git commands from the repository root. Commit or stash unfinished changes (`git stash`) before switching branches so your working tree is clean.

```bash
git switch main
git pull --ff-only origin main
git switch -c event-list-alice
```

A branch name only needs to indicate the general task and developer name, such as `event-list-alice` or `login-bob`; no fixed prefix or task number is required. Replace the example with your own task and name. Each member uses a separate short-lived branch for each task.

### Develop, test and push

Use the [local development setup](README.md#local-development) for daily development. Manually verify the affected behavior and run the relevant checks. Start each command block from the repository root:

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

The PR author or designated merger uses **Create a merge commit** when:

- The PR is ready, targets `main` and has no merge conflicts.
- Lint, Backend · Unit Tests, Backend · Integration Tests, Frontend · Tests & Build, Security · SAST, Dependencies & Secrets, and Images · Scan & Smoke Test all pass.
- 1 other team member has approved, feedback is addressed and all review conversations are resolved.

Delete the merged task branch on GitHub and update your local checkout:

```bash
git switch main
git pull --ff-only origin main
git fetch --prune
```

Start the next task on a new branch from the updated `main`. Preserve individual commits by using a merge commit; do not squash or force-push shared history.

### Repository administrator setup

The maintainer deferred enabling rulesets on 20 October; this section is future setup guidance, not a current delivery blocker. These are team conventions; documentation does not enable GitHub enforcement. Configure branch protection or a ruleset for `main` to require PRs, at least 1 approval, renewed approval after new changes, resolved conversations, and the six quality checks listed above. Select their current names from a completed workflow run. Block force pushes and deletion of `main`.

Telegram notification is not a required quality check for merging.


## Changes during feature freeze

The current application is a feature-freeze candidate. Until presentation acceptance is complete,
keep changes focused on reproducible defects, security findings, deployment reliability, tests and
accurate documentation. Defer new business features and broad refactors. A final presentation tag
should identify the exact tested commit after cloud walkthroughs, DAST, recovery and device checks.
See the [delivery checklist](docs/remaining-work.zh-CN.md).
