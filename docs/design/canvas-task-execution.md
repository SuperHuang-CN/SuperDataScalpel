# Canvas 真实执行设计

## 1. 范围与调用链

已发布的 `SPARK_CANVAS` 可以手动运行，也可以由 Quartz 定时计划触发真实运行。普通标量 JDBC 读取和 APPEND 覆盖 PostgreSQL、HighGo、MySQL、openGauss、Kingbase、Oracle、SQL Server、ClickHouse 和达梦；HTTP/JSON API 支持只读输入：

```text
Admin → 私有 MinIO manifest + Kafka command → Task Dispatcher → Runner
Admin ← Kafka execution event ← Task Dispatcher ← Kafka runner event
                                      └─ Local Docker / YARN / Kubernetes
```

Admin负责读取权威元数据、调用 Task Engine最终预检、生成不可变 manifest，并原子持久化 `TaskRun + Kafka Outbox`。Task Engine只负责预检。Dispatcher使用自己的 PostgreSQL账本负责准入、调度、恢复和终态收敛；Runner负责一次真实 Spark作业，完成后退出。手动和定时触发复用同一条提交链路；定时 TaskRun 使用 `SCHEDULED + REAL`，并以 `(scheduleId, scheduledFireAt)` 幂等。`FORBID` 在存在活动实例时记录 `SKIPPED`，`ALLOW` 为每个触发点提交独立 Spark Application，不检查其他运行、任务或节点是否使用相同模型和物理 Sink。当前不支持自动重试和跨输出事务。

同一次零行预检还基于 Spark Catalyst `analyzed LogicalPlan` 生成静态血缘预览。Input 属性只携带资源 UUID、Schema 版本、字段 UUID或安全哈希；每个 Output 从完成映射和 Cast 的 Writer 前 Dataset 独立反向追踪。该过程不触发 Action、不访问运行数据，也不改变执行计划。任务发布和重新启用把编译结果转换为不可变血缘快照并与任务状态原子提交；运行、重跑、实时启动、Checkpoint 与 Offset 不更新静态血缘。任务详情固定选择一条输出链路，字段级按输入和输出资产组织表卡片，字段行连接到中间任务节点；默认展示该链路前 20 个输出字段并支持最多 50 个多选，不会把不同输出链路合并。详细契约见[血缘接入契约 V3](lineage-integration-contract.md)。

JDBC 能力按节点而不是全局数据库白名单判定：

| 数据库 | 普通读取 | APPEND | OVERWRITE | UPSERT | Query Input | 增量输入 | 快照同步 | Geometry JDBC |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| PostgreSQL/HighGo/openGauss/Kingbase | 是 | 是 | 是 | 是 | 是 | 是 | 是 | 是（需 PostGIS 兼容扩展） |
| MySQL | 是 | 是 | 是 | 是 | 是 | 是 | 是 | 是（需 MySQL 8 空间能力） |
| Oracle/SQL Server/达梦 | 是 | 是 | 是 | 是 | 否 | 是 | 否 | 否 |
| ClickHouse | 是 | 是 | 是 | 否 | 否 | 否 | 否 | 否 |
| TDengine | 保持现有超级表/TMQ 规则 | 否 | 否 | 否 | 否 | 否 | 否 | 否 |

模型质检开放上述数据库的普通标量读取；Spark JAR 资源绑定开放普通标量读取和 APPEND。特殊能力仍在对应节点或 SDK 写入边界返回稳定配置错误，不再由发布阶段返回整张数据库产品白名单。

## 2. Manifest 边界

