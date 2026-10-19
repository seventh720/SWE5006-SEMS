# 智能活动管理与票务系统（SEMS）

[English](README.md) | [简体中文](README.zh-CN.md) | [文档导航](docs/README.md) | [测试覆盖](docs/testing.zh-CN.md)

SEMS（Smart Event Management and Ticketing System）采用 React、Spring Boot 模块化单体和 PostgreSQL。当前已实现身份与权限、活动生命周期、票种配置、免费预订、个人订单、组织者订单，电子票与二维码、Staff 分配与核销、站内通知、组织者统计，以及取消活动时的订单、库存和票券联动。

当前计划共5个Sprint；原 proposal 的4轮迭代与现行计划的调整应在项目管理材料中说明。Sprint 4核心业务已实现，Sprint 5用于缺陷修复、验收与交付。支付不在当前范围。

10月27日10:30（SGT）presentation 的要求映射、已验证问题和行动清单见[演示准备评审](docs/presentation-readiness-2026-10-19.zh-CN.md)。

当前待办与建议分工见[项目剩余工作](docs/remaining-work.zh-CN.md)。后续开发使用 `victor` 分支。

## 界面与头像

活动草稿的 **Card illustration** 可选择通用、科技、音乐、运动、艺术或社交插图；活动列表、Dashboard 和详情共用该选择，复制活动时保留。图片是内置矢量资源，无需外部图片服务。卡片悬停和轻微淡入支持系统“减少动态效果”设置。

点击 Dashboard 头像进入个人资料页，可上传 PNG/JPEG（最大5 MB），自动居中裁成256×256并保存；**Use default avatar** 恢复默认头像。头像仅通过本人登录接口读取，未上传或图片加载失败时显示默认图。

## 票种与预订

- 组织者：从“My events”进入活动，选择“Manage ticket types”设置票种；“View event orders”查看本人活动订单。
- 参与者：从活动详情预订免费票，通过Dashboard的“My orders”查看和取消订单。
- 已发布且未开始的活动也能补配票种。总配额不超过容量；票种首次预订后冻结配置，取消订单不解除冻结。
- 一单单票种、1–10张。下单请求必须携带 `Idempotency-Key`，网络重试复用同键。
- 活动取消会在同一事务中取消全部有效订单并返还库存；订单历史保留快照，取消活动不重新开放预订。
- 活动详情的“Copy to new draft”可复用本人草稿、已发布或已取消活动，含票种配置；复制后核对时间，再保存和发布。
- 活动可选设置开始报名时间；留空表示发布后即可报名。开售前用户可预约预填资料，在个人页查看预约状态和倒计时；开售后需手动确认，预约不占名额、不自动下单。
- 活动可设置报名截止时间（默认开始时间），截止后停止新预订。时间输入固定为英文 `YYYY-MM-DD HH:mm` 格式，采用SGT、精确到分钟；英文日历以浮层显示，不挤动表单。
- 活动草稿可分别要求姓名、邮箱、电话、学生证号、护照号，并添加一个自定义问题。选中的内容在预订时必填。
- Dashboard的“Saved booking details”可提前保存常用资料；预订时点击“Apply saved details”应用，仍可改填。资料页和预订页均有隐私说明。
- 免费订单无需支付，确认后自动为每张票生成独立票码。订单详情可查看二维码；已核销或作废的票不再显示可入场二维码。付费票不能下单。

启动新版后端会自动执行新增Flyway迁移，无需手工改库。已有用户、角色、活动和票种保留。沿用下方启动和测试命令。

[活动复用与资料预填验收](docs/acceptance/README.md) · [API、权限与事务约定](docs/sprint-3-api.zh-CN.md) · [数据库说明](docs/database/README.md) · [验收记录和截图](docs/acceptance/sprint3-2026-10-06/README.md)

## 1. 用户角色与初始账号

系统定义了 **4 种角色**，一个账号可以同时拥有多个角色。

| 角色代码 | 中文名称 | 业务定位（含后续迭代规划） |
|---|---|---|
| `ATTENDEE` | 参与者 | 浏览活动、预订门票、查看自己的票券 |
| `ORGANIZER` | 活动组织者 | 创建和管理有权限操作的活动 |
| `STAFF` | 活动工作人员 | 验票和现场签到 |
| `ADMIN` | 系统管理员 | 管理用户、分配角色，以及系统级管理 |

