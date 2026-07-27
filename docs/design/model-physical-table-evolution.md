# 模型物理表演进设计

本文记录模型物理表能力从“受控建表与结构校验”演进到“受控结构变更”的分步设计和实际边界。每一节在实现前定稿，并在实现和验证后补充结论。

## 第 1 步：通用表定义与结构指纹

### 目标

将当前只用于建表的 `CreateTableDefinition` 演进为描述表结构本身的 `TableDefinition`，并提供稳定的结构指纹。它既服务于当前的建表、结构比较，也作为后续变更计划的“变更前结构”“目标结构”和执行前防漂移校验的共同输入。

本步不生成 `ALTER TABLE`，不访问真实数据库，不改变模型业务状态，也不增加物理表配置项。

### 当前问题

- `CreateTableDefinition` 的名称把它限定为建表输入；后续变更计划需要表达原结构、目标结构和重建临时表，语义已经不准确。
- 字段比较依赖名称；模型字段本身虽然已有 UUID，但该稳定身份没有传递到方言层，后续无法可靠区分“改名”与“删除后新增”。
- 当前只能给出差异列表，不能生成可持久化、可比较的结构快照；执行计划在用户确认前无法验证物理表是否仍处于生成计划时的状态。

### 结构模型

新增不可变的 `TableDefinition`：

| 字段 | 说明 |
| --- | --- |
| `table` | Catalog、Schema、表名；用于 DDL 定位，不作为结构指纹的一部分 |
| `columns` | 受控、数据库无关的列定义 |
| `primaryKeyColumns` | 关系型主键列及顺序 |

`TableColumnDefinition` 增加可选 `columnId`（UUID）。它表示调用方提供的稳定逻辑身份：模型字段映射时传入字段 UUID；从 JDBC 元数据反向构造时为 `null`。方言不依赖任何业务实体，只把它用于比对“两个期望结构”时的列配对。实际物理结构的校验和结构指纹不包含该值。

ClickHouse 的 Engine、ORDER BY、PARTITION BY 不在本步放入 `TableDefinition`。这几个属性与关系型主键语义不同，等第 7 步定义单机 ClickHouse 存储配置后，以明确的存储定义扩展，避免现在用 `primaryKey` 错误承载 ClickHouse 排序键。

### 结构指纹

新增 `TableStructureFingerprint`，值为小写 SHA-256 十六进制字符串；新增计算器统一产生规范化载荷后哈希。

规范化规则：

- 忽略 `TableColumnDefinition.columnId`、表位置、字段显示说明、注释、默认值、索引和物理列顺序。
- 列按不区分大小写的列名排序；列名和主键列名用 `Locale.ROOT` 小写规范化。
- 保留列的逻辑类型、字符串长度、小数精度/小数位、可空性。
- 保留主键列的顺序，因为复合主键顺序会影响索引和约束语义。
- 每个组成项使用长度前缀编码，避免简单拼接产生歧义。

因此：改字段展示顺序不会改变指纹；改字段名、类型、长度、可空性或主键定义一定会改变指纹。结构指纹只证明“同一结构语义”，并不替代后续的完整差异列表。

### 从 JDBC 元数据构造结构快照

`DatabaseDialect` 增加 `snapshotTableDefinition(TableMetadata)`。由方言把 `ColumnMetadata` 归一为平台定义的 `TableColumnType`，再生成无 `columnId` 的 `TableDefinition`。

该方法是方言层而不是业务层的职责，原因是 JDBC 类型和原生类型需要按方言解释：例如 PostgreSQL 的 `int8` 应归一为 `LONG`，`text` 应归一为 `TEXT`。对于无法归一的物理类型，方言抛出明确的“不支持结构快照”异常；后续变更计划不能在未知结构上冒险生成 DDL。

已有 `compareTable` 继续以期望 `TableDefinition` 与物理元数据比较，保持当前严格匹配语义。后续计划生成将同时保存：

1. 期望原结构指纹；
2. 目标结构指纹；
3. 生成计划时的物理结构快照指纹。

执行前重新读取物理元数据并计算指纹；若与原结构指纹不一致，计划失效而不是继续执行。

### API 迁移与兼容策略

`DatabaseDialect`、`DatabaseTableOperator` 和 `JdbcModelPhysicalTablePort` 全部改用 `TableDefinition`。旧的 `CreateTableDefinition` 删除，不保留同名兼容包装类：它是未对外发布的方言内部 API，保留两套模型只会在后续变更规划中制造歧义。

模型字段到方言列定义的映射应传递字段 UUID；这不会改变已生成的 `CREATE TABLE` SQL，因为 `columnId` 从不参与 SQL 渲染。

### 验收与测试

- 保持 PostgreSQL、MySQL 的建表 SQL 完全不变。
- 已有结构比较结果完全不变。
- 新增测试证明：字段 UUID、物理列顺序和表位置不会影响指纹。
- 新增测试证明：名称、类型、长度、可空性和主键顺序改变时指纹改变。
- 新增测试证明：PostgreSQL 元数据能归一为与期望定义相同的结构，并得到相同指纹。

### 实现结论

已完成：

- `CreateTableDefinition` 已迁移为 `TableDefinition`，建表、结构比较和业务调用点统一使用该模型。
- `TableColumnDefinition` 已增加可选 `columnId`；模型字段映射会传递字段 UUID，SQL 渲染和结构指纹均忽略该属性。
- `TableStructureFingerprint` 使用结构规范化后的 SHA-256；表位置与列声明顺序不会改变指纹，主键顺序会改变指纹。
- `DatabaseDialect.snapshotTableDefinition` 已由 JDBC 方言实现，当前 PostgreSQL 元数据可归一为平台表定义。
- 已通过 `data-scalpel-dialect`、`data-scalpel-business` 及依赖模块测试；建表 SQL 与既有结构比较测试保持通过。

