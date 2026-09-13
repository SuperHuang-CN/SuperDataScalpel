# DataScalpel

DataScalpel 是面向企业或厅局、适合内网部署的全域数据管理平台。统一管理数据源、文件数据集、全景影像、码表、数据模型和资产目录，提供数据处理、服务发布与轻量 RBAC 等配套能力。

工程包含后端和独立的前端管理端：

- 后端：Spring Boot 多模块工程。
- 前端：`data-scalpel-ui`，React + TypeScript + Vite + Ant Design + AntV X6。

开发前先读 [工程约定](AGENTS.md)，再从 [开发文档入口](docs/README.md) 按任务选择模块规范与专题设计。

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
- `data-scalpel-business`：统一业务模块，内部按系统、数据源、模型、任务、数据服务和独立 MCP 平台等业务包组织。
- `data-scalpel-admin`：控制面启动模块、运行配置、OpenAPI、Actuator 和安全配置。
- `data-scalpel-service-engine`：可独立部署的数据服务运行面，保存部署快照，通过自身路由执行受控标准查询和参数化 SQL 查询，并通过内嵌 API Studio 管理数据源与 Groovy 脚本路由。
- `data-scalpel-task-sdk`：用户 Spark JAR 作业的轻量稳定公开 API；不依赖控制面、Canvas 或 Runner内部实现。
- `data-scalpel-task-sdk-testkit`：批/实时用户JAR的本地Spark测试工具，提供模型/JDBC写入捕获、假Kafka和StreamingQuery生命周期校验，不依赖Docker或平台内部实现。
- `data-scalpel-task-engine`：不依赖控制面模块的 Spark Canvas 编译与一次性 Runner实现，同时承载 Spark JAR SDK运行时适配；不负责业务排队和生命周期监管。
- `data-scalpel-task-dispatcher`：独立的持久化任务分发服务，通过 Kafka 接收提交/取消命令，并监管 Local Docker、YARN cluster 或 Kubernetes cluster 中的 Runner。
- `data-scalpel-filegdb`：不依赖现有业务模块和原生运行时的纯 Java 只读 FileGDB 解析库，包含本地来源和随机访问 SPI。
- `data-scalpel-filegdb-s3`：可选的同步 AWS SDK v2 S3 Range 来源适配器，不使用 CRT 或临时文件。
- `data-scalpel-shapefile`：运行时零第三方依赖的纯 Java 只读 Shapefile 解析库，支持本地来源和随机访问 SPI。
- `data-scalpel-shapefile-s3`：对已解包 SHP 组件执行 HeadObject 与条件 Range GET 的可选 AWS SDK v2 适配器。

业务实体统一继承 `BaseEntity`，使用 Java `UUID` 主键和 `createdAt`、`updatedAt` 字段。UUID 不绑定 PostgreSQL 专属列定义，由 Hibernate 根据数据库方言选择物理类型。

## 当前业务能力：全景影像

“数据管理 / 全景影像”支持成品上传、目录与列表管理、地图选点、360°浏览和统一资产登记。仅接收完整 360°×180°、2:1 的球形 JPEG，每张最多 100 MiB、宽度最多 32768 像素；原图保留原始字节，生成轻量预览。

替换保持资源 ID，成功后切换并回收旧文件；失败保留当前成品供浏览下载，不提供历史回滚。媒体访问需要登录和 `panorama.view`；已发布资产门户只展示安全摘要。XYZ 底图在系统配置的“全景地图设置”中维护，默认不请求底图。V1 图片处理按单 Admin 实例部署。

完整接口、元数据语义、权限和运行说明见 [全景影像管理 V1](docs/development/panorama-management-v1.md)。

## 当前业务能力：系统配置

系统配置位于“系统管理 / 系统配置”，由程序声明配置键、类型和说明，管理页面仅允许修改配置值，不支持任意新增、删除或改名。模型使用的数仓分层在“系统管理 / 数仓分层”独立动态维护，可配置推荐模型编码前缀和允许输入分层，不塞入字符串配置，也不扩展为通用字典框架。

当前内置配置：

- `platform.name`：前端品牌名称。
- `platform.subtitle`：前端顶部副标题。

接口需要有效 JWT：查询需要 `system.configuration.view`，修改需要 `system.configuration.update`：

- `GET /api/v1/system/configurations`：按统一 Search DSL 分页查询。
- `POST /api/v1/system/configurations/{id}/actions/update`：更新配置值。

列表接口返回稳定的 `PageResponse`（`content`、`totalElements`、`totalPages`、`page`、`size`），而非直接暴露 Spring Data 的 `Page` 序列化格式。详细设计见 [系统配置功能详细设计](docs/design/system-configuration.md)。

## DSH 接入

个人助手由独立 DSH 插件提供，Admin 通过 `/api/v1/dsh` 管理当前登录用户的工作区和会话。模型配置继续在 DSH 原生页面维护。顶部“AI 助手”打开全局侧边抽屉，支持个人会话新建、查询、重命名、归档恢复、流式对话、追问、停止执行及[上传附件和截图](docs/design/dsh-chat-attachments.md)。归档保留历史和工作文件；业务上下文和结果跳转留待下一批。见 [第四阶段第一批设计](docs/design/dsh-plugin-phase-four.md)。

