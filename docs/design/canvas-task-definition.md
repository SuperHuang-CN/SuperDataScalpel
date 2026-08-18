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

### 2.1 一个节点只表达一个动作

- 一个 `JDBC_INPUT` 节点只读取一张 JDBC 表。
- 一个 `JDBC_QUERY_INPUT` 节点只执行一条已显式分析的只读 JDBC 查询。
- 一个 `FILE_DATASET_INPUT` 节点只读取一张文件数据集逻辑表。
- 一个 `HTTP_API_INPUT` 节点只读取一个已声明 Schema 的 API 资源。
- 一个 `MODEL_INPUT` 节点只读取一个数据模型。
- 一个 `KAFKA_INPUT` 节点只读取一个 Topic，并持有自己的 Value Schema。
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
- 一个 `SPATIAL_JOIN` 节点只使用受控空间谓词执行一次两表 INNER 连接。
- 一个 `STREAM_JOIN` 节点只执行一次流式表连接。
- 一个 `RENAME` 节点只替换一张逻辑表，并原子重命名该表的零到多个字段。
- 一个 `FILTER` 节点只按一棵结构化条件树筛选一张逻辑表，并产生一个新的逻辑表。
- 一个 `SELECT_COLUMNS` 节点只裁剪并排序一张逻辑表的字段，并产生一个新的逻辑表。
- 一个 `DERIVE_COLUMNS` 节点只通过结构化表达式新增或覆盖字段，并产生一个新的逻辑表。
- 一个 `TYPE_CAST` 节点只显式转换一张来源表中的一个或多个字段类型。
- 一个 `AGGREGATE` 节点只对一张来源表执行一次全表或分组聚合。
- 一个 `UNION` 节点只按字段名纵向合并配置选择的多张逻辑表。
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
- 一个 `KAFKA_OUTPUT` 节点只描述一次向一个 Topic 的写入，并持有自己的 Value Schema。
- 一个 `FILE_OUTPUT` 节点只描述一次向用户指定的外部存储目录写入。
- 多张输入表、多次 Join 或多个输出目标使用多个图节点表达。

这样可以让画布直接表达数据血缘，避免在节点内部再次维护 `items`、`actions`、`mappings` 等小型工作流。

### 2.2 节点间数据使用表名作为 Map Key

节点间传递的数据在概念上表示为：

```text
Map<tableName, CanvasTable>
```

`CanvasTable.name` 是当前数据流中的逻辑表名，同时也是 Map Key：

- `JDBC_INPUT` 初始使用配置的 `tableName`。
- `JDBC_QUERY_INPUT` 使用配置的 `outputTableName`。
- `FILE_DATASET_INPUT` 使用不可修改的 `FileDatasetTable.code`。
- `HTTP_API_INPUT` 使用配置的 `outputTableName`。
- `MODEL_INPUT` 使用模型不可修改且全局唯一的 `code`。
- `KAFKA_INPUT` 使用配置的 `outputTableName`。
- `JOIN` 使用配置的 `outputTableName` 创建新表。
- `GEOMETRY_CONSTRUCT`、`SPATIAL_TRANSFORM`、`GEOMETRY_VALIDATE`、`SPATIAL_MEASURE`、
  `GEOMETRY_SERIALIZE`、`SPATIAL_CLIP`、`SPATIAL_AGGREGATE` 和 `SPATIAL_JOIN` 保留输入表，
  并使用 `outputTableName` 创建新表。
- `RENAME` 处理器替换逻辑表名及 Map Key，但不得修改输入表的物理来源信息。
- `FILTER` 保留全部输入表，并以配置的 `outputTableName` 追加筛选结果。
- `SELECT_COLUMNS` 保留全部输入表，并以配置的 `outputTableName` 追加字段投影结果。
- `DERIVE_COLUMNS` 保留全部输入表，并以配置的 `outputTableName` 追加派生结果。
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

Kafka Value Schema 是消息反序列化和序列化契约，归 `KAFKA_INPUT/KAFKA_OUTPUT` 节点自身所有，因此必须以内联 `valueSchema.columns` 保存。设计器可以把某个已发布模型的当前字段一次性复制进节点，也允许手工编辑或粘贴扁平 JSON Schema；复制完成后不保存模型 ID，后续模型修改不会自动改变 Kafka 节点。

设计器通过现有接口读取数据源、普通 JDBC 表元数据和已保存模型字段，用于配置和组装单次编译
的 `metadataSnapshot`，不进入导出的 Canvas 定义。数据源名称、数据库、Schema、字段列表和元数据
读取状态都属于设计时运行数据。

Task Engine 的编译请求使用独立的 `metadataSnapshot` 携带本次分析所需 Schema。该快照和后续不可变运行快照都不属于本文定义的 Canvas JSON，不得在导入导出时混入定义。

### 2.4 不保存数据源凭据

Canvas 定义中的 JDBC 节点只保存 `dataSourceId`，文件输入只保存 `fileDatasetTableId`，HTTP API 节点只保存 `dataSourceId/resourceId`，模型节点只保存 `modelId/targetModelId`，Kafka 节点只保存数据源 ID、Topic、节点自有 Value Schema 和映射配置，TMQ 节点只保存数据源 ID、Topic、数据库、超级表和定义指纹，文件输出只保存数据源 ID 与相对目录。URL、Broker 地址、对象 Key、物化前缀、用户名、密码、Token、API Key、Secret、签名密钥和其他凭据不得进入 Canvas JSON、节点配置或前端状态持久化结果。

`HTTP_API_INPUT.runtimeParameters` 会随 Canvas 定义明文持久化，只允许保存日期、业务筛选条件、初始游标等非敏感值。动态 Token 必须由 HTTP API 数据源的 OAuth2 或 Token Endpoint 鉴权在执行时生成，不能作为运行时参数绕过凭据边界。

## 3. Canvas JSON 协议

### 3.1 顶层结构

