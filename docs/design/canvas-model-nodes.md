# Canvas ModelInput 与 ModelOutput 设计

## 1. 范围

本文定义当前 Canvas `2.x` 协议中的两个模型节点。它们属于 Canvas `2.0` 基础能力，
`MODEL_OUTPUT` 的批流 UPSERT 从 Canvas `2.3` 开始支持：

- `MODEL_INPUT`：读取一个已发布数据模型对应的物理表。
- `MODEL_OUTPUT`：把一个上游逻辑表写入一个已发布数据模型。

模型节点复用模型管理已经确定的模型 UUID、不可修改 `code`、`schemaVersion`、物理表模式、字段定义和生命周期。Canvas Definition 只保存模型 UUID；模型名称、字段、数据源、物理位置和凭据都在设计时编译或运行准备阶段解析，不进入稳定定义。

当前范围只做全量读取和现有 Spark JDBC 写入，不实现任意读取条件、自定义 Spark/JDBC Options、自动建表、自动 Schema 演进或跨输出事务。

## 2. 核心决策

### 2.1 一个节点只处理一个模型

一个 `MODEL_INPUT` 只读取一个模型，一个 `MODEL_OUTPUT` 只写入一个模型。多个输入或输出使用多个节点表达，不在节点配置内维护 `items` 或 `mappings` 子工作流。

### 2.2 模型 code 是逻辑表名

`MODEL_INPUT` 使用模型不可修改、全局唯一的 `code` 作为 `Map<tableName, CanvasTable>` 的 Key。模型展示名称和物理位置变化不会改变下游表名；字段新增且未被下游引用时，任务通常无需编辑。

两个上游出现相同 Key 时仍返回 `DUPLICATE_TABLE_NAME`，不得因为来源模型 UUID 相同而去重。需要重复读取或自连接时由 `RENAME` 节点在分支合并前显式解决。

### 2.3 Definition 只保存模型引用

Definition 不保存模型名称、code、schemaVersion、字段、数据源 ID、Catalog、Schema、物理表名或连接信息。发布、启用和每次运行都必须根据当前模型重新生成权威快照并重新编译。

模型兼容演进不强制修改 Definition；模型字段删除、重命名或类型不兼容时，由编译器指出真正失效的下游配置。

### 2.4 模型快照不可变

设计器的模型快照只用于实时提示。Admin 在发布、启用和运行时重新读取模型、已保存字段和数据源，
构建不可变 `metadataSnapshot.models`，不读取物理表做结构相等检查。Runner 只使用 manifest 中的
快照，不访问 Admin 或管理数据库。

## 3. 稳定节点协议

### 3.1 MODEL_INPUT

```json
{
  "id": "69324552-dae6-4a2d-b315-e5ff9f645d20",
  "type": "MODEL_INPUT",
  "name": "订单模型输入",
  "layout": { "x": 120, "y": 160, "width": 240, "height": 120 },
  "configuration": {
    "modelId": "4bbd56c6-5c4f-4af7-8860-5adecc1c29bd"
  }
}
```

```ts
interface ModelInputConfiguration {
  modelId: string;
}
```

- 类别：`INPUT`
- 入边：必须为 `0`
- 出边：至少为 `1`
- 输出：只包含模型 `code` 对应的一个逻辑表
- 执行阶段：`READ`

### 3.2 MODEL_OUTPUT

```json
{
  "id": "e5b20cab-f4db-4edb-9618-b4573decb81d",
  "type": "MODEL_OUTPUT",
  "name": "订单明细模型输出",
  "layout": { "x": 820, "y": 160, "width": 240, "height": 120 },
  "configuration": {
    "sourceTableName": "order_customer",
    "targetModelId": "aa9a0258-707e-4614-bbf0-c62a6df3356a",
    "writeMode": "APPEND",
    "columnMappings": [
      { "sourceColumnName": "order_id", "targetColumnName": "order_id" },
      { "sourceColumnName": "customer_name", "targetColumnName": "customer_name" }
    ]
  }
}
```

