# Canvas 真实执行设计

## 1. 范围与调用链

已发布的 `SPARK_CANVAS` 可以手动运行，也可以由 Quartz 定时计划触发真实运行。JDBC 输入/输出支持 PostgreSQL 和 MySQL，HTTP/JSON API 支持只读输入：

```text
Admin → 私有 MinIO manifest + Kafka command → Task Dispatcher → Runner
Admin ← Kafka execution event ← Task Dispatcher ← Kafka runner event
                                      └─ Local Docker / YARN / Kubernetes
```

Admin负责读取权威元数据、调用 Task Engine最终预检、生成不可变 manifest，并原子持久化 `TaskRun + Kafka Outbox`。Task Engine只负责预检。Dispatcher使用自己的 PostgreSQL账本负责准入、调度、恢复和终态收敛；Runner负责一次真实 Spark作业，完成后退出。手动和定时触发复用同一条提交链路；定时 TaskRun 使用 `SCHEDULED + REAL`，并以 `(scheduleId, scheduledFireAt)` 幂等。`FORBID` 在存在活动实例时记录 `SKIPPED`，`ALLOW` 为每个触发点提交独立 Spark Application，不检查其他运行、任务或节点是否使用相同模型和物理 Sink。当前不支持自动重试和跨输出事务。

## 2. Manifest 边界

manifest 当前写出 `manifestVersion: 9`；Runner 兼容读取 v8，但 v8 不得携带
`JDBC_QUERY_INPUT` 或 `JDBC_OUTPUT UPSERT`；v7 及更早版本不再兼容。顶层分为 `execution`、`task`、
`metadataSnapshot`、`runtimeDataSources`、可空 `runtimeFileStorage` 和 `runtimeFileInputs`：

- `task.definition` 是原始稳定 Canvas 定义，绝不追加连接字段。
- `metadataSnapshot` 是 Admin 在发布、启用或运行时读取的权威表结构和模型快照；`models` 只服务 `MODEL_INPUT/MODEL_OUTPUT`，没有模型节点时必须是空数组。Kafka Value Schema 已内联在节点定义中，不进入模型快照。
- `metadataSnapshot.fileDatasetTables` 只包含文件表 ID、稳定 code、展示名、数据集类型、表/文件状态和按顺序排列的平台字段 Schema。
- `runtimeDataSources` 只包含当前定义实际引用的数据源，并以 `dataSourceId` 去重。
- JDBC URL、Catalog、Schema、用户名、密码和白名单连接参数，HTTP API 连接配置、运行凭据和被引用的 API 资源定义，以及外部 S3 的 endpoint、region、bucket、rootPrefix、pathStyleAccess 和凭据，仅位于 `runtimeDataSources`。
- 只有任务实际引用文件输入时才生成 `runtimeFileStorage`。`runtimeFileInputs` 按 Table ID 去重；表级保存数据集/表 ID、`schemaFingerprint`、权威 Schema 和强类型解析参数，每个输入再保存按当前顺序排列的来源列表。来源项保存稳定来源 ID、文件 ID、格式、压缩、存储形态、私有读取位置和来源键。

manifest第一阶段为明文 JSON，存放于私有 Bucket，便于排查。生产环境的对象存储 Endpoint 和短期预签名 URL必须使用 TLS；Bucket不得开放匿名读取。Admin向 Dispatcher只发送对象 Key和 manifest SHA-256；Dispatcher在真正提交时生成短期预签名 URL。对象路径固定为：

```text
task-runs/{runId}/attempts/1/manifest.json
task-runs/{runId}/attempts/1/result.json
task-runs/{runId}/attempts/1/console.log
```

密码、预签名 URL、完整 JDBC Properties、文件对象 Key、物化前缀、来源 Key和执行器临时路径不得进入日志、回执、`result.json`、Kafka终态事件或 `TaskRun` 字段。Manifest 不提供用户下载接口；Result 和 Console Log 由 Admin鉴权后代理下载，单次分别限制为 5 MiB和 20 MiB。对象保留期由 Bucket生命周期策略控制；运行最终事务失败时 Admin会最佳努力删除已经上传的孤立 Manifest，生命周期策略负责兜底。

## 3. Admin 准备与状态

