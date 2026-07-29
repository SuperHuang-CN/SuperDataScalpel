# Canvas 任务定义与节点设计

## 1. 文档范围

本文定义 `CANVAS` 任务的稳定协议、图结构语义和正式节点配置：

- `MODEL_INPUT`
- `JDBC_INPUT`
- `FILE_DATASET_INPUT`
- `HTTP_API_INPUT`
- `KAFKA_INPUT`
- `JOIN`
- `STREAM_JOIN`
- `RENAME`
- `MODEL_OUTPUT`
- `JDBC_OUTPUT`
- `KAFKA_OUTPUT`
- `FILE_OUTPUT`

模型节点的配置、模型快照、引用投影和运行边界以 [Canvas ModelInput 与 ModelOutput 设计](canvas-model-nodes.md) 为准。

可视化任务设计器负责 JSON 导入导出，并通过 Admin 网关调用 Task Engine 完成图分析、Schema 传播和业务校验。正式的 `SPARK_CANVAS` 任务定义页通过 Admin 保存和读取稳定定义；独立的 `/task/orchestration` 仍作为不持久化的试验页。节点配置阶段只读复用现有数据源列表、物理表和字段元数据接口。真实执行使用独立 manifest 和 Runner，不向本文定义的稳定 Canvas JSON 写入运行连接或凭据。

Canvas 定义必须是与 AntV X6、Java 类名和未来执行引擎解耦的稳定 JSON。X6 只负责编辑和展示，不得直接持久化 X6 Cell、Shape、Port 或运行时状态。

## 2. 核心决策

### 2.1 一个节点只表达一个动作

- 一个 `JDBC_INPUT` 节点只读取一张 JDBC 表。
- 一个 `FILE_DATASET_INPUT` 节点只读取一张文件数据集逻辑表。
- 一个 `HTTP_API_INPUT` 节点只读取一个已声明 Schema 的 API 资源。
- 一个 `MODEL_INPUT` 节点只读取一个数据模型。
- 一个 `KAFKA_INPUT` 节点只读取一个 Topic，并持有自己的 Value Schema。
- 一个 `JOIN` 节点只执行一次两表连接。
- 一个 `STREAM_JOIN` 节点只执行一次流式表连接。
- 一个 `RENAME` 节点只替换一张逻辑表，并原子重命名该表的零到多个字段。
- 一个 `MODEL_OUTPUT` 节点只描述一次向一个目标模型的写入。
- 一个 `JDBC_OUTPUT` 节点只描述一次向一张目标表的写入。
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
- `FILE_DATASET_INPUT` 使用不可修改的 `FileDatasetTable.code`。
- `HTTP_API_INPUT` 使用配置的 `outputTableName`。
- `MODEL_INPUT` 使用模型不可修改且全局唯一的 `code`。
- `KAFKA_INPUT` 使用配置的 `outputTableName`。
- `JOIN` 使用配置的 `outputTableName` 创建新表。
- `RENAME` 处理器替换逻辑表名及 Map Key，但不得修改输入表的物理来源信息。
- 数据库、Schema 和数据源 ID 不参与 Map Key 计算。
- 表名保持元数据接口返回的原始大小写，第一版按精确字符串匹配。

当多个上游 Map 合并时，只要出现相同 Key，就返回 `DUPLICATE_TABLE_NAME` 错误。即使两个 Key 指向同一个物理表，也不得静默去重或覆盖。

该规则使处理节点只依赖稳定的表名和必要字段名。物理表增加未被引用的字段时，通常不需要重新编辑任务；引用的表名或字段名发生变化时，定义校验应明确报错。

### 2.3 外部元数据 Schema 不进入定义，Kafka Schema 归节点所有

Canvas 定义只保存数据源 UUID、模型 UUID、文件数据集表 UUID、API 资源 UUID、表名和显式节点配置，不保存数据源中已有的数据库、Schema、模型名称、模型字段、文件路径/格式/解析参数、JDBC 原生类型、API 输出字段列表或预览数据。

Kafka Value Schema 是消息反序列化和序列化契约，归 `KAFKA_INPUT/KAFKA_OUTPUT` 节点自身所有，因此必须以内联 `valueSchema.columns` 保存。设计器可以把某个已发布模型的当前字段一次性复制进节点，也允许手工编辑或粘贴扁平 JSON Schema；复制完成后不保存模型 ID，后续模型修改不会自动改变 Kafka 节点。

设计器通过现有数据源接口读取最新的真实数据源、物理表和字段 Schema，用于配置和组装单次编译的 `metadataSnapshot`，不进入导出的 Canvas 定义。数据源名称、数据库、Schema、字段列表和元数据读取状态都属于设计时运行数据。