## 第 2 步：变更操作、风险、策略与检查模型

### 目标

建立不依赖 Spring、JPA、业务模型或具体数据库 SQL 的方言变更语言。后续 PostgreSQL、达梦和 ClickHouse 方言只需要根据“原结构 + 目标结构 + 实际元数据”生成该语言；业务层不需要理解各数据库的 DDL 细节。

本步只定义模型和校验规则，不生成改表 SQL，不执行检查，也不暴露 REST 接口。

### 变更操作

`TableChangeOperation` 表示一个原子结构变化，包含：

| 字段 | 含义 |
| --- | --- |
| `type` | 新增列、删除列、改名、改类型、改长度、改精度、改可空性、增删或替换主键 |
| `beforeColumn`、`afterColumn` | 列变更前后的受控定义；新增仅有 after，删除仅有 before |
| `beforePrimaryKeyColumns`、`afterPrimaryKeyColumns` | 主键变更前后的列顺序 |
| `strategy` | 对该操作的方言处理结论 |
| `risk` | 该操作的最高风险 |
| `reasons` | 可展示、可审计的原因码和说明 |
| `checks` | 必须通过的结构或数据前置检查 |

字段改名由模型字段 UUID 识别，而不是根据名称猜测。方言层只读取 `TableColumnDefinition.columnId`，不依赖模型实体。

### 策略、风险与原子性

`TableChangeStrategy`：

| 策略 | 含义 |
| --- | --- |
| `METADATA_ONLY` | 没有物理结构变化 |
| `IN_PLACE` | 可以在原表上执行 |
| `REBUILD_RECOMMENDED` | 原表修改可行，但重建更可控或风险更低 |
| `REBUILD_REQUIRED` | 物理布局或数据库限制要求重建 |
| `UNSUPPORTED` | 当前数据库/运行环境不允许平台执行 |

`TableChangeRisk` 只表达风险，不代替策略：`SAFE`、`CAUTION`、`DESTRUCTIVE`。例如“删除普通列”可以是 `IN_PLACE + DESTRUCTIVE`；“修改 ClickHouse ORDER BY”则是 `REBUILD_REQUIRED + CAUTION`。

`TableDdlAtomicity` 描述目标库的执行边界：

- `TRANSACTIONAL_BATCH`：一批 DDL 可整体回滚。
- `ATOMIC_SINGLE_STATEMENT`：单条语句原子，但多步流程不整体原子。
- `NON_TRANSACTIONAL_SEQUENCE`：顺序执行，失败时可能出现中间状态。

原子性由方言结合运行环境在后续步骤填写。它不能由数据库类型静态推断：达梦受 `DDL_AUTO_COMMIT` 和 DPC 模式影响；ClickHouse 单机也没有跨多条 DDL 的事务。

### 前置检查

前置检查必须是类型化模型，不能让前端或业务层提交 SQL。`TableChangeCheck` 支持：

- 原表结构指纹一致；
- 表为空；
- 一列或多列不存在空值；
- 一列或多列值唯一；
- 字符串最大长度不超过目标长度；
- 小数数据可放入目标精度/小数位；
- 不存在外部依赖；
- 运行环境满足方言要求。

检查记录只存结构化参数，如列名、长度、精度和期望指纹。第 4～7 步由方言渲染受控检查 SQL 并由执行器读取结果；任何 API 都不会接收原始 SQL。

### 汇总计划

`TableChangePlan` 包含原定义、目标定义、每项操作、计划级检查、原因、推荐策略、最高风险和 DDL 原子性。它要求原表和目标表位置相同；改物理表位置不是本轮模型变更范围。

计划级策略和风险不得低于任一操作的结论。提供两个明确的能力判断：

- 是否可原表执行；
- 是否可重建执行。

业务层后续依据这两个判断决定向用户展示“原表修改”“重建物理表”还是“暂不支持”，而不是自行推断数据库能力。

### 验收与测试

- 所有操作记录的结构前后条件必须受构造器校验。
- 主键操作不能携带列定义，列操作不能混入主键定义。
- 计划必须保证原/目标表位置一致，且汇总风险和策略不低于任何子操作。
- 前置检查的列名、长度、精度、指纹等参数必须按检查类型校验。

### 实现结论

已完成：

- 已在方言模型层增加操作、策略、风险、原子性、原因、前置检查和汇总计划等不可变类型。
- 操作模型明确区分列修改与主键修改，构造时阻止两类载荷混用。
- 计划模型保证汇总策略和风险不会弱于任一子操作，并直接暴露原表执行与重建执行是否可选。
- 所有前置检查均为类型化参数，不包含也不接受任意 SQL。
- 已补充模型约束测试，并通过 `data-scalpel-dialect`、`data-scalpel-business` 与依赖模块测试。

## 第 3 步：业务变更计划实体、接口与状态机

### 目标

在模型业务域持久化用户已经审阅但尚未执行的物理表变更计划。计划必须冻结“原模型结构、目标字段输入、方言结论和结构指纹”，使用户取消、重新编辑、系统重启或后续执行时都有唯一、可审计的依据。

本步建立规划、查询、取消和状态流转能力；JDBC 方言的真实 PostgreSQL 规划与执行将在第 4、5 步接入。

### 一致性原则

模型管理库与目标数据源不是一个分布式事务，不能假装一次提交就能同时修改两边。因此采用以下顺序：

1. 用户提交目标字段，服务校验模型、数据源和当前物理表结构。
2. 方言生成不可变 `TableChangePlan`。
3. 管理库保存计划和目标字段快照，但不修改 `ds_data_model_field`。
4. 后续执行成功并完成目标表校验后，才把目标快照写入正式字段表。

取消或被新计划替代时，正式模型字段与物理表都保持原状。

### 数据模型

