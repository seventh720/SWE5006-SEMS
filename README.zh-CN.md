# 智能活动管理与票务系统 · SEMS

[English](README.md) | [简体中文](README.zh-CN.md) | [文档导航](docs/README.md) | [在线系统](https://swe5006.seventh720.com)

SWE5006 第 10 组课程项目，使用 React、TypeScript、Spring Boot 和 PostgreSQL，实现活动发布、免费预订、电子票、工作人员签到、取消联动、通知和报表。

## 当前交付状态

**2026年10月20日（SGT）已核验**：业务基线 [`71082b8`](https://github.com/seventh720/SWE5006-SEMS/commit/71082b840ece4401515c1db9e3ac68d9a6f32e9e) 的 [CI/CD](https://github.com/seventh720/SWE5006-SEMS/actions/runs/39295381246) 全部通过，已部署到 HTTPS 云端，后端实际 JWT 配置为 `PT1H`。

当前可进入**功能冻结**。演示版本最终封板前，还需完成云端多角色验收、DAST、隔离数据库恢复演练、性能基线和手机扫码验证，详见[交付清单](docs/remaining-work.zh-CN.md)。正式 presentation：**2026年10月27日 10:30 SGT**。

## 功能与角色

| 身份 | 已实现流程 |
|---|---|
| 游客 | 按标题、地点、SGT 日期和活动阶段筛选已发布活动 |
| `ATTENDEE` 参与者 | 保存报名资料、预登记、免费订票、查看本人订单及二维码、取消符合条件的订单 |
| `ORGANIZER` 组织者 | 管理自己的活动、票额、报名时间、工作人员分配、订单和报表 |
| `STAFF` 工作人员 | 仅对已分配活动验票、签到及查看签到记录 |
| `ADMIN` 管理员 | 分页检索用户、管理角色、查看全局报表，并管理自己组织的活动 |

同一账号可拥有多个角色。公开注册默认授予 `ATTENDEE`；工作人员角色还需活动分配才能验票。系统另有头像、活动插图和站内通知。

**范围边界：** 当前仅提供免费票。支付/退款、座位图、优惠券、邮件/短信通知、自助修改登录邮箱或密码，不属于本次交付范围。

### 关键业务规则

- 每个订单包含一种票、1–10 张。幂等键防止重复下单，活动锁与配额校验防止超卖；确认订单按人数生成独立电子票。
- 预登记只保存资料，不占库存、不自动下单。报名截止时间不能晚于活动开始时间。
- 签到窗口为开始前15分钟至开始后15分钟；若活动更早结束，则提前关闭。重复核销被拒绝。
- 取消会在事务中联动订单、库存、票券和通知。已核销订单不能由参与者单独取消；取消活动会作废票券并保留签到历史。
- 已结束的发布活动不能通过改日期重新开放。复用使用 **Copy to new draft**，原订单和签到记录保留在原活动。
- JWT 默认1小时。前端处理过期和401，在页面恢复焦点、每30秒及403后核对角色。后端在后续请求中立即执行角色撤销；新增权限需重新登录。实际角色变更会生成站内通知。
- 注册和首次管理员密码为 **8–16 位 ASCII 英文字母或数字**，允许纯字母、纯数字及混合；旧密码登录兼容。最后一个有效管理员受并发降权保护。

## 技术结构

```text
backend/             Spring Boot API、领域模块、Flyway 迁移及测试
frontend/            React/TypeScript 应用及组件测试
deployment/          本地/云端 Compose、PostgreSQL 镜像及代理示例
scripts/             Lint、安全扫描、镜像验证及部署脚本
.github/workflows/   CI 质量检查与 staging CD
docs/                设计决策、接口约定、测试与演示证据
```

后端采用模块化单体，包括 `identity`、`event`、`ticketing`、`booking`、`checkin`、`notification`、`reporting`。签到和报表通过 JDBC 读取共享表的边界例外记录在[架构决策](docs/adr/0001-modular-monolith.md)中。

设计模式已有具体对应：**Observer** 用于同步事务事件及通知，**State** 用于票券生命周期，**Strategy** 用于报名资料校验。定价 Strategy 随收费票范围延期，演示使用资料校验策略。业务时间边界、JWT、通知和 JPA 审计统一使用注入的 `Clock`。模式图与演示路线见[演示准备评审](docs/presentation-readiness-2026-10-19.zh-CN.md)。

## 快速启动

环境基线：JDK **21**、Maven **3.9+**、Node.js **22**、npm **10+**、Docker 和 Compose v2。云端 overlay 需要 Compose **2.24.4+**；数据库使用 PostgreSQL **17** 容器。

以下命令从仓库根目录执行。首次运行创建 `.env`；已有文件时直接编辑，不覆盖：

```bash
cp .env.example .env
```

启动前配置：

| 变量 | 要求 |
|---|---|
| `POSTGRES_DB` / `POSTGRES_USER` | 默认 `sems` / `sems_user` |
| `POSTGRES_PASSWORD` | 设置自己的数据库密码 |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | 本地后端连接参数，与上述数据库设置保持一致 |
| `SEMS_JWT_SECRET` | 替换为至少32字符的随机签名密钥 |
| `SEMS_JWT_ISSUER` / `SEMS_JWT_TTL` | 默认 `sems-api` / `PT1H` |

完整 Docker 模式自动用 `POSTGRES_*` 配置后端数据库连接。`.env` 和 `.secrets/` 已被 Git 忽略。

### 方式 A：Docker 运行完整系统

```bash
docker compose --env-file .env -f deployment/compose.yaml up --build
```

访问 [localhost:3000](http://localhost:3000)，后端健康检查为 [localhost:8080/actuator/health](http://localhost:8080/actuator/health)。停止并保留数据库：

```bash
docker compose --env-file .env -f deployment/compose.yaml down
```

### 方式 B：本地开发前后端

与方式 A 二选一，避免端口冲突。启动 Docker 和数据库：

```bash
docker compose --env-file .env -f deployment/compose.yaml up -d postgres
```

在 Bash/Zsh 或 WSL 中启动后端：

```bash
set -a
source .env
set +a
mvn -f backend/pom.xml spring-boot:run
```

另开终端启动前端：

```bash
npm --prefix frontend ci
npm --prefix frontend run dev
```

访问 [localhost:5173](http://localhost:5173)。Vite 将 `/api` 和 `/actuator` 代理至 `8080`；本地数据库端口为 `5432`。新数据库只有角色定义，不自动生成演示活动或账号。

### 初始化首个管理员

在私有 `.env` 中设置 `SEMS_BOOTSTRAP_ADMIN_USERNAME`、`SEMS_BOOTSTRAP_ADMIN_EMAIL`，以及自己选择的 **8–16 位字母/数字** `SEMS_BOOTSTRAP_ADMIN_PASSWORD`，再启动后端。邮箱或密码留空时不执行初始化；邮箱已存在则授予该账号 `ADMIN`，否则创建新账号。完成后清空 bootstrap 密码。

账号密码规则不限制数据库密码和 JWT 签名密钥。

## 测试与 CI/CD

根据修改范围，从仓库根目录执行对应检查：

```bash
npm --prefix frontend ci
bash scripts/ci/lint.sh
mvn -f backend/pom.xml verify
RUN_CONTAINER_TESTS=true mvn -f backend/pom.xml verify
npm --prefix frontend run test:coverage
npm --prefix frontend run build
```

Lint 中的 actionlint 容器和 PostgreSQL 集成测试需要 Docker 运行。未设置 `RUN_CONTAINER_TESTS=true` 时，容器测试会跳过。前端 `build` 已包含类型检查。覆盖率报告位于 `backend/target/site/jacoco/index.html` 和 `frontend/coverage/index.html`，当前展示真实覆盖率但不设置最低百分比门槛。最新回归为**后端211项、前端244项**，详见[测试记录](docs/testing.zh-CN.md)。

| 阶段 | 内容 |
|---|---|
| Lint | ESLint、Checkstyle、workflow/shell检查、发布清单校验 |
| Backend / Frontend | 单元测试、PostgreSQL集成测试、覆盖率和前端构建 |
| Security | SAST、依赖和密钥扫描 |
| Images | 用已测试产物构建，扫描三个运行镜像，隔离环境注册/登录冒烟 |
| Deploy · Staging | 仅main：发布及拉取digest镜像、备份数据库、部署、检查公网HTTPS |
| Notify · Telegram | 汇总结果；通知失败不改变质量检查结论 |

PR 仅检查，不部署；main 发布镜像并通过 GitHub `staging` 环境部署。检查报告保存为 Actions artifacts。受控安全例外记录在 [trivyignore.yaml](scripts/security/trivyignore.yaml)；DAST 仍是待验收项。凭据配置、服务器目录和恢复边界见[部署维护说明](docs/deployment.md)。

## 数据库查看与变更

本地 Navicat/DBeaver 使用 `localhost:5432` 和 `.env` 中的 `POSTGRES_*`。云端使用 SSH 隧道及 `sems5006_viewer` 只读账号，见[云端连接配置](docs/deployment.md#database-inspection)。

数据库结构变更必须新增 `backend/src/main/resources/db/migration/VyyyyMMddHHmm__description.sql`，不要修改已执行迁移。Flyway 在启动时迁移，Hibernate 仅校验结构。字段与关系见[数据库说明](docs/database/README.md)。

## 团队协作与文档

从最新 main 创建任务分支，完成相关验证后提交 PR 并由组员审查。合并使用 **Create a merge commit**，保留原提交；不使用 squash。main 合并会触发 CD。完整步骤见 [CONTRIBUTING.md](CONTRIBUTING.md)。

- [文档导航](docs/README.md)：接口、架构和数据库资料。
- [部署维护](docs/deployment.md)：CI/CD、共享代理、数据库访问与恢复。
- [测试记录](docs/testing.zh-CN.md)：当前验证结果及历史证据。
- [交付清单](docs/remaining-work.zh-CN.md)：功能冻结和最终封板前的验收工作。
- [系统演示脚本](docs/sprint-4-demo.zh-CN.md)及[准备评审](docs/presentation-readiness-2026-10-19.zh-CN.md)：多角色演示和课程要求映射。
