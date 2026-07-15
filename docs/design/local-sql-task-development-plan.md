# 本地 SQL 任务第一阶段 Codex 开发计划

## 第一阶段交付目标

第一阶段只交付 `LOCAL_SQL` 任务的最小完整闭环：

```text
任务 CRUD
  → 配置一条 SELECT / WITH ... SELECT
  → 选择同一数据存储内的输入模型和输出模型
  → 保存、校验、发布
  → 手动触发异步运行
  → 方言生成 INSERT INTO ... SELECT
  → 查看运行状态和错误结果
```

完成标准不是只出现任务菜单或保存一段 SQL，而是用户能够通过页面创建一个本地 SQL 任务，并在受支持的 JDBC 数据库中完成一次真实的 `INSERT INTO ... SELECT`。

第一阶段明确不做：

- Quartz、Cron、单次定时和周期调度；
- Canvas、Spark、Kafka、Dispatcher、独立 Actuator；
- 流任务、工作流任务、跨数据源任务和多 SQL 步骤；
- SQL 参数、运行变量、任务依赖和补数；
- 暂停、恢复、重试、手动取消和失败自动重试；
- 单独的逐行运行日志系统；
- 多实例分布式任务抢占和执行恢复；
- SQL Parser、Flyway、jOOQ 或新的基础设施依赖。

第一阶段只支持手动运行。调度属于下一阶段，并在确认后再评估 Quartz。

## 已确认的产品规则

本计划以[本地 SQL 任务定义设计](./local-sql-task-definition.md)为基础，开发中不得自行改变以下规则：

- 一个任务只有一条查询 SQL。
- SQL 可以是 `SELECT`，也可以是只读的 `WITH ... SELECT`。
- 用户只保存查询部分，平台根据输出模型生成 `INSERT INTO ... SELECT`。
- CTE 内同样禁止 `INSERT`、`UPDATE`、`DELETE` 和 DDL。
- 输入模型至少一个，输出模型恰好一个；输入和输出必须属于同一个启用的 JDBC 数据存储。
- 输入模型用于血缘和编辑辅助，不通过 SQL 解析推断或校验表权限。
- SQL 输出列别名必须与输出模型字段编码匹配。
- 最终 `INSERT` 显式列出目标字段，并按照查询结果列的实际顺序排列；ClickHouse 等按位置映射的数据库不能使用模型默认字段顺序。
- 第一版写入方式为 `APPEND` 和 `OVERWRITE`，但只有方言明确声明并验证相应能力时才允许发布和运行。
- 任务状态使用 `DRAFT`、`PUBLISHED`、`DISABLED`。
- 已发布任务不能修改定义；停用后可以修改，重新启用时重新完整校验。

## Codex 执行约束

- 从当前工作树继续开发，保留所有已有用户修改，不回滚、不覆盖无关文件。
- 后端只在现有 Maven 模块中开发：业务代码进入 `data-scalpel-business/task`，JDBC 方言能力进入 `data-scalpel-dialect`，运行配置进入 `data-scalpel-admin`。
- 不新增 Maven 模块，不把任务业务代码放入 `data-scalpel-admin`。
- 前端只扩展现有 `data-scalpel-ui/src/modules/task`，不拆应用、不引入新 UI 或状态框架。
- 使用工程内 `./mvnw`，不得绕过 `.mvn/maven.config` 和 `settings-superhuang.xml`。
- 每个开发批次先补有针对性的测试，再运行该批次相关验证；全部完成后运行 `./mvnw verify` 和 `pnpm check`。
- 外部 JDBC 调用不能处于管理库 JPA 事务中。
- 除用于隔离外部 JDBC 检查和异步执行边界外，不增加没有当前使用场景的接口、工厂、事件总线或转译层。
- 第一阶段不提交 Git commit、不推送远端，除非用户另行要求。

## 开发批次总览

