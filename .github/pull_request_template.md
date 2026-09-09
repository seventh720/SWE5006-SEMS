## 任务 / Task

关联 Issue / User Story：

## 变更内容 / Changes

简述解决的问题与修改后的行为；界面变更请附截图。
Describe the problem and resulting behavior; attach screenshots for UI changes.

## 验证 / Validation

填写实际运行的命令、结果和手动验证步骤；未运行的检查说明原因。
List commands run, results and manual checks; explain any checks not run.

## 数据库与配置 / Database and configuration

填写新增迁移、环境变量或组员需要执行的步骤；无则写 N/A。不要填写密钥。
List new migrations, environment variables or teammate setup steps; use N/A if none. Do not include secrets.

## 合并前检查 / Before merging

- [ ] 目标分支为 `main`，本次 PR 只包含当前任务相关变更。 / Targets `main` and contains only task-related changes.
- [ ] 已完成相关测试、文档更新；未提交 `.env` 或密钥。 / Relevant tests and docs are complete; no `.env` or secrets committed.
- [ ] 已邀请 1 名其他组员 review。 / Requested 1 other team member as reviewer.
- [ ] 已有 1 名其他组员批准，且批准覆盖最新代码。 / 1 approval from another team member, covering the latest code.
- [ ] `backend`、`backend-integration`、`frontend` CI 全部通过。 / All three CI checks pass.
- [ ] 审查意见已处理，讨论已解决，无合并冲突。 / Feedback addressed, conversations resolved and no merge conflicts.

满足以上条件后使用 Squash and merge，并删除远端任务分支。
Use Squash and merge once these conditions are met, then delete the remote task branch.
