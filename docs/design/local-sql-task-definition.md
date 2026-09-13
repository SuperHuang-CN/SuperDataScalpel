# 本地 SQL 任务定义第一版

## 目标与核心决策

本文设计第一阶段中的 `LOCAL_SQL` 任务定义子域，包括任务基本信息、SQL 定义、模型引用、定义校验和发布状态。手动运行与运行结果按[第一阶段开发计划](./local-sql-task-development-plan.md)实现；定时调度、逐行运行日志和取消操作不进入第一阶段。

本版采用以下明确边界：

- 一个任务只包含一条查询 SQL，不在一个任务内部维护多条顺序执行的 SQL。多条 SQL 的失败边界、事务范围和中间结果都属于工作流能力，不在本版隐式引入。
- SQL 在一个 JDBC 数据存储内执行。所有输入模型与输出模型必须绑定同一个数据存储。
- 用户编写的是 `SELECT` 查询部分，允许以 `WITH` 开头的查询；平台根据输出模型生成完整的 `INSERT INTO ... SELECT`。任务定义不接受用户直接编写目标表、`INSERT`、`UPDATE`、`DELETE`、DDL 或多语句脚本。
- 输入模型是显式声明的表级依赖和编辑辅助信息。发布校验使用 JSqlParser AST 验证基础表引用并生成字段血缘，但 AST 不作为 SQL 安全边界，也不把输入模型声明扩展为细粒度访问权限。
- 输出字段通过 SQL 结果列别名与模型字段编码匹配，不增加独立字段映射配置。查询结果可以是输出模型字段的子集，但输出模型必须定义主键，且结果中必须包含全部主键字段；需要改名时直接在 SQL 中使用 `AS`。
- 任务定义使用关系表保存，不把整个定义序列化为不可查询的 JSON。这样可以直接完成模型引用保护、模型血缘查询和后续运行快照生成。
- 不引入新的 Maven 模块、调度框架、消息队列或执行器抽象。

## 业务语义

一个本地 SQL 任务表示一次数据库内的数据加工：

```text
同一 JDBC 数据存储
  输入模型（一个或多个）
       ↓
  一条 SELECT / WITH ... SELECT 查询
       ↓
  输出模型（恰好一个）
       ↓
  APPEND 或 OVERWRITE 写入策略
```

任务定义只引用模型 UUID。模型名称、物理表名、数据源名称等展示信息在响应时查询，不冗余保存。数据源由输出模型推导，并与每个输入模型的 `storageDataSourceId` 比较。

第一版不支持：

- 跨数据源查询；
- 一个任务内的多 SQL 步骤；
- 存储过程、DDL、DML 脚本；
- SQL 参数、运行变量和密钥变量；
- `MERGE`、主键更新或自定义冲突策略；
- 临时表、中间模型和任务依赖；
- SQL 级表权限分析；
- 调度配置和并发策略。

## `INSERT INTO ... SELECT` 生成规则

任务定义保存的是查询部分，而不是最终执行语句。例如用户保存：

```sql
WITH paid_order AS (
    SELECT customer_id, amount
    FROM order_detail
    WHERE status = 'PAID'
)
SELECT
    customer_id,
    COUNT(*) AS order_count,
    SUM(amount) AS total_amount
FROM paid_order
GROUP BY customer_id
```

平台根据输出模型的物理表和字段生成逻辑上等价的写入语句：

```sql
INSERT INTO order_summary (customer_id, order_count, total_amount)
WITH paid_order AS (...)
SELECT customer_id, COUNT(*) AS order_count, SUM(amount) AS total_amount
FROM paid_order
GROUP BY customer_id
```

上述示例只表达语义，不作为所有数据库共用的字符串模板。不同数据库对 CTE 的位置要求并不完全相同：

