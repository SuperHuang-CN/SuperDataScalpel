# DataScalpel

DataScalpel 是面向内网部署的数据中台。当前已完成前后端基础框架、轻量 RBAC、系统配置、通用目录、数据源管理，以及元数据模式的模型和字段管理能力。

工程包含后端和独立的前端管理端：

- 后端：Spring Boot 多模块工程。
- 前端：`data-scalpel-ui`，React + TypeScript + Vite + Ant Design + AntV X6。

## 技术基线

- JDK 21
- Spring Boot 4.1.0
- Maven 多模块
- Spring MVC / Validation / Problem Details
- Spring Data JPA + PostgreSQL
- Hibernate `ddl-auto=update`
- Spring Security + JWT
- Spring Boot Actuator
- springdoc-openapi

第一版本不引入 jOOQ、Flyway、Spring Modulith 或 ArchUnit。

## 模块

- `data-scalpel-contracts`：模块间稳定的请求、响应契约，例如 `SearchRequest`、`PageResponse`。
- `data-scalpel-web-core`：通用 Web 自动配置和参数校验错误响应。
- `data-scalpel-dialect`：纯 Java/JDBC 的数据库方言、连接规格和元数据读取内核。
- `data-scalpel-business`：统一业务模块，内部按系统、数据源、模型、任务和数据服务等业务包组织。
- `data-scalpel-admin`：控制面启动模块、运行配置、OpenAPI、Actuator 和安全配置。

业务实体统一继承 `BaseEntity`，使用 Java `UUID` 主键和 `createdAt`、`updatedAt` 字段。UUID 不绑定 PostgreSQL 专属列定义，由 Hibernate 根据数据库方言选择物理类型。

## 当前业务能力：系统配置

系统配置位于“系统管理 / 系统配置”，由程序声明配置键、类型和说明，管理页面仅允许修改配置值，不支持任意新增、删除或改名。

当前内置配置：

- `platform.name`：前端品牌名称。
- `platform.subtitle`：前端顶部副标题。

接口需要有效 JWT：查询需要 `system.configuration.view`，修改需要 `system.configuration.update`：

- `GET /api/v1/system/configurations`：按统一 Search DSL 分页查询。
- `POST /api/v1/system/configurations/{id}/actions/update`：更新配置值。

列表接口返回稳定的 `PageResponse`（`content`、`totalElements`、`totalPages`、`page`、`size`），而非直接暴露 Spring Data 的 `Page` 序列化格式。详细设计见 [系统配置功能详细设计](docs/design/system-configuration.md)。

## 当前业务能力：系统访问管理

系统采用简单 RBAC：每个用户只属于一个角色，角色可配置多个权限。权限由代码声明、应用启动时自动同步，权限管理页面只读；`super_admin` 是系统内置角色，始终拥有全部有效权限。用户、角色、权限管理均在“系统管理”下。

所有业务 API（健康检查、OpenAPI、Swagger 和登录接口除外）均要求 JWT，Resource 使用权限编码进行后端鉴权，前端据同一权限集合隐藏菜单和操作。详细设计见 [系统访问管理](docs/design/system-access-management.md)。

## 当前业务能力：目录与数据源

目录按业务范围隔离，数据源当前使用 `DATA_SOURCE` 目录树。数据源只保存可选的 `directoryId` UUID；目录树会统计直属和子树累计数量，父目录筛选会包含其全部子目录。数据源支持 PostgreSQL、MySQL、Oracle、SQL Server、ClickHouse、达梦、人大金仓和 openGauss 的连接定义、真实连接测试、库/Schema、表、字段、主键、索引与数据预览。详细设计见 [通用目录管理](docs/design/directory-management.md) 和 [数据源管理](docs/design/data-source-management.md)。

## 当前业务能力：模型管理

模型管理维护可被后续任务和数据服务引用的结构契约。模型绑定具有“数据存储”用途的数据源，记录预期物理位置和稳定的字段 UUID，支持草稿、发布、停用和启用状态。