## 接口响应与错误

成功响应直接返回明确 DTO、`PageResponse`、文件流或空响应，不使用通用成功包裹结构。所有 HTTP 错误响应统一采用 RFC 9457 `application/problem+json`，其中 `code` 是供前后端稳定识别的错误码，字段校验错误还会包含 `violations`。完整契约和实现约定见 [后端 API 响应与异常处理](docs/design/backend-api-response-and-error-handling.md)。

## 当前业务能力：系统访问管理

系统采用简单 RBAC：每个用户只属于一个角色，角色可配置多个权限。权限由代码声明、应用启动时自动同步，权限管理页面只读；`super_admin` 是系统内置角色，始终拥有全部有效权限。用户、角色、权限管理均在“系统管理”下。

所有业务 API（健康检查、OpenAPI、Swagger 和登录接口除外）均要求 JWT，Resource 使用权限编码进行后端鉴权，前端据同一权限集合隐藏菜单和操作。详细设计见 [系统访问管理](docs/design/system-access-management.md)。

## 当前业务能力：目录、数据源与文件数据集

目录按业务范围隔离。数据源使用 `DATA_SOURCE` 目录树，文件数据集使用 `FILE_DATASET` 目录树；业务实体只保存可选的 `directoryId` UUID，目录树会统计直属和子树累计数量。JDBC 数据源支持 PostgreSQL、HighGo、MySQL、Oracle、SQL Server、ClickHouse、达梦、人大金仓和 openGauss 的连接定义、真实连接测试、库/Schema、表、字段、主键、索引与数据预览。TDengine 以 WebSocket JDBC 和 RESTful JDBC 两种明确类型接入，共用只读超级表方言，只发现超级表并支持外部模型和 Canvas `JDBC_INPUT`，不管理子表或写入。HTTP API 数据源支持运行时 Token、签名、四种分页、异步提交/轮询、API 资源测试及 Canvas `HTTP_API_INPUT`；ArcGIS REST 与 OGC WFS 数据源支持服务发现、空间要素资源登记、Schema/属性预览和 Canvas `SPATIAL_SERVICE_INPUT`。Kafka 集群与固定 Bucket/根目录的 S3 对象存储已支持登记和管理，客户端接入留待后续阶段。

文件数据集支持上传、查询、修改、替换内容、下载、删除、格式专属解析参数和样本预览。原始文件存入系统私有的 S3 兼容对象存储，记录只保存内部 Object Key，不向 API 返回存储地址或凭证；当前可真实解析 CSV、TSV、TXT、JSON、JSONL、XLS、XLSX、Parquet、Avro、FileGDB 和 Shapefile。CSV、TSV、TXT、JSONL 可使用 GZIP 外层压缩，GZIP 是压缩属性而不是文件格式。FileGDB 和 Shapefile 使用 ZIP 作为上传容器，后台安全物化后通过 S3 Range Reader 生成独立表 Schema 与空间预览；一个 GDB ZIP 发现多张业务表，一个 SHP ZIP 固定生成一张表且同一 SHP 数据集可上传多个 ZIP。详细设计见 [通用目录管理](docs/design/directory-management.md)、[数据源管理](docs/design/data-source-management.md)、[空间服务数据源](docs/design/spatial-service-data-source.md)、[文件数据集管理](docs/design/file-dataset-management.md)、[空间文件数据集解析](docs/design/geospatial-file-dataset-parsing.md)和 [TSV、GZIP 与 Avro 设计](docs/design/file-dataset-tsv-gzip-avro.md)。

## 当前业务能力：数据标准

“数据标准 / 码表管理”用于动态维护业务枚举和分级代码。码表项直接采用多根、任意深度的树形结构，节点编码在整张码表内唯一；父节点也可以作为合法业务值，实际可用状态同时受码表、当前节点和全部祖先的启用状态约束。码表支持内容版本并发校验、树节点移动、Excel 多码表原子导入和前序导出，以及模型字段引用反查。

模型字段和常用字段模板字段可以绑定一张兼容且已启用的码表，物理业务表仍保存节点编码，不保存节点 UUID。码表绑定只属于业务元数据，不进入 DDL、物理表指纹或物理变更计划；已停用码表保留历史绑定并显示警告。被模型字段或模板字段引用后，码表编码、取值类型、节点编码和节点删除受到保护。查询需要 `standard.dictionary.view`，维护需要 `standard.dictionary.manage`；真实模型字段引用明细还要求 `model.view`。详细规则和接口见[树形码表管理](docs/design/standard-dictionary-management.md)。

## 当前业务能力：模型管理

