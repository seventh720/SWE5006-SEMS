# 测试与覆盖情况

## 2026-10-19 报名资料校验Strategy

完整后端`clean verify`通过：**208项，0失败、0错误、0跳过**；真实PostgreSQL/Testcontainers，日志`/tmp/sems-strategy-backend.log`。本轮无前端或数据库迁移。

- 新增8组非法格式/长度用例，验证文本、邮箱、电话策略与HTTP Bean Validation一致。
- 新增国际姓名、带标签邮箱、电话和文本最大长度接受用例，以及直接调用BookingService时错误邮箱不会占库存/写订单的用例。
- 原必填与禁止额外个人资料测试保留；普通预订、预登记、Observer、State及并发回归全部通过。
- `getReason()`可空警告通过`Objects.requireNonNull`断言错误原因必须存在后再检查内容解决；未屏蔽编译器警告。
- 策略结构及准确演示方式见[准备评审第12节](presentation-readiness-2026-10-19.zh-CN.md)。


## 2026-10-19 Observer与State保障

执行 `env -u DEBUG RUN_CONTAINER_TESTS=true mvn -f backend/pom.xml clean verify -q`，后端 **198项通过，0失败、0错误、0跳过**，构建成功；真实PostgreSQL/Testcontainers。日志：`/tmp/sems-patterns-backend.log`。本轮无前端修改，未重复前端回归。

- 新增6项票券领域测试：VALID签到、VALID/已签到票作废、重复作废保留首次时间、已使用/作废票拒绝签到且不改历史。
- 新增4项集成测试：确认通知失败整体回滚且同键可重试；取消通知失败保持原订单/库存/票状态；Observer必须加入发布者事务；新事务中重新加载CHECKED_IN/VOID后行为正确。
- 加强既有幂等通知用例，同时检查自动出票和作废结果；原有出票失败回滚、个人取消已签到订单拒绝、并发签到、取消竞态、HTTP授权/时段/审计用例全部通过。
- State重构不新增数据库状态、不改API；Observer保持进程内同步、事务内监听。代码、类图与演示脚本见[准备评审第11节](presentation-readiness-2026-10-19.zh-CN.md)。


## 2026-10-19 搜索、分页、Staff刷新与报表

- 后端完整188项，0失败、0错误、0跳过：真实PostgreSQL/Testcontainers；`RUN_CONTAINER_TESTS=true mvn -f backend/pom.xml verify -q`。日志`/tmp/sems-report-backend.log`。
- 前端235项通过，类型检查与生产构建通过。日志`/tmp/sems-report-frontend.log`、`/tmp/sems-report-build.log`。
- 新增用例验证用户姓名/邮箱大小写和字面检索、分页/全局账号计数、活动阶段/地点/SGT日期、报表所有者和ADMIN权限、状态过滤、跨页总计、签到聚合不放大订单数量；组件测试覆盖筛选翻页和Staff失效资料处理。
- Chrome独立测试环境实测：13用户分页/邮箱检索；全平台14票种/42确认票，本人12票种/36确认票；翻页汇总一致；地点与Ended组合筛选；Staff手动刷新。浏览器验收为人工操作，不冒称自动化E2E。
- 用户列表响应由数组变为分页对象，Dashboard改用独立summary接口；部署需同步前后端。无需新增数据库迁移。


## 2026-10-19 优先缺陷修复回归

用户要求的F1–F3已修复。执行 `env -u DEBUG RUN_CONTAINER_TESTS=true mvn -f backend/pom.xml clean verify -q`，后端 **183项通过，0失败、0错误、0跳过**。真实PostgreSQL/Testcontainers，新增11项（管理员并发用例重复3次计3项）。日志：`/tmp/sems-priority-fixes-1019/backend.log`。