发布、重新启用和每次手动运行都会在管理数据库事务外：

1. 读取当前 Canvas 定义快照。
2. 解析 Input/Output 引用的数据源、模型和物理表；Kafka 节点只解析数据源与 Topic，不解析模型。
3. 校验模型节点引用的模型发布状态和物理结构，以及数据源/API 资源启用状态、连接类别和 SOURCE/STORAGE/DISTRIBUTION 用途。
4. 读取真实 JDBC 表元数据、唯一键、模型字段和物理位置；`JDBC_QUERY_INPUT` 重新分析只读 SQL 并严格比较保存的 Hash 与字段快照；HTTP API 使用资源声明的输出 Schema；文件输入读取当前来源、平台 Schema 和 Schema 指纹，然后构建 `metadataSnapshot` 与有序来源 Manifest，不读取文件内容。
5. 调用 Task Engine 编译接口，要求 `valid=true`。
6. 在短事务中重新检查定义版本、模型 `schemaVersion/updatedAt/status` 和数据源 `updatedAt`，然后保存已经生成的文件来源快照。文件数据不执行二次版本比对。

运行时 Admin先上传 manifest，再在短事务内原子创建 `QUEUED` TaskRun 和 `SUBMIT_EXECUTION` Outbox。TaskRun保存独立的 executionRunId、executionId、attempt、deadline、计算引擎路由快照和三个对象 Key，不保存密码。Kafka暂时不可用时 Outbox保留并重试。

状态流转为：

```text
QUEUED → RUNNING → SUCCESS | FAILED | TIMED_OUT
                 → CANCEL_REQUESTED → CANCELLED
```

Dispatcher和Runner不反向调用 Admin HTTP。Runner向 Kafka发送状态，Dispatcher校验并落账后通过 Admin事件 Topic发布；Admin按 engineId、runId、executionId、attempt和递增 sequence幂等应用。Admin低频通过 Dispatcher HTTP查询长期未收敛执行，作为 Kafka事件丢失时的修复路径。

## 4. Dispatcher 执行管理

Admin允许主动查询和管理 Dispatcher，但正式提交与取消使用 Kafka。Dispatcher HTTP仅保留控制面查询：

```http
GET /api/v1/task-executions/{executionId}
```

取消命令与提交命令一样只通过 Kafka发送，不提供 Dispatcher HTTP取消旁路。

Dispatcher根据部署时固定的 Backend选择 Local Docker、YARN cluster或 Kubernetes cluster。默认并发和队列由计算引擎注册策略控制。动态预签名 URL只进入受限 launch文件，不写入日志或命令行。超时、取消、日志采集和重启恢复均由 Backend实现，Dispatcher在上传日志并校验 result后发布最终 Admin事件。

## 5. Runner 语义

Runner读取 launch描述、下载 manifest、校验 SHA-256、严格反序列化并验证执行身份和 deadline，然后使用 Backend提供的 Spark环境（Local为 `local[*]`，集群模式使用现有 SparkContext）。它先使用零行编译器再次验证完整 Canvas，再读取真实 JDBC Schema；所有 Input 和 Output计划准备完成前不发生写入。Compiler 与 Runner 通过同一个内置 Registry 调用同一组 Input、Processor、Output Operator，只分别注入零行无副作用 I/O 与真实运行 I/O。

- `JDBC_INPUT`：按 PostgreSQL Schema 或 MySQL Database 限定并引用物理表。含 Geometry 时，
  使用方言引用的受控查询执行 `ST_AsBinary`，Spark 读取 WKB 后通过 Sedona
  `ST_GeomFromWKB + ST_SetSRID` 生成 `GeometryUDT`。
- `JDBC_QUERY_INPUT`：只支持 PostgreSQL/MySQL 的单条 `SELECT` 或 `WITH ... SELECT`。Compiler
  只使用定义中保存的字段快照创建零行 BOUNDED Dataset；Runner 再次校验只读语法和 SQL Hash，
  使用只读 Session 执行受控 Spark JDBC 查询，并按名称、顺序、平台类型、类型参数和 nullable
  检查运行时 Schema。实时任务只在启动时读取并缓存一次，运行期间不刷新；日志和错误链不包含 SQL。
