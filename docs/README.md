# 文档导航

项目使用从[中文README](../README.zh-CN.md)或[English README](../README.md)开始。报告和演示从以下两个入口取材：

- [Final report素材与证据](final-report.zh-CN.md)：10月12日旧稿修订、可复用英文正文、设计模式与证据版本映射。
- [Presentation逐页准备](presentation.zh-CN.md)：英文标题、页面内容、图表数据、演示节奏及视频入口。
- [10月27日报告验证证据](acceptance/report-evidence-2026-10-27/README.md)：新CI原始产物、版本、结果与哈希；历史DAST及性能基线分别标注。

## 实现与维护

| 文档 | 内容 |
|---|---|
| [部署维护](deployment.md) | CI/CD、代理、数据库访问、备份与恢复 |
| [接口与事务](sprint-3-api.zh-CN.md) | 身份、活动、票种、预订、电子票、Staff及报表契约 |
| [取消联动](sprint-4-cancellation.zh-CN.md) | 锁顺序、事务与取消一致性 |
| [通知](notification-integration.zh-CN.md) | 类型、权限、定时与去重 |
| [数据库](database/README.md) / [架构决策](adr/0001-modular-monolith.md) | ERD、字段约束、迁移及模块边界 |
| [贡献指南](../CONTRIBUTING.md) | 开发检查与PR约定 |

## 验证与演示

| 文档 | 内容 |
|---|---|
| [测试与覆盖](testing.zh-CN.md) / [DAST](dast.zh-CN.md) | 测试结果、命令、扫描及验证边界 |
| [验收证据索引](acceptance/README.md) | 历史浏览器截图、业务及恢复记录 |
| [容量验证](acceptance/capacity-2026-10-21/README.md) | 500/1000 VU指标、模型、资源、库存和清理证据 |
| [业务演示脚本](sprint-4-demo.zh-CN.md) | 多角色操作、预期结果及签到窗口 |
| [交付材料入口](remaining-work.zh-CN.md) | 尚需整合的材料及演示数据边界 |
| [Sprint 2历史需求](sprint-2-plan.zh-CN.md) / [Sprint 3历史需求](sprint-3-plan.zh-CN.md) | 用户故事和DoD，非当前任务分工或完成证明 |

历史证据保留原日期、版本与结果。原始质量报告及`presentation-recordings/`被Git忽略，不随克隆下载；报告与视频入口详见上述素材文档。完整旧计划可从Git历史追溯，不重复维护逐次开发日志。
