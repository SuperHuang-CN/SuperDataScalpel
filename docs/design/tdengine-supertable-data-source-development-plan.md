# TDengine 超级表数据源设计与开发说明

## 1. 状态与范围

- 状态：已实现第一阶段代码，尚未使用真实 TDengine 集群完成联调。
- JDBC 驱动：`com.taosdata.jdbc:taos-jdbcdriver:3.6.3`。
- 数据源类型：`TDENGINE_WEBSOCKET`、`TDENGINE_RESTFUL`。
- 推荐类型：`TDENGINE_WEBSOCKET`；`TDENGINE_RESTFUL` 仅用于兼容已有 RESTful 部署。
- 资源边界：只发现、绑定和读取超级表，不枚举、不绑定、不管理子表。
- 用途边界：只允许 `SOURCE`，不开放 `STORAGE` 或 `DISTRIBUTION`。

第一阶段支持连接测试、数据库发现、超级表发现、结构读取、数据预览、外部模型，以及 Canvas
`JDBC_INPUT` 和已发布外部模型的 `MODEL_INPUT`。不支持受管模型、建表和写入、JDBC 自定义查询输入、
SQL/脚本数据服务、超级表物理统计，以及任何子表管理能力。

## 2. 类型与抽象决策

TDengine 在 DataScalpel 中表现为两个明确的数据源产品，而不是一个带“传输方式”表单字段的模糊
类型：

| 数据源类型 | JDBC URL | 驱动类 | 定位 |
| --- | --- | --- | --- |
| `TDENGINE_WEBSOCKET` | `jdbc:TAOS-WS://host:6041/database` | `com.taosdata.jdbc.ws.WebSocketDriver` | 推荐方式 |
| `TDENGINE_RESTFUL` | `jdbc:TAOS-RS://host:6041/database` | `com.taosdata.jdbc.rs.RestfulDriver` | 兼容方式 |

产品类型分开的原因是连接协议、驱动入口、运行时依赖诊断和部署要求不同，使用者应能从数据源类型、
连接摘要和错误信息中直接确认实际协议。保存后也不允许通过修改普通连接参数把一种类型静默切换为
另一种类型。

技术实现不复制两套方言：

```text
DataSourceType
├── TDENGINE_WEBSOCKET ─┐
└── TDENGINE_RESTFUL ───┴─> TdEngineJdbcTransport
                                  │ 类型 ID、显示名、URL 前缀、驱动类
                                  v
                           TdEngineDialect
                                  │ 统一能力、标识符、类型映射和超级表元数据
                                  v
                         DatabaseInspector / 模型 / Canvas Runner
```

`TdEngineJdbcTransport` 是方言内部的有限枚举，不是新的业务抽象或动态插件机制。
`TdEngineDialect` 通过构造参数接收传输方式；超级表发现、结构解析、平台类型映射、查询限定和安全
校验全部只有一份实现。两种实例继续注册到现有 `BuiltInDialects`，上层只按稳定的数据源类型 ID 查找
方言。

这种结构保留了两个清晰的用户选择，同时把变化严格限制在四个传输属性中，后续修复元数据或类型
映射时不会出现 WebSocket 与 RESTful 行为漂移。

## 3. 连接配置

两种类型复用现有 JDBC 连接契约：主机、端口、数据库、用户名、密码和非敏感高级参数。默认端口为
`6041`，Schema 不适用，数据库使用 Catalog 语义。完整 JDBC URL 仍由方言生成，前端只展示预览，
不允许使用者手写 URL。

WebSocket 当前开放 `useSSL` 布尔参数。RESTful 第一阶段不额外开放预定义参数；普通自定义参数仍经过
现有参数名称、数量、长度、敏感词和保留字校验后交给驱动。