通过公开注册接口创建的账号默认获得 `ATTENDEE` 角色。首次初始化时，数据库只有角色定义；在有人注册或启用管理员初始化配置之前，没有持久化的用户账号。

配置 `SEMS_BOOTSTRAP_ADMIN_EMAIL` 和 `SEMS_BOOTSTRAP_ADMIN_PASSWORD` 后，可以在后端启动时初始化管理员。真实账号和默认管理员密码不会提交到 Git。

## 2. 项目目录与模块划分

```text
backend/                  Spring Boot 后端 API 与 Flyway 数据库迁移脚本
frontend/                 React + TypeScript 前端
deployment/compose.yaml   PostgreSQL 与完整应用的容器运行配置
docs/                     架构与数据库设计说明
.github/workflows/        持续集成（CI）工作流
```

后端采用模块化单体架构：在一个后端应用中，按业务职责划分模块。

| 模块 | 职责 |
|---|---|
| `identity` | 身份认证、用户与角色管理（Sprint 1 已实现） |
| `event` | 活动管理（已实现） |
| `ticketing` | 票种、库存、电子票、核销与作废状态 |
| `booking` | 免费预订、订单查询和取消（已实现） |
| `checkin` | 工作人员分配、验票、签到与审计（已实现） |
| `attendance` | 预留包；实际签到实现在 `checkin` |
| `notification` | 站内通知和已读状态（已实现） |
| `reporting` | 本人活动统计及跨活动报表；管理员可查看全部活动，支持筛选和分页 |

后续开发时，模块之间应通过公开的应用接口协作，避免直接共享或访问其他模块的 Repository（数据访问层）和 JPA Entity（数据库映射实体）。

## 3. 开发环境

团队统一使用以下版本作为开发基线：

| 工具 | 要求或建议版本 | 用途 |
|---|---|---|
| Git | 2.40 或更新版本 | 版本控制与代码合并 |
| Java JDK | 21 LTS | 后端编译与运行 |
| Maven | 3.9 或更新版本 | 后端构建与测试 |
| Node.js | 22 LTS | 前端开发工具链 |
| npm | 10 或更新版本 | 前端依赖管理 |
| Docker Desktop / Engine | 24 或更新版本 | 运行数据库和应用容器 |
| Docker Compose | v2 或更新版本 | 编排本地多个容器 |
| PostgreSQL | 17 容器镜像 | 关系型数据库 |
| Navicat | 可选 | 查看数据库与调试 SQL |

Maven 编译目标是 Java 21。较新的本地 JDK 可能也能编译，但组员和 CI 应统一使用 JDK 21，减少环境差异。

默认端口如下：

| 服务 | 端口 |
|---|---|
| React 本地开发服务器 | `5173` |
| Docker 中的前端 | `3000` |
| Spring Boot 后端 API | `8080` |
| PostgreSQL 数据库 | `5432` |

## 4. 首次启动

以下命令默认从项目根目录执行；出现 `cd` 时再切换目录。环境变量加载命令适用于 Bash / Zsh；Windows 用户可在 WSL 的 Bash 中执行。

### 4.1 创建本地环境配置

克隆仓库后，将示例配置复制为本地配置。如果已有 `.env`，直接编辑现有文件，避免覆盖自己的配置。

```bash
cp .env.example .env
```

启动前修改 `.env`：

| 变量 | 配置说明 |
|---|---|
| `POSTGRES_DB` | 数据库名称，默认 `sems` |
| `POSTGRES_USER` | 数据库用户名，默认 `sems_user` |
| `POSTGRES_PASSWORD` | 数据库密码，必须替换示例值 |
| `DB_URL` | 本地运行后端时的数据库地址；数据库名应与 `POSTGRES_DB` 一致 |
| `DB_USERNAME` | 本地运行后端时的数据库用户名，应与 `POSTGRES_USER` 一致 |
| `DB_PASSWORD` | 本地运行后端时的数据库密码，应与 `POSTGRES_PASSWORD` 一致 |
| `SEMS_JWT_SECRET` | JWT 签名密钥，必须替换为至少 32 个字符的随机值 |
| `SEMS_JWT_ISSUER` | JWT 签发者，默认 `sems-api` |
| `SEMS_JWT_TTL` | JWT 有效期，默认 `PT1H`，即 1 小时 |

`.env` 已被 Git 忽略，不要将本地密码或密钥提交到仓库。完整 Docker 模式下，Compose 会自动使用 `POSTGRES_*` 配置后端数据库连接。