Canvas 4.46 的事件窗口指标仍完全位于 `task.definition`，不新增 Manifest/Result 字段或 HTTP API。
`TRACK_DETECT_INCIDENTS` 在确定轨迹片段之后，通过 Spark ROWS 窗口计算节点内临时指标，再判断开始/结束条件；
将左闭右开偏移转换为 Spark 的闭区间 `[start,end-1]`。指标只读取原字段，不互相形成计划链。
Compiler 同一路径只解析零行惰性计划，不启动 Job、Checkpoint、缓存或外部读取；Runner 仍按整个节点记录生命周期，
安全摘要只新增指标数量，不记录窗口偏移、别名或条件字面量。逐输出血缘从 Catalyst 追溯原字段，不把临时指标登记为来源。
这项能力不等同任意 Arcade 脚本，窗口函数与 NULL 规则见[事件检测设计](canvas-spatial-next-processors/track-detect-incidents.md#8-446-受控字段窗口条件)。

manifest 当前写出 `manifestVersion: 28`；Runner 严格只读取 v28，不保留旧版本兼容分支。v28 仅增加保留厂商身份的 `HIGHGO` 运行时数据库枚举；发布升级前需要处理完旧版本在途任务。
顶层分为 `execution`、`task`、
`metadataSnapshot`、`runtimeDataSources`、可空 `runtimeFileStorage`、`runtimeFileInputs` 和
`snapshotSyncLimits` 和可空的 `canvasTrial`：

- `task.definition` 是原始稳定 Canvas 定义，绝不追加连接字段。
- `metadataSnapshot` 是 Admin 在发布、启用或运行时生成的逻辑编译元数据；`models` 服务
  `MODEL_INPUT/MODEL_OUTPUT/MODEL_SNAPSHOT_SYNC_OUTPUT`，字段来自已保存模型字段，按模型主键
  标记生成逻辑主键元数据。它用于 Schema 传播、Spark 计划和字段解析，不是运行时物理相等契约。
  没有模型节点时必须是空数组。Kafka Value Schema 已内联在节点定义中，不进入模型快照。
- `metadataSnapshot.fileDatasetTables` 只包含文件表 ID、稳定 code、展示名、数据集类型、表/文件状态和按顺序排列的平台字段 Schema。
- `runtimeDataSources` 只包含当前定义实际引用的数据源，并以 `dataSourceId` 去重。
- JDBC URL、Catalog、Schema、用户名、密码和白名单连接参数，HTTP API 连接配置、运行凭据和被引用的 API 资源定义，以及外部 S3 的 endpoint、region、bucket、rootPrefix、pathStyleAccess 和凭据，仅位于 `runtimeDataSources`。
- 只有任务引用 `TDENGINE_TMQ_INPUT` 时，对应运行数据源才增加 `tdEngineTmqConnection`，直接由保存的主机、端口、账号、密码和 `useSSL` 组装，不反向解析 JDBC URL。
- 只有任务实际引用文件输入时才生成 `runtimeFileStorage`。`runtimeFileInputs` 按 Table ID 去重；
  表级保存数据集/表 ID、兼容保留但不参与运行门禁的 `schemaFingerprint`、逻辑 Schema 和强类型
  解析参数，每个输入再保存按当前顺序排列的来源列表。来源项保存稳定来源 ID、文件 ID、格式、
  压缩、存储形态、私有读取位置和来源键。
- `snapshotSyncLimits` 由 Admin 部署配置生成，默认限制每侧 100,000 行、来源与目标合计估算 256 MiB、锁等待 30 秒；限制不进入 Canvas JSON。
- `canvasTrial` 只出现在批处理 Canvas 试运行，保存目标节点、目标逻辑表和按原 Schema 顺序排列的返回字段；普通运行、实时 Canvas、模型质检和 Spark JAR 必须为空。
- `streaming` 保存唯一无界输入节点的触发间隔；JDBC 增量输入还保存来源节点、来源签名和可选的跨定义版本初始 Offset。该 Offset 只在新版本没有自身 Checkpoint 时生效，同版本始终以 Spark Checkpoint 为事实来源。

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
2. 解析 Input/Output 引用的数据源、模型和物理位置；Kafka 节点只解析数据源与 Topic，不解析模型。
3. 校验模型发布状态，以及数据源/API 资源启用状态、连接类别和 SOURCE/STORAGE/DISTRIBUTION 用途。
4. 普通 `JDBC_INPUT/JDBC_OUTPUT` 读取当前表元数据和唯一键以建立计划；模型节点直接使用已保存
   模型字段；`JDBC_QUERY_INPUT` 使用保存的字段快照并校验 SQL Hash；HTTP API 使用资源声明的
   输出 Schema；文件输入读取当前来源和平台 Schema，然后构建 `metadataSnapshot` 与有序来源
   Manifest，不读取文件内容，也不比较历史与当前物理 Schema。
5. 调用 Task Engine 编译接口，要求 `valid=true`。
6. 在短事务中重新检查定义版本、模型 `schemaVersion/updatedAt/status` 和数据源 `updatedAt`，然后保存已经生成的文件来源快照。文件数据不执行二次版本比对。

运行时 Admin先上传 manifest，再在短事务内原子创建 `QUEUED` TaskRun 和 `SUBMIT_EXECUTION` Outbox。TaskRun保存独立的 executionRunId、executionId、attempt、deadline、计算引擎路由快照和三个对象 Key，不保存密码。Kafka暂时不可用时 Outbox保留并重试。

状态流转为：

```text
QUEUED → RUNNING → SUCCESS | FAILED | TIMED_OUT
                 → CANCEL_REQUESTED → CANCELLED
```

Dispatcher和Runner不反向调用 Admin HTTP。Runner向 Kafka发送状态，Dispatcher校验并落账后通过 Admin事件 Topic发布；Admin按 engineId、runId、executionId、attempt和递增 sequence幂等应用。Admin低频通过 Dispatcher HTTP查询长期未收敛执行，作为 Kafka事件丢失时的修复路径。

节点试运行复用同一 TaskRun、Dispatcher、Runner、取消、日志和制品链路，执行模式为 `TRIAL`。提交接口接收当前已应用的内存 Canvas 草稿及已保存定义版本，Admin 校验并发版本后只保留目标 Input/Processor 的上游闭包，所有 Output 和无关分支都不进入 Manifest，也不准备输出凭据。Runner 完整计算闭包，到达目标节点后按请求字段投影目标逻辑表并读取第 101 行判断截断；接口最多返回 100 行、总计 4 MiB。试运行不保存定义、不写外部目标、不发布血缘，`affectedRows` 固定为空。实时 Canvas、Output 目标、已发布任务及存在活动运行时拒绝提交。

## 4. Dispatcher 执行管理

Admin允许主动查询和管理 Dispatcher，但正式提交与取消使用 Kafka。Dispatcher HTTP仅保留控制面查询：

```http
GET /api/v1/task-executions/{executionId}
```

取消命令与提交命令一样只通过 Kafka发送，不提供 Dispatcher HTTP取消旁路。

Dispatcher根据部署时固定的 Backend选择 Local Docker、YARN cluster或 Kubernetes cluster。默认并发和队列由计算引擎注册策略控制。动态预签名 URL只进入受限 launch文件，不写入日志或命令行。超时、取消、日志采集和重启恢复均由 Backend实现，Dispatcher在上传日志并校验 result后发布最终 Admin事件。

## 5. Runner 语义

Runner读取 launch描述、下载 manifest、校验 SHA-256、严格反序列化并验证执行身份和 deadline，
然后使用 Backend提供的 Spark环境（Local为 `local[*]`，集群模式使用现有 SparkContext）。它先
使用零行编译器再次验证完整 Canvas，再按逻辑 Schema 构造真实读取与写入计划；不比较运行时
物理 Schema 与保存快照是否相等。所有 Input 和 Output计划准备完成前不发生写入。Compiler 与
Runner 通过同一个内置 Registry 调用同一组 Input、Processor、Output Operator，只分别注入零行
无副作用 I/O 与真实运行 I/O。

- `JDBC_INPUT`：按配置顺序为同一数据源下的每张已选物理表分别建立 Dataset，并使用对应数据库
  方言限定和引用物理表；节点输出一个以原始表名为 Key 的有序 Map。实际读取按“运行 Manifest 连接与凭据 →
  数据源 JDBC Properties → 当前表 `readOptions` → 平台最终 `dbtable` → `load()`”应用参数；高级参数只影响
  对应表，不能覆盖连接、目标表或分片参数。Compiler 不连接数据库或执行 `sessionInitStatement`，日志、节点摘要、
  Spark 选项脱敏和错误摘要均不得输出读取参数值。普通标量读取支持能力矩阵中的 JDBC 数据库；
  Geometry 仅允许具备 PostGIS 兼容扩展的 PostgreSQL/HighGo/openGauss/Kingbase 和 MySQL 8，使用方言引用的受控查询执行 `ST_AsBinary`，
  Spark 读取 WKB 后通过 Sedona `ST_GeomFromWKB + ST_SetSRID` 生成 `GeometryUDT`。
- `JDBC_QUERY_INPUT`：只支持 PostgreSQL/HighGo/MySQL/openGauss/Kingbase 的单条 `SELECT` 或 `WITH ... SELECT`。Compiler
  只使用定义中保存的字段快照创建零行 BOUNDED Dataset；Runner 再次校验只读语法和 SQL Hash，
  使用只读 Session 执行受控 Spark JDBC 查询，不比较运行时结果 Schema。实时任务只在启动时读取
  并缓存一次，运行期间不刷新；日志和错误链不包含 SQL。
- `SQL_TRANSFORM`：仅支持批任务。每个节点为当前完整上游表 Map 创建独立 Spark 子 Session，
  将各表的已分析 LogicalPlan 重新绑定并注册为节点本地临时视图，再执行一条受控 `SELECT` 或
  `WITH ... SELECT` 并把分析后的结果计划重新绑定回执行 Session。视图在 `finally` 清理，多个
  SQL 节点即使使用相同逻辑表 code 也不会冲突。Parser/Analyzer 只允许当前 Canvas 表与本查询
  CTE，拒绝 DDL、DML、多语句、外部 Catalog、文件/JDBC Relation、显式视图操作和 TVF；不触发
  Spark Action。节点安全摘要只记录 SQL SHA-256、长度、引用的安全逻辑表 code、输出表名和字段数，
  不记录 SQL 正文或 Literal。
- `FILE_DATASET_INPUT`：仅用于批任务，使用 Manifest 的私有 S3 配置和逻辑表运行快照读取提交时
  的有序来源，输出以逻辑表 code 命名的 `BOUNDED` Dataset。CSV/TSV/TXT/JSON/JSONL/Excel
  使用保存 Schema 做显式解析和类型转换；Parquet/Avro 按逻辑字段名投影并 Cast；SHP/GDB 按
  逻辑字段名取值，忽略额外字段，缺失标量字段产生 `null`，唯一要素 Geometry 映射到配置的
  Geometry 字段。各来源按 Manifest 顺序 `unionByName`，语义为 `UNION ALL`。Runner 不比较文件
  指纹或整表字段数量、顺序、类型及 Geometry 元数据；实际值或 Geometry 无法转换时按真实解析
  错误失败。覆盖、替换或删除会立即清理旧对象，因此旧任务允许以文件读取错误失败。
- `HTTP_API_INPUT`：按有序资源选择逐项执行鉴权、签名、分页或异步轮询；每个结果页按最多 10,000 行分批转换，并立即通过 `DISK_ONLY` eager Local Checkpoint 物化到 Spark Executor 磁盘，全部选择项完成配置与元数据校验后才开始读取，并以各自 `outputTableName` 注册逻辑表。详细内存和失败语义见 [HTTP API 数据源第二阶段：分批读取设计](http-api-data-source-phase-two-batched-reading.md)。
- `MODEL_INPUT`：按模型快照的精确物理位置读取，以模型 code 作为逻辑表名。
- `KAFKA_INPUT`：JSON 使用节点内联 Value Schema 和 `FAILFAST` 解析；TEXT 按 UTF-8 输出固定 STRING `value`，BINARY 输出原始 BINARY `value`。选中的 Kafka 元数据按固定 `_kafka_*` 字段追加，tombstone 行保留且元数据仍可用；运行时连接只来自 Manifest，不查询数据模型或自动识别格式。
- `TDENGINE_TMQ_INPUT`：通过内置 Spark DataSource V2 MicroBatch Source 订阅 WebSocket TMQ，
  以 Spark Checkpoint 保存每个 VGroup 的下一条待读 Offset。每个微批单 Consumer、单
  InputPartition，关闭自动提交且 `commit(end)` 不提交 TMQ Offset；消息只映射为超级表字段和 TAG。
  Offset 过期、VGroup 改变、Topic v2 结构指纹变化或 Schema 不匹配均明确停止，不自动跳过。可选
  事件时间必须显式选择 TIMESTAMP 字段并同时设置 Watermark，不自动猜测。Consumer Group 使用
  `taskId + sourceNodeId + outputNodeId + writeId` 的稳定身份；旧 Group 由管理端后台非阻断清理。
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
  坐标单位，SPHEROID 仅允许 EPSG:4326 并使用米。Canvas 4.52 可从数值字段或受控确定性
  逐行表达式计算距离；NULL 动态距离输出 NULL，非正或非有限实际值稳定失败。
- `GEOMETRY_EXPLODE`：批流共用 `ST_Dump` 和 outer generator，将部件展开为多行；NULL 或
  Empty 输入保留一行，可选序号从 0 开始。
- `SPATIAL_MEASURE`：批流共用 Sedona Column API 执行平面或 WGS84 椭球测量，按配置顺序
  追加 DOUBLE 字段；4.50 可逐项换算距离/面积输出单位，缺失单位保持旧结果；节点不增加流式状态。
- `GEOMETRY_SERIALIZE`：批流共用 `ST_AsText/ST_AsBinary/ST_AsGeoJSON`，保留原 Geometry；
  GeoJSON 只允许 EPSG:4326。
- `SPATIAL_CLIP`：批处理使用 `ST_Intersects` INNER 候选连接与 `ST_Intersection`，过滤 NULL
  和 Empty 结果，只输出来源属性与裁剪字段。4.77 的 `DISSOLVE_ALL` 先按计划内来源行 ID 聚合
  全部相交 Mask，再对每条来源裁剪一次；重叠 Mask 不重复覆盖区域，分离片段保留为一个 Multi 结果。
  `PAIRWISE` 及缺失/null 保持每条 Mask 独立输出的旧语义。4.51 的
  `SOURCE_FAMILY_2D` 使用 `ST_Force2D + ST_CollectionExtract + ST_Multi` 过滤低维接触并输出
  来源对应的 Multi 家族；缺失/null 与旧版策略继续输出通用 Geometry。
- `SPATIAL_AGGREGATE`：批处理对有界来源执行全局或分组 `ST_Union_Agg`、
  `ST_Intersection_Agg`、`ST_Collect_Agg` 或 `ST_Envelope_Agg`，每个结果独立恢复来源 SRID。
  Canvas 4.53 可为单 UNION 启用 Dissolve All/List，追加来源要素计数和受控标量统计，并通过
  `ST_Multi` 或 `ST_Dump` 输出 Multipart/Singlepart；无结果 Geometry 的组不生成要素。Canvas 4.61
  可在无分组字段时显式按 Polygon/MultiPolygon 的 `ST_Intersects` 边及 GraphFrames 连通分量分别融合；
  NULL/Empty 不进入连通图，Compiler 不运行图作业。
- `SPATIAL_JOIN`：批处理支持 INNER，以及 Canvas 4.56 起保留全部左侧目标要素的 LEFT；LEFT 未匹配时
  右侧投影字段为 NULL，RIGHT/FULL 仍不支持。九种受控空间谓词多条件固定使用 AND；两侧
  Geometry 的 CRS 与 dimension 必须一致。Canvas 4.55 可附加至多八组非 Geometry 属性等值条件，
  与全部空间条件按 AND 组合并使用 Spark SQL 普通等号；缺失/null 保持原空间匹配。Canvas 4.54
  可按显式来源侧投影、排除、改名和排序左右字段，通过限定列 `select + alias` 解决同名字段；
  缺失/null 投影保持旧版全字段和同名拒绝语义。Canvas 4.57 可显式设置
  `joinOperation=JOIN_ONE_TO_MANY`；缺失/null 也保持相同的一对多执行语义，即输出每个匹配组合。
  Canvas 4.58 的 `JOIN_ONE_TO_ONE` 可汇总全部匹配记录，输出 Join Count 和至多 32 项忽略 NULL 的
  `SUM/MIN/MAX/MEAN/STDDEV`；也可按 FIRST、数值最大/最小或日期最新/最旧保留一条。保留模式必须提供
  显式稳定排序，完整排序仍并列时以 `SPATIAL_JOIN_KEEP_ORDER_NOT_UNIQUE` 失败，不依赖 Spark 输入顺序。
  Canvas 4.59 可再附加一项时间关系：每侧以开始字段和可选结束字段表示瞬时或闭区间，支持 12 种
  Allen 方向关系及 `NEAR/NEAR_BEFORE/NEAR_AFTER` 固定时长关系。时间字段必须使用相同的
  `DATE/TIMESTAMP/TIMESTAMP_NTZ` 类型；NULL 或反向区间不匹配。时间关系与全部空间、属性条件按 AND
  组合，固定日为 24 小时、固定周为 7 日，不使用日历窗口。Canvas 4.60 可增加独立空间 Near：
  PLANAR 使用来源 CRS，GEODESIC 仅支持 EPSG:4326 XY 并通过 ECEF 三轴保守候选召回后执行真实
  Geometry 最近位置距离判断，不使用质心。拓扑、空间 Near、属性和时间条件全部按 AND 组合。
  一对多可选择输出空间距离和时间 Near 区间间隔，均为 `DECIMAL(38,12)`；LEFT 未匹配记录为 NULL。
  一对一可使用 Near 过滤但不能启用距离输出。
- `UNION`：缺失/null `mergingTables` 保持旧版严格同字段集合的 `unionByName`。Canvas 4.62 的非 null
  配置启用 Merge Layers：第一张输入为基准层；后续层默认同名 Match、非同名字段按顺序追加，并为
  缺失字段补 NULL；自定义规则可 Match 已有字段、Rename 为新字段或 Remove。Match 仅允许同类型或
  数值类型间显式 Cast，Geometry 必须 Match 到类型、CRS、维度一致的基准 Geometry。无界输入的事件
  时间允许来源字段名不同，但必须 Match 到基准事件时间字段，Watermark 仍必须一致。Compiler 只构造
  投影和 Union 计划，不触发 Spark Action；安全摘要只记录模式、表数和规则数。
- `MASK_FIELDS`：只使用节点内嵌 `definition`，通过 Spark 内置 `Column` 表达式原位替换配置字段；不查询或同步全局脱敏规则，批处理和流处理复用同一个无状态 Operator。
- `JSON_EXTRACT`：通过 Spark VARIANT `parse_json/try_parse_json` 和 `variant_get/try_variant_get` 从 STRING 字段追加结构化标量字段；Compiler 只分析零行计划，批处理和流处理复用同一个无状态 Operator。
- `MODEL_OUTPUT`：目标和字段来自模型快照，APPEND 可写受管或外部模型，OVERWRITE 只允许受管模型；UPSERT 自动使用模型完整主键，批流共用 JDBC UPSERT，流式通过独立 `foreachBatch` 和 Checkpoint 按至少一次交付。
- `JDBC_OUTPUT`：统一显式映射由共享 Operator 使用 `select + alias + 显式 cast`。
  无 Geometry 时继续使用 Spark JDBC append；含 Geometry 时整行使用分区
  `PreparedStatement`，Geometry 经 `ST_AsBinary` 转为 WKB，再由目标库
  `ST_GeomFromWKB(?, databaseLocalSrid)` 写入。kind、CRS、dimension 必须完全一致。`UPSERT`
  必须完整选择目标主键或安全唯一索引，写入前拒绝 NULL Key 和当前 Dataset/micro-batch 内重复 Key；
  PostgreSQL/HighGo/openGauss/Kingbase 使用所选 `ON CONFLICT`，MySQL 使用 `ON DUPLICATE KEY UPDATE`，Oracle、SQL Server 和达梦使用受控单行 `MERGE`。每分区独立事务，
  Streaming 通过 `foreachBatch` 提供键级重放收敛，整体仍是至少一次交付，不提供跨分区全局事务。
- `JDBC_SNAPSHOT_SYNC_OUTPUT/MODEL_SNAPSHOT_SYNC_OUTPUT`：仅用于 BATCH 和 BOUNDED 小数据实体快照。来源映射并 Cast 为目标类型后，在 Driver 校验来源 Key；Runner 使用一条 JDBC 连接取得 PostgreSQL/HighGo/MySQL/openGauss/Kingbase 严格表锁，在同一事务中读取目标、校验目标 Key、执行比较和删除熔断，最后按 `DELETE → UPDATE → INSERT` 提交。Geometry 比较在具备 PostGIS 兼容扩展的 PostgreSQL 家族及 MySQL 8 开放。模型节点只解析已发布 MANAGED 模型目标，比较与写入完全复用 JDBC 执行器。
- `KAFKA_OUTPUT`：Canvas 4.6 新写入按 JSON/TEXT/BINARY 序列化上游字段。JSON 使用 `to_json(struct(...), ignoreNullFields=false)`，TEXT 原样发送 STRING，BINARY 原样发送字节；TEXT/BINARY 的 NULL 产生 tombstone。可选 Key 只允许 STRING/BINARY 且保持原类型。4.0～4.5 旧写入继续使用内联 Value Schema、映射、Cast 和字符串 Key 兼容语义。每条 `writeId` 仍启动独立 StreamingQuery/Checkpoint，整体按至少一次交付。
- `FILE_OUTPUT`：仅用于批任务，将来源表写到精确的 `s3a://{bucket}/{rootPrefix}/{targetPath}/`。CSV、JSON Lines 固定 UTF-8，普通 Parquet 固定 Snappy，并继续使用允许多个 `part-*` 和 `_SUCCESS` 的 Spark 目录数据集语义。Canvas `4.0` 的 Shapefile 使用 Driver 专用 Writer，通过 `toLocalIterator()` 流式生成唯一一套 SHP/SHX/DBF/PRJ/CPG；默认打成 ZIP，也可直接提交五个组件。GeoParquet 通过 Sedona 分布式写出 GeoParquet 1.1.0、WKB、显式 PROJJSON 和可选逐行 bbox；GeoJSON 使用 Driver 专用 Writer 生成唯一 RFC 7946 FeatureCollection，限 EPSG:4326 + XY 且达到 1.8GB 时失败。Shapefile/GeoJSON 制品先完整上传运行级临时前缀，再按冲突策略提交，`_SUCCESS` 始终最后写入；GeoParquet 复用 Spark/Hadoop 目录提交。S3 `OVERWRITE` 均不是原子替换。
- `OVERWRITE`：普通关系型 JDBC 数据库均使用 `TRUNCATE TABLE` 后 append/受控批量 INSERT，
  不 drop/recreate，也不承诺两个步骤为原子事务；TDengine 不支持普通 JDBC 输出。外键、权限、
  ClickHouse 集群表及数据库版本造成的 TRUNCATE 失败由真实运行返回 JDBC 错误。

Task Engine、Local Runner 和 Cluster Runner 在创建 SparkContext 前固定启用 Sedona 1.9.0、
Kryo serializer 和 `SedonaKryoRegistrator`；每个 child SparkSession 都调用
`SedonaContext.create(session)` 注册空间 SQL 与类型。Local Runner 与 Cluster Runner
同时携带 `geotools-wrapper 1.9.0-33.5`；Task Engine 额外携带排除 `gt-main` 的
`gt-shapefile 33.5`，为 Shapefile Writer 提供格式实现，GeoTools 核心类仍只有 wrapper
一套来源。它同时为 SPHEROID Buffer 提供 Sedona 所需的 CRS 运行能力。Runner 仅在
Geometry Output 写入前通过 `data-scalpel-dialect` 读取生成写入 SQL 所必需的目标字段数据库
本地 SRID，不比较目标表字段总数、顺序、标量类型、Geometry kind、CRS 或 dimension。目标
Geometry 字段或有效本地 SRID不可用时返回 `SPATIAL_TARGET_METADATA_UNAVAILABLE`；其余问题
由真实写入结果判断。

空间执行第一阶段只支持具备 PostGIS 兼容扩展的 PostgreSQL/HighGo/openGauss/Kingbase、MySQL 8、EPSG 和 XY。Canvas `4.0` 的空间
基础、修复、缓冲、拆分 Processor 同时支持有界和无界 Dataset，完整继承来源的事件时间和
Watermark；空间裁剪与空间聚合只接受
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

Processor 允许没有下游出边。Compiler 仍构造并分析该节点的惰性 Dataset 计划、返回可用输出 Schema，同时产生 `UNCONSUMED_PROCESSOR_OUTPUT` 警告；Runner 不会因为悬空结果额外触发 Spark Action，也不会为其创建写入或 StreamingQuery。共享上游仍可能因其他有效输出链路或既有输入缓存语义而执行，但悬空 Processor 结果不会被消费，也不影响同一任务中的其他输出链路。实时任务如果整张图没有任何可启动的 Output，仍以 `STREAMING_OUTPUT_REQUIRED` 拒绝运行。

普通输出通过 `Dataset.observe` 在同一次写入计划中采集输出行数，不为日志或统计单独触发 `count()`。Snapshot Sync 为执行规模和 Key 安全校验显式缓存并统计来源，结构化指标来自已提交 ChangeSet。多个 Output 按稳定拓扑顺序执行，先前成功写入不会因后续失败回滚；不同目标表或不同数据源之间没有分布式事务。JDBC OVERWRITE 先执行 `TRUNCATE`；普通 Spark 文件输出由 Spark 处理目标目录，Shapefile 则先完成本地与 S3 临时制品，提交阶段才删除目标前缀。后续失败可能留下空目标或部分数据，因此真实运行不自动重试，页面在提交真实运行前必须明确提示该风险。

外部 S3 使用 `fs.s3a.bucket.<bucket>.*` 的 bucket 级 Hadoop 配置，不读取或覆盖平台 Spark 的默认 S3 连接。相同 bucket 在单次任务中不得出现 endpoint、region、path-style 或凭据不同的配置，也不得与平台文件输入存储发生同 bucket 配置冲突。执行身份至少需要目标前缀的列举、写入、删除和分片上传权限。

Runner 的 `result.json` 当前固定使用 `schemaVersion: 11`，Dispatcher 兼容读取 v2～v11 并拒绝更早或未知版本。v8增加批处理 Spark JAR运行期血缘证据，v9增加 Spark JAR写入试运行预览，v10增加互斥的 Canvas节点试运行预览，v11增加实时 Spark JAR 的正常停止终态、失败前部分试运行预览和执行观测结果。Canvas试运行查询兼容读取 v10～v11，并继续严格核对运行身份。Canvas试运行成功时必须携带所选 Schema、最多100条`rowsJson`、截断标记和安全警告；失败时不得携带预览，且任何终态的`affectedRows`都为空。一个 Canvas 节点在 `nodeResults` 中只能出现一次。普通 JDBC、模型和文件多目标 Output 使用 `OUTPUT_WRITES` 指标，按稳定 `writeId` 保存每条写入的来源表、安全目标名称、终态、影响行数和错误码；全部成功时节点为 `SUCCESS`，中途失败时已提交项为 `SUCCESS`、当前项为 `FAILED`、同节点后续项为 `SKIPPED`。节点及任务影响行数是已成功提交项之和，只要任一成功项行数未知则为 `null`；失败不回滚已经提交的目标。成功的 Snapshot Sync 节点继续写入 `SNAPSHOT_SYNC` 指标，包含来源、目标、新增、更新、删除、未变化和保留目标独有行数，失败或回滚不返回成功指标。所有 Input、Processor 和 Output 分别以 `READ`、`PROCESS`、`WRITE` 阶段记录节点开始、成功或失败；普通多目标 Output 每个节点只记录一次节点生命周期，逐写入过程使用携带 `writeId` 的安全事件。失败结果保留此前已完成的节点，并让顶层错误、失败节点错误和失败写入错误码保持一致。结构化错误包含稳定错误码、类别、可重试标记、节点身份、SQLState和诊断 ID，不包含异常堆栈。

HTTP API 请求在最终 URL、Header 和 Body 确定后签名，每页和每次重试重新生成时间戳、Nonce 和签名。OAuth2 Client Credentials 或自定义 Token Endpoint 的 Token 按有效期缓存；业务请求返回 `401/403` 时最多刷新并重试一次。同步分页支持页码、Offset/Limit、Cursor 和 Next URL；异步接口先提交并轮询，只有成功后才进入结果读取及分页。最大页数、行数、响应字节数、持续时间和重复游标/URL检测都是硬限制。

HTTP API 批次的 Local Checkpoint 用于限制 Driver 堆占用，不是容错 Checkpoint。Executor 本地块丢失时本次任务失败并由平台按整次任务处理。批次 Dataset 保留到所有下游 Output Action 完成，随后统一 `unpersist`；Runner 的 `spark.stop()` 负责最终兜底。任务取消仍由 Dispatcher 终止独立 Runner JVM、容器或集群 Application，不增加连接器级取消控制面；已经提交给远端 API 的异步任务不会因此自动取消。

控制台日志记录 `TASK_START/TASK_SUCCESS/TASK_FAILED` 和 `NODE_START/NODE_SUCCESS/NODE_FAILED`。TMQ 摘要只允许数据源 ID、Topic、超级表、首次位置和字段数量；Spark 配置对密码、Token、Key 及 TDengine 密码选项启用脱敏，不记录完整 Properties、Checkpoint URI 或数据行。Snapshot Sync 摘要只记录目标身份、Key 字段名、映射数量、删除策略、阈值和结果计数，不记录 Key 值、before/after、Geometry 或数据行。文件 Input 的安全摘要只包含节点 ID、Table ID、table code、格式和字段数；空间文件 Output 摘要只记录来源表、Geometry 字段、空间类型/CRS/维度、格式选项、目标相对路径和冲突策略，不得记录 Geometry、坐标、属性值、完整 S3 URI 或临时路径；`MASK_FIELDS` 摘要只包含节点 ID、字段数量、策略类型以及全局来源和自定义规则数量，不得记录固定替换值、完整参数或节点配置；`JSON_EXTRACT` 摘要只记录来源/输出表、来源字段、提取数量、目标类型集合和失败策略，不得记录 JSON Path、JSON 内容或实际值。空间基础 Processor 摘要只记录表名、字段名、来源/格式、测量类型、CRS 和规则数量，不得记录 WKT、WKB、GeoJSON、坐标或测量结果。Geometry 修复、Buffer 和拆分节点的摘要只允许记录表名、字段名、模式、距离和 CRS；空间裁剪和空间聚合摘要只允许记录表名、字段名、聚合 kind、CRS 和规则数量，不得记录匹配数量、组大小、实际 Geometry、坐标、部件内容、裁剪/聚合结果或数据行。节点失败只打印一次经过脱敏的异常链和调用栈，最大 64 KiB；任务级失败只打印摘要。密码、Secret、Token、Credential、Access Key、签名参数、预签名 URL、完整 JDBC Properties、文件对象 Key、物化前缀、来源 Key、临时路径、HTTP 响应业务数据、数据行、固定替换值、脱敏测试值和 SQL参数值必须被屏蔽。

## 6. 管理端运行观察

HDBSCAN 执行阶段使用下列安全错误分类；Canvas 4.45 已通过显式诊断配置接入节点，结果协议不变。
这些错误沿用现有 `code/message/category/retryable/phase/diagnosticId` 字段，Dispatcher、Admin 与前端按已有
字符串错误码和类别传递、展示，不新增另一套错误枚举或响应包装。

| 错误码 | 类别 | 阶段 / 重试 | 含义 |
| --- | --- | --- | --- |
| SPATIAL_HDBSCAN_TREE_INVALID | INTERNAL | PROCESS / false | 内部生成树、分支或收敛关系不一致，不归咎于用户点字段 |
| SPATIAL_HDBSCAN_HIERARCHY_LIMIT_EXCEEDED | RESOURCE | PROCESS / false | 超过当前层次资源保护边界，不隐式切分数据后返回部分簇 |
| SPATIAL_HDBSCAN_NUMERIC_RANGE_INVALID | SCHEMA | PROCESS / false | 距离或密度无法可靠表示，不回退为另一算法或返回无穷诊断 |

三项摘要均不包含观测身份值、坐标、密度值或原异常正文；包装异常中的稳定错误码优先于一般空间库错误。

Canvas 与 LOCAL_SQL 共用任务运行记录和详情入口。活动状态包括 `QUEUED`、`RUNNING`、
`CANCEL_REQUESTED`、`STOP_REQUESTED`，详情页每 2 秒刷新；终态停止详情轮询，任务运行列表在无活动实例时降低到每 10 秒刷新。

`task_run` 为 Spark Canvas 保存结构化错误列：错误码、类别、可重试标记、阶段、SQLState、节点 ID/类型/名称和诊断 ID。现有 `message/error_detail` 继续服务 LOCAL_SQL 和通用错误表达。运行详情优先展示结构化错误，不展示 Java 调用栈；用户仍可下载原始 `result.json` 和经过脱敏的控制台日志。

活动 Canvas运行可通过以下 Action取消：

```http
POST /api/v1/task-runs/{runId}/actions/cancel
```

`CANCEL_REQUESTED`重复取消幂等；终态或不支持外部取消的运行返回 `409`。

取消中或实时停止中的运行允许升级为强制终止：

```http
POST /api/v1/task-runs/{runId}/actions/force-terminate
```

该操作要求 `task.execute`，通过独立 Kafka 命令让 Dispatcher 立即杀死 Spark Application，不能只修改
`task_run` 状态。确认终止后记录为 `CANCELLED`；Backend 终态无法确认时记录为 `FAILED`，避免运行记录
无限停留在请求状态。

制品接口为：

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

## 2026-09 运行管理内部职责

TaskRunArtifactQueryService 负责日志、产物下载/预览和 Canvas/JAR 试运行结果读取，继续核对结果版本及运行身份，数据库只读取短事务快照。TaskRunService 保留兼容入口与提交编排；Canvas、JAR 和质检任务复用计划锁/触发去重、计算引擎复核及 Run+Submit Outbox 同事务提交约束，类型专属准备与定义校验仍各自维护。