TDengine 驱动的旧兼容参数 `batchfetch=true` 或 `batchLoad=true` 可能把 RESTful 连接实际切换为
WebSocket。`TDENGINE_RESTFUL` 明确拒绝这两个真值参数，并提示直接选择 WebSocket 类型，避免页面显示、
运行 Manifest 和实际网络协议不一致。

连接测试沿用现有 JDBC 诊断契约。Admin 运行时必须包含 TDengine 驱动；Task Runner 的本地和集群制品
也必须包含该驱动，集群精简包同时保留其 WebSocket 传输依赖。密码只进入加密存储和短期运行快照，
不进入类型定义、元数据响应、日志或任务结果。

## 4. 能力契约

两种类型共享以下基础能力集：

```text
TEST_CONNECTION
LIST_NAMESPACES
LIST_TABLES
READ_TABLE_METADATA
PREVIEW_DATA
STANDARD_QUERY
```

`TDENGINE_WEBSOCKET` 另外提供 `TMQ_SUBSCRIBE`，用于 Topic 发现和
`TDENGINE_TMQ_INPUT` 实时订阅；`TDENGINE_RESTFUL` 不具备该能力。Topic 由外部 TDengine
管理，DataScalpel 不创建、修改、删除或预览 Topic。具体边界见
[TDengine TMQ 输入](tdengine-tmq-input-development-plan.md)。

其中 `STANDARD_QUERY` 只服务于模型的受控字段查询和预览，不代表开放任意 SQL 或 SQL 数据服务。
以下能力必须保持关闭：

- `DDL`、物理结构变更和任何写入能力。
- SQL 服务查询和 JDBC 自定义查询元数据分析。
- 物理表统计。
- 子表发现、子表 Schema 和子表级任务配置。

类型响应使用独立的 `resourceBrowserKind=TDENGINE_SUPERTABLES`。前端据此显示“超级表”页签和文案，
不把 TDengine 伪装成普通 `JDBC_TABLES`。WebSocket 类型再根据 `TMQ_SUBSCRIBE` 在资源区显示
“超级表 / TMQ Topic”内层页签；RESTful 类型始终只有超级表。

## 5. 超级表元数据

TDengine 没有被强行套入通用 `DatabaseMetaData#getTables`。`DatabaseMetadataProvider` 是方言的可选
能力边界：普通数据库继续使用统一 JDBC 元数据读取器，TDengine 方言仅覆盖非标准资源发现和结构读取。

读取过程如下：

1. 从 `information_schema.ins_databases` 发现数据库，排除 `information_schema` 和
   `performance_schema`。
2. 从 `information_schema.ins_stables` 按数据库发现 `stable_name`，资源类型固定返回
   `SUPERTABLE`。
3. 读取结构或预览前，再用 `ins_stables` 精确确认目标仍是超级表。
4. 使用受引用标识符执行 `DESCRIBE database.stable`，读取 Field、Type、Length 和 Note。
5. 从 `ins_databases.precision` 读取数据库时间精度，再完成时间字段的平台映射。
6. 预览使用受限的 `SELECT * FROM database.stable LIMIT n`，返回的是该超级表聚合的子表数据；
   DataScalpel 不暴露或管理具体子表。

实现不得读取 `information_schema.ins_tables`，也不得执行 `SHOW TABLES`。即使调用方构造了子表名称，
结构和预览入口也会因超级表确认失败而返回稳定错误。

## 6. 字段角色与类型映射

通用 JDBC 字段元数据增加了可选语义角色：

- `TIME_KEY`：`DESCRIBE` 中第一个非 TAG 的 `TIMESTAMP` 指标列。
- `TAG`：`DESCRIBE` 的 Note 为 `TAG` 的标签列。
- `REGULAR`：其他指标列；既有数据库和历史模型默认使用该值。

外部模型把角色保存到可空字段 `physical_column_role`。已有数据无需迁移，空值读取为 `REGULAR`。
角色用于详情、导入预览和后续时序语义识别，不改变模型字段 UUID、平台数据类型或任务表结构契约。