| 批次 | 目标 | 完成后可见结果 |
| --- | --- | --- |
| P0 | 基线和测试落点 | 当前工程状态、现有改动和验证基线明确 |
| P1 | 方言层 `INSERT ... SELECT` 能力 | 八种方言能够生成普通查询和 CTE 写入 SQL |
| P2 | 任务定义后端 | 可通过 API 创建、配置、查询本地 SQL 任务，状态和引用规则落地 |
| P3 | SQL 只读检查和发布校验 | 能连接目标库检查 SQL 输出字段和类型，并完成发布 |
| P4 | 手动异步运行 | 发布任务可真实执行，产生持久化运行记录 |
| P5 | 任务管理前端 | 可管理任务、编辑 SQL、校验和发布 |
| P6 | 运行记录前端 | 可手动运行并查看状态、耗时和错误 |
| P7 | 全链路收口 | 测试、文档、构建和真实 PostgreSQL 验收通过 |

每个批次独立完成并验证后再进入下一批次。不得先堆完全部代码再一次性补测试。

## 当前实施状态（2026-07-14）

- P1 至 P7 已实现：方言层只读词法检查、CTE Insert-Select 渲染、任务定义和发布校验、持久化异步运行、任务管理 UI 与运行记录 UI 均已接入。
- 八种内建方言已有渲染单测；`OVERWRITE` 仅由 PostgreSQL 能力声明，ClickHouse 仅开放 `APPEND`。
- `TaskIntegrationTests` 覆盖定义保存、发布、停用、运行入队、模型/目录引用保护和“有运行记录后不可删除”。
- `PostgreSqlLocalSqlTaskIntegrationTest` 是按环境变量启用的真实 PostgreSQL 验收：普通和 CTE `APPEND`、查询列顺序、列级发布失败、运行失败、超时、同任务并发限制、`OVERWRITE` 成功与失败回滚、停用/重启用和运行快照均已于 2026-07-14 执行通过。
- MySQL、Oracle、SQL Server、ClickHouse、达梦、人大金仓和 openGauss 当前只有方言渲染与能力单测；不得将其表述为真实数据库执行验证。

## P0：基线确认

### 工作内容

- 记录 `git status --short --branch`，确认任务模块将修改的文件是否已有用户改动。
- 检查根 `pom.xml`、业务模块依赖、Admin 测试基类和前端路由现状。
- 运行不修改外部数据的当前基线测试：

```bash
./mvnw -pl data-scalpel-business,data-scalpel-admin -am test
```

在 `data-scalpel-ui` 目录运行：

```bash
pnpm check
```

- 记录已有失败，区分“开发前已存在”与本阶段引入的问题。

### 完成条件

- 没有覆盖当前数据源、模型、数据服务和前端模型详情页的在途修改。
- 确定任务集成测试沿用的 PostgreSQL 测试配置和可复用样例表。
- 若基线失败，先只记录；除非阻塞任务开发，不顺手修改无关问题。

## P1：扩展 JDBC 方言能力

### 目标

让 `data-scalpel-dialect` 提供与业务实体无关的两项能力：

1. 检查一条只读查询并返回结果列元数据；
2. 根据目标表、目标字段和查询部分生成最终 `INSERT INTO ... SELECT`。

### 数据结构

在方言模块增加小型、不可变模型，名称在实现时保持语义清晰即可：

- 查询结构：可选 CTE 部分、最外层 `SELECT` 部分；
- 查询结果列：名称、JDBC 类型、原生类型名、可空性；
- Insert-Select 计划：最终 SQL、目标字段顺序、方言能力信息。

这些类型不得引用 `DataTask`、`DataModel`、Spring 或 JPA。

### 方言接口

为现有 `DatabaseDialect` 增加明确能力，而不是在任务 Service 中写数据库类型 `switch`：

- 引用目标表和字段标识符；
- 生成普通 `INSERT INTO target(columns) SELECT ...`；
- 生成带 CTE 的 Insert-Select；
- 生成只返回元数据或零行的检查 SQL；
- 声明是否支持 `APPEND`；
- 声明是否支持第一版定义的 `OVERWRITE`。

CTE 输出形式至少覆盖：

| 方言 | 生成形式 |
| --- | --- |
| PostgreSQL | 固定使用经过测试的一种 `WITH ... INSERT` 或 `INSERT ... WITH ... SELECT` |
| MySQL | `INSERT INTO ... WITH ... SELECT ...` |
| SQL Server | `WITH ... INSERT INTO ... SELECT ...` |
| ClickHouse | `INSERT INTO ... WITH ... SELECT ...` |
| Oracle、达梦、人大金仓、openGauss | 由方言实现并用单元测试锁定 |

### 只读查询分析

