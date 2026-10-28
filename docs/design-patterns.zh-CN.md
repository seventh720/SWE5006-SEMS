# 设计模式与实现依据

以下五个设计问题对应当前实现。静态结构与动态行为的完整图解见正式报告；这里保留代码入口、关键决策和可复验的测试依据。

| 设计问题 | 负责人 | 模式 | 实现入口 |
|---|---|---|---|
| DP01 活动取消协作 | Lu Junyi | Observer | `EventManagementService`、`EventCancellationListener`、`BookingCancellationListener` |
| DP02 可替换凭据校验 | Hu Jia | Strategy | `AuthService`、`PasswordEncoder`、`SecurityConfiguration` |
| DP03 角色变更通知分离 | Chai Muyang | Observer | `UserAdministrationService`、`UserRolesChanged`、`UserRolesChangedListener` |
| DP04 报名资料校验 | Huang Haoyang | Strategy | `BookingInfoValidator`、`BookingFieldValidationStrategy` 及三个字段策略 |
| DP05 票券生命周期行为 | Luo Runzhou | State | `IssuedTicket`、`TicketState`、`ValidTicketState`、`CheckedInTicketState`、`VoidTicketState` |

## 活动取消

`EventManagementService` 发布 `EventCancelled`，订单监听器取消确认订单并发布 `BookingCancelled`，票券和通知监听器处理后续变化。服务无须逐个调用这些后续模块。同步监听器使用原业务事务；异常回滚活动、订单、库存、票券及通知。活动行锁和模块公共接口负责并发与边界，Observer 本身不提供这些保证。

实现见 [event](../backend/src/main/java/com/team10/sems/event/) 和 [booking](../backend/src/main/java/com/team10/sems/booking/)。`BookingIntegrationTest` 覆盖取消竞态、失败回滚和无重复库存释放；[取消契约](cancellation.zh-CN.md)记录锁顺序。

## 凭据校验

`AuthService` 注入 Spring Security 的 `PasswordEncoder`。`SecurityConfiguration` 提供强度 12 的 `BCryptPasswordEncoder`；注册调用 `encode`，登录调用 `matches`，校验成功后才签发会话令牌。服务依赖算法接口，替换实现时仍需处理已有密码哈希的兼容性。

实现见 [AuthService](../backend/src/main/java/com/team10/sems/identity/internal/application/AuthService.java)；`AuthServiceTest` 和 `AuthenticationFlowIntegrationTest` 验证注册、拒绝无效凭据及会话建立。

## 角色变更通知

`UserAdministrationService` 校验最后一位有效管理员保护，并比较角色集合；只有实际变化才发布 `UserRolesChanged`。`UserRolesChangedListener` 创建通知，写入失败会使角色修改回滚，无变化不会重复通知。后续请求使用 JWT 角色与数据库当前角色的交集执行授权。

实现见 [identity](../backend/src/main/java/com/team10/sems/identity/) 和 [notification](../backend/src/main/java/com/team10/sems/notification/)。`AuthenticationFlowIntegrationTest` 覆盖并发管理员保护、撤权与通知回滚。

## 报名资料校验

`BookingService` 与 `PreRegistrationService` 共享 `BookingInfoValidator`。公共规则先拒绝缺失的必填资料和未要求的个人资料，再按字段委托 Text、Email 或 Phone 策略。普通文本最多 100 字符，自定义回答最多 500 字符；邮箱复用 Jakarta 验证，电话复用 5–30 字符的既有规则。HTTP Bean Validation 保留，直接服务调用也会执行策略校验。

实现见 [booking application](../backend/src/main/java/com/team10/sems/booking/internal/application/)。`BookingInfoValidatorTest` 验证算法及边界；`BookingServiceTest` 验证非 HTTP 调用提交错误邮箱时不占库存。原定价策略随付费功能延期，本期实现的是资料校验策略。

## 票券状态

`IssuedTicket` 保留数据库状态码，并由 `TicketState.fromCode` 选择无状态的具体 State。`VALID` 可签到或作废；`CHECKED_IN` 拒绝再次签到，但活动取消可以作废并保留核销历史；`VOID` 拒绝签到，重复作废保留首次作废时间。角色、活动分配、签到时段、锁和事务仍由服务负责。

实现见 [ticketing domain](../backend/src/main/java/com/team10/sems/ticketing/internal/domain/)。`IssuedTicketTest` 覆盖状态转换矩阵；`BookingIntegrationTest` 与 `StaffCheckInIntegrationTest` 覆盖并发取消、授权、时段和审计。

各项已执行结果、覆盖率及测量版本见[测试记录](testing.zh-CN.md)，原始产物摘要见[验证证据](acceptance/report-evidence-2026-10-27/README.md)。