- 真实签名JWT：撤销ADMIN后旧token的查询和提权写入均403；保留ATTENDEE继续访问本人订单；ORGANIZER/STAFF降权同样生效；新增角色仍需新token；停用/锁定/删除账号和非法subject返回401。
- 最后有效管理员：移除返回409；停用管理员不计入保底数量；保留ADMIN并增添角色仍成功。两名管理员持各自真实token并发自降权，均一项成功、一项409，最终1名有效管理员。
- 活动：精确开始/结束边界；未来活动正常改期但禁止倒填到当前/过去；开始后冻结start/end，地点更新仍允许；结束后拒绝修改。
- 历史完整性：真实HTTP签到后再尝试改期，订单/库存/票券/签到记录不变、无错误通知；复制已结束活动可设新时间并发布，新活动库存独立且无原订单/票券。

本轮业务修改仅在后端，未改前端行为，也未重复前端测试。前端224项及构建结果见上一轮记录。无新增数据库迁移，尚未提交/推送/部署；当前运行服务需更新构建后生效。


## 2026-10-19 presentation准备评审

基线：victor的d922107加本轮请求Hook/import及日志整理；未查询远端CI或部署。

| 最终检查 | 本轮结果 |
|---|---|
| 后端 `env -u DEBUG RUN_CONTAINER_TESTS=true mvn -f backend/pom.xml clean verify -q` | 172项，0失败、0错误、0跳过；真实PostgreSQL/Testcontainers |
| 后端完整行覆盖率 | 1185/1260，94.05% |
| 后端完整分支覆盖率 | 361/452，79.87% |
| 前端 `npm --prefix frontend run test:coverage` | 224项，30文件，全部通过 |
| 前端行/分支覆盖率 | 92.37% / 82.28% |
| 前端 `npm --prefix frontend run build` | TypeScript与生产构建通过 |
| `git diff --check` | 通过 |

测试日志在`/tmp/sems-pre-review-1019/`。最初后端执行被沙箱限制访问Docker，授权后通过；提取Hook时遗漏页面仍用到的useEffect import，检查发现后已修正，最终全部重跑通过。最终测试去除继承的DEBUG环境变量，配合测试日志级别使输出从21099行降至109行，DEBUG行从7108降为0，保留警告及错误。

临时源码副本另执行2项真实数据库探针，复现：角色撤销后旧JWT仍有ADMIN权限、最后ADMIN可移除自身角色、已结束活动可改到未来。探针成功只代表复现，不代表修复；未把这些观察断言加入正常产品测试。修复验收要求见[演示准备评审](presentation-readiness-2026-10-19.zh-CN.md)。

本轮浏览器实际检查了Staff活动选择和时段禁用、组织者编辑入口及401提示、公开标题搜索和已结束活动详情。未再次执行完整出票核销链、真机摄像头或线上部署；完整链路历史记录见下文。


## 2026-10-19 Sprint 4 功能收尾

- 后端完整回归：172项通过，0失败、0错误、0跳过，真实PostgreSQL/Testcontainers。最终执行于 /tmp/sems-s4-final-backend 的当前源码副本，避免 IDE 增量编译干扰仓库 target。
- 前端：224项通过，30个测试文件；TypeScript类型检查及生产构建通过。
- 补齐PR #26 review：核销与作废时间由可注入Clock提供，并验证精确时间戳。
- 新增验证：确认订单自动出票、本人票券归属与幂等补票、取消订单不再出票、出票失败回滚订单/库存/通知；免费下单→出票→STAFF验票/核销→统计→活动取消的真实HTTP接口链路。
- STAFF分配：仅活动所有者可查询/变更、无角色拒绝、分配/撤销去重、重新分配重新通知、通知写入失败回滚授权。
- 活动调整/取消：已分配STAFF接收通知；同时持有订单时避免重复活动通知。
- 二维码：使用真实 qrcode 生成矩阵，再以真实 jsQR 解码，确认与扫码页面载荷一致；页面测试覆盖取消/已核销隐藏二维码、失败刷新隐藏旧码。
- 2026-10-19已通过 Chrome 页面操作完成核心业务过程验收，见 [浏览器验收记录](acceptance/sprint4-2026-10-19/README.md)。截图通道不可用，保存实际 DOM 记录；手输票码核销通过。未执行物理手机摄像头、生产部署、跨设备实拍扫码或远端CI。本地测试通过不等于这些交付项已完成。执行脚本见 sprint-4-demo.zh-CN.md。
- 已更新旧测试：两个确认订单现在自动生成两张票，跨订单越权测试仍验证两张票状态不变。此前出现一次本地类加载异常；隔离源码重新构建后的完整验证已通过。