### 4.2 方式 A：使用 Docker 启动完整系统

适合快速运行系统或进行功能演示。先启动 Docker，再执行：

```bash
docker compose --env-file .env -f deployment/compose.yaml up --build
```

启动后访问：

- 前端：<http://localhost:3000>
- 后端健康检查：<http://localhost:8080/actuator/health>

停止容器并保留本地数据库数据：

```bash
docker compose --env-file .env -f deployment/compose.yaml down
```

### 4.3 方式 B：本地开发前后端，Docker 运行数据库

适合日常修改代码。与方式 A 二选一，避免同时启动两套后端导致端口冲突。

先在项目根目录启动 PostgreSQL：

```bash
docker compose --env-file .env -f deployment/compose.yaml up -d postgres
```

在项目根目录加载环境变量，然后启动后端：

```bash
set -a
source .env
set +a
cd backend
mvn spring-boot:run
```

另开一个终端，从项目根目录启动前端：

```bash
cd frontend
npm install
npm run dev
```

打开 <http://localhost:5173>。Vite 会将 `/api` 和 `/actuator` 请求代理到 `http://localhost:8080`。

## 5. Navicat 连接与数据库变更

PostgreSQL 启动后，在 Navicat 中新建 PostgreSQL 连接：

| 连接项 | 填写内容 |
|---|---|
| 主机 | `localhost` |
| 端口 | `5432` |
| 数据库 | `.env` 中的 `POSTGRES_DB`，通常为 `sems` |
| 用户名 | `.env` 中的 `POSTGRES_USER`，通常为 `sems_user` |
| 密码 | 本地 `.env` 中的 `POSTGRES_PASSWORD` |

Navicat 用于查看表结构、检查数据和调试查询。数据库结构变更必须通过新增 `backend/src/main/resources/db/migration` 下的迁移文件完成，不能只在 Navicat 界面中修改，否则其他组员的数据库无法同步。

后端启动时会自动运行 Flyway 迁移。Hibernate 使用 `ddl-auto: validate`，只检查 Java 实体映射是否与数据库结构一致，不会自动修改表结构。

迁移文件命名规范：

```text
VyyyyMMddHHmm__short_description.sql
```

版本号与描述之间使用 **两个下划线**。不要修改已经执行过的迁移文件；应新增迁移文件，并与相关 Java 实体、测试和实体关系图（ERD）更新一起提交到同一个 PR。

## 6. 初始化管理员（可选）

需要创建首个管理员时，在本地 `.env` 或部署环境的密钥管理配置中设置：

```dotenv
SEMS_BOOTSTRAP_ADMIN_USERNAME=admin
SEMS_BOOTSTRAP_ADMIN_EMAIL=admin@example.com
SEMS_BOOTSTRAP_ADMIN_PASSWORD=replace-with-a-strong-password
```

请将示例邮箱和密码替换为实际配置。后端启动时，如果该邮箱不存在，就创建账号；如果已经存在，则为该账号授予 `ADMIN` 角色。完成首次受控部署后，清空初始化管理员密码配置。

## 7. 已实现的 API

活动浏览页面和数据库查询接口已实现。启动或重启新版后端时，Flyway 自动创建活动表；访问前端 `/events` 即可浏览。空库显示空列表，不会自动插入演示活动。组织者草稿创建、编辑、发布和取消现已实现。

| 方法 | 接口路径 | 功能 | 访问权限 |
|---|---|---|---|
| `GET` | `/api/v1/events?page=0&size=10&keyword=...` | 已发布活动列表，标题搜索、分页 | 无需登录 |
| `GET` | `/api/v1/events/{id}` | 已发布活动详情 | 无需登录 |

`page` 从 0 开始，`size` 默认 10、范围 1–50。按开始时间、ID 升序返回；标题搜索忽略大小写和首尾空白，`%`、`_` 按普通字符匹配。列表响应为 `items`、`page`、`size`、`totalElements`、`totalPages`。详情字段为 `id`、`title`、`description`、`location`、`startsAt`、`endsAt`、`capacity`、`status`；时间使用含时区 ISO 8601。

非法分页／UUID 返回 `400`；不存在、草稿和取消活动的详情统一返回 `404`，采用 Problem JSON。仅上述 GET 接口允许匿名访问。

```bash
curl -i 'http://localhost:8080/api/v1/events?page=0&size=10'
```

