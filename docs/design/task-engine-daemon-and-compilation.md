# Task Engine Daemon 与 Canvas 编译设计

状态：现行设计。开发约束见 [编译与执行规范](../development/task-engine.md)；当前协议版本及读写端见 [协议版本定位](../README.md#协议版本定位)。

## 1. 范围

`data-scalpel-task-engine` 提供彼此隔离的长期预检 Daemon和一次性 Runner构建产物：

- Java 21、Spark 4.1.1、Scala 2.13、Apache Sedona 1.9.0。
- 普通 Java Main 和 JDK `HttpServer`，不使用 Spring、Servlet、Thrift 或 gRPC。
- 默认以 `local[*]` 长期运行，也可由未来的 `spark-submit --master yarn --deploy-mode client` 启动同一个 Main。
- Canvas 当前协议以 Contracts 的 `CanvasDefinition` 常量为准；同大版本旧小版本可读，并在保存时规范化为当前小版本；不同大版本及未来小版本拒绝读取，不隐式迁移或覆盖原始定义。`JDBC_INPUT` 使用同一数据源下的有序多表配置，并可在每张表上保存高级读取参数。
- 同一大版本内只允许向下兼容的小版本增量；删除字段、改变既有节点语义等破坏性变化必须升级大版本并将小版本归零。
- 根据请求携带的元数据快照创建零行 DataFrame，只构造并分析 Spark 逻辑计划。
- 编译接口不连接 JDBC、不调用 Spark Action、不创建 `DataFrameWriter`。
- Daemon不包含真实执行、Docker、Kafka、MinIO或回执职责；Runner连接 PostgreSQL/PostGIS
  和 MySQL 8，并通过 WKB桥接 Sedona `GeometryUDT`。

模块保留在根 Maven reactor 中，不继承 Spring Boot parent；复用稳定 contracts 和
`data-scalpel-dialect`，不依赖 business、admin 或 service-engine。方言依赖只用于真实
Runner的标识符、空间目录和数据库本地 SRID 解析。

## 2. 结构与职责

```text
TaskEngineDaemon
├─ EngineConfiguration
├─ SparkRuntime
├─ TaskCompilationService
│  ├─ CanvasTaskCompiler
│  │  └─ CanvasGraphPlan
│  └─ SparkJarOnlineSourceCompiler
├─ CanvasNodeOperatorRegistry
│  ├─ ModelInputNodeOperator
│  ├─ JdbcInputNodeOperator
│  ├─ JdbcQueryInputNodeOperator
│  ├─ FileDatasetInputNodeOperator
│  ├─ HttpApiInputNodeOperator
│  ├─ SpatialServiceInputNodeOperator
│  ├─ KafkaInputNodeOperator
│  ├─ TdEngineTmqInputNodeOperator
│  ├─ JoinNodeOperator
│  ├─ GeometryConstructNodeOperator
│  ├─ SpatialTransformNodeOperator
│  ├─ GeometryValidateNodeOperator
│  ├─ GeometryRepairNodeOperator
│  ├─ GeometryBufferNodeOperator
│  ├─ GeometryExplodeNodeOperator
│  ├─ SpatialMeasureNodeOperator
│  ├─ GeometrySerializeNodeOperator
│  ├─ SpatialClipNodeOperator
│  ├─ SpatialAggregateNodeOperator
│  ├─ SpatialJoinNodeOperator
│  ├─ StreamJoinNodeOperator
│  ├─ RenameNodeOperator
│  ├─ FilterNodeOperator
│  ├─ SelectColumnsNodeOperator
│  ├─ DeriveColumnsNodeOperator
│  ├─ TypeCastNodeOperator
│  ├─ AggregateNodeOperator
│  ├─ UnionNodeOperator
│  ├─ DeduplicateNodeOperator
│  ├─ NullHandlingNodeOperator
│  ├─ ValueMappingNodeOperator
│  ├─ MaskFieldsNodeOperator
│  ├─ JsonExtractNodeOperator
│  ├─ WindowNodeOperator
│  ├─ TopNNodeOperator
│  ├─ ModelOutputNodeOperator
│  ├─ ModelSnapshotSyncOutputNodeOperator
│  ├─ JdbcOutputNodeOperator
│  ├─ JdbcSnapshotSyncOutputNodeOperator
│  ├─ KafkaOutputNodeOperator
│  └─ FileOutputNodeOperator
└─ TaskEngineHttpServer

TaskRunnerMain
└─ CanvasTaskExecutor
   └─ 同一个 CanvasNodeOperatorRegistry
```

主要包：

- `contract`：HTTP、任务、Canvas、元数据和编译结果的稳定 record/enum/sealed interface。
- `compiler`：请求并发、超时、取消和元数据索引。
- `canvas`：Compiler 与 Runner 共用的无状态节点 Operator、统一 Registry、执行上下文和预检/运行 I/O 端口。
- `compiler.canvas`：容错图分析、稳定拓扑编排、Schema 传播和编译问题汇总。
- `spark`：基础 SparkSession、请求级 child session、Job Group 和平台类型转换。
- `http`：认证、路由、10 MiB 请求限制、严格 JSON 和 Problem Detail。
- `config`：默认配置、外部 properties 和环境变量覆盖。
- `runner`：manifest 校验、真实 Spark JDBC 计划和结果生成。

第一阶段直接绑定 Spark，不提供计算引擎 SPI 或插件层。

## 3. Spark 生命周期与隔离

进程在创建 SparkContext 前固定 Kryo serializer 和 `SedonaKryoRegistrator`，使用
`SedonaContext.builder()` 创建唯一基础 `SparkSession` 和共享 `SparkContext`。每次编译：

1. 取得公平 Semaphore 许可。
2. 拒绝已经处于活动状态的相同 `requestId`。
3. 在编译线程创建 `baseSession.newSession()` 并调用 `SedonaContext.create(session)`。
4. 设置 `task-compilation-{requestId}` Job Group。
5. 按稳定拓扑序编译节点，并在节点之间检查取消和线程中断。
6. 完成后清理 Job Group 和活动请求，不关闭 child session，也不清共享 CacheManager。

只有 Daemon 退出时才停止基础 SparkSession。超时或显式取消会设置请求取消标志、取消对应 Job Group 并中断编译 Future。

默认 Spark 配置：

```properties
spark.master=local[*]
spark.app.name=DataScalpel Task Engine
spark.ui.enabled=false
spark.sql.shuffle.partitions=4
spark.sql.caseSensitive=true
spark.sql.ansi.enabled=true
spark.sql.session.timeZone=UTC
```

`SparkConf` 已有的 `spark.*` 属性优先，因此 `spark-submit` 注入的 master、application name 等不会被默认值覆盖。

## 4. HTTP API

### 4.1 健康检查

以下接口不认证：

```http
GET /health/live
GET /health/ready
```

`live` 表示 HTTP 进程可响应。`ready` 仅在基础 SparkContext 存在且未停止时返回 `200`，响应包含 Spark 版本、Application ID 和 master。

### 4.2 编译任务

```http
POST /api/v1/task-compilations
Authorization: Bearer <token>
Content-Type: application/json
```

请求顶层结构如下；示例版本号仅展示字段格式，请求须使用 Contracts 声明的当前大/小版本：

```json
{
  "requestId": "e1ec3ef6-170b-4dc4-bd22-87c482e7af4a",
  "task": {
    "type": "CANVAS",
    "definition": {
      "schemaVersion": 4,
      "schemaMinorVersion": 21,
      "nodes": [],
      "edges": []
    }
  },
  "metadataSnapshot": {
    "dataSources": [],
    "models": []
  }
}
```

JSON 未声明字段、未知任务类型、未知节点类型、非法 enum/UUID 和歧义元数据快照返回 HTTP `400`。配置缺失、图冲突或 Schema 冲突是可分析的业务结果，返回 HTTP `200` 和 `valid=false`。

响应将画布级问题放在 `canvasIssues`，每个定义节点按原数组顺序返回一个 `nodeResults` 条目。`valid` 仅在所有问题都没有 `ERROR` 时为 true。编译问题不写回 Canvas 定义。

### 4.3 Spark JAR在线源码编译

内部接口`POST /api/v1/spark-jar-source-compilations`与Canvas预检共享有界线程池、并发许可和30秒超时，但不会创建
SparkSession。请求只包含`requestId`和一个固定主类的完整Java源码。Task Engine通过JDK 21`JavaCompiler`使用受控的
Spark 4.1.1、Scala及DataScalpel SDK classpath编译，并额外生成不入JAR的类型检查类，验证公开主类、无参构造器和
`SparkBatchJob`契约。

Annotation Processor被禁用，编译过程不联网、不解析Maven坐标，也不加载或执行用户类。成功响应返回确定性小型JAR及
源码/JAR摘要；失败响应返回最多200条包含行列范围的诊断。

在线源码试运行复用同一编译接口。Business不把成功制品应用为当前JAR，而是创建`executionMode=TRIAL`的TaskRun并把临时JAR
保存到该次运行的制品目录。Manifest中的`sparkJarJob.executionPurpose`为`TRIAL`；Runner使用真实输入，在SDK Writer边界生成
最多100行的快速预览并跳过数据库写入。result schema v9的`trialPreview`由Dispatcher校验后留在固定结果制品中，不进入事件消息。

Canvas 节点试运行使用专用 `compileTrial` 入口，并继续复用同一节点 Registry 和 Operator。Business 根据目标节点计算上游闭包，
Manifest v27 的 `canvasTrial` 保存目标节点、逻辑表和字段选择；Runner 执行到目标节点后再投影字段并读取最多 101 行，Result v10
返回最多 100 行的 `canvasTrialPreview`。该入口只放宽目标 Input 作为终点以及目标 Processor 未消费提示，不改变正常 Canvas 编译规则。

### 4.4 取消

```http
POST /api/v1/task-compilations/{requestId}/actions/cancel
Authorization: Bearer <token>
```

活动请求返回 `202` 和 `CANCEL_REQUESTED`；不存在的活动请求返回 `404` Problem Detail。请求完成后可以再次使用相同 requestId。

### 4.5 HTTP 错误

所有 Engine HTTP 错误使用 `application/problem+json`，扩展字段固定为 `code` 和 `timestamp`。未预期异常记录完整堆栈，但响应不暴露类名、Spark 计划、元数据内容或文件路径。

### 4.6 Admin 编译网关

浏览器不直接访问 Task Engine。Canvas Designer 统一调用 Admin 的同路径网关：

```http
POST /api/v1/task-compilations
POST /api/v1/task-compilations/{requestId}/actions/cancel
```

两个接口都要求 `task.view` 权限。Admin 使用明确的 Java record、enum 和 Canvas 节点判别联合接收并转发契约，不依赖 `data-scalpel-task-engine` 模块，也不保存定义、编译结果或元数据快照。

Admin 每次请求都从系统配置 `task.engine.base-url` 读取地址，因而地址修改可以立即生效。Bearer Token 只从 Admin 进程环境变量 `DATASCALPEL_TASK_ENGINE_TOKEN` 读取，不返回浏览器，也不得写入日志。默认连接超时为 3 秒，请求超时为 35 秒。

Canvas Designer 不保留前端 Schema 传播或业务校验器。当前请求返回的 `canvasIssues`、节点 `issues`、`inputTables` 和 `outputTables` 是节点状态、配置选项和定义有效性的唯一来源；定义语义变化后旧结果立即失效，等待元数据和编译期间遮罩工作区。

Engine 返回的 `200 valid=false` 仍作为正常编译结果原样返回。`400/404/409/429/504` 保留状态和安全 detail；Engine 的认证失败、5xx、连接错误统一转为 `502 UPSTREAM_UNAVAILABLE`，响应读取超时转为 `504 UPSTREAM_TIMEOUT`。取消已经结束的请求得到 `404`，设计器将其作为最佳努力取消的可忽略结果。

## 5. 元数据快照

`metadataSnapshot` 是单次编译的临时输入，不属于 Canvas 定义。它只携带：

- 数据源 ID、启用状态、`JDBC` 连接类型和 `SOURCE/STORAGE/DISTRIBUTION` 用途。
- 数据源内的 `TABLE/VIEW`、物理表名、稳定平台字段 Schema 和可安全用于推荐的物理唯一键。
- 模型 UUID、code、名称、schemaVersion、状态、物理模式、数据源 UUID、精确物理位置、已保存
  字段 Schema 和按模型主键标记构造的逻辑主键。
- Kafka Value Schema 不属于元数据快照，直接来自 Canvas 节点的内联 `valueSchema`；Kafka 数据源快照只表达数据源状态、连接类型和用途。
- 不包含 URL、账号、密码或其他连接内容。

同一数据源 ID、同一数据源中的表名、同一模型 UUID、同一模型 code 或同一表/模型中的字段名重复会使快照产生歧义并返回 HTTP `400`。JDBC Input 可以读取 TABLE 或 VIEW；Output 目标必须是 TABLE。模型节点的完整快照规则见 [Canvas ModelInput 与 ModelOutput 设计](canvas-model-nodes.md)。

平台类型固定为：

```text
BOOLEAN BYTE SHORT INTEGER LONG FLOAT DOUBLE DECIMAL
STRING BINARY DATE TIMESTAMP TIMESTAMP_NTZ
```

DECIMAL precision 必须在 `1..38`，scale 必须在 `0..precision`；只有 STRING 可以设置正整数 length。

当前设计器从已经加载的数据源详情、普通 JDBC 表字段元数据和已保存模型字段组装临时快照，
只包含当前 Canvas 节点引用的数据源、表和模型。同一引用会合并，数据源用途保留真实的集合语义。
元数据尚在读取时暂停编译；读取失败时不提交残缺快照。该前端快照只用于本阶段零行预编译，
保存或执行任务时必须由 Admin 重建可信逻辑快照。

## 6. Canvas 图与 Schema 编译

图分析在 ID 校验完成前使用节点数组位置作为身份。依次检查协议版本、公共字段、节点/边 UUID、重复 ID、端点、自连接、重复方向边和节点度数，再使用 Kahn 算法得到稳定拓扑序。未进入拓扑序的节点标记 `CANVAS_CYCLE`。一个分支失败不会阻止无依赖分支，失败分支的下游得到 `UPSTREAM_INVALID`。

编译期表模型为：

```text
Map<tableName, SparkCanvasTable>
```

Map 按定义边顺序无覆盖合并。重复 Key 返回 `DUPLICATE_TABLE_NAME`。Schema、字段列表和表 Map 对处理器只读，处理器总是生成新对象。

### 6.1 JDBC_INPUT

按配置顺序和精确字符串在快照中查找启用的 JDBC SOURCE 数据源及全部已选物理表，将每张表的
平台 Schema 显式转成 Spark `StructType`，再分别创建零行 DataFrame。输出 Map 为每个物理
`tableName` 保留一个 Key；同一节点内重复 Key 返回 `DUPLICATE_TABLE_NAME`。

### 6.1.1 JDBC_QUERY_INPUT

检查 PostgreSQL/MySQL SOURCE 数据源、单条只读 SQL、规范化 SQL SHA-256、非空字段快照和
输出逻辑表名。字段名称必须唯一，类型参数必须合法，首版拒绝 Geometry。Compiler 只依据节点
保存的 `outputColumns` 创建零行 BOUNDED Dataset，不访问数据库；输出 Origin 为 `JDBC_QUERY`。
发布、重新启用和运行准备阶段不重新分析或比较查询结果 Schema；SQL Hash 变化仍要求重新分析
和保存。实时任务把真实查询结果作为启动时加载一次的静态维表。

### 6.2 MODEL_INPUT

按模型 UUID 查找已发布模型逻辑快照，校验数据源可读，将已保存模型字段转成零行 DataFrame。
不读取物理表做相等校验。输出 Map 只有模型不可修改 `code` 一个 Key；模型名称、物理表名和
数据源不参与 Key。

### 6.3 KAFKA_INPUT

检查启用且具有 `SOURCE` 用途的 Kafka 数据源、Topic、输出逻辑表名、首次启动位置、Value 格式和元数据字段选择。JSON 的内联 Value Schema 必须非空、字段名唯一且类型参数合法；TEXT/BINARY 固定产生 `value: STRING/BINARY`，不接受结构化 Schema。选中的 Kafka Key、Topic、Partition、Offset 和 Timestamp 使用固定 `_kafka_*` 字段名追加，重名和重复选择返回配置错误。Operator 只创建零行无界 Dataset Schema，不查询模型快照或 Topic 样本；Timestamp 不自动成为事件时间。

### 6.4 JOIN

取得两个直接上游表，检查左右表、输出表名、Join 类型和至少一个 EQUALS 条件。多个条件使用 Spark `Column.equalTo().and()`。左右表字段重名直接返回 `DUPLICATE_COLUMN_NAME`。

编译器实际调用零行 Dataset 的 `join` 构造逻辑计划，并从 Spark 输出 StructType 恢复字段顺序、扩展元数据和外连接可空性。支持 INNER、LEFT、RIGHT、FULL。输出 Map 保留所有输入表并新增 Join 结果表。

Processor 不维护平台类型兼容矩阵，也不按字段类型产生风险警告。Spark Analyzer 接受共享 Operator 建立的表达式时编译通过，Analyzer 拒绝时返回 `SPARK_ANALYSIS_ERROR`。

### 6.5 空间基础 Processor

`GEOMETRY_CONSTRUCT` 从 WKT、WKB、GeoJSON 或 X/Y 普通字段构造带明确 EPSG、XY 维度和
具体 GeometryKind 的 Sedona Geometry，并显式设置 SRID；非空畸形载荷或实际 kind 与声明
不一致时由 Runner 失败。`GEOMETRY_VALIDATE` 通过 `ST_IsValid` 和可选的
`ST_IsValidReason` 追加诊断字段，不删除、修复或拒绝无效 Geometry。

`SPATIAL_MEASURE` 按配置顺序追加最多 32 个 DOUBLE 测量字段，支持面积、长度、周长、
距离和 Point X/Y；平面模式使用原 CRS 单位，椭球模式只接受 EPSG:4326。
`GEOMETRY_SERIALIZE` 将 Geometry 追加序列化为 WKT、WKB 或 GeoJSON，其中 GeoJSON 只
允许 EPSG:4326，其他 CRS 必须先显式转换。

四个节点均保留输入表 Map 并以新逻辑表名追加结果，支持 BATCH 和 STREAMING，继承来源
表的有界性、事件时间与 Watermark，不引入流式状态。Compiler 只在零行 Dataset 上构造并
分析 Sedona 表达式；日志、安全摘要、编译响应和异常不得包含 Geometry、坐标、序列化内容
或测量结果等真实空间数据值。

### 6.6 MODEL_OUTPUT

检查来源逻辑表、目标模型、数据源 STORAGE 用途、物理模式、写入模式和显式字段映射。目标字段使用模型字段 code，与 JDBC_OUTPUT 复用同一个字段映射和显式 Spark Cast 实现。APPEND 可写受管或外部模型，OVERWRITE 只允许受管模型。UPSERT 仅支持 PostgreSQL/MySQL，Key 固定取模型按字段顺序声明的完整主键；Key 必须全部完成映射且不能是 Geometry。平台不读取物理表预检唯一约束。Batch 支持 APPEND/OVERWRITE/UPSERT；Streaming 支持 APPEND/UPSERT，并通过独立 `foreachBatch` 与 Checkpoint 按至少一次交付。

### 6.7 JDBC_OUTPUT

检查 sourceTableName、启用且具有 `DISTRIBUTION`（数据分发）用途的 JDBC 数据源、TABLE 目标、APPEND/OVERWRITE/UPSERT 和显式映射。共享 Operator 使用 Spark `select/alias/cast` 构造映射计划并触发 Analyzer；预检 I/O 只接收零行 Dataset，不创建 Writer。

UPSERT 只支持 PostgreSQL/MySQL，配置必须按数据库返回顺序完整匹配一组主键或安全唯一索引，
且所有 Key 都必须进入映射后的目标字段；Key 不得是自增、生成或 Geometry 字段。冲突时仅更新
已映射的非 Key 字段；仅 Key 映射时 PostgreSQL `DO NOTHING`，MySQL 执行无变化更新。MySQL
目标存在多组唯一键时返回 `MYSQL_UPSERT_MULTIPLE_UNIQUE_KEYS` 警告。Streaming 允许 APPEND
和 UPSERT，继续拒绝 OVERWRITE；运行时写入语义见 [JDBC_OUTPUT UPSERT 设计](canvas-jdbc-output-upsert-design.md)。

Output 专用字段转换策略分为安全、风险和不支持三类：可证明无损的扩大转换自动 Cast 且不提示；Spark 支持但可能受实际值、nullable、STRING length 或 DECIMAL 精度影响的转换自动 Cast 并产生警告；Spark Analyzer 不支持的 Cast 才产生错误。未映射来源字段直接忽略，目标必填字段缺失和重复目标映射仍是错误。该策略不得供 Processor 判断字段类型兼容性。

### 6.8 KAFKA_OUTPUT

检查来源无界表、启用且具有 `DISTRIBUTION` 用途的 Kafka 数据源、Topic、Value 格式、字段选择和可选 Key。新模式的 JSON 至少选择一个非 Geometry 字段；TEXT/BINARY 只能选择一个 STRING/BINARY 字段；Key 只能是 STRING/BINARY。Output 不在节点内改名、Cast 或拼装业务载荷，这些动作由前置 Processor 完成。Canvas 4.0～4.5 写入继续走旧版内联 Value Schema、映射和显式 Cast 路径。Compiler 不查询模型、不建立 Kafka Writer；Runner 才使用 Manifest 中的 Kafka 连接信息准备真实流式输出。

### 6.9 FILE_OUTPUT

仅接受有界来源表，以及已启用、连接类型为 S3、用途包含 `DISTRIBUTION` 的数据源。检查用户指定的相对目录、`FAIL_IF_EXISTS/OVERWRITE` 冲突策略和 CSV、JSON Lines、Parquet、Shapefile、GeoParquet、GeoJSON 的判别式格式参数。

Shapefile 是 FILE_OUTPUT 的正式格式。Compiler 额外检查 EPSG + XY Geometry、Shape 类型兼容、文件基础名和 `1..255` 个有序 DBF 属性映射，包括 10 位 ASCII 字段名、STRING UTF-8 字节宽度、数值宽度与受支持平台类型。Compiler 只使用零行 Dataset 和元数据 Schema，不读取 Geometry、不连接 S3、不建立 Writer。

Runner 使用 Manifest 的外部 S3 连接和 bucket 级 S3A 配置。普通格式继续使用 Spark Writer；Shapefile 通过 Driver 本地 GeoTools 33.5 Writer 和 `Dataset.toLocalIterator()` 生成唯一一套 ZIP 或五组件制品，上传运行级临时前缀后提交精确目标目录，最后写 `_SUCCESS`。GeoParquet 使用 Sedona 分布式写出，GeoJSON 使用受大小限制的 Driver FeatureCollection Writer。完整约束见 [FILE_OUTPUT Shapefile 输出设计](canvas-shapefile-output-design.md)、[GeoParquet 输出设计](canvas-geoparquet-output-design.md)和 [GeoJSON 输出设计](canvas-geojson-output-design.md)。

### 6.10 JDBC 与模型快照同步 Output

`JDBC_SNAPSHOT_SYNC_OUTPUT` 和 `MODEL_SNAPSHOT_SYNC_OUTPUT` 是正式快照同步节点，
只支持 BATCH、有界来源、一条入边和无出边。两个独立 Operator 只解析 JDBC 表或已发布
MANAGED 模型目标，字段映射、显式 Cast、Key 与删除策略校验统一由
`SnapshotSyncOperatorSupport` 完成；Compiler 仍只分析零行 Dataset，不连接目标数据库。

用户必须选择 `1..32` 个已映射目标字段作为复合 Key。数据库主键和安全唯一索引只用于推荐；
所选字段未匹配物理唯一键时返回 `SNAPSHOT_SYNC_KEY_NOT_DATABASE_UNIQUE` Warning，不阻止
编译。Key 不允许 Geometry、自增或生成字段；nullable Key 只产生编译 Warning，真实 NULL 和
来源/目标重复 Key 由 Runner 在运行时拒绝，错误不得包含实际 Key 值。

Runner 使用共享 `JdbcSnapshotSyncExecutor`：先把来源映射并 Cast 为目标 Schema，缓存并校验
来源；再以单条 JDBC 连接取得 PostgreSQL/MySQL 严格写锁，在同一事务读取目标并形成内部
INSERT/UPDATE/DELETE/UNCHANGED/RETAINED 分类。Geometry 使用 JTS/Sedona 拓扑相等；比较
所有已映射、可写的非 Key 字段，Key 永不更新。任何 DML 前必须完成空来源删除拦截以及最大
删除行数、比例保护，之后固定按 `DELETE → UPDATE → INSERT` 批量执行；任一步失败整体回滚。
实现不创建 Outbox、不保存内部 ChangeSet，也不向 Kafka 发送行级变化。

## 7. Runner 协议版本

Admin 与 Runner 的 `CURRENT_MANIFEST_VERSION` 必须一致；Runner 的 `ManifestVersionSupport`
只接受当前版本，不保证读取上一版本。受保护 `snapshotSyncLimits` 不进入 Canvas JSON，包含每侧最大行数、
来源与目标合计估算字节上限和锁等待秒数，默认分别为 `100000`、`256 MiB` 和 `30` 秒。

Runner 只按 `TaskExecutionResult.CURRENT_SCHEMA_VERSION` 写出 `result.json`。成功的 Snapshot Sync 节点在
`NodeExecutionResult.metrics` 写入 `kind: SNAPSHOT_SYNC` 及来源、目标、新增、更新、删除、
未变化、保留目标独有行数量；`rowsWritten` 等于新增、更新、删除之和，任务 `affectedRows`
继续汇总已提交 Output 的 `rowsWritten`。失败或事务回滚的节点不得携带成功指标。Dispatcher
按 `DispatcherTaskResult.supportsSchemaVersion` 读取其声明范围内的 Result，并执行对应版本的字段校验，
以收敛已运行任务；其兼容范围与 Runner 的写出版本分开维护。具体代码入口见 [协议版本定位](../README.md#协议版本定位)。

## 8. 配置与认证

优先级为“环境变量 > 外部 properties > 内置默认值”。外部配置由 `DATASCALPEL_TASK_ENGINE_CONFIG` 指定；分发脚本默认读取 `conf/task-engine.properties`。

主要环境变量：

```text
DATASCALPEL_TASK_ENGINE_HOST
DATASCALPEL_TASK_ENGINE_PORT
DATASCALPEL_TASK_ENGINE_TOKEN
DATASCALPEL_TASK_ENGINE_CONFIG
DATASCALPEL_TASK_ENGINE_MAX_CONCURRENCY
DATASCALPEL_TASK_ENGINE_ACQUIRE_TIMEOUT_SECONDS
DATASCALPEL_TASK_ENGINE_COMPILE_TIMEOUT_SECONDS
DATASCALPEL_TASK_ENGINE_JAVA_OPTS
```

Token 没有默认值且只允许从环境变量读取；为空时拒绝启动。所有 `/api/v1/**` 使用常量时间 Bearer Token 比较，健康检查不认证。

默认容量：最大并发 2、取得许可超时 10 秒、编译超时 30 秒、HTTP 线程 8。资源取得超时返回 `429`，编译超时返回 `504`，相同活动 requestId 返回 `409`。

## 9. 构建、分发与启动

```bash
./mvnw -pl data-scalpel-task-engine test
./mvnw -pl data-scalpel-task-engine -Ptask-engine-full-package package
```

默认 `package` 只生成普通薄 JAR 和 Local Docker 所需的 `runner-local.jar`。
显式启用 `task-engine-full-package` Profile 时，额外生成 `runner-cluster.jar`，
以及目录、zip、tar.gz 三种分发结果。分发内容为：

```text
data-scalpel-task-engine/
├─ bin/task-engine
├─ conf/task-engine.properties
├─ conf/log4j2.properties
├─ examples/*.json
└─ lib/*.jar
```

本地启动：

```bash
export DATASCALPEL_TASK_ENGINE_TOKEN='<engine-token>'
./bin/task-engine
```

未来 YARN client 方式使用相同 Main，Driver 和 HTTP Server 仍位于提交命令所在的固定服务器：

```bash
spark-submit \
  --master yarn \
  --deploy-mode client \
  --class cn.superhuang.datascalpel.taskengine.TaskEngineDaemon \
  lib/data-scalpel-task-engine.jar
```

Daemon始终只提供编译，readiness只检查基础 SparkContext。Runner由 Task Dispatcher提交，真实执行协议与安全边界见 [Canvas 真实执行设计](canvas-task-execution.md)和[执行平台开发文档](task-execution-platform/03-task-dispatcher-foundation.md)。