```json
{
  "schemaVersion": 2,
  "schemaMinorVersion": 3,
  "nodes": [],
  "edges": []
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `schemaVersion` | integer | Canvas JSON 协议大版本，当前固定为 `2` |
| `schemaMinorVersion` | integer | Canvas JSON 协议小版本；缺失时按 `0`，当前写出版本为 `3` |
| `nodes` | array | 节点定义，按照前端保存顺序持久化；业务逻辑不得依赖数组顺序 |
| `edges` | array | 有向边定义，业务逻辑不得依赖数组顺序 |

当前写出版本统一为 `2.3`。`2.0/2.1/2.2` 定义仍可读取，保存和导出时规范化为 `2.3`；
`TDENGINE_TMQ_INPUT` 从小版本 1 开始可用，`JDBC_INCREMENTAL_INPUT` 从小版本 2 开始可用，`MODEL_OUTPUT` 的 UPSERT 从小版本 3 开始可用。Canvas `1.x` 与 `2.x` 不兼容：后端必须先读取持久化的协议版本，再决定是否反序列化定义；发现旧大版本时返回明确的不兼容状态，定义内容置空，由用户选择从空白 Canvas `2.3` 重新配置。重新配置只在用户保存后覆盖旧 JSON，不进行跨大版本自动迁移。

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

JDBC 数据源已经固定数据库和 Schema，Canvas 节点只保存该数据源下的 `tableName`，不重复保存 `catalogName` 或 `schemaName`：

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
  "tableName": "orders"
}
```

对应 TypeScript 类型：

```ts
interface JdbcInputConfiguration {
  dataSourceId: string;
  tableName: string;
}
```

第一版不支持在定义中直接填写 JDBC URL、自定义 SQL、分区参数、Driver 参数或任意连接 Options。需要自由 SQL 输入时，应设计独立节点类型，不能扩张 `JDBC_INPUT` 的含义。

### 5.2 输入与输出

- 节点类别：`INPUT`
- 入边数量：必须为 `0`
- 出边数量：至少为 `1`
- 输出 Map：只包含一个条目
- 输出 Key：`configuration.tableName`

概念结果：

```text
{
  "orders" -> CanvasTable(name="orders", origin=JdbcTableOrigin(...))
}
```

### 5.3 校验

- `dataSourceId` 是有效 UUID，数据源存在且已启用。
- 数据源类型是 JDBC，并具有 `SOURCE` 用途。
- `tableName` 完整，且没有包含数据库或 Schema 前缀。
- 通过元数据接口可以定位该表。
- 表字段可以全部映射为平台类型；存在 `LOSSY` 或 `UNSUPPORTED` 映射时校验失败。
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

SQL 最大 100,000 字符，只接受 PostgreSQL/MySQL 的单条 `SELECT` 或 `WITH ... SELECT`。不支持模板变量、运行参数、Session 配置、预览和并行分区读取。Hash 使用去除可选终止分号并 trim 后的 UTF-8 SQL 计算，固定为 64 位小写 SHA-256。

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

- `dataSourceId` 必须是已启用、具有 `SOURCE` 用途的 PostgreSQL 或 MySQL 数据源。
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
  "resourceId": "e67ebceb-78ab-4eb8-bf90-7d428cc8fa09",
  "outputTableName": "api_orders",
  "runtimeParameters": [
    { "name": "startDate", "value": "2026-07-01" }
  ]
}
```

对应 TypeScript 类型：

```ts
interface HttpApiInputConfiguration {
  dataSourceId: string;
  resourceId: string;
  outputTableName: string;
  runtimeParameters: Array<{ name: string; value: string }>;
}
```

节点不保存 Base URL、请求模板、输出 Schema 或凭据。API 资源负责请求、签名、分页、异步轮询和 Schema；Canvas 只提供本次任务固定的非敏感业务参数。

### 6.2 输入与输出

- 节点类别：`INPUT`
- 入边数量：必须为 `0`
- 出边数量：至少为 `1`
- 输出 Map：只包含一个条目
- 输出 Key：`configuration.outputTableName`
- 编译 Schema：来自 API 资源显式声明的 `outputFields`，编译时不访问远程接口

### 6.3 校验

- `dataSourceId/resourceId` 是有效 UUID，且资源确实属于该数据源。
- 数据源类型是 `HTTP_API`、已启用并具有 `SOURCE` 用途；API 资源也必须已启用。
- `outputTableName` 是合法且不冲突的逻辑表名。
- 运行时参数名合法且不重复；参数值不能用于保存敏感凭据。
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

interface JoinConfiguration {
  leftTableName: string;
  rightTableName: string;
  outputTableName: string;
  joinType: JoinType;
  conditions: JoinCondition[];
}
```

第一版只支持等值 Join，多个条件固定使用 `AND` 组合。暂不支持 CROSS、非等值操作符、表达式、OR 条件或隐式类型转换。

### 7.2 输入与输出

- 节点类别：`PROCESSOR`
- 入边数量：必须为 `2`
- 出边数量：至少为 `1`
- 节点先合并两个直接上游输出的表 Map。
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

Join 结果字段顺序固定为左表字段在前、右表字段在后。