```ts
interface ModelOutputConfiguration {
  sourceTableName: string;
  targetModelId: string;
  writeMode: 'APPEND' | 'OVERWRITE' | 'UPSERT' | null;
  columnMappings: CanvasColumnMapping[];
}
```

- 类别：`OUTPUT`
- 入边：必须为 `1`
- 出边：必须为 `0`
- 输出：不产生下游表 Map
- 执行阶段：`WRITE`

`CanvasColumnMapping` 与现有 JDBC 输出映射共享 JSON 结构。代码层应使用通用名称和公共校验逻辑，避免 JDBC 与模型输出规则分叉。

## 4. 模型元数据快照

`metadataSnapshot` 增加必填 `models` 数组；没有模型节点时固定为空数组：

```json
{
  "dataSources": [],
  "models": [
    {
      "id": "4bbd56c6-5c4f-4af7-8860-5adecc1c29bd",
      "code": "order_detail",
      "name": "订单明细",
      "schemaVersion": 3,
      "status": "PUBLISHED",
      "physicalTableMode": "MANAGED",
      "dataSourceId": "1a16de93-5da8-40b5-bbea-e5c8813d55a3",
      "catalogName": "warehouse",
      "schemaName": "public",
      "physicalTableName": "dwd_order_detail",
      "columns": []
    }
  ]
}
```

每个模型快照固定包含：

- 模型 UUID、code、名称、schemaVersion 和状态。
- `MANAGED/EXTERNAL` 物理表模式。
- 数据源 UUID和精确物理位置。
- 按模型字段顺序排列的平台字段 Schema。

字段标识使用 `DataModelField.code`，展示名称不参与 Spark 字段解析。字段类型参数、可空性和
Geometry 定义直接来自模型字段；`defaultValue=null`、`autoIncrement=false`、`generated=false`。
按字段顺序排列的 `primaryKey=true` 字段构造逻辑主键元数据。

同一模型 UUID、同一模型 code 或模型内重复字段会使快照产生歧义，Task Engine 必须以 `INVALID_METADATA_SNAPSHOT` 拒绝请求。

## 5. 可用性与写入边界

模型节点支持具有完整 EPSG + XY 类型定义的 Geometry 字段；实时 `MODEL_OUTPUT` 仍明确禁止
Geometry 写入。Kafka 内联 Value Schema 不接受 Geometry。Task Engine 的 `SparkTypeMapper`
不得将 Geometry 降级为 String/Binary。

`MODEL_INPUT` 要求：

- 模型存在、状态为 `PUBLISHED` 且至少有一个字段。
- 关联数据源已启用、属于 JDBC，且具有 `SOURCE` 或 `STORAGE` 用途。
- 模型物理位置配置完整；运行前不比较物理表与模型字段是否完全一致。
- 当前 Runner 支持该数据库产品。
- `MANAGED` 和 `EXTERNAL` 都允许读取。

`MODEL_OUTPUT` 要求：

- 目标模型存在、状态为 `PUBLISHED` 且至少有一个字段。
- 数据源已启用、属于 JDBC 且具有 `STORAGE` 用途。
- `APPEND` 允许写入 `MANAGED` 或 `EXTERNAL`。
- `OVERWRITE` 只允许支持该模式的数据库和 `MANAGED` 模型，固定语义为先 `TRUNCATE`、再 append，
  不得 Drop/Recreate。
- `UPSERT` 只允许 PostgreSQL/MySQL，可写入 `MANAGED` 或 `EXTERNAL`，Key 自动取目标模型按字段
  顺序声明的完整主键；模型无主键或主键未全部映射时编译失败。
- Batch 对普通 JDBC 数据库支持 `APPEND`，并按数据库能力开放 `OVERWRITE/UPSERT`；Streaming
  支持 `APPEND`，仅 PostgreSQL/MySQL 支持 `UPSERT`，继续禁止 `OVERWRITE` 和 Geometry。
- 平台不检查物理表是否存在对应主键或唯一约束，由实际数据库 UPSERT 结果判定成功或失败。
- 当前 Runner 支持该数据库产品。