- `FILE_DATASET_INPUT`：仅用于批任务，使用 Manifest 的私有 S3 配置和逻辑表运行快照读取提交时的有序来源，输出以逻辑表 code 命名的 `BOUNDED` Dataset。CSV/TSV/TXT/JSON/JSONL/Parquet/Avro/SHP 对每个来源使用同一权威 Schema 与 FAILFAST Reader，并按 Manifest 顺序 `unionByName`，语义为 `UNION ALL`；Excel/GDB 当前仍为单物理文件下的 Sheet/图层来源。SHP/GDB Geometry 由现有受控 Reader 直接转换为带 EPSG SRID 的 JTS Geometry/Sedona `GeometryUDT`，不会先转成 JSON String；运行前重新校验 kind、CRS 和 dimension。覆盖、替换或删除会立即清理旧对象，因此旧任务允许以文件读取错误失败。
- `HTTP_API_INPUT`：按资源快照执行鉴权、签名、分页或异步轮询；每个结果页按最多 10,000 行分批转换，并立即通过 `DISK_ONLY` eager Local Checkpoint 物化到 Spark Executor 磁盘，全部批次成功后再按显式 Schema 合并为 DataFrame，以节点的 `outputTableName` 注册逻辑表。详细内存和失败语义见 [HTTP API 数据源第二阶段：分批读取设计](http-api-data-source-phase-two-batched-reading.md)。
- `MODEL_INPUT`：按模型快照的精确物理位置读取，以模型 code 作为逻辑表名。
- `KAFKA_INPUT`：直接使用节点内联 Value Schema 解析消息并生成无界表；运行时连接只来自 Manifest，不查询数据模型。
- `JOIN`：支持 INNER、LEFT、RIGHT、FULL；多个 EQUALS 条件固定使用 AND。
- `GEOMETRY_CONSTRUCT`：批流共用一个无状态 Operator，从 WKT/WKB/GeoJSON/X-Y 构造
  Geometry，显式设置 SRID，并在真实值解析失败或 kind 不匹配时返回稳定安全错误。
- `SPATIAL_TRANSFORM`：批处理使用 Sedona `ST_Transform` 显式转换 EPSG CRS；不修改 kind
  和 dimension，不接受用户 SQL。
- `GEOMETRY_VALIDATE`：批流共用 `ST_IsValid/ST_IsValidReason` 追加诊断字段，不过滤、修复
  或拒绝无效 Geometry。
- `GEOMETRY_REPAIR`：批流共用 `ST_MakeValid(geometry, false)` 追加通用 Geometry 修复字段；
  不覆盖原字段，真实值无法修复时任务失败。
- `GEOMETRY_BUFFER`：批流共用 Sedona Buffer 表达式追加 MultiPolygon；PLANAR 使用来源 CRS
  坐标单位，SPHEROID 仅允许 EPSG:4326 并使用米。
- `GEOMETRY_EXPLODE`：批流共用 `ST_Dump` 和 outer generator，将部件展开为多行；NULL 或
  Empty 输入保留一行，可选序号从 0 开始。
- `SPATIAL_MEASURE`：批流共用 Sedona Column API 执行平面或 WGS84 椭球测量，按配置顺序
  追加 DOUBLE 字段；节点不增加流式状态。
- `GEOMETRY_SERIALIZE`：批流共用 `ST_AsText/ST_AsBinary/ST_AsGeoJSON`，保留原 Geometry；
  GeoJSON 只允许 EPSG:4326。
- `SPATIAL_CLIP`：批处理使用 `ST_Intersects` INNER 候选连接与 `ST_Intersection`，过滤 NULL
  和 Empty 结果，只输出来源属性与裁剪字段；一个来源命中多个 Mask 时输出多行。
- `SPATIAL_AGGREGATE`：批处理对有界来源执行全局或分组 `ST_Union_Agg`、
  `ST_Intersection_Agg`、`ST_Collect_Agg` 或 `ST_Envelope_Agg`，每个结果独立恢复来源 SRID。
- `SPATIAL_JOIN`：批处理仅支持 INNER 和九种受控空间谓词，多条件固定使用 AND；两侧
  Geometry 的 CRS 与 dimension 必须一致。