正常空库返回 `200` 和 `items: []`。若页面仍报加载失败，先确认运行的是新版后端；Docker 部署需重新构建后端镜像。数据库字段与关系见 [数据库说明](docs/database/README.md)。


### 按身份显示的首页

个人视图显示真实的已发布活动；组织者视图显示本人草稿／已发布／已取消总数，以及最多 4 个未来开始的已发布活动；管理员视图提供用户与角色管理入口和账号概况；工作人员视图提供已分配活动、扫码或手输验票、核销和签到记录。

多角色账号可以切换视图，默认优先级为管理员、组织者、工作人员、个人。所有账号均可浏览个人视图，管理员也可进入组织者视图管理自己的活动。右上角 **My account** 可查看账号资料、修改用户名和头像并退出登录；暂不支持修改邮箱和密码。

组织者统计通过 `GET /api/v1/organizer/events/summary` 获取，需 `ORGANIZER` 或 `ADMIN`，只统计 JWT 当前用户的数据，不受列表分页影响。响应包含 `drafts`、`published`、`cancelled`、`upcoming`。管理员账号概况复用现有用户列表接口，角色统计可能重叠，因为账号支持多角色。

### 组织者草稿管理

使用具有 `ORGANIZER` 或 `ADMIN` 角色的账号登录，从首页进入 **My events**（`/organizer/events`），点击 **Create event** 填写标题、描述、地点、起止时间及正整数容量，然后 **Save draft**。表单时间统一为新加坡时区。管理员分配组织者角色后，用户需重新登录获取新令牌。

| 方法 | 接口路径 | 功能 |
|---|---|---|
| `GET` | `/api/v1/organizer/events?page=0&size=10` | 本人活动列表，创建时间及 ID 降序 |
| `GET` | `/api/v1/organizer/events/{id}` | 本人活动管理详情 |
| `POST` | `/api/v1/organizer/events` | 保存完整草稿，返回 `201` 与 Location |
| `PUT` | `/api/v1/organizer/events/{id}` | 编辑本人草稿，必须携带当前 `version` |
| `POST` | `/api/v1/organizer/events/{id}/publish` | 发布本人草稿，JSON 请求携带 `version` |
| `POST` | `/api/v1/organizer/events/{id}/cancel` | 取消本人草稿或已发布活动，JSON 请求携带 `version` |

所有管理请求均携带 JWT。创建者由 JWT 确定；即使是管理员，也不能查看或编辑他人的活动。角色不符返回 `403`，非本人或不存在返回 `404`，版本过期或编辑已取消活动返回 `409`。

草稿请求字段为 `title`（1–200 字符）、`description`（1–10000）、`location`（1–500）、`startsAt`、`endsAt`（含时区时间）、`capacity`（1–2147483647 整数）。时间必须先开始后结束。管理响应在公开字段之外增加 `version`、`createdAt`、`updatedAt`。冲突时复制需要保留的文字，再点击 **Reload latest version**。

验收步骤：新建草稿 → 返回 My events → 刷新确认记录仍在 → 编辑保存 → 刷新确认修改 → 访问公开列表确认草稿未出现。第二个组织者使用该 ID 读取或编辑应返回 `404`。保存修改后点击 **Publish event → Confirm publication**，公开列表和详情即可读取该活动。发布时开始时间必须晚于当前时间；发布后仍可修改名称、介绍、地点、时间、插图和容量（不能低于票种总配额），结束时间必须晚于当前时间；报名资料要求保持不变。订单展示最新活动安排，下单快照保留。点击 **Cancel event → Confirm cancellation** 后，公开列表移除该活动，公开详情返回 `404`，本人管理列表保留取消记录。取消不可恢复，重复发布或重复取消返回 `409`。未保存的表单修改必须先保存，再发布或取消。

认证（Authentication）用于确认“你是谁”；授权（Authorization）用于判断“你能执行哪些操作”。