- 普通查询统一为 `INSERT INTO <target> (<columns>) SELECT ...`。
- MySQL 等数据库允许 CTE 位于目标表之后、主查询之前，即 `INSERT INTO ... WITH ... SELECT ...`。
- SQL Server 要求 CTE 位于 `INSERT` 之前，即 `WITH ... INSERT INTO ... SELECT ...`。
- PostgreSQL 同时支持将 `WITH` 附着到 `INSERT`，也支持查询部分自身带 `WITH`；实现时仍选择一种稳定的方言输出形式。
- ClickHouse 明确同时支持 `INSERT INTO ... WITH ... SELECT ...` 和 `WITH ... INSERT INTO ... SELECT ...`；本项目统一采用前一种形式。
- Oracle、达梦、人大金仓和 openGauss 由各自方言实现并通过针对性测试确认，任务 Service 不直接拼接数据库专属形式。

因此 SQL 词法检查需要把定义规范化为“可选 CTE 部分 + 最外层 SELECT 部分”，方言层再生成最终写入 SQL。该检查只跟踪注释、字符串、引用标识符和括号层级，仍是单语句和只读安全边界；通过后另由 JSqlParser AST 做静态血缘分析，后者不参与 SQL 执行授权。

`WITH` 中的每个 CTE 也必须是只读查询。即使 PostgreSQL 等数据库允许在 CTE 内执行 `INSERT`、`UPDATE` 或 `DELETE`，本任务类型也明确拒绝这种写法，保证整条定义只有平台生成的一个写入目标。

最终 `INSERT` 必须显式列出目标字段，并按照 SQL 结果列的实际顺序生成该字段列表，不能直接使用模型字段默认顺序。ClickHouse 的 `INSERT ... SELECT` 按位置映射列，因此即使 SQL 输出别名集合正确，目标字段列表顺序错误也会写入错误字段。

## 领域模型

### `DataTask`

表名：`task`

| 字段 | 类型 | 约束与含义 |
| --- | --- | --- |
| `id`、`created_at`、`updated_at` | UUID / 时间 | 继承 `BaseEntity` |
| `name` | String(100) | 任务名称 |
| `directory_id` | UUID，可空 | `DirectoryScope.TASK` 下的目录标量引用 |
| `type` | `TaskType` | 创建时必填；本任务定义只适用于 `LOCAL_SQL` |
| `status` | `TaskStatus` | `DRAFT`、`PUBLISHED`、`DISABLED` |
| `description` | String(1000)，可空 | 任务说明 |

任务类型创建后不可修改。任务名称、目录和说明不影响执行语义，任何状态下都可以更新；类型和当前定义版本不可通过基本信息接口修改。任务及其定义、计划和运行实例统一使用任务 UUID 关联，不再维护额外的任务编码。

### `LocalSqlTaskDefinition`

表名：`task_local_sql_definition`

| 字段 | 类型 | 约束与含义 |
| --- | --- | --- |
| `id`、`created_at`、`updated_at` | UUID / 时间 | 继承 `BaseEntity` |
| `task_id` | UUID | 唯一、不可修改，标量引用 `DataTask` |
| `sql_text` | 长文本 | 一条 `SELECT` 或 `WITH ... SELECT` 查询，最多 100,000 字符 |
| `output_model_id` | UUID | 唯一输出模型 |
| `write_mode` | `LocalSqlWriteMode` | `APPEND` 或 `OVERWRITE` |
| `timeout_seconds` | Integer | SQL 超时，默认 300，范围 1～3600 秒 |
| `version` | Integer | 初次保存为 1，每次成功更新整体加 1 |

`task_id` 建唯一约束。定义不存在表示任务尚未配置；不使用一行大量空字段表示未配置状态。

SQL 文本使用普通字符串长字段映射，不使用 PostgreSQL 专属 `columnDefinition`。具体 JPA 映射在实现时以 Hibernate 可移植长字符串类型为准。

### `LocalSqlTaskInput`

表名：`task_local_sql_input`

| 字段 | 类型 | 约束与含义 |
| --- | --- | --- |
| `id`、`created_at`、`updated_at` | UUID / 时间 | 继承 `BaseEntity` |
| `task_id` | UUID | 标量引用 `DataTask` |
| `model_id` | UUID | 输入模型标量引用 |
| `sort_order` | Integer | 编辑器和响应中的稳定展示顺序，从 0 开始 |

建立以下唯一约束：

- `(task_id, model_id)`：同一输入模型不能重复选择；
- `(task_id, sort_order)`：同一任务的输入顺序不能重复。

