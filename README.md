# DataScalpel

DataScalpel 是面向内网部署的数据中台。当前已完成前后端基础框架、轻量 RBAC、系统配置、通用目录、数据源管理、文件数据集管理，以及可管理物理表的模型管理能力。

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
- `data-scalpel-dialect`：纯 Java/JDBC 的数据库方言、连接规格、元数据读取和受控建表内核。
- `data-scalpel-business`：统一业务模块，内部按系统、数据源、模型、任务和数据服务等业务包组织。
- `data-scalpel-admin`：控制面启动模块、运行配置、OpenAPI、Actuator 和安全配置。
- `data-scalpel-service-engine`：可独立部署的数据服务运行面，保存发布快照、动态注册公共路由，并以 JDBC 执行受控标准查询。

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

## 接口响应与错误

成功响应直接返回明确 DTO、`PageResponse`、文件流或空响应，不使用通用成功包裹结构。所有 HTTP 错误响应统一采用 RFC 9457 `application/problem+json`，其中 `code` 是供前后端稳定识别的错误码，字段校验错误还会包含 `violations`。完整契约和实现约定见 [后端 API 响应与异常处理](docs/design/backend-api-response-and-error-handling.md)。

## 当前业务能力：系统访问管理

系统采用简单 RBAC：每个用户只属于一个角色，角色可配置多个权限。权限由代码声明、应用启动时自动同步，权限管理页面只读；`super_admin` 是系统内置角色，始终拥有全部有效权限。用户、角色、权限管理均在“系统管理”下。

所有业务 API（健康检查、OpenAPI、Swagger 和登录接口除外）均要求 JWT，Resource 使用权限编码进行后端鉴权，前端据同一权限集合隐藏菜单和操作。详细设计见 [系统访问管理](docs/design/system-access-management.md)。

## 当前业务能力：目录、数据源与文件数据集

目录按业务范围隔离。数据源使用 `DATA_SOURCE` 目录树，文件数据集使用 `FILE_DATASET` 目录树；业务实体只保存可选的 `directoryId` UUID，目录树会统计直属和子树累计数量。JDBC 数据源支持 PostgreSQL、MySQL、Oracle、SQL Server、ClickHouse、达梦、人大金仓和 openGauss 的连接定义、真实连接测试、库/Schema、表、字段、主键、索引与数据预览；Kafka 集群与固定 Bucket/根目录的 S3 对象存储已支持登记和管理，客户端接入留待后续阶段。

文件数据集支持上传、查询、修改、替换内容、下载、删除、格式专属解析参数和样本预览。原始文件存入系统私有的 S3 兼容对象存储，记录只保存内部 Object Key，不向 API 返回存储地址或凭证；当前可真实解析 CSV、TSV、TXT、JSON、JSONL、XLS、XLSX、Parquet 和 Avro。CSV、TSV、TXT、JSONL 可使用 GZIP 外层压缩，GZIP 是压缩属性而不是文件格式。详细设计见 [通用目录管理](docs/design/directory-management.md)、[数据源管理](docs/design/data-source-management.md)、[文件数据集管理](docs/design/file-dataset-management.md) 和 [TSV、GZIP 与 Avro 设计](docs/design/file-dataset-tsv-gzip-avro.md)。

## 当前业务能力：模型管理

模型管理维护可被后续任务和数据服务引用的结构契约。模型绑定具有“数据存储”用途的数据源，记录预期物理位置和稳定的字段 UUID，支持草稿、发布、停用和启用状态。

模型可选择“新建物理表”或“绑定已有表”。绑定已有表时会在管理数据库事务外读取并映射表元数据，再用短事务保存模型和导入字段；字段物理结构继续由数据库维护，平台只允许维护字段名称、说明和展示排序。模型、任务和数据服务统一使用与 Spark SQL 语义对齐的平台标量类型，数据库双向映射只由方言实现，详见[模型平台数据类型设计](docs/design/model-data-type-system.md)。PostgreSQL、MySQL 与单机 ClickHouse `MergeTree` 支持根据字段定义生成受控建表 SQL；发布、重新启用和数据查询前都会实时校验物理表结构。模型详情提供固定 50 行的快速预览，以及字段白名单约束的筛选、排序和分页查询；两者都复用方言层的参数化标准查询链路，不允许任意 SQL。物理表修改统一先生成风险与前置检查明确的计划，再由技术用户确认执行；具体数据库边界见[模型物理表演进设计](docs/design/model-physical-table-evolution.md)。删除模型仍只删除元数据。详细交互见 [模型管理第一版](docs/design/model-management.md)。

## 当前业务能力：本地 SQL 任务

第一阶段已提供 `LOCAL_SQL` 手动任务：任务声明至少一个输入模型、一个输出模型，以及同一 JDBC 数据存储中的一条 `SELECT` 或只读 `WITH ... SELECT`。平台校验查询只有一个只读语句、读取结果列元数据并按结果列顺序生成 `INSERT INTO ... SELECT`；用户不能提交完整 DML/DDL 脚本。