Task Engine 的编译请求使用独立的 `metadataSnapshot` 携带本次分析所需 Schema。该快照和后续不可变运行快照都不属于本文定义的 Canvas JSON，不得在导入导出时混入定义。

### 2.4 不保存数据源凭据

Canvas 定义中的 JDBC 节点只保存 `dataSourceId`，文件输入只保存 `fileDatasetTableId`，HTTP API 节点只保存 `dataSourceId/resourceId`，模型节点只保存 `modelId/targetModelId`，Kafka 节点只保存数据源 ID、Topic、节点自有 Value Schema 和映射配置，文件输出只保存数据源 ID 与相对目录。URL、Broker 地址、对象 Key、物化前缀、用户名、密码、Token、API Key、Secret、签名密钥和其他凭据不得进入 Canvas JSON、节点配置或前端状态持久化结果。

`HTTP_API_INPUT.runtimeParameters` 会随 Canvas 定义明文持久化，只允许保存日期、业务筛选条件、初始游标等非敏感值。动态 Token 必须由 HTTP API 数据源的 OAuth2 或 Token Endpoint 鉴权在执行时生成，不能作为运行时参数绕过凭据边界。

## 3. Canvas JSON 协议

### 3.1 顶层结构

```json
{
  "schemaVersion": 1,
  "schemaMinorVersion": 6,
  "nodes": [],
  "edges": []
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `schemaVersion` | integer | Canvas JSON 协议大版本，当前固定为 `1` |
| `schemaMinorVersion` | integer | Canvas JSON 协议小版本；缺失时按 `0`，当前写出版本为 `6` |
| `nodes` | array | 节点定义，按照前端保存顺序持久化；业务逻辑不得依赖数组顺序 |
| `edges` | array | 有向边定义，业务逻辑不得依赖数组顺序 |

当前实现兼容读取 `1.0`～`1.6`，并把新保存、接口返回、Manifest 和导出统一规范化为 `1.6`。`MODEL_INPUT/MODEL_OUTPUT` 从 `1.1` 开始可用，`RENAME` 从 `1.2` 开始可用，`STREAM_JOIN` 从 `1.3` 开始可用，`FILE_DATASET_INPUT` 从 `1.4` 开始可用，使用内联 Value Schema 的 `KAFKA_INPUT/KAFKA_OUTPUT` 从 `1.5` 开始可用，`FILE_OUTPUT` 从 `1.6` 开始可用。低版本携带高版本节点时返回 `NODE_TYPE_REQUIRES_SCHEMA_VERSION`；同主版本但高于当前的小版本、其他大版本、负数小版本以及版本与节点能力不一致的定义必须拒绝。

小版本通常用于新增节点类型、可选字段或其他不改变已有定义语义的能力；删除或重命名字段、改变已有节点语义、修改核心图规则等不兼容变化原则上升级大版本。本次 Kafka 节点尚无历史业务数据，按明确决策以 `1.5` 直接替换未投入使用的 `valueModelId` 草案，不升级 `2.0`，也不保留兼容分支。版本不得使用 JSON 小数表示，避免 `1.1`、`1.10` 的比较歧义。

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
    "width": 240,
    "height": 120
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

`type` 决定节点类别、默认尺寸、端口规则、配置组件和 Schema 处理器，因此不持久化客户端传入的 `category`、`shape` 或节点组件名称。

布局坐标和尺寸必须是有限数值。第一版建议限制：

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
- 结果字段名不得重复。

## 8. `JDBC_OUTPUT` 节点

### 8.1 配置结构

```json
{
  "sourceTableName": "order_customer",
  "dataSourceId": "a406e119-fbd2-4173-84ee-c92c69231168",
  "targetTableName": "dwd_order_customer",
  "writeMode": "APPEND",
  "columnMappingMode": "EXPLICIT",
  "columnMappings": [
    {
      "sourceColumnName": "order_id",
      "targetColumnName": "order_id"
    },
    {
      "sourceColumnName": "customer_name",
      "targetColumnName": "customer_name"
    }
  ]
}
```

对应 TypeScript 类型：

```ts
type JdbcWriteMode = 'APPEND' | 'OVERWRITE';
type ColumnMappingMode = 'BY_NAME' | 'EXPLICIT';

interface JdbcColumnMapping {
  sourceColumnName: string;
  targetColumnName: string;
}

