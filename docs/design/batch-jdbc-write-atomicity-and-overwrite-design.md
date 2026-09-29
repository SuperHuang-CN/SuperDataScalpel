# 批处理 JDBC 写入统一与覆盖策略改造方案

状态：**历史讨论稿**。整理日期：2026-09-29。后续预研、最终取舍和实施边界以[实施方案](batch-jdbc-write-implementation-20260929.md)为准，本稿中的候选能力不等于已经实现。

本文汇总画布、JAR SDK、在线 Java 开发的批处理写入讨论，并提出 Web 端覆盖范围交互。数据库原生能力、项目现有实现与拟实施能力分别说明；本文不表示所有数据库已经适配或经过性能验证。本轮只新增设计文档，不修改任务定义、数据库、运行服务和功能代码。

现行约束入口：[Task Engine 规范](../development/task-engine.md)、[SDK 设计](spark-jar-task-sdk-v1.md)、[Canvas 定义](canvas-task-definition.md)、[现有 UPSERT](canvas-jdbc-output-upsert-design.md)、[空间执行规则](spatial-field-structure-management-v1.md#canvassedona-空间执行扩展)。

## 1. 总体结论与范围

推荐：**统一业务语义与共用底层，按数据库能力选择提交实现，不统一强制一种物理写法。**

| 问题 | 推荐解决方式 |
| --- | --- |
| APPEND / UPSERT 分区提交后部分失败 | 支持“中间表装载 + 单次输出事务提交”；正式目标不参与 Spark 分区装载 |
| OVERWRITE 先清空再写可能破坏旧数据 | 中间表准备完成后事务替换；大规模全量覆盖另评估影子表原子切换 |
| 只重算某一天、某个区域等数据 | OVERWRITE 增加“全部数据 / 指定条件”，每条输出独立维护删除条件 |
| 双写与大事务成本 | 轻量中间表、受控装载并行度、数据库集合式 SQL；不为统计重复扫描；大表不强制全量 DELETE + INSERT |
| 画布与 SDK 写入实现不同 | 复用 Task Engine 执行侧的写入计划、Geometry 编码、装载、提交和结果收集 |
| “影响行数”含义混乱 | 分开显示处理行数、提交结果和可靠可得的数据库变更数；未知不填零、不伪造精确值 |

### 1.1 覆盖范围

- 批处理 Canvas 的 `JDBC_OUTPUT`、`MODEL_OUTPUT`，包括同一节点内多条独立写入。
- 批处理 JAR SDK 的模型/JDBC 读写；在线 Java 通过相同 SDK 运行，不另做一套实现。
- APPEND、UPSERT、全量覆盖、条件覆盖，以及已有空间执行支持范围内的 Geometry 读写复用。
- 用户直接调用原生 Spark Writer 或自建 JDBC 的代码不受本方案接管。

本次不扩展实时任务、LOCAL_SQL、文件/Kafka Sink、数据填报和快照同步的业务能力；现有行为保持。快照同步已有独立差异比较和统计，不把普通 UPSERT 改造成全量快照同步。

### 1.2 原子性边界

一笔输出操作对一张目标表整体提交或整体回滚。一个 Canvas 节点的两条写入、一个 JAR 的两次 SDK 调用，仍然是两笔独立操作；前一笔成功、后一笔失败，不回滚前一笔。

不承诺跨数据库事务、上游多连接读取的一致快照、任意并发业务写入合并或端到端 Exactly Once。不新增平台级目标占用检查、任务间扫描、分布式锁。

## 2. 当前实现与本次差异

以下按本轮核对的源码记录，不把历史文档版本号作为新版本要求。

| 路径 | 当前实现 | 本次关注 |
| --- | --- | --- |
| Canvas 普通 APPEND | Spark JDBC Writer 直接写目标表 | 改造后可装载中间表，再统一提交 |
| Canvas Geometry / UPSERT | 自写 `foreachPartition`，每分区独立 JDBC 事务，500 行批量执行 | 提取共用执行侧组件；批次执行不等于事务提交 |
| JAR SDK UPSERT | 逐行参数 UPSERT，1000 行批量执行，每分区提交；没有物理中间表 | 增加集合式合并路线，避免和 Canvas 重复实现 |
| 批处理 OVERWRITE | 先独立清表，再写入；清理与后续分区写入不是同一事务 | 全量/条件覆盖在最终提交阶段完成 |
| Canvas 行数 | 随写入 Action 的 `observe` 统计 | 复用实际装载指标，不统计成目标已提交数 |
| JAR SDK 行数 | `persist(MEMORY_AND_DISK)`、先 `count()`、再写入 | 避免仅为观测多一次 Action；缓存是否需要按实际执行目的决定 |
| 本地 SQL | 直接数据库 INSERT SELECT；支持的覆盖路径已有事务 | 本轮不合并改造，不混同 Canvas 的非原子路径 |

源码入口：

- [CanvasTaskExecutor](../../data-scalpel-task-engine/src/main/java/cn/superhuang/datascalpel/taskengine/runner/CanvasTaskExecutor.java)
- [SpatialJdbcRuntimeSupport](../../data-scalpel-task-engine/src/main/java/cn/superhuang/datascalpel/taskengine/runner/SpatialJdbcRuntimeSupport.java)
- [SparkJarJobContextImpl](../../data-scalpel-task-engine/src/main/java/cn/superhuang/datascalpel/taskengine/runner/SparkJarJobContextImpl.java)
- [SparkOutputMetricsCollector](../../data-scalpel-task-engine/src/main/java/cn/superhuang/datascalpel/taskengine/runner/SparkOutputMetricsCollector.java)
- [JdbcInsertSelectExecutor](../../data-scalpel-dialect/src/main/java/cn/superhuang/data/scalpel/dialect/runtime/JdbcInsertSelectExecutor.java)

## 3. Web 交互：在每条输出中维护覆盖范围

### 3.1 入口与布局

沿用截图所在的“设置 JDBC 写入”弹窗：左侧来源表、目标表与写入设置，右侧字段映射。模型输出使用相同交互，不另建“删除管理”页面，也不新增独立删除算子。

写入模式保持三项：

- `APPEND · 追加`
- `OVERWRITE · 覆盖`：不再固定写成“清空后写入”，避免条件覆盖仍被理解为全表清空。
- `UPSERT · 按唯一键插入或更新`；模型输出继续明确“按模型主键”。

仅选中 OVERWRITE 时，在左侧“写入模式”下方显示：

```text
写入模式       [ OVERWRITE · 覆盖       v ]
覆盖范围       ( ) 全部数据  (●) 指定条件

删除条件       business_date >= 2026-09-28
               且 business_date < 2026-09-29
               2 条条件                 [编辑条件]

输入为空时     [ 保留原数据并报错       v ]

仅替换满足条件的旧数据；范围外数据保留。
本次写入数据也必须属于此范围。
```

该示意展示字段关系，不要求增加截图之外的固定宽度。沿用当前 1080px 输出设置弹窗和紧凑两栏，左栏独立滚动、右侧字段映射可见，不为条件区堆叠大 Card 或常驻大面积 Alert。

### 3.2 条件编辑交互

- 左侧保留条件数量、可读摘要和“配置条件 / 编辑条件”按钮；完整条件较长时用受控 Modal 编辑，不把复杂条件树挤入左侧窄栏。
- 编辑器使用“目标字段 → 操作符 → 类型化值”的紧凑条件行，支持 AND/OR 分组、添加条件/分组及移除。
- 复用现有 `FilterConditionTreeEditor` 的结构化交互；若需提取，放在 task/canvas 的共用组件目录，只有输出和 Filter 的真实复用部分被提取，不建设万能表单。
- **字段候选来自目标表/模型，不是来源表**。摘要同样使用目标字段名；字段映射重命名后不改变删除条件引用。
- 没有选择目标时，条件入口禁用并说明“请先选择目标表”；元数据加载、失败与重试就近展示。
- 切换目标或刷新元数据后，失效字段和不兼容值保留原配置并标红，不静默删除条件或变成全表覆盖。
- 编辑器“应用条件”只修改当前输出设置草稿；原弹窗“保存此项”才回写该 `writeId`；取消不修改原配置。最终任务保存/发布沿用现有流程。
- 普通帮助使用邻近 Tooltip/ContextHelp；条件缺失、失效和全表清理风险直接可见。键盘和触屏均能操作，不依赖 Hover 才可发现。

### 3.3 模式切换与风险提示

- APPEND：隐藏覆盖范围，不执行任何删除条件。
- UPSERT：显示现有匹配键设置，隐藏覆盖范围；模型主键来源规则不变。
- OVERWRITE + 全部数据：明确提示“成功提交后，目标表全部旧数据将被本次结果替换”。
- OVERWRITE + 指定条件：至少一条有效条件；条件为空是配置错误，**绝不能退化成全表覆盖**。
- 切换写入模式可在当前编辑会话保留未激活草稿，提交的稳定定义只包含当前生效的覆盖配置，避免后台误执行隐藏条件。
- 从指定条件切换到全部数据并保存时，对扩大删除范围做一次明确确认，显示目标名称；不为每次定时运行增加人工确认。
- 空输入推荐默认“保留原数据并报错”；显式选“允许清空覆盖范围”时展示危险说明，并在保存时确认。新默认不直接套用到旧定义。
- 输出列表、画布节点摘要及发布摘要显示“覆盖全部 / 条件覆盖·N 条条件”；运行详情显示实际提交策略和范围类型。敏感条件值不进入日志、Kafka 事件或通用诊断摘要。
- 不加入“立即执行删除”按钮；保存、编译、发布检查和编辑条件均不得清理真实数据。

### 3.4 第一版不增加的交互

- 不开放原始 `DELETE` SQL 编辑器。
- 不复用 Filter 的 Spark SQL 表达式模式作为数据库删除条件：两者语法、函数和执行位置不同。
- 不默认发起“预计删除多少行”或目标前后全表计数查询；需要时作为后续独立只读核对能力，不能把估计数当运行保证。
- 目标选择与字段映射保持已有自增/生成列写入限制，不因中间表改造自动放开。

## 4. 条件覆盖语义与契约

删除谓词只描述目标旧数据范围；不代替上游过滤。第一版推荐：输入在字段映射、目标类型转换及中间表装载后，必须全部满足同一目标谓词；发现范围外数据则在删除前失败，不自动丢弃。

为保证范围核对，谓词引用的列第一版要求出现在本次写入映射中。不依赖最终目标表默认值或触发器来推测输入是否属于范围；此限制在字段旁明确提示。缺省值/生成列参与覆盖条件作为后续需求，不隐式推导。

谓词在数据库侧渲染并绑定参数，使目标删除和中间表范围核对遵循相同的排序规则、类型与 NULL 语义。只有谓词结果 TRUE 才属于范围；检查越界必须同时捕获 FALSE 和 UNKNOWN，不能简单用 `NOT(predicate)` 漏掉 NULL。

第一版采用现有结构化标量条件的适用子集：等于/不等于、大小比较、IN/NOT IN、IS NULL/IS NOT NULL；Geometry 不作为普通范围条件。复用现有深度、节点数、IN 值数量限制。日期/时间保持明确类型与时区。文本匹配、运行参数和任意函数不在首批范围。

建议在 `JdbcOutputWrite`、`ModelOutputWrite` 的每个 `writeId` 下增加覆盖配置。以下是**待定字段示意，不是已可用 API**：

```json
{
  "writeMode": "OVERWRITE",
  "commitMode": "ATOMIC",
  "overwrite": {
    "scope": "MATCHING",
    "condition": {
      "kind": "PREDICATE",
      "columnName": "business_date",
      "operator": "EQUALS",
      "values": [{ "dataType": "DATE", "value": "2026-09-28" }]
    },
    "emptyInputPolicy": "FAIL"
  }
}
```

- `scope`：`ALL / MATCHING`；`ALL` 不携带生效条件。
- `emptyInputPolicy`：`FAIL / CLEAR_SCOPE`，只适用于覆盖。
- 条件复用 Contracts 的结构化定义，不复制第二棵 Canvas 条件树；Dialect 接收无 Spark/业务实体的受控条件表示。
- SDK 不依赖 Contracts/Canvas。以 SDK 自身的轻量公开条件构造 API 表达同等能力，再由 Engine 映射到共享执行计划；具体方法名随 API 评审确定，不开放任意 SQL 字符串作为捷径。
- 外部模型仍禁止 OVERWRITE；“仅删除一部分”不是绕过 EXTERNAL 限制的理由。

## 5. 执行流程与必要校验

### 5.1 事务路线

1. 准备输出：解析绑定、模式、映射、条件；查询创建中间表所需的物理元数据和实际数据库能力。
2. 创建本次专属中间表，在目标数据库内完成 Spark 并行装载。
3. 确认装载完整结束，并执行与模式相关的必要检查。
4. 同一个目标 JDBC 连接关闭自动提交，执行最终 APPEND、UPSERT 或覆盖。
5. 提交确认后记录输出成功；清理中间表。

APPEND 使用 `INSERT ... SELECT`；条件覆盖使用 `DELETE WHERE ...` 加 `INSERT ... SELECT`；全量事务覆盖使用 DELETE 加 INSERT。中间表创建和清理不混入最终数据事务，避免某些数据库 DDL 隐式提交。

### 5.2 必要检查不是全量对账

| 检查 | 执行要求 |
| --- | --- |
| 分区装载结束 | 全部分区完成且无失败/取消；不可把“部分批次执行成功”当完成 |
| UPSERT 空键/重复键 | 保留现有业务规则；选 Spark 或数据库一处检查，不两边重复全量检查 |
| 覆盖范围 | 条件有效；在中间表中有界查找第一条越界记录，不向日志输出数据值 |
| 空数据保护 | 利用可靠装载指标或存在性查询判断，不仅为此全表 count |
| 真实数据库约束 | 最终执行时由数据库判断类型、非空、唯一性等；失败整笔回滚，不在 Compiler 试写 |

中间表中的唯一性判定还需考虑数据库排序规则与 Spark 等值语义差异；不得默认以来源端去重代替目标键语义。MySQL 多唯一键可能匹配其他约束的既有提示继续保留。

不新增平台级严格 Schema 相等门禁：物理元数据读取用于生成暂存列和合法 SQL，不与模型/Manifest 全字段强制一致，不因不相关字段新增或顺序变化拒绝执行。目标实际不兼容由明确的建表/写入错误反馈。Compiler 继续仅分析元数据快照与零行计划。

### 5.3 失败、重试和提交结果

- 装载或校验失败：正式目标不变；最终事务失败且确认回滚：正式目标恢复原状态。
- 最终提交成功而清理失败：输出仍成功，附清理待处理信息；不得误报失败诱导重跑。
- 提交网络异常：视为提交结果不确定，不能按零行或确认回滚处理，不自动重放 APPEND；保留必要执行证据与中间表。第一版不承诺跨系统自动 Exactly Once，也不默认新建目标库事务收据表。
- 对同一执行/输出的结果回传重试和对数据操作的重新执行必须区分。Dispatcher/Runner 自动重试策略需识别提交不确定，不能只凭通用异常 retryable 重做写入。
- 分区重试/推测执行可能使直接装载同一中间表产生重复行。正式开放前必须有可验证的装载隔离/去重提交方案；仅关闭推测执行不够，按业务键去重也会错误删除 APPEND 的合法重复行。
- 分区重试处理的实现候选是按分区尝试隔离暂存、只选成功尝试参与发布；需同时评估表数量、驱动支持与成本后确定。此项是实施前的硬性设计门槛，不以“最终检查总行数”替代。
- 外键级联、触发器、自增/序列及其他业务并发写入需评估。不能自动禁用约束，事务回滚也不等于序列和外部触发器副作用全部还原。没有依据时不承诺对任意表安全覆盖。

## 6. 中间表如何创建与回收

### 6.1 不是会话临时表，也不是完整克隆表

使用本次执行、Attempt、writeId 独占的普通物理中间表，使 Spark 多连接能够共同装载。默认位于目标数据库、同 Schema；专用 Schema 必须有明确部署配置和授权。名称使用受控短前缀、随机/执行标识，按数据库长度限制转义；不将用户名称直接拼成 DDL。

仅创建实际写入的列，依据目标物理类型保存值；不默认复制主键、外键、业务索引、触发器、默认值、自增或授权。必要的 UPSERT 索引在装载后按方言策略建立。未映射的默认值列在最终目标执行时处理，已有生成列/自增写入限制保持。

权限必须覆盖查询所需元数据、创建/装载/读取/删除中间表及最终目标 DML。原来只有目标表写权限的账号不一定满足，不能上线后才静默切换。中间表遵循部署访问控制，权限不足提前反馈。

### 6.2 元数据获取与缺口

复用 `JdbcMetadataReader.readTable/readColumns`：JDBC `DatabaseMetaData.getColumns` 读取字段名、原生类型、长度精度、可空性、默认值、自增/生成标志。再通过 `DatabaseDialect.enrichColumnMetadata` 补充数据库目录信息。

现有 `ColumnMetadata` 不是可无损重建任意字段的完整 DDL 描述；字符集、排序规则、域/枚举、时间精度、原生类型修饰符等按实际支持列补齐。不能直接把 `TYPE_NAME` 拼接成任意 DDL，也不能通过平台类型往返转换后忽略有损信息。无法可靠构建中间列时明确“不支持该暂存类型”，不偷偷转字符串。

不将 `CREATE TABLE AS SELECT ... WHERE 1=0` 当跨数据库通用无损复制方案；具体方言验证字段语义后可选用原生建表能力。

Geometry 的物理列结构与写入 CRS 分开：读取必要物理属性不等于重定义任务空间语义。原生写入的 SRID 继续来自任务目标字段快照中的 EPSG CRS，不通过回查空间目录替代、不自动转换坐标，也不添加目录漂移门禁。

### 6.3 生命周期

提交确认后尽快清理；失败、取消、进程退出时最佳努力清理。提交不确定时保留。异常残留必须记录所有权和执行身份，再按配置保留期限与运行状态处理；不能仅按 `ds_stage_*` 前缀批量删除，不删除其他任务或用户表。具体保留时长在部署配置评审时确定。

## 7. 数据库策略、兼容回退与性能

### 7.1 UPSERT 执行方式

优先由方言渲染集合式 SQL：PostgreSQL 的 `INSERT SELECT ... ON CONFLICT`、MySQL 的 `INSERT SELECT ... ON DUPLICATE KEY UPDATE`、Oracle/SQL Server/达梦等经验证的 `MERGE`。

原生集合式不适用时：

1. 可选择同一事务内 UPDATE 已存在记录 + INSERT 不存在记录；必须验证并发插入、唯一键、隔离和锁行为，不能把两条 SQL 直接串接就当正确实现。
2. 如已有可靠的参数化逐行 UPSERT，可提供“中间表流式读取 + 单连接批量执行 + 最后一次 commit”的兼容实现；有界读取，避免 Driver 全量收集，并验证读游标与写连接的驱动限制。
3. 不具备所需事务或合并能力则明确不支持。上述策略按能力预先选择，不在失败后逐级换方案重跑，不回退到多个 Spark 分区直接提交目标表。

集合式 SQL 仍需要索引匹配、约束检查和日志写入，不代表没有逐行处理成本。批量装载每 500/1000 行执行与最后事务提交是两件事。

### 7.2 能力矩阵

| 产品 | 原生事务路线判断 | 不可省略的条件 |
| --- | --- | --- |
| PostgreSQL、瀚高、openGauss、金仓 | 普通事务表可作为适配候选 | 具体产品版本、物理类型、合并语法和空间扩展需验证；不以继承同一个 Java 方言当证明 |
| MySQL | InnoDB 等事务引擎可适配 | 排除非事务表；关注多唯一键冲突；DDL 隐式提交 |
| Oracle | 可适配 DML 事务 | DDL 放事务外；关注触发器、序列、UNDO 和约束 |
| SQL Server | 可适配 DML 事务 | 普通读是否阻塞取决于 RCSI/快照隔离等配置；不擅自修改用户数据库设置 |
| 达梦 | 可适配 DML 事务 | DDL/TRUNCATE 隐式提交行为与具体版本需核对 |
| ClickHouse | 不作为普通多语句原子事务路线开放 | 实验事务有引擎/部署限制；独立评估 Atomic/Shared 引擎原子交换，不承诺通用 UPSERT |
| TDengine | 本轮不开放写入 | 当前项目仅作为 SOURCE；当前官方 JDBC 文档不提供该事务能力 |

本表只说明适配方向，不是本系统已验收清单。Kafka/S3/文件不套用数据库表事务方案。

### 7.3 大表与影子表路线

- “双写”不等于耗时翻倍：第二次数据库内部读写没有再次经过 Spark 网络与对象绑定，但磁盘、索引、日志成本仍然存在。
- 看本次改动规模，不仅看目标总行数：亿级表追加少量数据和全量覆盖是两种问题；条件覆盖要有适合谓词的访问路径。
- 读取分片与写入分区独立；`foreachPartition` 不会自动增大并行度。显式分区重分布有 Shuffle 成本，不能把增加 Executor 当成自动并行读写。
- 装载并发、批次大小和超时使用清晰配置，能力已存在则复用；不给出未经压测的“超过 N 行必须切换”阈值。
- 对独立、可安全切换的大表全量覆盖，后续增加“直接装载影子表 + 准备索引/权限 + 原子切换”。需要完整处理原表依赖，不与轻量中间表建表混为一谈。
- 影子表工厂沿用现有方言注册体系按数据库类型分派，不新增第二套产品枚举或插件注册机制。原子改名不等于外键、视图、权限和服务都自动转移；不满足条件就不开放切换。
- PostgreSQL、SQL Server 的某些 TRUNCATE 可以事务回滚，但锁行为可能阻塞普通查询；MySQL、Oracle、达梦不能当作可回滚的普通 DELETE 替代。第一阶段保障连续读取的默认方案不自动选 TRUNCATE；允许查询等待的优化场景另行明确。

## 8. Geometry 与工程内复用

批处理 Canvas 已有 WKB 与原生空间构造函数桥接；批处理 SDK 普通 JDBC 读写不能仅因类型检查允许 Geometry 就视为完整空间 IO 已实现。本次补齐 SDK/在线模式真实读写，并与 Canvas 复用。

- 保持当前 EPSG + XY、已有 PostGIS 兼容数据库/MySQL 空间执行边界；结构管理支持 ClickHouse WKB 不代表任务空间执行已经支持。
- 复用 WKB 编解码、NULL、目标 CRS 使用、数据库参数绑定以及中间表到目标表的原生 Geometry 转交。
- 不将 Geometry 当普通 `setObject` 全库通用，不新增自动 CRS 转换，不开放 Geometry 作为 UPSERT 键或普通条件字段。
- Geometry 校验和统计不得为了“证明一致”重复扫描全部数据。

| 工程 | 本次职责 |
| --- | --- |
| Contracts | Canvas 每条输出的覆盖配置、稳定条件与执行结果协议；无 Spark 类型 |
| Dialect | 元数据补充、中间表 DDL、绑定参数的条件 SQL、集合式写入、事务限制及后续克隆/切换能力；不放任务编排 |
| Task Engine | Canvas/JAR 共用的运行写入计划、Geometry、装载与提交编排、失败处理、统计；抽离对 `CanvasTaskExecutor` 静态工具的反向依赖 |
| SDK / TestKit | 公开覆盖条件与可选提交保障 API；模拟条件/空数据行为，不伪装成真实数据库事务验证 |
| Business / Admin | 定义保存、发布快照、资源授权、错误与运行结果；外部数据库操作不进入管理库长事务 |
| Dispatcher | 传递协议、取消与结果对账，不执行数据库写入、不改变 Kafka 触发链路 |
| UI | 每条输出的范围配置、提交能力提示、摘要与运行统计 |

不新增 Maven 模块、生产框架依赖、Spring 扫描插件或通用工作流框架；以当前 `DatabaseDialect` 及 Registry 为工厂入口，只增加实际需要的方言能力。

## 9. 统计：真实、可解释、不拖慢写入

| 指标 | 拟定含义 |
| --- | --- |
| 处理行数 | 本次输出数据流的条数；来自实际装载 Action，不能标成正式目标已写入 |
| 提交结果 | 未提交、已提交、已回滚、结果待确认；属于输出级语义，具体协议字段/枚举待版本设计，不擅自扩展任务终态 |
| 新增/更新/删除数 | 只在方言和驱动能可靠返回时记录；确认 commit 后才作为已提交统计发布 |
| 影响行数 | 现有字段需明确兼容口径，不静默从“输入条数”改成“实际变更数” |

不把 PostgreSQL 匹配但内容未变的更新数、MySQL UPSERT 的 1/2/0 返回值和 JDBC `SUCCESS_NO_INFO` 混为统一精确变化数；不简单相加或除以二。不为了拆分更新/未变化执行全表差异比较。触发器/级联的间接修改不承诺计入普通 DML 计数。

覆盖推荐分别展示删除数、插入数；删除 100 再插入 100 不是“200 个不同业务对象发生变化”。TRUNCATE 无可靠删除数时标未知。

源输出 S、目标前 B/后 A 的总数可作可选核对：A-B 是净增量；在无并发/触发器副作用、无忽略写入且键唯一等条件下，UPSERT 可估新增 A-B、匹配已有 S-(A-B)，但不能估内容真正改变了多少。不默认增加两次全表 COUNT，也不据此认定提交成功。

当前失败输出可能有已提交分区但统计未知。保留 DIRECT 时继续明确此风险，不把顶层已有成功输出累计值解释为全部实际副作用。ATOMIC 路线也不能把提交异常直接填零。

## 10. 兼容、试运行与上线边界

### 10.1 提交保障建议（待确认）

建议显式区分 `DIRECT / ATOMIC`，避免旧任务升级后突然需要 CREATE/DROP 权限并改变执行成本。此为本方案的新增建议，不是此前已经确认的字段。

- 旧定义缺失提交模式时保持现有 DIRECT 语义；原覆盖范围继续是全表，原空数据行为保持，不静默套新保护规则。
- 新配置在目标支持且部署就绪时推荐 ATOMIC；没有原子能力时明确禁用并说明原因，不自动降级。DIRECT 作为兼容项放低频“提交保障”配置，明确“可能部分成功”，不在主表单堆叠所有内部执行策略。
- 第一版新增条件覆盖仅在 ATOMIC 下开放；不额外建设非原子条件删除路径。
- 新增原子能力不等于所有存量 SDK 调用自动改变。SDK 新能力以显式 API 启用并保持旧签名；是否后续整体迁移需另行确认。
- 若评审选择直接替换旧语义，必须按工程协议规则评估破坏性大版本升级，不通过按小版本猜测旧语义实现暗迁移。

### 10.2 协议与对外说明

Canvas Definition → 保存/发布 → Manifest → Runner → result.json → Dispatcher/Kafka → Admin API → UI 全链路同步；版本号取实施时源码常量。新增字段、默认值和未知/失败口径同步 OpenAPI 中文契约。SDK API 说明、示例和 TestKit 同步，不仅修改网页表单。

新能力确认实施后再更新现行规范及旧专题的当前行为描述；本评审稿不覆盖现行生产规则。

### 10.3 试运行

沿用已有试运行输出拦截，不因为原子方案而对真实目标执行删除、创建暂存数据表或合并写入。条件与映射可做纯配置校验，真实数据库权限、事务与性能由明确的集成验证覆盖。用户自行使用原生 Writer 的副作用边界保持现有说明。

## 11. 分阶段实施与验证计划

### 第一阶段：共用基础与事务提交

1. 确认第 12 节的产品取舍，细化分区重试装载方案和提交不确定处理，再进入编码。
2. 提取 Canvas/JAR 共用写入与 Geometry 读写，整理各方言元数据缺口。
3. 实现已验证数据库的中间表生命周期、APPEND/UPSERT/全量与条件覆盖的原子提交。
4. 接入 Web 条件编辑、SDK 能力、结果统计与协议，保留明确的旧任务兼容路径。
5. 文档记录逐数据库“已实现/已验证/限制”，未验证的原子策略不对用户宣称可用。

### 第二阶段：有依据地优化大规模全量覆盖

逐数据库实现影子表克隆/切换，并验证对象依赖、索引、权限、序列、外键、触发器、并发读取与失败恢复；不能因为已有原子 RENAME 就直接开放所有表。仅在真实场景收益明确时接入。

### 验证项

以下是后续实施的建议验收清单；执行遵循根测试政策及用户具体要求，本次文档整理不启动服务、不执行数据库写入测试。

- 交互：四种写入语义、条件空值、AND/OR、失效字段保留、模式切换、取消、每 writeId 隔离、EXTERNAL/Streaming 限制、键盘操作及截图相同布局下可用性。
- 正确性：新增、匹配更新、合法重复 APPEND、UPSERT 空键/重复键、多唯一约束、空结果保护、条件边界/NULL/日期时区、Geometry 读写与真实类型适配。
- 故障：装载分区失败、重试、推测执行、取消、最终 SQL 失败、死锁、commit 响应丢失、进程终止、提交后清理失败；断言正式目标及运行结果一致。
- 并发：普通查询能否继续读旧数据，其他写入的约束冲突，DDL 变化，不新增平台占用仲裁。
- 性能：记录数据库/驱动版本、硬件、源/目标规模与行宽、Geometry 比例、索引、分区数和批次大小；分阶段记录装载、校验、合并、commit、清理耗时、日志/空间峰值和服务查询延迟。
- 对照：现有 DIRECT、中间表事务、大表影子切换（实现后）；统计开启/关闭的额外成本。不给出未经测量的吞吐承诺。

## 12. 全局评审需要确认的取舍

1. **提交保障**：是否接受新增 ATOMIC 能力、旧任务继续 DIRECT、新配置推荐 ATOMIC 的兼容方案？还是要求一次性迁移旧任务（需要重新评估版本与权限）？
2. **条件覆盖**：是否接受本稿“目标字段条件 + 输入必须落在相同范围 + 首批不支持原始 SQL”，以及条件字段必须已映射的简化边界？
3. **空输入**：新原子覆盖默认保留旧数据并报错，显式允许才清空范围；存量行为不暗改。
4. **实施顺序**：先落地共用事务路线，影子表切换作为大规模全量覆盖的第二阶段；首批数据库按实际可提供验证环境确定。
5. **统计**：默认处理行数 + 提交结果，可可靠获得时显示变更明细；不默认统计目标前后总数。

分区重试和提交不确定的工程方案不是交给用户排查的问题，由实施方补齐设计与验证后才能宣称原子写入已完成。

## 13. 数据库文档依据

以下支持原生能力判断，不代替本系统实际部署版本验证：

- [PostgreSQL 事务](https://www.postgresql.org/docs/current/tutorial-transactions.html)、[TRUNCATE 的回滚与锁/MVCC 限制](https://www.postgresql.org/docs/current/sql-truncate.html)、[批量装载](https://www.postgresql.org/docs/17/populate.html)。
- [MySQL 非事务表无法回滚](https://dev.mysql.com/doc/refman/8.4/en/nontransactional-tables.html)、[UPSERT 影响行数规则](https://dev.mysql.com/doc/refman/8.0/en/insert-on-duplicate.html)。
- [Oracle DML 与事务、DDL 隐式提交](https://docs.oracle.com/zh-cn/database/oracle/oracle-database/26/tdddg/dml-and-transactions.html)。
- [SQL Server 隔离级别与快照读](https://learn.microsoft.com/en-us/sql/t-sql/statements/set-transaction-isolation-level-transact-sql?view=sql-server-ver17)。
- [达梦一致性与事务](https://eco.dameng.com/document/dm/zh-cn/pm/consistency-concurrency.html)、[金仓事务管理](https://bbs.kingbase.com.cn/kingbase-doc/v8.6.7.12/admin/general/specification/transaction.html)、[openGauss 事务控制](https://docs.opengauss.org/zh/docs/latest/sql_reference/controlling_transactions.html)、[瀚高并发控制](https://www.highgo.com/document/v9/zh-cn/reference/referenceFour13.html)。
- [ClickHouse 事务限制](https://clickhouse.com/docs/concepts/features/operations/insert/transactions)、[EXCHANGE](https://clickhouse.com/docs/sql-reference/statements/exchange)、[TDengine JDBC 事务限制](https://docs.tdengine.com/developer-guide/connectors-reference/java/)。