更新定义采用“校验完整请求 → 删除原输入引用 → 批量保存新输入引用 → 更新定义版本”的单个管理库事务。输入数量限制为 1～50 个。

### 枚举

```text
TaskType
  LOCAL_SQL
  SPARK_CANVAS

TaskStatus
  DRAFT
  PUBLISHED
  DISABLED

LocalSqlWriteMode
  APPEND
  OVERWRITE
```

`OVERWRITE` 语义为“清空输出表后写入本次查询结果”。PostgreSQL、HighGo、openGauss 与人大金仓在同一外部 JDBC 事务中实现并声明此能力；其他方言保存草稿时可选择，但发布、校验和运行会返回 `WRITE_MODE_UNSUPPORTED`。ClickHouse 只支持 `APPEND`。

## 状态与修改规则

```text
创建
  ↓
DRAFT ──发布──> PUBLISHED ──停用──> DISABLED
                  ↑                    │
                  └────重新启用────────┘
```

- `DRAFT`：可以保存和修改 SQL 定义；通过完整校验后可以发布。
- `PUBLISHED`：定义不可修改、任务不可删除；基本名称、目录和说明仍可修改。
- `DISABLED`：可以修改定义，也可以删除。修改定义后保持 `DISABLED`，重新启用时重新执行完整校验。
- `PUBLISHED -> DISABLED` 不删除定义，也不回退版本。
- `DISABLED -> PUBLISHED` 复用“发布”级别的完整校验。
- 任务类型永远不允许修改。

定义版本只在定义内容实际成功保存后递增。输入顺序、SQL、输出模型、写入方式或超时时间任一变化都视为新版本。未来创建运行实例时必须复制该版本的完整不可变快照，不能只记录 `taskId` 后读取最新定义。

## 请求与响应契约

### 创建任务

```json
{
  "name": "每日订单汇总",
  "directoryId": "uuid-or-null",
  "type": "LOCAL_SQL",
  "description": "将订单明细汇总到订单统计模型"
}
```

`type` 在创建请求中必填；创建本地 SQL 任务时传 `LOCAL_SQL`。类型创建后不可修改，创建后状态为 `DRAFT`，定义尚未配置。

### 保存定义

```json
{
  "sql": "SELECT customer_id, COUNT(*) AS order_count, SUM(amount) AS total_amount FROM order_detail GROUP BY customer_id",
  "inputModelIds": [
    "input-model-uuid"
  ],
  "outputModelId": "output-model-uuid",
  "writeMode": "OVERWRITE",
  "timeoutSeconds": 300
}
```

保存请求始终提交完整定义，不提供字段级增量更新。服务端负责去除 SQL 首尾空白和可选的单个末尾分号；不改写 SQL 内部格式。

### 任务列表响应

列表不返回 SQL 和完整模型列表，只返回管理页面需要的摘要：

```json
{
  "id": "task-uuid",
  "name": "每日订单汇总",
  "directoryId": "directory-uuid",
  "type": "LOCAL_SQL",
  "status": "DRAFT",
  "definitionConfigured": true,
  "definitionVersion": 2,
  "outputModelId": "output-model-uuid",
  "outputModelName": "订单统计",
  "updatedAt": "2026-07-14T10:00:00+08:00"
}
```

### 定义详情响应

定义响应返回引用对象的当前摘要，摘要只用于展示，不进入更新请求：

```json
{
  "taskId": "task-uuid",
  "configured": true,
  "version": 2,
  "sql": "SELECT ...",
  "inputs": [
    {
      "modelId": "input-model-uuid",
      "modelCode": "order_detail",
      "modelName": "订单明细",
      "schemaVersion": 3
    }
  ],
  "output": {
    "modelId": "output-model-uuid",
    "modelCode": "order_summary",
    "modelName": "订单统计",
    "schemaVersion": 1
  },
  "resolvedDataSource": {
    "id": "datasource-uuid",
    "code": "warehouse",
    "name": "业务数据仓库",
    "type": "POSTGRESQL"
  },
  "writeMode": "OVERWRITE",
  "timeoutSeconds": 300,
  "updatedAt": "2026-07-14T10:00:00+08:00"
}
```