`DataModel` 增加 `schemaVersion`，初始为 1。直接保存尚未绑定物理表的草稿字段时递增；完成一个物理变更计划后递增。计划记录基于的版本与当前版本不一致，即视为过期，不允许执行。

新增 `ds_data_model_physical_change`：

| 字段 | 含义 |
| --- | --- |
| `model_id` | 模型 UUID 标量引用 |
| `base_schema_version`、`target_schema_version` | 计划基于的模型版本与成功后的版本 |
| `status` | 计划生命周期状态 |
| `strategy`、`risk`、`ddl_atomicity` | 方言决策快照 |
| `before_fingerprint`、`target_fingerprint` | 原/目标结构指纹 |
| `plan_snapshot` | 完整方言计划 JSON，不使用 PostgreSQL 专属 JSONB |
| `target_fields_snapshot` | 用户确认的目标字段 JSON |
| `execution_started_at`、`completed_at`、错误信息 | 后续执行与恢复信息 |

计划 JSON 是审计快照，不参与实体 Search DSL。实体间只保存 `modelId`，不建立 JPA 关联。

### 状态机

```text
PLANNED → APPLYING → SUCCEEDED
    │          ├→ FAILED
    │          └→ PARTIAL
    ├→ CANCELLED
    └→ SUPERSEDED
```

- 仅 `PLANNED` 可以取消或被新计划替代。
- `APPLYING` 不能取消，也不能创建新计划。
- `FAILED` 表示确认物理表仍为原结构；`PARTIAL` 表示物理表既不符合原结构也不符合目标结构，必须人工处理或后续执行协调动作。
- 字段编辑只允许模型为 `DRAFT` 或 `DISABLED`；`PUBLISHED` 必须先停用。`DISABLED + MANAGED` 的物理结构修改继续通过变更计划执行，不允许直接保存绕过 DDL 审阅。
- 计划创建只允许 `MANAGED` 模式且模型为 `DRAFT` 或 `DISABLED`；`EXTERNAL` 第一版不允许修改物理结构，但可维护字段名称、说明和展示排序。
- 生成计划前必须实时检查物理表为 `MATCHED`。已漂移的表不基于猜测状态生成 DDL。

### 业务端口与接口

`ModelPhysicalTablePort` 新增“生成变更计划”方法，输入为经过业务校验的原/目标 `TableDefinition`。业务层负责字段 UUID、版本、模型状态、快照和计划生命周期；JDBC 实现负责实时元数据、数据库能力和方言结论。

