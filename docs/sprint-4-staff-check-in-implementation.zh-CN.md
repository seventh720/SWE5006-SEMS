# Sprint 4 验票与现场签到
## 已实现
- STAFF 专属工作台，读取本人获授权的活动；按活动选择后扫码（HTTPS 浏览器，使用 jsQR 解码）或手动输入票券 UUID。
- 支持 UUID 或 sems:ticket:UUID 格式的二维码内容；与电子票模块约定使用后者，暂不支持任意 URL / 其他二维码格式。
- 先查询票券状态，再由工作人员确认核销；重复、作废、跨活动票券不能核销。
- 后端限制核销时段为开始前 15 分钟（含）至开始后 15 分钟（不含）；若活动更早结束，则以结束时间为截止，活动必须 PUBLISHED；不依赖前端时钟。
- 操作者及签到时间存入独立审计表，支持按活动查询最近 200 条；取消活动不会抹掉历史。
- 核销与记录同一事务；活动行锁先于授权检查和票券读取，避免与取消或撤权竞态。
- 组织者可通过接口授权或撤销单个活动的 STAFF 访问；角色本身不代表任何活动授权。
  HTTP 接口
- GET /api/v1/staff/events：当前 STAFF 已分配活动。
- PUT /api/v1/staff/events/{eventId}/assignments/{staffId}：该活动组织者授权 STAFF。
- DELETE /api/v1/staff/events/{eventId}/assignments/{staffId}：撤权。
- POST /api/v1/staff/events/{eventId}/verify：{"code":"sems:ticket:<UUID>"}，仅预检不修改状态。
- POST /api/v1/staff/events/{eventId}/check-ins：相同请求体，原子核销并记操作员。
- GET /api/v1/staff/events/{eventId}/check-ins：签到记录。 
## 联调前注意
1. 电子票模块须将票券 ID 作为二维码载荷（sems:ticket:<ticketId>），并确保确认订单已出票。当前仓库尚未自动出票，未出票订单无法验票。
2. 工作人员授权模块后续接入时，应复用 event_staff_assignments 表及授权 API，避免建立第二套授权体系。授权需要组织者拥有活动，且目标账号具备 STAFF 角色。
3. 摄像头扫码要求 HTTPS 和摄像头权限，不再依赖 BarcodeDetector。优先后置摄像头，支持切换设备、权限失败重试及手动启动预览。手机通过局域网 HTTP IP 访问不能调用摄像头。
4. 签到窗口为 [startsAt − 15min, min(startsAt + 15min, endsAt))，验票和核销均由后端执行该规则。
5. 后端需要 PostgreSQL/Flyway，迁移自动建立活动授权表和签到审计表。

## 移动端扫码补充
- 参考 SWE5001 的 TicketCamera：设置 muted/defaultMuted/playsInline；自动播放被阻止时显示 Start preview，不重复申请摄像头权限。
- 根据视频实际尺寸调整预览比例，横竖屏切换时更新；完整显示画面，不固定宽高导致黑边或裁切二维码。
- 关闭、切换活动/摄像头、切后台、识别成功和卸载时停止摄像头；延迟返回的授权也会被释放。
- 扫码只填入票码，仍需 Verify ticket → Confirm check-in，保持人工确认流程。
- 切换票码后丢弃旧验票响应；活动时间窗口每秒更新，签到窗口结束后关闭扫码。
- 自动化覆盖摄像头权限、播放恢复、横竖屏比例、释放资源、旧验票响应和活动开始边界。真机摄像头硬件、实际画面和 HTTPS 部署仍需手机验收。