## 2026-10-16 修改用户名验证

后端完整验证：144项通过，0失败、0错误、0跳过；新增用户名修改后身份/登录保持、格式和重名拒绝、仅本人可修改及并发同名竞争测试。前端181项通过，24个测试文件，类型检查与生产构建通过；新增AuthContext及sessionStorage同步、刷新保留、重名失败保留输入和会话过期测试。本轮未执行真实浏览器验收或远端CI。

## 2026-10-16 通知模块验证

后端 `RUN_CONTAINER_TESTS=true mvn -f backend/pom.xml verify -q`：**141项通过，0失败、0错误、0跳过**，包括44项单元测试和97项PostgreSQL集成测试。新增通知列表/已读的本人隔离、请求幂等、分页、开售前5分钟边界、并发提醒去重、窗口内即时提醒、已订票/删除/取消排除及STAFF通知调用事务回滚验证；已有活动调整和取消回滚测试同时检查通知。

前端 `npm --prefix frontend run build` 及 `npm --prefix frontend test`：**178项通过，23个测试文件**，类型检查与生产构建通过。通知默认启用，相关组件使用模拟API测试；未执行本轮真实浏览器全流程或远端CI，STAFF授权链路仍待接入。本轮未重新统计覆盖率。

## 2026-10-16 Sprint 4 取消联动验证

执行 `RUN_CONTAINER_TESTS=true mvn -f backend/pom.xml verify -q`，后端 **136项通过，0失败、0错误、0跳过**（44项单元测试、92项PostgreSQL集成测试）。`BookingIntegrationTest` 由33项增加到42项；原活动取消回滚测试增加票券回滚断言。覆盖出票与取消、核销与取消竞态，重复出票/核销、取消后禁止操作、已核销订单拒绝个人取消、活动取消保留核销历史以及原有权限/库存回归。

本次没有修改前端，也未重跑前端或查询远端CI。下文覆盖率和前端170项为2026-10-09历史基线；不代表新增票券代码的覆盖率。内部接口及尚待接入的页面/授权范围见[Sprint 4取消联动](sprint-4-cancellation.zh-CN.md)。

## 2026-10-09 回归与覆盖率基线

检查日期：2026-10-09。对象：`victor` 报名时间、预约、日历、插图、头像和已发布活动编辑功能。本报告来自本地重新执行，没有查询或更新远端 CI。

## 实测结果

| 检查 | 结果 |
|---|---|
| 后端完整回归 | 127 项通过，0 失败、0 错误、0 跳过；11 个测试类 |
| 后端单元测试 / PostgreSQL 集成测试 | 44 / 83 项；集成测试使用 Testcontainers |
| 完整测试 JaCoCo 行覆盖率 | 894/941（95.0%） |
| 完整测试 JaCoCo 分支覆盖率 | 245/314（78.0%） |
| 仅单元测试行 / 分支覆盖率 | 302/941（32.1%） / 110/314（35.0%） |
| 前端回归 | **170 项通过**，21 个测试文件 |
| 前端类型检查与生产构建 | 通过 |
| 前端行覆盖率 | **92.73%**，855 / 922 行 |
| 前端分支覆盖率 | **84.16%**，1175 / 1396 分支 |

覆盖率来自2026-10-09分别执行的单元测试和完整测试 `clean verify`，避免历史执行数据混入。分母为后端生产代码，不包含前端，也不衡量尚未实现的功能。

## 业务用例覆盖