| 方法 | 接口路径 | 功能 | 访问权限 |
|---|---|---|---|
| `POST` | `/api/v1/auth/register` | 注册账号 | 无需登录 |
| `POST` | `/api/v1/auth/login` | 登录并获取令牌 | 无需登录 |
| `GET` | `/api/v1/auth/me` | 查询当前用户 | 已登录 |
| `GET` | `/api/v1/admin/users` | 分页用户列表，支持用户名/邮箱keyword检索 | `ADMIN` |
| `GET` | `/api/v1/admin/users/summary` | 全局用户及角色计数 | `ADMIN` |
| `GET` | `/api/v1/organizer/reports` | 本人活动票种报表、筛选及分页汇总 | `ORGANIZER`/`ADMIN` |
| `GET` | `/api/v1/admin/reports` | 全平台活动票种报表 | `ADMIN` |
| `PUT` | `/api/v1/admin/users/{userId}/roles` | 管理用户角色 | `ADMIN` |
| `GET` | `/api/v1/demo/organizer` | 组织者权限演示 | `ORGANIZER` 或 `ADMIN` |
| `GET` | `/api/v1/demo/staff` | 工作人员权限演示 | `STAFF` 或 `ADMIN` |

注册示例：

```bash
curl -i http://localhost:8080/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","email":"alice@example.com","password":"Password123"}'
```

登录示例：

```bash
curl -s http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"alice@example.com","password":"Password123"}'
```

调用受保护接口时，将登录返回的 JWT 令牌放入请求头：`Authorization: Bearer <token>`。例如，将下面的 `<token>` 替换为实际令牌后查询当前用户：

```bash
curl -i http://localhost:8080/api/v1/auth/me \
  -H 'Authorization: Bearer <token>'
```

前端将短期令牌存放在 `sessionStorage` 中，关闭标签页或结束页面会话后通常会被清除。接口是否允许访问，最终由后端权限校验决定。

## 8. 测试与质量检查

以下每组命令都从项目根目录开始执行。

后端单元测试：

```bash
cd backend
mvn test
```

PostgreSQL 集成测试需要 Docker 已启动，并显式启用容器测试：

```bash
cd backend
RUN_CONTAINER_TESTS=true mvn test
```

前端类型检查、测试与构建：

```bash
cd frontend
npm run typecheck
npm test
npm run build
```

`mvn verify` 会把后端 JaCoCo 报告写入 `backend/target/site/jacoco/index.html`。后端代码行覆盖率目前只用于展示，不设置阻断构建的最低阈值。

GitHub Actions 会运行后端单元测试、PostgreSQL 集成测试和前端检查。自动依赖升级 PR 已关闭；依赖版本只在团队决定需要升级时手动调整。

### 8.1 Telegram CI 通知

CI 的最后一个任务使用 `appleboy/telegram-action@v1.0.1`，发送后端结果及代码行覆盖率、PostgreSQL 集成测试结果、前端结果、提交信息，以及对应 GitHub Actions 运行链接。Telegram 通知失败不会改变构建结果。

1. 在 Telegram 中通过 `@BotFather` 创建机器人，并妥善保存 token。
2. 给机器人发送一条消息；如果发送到群组，则把机器人加入群组后在群内发送一条消息。
3. 访问 `https://api.telegram.org/bot<TOKEN>/getUpdates`，从 `message.chat.id` 取得 chat ID。群组的 chat ID 通常是负数。
4. 打开 GitHub 仓库的 **Settings > Secrets and variables > Actions**，添加两个 Repository secrets：
   - `TELEGRAM_TOKEN`：BotFather 提供的 token。
   - `TELEGRAM_TO`：接收通知的个人聊天或群组 ID。

不要把这两个值写入 `.env`、工作流 YAML、源码或 Git 历史。没有配置这两个 Secrets 时，CI 会跳过通知并正常完成。

## 9. 团队 Git 协作流程

统一流程：**更新 `main` → 创建自己的任务分支 → 本地开发与测试 → push → 发起 PR → 邀请 1 人 review → 修改并通过检查 → merge → 同步本地 `main`**。所有变更通过 PR 合并，不直接向 `main` push。

### 9.1 每个任务创建自己的分支

首次开发先完成第 3、4 节的环境配置。以下 Git 命令从项目根目录执行；切换分支前先提交或暂存当前未完成的修改（`git stash`），确保工作区干净。

```bash
git switch main
git pull --ff-only origin main
git switch -c event-list-alice
```

分支名能看出大致任务和开发者名字即可，例如 `event-list-alice`、`login-bob`，不要求固定前缀或任务编号。将示例替换为自己的任务和名字。每人每个任务使用独立的短期分支，避免多人共用一个开发分支或长期复用已合并分支。

### 9.2 本地开发与提交前检查

日常开发按第 4.3 节启动数据库、后端和前端。完成修改后，手动验证相关功能，并运行相关检查。下面每组命令分别从项目根目录执行：