接口统一使用 GET、POST：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/api/v1/models/{id}/physical-table-change-plans` | 提交目标字段并生成计划 |
| `GET` | `/api/v1/models/{id}/physical-table-change-plans` | 使用统一 Search DSL 查询计划历史 |
| `GET` | `/api/v1/models/{id}/physical-table-change-plans/{planId}` | 查询计划详情和快照 |
| `POST` | `/api/v1/models/{id}/physical-table-change-plans/{planId}/actions/cancel` | 取消尚未执行的计划 |

执行与协调接口在第 4、5 步实现。当前 JDBC 实现显式返回“不支持变更规划”，不会伪造计划；集成测试使用测试端口验证计划实体、版本和状态机。

### 验收与测试

- 同一模型存在 `APPLYING` 计划时不得生成新计划。
- 新计划自动将旧 `PLANNED` 计划标记为 `SUPERSEDED`。
- 取消不能影响正式字段和模型版本。
- 计划响应必须来自保存的 JSON 快照，而不是依据当前可变字段重新推导。
- 计划历史必须使用固定 `modelId` 条件与统一 SearchEngine 组合查询。

### 实现结论

已完成：

- 新增模型结构版本、物理表变更计划实体和状态机；实体只保存 `modelId` 标量引用。
- 新增计划生成、Search DSL 历史查询、详情和取消接口；计划响应始终反序列化自已保存的方言 JSON 快照。
- 目标字段在生成计划时仅以 JSON 快照保存，未覆盖正式字段表；取消计划不会改变模型字段或 `schemaVersion`。
- `ModelPhysicalTablePort` 已增加规划边界。当前 JDBC 实现明确拒绝尚未支持的规划；测试端口验证了计划持久化、查询、取消及版本不变的行为。
- 已通过模型管理 Spring 集成测试（3 项）以及依赖模块编译和测试。

## 第 4 步：PostgreSQL 原表修改闭环

### 目标

为 PostgreSQL 的 `MANAGED` 模型实现“生成原表修改计划—检查—单事务执行—结构校验—落模型字段”的完整闭环。该能力只处理明确可在原表完成的变更；重建方案在第 5 步实现。

### PostgreSQL 规则矩阵

| 变更 | 策略 | 风险与检查 |
| --- | --- | --- |
| 新增可空列 | `IN_PLACE` | `SAFE` |
| 新增非空列 | `IN_PLACE` | `CAUTION`；要求目标表为空，因为当前模型没有默认值/回填表达式 |
| 删除普通列 | `IN_PLACE` | `DESTRUCTIVE`；不使用 `CASCADE`，检查外部依赖 |
| 字段改名 | `IN_PLACE` | `SAFE`；由字段 UUID 识别 |
| `INTEGER → LONG` | `IN_PLACE` | `SAFE` |
| 字符串长度扩大 | `IN_PLACE` | `SAFE` |
| 字符串长度缩小 | `IN_PLACE` | `CAUTION`；检查最大实际长度 |
| 小数精度扩大且不缩小小数位 | `IN_PLACE` | `SAFE` |
| 小数精度/小数位缩小 | `IN_PLACE` | `CAUTION`；检查每条数据能否放入目标精度 |
| 可空改非空 | `IN_PLACE` | `CAUTION`；检查空值 |
| 非空改可空 | `IN_PLACE` | `SAFE` |
| 新增/替换主键 | `IN_PLACE` | `CAUTION`；检查空值、重复值和外部依赖 |
| 删除主键 | `IN_PLACE` | `CAUTION`；检查外部依赖 |
| 任意其他类型转换 | `REBUILD_RECOMMENDED` 或 `UNSUPPORTED` | 本步不执行，交给第 5 步或提示用户取消 |

PostgreSQL 物理主键通过 `ALTER TABLE ... DROP CONSTRAINT / ADD PRIMARY KEY` 修改；它不因为“主键变更”而一律重建。只有涉及不兼容类型、依赖对象或无法安全转化的数据时才需要重建或拒绝。

### 方言计划与 SQL

`DatabaseDialect` 增加 PostgreSQL 变更规划入口，输入原定义、目标定义和真实表元数据，输出 `TableChangePlan`。计划新增受控执行选项：本步仅产生 `IN_PLACE` 选项，包含不可由前端编辑的 DDL 语句。

DDL 的顺序固定：字段改名 → 字段类型/长度/精度 → 可空性 → 主键删除/新增 → 新增列 → 删除列。需要先去除旧主键的变更在删除或调整相关字段之前执行。所有标识符均由方言引用，业务层和前端不拼接 SQL。

### 执行与校验

PostgreSQL 选项标记为 `TRANSACTIONAL_BATCH`，执行器使用同一 JDBC 连接：

1. `BEGIN`。
2. 重新读取元数据，确认仍严格匹配计划的原结构；否则终止为“结构漂移”。
3. 执行类型化前置检查。
4. 顺序执行计划 DDL。
5. 再次读取元数据，确认严格匹配目标结构。
6. 成功则提交，任何失败则回滚。

目标库事务完成后，管理库才在独立事务内应用目标字段快照、递增 `schemaVersion` 并标记计划成功。发生数据库异常时，计划标记为 `FAILED`；本步 PostgreSQL DDL 已回滚，不应留下部分结构。

### 接口

新增：

```text
POST /api/v1/models/{id}/physical-table-change-plans/{planId}/actions/execute
```

请求显式携带 `executionMode=IN_PLACE`。本步不接受重建模式；第 5 步才开放 `REBUILD`。执行接口同步返回最终计划状态；重建或长耗时 Mutation 的异步进度属于后续步骤。

### 验收与测试

- PostgreSQL 方言单元测试覆盖所有规则矩阵和生成 SQL。
- 执行器测试覆盖结构漂移、检查失败、DDL 失败回滚和目标结构校验。
- 模型集成测试覆盖：停用模型生成计划、执行成功后字段与版本更新，以及失败不更新字段。
- 不使用真实生产数据源；PostgreSQL 实库验证在本步代码闭环后单独使用管理库测试 Schema 进行。

### 实现结论

已完成：

- PostgreSQL 方言已按字段 UUID 识别改名，并生成受控的 `IN_PLACE` SQL；SQL 不来自前端，也不接受自由输入。
- `DatabaseTableOperator` 在同一目标库 JDBC 事务中完成：原结构指纹校验 → 数据前置检查 → DDL → 目标结构校验 → 提交；任一步失败均回滚。
- 模型变更计划执行接口在目标库提交后，才在管理库中写入目标字段快照并递增 `schemaVersion`；目标库失败会将计划标记为 `FAILED`，管理库收尾失败会标记为 `PARTIAL`，供后续人工核对。
- 修复了真实 JDBC 元数据中“无主键表返回 `null`”的边界：平台统一将其表示为空主键元数据，避免结构比较出现空指针。
- 新增默认跳过的 PostgreSQL 实库集成测试。启用 `DATASCALPEL_PG_INTEGRATION=true` 后，它仅在 `datascalpel_adapter_test`（可覆盖）创建随机表，并始终清理：已验证一次成功的改名/缩短长度/设非空变更，以及一次因数据超长导致的预检拒绝和原结构保持。

## 第 5 步：PostgreSQL 重建表闭环

### 目标

当 PostgreSQL 不能以原表 `ALTER` 完成模型修改、但平台能明确构造数据复制规则时，向技术用户提供唯一的 `REBUILD` 执行选项。重建后表名不变、结构变为目标定义；整个物理过程必须是一个 PostgreSQL 事务。

本步不把“重建”理解为不受约束的兜底。无法给出确定数据映射的变更仍返回 `UNSUPPORTED`，用户只能取消计划、先人工处理数据，或在后续版本提供专门的数据迁移能力。

### 适用范围与策略

| 场景 | 计划策略 | 是否提供 `REBUILD` |
| --- | --- | --- |
| 第 4 步已支持的原表操作 | `IN_PLACE` | 否；避免不必要的复制和锁表 |
| 平台已定义复制表达式的类型变换（如数值族、字符串/长文本、日期时间、布尔与字符串之间） | `REBUILD_REQUIRED` | 是 |
| 与重建同时发生的改名、新增/删除字段、主键变化 | 随主类型变换合并为 `REBUILD_REQUIRED` | 是 |
| 新增非空字段且源表非空 | `REBUILD_REQUIRED` | 否；当前没有默认值或回填表达式，不能凭空生成数据 |
| 二进制与其他类型间转换、未知 JDBC 类型、无法定义转换表达式 | `UNSUPPORTED` | 否 |

字符串缩短、小数精度缩小、可空改非空、目标主键变更仍生成现有的类型化前置检查。文本转数字、文本转日期等由 PostgreSQL `CAST` 在复制阶段验证；任何一行不能转换都会使整个事务回滚，计划记录为失败，不会替换原表。

### 重建前置条件

物理表由平台接管的定义只包含列和主键，不包含用户额外维护的索引、触发器、规则、视图或外键。因此重建前必须验证：

1. 计划原结构指纹仍匹配真实表。
2. 所有数据型检查通过。
3. 不存在引用该表的外部外键。
4. 不存在非主键索引、用户触发器或规则等平台无法安全重建的对象。

第 3、4 项统一为 PostgreSQL 专用的“无重建依赖”检查。失败时不执行 DDL，提示用户先人工迁移或移除依赖，再重新生成计划；绝不使用 `CASCADE`。这一限制比“重建后悄悄丢失索引/触发器”更符合数据中台的可控性要求。

### 受控 SQL 与事务顺序

方言在生成计划时创建随机、受引号保护的影子表和备份表名，并将完整 SQL 作为不可编辑的计划快照保存。固定顺序如下：

1. `LOCK TABLE` 获得排他锁，保证复制期间没有并发写入或结构变化。
2. `CREATE TABLE <shadow>`，完全按目标 `TableDefinition` 创建。
3. `INSERT INTO <shadow>(目标列...) SELECT 映射表达式... FROM <source>`。
4. 将原表改名为临时备份名，将影子表改回原表名。
5. 删除临时备份表。
6. 重新读取元数据，确认原表名下的结构匹配目标指纹，然后提交。

PostgreSQL 的 DDL 与数据复制都处于同一事务；锁冲突、转换错误、复制错误、重命名错误、删除备份失败或后置校验失败都将回滚。因而外部客户端始终只能看见重建前或重建后的表，不会看见半成品影子表或“原表已改名、新表未就位”的中间状态。

### 数据复制映射

以字段 UUID 为第一匹配键：同一 UUID 的源字段复制到目标字段，即使发生改名也不会丢失数据。新增字段仅允许可空，复制 `NULL`；删除字段不写入影子表。没有 UUID 的历史字段只允许同名匹配，避免猜测字段语义。

每种 PostgreSQL 可执行转换由方言显式输出表达式，例如：

- 相同类型或安全扩展：直接引用源列；
- `INTEGER/LONG/DECIMAL`：使用 PostgreSQL 明确的数值 `CAST`；
- `STRING/TEXT`：使用 `CAST(... AS varchar(n))` 或 `text`；
- `DATE/DATETIME`：使用 `CAST`；
- `BOOLEAN ↔ STRING/TEXT`：使用 `CAST`。

不支持的组合不会进入执行计划。复制 SQL 只引用方言生成的标识符和类型，不包含用户提交的 SQL 片段。

### 执行器、状态与超时

`TableChangeExecutionOption` 使用 `REBUILD + TRANSACTIONAL_BATCH` 表示该方案；执行器按执行模式选择更长的受控语句超时（重建默认 5 分钟，原表修改保持 30 秒）。数据库端出错会回滚并映射为统一错误码，管理库状态机沿用第 4 步的 `FAILED` / `PARTIAL` 收尾规则。

本步仍为同步执行。前端会先展示风险、前置检查与 SQL 摘要，用户显式选择“执行重建”后才发起请求；第 8 步再补充计划详情、状态轮询和执行进度展示。

### 验收与测试

- 方言单元测试覆盖：可重建转换、不可转换拒绝、字段 UUID 改名映射、影子表 SQL 顺序与依赖检查。
- JDBC 集成测试覆盖：成功重建后数据与结构正确；转换失败时事务回滚且原表/数据仍在；检测到额外依赖时不执行。
- PostgreSQL 实库集成测试继续只使用 `datascalpel_adapter_test` 的随机表，不对任何业务表执行重建。

### 实现结论

已完成：

- PostgreSQL 方言现在会将不支持原表修改、但具有明确转换规则的字段类型变化规划为 `REBUILD_REQUIRED`，并仅提供受控的 `REBUILD` 选项；二进制等无法可靠转换的组合仍保持 `UNSUPPORTED`。
- 重建计划按字段 UUID 映射数据，生成影子表、复制、原表/影子表改名、删除备份表的固定 SQL 顺序，并在首句取得排他锁。执行器对 `REBUILD` 使用 5 分钟语句超时，对原表变更继续使用 30 秒。
- 增加 PostgreSQL 的“无重建依赖”检查：外键（入/出）、检查/唯一/排他约束、非主键索引、用户触发器、规则和视图/物化视图依赖都会在 DDL 前阻止重建；不会使用 `CASCADE`，也不会静默丢失这些对象。
- PostgreSQL 单元测试覆盖重建计划、SQL 顺序、影子表命名、依赖检查和不可转换类型；模型管理集成测试确认通用计划状态机和执行模式仍正常。
- 真实 PostgreSQL 集成测试已验证：字符串转日期的重建后数据与结构正确；非法日期转换使整个事务回滚并保留原表/原数据；额外普通索引会在 DDL 前被拒绝。所有测试表均为 `datascalpel_adapter_test` 下的随机表并已自动清理。

## 第 6 步：达梦运行参数与原表修改规则

### 目标

将达梦纳入相同的“先生成计划、再由用户确认执行”的框架，但不把 PostgreSQL 的事务能力错误移植过去。达梦是否可安全执行一组 DDL 取决于运行时参数，必须在连接到目标库后检测，而不是由数据源类型静态推断。

### 运行时前提

方言在生成计划和执行前均查询：

```sql
SELECT SF_GET_PARA_VALUE(2, 'DDL_AUTO_COMMIT');
SELECT SF_GET_PARA_VALUE(2, 'DPC_MODE');
```

仅当 `DDL_AUTO_COMMIT=0` 且 `DPC_MODE=0`（非 DPC）时，平台才提供 `IN_PLACE + TRANSACTIONAL_BATCH` 选项。否则计划为 `UNSUPPORTED`，明确显示“达梦当前运行环境会自动提交 DDL，平台不能保证多步变更的整体回滚”，不允许执行。

该结论来自达梦官方事务文档：DDL 前会提交已有事务，DDL 本身是否提交受 `DDL_AUTO_COMMIT` 控制；DMDPC 执行 DDL 强制自动提交且不支持关闭该参数。运行参数在计划和执行之间发生变化时，执行前的 `DATABASE_RUNTIME_SUPPORTED` 检查会再次拒绝，保证不会在错误的原子性假设下执行。

### 本步支持的原表修改

在运行时前提满足时，仅执行文档和测试能够明确覆盖的达梦 `ALTER TABLE` 子集：

| 变更 | 策略 | 前置检查/说明 |
| --- | --- | --- |
| 新增可空列 | `IN_PLACE + SAFE` | `ADD COLUMN` |
| 新增非空列 | `IN_PLACE + CAUTION` | 源表必须为空；达梦非空新增列不能直接加到已有数据表 |
| 字段改名 | `IN_PLACE + SAFE` | `RENAME COLUMN`，由字段 UUID 识别 |
| 字符串长度扩大 | `IN_PLACE + SAFE` | `MODIFY`，保留原可空性 |
| `INTEGER → LONG` | `IN_PLACE + CAUTION` | `MODIFY`；由达梦完成已有值的可转换性校验 |
| 可空改非空 | `IN_PLACE + CAUTION` | 检查无空值，再 `MODIFY ... NOT NULL` |
| 非空改可空 | `IN_PLACE + SAFE` | `MODIFY ... NULL` |

删除列、缩短字符串、任意小数精度变化、`VARCHAR ↔ TEXT/CLOB`、布尔/二进制变化、主键变更，以及任何未列出的类型转换，本步都返回 `UNSUPPORTED`。它们不是“悄悄尝试一下”的对象：例如达梦官方明确说明 `VARCHAR` 不能直接改成大字段类型，需要加列、拷贝、删列、改名或新表迁移。达梦重建/迁移会在有真实达梦环境和依赖对象策略后单独设计，不复用 PostgreSQL 的影子表方案。

### 受控 SQL 与执行

所有 SQL 由 `DamengDialect` 从 `TableDefinition` 渲染，前端只看到计划快照：

```text
ALTER TABLE "模式"."表" ADD COLUMN "字段" VARCHAR(n)
ALTER TABLE "模式"."表" RENAME COLUMN "旧字段" TO "新字段"
ALTER TABLE "模式"."表" MODIFY "字段" VARCHAR(n) [NOT NULL]
ALTER TABLE "模式"."表" MODIFY "字段" NULL | NOT NULL
```

`DatabaseTableOperator` 使用与 PostgreSQL 相同的执行顺序：原结构指纹 → 运行环境/数据预检 → 受控 DDL → 目标结构校验 → 提交或回滚。计划阶段与执行阶段都通过同一个 JDBC 连接读取运行参数；缺失查询权限、返回未知值或参数不满足时一律拒绝，而不是猜测“可能能回滚”。

### 适配器边界

`DatabaseDialect` 增加带 JDBC `Connection` 的计划重载，默认实现保持既有、无运行参数的方言行为。只有达梦覆盖该重载；`DatabaseTableOperator` 统一负责在目标连接内读取元数据并调用该重载。因此方言层仍不依赖 Spring/JPA/业务实体，业务层只消费已经冻结的 `TableChangePlan`。

### 验收与测试

- 单元测试覆盖 `DDL_AUTO_COMMIT` / `DPC_MODE` 的运行时分支、支持操作生成的 SQL、非空新增/设非空的检查，以及不支持操作没有执行选项。
- 回归模型管理计划与执行测试，确认新的方言重载不改变 PostgreSQL 行为。
- 当前工程没有可用于自动化验收的达梦实例或凭据，因此不做“假装真实”的数据库集成测试；接入实际达梦测试库后，先做参数读取、事务回滚与每条受控 SQL 的独立验收，再扩大支持矩阵。

### 实现结论

已完成：

- 达梦方言在规划阶段通过同一目标库 JDBC 连接读取 `DDL_AUTO_COMMIT` 与 `DPC_MODE`；运行参数不可读取、`DDL_AUTO_COMMIT` 非 `0` 或 DPC 模式启用时，均只生成不可执行计划。
- 在满足运行时前提时，达梦仅生成新增字段、字段改名、字符串扩容、`INTEGER → LONG` 与可空性调整的受控原表 SQL；删除字段、缩短字段、精度/大字段/主键等变更保持不可执行。
- `DatabaseTableOperator` 的规划入口已经统一在连接内读取物理元数据并调用方言的连接感知规划重载，未改变 PostgreSQL 的已有行为。
- 由于文件数据集依赖中的 Hadoop Common 会传递引入过旧的 Nimbus JOSE 与 reload4j，已仅在该依赖边排除冲突项，确保 Spring Security 使用 Boot 管理的 Nimbus 版本和 Logback。
- 达梦规划器 4 项单元测试、PostgreSQL 规划器回归以及模型管理 Spring 集成测试均已通过；尚无达梦测试实例，因此没有声称完成真实达梦 DDL 验收。

## 第 7 步：ClickHouse 单机 MergeTree 存储表

### 目标

让 `CLICKHOUSE` 数据存储可以作为 `MANAGED` 模型的受控物理表目标，并支持一小组不会触发异步 Mutation、也不需要分布式 DDL 的结构演进。本步只面向单机 ClickHouse：不支持 `ON CLUSTER`、`ReplicatedMergeTree`、`Distributed`、分区、TTL、投影、物化视图管理或用户自定义引擎参数。

### 存储模型

ClickHouse 的“主键”是稀疏索引与排序的一部分，不保证唯一性，不能把模型字段的关系型 `primaryKey` 标记直接渲染为 `PRIMARY KEY` 约束。因此本步新增明确的 ClickHouse 专属模型配置：`clickHouseOrderByColumns`。

- 数据源类型为 ClickHouse 且模型为 `MANAGED` 时，该配置可指定零到多个模型字段编码，顺序即单机 `MergeTree` 的排序键顺序。
- 固定引擎为 `MergeTree()`；空排序键显式渲染为 `ORDER BY tuple()`，不依赖服务器开关。
- 不支持表达式排序键；每个排序键必须是当前模型中的简单字段编码，禁止重复。
- 关系型 `primaryKey` 字段仍是模型元数据，但不会在 ClickHouse 创建唯一约束或物理 `PRIMARY KEY`；界面后续应明确展示这一差异。
- `STRING` 与 `TEXT` 在 ClickHouse 都投影为物理 `String`，长度不是 ClickHouse 的物理约束；`BINARY` 第一版不支持作为受控 ClickHouse 字段。

`TableDefinition` 新增受控、可序列化的存储定义（无存储定义或 `MERGE_TREE + orderByColumns`）。物理结构指纹随之纳入引擎和排序键，保证计划生成后引擎或排序键漂移会失效。JDBC 的通用列元数据不能读出这些信息，因此 ClickHouse 方言额外从 `system.tables` 读取 `engine` 与 `sorting_key`；非平台可解析的引擎或复杂排序表达式视为不受管的物理结构，不生成修改计划。

模型创建、更新字段和创建物理表前都会验证排序键引用的字段存在。物理表已经存在时不允许直接修改排序键配置；排序键重写需要复制/换表策略，留待后续专门设计，不能先修改模型配置再把物理表置于漂移状态。

### 建表 SQL

受控 SQL 只来自 `ClickHouseDialect`：

```sql
CREATE TABLE `database`.`table` (
  `event_id` Int64,
  `event_time` DateTime,
  `payload` Nullable(String)
)
ENGINE = MergeTree()
ORDER BY (`event_time`, `event_id`)
```

可空列使用 `Nullable(T)`；非空列使用基础类型。平台不生成 `DEFAULT`、`CODEC`、`SETTINGS`、`PARTITION BY` 或任何自由 SQL，避免“新增列后由默认表达式补数”等超出模型契约的行为。

### 可执行变更边界

ClickHouse 不支持跨多条 DDL 的事务，但官方文档说明一条 MergeTree `ALTER TABLE` 查询是原子的。平台因此只在能够汇总成**一条** `ALTER TABLE` 的情况下提供 `IN_PLACE + ATOMIC_SINGLE_STATEMENT`：

| 变更 | 结论 | 说明 |
| --- | --- | --- |
| 新增可空列 | 可执行，`SAFE` | 只改结构；历史 part 读取时使用类型默认值 |
| 新增非空列 | 可执行，`CAUTION` | 要求表为空；不把零值/空串当作业务回填 |
| 改名非排序键字段 | 可执行，`SAFE` | 排序键/主键表达式中的列不能改名 |
| 删除非排序键字段 | 可执行，`DESTRUCTIVE` | 永久删除列数据；依赖物化视图时由 ClickHouse 拒绝 |
| 类型、长度、精度、可空性变化 | 不执行 | 可能重写数据或产生 Mutation，第一版拒绝 |
| 排序键、引擎或关系型主键定义变化 | 不执行 | 需要换表/复制或与模型语义不一致 |
| 删除或改名排序键字段 | 不执行 | ClickHouse 明确限制此类操作 |

允许的多个操作组合为一条：

```sql
ALTER TABLE `database`.`table`
  RENAME COLUMN `old_name` TO `new_name`,
  ADD COLUMN `extra` Nullable(String),
  DROP COLUMN `obsolete`