| 范围 | 已覆盖行为 | 主要测试位置 |
|---|---|---|
| 身份与权限 | 注册登录、用户名大小写唯一与并发冲突、JWT 过期/错误签发者、角色分配与访问隔离 | `AuthenticationFlowIntegrationTest`、`AuthServiceTest`、`TokenServiceTest`；前端 AuthPage、AdminUsersPage |
| 活动管理 | 私有草稿、编辑、发布、取消、搜索分页、归属、版本冲突与并发编辑、旧库迁移 | `EventBrowseIntegrationTest`（28 项）、EventTest；前端 EventPages、OrganizerPages |
| 票种与库存 | 总容量、票种配额、免费/付费限制、首次销售后冻结、角色与所属活动 | `TicketTypeIntegrationTest`（13 项）、`TicketTypeTest`（14 项）；前端票种管理和展示 |
| 正式预订 | 幂等重试、最后一票并发、取消只返还一次、活动取消与下单并发、失败事务回滚、订单归属与快照 | `BookingIntegrationTest`；前端 ReserveControl、MyOrdersPage、OrderDetailPage、OrganizerOrdersPage |
| 活动复用与隐私 | 复制活动清空库存、独立学生证/护照、自定义字段、资料隔离、订单快照、历史证件迁移 | `BookingIntegrationTest`；前端 ProfilePage、OrganizerPages、ReserveControl |
| 报名窗口与预约 | 未开售/截止拒绝正式报名、预约不占名额、重复及并发预约、开售后显式确认、取消活动状态 | `BookingIntegrationTest`（上述预订、资料和时间场景合计33项）；前端 PreRegistrationsPage、TicketTypesSection、DashboardPage |
| 日期选择 | 英文控件、分钟精度、跨月日期截断、闰年、Apply、Cancel、Esc；SGT 输入转换 | 前端 DateTimeField、drafts、OrganizerPages |

这些是功能及组件覆盖；前端请求多使用 mock，不能代替真实浏览器与后端联调。已有浏览器证据见[验收资料](acceptance/README.md)。

头像新增测试覆盖默认/加载失败回退、本人读取和保存、其他用户数据隔离、无效图片拒绝与删除；活动插图覆盖字段校验、保存、复制和公开读取。

## 建议补齐的测试

按实际风险排序，不以把覆盖率凑到100%为目标。

1. **新日历浮层的浏览器回归**：桌面/手机打开后相邻表单位置不变、视口边缘滚动、点外部关闭、键盘焦点和多个时间字段切换。已补充 Chrome 桌面展开前后表单纵坐标不变的检查；手机视口边缘、键盘焦点及多字段切换仍需进一步覆盖。
2. **预约页异常与分页**：加载/删除失败、401 重新登录、上一页/下一页、预填加载失败，以及资料填写过程中刚好开售/截止的反馈。已有倒计时与正常恢复用例，尚未覆盖上述组合。
3. **后端预约边界**：URL 与 body 的 eventId 不同、预约数量超过票种配额、未设置开售时间，以及分页极值。JaCoCo 显示相关分支未完整触发；正常预约和权限已覆盖。
4. **身份与初始化**：启用 bootstrap 时新建管理员、给已有用户授予 ADMIN、重复启动不重复创建；并发相同邮箱冲突、停用账号登录拒绝。当前 bootstrap 有8行未覆盖，identity 分支覆盖低于订单模块。
5. **持续集成可重复性**：前端覆盖率报告已接入CI；真实浏览器验收脚本目前位于本地临时目录，未纳入仓库或 CI。后续应将核心端到端流程转成可重复执行的测试。

并发功能测试不是压力测试。跨浏览器、实体手机、大规模负载、数据库备份恢复仍留在 Sprint 5 验收。该段为10月9日历史缺口：当时支付、出票、签到、通知和报表尚未实现。出票、签到、通知和基础统计的最新状态见本文顶部；支付仍不在当前范围。

## 如何运行

从项目根目录执行：

```bash
# Docker 已启动；完整后端回归与干净的覆盖率报告
RUN_CONTAINER_TESTS=true mvn -f backend/pom.xml clean verify

# 前端交互测试、类型检查与生产构建
npm --prefix frontend run test:coverage
npm --prefix frontend run build
```

报告：`backend/target/site/jacoco/index.html`（人工查看）、`jacoco.xml`（计数）、`backend/target/surefire-reports/`（测试结果）。这些是本地生成物，不提交到仓库。

不设置 `RUN_CONTAINER_TESTS=true` 时，容器集成测试会跳过。CI 的 `backend` 任务通过 `clean verify` 生成单元测试覆盖率；`backend-integration` 设置该变量并通过 `clean verify` 在同一次运行中执行单元及数据库集成测试，生成总覆盖率。两套报告均显示行与分支覆盖率，并分别上传 `backend-unit-coverage`、`backend-combined-coverage`（含 HTML/XML 报告及测试结果）；通知同时列出两套数据。总覆盖率来自完整运行，不是两个百分比相加。当前后端没有最低覆盖率阻断阈值。