未配置时仍返回 `200`，其中 `configured=false`、`version=0`、`inputs=[]`，其余定义字段为空或使用界面默认值。这样编辑页不需要把“未配置”当作异常处理。

## 校验规则

### 保存定义时的本地校验

保存不访问外部数据库，避免数据库短时不可用导致草稿无法保存，但必须完成以下检查：

1. 任务存在、类型为 `LOCAL_SQL`，状态为 `DRAFT` 或 `DISABLED`。
2. SQL 非空且不超过 100,000 字符。
3. SQL 词法检查后只有一条语句；允许 `SELECT` 或 `WITH ... SELECT`，拒绝 DDL、DML 和 JDBC 多语句。
4. 输入模型数量为 1～50，UUID 不重复；输出模型不能同时作为输入模型。
5. 所有模型存在。
6. 所有输入模型和输出模型的 `storageDataSourceId` 相同。
7. 推导出的数据源存在、已启用、连接类型为 JDBC，并具有 `STORAGE` 用途。
8. `writeMode` 只允许 `APPEND`、`OVERWRITE`；`OVERWRITE` 的输出模型必须为 `MANAGED` 模式，避免直接清空外部绑定表。
9. 超时时间范围为 1～3600 秒。

保存阶段允许模型仍处于 `DRAFT` 或 `DISABLED`，便于任务和模型并行准备；发布阶段才要求模型全部可运行。

SQL 词法检查只承担“单条只读查询”这一明确边界。JSqlParser AST 单独负责表引用验证和字段 provenance；解析失败只降低血缘覆盖度，不绕过词法检查，也不阻断原本能通过 JDBC 元数据校验的方言 SQL。

### 校验定义、发布和重新启用时的完整校验

完整校验在本地校验基础上增加：

1. 输入模型和输出模型都必须为 `PUBLISHED`。
2. 输出模型物理表必须存在，并与当前模型字段定义一致。
3. 每个输入模型的物理表必须存在，并与当前模型字段定义一致。
4. 数据源仍处于启用状态，数据库驱动可用且能够建立连接。
5. 在只读短连接中检查查询可编译，并读取结果列元数据；不写入输出表。
6. 查询结果列名按数据库标识符规则归一化后必须唯一。
7. 输出模型必须至少定义一个主键字段，查询结果必须包含全部主键字段。
8. 查询结果列必须都是输出模型已有字段；允许省略非主键字段，但不允许出现模型之外的多余字段。
9. 每个查询结果 JDBC 类型必须能够映射为平台逻辑类型，并与对应输出字段类型兼容。

完整校验返回结构化结果，不使用单一字符串吞掉所有问题：

```json
{
  "valid": false,
  "definitionVersion": 2,
  "issues": [
    {
      "code": "MISSING_PRIMARY_KEY_COLUMN",
      "field": "customer_id",
      "message": "查询结果缺少输出模型主键字段：customer_id"
    }
  ],
  "columns": [
    {
      "name": "customer_id",
      "logicalType": "STRING",
      "outputFieldCode": "customer_id",
      "compatible": true
    }
  ]
}
```

外部 JDBC 校验不得运行在管理库的 JPA 事务中。`data-scalpel-business/task` 负责编排任务、模型和数据源；纯 JDBC 的查询元数据检查能力放入 `data-scalpel-dialect`，保持其不依赖 Spring、JPA 和业务实体。

当前 `DatabaseDialect` 尚未公开任意查询的只读元数据检查和 `INSERT INTO ... SELECT` 渲染能力。实现本设计时，应增加两个小而明确的方言/JDBC 能力：

- 根据目标表、目标字段、可选 CTE 和最外层 SELECT 生成最终写入 SQL；
- 按数据库生成零行查询包装并读取 `ResultSetMetaData`。

任务 Service 只提供经过校验的结构化参数，不复制八种数据库语法。JSqlParser 依赖只存在于 `data-scalpel-dialect`，该模块向业务层暴露不含第三方 AST 类型的中性血缘结果。

### SQL 与模型引用的关系