- 实现轻量词法扫描，识别注释、字符串、引用标识符、分号和括号层级。
- 接受单条 `SELECT` 或 `WITH ... SELECT`。
- 将查询规范化为“可选 CTE + 最外层 SELECT”。
- 拒绝多语句，以及查询主体或 CTE 中的 DDL/DML。
- 不解析 `FROM/JOIN`，不推断模型血缘，不构建完整 SQL AST。

### 测试

- 每种内建方言至少覆盖普通 SELECT 和 WITH SELECT 两种 Insert-Select 输出。
- 覆盖 ClickHouse 目标字段顺序。
- 覆盖 SQL Server 的 CTE 前置形式。
- 覆盖字符串、注释、嵌套子查询中的关键字和分号，不产生误判。
- 覆盖多语句、DML CTE 和空 SQL 拒绝。

### 验证

```bash
./mvnw -pl data-scalpel-dialect test
```

### 完成条件

- 任务业务代码不需要判断数据库产品。
- 不新增第三方 SQL 解析依赖。
- 八种方言的 SQL 形状都被单元测试固定。

## P2：任务定义后端

### 领域与持久化

在 `data-scalpel-business/task` 实现：

- `DataTask`
- `LocalSqlTaskDefinition`
- `LocalSqlTaskInput`
- `TaskType.LOCAL_SQL`
- `TaskStatus`
- `LocalSqlWriteMode`
- 三个对应 Repository

遵守已有实体约定：`BaseEntity`、UUID、时间字段、标量 UUID 引用、无 JPA Entity 关联。

### 业务服务

实现一个直接的 `DataTaskService`，负责：

- Search DSL 列表查询；
- 创建和修改任务基本信息；
- 查询任务详情和定义；
- 整体保存定义并递增版本；
- 发布、停用、重新启用、删除；
- 输入/输出模型和数据源一致性校验；
- DTO 直接映射。

保存定义只做本地和管理库校验，不连接外部数据库。发布和重新启用调用 P3 的完整校验。

### API

实现定义设计中约定的 GET/POST 接口：

- `GET /api/v1/tasks`
- `GET /api/v1/tasks/{id}`
- `GET /api/v1/tasks/{id}/definition`
- `POST /api/v1/tasks`
- `POST /api/v1/tasks/{id}/actions/update`
- `POST /api/v1/tasks/{id}/actions/update-definition`
- `POST /api/v1/tasks/{id}/actions/validate-definition`
- `POST /api/v1/tasks/{id}/actions/publish`
- `POST /api/v1/tasks/{id}/actions/disable`
- `POST /api/v1/tasks/{id}/actions/enable`
- `POST /api/v1/tasks/{id}/actions/delete`

Web 代码按 `web/resource`、`web/request`、`web/response` 分包，不暴露 Entity。

### 横向接入

- `DirectoryScope` 增加 `TASK`。
- `DirectoryService` 增加任务目录统计和删除保护。
- 权限目录增加：`task.view/create/update/delete/publish`。
- `DataModelService.delete` 增加输入模型、输出模型引用保护。
- 任务列表 Repository 继承 `SearchRepository`。

### 测试

- Repository 唯一约束和目录统计。
- 创建、更新、定义整体替换、版本递增。
- 输入重复、输入输出相同、跨数据源、非 JDBC 和非存储数据源拒绝。
- 状态流转、已发布定义不可修改、删除限制。
- 模型和目录删除引用保护。
- Resource 权限、请求校验和 HTTP 状态码。

### 验证

```bash
./mvnw -pl data-scalpel-admin -am test
```

### 完成条件

- 不依赖外部业务数据库也能完成任务定义 CRUD。
- 数据库不可用时草稿仍可保存。
- 发布接口暂时接入 P3 的完整校验后才算最终完成。

## P3：完整 SQL 校验

### 业务边界

增加 `LocalSqlDefinitionInspectionPort` 及 JDBC 实现，用于：

- 获取输入、输出模型当前物理表状态；
- 建立短生命周期只读 JDBC 连接；
- 校验查询能够编译；
- 读取查询结果列元数据；
- 将结果列与输出模型字段进行名称和类型比较；
- 按查询结果列顺序确定最终目标字段顺序；
- 预生成最终 Insert-Select SQL，但不执行写入。

外部 JDBC 检查在 JPA 事务外执行。发布流程采用：