第一版不静默修改重名字段。左右表存在同名输出字段时返回 `DUPLICATE_COLUMN_NAME`，由 `RENAME` 处理器在 Join 前显式重命名字段。Join 条件中使用的同名字段也不例外，因为结果同时保留左右两列。

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
- 结果字段名不得重复。

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
- 恰好一条入边，至少一条出边。
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
  ]
}
```

- 类别为 `PROCESSOR`，执行模式为 `BATCH`，协议引入版本为 `1.20`。
- 恰好两条入边，至少一条出边；第一阶段只支持 `INNER`。
- 条件数量为 `1..8`，多条件固定使用 `AND`，重复条件被拒绝。
- 谓词支持 `INTERSECTS/CONTAINS/WITHIN/COVERS/COVERED_BY/TOUCHES/OVERLAPS/CROSSES/EQUALS`。
- 两侧字段必须是 Geometry 且 CRS、dimension 完全一致；不执行隐式 CRS 转换。
- 输出字段顺序为左表后接右表；同名字段返回 `DUPLICATE_COLUMN_NAME`。
- 不支持 `DISJOINT`、`DWITHIN`、外连接和任意表达式。

## 7.7 `GEOMETRY_CONSTRUCT` 节点

节点从 `WKT/WKB/GEOJSON/POINT_FROM_XY` 判别来源构造一个 Sedona Geometry 字段，显式
设置目标 `GeometryKind + EPSG CRS + XY`，并将字段追加到来源字段之后。

- 类别为 `PROCESSOR`，支持 `BATCH/STREAMING`，协议引入版本为 `1.21`。
- 恰好一条入边，至少一条出边；来源 NULL 时输出 NULL。
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
- AREA/PERIMETER 只接受 Polygon/MultiPolygon，LENGTH 只接受
  LineString/MultiLineString，X/Y 只接受 Point。
- DISTANCE 两侧 dimension 必须一致；PLANAR 还要求 CRS 完全一致。
- 完整测量联合和错误码见 [空间测量设计](canvas-spatial-measure-processor-design.md)。

## 7.10 `GEOMETRY_SERIALIZE` 节点

节点保留原 Geometry，并追加 WKT STRING、WKB BINARY 或 GeoJSON STRING 字段。

- 类别为 `PROCESSOR`，支持 `BATCH/STREAMING`，协议引入版本为 `1.21`。
- NULL Geometry 输出 NULL；GeoJSON 只接受 EPSG:4326。
- 不执行隐式坐标转换、精度裁剪，也不支持 EWKT/EWKB/KML/GML。
- Kafka Value Schema 仍不接受 Geometry；写 Kafka 前按需使用 `SELECT_COLUMNS` 移除原字段。
- 完整配置见 [Geometry 序列化设计](canvas-geometry-serialize-processor-design.md)。

## 7.11 `GEOMETRY_REPAIR` 节点

节点保留来源 Geometry，并使用 Sedona `ST_MakeValid(geometry, false)` 追加修复结果字段。

- 类别为 `PROCESSOR`，支持 `BATCH/STREAMING`，协议引入版本为 `1.22`。
- 恰好一条入边，至少一条出边；节点逐行无状态处理。
- NULL 输入输出 NULL；无法修复的真实 Geometry 在运行时失败，不回退原值或静默置 NULL。
- 修复可能改变具体 GeometryKind，因此输出字段固定声明为通用 `GEOMETRY`；CRS 和 dimension
  继承来源字段。
- 完整配置、错误码和安全边界见
  [Geometry 修复设计](canvas-geometry-repair-processor-design.md)。

## 7.12 `GEOMETRY_BUFFER` 节点

节点保留来源 Geometry，并按显式距离模式追加规范化的 MultiPolygon 缓冲字段。

- 类别为 `PROCESSOR`，支持 `BATCH/STREAMING`，协议引入版本为 `1.22`。
- 恰好一条入边，至少一条出边；距离必须是有限正数。
- PLANAR 使用来源 CRS 坐标单位；EPSG:4326 下产生角度单位 Warning。
- SPHEROID 只接受 EPSG:4326，距离单位为米。
- 结果通过 `ST_Multi` 规范化为 `MULTIPOLYGON`，CRS、dimension 和 nullable 继承来源字段。
- 完整配置、错误码和运行依赖见
  [Geometry Buffer 设计](canvas-geometry-buffer-processor-design.md)。

## 7.13 `GEOMETRY_EXPLODE` 节点

节点使用 `ST_Dump` 将 MultiGeometry、GeometryCollection 或普通 Geometry 展开为部件行，
并复制来源行的全部属性。

- 类别为 `PROCESSOR`，支持 `BATCH/STREAMING`，协议引入版本为 `1.22`。
- 恰好一条入边，至少一条出边；节点可能增加行数，但不增加流式状态。
- 使用 outer 展开语义，NULL 或 Empty 输入保留一行并输出 NULL 部件。
- 可选部件序号从 0 开始；NULL 或 Empty 行的序号为 NULL。
- MultiPoint/MultiLineString/MultiPolygon 的输出 kind 分别收窄为
  Point/LineString/Polygon，集合或通用 Geometry 保持通用 `GEOMETRY`。
- 完整配置和错误码见
  [Geometry 拆分设计](canvas-geometry-explode-processor-design.md)。

## 7.14 `SPATIAL_CLIP` 节点

节点使用 Polygon/MultiPolygon Mask 表裁剪来源表的一个 Geometry 字段，并保留来源属性、
追加裁剪结果字段；Mask 属性不进入输出。

- 类别为 `PROCESSOR`，仅支持 `BATCH`，协议引入版本为 `1.23`。
- 恰好两条入边，至少一条出边；来源表和 Mask 表必须不同且均为 BOUNDED。
- 固定使用 `ST_Intersects` INNER 候选连接与 `ST_Intersection`；NULL、Empty 和未命中结果不输出。
- 一个来源行命中多个 Mask 时输出多行，不自动 dissolve、去重或稳定排序。
- 两侧 Geometry 的 CRS 和 dimension 必须一致，Mask kind 只允许 Polygon/MultiPolygon。
- 输出 kind 固定为通用 `GEOMETRY`，CRS 和 dimension 继承来源字段；输出事件时间和 Watermark
  清空。
- 完整配置、错误码和安全边界见
  [空间裁剪设计](canvas-spatial-clip-processor-design.md)。

## 7.15 `SPATIAL_AGGREGATE` 节点

节点按零到多个普通标量字段分组，对一张来源表执行 `UNION/INTERSECTION/COLLECT/ENVELOPE`
空间聚合。

- 类别为 `PROCESSOR`，仅支持 `BATCH`，协议引入版本为 `1.23`。
- 恰好一条入边，至少一条出边；只接受 BOUNDED 来源。
- `groupByColumns=[]` 表示全局聚合；分组字段不得重复或使用 Geometry。
- 聚合项数量为 `1..32`；输出字段名不得重复，也不得与分组字段同名。
- 每个结果独立继承来源 Geometry 的 CRS 和 dimension，kind 固定为通用 `GEOMETRY`，
  nullable 为 true。
- 输出 Schema 为分组字段后接聚合字段；输出事件时间和 Watermark 清空。
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

`JDBC_OUTPUT` 的 Batch 支持三种模式，Streaming 支持 `APPEND/UPSERT` 并拒绝 `OVERWRITE`；Streaming JDBC 输出继续拒绝 Geometry，Batch UPSERT 可以更新非 Key Geometry 字段。`MODEL_OUTPUT` 从 Canvas `2.3` 起同样在 Batch 支持 `APPEND/OVERWRITE/UPSERT`、在 Streaming 支持 `APPEND/UPSERT`；其 UPSERT Key 固定取目标模型完整主键，不写入节点配置，也不检查物理表唯一约束。

UPSERT 写入前检查当前 Dataset 或 micro-batch：Key 含 NULL 返回 `UPSERT_KEY_NULL`，批内重复返回 `UPSERT_DUPLICATE_KEY`，错误不得包含实际 Key 值。每个 Spark 分区使用独立 JDBC 事务，不提供跨分区全局事务；Streaming 是至少一次交付，micro-batch 重放只保证键级收敛，不宣称 Exactly Once。完整运行规则见 [JDBC_OUTPUT UPSERT 设计](canvas-jdbc-output-upsert-design.md)。

### 8.5 校验

- 上游 Map 中存在 `sourceTableName`。
- `dataSourceId` 对应已启用的 JDBC 数据源，并具有 `DISTRIBUTION`（数据分发）用途。
- 目标表存在且可读取元数据。
- 目标对象必须是允许写入的物理表，不能是只读视图。
- 写入模式和字段映射配置完整。
- 字段映射满足目标表必填字段和平台类型兼容要求。
- UPSERT Key 与目标表当前 `uniqueKeys` 中的一整组字段精确匹配，并满足可写、映射和类型限制。
- PostgreSQL/MySQL 以外的数据库不得使用 UPSERT；MySQL 目标存在多组唯一键时返回 `MYSQL_UPSERT_MULTIPLE_UNIQUE_KEYS` Warning。

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

1. 数据源：远程搜索真实数据源，只列出已启用、JDBC 类型、具有 `SOURCE` 用途且支持表和字段元数据读取的数据源；选项展示名称、编码、数据库类型、数据库和 Schema，定义只保存 UUID。
2. 物理表：从所选数据源配置的数据库和 Schema 中搜索真实物理表，不展示视图。选择器使用远程关键字过滤和虚拟列表；结果超过接口的 500 项限制时明确提示继续输入表名筛选，并提供刷新入口。
3. 字段预览：选择表后读取最新字段元数据，只读展示字段名、平台类型、可空性和注释；加载和失败状态必须持续可见并允许重试。

切换数据源时不得静默清空物理表；保留原值并使用新数据源重新解析，表不存在时阻止应用并显示错误。节点卡片配置完成后显示数据源名称、物理表限定名和输出表 Key。

### 9.2A `JDBC_QUERY_INPUT` 面板

表单顺序：

1. 数据源：只列出已启用、具有 `SOURCE` 用途的 PostgreSQL/MySQL 数据源。
2. 输出逻辑表名。
3. Monaco SQL 编辑器。
4. “分析 SQL”操作区，显示未分析、分析中、有效、已过期或失败状态。
5. 上次成功分析得到的只读字段预览。

编辑 SQL 时不得自动访问数据库；保留已有 Hash 和字段快照，并明确标记“分析结果已过期”。用户点击“分析 SQL”成功后才同时替换 `analyzedSqlSha256` 和 `outputColumns`；失败时保留当前 SQL 与原快照。未分析或已过期的草稿允许应用和导出，由 Compiler 返回稳定错误。

导入 JSON 后直接展示定义内快照；Hash 与 SQL 匹配时不强制重新分析。节点卡片和安全摘要只展示数据源、输出表和字段数，不显示 SQL 或其中的字面量。

### 9.3 `HTTP_API_INPUT` 面板

- 数据源下拉只显示已启用、具有 `SOURCE` 用途的 HTTP API 数据源。
- API 资源下拉只显示当前数据源下已启用的资源。
- 输出表名和非敏感运行时参数由用户配置；面板持续提示参数会进入 Canvas JSON，禁止填写 Token、密码、API Key 或 Secret。
- 资源输出字段以只读方式预览，字段变化通过重新读取资源详情和重新编译反映，不复制进 Canvas 定义。

### 9.4 `JOIN` 面板

表单顺序：

1. 左表：从两个直接上游 Map 的无冲突合并结果中选择。
2. 右表：排除已经选择的左表。
3. Join 类型。
4. 输出表名。
5. Join 条件列表：每行选择左字段、操作符和右字段，支持新增和删除。

选择左右表后，字段下拉框只展示当前 Task Engine 节点结果中对应表的 Schema。Engine 使用实际 Spark Analyzer 判断兼容性；Analyzer 接受时不显示平台类型提示，Analyzer 拒绝时显示节点错误。

节点卡片配置完成后显示简要表达式，例如：

```text
orders INNER customers → order_customer
```

当两个上游 Map 存在同名表，或 Join 结果存在同名字段时，配置面板应直接展示冲突名称，不生成自动前缀。

### 9.5 `RENAME` 面板

表单顺序：

1. 来源表：从唯一直接上游的无冲突 Map 中选择一张逻辑表。
2. 输出逻辑表名：始终必填；只改字段名时与来源表名相同。
3. 字段重命名列表：每行选择一个来源字段并填写目标字段名，支持新增和删除。

字段映射按原始 Schema 同时生效，不按配置顺序链式执行，因此 `a -> b, b -> a` 是合法交换。第一次选择来源表且输出表名为空时，前端可以复制来源表名；此后上游变化必须保留已有配置并由 Task Engine 展示失效引用，不得静默清空或改名。

节点卡片显示 `orders -> source_orders` 和字段重命名数量。前端不计算最终字段冲突，来源表和字段选项只使用 Task Engine 返回的 `inputTables`。

### 9.6 `FILTER` 面板

表单顺序：

1. 来源表：从唯一直接上游的无冲突 Map 中选择一张逻辑表。
2. 输出表名：必须与当前输入 Map 中所有表名不同。
3. 条件树：支持嵌套 `AND/OR` 条件组和字段谓词；字段候选只使用 Task Engine 返回的来源 Schema。

操作符控制 Literal 数量：`IS_NULL/IS_NOT_NULL` 不填写值，`IN/NOT_IN` 至少一个值，其余操作符恰好一个值。上游字段失效时保留字段名、操作符和 Literal 并显示错误，不静默清空。节点卡片只显示来源表、输出表和条件数量，不展示 Literal 实际值。

### 9.7 `SELECT_COLUMNS` 面板

表单顺序：

1. 来源表：从唯一直接上游的无冲突 Map 中选择一张逻辑表。
2. 输出表名：必须与当前输入 Map 中所有表名不同。
3. 字段双区选择器：可搜索并添加来源字段，已选字段区支持上移、下移、删除、全选和清空。

已选字段数组顺序就是输出 Schema 顺序；“全选”必须展开为当前明确字段名，不能保存通配符。字段行显示名称、平台类型和 nullable。切换来源表或上游 Schema 变化时保留旧字段及顺序，失效字段原位标红，由用户显式处理。节点卡片只显示来源表、输出表和字段数量。

### 9.8 `DERIVE_COLUMNS` 面板

面板顶部配置来源表和输出表名，中部列出派生字段，局部编辑区配置目标字段名、新增/覆盖模式和结构化表达式。表达式构建器固定支持字段引用、Literal、二元运算、白名单函数和 `CASE_WHEN`，不提供自由 SQL。

同一节点内的字段候选始终只来自 Task Engine 返回的原始来源 Schema，不包含本节点其他派生结果。上游字段失效时保留 AST 并在引用处标红。流任务覆盖事件时间字段时立即提示 `STREAM_EVENT_TIME_COLUMN_IMMUTABLE`。节点卡片只显示新增/覆盖数量和函数类别，不显示 Literal 值。

### 9.9 `TYPE_CAST` 面板

顶部选择来源表和输出表名，下方维护转换项。每项配置来源字段、目标平台类型、STRING/DECIMAL 参数和 `FAIL/SET_NULL` 策略。GEOMETRY 不在可选类型中；上游字段失效时保留原字段名和类型配置。

`SET_NULL` 明确提示真实值转换失败后会变为 NULL，预检不会读取真实数据或统计影响行数。流模式选择事件时间字段时立即标错。节点卡片只显示转换数量与两种策略数量。

### 9.10 `AGGREGATE` 面板

顶部选择来源表和输出表名。分组字段区支持搜索添加、上移、下移和删除；不选择分组字段表示全表聚合。聚合指标区按顺序配置函数、来源字段、`DISTINCT` 和输出字段名；`COUNT` 可选择“全部行（*）”。

上游表或字段失效时保留原值并标红。`MIN/MAX` 的非法 DISTINCT、COUNT(*) 与 DISTINCT 的非法组合、重复输出字段以及与分组字段同名的问题必须在应用前提示，同时由 Task Engine 返回稳定错误。节点卡片只显示来源/输出表、分组字段数和指标数。

### 9.11 `UNION` 面板

输入表使用可排序列表，至少选择两张可见上游逻辑表。第一张表标记为“输出字段顺序基准”。每张表显示字段数和有界性；字段集合差异以紧凑的“缺少/额外”摘要展示，字段类型差异只标记为等待 Spark Analyzer 判断。

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
  "schemaVersion": 2,
  "schemaMinorVersion": 3,
  "nodes": [
    {
      "id": "878f22f4-86cf-4487-b697-5bc34eccb169",
      "type": "JDBC_INPUT",
      "name": "订单输入",
      "layout": { "x": 80, "y": 80, "width": 240, "height": 120 },
      "configuration": {
        "dataSourceId": "c5c021bd-35d1-43ae-bbdb-ff90ff824ba0",
        "tableName": "orders"
      }
    },
    {
      "id": "3952906c-083d-434c-ac9c-d4d388bac74c",
      "type": "JDBC_INPUT",
      "name": "客户输入",
      "layout": { "x": 80, "y": 280, "width": 240, "height": 120 },
      "configuration": {
        "dataSourceId": "c5c021bd-35d1-43ae-bbdb-ff90ff824ba0",
        "tableName": "customers"
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
- `JOIN` 恰好有两条入边。
- `RENAME` 恰好有一条入边。
- `FILTER` 恰好有一条入边。
- `SELECT_COLUMNS` 恰好有一条入边。
- `DERIVE_COLUMNS` 恰好有一条入边。
- `TYPE_CAST` 恰好有一条入边。
- `AGGREGATE` 恰好有一条入边。
- `UNION` 至少有一条入边。
- `DEDUPLICATE` 恰好有一条入边。
- `NULL_HANDLING` 恰好有一条入边。
- `VALUE_MAPPING` 恰好有一条入边。
- `MASK_FIELDS` 恰好有一条入边和一条出边。
- `JSON_EXTRACT` 恰好有一条入边。
- `WINDOW` 恰好有一条入边。
- `TOP_N` 恰好有一条入边。
- `SPATIAL_CLIP` 恰好有两条入边。
- `SPATIAL_AGGREGATE` 恰好有一条入边。
- 除输出节点外，每个节点至少有一条出边。
- 除输入节点外，每个节点至少有一条入边。
- 不允许与有效输入到输出路径无关的游离节点。

Task Engine 中的 Schema 校验按拓扑顺序执行：

1. `JDBC_INPUT` 从数据源读取最新表元数据；`JDBC_QUERY_INPUT` 校验 SQL Hash，并从节点保存的字段快照生成以 `outputTableName` 为 Key 的有界表；`FILE_DATASET_INPUT` 从文件表元数据生成以稳定 code 为 Key 的有界表，`HTTP_API_INPUT` 从资源声明的输出 Schema 生成以 `outputTableName` 为 Key 的表，`MODEL_INPUT` 从模型快照生成以模型 code 为 Key 的表，`KAFKA_INPUT` 直接使用节点内联 Value Schema 生成无界表，`TDENGINE_TMQ_INPUT` 使用 TMQ Topic 元数据快照中的完整超级表字段生成无界表。
2. 节点接收所有直接上游的输出 Map，并执行无覆盖合并。
3. `JOIN` 检查左右表、条件和字段类型，追加结果表。
4. `RENAME` 替换选中表的 Map Key，并使用共享 Spark 投影原子生成新字段 Schema。
5. `FILTER` 使用结构化条件 AST 筛选来源表，保留输入 Map 并追加新的输出表。
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
18. `SPATIAL_CLIP` 合并两个上游 Map，校验 BOUNDED、CRS、dimension 和 Mask kind，追加只含来源属性与裁剪结果的有界表。
19. `SPATIAL_AGGREGATE` 对 BOUNDED 来源执行全局或分组空间聚合，追加清空事件时间和 Watermark 的有界结果表。
20. `JDBC_OUTPUT`、`MODEL_OUTPUT` 和 `KAFKA_OUTPUT` 检查源表、目标及字段映射，但不执行真实写入。
21. 每个节点返回独立校验摘要；图级错误放入单独的 `canvasIssues`，不制造 `@canvas` 伪节点。

建议稳定错误码：

| 错误码 | 含义 |
| --- | --- |
| `UNSUPPORTED_SCHEMA_VERSION` | Canvas 协议版本不受支持 |
| `DUPLICATE_NODE_ID` | 节点 ID 重复 |
| `DUPLICATE_EDGE_ID` | 边 ID 重复 |
| `EDGE_ENDPOINT_NOT_FOUND` | 边引用不存在的节点 |
| `INVALID_NODE_DEGREE` | 节点入边或出边数量不符合类型规则 |
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
| `DUPLICATE_RENAME_SOURCE_COLUMN` | Rename 对同一来源字段配置了多次 |
| `RENAME_HAS_NO_EFFECT` | Rename 的表名和字段名均未发生变化，仅作为警告 |
| `REDUNDANT_RENAME_MAPPING` | Rename 字段映射前后名称相同，仅作为警告 |
| `INVALID_FILTER_CONDITION` | Filter 条件 AST 结构、深度或节点数量无效 |
| `EMPTY_FILTER_GROUP` | Filter 条件组没有子条件 |
| `INVALID_FILTER_OPERATOR` | Filter 操作符无效 |
| `INVALID_FILTER_OPERAND_COUNT` | Filter 操作符与 Literal 数量不匹配 |
| `INVALID_FILTER_LITERAL` | Filter Literal 类型或稳定字符串格式无效 |
| `EMPTY_COLUMN_SELECTION` | Select Columns 没有选择任何字段 |
| `DUPLICATE_SELECTED_COLUMN` | Select Columns 重复选择同一字段 |
| `EMPTY_DERIVATIONS` | Derive Columns 没有配置派生字段 |
| `DUPLICATE_DERIVATION_TARGET` | 同一派生目标字段配置多次 |
| `DERIVATION_TARGET_ALREADY_EXISTS` | 新增模式的目标字段已经存在 |
| `DERIVATION_TARGET_NOT_FOUND` | 覆盖模式的目标字段不存在 |
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

- 节点类别为 `PROCESSOR`，恰好一条入边并至少一条出边。
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
    "fileDatasetTableId": "bd6c3996-5e96-4714-ae65-f35b015f3cd1"
  }
}
```