interface JdbcOutputConfiguration {
  sourceTableName: string;
  dataSourceId: string;
  targetTableName: string;
  writeMode: JdbcWriteMode;
  columnMappingMode: ColumnMappingMode;
  columnMappings: JdbcColumnMapping[];
}
```

### 8.2 输入与输出

- 节点类别：`OUTPUT`
- 入边数量：必须为 `1`
- 出边数量：必须为 `0`
- 从上游 Map 中按 `sourceTableName` 取得待输出表。
- 输出节点不产生下游表 Map。
- 定义校验和预运行只检查配置及 Schema，不执行任何写入。

### 8.3 字段映射模式

`BY_NAME`：

- `columnMappings` 必须为空。
- 按精确字段名自动映射源字段和目标字段。
- 目标表中非空、无默认值且非自动生成的可写字段必须存在同名源字段。
- 源表中目标表不存在的额外字段不会写入，校验结果返回警告但不失败。
- 同名字段使用显式 Spark Cast 写入目标类型；安全转换直接通过，风险转换返回警告，Analyzer 不支持时校验失败。

`EXPLICIT`：

- `columnMappings` 至少包含一项。
- 每个源字段和目标字段都必须存在。
- 同一个目标字段只能被映射一次。
- 相同的源字段可以按明确配置映射到多个目标字段。
- 目标表必填可写字段必须全部被覆盖。
- 映射字段使用显式 Spark Cast。风险转换保留为警告并允许继续，Analyzer 不支持的转换校验失败。

### 8.4 写入模式

- `APPEND`：向目标表追加写入。
- `OVERWRITE`：保留目标表结构，清空目标表后写入。

本文只定义稳定语义。未来执行引擎必须根据数据库方言能力、事务范围和目标表特性决定是否支持 `OVERWRITE`；不支持时必须在发布或运行前阻止，不得退化成其他写入方式。

### 8.5 校验

- 上游 Map 中存在 `sourceTableName`。
- `dataSourceId` 对应已启用的 JDBC 数据源，并具有 `DISTRIBUTION`（数据分发）用途。
- 目标表存在且可读取元数据。
- 目标对象必须是允许写入的物理表，不能是只读视图。
- 写入模式、映射模式和字段配置一致。
- 字段映射满足目标表必填字段和平台类型兼容要求。

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

### 9.6 `JDBC_OUTPUT` 面板

表单顺序：

1. 来源表：从直接上游 Map 中选择。
2. 目标数据源：只列出已启用、JDBC 类型且具有 `DISTRIBUTION`（数据分发）用途的数据源。
3. 目标物理表：从目标数据源配置的数据库和 Schema 中加载。
4. 写入模式。
5. 字段映射模式。
6. 显式字段映射表；仅在 `EXPLICIT` 时显示。

选择 `BY_NAME` 时，面板说明映射由 Task Engine 按同名字段完成；应用配置后，通过 Engine 问题展示缺少的目标必填字段、字段转换风险、不支持的 Cast 和被忽略的额外源字段，不在前端重复计算匹配结果。选择 `EXPLICIT` 时，来源字段选项取自 Engine 的上游 Schema，目标字段选项取自真实目标表元数据。

节点卡片配置完成后显示：

```text
order_customer → public.dwd_order_customer (APPEND)
```

## 10. 完整定义示例

```json
{
  "schemaVersion": 1,
  "schemaMinorVersion": 5,
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
        "columnMappingMode": "BY_NAME",
        "columnMappings": []
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
- 除输出节点外，每个节点至少有一条出边。
- 除输入节点外，每个节点至少有一条入边。
- 不允许与有效输入到输出路径无关的游离节点。

Task Engine 中的 Schema 校验按拓扑顺序执行：

1. `JDBC_INPUT` 从数据源读取最新表元数据，`FILE_DATASET_INPUT` 从文件表元数据生成以稳定 code 为 Key 的有界表，`HTTP_API_INPUT` 从资源声明的输出 Schema 生成以 `outputTableName` 为 Key 的表，`MODEL_INPUT` 从模型快照生成以模型 code 为 Key 的表，`KAFKA_INPUT` 直接使用节点内联 Value Schema 生成以 `outputTableName` 为 Key 的无界表。
2. 节点接收所有直接上游的输出 Map，并执行无覆盖合并。
3. `JOIN` 检查左右表、条件和字段类型，追加结果表。
4. `RENAME` 替换选中表的 Map Key，并使用共享 Spark 投影原子生成新字段 Schema。
5. `JDBC_OUTPUT`、`MODEL_OUTPUT` 和 `KAFKA_OUTPUT` 检查源表、目标及字段映射，但不执行真实写入。
6. 每个节点返回独立校验摘要；图级错误放入单独的 `canvasIssues`，不制造 `@canvas` 伪节点。

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
| `SPARK_ANALYSIS_ERROR` | Spark Analyzer 无法建立 Processor 计算计划 |
| `COLUMN_CAST_RISK` | Spark 支持 Output Cast，但实际值可能转换失败 |
| `NULLABILITY_RISK` | nullable 来源可能无法写入 non-null 目标 |
| `STRING_LENGTH_RISK` | 来源字符串可能超过目标长度 |
| `DECIMAL_PRECISION_RISK` | Decimal 写入目标时可能溢出或舍入 |
| `UNSUPPORTED_COLUMN_CAST` | Spark Analyzer 不支持 Output 字段转换 |
| `DATA_SOURCE_UNAVAILABLE` | 数据源不存在、停用、类型或用途不匹配 |
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
  | CanvasNodeBase<'FILE_DATASET_INPUT', FileDatasetInputConfiguration>
  | CanvasNodeBase<'HTTP_API_INPUT', HttpApiInputConfiguration>
  | CanvasNodeBase<'KAFKA_INPUT', KafkaInputConfiguration>
  | CanvasNodeBase<'JOIN', JoinConfiguration>
  | CanvasNodeBase<'STREAM_JOIN', StreamJoinConfiguration>
  | CanvasNodeBase<'RENAME', RenameConfiguration>
  | CanvasNodeBase<'MODEL_OUTPUT', ModelOutputConfiguration>
  | CanvasNodeBase<'JDBC_OUTPUT', JdbcOutputConfiguration>
  | CanvasNodeBase<'KAFKA_OUTPUT', KafkaOutputConfiguration>;
```

未来后端接入时也应使用明确的 Request、Response 和节点配置类型。不得把 `Map<String, Object>`、JPA Entity、X6 JSON 或未来执行引擎对象作为 Canvas REST 契约。

前端节点注册表只维护展示和编辑能力：

- 节点类别
- 默认尺寸
- X6 Shape 与配置面板
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

后端只拒绝无法安全加载的协议结构，例如版本不支持、未知节点类型、重复 ID、非法布局和缺失边端点。节点尚未配置、图度数不满足、环路或字段冲突等业务无效草稿允许保存，并继续由 Task Engine 展示设计期问题。相同规范化定义重复保存不增加定义版本。

`model-relations` 只读取最后保存定义同步维护的 `task_canvas_model_reference`，按模型 UUID 聚合 `MODEL_INPUT/MODEL_OUTPUT` 的 `INPUT/OUTPUT` 角色，并从 Canvas 定义 JSON 按节点 ID 补充节点名称。JDBC、Kafka、文件和 HTTP 节点不进入模型关系。关系索引与 Canvas 定义在同一事务替换，保存失败时一起回滚；未保存的前端修改、运行历史和临时表不进入查询。模型侧的 `/api/v1/models/{id}/related-tasks` 使用相同事实来源反查并按任务去重。

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

`KAFKA_OUTPUT` 使用相同的 `valueSchema` 结构，并额外配置 `sourceTableName`、可选 `keyColumnName`、`columnMappingMode` 和 `columnMappings`。Value Schema 至少包含一个字段，字段名必须唯一；`STRING` 可设置正整数 `length`，`DECIMAL` 必须设置 `precision: 1..38` 和 `scale: 0..precision`，其他类型不得携带这三个参数。

配置中不再存在 `valueModelId`。Kafka 节点不是模型节点，不创建任务模型引用，也不会在发布、编译或运行准备时查询模型。没有历史 Kafka 定义需要迁移，因此 `1.4` 及更低版本携带 Kafka 节点时直接拒绝，不保留旧字段兼容分支。

### 16.2 设计器与编译语义

- Inspector 支持手工增删、排序和编辑字段。
- “从模型 Schema 导入”只读取一次当前已发布模型字段并复制成内联列；选择结果和模型 ID 不进入定义。
- “粘贴 JSON Schema”第一阶段只接受根类型为 object 的扁平 properties，支持 boolean、integer、number、string、date 和 date-time；嵌套 object、array 明确拒绝。
- `KAFKA_INPUT` Operator 直接把内联列转为 Spark Schema，并输出 `UNBOUNDED` 表；Compiler 和 Runner 不需要模型元数据。
- `KAFKA_OUTPUT` Operator 直接把内联列作为目标 Schema，与 JDBC/模型 Output 复用字段映射和显式 Spark Cast。
- Schema 缺失、空字段、重复字段、非法类型参数属于 Compiler `ERROR`；模型是否仍存在、是否变更与节点有效性无关。