自定义下拉菜单补充验证：键盘方向键/首尾项/文字定位、Esc取消、点击外部关闭、禁用状态；Chrome中选月份与分钟59并应用成功，390px手机菜单位于视口内，无原生select。

发布前票种规则回归：后端105项、前端165项及构建通过；用例验证空票种发布回滚、免费票配额等于容量、重复点击不重复创建，以及草稿隐藏订单入口。该段为当时验收记录；最新统计见本文顶部。

已发布活动编辑回归：验证归属隔离、过期版本冲突、容量约束、固定报名资料配置、订单库存与原始快照保留，以及订单展示最新安排。当前后端106项、前端168项通过，前端构建通过。浏览器以模拟API检查390px/1360px布局、标题背景、隐私折叠与展开及操作/订单卡片间距。

保存失败提示保留后端具体原因，不再把所有409响应都归为版本冲突。运行中的旧后端需要重启才能加载已发布活动编辑功能。

2026-10-09 审查建议回归：后端108项、前端170项及构建通过。覆盖未开放时调整开始报名时间、已开放/立即开放后拒绝修改、其他活动信息仍能保存，以及前端只读字段和原始时间精度保留。报名资料校验先于库存预留；资料不完整时优先返回400，资料完整后继续检查报名窗口。

覆盖率与CI调整：新增19项核心单元测试，覆盖各项资料必填及禁止提交未要求资料、校验失败不预留库存、幂等重试与冲突、重复取消只释放一次库存、活动开始后禁止取消、报名时间边界和取消活动状态。完整127项测试通过，0跳过。CI YAML解析及覆盖率摘要脚本正常/缺失报告检查通过；以上结果来自本地验证，远端结果以对应 GitHub Actions 运行记录为准。

## 前端覆盖率与流水线

