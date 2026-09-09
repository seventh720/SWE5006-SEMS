# 智能活动管理与票务系统（SEMS）

[English](README.md) | [简体中文](README.zh-CN.md)

SEMS（Smart Event Management and Ticketing System）是一个按业务模块划分的活动管理与票务系统。目前第一轮迭代（Sprint 1）已实现完整的身份认证与权限控制流程，包括用户注册、密码哈希存储、登录、JWT 校验、当前用户信息查询、基于角色的接口授权，以及管理员分配用户角色。

项目包含 React 前端、Spring Boot 模块化单体后端、PostgreSQL 数据库迁移脚本、Docker Compose 配置，以及初步的 GitHub Actions 持续集成流程。活动、购票和签到等业务功能将在后续迭代中实现。

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
| `event` | 活动管理（预留） |
| `ticketing` | 票务管理（预留） |
| `booking` | 预订管理（预留） |
| `attendance` | 验票与签到（预留） |
| `notification` | 通知（预留） |
| `reporting` | 报表（预留） |

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
| `SEMS_JWT_TTL` | JWT 有效期，默认 `PT30M`，即 30 分钟 |

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

## 7. Sprint 1 已实现的 API

认证（Authentication）用于确认“你是谁”；授权（Authorization）用于判断“你能执行哪些操作”。

| 方法 | 接口路径 | 功能 | 访问权限 |
|---|---|---|---|
| `POST` | `/api/v1/auth/register` | 注册账号 | 无需登录 |
| `POST` | `/api/v1/auth/login` | 登录并获取令牌 | 无需登录 |
| `GET` | `/api/v1/auth/me` | 查询当前用户 | 已登录 |
| `GET` | `/api/v1/admin/users` | 查询用户列表 | `ADMIN` |
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

使用短期功能分支，例如：

```text
feature/us-01-registration
feature/us-02-jwt-login
feature/us-03-rbac
```

团队应保护 `main` 分支，并要求 CI 通过、至少一名组员完成代码审查后再合并。PR（Pull Request）是提交代码供组员审查和合并的请求。

涉及数据库变更的 PR 应同时包含 Flyway 迁移脚本、对应业务代码、自动化测试和数据库文档，确保代码与数据库结构一起更新。