配置只能保存 `fileDatasetTableId`。数据集 ID、文件 ID、格式、路径、解析参数、Schema、输出表名和存储凭据均由权威元数据及运行 Manifest 提供，不得进入定义。输出逻辑表名固定为不可修改的 `FileDatasetTable.code`。

### 15.2 图与编译语义

- 类别为 `INPUT`，入边必须为 0，出边至少为 1。
- 仅支持 `BATCH`，输出 `BOUNDED` Dataset；流任务可以导入回显，但 Compiler 返回 `NODE_EXECUTION_MODE_NOT_SUPPORTED`。
- 表、来源文件和 Schema 必须存在，且表和文件都处于 `READY`。
- Compiler 只使用 `metadataSnapshot.fileDatasetTables` 创建显式 Schema 的零行 Dataset，不访问对象存储，也不重新推断 Schema。
- 输出 Map 只有以表 code 为 Key 的一个条目，来源用 `fileDatasetTableId` 明确标识；与其他上游同名时继续返回 `DUPLICATE_TABLE_NAME`。

### 15.3 设计器

Batch Palette 展示“文件数据集输入”，Streaming Palette 隐藏。Inspector 依次选择文件数据集和 `READY` 逻辑表，并只读展示字段 Schema。已经保存的表失效、删除或变为不可用时保留原 UUID，等待 Compiler 展示权威错误，不静默清空配置。