普通标量 `MODEL_INPUT` 支持 PostgreSQL、MySQL、openGauss、Kingbase、Oracle、SQL Server、
ClickHouse 和达梦。`MODEL_OUTPUT` 对这些数据库开放 APPEND；OVERWRITE 仅支持 PostgreSQL、
MySQL、openGauss 和 Kingbase，UPSERT 仅支持 PostgreSQL/MySQL，Geometry 仅支持
PostgreSQL/PostGIS 和 MySQL 8。设计器根据模型存储数据源禁用不支持的模式，但必须保留并标红
已保存的失效值，使无效草稿仍可应用和保存。

平台不仲裁输出目标占用关系。同一个模型可以在一个 Definition 中同时作为 `MODEL_INPUT` 和 `MODEL_OUTPUT`，多个 `MODEL_OUTPUT` 也可以指向同一个目标模型；不同任务同样可以写入同一个模型或物理表。每个节点按自身配置形成独立读写计划，并发覆盖、数据库锁竞争和最终数据结果由底层 Sink 语义与实施配置决定。

## 6. Schema 传播与字段映射

`MODEL_INPUT` 编译时使用模型 code 创建逻辑表，Origin 使用判别结构：

```json
{
  "kind": "MODEL",
  "modelId": "4bbd56c6-5c4f-4af7-8860-5adecc1c29bd",
  "modelCode": "order_detail",
  "schemaVersion": 3
}
```

`MODEL_OUTPUT` 与 `JDBC_OUTPUT` 共用显式字段映射规则：

- 映射至少一项，来源和目标字段必须存在，同一目标字段只能映射一次。
- 目标必填且无默认值、非自动生成的字段必须被覆盖。
- 未映射来源字段直接忽略。
- 设计器可将完全同名、忽略大小写或驼峰/下划线等价的字段自动填入映射；自动匹配结果直接保存为普通 `columnMappings`，运行时不再动态匹配。
- 字段映射使用与 `JDBC_OUTPUT` 相同的显式 Spark Cast：安全转换直接通过，可能因实际值失败的转换产生警告，Analyzer 不支持的转换才产生错误。
- nullable 来源写入 non-null 目标、STRING 长度收窄和 DECIMAL 精度/小数位收窄分别产生风险警告，不在预检阶段扫描真实数据。

稳定字段转换问题包括：

- `COLUMN_CAST_RISK`
- `NULLABILITY_RISK`
- `STRING_LENGTH_RISK`
- `DECIMAL_PRECISION_RISK`
- `UNSUPPORTED_COLUMN_CAST`

稳定编译错误至少包括：

- `MODEL_ID_REQUIRED`
- `MODEL_NOT_FOUND`
- `MODEL_NOT_PUBLISHED`
- `MODEL_DATA_SOURCE_UNAVAILABLE`
- `UNSUPPORTED_MODEL_DATA_SOURCE`
- `OVERWRITE_REQUIRES_MANAGED_MODEL`
- `OVERWRITE_DATABASE_NOT_SUPPORTED`
- `UPSERT_DATABASE_NOT_SUPPORTED`
- `UPSERT_KEY_REQUIRED`
- `UPSERT_KEY_NOT_MAPPED`
- `UPSERT_KEY_COLUMN_NOT_ALLOWED`
- `STREAMING_MODEL_OUTPUT_OVERWRITE_NOT_SUPPORTED`

字段和表错误继续复用既有错误码，不创建同义错误。

## 7. Admin 引用与并发一致性

Canvas JSON 是配置真源。Admin 维护派生表 `task_canvas_model_reference`，字段为 `task_id`、`node_id`、`model_id`、`reference_role: INPUT | OUTPUT` 和基础审计字段，唯一约束为 `(task_id, node_id)`，并按 `task_id`、`model_id` 建索引。

Definition 保存时在同一事务中整体替换引用投影。无效草稿允许保存；空 modelId 不生成投影，合法 UUID即使当前模型不存在也保留引用。该投影仅用于模型删除保护、任务删除清理和后续模型相关任务查询，不参与执行配置解析。

