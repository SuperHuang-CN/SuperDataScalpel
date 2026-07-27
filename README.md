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
- `data-scalpel-service-engine`：可独立部署的数据服务运行面，保存部署快照、动态注册公共路由，并以 JDBC 执行受控标准查询或冻结的参数化 SQL 查询。
- `data-scalpel-task-engine`：不依赖控制面模块的 Spark Canvas 预检服务；Daemon 只做零行 Dataset 编译校验，同时构建一次性 Runner 制品，但不调度或监管真实任务。
- `data-scalpel-task-dispatcher`：独立的持久化任务分发服务，通过 Kafka 接收提交/取消命令，并监管 Local Docker、YARN cluster 或 Kubernetes cluster 中的 Runner。
- `data-scalpel-filegdb`：不依赖现有业务模块和原生运行时的纯 Java 只读 FileGDB 解析库，包含本地来源和随机访问 SPI。
- `data-scalpel-filegdb-s3`：可选的同步 AWS SDK v2 S3 Range 来源适配器，不使用 CRT 或临时文件。
- `data-scalpel-shapefile`：运行时零第三方依赖的纯 Java 只读 Shapefile 解析库，支持本地来源和随机访问 SPI。
- `data-scalpel-shapefile-s3`：对已解包 SHP 组件执行 HeadObject 与条件 Range GET 的可选 AWS SDK v2 适配器。

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

目录按业务范围隔离。数据源使用 `DATA_SOURCE` 目录树，文件数据集使用 `FILE_DATASET` 目录树；业务实体只保存可选的 `directoryId` UUID，目录树会统计直属和子树累计数量。JDBC 数据源支持 PostgreSQL、MySQL、Oracle、SQL Server、ClickHouse、达梦、人大金仓和 openGauss 的连接定义、真实连接测试、库/Schema、表、字段、主键、索引与数据预览。HTTP API 数据源支持运行时 Token、签名、四种分页、异步提交/轮询、API 资源测试及 Canvas `HTTP_API_INPUT`；Kafka 集群与固定 Bucket/根目录的 S3 对象存储已支持登记和管理，客户端接入留待后续阶段。

文件数据集支持上传、查询、修改、替换内容、下载、删除、格式专属解析参数和样本预览。原始文件存入系统私有的 S3 兼容对象存储，记录只保存内部 Object Key，不向 API 返回存储地址或凭证；当前可真实解析 CSV、TSV、TXT、JSON、JSONL、XLS、XLSX、Parquet、Avro、FileGDB 和 Shapefile。CSV、TSV、TXT、JSONL 可使用 GZIP 外层压缩，GZIP 是压缩属性而不是文件格式。FileGDB 和 Shapefile 使用 ZIP 作为上传容器，后台安全物化后通过 S3 Range Reader 生成独立表 Schema 与空间预览；一个 GDB ZIP 发现多张业务表，一个 SHP ZIP 固定生成一张表且同一 SHP 数据集可上传多个 ZIP。详细设计见 [通用目录管理](docs/design/directory-management.md)、[数据源管理](docs/design/data-source-management.md)、[文件数据集管理](docs/design/file-dataset-management.md)、[空间文件数据集解析](docs/design/geospatial-file-dataset-parsing.md)和 [TSV、GZIP 与 Avro 设计](docs/design/file-dataset-tsv-gzip-avro.md)。

## 当前业务能力：模型管理

模型管理维护可被后续任务和数据服务引用的结构契约。模型绑定具有“数据存储”用途的数据源，记录预期物理位置和稳定的字段 UUID，支持草稿、发布、停用和启用状态。