## 16. Kafka 节点内联 Value Schema

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
    "outputTableName": "order_events",
    "startingOffsets": "LATEST"
  }
}
```

`KAFKA_OUTPUT` 使用相同的 `valueSchema` 结构，并额外配置 `sourceTableName`、可选 `keyColumnName` 和 `columnMappings`。Value Schema 至少包含一个字段，字段名必须唯一；`STRING` 可设置正整数 `length`，`DECIMAL` 必须设置 `precision: 1..38` 和 `scale: 0..precision`，其他类型不得携带这三个参数。

配置中不再存在 `valueModelId`。Kafka 节点不是模型节点，不创建任务模型引用，也不会在发布、编译或运行准备时查询模型。没有历史 Kafka 定义需要迁移，因此 `1.4` 及更低版本携带 Kafka 节点时直接拒绝，不保留旧字段兼容分支。

### 16.2 设计器与编译语义

- Inspector 支持手工增删、排序和编辑字段。
- “从模型 Schema 导入”只读取一次当前已发布模型字段并复制成内联列；选择结果和模型 ID 不进入定义。
- “粘贴 JSON Schema”第一阶段只接受根类型为 object 的扁平 properties，支持 boolean、integer、number、string、date 和 date-time；嵌套 object、array 明确拒绝。
- `KAFKA_INPUT` Operator 直接把内联列转为 Spark Schema，并输出 `UNBOUNDED` 表；Compiler 和 Runner 不需要模型元数据。
- `KAFKA_OUTPUT` Operator 直接把内联列作为目标 Schema，与 JDBC/模型 Output 复用字段映射和显式 Spark Cast。
- Schema 缺失、空字段、重复字段、非法类型参数属于 Compiler `ERROR`；模型是否仍存在、是否变更与节点有效性无关。

## 16A. `TDENGINE_TMQ_INPUT`

该节点从 Canvas `2.1` 开始提供，只支持 `STREAMING`：

```json
{
  "dataSourceId": "bd6c3996-5e96-4714-ae65-f35b015f3cd1",
  "topicName": "meters_topic",
  "catalogName": "power",
  "supertableName": "meters",
  "topicDefinitionFingerprint": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
  "outputTableName": "meter_events",
  "startingOffsets": "EARLIEST",
  "maxOffsetsPerVGroupPerTrigger": 10000
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
- 节点入边为 0、出边至少 1，产生以 `outputTableName` 为 Key 的 `UNBOUNDED` 表；Schema 来自超级表
  字段与 TAG，不增加 Topic、子表、VGroup 或 Offset 技术列。
- Consumer Group、Client ID、自动提交和任意 TMQ Properties 均不是用户配置；完整运行及 Offset
  语义见 [TDengine TMQ 输入](tdengine-tmq-input-development-plan.md)。

## 16B. `JDBC_INCREMENTAL_INPUT`

该节点从 Canvas `2.2` 开始提供，只支持 `STREAMING`。它按固定间隔读取普通 JDBC 物理表的完整时间窗口：

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
  "sourceTableName": "orders",
  "outputTableName": "paid_orders",
  "condition": {
    "kind": "GROUP",
    "operator": "AND",
    "children": [
      {
        "kind": "PREDICATE",
        "columnName": "status",
        "operator": "IN",
        "values": [
          { "dataType": "STRING", "value": "PAID" },
          { "dataType": "STRING", "value": "SHIPPED" }
        ]
      },
      {
        "kind": "PREDICATE",
        "columnName": "amount",
        "operator": "GREATER_THAN",
        "values": [
          { "dataType": "DECIMAL", "value": "100.00" }
        ]
      }
    ]
  }
}
```

条件是以 `kind` 为判别字段的递归联合：

- `GROUP`：`operator` 为 `AND/OR`，`children` 至少一项。
- `PREDICATE`：包含 `columnName`、操作符和 `values`。
- 操作符固定为 `EQUALS`、`NOT_EQUALS`、`GREATER_THAN`、`GREATER_THAN_OR_EQUALS`、`LESS_THAN`、`LESS_THAN_OR_EQUALS`、`IN`、`NOT_IN`、`IS_NULL`、`IS_NOT_NULL`、`CONTAINS`、`STARTS_WITH`、`ENDS_WITH`。
- Literal 保存 `PlatformDataType + string/null value`。Boolean 使用 `true/false`，日期使用 `yyyy-MM-dd`，Timestamp 使用带时区的 ISO-8601，Timestamp NTZ 使用无时区 ISO-8601，Binary 使用 Base64。
- 条件最大嵌套深度为 12，总节点数最多 256，单个 `IN/NOT_IN` 最多 100 个值。
- `IS_NULL/IS_NOT_NULL` 的 values 必须为空，`IN/NOT_IN` 至少一项，其余操作符恰好一项。同一谓词中的 Literal 类型必须一致。
- 首期不支持字段与字段比较、SQL 片段、子查询、运行参数或自定义函数。

### 17.2 Map、Schema 与批流语义

- 节点类别为 `PROCESSOR`，恰好一条入边、至少一条出边。
- 支持 `BATCH` 和 `STREAMING`；两种模式使用同一配置和同一个无状态 `FilterNodeOperator`。
- 从合并后的输入 Map 中按 `sourceTableName` 精确取表。
- 复制全部输入 Map，以 `outputTableName` 追加过滤结果。输出名与任何现有 Key 冲突时返回 `DUPLICATE_TABLE_NAME`，包括来源表名。
- 输出字段、顺序、Origin、有界性、事件时间和 Watermark 全部继承来源表。
- Compiler 使用零行 Dataset 和同一 Operator 构造实际 Spark 条件并由 Analyzer 判断可执行性，不读取真实数据。
- 节点安全摘要只记录来源/输出表、谓词数、条件组数和操作符集合，不记录 Literal 实际值。

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

- 节点类别为 `PROCESSOR`，恰好一条入边、至少一条出边。
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
  "sourceTableName": "orders",
  "outputTableName": "orders_enriched",
  "derivations": [
    {
      "targetColumnName": "amount_with_tax",
      "replaceExisting": false,
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
  ]
}
```