第一阶段无损映射如下：

| TDengine 类型 | 平台类型 | 说明 |
| --- | --- | --- |
| `BOOL` | `BOOLEAN` | 精确 |
| `TINYINT`、`SMALLINT`、`INT`、`BIGINT` | 对应整数类型 | 精确 |
| 无符号小整数 | 更宽一档的有符号整数 | 保留完整值域 |
| `BIGINT UNSIGNED` | `DECIMAL(20,0)` | 保留完整值域 |
| `FLOAT`、`DOUBLE` | `FLOAT`、`DOUBLE` | 精确映射 |
| `TIMESTAMP`，毫秒/微秒精度 | `TIMESTAMP` | 当前平台可表达 |
| `TIMESTAMP`，纳秒精度 | 不支持 | 阻止静默截断 |
| `BINARY`、`NCHAR`、`VARCHAR` | `STRING(length)` | 保留声明长度 |
| `VARBINARY` | `BINARY` | 精确映射 |
| `JSON`、`GEOMETRY`、未知类型 | 不支持导入/任务 | 资源结构仍可查看 |

如果数据库时间精度无法确认，结构读取直接失败；不能假定毫秒精度。任何字段出现 `LOSSY` 或
`UNSUPPORTED` 映射时，外部模型导入和 Canvas 运行准备必须阻止执行，但元数据页仍可帮助使用者定位
具体原生字段。

## 7. 模型边界

TDengine 只允许创建 `EXTERNAL` 模型并绑定已有超级表。创建或更换绑定时仍执行实时超级表确认、字段
映射和短事务保存；子表名称会被拒绝。`MANAGED` 模型、建表 DDL、物理变更计划和物理统计刷新均不开放。

模型字段继续使用通用平台类型，另外保存 `TIME_KEY/TAG/REGULAR` 物理角色。发布、重新启用和数据查询
前通过同一 `TdEngineDialect` 重新校验真实结构，避免管理端导入和任务运行使用两套 TDengine 规则。

第一阶段不允许 TDengine 外部模型被标准表数据服务使用：数据源没有 `STORAGE` 用途，数据服务的既有
存储校验会阻止该路径。

## 8. Canvas 与运行时边界

TDengine 仅进入 Canvas 的只读输入路径：

- 设计器选择数据源、数据库和超级表，元数据对象类型保存为 `SUPERTABLE`。
- `JDBC_INPUT` 直接引用超级表；`MODEL_INPUT` 只允许读取已发布且绑定超级表的外部模型。
- 草稿保存只做稳定结构校验，不在管理数据库事务中访问 TDengine；发布、重新启用和运行准备边界
  重新读取超级表元数据。
- Manifest 使用 `TDENGINE_WEBSOCKET` 或 `TDENGINE_RESTFUL` 作为稳定运行时数据库类型，并携带由
  方言生成的 JDBC URL、驱动类和非敏感属性。
- Runner 按注册方言校验 URL 前缀、驱动类、标识符引用和限定表名，并在读取前再次确认对象仍是
  超级表，再交给 Spark JDBC 读取。

不允许 TDengine 用于 `JDBC_QUERY_INPUT`。两种类型只有 `SOURCE` 用途，因此现有用途校验还会阻止
`JDBC_OUTPUT`、JDBC 快照同步输出、模型输出和本地 SQL 输出。任务数据源引用索引继续使用通用的
`JDBC_TABLE` 资源类别；它表达稳定引用形态，不需要为了页面文案增加 TDengine 专属索引类型。

## 9. 数据服务边界

- `SQL_QUERY`：方言没有 `SQL_SERVICE_QUERY` 能力，拒绝启用。
- `SCRIPT_API`：显式拒绝 TDengine，避免绕过只读超级表边界执行任意脚本 SQL。
- `STANDARD_TABLE`：要求模型绑定 `STORAGE` 数据源，TDengine 的 SOURCE-only 用途自然阻止该路径。