使用与 Vitest 匹配的 `@vitest/coverage-v8@4.1.11`，配置参考 [Vitest 覆盖率文档](https://vitest.dev/guide/coverage.html)。统计 `src/**/*.{ts,tsx}`，仅排除测试文件与类型声明；已核对40个源码文件均进入报告，未执行的入口与路由也保留在分母中。生成物位于 `frontend/coverage/` 并由 Git 忽略。

CI 运行 `test:coverage`，在摘要和 Telegram 通知显示行/分支覆盖率，并上传 `frontend-coverage`（HTML、LCOV、JSON摘要）。类型检查由 `npm run build` 执行一次。报告缺失时显示 N/A，不误报为0%。

本轮170项测试和生产构建通过。低覆盖项包括路由入口、ProtectedRoute、AttendeeInfoSummary和头像部分逻辑；应优先补权限跳转、资料展示及头像错误路径。当前先记录基线，没有新增覆盖率阻断门槛。组件测试主要使用模拟API，不能代替真实浏览器和服务端联调。

流水线仍是CI：PR及main推送触发三个并行检查任务，再汇总通知。核心浏览器端到端测试与自动部署尚未接入；CD需在部署环境确定后配置。新配置将在创建或更新 PR、或推送 main 后执行。


## Lint 与静态安全检查（2026-10-19）

CI 新增 `Source and workflow lint` 和 `SAST, dependency and secret scans` 两个作业。

- 前端：ESLint 10 / TypeScript recommended 规则，`npm --prefix frontend run lint`，零 warning 门槛；类型检查仍在 build 中执行。
- 后端：Checkstyle 在 Maven validate 阶段检查主源码及测试源码的空语句、equals/hashCode 配对、switch 穿透、修饰符顺序与 long 字面量。执行 `mvn -f backend/pom.xml validate`。这是基础规则集，不是全面格式化检查。
- 流水线：actionlint 校验 GitHub Actions，`bash -n` 检查扫描脚本。
- SAST：Semgrep 1.181.0，Java / TypeScript registry 规则，发现或解析失败使检查失败。规则集会随 registry 更新；不能声称永久固定扫描结果。
- SCA/密钥：Trivy 0.75.0 扫描 npm 锁文件与源码密钥；另扫描本轮构建的 Spring Boot JAR 内嵌 Java 依赖。HIGH/CRITICAL 阻断，没有全局 ignore-unfixed。不是只扫描无法完整解析父 BOM 的 pom.xml。
- 本地执行：先 `RUN_CONTAINER_TESTS=true mvn -f backend/pom.xml clean verify`，再 `bash scripts/security/scan.sh`。需要 Docker 和联网下载规则、漏洞库。
- 扫描器只挂载 Git 纳入范围的文件副本及明确选择的 JAR；本机被忽略的 `.env`、node_modules 等不被挂载。未提交但未被忽略的新文件也会扫描；Git 历史不在范围内。
- 所有扫描完成后统一返回状态，报告存放在被 Git 忽略的 `security-reports/`，CI 无论成功失败均上传报告，保留 7 天。报告可能含命中片段，应限制 artifact 访问。
- 仓库管理员仍需在分支保护中将两个新作业设为 required checks；添加 workflow 本身不会改变分支保护。

初次扫描发现 Jackson、Tomcat、pgJDBC 的漏洞，分别升级至 Jackson BOM 2.21.7、Tomcat 10.1.60、pgJDBC 42.7.12。后续升级 Spring Boot BOM 时复核这些版本覆盖。

Spring MVC 6.2.19 的两条记录保留为**当前功能范围不适用的限时例外**，不是已修复：

| CVE | 本项目适用性判断 | 复核条件 |
| --- | --- | --- |
| CVE-2026-47884 | 后端仅提供 REST JSON API，没有 XsltView 或隐式服务端视图渲染 | 引入 MVC 视图或 XSLT 时撤销例外 |
| CVE-2026-47890 | 没有 SSE 视图片段；通知使用 JSON 轮询 | 引入 SSE / 视图片段时撤销例外 |

例外仅匹配指定 CVE 和 `spring-webmvc@6.2.19`，2026-11-17 到期，报告保留 suppressed findings。`check-spring-scope.py` 在扫描前检测相关源码/配置变化；关键词检查不能替代架构复核，也不能证明第三方或反射配置安全。应规划受支持 Spring 版本升级，而非无限延期。

依据：[Spring XSLT 公告](https://spring.io/security/cve-2026-47884/)、[Spring SSE 公告](https://spring.io/security/cve-2026-47890/)、[Jackson 2.21.7](https://github.com/FasterXML/jackson/wiki/Jackson-Release-2.21.7)、[Tomcat 安全公告](https://tomcat.apache.org/security-10.html)。

本阶段不包含 DAST、容器镜像扫描或自动部署；业务权限和并发正确性仍由专项测试验证。

本地验证：ESLint、Checkstyle、actionlint、shell 语法检查通过；后端 208 项测试（含 PostgreSQL 集成）全部通过、零跳过；前端 235 项测试及构建通过，通知测试类型写法调整后额外复验 4 项通知测试和类型检查。最终 Semgrep 零 findings / 解析错误；Trivy 无未豁免 HIGH/CRITICAL 发现，Java 报告保留上述两条限时例外。远端 CI 尚待推送后验证。

### CI 报告展示

Actions 的 Artifacts 分别提供 `lint-report`（摘要和各工具文本输出）、`sast-report`（Semgrep JSON）、`dependency-secret-reports`（Trivy JSON）。检查失败时仍上传已有报告；lint 会继续收集各工具结果，最终以非零状态阻断。报告保留 7 天，可用于演示，但应在到期前保存所需课程证据。DAST 尚未实施，因此没有 `dast-report`；后续运行 ZAP 后再增加实际 HTML/JSON 报告。

Artifact Actions 升级为 upload-artifact 7.0.2 / download-artifact 8.0.2，替换引发 Node.js 20 弃用提示的 v4。此处是 Action 自身运行时，与前端项目使用的 Node.js 22 独立。IDE 自动生成的 backend/.settings 等文件不再跟踪，本地文件保留，Java 版本和构建检查仍由 pom.xml 管理。