```

计划和执行前均重新读取列、引擎、排序键并校验结构指纹；非空新增还执行 `TABLE_EMPTY`。执行器新增对 `ATOMIC_SINGLE_STATEMENT` 的支持：只接受恰好一条方言 SQL，以自动提交方式执行，随后读取结构做后置校验。若单条 SQL 已成功但后置校验失败，计划记录为 `PARTIAL`，而非错误地标记为“已回滚失败”。

### 验收与测试

- 单元测试覆盖：`MergeTree` 建表 SQL、空/非空排序键、物理 `String` 与模型字段类型的归一、`system.tables` 存储元数据解析，以及计划中允许与拒绝的操作矩阵。
- 执行器测试覆盖：原子单语句仅接受一条 SQL、前置结构/空表检查、成功后的后置校验和后置校验失败的 `PARTIAL` 状态。
- 不连接真实 ClickHouse。当前无隔离的 ClickHouse 测试库，且本步不应以开发机或生产节点替代自动化集成环境；接入后优先验证 `system.tables` 元数据格式、可空列 JDBC 元数据和单条 `ALTER` 的原子性。

### 实现结论

已完成：

- `TableDefinition` 和结构指纹已纳入受控的存储定义；当前只增加 `MERGE_TREE + orderByColumns`，既有关系型定义保持“无存储定义”的兼容语义。
- `DataModel`、创建/更新请求与响应已增加 `clickHouseOrderByColumns`。它只允许用于 `MANAGED` ClickHouse 模型；字段保存、建表和变更规划都会校验排序键引用存在。物理表存在后，直接修改排序键会被拒绝。
- `ClickHouseDialect` 已生成固定的单机 `CREATE TABLE ... ENGINE = MergeTree() ORDER BY ...`，并从 `system.tables` 读取引擎与排序键用于结构校验。它不接受关系型主键、二进制字段、复制/分布式引擎或自由表达式排序键。
- 已实现安全子集的单条 `ALTER TABLE` 计划：新增列、非排序键改名、非排序键删除会合并为一条 `ATOMIC_SINGLE_STATEMENT` 选项；类型、可空性、排序键、引擎和关系型主键变更均明确返回不可执行计划。
- 执行器已支持原子单语句方式，并在成功 DDL 后重新校验结构；单语句成功但后置校验失败时，业务计划会标记为 `PARTIAL`，避免错误宣称已回滚。
- 已通过全部方言模块回归（28 项，真实 PostgreSQL 用例按环境变量跳过）和模型管理 Spring 集成测试（5 项）。当前没有隔离的 ClickHouse 测试库，因此未执行真实 ClickHouse 集成测试；待接入后需要验证 `system.tables.sorting_key` 返回格式、JDBC nullable 元数据和原子 `ALTER` 行为。

## 第 8 步：变更计划界面、确认执行与执行结果

### 目标

把前七步已经具备的后端能力落实为技术人员可审阅、可确认、可追溯的紧凑界面。界面不自行判断数据库能力，也不拼接 SQL；它只呈现后端冻结的计划快照、前置检查、执行选项和最终状态。

本步包含两个界面入口：

1. **字段定义页签**负责暂存字段编辑。在受管物理表不存在时，仍可直接保存字段；物理表已匹配时，字段名称、说明和展示排序可直接保存，物理结构调整的主操作改为“生成变更计划”。
2. **物理变更页签**负责查看计划历史、计划详情、取消待执行计划和选择一个后端提供的执行方式。

这两个入口共用同一个计划详情抽屉，避免把 SQL、风险和执行按钮复制到多个页面。

### 直接保存的保护边界

`update-fields` 不能成为绕过计划的后门。对于 `MANAGED` 模型：

- 只有 `DRAFT` 和 `DISABLED` 可提交字段修改；`PUBLISHED` 始终只读，必须先停用；
- 仅修改字段名称、说明和展示排序属于纯模型元数据变更，可直接保存，不要求执行 DDL，也不依赖物理表处于严格匹配状态；
- 物理表不存在时，字段仍可直接保存，便于新模型先定义字段、再建表；
- 物理表已严格匹配时，涉及字段编码、类型参数、可空、主键、字段增删等物理结构的直接保存请求拒绝，要求通过变更计划执行；
- 物理表已漂移、不可访问或不受支持时，物理结构修改仍被阻止，避免模型结构进一步偏离真实表；用户应先修复物理表状态或取消当前操作。

该规则由后端执行，前端根据本地字段差异和实时检查结果自动选择“保存字段”或“生成变更计划”。`EXTERNAL` 模型仍沿用原有“保存定义后重新校验/绑定”的流程；本轮不扩展外部表的受控改表能力。

### UI 结构与交互

模型详情新增紧凑的“物理变更”页签：

- 表格列出计划创建时间、基线/目标版本、策略、风险、原子性、状态和完成时间；操作列只有“查看”、待执行时的“取消”，保持图标化紧凑。
- 计划详情抽屉顶部显示状态、策略、风险、原子性和版本范围；失败或部分完成时持续显示后端错误码与错误信息。
- 抽屉内依次展示：变更操作、前置检查、方言给出的原因、受控 SQL。SQL 只读、可复制，按执行方式折叠显示；前后结构指纹也显示为可复制技术信息。
- 只有 `PLANNED` 状态且执行选项存在时显示执行按钮。每个选项都必须显式确认：确认框写明执行方式、最高风险、原子性和 SQL 条数。没有选项时，界面只显示不支持原因，不提供“强制执行”。
- 执行接口是同步的，因而界面只显示请求中的“执行中” loading；完成后立即使用服务端返回状态刷新计划、模型详情和物理表检查。`APPLYING` 是可恢复的服务端状态，页面刷新后仍会如实显示，并提供刷新入口；不虚构进度百分比。
- `PARTIAL` 明确显示为“可能已部分完成，必须人工核验”，不提供重试按钮。`FAILED` 显示失败原因；待执行计划可取消或在字段编辑后重新生成（旧计划由后端标记为 `SUPERSEDED`）。

字段页签会实时读取物理表状态：`MATCHED` 时依据差异自动显示“保存字段”或“生成变更计划”；其他非 `NOT_FOUND` 的受管状态持续告警并阻止物理结构修改，但纯模型元数据仍可保存。`DISABLED` 明确提示可以编辑，`PUBLISHED` 明确提示先停用。计划生成成功后自动打开计划详情抽屉，用户无需在页面间寻找新计划。

### 前端契约与缓存

新增明确的 TypeScript 类型及以下既有 REST 接口调用：

```text
POST /api/v1/models/{modelId}/physical-table-change-plans
GET  /api/v1/models/{modelId}/physical-table-change-plans
GET  /api/v1/models/{modelId}/physical-table-change-plans/{planId}
POST /api/v1/models/{modelId}/physical-table-change-plans/{planId}/actions/cancel
POST /api/v1/models/{modelId}/physical-table-change-plans/{planId}/actions/execute
```

TanStack Query 的计划列表 key 以模型 ID 和查询参数组成；生成、取消或执行后统一失效计划列表、模型详情、物理表检查和数据预览缓存。类型中完整表达策略、风险、原子性、检查、操作、执行选项与错误信息，不使用 `any` 或前端猜测枚举值。

ClickHouse 单机 `MergeTree` 的排序键也在模型抽屉中增加为可输入的字段编码标签，仅在选择 ClickHouse 受管存储时显示。它会提示“不是关系型唯一主键”；最终字段存在性和物理表存在时的修改限制仍以后端校验为准。

### 验收与测试

- UI 静态逻辑测试覆盖：策略/风险/状态标签、停用模型编辑状态，以及纯模型元数据和物理结构修改的按钮分流。
- `pnpm check` 覆盖类型检查、lint、生产构建和前端单元测试。
- 后端集成测试覆盖：停用模型的纯模型元数据可直接保存；物理表已匹配时结构性 `update-fields` 被拒绝并可通过变更计划执行；已发布模型保持只读；物理表不存在时仍能直接保存。
- 不在本步连接 PostgreSQL、达梦或 ClickHouse；本步验证的是界面与既有受控接口的契约和状态处理。