表达式使用 `kind` 判别联合：

- `COLUMN`：`columnName` 引用进入节点时的原始来源字段。
- `LITERAL`：复用 FILTER 的 `CanvasLiteral`。
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

- 节点类别为 `PROCESSOR`，恰好一条入边、至少一条出边，支持 `BATCH` 和 `STREAMING`。
- 同一节点内所有表达式只引用原始来源 Schema；后一项不能引用前一项新建的字段。
- Operator 基于原始 Dataset 构造一次最终 `select`：覆盖字段保留原位置，新增字段按 derivations 顺序追加。
- `replaceExisting=false` 要求目标字段不存在；`replaceExisting=true` 要求目标字段存在；目标名不能重复。
- 复制全部输入 Map，以 `outputTableName` 追加结果；输出名与任何现有 Key 冲突时返回 `DUPLICATE_TABLE_NAME`。
- 未改变字段完整继承平台元数据。派生字段的类型和 nullable 取 Analyzer 结果，默认值、自增、生成列和注释等物理属性清空。
- Origin、有界性、事件时间和 Watermark 默认继承来源表。流模式禁止覆盖事件时间字段。
- Compiler 与 Runner 使用同一个无状态 `DeriveColumnsNodeOperator`；CASE 条件和 FILTER 共享同一个谓词表达式构建器。
- 安全摘要只记录来源/输出表、目标字段名、新增/覆盖数量、表达式 kind 和函数名集合，不记录 Literal、生成 SQL 或数据行。