```text
管理库读取不可变校验快照
  → 结束只读事务
  → 外部 JDBC 检查
  → 新管理库事务重新确认定义版本未变化
  → 更新为 PUBLISHED
```

如果校验期间定义版本变化，发布返回冲突，不发布旧版本。

### 校验结果

返回结构化问题代码和列对照，不把所有错误压缩成异常字符串。至少包含：

- SQL 语法或连接失败；
- 模型未发布；
- 物理表不匹配；
- 输出字段缺失或多余；
- 输出列名重复；
- 字段类型不兼容；
- 写入方式不被当前方言支持。

### 数据库验证范围

- 自动化真实数据库集成测试以工程现有 PostgreSQL 环境为第一验收基线。
- 其余方言先完成渲染和类型映射单元测试。
- 如果没有 MySQL、Oracle、SQL Server、ClickHouse、达梦、人大金仓或 openGauss 测试环境，不宣称完成真实兼容性验证；文档明确记录未验证项。

### 验证

```bash
./mvnw -pl data-scalpel-dialect,data-scalpel-admin -am test
```

### 完成条件

- 校验接口能返回列级问题。
- 发布和重新启用必定重新校验当前定义版本。
- 校验不产生目标表写入。

## P4：手动异步运行

### 运行模型

增加 `TaskRun`，表名建议为 `ds_task_run`，继承 `BaseEntity`，至少包含：

| 字段 | 含义 |
| --- | --- |
| `task_id` | 任务 UUID 标量引用 |
| `definition_version` | 本次运行使用的定义版本 |
| `definition_snapshot` | 不含凭据的不可变运行定义 JSON |
| `trigger_type` | 第一版固定 `MANUAL` |
| `status` | `QUEUED/RUNNING/SUCCESS/FAILED/TIMED_OUT` |
| `queued_at/started_at/ended_at` | 生命周期时间 |
| `affected_rows` | JDBC 能返回时记录写入行数 |
| `message` | 简短结果或安全错误信息 |
| `error_detail` | 截断后的内部错误详情，不包含密码和连接密钥 |

运行快照至少保存 SQL、输入/输出模型 UUID、输出物理位置、数据源 UUID、写入方式、超时和目标字段顺序。不得保存数据库密码、JWT、数据源完整连接参数。

任务存在运行记录后第一阶段禁止删除任务，避免运行历史失去归属；可以停用。

### API

- `POST /api/v1/tasks/{id}/actions/run`：创建运行记录并异步提交，返回 `202 Accepted`。
- `GET /api/v1/tasks/{id}/runs`：使用固定 `taskId` 与 Search DSL 条件 `AND` 查询。
- `GET /api/v1/task-runs/{runId}`：查询运行详情。

增加权限 `task.execute`。只有 `PUBLISHED` 任务可以运行。

### 异步执行

- 使用 Spring 已有线程池能力，不引入消息队列。
- 使用可配置的小型固定线程池和有界队列；建议默认并发 4、队列 100，最终值进入 Admin 配置。
- 同一任务第一版只允许一个 `QUEUED` 或 `RUNNING` 实例；通过任务行悲观锁和活动实例检查避免并发竞争，不使用 `task.lastRunStatus` 作为锁。
- 队列满时不创建假运行，返回明确的服务繁忙错误；如果运行记录已创建，必须落为 `FAILED` 并写明原因。
- 应用启动时将遗留的 `QUEUED/RUNNING` 记录标记为 `FAILED`，原因是当前单体重启，第一阶段不尝试恢复外部 JDBC 语句。
- SQL 使用定义中的超时；超时映射为 `TIMED_OUT`。

### 写入执行

- `APPEND`：执行方言生成的一条 `INSERT INTO ... SELECT`。
- `OVERWRITE`：只在方言声明支持且行为已经测试时开放。
- 不用通用的“TRUNCATE 成功后 INSERT 失败”假装原子覆盖。
- 对不能保证本阶段覆盖语义的数据库，保存草稿可以保留该选项，但发布和运行必须返回明确的 `WRITE_MODE_UNSUPPORTED`；前端根据校验结果提示用户改为 `APPEND`。
- PostgreSQL 作为第一阶段真实 `OVERWRITE` 验收数据库时，在同一外部事务内完成清空和写入，并通过失败回滚测试。
- ClickHouse 第一阶段保证 `APPEND`；是否开放 `OVERWRITE` 取决于明确的数据替换策略，不以普通 `TRUNCATE + INSERT` 默认开启。