```bash
cd backend
mvn verify
# 涉及数据库或持久化逻辑时，启动 Docker 后再运行：
RUN_CONTAINER_TESTS=true mvn test
```

```bash
cd frontend
npm ci
npm run typecheck
npm test
npm run build
```

涉及数据库变更的 PR 应同时包含新增 Flyway 迁移脚本、对应业务代码、自动化测试和数据库文档。不要修改已经执行过的迁移文件。

回到项目根目录，检查差异后，只暂存本任务需要的文件（替换下面的示例路径）：

```bash
# 暂存并提交当前修改
git add .
git commit -m "feat: add event list"

# 同步最新主分支
git fetch origin
git merge origin/main

# 推送自己的分支
git push -u origin xxx
```

不要提交 `.env`、密码、密钥或本地生成文件。提交信息应说明本次修改，例如 `feat: add event list`、`fix: validate login input`、`docs: clarify local setup`。

### 9.3 发起 Pull Request 并邀请审查

1. 在 GitHub 创建 PR，选择 **base: `main`**、**compare: 自己的任务分支**。
2. 按 PR 模板填写任务或 Issue、变更内容、测试命令与结果；界面变更附截图，数据库或配置变更说明组员需要执行的步骤。
3. 在 **Reviewers** 中邀请 **1 名其他组员**，优先邀请相关模块负责人。尚未完成的工作可先开 Draft PR，准备好后再标记为 Ready for review。
4. 获得 **1 名其他组员的 Approve** 后才可合并。仅留言或邀请 reviewer 不算批准。
5. Reviewer 检查功能是否符合任务要求、代码是否清晰、异常和权限边界是否处理妥当，以及测试和文档是否充分。

当前 CI 在创建或更新 PR 时运行，并在 push 到 `main` 后运行；仅 push 自己的分支但未创建 PR，不会触发当前 CI。

### 9.4 处理意见、同步主分支并合并

根据 review 意见在原分支修改，重新测试后提交并 push，PR 会自动更新，无需重新创建：

```bash
git add path/to/changed-file
git commit -m "fix: address review feedback"
git push
```

如果 `main` 已更新，在工作区干净时将最新主分支合入自己的任务分支：

```bash
git fetch origin
git merge origin/main
```

如有冲突，逐个编辑冲突文件，确认保留的逻辑后 `git add` 对应文件并执行 `git commit` 完成合并；需要取消本次冲突合并时执行 `git merge --abort`。合并后重新运行相关检查并 `git push`。新增修改后请 reviewer 复查，确保批准覆盖最新代码。

满足以下条件后，由 PR 作者或负责合并的组员执行 **Squash and merge**：

- PR 已完成，目标分支为 `main`，不存在合并冲突。
- `backend`、`backend-integration`、`frontend` 三项 CI 检查全部通过。
- 已获得 1 名其他组员批准，所有审查意见已处理、讨论已解决。

合并后删除 GitHub 上对应的任务分支，再在本地同步：

```bash
git switch main
git pull --ff-only origin main
git fetch --prune
```

下一个任务重新从最新 `main` 创建分支。Squash 合并后，`git branch -d` 可能因原提交未直接进入 `main` 而拒绝删除本地旧分支；可以先保留，确认 PR 已合并且无独有改动后再清理。

### 9.5 仓库管理员配置

上述内容是团队约定；文档本身不会启用 GitHub 的强制保护。管理员应为 `main` 配置分支保护或 ruleset：要求通过 PR 合并、至少 1 个 approval、最新修改后重新批准、解决全部讨论，并将 `backend`、`backend-integration`、`frontend` 设为必需状态检查，禁止 force push 和删除主分支。

Telegram 通知不是合并所需的质量检查。

发布前必须配置至少一个票种。草稿页的 **Create free ticket** 按已保存活动容量创建免费票，不自动发布；草稿不显示订单入口。

报名窗口调整规则：尚未开放的已发布活动可以调整报名开始时间；一旦已开放（含发布后立即开放），开始时间固定，后端拒绝修改并返回409。活动时间、地点和报名截止时间仍可按现有规则调整。预订先完成幂等检查、锁定活动和报名资料校验，再预留库存。

前端覆盖率：运行 `npm --prefix frontend run test:coverage`，查看 `frontend/coverage/index.html`。CI分别上传后端单元、后端完整、前端覆盖率报告，并在摘要和通知中展示行/分支覆盖率。前端类型检查在构建时执行一次。当前记录覆盖率基线，未设置百分比阻断门槛；浏览器端到端检查尚未接入；main 的自动部署流程见下文。