因此 Service Engine 第一阶段不引入 TDengine 驱动或注册逻辑。若未来开放服务查询，应先单独确认查询
协议、时间范围强制条件、分页稳定性、资源上限和 Service Engine 驱动依赖，不能仅打开一个能力枚举。

## 10. 前端交互

- 新建数据源时展示两个独立选项，WebSocket 标记为推荐，RESTful 提示为兼容方式。
- URL 预览分别使用 `jdbc:TAOS-WS://` 和 `jdbc:TAOS-RS://`，不显示可编辑的完整 URL。
- 数据源详情资源页签命名为“超级表”，隐藏“包含视图”和索引页签。
- 超级表列表支持数据库、名称搜索和刷新；结构中以标签区分时间主键、指标和 TAG。
- 预览区明确说明结果聚合超级表下的数据，但系统不会发现、展示或管理子表。
- 外部模型导入预览及模型字段页继续显示物理字段角色。
- Canvas 表选择器把 `SUPERTABLE` 显示为“超级表”，TDengine 不出现在自定义查询输入或输出数据源
  候选中。

## 11. 错误、安全与事务

- JDBC 异常继续使用现有连接测试诊断和 RFC 9457 `ProblemDetail`，不新增平行错误协议。
- 子表或非超级表访问返回资源不存在/不受支持的稳定业务错误，不回退到普通表读取。
- RESTful 隐式协议切换参数、无法确认的时间精度和纳秒时间戳必须显式失败。
- 所有标识符由方言引用；查询值使用 PreparedStatement。预览行数仍受现有上限保护。
- 外部 TDengine 调用保持在管理数据库事务之外；只在读取连接快照和保存模型状态时使用短事务。
- 密码、完整连接属性和数据值不得进入日志、模型字段角色或任务结果。

## 12. 接口兼容

不新增 TDengine 专属 REST 路由。现有数据源类型、连接测试、命名空间、表、表结构、预览和外部模型
接口继续复用，差异由类型能力、`resourceBrowserKind`、`SUPERTABLE` 对象类型和字段角色表达。

新增枚举值只扩展稳定契约；既有 JDBC 类型行为不变。`physical_column_role` 对历史记录可空，避免依赖
`ddl-auto=update` 执行非可靠的数据重写。

## 13. 验证矩阵

已完成的静态验证：

- `data-scalpel-dialect`、`data-scalpel-business`、`data-scalpel-task-engine` 和
  `data-scalpel-admin` 相关模块编译通过。
- TDengine 驱动 JAR 中已确认两种驱动类和 URL 前缀。
- Task Runner 本地包和精简集群包构建通过，两个制品均已确认包含 WebSocket/RESTful 驱动类及
  `Java-WebSocket` 客户端类。
- `BuiltInDialectsTest` 的 10 个方言注册、URL、能力与连接参数用例通过。
- 前端 TDengine 相关 TypeScript 错误已清理；当前完整构建只剩工作区中与本功能无关的既有错误。
- `git diff --check` 通过。

真实 TDengine 3.x 环境还需完成：

1. WebSocket/RESTful 连接、认证、SSL 和错误诊断。
2. `ins_databases`、`ins_stables` 和 `DESCRIBE` 在目标版本及账号权限下的字段兼容性。
3. 子表不会出现在发现结果，且构造子表名称不能绕过结构/预览校验。
4. 毫秒、微秒、纳秒数据库的精度识别和纳秒阻断。
5. 常用数值、字符串、二进制、TAG 及不支持类型的映射结果。
6. 外部模型导入、结构漂移、受控查询和超级表聚合预览。
7. Spark JDBC 分别通过 WebSocket 与 RESTful 读取超级表，并在本地、YARN/Kubernetes 集群制品中
   确认驱动及传输依赖完整。
8. RESTful `batchfetch/batchLoad=true` 被拒绝，其他允许参数不会改变实际协议。