第一版是纯元数据模式：发布和删除均不会连接数据存储，也不会创建、修改或删除物理表。物理表能力通过明确端口留待后续阶段实现。详细设计见 [模型管理第一版](docs/design/model-management.md)。

## 构建

工程固定使用根目录下的 `settings-superhuang.xml`。首次克隆后先从安全模板创建本地配置，
再按需补充私有仓库凭据；该本地文件不会提交到 Git：

```bash
cp settings-superhuang.example.xml settings-superhuang.xml
./mvnw verify
```

## 前端

前端使用 pnpm 管理依赖。推荐使用 Node.js 24 LTS；Node.js 22.12+ 也可满足构建要求。

```bash
cd data-scalpel-ui
pnpm install
pnpm dev
```

前端质量检查：

```bash
pnpm check
```

任务编排画布采用 AntV X6；其节点、连线和配置会保存为稳定的 JSON 定义，而不是 X6 内部对象。

## 启动

准备 PostgreSQL 数据库 `data_scalpel`，或者通过环境变量覆盖连接信息：

```bash
export DATASCALPEL_DB_URL="jdbc:postgresql://localhost:5432/data_scalpel"
export DATASCALPEL_DB_USERNAME="postgres"
export DATASCALPEL_DB_PASSWORD="postgres"
./mvnw -pl data-scalpel-admin -am package
java -jar data-scalpel-admin/target/data-scalpel-admin-0.1.0-SNAPSHOT.jar
```

当前工作区可在根目录的 `config/application-local.yml` 中保存不提交的本地数据库连接，并使用本地 Profile 启动。Spring Boot 会从外部 `config` 目录读取该文件，它不会进入构建产物：

```bash
java -jar data-scalpel-admin/target/data-scalpel-admin-0.1.0-SNAPSHOT.jar --spring.profiles.active=local
```

也可以使用根目录下不提交的 `start-local.sh` 同时启动前后端。默认使用后端 `8080`、前端 `8887`；若端口已被占用，可临时指定备用端口：

```bash
BACKEND_PORT=18080 FRONTEND_PORT=18887 ./start-local.sh
```

默认开发管理员：

- 用户名：`admin`
- 密码：`admin123456`

可以用 `DATASCALPEL_ADMIN_USERNAME` 和 `DATASCALPEL_ADMIN_PASSWORD` 覆盖。

登录获取 JWT：

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123456"}'
```

基础入口：

- 健康检查：`GET /actuator/health`
- OpenAPI：`GET /v3/api-docs`
- Swagger UI：`GET /swagger-ui.html`
- 当前用户：`GET /api/v1/auth/me`，需要 Bearer Token
- 系统配置：`GET /api/v1/system/configurations`，需要 `system.configuration.view`

## 实体通用查询

业务实体 Repository 统一继承 `SearchRepository<T, ID>`，列表查询使用 `SearchEngine`：

```java
Page<Task> page = searchEngine.search(request, Task.class, taskRepository);
```

带固定业务上下文（例如 `projectId`、`catalogId`）时使用带 `Specification` 的重载；该条件会始终与客户端搜索条件组合。

`SearchRequest` 的 HTTP 参数为 `search`、`page`、`size`、`sort`：

```text
GET /api/tasks?search=state:"RUNNING" AND name:*"测试"*&page=0&size=20&sort=-updatedAt,name
```

- `page` 从 0 开始，默认 0；`size` 默认 20，最大 500。
- `sort` 用逗号分隔，前缀 `-` 表示倒序；未指定排序时按 `id DESC`，显式排序也会追加 `id` 作为稳定的分页兜底排序。
- 支持 `:`、`!`、`>`、`>=`、`<`、`<=`，以及 `*"value"*`（包含）、`"value"*`（开头）、`*"value"`（结尾）。
- 支持 `AND`、`OR` 和括号；`AND` 优先于 `OR`。
- 普通值必须使用双引号；支持 `\"` 和 `\\` 转义。`field:null` 和 `field!null` 分别表示空与非空。
- 时间使用 ISO-8601；第一版只支持实体自身及父类的标量字段，不支持关联、集合、JSON、LOB 和 `a.b` 路径。