- `MASK_FIELDS`：只使用节点内嵌 `definition`，通过 Spark 内置 `Column` 表达式原位替换配置字段；不查询或同步全局脱敏规则，批处理和流处理复用同一个无状态 Operator。
- `JSON_EXTRACT`：通过 Spark VARIANT `parse_json/try_parse_json` 和 `variant_get/try_variant_get` 从 STRING 字段追加结构化标量字段；Compiler 只分析零行计划，批处理和流处理复用同一个无状态 Operator。
- `MODEL_OUTPUT`：目标和字段来自模型快照，APPEND 可写受管或外部模型，OVERWRITE 只允许受管模型。
- `JDBC_OUTPUT`：BY_NAME/EXPLICIT 都由共享 Operator 使用 `select + alias + 显式 cast`。
  无 Geometry 时继续使用 Spark JDBC append；含 Geometry 时整行使用分区
  `PreparedStatement`，Geometry 经 `ST_AsBinary` 转为 WKB，再由目标库
  `ST_GeomFromWKB(?, databaseLocalSrid)` 写入。kind、CRS、dimension 必须完全一致。`UPSERT`
  必须完整选择目标主键或安全唯一索引，写入前拒绝 NULL Key 和当前 Dataset/micro-batch 内重复 Key；
  PostgreSQL 使用所选 `ON CONFLICT`，MySQL 使用 `ON DUPLICATE KEY UPDATE`。每分区独立事务，
  Streaming 通过 `foreachBatch` 提供键级重放收敛，整体仍是至少一次交付，不提供跨分区全局事务。
- `KAFKA_OUTPUT`：目标字段来自节点内联 Value Schema，BY_NAME/EXPLICIT 与其他 Output 复用映射和 Cast；可选 Key 字段来自上游表。
- `FILE_OUTPUT`：仅用于批任务，将来源表写到精确的 `s3a://{bucket}/{rootPrefix}/{targetPath}/`。CSV、JSON Lines 固定 UTF-8，普通 Parquet 固定 Snappy，并继续使用允许多个 `part-*` 和 `_SUCCESS` 的 Spark 目录数据集语义。Canvas `1.24` 的 Shapefile 使用 Driver 专用 Writer，通过 `toLocalIterator()` 流式生成唯一一套 SHP/SHX/DBF/PRJ/CPG；默认打成 ZIP，也可直接提交五个组件。Canvas `1.25` 的 GeoParquet 通过 Sedona 分布式写出 GeoParquet 1.1.0、WKB、显式 PROJJSON 和可选逐行 bbox；GeoJSON 使用 Driver 专用 Writer 生成唯一 RFC 7946 FeatureCollection，限 EPSG:4326 + XY 且达到 1.8GB 时失败。Shapefile/GeoJSON 制品先完整上传运行级临时前缀，再按冲突策略提交，`_SUCCESS` 始终最后写入；GeoParquet 复用 Spark/Hadoop 目录提交。S3 `OVERWRITE` 均不是原子替换。
- `OVERWRITE`：使用 JDBC `TRUNCATE TABLE` 后 append/受控批量 INSERT，不 drop/recreate。

Task Engine、Local Runner 和 Cluster Runner 在创建 SparkContext 前固定启用 Sedona 1.9.0、
Kryo serializer 和 `SedonaKryoRegistrator`；每个 child SparkSession 都调用
`SedonaContext.create(session)` 注册空间 SQL 与类型。Local Runner 与 Cluster Runner
同时携带 `geotools-wrapper 1.9.0-33.5`；Task Engine 额外携带排除 `gt-main` 的
`gt-shapefile 33.5`，为 Shapefile Writer 提供格式实现，GeoTools 核心类仍只有 wrapper
一套来源。它同时为 SPHEROID Buffer 提供 Sedona 所需的 CRS 运行能力。Runner 在任何
Output 写入前通过
`data-scalpel-dialect` 重新读取 PostGIS/MySQL 8 空间元数据，比较 Geometry kind、稳定
EPSG CRS、dimension 和数据库本地 SRID，漂移时返回 `SPATIAL_SCHEMA_DRIFT`。