模型管理维护可被后续任务和数据服务引用的结构契约。模型绑定具有“数据存储”用途的数据源，记录预期物理位置和稳定的字段 UUID，生命周期状态为草稿、已发布和已停用；草稿与已停用模型统一通过“发布”进入已发布状态。模型可以选择全局动态定义的数仓分层，也可以保持未分层；目录负责资产归属和导航，分层负责表达 ODS、DWD 等加工阶段。系统仅首次初始化 `ODS/DIM/DWD/DWS/ADS`，之后可按实际规范增删改、排序和启停；分层还可配置推荐模型编码前缀，以及 `UNRESTRICTED/ALLOW_LIST` 输入分层规划。输入规则第一版只配置和展示，不读取或限制任务关系。停用分层保留已有引用但不能新分配，被模型或其他分层规范引用的分层不能删除。模型管理还提供全局常用字段模板，支持单字段和字段组、自由分类、启停及可选码表；选用时只复制字段快照，后续模板变化不传播到模型，也不触发 DDL。

模型可选择“新建物理表”或“绑定已有表”。绑定已有表时会在管理数据库事务外读取并映射表元数据，再用短事务保存模型和导入字段；字段物理结构继续由数据库维护，平台允许维护字段名称、说明、展示排序和关联码表。模型列表通过统一“新建模型”菜单提供手动创建、从 JDBC 数据源表创建、从已解析文件数据集逻辑表创建和 Excel 模板导入。从 JDBC、文件数据集或 Excel 复制结构都创建 `MANAGED + DRAFT` 草稿，可在校对页设置或修正数仓分层和字段，不绑定来源、不导入数据，创建过程也不建表；后续可以显式查看 DDL 并建表，也可以在发布时自动创建缺失的受管物理表。文件数据集创建只读取管理库中的逻辑表 Schema，支持一批多表、三个并发和失败项重试；CSV/JSON/Excel 等样本推断类型会明确提示，Parquet/Avro/GDB/SHP 主要使用声明 Schema。普通新建、JDBC 和文件 Schema 创建会使用分层前缀生成可编辑编码候选，Excel 编码保持文件原值。V5 在每个模型行定义已有 `MODEL` 目录路径，一份文件可以覆盖多个目录；整份 Excel 只提交一次并在一个事务中原子创建，任意冲突都会整批回滚。Excel V1—V4 继续兼容，缺少目录列时按未分类处理并提示，缺少数仓分层或码表列时按未配置处理。模型列表可为 `MANAGED/EXTERNAL` 物理表手动采集行数与总占用空间快照，并支持最多选择 50 个模型、三个并发的非原子批量发布，单项失败不回滚其他模型。列表只读管理库缓存，刷新时由九种 JDBC 方言读取各自系统统计，保留精确/估算质量且不回退执行通用 `COUNT(*)`。模型、任务和数据服务统一使用平台类型契约，数据库双向映射只由方言实现，详见[模型平台数据类型设计](docs/design/model-data-type-system.md)。PostgreSQL、HighGo、MySQL、openGauss、人大金仓、达梦与单机 ClickHouse `MergeTree` 支持根据字段定义生成受控建表 SQL；PostgreSQL、HighGo、openGauss 与人大金仓共享完整的原表修改及事务型重建，MySQL 支持受控原表修改安全子集；Geometry 结构管理 V1 支持已安装 PostGIS 兼容扩展的 PostgreSQL 家族、MySQL 8.x InnoDB，以及将原始二维 WKB 存入 `String` 并用列 comment marker 声明空间语义的单机 ClickHouse，固定为 EPSG CRS 与 XY，且不创建空间索引。发布会实时校验物理表，缺失的受管表自动创建，外部表只校验、不创建。模型详情提供固定 50 行的快速预览、字段白名单约束的筛选查询，以及基于任务最后保存定义的关联任务列表；Geometry 与二进制字段默认不返回，也不能参与普通查询。PostgreSQL 家族 PostGIS 兼容 Geometry 另提供 MapLibre 无底图动态空间预览：PostGIS 将当前视口过滤、裁剪、转换和简化到 EPSG:3857，服务端用 Java2D 返回透明 PNG，浏览器不接收 Geometry；平台不创建空间索引，无可用 GiST/SP-GiST 索引时复用模型物理统计快照，仅允许行数不超过 50,000 的小表受限预览，统计为空时由页面提示手动刷新。物理表修改统一先生成风险与前置检查明确的计划，再由技术用户确认执行；已创建且包含 Geometry 的受管表第一版整体禁用物理结构变更。具体数据库边界见[模型物理表演进设计](docs/design/model-physical-table-evolution.md)和[空间字段结构管理 V1](docs/design/spatial-field-structure-management-v1.md)。删除模型仍只删除元数据。详细交互见 [模型管理第一版](docs/design/model-management.md)。

## 当前业务能力：任务管理

任务支持 `LOCAL_SQL`、批/实时 Canvas、模型质检以及批/实时 Spark JAR，类型创建后不可修改。`LOCAL_SQL` 任务声明至少一个输入模型、一个输出模型，以及同一 JDBC 数据存储中的一条 `SELECT` 或只读 `WITH ... SELECT`。平台校验查询只有一个只读语句、读取结果列元数据并按结果列顺序生成 `INSERT INTO ... SELECT`；用户不能提交完整 DML/DDL 脚本。