任务定义支持草稿、发布和停用。发布或重新启用会检查输入/输出模型及物理表、查询输出字段别名和类型，并且不写入目标表。已发布任务可异步手动运行，运行记录保存不可变的无凭据快照、状态、耗时、影响行数和安全错误信息；每个任务同一时间只允许一个排队或运行实例。`OVERWRITE` 目前只对 PostgreSQL 开放事务性清空再写入，ClickHouse 第一阶段仅支持 `APPEND`。PostgreSQL 已有真实任务集成验收；其余方言当前仅验证 SQL 渲染。

主要接口包括：`/api/v1/tasks`、`/api/v1/tasks/{id}/definition`、`/api/v1/tasks/{id}/actions/{publish|disable|enable|run}`、`/api/v1/tasks/{id}/runs`。详细约束和已验证范围见[本地 SQL 任务定义](docs/design/local-sql-task-definition.md)与[开发计划](docs/design/local-sql-task-development-plan.md)。

## 当前业务能力：数据服务发布

第一版支持将一个已发布、物理表结构已校验的模型发布为一个标准数据服务。控制面管理 Service Engine 和数据服务定义；每个 Engine 是独立 JVM，使用独立 PostgreSQL 保存可恢复的部署快照，并在发布成功后动态注册实际的 `POST /open-api/v1/...` 路由。

公共接口使用统一的分页、列选择、过滤、排序、分组和聚合协议。请求会先基于已发布字段白名单编译为自有查询 AST，再由数据库方言编译为参数化 JDBC SQL；第一版不提供任意 SQL、脚本发布或自定义响应协议。详细设计及接口示例见[数据服务发布第一版](docs/design/data-service-publishing.md)。

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
export DATASCALPEL_FILE_STORAGE_ENDPOINT="http://home.superhuang.cn:9000"
export DATASCALPEL_FILE_STORAGE_BUCKET="datascalpel"
export DATASCALPEL_FILE_STORAGE_ACCESS_KEY="<access-key>"
export DATASCALPEL_FILE_STORAGE_SECRET_KEY="<secret-key>"
./mvnw -pl data-scalpel-admin -am package
java -jar data-scalpel-admin/target/data-scalpel-admin-0.1.0-SNAPSHOT.jar
```

文件对象存储使用 S3 协议；默认 Region 为 `us-east-1`、根前缀为 `data-scalpel`、path-style 为开启状态，均可通过 `DATASCALPEL_FILE_STORAGE_*` 环境变量覆盖。AccessKey 和 SecretKey 只应通过运行环境或不提交的本地 Profile 提供。

当前工作区可在根目录的 `config/application-local.yml` 中保存不提交的本地数据库连接，并使用本地 Profile 启动。Spring Boot 会从外部 `config` 目录读取该文件，它不会进入构建产物：

```bash
java -jar data-scalpel-admin/target/data-scalpel-admin-0.1.0-SNAPSHOT.jar --spring.profiles.active=local
```

也可以使用根目录下不提交的 `start-local.sh` 同时启动前后端。默认使用后端 `8080`、前端 `8887`；若端口已被占用，可临时指定备用端口：

```bash
BACKEND_PORT=18080 FRONTEND_PORT=18887 ./start-local.sh
```

### 启动 Service Engine

Service Engine 与 Admin 使用不同数据库。为 Engine 单独准备 PostgreSQL 库，再通过运行环境提供以下配置；不要把密码、管理 Token 或快照加密密钥提交到工程。

```bash
export DATASCALPEL_ENGINE_DB_URL="jdbc:postgresql://<engine-db-host>:5432/<engine-db-name>"
export DATASCALPEL_ENGINE_DB_USERNAME="<engine-db-user>"
export DATASCALPEL_ENGINE_DB_PASSWORD="<engine-db-password>"
export DATASCALPEL_ENGINE_CODE="dev-engine-01"
export DATASCALPEL_ENGINE_MANAGEMENT_TOKEN="<shared-admin-to-engine-token>"
export DATASCALPEL_ENGINE_ENCRYPTION_KEY="$(openssl rand -base64 32)"
./mvnw -pl data-scalpel-service-engine -am package
java -jar data-scalpel-service-engine/target/data-scalpel-service-engine-0.1.0-SNAPSHOT.jar
```

`DATASCALPEL_ENGINE_MANAGEMENT_TOKEN` 必须与 Admin 的同名环境变量一致。`DATASCALPEL_ENGINE_ENCRYPTION_KEY` 用于加密 Engine 数据库中的数据源连接快照；更换它会导致既有快照无法恢复，因此需要按密钥轮换流程重新发布服务。Engine 健康检查为 `GET /actuator/health`，控制面接口位于 `/internal/v1/**`，公共数据接口位于 `/open-api/v1/**`。

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