空间执行第一阶段只支持 PostgreSQL/PostGIS、MySQL 8、EPSG 和 XY。Canvas `1.21` 的四个
空间基础 Processor 与 Canvas `1.22` 的修复、缓冲、拆分 Processor 同时支持有界和无界
Dataset，完整继承来源的事件时间和 Watermark；Canvas `1.23` 的空间裁剪与空间聚合只接受
BOUNDED Dataset，输出同为 BOUNDED 并清空事件时间和 Watermark。所有空间 Processor 的
Compiler 都只构造零行计划，不读取真实 Geometry。普通节点可以透明携带 Geometry，但普通
Join 条件、排序/分区键、聚合/分组键、去重键和 Geometry/标量 Cast 均被拒绝；CSV、JSON
Lines、普通 Parquet、Kafka 和 HTTP 等非空间 Output 不写出 Geometry。Shapefile、GeoParquet 和
GeoJSON 是明确空间格式：都只接受唯一 EPSG + XY Geometry；GeoJSON 进一步固定为
EPSG:4326，Shapefile 还必须符合选定的 Shape 类型。

SHP/GDB 空间输入第一阶段使用单 Spark 分区，支持继续连接 `SPATIAL_TRANSFORM`、
`SPATIAL_JOIN` 并最终写入 PostGIS/MySQL Geometry 表或 Shapefile。Shapefile 输出由 Driver
串行生成，不承诺大文件并行切分；仍不支持 GDB 文件输出。

Join 等 Processor 的兼容性只以共享 Operator 建立的真实 Spark 表达式及 Analyzer 结果为准，不按平台字段类型另建兼容矩阵或风险警告；Analyzer 接受即通过，拒绝才阻止任务。Output 继续使用专用的轻量转换风险策略：安全转换自动 Cast 且不提示，Spark 支持但可能因实际值失败的转换产生预检警告并继续发布/执行，Analyzer 不支持的 Cast 才阻止任务。Runner 保持 ANSI 模式，风险转换遇到非法值、溢出或精度问题时明确失败，不静默转成 null。

每个输出通过 `Dataset.observe` 在同一次写入计划中采集输出行数，不为日志或统计单独触发 `count()`。多个 Output 按稳定拓扑顺序执行，先前成功写入不会因后续失败回滚；不同目标表或不同数据源之间没有分布式事务。JDBC OVERWRITE 先执行 `TRUNCATE`；普通 Spark 文件输出由 Spark 处理目标目录，Shapefile 则先完成本地与 S3 临时制品，提交阶段才删除目标前缀。后续失败可能留下空目标或部分数据，因此真实运行不自动重试，页面在提交真实运行前必须明确提示该风险。

外部 S3 使用 `fs.s3a.bucket.<bucket>.*` 的 bucket 级 Hadoop 配置，不读取或覆盖平台 Spark 的默认 S3 连接。相同 bucket 在单次任务中不得出现 endpoint、region、path-style 或凭据不同的配置，也不得与平台文件输入存储发生同 bucket 配置冲突。执行身份至少需要目标前缀的列举、写入、删除和分片上传权限。

Runner 的 `result.json` 固定使用 `schemaVersion: 2`，Dispatcher拒绝 v1。所有 Input（包括 Kafka）、Processor 和 Output（包括 Kafka）分别以 `READ`、`PROCESS`、`WRITE` 阶段记录节点开始、成功或失败；失败结果保留此前已完成的节点，并让顶层错误与失败节点错误共享同一个诊断 ID。结构化错误包含稳定错误码、类别、可重试标记、节点身份、SQLState和诊断 ID，不包含异常堆栈。

HTTP API 请求在最终 URL、Header 和 Body 确定后签名，每页和每次重试重新生成时间戳、Nonce 和签名。OAuth2 Client Credentials 或自定义 Token Endpoint 的 Token 按有效期缓存；业务请求返回 `401/403` 时最多刷新并重试一次。同步分页支持页码、Offset/Limit、Cursor 和 Next URL；异步接口先提交并轮询，只有成功后才进入结果读取及分页。最大页数、行数、响应字节数、持续时间和重复游标/URL检测都是硬限制。