## 镜像与共享服务器部署（2026-10-19）

流水线新增 `Images · Scan & Smoke Test`。它等待 lint、静态安全、前后端测试和数据库集成检查通过，下载本次构建的 JAR/dist，使用 Dockerfile 的 `ci` target 打包；不会在镜像阶段重新编译不同产物。Trivy 扫描 backend、frontend、postgres 三个运行镜像的 HIGH/CRITICAL 漏洞及密钥。沿用测试说明中两条精确 Spring MVC 适用性例外，其他发现阻断。

镜像冒烟测试使用独立 Compose 项目、随机临时凭据、临时数据库卷和动态本机端口。检查健康状态、全新 Flyway 迁移、页面深链接、公共 API，以及带外部 HTTPS Origin 的注册/登录/身份查询；退出时仅清理该次测试创建的容器与卷。该检查不是 DAST，也不代表真实域名证书已经验证。

在 main 的 push 上，扫描和冒烟成功后发布 GHCR 镜像，以提交 SHA 标记，并产出 `release-images` artifact（`compose.release.json`，镜像引用固定为 digest，保留 30 天）。PR 只构建和检查，不推送镜像。`image-security-reports` 保存扫描和冒烟记录 7 天。第一次 main 发布仍需实际验证 GHCR 权限；服务器拉取私有镜像需要有 read:packages 权限的凭据。

本地复验：

```bash
RUN_CONTAINER_TESTS=true mvn -f backend/pom.xml clean verify
npm --prefix frontend ci
npm --prefix frontend run build
bash scripts/cd/images.sh
bash scripts/cd/image-smoke.sh
```

本地默认 Dockerfile target 仍支持从源码构建。`.dockerignore` 使用允许列表，防止本机 node_modules、IDE 配置、环境文件和测试日志进入镜像上下文。PostgreSQL 镜像使用 Alpine su-exec 替代含旧 Go 依赖的 gosu；保持原版入口流程与数据目录。

### 与 SWE5001 共用 HTTPS 入口

5006 使用独立的 Compose 项目名 `swe5006`、数据库卷、目录、密码和 JWT 密钥。先检查主机剩余内存；cloud overlay 的上限合计约 1.4 GiB，主机还需为现有 5001 和系统预留空间。不要覆盖 5001 的数据库或环境文件。

`deployment/compose.cloud.yaml` 需要 Docker Compose 2.24.4+。它移除前后端宿主机端口发布，仅将数据库绑定到 `127.0.0.1:15432` 供 SSH 隧道访问，设置重启策略、日志轮转、内存限制；后端仅接受内部前端代理，只有前端加入名为 `swe5006-edge` 的网络。现有 Caddy 通过此网络访问 `sems5006-web:80`。

上线顺序（当前只提供配置，尚未在服务器执行）：

1. 在服务器独立目录保存本项目部署文件和私有 `.env`，从所选 main CI 下载 `compose.release.json`。确认文件记录的提交就是要部署的版本。已有 5006 数据时，升级前先备份并检查备份可读；首次部署创建空库。不要使用 `down -v` 更新真实环境。
2. 登录镜像仓库，然后在项目根目录执行：

   ```bash
   docker compose --env-file .env -p swe5006 -f deployment/compose.yaml -f deployment/compose.cloud.yaml -f compose.release.json pull
   docker compose --env-file .env -p swe5006 -f deployment/compose.yaml -f deployment/compose.cloud.yaml -f compose.release.json up -d --no-build --wait
   ```

3. `swe5006-edge` 创建后，将 `deployment/proxy/compose.swe5001-overlay.yaml` 作为附加配置用于现有 5001 Compose，使 Caddy 同时保留原 default 网络并连接 5006 网络。必须沿用 5001 原有项目名、镜像锁定文件及所有既有 compose 参数；只更新 Caddy，不能用这个 overlay 单独启动整个 5001 项目。将该网络设置纳入 5001 后续部署配置，否则下一次 5001 部署可能移除连接。
4. 将 `deployment/proxy/swe5006.Caddyfile` 的站点段加入原 Caddyfile，保留 5001 站点；先 validate 再 reload。此配置也应持久纳入 5001 的部署源文件，不能只修改临时 release 目录。不要再启动第二个 Caddy 抢占 80/443。
5. 检查 `https://swe5006.seventh720.com` 的证书、登录、活动浏览及手机扫码，并回归检查 5001 域名。第一次创建管理员后清除 bootstrap 密码配置。