输入模型列表是用户显式确认的表级依赖和编辑器 Schema 来源。AST 会把 `FROM`、`JOIN`、CTE、子查询和集合运算中的基础表匹配到这些显式模型；可靠发现的未声明或歧义表引用会阻断发布，未使用的已声明输入只产生警告并继续保留为表级输入资产。系统不自动修改输入模型列表，也不把物理表名替换为模板变量。

字段血缘以 AST provenance 和 JDBC 实际输出列共同决定：裸字段为 `DIRECT`，函数、计算、`CASE` 和类型转换为 `CALCULATED`，聚合为 `AGGREGATED`；Join、过滤、分组、排序和窗口分区独立记录为字段用途。常量和 NULL 使用无来源输出行为，无法证明的写入字段使用 `WRITTEN_UNKNOWN_SOURCE`。AST 完全可靠时发布 `FIELD_COMPLETE`，其余发布 `FIELD_PARTIAL`，不得用同名或字符串匹配猜测来源。

编辑器应提供“插入表名”“插入字段名”操作，使用模型当前的 `catalog/schema/physicalTableName` 和方言引用规则帮助用户生成正确 SQL。发布校验关注查询能否编译以及输出结构是否匹配。

数据源配置的 Schema 会作为 PostgreSQL、Oracle、达梦、人大金仓和 openGauss JDBC 会话的默认 Schema，因此这些数据库可以引用该 Schema 下的未限定表名。MySQL 和 ClickHouse 以数据源的数据库名作为默认命名空间。SQL Server 驱动不支持在连接后修改会话默认 Schema，且一个任务可能引用不同 Schema 的模型；SQL Server 中应始终使用 `[schema].[table]` 形式的完整表名。为消除跨数据库和跨 Schema 的歧义，任务 SQL 均建议使用模型提供的完整物理表名。

## REST API

所有业务写操作遵守 GET/POST 约定：

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| `GET` | `/api/v1/tasks` | `task.view` | 使用统一 Search DSL 查询任务列表 |
| `GET` | `/api/v1/tasks/{id}` | `task.view` | 查询任务基本详情和定义摘要 |
| `GET` | `/api/v1/tasks/{id}/definition` | `task.view` | 查询完整本地 SQL 定义 |
| `GET` | `/api/v1/tasks/{id}/model-relations` | `task.view` | 聚合查询最后保存定义中的输入、输出模型及引用位置 |
| `POST` | `/api/v1/tasks` | `task.create` | 创建任务基本信息，`type` 决定定义类型 |
| `POST` | `/api/v1/tasks/{id}/actions/update` | `task.update` | 修改名称、目录和说明 |
| `POST` | `/api/v1/tasks/{id}/actions/update-definition` | `task.update` | 整体保存本地 SQL 定义 |
| `POST` | `/api/v1/tasks/{id}/actions/validate-definition` | `task.update` | 对已保存定义执行完整只读校验 |
| `POST` | `/api/v1/tasks/{id}/actions/publish` | `task.publish` | 校验成功后从草稿发布 |
| `POST` | `/api/v1/tasks/{id}/actions/disable` | `task.publish` | 停用已发布任务 |
| `POST` | `/api/v1/tasks/{id}/actions/enable` | `task.publish` | 完整校验后重新启用 |
| `POST` | `/api/v1/tasks/{id}/actions/delete` | `task.delete` | 删除草稿或已停用任务及其定义 |

`validate-definition` 校验已保存版本，不接收临时 SQL。用户先保存，再校验，避免“界面验证的是一份内容、服务端发布的是另一份内容”。发布接口内部再次执行完整校验，不能依赖之前的校验结果或时间戳。

校验响应同时返回 `lineageCoverage`、`lineageAnalysisStatus` 和 `lineageWarnings`，用于在界面预告正式发布时的血缘覆盖程度；校验本身不写快照。发布和重新启用在事务外完成 JDBC/AST 分析和草稿构造，最终短事务内先幂等发布血缘快照，再变更任务状态，任一失败共同回滚。保存、停用和重复运行均不修改血缘。