发布、启用和运行的最终事务必须重新检查：

- Canvas definitionVersion 未变化。
- 所有模型仍存在，状态、updatedAt 和 schemaVersion 未变化。
- 所有数据源仍启用且 updatedAt 未变化。
- 计算引擎路由未变化。

模型在任务入队后变化不修改已生成 manifest；Runner 使用入队时的模型逻辑快照。该快照用于
Schema 传播、Spark 计划和字段解析，不要求运行时物理表与快照完全一致；真实读取、分析、转换
或写入无法完成时，由 Spark、JDBC 驱动或目标数据库返回实际执行错误。

## 8. Runner、日志与结果

`MODEL_INPUT` 从模型快照取得物理位置和数据源，使用 Spark JDBC 读取，然后以模型 code 写入表
Map。Runner 不比较物理字段数量、顺序、类型参数或可空性；成功只表示读取计划已准备，不为日志
触发 Spark Action。

`MODEL_OUTPUT` 使用模型快照定位目标，按公共映射逻辑执行 `select + alias`，复用现有输出指标采集，不单独 `count()`。Batch UPSERT 复用通用 JDBC UPSERT；Streaming 使用独立 `foreachBatch` 查询和 Checkpoint，按至少一次交付。多个输出不提供跨输出事务。

节点日志摘要允许包含 modelId、modelCode、modelSchemaVersion、dataSourceId、物理表、来源表、写入模式和映射数量；禁止记录数据行、字段值、凭据或完整 manifest。

未分类失败按节点回退为 `MODEL_INPUT_FAILED` 或 `MODEL_OUTPUT_FAILED`；底层 JDBC SQLState 分类优先于节点回退。`result.json` 保持 `schemaVersion: 2`，Dispatcher 严格节点类型白名单增加 `MODEL_INPUT`、`MODEL_OUTPUT`，不新增结果字段。

## 9. 设计器交互

Palette 在现有 HTTP API 输入节点之外增加“模型输入”和“模型输出”，并按输入、处理器、输出分组展示。模型选择使用现有模型列表和详情 API，只列出已发布模型；已经保存但后来不可用的模型必须保留原值并展示错误，不得静默清空。

`MODEL_INPUT` 面板展示模型名称、code、schemaVersion、物理模式、数据源、物理位置和只读字段列表。`MODEL_OUTPUT` 面板依次配置来源表、目标模型、写入模式和固定的“目标字段 ← 来源字段”映射；选择 EXTERNAL 目标时禁用 OVERWRITE，选择 UPSERT 时只读展示模型主键，不提供 Key 配置。

前端只负责安全解析、表单护栏和元数据组装。模型状态、图语义、Schema 传播及字段兼容性仍由 Task Engine 给出唯一结论；前后端都不检查模型或物理 Sink 是否被其他节点、任务或运行实例同时使用。

## 10. 版本与非目标

- Canvas Definition 当前写出版本为 `2.3`；模型输入、模型输出和统一显式字段映射均属于 `2.0`，模型 UPSERT 属于 `2.3` 增量能力。
- Execution manifest 当前版本为 `18`，`metadataSnapshot.models` 必填。
- Runner `result.json` 保持版本 `2`。
- 当前系统把保存、返回、生成 Manifest 和导出统一规范化为 `2.3`；Canvas `1.x` 定义不反序列化、不迁移，由用户重新配置。Kafka 节点的“从模型导入 Schema”只是前端一次性复制，不创建本节定义的模型引用。
- `MODEL_INPUT` 和 `MODEL_OUTPUT` 均为 Canvas `2.0` 的正式节点。
- PostgreSQL 存量升级和模型引用回填使用
  [spark-canvas-admin-postgresql.sql](../operations/spark-canvas-admin-postgresql.sql)，不重建任务、运行记录或 Canvas 定义表。

第一阶段不实现：时间增量、过滤下推、模型 Schema 自动修改、目标表自动创建、任意 Spark/JDBC Options、新数据库的 OVERWRITE/UPSERT/Geometry 扩展、自动重试、跨输出事务以及模型/物理目标占用治理。