### 运行状态一致性

- 创建运行记录和提交线程池之间不得留下永久 `QUEUED` 状态。
- 状态只允许：`QUEUED -> RUNNING -> SUCCESS/FAILED/TIMED_OUT`。
- 所有终态必须设置 `endedAt`。
- 错误响应和运行详情不得返回凭据或完整 JDBC URL。

### 测试

- 发布状态、同任务并发和权限检查。
- 定义快照不可变。
- 普通 SELECT、WITH SELECT 真实写入。
- ClickHouse 形式的 SQL 渲染和目标字段顺序单元测试。
- APPEND 成功、SQL 失败、超时、队列满和应用恢复状态。
- PostgreSQL OVERWRITE 成功和中途失败回滚。
- 运行列表固定条件不能被客户端 Search DSL 绕过。

### 验证

```bash
./mvnw -pl data-scalpel-admin -am test
```

### 完成条件

- 手动运行立即返回运行 ID，不占用 HTTP 请求直到 SQL 完成。
- 运行状态最终可收敛，不出现永久活动状态。
- 至少在真实 PostgreSQL 测试库完成 Insert-Select 闭环。

## P5：任务管理与 SQL 编辑器前端

### 路由和模块

在现有 `modules/task` 内增加：

```text
modules/task/
├── api/
├── components/
├── hooks/
├── model/
├── pages/
└── index.ts
```

现有 Canvas 示例保持隔离，不复用到本地 SQL 页面，也不让 X6 进入任务列表首屏包。

建议路由：

- `/tasks`：任务列表；
- `/tasks/:taskId`：任务详情；
- `/tasks/:taskId/definition`：本地 SQL 定义编辑器，路由级懒加载。

### 任务列表

- 左侧 `TASK` 目录树，右侧 Search DSL 查询和表格。
- 高频筛选：关键词、状态；高级筛选：编码、更新时间。
- 新建和基本信息编辑使用 Drawer。
- 行操作根据状态显示：详情、编辑定义、发布/停用/启用、运行、删除。
- 权限不足时不展示操作，同时后端仍强制鉴权。

### SQL 定义编辑器

- 使用工程已有 Monaco，不新增编辑器依赖。
- 左侧显示输入模型及字段；提供插入完整物理表名和字段名。
- 中间编辑 `SELECT` 或 `WITH ... SELECT`。
- 右侧配置输出模型、写入方式、超时，并展示输出字段。
- 底部展示 SQL 校验结果、结果列顺序、输出字段匹配和方言能力问题。
- “保存”只保存定义；“校验”校验已保存版本；“发布”内部再次校验。
- 有未保存修改时离开页面必须确认。
- 使用 TanStack Query 管理任务、定义、模型和校验结果；编辑态使用 Form/React 状态。

### 跨模块依赖

- 任务模块通过模型模块 `index.ts` 使用模型选择器和模型摘要类型。
- 任务模块通过目录模块公开入口使用目录树。
- 不从其它业务模块内部路径导入。

### 测试

- 任务 Search 请求和目录筛选。
- DTO 严格 TypeScript 类型，无 `any`。
- SQL 定义表单转换、输入输出同源提示、字段顺序展示。
- 状态对应操作可见性。
- 未保存离开保护。
- Monaco 路由懒加载。

### 验证

```bash
cd data-scalpel-ui
pnpm check
```

## P6：运行记录前端

### 工作内容

- 任务详情增加“运行记录”区域，只在运行 API 完成后加入。
- 发布任务提供“立即运行”，提交后跳转或定位到新运行记录。
- 使用轮询刷新 `QUEUED/RUNNING` 记录；进入终态后停止轮询。
- 显示状态、定义版本、触发时间、开始/结束时间、耗时、影响行数和安全错误信息。
- 第一阶段没有逐行日志、取消、重试按钮，不显示未实现占位入口。
- 行级运行操作只锁定当前任务，不锁定整个任务列表。

### 测试

- 运行请求和 query cache 刷新。
- 活动状态轮询启动、终态停止和页面卸载清理。
- 失败、超时和队列满反馈。
- 无运行记录、加载失败和重试状态。

### 验证

```bash
cd data-scalpel-ui
pnpm check
```