模型可选择“新建物理表”或“绑定已有表”。绑定已有表时会在管理数据库事务外读取并映射表元数据，再用短事务保存模型和导入字段；字段物理结构继续由数据库维护，平台只允许维护字段名称、说明和展示排序。模型、任务和数据服务统一使用平台类型契约，数据库双向映射只由方言实现，详见[模型平台数据类型设计](docs/design/model-data-type-system.md)。PostgreSQL、MySQL 与单机 ClickHouse `MergeTree` 支持根据字段定义生成受控建表 SQL；其中 Geometry 结构管理 V1 支持已安装 PostGIS 的 PostgreSQL 和 MySQL 8.x InnoDB，固定为 EPSG CRS 与 XY，且不创建空间索引。发布、重新启用和数据查询前都会实时校验物理表结构。模型详情提供固定 50 行的快速预览、字段白名单约束的筛选查询，以及基于任务最后保存定义的关联任务列表；Geometry 与二进制字段默认不返回，也不能参与查询。物理表修改统一先生成风险与前置检查明确的计划，再由技术用户确认执行；已创建且包含 Geometry 的受管表第一版整体禁用物理结构变更。具体数据库边界见[模型物理表演进设计](docs/design/model-physical-table-evolution.md)和[空间字段结构管理 V1](docs/design/spatial-field-structure-management-v1.md)。删除模型仍只删除元数据。详细交互见 [模型管理第一版](docs/design/model-management.md)。

## 当前业务能力：任务管理

任务创建时可选择 `LOCAL_SQL` 或 `SPARK_CANVAS`，类型创建后不可修改。`LOCAL_SQL` 任务声明至少一个输入模型、一个输出模型，以及同一 JDBC 数据存储中的一条 `SELECT` 或只读 `WITH ... SELECT`。平台校验查询只有一个只读语句、读取结果列元数据并按结果列顺序生成 `INSERT INTO ... SELECT`；用户不能提交完整 DML/DDL 脚本。

任务定义支持草稿、发布和停用。发布或重新启用会检查输入/输出模型及物理表、查询输出字段别名和类型，并且不写入目标表。任务详情统一按最后保存的 Local SQL 或 Canvas 定义展示输入、输出模型；该关系是只读投影，不从 SQL 文本猜测，也不包含运行历史。Local SQL 和 Canvas 模型输入/输出第一版拒绝包含 Geometry 的模型并返回稳定的 `SPATIAL_FIELD_UNSUPPORTED`，不得把 Geometry 映射为 String/Binary。已发布任务可异步手动运行，运行记录保存不可变的无凭据快照、状态、耗时、影响行数和安全错误信息；每个任务同一时间只允许一个排队或运行实例。`OVERWRITE` 目前只对 PostgreSQL 开放事务性清空再写入，ClickHouse 第一阶段仅支持 `APPEND`。PostgreSQL 已有真实任务集成验收；其余方言当前仅验证 SQL 渲染。

`SPARK_CANVAS` 任务使用统一定义路由进入图形化编辑器，定义保存到 `task_canvas_definition`，并继续通过 Task Engine 做设计期零行 Spark 编译校验。Canvas 支持按模型 UUID 配置 `MODEL_INPUT` 和 `MODEL_OUTPUT`：输入使用不可修改的模型 code 作为逻辑表名，输出从目标模型解析数据源和物理位置。Canvas 发布、重新启用和手动运行会由 Admin重新读取权威表结构；真实运行使用私有 MinIO明文 manifest和 Kafka可靠消息，由 Task Dispatcher提交一次性 Spark Runner。第一期支持 Local Docker，YARN/Kubernetes使用 cluster模式扩展；数据源支持 PostgreSQL、MySQL、APPEND与 `TRUNCATE + APPEND`语义的 OVERWRITE，仍不支持定时计划和自动重试。Canvas Definition 兼容读取 `1.0/1.1`，新增模型节点使用 `1.1`，新保存和导出统一规范化为 `1.1`；增加小版本列并回填模型引用时执行 [Admin PostgreSQL迁移脚本](docs/operations/spark-canvas-admin-postgresql.sql)，无需重建任务、运行记录或 Canvas 定义表。

