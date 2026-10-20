# 文档导航

先看[交付状态与剩余工作](remaining-work.zh-CN.md)。500 VU交付目标已完成；本轮500读取/混合保持30秒，p95为2.06/1.92秒、错误0。1000读取保持180秒，p95为2.94秒、错误0，通过；混合保持180秒，p95为3.51秒、错误0，延迟未通过；库存竞争p95为4.10秒，另有1次连接失败，无超卖。测试数据已清理，无健康保护中断、OOM或容器重启。详情见[最新容量报告](acceptance/capacity-2026-10-21/README.md)。云端流程、DAST、手机扫码和隔离恢复已有结果，演示/敏捷材料及最终演示准备仍待完成。

## 使用与维护

| 文档 | 用途 |
|---|---|
| [项目说明（中文）](../README.zh-CN.md) / [English](../README.md) | 环境、运行、角色与业务规则 |
| [部署维护](deployment.md) | CI/CD、共享代理、SSH数据库查看、备份与恢复边界 |
| [团队协作](../CONTRIBUTING.md) | 任务分支、团队审查、merge commit与功能冻结 |
| [接口与事务](sprint-3-api.zh-CN.md) | 当前身份、活动、票种、预订、电子票、Staff与报表契约 |
| [取消联动](sprint-4-cancellation.zh-CN.md) | 内部票券接口、锁顺序、事务及取消一致性 |
| [通知](notification-integration.zh-CN.md) | 站内类型、权限、定时与去重 |
| [数据库](database/README.md) / [架构决策](adr/0001-modular-monolith.md) | Flyway、ERD、模块化单体与当前边界例外 |

## 验证与交付

| 文档 | 用途 |
|---|---|
| [测试与覆盖](testing.zh-CN.md) | 当前测试数量/覆盖率、执行命令、安全例外及验证边界 |
| [DAST](dast.zh-CN.md) | 已通过的云端ZAP基线、运行方式和报告 |
| [10月20日云端验收](acceptance/sprint4-2026-10-20/README.md) | 多角色、二维码/核销截图、恢复校验、真实性能数据与测试清理 |
| [最新云端容量验证](acceptance/capacity-2026-10-21/README.md) | 最新部署500目标完成、1000读取通过及混合/库存限制，含请求、资源、库存和清理证明 |
| [验收证据索引](acceptance/README.md) | 历史浏览器验收与截图，保留各自日期/环境 |
| [演示指南](presentation.zh-CN.md) | 待制作材料、课程要求映射、Observer/State/Strategy与演示节奏 |
| [业务演示脚本](sprint-4-demo.zh-CN.md) | 多角色操作顺序、预期结果及签到窗口 |
| [交付清单](remaining-work.zh-CN.md) | 已完成项与唯一剩余行动清单 |

## 原始计划

[Sprint 2](sprint-2-plan.zh-CN.md)与[Sprint 3](sprint-3-plan.zh-CN.md)保留原始用户故事、负责人、验收标准和DoD，用于课程追踪，不代表当前状态。逐次开发日志不再重复维护；需要追溯时查看Git/PR历史，不删除原始验收证据。