## P7：全链路收口

### 自动化验证

在工程根目录执行：

```bash
./mvnw verify
```

在前端目录执行：

```bash
pnpm check
```

如果前端依赖或构建配置发生变化，额外确认生产构建和首屏分包；本计划原则上不新增前端依赖。

### PostgreSQL 真实验收场景

准备同一数据存储下的输入、输出模型，至少验收：

1. 普通 `SELECT` + `APPEND`；
2. `WITH ... SELECT` + `APPEND`；
3. SQL 输出列顺序与模型字段顺序不同，但按别名正确写入；
4. 输出模型没有主键、查询缺少主键字段、包含模型外字段或类型不兼容时发布失败；
5. SQL 运行错误时目标表和运行状态符合预期；
6. 超时后运行进入 `TIMED_OUT`；
7. 同一任务并发触发被拒绝；
8. PostgreSQL `OVERWRITE` 成功以及失败回滚；
9. 停用后不能运行，修改定义并重新启用后运行新版本；
10. 运行快照不随任务后续修改而变化。

### 文档收口

- 更新 README 当前能力和启动依赖。
- 更新本地 SQL 任务定义文档中的最终 API 与已实现方言能力。
- 记录每种数据库的“渲染单测已覆盖 / 真实数据库已验证”状态，不能混为一谈。
- 明确 ClickHouse 第一阶段的 APPEND 支持和 OVERWRITE 状态。

## 第一阶段验收清单

- [x] 只存在 `LOCAL_SQL` 一种可创建任务类型。
- [x] 任务目录、权限、Search 列表和引用删除保护完成。
- [x] 定义支持单条 `SELECT` 和只读 `WITH ... SELECT`。
- [x] 八种方言的 Insert-Select SQL 生成有单元测试。
- [x] ClickHouse 使用明确的 CTE 形式并按查询列顺序生成目标字段。
- [x] 保存不依赖外部数据库，发布必须执行完整只读校验。
- [x] SQL 输出字段和类型与输出模型匹配。
- [x] 发布任务可以异步手动运行并持久化状态。
- [x] 至少完成 PostgreSQL 真实 Insert-Select 自动化或可重复集成验收。
- [x] 不支持的方言/写入方式返回明确错误，不静默降级。
- [x] 前端任务列表、SQL 编辑器、发布、运行和运行记录可用。
- [x] X6、Canvas、Quartz、Kafka、Spark 未进入第一阶段实现。
- [x] `./mvnw verify` 通过。
- [x] `pnpm check` 通过。

### PostgreSQL 真实验收的复现方式

`PostgreSqlLocalSqlTaskIntegrationTest` 默认跳过，避免普通构建依赖外部服务。准备一个可丢弃的 PostgreSQL 数据库和具备建表权限的用户后，设置以下变量运行：

```bash
export DATASCALPEL_PG_INTEGRATION=true
export DATASCALPEL_PG_HOST=127.0.0.1
export DATASCALPEL_PG_PORT=5432
export DATASCALPEL_PG_DATABASE=<disposable-database>
export DATASCALPEL_PG_SCHEMA=datascalpel_task_test
export DATASCALPEL_PG_USERNAME=<test-user>
export DATASCALPEL_PG_PASSWORD=<test-password>
./mvnw -pl data-scalpel-admin -am -Dtest=PostgreSqlLocalSqlTaskIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

测试在指定 schema 下创建随机命名的输入/输出表、触发器和约束，并在每个场景后清理。管理库仍使用测试 profile 的 H2，不会写入运行中的控制面数据库。

## Codex 停止与确认点

以下情况出现时，Codex 应停止对应实现并与用户确认，不自行扩大范围：

1. 某数据库需要新增第三方 SQL Parser 或额外驱动之外的生产依赖才能支持 CTE。
2. `OVERWRITE` 需要接受非原子 `TRUNCATE + INSERT` 才能实现。
3. 真实需求要求多条 SQL、跨数据源、存储过程或用户直接编写完整 `INSERT`。
4. 手动运行必须支持多应用实例、进程重启恢复或立即取消。
5. 需要引入 Quartz、消息队列或独立执行进程。
6. 现有在途修改与任务实现产生无法安全合并的同文件冲突。

这些需求都属于第一阶段边界变化，应先更新设计和计划，再继续编码。