任务详情的关联模型查询以 `task_local_sql_input` 和 `task_local_sql_definition.output_model_id` 为事实来源，分别映射为 `INPUT` 和 `OUTPUT`。同一模型按 UUID 聚合，输入位置使用一基顺序展示；SQL 文本中的表名不会被解析为模型关系。模型侧通过 `/api/v1/models/{id}/related-tasks` 反查任务，返回的是当前保存定义的只读投影，不包含运行快照或历史版本。

普通列表使用 `SearchRequest`、`SearchEngine`、`SearchRepository`。第一版允许搜索和排序的主要字段为：`name`、`directoryId`、`type`、`status`、`createdAt`、`updatedAt`。SQL 长文本和模型引用不进入实体 Search DSL。

## 包结构

所有实现都位于 `data-scalpel-business` 的 `task` 业务包：

```text
business/task/
├── domain/
│   ├── DataTask
│   ├── LocalSqlTaskDefinition
│   ├── LocalSqlTaskInput
│   ├── TaskType
│   ├── TaskStatus
│   └── LocalSqlWriteMode
├── repository/
│   ├── DataTaskRepository
│   ├── LocalSqlTaskDefinitionRepository
│   └── LocalSqlTaskInputRepository
├── service/
│   ├── DataTaskService
│   ├── LocalSqlDefinitionValidator
│   └── LocalSqlDefinitionInspectionPort
└── web/
    ├── resource/
    ├── request/
    └── response/
```

这里的 `LocalSqlDefinitionInspectionPort` 只隔离外部 JDBC 检查，便于单元测试和明确 JPA 事务边界；不为 Repository、Service 或 DTO 映射增加接口、工厂、Assembler、Command 等额外层次。

普通任务请求、响应和第一阶段的 `TaskRunDefinitionSnapshot` 均保留在业务模块内，不移入 `data-scalpel-contracts`。只有未来独立执行端确实需要稳定的跨模块任务协议时，再设计相应契约。

## 目录、权限和引用保护

- `DirectoryScope` 增加 `TASK`。
- 目录树统计通过 `DataTaskRepository` 按 `directory_id` 聚合；包含任务的目录不能删除。
- 权限目录增加 `task.view`、`task.create`、`task.update`、`task.delete`、`task.publish`、`task.execute`。定义校验属于更新权限，不单独增加权限。
- 数据模型被任意本地 SQL 任务作为输入或输出引用时不能删除；必须先修改或删除引用任务。
- 数据源不被任务直接引用。数据源已有的“被模型引用则不能删除”规则自然保护任务的推导数据源。
- 删除任务时，在同一事务内先删除输入引用和定义，再删除任务；不使用数据库级级联和 JPA Entity 关联。

## 前端设计

本地 SQL 不使用 AntV X6。现有 `modules/task/canvas` 与本功能没有依赖关系，后续 Canvas 任务立项前不扩展它。

任务模块按以下结构组织：

```text
modules/task/
├── api/
├── components/
├── hooks/
├── model/
├── pages/
└── index.ts
```

页面建议：

- 任务列表：左侧 `TASK` 目录树，右侧统一查询栏和任务表格；新建、编辑基本信息使用 Drawer。
- 任务详情：基础信息、SQL 定义摘要；执行历史在运行阶段再增加，不放空标签页。
- SQL 定义编辑器：使用独立懒加载路由 `/tasks/{id}/definition`，不放在普通 Drawer 中。

编辑器布局：

```text
顶部：返回 / 保存 / 校验 / 发布（或重新启用）
左侧：输入模型列表及字段 Schema，可插入表名、字段名
中间：Monaco SQL 编辑器
右侧：输出模型、输出字段 Schema、写入方式、超时时间
底部：结构化校验结果和 SQL 输出列对照
```

工程已经包含 Monaco，编辑器必须路由级懒加载，不能进入任务列表和应用首页首屏包。服务端数据由 TanStack Query 管理；编辑中的 SQL 和表单状态使用 React/Ant Design Form，不建立全局 Store。

离开编辑页时，如本地内容与最近一次成功保存的定义不同，应提示未保存修改。保存成功后刷新任务详情和定义 query；校验、发布只让当前任务对应操作进入 loading。