主要接口包括：`/api/v1/tasks`、`/api/v1/tasks/{id}/definition`、`/api/v1/tasks/{id}/canvas-definition`、`/api/v1/tasks/{id}/model-relations`、`/api/v1/models/{id}/related-tasks`、定义更新 Action、生命周期 Action 和运行记录接口。详细约束见[本地 SQL 任务定义](docs/design/local-sql-task-definition.md)、[Canvas 任务定义](docs/design/canvas-task-definition.md)与[Canvas 真实执行设计](docs/design/canvas-task-execution.md)。

## 当前业务能力：数据服务启停与网关发布

数据服务支持 `STANDARD_TABLE` 和 `SQL_QUERY` 两种创建后不可切换的模式。标准模式启用一个已发布且物理结构已校验的模型；SQL 模式先选择 PostgreSQL JDBC 数据源，再关联该数据源下至少一个任意状态的模型，并在启用时冻结只读、命名参数化 SQL 模板。控制面管理 Service Engine 和数据服务定义；每个 Engine 是独立 JVM，使用独立 PostgreSQL 保存可恢复的部署快照，并在启用成功后动态注册实际的 `POST /open-api/v1/...` 路由。

启用/停用管理 Engine 运行态；“发布”只把已经启用的服务创建或同步到当前网关。第一阶段的 Kong OSS 适配器创建受 DataScalpel 标签保护的 Service 和 Route，调用方使用网关 Proxy 地址；停用会先撤回全部历史网关绑定，任一撤回失败时保持 Engine 在线。标准模式继续使用统一的分页、列选择、过滤、排序、分组和聚合协议；SQL 模式只接收启用快照声明的标量参数、分页和可选 count。详细设计见[数据服务定义、Engine 启用与查询运行设计](docs/design/data-service-publishing.md)和[数据服务启停与网关发布设计](docs/design/data-service-gateway-publishing.md)。

