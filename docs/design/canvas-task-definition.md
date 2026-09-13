# Canvas 任务定义与节点设计

Canvas 内置节点的前端注册、Inspector 动态加载、元数据 Provider 与 Java 契约归属遵循
[Canvas 节点扩展架构](canvas-node-extension-architecture.md)。本文只定义稳定 JSON 与节点业务语义；
X6 Shape、Palette 分组、图标和校验结果不属于持久化协议。

## 1. 文档范围

本文定义 `CANVAS` 任务的稳定协议、图结构语义和正式节点配置：

- `MODEL_INPUT`
- `JDBC_INPUT`
- `JDBC_QUERY_INPUT`
- `FILE_DATASET_INPUT`
- `HTTP_API_INPUT`
- `KAFKA_INPUT`
- `TDENGINE_TMQ_INPUT`
- `JOIN`
- `GEOMETRY_CONSTRUCT`
- `SPATIAL_TRANSFORM`
- `GEOMETRY_VALIDATE`
- `SPATIAL_MEASURE`
- `GEOMETRY_SERIALIZE`
- `SPATIAL_CLIP`
- `SPATIAL_AGGREGATE`
- `SPATIAL_JOIN`
- `STREAM_JOIN`
- `RENAME`
- `FILTER`
- `SQL_TRANSFORM`
- `SELECT_COLUMNS`
- `DERIVE_COLUMNS`
- `TYPE_CAST`
- `AGGREGATE`
- `UNION`
- `DEDUPLICATE`
- `NULL_HANDLING`
- `VALUE_MAPPING`
- `MASK_FIELDS`
- `JSON_EXTRACT`
- `WINDOW`
- `TOP_N`
- `MODEL_OUTPUT`
- `JDBC_OUTPUT`
- `JDBC_SNAPSHOT_SYNC_OUTPUT`
- `MODEL_SNAPSHOT_SYNC_OUTPUT`
- `KAFKA_OUTPUT`
- `FILE_OUTPUT`

模型节点的配置、模型快照、引用投影和运行边界以 [Canvas ModelInput 与 ModelOutput 设计](canvas-model-nodes.md) 为准。

可视化任务设计器负责 JSON 导入导出，并通过 Admin 网关调用 Task Engine 完成图分析、Schema 传播和业务校验。正式的 `SPARK_CANVAS` 任务定义页通过 Admin 保存和读取稳定定义；独立的 `/task/orchestration` 仍作为不持久化的试验页。节点配置阶段只读复用现有数据源列表、物理表和字段元数据接口。真实执行使用独立 manifest 和 Runner，不向本文定义的稳定 Canvas JSON 写入运行连接或凭据。

Canvas 定义必须是与 AntV X6、Java 类名和未来执行引擎解耦的稳定 JSON。X6 只负责编辑和展示，不得直接持久化 X6 Cell、Shape、Port 或运行时状态。

## 2. 核心决策

### 2.1 一个节点只表达一个动作或一个同类资源集合

- 一个 `JDBC_INPUT` 节点只绑定一个 JDBC 数据源，可以从该数据源读取一张或多张物理表；每张表仍形成独立的 Canvas 表。
- 一个 `JDBC_QUERY_INPUT` 节点只执行一条已显式分析的只读 JDBC 查询。
- 一个 `FILE_DATASET_INPUT` 节点绑定一个文件数据集，可读取其中多张逻辑表。
- 一个 `HTTP_API_INPUT` 节点绑定一个数据源，可读取多个已声明 Schema 的 API 资源。
- 一个 `SPATIAL_SERVICE_INPUT` 节点绑定一个空间服务数据源，可读取多个要素资源。
- 一个 `MODEL_INPUT` 节点可读取多个已发布数据模型（允许跨数据源）。
- 一个 `KAFKA_INPUT` 节点只读取一个 Topic，并持有自己的 Value 格式、可选内联 Schema 和元数据字段选择。
- 一个 `TDENGINE_TMQ_INPUT` 节点只订阅一个由外部系统管理的完整超级表 TMQ Topic。
- 一个 `JOIN` 节点只执行一次两表连接。
- 一个 `GEOMETRY_CONSTRUCT` 节点只从普通字段构造一个明确 kind/CRS/dimension 的 Geometry 字段。
- 一个 `SPATIAL_TRANSFORM` 节点只显式转换一张表中的一个 Geometry 字段 CRS。
- 一个 `GEOMETRY_VALIDATE` 节点只诊断一个 Geometry 字段并追加合法性结果。
- 一个 `SPATIAL_MEASURE` 节点只对一张表配置 `1..32` 个逐行空间测量项。
- 一个 `GEOMETRY_SERIALIZE` 节点只把一个 Geometry 字段序列化为一个普通字段。
- 一个 `SPATIAL_CLIP` 节点只使用一张 Polygon/MultiPolygon Mask 表裁剪一张来源表的一个
  Geometry 字段。
- 一个 `SPATIAL_AGGREGATE` 节点只对一张有界表执行一次分组或全局空间聚合。
- 一个 `SPATIAL_JOIN` 节点只执行一次两表批处理空间连接；可组合受控拓扑、空间 Near、属性和时间条件，
  并显式选择 INNER/LEFT 与一对多/一对一结果粒度。
- 一个 `STREAM_JOIN` 节点只执行一次流式表连接。
- 一个 `RENAME` 节点只替换一张逻辑表，并原子重命名该表的零到多个字段。
- 一个 `FILTER` 节点可选择多张逻辑表逐表筛选；每项可使用结构化条件树或受控 SQL 布尔表达式。
- 一个 `SQL_TRANSFORM` 节点对完整上游表 Map 执行一条受控 Spark SQL `SELECT` 或
  `WITH ... SELECT`，保留全部上游表并追加一张新逻辑表。
- 一个 `SELECT_COLUMNS` 节点只裁剪并排序一张逻辑表的字段，并产生一个新的逻辑表。
- 一个 `DERIVE_COLUMNS` 节点可对多张已选逻辑表应用共享的全局规则和各表独立规则；每张表可原表更新或生成一张新逻辑表。
- 一个 `TYPE_CAST` 节点只显式转换一张来源表中的一个或多个字段类型。
- 一个 `AGGREGATE` 节点只对一张来源表执行一次全表或分组聚合。
- 一个 `UNION` 节点纵向合并配置选择的多张逻辑表；可保持严格同 Schema，也可按基准层执行
  Merge Layers 字段 Match/Rename/Remove 和缺失字段补 NULL。
- 一个 `DEDUPLICATE` 节点只按全字段或业务键删除一张来源表中的重复行。
- 一个 `NULL_HANDLING` 节点只按有序规则删除含 NULL 的行或填充 NULL 字段。
- 一个 `VALUE_MAPPING` 节点只对少量明确原值执行精确、内联的静态映射。
- 一个 `MASK_FIELDS` 节点只对一张来源表中明确选择的字段执行脱敏替换。
- 一个 `JSON_EXTRACT` 节点只从一张来源表的一个 STRING 字段中提取结构化字段。
- 一个 `WINDOW` 节点只按明确分区、排序和 ROWS Frame 追加窗口字段。
- 一个 `TOP_N` 节点只按明确排序选择全局或各分组的前 N 行。
- 一个 `MODEL_OUTPUT` 节点只描述一次向一个目标模型的写入。
- 一个 `JDBC_OUTPUT` 节点只描述一次向一张目标表的写入。
- 一个 `JDBC_SNAPSHOT_SYNC_OUTPUT` 节点只对比并同步一张 JDBC 目标表的完整实体快照。
- 一个 `MODEL_SNAPSHOT_SYNC_OUTPUT` 节点只对比并同步一个 MANAGED 模型的完整实体快照。
- 一个 `KAFKA_OUTPUT` 节点可以包含多条按 `writeId` 独立寻址的 Topic 写入；每条写入独立选择来源表、Topic、Value 格式和可选 Key。
- 一个 `FILE_OUTPUT` 节点只描述一次向用户指定的外部存储目录写入。
- 跨数据源输入、多次 Join 或多个输出目标使用多个图节点表达。同一数据源下批量选择物理表不再要求重复创建 JDBC Input。

这样可以让画布直接表达数据血缘，避免在节点内部再次维护 `items`、`actions`、`mappings` 等小型工作流。

### 2.2 节点间数据使用表名作为 Map Key

节点间传递的数据在概念上表示为：

```text
Map<tableName, CanvasTable>
```

`CanvasTable.name` 是当前数据流中的逻辑表名，同时也是 Map Key：

- `JDBC_INPUT` 为 `configuration.tables` 中每个物理表产生一个以原始 `tableName` 为 Key 的条目。
- `JDBC_QUERY_INPUT` 使用配置的 `outputTableName`。
- `FILE_DATASET_INPUT` 使用不可修改的 `FileDatasetTable.code`。
- `HTTP_API_INPUT` 使用配置的 `outputTableName`。
- `MODEL_INPUT` 使用模型不可修改且全局唯一的 `code`。
- `KAFKA_INPUT` 使用配置的 `outputTableName`。
- `JOIN` 使用配置的 `outputTableName` 创建新表。
- `GEOMETRY_CONSTRUCT`、`SPATIAL_TRANSFORM`、`GEOMETRY_VALIDATE`、`GEOMETRY_REPAIR`、
  `GEOMETRY_DERIVE`、`GEOMETRY_BUFFER`、`GEOMETRY_EXPLODE`、`SPATIAL_MEASURE`、
  `GEOMETRY_SERIALIZE`、`SPATIAL_CLIP`、`SPATIAL_AGGREGATE` 和 `SPATIAL_JOIN` 保留输入表，
  并使用 `outputTableName` 创建新表。
- `RENAME` 处理器替换逻辑表名及 Map Key，但不得修改输入表的物理来源信息。
- `FILTER` 按 `operations` 逐表筛选：`REPLACE_SOURCE` 替换来源 Map 项，`CREATE_NEW_TABLE` 追加筛选结果。
- `SELECT_COLUMNS` 保留全部输入表，并以配置的 `outputTableName` 追加字段投影结果。
- `DERIVE_COLUMNS` 按 `operations` 逐表处理：`REPLACE_SOURCE` 替换对应 Map Key，`CREATE_NEW_TABLE` 以该项 `outputTableName` 追加派生结果；全局规则与表级规则都只引用该表进入节点时的原始 Schema。
- `TYPE_CAST` 保留全部输入表，并以配置的 `outputTableName` 追加类型转换结果。
- `AGGREGATE` 保留全部输入表，并以配置的 `outputTableName` 追加聚合结果。
- `UNION` 保留全部输入表，并以配置的 `outputTableName` 追加合并结果。
- `DEDUPLICATE` 保留全部输入表，并以配置的 `outputTableName` 追加去重结果。
- `NULL_HANDLING` 保留全部输入表，并以配置的 `outputTableName` 追加空值处理结果。
- `VALUE_MAPPING` 保留全部输入表，并以配置的 `outputTableName` 追加值映射结果。
- `MASK_FIELDS` 保留全部输入表，并以配置的 `outputTableName` 追加字段脱敏结果。
- `JSON_EXTRACT` 保留全部输入表，并以配置的 `outputTableName` 追加 JSON 提取结果。
- `WINDOW` 保留全部输入表，并以配置的 `outputTableName` 追加窗口计算结果。
- `TOP_N` 保留全部输入表，并以配置的 `outputTableName` 追加行筛选结果。
- 数据库、Schema 和数据源 ID 不参与 Map Key 计算。
- 表名保持元数据接口返回的原始大小写，第一版按精确字符串匹配。

当多个上游 Map 合并时，只要出现相同 Key，就返回 `DUPLICATE_TABLE_NAME` 错误。即使两个 Key 指向同一个物理表，也不得静默去重或覆盖。

该规则使处理节点只依赖稳定的表名和必要字段名。物理表增加未被引用的字段时，通常不需要重新编辑任务；引用的表名或字段名发生变化时，定义校验应明确报错。

### 2.3 外部元数据 Schema 默认不进入定义，查询与 Kafka Schema 归节点所有

Canvas 定义只保存数据源 UUID、模型 UUID、文件数据集表 UUID、API 资源 UUID、表名和显式节点配置，不保存数据源中已有的数据库、Schema、模型名称、模型字段、文件路径/格式/解析参数、JDBC 原生类型、API 输出字段列表或预览数据。`JDBC_QUERY_INPUT.outputColumns` 是查询 SQL 的已分析结果快照，属于该节点的稳定契约，是这一规则的明确例外。

`JDBC_QUERY_INPUT` 保存规范化 SQL 的 SHA-256 和完整输出字段快照。Compiler 只使用该快照构造
零行计划，不在编译期连接外部数据库；发布、重新启用和运行准备不重新分析或比较查询结果结构。
快照是逻辑规划依据，SQL 文本变化仍必须重新分析和保存，且不得由后端静默更新。

Kafka Input 的 JSON Value Schema 是消息反序列化契约，归 `KAFKA_INPUT` 节点自身所有，因此以内联 `valueSchema.columns` 保存；TEXT/BINARY 不使用内联 Schema，消息固定输出到 `value` 字段。Canvas 4.6 的新 Kafka Output 不再维护第二份目标 Schema 或字段映射，而是直接从上游 Schema 选择字段并按 JSON/TEXT/BINARY 序列化。Canvas 4.0～4.5 已保存的 Kafka Output 继续保留内联 Schema 与显式映射兼容路径，升级时不改变其 Cast、字段名或 Key 语义。

设计器通过现有接口读取数据源、普通 JDBC 表元数据和已保存模型字段，用于配置和组装单次编译
的 `metadataSnapshot`，不进入导出的 Canvas 定义。数据源名称、数据库、Schema、字段列表和元数据
读取状态都属于设计时运行数据。

Task Engine 的编译请求使用独立的 `metadataSnapshot` 携带本次分析所需 Schema。该快照和后续不可变运行快照都不属于本文定义的 Canvas JSON，不得在导入导出时混入定义。

### 2.4 不保存数据源凭据

Canvas 定义中的 JDBC 节点只保存 `dataSourceId` 和表选择，文件输入只保存一个 `fileDatasetId` 和表选择，HTTP API 与空间服务节点只保存数据源 ID 和资源选择，模型节点只保存模型选择/目标模型 ID，Kafka 节点只保存数据源 ID、Topic、Value 格式、逻辑字段选择和必要的兼容 Schema/映射，TMQ 节点只保存数据源 ID、Topic、数据库、超级表和定义指纹，文件输出只保存数据源 ID 与相对目录。URL、Broker 地址、对象 Key、物化前缀、用户名、密码、Token、API Key、Secret、签名密钥和其他凭据不得进入 Canvas JSON、节点配置或前端状态持久化结果。

`HTTP_API_INPUT.runtimeParameters` 会随 Canvas 定义明文持久化，只允许保存日期、业务筛选条件、初始游标等非敏感值。动态 Token 必须由 HTTP API 数据源的 OAuth2 或 Token Endpoint 鉴权在执行时生成，不能作为运行时参数绕过凭据边界。

## 3. Canvas JSON 协议

### 3.1 顶层结构