## 20. `TYPE_CAST` 处理器

### 20.1 稳定配置

```json
{
  "sourceTableName": "orders",
  "outputTableName": "typed_orders",
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
      "columnName": "submitted_at_text",
      "targetType": {
        "type": "TIMESTAMP",
        "length": null,
        "precision": null,
        "scale": null,
        "geometry": null
      },
      "failureStrategy": "SET_NULL"
    }
  ]
}
```

- `casts` 至少一项，同一字段只能配置一次。
- `targetType` 直接使用稳定 `PlatformTypeDefinition`；STRING 可设置正整数 length，DECIMAL 必须设置 `precision: 1..38` 和 `scale: 0..precision`，其他标量类型不携带参数。
- Spark Canvas 首期不支持 GEOMETRY 转换，Inspector 不展示，Operator 返回 `INVALID_TARGET_PLATFORM_TYPE`。
- `FAIL` 使用开启 ANSI 语义的普通 Spark cast；真实值无法转换时节点运行失败。
- `SET_NULL` 使用 Spark `Column.try_cast(DataType)`；真实值无法转换时结果为 NULL。

### 20.2 Map、Schema 与批流语义

- 节点类别为 `PROCESSOR`，恰好一条入边、至少一条出边，支持 `BATCH` 和 `STREAMING`。
- Operator 基于来源 Dataset 构造一次 `select`；未转换字段直接投影，转换字段在原位置使用原字段名 alias。
- 复制全部输入 Map，以 `outputTableName` 追加结果；输出名与任何现有 Key 冲突时返回 `DUPLICATE_TABLE_NAME`。
- 未转换字段完整继承元数据。转换字段类型来自目标平台类型和 Analyzer，物理默认值、自增、生成列和注释清空。
- `FAIL` 的 nullable 取 Analyzer 结果；`SET_NULL` 固定 nullable 为 true。
- Origin、有界性、事件时间和 Watermark 默认继承来源表；流模式禁止转换事件时间字段。
- Cast 能否建立计划只由 Spark Analyzer 判断；Compiler 不读取真实值，也不承诺运行时数据一定可转换。
- Compiler 与 Runner 使用同一个无状态 `TypeCastNodeOperator`，不修改全局 ANSI 配置，不拼接用户 SQL。
- 安全摘要只记录来源/输出表、字段名、目标平台类型和失败策略，不记录字段值、失败值或生成 SQL。

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