数据服务模块还提供与后台登录用户完全分离的 API Consumer 管理。Consumer 编码全局唯一且创建后不可修改；DataScalpel 保存 Consumer、API Key 和服务订阅主数据，通过职责单一的薄网关端口投影到当前网关。Kong 实现使用 `key-auth + acl`：API Key 只显示一次，Consumer 的全部有效 Key 共享服务订阅；受保护服务只有同时通过身份认证和服务 ACL 才能进入 Service Engine。当前不包含配额、限流和调用统计。详细设计见 [API 消费者、凭证与服务订阅管理](docs/design/api-consumer-management.md)和[API 消费者凭证与服务订阅开发计划](docs/design/api-consumer-service-subscription-development-plan.md)。

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
export DATASCALPEL_TASK_ENGINE_TOKEN="<task-engine-token>"
export DATASCALPEL_COMPUTE_ENGINE_CREDENTIAL_KEY="$(openssl rand -base64 32)"
export DATASCALPEL_DATA_SOURCE_CREDENTIAL_KEY="$(openssl rand -base64 32)"
./mvnw -pl data-scalpel-admin -am package
java -jar data-scalpel-admin/target/data-scalpel-admin-0.1.0-SNAPSHOT.jar
```

文件对象存储使用 S3 协议；默认 Region 为 `us-east-1`、根前缀为 `data-scalpel`、path-style 为开启状态，均可通过 `DATASCALPEL_FILE_STORAGE_*` 环境变量覆盖。AccessKey 和 SecretKey 只应通过运行环境或不提交的本地 Profile 提供。`DATASCALPEL_DATA_SOURCE_CREDENTIAL_KEY` 用于加密 HTTP API 数据源凭据，必须使用包含 16、24 或 32 字节的 Base64 密钥并在部署生命周期内稳定保存；丢失或直接更换会导致既有 API 凭据无法解密。

当前工作区的本地功能测试和联调优先使用根目录的 `config/application-local.yml`。其中可以保存不提交的数据库、Kong 等本地连接；Spring Boot 使用 `local` Profile 从该外部文件读取配置，它不会进入构建产物：

```bash
java -jar data-scalpel-admin/target/data-scalpel-admin-0.1.0-SNAPSHOT.jar --spring.profiles.active=local
```

也可以使用根目录的 `start-local-dev.sh` 启动开发环境。脚本会为 Admin、Service Engine 和 Task Dispatcher 显式启用 `local` Profile 并加载上述配置；Task Engine 不是 Spring Boot 应用，不读取该 YAML。默认使用后端 `18080`、前端 `18887`；若端口已被占用，可临时指定备用端口：

```bash
BACKEND_PORT=18080 FRONTEND_PORT=18887 ./start-local-dev.sh
```

本地脚本中的 Dispatcher 数据库连接与 Admin 解耦：默认复用 Admin JDBC URL 的 PostgreSQL 主机和查询参数，但数据库名固定为 `datascalpel`，并强制使用 `dispatcher` Schema。需要覆盖时使用独立变量，不能通过 `DATASCALPEL_DB_URL` 间接改变 Dispatcher 数据库：

```bash
export DATASCALPEL_TASK_DISPATCHER_DB_URL="jdbc:postgresql://localhost:5432/datascalpel?currentSchema=dispatcher"
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
export DATASCALPEL_ENGINE_QUERY_MAXIMUM_OFFSET="100000"
./mvnw -pl data-scalpel-service-engine -am package
java -jar data-scalpel-service-engine/target/data-scalpel-service-engine-0.1.0-SNAPSHOT.jar
```

`DATASCALPEL_ENGINE_MANAGEMENT_TOKEN` 必须与 Admin 的同名环境变量一致。`DATASCALPEL_ENGINE_ENCRYPTION_KEY` 用于加密 Engine 数据库中的数据源连接快照；更换它会导致既有快照无法恢复，因此需要按密钥轮换流程重新启用服务。Engine 健康检查为 `GET /actuator/health`，控制面接口位于 `/internal/v1/**`，公共数据接口位于 `/open-api/v1/**`。

### 启动 Task Engine

Task Engine 使用 Spark 4.1.1 和 JDK `HttpServer` 独立运行，不读取 Admin 数据库。构建分发包并设置独立 Bearer Token：

```bash
./mvnw -pl data-scalpel-task-engine package
export DATASCALPEL_TASK_ENGINE_TOKEN="<task-engine-token>"
./data-scalpel-task-engine/target/data-scalpel-task-engine-0.1.0-SNAPSHOT-distribution/data-scalpel-task-engine/bin/task-engine
```

Task Engine只提供 Canvas预检，不启动或监管真实任务。真实执行由独立 Task Dispatcher负责：

```bash
./mvnw -pl data-scalpel-task-dispatcher,data-scalpel-task-engine -am package
export DATASCALPEL_TASK_DISPATCHER_TOKEN="<dispatcher-token>"
export DATASCALPEL_TASK_DISPATCHER_RUNNER_JAR="$(pwd)/data-scalpel-task-engine/target/data-scalpel-task-engine-0.1.0-SNAPSHOT-runner-local.jar"
java -jar data-scalpel-task-dispatcher/target/data-scalpel-task-dispatcher-0.1.0-SNAPSHOT.jar
```

Task Engine默认监听 `8091`，Dispatcher本地默认监听 `18092`。两者健康检查均为 `GET /health/live`、`GET /health/ready`；Task Engine只保留 `POST /api/v1/task-compilations`。Admin使用同一个 `DATASCALPEL_TASK_ENGINE_TOKEN` 访问 Engine，并从系统设置 `task.engine.base-url` 动态读取地址；Canvas Designer只调用 Admin网关，不接触 Engine Token。完整契约见 [Task Engine Daemon 与 Canvas 编译设计](docs/design/task-engine-daemon-and-compilation.md)和[Canvas 真实执行设计](docs/design/canvas-task-execution.md)。

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