Nginx 保留可信 Caddy 提供的 HTTPS 协议信息，cloud 后端启用 Spring forwarded headers。前提是云端前后端不直接暴露公网；不要在开放的后端端口上无条件信任转发头。共享代理变更需要结合服务器实际目录及网络配置执行，仓库中的示例不代表已上线。

自动部署配置见下文。当前没有自动数据库回滚或 DAST。应用镜像回退也不等于数据库可以无条件降级，回退前必须检查迁移兼容性。


## CD 与云端数据库查看（2026-10-20）

`main` push → Lint / Tests / SAST → 构建、扫描和冒烟验证镜像 → 发布 digest → `Deploy · Staging`。
PR 不部署。部署使用同一次 CI 的 `release-images`，校验提交及三个镜像摘要，不在服务器重新构建。
同一环境串行部署；开始前跳过已被更新 main 替代的运行。GitHub `staging` environment 保存
`STAGING_SSH_KEY`、`STAGING_KNOWN_HOSTS`，变量 `STAGING_HOST`。SSH 私钥不进入仓库。

服务器布局：
- `/home/ubuntu/swe5006/.env`：主机私有配置，CD 不覆盖。
- `releases/<sha>-<run>-<attempt>/`：每次部署的 Compose、脚本及镜像摘要。
- `current` / `previous`：最近成功及前一次成功部署。
- `backups/pre-<sha>-<timestamp>.dump`：升级前 PostgreSQL 自定义格式备份。
- `deployed-sha`：仅在容器健康且公网 HTTPS 检查通过后更新。

部署前必须成功备份已有数据库，随后 `up --no-build --wait`，重启前端使 Nginx 重新解析后端，
再验证页面、API 和 `/actuator/health`。失败时保留旧成功标记和备份，并让 CI 失败；
运行中的容器可能已经更新，不能据成功标记断言已经回滚。确认 Flyway 兼容性后才能手动回退镜像；
数据库恢复需单独操作，不能对真实环境执行 `down -v`。该流程升级现有实例，首次建库仍使用前面的初始化步骤。

Navicat / DBeaver 使用 SSH 隧道：
- SSH 主机 `18.139.212.35`，端口 `22`，用户 `ubuntu`，使用 Lightsail 私钥。
- 数据库主机 `127.0.0.1`，端口 `15432`，数据库 `sems`，用户 `sems5006_viewer`。
- 只读账号密码保存在本地忽略目录 `.secrets/cloud-staging/navicat.json`，不提交 Git。
- 账号仅有 public schema 现有及后续表的 SELECT 权限，不能新增、修改或删除业务数据。

5001 数据库保留原来的 `127.0.0.1:5432`。不要在 Lightsail 防火墙开放 15432。
共享 Caddy 的持久化修复位于 SWE5001 PR #16，须在下次 5001 部署前合并，保留 5006 代理网络及站点。


## 会话、角色通知与业务时间（2026-10-20）

- JWT 默认有效期为 1 小时，由后端签发；前端保存实际到期时间，到期或受保护接口返回 401 时清除会话并提示重新登录。旧 JWT 不会因配置修改而延长有效期。
- 角色修改成功时，在同一事务内生成 `ROLES_CHANGED` 站内通知，包含修改前后角色；重复保存相同角色不产生通知。通知失败则权限修改一起回滚。
- 登录用户每 30 秒、回到页面及遇到 403 时核对 `/api/v1/auth/me`。发现角色变化后提示重新登录；管理员修改自己的角色即时执行相同处理。普通资源权限不足和临时网络错误不会直接退出。
- 新注册和首次管理员设置密码为 8–16 位 ASCII 英文字母或数字，允许纯数字、纯字母及混合；中文、空格、符号和超过长度的输入返回校验错误。旧密码登录兼容，不批量修改现有账号。
- `BusinessTimeConfiguration` 提供唯一 `Clock`，JWT 签发/校验、报名/取消/签到边界、通知和 JPA 审计时间共用它。领域对象需要时间时由调用方传入 `clock.instant()`，不自行读取系统时间。
- 部署时检查主机 `.env` 的 `SEMS_JWT_TTL=PT1H`，因为既有环境变量优先于仓库默认值。Flyway 会增加角色通知类型；合并后由 CD 发布。