- 节点类别为 `PROCESSOR`，恰好一条入边、至少一条出边，仅支持 `BATCH`。
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
  "mode": "ALL"
}
```

- `inputTableNames` 至少包含两个不同表名；数组顺序稳定，第一张表决定输出字段顺序。
- 每张输入表必须具有完全相同的字段名集合，字段原始顺序可以不同。
- 仅支持按字段名合并，不支持按位置合并、缺失字段补 NULL、忽略额外字段或自动重命名。
- `ALL` 保留重复行；`DISTINCT` 对完整 Union 结果执行全字段去重。

### 22.2 Map、Schema 与批流语义

- 节点类别为 `PROCESSOR`，至少一条入边、至少一条出边，支持 `BATCH` 和 `STREAMING`。
- 直接上游 Map 仍先按普通规则合并；同名 Key 在进入 UNION 前就返回 `DUPLICATE_TABLE_NAME`。
- Operator 按配置顺序查找表，使用第一张表字段顺序，以 `unionByName` 合并其余 Dataset。
- 字段类型兼容和必要提升由 Spark Analyzer 判断。输出类型和 nullable 取最终 Analyzer Schema，来源专属物理属性和注释清空，`origin=null`。
- 复制全部输入 Map，以 `outputTableName` 追加结果；输出名与任何现有 Key 冲突时返回 `DUPLICATE_TABLE_NAME`。
- 所有输入必须具有相同 `datasetKind`：全部有界输出 `BOUNDED`，全部无界输出 `UNBOUNDED`，混合输入拒绝。
- 无界输入的 `eventTimeColumn` 和 `watermarkDelay` 必须全部一致并由输出继承；有界输出清空这两个字段。
- 无界输入只支持 `ALL`；`DISTINCT` 会形成无边界状态，因此返回 `STREAMING_UNION_DISTINCT_NOT_SUPPORTED`。
- Compiler 与批流 Runner 使用同一个无状态 `UnionNodeOperator`。安全摘要只记录输入/输出表名、输入数量和模式，不记录数据值或重复行样本。

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

- 节点类别为 `PROCESSOR`，恰好一条入边、至少一条出边，仅支持 `BATCH`。
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

- 节点类别为 `PROCESSOR`，恰好一条入边、至少一条出边；最低协议版本为 Canvas `1.14`。
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

- 节点类别为 `PROCESSOR`，恰好一条入边、至少一条出边；最低协议版本为 Canvas `1.15`。
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

- 节点类别为 `PROCESSOR`，恰好一条入边、至少一条出边；最低协议版本为 Canvas `1.16`，仅支持 `BATCH` 和 `BOUNDED` 来源。
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

- 节点类别为 `PROCESSOR`，恰好一条入边、至少一条出边；最低协议版本为 Canvas `1.17`，仅支持 `BATCH` 和 `BOUNDED` 来源。
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
- `KEEP_LENGTH_MASK` 将每个字符替换为掩码字符。
- `FIXED_VALUE` 使用最长 1024 字符的固定字符串替换。
- `NULLIFY` 使用原字段类型的 `null` 替换。
- 所有策略对输入 `null` 保持 `null`；掩码字符默认 `*`，配置时必须是一个 Unicode 字符。

全局规则只是配置模板。任务保存、发布、编译和运行不查询规则、不比较定义、不检查来源是否存在，也不以全局当前定义覆盖节点配置。编辑时的变化和删除提示仅由 Inspector 通过普通规则详情接口完成，详见[数据脱敏规则和字段脱敏节点](data-masking-rules.md)。

### 28.2 Map、Schema 与批流语义

- 节点类别为 `PROCESSOR`，恰好一条入边和一条出边；最低协议版本为 Canvas `1.18`，支持 `BATCH` 和 `STREAMING`。
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

- 节点类别为 `PROCESSOR`，恰好一条入边、至少一条出边；最低协议版本为 Canvas `1.19`。
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

`FILE_OUTPUT` 从 Canvas `1.6` 引入，公共配置保持不变：

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
- `SHAPEFILE`：从 Canvas `1.24` 开始可用；低版本携带该格式时返回
  `FORMAT_OPTION_REQUIRES_SCHEMA_VERSION`。
- `GEOPARQUET`：从 Canvas `1.25` 开始可用；配置为
  `geometryColumnName + compression(SNAPPY|ZSTD) + coveringMode(NONE|ROW_BBOX)`。
- `GEOJSON`：从 Canvas `1.25` 开始可用；配置为
  `baseName + geometryColumnName + idColumnName + ignoreNullProperties`。

`GEOPARQUET/GEOJSON` 在低于 `1.25` 的定义中同样返回
`FORMAT_OPTION_REQUIRES_SCHEMA_VERSION`。

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

`JDBC_SNAPSHOT_SYNC_OUTPUT` 与 `MODEL_SNAPSHOT_SYNC_OUTPUT` 是 Canvas `2.0` 的正式节点，使用统一的显式字段映射配置：

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
- JDBC 目标必须是具有 DISTRIBUTION 用途的 PostgreSQL/MySQL 普通表；模型目标必须是已发布
  MANAGED 模型，其存储数据源具有 STORAGE 用途且为 PostgreSQL/MySQL。
- 来源字段完成显式映射并 Cast 为目标平台类型后，才参与 Key 校验和比较。
- 来源与目标 Key 必须非 NULL 且各自唯一；Key 不匹配数据库唯一约束只产生 Warning。
- 标量按目标类型精确比较，Geometry 使用 kind/CRS/dimension 一致前提下的拓扑相等。
- 目标独有行默认 KEEP；DELETE 时空来源禁止删除，且候选删除数量和比例必须同时通过阈值。
- Runner 在单条 JDBC 连接和严格表锁内读取目标，并在一个事务中按
  `DELETE → UPDATE → INSERT` 执行；任一步失败整体回滚。
- 运行规模由 Manifest v12 的 `snapshotSyncLimits` 控制，Canvas 定义不保存部署限制。
- 完整规则见 [JDBC 快照同步 Output](canvas-jdbc-snapshot-sync-output-design.md) 和
  [模型快照同步 Output](canvas-model-snapshot-sync-output-design.md)。