## 测试与验收基线

后端至少覆盖：

- 任务目录范围和状态流转；
- 已发布定义不可修改、已发布任务不可删除；
- 定义整体替换和版本递增；
- 输入模型去重、输出不能作为输入、同一数据源约束；
- 非 JDBC、未启用、非存储用途数据源校验；
- `OVERWRITE` 对外部绑定模型的限制；
- 单条查询边界和多语句/DML/DDL 拒绝；
- 发布时模型状态、物理表和 SQL 输出字段校验；
- 模型和目录删除引用保护；
- REST 只使用 GET/POST，并返回明确 DTO；
- 外部 JDBC 失败不会遗留错误的 `PUBLISHED` 状态。
- 输入或输出模型包含 Geometry 时在 SQL 元数据检查前返回 `SPATIAL_FIELD_UNSUPPORTED`。

前端至少覆盖：

- 任务列表查询与目录筛选；
- 定义请求/响应的严格 TypeScript 类型；
- 输入/输出模型选择和同源提示；
- 保存、校验、发布状态控制；
- 未保存离开提示；
- Monaco 懒加载不进入普通页面首屏。

实现完成时后端运行 `./mvnw verify`，前端运行 `pnpm check`。

## 后续阶段接口边界

第一阶段已经按定义版本生成不可变运行快照，并提供本地线程池、单任务并发限制和手动运行。调度、逐行日志、取消、失败重试和跨进程恢复仍不进入本阶段。

## 已实现的运行边界

- Local SQL V1 不支持 Geometry 模型输入或输出，不把空间列映射为字符串或二进制。
- `POST /api/v1/tasks/{id}/actions/run` 只接受已发布任务，并立即创建 `TaskRun` 后提交到有界本地线程池；默认并发为 4、队列为 100，可通过 `DATASCALPEL_TASK_RUN_CONCURRENCY`、`DATASCALPEL_TASK_RUN_QUEUE_CAPACITY` 调整。
- `TaskRun` 保存无凭据的定义版本快照，状态为 `QUEUED`、`RUNNING`、`SUCCESS`、`FAILED` 或 `TIMED_OUT`。同一任务同时只允许一个活动实例；应用启动时把遗留活动实例标为失败，不尝试恢复外部 JDBC 语句。
- `APPEND` 执行方言生成的单条 `INSERT INTO ... SELECT`。PostgreSQL、HighGo、openGauss 与人大金仓的 `OVERWRITE` 在同一个外部事务中先执行受控清空、再写入，异常时回滚；不对其他方言静默执行 `TRUNCATE + INSERT`。
- 前端任务列表、定义页、Monaco 编辑器、结构化校验结果和运行记录轮询已接入；Monaco 仅由定义页懒加载。

自动化测试已覆盖八种方言的 SQL 形状、任务定义生命周期、引用保护和运行记录入队。`PostgreSqlLocalSqlTaskIntegrationTest` 还会在可选 PostgreSQL 环境中通过完整任务 Service 与异步 Worker 验收普通/CTE `APPEND`、查询列顺序、列级发布拒绝、运行失败、超时、同任务并发限制、事务性 `OVERWRITE` 回滚、停用/重新启用和无凭据运行快照。该验收已于 2026-07-14 执行通过。

真实数据库验证范围如下：

| 数据库 | 第一阶段能力 | 验证状态 |
| --- | --- | --- |
| PostgreSQL | `APPEND`、事务性 `OVERWRITE` | 方言单测与真实任务集成测试已通过 |
| HighGo、openGauss、人大金仓 | `APPEND`、事务性 `OVERWRITE` | PostgreSQL 家族方言契约测试；未执行真实库验收 |
| ClickHouse | `APPEND` | 方言渲染/目标字段顺序单测；未执行真实库验收 |
| MySQL、Oracle、SQL Server、达梦 | `APPEND` | 方言渲染单测；未执行真实库验收 |

真实 PostgreSQL 验收通过 `DATASCALPEL_PG_INTEGRATION=true` 启用；环境变量和执行命令见[开发计划](./local-sql-task-development-plan.md#postgresql-真实验收的复现方式)。