HTTP API 批次的 Local Checkpoint 用于限制 Driver 堆占用，不是容错 Checkpoint。Executor 本地块丢失时本次任务失败并由平台按整次任务处理。批次 Dataset 保留到所有下游 Output Action 完成，随后统一 `unpersist`；Runner 的 `spark.stop()` 负责最终兜底。任务取消仍由 Dispatcher 终止独立 Runner JVM、容器或集群 Application，不增加连接器级取消控制面；已经提交给远端 API 的异步任务不会因此自动取消。

控制台日志记录 `TASK_START/TASK_SUCCESS/TASK_FAILED` 和 `NODE_START/NODE_SUCCESS/NODE_FAILED`。文件 Input 的安全摘要只包含节点 ID、Table ID、table code、格式和字段数；空间文件 Output 摘要只记录来源表、Geometry 字段、空间类型/CRS/维度、格式选项、目标相对路径和冲突策略，不得记录 Geometry、坐标、属性值、完整 S3 URI 或临时路径；`MASK_FIELDS` 摘要只包含节点 ID、字段数量、策略类型以及全局来源和自定义规则数量，不得记录固定替换值、完整参数或节点配置；`JSON_EXTRACT` 摘要只记录来源/输出表、来源字段、提取数量、目标类型集合和失败策略，不得记录 JSON Path、JSON 内容或实际值。空间基础 Processor 摘要只记录表名、字段名、来源/格式、测量类型、CRS 和规则数量，不得记录 WKT、WKB、GeoJSON、坐标或测量结果。Geometry 修复、Buffer 和拆分节点的摘要只允许记录表名、字段名、模式、距离和 CRS；空间裁剪和空间聚合摘要只允许记录表名、字段名、聚合 kind、CRS 和规则数量，不得记录匹配数量、组大小、实际 Geometry、坐标、部件内容、裁剪/聚合结果或数据行。成功只表示读取或执行计划建立完成，不得额外触发 `count()`。节点失败只打印一次经过脱敏的异常链和调用栈，最大 64 KiB；任务级失败只打印摘要。密码、Secret、Token、Credential、Access Key、签名参数、预签名 URL、完整 JDBC Properties、文件对象 Key、物化前缀、来源 Key、临时路径、HTTP 响应业务数据、数据行、固定替换值、脱敏测试值和 SQL参数值必须被屏蔽。

## 6. 管理端运行观察

Canvas 与 LOCAL_SQL 共用任务运行记录和详情入口。活动状态包括 `QUEUED`、`RUNNING`、`CANCEL_REQUESTED`，详情页每 2 秒刷新；终态停止详情轮询，任务运行列表在无活动实例时降低到每 10 秒刷新。

`task_run` 为 Spark Canvas 保存结构化错误列：错误码、类别、可重试标记、阶段、SQLState、节点 ID/类型/名称和诊断 ID。现有 `message/error_detail` 继续服务 LOCAL_SQL 和通用错误表达。运行详情优先展示结构化错误，不展示 Java 调用栈；用户仍可下载原始 `result.json` 和经过脱敏的控制台日志。

活动 Canvas运行可通过以下 Action取消：

```http
POST /api/v1/task-runs/{runId}/actions/cancel
```

`CANCEL_REQUESTED`重复取消幂等；终态或不支持外部取消的运行返回 `409`。制品接口为：

```http
GET /api/v1/task-runs/{runId}/artifacts/result
GET /api/v1/task-runs/{runId}/artifacts/log
```

两个接口都要求 `task.view`，且只能读取 TaskRun固化的对象 Key，不接受客户端指定任意 Key。Tracking URL只在协议为 HTTP或 HTTPS时渲染为外部链接。

## 7. 构建与本地运行

`data-scalpel-task-engine` 同时生成 Daemon薄 JAR、分发包和两种 Runner制品：

```text
data-scalpel-task-engine-0.1.0-SNAPSHOT-runner-local.jar
data-scalpel-task-engine-0.1.0-SNAPSHOT-runner-cluster.jar
```

本地 `start-local-dev.sh` 会检查 Kafka、MinIO、Docker 和 Local Runner 制品，启动只负责预检的 Task Engine 及 Local Docker Dispatcher，并登记计算引擎。MinIO 或数据库位于宿主机时，应使用容器可访问的 `host.docker.internal` 或实际 DNS；系统不会自动改写数据源地址。
