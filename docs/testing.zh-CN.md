# 测试与覆盖情况

核验日期：2026-10-21（SGT）。覆盖率基线`270c2b8`对应[main运行39311200552](https://github.com/seventh720/SWE5006-SEMS/actions/runs/39311200552)；最新部署版本`037720b`的[main质量检查、部署及DAST](https://github.com/seventh720/SWE5006-SEMS/actions/runs/39475238673)全部成功。原始验收保留在[验收目录](acceptance/README.md)，开发历史可查Git。

## 当前结果

| 检查 | 实际结果与来源 |
|---|---|
| 后端单元+PostgreSQL集成 | 211项；0失败、0错误、0跳过；`backend-combined-coverage`内Surefire XML |
| 后端完整行/分支覆盖率 | 1326/1402（94.58%）/428/524（81.68%）；同次JaCoCo根计数 |
| 前端组件/逻辑 | 244项、32个测试文件；类型检查及生产构建通过 |
| 前端行/分支覆盖率 | 1332/1436（92.75%）/1722/2063（83.47%）；`frontend-coverage/coverage-summary.json` |
| Lint | ESLint、Checkstyle、actionlint、shell语法、发布manifest校验、5项DAST门槛测试通过 |
| 静态安全 | SAST、依赖/密钥检查及三镜像扫描通过；保留下述Spring适用性例外 |
| 部署与DAST | main部署、HTTPS检查、公开页面/API被动扫描及报告清理全部成功；高/中/低危0，信息4 |
| 云端业务/恢复/性能 | 多角色闭环、非空业务备份隔离恢复、1/5/10 VU读取基线完成；见[10月20日实测](acceptance/sprint4-2026-10-20/README.md) |
| 云端容量验证 | [最新报告](acceptance/capacity-2026-10-21/README.md)：500 VU交付目标已完成；本轮500读取/混合保持30秒，p95为2.06/1.92秒、错误0。1000读取保持180秒，p95为2.94秒、错误0，通过；混合保持180秒，p95为3.51秒、错误0，延迟未通过；库存竞争p95为4.10秒，另有1次连接失败，无超卖。测试数据已清理，无健康保护中断、OOM或容器重启 |
| 手机扫码 | 维护者确认通过；设备/浏览器未提供，未扩大为全移动端兼容性结论 |

后端单元与完整回归的覆盖率分别计算，不相加。前端统计生产`src`代码，保留未执行入口/路由在分母中。当前没有最低覆盖率百分比门槛。

基于`main 7b46634`的[本轮鉴权查询优化](acceptance/auth-lookup-2026-10-20/README.md)在本地通过216项后端验证（含1项显式启用的可选查询对比），新增4项功能/窄SQL回归。可选对比默认跳过，常规完整回归为215项功能测试。上表覆盖率仍属于已注明的历史基线；本地查询收益不能替代云端容量复测。

当前500 VU目标已通过云端复测。4项控制器离线测试验证档位上限与重试幂等隔离，8项监控/健康测试覆盖断线停测、自定义阈值传递、非法配置拒绝、旧2秒规则复现、新5秒规则及连续计数恢复、失败健康响应停测，均接入lint。监控默认健康停止线5秒、探针超时6秒，已用于最新完整1000分级实测；业务p95评分仍为3秒。

代理连接复用修正另有[真实nginx回归](../scripts/cd/test-proxy-keepalive.py)：20个请求使用1条上游连接，并逐请求验证鉴权及路径/来源转发，已接入镜像冒烟。另以单worker、96 MiB及继承1024文件软上限保持并复用1200条前端连接：旧配置重现EOF，新连接/文件额度配置通过；该回归已接入镜像冒烟，修正已部署，云端1000读取通过，混合/库存仍有延迟与连接可靠性限制。云端成绩见最新容量报告。

## 主要覆盖

| 范围 | 已覆盖行为 | 主要测试 |
|---|---|---|
| 身份与会话 | 注册/密码格式、旧密码登录、用户名唯一/并发、JWT1小时边界、角色撤销、最后有效管理员并发保护、角色通知回滚 | `AuthenticationFlowIntegrationTest`、`AuthServiceTest`、`TokenServiceTest`、前端AuthContext/AdminUsersPage |
| 活动与票种 | 归属、版本冲突、发布前票种、已开始/结束编辑限制、容量/配额、首次预订后冻结、复制不带订单、组合筛选 | `EventBrowseIntegrationTest`、`EventTest`、`TicketTypeIntegrationTest` |
| 订单与资料 | 幂等及同键冲突、最后一票竞争、资料校验不占库存、预约不占名额/不自动下单、SGT报名边界、快照和隐私 | `BookingIntegrationTest`、`BookingServiceTest`、`BookingInfoValidatorTest` |
| 出票与核销 | 自动出票/补票幂等、通知或出票失败整体回滚、角色/活动授权、重复核销、时段与操作审计、取消竞态 | `BookingIntegrationTest`、`StaffCheckInIntegrationTest`、`IssuedTicketTest` |
| 通知与报表 | 本人隔离/已读、提醒窗口和去重、授权通知事务、票数去重、跨页总计、组织者/管理员范围 | 上述集成测试、`ReportingIntegrationTest`、前端Notifications/Reports |
| QR与相机 | 真实qrcode→jsQR往返、取消/已核销隐藏QR、权限与播放错误、相机切换/释放资源、旧验票响应隔离 | `ticketQr.test.ts`、`MyTicketsPage.test.tsx`、`TicketCamera.test.tsx`、StaffPage交互测试 |

组件测试通常模拟API；真实PostgreSQL测试与云端验收分别记录，不将mock等同于完整浏览器E2E。三种模式的代码与测试关系见[演示指南](presentation.zh-CN.md)。

## 执行方法

从仓库根目录运行；Docker须启动，完整后端验证使用JDK21。

```bash
bash scripts/ci/lint.sh
mvn -f backend/pom.xml clean verify
RUN_CONTAINER_TESTS=true mvn -f backend/pom.xml clean verify
npm --prefix frontend run test:coverage
npm --prefix frontend run build
```

未设置`RUN_CONTAINER_TESTS=true`会跳过容器集成测试。`build`已包含前端类型检查。

本地报告：`backend/target/site/jacoco/index.html`、`backend/target/surefire-reports/`、`frontend/coverage/index.html`，均不提交Git。CI上传`backend-unit-coverage`、`backend-combined-coverage`、`frontend-coverage`；缺失覆盖率报告显示N/A，不伪报0%。

## 安全扫描与报告

- ESLint零warning；Checkstyle检查基础Java规则，不代表全面格式化审查。
- Semgrep 1.181.0扫描Java/TypeScript，发现或解析失败阻断；registry规则可更新。
- Trivy 0.75.0扫描源码/锁文件密钥及依赖、实际受测JAR与运行镜像；未豁免HIGH/CRITICAL阻断，无全局ignore-unfixed。
- 静态扫描仅复制Git范围源码和明确构建物，不挂载本机`.env`、私钥或node_modules；不扫描Git历史。
- 本地构建JAR后可运行`bash scripts/security/scan.sh`；镜像/冒烟步骤见[部署维护](deployment.md)。ZAP执行与基线限制见[DAST](dast.zh-CN.md)。

Actions报告包括`lint-report`、`sast-report`、`dependency-secret-reports`、`image-security-reports`和`dast-report`。安全报告保留7天；演示所需原报告已下载在本机忽略目录，仍需随离线演示材料妥善备份。

Spring MVC的两条限时例外不代表依赖已修复：

| CVE | 当前范围不适用的依据 |
|---|---|
| CVE-2026-47884 | 后端REST JSON，无XsltView/隐式服务端视图 |
| CVE-2026-47890 | 无SSE视图片段，通知使用JSON轮询 |

配置仅匹配`spring-webmvc@6.2.19`，2026-11-17到期，见[trivyignore.yaml](../scripts/security/trivyignore.yaml)。引入相关架构或升级版本时重新复核；`check-spring-scope.py`的关键词检查不能替代人工判断。原公告：[XSLT](https://spring.io/security/cve-2026-47884/)、[SSE](https://spring.io/security/cve-2026-47890/)。

## 验证边界

容量报告分别判断读取、混合写入和库存竞争，记录整体及逐接口p95和真实错误。VU表示带思考时间的活跃虚拟用户；结果不代表等量RPS、真实浏览器或长稳态，也未测出最大容量。恢复验证到隔离数据库级，不包含整机重建、异地灾备或自动回滚。完整浏览器E2E尚未进入CI，多浏览器/设备兼容性也未穷尽；演示应如实说明这些限制。

当前剩余交付项只维护在[交付清单](remaining-work.zh-CN.md)。
