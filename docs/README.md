# 文档导航

先看[交付状态与剩余工作](remaining-work.zh-CN.md)：本组pre已确认**10月27日10:30 SGT**，按教师通知采用**40分钟展示加5分钟Q&A**。应用验收、500 VU目标和两份演示视频已完成，当前优先slides、敏捷/贡献材料、脚本化质量属性展示、现场活动窗口及彩排。完整final report在pre后补齐；[课程原文核对](presentation.zh-CN.md#2026年10月21日原文核对)已区分公告、通用指南、报告要求和历史素材。1000 VU是额外探索，结论和数值统一见[最新容量报告](acceptance/capacity-2026-10-21/README.md)，不列为本轮必做项。

准备pre时，按**交付清单 → 演示指南 → 业务脚本 → 测试与验收证据**阅读。项目运行从根目录README开始；具体接口与数据结构以实现文档及Flyway迁移为准。

## 使用与维护

| 文档 | 用途 |
|---|---|
| [项目说明（中文）](../README.zh-CN.md) / [English](../README.md) | 环境、运行、角色与业务规则 |
| [部署维护](deployment.md) | CI/CD、共享代理、SSH数据库查看、备份与恢复边界 |
| [团队协作](../CONTRIBUTING.md) | 任务分支、团队审查、merge commit与功能冻结 |
| [接口与事务](sprint-3-api.zh-CN.md) | 当前身份、活动、票种、预订、电子票、Staff与报表契约 |
| [取消联动](sprint-4-cancellation.zh-CN.md) | 内部票券接口、锁顺序、事务及取消一致性 |
| [通知](notification-integration.zh-CN.md) | 站内类型、权限、定时与去重 |
| [数据库](database/README.md) / [架构决策](adr/0001-modular-monolith.md) | Flyway、当前完整核心ERD、字段约束、模块化单体与当前边界例外 |

## 验证与交付

| 文档 | 用途 |
|---|---|
| [测试与覆盖](testing.zh-CN.md) | 当前测试数量/覆盖率、执行命令、安全例外及验证边界 |
| [DAST](dast.zh-CN.md) | 已通过的云端ZAP基线、运行方式和报告 |
| [10月20日云端验收](acceptance/sprint4-2026-10-20/README.md) | 多角色、二维码/核销截图、恢复校验、真实性能数据与测试清理 |
| [最新云端容量验证](acceptance/capacity-2026-10-21/README.md) | 容量实测版本的500目标完成、1000读取通过及混合/库存限制，含请求、资源、库存和清理证明 |
| [验收证据索引](acceptance/README.md) | 历史浏览器验收与截图，保留各自日期/环境 |
| [演示指南](presentation.zh-CN.md) | Slides内容结构、课程要求映射、Observer/State/Strategy、视频入口与演示节奏 |
| [业务演示脚本](sprint-4-demo.zh-CN.md) | 多角色操作顺序、预期结果及签到窗口 |
| [交付清单](remaining-work.zh-CN.md) | 已完成项与唯一剩余行动清单 |

## 原始计划

[Sprint 2](sprint-2-plan.zh-CN.md)与[Sprint 3](sprint-3-plan.zh-CN.md)保留原始用户故事、负责人、验收标准和DoD，用于课程追踪，不代表当前状态。逐次开发日志不再重复维护；需要追溯时查看Git/PR历史，不删除原始验收证据。

## 本地演示材料与报告

下列目录被Git忽略，克隆仓库不会获得这些文件。演示文件名和配套说明见[交付清单](remaining-work.zh-CN.md#已备材料与使用边界)。

| 目录 | 内容 |
|---|---|
| `presentation-recordings/2026-10-21/cicd/` | CI/CD英文及中英字幕成片、讲解稿、字幕与章节 |
| `presentation-recordings/2026-10-21/system-demo-v3/` | 新版系统Demo成片、讲解稿、字幕、章节与验证摘要 |
| `presentation-recordings/2026-10-21/ui-review/` | PR #48本地响应式检查截图，使用隔离测试数据 |
| `lint-reports/`、`security-reports/`、`image-reports/`、`dast-reports/`、`performance-reports/` | 原始质量/扫描/性能报告；仓库中的脱敏结论与来源见测试说明及验收索引 |

维护时只在交付清单更新行动状态，README保留摘要。验收记录描述当时版本与环境；后续上线、清理或录屏不改写历史成绩。视频版本、测试基线、压测版本分别标注，避免把旧报告当成最新部署的重新验收。