```json
{
  "schemaVersion": 4,
  "schemaMinorVersion": 77,
  "nodes": [],
  "edges": []
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `schemaVersion` | integer | Canvas JSON 协议大版本，当前固定为 `4` |
| `schemaMinorVersion` | integer | Canvas JSON 协议小版本；当前写出版本为 `77` |
| `nodes` | array | 节点定义，按照前端保存顺序持久化；业务逻辑不得依赖数组顺序 |
| `edges` | array | 有向边定义，业务逻辑不得依赖数组顺序 |

当前写出版本统一为 `4.77`。`4.77` 为 `SPATIAL_CLIP` 增加可选 `maskCombination`：新建节点默认
`DISSOLVE_ALL`，对每条来源要素只融合与其相交的 Mask 后裁剪一次，避免重叠 Mask 重复输出覆盖区域；
缺失/null 保持旧版 `PAIRWISE` 逐条 Mask 裁剪。`4.76` 新增批处理 `SPATIAL_DESCRIBE_DATASET`，在保留来源表的同时输出
逐字段统计表、数据集描述表，以及可选样本表和 XY Envelope 范围表。节点只构造惰性 Spark 计划，
真实统计在任务执行时完成；Geometry 可选，但输出范围时必须显式选择 Geometry 字段。`4.75` 新增批处理
`SPATIAL_SIMILAR_LOCATIONS`，按参考位置与候选位置
共有的数值属性执行全集标准化，并返回最相似、最不相似或两端候选。首版只接入 GeoAnalytics 的
属性值与属性轮廓方法；不包含 ArcGIS Pro 的 Ranked、Scale 或 Collapse 扩展。`4.74` 新增批处理 `SNAP_TRACKS`，将有界 XY Point
轨迹按时间、直接相邻路网拓扑和可选通行方向联合匹配到 XY LineString 网络，输出
吸附点、匹配线和诊断字段。`4.73` 新增批处理 `TRACE_PROXIMITY_EVENTS`，从显式 ID 或起始表出发，
按 Point 空间距离、时间距离和可选同值属性向下游逐层传播，输出首次接触事件及可选后续轨迹。
`4.72` 的 `SPATIAL_GROUP_BY_PROXIMITY` 按空间及可选时间、
受控属性关系对同一空间表建立无向邻接边并求传递连通组；每个来源要素保留一行并追加组 ID。`4.71`
新增批处理 `SPATIAL_ENRICH_FROM_GRID`，把已有多变量 Polygon
格网的显式属性按相交关系回填到有界 XY Point 表，并稳定保留未匹配 Point。`4.70` 新增批处理
`SPATIAL_MULTI_VARIABLE_GRID`，从多张投影 XY
Point/Line/Polygon 来源的共同外包范围生成统一方格或六边形，并按变量独立计算最近距离、最近属性和
关联要素汇总。`4.69` 新增批处理 `SPATIAL_HOT_SPOTS`，从投影 XY Point
生成完整方格并计算固定距离邻域 Getis-Ord Gi*、双侧显著性与可选 FDR；`4.68` 新增批处理
`SPATIAL_DENSITY`，从投影 XY Point 生成 Uniform 或 Kernel 方格/六边形矢量密度格网；`4.0` 将
`MODEL_INPUT`、`FILE_DATASET_INPUT`、`HTTP_API_INPUT` 和
`SPATIAL_SERVICE_INPUT` 从单资源结构改为有序资源数组；这是破坏性协议变化，所有 `3.x` 定义均不导入、
不迁移或猜测旧字段语义，必须重新配置后保存。一个 Input 节点的唯一输出端口传递完整表 Map，数组中每个资源产生一张表。

`SQL_TRANSFORM` 从 `4.1` 引入；`TYPE_CAST.epochTimestampUnit` 从 `4.2` 引入；
`TYPE_CAST.stringTemporalParseOptions` 从 `4.3` 引入；`KAFKA_INPUT.valueFormat/metadataFields` 从 `4.4` 引入；
`TDENGINE_TMQ_INPUT.eventTimeColumn/watermarkDelaySeconds` 从 `4.5` 引入；
`KAFKA_OUTPUT.valueFormat/valueColumnNames` 从 `4.6` 引入；`TYPE_CAST.temporalStringFormatOptions`
从 `4.7` 引入；带 Epoch 单位的 `DATE/TIMESTAMP → LONG` 从 `4.8` 引入；
`GEOMETRY_DERIVE` 从 `4.9` 引入；`GEOMETRY_SIMPLIFY` 从 `4.10` 引入；
`SPATIAL_NEAREST`、`SPATIAL_SUMMARIZE_WITHIN`、`SPATIAL_OVERLAY` 分别从 `4.11`、`4.12`、`4.13`
引入；四个轨迹节点 `TRACK_RECONSTRUCT`、`TRACK_MOTION_STATISTICS`、`TRACK_FIND_DWELL`、
`TRACK_DETECT_INCIDENTS` 分别从 `4.14`～`4.17` 引入；`SPATIAL_BIN_AGGREGATE`、
`SPATIAL_POINT_CLUSTER`、`SPATIAL_CENTER_DISPERSION` 分别从 `4.18`、`4.19`、`4.20` 引入。
事件检测可选生命周期策略、状态字段、同时间顺序，以及四类轨迹节点可选固定时间边界从 `4.21` 引入。
格网节点显式 `binSizeSemantics` 同样从 `4.21` 引入，缺失/null 保持旧边长语义。
驻留检测可选 `dwellSemantics/rangeOptions` 从 `4.22` 引入，缺失/null 保持旧相邻连段语义。
运动统计可选 `motionSemantics/windowOptions` 从 `4.23` 引入，缺失/null 保持旧 lag 语义。
区域统计逐项 `valueTreatment/weighting` 与 `COUNT_FIELD/ANY` 从 `4.24` 引入，缺失/null 保持原值、不加权。
区域汇总可选 `groupResult` 从 `4.25` 引入，缺失/null 保持旧扁平分组；对象存在时允许显式 LINKED_TABLES/LEGACY_FLAT 模式并保留非活动设置。
空间叠加 `IDENTITY/SYMMETRICAL_DIFFERENCE` 和可选 `geometryPolicy` 从 `4.26` 引入；旧三模式缺失/null 策略不改解释。
轨迹重建可选 `reconstruction` 从 `4.27` 引入，提供确定次序、受控表达式拆分与连接段归属；缺失/null 保持旧点连线。
轨迹重建可选 `reconstruction.pathGeometry` 从 `4.28` 引入，显式选择按距离方法生成 MultiLineString；缺失/null 保持旧顶点线。
Geometry 派生逐项及简化节点可选 `geometryPolicy` 从 `4.29` 引入，显式保留维度/输出 XY；缺失/null 保持旧行为。
最近邻可选 `matching` 从 `4.30` 引入：显式真实距离、来源身份及可选连接线结果；缺失/null 保持旧 KNN 语义。
中心分析可选 `resultMode` 及逐分析 `outputTableName` 从 `4.31` 引入；缺失/null 保持旧宽表算法。
中央要素可选 `analyses[i].centralFeatureColumns` 从 `4.32` 引入；缺失/null 保留原字段结构，显式数组仅在独立中央要素结果中生效。
格网 H3 形状及可选 `h3` 从 `4.33` 引入；未启用时保持旧方格/六边形语义，非活动对象也要求 4.33。
格网 `statistics[i].kind=COUNT_FIELD|ANY` 从 `4.34` 引入，低版本使用这些类型返回 `SPATIAL_BIN_FIELD_STATISTICS_REQUIRE_SCHEMA_VERSION`。
轨迹重建/驻留 `summaryStatistics[i].kind=COUNT_FIELD|ANY` 从 `4.35` 引入，含非活动草稿；低版本返回 `TRACK_FIELD_STATISTICS_REQUIRE_SCHEMA_VERSION`。
国际码/平方码及独立美国测量制距离和面积单位从 `4.36` 引入，含隐藏草稿；低版本返回 `SPATIAL_EXTENDED_UNITS_REQUIRE_SCHEMA_VERSION`。
旧 FEET/MILES/NAUTICAL_MILES 等保持国际制换算；完整枚举、换算与面板语义见[公共空间单位](canvas-spatial-units.md)。
区域汇总 `statistics[i].kind=VARIANCE|STDDEV` 与 `weighting=INTERSECTION_FRACTION` 的组合从 `4.37` 引入；
低版本返回 `SPATIAL_WITHIN_WEIGHTED_DISPERSION_REQUIRE_SCHEMA_VERSION`，定位到该项 `.weighting`。
平面格网可选 `planarGrid` 从 `4.38` 引入；任意非 null 对象（含 H3 下非活动草稿）低于 4.38 返回 `SPATIAL_PLANAR_GRID_REQUIRE_SCHEMA_VERSION`。
Bins/Within 的可选 `temporalSlicing.calendar` 从 `4.39` 引入；非 null 对象（含 FIXED_DURATION 非活动草稿）低版本返回 `SPATIAL_CALENDAR_WINDOW_REQUIRE_SCHEMA_VERSION`。
点聚类可选 `dbscan` 从 `4.40` 引入；非 null 对象（含非活动草稿）低版本返回 `SPATIAL_DBSCAN_OPTIONS_REQUIRE_SCHEMA_VERSION`。
区域汇总可选 `regions` 从 `4.41` 引入；非 null 对象（包括 AREA_TABLE 下的隐藏格网草稿）低版本返回 `SPATIAL_WITHIN_REGIONS_REQUIRE_SCHEMA_VERSION`。
轨迹重建可选 `reconstruction.areaGeometry` 从 `4.42` 引入，含非活动草稿的任何非 null 对象低版本返回 `TRACK_AREA_GEOMETRY_REQUIRE_SCHEMA_VERSION`。
面轨迹可选 `windowBindings` 的非空数组从 `4.43` 引入，含隐藏/非活动设置，低版本返回 `TRACK_BUFFER_WINDOWS_REQUIRE_SCHEMA_VERSION`。
面轨迹可选 `geodesicBoundary` 对象从 `4.44` 引入，含隐藏/非活动设置，低版本返回 `TRACK_GEODESIC_AREA_REQUIRE_SCHEMA_VERSION`。
HDBSCAN 可选诊断配置 `hdbscan` 从 `4.45` 引入，包含非活动草稿；低版本返回 `SPATIAL_HDBSCAN_OPTIONS_REQUIRE_SCHEMA_VERSION`。
事件检测非空 `conditionWindows` 从 `4.46` 引入；低版本返回 `TRACK_INCIDENT_WINDOWS_REQUIRE_SCHEMA_VERSION`。
固定时长枚举 `SpatialDurationUnit.WEEKS` 从 `4.47` 引入；每周固定 604800 秒，包括非活动草稿的固定时长字段，
低版本返回 `SPATIAL_DURATION_WEEKS_REQUIRE_SCHEMA_VERSION`。已有日历周/日历月年不改变、不触发此门槛；
完整字段、固定周与日历周区别见[公共时长单位](canvas-spatial-units.md#447-固定时长周与日历周)。
最近邻 `matching.geodesicGeometryMode=GEOMETRY` 从 `4.48` 引入；缺失/null 按 `POINT_ONLY` 保持旧版仅 Point
测地语义。显式 GEOMETRY 即使位于 LEGACY_KNN 非活动草稿中也要求 4.48，低版本返回
`SPATIAL_NEAREST_GEODESIC_GEOMETRY_REQUIRE_SCHEMA_VERSION`。
Geometry Buffer 可选 `distanceUnit` 从 `4.49` 引入；缺失/null 保持旧版 PLANAR 使用来源 CRS 单位、
SPHEROID 使用米的语义。显式单位低版本返回 `GEOMETRY_BUFFER_UNIT_REQUIRE_SCHEMA_VERSION`。
空间测量中 AREA/LENGTH/PERIMETER/DISTANCE 的可选 `outputUnit` 从 `4.50` 引入；缺失/null
保持旧版 PLANAR 来源 CRS 单位（面积为平方）、SPHEROID 米/平方米结果。显式单位低版本返回
`SPATIAL_MEASURE_UNIT_REQUIRE_SCHEMA_VERSION`。
空间裁剪可选 `geometryPolicy` 从 `4.51` 引入；`SOURCE_FAMILY_2D` 只保留来源点/线/面家族并
规范化为二维 Multi，`LEGACY_ANY_DIMENSION` 及缺失/null 保持旧版通用 Geometry 结果。
显式策略低版本返回 `SPATIAL_CLIP_GEOMETRY_POLICY_REQUIRE_SCHEMA_VERSION`。
Geometry Buffer 可选 `distanceSource/distanceFieldName/distanceExpression` 从 `4.52` 引入；
缺失/null 来源保持旧版固定 `distance` 语义，FIELD 和 EXPRESSION 分别逐行读取数值字段或受控确定性
Spark 数值表达式。任一新增字段的非 null 值（包括非活动草稿）在低版本返回
`GEOMETRY_BUFFER_DISTANCE_SOURCE_REQUIRE_SCHEMA_VERSION`。
空间聚合可选 `dissolve` 从 `4.53` 引入；非 null 对象（包括 `enabled=false` 的非活动草稿）
在低版本返回 `SPATIAL_AGGREGATE_DISSOLVE_REQUIRE_SCHEMA_VERSION`。启用时要求恰好一个 UNION；
空分组对应 Create Buffers Dissolve All，非空分组对应 List，并可配置来源要素计数、九种标量统计和
Multipart/Singlepart 输出。
`dissolve.groupingMode` 从 `4.61` 引入；缺失/null 保持上述 All/List 语义。显式
`CONNECTED_COMPONENTS` 只允许空分组和 Polygon/MultiPolygon，并按相交、重叠或接触关系的传递闭包
分别融合；低版本返回 `SPATIAL_DISSOLVE_GROUPING_MODE_REQUIRE_SCHEMA_VERSION`。
`UNION.mergingTables` 从 `4.62` 引入；缺失/null 保持严格同 Schema Union，空数组启用默认
Merge Layers 对齐，非空数组只保存需要覆盖默认行为的合并层字段规则。低版本携带非 null 值时返回
`UNION_MERGE_LAYERS_REQUIRE_SCHEMA_VERSION`。
事件检测 `conditionWindows[i].source=TRACK_DISTANCE` 从 `4.63` 引入；缺失/null/FIELD 保持
4.46 的原始字段窗口语义。轨迹距离来源按 WGS84 测地线计算各 Point 自当前片段首观测起的累计距离，
再按左闭右开观测范围聚合，单位固定为米。低版本返回
`TRACK_INCIDENT_DISTANCE_WINDOWS_REQUIRE_SCHEMA_VERSION`。
事件检测 `conditionWindows[i].source=TRACK_SPEED` 从 `4.64` 引入；按同一左闭右开范围聚合逐观测
WGS84 速度，单位固定为米/秒。片段首观测速度为 0，后续速度使用前一观测到当前观测的测地距离与时间差；
低版本返回 `TRACK_INCIDENT_SPEED_WINDOWS_REQUIRE_SCHEMA_VERSION`。
事件检测 `conditionWindows[i].source=TRACK_ACCELERATION` 从 `4.65` 引入；按同一范围聚合逐观测
加速度，单位固定为米/秒²。片段首观测加速度为 0，后续值使用当前与前一观测速度差及时间差；低版本返回
`TRACK_INCIDENT_ACCELERATION_WINDOWS_REQUIRE_SCHEMA_VERSION`。
事件检测非空 `conditionScalars` 从 `4.66` 引入；每项绑定 TRACK_START_TIME、TRACK_DURATION、
TRACK_CURRENT_TIME 或 TRACK_INDEX 之一，四种结果均为 LONG。开始/当前时间使用 Unix Epoch 毫秒，
持续时间使用毫秒，观测序号从 0 开始，并在当前 DataScalpel 轨迹片段边界重置。低版本返回
`TRACK_INCIDENT_SCALARS_REQUIRE_SCHEMA_VERSION`。
事件检测 `conditionScalars[i].source=TRACK_POINT_X_AT|TRACK_POINT_Y_AT` 从 `4.67` 引入；
必填有符号 32 位整数 `offset`，0/负数/正数分别表示当前/过去/未来观测。只读取当前
轨迹片段内带完整元数据的 Point X/Y，越界或 Geometry 为 NULL 时返回 NULL；结果为
可空 DOUBLE，单位跟随来源 CRS，不做投影换算。非坐标来源忽略但保留隐藏 `offset`。
低版本返回 `TRACK_INCIDENT_POINT_COORDINATES_REQUIRE_SCHEMA_VERSION`。
`SPATIAL_HOT_SPOTS` 从 `4.69` 引入；低版本不能携带该节点。节点以投影 XY Point、规则方格和固定距离
二元邻域计算 Getis-Ord Gi*，输出原始双侧 p-value、可选 Benjamini-Hochberg 调整值和 `-3..3`
置信分级。默认分析方格点数，显式 `FIELD_SUM` 是平台扩展；可选时间切片按片独立计算统计与多重检验。
`SPATIAL_MULTI_VARIABLE_GRID` 从 `4.70` 引入；低版本不能携带该节点。节点以所有变量来源 Geometry
的共同未筛选外包范围生成统一方格或六边形，各变量独立选择来源、筛选和可选搜索距离，并输出最近距离、
最近属性或关联要素汇总；最多 32 个变量和 100 万格。
`SPATIAL_ENRICH_FROM_GRID` 从 `4.71` 引入；低版本不能携带该节点。节点接收有界 XY Point 和已有
XY Polygon 多变量格网，按相交回填显式选择的标量字段；未匹配 Point 保留，边界多格命中按格网 ID
确定性选择一格。
`SPATIAL_GROUP_BY_PROXIMITY` 从 `4.72` 引入；低版本不能携带该节点。节点接收一张有界 XY
Point/Line/Polygon 表，支持 Intersects、Touches、Near Planar/Geodesic 及可选时间 Intersects/Near、
受控同值/绝对差属性关系，并通过 Connected Components 输出传递连通组。
`TRACE_PROXIMITY_EVENTS` 从 `4.73` 引入；低版本不能携带该节点。节点接收一张有界、带时间和
STRING 实体 ID 的 XY Point 观测表，支持显式起始 ID 或另一张起始表。空间、时间和同值属性全部
满足时形成接触，按最大深度输出每个下游实体首次追踪事件及可选后续轨迹。
`SNAP_TRACKS` 从 `4.74` 引入；低版本不能携带该节点。节点接收有界 XY Point
观测表和有界 XY LineString 网络表，按轨迹时间次序使用 Viterbi 联合选择搜索距离内的
道路候选，并验证同线或共享端点的直接相邻转移及可选方向。首版不搜索无观测的多条中间道路。
`SPATIAL_SIMILAR_LOCATIONS` 从 `4.75` 引入；低版本不能携带该节点。节点接收参考位置表与候选位置表，
按 1～32 个同名同数值类型字段形成参考目标并排名。属性值使用标准化平方差之和；属性轮廓使用
`1 - cosine`，因此 `cosimindex=0` 表示最相似。完整契约见
[查找相似位置](canvas-spatial-next-processors/spatial-similar-locations.md)。
`SPATIAL_DESCRIBE_DATASET` 从 `4.76` 引入；低版本不能携带该节点。节点接收一张有界表，保留全部
入口表，并依次追加字段统计表、数据集描述表、可选样本表和可选范围表。逐字段统计跳过 Geometry/Binary；
范围表是所选非空 Geometry 的共同 XY Envelope Polygon，继承 CRS 并固定为 XY。完整契约见
[描述数据集](canvas-spatial-next-processors/spatial-describe-dataset.md)。
空间裁剪可选 `maskCombination` 从 `4.77` 引入。`DISSOLVE_ALL` 使用空间 INNER Join 为每条来源要素
定位相交 Mask，以计划内来源行 ID 分组执行 `ST_Union_Agg`，再对来源执行一次 Intersection；重叠覆盖
不会重复输出，分离片段保留在同一个 Multi Geometry 中。`PAIRWISE` 及缺失/null 保持旧版逐 Mask
输出。任意非 null 值在低版本返回 `SPATIAL_CLIP_MASK_COMBINATION_REQUIRE_SCHEMA_VERSION`。
空间连接可选 `outputColumns` 从 `4.54` 引入；缺失/null 保持旧版左表全部字段后接右表全部字段，
且左右字段同名时拒绝。显式数组按来源侧选择、排除、改名和排序字段；空数组是可保存草稿，Compiler
返回 `JOIN_OUTPUT_COLUMNS_REQUIRED`。任何非 null 数组在低版本返回
`SPATIAL_JOIN_OUTPUT_COLUMNS_REQUIRE_SCHEMA_VERSION`。
空间连接可选 `attributeConditions` 从 `4.55` 引入；缺失/null 保持仅空间条件语义，非 null 数组
最多 8 项并与所有空间条件按 AND 组合。低版本返回
`SPATIAL_JOIN_ATTRIBUTE_CONDITIONS_REQUIRE_SCHEMA_VERSION`。
空间连接 `joinType=LEFT` 从 `4.56` 引入，表示保留全部左侧目标要素；未匹配记录的右侧投影字段为
NULL。`INNER` 保持旧结果，`RIGHT/FULL` 仍不支持。低版本返回
`SPATIAL_JOIN_KEEP_ALL_REQUIRE_SCHEMA_VERSION`。
空间连接可选 `joinOperation` 从 `4.57` 引入；`JOIN_ONE_TO_MANY` 表示同一目标要素命中多条连接记录时
输出全部匹配组合。缺失/null 保持既有一对多行为；显式值在低版本返回
`SPATIAL_JOIN_OPERATION_REQUIRE_SCHEMA_VERSION`。Canvas 4.58 起可选择 `JOIN_ONE_TO_ONE` 并通过
`oneToOne` 配置汇总全部匹配项或确定性保留一项；一对一及任何非 null `oneToOne` 草稿在低版本返回
`SPATIAL_JOIN_ONE_TO_ONE_REQUIRE_SCHEMA_VERSION`。
空间连接可选 `temporalCondition` 从 `4.59` 引入；缺失/null 保持不按时间匹配，非 null 对象（包括
未完成草稿）与全部空间、属性条件按 AND 组合。每侧用开始字段与可选结束字段表达瞬时或闭区间，支持
15 种有方向时间关系；任何非 null 对象在低版本返回
`SPATIAL_JOIN_TEMPORAL_CONDITION_REQUIRE_SCHEMA_VERSION`。
空间连接可选 `spatialNear` 与 `distanceOutput` 从 `4.60` 引入。`spatialNear` 可单独作为空间条件，
也可与拓扑、属性和时间条件按 AND 组合；`PLANAR` 在来源 CRS 中判断，`GEODESIC` 仅接受
EPSG:4326 XY 并使用 Geometry 真实最近位置。`distanceOutput.enabled=true` 仅支持一对多，可分别输出
空间距离和时间 Near 的区间间隔。任一非 null 对象在低版本返回
`SPATIAL_JOIN_NEAR_REQUIRE_SCHEMA_VERSION`。
较低小版本定义继续读取，并在保存和导出时规范化为 `4.77`；
标记为低于引入版本却携带对应能力的定义必须拒绝，
不能因为升级而猜测其语义。

同一大版本内，小版本只允许新增节点类型、可选字段或其他不改变已有定义语义的能力，并必须向下兼容；读取受支持的较低小版本后，保存和导出统一规范化为当前小版本。高于当前实现的小版本必须拒绝。删除或重命名字段、改变已有字段或节点语义、修改核心图规则等不兼容变化必须升级大版本，并将小版本重置为 `0`。版本不得使用 JSON 小数表示，避免 `2.1`、`2.10` 的比较歧义。

实体中的 `definitionVersion` 与 JSON 中的协议版本含义不同：

- `schemaVersion + schemaMinorVersion` 表示 JSON 协议版本。
- `definitionVersion` 表示某个任务定义被成功修改的次数。

### 3.2 节点公共结构

```json
{
  "id": "2e73144a-372b-40ae-b972-d56e528470c5",
  "type": "JDBC_INPUT",
  "name": "订单表输入",
  "layout": {
    "x": 120,
    "y": 160,
    "width": 300,
    "height": 164
  },
  "configuration": {}
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | UUID | 节点稳定 ID，创建后不得因移动、改名或重新加载而改变 |
| `type` | enum | 稳定节点类型 ID，不得使用 Java 类名或 X6 Shape 名称 |
| `name` | string | 用户可编辑的展示名称，长度 `1..100` |
| `layout` | object | 与 X6 解耦的画布布局信息 |
| `configuration` | object | 由 `type` 决定的强类型配置 |

`type` 决定节点类别、语义化展示、尺寸派生规则、端口规则、配置组件和 Schema 处理器，因此不持久化客户端传入的 `category`、`shape` 或节点组件名称。

`layout.x/y` 是稳定的画布位置。`layout.width/height` 只是在定义写出时记录的语义节点基础尺寸快照，不是用户可编辑配置，也不是加载时的权威尺寸。编辑器加载新旧定义时必须保留 `x/y`，忽略已保存的 `width/height`，再通过对应节点 Spec 的 `canvasView.resolveSize(configuration)` 重新计算基础尺寸；用户不能自由拉伸节点。保存、导出和定义指纹比较均使用重新计算后的基础尺寸，因此仅打开包含旧尺寸的定义不会产生未保存修改。

节点主资源未选择时使用紧凑占位尺寸；部分或完整配置按节点专属结构展开。字段、条件、映射和函数列表最多预览前两条，预览数量、节点专属只读内容、Task Engine 返回的 `inputTables/outputTables`、元数据名称和校验状态均不属于稳定任务语义。警告或错误可在运行时临时增加 28px 摘要条，但该增量不得写入 `layout.height`。

布局坐标和写出的基础尺寸必须是有限数值。第一版建议限制：

- `x`、`y`：`-100000..100000`
- `width`：`180..1000`
- `height`：`96..1000`

### 3.3 边结构

```json
{
  "id": "ba2b498b-6ab9-47e1-90c4-e4ee38894fe1",
  "sourceNodeId": "2e73144a-372b-40ae-b972-d56e528470c5",
  "targetNodeId": "c91532b3-88c2-4cda-a863-b8807baa7d42"
}
```

边只表达业务节点之间的数据流，不持久化 X6 Port ID、Connector、Router、颜色或折线路径。第一版节点只有一个逻辑输入端和一个逻辑输出端，前端端口由节点注册表生成。

### 3.4 不进入持久化定义的字段

以下信息只属于设计器或未来运行期：

- X6 `shape`、`view`、`ports`、`zIndex` 和完整 Cell JSON
- 节点选中、折叠、悬浮、校验中等 UI 状态
- 画布缩放比例、滚动位置和当前选中节点
- 节点运行状态、开始结束时间、行数、日志和错误堆栈
- JDBC/HTTP 连接内容和凭据
- Dataset、DataFrame、Spark 类型或执行器内部对象
- 从数据库读取的字段 Schema 和数据预览

## 4. 物理表定位

JDBC 数据源已经固定数据库和 Schema，Canvas 节点中的每个表配置只保存该数据源下的 `tableName`，不重复保存 `catalogName` 或 `schemaName`：

- `tableName` 必填，去除首尾空白后不能为空。
- `tableName` 只保存元数据返回的原始表名，不允许提交已经拼接、引用或转义的 `schema.table` 字符串。
- 执行时通过 `dataSourceId` 读取数据源的数据库和 Schema，与节点中的 `tableName` 共同定位物理表。
- 一个数据源只允许在其配置的数据库和 Schema 内选表；需要访问其他 Schema 时创建独立数据源。
- 后续数据库访问仍由方言负责标识符引用以及数据库、Schema 的解析。

## 5. `JDBC_INPUT` 节点

### 5.1 配置结构

```json
{
  "dataSourceId": "a406e119-fbd2-4173-84ee-c92c69231168",
  "tables": [
    {
      "tableName": "orders",
      "readOptions": [
        { "name": "fetchsize", "value": "10000" },
        { "name": "pushDownAggregate", "value": "true" }
      ]
    },
    { "tableName": "customers", "readOptions": [] }
  ]
}
```

对应 TypeScript 类型：

```ts
interface JdbcInputTableSelection {
  tableName: string;
  readOptions: JdbcInputReadOption[];
}

interface JdbcInputReadOption {
  name: string;
  value: string;
}

interface JdbcInputConfiguration {
  dataSourceId: string;
  tables: JdbcInputTableSelection[];
}
```

`tables` 使用对象数组而不是字符串数组，为逐表读取调优与以后独立的分片读取配置保留稳定边界。
`readOptions` 从 Canvas `4.0` 起属于每张 JDBC 表选择；其键和值均为字符串，并在读取
该物理表时才生效。它不保存 URL、Driver、用户、密码、物理表、Catalog/Schema 或其他连接上下文。
自由 SQL 仍使用独立 `JDBC_QUERY_INPUT`。

### 5.1A 逐表高级读取参数

每张表最多可保存 32 个参数。Task Engine 是权威校验边界：参数名长度为 `1..128`，匹配
`[A-Za-z][A-Za-z0-9._-]{0,127}`，按大小写不敏感唯一；参数值最多 4096 个字符，允许多行。
`fetchsize`、`queryTimeout` 必须为非负整数；`pushDownPredicate`、`pushDownAggregate`、
`pushDownLimit`、`pushDownOffset`、`pushDownTableSample`、`pushDownJoin` 和
`preferTimestampNTZ` 必须是 `true/false`；`sessionInitStatement` 配置后不能为空。

以下名称由平台控制、与写入相关、包含凭据或预留给未来分片读取，必须拒绝：连接和目标参数
`url/driver/user/password/dbtable/query/prepareQuery/customSchema`，认证提供器参数，
`catalog/schema/currentSchema/database/databaseName`，写入参数，
`partitionColumn/lowerBound/upperBound/numPartitions`，以及匹配
`password/passwd/pwd/secret/token/credential/apiKey/accessKey/secretKey/privateKey` 的名称。
未知参数允许保存，并由 Spark/JDBC Driver 在真实读取时解释。

实际应用顺序固定为：运行 Manifest 的受保护连接与凭据 → 数据源 JDBC Properties → 当前物理表
`readOptions` → 平台最终设置 `dbtable` → `load()`。因此逐表参数可以覆盖普通数据源 JDBC Property，
但不能覆盖连接、目标表或分片边界。Compiler 不连接数据库也不执行 `sessionInitStatement`。

### 5.2 输入与输出

- 节点类别：`INPUT`
- 入边数量：必须为 `0`
- 出边数量：至少为 `1`
- 输出 Map：包含 `configuration.tables` 对应的全部无重复条目
- 输出 Key：每个 `configuration.tables[i].tableName`

概念结果：

```text
{
  "orders" -> CanvasTable(name="orders", origin=JdbcTableOrigin(...)),
  "customers" -> CanvasTable(name="customers", origin=JdbcTableOrigin(...))
}
```

### 5.3 校验

- `dataSourceId` 是有效 UUID，数据源存在且已启用。
- 数据源类型是 JDBC，并具有 `SOURCE` 用途。
- `tables` 至少包含一个对象，每个 `tableName` 完整且没有包含数据库或 Schema 前缀。
- 同一节点内 `tableName` 精确匹配后唯一，重复时返回 `DUPLICATE_TABLE_NAME`。
- 通过元数据接口可以定位每张表。
- 每张表的字段都可以映射为平台类型；存在 `LOSSY` 或 `UNSUPPORTED` 映射时校验失败。
- `readOptions` 按上述逐表高级读取参数规则校验；参数值、尤其是 `sessionInitStatement`，不得进入节点卡片、摘要、运行日志或错误摘要。
- 不在定义中保存读取到的字段 Schema。

## 5A. `JDBC_QUERY_INPUT` 节点

### 5A.1 配置结构

```json
{
  "dataSourceId": "a406e119-fbd2-4173-84ee-c92c69231168",
  "sql": "SELECT order_id, amount FROM orders",
  "outputTableName": "queried_orders",
  "analyzedSqlSha256": "e2d01962c99c45414d11e972cd83417821451accf8b66e95b40a35513c0fd8c5",
  "outputColumns": [
    {
      "name": "order_id",
      "fieldType": "LONG",
      "length": null,
      "precision": null,
      "scale": null,
      "nullable": false,
      "defaultValue": null,
      "autoIncrement": false,
      "generated": false,
      "comment": null,
      "geometry": null
    }
  ]
}
```

对应 TypeScript 类型：

```ts
interface JdbcQueryInputConfiguration {
  dataSourceId: string;
  sql: string;
  outputTableName: string;
  analyzedSqlSha256: string;
  outputColumns: CanvasColumnSchema[];
}
```

SQL 最大 100,000 字符，只接受 PostgreSQL/HighGo/MySQL/openGauss/人大金仓的单条 `SELECT` 或 `WITH ... SELECT`。不支持模板变量、运行参数、Session 配置、预览和并行分区读取。Hash 使用去除可选终止分号并 trim 后的 UTF-8 SQL 计算，固定为 64 位小写 SHA-256。

### 5A.2 输入、输出与运行语义

- 节点类别：`INPUT`
- 执行模式：`BATCH`、`STREAMING`
- 入边数量：必须为 `0`
- 出边数量：至少为 `1`
- 输出 Map：只包含 `outputTableName` 对应的一个 `BOUNDED` 表
- 输出 Origin：`JDBC_QUERY`
- Compiler 使用保存的 `outputColumns` 构造零行 Dataset，不访问数据库
- Streaming 任务只在启动时读取一次并作为静态维表缓存，运行期间不自动刷新

### 5A.3 分析与校验

- `dataSourceId` 必须是已启用、具有 `SOURCE` 用途的 PostgreSQL、HighGo、MySQL、openGauss 或人大金仓数据源。
- `outputTableName` 必填，且不得与当前表 Map 中已有名称冲突。
- 用户必须通过查询分析接口显式生成 Hash 与字段快照；编辑 SQL 不自动访问数据库。
- `outputColumns` 至少一项，字段名称精确匹配且不重复；类型参数必须完整合法。
- 当前 SQL Hash 与 `analyzedSqlSha256` 不一致时返回 `JDBC_QUERY_SCHEMA_STALE`。
- 发布、重新启用和运行准备不重新分析或比较查询结果结构；保存的 `outputColumns` 只作为逻辑
  Schema。SQL 文本变化仍返回 `JDBC_QUERY_SCHEMA_STALE`，真实结果能否使用由 Spark 实际执行判断。
- 查询列必须能无损映射为平台类型；首版拒绝 Geometry。需要空间字段时先让 SQL 输出 WKT/WKB，再连接 `GEOMETRY_CONSTRUCT`。
- Runner 再次校验只读语法和 Hash，不比较运行时结果结构；日志、生命周期摘要和错误不得包含
  SQL、SQL 字面量、查询数据或凭据。

查询分析接口、只读连接和元数据错误边界见 [数据源管理](data-source-management.md)，完整设计见 [JDBC_QUERY_INPUT 设计](canvas-jdbc-query-input-design.md)。

## 6. `HTTP_API_INPUT` 节点

### 6.1 配置结构

```json
{
  "dataSourceId": "d5823019-3479-45bc-81ef-f945814558cc",
  "resources": [
    {
      "resourceId": "e67ebceb-78ab-4eb8-bf90-7d428cc8fa09",
      "outputTableName": "api_orders",
      "runtimeParameters": [{ "name": "startDate", "value": "2026-07-01" }]
    }
  ]
}
```

对应 TypeScript 类型：

```ts
interface HttpApiInputConfiguration {
  dataSourceId: string;
  resources: HttpApiInputResourceSelection[];
}
```

节点不保存 Base URL、请求模板、输出 Schema 或凭据。API 资源负责请求、签名、分页、异步轮询和 Schema；Canvas 只提供本次任务固定的非敏感业务参数。

### 6.2 输入与输出

- 节点类别：`INPUT`
- 入边数量：必须为 `0`
- 出边数量：至少为 `1`
- 输出 Map：按 `resources` 配置顺序包含全部资源
- 输出 Key：每项的 `outputTableName`
- 编译 Schema：来自 API 资源显式声明的 `outputFields`，编译时不访问远程接口

### 6.3 校验

- `dataSourceId` 和每项 `resourceId` 是有效 UUID，且资源确实属于该数据源；同一资源不能重复。
- 数据源类型是 `HTTP_API`、已启用并具有 `SOURCE` 用途；API 资源也必须已启用。
- 每项 `outputTableName` 是合法且不冲突的逻辑表名。
- 每项运行时参数名合法且不重复；参数值不能用于保存敏感凭据。
- API 资源存在至少一个完整、可由平台类型表达的输出字段。

## 7. `JOIN` 节点

### 7.1 配置结构

```json
{
  "leftTableName": "orders",
  "rightTableName": "customers",
  "outputTableName": "order_customer",
  "joinType": "INNER",
  "conditions": [
    {
      "leftColumnName": "customer_id",
      "operator": "EQUALS",
      "rightColumnName": "customer_key"
    }
  ],
  "outputColumns": [
    {
      "sourceSide": "LEFT",
      "sourceColumnName": "id",
      "outputColumnName": "id",
      "included": true
    },
    {
      "sourceSide": "RIGHT",
      "sourceColumnName": "id",
      "outputColumnName": "customers_id",
      "included": true
    }
  ],
  "outputColumns": [
    {
      "sourceSide": "LEFT",
      "sourceColumnName": "id",
      "outputColumnName": "id",
      "included": true
    },
    {
      "sourceSide": "RIGHT",
      "sourceColumnName": "id",
      "outputColumnName": "regions_id",
      "included": true
    }
  ]
}
```

对应 TypeScript 类型：

```ts
type JoinType = 'INNER' | 'LEFT' | 'RIGHT' | 'FULL';

interface JoinCondition {
  leftColumnName: string;
  operator: 'EQUALS';
  rightColumnName: string;
}

interface JoinOutputColumn {
  sourceSide: 'LEFT' | 'RIGHT';
  sourceColumnName: string;
  outputColumnName: string;
  included: boolean;
}

interface JoinConfiguration {
  leftTableName: string;
  rightTableName: string;
  outputTableName: string;
  joinType: JoinType;
  conditions: JoinCondition[];
  outputColumns: JoinOutputColumn[];
}
```

第一版只支持等值 Join，多个条件固定使用 `AND` 组合。暂不支持 CROSS、非等值操作符、表达式、OR 条件或隐式类型转换。

### 7.2 输入与输出

- 节点类别：`PROCESSOR`
- 入边数量：至少为 `1`，不限制上限
- 出边数量：至少为 `1`
- 单条入边可以携带包含左右表的完整表 Map；多条入边用于合并来自不同上游分支的表 Map。
- 存在多条入边时，节点先无覆盖合并所有直接上游输出的表 Map。
- 合并时发现同名表立即失败，不允许后到的表覆盖先到的表。
- 左表和右表从合并后的 Map 中按表名取得。
- 输出 Map 保留所有输入表，并新增 `outputTableName` 对应的 Join 结果表。
- `outputTableName` 已存在时返回 `DUPLICATE_TABLE_NAME`。

示意：

```text
输入：
{
  "orders" -> Orders,
  "customers" -> Customers
}

输出：
{
  "orders" -> Orders,
  "customers" -> Customers,
  "order_customer" -> JoinedOrders
}
```

### 7.3 字段 Schema

Join 结果使用 `outputColumns` 执行显式投影，数组顺序就是最终字段顺序。`included=false`
保留配置但不输出该字段；启用字段的最终 `outputColumnName` 按大小写不敏感规则保持唯一。

左右表允许存在同名来源字段。Inspector 首次取得两侧 Schema 时生成可编辑建议：左表字段保持原名；
右表字段只有与左表同名时才建议为 `右表表名_字段名`，例如 `sys_dept.id → sys_dept_id`。
如果建议名称仍与其他输出字段重名，不继续追加编号或二次前缀，由用户手动改名或排除字段。
已经编辑的投影不因上游 Schema 变化被静默重建。

Join 类型对可空性的影响：

- `INNER`：保留左右字段原始可空性。
- `LEFT`：右表字段统一视为可空。
- `RIGHT`：左表字段统一视为可空。
- `FULL`：左右字段统一视为可空。

### 7.4 校验

- `leftTableName`、`rightTableName` 和 `outputTableName` 均非空。
- 左右表名不能相同。
- 输出表名不能与当前输入 Map 中任何 Key 相同。
- 输入 Map 中必须存在左右表，且必须是两个不同的 `CanvasTable`。
- 至少配置一个 Join 条件。
- 条件中的左右字段分别存在于左右表。
- 同一组左右字段条件不得重复。
- Join 先构造真实 Spark 等值表达式并由 Analyzer 判断可比较性；Analyzer 接受时直接通过且不再按平台类型产生风险警告，Analyzer 拒绝时返回错误。
- Geometry 不得作为普通 Join 的 `EQUALS` 条件；空间相等必须使用 `SPATIAL_JOIN/EQUALS`。
- `outputColumns` 至少启用一个字段，同一侧来源字段只能配置一次。
- 每项来源字段必须存在于对应左表或右表；失效配置保留并返回 `COLUMN_NOT_FOUND`。
- 最终启用的输出字段名不得重复；重名返回 `DUPLICATE_COLUMN_NAME`。

## 7A. `STREAM_JOIN` 节点

`STREAM_JOIN` 复用普通 Join 的 `JoinCondition` 和 `JoinOutputColumn`，第一阶段只支持左侧无界流
关联右侧有界静态维表：

```json
{
  "leftTableName": "order_events",
  "rightTableName": "dim_customer",
  "outputTableName": "enriched_events",
  "joinType": "LEFT",
  "conditions": [
    {
      "leftColumnName": "customer_id",
      "operator": "EQUALS",
      "rightColumnName": "id"
    }
  ],
  "outputColumns": [
    {
      "sourceSide": "LEFT",
      "sourceColumnName": "event_time",
      "outputColumnName": "event_time",
      "included": true
    },
    {
      "sourceSide": "RIGHT",
      "sourceColumnName": "id",
      "outputColumnName": "dim_customer_id",
      "included": true
    }
  ]
}
```

- `joinType` 仅支持 `INNER | LEFT`；左表必须为 `UNBOUNDED`，右表必须为 `BOUNDED`。
- 左右表允许同名字段，最终结果严格按 `outputColumns` 顺序执行限定来源的 `select + alias`。
- Inspector 的默认命名、字段排除、改名、排序、右侧 Join Key 排除和重建规则与普通 Join 相同；
  右侧重名字段使用完整逻辑表名作为前缀，建议后仍冲突时由用户处理。
- 同一侧来源字段只能配置一次，至少启用一个输出字段，最终字段名按大小写不敏感唯一。
- 左侧事件时间字段保留时继续传播 Watermark；被改名时同步更新事件时间字段名；被排除时清除
  输出 Schema 的事件时间和 Watermark。右侧静态表的事件时间信息不传播。
- 静态维表只在流任务启动时读取，不自动刷新；本节点不支持 Stream-Stream Join。

## 7.5 `SPATIAL_TRANSFORM` 节点

节点从一条直接上游选择逻辑表和 Geometry 字段，使用 Sedona `ST_Transform` 显式转换 CRS，
并以新表名追加结果；所有其他字段及 Geometry kind、dimension 保持不变。

```json
{
  "sourceTableName": "orders",
  "outputTableName": "orders_3857",
  "geometryColumnName": "location",
  "targetCrs": {
    "authority": "EPSG",
    "code": 3857
  }
}
```

- 类别为 `PROCESSOR`，执行模式为 `BATCH`，协议引入版本为 `1.20`。
- 至少一条入边；允许没有出边，此时结果无人消费并产生 `UNCONSUMED_PROCESSOR_OUTPUT` 警告。
- source CRS 只来自 Task Engine 上游 Schema，定义不能覆盖。
- 第一阶段只接受 `EPSG + XY`；源和目标 CRS 相同时返回 Warning。
- 输出表名与已有 Map Key 冲突时返回 `DUPLICATE_TABLE_NAME`。

## 7.6 `SPATIAL_JOIN` 节点

节点使用 Sedona 受控 Column API 执行两表空间连接，不接受用户 SQL：

```json
{
  "leftTableName": "orders",
  "rightTableName": "regions",
  "outputTableName": "orders_with_region",
  "joinType": "INNER",
  "conditions": [
    {
      "leftGeometryColumnName": "location",
      "predicate": "WITHIN",
      "rightGeometryColumnName": "boundary"
    }
  ],
  "attributeConditions": [
    {
      "leftColumnName": "tenant_id",
      "operator": "EQUALS",
      "rightColumnName": "tenant_id"
    }
  ],
  "temporalCondition": {
    "relationship": "NEAR_BEFORE",
    "leftStartColumnName": "ordered_at",
    "leftEndColumnName": null,
    "rightStartColumnName": "valid_from",
    "rightEndColumnName": "valid_to",
    "nearDistance": 15,
    "nearDistanceUnit": "MINUTES"
  },
  "spatialNear": {
    "leftGeometryColumnName": "location",
    "rightGeometryColumnName": "boundary",
    "distanceMethod": "GEODESIC",
    "distance": 2,
    "distanceUnit": "KILOMETERS"
  },
  "distanceOutput": {
    "enabled": true,
    "spatialDistanceColumnName": "join_distance_km",
    "spatialDistanceUnit": "KILOMETERS",
    "temporalDifferenceColumnName": "join_time_gap_minutes",
    "temporalDifferenceUnit": "MINUTES"
  },
  "joinOperation": "JOIN_ONE_TO_MANY"
}
```

- 类别为 `PROCESSOR`，执行模式为 `BATCH`，协议引入版本为 `1.20`。
- 至少一条入边；允许没有出边，左右表从合并后的表 Map 选择。左表是目标要素，右表是连接要素；
  支持 `INNER`，Canvas 4.56 起支持 `LEFT`。
- 拓扑条件数量为 `0..8`，多条件固定使用 `AND`，重复条件被拒绝；`conditions` 与 `spatialNear`
  至少配置一种。
- 谓词支持 `INTERSECTS/CONTAINS/WITHIN/COVERS/COVERED_BY/TOUCHES/OVERLAPS/CROSSES/EQUALS`。
- 两侧字段必须是 Geometry 且 CRS、dimension 完全一致；不执行隐式 CRS 转换。
- Canvas 4.55 的可选 `attributeConditions` 最多 8 项，复用普通 Join 的字段等值条件；每项左右字段
  必须存在且不能是 Geometry，使用 Spark SQL 普通等号，因此任一侧 NULL 都不匹配。属性条件与全部
  空间条件固定按 `AND` 组合，字段类型能否比较由 Spark Analyzer 判断，不另建平台类型白名单。
- 缺失/null `attributeConditions` 保持旧空间连接；非 null 空数组表示显式不增加属性条件。纯属性连接
  继续使用 `JOIN`，不会为 ArcGIS 工具名重复建设第二套普通 Join。
- Canvas 4.59 的可选 `temporalCondition` 支持 `EQUALS/INTERSECTS/DURING/CONTAINS/FINISHES/
  FINISHED_BY/MEETS/MET_BY/OVERLAPS/OVERLAPPED_BY/STARTS/STARTED_BY/NEAR/NEAR_BEFORE/NEAR_AFTER`。
  左侧是目标要素、右侧是连接要素；每侧必填开始/瞬时时间字段，可选结束字段，结束字段为 null 时按瞬时
  处理。全部字段必须同为 `DATE`、`TIMESTAMP` 或 `TIMESTAMP_NTZ`，区间按闭区间解释。
- `NEAR/NEAR_BEFORE/NEAR_AFTER` 使用正整数固定时长，单位支持毫秒、秒、分钟、小时、固定 24 小时日和
  固定 7 日周；时间关系与全部空间及属性条件按 AND 组合。时间为 NULL 或任一侧开始晚于结束的记录
  不匹配；LEFT 下仍保留该目标记录，右侧投影为 NULL。Compiler 只构造惰性计划，不读取真实时间值。
- Canvas 4.60 的可选 `spatialNear` 独立于拓扑谓词。`PLANAR` 使用来源 CRS 的二维距离；
  `GEODESIC` 仅支持 EPSG:4326 XY 的 Point、MultiPoint、LineString、MultiLineString、Polygon 和
  MultiPolygon，并使用 Geometry 真实最近位置的 WGS84 椭球距离，不使用质心或 Sedona 非点球面近似。
  GEODESIC 先以 ECEF XY 包围和独立 Z 区间执行保守候选召回，再以真实测地距离作最终包含边界的阈值判断；
  候选索引只允许假阳性，不决定结果。NULL、Empty 和无效 Geometry 不匹配或以稳定运行错误失败。
- `distanceOutput.enabled=true` 只允许 `JOIN_ONE_TO_MANY`。空间 Near 输出空间距离；时间
  `NEAR/NEAR_BEFORE/NEAR_AFTER` 输出两个闭区间的非负间隔，相交时为 0；两种 Near 同时启用时输出
  两个独立字段。字段类型统一为 `DECIMAL(38,12)`，LEFT 未匹配记录为 NULL。空间和时间输出单位分别
  独立配置；GEODESIC 不允许来源 CRS 单位。
- `INNER` 只保留匹配目标；`LEFT` 对应 Join Features 的 Keep all target features，保留全部左表记录，
  未匹配记录的右侧投影字段为 NULL。一个目标命中多个右表记录时仍逐组合输出，不聚合或去重。
- Canvas 4.57 的可选 `joinOperation` 可将结果粒度显式声明为 `JOIN_ONE_TO_MANY`。缺失/null 与该值
  语义相同，均输出全部匹配组合。
- Canvas 4.58 的 `JOIN_ONE_TO_ONE` 要求 `oneToOne`：`SUMMARIZE_MATCHES` 只直接投影左侧目标字段，
  追加 Join Count 和至多 32 项右表数值统计；`SUM/MIN/MAX/MEAN/STDDEV` 忽略 NULL，STDDEV 为样本标准差。
  LEFT 未匹配目标的 Join Count 为 0、统计为 NULL。
- `KEEP_ONE` 可选择 FIRST、数值最大/最小或日期最新/最旧。FIRST 完全按右表 `stableOrder` 取首项；
  其他策略先按主字段排序，再以 `stableOrder` 消除并列。稳定顺序至少一项、不能使用 Geometry；完整排序
  仍并列时运行失败，不依赖 Spark 输入或分区顺序。
- Canvas 4.54 的 `outputColumns` 复用 Join 字段投影：来源侧与字段必须存在且不能重复，至少启用一项，
  启用后的最终名称按大小写不敏感规则唯一；结果字段严格按照数组顺序执行限定来源的 `select + alias`。
- 新建议保持左表字段原名；右表字段与左表重名时使用`右表完整逻辑表名_字段名`。建议后仍重名时不追加
  隐藏序号，由用户排除或改名。
- 缺失/null `outputColumns` 保持 4.53 及更早的旧行为：左表全部字段后接右表全部字段，同名字段返回
  `DUPLICATE_COLUMN_NAME`，不会因读取旧定义而自动改名。
- 不支持 `DISJOINT`、`DWITHIN`、`RIGHT/FULL`、任意表达式或隐藏拓扑容差；空间 Near 必须通过
  `spatialNear` 显式配置，时间 Near 仍由 `temporalCondition` 独立表达。

## 7.7 `GEOMETRY_CONSTRUCT` 节点

节点从 `WKT/WKB/GEOJSON/POINT_FROM_XY` 判别来源构造一个 Sedona Geometry 字段，显式
设置目标 `GeometryKind + EPSG CRS + XY`，并将字段追加到来源字段之后。

- 类别为 `PROCESSOR`，支持 `BATCH/STREAMING`，协议引入版本为 `1.21`。
- 至少一条入边；允许没有出边，来源 NULL 时输出 NULL。
- WKT/GeoJSON 来源必须是 STRING，WKB 必须是 BINARY，X/Y 必须是数值字段。
- 目标必须是具体 kind；`POINT_FROM_XY` 的目标固定为 POINT。
- 嵌入式 CRS 不受信任；畸形值或实际 kind 不匹配在运行时失败，不静默置 NULL。
- 完整配置、错误码和安全边界见
  [Geometry 构造设计](canvas-geometry-construct-processor-design.md)。

## 7.8 `GEOMETRY_VALIDATE` 节点

节点使用 `ST_IsValid` 追加 nullable BOOLEAN，并可使用 `ST_IsValidReason` 追加 nullable
STRING。原因仅在 Geometry 无效时写入；有效或 NULL 输入的原因均为 NULL。

- 类别为 `PROCESSOR`，支持 `BATCH/STREAMING`，协议引入版本为 `1.21`。
- 节点只诊断，不删行、不修复，也不因 `isValid=false` 失败。
- 原 Geometry 字段及空间元数据保持不变。
- 完整配置见 [Geometry 校验设计](canvas-geometry-validate-processor-design.md)。

## 7.9 `SPATIAL_MEASURE` 节点

节点按配置顺序执行 `1..32` 个 `AREA/LENGTH/PERIMETER/DISTANCE/X/Y` 测量，并在来源
字段之后追加 nullable DOUBLE 字段。

- 类别为 `PROCESSOR`，支持 `BATCH/STREAMING`，协议引入版本为 `1.21`。
- PLANAR 使用 CRS 坐标单位或平方；EPSG:4326 产生角度单位 Warning。
- SPHEROID 只接受 EPSG:4326，长度/周长/距离输出米，面积输出平方米。
- 4.50 可为 AREA 显式选择公共面积单位，为 LENGTH/PERIMETER/DISTANCE 显式选择公共距离单位；
  投影 PLANAR 按 CRS 轴单位可靠换算，地理 PLANAR 不把角度/角度平方近似成米制结果。
- 缺失/null `outputUnit` 保持旧结果；SPHEROID 不接受 `SOURCE_CRS_UNIT`。
- AREA/PERIMETER 只接受 Polygon/MultiPolygon，LENGTH 只接受
  LineString/MultiLineString，X/Y 只接受 Point。
- DISTANCE 两侧 dimension 必须一致；PLANAR 还要求 CRS 完全一致。
- 完整测量联合和错误码见 [空间测量设计](canvas-spatial-measure-processor-design.md)。

## 7.10 `GEOMETRY_SERIALIZE` 节点

节点保留原 Geometry，并追加 WKT STRING、WKB BINARY 或 GeoJSON STRING 字段。

- 类别为 `PROCESSOR`，支持 `BATCH/STREAMING`，协议引入版本为 `1.21`。
- NULL Geometry 输出 NULL；GeoJSON 只接受 EPSG:4326。
- 不执行隐式坐标转换、精度裁剪，也不支持 EWKT/EWKB/KML/GML。
- Kafka JSON Value 不接受 Geometry；写 Kafka 前先使用本节点转换为 WKT/WKB/GeoJSON，再在 Kafka Output 中选择序列化后的字段。
- 完整配置见 [Geometry 序列化设计](canvas-geometry-serialize-processor-design.md)。

## 7.11 `GEOMETRY_REPAIR` 节点

节点保留来源 Geometry，并使用 Sedona `ST_MakeValid(geometry, false)` 追加修复结果字段。

- 类别为 `PROCESSOR`，支持 `BATCH/STREAMING`，协议引入版本为 `1.22`。
- 至少一条入边；允许没有出边，节点逐行无状态处理。
- NULL 输入输出 NULL；无法修复的真实 Geometry 在运行时失败，不回退原值或静默置 NULL。
- 修复可能改变具体 GeometryKind，因此输出字段固定声明为通用 `GEOMETRY`；CRS 和 dimension
  继承来源字段。
- 完整配置、错误码和安全边界见
  [Geometry 修复设计](canvas-geometry-repair-processor-design.md)。

## 7.12 `GEOMETRY_DERIVE` 节点

以下引入于 4.9～4.20 的节点描述当前协议/实现（含明确标记的 4.21～4.60 可选能力），不是 ArcGIS 能力完成声明。
[空间分析审计路线图](canvas-spatial-analysis-processor-roadmap.md)及各节点 MD 同时记录当前配置、
目标参数/UI、实质差距与验收条件；目标字段不能直接用于当前定义。

节点保留来源字段，并按配置顺序一次性追加 `1..32` 个 Geometry 派生字段。

- 类别为 `PROCESSOR`，支持 `BATCH/STREAMING`，协议引入版本为 `4.9`。
- 支持 `CENTROID`、`POINT_ON_SURFACE`、`ENVELOPE`、`CONVEX_HULL` 和 `BOUNDARY`。
- `CENTROID/POINT_ON_SURFACE` 输出 `POINT`，其他结果声明为通用 `GEOMETRY`；CRS、有界性、事件时间和 Watermark 继承来源表。
- 4.29 每个 GeometryDerivation 可选 `geometryPolicy: PRESERVE_DIMENSION|OUTPUT_XY|LEGACY|null`。
  缺失/null/LEGACY 保持旧 Sedona 路径及既有 XY 限制；新策略检查无效输入并保证实际结果维度。
  PRESERVE_DIMENSION 下 CENTROID/POINT_ON_SURFACE/ENVELOPE 仅支持 XY；CONVEX_HULL/BOUNDARY 可保留 XY/XYZ/XYM/XYZM。
  OUTPUT_XY 显式只降维结果，原字段保留；高维来源提示 Warning。BOUNDARY 不支持 GeometryCollection，已知类型在编译期拒绝，通用 Geometry 在运行期检查。
  新增规则默认空函数/空来源/空输出、PRESERVE_DIMENSION，不猜选字段；空函数允许草稿保存，Compiler 拒绝执行。
- 任何显式 policy（包括 LEGACY）在低于 4.29 的定义中以 `GEOMETRY_UNARY_POLICY_REQUIRE_SCHEMA_VERSION` 拒绝。
  坐标/类型/维度能力错误分别为 `GEOMETRY_UNARY_INPUT_INVALID`、`GEOMETRY_UNARY_KIND_UNSUPPORTED`、`GEOMETRY_UNARY_DIMENSION_UNSUPPORTED`，不回显数据值。
- 所有派生项只引用节点入口处的来源字段，使用单次 `select` 生成，不允许同节点派生项相互引用。
- 完整配置、错误码和安全边界见
  [Geometry 派生设计](canvas-spatial-next-processors/geometry-derive.md)。

## 7.13 `GEOMETRY_SIMPLIFY` 节点

节点保留来源字段，使用 Douglas-Peucker 或拓扑保持算法追加一个简化后的 Geometry 字段。

- 类别为 `PROCESSOR`，支持 `BATCH/STREAMING`，协议引入版本为 `4.10`。
- 容差必须是有限正数并保存显式单位；投影 CRS 下可将[公共距离单位](canvas-spatial-units.md)可靠换算到
  来源轴单位，地理 CRS 只允许来源 CRS 角度单位并产生 Warning；角度单位不一定是度，例如 EPSG:4807 使用 grad。
- 4.29 同样可选 `geometryPolicy`，与派生共享协议门槛；缺失/null/LEGACY 保持原 Sedona 行为。
  新策略不执行隐式投影、输入修复或 Empty 行丢弃，NULL→NULL；输出字段为通用 `GEOMETRY`，CRS、nullable、有界性、事件时间和 Watermark 保持。
- PRESERVE_DIMENSION：Douglas-Peucker 可靠支持 XY/XYZ，M 不支持并在编译期拒绝；单要素拓扑保持可保留 XY/XYZ/XYM/XYZM。
  OUTPUT_XY 显式输出真实 XY，不修改来源字段；不是三维或测地简化。
- 新策略 Douglas-Peucker 关闭隐式面积修复；简化后无效结果以 `GEOMETRY_SIMPLIFY_RESULT_INVALID` 安全失败，合法 Empty 保留。
  旧策略仍保留库原有的面积后处理，不能把新语义冒充旧行为。
- 新建默认单要素拓扑保持、PRESERVE_DIMENSION、容差 null，必须由用户配置容差；null/非正数允许保存草稿，编译时拒绝。
  单位转换溢出返回安全配置问题；正数下溢为零时使用 `INVALID_GEOMETRY_SIMPLIFY_TOLERANCE` 定位到容差字段，
  不静默执行零容差，也不为仍可表示的正数增加人为最小值。
- 单要素拓扑保持不保证不同要素的公共边界；存在各自有效但重叠/缝隙的本地反例，不提供整图层覆盖简化。
- 完整配置、UI 与首版边界见
  [Geometry 简化设计](canvas-spatial-next-processors/geometry-simplify.md)。

## 7.13A `SPATIAL_NEAREST` 节点

- 类别为 `PROCESSOR`，仅支持 `BATCH`，协议引入版本为 `4.11`。
- 从合并后的入口表 Map 选择来源表和候选表，支持 Top N、最大距离、平面/测地线方法、距离单位和
  显式字段投影；结果始终生成新逻辑表。
- 参照 Enterprise 标准 Find Nearest 的部分距离能力，非 GA Server 同名工具；路网结果尚未实现。
- 4.30 可选 `matching: { semantics: EXACT_DISTANCE|LEGACY_KNN|null, sourceIdColumnName, connectionLines, geodesicGeometryMode? }`。
  matching 缺失/null 及显式 LEGACY_KNN 保持原语义；对象存在且 semantics 缺失/null 表示 EXACT_DISTANCE。
  任何显式 matching 对象（含旧版/非活动设置）低于 4.30 均以 `SPATIAL_NEAREST_MATCHING_REQUIRE_SCHEMA_VERSION` 拒绝。
- 真实距离策略：PLANAR 支持有效 XY 几何最近位置；GEODESIC 要求 WGS84 XY。缺失/null
  `geodesicGeometryMode` 按 POINT_ONLY 保持旧版，仅 Point；4.48 显式 GEOMETRY 支持 Point/MultiPoint/
  LineString/MultiLineString/Polygon/MultiPolygon，GeometryCollection 拒绝，通用 Geometry 在惰性执行时检查。
  来源/候选身份字段在读取计划内验证非空与唯一，分别返回 `SPATIAL_NEAREST_SOURCE_ID_INVALID` / `SPATIAL_NEAREST_CANDIDATE_ID_INVALID`；不在编译阶段扫描数据。
- 测地真实距离的候选使用 ECEF 三轴保守包围：XY 空间 Join 加 Z 区间不会排除真实范围内候选，
  但 ECEF 距离不作为结果。有半径时按该半径召回后用真实距离筛选；无半径时用二维 KNN 只取得真实 WGS84
  距离上界，再以三轴包围恢复上界内所有候选；二维种子不是最终候选判定。平面模式沿用来源 CRS 空间候选。
  测地距离、上下界和两端实际位置来自同一内部 Struct；半径跨越未决区间时安全失败，Top N 前缀只有在区间顺序
  可证明时才返回。Point 距离为确定数值；非点区间若不能证明排序或半径侧则安全失败。
  同位置不同来源保持独立；距离列为 DECIMAL(38,12)，排名使用未舍入距离。无半径有中间结果规模 Warning，不保证无限规模。
- 可选 `connectionLines: { enabled, outputTableName, geometryColumnName, maximumGeodesicSegmentLength, maximumGeodesicSegmentLengthUnit }`。
  启用后追加独立 BOUNDED 表，保留匹配投影/距离/排名，再增加 XY MultiLineString；未命中不生成线。
  平面端点是几何最近位置；测地端点直接复用距离 Struct 的同一位置对，再做 WGS84 椭球加密与日期线切分，
  不从原 Geometry 二次求解。段长为显式线性单位，单线最多 100 万顶点。
  两表派生于同一惰性匹配关系，不承诺不同 Output Action 只计算一次，不自动缓存或提供跨表事务。
- `SPATIAL_NEAREST_GEOMETRY_INVALID`、`SPATIAL_NEAREST_DISTANCE_INVALID` 及身份/类型错误为安全 SCHEMA 非重试错误；
  `SPATIAL_NEAREST_CONNECTION_VERTEX_LIMIT_EXCEEDED` 为 CONFIGURATION 非重试错误。摘要不包含坐标或实际数据值。
- 完整语义见 [最近要素设计](canvas-spatial-next-processors/spatial-nearest.md)。

## 7.13B `SPATIAL_SUMMARIZE_WITHIN` 节点

- 4.41 可选 `regions` 在 AREA_TABLE/PLANAR_GRID 之间选择。缺失/null 仍用原区域表；格网只要求一张被汇总输入表，生成真实方格/六边形区域，直接支持点、线、面，不使用质心近似。
  字段为 `mode`、`binShape`、`binSize`、`binSizeUnit`、`planarGrid`（原点/范围）、`binIdColumnName`、`binGeometryColumnName`。
  平面格网使用来源投影 CRS；方格边长、六边形对边距离。格网区域 ID 稳定，原区域表及字段/键设置作为非活动草稿保留。
  主结果输出格网 ID、Geometry 和统计，关联组表使用自动格网键；内部区域表不进入输出 Map。
  详细边界、空区域、范围限制和紧凑配置见[区域汇总设计第 11 节](canvas-spatial-next-processors/spatial-summarize-within.md#11-441-规则格网汇总区域)。

- 类别为 `PROCESSOR`，仅支持 `BATCH`，协议引入版本为 `4.12`。
- 按面区域汇总另一张点、线或面表，支持统计项、分组少数/多数、组百分比、固定/滑动时间切片和
  区域字段投影；旧模式输出一张扁平关系表，4.25 可选主表/关联组表。
- 4.24 每个统计项增加可选 `valueTreatment: ORIGINAL_VALUE|APPORTION_TOTAL|null` 与
  `weighting: NONE|INTERSECTION_FRACTION|null`。旧项缺失/null 时仍使用原值、不加权。
  分摊使用 p=交叠测度/整个来源要素测度，再对 p×x 做普通统计；原值加权均值为 Σ(p×x)/Σp。
  不支持分摊后再次加权或点要素形状加权。
  4.37 增加原值交叠比例加权 VARIANCE/STDDEV：μ=Σ(px)/Σp，V=Σ[p(x−μ)²]/[((n−1)/n)Σp]，SD=√V。
  n 为当前字段的有效正权重记录数；NULL/非有限值及非正权重同时排除，n<2 或算术溢出返回 NULL。
  使用有界可合并中心矩，不增加 Action；主表与组表分别聚合。公式来源、官方文字算例矛盾及服务验证边界见节点设计第 9 节。
- 4.24 `COUNT_FIELD` 统计非空字段数量，`ANY` 返回任意非空字符串，不保证顺序；旧 `COUNT` 继续计要素行数。
  新字段/枚举低于 4.24 使用 `SPATIAL_WITHIN_STATISTICS_REQUIRE_SCHEMA_VERSION` 拒绝。
  非法统计组合和不支持形状分别报 `SPATIAL_WITHIN_STATISTIC_COMBINATION_UNSUPPORTED`、
  `SPATIAL_WITHIN_SHAPE_WEIGHT_UNSUPPORTED`；错误定位到具体统计项。
- 4.25 `groupResult` 支持显式区域唯一键和关联组表配置。启用分组且 mode 不是 LEGACY_FLAT 时，主表每区域一行，组表每区域+组值一行；时间切片时均追加窗口粒度。
  主表总体统计直接计算，不能聚合组均值代替。组占比按区域内点数/相交长度/相交面积，组表不复制区域 Geometry。
  无匹配的空区域只保留主表，真实 NULL 组继续保留。分组未启用或 mode=LEGACY_FLAT 时保留但不执行关联表设置。
- 关联键由明确的区域标量字段提供，参与结果的空/重复键在惰性执行中报 `SPATIAL_WITHIN_AREA_KEY_INVALID`（SCHEMA，不重试，摘要不带键值）。
  新能力门槛为 `SPATIAL_WITHIN_GROUP_RESULT_REQUIRE_SCHEMA_VERSION`；少数/多数按正形状量比较，并列按组值升序、NULL 最后选择一个，是平台明确规则，未宣称与官方并列策略相同。
- 格网区域、日历切片及完整官方统计服务对照仍待补齐。时间切片与空区域保留属于平台组合/扩展，不能一概视为同名参数对齐。
- 完整语义见 [区域内汇总设计](canvas-spatial-next-processors/spatial-summarize-within.md)。

## 7.13C `SPATIAL_OVERLAY` 节点

- 类别为 `PROCESSOR`，仅支持 `BATCH`，协议引入版本为 `4.13`。
- 支持 `INTERSECTION/ERASE/UNION/IDENTITY/SYMMETRICAL_DIFFERENCE`，后两种从 4.26 开始支持。
  两侧 Geometry 要求 CRS 与坐标维度一致，使用显式字段投影解决同名属性；不要求两条物理入边。
- 4.26 可选 `geometryPolicy: FAMILY_2D | LEGACY_GEOMETRY | null`。旧三模式缺失/null 保持旧解释；新建默认 FAMILY_2D。
  新两模式缺失/null 按 FAMILY_2D 处理，显式 LEGACY_GEOMETRY 为可保存的无效草稿，编译时拒绝。
- FAMILY_2D 按官方点/线/面组合约束并输出二维多部件几何：相交取较低输入家族，其余取左家族。
  低于结果家族的接触部分不输出；保留多部件不拆行。NULL/空输入跳过，无效几何在惰性计划消费时返回安全错误，不自动修复。
- Identity 为成对相交加左独有部分；对称差为双方独有部分。差集先按来源要素聚合全部遮罩，不收集到 Driver。
  缺失侧属性可空，保留输入 Map，新增 BOUNDED 结果表且清除事件时间/Watermark。
- 交叠仍为要素配对语义，不去除同侧重叠，不宣称全局无重叠分区；精度/容差与真实官方服务边界对照仍未完成。
- 完整语义见 [空间叠加设计](canvas-spatial-next-processors/spatial-overlay.md)。

## 7.13D～7.13G Track 节点

- 4.35 重建/驻留汇总增加 `COUNT_FIELD`（非 Geometry 标量的非 NULL 计数）和 `ANY`。
  COUNT 仍为成员点数，不等同字段 Count。重建 Any 仅字符串，驻留 Any 允许字符串/数值，保留来源类型；
  全 NULL 返回 NULL，空字符串计数，不做去重或确定性采样。非法 Any 类型返回 `TRACK_SUMMARY_ANY_FIELD_NOT_SUPPORTED`。
  汇总输出 Schema 按 Spark 实际聚合类型回填，修正 INTEGER SUM/Decimal 提升不一致；数值算法和 FIRST/LAST 的首末 NULL 保留不变。
  驻留点级输出不执行隐藏汇总；隐藏新类型仍要求 4.35。统计弹窗取消不提交，失效值和无效草稿保留。
  详细映射与未决官方公式见两个节点文档，本次不修改 Manifest/Result/HTTP API。

- `TRACK_RECONSTRUCT`、`TRACK_MOTION_STATISTICS`、`TRACK_FIND_DWELL`、
  `TRACK_DETECT_INCIDENTS` 均为 `PROCESSOR`、仅支持 `BATCH`，协议引入版本依次为 `4.14`～`4.17`。
- 四者共享轨迹标识、时间字段、时间/距离片段边界与显式单位；不在 Driver 收集轨迹，也不在摘要中
  暴露轨迹 ID、坐标、条件字面量或统计结果。
- `TRACK_RECONSTRUCT` 的 4.27 可选 `reconstruction`：`semantics: ORDERED_SEGMENTS|LEGACY_POINTS|null`、
  `orderByColumns: string[]`、`splitBoundaryOption: GAP|FINISH_LAST|START_NEXT|null`、
  `splitExpression: { expression: string, bindings: {name:string, sourceColumnName:string, offset:integer|null}[], enabled?:boolean|null }|null`。
  对象缺失/null 或显式 LEGACY_POINTS 保持旧行为；新建默认有序片段，单点线片段跳过。非活动设置保留，任意对象要求 4.27。
- 有序重建中，时间+同时间字段决定点序及 FIRST/LAST；同轨迹重复次序在运行时以安全错误拒绝。
  NULL 时间/NULL 或空几何跳过。表达式为单个确定性 Spark SQL 布尔条件，不是 Arcade；NULL/false 不拆分。
  窗口绑定最多 32 项、偏移 -1000～1000；按轨迹和固定周期隔离，不自定义 SQL OVER 窗口。
- Gap 不跨接；FinishLast 将拆分后的首观测共享到前段；StartNext 将前段最后观测共享到后段。
  共享观测参与结果起止、点数和统计；固定周期始终 Gap。4.42 平面面轨迹和 4.44 测地面接入见下文；全球域及完整官方对照尚未补齐。
  完整配置、平台边界与 UI 见[轨迹重建设计](canvas-spatial-next-processors/track-reconstruct.md#7-canvas-427-有序片段表达式和连接段归属)。
- 4.28 可选 `reconstruction.pathGeometry`：`mode: METHOD_PATH|LEGACY_VERTEX_LINE|null`、
  `maximumGeodesicSegmentLength: number|null`、`maximumGeodesicSegmentLengthUnit: SpatialDistanceUnit|null`。
  对象缺失/null 保持 LineString；对象内 mode 缺失/null 等同 METHOD_PATH，仅有序重建时生效。任何非空对象（包括非活动设置）要求 4.28。
  METHOD_PATH + GEODESIC 使用 WGS84 XY 椭球最短路径、等距离加密及日期线切分，结果为 MultiLineString；
  最大段长必须为有限正数和明确线性单位。METHOD_PATH + PLANAR 包装为 MultiLineString，保留顶点/维度，不使用测地加密参数。
  新建初值为 METHOD_PATH、10 KILOMETERS（平台推荐，不是官方默认）；旧定义不自动补策略。
  插值只改变 Geometry，不增加观测数量、时间或统计样本；每个结果片段最多一百万顶点，超限安全失败，不静默截断。
  `INVALID_TRACK_GEODESIC_SEGMENT_LENGTH` 和 `TRACK_GEODESIC_VERTEX_LIMIT_EXCEEDED` 为不可重试配置错误；
  `TRACK_GEODESIC_COORDINATE_INVALID` 为不可重试 Schema 错误；摘要不回显坐标、表达式或数据值。
- 4.42 可选 `reconstruction.areaGeometry`：`enabled?:boolean|null`、`bufferMode:NONE|FIELD|EXPRESSION|null`、
  `bufferField:string|null`、`bufferExpression:string|null`、`bufferUnit:SpatialDistanceUnit|null`。
  缺失/null 或 enabled=false 保持线轨迹；非空对象 enabled 缺失/null 为启用，仅有序策略生效，其他分支草稿保留。
  4.42 分支支持 PLANAR、XY、Point/Polygon/MultiPolygon；Point 必须指定字段或受控逐行数值表达式，Polygon 可不缓冲。
  每观测构造缓冲后，按原有确定次序对相邻面取凸包并合并，输出 MultiPolygon；不是全轨迹凸包或仅 Union 离散观测。
  面片段保留单个观测。原几何决定拆分距离，共享观测的缓冲值与统计不变；不增添观测或 Spark Action。
  点距离需有限正数，面缓冲需有限非负数；不接受 NULL，数值错误在消费数据时返回 `TRACK_BUFFER_DISTANCE_INVALID`。
  `TRACK_AREA_GEOMETRY_INVALID`、`TRACK_AREA_VERTEX_LIMIT_EXCEEDED` 同为不可重试 Schema 错误，摘要不包含值或表达式。
  不以平面近似悄悄替代测地方法；测地面须显式配置下方 4.44 边界采样，缺失时报必填问题。表达式不兼容 Arcade，受控窗口见 4.43。
  完整约束、精度和官方待验收差异见[平面面轨迹](canvas-spatial-next-processors/track-reconstruct.md#10-canvas-442-显式平面面轨迹)。
- 4.43 `areaGeometry.windowBindings?: {name:string,sourceColumnName:string,startOffset:integer|null,endOffset:integer|null,statistic:TrackSummaryStatisticKind|null}[]|null`。
  缺失/null 为空，空数组不要求新版本；任意非空数组（含非活动分支）要求 4.43。仅有序面轨迹的 EXPRESSION 模式执行。
  最多 32 项、名称唯一且不与输入字段/平台内部前缀冲突；全部读取原始数值字段，不引用其他绑定。
  偏移范围 -1000～1000，两端包含且起点不大于终点；按轨迹、时间及同时间次序排序，在固定周期内构造 ROWS 窗口。
  统计支持 COUNT_FIELD、SUM、MEAN、MIN、MAX、RANGE、STDDEV、VARIANCE、FIRST、LAST。计数为空为 0；其他空窗口为 NULL；
  首末值不跳过 NULL，方差/标准差为样本公式。缓冲表达式可用 `coalesce(history_mean,radius)` 显式补足历史。
  在普通 gap/表达式拆分及端点共享前计算一次；与拆分绑定作用域隔离，不向输出 Map/Schema 泄漏窗口列。
  禁止在表达式里自定义 OVER/聚合/子查询/展开，不额外触发 Action。配置问题为 `TRACK_BUFFER_WINDOW_COUNT_EXCEEDED`、
  `INVALID_TRACK_BUFFER_WINDOW`、`TRACK_BUFFER_WINDOW_NUMERIC_REQUIRED` 或既有字段错误，路径精确到窗口数组项。
  [窗口参数与 UI](canvas-spatial-next-processors/track-reconstruct.md#11-canvas-443-缓冲观测窗口绑定)说明官方能力映射及平台边界。
- 4.44 `areaGeometry.geodesicBoundary?: {maximumSegmentLength:number|null,maximumSegmentLengthUnit:SpatialDistanceUnit|null}|null`。
  缺失/null 不自动补值；非空对象含非活动草稿要求 4.44。仅有序面轨迹 + GEODESIC 执行，其他模式保留但不校验业务值。
  最大段长须有限正数及线性单位，控制边界离散粒度而非位置误差，不读取 `pathGeometry` 的隐藏段长。
  来源与实际 Geometry 必须是 EPSG:4326 XY Point/Polygon/MultiPolygon，原始测地面拓扑不使用经纬度平面 IsValid 替代。
  原始 Geometry 的距离阈值谓词决定 gap，跨阈值继续求精而非比较粗距离或质心；足迹与距离求解分开。
  逐观测足迹以私有 Struct 保留真实顶点和渲染区域，穿过窗口、排序与共享端点，最后仅连接相邻观测。
  局部域、百万顶点/计算预算和未决拓扑仍按安全错误拒绝；不回退平面、不返回未验证结果，全球域与官方服务对照仍未完成。
  [测地面完整链路接入](canvas-spatial-next-processors/track-reconstruct.md#17-canvas-444-测地面链路接入)记录参数、面板和剩余边界。
- `TRACK_FIND_DWELL` 首版使用连续相邻点阈值分段，不宣称完整等价 ArcGIS 的候选驻留范围算法。
  4.22 新增 `dwellSemantics: LEGACY_ADJACENT|REFERENCE_CENTER|null`；缺失/null 仍为旧版，新建节点默认参考中心。
  `rangeOptions` 为可选对象：`resultMode: MEAN_CENTERS|CONVEX_HULLS|DWELL_FEATURES|ALL_FEATURES|null`、
  `orderByColumns: string[]`、`durationUnit: SpatialDurationUnit|null`、
  `meanDistanceColumnName: string`、`meanDistanceUnit: SpatialDistanceUnit|null`、`dwellFlagColumnName: string`。
  新策略以首点范围形成满足时长的连续候选，固定候选均值中心向前后扩展，不复用观测；输出模式只验证生效字段。
  均值中心/凸包每驻留一行；驻留点/全部点保留原字段并追加 ID 与标记，非驻留 ID 为 NULL。
  点级输出不应用汇总项、聚合字段和单位，但保留其草稿；旧 `outputGeometryKind` 在新策略下不生效。
  四输出均产生有界新表并保留入口 Map，不添加 Watermark。
  非空新字段低于 4.22 拒绝并返回 `TRACK_DWELL_RANGE_REQUIRE_SCHEMA_VERSION`。
  运行点非法、均值中心无法确定分别为 `TRACK_DWELL_POINT_INVALID`、`TRACK_DWELL_CENTER_UNDEFINED`（SCHEMA 类别），摘要不含坐标。
  完整算法选择、空间适配与单轨迹内存边界见[驻留设计](canvas-spatial-next-processors/track-find-dwell.md#7-422-参考中心策略与实现边界)；不宣称官方算法完全等价。
- `TRACK_DETECT_INCIDENTS` 复用受控 Filter 条件树。新生命周期策略在未配置距离边界时支持无 Geometry
  的表或携带点/线/面的观测；距离边界仍需要 Point 和距离方法。
- `TRACK_MOTION_STATISTICS.historyPoints` 在旧策略中仍是 lag 偏移量；旧 IDLE 仅有距离阈值。
  4.23 新增可选 `motionSemantics: LEGACY_LAG|OBSERVATION_WINDOW|null`，缺失/null 不改旧结果。
  新策略由 `windowOptions` 独立配置观测窗口、顺序、八组 31 项输出、输出单位、输入高程来源/单位及 Idle 时间阈值；
  旧 `historyPoints/metrics` 保留但不参与新策略。Idle 距离仍由原 `idleDistanceThreshold/Unit` 指定。
  当前新建默认 3 个观测、测地线、距离与速度组；默认组是平台推荐，不是官方默认全部组。
  结构可解析的未完成草稿可保存；指标组必须完整、指标类型/UUID/输出字段唯一，普通语义错误由 Operator 定位。
  窗口内只统计完整包含的运动段，瞬时指标不受窗口长度影响；平均速度按有效段总距离/总时长，
  坡度是高差/水平距离的比值，Idle 距离与时间使用严格 < / >。
  不足历史使用已有观测，NULL 不重排、不跨缺失 Geometry 连段；零时长速度/加速度/Idle 为 NULL。
  `TRACK_MOTION_WINDOW_REQUIRE_SCHEMA_VERSION` 拒绝低于 4.23 的新配置；
  `TRACK_MOTION_POINT_INVALID` 以安全 SCHEMA 错误报告非法坐标，不包含点值。
  完整契约、缺失值和窗口边界规则见[运动统计设计](canvas-spatial-next-processors/track-motion-statistics.md#7-423-观测历史窗口策略)。
- 事件检测 `incidentSemantics` 缺失或 `null` 时为 `LEGACY`，旧定义的持续激活、包含结束行及整段时长语义不变。
  新节点默认 `CONDITION_LIFECYCLE`：无结束条件时 start 非 true 即结束；显式 end=true 优先结束；
  激活期间重复 start 不另开事件。`incidentStatusColumnName` 指定 Started/OnGoing/Ended 字段；
  Ended 不是事件成员，但在 ALL_EVENTS 中带事件 ID 和逐观测持续时间；轨迹尾没有关闭观测时结束时间为空。
  INCIDENTS_ONLY 仅保留成员，不虚构 Ended 行。
- 生命周期 `orderByColumns` 默认为空数组；按时间和这些字段升序排列，同时间仍不唯一时运行返回
  `TRACK_OBSERVATION_ORDER_NOT_UNIQUE`。无时间观测被排除；编译只构造惰性校验计划，不真实读取。
- 4.46 可选 `conditionWindows: TrackIncidentWindow[]`，缺失/null 规范化为空数组。
  每项为 `{ bindingName: string, sourceColumnName: string, kind: COUNT|SUM|MEAN|MIN|MAX|FIRST|LAST|STDDEV_POP|VARIANCE_POP|null,
  startOffset: integer|null, endOffset: integer|null, source?: FIELD|TRACK_DISTANCE|TRACK_SPEED|TRACK_ACCELERATION|null }`。仅 CONDITION_LIFECYCLE 计算，LEGACY 保留但不执行。
  任意非空数组（含非活动草稿）低于 4.46 使用 `TRACK_INCIDENT_WINDOWS_REQUIRE_SCHEMA_VERSION` 拒绝；节点引入版本不变。
  指标按轨迹片段及确定次序，从入口原字段一次性生成，不相互依赖。偏移采用左闭右开 `[startOffset,endOffset)`，
  0 为当前、负数为过去、正数为未来；`[-5,0)` 只取前 5 条，边缘裁剪到实际观测。
  指标名称为字母或下划线开头的 1～128 位 ASCII 标识符，不得与任何原字段或其他指标大小写不敏感重名。
  偏移为有界 32 位整数（起点不能为 MIN_VALUE），起点小于终点；错误为 `TRACK_INCIDENT_WINDOW_NAME_INVALID`、
  `TRACK_INCIDENT_WINDOW_RANGE_INVALID`，重名/失效字段复用现有错误码。NULL 函数/偏移和空名可保存草稿，编译时校验。
  COUNT 为字段非空计数，FIRST/LAST 保留首末 NULL；其余聚合忽略 NULL。空窗 COUNT=0，其余 NULL；方差/标准差为总体统计。
  类型由 Spark Analyzer 决定，不在前端传播推测 Schema；Inspector 中指标候选仅供节点内条件值编辑。
  指标不输出、不形成独立来源资产；原字段和有界新表语义不变。详见[事件窗口设计](canvas-spatial-next-processors/track-detect-incidents.md#8-446-受控字段窗口条件)。
- 4.63 的 `source=TRACK_DISTANCE` 复用同一组聚合函数。它对 `[startOffset,endOffset)` 中实际存在的
  逐观测累计轨迹距离求值。当前片段首观测累计值为 0；官方 `[-1,2)` 示例的 `[0,60,140]` 表示三个
  观测的累计距离，不是两段长度。边缘自然裁剪，且不跨轨迹或已拆分片段。距离固定使用 EPSG:4326 XY
  Point 的测地线米，不读取 `distanceMethod`，也不在摘要中记录坐标。NULL Point 使该观测及本片段后续
  无法证明完整累计值的观测返回 NULL。缺失/null/FIELD 继续聚合 `sourceColumnName`，任意非活动
  TRACK_DISTANCE 草稿同样要求 4.63。
- 4.64 的 `source=TRACK_SPEED` 也复用同一组聚合函数，并对相同的逐观测范围求值。片段首观测速度按
  官方示例为 0；后续值为上一观测到当前观测的 WGS84 测地距离除以时间差，固定使用米/秒。同时间、
  NULL Point 或前一点缺失时返回 NULL，不除以 0、不跨轨迹或片段，也不读取 `distanceMethod`。任意非活动
  TRACK_SPEED 草稿同样要求 4.64。
- 4.65 的 `source=TRACK_ACCELERATION` 继续复用同一组聚合函数。片段首观测加速度按官方示例为 0；
  后续值为当前速度与前一观测速度之差除以时间差，固定使用米/秒²。同时间、当前或前一速度为 NULL 时
  返回 NULL，不跨轨迹或片段。任意非活动 TRACK_ACCELERATION 草稿同样要求 4.65。
- 4.66 可选 `conditionScalars: TrackIncidentScalar[]`，缺失/null 规范化为空数组。4.66 每项为
  `{ bindingName: string, source: TRACK_START_TIME|TRACK_DURATION|TRACK_CURRENT_TIME|TRACK_INDEX|null, offset?: integer|null }`。
  四项分别对应官方 TrackStartTime、TrackDuration、TrackCurrentTime 和 TrackIndex；开始/当前时间是
  Unix Epoch 毫秒，时长是片段起点至当前观测的毫秒数，序号从 0 开始。它们按当前轨迹和既有固定边界/gap
  片段重置，统一作为 LONG 临时字段，仅供条件引用，不进入输出。名称与原字段、窗口指标和其他标量
  大小写不敏感唯一；非空数组（包括 LEGACY 非活动草稿）要求 4.66。
- 4.67 在同一数组增加 `TRACK_POINT_X_AT|TRACK_POINT_Y_AT`。两种来源必须配置
  `offset`；0 为当前、负数为过去、正数为未来观测，超出当前 DataScalpel 轨迹片段或
  Geometry 为 NULL 时返回 NULL。必须选择带完整元数据的 Point，坐标为可空 DOUBLE，
  数值和单位跟随来源 CRS，不限 WGS84。非坐标来源忽略但保留草稿 `offset`。
  任意坐标来源（包括 LEGACY 非活动草稿）要求 4.67。
- Distance/Speed/Acceleration 的 Current 等价于对应来源的 `FIRST + [0,1)`，At(n) 等价于
  `FIRST + [n,n+1)`；Inspector 帮助直接说明该配置，不为等价表达新增协议类型。
- 四类轨迹节点的 `boundaries.fixedTimeBoundary` 是可选对象：`interval: integer|null`、
  `unit: MILLISECONDS|SECONDS|MINUTES|HOURS|DAYS|WEEKS|MONTHS|YEARS|null`、
  `referenceTime: string|null`、`timeZone: string|null`。缺失/null 不启用，未完成对象允许保存草稿。
  启用后 Compiler 要求正整数周期和单位，参考时刻默认 Epoch、时区默认 UTC；边界为左闭右开。
  小时及更小单位是实际时长，日/周/月/年为日历周期，月末与闰年始终从原参考时刻推进，不能按 30/365 天替代。
  无偏移量参考时间按 IANA 时区解释；夏令时不存在或歧义本地时间拒绝，须填写明确偏移量。
  固定边界与相邻 gap 独立、任一满足即拆分，无时间观测排除；不自动补充空片段。
- 上述可选能力要求 4.21；旧小版本不能携带非空新能力，未启用时保持旧语义。
  Manifest、Result 与 HTTP API 不升级。4.46 已接入受控原始字段窗口，4.63～4.65 已接入累计距离、
  逐观测速度和逐观测加速度窗口，4.66 已接入四种轨迹时间/序号标量，4.67 已接入 Point 相对观测 X/Y 标量。
  剩余差距是返回复合对象的 Geometry/TrackWindow、整行对象、更丰富的受控表达式，以及真实 ArcGIS 服务对照；不能把这些能力解释为已经
  支持任意 Arcade。
- 详细配置分别见[轨迹重建](canvas-spatial-next-processors/track-reconstruct.md)、
  [运动统计](canvas-spatial-next-processors/track-motion-statistics.md)、
  [查找驻留](canvas-spatial-next-processors/track-find-dwell.md)和
  [检测事件](canvas-spatial-next-processors/track-detect-incidents.md)。

### 4.39 Bins / Within 共享日历时间切片

- `temporalSlicing.calendar` 为可选 `{ mode: FIXED_DURATION|CALENDAR|null, intervalUnit, repeatIntervalUnit }`；
  日历单位为 MILLISECONDS/SECONDS/MINUTES/HOURS/DAYS/WEEKS/MONTHS/YEARS，两个单位均允许 null 草稿。
- 缺失/null/FIXED_DURATION 保持旧固定窗口（DAYS=24h）。CALENDAR 使用嵌套单位，日周/月年按时区推进，小时及更小单位按实际时长。
  根单位不执行但保留，根窗宽/重复整数共用；切换语义须确认且不转换数值。
- 同族起止从原参考时刻计算，避免月末漂移；跨族先起点再加窗宽。无参考时使用 Epoch 对应本地时刻。
  无偏移参考时间必须唯一存在，微秒以下精度拒绝。窗口左闭右开，可重叠/留空，NULL 不参与且不虚构无观测时间窗。
- 每条观测最多检查 4096 个候选窗口，超限不截断；惰性 UDF 只展开一次、不触发额外 Action。
  `SPATIAL_CALENDAR_WINDOW_LIMIT_EXCEEDED` 为不可重试 CONFIGURATION；`SPATIAL_CALENDAR_WINDOW_RANGE_INVALID` 为不可重试 SCHEMA。
- Inspector 时间弹窗使用独立草稿，取消不带入下一次打开；不完整模式/单位允许保存，由 Compiler 返回明确错误。
  详细配置、DST/混合单位裁决和 UI 见[空间日历窗口](canvas-spatial-calendar-windows.md)。Manifest/Result/API 不升级。

## 7.13H `SPATIAL_BIN_AGGREGATE` 节点

- 类别为 `PROCESSOR`，仅支持 `BATCH`，协议引入版本为 `4.18`。
- 将投影 CRS 的 XY Point 分配到方格或六边形，支持数值统计、分组和时间切片。空格网按已占用
  索引的矩形包络补齐；没有来源点时不生成无法推导范围的格网。
- `binSizeSemantics` 缺失/null/LEGACY_SIDE_LENGTH 时六边形 `binSize` 保持旧边长；
  4.21 新增 HEXAGON_FLAT_TO_FLAT，以对边距离配置，内部边长=d/√3。方格始终按边长。
  新节点默认新策略，旧节点不自动转换；界面切换需确认，已占用与补齐空格网统一换算。
  显式平面原点与业务范围见 4.38，不因尺寸修正就宣称完整对齐。
- 4.33 新增 `binShape=H3` 及可选 `h3: { mode: RESOLUTION|APPROXIMATE_SIZE|null, resolution: integer|null }`。
  分辨率模式要求 0～15；近似模式读取根大小/线性单位，按 √3×H3 平均边长的绝对差选级别（平台约定，非已验证 Esri 公式）。
  非生效参数保留不执行；H3 形状或非 null h3 要求 4.33，否则 `SPATIAL_H3_REQUIRE_SCHEMA_VERSION`。
  来源必须 EPSG:4326 XY Point；输出有点 Cell ID + MultiPolygon，跨日期线及极区切分。
  NULL/Empty 排除，真实非法坐标惰性失败；H3 不支持空格网补齐，保留草稿并报 `SPATIAL_H3_EMPTY_BINS_UNSUPPORTED`。
  具体稳定错误、近似精度及 Inspector 交互见节点第 8 节；Manifest/Result/HTTP API 不变。
- 4.34 增加字段非空 `COUNT_FIELD` 和字符串样本 `ANY`。COUNT 仍为点数且不使用来源字段。
  COUNT_FIELD 不去重、空字符串计数、NULL 不计数；ANY 忽略 NULL，空格网/全 NULL 为 NULL，不保证跨重跑相同样本。
  聚合输出按 Spark 实际类型回填 Schema，修正整数 SUM / Decimal 聚合的类型提升，实际数值算法不变。
- Bins / Within 固定时长切片支持重复间隔大于窗宽：以参考时刻对齐重复周期，保留每周期前一段窗宽，
  空隙/NULL 时间排除。起止字段由一次窗口展开同时生成，修复原先重叠窗口重复展开产生的错误组合/重复统计。
  当前天仍为固定 24 小时；月/年和日历 DST 不是此修复的能力。详见节点第 9 节。
- 格网计算的来源、统计和分组指示器使用内部别名隔离，最终投影才应用配置名称；业务字段不得覆盖内部索引/时间列。
  重叠窗口 Expand 逐输出位置追踪各投影的字段来源；不将未知分支伪装为完整血缘。
- 4.38 可选 `planarGrid: { originX, originY, extent: { mode: DATA_BOUNDS|EXPLICIT_BOUNDS|null, minX, minY, maxX, maxY }|null }`。
  坐标均可为 null 草稿；实际平面执行要求有限原点及有效活动范围。缺失/null 保留旧 ID、原点与数据范围。
  SQUARE 原点为 (0,0) 单元左下角，HEXAGON 为中心；方向不旋转，坐标用来源投影 CRS 单位，与大小显示单位独立。
  新 ID 包含形状、EPSG、实际大小和原点的身份哈希；范围、任务和节点不参与身份，跨任务相同配置保持对齐。
  显式范围先按左闭右开规则筛点，再聚合完整单元；补空保留正面积交叠单元及被选中边界点的原分配单元，不裁剪 Geometry。
  显式范围无点时可补空间格网；时间切片仍只使用实际窗口，不凭空补时间。H3 保留但不执行 planarGrid。
  新对齐模式补空最多 100 万候选空间格网：显式范围编译检查、数据范围惰性运行检查，超限返回 `SPATIAL_GRID_CELL_LIMIT_EXCEEDED`。
  原点/范围无效为 `INVALID_SPATIAL_GRID_ORIGIN` / `INVALID_SPATIAL_GRID_EXTENT`；实际点索引非有限或超出可靠精度为 `SPATIAL_GRID_POINT_INVALID`。
  范围设置、恢复旧版确认、数值与安全规则见节点第 10 节。日历及球面范围仍待完成，不宣称 ArcGIS 有同名原点参数。
- 完整语义见 [空间格网聚合设计](canvas-spatial-next-processors/spatial-bin-aggregate.md)。

## 7.13I `SPATIAL_POINT_CLUSTER` 节点

- 类别为 `PROCESSOR`，仅支持 `BATCH`，协议引入版本为 `4.19`。
- 首版使用 Sedona 的分布式 DBSCAN，保留输入行并追加 cluster ID 和噪声标记。
- DBSCAN 依赖 Spark RDD Checkpoint；Local 模式自动使用应用隔离的临时目录，Cluster 模式要求
  `spark.checkpoint.dir` 指向执行器共享文件系统，缺失时返回
  `SPATIAL_CLUSTER_CHECKPOINT_NOT_CONFIGURED`。
- HDBSCAN 从 4.45 通过显式诊断配置接入；Multi-scale 仍为不可执行的历史占位，Compiler 返回
  `SPATIAL_CLUSTER_ALGORITHM_NOT_AVAILABLE`，不得静默降级。
- ArcGIS GeoAnalytics Server 对照范围只有 DBSCAN/HDBSCAN；Multi-scale 是当前开发期占位，
  不属于该工具的对齐目标。4.40 增加 Linear 时空 DBSCAN；4.45 增加 HDBSCAN 及四类诊断，规模和官方数值对照仍未完成。
- 4.40 可选 `dbscan: { mode: LEGACY_SPATIAL|SPATIAL|LINEAR|null, timeColumnName: string, searchDuration: long|null, searchDurationUnit: SpatialDurationUnit|null }`。
  缺失/null/LEGACY_SPATIAL 保留原算法；SPATIAL/LINEAR 只连核心点再归属边界，不用时间桶近似。
  Linear 需要 TIMESTAMP、正整数固定时长，空间与时间阈值同时满足（含等号）；支持毫秒至日，日为 24h。
  新建默认 SPATIAL，隐藏时间草稿保留；显式切换确认，空模式/非法时长可保存，由 Compiler 标错。
- 参与点 ID 非空且唯一，相同位置不合并；新模式跳过 NULL/Empty Geometry 和 Linear 的 NULL 时间，非法实际点安全失败。
  噪声保留 NULL 簇号/true，簇号只在本次结果有效。全邻域图分布式执行但最坏 O(n²)，需要 Checkpoint 和实际资源。
- 修复预检直接调用 Sedona 提交作业的问题：Compiler 只构造零行 Schema/集合血缘计划，不设置 Checkpoint 或执行连通分量。
  Runner 仍使用同一 Operator 的真实算法阶段，实际身份/邻域/结果 Checkpoint 后释放 GraphFrames 持久结果；无 Driver 全表收集。
  `SPATIAL_CLUSTER_POINT_INVALID` / `SPATIAL_CLUSTER_FEATURE_ID_INVALID` 为不可重试 SCHEMA；误执行预检表达式为不可重试 CONFIGURATION `SPATIAL_CLUSTER_PREVIEW_NOT_EXECUTABLE`。
- 完整语义见 [空间点聚类设计](canvas-spatial-next-processors/spatial-point-cluster.md)。

### 4.45 HDBSCAN 诊断配置

`SpatialPointClusterConfiguration` 增加可选 `hdbscan` 对象：

```ts
interface SpatialHdbscanOptions {
  probabilityColumnName: string;
  outlierColumnName: string;
  exemplarColumnName: string;
  stabilityColumnName: string;
}
```

- 缺失/null 不补默认值。任何非 null 对象含 DBSCAN 下的非活动草稿均要求 4.45；Java 保留原八/九参数构造器。
- 保存端要求对象中的四个字段为字符串（空字符串可保存，null 属于结构错误）；前端导入把缺失/null 字段名规范化为空字符串。
- 仅 `parameters.algorithm=HDBSCAN` 执行，缺对象返回 `SPATIAL_HDBSCAN_DIAGNOSTICS_REQUIRED`。
  四名必填，按大小写不敏感与原列、簇/噪声及其他诊断列判重，问题路径为 `configuration.hdbscan.<field>`。
  非 HDBSCAN 分支保留对象但不校验其中业务值，不改变旧 DBSCAN 输出 Schema 或参数语义。
- 参数仍只有 minimumFeatures（2～100000，包含自身）；不使用隐藏的 DBSCAN 半径、时间字段和时间邻域。
  跳过 NULL/Empty Geometry，实际非 Point/非法坐标失败；参与 ID 非空且唯一，重复位置不合并。
- 保留每个有效原行并按配置追加簇 ID、噪声、概率 DOUBLE、GLOSH DOUBLE、代表点 BOOLEAN、持久性 DOUBLE。
  噪声 clusterId=null、noise=true、probability=0、exemplar=false、stability=null；不足最少要素时 outlier=0。
  原入口 Map 保留，结果为 BOUNDED 新表，原事件时间字段按既有点聚类规则保留、Watermark 清空。
- 使用真实互达距离 MST、分布式压缩层次及 EOM；仅标量迭代状态返回 Driver。
  当前完整点对最坏 O(n²)，层次轮数最坏随树深增长；不承诺大规模或与 ArcGIS STABILITY 数值等价。
- Compiler 只构造零行/集合依赖计划，所有六个追加字段均依赖参与 Geometry/身份集合；
  不执行隐藏时间配置、不建 Checkpoint、不启动聚类作业。真实数据校验与原行回接在 Runner。
- HDBSCAN 运行时使用显式 Checkpoint 作用域：最终结果独立物化后保留供下游读取，
  其余自有快照和成功图阶段已确认归属的中间目录在成功/失败退出时最佳努力清理，不扫描共享目录或删除上游。
  未成功返回的库阶段目录、强杀残留、运行磁盘峰值及最终结果应用级回收仍有边界；详见[聚类生命周期](canvas-spatial-next-processors/spatial-point-cluster.md#15-hdbscan-中间-checkpoint-的显式归属与清理)。
- Inspector 的四字段设置采用独立 680px Modal，取消不改草稿，普通字段错误仍可保存。
  切换算法需确认，诊断对象和时间对象跨切换保留；算法 parameters 的非活动分支只在本面板会话保留。
  Canvas 仅显示诊断项数；Runner 摘要仅增加 diagnosticCount，不记录诊断数值或成员数据。
- 新读写端协调支持 Canvas 4.45；Manifest、Result、HTTP API 与节点引入版本 4.19 不变。

## 7.13J `SPATIAL_CENTER_DISPERSION` 节点

- 类别为 `PROCESSOR`，仅支持 `BATCH`，协议引入版本为 `4.20`。
- 缺失/null/LEGACY_WIDE 的 `resultMode` 保留投影 CRS XY Point、每组一行多 Geometry 宽表及固定轮数中位中心旧算法。
- 4.31 `resultMode: ANALYSIS_TABLES`：每项通过可选 `analyses[i].outputTableName` 指定独立结果表，按分析数组顺序追加到入口 Map。
  未使用的节点级宽表名和逐项表名保留；任意非空 resultMode 或非 null 逐项表名低于 4.31 以 `SPATIAL_CENTER_RESULTS_REQUIRE_SCHEMA_VERSION` 拒绝。
- 新模式支持投影 CRS XY Point/Line/Polygon 及 Multi，通用 Geometry 在执行时检查，混合集合拒绝。线面以质心参与位置分析，中央要素返回原 Geometry 与原类型 ID。
  中央要素 ID 在有效要素中非空且全局唯一；平局按原类型 ID 顺序，不按数字转字符串后的顺序。
- 新模式中位中心使用修正 Weiszfeld 和凸目标残余次梯度停止证据，上限 10000 次；未收敛以 `SPATIAL_CENTER_MEDIAN_NOT_CONVERGED` 失败，不把迭代轮数当作精度保证。
  坐标和权重内部缩放，目标差距容差为 `1e-10 × 归一化总权重 × max(1, 坐标包络半幅)` 对应的原坐标目标量级；不是位置误差或官方容差承诺。
- 有效要素排除 NULL/Empty Geometry、NULL 权重；零权重保留为中央候选但不贡献统计，全零组/空输入不输出行。
  正权重支持点决定内部坐标范围；零权重不参与统计缩放、中位停止半径或离散矩，避免远端零权重要素引入舍入偏差。
  权重负值/非有限值、非法 Geometry、不可表示数值均安全失败。退化圆/椭圆输出 Empty Polygon，不添加虚假最小半径。
- 分组在 Executor 中计算，收集前限制 100000 要素/100 万顶点；若包含中央要素，限制为 5000 要素/100 万顶点。
  不向 Driver collect，不引入缓存/额外 Action；不同下游 Action 可能重算同一惰性分析关系。超限为 `SPATIAL_CENTER_GROUP_LIMIT_EXCEEDED` CONFIGURATION 非重试错误。
- 4.32 中央要素可选 `analyses[i].centralFeatureColumns: { sourceColumnName, outputColumnName, included }[]`：
  显式数组替代自动输出分组/ID，按顺序投影选中原记录的字段，结果 Geometry 仍以 outputColumnName 追加。
  `[]` 表示仅输出结果 Geometry；缺失/null 保留 4.31 分组+ID+Geometry。分组/ID 可以显式排除或改名。
  仅 ANALYSIS_TABLES + CENTRAL_FEATURE 生效；其他分析和旧宽表保留配置但不执行。
  任何非 null 数组（包括空和非活动数组）低于 4.32 均返回 `SPATIAL_CENTER_PROJECTION_REQUIRE_SCHEMA_VERSION`。
  启用字段必须存在，来源和输出分别唯一，输出不得与结果 Geometry 重名（大小写不敏感）。排除项的失效字段和名称保留但不参与执行校验。
  属性按有效且唯一的原类型 ID 关联回同一条有效记录，不分别 FIRST/MAX 聚合属性，不将整条属性记录 collect_list。
  显式投影保留来源 eventTimeColumn 时同步输出别名，否则清除；结果 BOUNDED，始终无 Watermark。这不是新增 interval 时间模型。
- 标准距离为平台扩展，不是 GA 同名工具的第五种分析。平均/中位/椭圆时间输出、完整 interval 元数据、官方椭圆加权公式对照与真实规模验收仍未完成；当前不冒称完整 GA 对齐。
- 完整语义见 [中心与离散统计设计](canvas-spatial-next-processors/spatial-center-dispersion.md)。

## 7.13K `SPATIAL_DENSITY` 节点

- 类别为 `PROCESSOR`，仅支持 `BATCH`，协议引入版本为 `4.68`；至少一条入边，允许没有出边。
- 输入必须是带完整 CRS 元数据的投影 XY Point。NULL/Empty Point 不参与；节点始终生成新
  `BOUNDED` 逻辑表并按配置名追加到入口表 Map，来源表及其他表保持不变。
- `weighting` 为 `UNIFORM` 或 `KERNEL`。Uniform 对半径内每个点使用
  `quantity / (πr²)`；Kernel 使用四次核
  `3 / (πr²) × (1 - d²/r²)² × quantity`，只计算格网中心到点的距离不超过半径的贡献。
  点数密度始终输出；`fields` 最多 32 个数值数量字段，NULL 数值贡献 0，非有限实际数值安全失败。
- `binShape` 为 `SQUARE/HEXAGON`；方格大小是边长，六边形大小是对边距离。格网固定对齐
  `(0, 0)`，只输出有贡献的格网，不生成空格网或 H3。搜索半径换算后必须严格大于格网大小，
  半径/大小最多为 512。
- `binSize/radius` 使用独立线性单位并可靠换算到来源 CRS 轴单位；`areaUnit` 只换算密度结果。
  地理 CRS、不可解析或非线性轴单位在编译期拒绝。
- 可选 `temporalSlicing` 复用空间汇总的左闭右开固定/日历窗口；NULL 时间或窗口间隙中的点不参与。
  输出顺序固定为格网 ID、Polygon Geometry、可选窗口起止、点数密度和 `fields` 定义顺序。
- Compiler 只构造零行/惰性 Spark 计划，不读取真实点数据。Runner 摘要只记录表名、方法、形状、
  数量字段数和是否切片，不记录半径、数量值或时间内容。
- 当前公式是 DataScalpel 的确定性平台实现；尚未与真实 ArcGIS Enterprise 作业做逐格网数值、
  边缘和规模对照，不宣称与 Esri 内部 Kernel 实现完全数值等价。完整设计见
  [Calculate Density](canvas-spatial-next-processors/spatial-density.md)。

## 7.13L `SPATIAL_HOT_SPOTS` 节点

- 类别为 `PROCESSOR`，仅支持 `BATCH`，协议引入版本为 `4.69`；至少一条入边，允许没有出边。
- 输入必须是带完整 CRS 元数据的投影 XY Point。NULL/Empty Point 不参与；节点始终生成新的
  `BOUNDED` 逻辑表并追加到入口表 Map，来源表及其他表保持不变。
- 默认 `analysisSource=POINT_COUNT`，使用每格点数计算 Gi*；`FIELD_SUM` 是显式平台扩展，先按格网
  求和一个数值字段，NULL 贡献 0，实际非有限值安全失败。两种模式均输出点数和最终分析值。
- `binSize` 是固定原点 `(0, 0)` 方格的边长。分析范围为有效点外包矩形覆盖的完整方格，包含零值格；
  总格数连同所有时间片最多 1,000,000。
- `neighborhoodDistance` 以方格中心距离定义二元权重并包含当前格；换算后必须严格大于方格边长，
  与边长之比最多为 64。两种距离单位分别换算到来源投影 CRS 第一轴单位；地理 CRS 不支持。
- 每个时间片独立计算 Getis-Ord Gi*。原始双侧 p-value 始终输出；`NONE` 直接分级，`FDR_BH`
  使用片内 Benjamini-Hochberg 调整值分级。置信分级为 `-3..3`，符号表示冷/热点，绝对值表示
  90%、95%、99% 三档显著性；退化总体固定输出 `z=0/p=1/bin=0`。
- 输出顺序为格网 ID、Polygon Geometry、可选窗口起止、点数、分析值、z-score、原始 p-value、
  调整后 p-value 和置信分级。Compiler 只构造零行/惰性计划；Runner 摘要不记录坐标或数据值。
- 当前实现采用公开 Gi* 公式、标准正态双侧概率和显式 BH 规则；真实 Enterprise 的边缘、FDR、
  数值和规模对照仍开放，不宣称与 Esri 内部实现逐位一致。完整设计见
  [Find Hot Spots](canvas-spatial-next-processors/spatial-hot-spots.md)。

## 7.13M `SPATIAL_MULTI_VARIABLE_GRID` 节点

- 类别为 `PROCESSOR`，仅支持 `BATCH`，协议引入版本为 `4.70`；至少一条入边，允许没有出边。
- `variables` 为一至 32 个有序变量。每项选择一张 `BOUNDED` 来源表、带完整相同投影 CRS 元数据的
  XY Point/Line/Polygon 家族 Geometry、可选受控筛选和唯一输出字段；多个变量可以引用同一张表。
- 所有唯一“来源表 + Geometry 字段”的有效 Geometry 在不应用变量筛选的情况下共同决定分析外包范围。
  SQUARE 的大小为边长，HEXAGON 为对边距离，固定原点 `(0, 0)`；输出与范围相交的完整 Polygon 格网。
- `DISTANCE_TO_NEAREST` 和 `ATTRIBUTE_OF_NEAREST` 必须配置搜索距离，使用格网中心到来源 Geometry 的
  平面最短距离；半径内无命中输出 NULL。前者输出该变量距离单位下的 DOUBLE，后者继承所选标量字段类型。
- `ATTRIBUTE_SUMMARY_OF_RELATED` 有搜索距离时按格网中心距离关联，无搜索距离时按要素与完整格网相交。
  COUNT 输出非空 LONG 且无命中为 0；SUM/MEAN/MIN/MAX/RANGE/STDDEV/VARIANCE 输出可空 DOUBLE；
  ANY 第一版只接受 STRING，并以确定性最小值实现。
- 搜索距离与格网大小之比最多 512，格网候选最多 1,000,000。实际无效 Geometry、非有限统计值和
  容量超限分别使用稳定安全错误；Compiler 只构造零行或惰性计划。
- 结果列为格网 ID、共同 CRS 的 XY Polygon 和变量数组顺序下的结果字段；来源及其他入口表不变，
  新的 `BOUNDED` 结果表追加到 Map 末尾，无事件时间和 Watermark。
- Runner 摘要只记录变量数、来源表数、筛选变量数、格网形状和输出表，不记录字段名、筛选字面量、
  搜索距离、坐标或数据值。真实 Enterprise 边缘、并列、统计、不同 Geometry/单位及规模对照仍开放。
  完整契约和面板见 [Build Multi-Variable Grid](canvas-spatial-next-processors/spatial-multi-variable-grid.md)。

## 7.13N `SPATIAL_ENRICH_FROM_GRID` 节点

- 类别为 `PROCESSOR`，仅支持 `BATCH`，协议引入版本为 `4.71`；至少一条入边，允许没有出边。
- `pointTableName` 必须是有界 XY Point；`gridTableName` 必须是有界 XY Polygon/MultiPolygon，
  两侧 Geometry CRS 完全一致。需要变换时在上游显式使用 Spatial Transform。
- `enrichFields` 显式选择格网非 Geometry 标量字段并可改名；来源字段不得重复，结果字段与 Point
  原字段及其他结果字段按大小写不敏感规则唯一。空数组只允许保存草稿，Compiler 拒绝执行。
- 关系固定为 Point 与完整格网 Polygon 相交。每个 Point 恰好输出一行；未匹配时丰富字段为 NULL，
  共享边界或异常重叠命中多个格网时按 `gridIdColumnName` 的字符串顺序稳定选择一格。
- 命中格网 ID 为 NULL 时运行返回 `SPATIAL_ENRICH_GRID_ID_NULL`。结果保留 Point 全部字段及顺序，
  再追加可空的丰富字段；入口表保持不变，新结果表追加到 Map 末尾。
- 本节点不重算格网变量，不复制 ArcGIS 服务发布和 Data Store 参数，也不自动把格网后续新增字段加入结果。
  完整契约和面板见 [Enrich From Multi-Variable Grid](canvas-spatial-next-processors/spatial-enrich-from-grid.md)。

## 7.13O `SPATIAL_GROUP_BY_PROXIMITY` 节点

- 类别为 `PROCESSOR`，仅支持 `BATCH`，协议引入版本为 `4.72`；至少一条入边，允许没有出边。
- 来源为一张有界、带完整 CRS 的 XY Point/MultiPoint、LineString/MultiLineString 或
  Polygon/MultiPolygon 表。Intersects 支持三类家族，Touches 只支持 Line/Polygon。
- Near Planar 使用来源投影 CRS 的二维最近距离；地理 CRS 必须先显式转换。Near Geodesic 只支持
  EPSG:4326 XY，并使用真实 Geometry 最近位置；不接受来源 CRS 单位。
- 可选时间关系使用一个开始/瞬时字段和可选结束字段表达闭区间，支持 Intersects 与 Near。时间 NULL
  不形成边，实际倒置区间稳定失败。毫秒至周使用固定时长，月/年使用会话时区日历区间。
- 至多 8 个属性关系支持同一字段普通等号或数值绝对差阈值；它们与空间、时间关系全部按 AND 组合。
  当前不是 ArcGIS 任意对称属性表达式的完整实现。
- 满足全部条件的要素对构成无向边，结果使用 Connected Components 求传递闭包。NULL/Empty Geometry
  不形成边但作为孤立顶点保留；每个来源要素恰好输出一行并追加非空 LONG 组 ID。
- 组 ID 只表达成员关系，不保证连续、排序或跨运行稳定。来源及其他入口表继续保留，新有界结果追加，
  不传播事件时间或 Watermark。Compiler Preview 不执行图算法。
- Runner 摘要不记录距离/时间/属性阈值、坐标或字段值。完整契约和面板见
  [Group By Proximity](canvas-spatial-next-processors/spatial-group-by-proximity.md)。

## 7.13P `TRACE_PROXIMITY_EVENTS` 节点

- 类别为 `PROCESSOR`，仅支持 `BATCH`，协议引入版本为 `4.73`；至少一条入边，允许没有出边。
- 来源为一张有界、带完整 CRS 的 XY Point 观测表；实体 ID 必须是 STRING，观测时间必须是 TIMESTAMP。
  NULL/Empty Geometry、NULL 时间或 NULL 实体 ID 不参与追踪；非空但无效的 Point 稳定失败。
- Planar 使用来源投影 CRS 的二维距离；Geodesic 仅接受 EPSG:4326 XY 并使用 WGS84 真实距离。
  空间、时间和至多 8 个同值属性约束全部满足时，不同实体的观测才形成接触。
- 起始实体可由 1～256 个显式 ID 和可选 Epoch 毫秒开始时间定义，也可从另一张有界上游表读取
  STRING ID 与可选 TIMESTAMP。起始实体深度为 0，最大传播深度为 1～32。
- 传播只能使用不早于上游实体到达时间的接触。同一下游实体有多个候选时，按事件时间、上游实体 ID
  和内部行身份稳定选择首次事件；起始实体不伪造成 `from_id/to_id` 事件。
- 首次事件结果保留下游首次接触观测的原字段，并追加来源实体、目标实体、深度、持续分钟数和事件时间。
  可选轨迹表包含起始实体深度 0 轨迹，以及下游实体从首次接触起的后续观测。
- 入口表继续保留，事件表和可选轨迹表追加到 Map 末尾；两张结果均为 `BOUNDED`，不传播事件时间或 Watermark。
  Compiler Preview 不执行自连接、起始实体查找、BFS 或 Checkpoint。
- Runner 摘要和 Canvas 卡片不得展示实体 ID 值、距离值、坐标或数据行。完整契约、错误码和面板见
  [Trace Proximity Events](canvas-spatial-next-processors/trace-proximity-events.md)。

## 7.13Q `SNAP_TRACKS` 节点

- 类别为 `PROCESSOR`，仅支持 `BATCH`，协议引入版本为 `4.74`；至少一条入边，允许没有出边。
- 轨迹观测是有界 XY Point，道路网络是同 CRS 的有界 XY LineString。轨迹由
  1～8 个标识字段分组，按 TIMESTAMP 和可选同时间顺序字段执行确定性排序。
- 线网必须显式配置非空唯一线 ID 和非空同类型 From/To 节点。方向对象缺失时所有线双向通行；
  存在时把显式属性值映射为顺向、逆向、双向或禁行，未命中的实际值按禁行处理。
- Planar 要求可换算线性单位的投影 CRS；Geodesic 仅支持 EPSG:4326 XY。每个观测在
  搜索距离内最多允许 32 个候选，超出时稳定失败，不静默截断。
- 每个轨迹片段使用 Viterbi 动态规划联合匹配。候选转移只允许同一条线，或共享一个 From/To
  节点的直接相邻线，并按方向检查可达性。不连通的更近候选不得退化成独立最近线结果。
- 相邻时间 gap、相邻距离 gap 和固定时间周期均可切分轨迹。单观测片段固定标记未匹配。
- 结果保留点表原字段，可选追加至多 32 个道路标量属性，再追加吸附 Point、匹配线 ID、
  `M/U` 状态、原/匹配坐标和固定米制匹配距离。`ALL_FEATURES` 保留未匹配观测，
  `MATCHED_FEATURES` 只保留匹配观测。
- 入口 Point、Line 和其他表保持原 Map 顺序，新的有界结果表追加；Compiler Preview 只构造
  零行 Schema，不执行空间 Join、候选聚合或 Viterbi。
- Canvas 卡片和 Runner 摘要不得展示搜索距离数值、方向映射值、坐标或数据内容。
  首版只支持直接相邻线，不声明与 ArcGIS 完全等价；完整配置、算法、错误码和面板见
  [Snap Tracks](canvas-spatial-next-processors/snap-tracks.md)。

## 7.13R `SPATIAL_SIMILAR_LOCATIONS` 节点

- 类别为 `PROCESSOR`，仅支持 `BATCH`，协议引入版本为 `4.75`；至少一条入边，允许没有出边。
- 参考表和候选表都必须是有界 XY 空间表；Geometry 类型、CRS 和维度必须一致。NULL/Empty Geometry
  不参与分析，但其他入口表和两张来源表继续保留，新的有界结果表追加到 Map 末尾。
- 两侧唯一 ID 在运行时必须非空且唯一。多个参考要素按每个分析字段的标准化平均值形成共同目标；
  标准化总体固定包含筛选后的参考和候选全集。
- `ATTRIBUTE_VALUES` 使用各字段标准化值与参考目标的平方差之和，越小越相似；
  `ATTRIBUTE_PROFILES` 使用标准化向量的 `1 - cosine`，范围为 0～2，0 最相似且至少需要两个字段。
- 可返回最相似、最不相似或两端候选；`BOTH` 在候选不足时自动缩小每端数量，保证两端不重叠。
  相同分数按候选 ID 字符串确定排名，不依赖 Spark 分区顺序。
- 分析字段必须在两表同名、同数值类型，最多 32 项；候选附加标量字段最多 64 项，不参与标准化或排名。
  分析值的 NULL、NaN 和 Infinity 稳定失败，不静默忽略或填补。
- 结果同时包含筛选后的全部参考位置和选中候选，显式输出位置类型、参考/候选 ID、两种排名、
  `simindex`、`cosimindex` 及用于渲染的有符号 `labelrank`。Compiler Preview 只构造惰性计划，
  不为预检扫描真实 ID 或数值。
- Canvas 卡片和 Runner 摘要只显示表名、匹配方法、返回范围和字段数量，不显示筛选字面量、属性值、
  坐标或数据行。当前是 GeoAnalytics 核心可执行子集，不声明与真实 Enterprise 数值完全等价；
  完整配置、面板和证据边界见
  [查找相似位置](canvas-spatial-next-processors/spatial-similar-locations.md)。

## 7.13S `SPATIAL_DESCRIBE_DATASET` 节点

- 类别为 `PROCESSOR`，仅支持 `BATCH`，协议引入版本为 `4.76`；至少一条入边，允许没有出边。
- 来源必须是一张 `BOUNDED` 表。节点保留全部入口表及其顺序，再依次追加字段统计表、数据集描述表、
  可选样本表和可选范围表；所有启用的结果表名必须非空、大小写不敏感唯一，且不能占用入口表名。
- 字段统计排除 Geometry 与 Binary。每个其他字段输出非空/空值数；数值字段统一转 DOUBLE 后输出
  Sum、Mean、Min、Max、Range、总体标准差和总体方差；日期时间字段输出 Min、Max 与毫秒范围；
  String/Boolean 的 `any_value` 使用确定性的最小非空字符串。
- 数据集描述表固定一行，包含记录数、字段数、可选 Geometry/事件时间计数与范围，以及同一安全描述的
  `description_json`。未选择 Geometry 时仍可生成字段统计、描述和样本，不推测空间字段。
- `sampleSize=0` 关闭样本；启用后使用 Spark `limit`，保留来源 Schema、事件时间和 Watermark 元数据，
  但不承诺跨重分区得到相同样本。范围开关独立于样本，并要求显式选择带完整元数据的 Geometry。
- 范围表只对非 NULL、非 Empty Geometry 求共同 XY Envelope，输出继承来源 CRS 的 XY Polygon；无有效
  Geometry 时输出空表。它不是最小包围几何、凸包或业务边界。
- Compiler 只构造零行或惰性计划，不执行统计、采样或范围扫描。Canvas 与 Runner 摘要只记录来源、
  启用的结果种类和安全表名，不记录数据值。完整配置、输出 Schema 和面板见
  [Describe Dataset](canvas-spatial-next-processors/spatial-describe-dataset.md)。

## 7.14 `GEOMETRY_BUFFER` 节点

节点保留来源 Geometry，并按显式距离模式追加规范化的 MultiPolygon 缓冲字段。

- 类别为 `PROCESSOR`，支持 `BATCH/STREAMING`，协议引入版本为 `1.22`。
- 至少一条入边；允许没有出边，实际参与计算的距离必须是有限正数。
- 4.49 可显式保存 `distanceUnit`；PLANAR 将固定线性单位可靠换算到来源投影 CRS 的轴单位，
  地理 CRS 只允许 `SOURCE_CRS_UNIT` 并产生角度单位 Warning。
- SPHEROID 只接受 EPSG:4326，将米、千米、国际/美国测量制等固定线性单位换算为米；
  不接受 `SOURCE_CRS_UNIT`。缺失/null 单位保持旧版固定米语义。
- 4.52 可显式选择 `CONSTANT/FIELD/EXPRESSION`。缺失/null 来源保持固定值；FIELD 必须是数值字段，
  EXPRESSION 必须能由 Spark Analyzer 证明为确定性逐行数值表达式，不允许聚合、窗口、生成器或子查询。
- 动态距离 NULL 产生 NULL Buffer；0、负数、NaN、Infinity 或转换溢出在运行时稳定失败，不跳过行。
- 结果通过 `ST_Multi` 规范化为 `MULTIPOLYGON`，CRS、dimension 和 nullable 继承来源字段。
- 完整配置、错误码和运行依赖见
  [Geometry Buffer 设计](canvas-geometry-buffer-processor-design.md)。

## 7.15 `GEOMETRY_EXPLODE` 节点

节点使用 `ST_Dump` 将 MultiGeometry、GeometryCollection 或普通 Geometry 展开为部件行，
并复制来源行的全部属性。

- 类别为 `PROCESSOR`，支持 `BATCH/STREAMING`，协议引入版本为 `1.22`。
- 至少一条入边；允许没有出边，节点可能增加行数，但不增加流式状态。
- 使用 outer 展开语义，NULL 或 Empty 输入保留一行并输出 NULL 部件。
- 可选部件序号从 0 开始；NULL 或 Empty 行的序号为 NULL。
- MultiPoint/MultiLineString/MultiPolygon 的输出 kind 分别收窄为
  Point/LineString/Polygon，集合或通用 Geometry 保持通用 `GEOMETRY`。
- 完整配置和错误码见
  [Geometry 拆分设计](canvas-geometry-explode-processor-design.md)。

## 7.16 `SPATIAL_CLIP` 节点

节点使用 Polygon/MultiPolygon Mask 表裁剪来源表的一个 Geometry 字段，并保留来源属性、
追加裁剪结果字段；Mask 属性不进入输出。

- 类别为 `PROCESSOR`，仅支持 `BATCH`，协议引入版本为 `1.23`。
- 至少一条入边；允许没有出边，来源表和 Mask 表从合并后的表 Map 选择，且必须不同并均为 BOUNDED。
- 固定使用 `ST_Intersects` INNER 候选连接与 `ST_Intersection`；NULL、Empty 和未命中结果不输出。
- 4.77 新建节点默认 `DISSOLVE_ALL`：每条来源要素先按计划内行 ID 聚合其相交 Mask，再裁剪一次；
  重叠 Mask 不重复覆盖区域，分离片段作为同一 Multi 结果返回。缺失/null 或 `PAIRWISE` 保持旧版
  每条 Mask 独立裁剪和多行输出；两种模式都不做来源去重或稳定排序。
- 两侧 Geometry 的 CRS 和 dimension 必须一致，Mask kind 只允许 Polygon/MultiPolygon。
- 4.51 新建节点默认 `SOURCE_FAMILY_2D`：过滤低维边界接触，输出来源对应的
  MultiPoint/MultiLineString/MultiPolygon + XY。缺失/null 或 `LEGACY_ANY_DIMENSION` 保持旧版
  通用 `GEOMETRY` 结果；CRS 继承来源字段，输出事件时间和 Watermark 清空。
- 完整配置、错误码和安全边界见
  [空间裁剪设计](canvas-spatial-clip-processor-design.md)。

## 7.17 `SPATIAL_AGGREGATE` 节点

节点按零到多个普通标量字段分组，对一张来源表执行 `UNION/INTERSECTION/COLLECT/ENVELOPE`
空间聚合。

- 类别为 `PROCESSOR`，仅支持 `BATCH`，协议引入版本为 `1.23`。
- 至少一条入边；允许没有出边，只接受 BOUNDED 来源。
- `groupByColumns=[]` 表示全局聚合；分组字段不得重复或使用 Geometry。
- 聚合项数量为 `1..32`；输出字段名不得重复，也不得与分组字段同名。
- 每个结果独立继承来源 Geometry 的 CRS 和 dimension，kind 固定为通用 `GEOMETRY`，
  nullable 为 true。
- 输出 Schema 为分组字段后接聚合字段；输出事件时间和 Watermark 清空。
- 4.53 可选 `dissolve`：启用时要求单 UNION；空/非空分组分别表达 Create Buffers 的 All/List，
  结果追加来源要素总数和最多 32 个标量统计。Multipart 每组最多一行，Singlepart 按部件拆行；
  NULL/Empty UNION 结果不产生 Dissolve 输出要素。
- 4.61 可选 `dissolve.groupingMode=CONNECTED_COMPONENTS`：只允许空分组和面类型，按二维相交、重叠
  或接触关系的传递闭包分别 UNION；NULL/Empty 不进入连通图。缺失/null 继续保持 4.53 的全局 All。
- 完整配置、NULL/Empty 语义和错误码见
  [空间聚合设计](canvas-spatial-aggregate-processor-design.md)。

## 8. `JDBC_OUTPUT` 节点

### 8.1 配置结构

```json
{
  "sourceTableName": "order_customer",
  "dataSourceId": "a406e119-fbd2-4173-84ee-c92c69231168",
  "targetTableName": "dwd_order_customer",
  "writeMode": "APPEND",
  "columnMappings": [
    {
      "sourceColumnName": "order_id",
      "targetColumnName": "order_id"
    },
    {
      "sourceColumnName": "customer_name",
      "targetColumnName": "customer_name"
    }
  ],
  "upsertKeyColumns": []
}
```

对应 TypeScript 类型：

```ts
type JdbcWriteMode = 'APPEND' | 'OVERWRITE' | 'UPSERT';

interface JdbcColumnMapping {
  sourceColumnName: string;
  targetColumnName: string;
}

interface JdbcOutputConfiguration {
  sourceTableName: string;
  dataSourceId: string;
  targetTableName: string;
  writeMode: JdbcWriteMode | null;
  columnMappings: JdbcColumnMapping[];
  upsertKeyColumns: string[];
}
```

### 8.2 输入与输出

- 节点类别：`OUTPUT`
- 入边数量：必须为 `1`
- 出边数量：必须为 `0`
- 从上游 Map 中按 `sourceTableName` 取得待输出表。
- 输出节点不产生下游表 Map。
- 定义校验和预运行只检查配置及 Schema，不执行任何写入。

### 8.3 统一字段映射

- `columnMappings` 至少包含一项。
- 每个源字段和目标字段都必须存在。
- 同一个目标字段只能被映射一次。
- 相同的源字段可以按明确配置映射到多个目标字段。
- 目标表必填可写字段必须全部被覆盖。
- 自增和生成字段不能配置映射；可空或具有数据库默认值的目标字段允许不映射。
- 未映射来源字段直接忽略，不产生额外字段警告。
- 映射按目标 Schema 顺序保存；目标字段失效时保留失效项，直到用户手工清除或重新选择目标资源。
- 映射字段使用显式 Spark Cast。风险转换保留为警告并允许继续，Analyzer 不支持的转换校验失败。

设计器固定按“目标字段 ← 来源字段”展示，并可自动填充空白映射。自动匹配依次尝试完全同名、忽略大小写、转小写并移除下划线；任一层级存在多个来源候选时不自动选择。自动匹配只发生在设计时，结果直接保存为普通 `columnMappings`，Task Engine 运行时不再动态按名称匹配。

### 8.4 写入模式

- `APPEND`：向目标表追加写入。
- `OVERWRITE`：保留目标表结构，清空目标表后写入。
- `UPSERT`：按目标表的一整组主键或安全唯一索引字段插入或更新。PostgreSQL 严格使用所选字段组作为 `ON CONFLICT` 目标；MySQL 使用原生 `ON DUPLICATE KEY UPDATE`，任一唯一约束冲突都可能触发更新。

非 `UPSERT` 模式下 `upsertKeyColumns` 必须为空；旧定义缺少该字段时规范化为空数组。`UPSERT` 必须一次选择目标元数据 `uniqueKeys` 中完整且顺序一致的一项，不能逐字段拼接。Key 必须全部被输出映射覆盖，并且不能是自增字段、生成字段或 Geometry；冲突时只更新已映射的非 Key 字段。只有 Key 而没有非 Key 字段时，PostgreSQL 使用 `DO NOTHING`，MySQL 执行无变化更新。

`JDBC_OUTPUT` 的 Batch 支持三种模式，Streaming 支持 `APPEND/UPSERT` 并拒绝 `OVERWRITE`；Streaming JDBC 输出继续拒绝 Geometry，Batch UPSERT 可以更新非 Key Geometry 字段。Canvas `3.0` 中的 `MODEL_OUTPUT` 同样在 Batch 支持 `APPEND/OVERWRITE/UPSERT`、在 Streaming 支持 `APPEND/UPSERT`；其 UPSERT Key 固定取目标模型完整主键，不写入节点配置，也不检查物理表唯一约束。

UPSERT 写入前检查当前 Dataset 或 micro-batch：Key 含 NULL 返回 `UPSERT_KEY_NULL`，批内重复返回 `UPSERT_DUPLICATE_KEY`，错误不得包含实际 Key 值。每个 Spark 分区使用独立 JDBC 事务，不提供跨分区全局事务；Streaming 是至少一次交付，micro-batch 重放只保证键级收敛，不宣称 Exactly Once。完整运行规则见 [JDBC_OUTPUT UPSERT 设计](canvas-jdbc-output-upsert-design.md)。

### 8.5 校验

- 上游 Map 中存在 `sourceTableName`。
- `dataSourceId` 对应已启用的 JDBC 数据源，并具有 `DISTRIBUTION`（数据分发）用途。
- 目标表存在且可读取元数据。
- 目标对象必须是允许写入的物理表，不能是只读视图。
- 写入模式和字段映射配置完整。
- 字段映射满足目标表必填字段和平台类型兼容要求。
- UPSERT Key 与目标表当前 `uniqueKeys` 中的一整组字段精确匹配，并满足可写、映射和类型限制。
- UPSERT 当前支持 PostgreSQL、HighGo、MySQL、openGauss、人大金仓、达梦、Oracle 与 SQL Server；ClickHouse 和 TDengine 不开放。MySQL 目标存在多组唯一键时返回 `MYSQL_UPSERT_MULTIPLE_UNIQUE_KEYS` Warning。

## 9. 设计器配置面板

### 9.1 公共交互

- 单击节点后在画布右侧打开与节点类型对应的配置面板。
- `INPUT`、`PROCESSOR`、`OUTPUT` 分别使用蓝色、紫色、绿色标题栏区分类别，不在节点右上角重复显示类别标签。
- 节点名称属于画布元信息，与节点位置一样直接在画布上维护；双击标题进入行内编辑，`Enter` 或失焦保存，`Esc` 取消。
- 节点名称不能为空，去除首尾空白后最长 100 个字符；重命名必须支持撤销和重做，且不得改变表名 Map Key。
- 右侧配置面板只编辑节点业务配置，不得提交或覆盖节点名称。
- 画布鹰眼图固定在左下角；撤销、重做、放大、缩小和居中使用纯图标工具条展示在鹰眼图右侧。节点删除只保留节点标题栏入口和键盘操作，不在全局工具栏重复提供。
- 节点配置变化后先更新页面内存状态；正式任务定义页只有在用户明确点击“保存定义”时才整体提交后端，独立试验页不调用保存接口。两者都不写入 `localStorage`。
- 上游节点、连线或配置变化后调用 Task Engine；下游节点的可用表和字段只使用当前 Engine 响应中的 `inputTables/outputTables`。
- 已保存的下游选择不再可用时，不自动清空用户配置；保留原值并在节点上显示校验错误，便于用户定位修改。
- 前端不执行拓扑分析、表 Map 合并、Join Schema、字段兼容性或 Output 映射校验；Task Engine 是有效性、问题和节点输入输出的唯一来源。
- JSON 安全解析、X6 自连接/重复边/环路拦截和表单必填规则属于编辑交互护栏，不替代 Engine 校验。
- 当前定义等待元数据、防抖或 Engine 响应时遮罩整个 Canvas 工作区；旧 Engine 结果立即失效，不得用过期 Schema 继续配置。
- 元数据读取失败只影响当前配置和校验，不得把失败状态或错误堆栈写入 Canvas 定义。

### 9.2 `JDBC_INPUT` 面板

表单顺序：

1. 数据源：远程搜索真实数据源，只列出已启用、JDBC 类型、具有 `SOURCE` 用途且支持表和字段元数据读取的数据源；定义只保存 UUID。
2. 管理物理表：打开双栏选择弹窗。候选区按物理表名远程搜索，每次最多读取 100 个表摘要；结果截断时提示继续输入关键词。已选项跨搜索结果保留，候选表不预取字段元数据。
3. 已选表配置：Inspector 中按照定义顺序完整列出所有已选物理表，支持移除、排序、按需展开字段和重新读取失败元数据。每一行对应一个独立 `JdbcInputTableSelection`。
4. 逐表读取配置：每张已选表在“查看字段”和“移除”之间提供设置按钮，打开独立的键值编辑弹窗。弹窗可添加、删除、清空和排序参数，并提示 Spark 按字符串传参、`sessionInitStatement` 在每条 JDBC 连接建立后执行、不得填写凭据及分片读取将使用后续专门配置。无效非空草稿必须保留并标红；仅名称和值同时为空的新建占位行可在保存时移除。删除带参数的表、取消选择或清空表时必须二次确认。

切换数据源时不得静默清空已选表；保留原值并使用新数据源重新解析。不存在的表就地标红，但错误草稿仍允许应用和保存，由发布/执行预检阻止运行。节点卡片单表时展示优先字段，多表时展示前 3 张表、字段数和剩余表数量；表名不附加数据库或 Schema。

### 9.2A `JDBC_QUERY_INPUT` 面板

表单顺序：

1. 数据源：只列出已启用、具有 `SOURCE` 用途的 PostgreSQL/HighGo/MySQL/openGauss/人大金仓数据源。
2. 输出逻辑表名。
3. Monaco SQL 编辑器。
4. “分析 SQL”操作区，显示未分析、分析中、有效、已过期或失败状态。
5. 上次成功分析得到的只读字段预览。

编辑 SQL 时不得自动访问数据库；保留已有 Hash 和字段快照，并明确标记“分析结果已过期”。用户点击“分析 SQL”成功后才同时替换 `analyzedSqlSha256` 和 `outputColumns`；失败时保留当前 SQL 与原快照。未分析或已过期的草稿允许应用和导出，由 Compiler 返回稳定错误。

导入 JSON 后直接展示定义内快照；Hash 与 SQL 匹配时不强制重新分析。节点卡片和安全摘要只展示数据源、输出表和字段数，不显示 SQL 或其中的字面量。

### 9.3 `HTTP_API_INPUT` 面板

- 数据源下拉只显示已启用、具有 `SOURCE` 用途的 HTTP API 数据源。
- “管理资源”使用双栏批量选择，候选只显示当前数据源下已启用资源，已选资源完整保留且可排序。
- 每个资源独立配置输出表名和非敏感运行时参数；面板持续提示参数会进入 Canvas JSON，禁止填写 Token、密码、API Key 或 Secret。
- 资源输出字段以只读方式预览，字段变化通过重新读取资源详情和重新编译反映，不复制进 Canvas 定义。

### 9.4 `JOIN` 面板

表单顺序：

1. 左表：从全部直接上游 Map 的无冲突合并结果中选择。
2. 右表：排除已经选择的左表。
3. Join 类型。
4. 输出表名。
5. Join 条件列表：每行选择左字段、操作符和右字段，支持新增和删除。
6. 输出字段列表：展示来源侧、来源字段、输出字段名和启用状态，支持排除、改名和排序。

选择左右表后，字段下拉框只展示当前 Task Engine 节点结果中对应表的 Schema。Engine 使用实际 Spark Analyzer 判断兼容性；Analyzer 接受时不显示平台类型提示，Analyzer 拒绝时显示节点错误。

节点卡片配置完成后显示简要表达式，例如：

```text
orders INNER customers → order_customer
```

面板提供“排除右侧 Join Key”和“重建建议”。重建建议会明确覆盖当前排除、改名和排序，并要求二次确认。
上游字段失效时保留原映射并标红；建议后仍重名时直接展示冲突，不自动追加数字。

### 9.4A `STREAM_JOIN` 面板

- 左表候选只显示无界流表，右表候选只显示有界静态表。
- 输出字段编辑复用 `JOIN` 面板，来源标签显示为“流”和“维”。
- 首次取得两侧 Schema 且当前投影为空时生成建议；已有投影不因上游变化被静默重建。
- 允许排除、改名、排序和一键排除右侧 Join Key；重建建议必须二次确认。
- 失效字段和最终重名就地标红，但业务校验错误不阻止应用草稿。

### 9.4B `SPATIAL_JOIN` 面板

- 左表标为目标表，右表标为连接表；“结果范围”提供“仅保留匹配目标（INNER）”与“保留全部目标
  （LEFT）”。连接粒度紧凑选择“一对多”或“一对一”，一对一旁提供设置入口和当前规则摘要。
- 一对一设置使用独立 Modal。汇总模式配置 Join Count 和有序数值统计；保留模式配置 FIRST、最大、最小、
  最新或最旧策略，以及至少一个稳定排序字段。Modal 明确完整顺序仍并列会运行失败，并建议最后使用右表主键。
- 左右表和空间条件仍在主面板配置；输出字段使用 860px 设置 Modal，避免长字段列表持续撑高 Inspector。
- “空间 Near”使用独立紧凑 Modal 配置左右 Geometry、Near/Near Geodesic、最大距离和单位；它可以作为
  唯一空间条件，也可与拓扑、属性、时间条件按 AND 组合。字段失效或配置不完整时保留草稿并就地报错。
- “距离输出”使用第二个紧凑 Modal。仅一对多可启用；根据当前空间 Near 和时间 Near 分别显示字段名与
  输出单位，两种 Near 同时启用时显示两组。切换到一对一或移除 Near 不静默清空已有非活动草稿。
- 新节点在首次选齐左右表时生成投影建议；已有投影不因上游 Schema 变化静默重建。
- 允许逐项排除、改名和排序；“重建建议”必须确认并明确会覆盖当前编辑。空间条件不是等值 Key，
  因此不显示普通 Join 的“排除右侧 Join Key”操作。
- 旧版缺失/null 投影显示“旧版全字段”，用户明确点击“启用字段投影并生成建议”后才写入 4.54 数组，
  打开或保存旧定义不会自动改变结果字段。
- 上游字段失效和最终重名就地标红，普通业务错误仍允许应用草稿。Canvas 仅显示启用输出字段数量，
  并以 `1:N`、`1:1 汇总` 或 `1:1 保留` 展示粒度；Near 只展示方法和距离输出状态，不展示 Geometry、
  距离阈值、时间阈值、坐标或数据值。

### 9.5 `RENAME` 面板

表单顺序：

1. 来源表：从唯一直接上游的无冲突 Map 中选择一张逻辑表。
2. 输出逻辑表名：始终必填；只改字段名时与来源表名相同。
3. 字段重命名列表：每行选择一个来源字段并填写目标字段名，支持新增和删除。

字段映射按原始 Schema 同时生效，不按配置顺序链式执行，因此 `a -> b, b -> a` 是合法交换。第一次选择来源表且输出表名为空时，前端可以复制来源表名；此后上游变化必须保留已有配置并由 Task Engine 展示失效引用，不得静默清空或改名。

节点卡片显示 `orders -> source_orders` 和字段重命名数量。前端不计算最终字段冲突，来源表和字段选项只使用 Task Engine 返回的 `inputTables`。

### 9.6 `FILTER` 面板

窄 Inspector 只展示已选处理表、筛选方式摘要和逐表配置入口。逐表 Modal 顶部配置输出方式，规则区可在“可视化条件”和“SQL 表达式”之间切换；切换时保留另一模式的草稿，但只有当前模式参与校验和执行。

可视化模式支持嵌套 `AND/OR` 条件组和字段谓词。SQL 模式只填写 Spark SQL 布尔谓词，不填写 `WHERE`；提供当前来源表字段插入，禁止完整 SQL、子查询、注释和分号，最大 8192 个字符。字段候选只使用 Task Engine 返回的来源 Schema。上游字段失效时保留原配置并显示错误，不静默清空。节点卡片只显示处理表、模式和条件数量，不展示 Literal 或 SQL 表达式正文。

### 9.7 `SELECT_COLUMNS` 面板

表单顺序：

1. 来源表：从唯一直接上游的无冲突 Map 中选择一张逻辑表。
2. 输出表名：必须与当前输入 Map 中所有表名不同。
3. 字段双区选择器：可搜索并添加来源字段，已选字段区支持上移、下移、删除、全选和清空。

已选字段数组顺序就是输出 Schema 顺序；“全选”必须展开为当前明确字段名，不能保存通配符。字段行显示名称、平台类型和 nullable。切换来源表或上游 Schema 变化时保留旧字段及顺序，失效字段原位标红，由用户显式处理。节点卡片只显示来源表、输出表和字段数量。

### 9.8 `DERIVE_COLUMNS` 面板

窄 Inspector 仅展示全局规则入口和已选处理表；每张表显示全局规则数、本表独立规则数和配置入口。全局规则作用于所有已选处理表，新增处理表后也立即继承。表级输出方式和输出表名仍独立配置。

全局规则和表级规则均通过宽 Modal 编辑：左侧维护规则列表，右侧配置目标字段名和结构化表达式。目标字段已存在时自动覆盖，不存在时自动追加；表配置 Modal 中的全局规则只读，可跳转至全局配置修改。表达式构建器固定支持字段引用、Literal、二元运算、白名单函数和 `CASE_WHEN`，不提供自由 SQL。

全局规则的字段候选取所有已选处理表共有的字段 code；任一全局规则无法应用到某张表时，节点整体无效且不产生部分输出。全局与表级规则写入同一目标字段时返回 `GLOBAL_DERIVATION_TARGET_CONFLICT`。同一节点内的字段候选始终只来自 Task Engine 返回的原始来源 Schema，不包含本节点其他派生结果。上游字段失效时保留 AST 并在引用处标红。流任务目标名命中事件时间字段时立即提示 `STREAM_EVENT_TIME_COLUMN_IMMUTABLE`。节点卡片显示处理表、全局规则和规则总数，不显示 Literal 值。

### 9.9 `TYPE_CAST` 面板

顶部选择来源表和输出表名，下方维护转换项。每项配置来源字段、目标平台类型、STRING/DECIMAL 参数和 `FAIL/SET_NULL` 策略。`LONG → TIMESTAMP` 以及 `DATE/TIMESTAMP → LONG` 额外配置 Epoch 单位：秒、毫秒或微秒；新建或重新选择这些组合时默认毫秒。来源字段为 `STRING` 且目标类型为 `DATE` 或 `TIMESTAMP` 时可指定 Spark datetime pattern：DATE 默认 `yyyy-MM-dd`；TIMESTAMP 默认 `yyyy-MM-dd HH:mm:ss`，并必须明确选择来源 IANA 时区，或选择字符串内含的 `Z` / 偏移。DATE、TIMESTAMP 或 TIMESTAMP_NTZ 转 STRING 时可指定输出 pattern；TIMESTAMP 还必须明确目标 IANA 时区，新规则默认 UTC。GEOMETRY 不在可选类型中；上游字段失效时保留原字段名、类型及其时间转换配置。

`SET_NULL` 明确提示真实值转换失败后会变为 NULL，预检不会读取真实数据或统计影响行数。流模式选择事件时间字段时立即标错。节点卡片只显示转换数量与两种策略数量。

### 9.10 `AGGREGATE` 面板

顶部选择来源表和输出表名。分组字段区支持搜索添加、上移、下移和删除；不选择分组字段表示全表聚合。聚合指标区按顺序配置函数、来源字段、`DISTINCT` 和输出字段名；`COUNT` 可选择“全部行（*）”。

上游表或字段失效时保留原值并标红。`MIN/MAX` 的非法 DISTINCT、COUNT(*) 与 DISTINCT 的非法组合、重复输出字段以及与分组字段同名的问题必须在应用前提示，同时由 Task Engine 返回稳定错误。节点卡片只显示来源/输出表、分组字段数和指标数。

### 9.11 `UNION` 面板

输入表使用可排序列表，至少选择两张可见上游逻辑表。第一张表标记为“输出字段顺序基准”。每张表显示字段数和有界性；严格模式下，字段集合差异以紧凑的“缺少/额外”摘要展示。

新建节点默认使用“灵活对齐”。每张合并层都有字段设置入口，紧凑表格逐字段选择 Match、Rename 或
Remove。Match 只列出同类型、数值兼容类型或 Geometry 定义完全一致的目标字段；切换动作时优先保留
当前合法目标，其次选择同名目标，仅有一个兼容目标时才自动选中。调整基准层或删除输入表时同步移除
不再适用的字段草稿；保存时只持久化相对默认行为发生变化的规则。

模式为 `ALL` 或 `DISTINCT`。无界输入选择 DISTINCT 时明确提示不支持；有界与无界输入混合、无界事件时间或 Watermark 不一致时保留配置并显示 Task Engine 错误。上游表失效时原位保留并标红。

### 9.12 `DEDUPLICATE` 面板

顶部选择来源表和输出表名。去重范围明确选择“全部字段”或“业务键字段”；业务键字段使用可排序列表。保留策略提供任意一条、第一条和最后一条，并显示各自语义。

`FIRST/LAST` 展示可排序的排序规则列表，每项配置字段、升降序和 NULL 在前/后。切换到 `ANY` 时如果已有排序规则，必须由用户确认后清空；不能静默丢弃。上游字段失效时保留 key 和 order 项并标红。

### 9.13 `NULL_HANDLING` 面板

顶部选择来源表和输出表名，下方维护按顺序执行的规则列表。规则分为“删除空值行”和“固定值填充”：前者选择一个或多个字段以及 `ANY_NULL/ALL_NULL`，后者选择单字段并使用平台类型化 Literal 输入。规则支持新增、上移、下移和删除。

上游字段失效时原位保留规则并标红；流模式只禁止填充事件时间字段，允许按事件时间是否为 NULL 删除行。节点卡片只显示来源/输出表、删除规则数和填充规则数，不展示 Literal 实际值。

### 9.14 `VALUE_MAPPING` 面板

顶部选择来源表和输出表名，下方按字段维护精确映射项。每个字段只能配置一次；映射项由同类型 `sourceValue` 和 nullable `targetValue` 组成，并选择未匹配非 NULL 值的 `KEEP/SET_NULL/SET_LITERAL/ERROR` 策略。

面板显示字段规则数、单字段映射项数和节点总映射项数；上游字段失效时保留全部映射。流模式禁止修改事件时间字段。Literal 实际值只存在于配置控件和稳定定义，不进入节点摘要、日志或错误消息。

### 9.15 `WINDOW` 面板

顶部选择来源表和输出表名，随后依次维护可排序的分区字段、排序规则和窗口函数。排序规则明确方向与 NULL 位置；函数根据 `kind` 只展示需要的字段、offset、可选默认 Literal、输出字段和显式 `ROWS` Frame。

面板持续提示窗口排序不保证下游物理写出顺序。`WINDOW` 仅在批处理 Palette 中展示；上游字段失效时保留分区、排序、函数及 Frame 配置并原位标红。摘要记录函数种类和结构，不记录默认 Literal。

### 9.16 `TOP_N` 面板

顶部选择来源表和输出表名，并明确选择“全局前 N”或“每组前 N”。分组模式维护可排序的分区字段；排序列表明确方向与 NULL 位置；`limit` 范围为 `1..1000000`；并列策略为精确 N 行或保留第 N 名并列。

面板提示该排序只用于选行且不保证下游写出顺序。`TOP_N` 仅在批处理 Palette 中展示；上游字段失效时保留分组和排序配置。

### 9.17 `MASK_FIELDS` 面板

顶部选择来源表和输出表名，下方维护字段规则列表；同一字段只能选择一次。每项独立选择全局规则或自定义规则，并根据 Task Engine 返回的来源 Schema 判断策略与字段类型、nullable 是否兼容。不兼容的全局规则保留在列表中但不可选择，同时展示具体原因。

选择全局规则时通过普通详情接口把规则 ID、编码、名称和完整执行定义复制进节点；全局模式参数只读。“同步当前规则”显式覆盖节点定义，“转为自定义”保留当前定义并删除来源信息。打开面板时按规则 ID 去重查询普通详情并比较执行定义：定义变化、规则删除和暂时无法校验使用不同提示；检查本身不修改配置或触发 dirty。

节点卡片可显示来源/输出表和字段数量；Task Engine 安全摘要只记录字段数量、策略类型和全局/自定义数量，不显示表名、字段名、字段值、固定替换值或完整参数。节点 ID 由统一生命周期日志记录。

### 9.18 `JSON_EXTRACT` 面板

顶部选择来源表、JSON 来源字段和输出表名。来源字段候选只显示 Task Engine 返回的 STRING 字段；原字段失效或变为非 STRING 时保留已有值并标红。解析失败策略显式选择 `ERROR` 或 `SET_NULL`。

提取项按数组顺序维护，支持新增、上移、下移和删除。每项配置 JSON Path、输出字段名和目标平台类型；STRING 可选 length，DECIMAL 必填 precision/scale，GEOMETRY 不展示。输出字段名与来源字段或其他提取项重复时即时提示。节点卡片只显示来源表/字段、输出表、提取数量和失败策略，不显示 JSON Path、JSON 内容或实际值。

### 9.19 `JDBC_OUTPUT` 面板

表单顺序：

1. 来源表：从直接上游 Map 中选择。
2. 目标数据源：只列出已启用、JDBC 类型且具有 `DISTRIBUTION`（数据分发）用途的数据源。
3. 目标物理表：从目标数据源配置的数据库和 Schema 中加载。
4. 写入模式。
5. UPSERT Key 约束；仅在 `UPSERT` 时显示。
6. 固定字段映射表：左侧按真实目标表 Schema 顺序展示目标字段，右侧选择 Engine 上游 Schema 中的来源字段。

面板提供“自动匹配空白字段”，支持完全同名、忽略大小写和驼峰/下划线等价匹配；不会覆盖手工值或失效值。自增和生成字段显示为“数据库生成”且不可选择来源。来源或目标元数据变化后保留失效值并标红，不静默清空。

UPSERT Key 以完整约束为单选项展示，例如“主键：id”或“唯一索引 uk_order：tenant_id, order_no”，配置只保存有序目标字段名，不保存约束名称。Key 中含自增、生成或 Geometry 字段的约束禁用。元数据变化导致已保存字段组失效时保留原值并标红，不静默换成其他约束。MySQL 目标存在多组唯一键时持续显示紧凑提示，说明原生冲突范围不限于用户选择的 Key。

节点卡片配置完成后显示：

```text
order_customer → public.dwd_order_customer (APPEND)
```

### 9.20 `FILE_OUTPUT` 面板

面板配置来源表、S3 `DISTRIBUTION` 数据源、相对目标目录、冲突策略和文件格式。CSV、JSON
Lines、Parquet 保持原有目录数据集配置；选择 Shapefile 时额外配置文件基础名、ZIP/组件目录、
Geometry 字段、目标 Shape 类型和有序 DBF 字段映射。GeoParquet 配置唯一 Geometry、
Snappy/ZSTD 压缩和可选的逐行 bbox；GeoJSON 配置文件基础名、EPSG:4326 Geometry、
可选 Feature ID 和 NULL properties 保留策略。

Geometry、字段、CRS 和维度候选只来自 Task Engine 返回的 `inputTables`。首次初始化可以根据
GeometryKind 推荐 Shape 类型并生成 DBF 短字段名；之后上游变化必须保留用户已有值并原位标红，
不得自动改名、截断或清空。STRING 映射必须显式保存 `1..254` 的 UTF-8 字节宽度。面板固定提示
五组件、10 位 ASCII 字段名、NULL/空字符串兼容性、Driver 串行写出、1.8GB 限制以及 S3
OVERWRITE 非原子语义。GeoParquet 明确标识为分布式目录；GeoJSON 明确标识为 RFC 7946
单文件并提示 1.8GB 限制。完整规则分别见
[Shapefile 输出设计](canvas-shapefile-output-design.md)、
[GeoParquet 输出设计](canvas-geoparquet-output-design.md) 和
[GeoJSON 输出设计](canvas-geojson-output-design.md)。

## 10. 完整定义示例

```json
{
  "schemaVersion": 3,
  "schemaMinorVersion": 1,
  "nodes": [
    {
      "id": "878f22f4-86cf-4487-b697-5bc34eccb169",
      "type": "JDBC_INPUT",
      "name": "订单输入",
      "layout": { "x": 80, "y": 80, "width": 240, "height": 120 },
      "configuration": {
        "dataSourceId": "c5c021bd-35d1-43ae-bbdb-ff90ff824ba0",
        "tables": [{ "tableName": "orders", "readOptions": [] }]
      }
    },
    {
      "id": "3952906c-083d-434c-ac9c-d4d388bac74c",
      "type": "JDBC_INPUT",
      "name": "客户输入",
      "layout": { "x": 80, "y": 280, "width": 240, "height": 120 },
      "configuration": {
        "dataSourceId": "c5c021bd-35d1-43ae-bbdb-ff90ff824ba0",
        "tables": [{ "tableName": "customers", "readOptions": [] }]
      }
    },
    {
      "id": "1f17a225-f602-4a25-a08e-1e67e0b1b2f5",
      "type": "JOIN",
      "name": "订单关联客户",
      "layout": { "x": 440, "y": 180, "width": 240, "height": 120 },
      "configuration": {
        "leftTableName": "orders",
        "rightTableName": "customers",
        "outputTableName": "order_customer",
        "joinType": "INNER",
        "conditions": [
          {
            "leftColumnName": "customer_id",
            "operator": "EQUALS",
            "rightColumnName": "customer_key"
          }
        ],
        "outputColumns": [
          { "sourceSide": "LEFT", "sourceColumnName": "order_id", "outputColumnName": "order_id", "included": true },
          { "sourceSide": "LEFT", "sourceColumnName": "customer_id", "outputColumnName": "customer_id", "included": true },
          { "sourceSide": "RIGHT", "sourceColumnName": "customer_key", "outputColumnName": "customer_key", "included": true },
          { "sourceSide": "RIGHT", "sourceColumnName": "customer_name", "outputColumnName": "customer_name", "included": true }
        ]
      }
    },
    {
      "id": "af86e1c1-7575-4ed3-9600-dad157e6e085",
      "type": "JDBC_OUTPUT",
      "name": "订单客户结果输出",
      "layout": { "x": 800, "y": 180, "width": 240, "height": 120 },
      "configuration": {
        "sourceTableName": "order_customer",
        "dataSourceId": "04d11960-1ee1-4282-8963-6fb52a21ab0c",
        "targetTableName": "dwd_order_customer",
        "writeMode": "APPEND",
        "columnMappings": [
          { "sourceColumnName": "order_id", "targetColumnName": "order_id" },
          { "sourceColumnName": "customer_id", "targetColumnName": "customer_id" },
          { "sourceColumnName": "customer_key", "targetColumnName": "customer_key" },
          { "sourceColumnName": "customer_name", "targetColumnName": "customer_name" }
        ],
        "upsertKeyColumns": []
      }
    }
  ],
  "edges": [
    {
      "id": "b1f04da1-5202-4112-8ba6-ac02ea9ba249",
      "sourceNodeId": "878f22f4-86cf-4487-b697-5bc34eccb169",
      "targetNodeId": "1f17a225-f602-4a25-a08e-1e67e0b1b2f5"
    },
    {
      "id": "66e50e26-a0d0-46e9-ae05-6a47ba47294a",
      "sourceNodeId": "3952906c-083d-434c-ac9c-d4d388bac74c",
      "targetNodeId": "1f17a225-f602-4a25-a08e-1e67e0b1b2f5"
    },
    {
      "id": "b825d9b1-0321-48a1-9a13-11ec068b7b08",
      "sourceNodeId": "1f17a225-f602-4a25-a08e-1e67e0b1b2f5",
      "targetNodeId": "af86e1c1-7575-4ed3-9600-dad157e6e085"
    }
  ]
}
```

## 11. 图级校验

第一阶段在每次节点配置、真实元数据或连线变化后，由 Task Engine 执行图结构和 Schema 校验。前端通过现有只读数据源接口读取 JDBC 表元数据，通过 API 资源详情读取声明式输出 Schema，并组装临时元数据快照；设计期不会调用远程业务 API。错误分为画布级和节点级，只用于界面反馈，即使草稿无效，导出的 JSON 也只包含稳定定义。

结构校验至少包括：

- `schemaVersion + schemaMinorVersion` 是受支持组合，且节点类型在该版本中可用。
- 节点 ID 和边 ID 全局唯一。
- 节点类型受支持，配置与节点类型匹配。
- 边的起点和终点存在。
- 禁止自连接、重复方向边和环路。
- 输入节点没有入边，输出节点没有出边。
- 所有 Processor 至少需要一条入边且不限制上限；多个前驱输出的表 Map 先执行无覆盖合并。
- Processor 需要的一张或多张逻辑表由节点配置按表名选择，不使用物理入边数量表达操作数。
- Processor 允许没有出边；此时编译继续推导节点 Schema，并产生 `UNCONSUMED_PROCESSOR_OUTPUT` 警告。
- Input 仍至少需要一条出边；Output 仍恰好需要一条入边。
- 除输入节点外，每个节点至少有一条入边。
- Processor 的悬空结果允许保留在草稿或可执行定义中，但不会被任何写入或流式查询消费，也不会影响其他输出链路。
- 实时任务整张图仍至少需要一个可启动的 Output；没有任何 Output 时返回 `STREAMING_OUTPUT_REQUIRED`。

Task Engine 中的 Schema 校验按拓扑顺序执行：

1. `JDBC_INPUT` 按配置顺序为全部已选物理表读取最新元数据并分别生成同名有界表；`JDBC_QUERY_INPUT` 校验 SQL Hash，并从节点保存的字段快照生成以 `outputTableName` 为 Key 的有界表；`FILE_DATASET_INPUT` 从文件表元数据生成以稳定 code 为 Key 的有界表，`HTTP_API_INPUT` 从资源声明的输出 Schema 生成以 `outputTableName` 为 Key 的表，`MODEL_INPUT` 从模型快照生成以模型 code 为 Key 的表，`KAFKA_INPUT` 直接使用节点内联 Value Schema 生成无界表，`TDENGINE_TMQ_INPUT` 使用 TMQ Topic 元数据快照中的完整超级表字段生成无界表。
2. 节点接收所有直接上游的输出 Map，并执行无覆盖合并。
3. `JOIN` 检查左右表、条件和字段类型，追加结果表。
4. `RENAME` 替换选中表的 Map Key，并使用共享 Spark 投影原子生成新字段 Schema。
5. `FILTER` 按每项配置使用结构化条件 AST 或受控 SQL 布尔表达式筛选来源表，并按输出方式替换来源表或追加新表。
6. `SELECT_COLUMNS` 使用单次 Spark 投影按配置顺序裁剪字段，保留输入 Map 并追加新的输出表。
7. `DERIVE_COLUMNS` 基于原始来源 Schema 使用单次 Spark 投影新增或覆盖字段，保留输入 Map 并追加新的输出表。
8. `TYPE_CAST` 使用显式 Spark cast/try_cast 在原位置转换字段，保留输入 Map 并追加新的输出表。
9. `AGGREGATE` 使用 Spark 分组与聚合表达式重塑 Schema，保留输入 Map 并追加新的有界输出表。
10. `UNION` 按字段名重排并合并多张结构一致的表，显式推导有界性、事件时间和 Watermark。
11. `DEDUPLICATE` 使用 dropDuplicates 或 Window + row_number 删除重复行，保留来源 Schema。
12. `NULL_HANDLING` 按规则顺序使用显式 NULL 判断删除行或填充 NULL，并保留来源表的有界性。
13. `VALUE_MAPPING` 使用类型化 Column CASE 精确映射字段值；普通 NULL 始终保留，`ERROR` 只在真实执行遇到未匹配非 NULL 值时失败。
14. `WINDOW` 对有界来源使用显式分区、排序和 ROWS Frame 追加窗口字段。
15. `TOP_N` 对有界来源使用 limit、row_number 或 rank 选择全局/分组前 N 行。
16. `MASK_FIELDS` 使用节点内嵌规则定义原位替换配置字段，继承来源 Schema 与有界性，不查询全局规则。
17. `JSON_EXTRACT` 使用 Spark VARIANT JSON 解析和 Path 提取追加结构化字段，继承来源表有界性。
18. `SPATIAL_CLIP` 合并两个上游 Map，校验 BOUNDED、CRS、dimension、Mask kind、来源家族策略与多 Mask 组合方式，按逐 Mask 或逐来源融合 Mask 的语义追加只含来源属性与裁剪结果的有界表。
19. `SPATIAL_AGGREGATE` 对 BOUNDED 来源执行全局或分组空间聚合，追加清空事件时间和 Watermark 的有界结果表。
20. `JDBC_OUTPUT`、`MODEL_OUTPUT` 检查源表、目标及字段映射；`KAFKA_OUTPUT` 检查来源、格式、Value 字段和可选 Key，但均不执行真实写入。
21. 每个节点返回独立校验摘要；图级错误放入单独的 `canvasIssues`，不制造 `@canvas` 伪节点。

建议稳定错误码：

| 错误码 | 含义 |
| --- | --- |
| `UNSUPPORTED_SCHEMA_VERSION` | Canvas 协议版本不受支持 |
| `DUPLICATE_NODE_ID` | 节点 ID 重复 |
| `DUPLICATE_EDGE_ID` | 边 ID 重复 |
| `EDGE_ENDPOINT_NOT_FOUND` | 边引用不存在的节点 |
| `INVALID_NODE_DEGREE` | 节点入边或出边数量不符合类型规则 |
| `UNCONSUMED_PROCESSOR_OUTPUT` | Processor 结果没有下游消费者，不会产生写入或流式查询，仅作为警告 |
| `CANVAS_CYCLE` | 画布存在环路 |
| `DUPLICATE_TABLE_NAME` | 上游 Map 或处理结果出现同名表 |
| `FILE_DATASET_TABLE_ID_REQUIRED` | 文件数据集表 ID 缺失或非法 |
| `FILE_DATASET_TABLE_NOT_FOUND` | 文件数据集表不存在 |
| `FILE_DATASET_TABLE_NOT_READY` | 文件数据集表未处于 `READY` |
| `FILE_DATASET_FILE_NOT_READY` | 来源文件未处于 `READY` |
| `FILE_DATASET_FORMAT_NOT_SUPPORTED` | Batch Reader 不支持该文件格式 |
| `FILE_DATASET_SCHEMA_EMPTY` | 文件表 Schema 为空 |
| `FILE_DATASET_SCHEMA_UNSUPPORTED` | 文件表 Schema 无法转换为 Spark Schema |
| `TABLE_NOT_FOUND` | 配置引用的逻辑表或物理表不存在 |
| `COLUMN_NOT_FOUND` | 配置引用的字段不存在 |
| `DUPLICATE_COLUMN_NAME` | 处理结果包含同名字段 |
| `JOIN_OUTPUT_COLUMNS_REQUIRED` | Join 或 Stream Join 未配置任何可输出字段 |
| `DUPLICATE_JOIN_OUTPUT_SOURCE` | Join 或 Stream Join 对同一侧来源字段配置了多次 |
| `DUPLICATE_RENAME_SOURCE_COLUMN` | Rename 对同一来源字段配置了多次 |
| `RENAME_HAS_NO_EFFECT` | Rename 的表名和字段名均未发生变化，仅作为警告 |
| `REDUNDANT_RENAME_MAPPING` | Rename 字段映射前后名称相同，仅作为警告 |
| `INVALID_FILTER_CONDITION` | Filter 条件 AST 结构、深度或节点数量无效 |
| `EMPTY_FILTER_GROUP` | Filter 条件组没有子条件 |
| `INVALID_FILTER_OPERATOR` | Filter 操作符无效 |
| `INVALID_FILTER_OPERAND_COUNT` | Filter 操作符与 Literal 数量不匹配 |
| `INVALID_FILTER_LITERAL` | Filter Literal 类型或稳定字符串格式无效 |
| `INVALID_FILTER_SQL_EXPRESSION` | Filter SQL 表达式包含禁用结构，或无法由 Spark 解析为当前来源表上的布尔谓词 |
| `EMPTY_COLUMN_SELECTION` | Select Columns 没有选择任何字段 |
| `DUPLICATE_SELECTED_COLUMN` | Select Columns 重复选择同一字段 |
| `EMPTY_DERIVATIONS` | Derive Columns 没有配置派生字段 |
| `DUPLICATE_DERIVATION_TARGET` | 同一派生目标字段配置多次 |
| `INVALID_DERIVATION_EXPRESSION` | 派生表达式 AST 结构或安全限制无效 |
| `UNSUPPORTED_EXPRESSION_FUNCTION` | 表达式函数不在稳定白名单中 |
| `INVALID_FUNCTION_ARGUMENTS` | 函数参数数量或必需结构无效 |
| `STREAM_EVENT_TIME_COLUMN_IMMUTABLE` | 流模式尝试覆盖事件时间字段 |
| `EMPTY_TYPE_CASTS` | Type Cast 没有配置任何字段转换 |
| `DUPLICATE_CAST_COLUMN` | 同一字段重复配置类型转换 |
| `INVALID_TARGET_PLATFORM_TYPE` | 目标平台类型或参数无效 |
| `INVALID_CAST_FAILURE_STRATEGY` | 类型转换失败策略无效 |
| `EMPTY_AGGREGATIONS` | Aggregate 没有配置聚合项 |
| `DUPLICATE_GROUP_BY_COLUMN` | Aggregate 分组字段重复 |
| `DUPLICATE_AGGREGATE_OUTPUT_COLUMN` | Aggregate 聚合输出字段名重复 |
| `AGGREGATE_OUTPUT_COLUMN_CONFLICT` | 聚合输出字段与分组字段同名 |
| `INVALID_AGGREGATE_FUNCTION` | 聚合函数无效 |
| `AGGREGATE_SOURCE_COLUMN_REQUIRED` | 聚合函数缺少必需的来源字段 |
| `INVALID_COUNT_STAR_CONFIGURATION` | COUNT(*) 与 DISTINCT 或其他函数形成非法组合 |
| `AGGREGATE_DISTINCT_NOT_SUPPORTED` | 当前聚合函数不支持 DISTINCT |
| `UNION_REQUIRES_MULTIPLE_TABLES` | Union 选择的输入表少于两张 |
| `DUPLICATE_UNION_INPUT_TABLE` | Union 输入表重复 |
| `INVALID_UNION_MODE` | Union 模式无效 |
| `UNION_SCHEMA_MISMATCH` | Union 输入字段名集合不一致 |
| `UNION_MIXED_DATASET_KIND` | Union 混合了有界与无界输入 |
| `UNION_EVENT_TIME_MISMATCH` | 无界 Union 输入事件时间字段不一致 |
| `UNION_WATERMARK_MISMATCH` | 无界 Union 输入 Watermark 不一致 |
| `STREAMING_UNION_DISTINCT_NOT_SUPPORTED` | 无界 Union 使用 DISTINCT |
| `INVALID_DEDUPLICATE_KEEP_STRATEGY` | 去重保留策略无效 |
| `DUPLICATE_DEDUPLICATE_KEY_COLUMN` | 去重键字段重复 |
| `DUPLICATE_DEDUPLICATE_SORT_COLUMN` | 去重排序字段重复 |
| `DEDUPLICATE_KEYS_REQUIRED` | FIRST/LAST 未配置去重键 |
| `DEDUPLICATE_ORDER_REQUIRED` | FIRST/LAST 未配置排序规则 |
| `DEDUPLICATE_ORDER_NOT_ALLOWED` | ANY 配置了排序规则 |
| `FULL_ROW_DEDUPLICATE_REQUIRES_ANY` | 全字段去重使用了非 ANY 策略 |
| `EMPTY_NULL_HANDLING_RULES` | Null Handling 没有配置任何规则 |
| `NULL_HANDLING_RULE_LIMIT_EXCEEDED` | Null Handling 规则超过 100 项 |
| `EMPTY_NULL_CHECK_COLUMNS` | 删除行规则没有选择检查字段 |
| `DUPLICATE_NULL_CHECK_COLUMN` | 同一删除行规则重复选择字段 |
| `INVALID_NULL_MATCH_MODE` | NULL 匹配模式无效 |
| `DUPLICATE_NULL_FILL_COLUMN` | 同一字段配置了多次固定值填充 |
| `NULL_FILL_VALUE_REQUIRED` | 固定值填充缺少非 NULL Literal |
| `INVALID_NULL_FILL_LITERAL` | 填充值格式无效 |
| `NULL_FILL_LITERAL_TYPE_MISMATCH` | 填充值类型与字段平台类型不一致 |
| `NULL_FILL_LITERAL_TYPE_NOT_SUPPORTED` | 字段类型不支持固定 Literal 填充 |
| `EMPTY_VALUE_MAPPING_RULES` | Value Mapping 没有配置字段规则 |
| `VALUE_MAPPING_RULE_LIMIT_EXCEEDED` | Value Mapping 字段规则超过 100 项 |
| `DUPLICATE_VALUE_MAPPING_COLUMN` | 同一字段配置了多条映射规则 |
| `EMPTY_VALUE_MAPPING_ENTRIES` | 某字段没有映射项 |
| `VALUE_MAPPING_ENTRY_LIMIT_EXCEEDED` | 单字段或节点映射项超过容量限制 |
| `DUPLICATE_VALUE_MAPPING_SOURCE` | 同一字段存在语义相同的重复源值 |
| `VALUE_MAPPING_SOURCE_REQUIRED` | 映射源值缺失或为 NULL |
| `INVALID_VALUE_MAPPING_LITERAL` | Value Mapping Literal 格式无效 |
| `VALUE_MAPPING_LITERAL_TYPE_MISMATCH` | Mapping Literal 类型与字段不一致 |
| `VALUE_MAPPING_TYPE_NOT_SUPPORTED` | 字段类型不支持内联值映射 |
| `INVALID_UNMATCHED_VALUE_STRATEGY` | 未匹配值策略无效 |
| `UNMATCHED_VALUE_REQUIRED` | SET_LITERAL 未配置默认值 |
| `UNMATCHED_VALUE_NOT_ALLOWED` | 非 SET_LITERAL 策略携带默认值 |
| `VALUE_MAPPING_UNMATCHED_VALUE` | ERROR 策略在运行时遇到未匹配非 NULL 值 |
| `DUPLICATE_WINDOW_PARTITION_COLUMN` | Window 分区字段重复 |
| `EMPTY_WINDOW_ORDER` | Window 没有排序字段 |
| `DUPLICATE_WINDOW_SORT_COLUMN` | Window 排序字段重复 |
| `EMPTY_WINDOW_FUNCTIONS` | Window 没有窗口函数 |
| `WINDOW_FUNCTION_LIMIT_EXCEEDED` | 窗口函数超过 100 项 |
| `DUPLICATE_WINDOW_OUTPUT_COLUMN` | 窗口输出字段名重复 |
| `WINDOW_OUTPUT_COLUMN_CONFLICT` | 窗口输出字段与来源字段同名 |
| `WINDOW_SOURCE_COLUMN_REQUIRED` | 窗口函数缺少必需来源字段 |
| `INVALID_WINDOW_COUNT_STAR` | COUNT(*) 配置组合无效 |
| `INVALID_WINDOW_OFFSET` | LAG/LEAD offset 越界 |
| `INVALID_WINDOW_DEFAULT_LITERAL` | LAG/LEAD 默认 Literal 无效 |
| `WINDOW_DEFAULT_LITERAL_TYPE_MISMATCH` | 窗口默认 Literal 与来源字段类型不一致 |
| `WINDOW_DEFAULT_LITERAL_TYPE_NOT_SUPPORTED` | 来源类型不支持非 NULL 窗口默认值 |
| `INVALID_WINDOW_FRAME` | ROWS Frame 类型、边界或顺序无效 |
| `INVALID_WINDOW_FRAME_OFFSET` | Frame offset 越界 |
| `WINDOW_REQUIRES_BOUNDED_INPUT` | Window 来源为 UNBOUNDED |
| `DUPLICATE_TOP_N_PARTITION_COLUMN` | Top N 分区字段重复 |
| `EMPTY_TOP_N_ORDER` | Top N 没有排序字段 |
| `DUPLICATE_TOP_N_SORT_COLUMN` | Top N 排序字段重复 |
| `INVALID_TOP_N_LIMIT` | N 超出允许范围 |
| `INVALID_TOP_N_TIE_STRATEGY` | Top N 并列策略无效 |
| `TOP_N_REQUIRES_BOUNDED_INPUT` | Top N 来源为 UNBOUNDED |
| `EMPTY_MASKING_RULES` | Mask Fields 没有配置字段规则 |
| `MASKING_RULE_LIMIT_EXCEEDED` | Mask Fields 字段规则超过 100 项 |
| `DUPLICATE_MASKING_FIELD` | 同一字段配置了多条脱敏规则 |
| `MASKING_RULE_SOURCE_REQUIRED` | 脱敏规则缺少 GLOBAL/INLINE 来源类型 |
| `MASKING_SOURCE_REFERENCE_REQUIRED` | GLOBAL 规则缺少来源引用 |
| `MASKING_SOURCE_REFERENCE_NOT_ALLOWED` | INLINE 规则保留了全局来源引用 |
| `INVALID_MASKING_SOURCE_RULE_ID` | 全局来源规则 ID 不是 UUID |
| `MASKING_STRATEGY_REQUIRED` | 脱敏规则缺少策略 |
| `MASKING_STRING_FIELD_REQUIRED` | STRING 专用策略配置到非 STRING 字段 |
| `MASKING_NULLIFY_REQUIRES_NULLABLE_FIELD` | NULLIFY 配置到不可空字段 |
| `INVALID_MASKING_CHARACTER` | 掩码字符不是一个 Unicode 字符 |
| `INVALID_MASKING_KEEP_LENGTH` | 保留字符数缺失或越界 |
| `MASKING_FIXED_VALUE_REQUIRED` | FIXED_VALUE 缺少固定替换值 |
| `MASKING_FIXED_VALUE_TOO_LONG` | 固定替换值超过 1024 个字符 |
| `MASKING_PARAMETER_NOT_ALLOWED` | 策略携带不适用的参数 |
| `EMPTY_JSON_EXTRACTIONS` | JSON Extract 没有配置提取项 |
| `JSON_EXTRACTION_LIMIT_EXCEEDED` | JSON Extract 提取项超过 100 项 |
| `JSON_SOURCE_COLUMN_TYPE_MISMATCH` | JSON 来源字段不是 STRING |
| `INVALID_JSON_PATH` | JSON Path 过长或未以 `$` 开头 |
| `DUPLICATE_JSON_OUTPUT_COLUMN` | 提取输出字段与来源字段或其他提取项重名 |
| `INVALID_JSON_FAILURE_STRATEGY` | JSON 解析失败策略无效 |
| `INVALID_SORT_DIRECTION` | 排序方向无效 |
| `INVALID_NULL_ORDERING` | NULL 排序位置无效 |
| `SPARK_ANALYSIS_ERROR` | Spark Analyzer 无法建立 Processor 计算计划 |
| `COLUMN_CAST_RISK` | Spark 支持 Output Cast，但实际值可能转换失败 |
| `NULLABILITY_RISK` | nullable 来源可能无法写入 non-null 目标 |
| `STRING_LENGTH_RISK` | 来源字符串可能超过目标长度 |
| `DECIMAL_PRECISION_RISK` | Decimal 写入目标时可能溢出或舍入 |
| `UNSUPPORTED_COLUMN_CAST` | Spark Analyzer 不支持 Output 字段转换 |
| `DATA_SOURCE_UNAVAILABLE` | 数据源不存在、停用、类型或用途不匹配 |
| `JDBC_QUERY_NOT_READ_ONLY` | JDBC Query SQL 不是单条只读 SELECT/CTE |
| `JDBC_QUERY_SCHEMA_REQUIRED` | JDBC Query 缺少已分析字段快照 |
| `JDBC_QUERY_SCHEMA_STALE` | 当前 SQL Hash 与已分析 Hash 不一致 |
| `JDBC_QUERY_GEOMETRY_NOT_SUPPORTED` | JDBC Query 首版不支持 Geometry 结果字段 |
| `UPSERT_KEY_REQUIRED` | UPSERT 未选择完整唯一键 |
| `UPSERT_KEY_NOT_UNIQUE_CONSTRAINT` | UPSERT Key 与目标表安全唯一键不匹配 |
| `UPSERT_KEY_NOT_MAPPED` | UPSERT Key 未全部进入目标字段映射 |
| `UPSERT_KEY_NULL` | 当前批次存在 NULL UPSERT Key |
| `UPSERT_DUPLICATE_KEY` | 当前批次内存在重复 UPSERT Key |
| `UPSERT_KEY_COLUMN_NOT_ALLOWED` | UPSERT Key 字段不存在、自动生成或为 Geometry |
| `STREAMING_MODEL_OUTPUT_OVERWRITE_NOT_SUPPORTED` | 实时模型输出不支持 OVERWRITE |
| `MYSQL_UPSERT_MULTIPLE_UNIQUE_KEYS` | MySQL 目标存在多组唯一键，原生冲突范围更宽（Warning） |
| `API_RESOURCE_NOT_FOUND` | HTTP API 输入引用的资源不存在或不属于该数据源 |
| `KAFKA_VALUE_SCHEMA_REQUIRED` | Kafka 节点缺少内联 Value Schema |
| `KAFKA_VALUE_SCHEMA_EMPTY` | Kafka Value Schema 没有字段 |
| `KAFKA_VALUE_SCHEMA_INVALID` | Kafka Value Schema 字段或类型参数无效 |
| `KAFKA_VALUE_SCHEMA_NOT_APPLICABLE` | TEXT/BINARY 格式错误地携带结构化 Value Schema |
| `KAFKA_METADATA_FIELD_INVALID` | Kafka 元数据字段为空或无效 |
| `KAFKA_METADATA_FIELD_DUPLICATE` | Kafka 元数据字段重复配置 |

## 12. 稳定协议与运行时边界

前端应使用以 `type` 为判别字段的联合类型，不得继续使用 `Record<string, unknown>`：

```ts
type CanvasNodeDefinition =
  | CanvasNodeBase<'MODEL_INPUT', ModelInputConfiguration>
  | CanvasNodeBase<'JDBC_INPUT', JdbcInputConfiguration>
  | CanvasNodeBase<'JDBC_QUERY_INPUT', JdbcQueryInputConfiguration>
  | CanvasNodeBase<'FILE_DATASET_INPUT', FileDatasetInputConfiguration>
  | CanvasNodeBase<'HTTP_API_INPUT', HttpApiInputConfiguration>
  | CanvasNodeBase<'KAFKA_INPUT', KafkaInputConfiguration>
  | CanvasNodeBase<'TDENGINE_TMQ_INPUT', TdEngineTmqInputConfiguration>
  | CanvasNodeBase<'JOIN', JoinConfiguration>
  | CanvasNodeBase<'STREAM_JOIN', StreamJoinConfiguration>
  | CanvasNodeBase<'RENAME', RenameConfiguration>
  | CanvasNodeBase<'FILTER', FilterConfiguration>
  | CanvasNodeBase<'SELECT_COLUMNS', SelectColumnsConfiguration>
  | CanvasNodeBase<'DERIVE_COLUMNS', DeriveColumnsConfiguration>
  | CanvasNodeBase<'TYPE_CAST', TypeCastConfiguration>
  | CanvasNodeBase<'AGGREGATE', AggregateConfiguration>
  | CanvasNodeBase<'UNION', UnionConfiguration>
  | CanvasNodeBase<'DEDUPLICATE', DeduplicateConfiguration>
  | CanvasNodeBase<'NULL_HANDLING', NullHandlingConfiguration>
  | CanvasNodeBase<'VALUE_MAPPING', ValueMappingConfiguration>
  | CanvasNodeBase<'MASK_FIELDS', MaskFieldsConfiguration>
  | CanvasNodeBase<'JSON_EXTRACT', JsonExtractConfiguration>
  | CanvasNodeBase<'WINDOW', WindowConfiguration>
  | CanvasNodeBase<'TOP_N', TopNConfiguration>
  | CanvasNodeBase<'MODEL_OUTPUT', ModelOutputConfiguration>
  | CanvasNodeBase<'JDBC_OUTPUT', JdbcOutputConfiguration>
  | CanvasNodeBase<'JDBC_SNAPSHOT_SYNC_OUTPUT', JdbcSnapshotSyncOutputConfiguration>
  | CanvasNodeBase<'MODEL_SNAPSHOT_SYNC_OUTPUT', ModelSnapshotSyncOutputConfiguration>
  | CanvasNodeBase<'KAFKA_OUTPUT', KafkaOutputConfiguration>;
```

未来后端接入时也应使用明确的 Request、Response 和节点配置类型。不得把 `Map<String, Object>`、JPA Entity、X6 JSON 或未来执行引擎对象作为 Canvas REST 契约。

前端节点注册表只维护展示和编辑能力：

- 节点类别
- 按强类型配置派生的语义基础尺寸和专属只读 Body
- X6 Shape、专属 SVG 图标与配置面板
- 入边和出边规则
- 配置 DTO 类型

结构校验器、Schema 处理器和 `inputTables/outputTables` 传播只存在于 Task Engine，不在前端维护第二份实现。

注册表不得决定运行引擎类型；Canvas 定义协议保持执行引擎中立。

## 13. 当前生命周期与执行边界

任务管理支持创建 `SPARK_CANVAS` 任务，并使用 `task_canvas_definition`、明确的节点判别联合和独立 Service 保存稳定定义。统一定义路由 `/task/{taskId}/definition` 先读取任务类型，再进入本地 SQL 或 Canvas 编辑器；任务类型创建后不可修改。

Canvas 定义接口为：

- `GET /api/v1/tasks/{id}/canvas-definition`
- `GET /api/v1/tasks/{id}/model-relations`
- `POST /api/v1/tasks/{id}/actions/update-canvas-definition`

定义查询响应使用 `loadStatus: UNCONFIGURED | LOADED | INCOMPATIBLE` 明确区分未配置、已加载和协议不兼容。响应同时返回持久化定义的 `schemaVersion/schemaMinorVersion`；`INCOMPATIBLE` 时保留任务定义版本和更新时间，但 `definition=null`，保证后端不会为了展示错误而反序列化旧大版本 JSON。发布、启用、批运行和实时启动读取到不兼容定义时统一返回 `409 Conflict`，要求先重新配置。

保存新定义时，后端只拒绝无法安全加载的协议结构，例如版本不支持、未知节点类型、重复 ID、非法布局和缺失边端点。节点尚未配置、图度数不满足、环路或字段冲突等业务无效草稿允许保存，并继续由 Task Engine 展示设计期问题。相同规范化定义重复保存不增加定义版本。

`model-relations` 只读取最后保存定义同步维护的 `task_canvas_model_reference`，按模型 UUID 聚合 `MODEL_INPUT/MODEL_OUTPUT/MODEL_SNAPSHOT_SYNC_OUTPUT` 的 `INPUT/OUTPUT` 角色，并从 Canvas 定义 JSON 按节点 ID 补充节点名称。JDBC、Kafka、文件和 HTTP 节点不进入模型关系。关系索引与 Canvas 定义在同一事务替换，保存失败时一起回滚；未保存的前端修改、运行历史和临时表不进入查询。模型侧的 `/api/v1/models/{id}/related-tasks` 使用相同事实来源反查并按任务去重。

独立 `/task/orchestration` 页面仍不读取任务 ID，页面状态只存在内存中，刷新即清空；用户通过 JSON 复制、下载或导入转移定义。

设计器通过 Admin 的 `/api/v1/task-compilations` 和取消 Action 间接调用 Task Engine，浏览器不访问 Engine 地址或 Token。节点、节点配置、连线和元数据快照发生语义变化后等待 400ms 自动编译；节点位置、选中状态、画布平移和缩放不触发。新版本会中止旧 Admin 请求并最佳努力取消 Engine 请求，只接受 requestId 和当前语义指纹都匹配的结果。

Task Engine 是节点卡片、配置抽屉、定义预览以及 `inputTables/outputTables` 的唯一校验来源。定义发生语义变化时立即丢弃旧 Engine 结果，并从等待元数据开始遮罩 Canvas，直到当前版本响应完成；节点位置和视图变化不触发遮罩。网络、容量或超时故障只表示 Engine 未完成校验，不把任务定义标记为业务无效，用户可在故障解除后手动重新校验。

Task Engine 预编译不负责设计器保存；Admin 管理面的 Canvas Definition Service 也不复制 Engine 的 Schema 传播和业务校验。接口细节见 [Task Engine Daemon 与 Canvas 编译设计](task-engine-daemon-and-compilation.md)。

`SPARK_CANVAS` 与 `LOCAL_SQL` 共用 `DRAFT → PUBLISHED → DISABLED → PUBLISHED` 生命周期以及任务详情页操作入口。发布和重新启用时，Admin 必须重新读取权威数据源元数据、要求绑定计算引擎存在有效执行路由，并通过 Task Engine 最终编译；发布预检不读取或写入业务数据。已发布任务允许通过统一的立即运行 Action 提交真实 Spark 任务，执行链路、快照、状态、取消和制品边界见 [Canvas 真实执行设计](canvas-task-execution.md)。

Canvas 正式执行只有 `Admin Outbox → Kafka → Dispatcher → Runner` 一条生产路径，不提供灰度模式、产品功能开关或回退到旧 HTTP 执行的旁路。测试 Profile 中的 Listener 替身仅用于隔离测试，不属于运行时功能开关。

`SPARK_CANVAS` 与 `LOCAL_SQL` 共用定时计划管理接口。Quartz 触发批处理 Canvas 时进入与手动运行相同的 Manifest、Outbox、Dispatcher 和 Runner 真实执行链路；`ALLOW` 按每个计划触发点创建独立实例，不做输出模型或物理 Sink 冲突治理。`SPARK_STREAMING_CANVAS` 是持续运行任务，继续不接受 Cron。自动重试、补数和跨多个 `JDBC_OUTPUT` 的原子事务不在当前范围内。

## 14A. `SQL_TRANSFORM` 处理器（Canvas 4.1）

```json
{
  "outputTableName": "order_summary",
  "sql": "WITH paid AS (SELECT `customer_id`, `amount` FROM `orders`) SELECT `customer_id`, sum(`amount`) AS `total_amount` FROM paid GROUP BY `customer_id`"
}
```

- `outputTableName` 和 `sql` 默认均为空字符串，允许保存未完成草稿；SQL 最大长度为
  `100000` 个字符。完整编译时两者必须非空。
- 节点类别为 `PROCESSOR`，仅支持 `BATCH`，至少一条入边；允许没有出边并返回
  `UNCONSUMED_PROCESSOR_OUTPUT` 警告。
- 节点接收直接上游传播的完整有序表 Map。执行成功后保留全部输入表，并在末尾追加
  `outputTableName` 对应的新 `BOUNDED` 表；输出名与现有表名冲突时返回
  `DUPLICATE_TABLE_NAME`，不能更新或替换已有 Canvas 表。
- 只接受单个 Spark SQL `SELECT` 或 `WITH ... SELECT`。SQL 表引用只能是当前输入 Map 的表 code
  或同一查询的 CTE。表 code、字段 code 大小写敏感；含点号、空格或其他特殊字符时必须使用反引号，
  反引号自身用两个反引号转义。
- DDL、DML、命令、SQL Script/多语句、显式视图操作、外部 Catalog、多段物理表、文件/JDBC
  Relation 和 Table-Valued Function 都返回稳定错误，且错误、日志、血缘和运行摘要不回显 SQL 正文或
  Literal。
- Compiler 与 Runner 都为每个 SQL 节点创建独立 Spark 子 Session。每张输入 Dataset 的已分析计划
  重新绑定后注册为本地临时视图，结果分析完毕后再绑定回节点执行 Session，并在 `finally` 清理视图；
  子 Session 不会停止共享 SparkContext。
- 输出 Schema 只使用 Spark Analyzer 结果。字段名必须非空且唯一，类型必须能映射到平台稳定类型；
  直接投影尽量保留字段描述和类型元数据。计算后缺少稳定 Kind/CRS 元数据的 Geometry 字段拒绝，复杂
  空间派生继续使用专属空间 Processor。

设计器 Inspector 只显示输出表、SQL 配置状态、引用表数量和“配置 SQL”入口。配置 Modal 左侧为可搜索的
上游表/字段树，点击表插入 `` `tableCode` ``，点击字段插入 `` `tableCode`.`columnCode` ``；右侧使用
Monaco SQL 编辑器。卡片不展示 SQL 片段或 Literal，只展示输出表、引用表数与编译得到的字段数。

## 14B. 简单 Processor 的多表 Operation（Canvas 4.0）

`RENAME`、`FILTER`、`SELECT_COLUMNS`、`DERIVE_COLUMNS`、`TYPE_CAST`、`DEDUPLICATE`、
`NULL_HANDLING`、`VALUE_MAPPING`、`MASK_FIELDS`、`JSON_EXTRACT` 和 `TOP_N` 使用
`operations` 数组配置多张来源表。每个 Operation 持有稳定 UUID、来源逻辑表、节点专属规则和：

- `REPLACE_SOURCE`：以处理结果替换同名来源表；只有 `RENAME` 可额外提供新逻辑表名。
- `CREATE_NEW_TABLE`：保留来源表并追加指定的新逻辑表。

同一来源表在节点中只能出现一次；所有 Operation 都从节点入口 Map 读取，不能引用同节点新产生的表。
任一 Operation 编译失败时节点整体无效。未处理的表按原顺序传播，覆盖结果保留来源位置，新表按
Operation 顺序追加。空数组是可保存草稿，但编译返回 `EMPTY_PROCESSOR_OPERATIONS`。

## 14. `RENAME` 处理器

### 14.1 配置

```json
{
  "sourceTableName": "orders",
  "outputTableName": "source_orders",
  "columnMappings": [
    {
      "sourceColumnName": "id",
      "targetColumnName": "order_id"
    }
  ]
}
```

- 节点类别为 `PROCESSOR`，至少一条入边；允许没有出边，悬空结果仅产生警告。
- 一个节点只处理输入 Map 中的一张逻辑表；其他表按原顺序透传。
- `outputTableName` 始终必填。只改字段名时填写与 `sourceTableName` 相同的值；只改表名时 `columnMappings` 为空。
- 不继承旧系统的多 Action 或表别名结构。

### 14.2 原子执行和 Map 语义

Operator 先根据原始 Schema 一次性计算所有最终字段名，再使用单次 Spark `select + alias` 建立新 Dataset。不得顺序调用 `withColumnRenamed`，避免字段交换和链式配置受执行顺序影响。

输出 Map 在原表所在位置用新 Key 替换旧 Key；输入 Map、上游 `SparkCanvasTable`、Dataset 和 Schema 均不得修改。新的 `CanvasTableSchema.name` 与 Map Key 一致，`CanvasTableOrigin` 以及字段类型、长度、精度、Scale、nullable、默认值、自动生成信息和注释全部保留。

当 `outputTableName` 与另一张输入表冲突时返回 `DUPLICATE_TABLE_NAME`；最终字段名冲突时返回 `DUPLICATE_COLUMN_NAME`；同一来源字段配置多次返回 `DUPLICATE_RENAME_SOURCE_COLUMN`。字段映射前后相同或整个节点无效果仍是可执行计划，只产生 WARNING。

Rename 必须放在同名表分支合并之前；上游 Map 已经发生名称冲突时，Compiler 会在 Rename 执行前返回错误。`RENAME` 是解决名称冲突的唯一显式节点，`JOIN`、Input 和 Map 合并逻辑不得自动添加前缀。

## 15. `FILE_DATASET_INPUT` 节点

### 15.1 稳定配置

```json
{
  "id": "c239e3ae-6ad5-430f-b66f-ec1c09124809",
  "type": "FILE_DATASET_INPUT",
  "name": "订单文件输入",
  "layout": { "x": 120, "y": 160, "width": 240, "height": 120 },
  "configuration": {
    "fileDatasetId": "4bbd56c6-5c4f-4af7-8860-5adecc1c29bd",
    "tables": [
      { "fileDatasetTableId": "bd6c3996-5e96-4714-ae65-f35b015f3cd1" },
      { "fileDatasetTableId": "c239e3ae-6ad5-430f-b66f-ec1c09124809" }
    ]
  }
}
```

配置固定绑定一个 `fileDatasetId`，并保存有序 `tables` 选择。每个 `fileDatasetTableId` 必须属于该数据集；同一表不得重复。文件 ID、格式、路径、解析参数、Schema、输出表名和存储凭据均由权威元数据及运行 Manifest 提供，不得进入定义。每张输出逻辑表名固定为不可修改的 `FileDatasetTable.code`。

### 15.2 图与编译语义

- 类别为 `INPUT`，入边必须为 0，出边至少为 1。
- 仅支持 `BATCH`，输出 `BOUNDED` Dataset；流任务可以导入回显，但 Compiler 返回 `NODE_EXECUTION_MODE_NOT_SUPPORTED`。
- 表、来源文件和 Schema 必须存在，且表和文件都处于 `READY`。
- Compiler 只使用 `metadataSnapshot.fileDatasetTables` 创建显式 Schema 的零行 Dataset，不访问对象存储，也不重新推断 Schema。
- 输出 Map 以配置顺序包含每张表的 code，来源用 `fileDatasetTableId` 明确标识；任一资源无效或任意 code 与其他上游同名时节点整体返回错误，不传播部分结果。

### 15.3 设计器

Batch Palette 展示“文件数据集输入”，Streaming Palette 隐藏。Inspector 先选择文件数据集，再通过双栏面板批量选择 `READY/SCHEMA_READY` 逻辑表；已选项完整保留、可排序、查看字段和删除。已经保存的表失效、删除或变为不可用时保留原 UUID，等待 Compiler 展示权威错误，不静默清空配置。

## 16. Kafka Value 格式与内联 Schema

### 16.1 稳定配置

`KAFKA_INPUT` 示例：

```json
{
  "id": "66d666a9-4c45-4fe0-b9bb-66425c2d035e",
  "type": "KAFKA_INPUT",
  "name": "订单事件输入",
  "layout": { "x": 120, "y": 160, "width": 240, "height": 120 },
  "configuration": {
    "dataSourceId": "bd6c3996-5e96-4714-ae65-f35b015f3cd1",
    "topic": "order-events",
    "valueSchema": {
      "columns": [
        {
          "name": "event_id",
          "fieldType": "LONG",
          "length": null,
          "precision": null,
          "scale": null,
          "nullable": false,
          "comment": "事件 ID"
        }
      ]
    },
    "valueFormat": "JSON",
    "metadataFields": ["KEY", "TOPIC", "PARTITION", "OFFSET", "TIMESTAMP"],
    "outputTableName": "order_events",
    "startingOffsets": "LATEST",
    "triggerIntervalSeconds": 10
  }
}
```

`KAFKA_INPUT.valueFormat` 只允许 `JSON/TEXT/BINARY`。JSON 使用非空 `valueSchema`；TEXT 固定输出 `value: STRING NULL`，BINARY 固定输出 `value: BINARY NULL`，后两者的正式 `valueSchema.columns` 必须为空。TEXT 固定使用 Spark UTF-8 解码，不自动识别字符集；BINARY 保留原始字节。

`metadataFields` 可选择 `KEY/TOPIC/PARTITION/OFFSET/TIMESTAMP`，按固定顺序追加为 `_kafka_key`、`_kafka_topic`、`_kafka_partition`、`_kafka_offset`、`_kafka_timestamp`，类型依次为 BINARY、STRING、INTEGER、LONG、TIMESTAMP，均使用保守的 nullable 声明。Key 不做文本解码；Timestamp 是普通字段，不自动成为事件时间或附加 Watermark。Value 字段与已选元数据字段重名时编译失败。Kafka tombstone 不被过滤，Value 解析结果为空但元数据继续输出。

Canvas 4.6 新建的 `KAFKA_OUTPUT` 写入使用 `valueFormat + valueColumnNames`：JSON 至少选择一个上游字段并按上游 Schema 顺序组成对象；TEXT 必须且只能选择一个 STRING 字段并按 UTF-8 原样发送；BINARY 必须且只能选择一个 BINARY 字段并原样发送字节。TEXT/BINARY 的 NULL Value 产生 Kafka tombstone。可选 Key 只允许 STRING 或 BINARY；是否同时进入 JSON Value 由字段选择决定。JSON Array、XML、CSV 和自定义文本应由前置 Processor 生成 STRING 后使用 TEXT；Avro、Protobuf、压缩或加密载荷应由前置 Processor 生成 BINARY 后使用 BINARY。Kafka Output 不负责 Schema Registry 或业务编码。

Canvas 4.0～4.5 的写入以 `valueFormat: null` 识别，继续使用 `valueSchema + columnMappings` 的旧版 JSON 映射和显式 Cast。4.6 可以读取并原样保存这种兼容模式；新建写入不再创建旧模式，也不会自动迁移旧图结构。

配置中不再存在 `valueModelId`。Kafka 节点不是模型节点，不创建任务模型引用，也不会在发布、编译或运行准备时查询模型。没有历史 Kafka 定义需要迁移，因此 `1.4` 及更低版本携带 Kafka 节点时直接拒绝，不保留旧字段兼容分支。

### 16.2 设计器与编译语义

- Inspector 支持选择 JSON、TEXT 或 BINARY；新节点默认 JSON 并选择全部五个元数据字段，4.0～4.3 旧节点按 JSON 且无元数据兼容读取。
- JSON 支持手工增删、排序和编辑字段；TEXT/BINARY 只读展示固定 `value` 字段。
- “从模型 Schema 导入”只读取一次当前已发布模型字段并复制成内联列；选择结果和模型 ID 不进入定义。
- “粘贴 JSON Schema”第一阶段只接受根类型为 object 的扁平 properties，支持 boolean、integer、number、string、date 和 date-time；嵌套 object、array 明确拒绝。
- `KAFKA_INPUT` Operator 根据 Value 格式和元数据选择生成 Spark Schema，并输出 `UNBOUNDED` 表；Compiler 和 Runner 不需要模型元数据。JSON 继续使用 `from_json(..., FAILFAST)`，TEXT/BINARY 不进行结构化字段解析。
- `KAFKA_OUTPUT` 新模式直接使用上游字段：JSON 由已选字段组成对象，TEXT/BINARY 直接输出单字段；旧版模式继续使用内联目标 Schema、映射和显式 Spark Cast。
- Schema 缺失、空字段、重复字段、非法类型参数属于 Compiler `ERROR`；模型是否仍存在、是否变更与节点有效性无关。

## 16A. `TDENGINE_TMQ_INPUT`

该节点只支持 `STREAMING`；事件时间与 Watermark 配置从 Canvas `4.5` 提供：

```json
{
  "dataSourceId": "bd6c3996-5e96-4714-ae65-f35b015f3cd1",
  "topicName": "meters_topic",
  "catalogName": "power",
  "supertableName": "meters",
  "topicDefinitionFingerprint": "v2:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
  "outputTableName": "meter_events",
  "startingOffsets": "EARLIEST",
  "maxOffsetsPerVGroupPerTrigger": 10000,
  "triggerIntervalSeconds": 10,
  "eventTimeColumn": "ts",
  "watermarkDelaySeconds": 60
}
```

- 只能引用已启用、具有 `SOURCE` 用途和 `TMQ_SUBSCRIBE` 能力的 `TDENGINE_WEBSOCKET` 数据源。
- Topic 必须是单一超级表、完整 `*` 投影的纯数据 Topic；RESTful、数据库 Topic、子表、投影、过滤、
  JOIN、聚合和 `WITH META` 均不支持。
- Topic 选择时保存数据库、超级表和 SHA-256 定义指纹；保存草稿不连接 TDengine，发布、重新启用和
  运行准备时重新校验 Topic、指纹和最新超级表 Schema。
- `startingOffsets` 只接受 `EARLIEST/LATEST`，且只在没有 Spark Checkpoint 的新部署生效。
- `maxOffsetsPerVGroupPerTrigger` 默认 `10000`，范围 `1..1000000`，表示 TMQ 消息块 Offset 跨度，
  不承诺等于行数。
- `triggerIntervalSeconds` 默认 `10`，范围 `1..300`；实时任务的触发间隔只从唯一无界输入节点读取。
- `eventTimeColumn` 与 `watermarkDelaySeconds` 必须同时留空或同时配置；事件时间字段必须是
  `TIMESTAMP`，Watermark 延迟范围为 `1..2592000` 秒。平台不自动猜测事件时间。
- 节点入边为 0、出边至少 1，产生以 `outputTableName` 为 Key 的 `UNBOUNDED` 表；Schema 来自超级表
  字段与 TAG，不增加 Topic、子表、VGroup 或 Offset 技术列。
- Consumer Group、Client ID、自动提交和任意 TMQ Properties 均不是用户配置；完整运行及 Offset
  语义见 [TDengine TMQ 输入](tdengine-tmq-input-development-plan.md)。

## 16B. `JDBC_INCREMENTAL_INPUT`

该节点在 Canvas `3.0` 中提供，只支持 `STREAMING`。它按固定间隔读取普通 JDBC 物理表的完整时间窗口：

```sql
WHERE incremental_time > :fromTime
  AND incremental_time <= :toTime
```

`toTime` 为源数据库当前时间减去可见性延迟。配置包含 `dataSourceId`、`tableName`、
`outputTableName`、`incrementalTimeColumn`、`startPosition: LATEST | EARLIEST | AT_TIME`、
可选 `startTime`、`cursorTimeZone`、`visibilityDelaySeconds` 和 `triggerIntervalSeconds`。
第一版只支持 PostgreSQL、MySQL、openGauss 和 Kingbase 的普通表，以及非空
`TIMESTAMP/TIMESTAMP_NTZ` 增量字段；不支持视图、TDengine 超级表、Geometry、删除捕获、分页、
行数截断、自定义增量 SQL 或复合游标。节点输出 `UNBOUNDED` 表，但不会自动设置事件时间或 Watermark。

实时定义必须且只能有一个 Kafka、TMQ 或 JDBC 增量无界输入。JDBC 增量输入第一版只允许连接一个
终端输出，避免多个 Spark Streaming Query 重复轮询源表。Spark Checkpoint 保存版本化时间 Offset，
提供至少一次语义；同版本优先使用 Checkpoint，跨定义版本仅在来源签名一致时使用管理库的最近提交
Offset 作为首次恢复位置。一个微批读取完整窗口，不使用 `LIMIT`、分页或 `ORDER BY`。

## 17. `FILTER` 处理器

### 17.1 稳定配置

```json
{
  "operations": [
    {
      "operationId": "0e49ab0d-238a-4212-a560-230d089941eb",
      "sourceTableName": "orders",
      "output": { "mode": "REPLACE_SOURCE", "outputTableName": null },
      "mode": "SQL_EXPRESSION",
      "condition": { "kind": "GROUP", "operator": "AND", "children": [] },
      "sqlExpression": "amount >= 100 AND status IN ('PAID', 'SHIPPED')"
    }
  ]
}
```

`mode` 固定为 `STRUCTURED` 或 `SQL_EXPRESSION`。`condition` 与 `sqlExpression` 同时保存，以便切换模式时保留未激活的编辑草稿；只有当前模式参与编译和执行。旧 4.0 定义缺失 `mode/sqlExpression` 时规范化为 `STRUCTURED` 和空字符串。

结构化条件是以 `kind` 为判别字段的递归联合：

- `GROUP`：`operator` 为 `AND/OR`，`children` 至少一项。
- `PREDICATE`：包含 `columnName`、操作符和 `values`。
- 操作符固定为 `EQUALS`、`NOT_EQUALS`、`GREATER_THAN`、`GREATER_THAN_OR_EQUALS`、`LESS_THAN`、`LESS_THAN_OR_EQUALS`、`IN`、`NOT_IN`、`IS_NULL`、`IS_NOT_NULL`、`CONTAINS`、`STARTS_WITH`、`ENDS_WITH`。
- Literal 保存 `PlatformDataType + string/null value`。Boolean 使用 `true/false`，日期使用 `yyyy-MM-dd`，Timestamp 使用带时区的 ISO-8601，Timestamp NTZ 使用无时区 ISO-8601，Binary 使用 Base64。
- 条件最大嵌套深度为 12，总节点数最多 256，单个 `IN/NOT_IN` 最多 100 个值。
- `IS_NULL/IS_NOT_NULL` 的 values 必须为空，`IN/NOT_IN` 至少一项，其余操作符恰好一项。同一谓词中的 Literal 类型必须一致。
- 结构化模式不支持字段与字段比较、运行参数或自定义函数。
- SQL 模式只接受一个布尔谓词，最大 8192 个字符。未引用字符串和反引号标识符中的 `WHERE/SELECT/FROM/JOIN/UNION/WITH/INSERT/UPDATE/DELETE/MERGE/CREATE/ALTER/DROP/TRUNCATE`、分号和 SQL 注释均被拒绝；Spark Analyzer 继续校验字段、函数、语法和布尔结果类型。
- SQL 表达式正文可能包含敏感 Literal，不得进入节点摘要、日志、错误消息或血缘展示。

### 17.2 Map、Schema 与批流语义

- 节点类别为 `PROCESSOR`，至少一条入边；允许没有出边，悬空结果仅产生警告。
- 支持 `BATCH` 和 `STREAMING`；两种模式使用同一配置和同一个无状态 `FilterNodeOperator`。
- 按 `operations[]` 顺序从合并后的输入 Map 中精确取表；同一来源表在同一节点内只能配置一次。
- `REPLACE_SOURCE` 替换来源 Map 项，`CREATE_NEW_TABLE` 追加过滤结果；新输出名冲突时返回 `DUPLICATE_TABLE_NAME`。
- 输出字段、顺序、Origin、有界性、事件时间和 Watermark 全部继承来源表。
- Compiler 使用零行 Dataset 和同一 Operator 构造实际 Spark 条件并由 Analyzer 判断可执行性，不读取真实数据。SQL 模式同样支持 Batch 和 Streaming。
- 节点安全摘要只记录处理表数、输出方式、模式、结构化谓词数和 SQL 表达式长度，不记录 Literal 或 SQL 表达式正文。

## 18. `SELECT_COLUMNS` 处理器

### 18.1 稳定配置

```json
{
  "sourceTableName": "orders",
  "outputTableName": "order_summary",
  "columns": [
    "order_id",
    "customer_id",
    "amount",
    "created_at"
  ]
}
```

- `sourceTableName` 和 `outputTableName` 必填。
- `columns` 至少一项，字段名按来源 Schema 原始值精确匹配。
- 数组顺序就是输出 Schema 顺序；同一字段不得重复。
- 配置不保存字段 Schema、字段索引、通配符、表达式或 X6 状态。

### 18.2 Map、Schema 与批流语义

- 节点类别为 `PROCESSOR`，至少一条入边；允许没有出边，悬空结果仅产生警告。
- 支持 `BATCH` 和 `STREAMING`；两种模式使用同一配置和同一个无状态 `SelectColumnsNodeOperator`。
- 从合并后的输入 Map 中按 `sourceTableName` 精确取表，使用一次 Spark `select` 按配置顺序投影字段。
- 复制全部输入 Map，以 `outputTableName` 追加投影结果。输出名与任何现有 Key 冲突时返回 `DUPLICATE_TABLE_NAME`，包括来源表名。
- 输出字段完整继承来源字段的平台类型、nullable、长度、精度、默认值、生成属性和注释。
- Origin 与有界性继承来源表。事件时间字段仍被选择时同时继承事件时间和 Watermark；该字段被裁剪时两者同时清空。
- 空选择返回 `EMPTY_COLUMN_SELECTION`，重复字段返回 `DUPLICATE_SELECTED_COLUMN`，不存在字段返回精确到数组项路径的 `COLUMN_NOT_FOUND`。
- 节点安全摘要只记录来源表、输出表、字段数量和字段名，不记录数据值。

## 19. `DERIVE_COLUMNS` 处理器

### 19.1 稳定配置与表达式 AST

```json
{
  "globalDerivations": [
    {
      "targetColumnName": "amount_with_tax",
      "expression": {
        "kind": "BINARY",
        "operator": "MULTIPLY",
        "left": { "kind": "COLUMN", "columnName": "amount" },
        "right": {
          "kind": "LITERAL",
          "literal": { "dataType": "DECIMAL", "value": "1.06" }
        }
      }
    }
  ],
  "operations": [
    {
      "operationId": "ad71b64a-0f0d-4dd0-87ad-a64b3302470f",
      "sourceTableName": "orders",
      "output": { "mode": "CREATE_NEW_TABLE", "outputTableName": "orders_enriched" },
      "derivations": []
    }
  ]
}
```

表达式使用 `kind` 判别联合：

- `COLUMN`：`columnName` 引用进入节点时的原始来源字段。
- `LITERAL`：复用 FILTER 的 `CanvasLiteral`。
- `RUNTIME_VALUE`：由 Task Engine 注入白名单运行时常量；首期仅支持 `EXECUTION_ID`（STRING）和
  `EXECUTION_STARTED_AT`（TIMESTAMP）。定义只保存枚举名，不保存实际执行值。
- `BINARY`：`operator` 为 `ADD/SUBTRACT/MULTIPLY/DIVIDE/MODULO`，包含 `left/right`。
- `FUNCTION`：包含白名单 `function` 与有序 `arguments`。
- `CASE_WHEN`：包含一个或多个 `condition + result` 分支和可空的 `elseExpression`；条件复用 FILTER 的 `CanvasFilterCondition`。

安全限制固定为：单节点最多 100 个 derivation、表达式最大深度 16、表达式节点总数最多 512、单个 CASE 最多 64 个分支。协议不保存 SQL、Spark 表达式、Java 类名或任意函数名。

### 19.2 函数参数

| 函数 | 参数规则 |
| --- | --- |
| `TRIM/LTRIM/RTRIM/LOWER/UPPER` | 恰好 1 个表达式 |
| `REPLACE` | 恰好 3 个表达式：来源、搜索值、替换值 |
| `SUBSTRING` | 恰好 3 个表达式：来源、起始位置、长度 |
| `COALESCE/CONCAT` | 至少 2 个表达式 |
| `DATE_FORMAT` | 恰好 2 个参数；第二个必须是 STRING Literal 格式串 |
| `DATE_ADD/DATE_SUB` | 恰好 2 个参数；第二个必须是整数 Literal |

函数和二元运算的类型兼容性只由实际 Spark 表达式与 Analyzer 判断，不维护另一套平台类型兼容矩阵。

### 19.3 Map、Schema 与批流语义

- 节点类别为 `PROCESSOR`，至少一条入边，允许没有出边，支持 `BATCH` 和 `STREAMING`。
- 每张已选表的有效规则是 `globalDerivations + operation.derivations`。同一表的全部表达式只引用该表进入节点时的原始 Schema；后一项不能引用前一项新建的字段。
- 全局规则必须对全部已选表都有效，新增处理表自动继承；任何一张表不兼容时整个节点失败，不产生部分输出。全局和本表规则使用同一目标字段时返回 `GLOBAL_DERIVATION_TARGET_CONFLICT`。
- Operator 对每张表基于原始 Dataset 构造一次最终 `select`：目标字段存在时覆盖并保留原位置，不存在时按有效规则顺序追加；全局规则可以在不同表上分别覆盖或新增。
- 每张表的有效规则中目标名不能重复。
- `REPLACE_SOURCE` 替换该来源表的 Map Key；`CREATE_NEW_TABLE` 使用此项 `outputTableName` 追加结果，输出名与现有 Key 冲突时返回 `DUPLICATE_TABLE_NAME`。
- 未改变字段完整继承平台元数据。派生字段的类型和 nullable 取 Analyzer 结果，默认值、自增、生成列和注释等物理属性清空。
- Origin、有界性、事件时间和 Watermark 默认继承每张来源表。流模式禁止覆盖事件时间字段。
- Compiler 与 Runner 使用同一个无状态 `DeriveColumnsNodeOperator`；CASE 条件和 FILTER 共享同一个谓词表达式构建器。
- 编译预览以零 UUID 和 Epoch 时间推导 Schema；真实 Batch 或 Streaming Attempt 分别注入 Manifest
  `executionId` 与一次执行入口捕获的 UTC 开始时间。同一 Attempt 的所有表和行共享该值，流任务中
  它不是微批次时间。
- 安全摘要只记录处理表数量、全局/本表规则数量、目标字段名、表达式 kind 和函数名集合，不记录 Literal、生成 SQL 或数据行。

## 20. `TYPE_CAST` 处理器

### 20.1 稳定配置

```json
{
  "operations": [{
    "operationId": "8c46bb6d-3a58-4ee4-9c6e-96a9fbcdcb04",
    "sourceTableName": "orders",
    "output": {
      "mode": "CREATE_NEW_TABLE",
      "outputTableName": "typed_orders"
    },
    "casts": [
      {
        "columnName": "amount_text",
        "targetType": {
          "type": "DECIMAL",
          "length": null,
          "precision": 18,
          "scale": 2,
          "geometry": null
        },
        "failureStrategy": "FAIL"
      },
      {
        "columnName": "submitted_at_ms",
        "targetType": {
          "type": "TIMESTAMP",
          "length": null,
          "precision": null,
          "scale": null,
          "geometry": null
        },
        "failureStrategy": "SET_NULL",
        "epochTimestampUnit": "MILLISECONDS"
      },
      {
        "columnName": "created_at_text",
        "targetType": {
          "type": "TIMESTAMP",
          "length": null,
          "precision": null,
          "scale": null,
          "geometry": null
        },
        "failureStrategy": "SET_NULL",
        "stringTemporalParseOptions": {
          "pattern": "yyyy-MM-dd HH:mm:ss.SSS",
          "zoneMode": "SOURCE_TIME_ZONE",
          "sourceTimeZone": "Asia/Shanghai"
        }
      },
      {
        "columnName": "completed_at",
        "targetType": {
          "type": "STRING",
          "length": null,
          "precision": null,
          "scale": null,
          "geometry": null
        },
        "failureStrategy": "FAIL",
        "temporalStringFormatOptions": {
          "pattern": "yyyy-MM-dd HH:mm:ss.SSS",
          "targetTimeZone": "Asia/Shanghai"
        }
      },
      {
        "columnName": "submitted_at",
        "targetType": {
          "type": "LONG",
          "length": null,
          "precision": null,
          "scale": null,
          "geometry": null
        },
        "failureStrategy": "FAIL",
        "epochTimestampUnit": "MILLISECONDS"
      }
    ]
  }]
}
```

- 每个处理表的 `casts` 至少一项，同一字段只能配置一次；`operationId` 保持稳定，`output` 使用既有的“更新当前表 / 生成新表”结构。
- `targetType` 直接使用稳定 `PlatformTypeDefinition`；STRING 可设置正整数 length，DECIMAL 必须设置 `precision: 1..38` 和 `scale: 0..precision`，其他标量类型不携带参数。
- Spark Canvas 首期不支持 GEOMETRY 转换，Inspector 不展示，Operator 返回 `INVALID_TARGET_PLATFORM_TYPE`。
- `FAIL` 使用开启 ANSI 语义的普通 Spark cast；真实值无法转换时节点运行失败。
- `SET_NULL` 使用 Spark `Column.try_cast(DataType)`；真实值无法转换时结果为 NULL。
- `epochTimestampUnit` 可选值为 `SECONDS`、`MILLISECONDS`、`MICROSECONDS`，允许 `LONG → TIMESTAMP` 以及 `DATE/TIMESTAMP → LONG`。`TIMESTAMP_NTZ` 不支持 Epoch 转换，单位也不按数值位数自动推断。
- `LONG → TIMESTAMP` 使用 Spark 的 `timestamp_seconds`、`timestamp_millis` 或 `timestamp_micros`；`TIMESTAMP → LONG` 使用对应的 `unix_seconds`、`unix_millis` 或 `unix_micros`。`DATE → LONG` 固定按该日期的 UTC `00:00:00` 计算。输入 NULL 始终输出 NULL；`SET_NULL` 在日期转时间戳超出可表示范围时输出 NULL。
- 缺少 Epoch 单位的旧规则继续使用原有 Spark Cast 语义；只打开并保存不会自动改写。新建或重新选择支持的 Epoch 转换组合时默认 `MILLISECONDS`。
- `stringTemporalParseOptions` 仅允许 `STRING → DATE/TIMESTAMP`，最多包含 128 个字符的 Spark datetime pattern。DATE 必须只配置 `pattern`。TIMESTAMP 的 `SOURCE_TIME_ZONE` 必须提供有效 IANA Zone ID，且 pattern 不得含有时区符号；`EMBEDDED_OFFSET` 不得填写来源时区，且 pattern 必须含未加引号的 `X/x/Z/O/V/z` 时区或偏移符号。两类特殊配置互斥，`TIMESTAMP_NTZ` 继续采用普通 Spark cast。
- 对字符串日期时间解析，`FAIL` 使用 ANSI 语义下的 `to_date` / `to_timestamp`，格式错误、非法日期和不能解析的 DST 本地时间会终止节点；`SET_NULL` 使用 `try_to_date` / `try_to_timestamp`，失败结果为 NULL。指定来源时区时，Spark 固定 UTC Session 先解析本地时间，再按该 Zone ID 归一为 UTC；自带偏移时直接遵从字符串中的 `Z` 或 `+08:00` 语义。
- `temporalStringFormatOptions` 仅允许 `DATE/TIMESTAMP/TIMESTAMP_NTZ → STRING`，pattern 最长 128 个字符。DATE 默认 `yyyy-MM-dd`；TIMESTAMP 和 TIMESTAMP_NTZ 默认 `yyyy-MM-dd HH:mm:ss`。TIMESTAMP 必须提供目标 IANA 时区，新规则默认 UTC；DATE 与 TIMESTAMP_NTZ 不允许配置时区。
- TIMESTAMP 先从 UTC Session 转为目标时区的显示墙钟时间，再按 pattern 格式化；DATE 与 TIMESTAMP_NTZ 不做时区换算。输出 pattern 禁止未加引号的 `X/x/Z/O/V/z`，避免生成与实际瞬时语义不一致的偏移文本。NULL 始终输出 NULL。
- 缺少 `temporalStringFormatOptions` 的旧规则继续使用 Spark 默认 Cast：DATE 通常为 `yyyy-MM-dd`，TIMESTAMP/TIMESTAMP_NTZ 为 Spark 默认日期时间文本；不会因为打开或保存配置而自动改写格式。

### 20.2 Map、Schema 与批流语义

- 节点类别为 `PROCESSOR`，至少一条入边，允许没有出边，支持 `BATCH` 和 `STREAMING`。
- Operator 基于来源 Dataset 构造一次 `select`；未转换字段直接投影，转换字段在原位置使用原字段名 alias。
- 复制全部输入 Map；`REPLACE_SOURCE` 替换该来源表，`CREATE_NEW_TABLE` 以配置的输出名追加结果，输出名与任何现有 Key 冲突时返回 `DUPLICATE_TABLE_NAME`。
- 未转换字段完整继承元数据。转换字段类型来自目标平台类型和 Analyzer，物理默认值、自增、生成列和注释清空。
- `FAIL` 的 nullable 取 Analyzer 结果；`SET_NULL` 固定 nullable 为 true。
- Origin、有界性、事件时间和 Watermark 默认继承来源表；流模式禁止转换事件时间字段。
- Cast 能否建立计划只由 Spark Analyzer 判断；Compiler 不读取真实值，也不承诺运行时数据一定可转换。
- Compiler 与 Runner 使用同一个无状态 `TypeCastNodeOperator`，不修改全局 ANSI 配置，不拼接用户 SQL。
- 安全摘要只记录来源/输出表、字段名、目标平台类型、失败策略、Epoch 单位，以及时间解析/格式化的时区模式、IANA Zone ID、pattern 长度和 SHA-256，不记录字段值、失败值、pattern 正文或生成 SQL。

## 21. `AGGREGATE` 处理器

### 21.1 稳定配置

```json
{
  "sourceTableName": "orders",
  "outputTableName": "customer_order_metrics",
  "groupByColumns": ["customer_id"],
  "aggregations": [
    {
      "function": "COUNT",
      "sourceColumnName": null,
      "outputColumnName": "order_count",
      "distinct": false
    },
    {
      "function": "SUM",
      "sourceColumnName": "amount",
      "outputColumnName": "total_amount",
      "distinct": false
    }
  ]
}
```

- `groupByColumns=[]` 表示全表聚合；字段顺序就是输出中的分组字段顺序，且不能重复。
- `aggregations` 至少一项，输出字段名必填、互不重复，并且不能与分组字段同名。
- 支持 `COUNT`、`SUM`、`AVG`、`MIN`、`MAX`。
- `COUNT(*)` 固定表示为 `function=COUNT + sourceColumnName=null + distinct=false`。
- `COUNT/SUM/AVG` 支持单字段 DISTINCT；`MIN/MAX` 不支持 DISTINCT。
- 除 COUNT(*) 外，每个聚合项都必须指定来源字段。

### 21.2 Map、Schema 与执行语义

- 节点类别为 `PROCESSOR`，至少一条入边，允许没有出边，仅支持 `BATCH`。
- 输出字段顺序固定为全部 `groupByColumns`，随后按 `aggregations` 配置顺序排列指标。
- Operator 使用 Spark Analyzer 推导聚合字段类型和 nullable，不在平台层复制函数类型矩阵，也不读取真实数据。
- 分组字段继承来源字段注释等展示元数据，但清空默认值、自增和生成列属性；指标字段不继承来源物理属性。
- 复制全部输入 Map，以 `outputTableName` 追加结果；输出名与任何现有 Key 冲突时返回 `DUPLICATE_TABLE_NAME`。
- 输出固定为 `BOUNDED`，`origin`、`eventTimeColumn` 和 `watermarkDelay` 固定为 `null`。
- Compiler 与 Runner 使用同一个无状态 `AggregateNodeOperator`。
- 安全摘要只记录来源/输出表、分组字段名、函数、来源字段名、输出字段名和 DISTINCT 标志，不记录聚合结果、数据值或生成表达式。

实时聚合涉及窗口、事件时间、Watermark、状态清理和输出模式，后续必须使用独立 `WINDOW_AGGREGATE` 节点，不能扩张本节点配置。

## 22. `UNION` 处理器

### 22.1 稳定配置

```json
{
  "inputTableNames": ["current_orders", "history_orders"],
  "outputTableName": "all_orders",
  "mode": "ALL",
  "mergingTables": [
    {
      "tableName": "history_orders",
      "fieldRules": [
        {
          "sourceColumnName": "legacy_status",
          "action": "MATCH",
          "targetColumnName": "status"
        },
        {
          "sourceColumnName": "temporary_note",
          "action": "REMOVE",
          "targetColumnName": null
        }
      ]
    }
  ]
}
```

- `inputTableNames` 至少包含两个不同表名；数组顺序稳定，第一张表决定输出字段顺序。
- `mergingTables=null` 或缺失时保持旧版严格模式：每张输入表必须具有完全相同的字段名集合，字段原始
  顺序可以不同。
- `mergingTables=[]` 启用默认 Merge Layers：基准层字段全部保留；后续层同名字段 Match，其他字段按
  原名依次追加，任一输入缺少的输出字段补 NULL。
- 非空 `mergingTables` 只需保存偏离默认行为的字段规则。`MATCH` 写入当时已经存在的输出字段，
  `RENAME` 以新名称追加字段，`REMOVE` 排除字段。来源字段和最终输出字段均按大小写不敏感唯一。
- Match 允许相同平台类型以及数值类型之间的显式 Cast；字符串与数值等其他跨类型 Match 拒绝。
- 空间输入必须均为空间层或均为具有相同数量 Geometry 字段的空间层。每个 Geometry 必须 Match 到
  基准层类型、CRS 和维度完全一致的 Geometry，不能 Rename 或 Remove。
- `ALL` 保留重复行；`DISTINCT` 对完整 Union 结果执行全字段去重。

### 22.2 Map、Schema 与批流语义

- 节点类别为 `PROCESSOR`，至少一条入边，允许没有出边，支持 `BATCH` 和 `STREAMING`。
- 直接上游 Map 仍先按普通规则合并；同名 Key 在进入 UNION 前就返回 `DUPLICATE_TABLE_NAME`。
- 严格模式按配置顺序查找表，使用第一张表字段顺序，以 `unionByName` 合并其余 Dataset。
- Merge Layers 先按规则对每张表执行限定字段的 `select + alias + cast`，再使用允许缺失字段的
  `unionByName`。后续合并层可以 Match 前面合并层已经追加的字段；不能引用尚未出现的字段。
- 输出类型和 nullable 取最终 Analyzer Schema，来源专属物理属性和注释清空，`origin=null`。
- 复制全部输入 Map，以 `outputTableName` 追加结果；输出名与任何现有 Key 冲突时返回 `DUPLICATE_TABLE_NAME`。
- 所有输入必须具有相同 `datasetKind`：全部有界输出 `BOUNDED`，全部无界输出 `UNBOUNDED`，混合输入拒绝。
- 无界严格输入的 `eventTimeColumn` 必须一致；Merge Layers 允许来源事件时间字段名不同，但必须 Match
  到基准层事件时间字段。两种模式的 `watermarkDelay` 都必须一致并由输出继承；有界输出清空二者。
- 无界输入只支持 `ALL`；`DISTINCT` 会形成无边界状态，因此返回 `STREAMING_UNION_DISTINCT_NOT_SUPPORTED`。
- Compiler 与批流 Runner 使用同一个无状态 `UnionNodeOperator`。安全摘要只记录输入/输出表名、输入
  数量、模式、是否启用 Merge Layers、配置层数和规则数量，不记录数据值或字段规则内容。
- 完整能力边界和 ArcGIS 对齐说明见 [UNION Merge Layers 设计](canvas-union-merge-layers-design.md)。

## 23. `DEDUPLICATE` 处理器

### 23.1 稳定配置

```json
{
  "sourceTableName": "orders",
  "outputTableName": "latest_orders",
  "keyColumns": ["order_id"],
  "keepStrategy": "FIRST",
  "orderBy": [
    {
      "columnName": "updated_at",
      "direction": "DESC",
      "nullOrdering": "LAST"
    }
  ]
}
```

- `keyColumns=[]` 表示按全部字段去重，只允许 `ANY`。
- `ANY` 任意保留一条，`orderBy` 必须为空。
- `FIRST/LAST` 必须至少配置一个去重键和一个排序字段。
- key 字段和排序字段各自不能重复。
- `FIRST` 按配置排序取第一行。
- `LAST` 反转每个排序字段的方向和 NULL 顺序后取第一行：例如 `ASC NULLS FIRST` 反转为 `DESC NULLS LAST`。

### 23.2 Map、Schema 与执行语义

- 节点类别为 `PROCESSOR`，至少一条入边，允许没有出边，仅支持 `BATCH`。
- `ANY + keyColumns=[]` 使用全字段 `dropDuplicates()`；带 key 时使用按 key 的 `dropDuplicates`。
- `FIRST/LAST` 使用 `Window.partitionBy(keyColumns).orderBy(...) + row_number()`，只保留序号 1；不执行 count、collect 或其他额外 Spark Action。
- 内部 row number 字段使用不与业务字段冲突的临时名称，并在最终投影中移除，不进入稳定定义、输出 Schema、下游 Map 或日志。
- 复制全部输入 Map，以 `outputTableName` 追加结果；输出名与任何现有 Key 冲突时返回 `DUPLICATE_TABLE_NAME`。
- 输出字段、顺序和完整平台元数据继承来源表，`origin` 继承来源；输出固定为 `BOUNDED`，事件时间和 Watermark 清空。
- Compiler 与 Runner 使用同一个无状态 `DeduplicateNodeOperator`。安全摘要只记录来源/输出表、key 字段名、策略和排序配置，不记录保留或删除的实际值。

实时去重需要事件时间、Watermark、状态 TTL 和迟到数据策略，后续必须使用独立 `STREAM_DEDUPLICATE` 节点。

## 24. `NULL_HANDLING` 处理器

### 24.1 稳定配置

```json
{
  "sourceTableName": "orders",
  "outputTableName": "orders_cleaned",
  "rules": [
    {
      "kind": "DROP_ROW",
      "columnNames": ["customer_id", "order_id"],
      "matchMode": "ANY_NULL"
    },
    {
      "kind": "FILL_LITERAL",
      "columnName": "status",
      "value": {
        "dataType": "STRING",
        "value": "UNKNOWN"
      }
    }
  ]
}
```

`rules` 是以 `kind` 为判别字段的有序联合，最多 100 项：

- `DROP_ROW`：`columnNames` 至少一项且不能重复；`ANY_NULL` 表示任一指定字段为 NULL 时删除整行，`ALL_NULL` 表示全部指定字段为 NULL 时删除整行。
- `FILL_LITERAL`：只在 `columnName` 为 NULL 时写入非 NULL `CanvasLiteral`；Literal 平台类型必须与目标字段一致，同一字段只能配置一次填充。
- `GEOMETRY` 首期不支持固定 Literal 填充。
- 规则严格按数组顺序执行；先填充再删除和先删除再填充具有不同语义。
- SQL NULL 与 NaN、空字符串、零值严格区分，不做隐式空值归一化。

### 24.2 Map、Schema 与批流语义

- 节点类别为 `PROCESSOR`，至少一条入边，允许没有出边；当前属于 Canvas `3.0` 基础能力。
- 支持 `BATCH` 和 `STREAMING`，两种模式使用同一配置和同一个无状态 `NullHandlingNodeOperator`。
- 从输入 Map 按 `sourceTableName` 精确取表，保留全部输入表，并以 `outputTableName` 追加处理结果；输出名与任何现有 Key 冲突时返回 `DUPLICATE_TABLE_NAME`。
- 输出字段名、顺序、平台类型、Origin、有界性、事件时间和 Watermark 继承来源表。
- `DROP_ROW` 不改变字段 Schema；`FILL_LITERAL` 字段的 nullable 取 Spark Analyzer 结果，并清空默认值、自增和生成列属性，保留字段注释。
- 流模式允许通过 `DROP_ROW` 删除事件时间为空的记录，但禁止填充事件时间字段，返回 `STREAM_EVENT_TIME_COLUMN_IMMUTABLE`。
- Compiler 仅在零行 Dataset 上建立并分析计划，不读取真实数据或统计被删除、被填充的行数。
- 安全摘要只记录来源/输出表、规则种类、字段名、匹配模式和 Literal 平台类型，不记录填充值或数据行。

## 25. `VALUE_MAPPING` 处理器

### 25.1 稳定配置

```json
{
  "sourceTableName": "orders_cleaned",
  "outputTableName": "orders_standardized",
  "rules": [
    {
      "columnName": "status",
      "entries": [
        {
          "sourceValue": {
            "dataType": "STRING",
            "value": "P"
          },
          "targetValue": {
            "dataType": "STRING",
            "value": "PAID"
          }
        },
        {
          "sourceValue": {
            "dataType": "STRING",
            "value": "X"
          },
          "targetValue": null
        }
      ],
      "unmatchedStrategy": "KEEP",
      "unmatchedValue": null
    }
  ]
}
```

- 一个节点最多配置 100 个字段规则；每个规则最多 200 个映射项，单节点最多 2,000 个映射项。
- 同一字段只能配置一条规则；同一规则中的 `sourceValue` 按平台类型语义比较后不得重复，例如 DECIMAL `1.0` 与 `1.00` 视为同一源值。
- `sourceValue` 必须是非 NULL Literal；`targetValue=null` 明确表示映射结果为 SQL NULL。非 NULL Literal 的平台类型必须与目标字段一致。
- 未匹配策略固定为：
  - `KEEP`：保留原值。
  - `SET_NULL`：写入 SQL NULL。
  - `SET_LITERAL`：写入必填的 `unmatchedValue`。
  - `ERROR`：真实运行命中未匹配非 NULL 值时以 `VALUE_MAPPING_UNMATCHED_VALUE` 失败。
- 来源值为 SQL NULL 时始终保持 NULL，不参与映射、不应用未匹配值，也不触发 `ERROR`。
- `GEOMETRY` 首期不支持内联值映射。节点只处理小型静态精确映射，不支持范围、正则、模糊或远程字典匹配。

### 25.2 Map、Schema 与批流语义

- 节点类别为 `PROCESSOR`，至少一条入边，允许没有出边；当前属于 Canvas `3.0` 基础能力。
- 支持 `BATCH` 和 `STREAMING`，两种模式使用同一配置和同一个无状态 `ValueMappingNodeOperator`。
- Operator 以一次最终投影处理全部规则，保持来源字段名和字段顺序；规则数组顺序和映射项顺序在 JSON 往返时保持稳定。
- 保留输入 Map 中全部表，以 `outputTableName` 追加结果；输出名与任一现有 Key 冲突时返回 `DUPLICATE_TABLE_NAME`。
- 未映射字段完整继承元数据；映射字段类型保持不变，nullable 取 Analyzer 结果，并清空默认值、自增和生成列属性。Origin、有界性、事件时间和 Watermark 继承来源表。
- 流模式禁止映射事件时间字段，返回 `STREAM_EVENT_TIME_COLUMN_IMMUTABLE`。
- Compiler 在零行 Dataset 上可以分析 `ERROR` 分支，但不会扫描真实数据判断映射覆盖率；只有 Runner 命中实际未匹配值时失败。
- `VALUE_MAPPING_UNMATCHED_VALUE` 属于不可重试的 `CONSTRAINT` 失败。错误、日志和安全摘要只记录字段名、规则/映射项数量、未匹配策略及是否映射为 NULL，不记录源值、目标值、默认值或实际未匹配值。

## 26. `WINDOW` 处理器

### 26.1 稳定配置

```json
{
  "sourceTableName": "orders",
  "outputTableName": "orders_windowed",
  "partitionByColumns": ["customer_id"],
  "orderBy": [
    {
      "columnName": "created_at",
      "direction": "ASC",
      "nullOrdering": "LAST"
    }
  ],
  "functions": [
    {
      "kind": "ROW_NUMBER",
      "outputColumnName": "order_sequence"
    },
    {
      "kind": "LAG",
      "sourceColumnName": "amount",
      "offset": 1,
      "defaultValue": null,
      "outputColumnName": "previous_amount"
    },
    {
      "kind": "SUM",
      "sourceColumnName": "amount",
      "outputColumnName": "running_amount",
      "frame": {
        "type": "ROWS",
        "start": {
          "kind": "UNBOUNDED_PRECEDING"
        },
        "end": {
          "kind": "CURRENT_ROW"
        }
      }
    }
  ]
}
```

`functions` 是以 `kind` 为判别字段的有序联合，最多 100 项：

- 排名：`ROW_NUMBER`、`RANK`、`DENSE_RANK`，只配置 `outputColumnName`。
- 偏移：`LAG`、`LEAD`，配置来源字段、`1..10000` 的 offset、可空默认 Literal 和输出字段名。非 NULL 默认 Literal 必须与来源字段平台类型一致。
- 聚合：`COUNT`、`SUM`、`AVG`、`MIN`、`MAX`，配置来源字段、输出字段名和明确 Frame；`COUNT` 的 `sourceColumnName=null` 表示 `COUNT(*)`。
- 取值：`FIRST_VALUE`、`LAST_VALUE`，配置来源字段、`ignoreNulls`、输出字段名和明确 Frame。
- `partitionByColumns` 可以为空且字段不能重复；`orderBy` 至少一项，字段不能重复，每项必须显式声明方向和 NULL 顺序。
- 输出字段名必须互不重复且不能与来源字段同名；同一节点的窗口函数不能引用本节点前面新生成的字段。

### 26.2 `ROWS` Frame

首期只支持 `type=ROWS`。边界判别值为：

- `UNBOUNDED_PRECEDING`
- `PRECEDING`，携带 `offset: 1..1000000`
- `CURRENT_ROW`
- `FOLLOWING`，携带 `offset: 1..1000000`
- `UNBOUNDED_FOLLOWING`

Frame 起点不能为 `UNBOUNDED_FOLLOWING`，终点不能为 `UNBOUNDED_PRECEDING`，且起点不能晚于终点。Frame 必须完整写入定义，不依赖 Spark 的隐式默认值；排名和偏移函数不携带 Frame。

### 26.3 Map、Schema 与执行语义

- 节点类别为 `PROCESSOR`，至少一条入边，允许没有出边；当前属于 Canvas `3.0` 基础能力，仅支持 `BATCH` 和 `BOUNDED` 来源。
- Operator 使用配置中的分区、排序及明确 `ROWS` Frame 构造 Spark Window 表达式，通过一次最终投影保留全部来源字段，并按 `functions` 顺序追加窗口字段。
- 保留输入 Map 中全部表，以 `outputTableName` 追加结果；输出名与任一现有 Key 冲突时返回 `DUPLICATE_TABLE_NAME`。
- 来源字段完整继承元数据；窗口字段的类型和 nullable 取 Spark Analyzer 结果，物理默认值、自增、生成列和注释清空。
- 输出 `origin` 继承来源表，`datasetKind=BOUNDED`，`eventTimeColumn` 和 `watermarkDelay` 清空。
- Compiler 只构造零行计划，不计算排名或触发 Spark Action。实时任务返回 `NODE_EXECUTION_MODE_NOT_SUPPORTED`，异常无界来源返回 `WINDOW_REQUIRES_BOUNDED_INPUT`。
- `orderBy` 只定义窗口计算顺序，不承诺下游节点或 Sink 的物理行顺序。
- 安全摘要可记录来源/输出表、分区和排序字段、函数 kind、字段名、offset、Frame 边界及 `ignoreNulls`，不得记录默认 Literal、实际排名、窗口结果或数据行。

流式事件时间窗口涉及 Watermark、状态 TTL、迟到数据和输出模式，必须使用独立流式节点，不能扩张本批处理协议。

## 27. `TOP_N` 处理器

### 27.1 稳定配置

```json
{
  "sourceTableName": "orders",
  "outputTableName": "top_orders_by_customer",
  "partitionByColumns": ["customer_id"],
  "orderBy": [
    {
      "columnName": "amount",
      "direction": "DESC",
      "nullOrdering": "LAST"
    },
    {
      "columnName": "order_id",
      "direction": "ASC",
      "nullOrdering": "LAST"
    }
  ],
  "limit": 3,
  "tieStrategy": "EXACT"
}
```

- `partitionByColumns=[]` 表示全局 Top N；非空表示每个分区分别取 Top N。分区字段不能重复。
- `orderBy` 至少一项且字段不能重复，数组顺序、方向和 NULL 顺序共同定义比较规则。
- `limit` 是 `1..1000000` 的整数。
- `EXACT` 在全局或每个分区最多保留 N 行；分区模式使用 `row_number()`。排序键存在并列且没有唯一 tie-breaker 时，无法保证具体保留哪一行。
- `WITH_TIES` 使用 `rank()` 并保留 `rank <= limit`；第 N 行存在相同排序键时保留全部并列记录，结果可以超过 N 行。不得使用 `dense_rank()` 代替。
- 配置不保存临时排名字段、Spark WindowSpec、SQL、X6 状态或实际排序边界值。

### 27.2 Map、Schema 与执行语义

- 节点类别为 `PROCESSOR`，至少一条入边，允许没有出边；当前属于 Canvas `3.0` 基础能力，仅支持 `BATCH` 和 `BOUNDED` 来源。
- 全局 `EXACT` 使用显式 `orderBy + limit`；分区 `EXACT` 使用 `row_number()`；全局和分区 `WITH_TIES` 使用 `rank()`。
- 内部排名字段使用不与业务字段冲突的临时名称，并在最终投影中移除，不进入定义、输出 Schema、下游 Map 或日志。
- 保留输入 Map 中全部表，以 `outputTableName` 追加结果；输出名与任一现有 Key 冲突时返回 `DUPLICATE_TABLE_NAME`。
- 输出字段、顺序和完整平台元数据继承来源表，`origin` 继承来源；输出 `datasetKind=BOUNDED`，事件时间和 Watermark 清空。
- Compiler 只构造和分析零行计划，不读取真实数据、不检查排序键唯一性，也不执行排序 Action。实时任务返回 `NODE_EXECUTION_MODE_NOT_SUPPORTED`，异常无界来源返回 `TOP_N_REQUIRES_BOUNDED_INPUT`。
- 排序只决定保留哪些行，不承诺后续节点、数据库或文件中的物理行顺序。
- 安全摘要只记录来源/输出表、分区字段、排序字段、方向、NULL 顺序、limit 和 tieStrategy，不记录第 N 行值、并列值、被删除行或实际输出行数。

## 28. `MASK_FIELDS` 处理器

### 28.1 稳定配置

```json
{
  "sourceTableName": "customers",
  "outputTableName": "customers_masked",
  "fieldRules": [
    {
      "fieldName": "mobile",
      "ruleSource": "GLOBAL",
      "sourceRuleRef": {
        "ruleId": "2e73144a-372b-40ae-b972-d56e528470c5",
        "ruleCode": "mask_mobile",
        "ruleName": "手机号脱敏"
      },
      "definition": {
        "strategy": "PARTIAL_MASK",
        "keepPrefixLength": 3,
        "keepSuffixLength": 4,
        "maskPosition": null,
        "maskCharacter": "*",
        "fixedValue": null
      }
    }
  ]
}
```

- `fieldRules` 至少一项、最多 100 项，同一字段不能重复。
- `ruleSource=GLOBAL` 必须保存来源规则 ID、编码、名称和完整 `definition`；保存只校验 ID 的 UUID 格式，不读取规则。
- `ruleSource=INLINE` 不允许保存 `sourceRuleRef`。
- `definition` 是节点自己的唯一执行配置，不是独立快照资源；不得保存测试值、真实数据样例或凭据。
- `PARTIAL_MASK` 保留前后字符，中间等长掩码；短值不能同时满足前后长度时整体掩码。
- `POSITION_MASK` 将从 1 开始计数的第 N 个字符替换为掩码字符；位置缺失时默认为第 2 位，长度不足 N 时原样保留。
- `KEEP_LENGTH_MASK` 将每个字符替换为掩码字符。
- `FIXED_VALUE` 使用最长 1024 字符的固定字符串替换。
- `NULLIFY` 使用原字段类型的 `null` 替换。
- 所有策略对输入 `null` 保持 `null`；掩码字符默认 `*`，配置时必须是一个 Unicode 字符。

全局规则只是配置模板。任务保存、发布、编译和运行不查询规则、不比较定义、不检查来源是否存在，也不以全局当前定义覆盖节点配置。编辑时的变化和删除提示仅由 Inspector 通过普通规则详情接口完成，详见[数据脱敏规则和字段脱敏节点](data-masking-rules.md)。

### 28.2 Map、Schema 与批流语义

- 节点类别为 `PROCESSOR`，至少一条入边，允许没有出边；当前属于 Canvas `3.0` 基础能力，支持 `BATCH` 和 `STREAMING`。
- 节点无状态并继承来源 `BOUNDED/UNBOUNDED`、事件时间字段和 Watermark；实时任务禁止脱敏事件时间字段。
- 来源表和字段必须存在；非 `NULLIFY` 策略只支持 `STRING`，`NULLIFY` 只支持可空字段。
- 保留输入 Map 中全部表，以 `outputTableName` 追加结果；输出名与任一现有 Key 冲突时返回 `DUPLICATE_TABLE_NAME`。
- 未配置字段直接透传；配置字段在输出表中原位替换，字段名称、顺序、平台类型和 nullable 保持不变。
- Operator 使用 Spark 内置 `Column` 表达式，不使用 Java UDF；`NULLIFY` 显式转换为原字段类型，批流复用同一实现。
- Compiler 只构造零行计划；字段缺失、类型不兼容、配置非法或 Spark Analyzer 失败会阻止任务。真实运行遇到脱敏失败时节点失败，禁止回退输出原值。
- 安全摘要只记录字段数量、策略类型和全局/自定义数量，节点 ID 由统一生命周期日志记录；禁止记录表名、字段名、固定替换值、完整参数、测试值或数据行。

## 29. `JSON_EXTRACT` 处理器

### 29.1 稳定配置

```json
{
  "sourceTableName": "orders",
  "outputTableName": "orders_json_extracted",
  "sourceColumnName": "payload",
  "extractions": [
    {
      "jsonPath": "$.customer.id",
      "outputColumnName": "customer_id",
      "targetType": {
        "type": "STRING",
        "length": 64,
        "precision": null,
        "scale": null,
        "geometry": null
      }
    },
    {
      "jsonPath": "$.amount",
      "outputColumnName": "payload_amount",
      "targetType": {
        "type": "DECIMAL",
        "length": null,
        "precision": 18,
        "scale": 2,
        "geometry": null
      }
    }
  ],
  "failureStrategy": "ERROR"
}
```

- `sourceTableName`、`outputTableName` 和 `sourceColumnName` 必填；来源字段必须是 `STRING`。
- `extractions` 至少一项、最多 100 项，数组顺序就是输出新增字段顺序。
- `jsonPath` 使用 Spark VARIANT Path 语法，长度不超过 512 个字符且必须以 `$` 开头；定义中不保存自由 SQL。
- `outputColumnName` 必填，不能与来源字段或同节点其他提取项重名。
- `targetType` 复用稳定 `PlatformTypeDefinition`，支持 Canvas 已有平台标量类型，首期不支持 `GEOMETRY`。
- JSON Path 不存在时始终返回 SQL NULL。
- `failureStrategy=ERROR` 时，畸形 JSON 或目标类型转换失败会导致运行失败。
- `failureStrategy=SET_NULL` 时，畸形 JSON 或目标类型转换失败返回 SQL NULL。

### 29.2 Map、Schema 与批流语义

- 节点类别为 `PROCESSOR`，至少一条入边，允许没有出边；当前属于 Canvas `3.0` 基础能力。
- 支持 `BATCH` 和 `STREAMING`，两种模式使用同一配置和同一个无状态 `JsonExtractNodeOperator`。
- 从输入 Map 按 `sourceTableName` 精确取表，保留全部输入表，并以 `outputTableName` 追加提取结果；输出名与任何现有 Key 冲突时返回 `DUPLICATE_TABLE_NAME`。
- 输出先完整保留来源字段及其顺序，再按 `extractions` 顺序追加字段。
- 来源字段完整继承平台元数据；新增字段使用配置的目标平台类型，固定为 nullable，默认值、自增、生成列和注释清空。
- 输出 `origin`、`datasetKind`、`eventTimeColumn` 和 `watermarkDelay` 继承来源表。节点只读取来源字段，不改变事件时间列。
- Operator 使用 Spark `parse_json/try_parse_json` 与 `variant_get/try_variant_get` 构造 `Column` 表达式，不拼接用户 SQL。
- Compiler 只在零行 Dataset 上构造并分析计划，不读取真实 JSON，不检查实际 Path 命中率，也不触发 Spark Action。
- 未分类运行时失败使用统一 `PROCESSOR_EXECUTION_FAILED`；Spark 惰性执行导致的真实数据错误不承诺错误到单个 Path 或数据值。
- 安全摘要只记录来源/输出表、JSON 来源字段、提取数量、目标类型集合和失败策略；不得记录 JSON Path、JSON 内容、样例值、失败值或数据行。

## 30. `FILE_OUTPUT` 节点

### 30.1 稳定配置

`FILE_OUTPUT` 是 Canvas `3.0` 的正式输出节点，公共配置保持不变：

```json
{
  "sourceTableName": "districts",
  "dataSourceId": "901e8938-bc1d-4bfd-91ec-d26bca38e8f6",
  "targetPath": "exports/districts",
  "conflictPolicy": "FAIL_IF_EXISTS",
  "formatOptions": {
    "type": "SHAPEFILE",
    "baseName": "districts",
    "packageMode": "ZIP",
    "geometryColumnName": "geom",
    "targetShapeType": "POLYGON",
    "attributeMappings": [
      {
        "sourceColumnName": "district_id",
        "targetFieldName": "DIST_ID",
        "targetStringByteLength": null
      },
      {
        "sourceColumnName": "district_name",
        "targetFieldName": "DIST_NAME",
        "targetStringByteLength": 160
      }
    ]
  }
}
```

`formatOptions` 是以 `type` 为判别字段的严格联合：

- `CSV`：`header + delimiter + quote + escape + nullValue`。
- `JSON_LINES`：`ignoreNullFields`。
- `PARQUET`：无额外字段，运行时固定 Snappy。
- `SHAPEFILE`：Canvas `3.0` 直接支持该格式。
- `GEOPARQUET`：Canvas `3.0` 直接支持该格式；配置为
  `geometryColumnName + compression(SNAPPY|ZSTD) + coveringMode(NONE|ROW_BBOX)`。
- `GEOJSON`：Canvas `3.0` 直接支持该格式；配置为
  `baseName + geometryColumnName + idColumnName + ignoreNullProperties`。

Shapefile 的 `packageMode` 为 `ZIP | COMPONENT_DIRECTORY`，目标 Shape 类型为
`POINT | MULTIPOINT | POLYLINE | POLYGON`。每项 DBF 映射固定保存来源字段、最多 10 位的
ASCII 目标字段名以及 STRING 专用的 UTF-8 字节宽度；属性顺序严格采用数组顺序。

### 30.2 图、编译与执行语义

- 节点类别为 `OUTPUT`，仅支持 `BATCH`，恰好一条入边且无出边；只接受 `BOUNDED` 来源。
- 目标必须是已启用、连接类型为 S3 且具有 `DISTRIBUTION` 用途的数据源。
- CSV、JSON Lines 和 Parquet 使用 Spark 目录数据集语义；这些格式仍不接受未序列化的
  Geometry 字段。
- Shapefile 一个节点始终生成一套制品，不按 Spark 分区拆分。Runner 通过
  `Dataset.toLocalIterator()` 将行流式送到 Driver 本地 GeoTools Writer，不调用
  `collect/count/coalesce(1)`。
- ZIP 输出 `{baseName}.zip + _SUCCESS`；组件目录输出
  `.shp/.shx/.dbf/.prj/.cpg + _SUCCESS`。完成标记始终最后提交。
- Geometry 必须是 EPSG + XY；不隐式转换 CRS、不降维、不拆 GeometryCollection、不修复
  拓扑。NULL 写 Null Shape，Empty Geometry 和运行时 Shape 类型不符会失败。
- 任一 SHP、SHX、DBF 或 ZIP 达到 1.8GB 时失败，不自动切分。
- 完整校验、DBF 类型映射、S3 暂存提交和错误协议见
  [FILE_OUTPUT Shapefile 输出设计](canvas-shapefile-output-design.md)。
- GeoParquet 固定写出 1.1.0，唯一 Geometry 使用 WKB 编码并在 Footer 中保存显式
  PROJJSON；`ROW_BBOX` 时追加 `{geometryColumnName}_bbox`，输出仍是允许多个
  `part-*.parquet` 的 Spark 目录数据集。
- GeoJSON 固定生成一个 RFC 7946 FeatureCollection，仅接受 EPSG:4326 + XY；
  Driver 通过 `toLocalIterator()` 逐行生成 `{baseName}.geojson`，不调用
  `collect/count/coalesce(1)`，达到 1.8GB 时失败。
- 两种空间格式都要求唯一 Geometry 和 `BOUNDED` 来源，不隐式转换 CRS、降维或
  修复 Geometry。完整规则见 [GeoParquet 输出设计](canvas-geoparquet-output-design.md)
  和 [GeoJSON 输出设计](canvas-geojson-output-design.md)。

## 31. 快照同步 Output

### 31.1 稳定配置

`JDBC_SNAPSHOT_SYNC_OUTPUT` 与 `MODEL_SNAPSHOT_SYNC_OUTPUT` 是 Canvas `3.0` 的正式节点，使用统一的显式字段映射配置：

```json
{
  "sourceTableName": "reservoir_snapshot",
  "keyColumns": ["reservoir_code"],
  "columnMappings": [
    { "sourceColumnName": "reservoir_code", "targetColumnName": "reservoir_code" }
  ],
  "deletePolicy": {
    "action": "DELETE",
    "maxDeleteRows": 1000,
    "maxDeleteRatio": 0.2
  }
}
```

JDBC 节点额外保存 `dataSourceId + targetTableName`；模型节点额外保存 `targetModelId`。
Key 固定保存 `1..32` 个目标字段名，由用户显式指定，不强制等于数据库唯一约束。`KEEP` 时
两个删除阈值必须为 null；`DELETE` 时最大删除行数必须为正整数，最大删除比例必须位于
`(0, 1]`。

### 31.2 图、编译与执行语义

- 两个节点均为 BATCH Output，恰好一条入边且无出边，只接受 BOUNDED 来源。
- JDBC 目标必须是具有 DISTRIBUTION 用途的 PostgreSQL/HighGo/MySQL/openGauss/人大金仓普通表；模型目标必须是已发布
  MANAGED 模型，其存储数据源具有 STORAGE 用途且为上述数据库。PostgreSQL 家族目标在实际具备 PostGIS 兼容扩展时支持 Geometry。
- 来源字段完成显式映射并 Cast 为目标平台类型后，才参与 Key 校验和比较。
- 来源与目标 Key 必须非 NULL 且各自唯一；Key 不匹配数据库唯一约束只产生 Warning。
- 标量按目标类型精确比较，Geometry 使用 kind/CRS/dimension 一致前提下的拓扑相等。
- 目标独有行默认 KEEP；DELETE 时空来源禁止删除，且候选删除数量和比例必须同时通过阈值。
- Runner 在单条 JDBC 连接和严格表锁内读取目标，并在一个事务中按
  `DELETE → UPDATE → INSERT` 执行；任一步失败整体回滚。
- 运行规模由 Manifest v12 的 `snapshotSyncLimits` 控制，Canvas 定义不保存部署限制。
- 完整规则见 [JDBC 快照同步 Output](canvas-jdbc-snapshot-sync-output-design.md) 和
  [模型快照同步 Output](canvas-model-snapshot-sync-output-design.md)。
