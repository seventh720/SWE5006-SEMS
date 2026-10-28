# Sprint 4 当前改动验证

以下“当前/尚未完成”均指2026-10-16当时版本，保留原始验收事实。后续完整业务已接入，见[10月20日云端验收](../sprint4-2026-10-20/README.md)；最新状态见[测试记录](../../testing.zh-CN.md)。

- 后端：`RUN_CONTAINER_TESTS=true mvn -f backend/pom.xml verify -q`，144项通过，无失败、错误或跳过。
- 前端：`npm --prefix frontend run build`、`npm --prefix frontend test`，类型检查及构建通过，181项测试通过。
- [用户名表单截图](username-form.png)：本地Chrome个人资料页布局，使用默认头像及未修改用户名时的禁用保存按钮。截图裁剪到表单，不包含报名个人资料。
- 当前浏览器会话已过期，未使用该会话进行保存、通知读取或完整业务联调；上述行为由本轮自动化测试覆盖。
- STAFF页面为框架，角色/活动分配、二维码页面及扫码HTTP接口尚未完成，不能把截图或内部事务测试当作完整签到交付。
