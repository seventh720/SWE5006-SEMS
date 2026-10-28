# 文档导航

项目使用从[中文 README](../README.zh-CN.md)或[English README](../README.md)开始。这里保留实现与维护参考、测试方法和可追溯的验收证据。

## 实现与维护

| 文档 | 内容 |
|---|---|
| [部署维护](deployment.md) | CI/CD、代理、数据库访问、备份与恢复 |
| [接口与事务](api.zh-CN.md) | 身份、活动、票种、预订、电子票、Staff 及报表契约 |
| [取消联动](cancellation.zh-CN.md) | 锁顺序、事务与取消一致性 |
| [通知](notification-integration.zh-CN.md) | 类型、权限、定时与去重 |
| [设计模式](design-patterns.zh-CN.md) | 五个设计问题对应的实现、边界和测试 |
| [数据库](database/README.md) / [架构决策](adr/0001-modular-monolith.md) | ERD、字段约束、迁移及模块边界 |
| [贡献指南](../CONTRIBUTING.md) | 开发检查与 PR 约定 |

## 验证与交付证据

| 证据 | 内容 |
|---|---|
| [测试与覆盖](testing.zh-CN.md) / [DAST](dast.zh-CN.md) | 测试结果、执行命令、扫描范围及验证边界 |
| [验收证据索引](acceptance/README.md) | 有日期和版本的浏览器截图、业务及恢复记录 |
| [10月27日验证产物](acceptance/report-evidence-2026-10-27/README.md) | CI 运行、结果、原始产物摘要及 SHA-256 |
| [容量验证](acceptance/capacity-2026-10-21/README.md) | 500/1000 VU 指标、资源、库存和清理证据 |
| [最终演示文稿](../outputs/presentation/SEMS_Presentation_Unified_20261025.pptx) | 已交付的演示材料；测试结果以对应日期的原始记录为准 |

历史验收保留原日期、版本与结果，目录中的 Sprint 名称标识当时的验收批次。过程计划、待办、排练讲稿和旧报告素材不再随当前项目维护。正式报告及原始证据包在本地 `outputs/final-report/` 保存；该目录被 Git 忽略。仓库保留脱敏证据摘要和选定截图，原始质量报告及录像仍分别保存在本地忽略目录。