任务定义支持草稿、发布和停用。发布或重新启用会检查输入/输出模型及物理表、查询输出字段别名和类型，并且不写入目标表。任务详情统一按最后保存的 Local SQL 或 Canvas 定义展示输入、输出模型；该关系是只读投影，不从 SQL 文本猜测，也不包含运行历史。Local SQL 第一版仍拒绝包含 Geometry 的输入/输出模型并返回稳定的 `SPATIAL_FIELD_UNSUPPORTED`，不得把 Geometry 映射为 String/Binary；Spark Canvas 则通过 Sedona 支持具备 PostGIS 兼容扩展的 PostgreSQL、HighGo、openGauss、人大金仓与 MySQL 8 Geometry。已发布任务可异步手动运行，运行记录保存不可变的无凭据快照、状态、耗时、影响行数和安全错误信息；每个任务同一时间只允许一个排队或运行实例。`OVERWRITE` 对 PostgreSQL、HighGo、openGauss 与人大金仓开放事务性清空再写入，ClickHouse 仅支持 `APPEND`。PostgreSQL 已有真实任务集成验收；其余方言当前仅验证 SQL 渲染。

`SPARK_CANVAS` 与 `SPARK_STREAMING_CANVAS` 使用统一定义路由进入图形化编辑器，定义保存到 `task_canvas_definition`，并继续通过 Task Engine 做设计期零行 Spark 编译校验。Canvas 支持按模型 UUID 配置 `MODEL_INPUT` 和 `MODEL_OUTPUT`：输入使用不可修改的模型 code 作为逻辑表名，输出从目标模型解析数据源和物理位置。同一 `JDBC_INPUT` 可以按顺序选择一个数据源下的多张物理表，每张表仍以原始表名形成独立 Canvas 表。批处理 Canvas 还提供 `SQL_TRANSFORM`，用单条受控 Spark SQL 查询读取当前上游逻辑表 Map 并追加一张新表；临时视图仅在节点私有 Spark 子 Session 中存在。`TYPE_CAST` 可在 LONG Epoch 与 TIMESTAMP 之间按秒、毫秒或微秒转换（新规则默认毫秒），DATE 也可按 UTC 零点输出 Epoch LONG；还可按受控 Spark pattern 解析或格式化日期时间字符串。空间分析已覆盖 Geometry 派生与简化、最近要素、区域汇总、叠加、四类轨迹分析、格网聚合、DBSCAN 点聚类以及中心与离散统计；所有节点继续使用一条边携带完整逻辑表 Map。Canvas 发布、重新启用和运行准备会由 Admin 读取权威逻辑元数据；真实运行使用私有 MinIO Manifest 和 Kafka 可靠消息，由 Task Dispatcher 提交 Spark Runner。PostgreSQL、HighGo、MySQL、openGauss、人大金仓、达梦、Oracle 与 SQL Server 支持 JDBC 输入输出、UPSERT 和增量读取，PostgreSQL、HighGo、MySQL、openGauss 与人大金仓同时支持 JDBC Query Input，ClickHouse 支持普通 JDBC 输入输出；`MODEL_OUTPUT` 在批处理中支持 APPEND、OVERWRITE、UPSERT，在实时处理中支持 APPEND、UPSERT，UPSERT Key 自动使用目标模型完整主键，实时写入通过独立 `foreachBatch` 和 Checkpoint 按至少一次交付。Canvas 与 Manifest 的当前版本以代码常量为准，见 [协议版本定位](docs/README.md#协议版本定位)。

`SPARK_JAR` 用于 Canvas 不适合表达的复杂批处理。用户可下载 Java 21 Maven 初始工程，以 `provided` 方式依赖轻量 `data-scalpel-task-sdk` 和 Spark 4.1.1，实现 `SparkBatchJob` 后上传最大 100 MiB 的用户 JAR。模板以 `test` 作用域引入 `data-scalpel-task-sdk-testkit`，可用本地Spark、资源仿真和写入捕获直接执行 `mvn test`，TestKit不会进入最终JAR。任务通过大小写敏感的模型/JDBC绑定名访问已授权资源，SDK提供APPEND、OVERWRITE、UPSERT和影响行数累计。Local Docker、YARN和Kubernetes共用同一Runner；手动运行、Cron、取消、日志和历史记录继续使用现有闭环。详细设计见 [Spark JAR 任务与 SDK v1](docs/design/spark-jar-task-sdk-v1.md)。

批处理和实时JAR作业均可通过 `SparkJobContext.observability()`记录结构化事件、当前阶段、Counter、Gauge和Operation Timer。平台展示当前Attempt的最新快照，事件沿用任务 `console.log`；模型/JDBC写入及实时查询注册提供无需额外Spark Action的自动观测。TestKit可直接捕获并断言这些结果。

`SPARK_STREAMING_JAR` 面向复杂 Structured Streaming。用户实现 `SparkStreamingJob`，通过 SDK读取原始 Kafka 行并显式注册全部查询；用户代码控制 Trigger、Output Mode和业务Schema，平台分配查询名和Checkpoint、统一监控和停止。模型/JDBC可作为静态维表，并可在 `foreachBatch` 中执行 APPEND/UPSERT。启动时可继续最近Checkpoint或创建新世代，任一查询失败会停止整个Application；不支持Cron和自动重启。

任务管理下提供全局脱敏规则 CRUD 和 Canvas `MASK_FIELDS` Processor。全局规则没有启停、版本、历史或快照状态；选择规则时把当前执行定义复制进节点，保存、发布和运行均只依赖节点内嵌配置。再次编辑时使用普通规则详情接口辅助比较，规则修改或删除不会自动改变已有任务。主要接口包括：`/api/v1/tasks`、`/api/v1/tasks/{id}/definition`、`/api/v1/tasks/{id}/canvas-definition`、`/api/v1/tasks/{id}/model-relations`、`/api/v1/models/{id}/related-tasks`、`/api/v1/masking-rules`、定义更新 Action、生命周期 Action 和运行记录接口。详细约束见[本地 SQL 任务定义](docs/design/local-sql-task-definition.md)、[Canvas 任务定义](docs/design/canvas-task-definition.md)、[Canvas 真实执行设计](docs/design/canvas-task-execution.md)与[数据脱敏规则和字段脱敏节点](docs/design/data-masking-rules.md)。

## 当前业务能力：数据服务启停与网关发布

数据服务支持 `STANDARD_TABLE`、`SQL_QUERY` 和 `SCRIPT_API` 三种创建后不可切换的模式，并采用“创建基础草稿 → 按需配置服务定义”的可恢复流程。新增和修改基础信息统一使用 Drawer，保存后留在当前列表或详情；用户再从成功提示、列表或详情手动进入定义编辑器。列表和详情显示定义状态与版本，定义未完成时不能启用。Engine 可以独立修改但不会清空原定义。标准模式通过 Engine 感知、目录与筛选结合的服务端分页选择器绑定一个已发布模型；SQL 模式在当前 Engine 已就绪且声明 `SQL_SERVICE_QUERY` 能力的数据源下关联至少一个模型，当前支持 PostgreSQL、HighGo、MySQL、openGauss、人大金仓、达梦、Oracle、SQL Server 与 ClickHouse，并复用同一模型选择工作区，通过大型 Drawer 完成目录、名称/编码/物理表、状态、数仓分层筛选、服务端分页和跨页多选，不再一次加载固定上限的模型。SQL 左侧固定数据源上下文并在独立滚动区展示全部已选模型，模型字段结构在展开时按需加载；选择在 Drawer 确认后才写入定义，已有失效引用会保留并明确提示。数据服务详情、SQL 编辑、关联模型和血缘通过 `GET /api/v1/data-services/{id}/related-models` 一次读取有序模型摘要，避免逐模型详情请求。SQL 定义在启用时冻结只读、命名参数化 SQL 模板；脚本模式绑定当前 Engine 已就绪的默认 JDBC 数据源，通过共享的 API Studio 简化工作台编辑和调试 Groovy，并在启用时由 Engine 内嵌的 API Studio 按服务 ID 保存或覆盖脚本、注册 `POST /open-api/v1/...` 路由。

服务引擎列表可进入详情页，统一查看基础配置、绑定的数据服务、已注册数据源和访问策略；数据服务 Tab 展示控制面保存的服务及最近部署结果，不直接扫描 Engine 的实时路由。详情页可直接打开内嵌 API Studio 的 `/modern-ui/` 管理界面。

DataScalpel 引擎详情的“数据源” Tab 同时展示 API Studio JDBC 连接池摘要（使用中/上限、空闲、等待、长 SQL/长占用），点击占用数量或“JDBC 监控”可查看活动连接、SQL 占用、最近 SQL 聚合和饱和事件。首次进入读取一次，默认手动刷新，可选择每 5 秒刷新；关闭详情、离开 Tab 或浏览器进入后台后停止对应轮询。监控不可用与注册/同步状态分开显示，不修改注册状态，GeoServer 不展示这组指标。

监控通过 Admin 代理读取 Engine，沿用 `service.engine.view` 权限，浏览器不接触 Management Token；摘要仅返回当前引擎在 DataScalpel 中已注册的数据源。只读接口为 `GET /api/v1/service-engines/{engineId}/data-source-pools` 和 `GET /api/v1/service-engine-data-sources/{registrationId}/pool-monitor`，Engine 对应 `GET /internal/v1/data-sources/pool-summaries` 和 `GET /internal/v1/data-sources/{dataSourceId}/pool-monitor`，内部接口仍要求 Engine Token。Admin 的远程监控调用位于管理数据库事务之外，连接/读取超时分别为 2/4 秒；旧版 Engine 缺少接口时提示升级并重启，不影响注册管理。

JDBC 监控复用 API Studio 的动态连接池包装，覆盖单表、SQL 和脚本服务，不另建连接池或执行探测 SQL。指标是当前进程内存快照，连接池重建或引擎重启后清空；最近 SQL 默认是最近 200 次已完成执行的指纹聚合，饱和事件默认保留 20 条（同原因事件有限流），并非全量审计。默认长 SQL 阈值为 5 秒，长占用为 30 秒，可由 `spring.super-api-studio.datasource.monitor` 下的 `enabled`、`recent-query-capacity`、`incident-capacity`、`long-running-query-threshold`、`long-held-connection-threshold` 等现有 API Studio 配置调整。预览沿用 Studio 的 SQL 归一化和截断，不采集 JDBC 参数或查询结果，但不能作为完整的敏感信息脱敏保证；无 Studio API 执行上下文的单表/SQL 调用可能显示“未知调用方”。

服务引擎现在分为 `DATASCALPEL` 和 `GEOSERVER` 两种同级运行时。GeoServer Engine 由 Admin 直接调用 REST API，使用加密保存的管理凭据和默认 `datascalpel` Workspace，不经过普通 Service Engine。第四种数据服务 `SPATIAL_SERVICE` 只允许选择 GeoServer Engine，将满足唯一 Geometry、明确 XY/EPSG 和单字段主键约束的 PostGIS 物理表发布为只读 WMS/WFS；空间服务不进入现有 API Gateway。空间服务详情页提供基于已发布 WMS 的交互预览，以及点线面简单制图和安全 SLD 上传；样式以 DataScalpel 草稿为源，按独立版本同步到 Workspace 专属 Style，实际业务流量仍直连 GeoServer。详细约束见[GeoServer 空间服务发布 V1](docs/design/geoserver-spatial-service-publishing-v1.md)。

启用/停用管理 Engine 运行态；“发布/取消发布”独立管理已经启用的服务是否通过网关向调用方开放。系统支持 Kong OSS 和独立的 Super API Gateway Provider，调用方使用当前 Provider 的 Proxy 地址；取消发布会撤回全部历史网关绑定但保持 Engine 在线，停用则在安全撤回后继续移除 Engine，任一撤回失败时都不会停止 Engine。标准模式继续使用统一的分页、列选择、过滤、排序、分组和聚合协议；SQL 模式只接收启用快照声明的标量参数、分页和可选 count；脚本模式第一版固定为可信 Groovy、POST、静态路径和一个默认数据源，保留 API Studio 的数据库访问、事务、日志与 SQL Trace 能力，暂不提供沙箱、超时中断或多语言。脚本补全和草稿调试由 Admin 代理到目标 Engine，浏览器不会直接访问 Engine 或持有 Management Token。详细设计见[数据服务定义、Engine 启用与查询运行设计](docs/design/data-service-publishing.md)、[数据服务启停与网关发布设计](docs/design/data-service-gateway-publishing.md)和[Super API Gateway Provider 集成](docs/design/super-api-gateway-provider-integration.md)。

数据服务模块还提供与后台登录用户完全分离的 API Consumer 管理。Consumer 编码全局唯一且创建后不可修改；DataScalpel 保存 Consumer、API Key 和服务订阅主数据，通过职责单一的薄网关端口投影到当前网关。Kong 实现使用 `key-auth + acl`：API Key 只显示一次，Consumer 的全部有效 Key 共享服务订阅；受保护服务只有同时通过身份认证和服务 ACL 才能进入 Service Engine。当前不包含配额、限流和调用统计。详细设计见 [API 消费者、凭证与服务订阅管理](docs/design/api-consumer-management.md)和[API 消费者凭证与服务订阅开发计划](docs/design/api-consumer-service-subscription-development-plan.md)。

## 当前业务能力：MCP 在线开发平台

编辑器使用内网可用的本地 Monaco 资源；复杂 Schema 与全部测试样例无损保留，本地未保存草稿不被后台刷新覆盖。运行保护覆盖排队、编译、校验和结果转换，编译/Schema/发布缓存均有容量上限；协议使用 MCP SDK 无状态分发，队列繁忙保留 HTTP 429，调用审计只记录安全错误分类。Tool 列表使用摘要接口，详情日志支持服务端分页。

MCP 管理是独立业务域，不依赖数据服务、Service Engine、API Gateway、API Consumer、平台数据源或模型。用户可以在线创建多个 MCP Server，并在每个 Server 下使用 Groovy 开发多个 Tool；Input Schema 支持普通参数的可视化编辑和完整 JSON Schema，高级结构保留为 JSON 编辑，Output Schema 可选。草稿修改不会影响调用方，发布时会将 Server 和全部启用 Tool 固化为不可变快照并原子切换当前版本；相同定义重复发布不增加版本。

MCP 访问凭证独立于 Server 管理：一个 Bearer Token 可授权多个 Server，一个 Server 也可接受多个 Token。Token 使用 SHA-256 摘要认证，并以独立 AES-GCM 密钥加密保存原文，管理员可按需查看和复制。公开入口为 `POST /mcp/{serverCode}`，支持 `initialize`、`notifications/initialized`、`ping`、`tools/list` 和 `tools/call`，由 Admin 直接托管无状态 Streamable HTTP 请求。调用日志记录凭证名称快照与版本，但不保存参数、结果、Token 或脚本日志正文。旧 Server 级 Token 不迁移且全部失效，需要创建新凭证、授权 Server 并更新客户端。

Groovy Tool 是受信任的内部代码，能够使用任意 Java 能力；执行超时和有界队列只能控制正常脚本，不能构成安全沙箱，也无法阻止脚本创建线程、访问文件/网络/环境变量或终止 Admin。只有可信开发人员应获得 `mcp.update`、`mcp.execute` 和 `mcp.publish` 权限。完整接口、发布模型、运行边界和配置见 [MCP 在线开发平台](docs/design/mcp-platform.md)。

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
```

本地运行统一从工程根目录执行 `./start-local-dev.sh`，具体见 [本地开发与调试](#本地开发与调试)。

前端质量检查：

```bash
pnpm check
```

任务编排画布采用 AntV X6；其节点、连线和配置会保存为稳定的 JSON 定义，而不是 X6 内部对象。

## 启动

### 本地开发与调试

日常开发、Codex 主动功能自测、页面/接口验证和联调统一使用当前开发环境，需要启动时从工程根目录执行：

```bash
./start-local-dev.sh
```

启动前先检查当前工程实例的健康状态及是否已加载本次修改，能够复用时直接使用已有实例。端口占用时先核对进程归属，不自行换端口另起一套环境；需要重启时仍使用本脚本。除非用户明确要求隔离环境，不另建临时应用、数据库或 Mock 服务替代真实开发环境。

脚本为 Admin、Service Engine 和 Task Dispatcher 显式启用 `local` Profile，并加载根目录不提交的 `config/application-local.yml`；Task Engine 使用脚本指定的 properties 配置。默认端口为后端 `18080`、前端 `18887`、Service Engine `8081`、Task Engine `18091`、Dispatcher `18092`，以当前脚本及已有环境配置为准。不要为自测临时更换 Profile、数据库、Schema 或外部服务地址。

保留启动终端或会话，从其输出和健康检查结果排查失败，不绕过脚本单独启动服务。脚本收到 `Ctrl+C` 或退出时会停止它启动的子进程；调试结束后默认保留开发环境供用户继续使用。

各 Java 应用的运行依赖统一从当前 Maven Reactor 解析，项目模块直接使用 `target/classes`；不读取本地仓库旧快照 POM 来推导传递依赖，也不要求先执行 `install`。本地脚本使用 `maven.test.skip=true`，不执行或编译测试源码；新增业务依赖后重新运行脚本即可更新启动依赖列表。仅编译和准备启动依赖、不启动应用或连接业务外部服务时，可执行：

```bash
./start-local-dev.sh --prepare --threads 2
```

`--prepare` 不代表应用已启动或功能验证通过。是否执行测试遵循 [根开发约定](AGENTS.md#测试与验证暂时禁用)，Maven 隔离自动化测试仍使用各模块 `application-test.yml`。

本地脚本中的 Dispatcher 数据库连接与 Admin 解耦：默认复用 Admin JDBC URL 的 PostgreSQL 主机和查询参数，但数据库名固定为 `datascalpel`，Schema 默认使用 `dispatcher`。需要配置时使用独立的 `DATASCALPEL_TASK_DISPATCHER_DB_URL` 与 `DATASCALPEL_TASK_DISPATCHER_DB_SCHEMA`，不能通过 `DATASCALPEL_DB_URL` 间接改变 Dispatcher 数据库。

### Admin 独立部署

以下单服务启动命令用于独立部署与运维；当前工作区的功能调试使用上节的统一脚本。

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
export DATASCALPEL_MCP_CREDENTIAL_KEY="$(openssl rand -base64 32)"
./mvnw -pl data-scalpel-admin -am package
java -jar data-scalpel-admin/target/data-scalpel-admin-0.1.0-SNAPSHOT.jar
```

文件对象存储使用 S3 协议；默认 Region 为 `us-east-1`、根前缀为 `data-scalpel`、path-style 为开启状态，均可通过 `DATASCALPEL_FILE_STORAGE_*` 环境变量覆盖。AccessKey 和 SecretKey 只应通过运行环境或不提交的本地 Profile 提供。`DATASCALPEL_DATA_SOURCE_CREDENTIAL_KEY` 用于加密 HTTP API 数据源凭据，必须使用包含 16、24 或 32 字节的 Base64 密钥并在部署生命周期内稳定保存；丢失或直接更换会导致既有 API 凭据无法解密。


`DATASCALPEL_MCP_CREDENTIAL_KEY` 只用于加密 MCP 访问凭证明文。凭证认证同时使用 SHA-256 摘要，因此缺少密钥不会阻止 Admin 启动或已有凭证调用，但会阻止创建、轮换和查看完整 Token。该密钥必须包含 16、24 或 32 字节的 Base64 内容并稳定备份，直接更换会导致已有 Token 无法查看。`start-local-dev.sh` 内置独立且稳定的本地开发密钥，也可通过同名环境变量覆盖；该默认值不得用于正式部署。

Admin 还需要独立的 Service Engine 凭据加密主密钥，用于加密保存每台 Engine 各自的 Management Token：

```bash
export DATASCALPEL_SERVICE_ENGINE_CREDENTIAL_KEY="$(openssl rand -base64 32)"
```

该密钥必须包含 16、24 或 32 字节的 Base64 内容并稳定保存。丢失或更换后，Admin 无法解密已登记 Engine 的 Token，需要使用原密钥恢复，或重建开发数据并重新登记 Engine。Admin 不再配置全局共享的 Engine Management Token。

### 启动 Service Engine

Service Engine 与 Admin 使用不同数据库。为 Engine 单独准备 PostgreSQL 库，再通过运行环境提供以下配置；不要把密码或管理 Token 提交到工程。

```bash
export DATASCALPEL_ENGINE_DB_URL="jdbc:postgresql://<engine-db-host>:5432/<engine-db-name>"
export DATASCALPEL_ENGINE_DB_USERNAME="<engine-db-user>"
export DATASCALPEL_ENGINE_DB_PASSWORD="<engine-db-password>"
export DATASCALPEL_ENGINE_CODE="dev-engine-01"
export DATASCALPEL_ENGINE_MANAGEMENT_TOKEN="<this-engine-management-token>"
export DATASCALPEL_ENGINE_QUERY_MAXIMUM_FILTER_COUNT="50"
export DATASCALPEL_ENGINE_QUERY_MAXIMUM_FILTER_DEPTH="5"
export DATASCALPEL_ENGINE_QUERY_MAXIMUM_OFFSET="100000"
./mvnw -pl data-scalpel-service-engine -am package
java -jar data-scalpel-service-engine/target/data-scalpel-service-engine-0.1.0-SNAPSHOT.jar
```

每台 Service Engine 独立配置 `DATASCALPEL_ENGINE_MANAGEMENT_TOKEN`。在 Admin 新建或修改该 Engine 时，填写与这台 Engine 相同的 Token；Admin 使用 `DATASCALPEL_SERVICE_ENGINE_CREDENTIAL_KEY` 加密保存它，不存在全局 Token 回退。Engine 数据源由内嵌 API Studio 统一保存和恢复，当前沿用 API Studio 的明文密码存储方式，不再使用独立的快照加密密钥。Engine 健康检查为 `GET /actuator/health`，控制面接口位于 `/internal/v1/**`，公共数据接口位于 `/open-api/v1/**`。

内嵌 API Studio 管理工作台通过 `http://<engine-host>:<engine-port>/modern-ui/` 访问，本地默认为 `http://localhost:8081/modern-ui/`。首次访问需要初始化 API Studio 管理员，后续使用 API Studio 自带的账号和会话登录；浏览器不使用 Service Engine Management Token。

### 启动 Task Engine

Task Engine 使用 Spark 4.1.1、Apache Sedona 1.9.0 和 JDK `HttpServer` 独立运行，
不读取 Admin 数据库。构建分发包并设置独立 Bearer Token：

```bash
./mvnw -pl data-scalpel-task-engine -Ptask-engine-full-package package
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

默认 `package` 只生成普通 Task Engine JAR 和 Local Docker 所需的
`runner-local.jar`。只有 Yarn/Kubernetes Runner 或独立 Task Engine 分发包需要
`-Ptask-engine-full-package`；该 Profile 额外生成 `runner-cluster.jar`、分发目录、
ZIP 和 TAR.GZ。

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
- 数仓分层：`GET /api/v1/model-warehouse-layers`，响应包含编码前缀、允许输入分层及双向引用计数；维护使用对应 `POST .../actions/*` 接口
- 常用字段模板：`GET/POST /api/v1/model-field-templates`，详情和维护使用 `/{id}` 及对应 `POST .../actions/*`；选用只复制元数据快照

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

### 任务工作流

`WORKFLOW` 任务复用现有目录、发布、Cron 和运行历史；Business 按依赖推进 Local SQL、Spark Canvas、模型质检和 Spark JAR 批任务。支持并行、父子运行追踪和级联取消；应用重启后未结束流程标记失败，不恢复执行。定义、接口及升级说明见 [TaskWorkflow V1](docs/design/task-workflow-v1.md)。

## 系统 MCP

“系统管理 → 系统 MCP”提供独立的 API 开放清单、专用访问令牌和审计。标准 MCP 客户端通过 `/system-mcp` 使用 `api_search`、`api_describe`、`api_invoke` 操作已授权接口。首次部署默认关闭，令牌由超级管理员创建并绑定现有系统用户。

此入口与在线 MCP 开发平台独立，首版只支持 JSON 与空响应，不支持文件上传下载、二进制或持续流。配置、维护和连接方式见 [系统 MCP 说明](docs/design/system-mcp.md)。
