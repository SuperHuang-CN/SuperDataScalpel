# Canvas `JDBC_SNAPSHOT_SYNC_OUTPUT` 设计文档

## 1. 状态、目标与范围

- 实现状态：已开发。
- 目标协议版本：Canvas `2.0`。
- 目标 Manifest 版本：`v11`。
- 当前正式版本为 Canvas `2.0`、Manifest `v11`、Result `v3`。
- 节点类型：`JDBC_SNAPSHOT_SYNC_OUTPUT`。
- 节点类别：`OUTPUT`。
- 执行模式：仅 `BATCH`。
- 图规则：恰好一条入边，不允许出边。
- 输入要求：来源表必须为 `BOUNDED`。

`JDBC_SNAPSHOT_SYNC_OUTPUT` 面向水库、河流、行政区划等数据量较小、以完整实体快照为输入的
同步场景。节点将来源快照与 JDBC 目标表进行对比，只写入真正发生的变化：

- 来源独有：`INSERT`。
- 两侧 Key 相同但比较字段变化：`UPDATE`。
- 两侧 Key 和比较字段均相同：不执行数据库写入。
- 目标独有：根据配置保留或 `DELETE`。

节点不是普通 `JDBC_OUTPUT` 的新写入模式。它需要读取并严格锁定目标表，在一条 JDBC 连接和
一个事务中完成比较、删除保护和全部 DML，因此使用独立节点类型。首版不支持流式任务、分区
并行写入或大数据临时表模式。

本设计是 `MODEL_SNAPSHOT_SYNC_OUTPUT` 的共享运行语义依据。模型节点只负责把模型解析为
受控 JDBC 目标，比较、事务、限制、指标和错误语义均复用本文档。

## 2. 稳定配置协议

公共协议：

```ts
type SnapshotTargetOnlyAction = 'KEEP' | 'DELETE';

interface SnapshotDeletePolicy {
  action: SnapshotTargetOnlyAction;
  maxDeleteRows: number | null;
  maxDeleteRatio: number | null;
}

interface SnapshotSyncConfiguration {
  sourceTableName: string;
  keyColumns: string[];
  columnMappings: JdbcColumnMapping[];
  deletePolicy: SnapshotDeletePolicy;
}

interface JdbcSnapshotSyncOutputConfiguration
  extends SnapshotSyncConfiguration {
  dataSourceId: string;
  targetTableName: string;
}
```

完整示例：

```json
{
  "sourceTableName": "reservoir_snapshot",
  "dataSourceId": "a406e119-fbd2-4173-84ee-c92c69231168",
  "targetTableName": "reservoir",
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

协议约束：

- `keyColumns` 保存目标字段名，数量必须为 `1..32`，顺序稳定且不得重复。
- 用户可以选择任意满足字段规则的组合，不要求 Key 与数据库主键或唯一索引一致。
- `columnMappings` 沿用现有 JDBC Output 显式字段映射协议。
- `deletePolicy` 必须存在；节点默认值为 `KEEP + null + null`。
- `KEEP` 时两个限制必须为 `null`。
- `DELETE` 时两个限制都必须配置；Inspector 初始值固定为 `1000` 和 `0.2`。
- `maxDeleteRows` 必须是正整数；`maxDeleteRatio` 必须位于 `(0, 1]`。
- 定义不保存目标 Catalog、Schema、字段快照、数据库方言、运行限制或锁参数。
- 定义不保存 ChangeSet、before/after、运行指标、数据行、凭据或 X6 内部对象。

Java Contracts 中两个配置 Record 实现同一个只读 `SnapshotSyncConfiguration` 接口，共享
`SnapshotDeletePolicy` 和 `SnapshotTargetOnlyAction`。接口只抽取公共访问器，不引入运行时
基础设施，也不改变 Jackson 输出结构。

## 3. 节点、目标和字段校验

### 3.1 图与目标能力

- 节点必须有一条入边且无出边，不产生下游表 Map。
- `sourceTableName` 必须存在于直接上游合并后的 Map 中，且 DatasetKind 为 `BOUNDED`。
- `dataSourceId` 必须解析为启用的 JDBC 数据源，并具有 `DISTRIBUTION` 用途。
- 首版只支持 PostgreSQL 和 MySQL；其他 JDBC 方言返回确定的 Compiler Error。
- `targetTableName` 必须是默认命名空间中的普通物理表，View 等只读对象不得使用。
- Compiler 只使用 Metadata Snapshot 和零行 Spark Dataset，不连接目标数据库、不锁表、不试读
  真实数据，也不生成真实 ChangeSet。

### 3.2 映射与目标 Cast

节点先复用现有 `OutputColumnMappingOperator`：

- 始终按 `columnMappings` 选择来源字段并别名为目标字段名。
- 目标必填可写字段、默认值、自增字段和生成字段沿用 `JDBC_OUTPUT` 规则；未映射来源字段直接忽略。
- 所有已映射字段必须显式 Cast 为目标平台类型；风险 Cast 在 Compiler 产生 Warning，Spark
  Analyzer 不支持的 Cast 直接失败。
- Runner 必须使用 Cast 后的 Dataset 做 Key 校验和比较，禁止比较原始来源类型。

例如来源字符串 `"1"` 成功 Cast 为目标 `INTEGER 1` 后，与目标整数 `1` 视为相同，不产生
UPDATE。

### 3.3 Key 规则

Key 字段必须：

- 存在于目标 Schema，并被当前字段映射覆盖。
- 是可写普通标量字段，不能是 Geometry、自增字段或生成字段。
- 在配置中不重复。

目标字段 nullable 时返回 `SNAPSHOT_SYNC_NULLABLE_KEY` Warning，但允许生成计划；真实来源或
目标任一 Key 分量为 NULL 时 Runner 失败。若 Key 字段集合不等于目标元数据中任一主键或安全
唯一索引的完整字段集合，返回 `SNAPSHOT_SYNC_KEY_NOT_DATABASE_UNIQUE` Warning。唯一约束只
用于提示，不限制用户选择。

Runner 必须在执行 DML 前验证：

- 来源 Key 非 NULL 且来源内唯一。
- 目标 Key 非 NULL 且目标内唯一。

任一校验失败都不得输出实际 Key、字段值或数据行。Key 字段变化无法被识别为原行更新，固定
表现为旧 Key 行 DELETE、新 Key 行 INSERT。

## 4. 比较语义

### 4.1 比较范围

- 比较全部已映射、可写的非 Key 字段。
- 未映射目标字段不比较、不更新；INSERT 时依赖目标默认值或 nullable。
- 只有至少一个比较字段变化时才产生 UPDATE。
- 对已判定变化的行，UPDATE 统一设置全部已映射、可写的非 Key 字段，以保持单一稳定 SQL
  形状；内部仍精确保留 `changedColumns`。
- 如果只映射 Key，匹配行全部为 UNCHANGED，来源独有行仍可 INSERT，目标独有行仍受删除策略
  控制。

### 4.2 标量相等规则

比较双方均先规范化为目标平台类型：

- NULL 与 NULL 相同；仅一侧为 NULL 时不同。
- BOOLEAN 和整数按目标值精确比较。
- FLOAT/DOUBLE 使用与 Spark 空值安全相等一致的精确语义，不引入容差。
- DECIMAL 按目标 precision/scale Cast 后的数值比较，不以 Java 对象 scale 差异制造更新。
- STRING 精确比较，不 trim、不忽略大小写、不做 Unicode 归一化。
- BINARY 按字节内容比较。
- DATE、TIMESTAMP、TIMESTAMP_NTZ 按目标类型、时区语义和目标精度比较。

首版不提供逐字段忽略规则、字符串标准化、浮点容差或自定义比较表达式。此类需求应在上游
Processor 中显式处理。

### 4.3 Geometry 相等规则

- 来源 Geometry 必须与目标字段的 GeometryKind、CRS 和 dimension 兼容。
- 来源 Dataset 完成目标 Schema 规范化，目标 JDBC 值由方言受控读取并转换为 JTS Geometry。
- 非 NULL Geometry 固定使用拓扑相等判断；坐标顺序、环方向或表达形式不同但拓扑等价时不
  产生 UPDATE。
- NULL 与 NULL 相同，NULL 与非 NULL 不同。
- 无效 Geometry、解析失败或拓扑比较异常时任务失败，提示先使用 `GEOMETRY_VALIDATE` 或
  `GEOMETRY_REPAIR`，不得自动修复、置 NULL 或回退为 WKB 比较。

## 5. ChangeSet 与删除保护

Runner 内部构造不可序列化到 Canvas 的 `SnapshotChangeSet`：

- `INSERT`：Key、after 和写入字段。
- `UPDATE`：Key、before、after、changedColumns 和统一更新字段。
- `DELETE`：Key 和 before。
- `UNCHANGED`：只计数。
- `RETAINED`：目标独有但配置 KEEP，只计数。

before/after 只存在于当前 Runner 内存，不写入日志、错误、result.json、管理数据库、Kafka 或
Outbox。

目标独有行处理：

- `KEEP`：不执行 DELETE，计入 `retainedTargetOnlyRows`。
- `DELETE`：候选目标独有行全部进入删除保护检查。
- 来源行数为 0 且目标行数大于 0 时，固定返回 `SNAPSHOT_SYNC_EMPTY_SOURCE_DELETE_BLOCKED`。
- 候选删除数大于 `maxDeleteRows` 时失败。
- `targetRows > 0` 时按 `deletedRows / targetRows` 计算比例；大于 `maxDeleteRatio` 时失败。
- 等于阈值允许执行；任一阈值超限即整体失败。
- 来源和目标都为空时 DELETE 模式允许成功，并返回全零指标。

所有删除保护必须在第一条 DML 前完成。保护失败不得执行部分删除、更新或插入。

## 6. 规模限制与运行配置

本节点是明确的小数据 Driver 比较模式。Manifest v11 增加受保护的
`snapshotSyncLimits`，由 Admin 部署配置生成，不进入 Canvas JSON：

| 配置 | Admin 配置前缀 | 默认值 |
| --- | --- | --- |
| 单侧最大行数 | `data-scalpel.task.snapshot-sync.max-rows-per-side` | `100000` |
| 来源与目标合计估算字节 | `data-scalpel.task.snapshot-sync.max-estimated-bytes` | `268435456` |
| 目标锁等待秒数 | `data-scalpel.task.snapshot-sync.lock-timeout-seconds` | `30` |

- 来源 Dataset 使用 `MEMORY_AND_DISK` 缓存，完成行数、Key 和估算字节检查后在 Driver 建 Map。
- 目标读取过程中同步检查目标行数和合计估算字节；超限立即回滚并释放锁。
- 字节数是按目标类型和值估算的保护值，不宣称等于 JVM 精确占用。
- 限制超出时不自动切换分布式 Join、分区事务或临时表模式。
- 后续大数据同步应设计独立的数据库临时表/集合运算执行模式，不能改变本节点的小数据语义。

Manifest v10 不包含运行限制，也不得携带本节点。新 Runner 兼容不含新节点的 v10；v10
携带新节点时拒绝。

## 7. JDBC 事务和方言能力

### 7.1 统一执行顺序

1. 在数据库事务外完成来源 Dataset Cast、缓存、规模和来源 Key 校验。
2. 打开一条目标 JDBC 连接。
3. 通过方言设置 30 秒锁等待并取得目标表严格写锁。
4. 在同一锁和事务范围内读取目标比较字段，校验规模及目标 Key。
5. 计算完整 ChangeSet 和删除保护，不执行 DML。
6. 按 `DELETE → UPDATE → INSERT` 执行 PreparedStatement。
7. 每种语句固定使用 500 行 batch；不依赖驱动 affected-row 特殊语义统计结果。
8. 全部成功后提交；异常时回滚；finally 中恢复连接状态并释放锁。

来源准备失败时不会取得目标锁。取得锁后发生的目标读取、比较、保护或写入失败必须整体回滚。

### 7.2 方言职责

`data-scalpel-dialect` 增加不依赖 Spark、Spring 和业务实体的受控能力：

- 目标严格写锁和锁等待语句。
- 按受信任元数据字段生成目标快照 SELECT。
- Geometry 使用方言空间函数输出受控 WKB，并在 Task Engine 中恢复平台 Geometry。
- INSERT、统一字段 UPDATE 和按复合 Key DELETE 的 PreparedStatement SQL。
- 标识符引用、Catalog/Schema 规则和数据库类型绑定。

PostgreSQL 在事务内取得阻止并发 DML 的表锁，提交或回滚时释放。MySQL 使用受控 WRITE
表锁，必须在 finally 显式释放并恢复原连接状态。锁超时返回稳定错误，不降级为无锁执行。

首版每个同步节点只使用一条 JDBC 连接和一个事务，不复用现有 UPSERT 的 Spark 分区独立事务，
也不宣称多个 Output 之间具有全局事务。

## 8. Task Engine、结果协议和安全边界

### 8.1 共享实现结构

- `JdbcSnapshotSyncOutputNodeOperator` 只解析 JDBC 目标并调用共享支持类。
- `SnapshotSyncOperatorSupport` 统一执行公共配置、映射、Cast、Key、删除策略和 Schema 校验。
- `CanvasPreparedSnapshotSyncOutput` 保存受保护运行目标、Cast 后 Dataset、目标 Schema、Key、
  映射字段和删除策略。
- `JdbcSnapshotSyncExecutor` 是 JDBC 与 Model 两个节点唯一的真实比较和事务写入实现。
- `SnapshotValueNormalizer` 和 `SnapshotValueComparator` 统一处理平台类型与 Geometry。
- 不为 JDBC 和 Model 复制两套 ChangeSet、SQL、限制或事务实现。

两个节点仍各自对应唯一 `CanvasNodeOperator` 并显式注册，符合内置 Registry 规则。
两个节点随 Canvas `1.27` 一起注册，稳定 NodeType 数量从 38 增加到 40。

### 8.2 结构化结果指标

`result.json` 升级为 `schemaVersion: 3`，`NodeExecutionResult` 增加可空判别联合 `metrics`：

```ts
interface SnapshotSyncMetrics {
  kind: 'SNAPSHOT_SYNC';
  sourceRows: number;
  targetRows: number;
  insertedRows: number;
  updatedRows: number;
  deletedRows: number;
  unchangedRows: number;
  retainedTargetOnlyRows: number;
}
```

- 成功的快照同步节点必须返回完整非负指标，包括全零结果。
- `sourceRows = insertedRows + updatedRows + unchangedRows`。
- `targetRows = deletedRows + retainedTargetOnlyRows + updatedRows + unchangedRows`。
- `rowsWritten = insertedRows + updatedRows + deletedRows`。
- 失败或回滚的节点 `metrics=null`，不得把候选变更伪装为已提交指标。
- Runner 只写 v3；Dispatcher 严格读取 v3，同时在滚动升级期兼容既有 v2，v2 节点指标为空。
- 前端通过现有 result 制品接口按需读取 v2/v3，在运行详情显示同步指标，不新增 HTTP API。

### 8.3 生命周期和日志

- 节点阶段固定为 `WRITE`。
- 成功消息为“JDBC 快照同步完成”。
- `affectedRows` 使用 ChangeSet 已提交数量，不使用 MySQL/驱动返回的 0/1/2 语义。
- 安全摘要只记录数据源 UUID、目标表、Key 字段名、映射数量、删除策略和阈值。
- 日志和结果不得包含 Key 值、数据行、before/after、Geometry、WKT/WKB、凭据或完整 JDBC
  Properties。

## 9. 前端设计器

- Palette 名称为“JDBC 快照同步”，分组为数据库输出，说明为“对比完整快照并增量增删改目标表”。
- 只在 BATCH 模式显示。
- Inspector 顺序固定为：来源表 → 目标数据源 → 目标表 → 字段映射 → 对比 Key → 目标独有行
  策略 → 删除保护。
- Key 候选只显示已映射、可写、非 Geometry、非自增、非生成的目标字段。
- 主键和安全唯一索引字段显示推荐标记；用户仍可组合其他字段。
- nullable 或非数据库唯一 Key 显示 Compiler Warning，不在前端重复实现有效性判断。
- 切换为 DELETE 时先确认“来源代表目标完整快照”，随后显示红色风险区和两个必填阈值，默认
  为 1000 行、20%。确认只控制当前交互，不增加 Canvas JSON 字段。
- 上游、目标或映射变化导致 Key 失效时保留原值并标红，不自动清空或替换。
- 节点摘要显示目标数据源、目标表、Key 数量和 KEEP/DELETE，不显示阈值之外的数据值。
- 运行详情对 v3 Snapshot Sync 指标使用紧凑统计卡展示；v2 或无指标节点保持现有展示。

前端抽取共享 `SnapshotSyncConfigurationFields`，负责来源、映射、Key 和删除策略；JDBC
Inspector 只提供数据源/目标表选择器，Model Inspector 只提供模型选择器。

## 10. 稳定错误码

复用现有必填项、UUID、表不存在、数据源不可用、字段映射、Spark Analyzer、JDBC 连接和
数据库约束错误；不产生运行时整表 Schema 漂移错误。新增至少包括：

| 错误码 | 级别/阶段 | 条件 |
| --- | --- | --- |
| `SNAPSHOT_SYNC_REQUIRES_BOUNDED_INPUT` | ERROR / Compiler | 来源不是 BOUNDED |
| `SNAPSHOT_SYNC_DATABASE_NOT_SUPPORTED` | ERROR / Compiler | 目标不是 PostgreSQL/HighGo/MySQL/openGauss/人大金仓 |
| `SNAPSHOT_SYNC_KEY_REQUIRED` | ERROR / Compiler | Key 为空或超过 32 项 |
| `SNAPSHOT_SYNC_KEY_DUPLICATE` | ERROR / Compiler | Key 字段重复 |
| `SNAPSHOT_SYNC_KEY_COLUMN_NOT_FOUND` | ERROR / Compiler | Key 不存在于目标 |
| `SNAPSHOT_SYNC_KEY_NOT_MAPPED` | ERROR / Compiler | Key 未被字段映射覆盖 |
| `SNAPSHOT_SYNC_KEY_COLUMN_NOT_ALLOWED` | ERROR / Compiler | Key 是 Geometry、自增或生成字段 |
| `SNAPSHOT_SYNC_NULLABLE_KEY` | WARNING / Compiler | Key 元数据允许 NULL |
| `SNAPSHOT_SYNC_KEY_NOT_DATABASE_UNIQUE` | WARNING / Compiler | Key 不匹配已知唯一字段集合 |
| `SNAPSHOT_SYNC_DELETE_POLICY_INVALID` | ERROR / Compiler | 删除策略与阈值不一致 |
| `SNAPSHOT_SYNC_SOURCE_KEY_NULL` | ERROR / Runner | 来源真实 Key 含 NULL |
| `SNAPSHOT_SYNC_SOURCE_KEY_DUPLICATE` | ERROR / Runner | 来源真实 Key 重复 |
| `SNAPSHOT_SYNC_TARGET_KEY_NULL` | ERROR / Runner | 目标真实 Key 含 NULL |
| `SNAPSHOT_SYNC_TARGET_KEY_DUPLICATE` | ERROR / Runner | 目标真实 Key 重复 |
| `SNAPSHOT_SYNC_SOURCE_ROW_LIMIT_EXCEEDED` | ERROR / Runner | 来源超过单侧行数限制 |
| `SNAPSHOT_SYNC_TARGET_ROW_LIMIT_EXCEEDED` | ERROR / Runner | 目标超过单侧行数限制 |
| `SNAPSHOT_SYNC_MEMORY_LIMIT_EXCEEDED` | ERROR / Runner | 合计估算字节超限 |
| `SNAPSHOT_SYNC_EMPTY_SOURCE_DELETE_BLOCKED` | ERROR / Runner | 空来源将删除非空目标 |
| `SNAPSHOT_SYNC_DELETE_ROWS_EXCEEDED` | ERROR / Runner | 候选删除数量超限 |
| `SNAPSHOT_SYNC_DELETE_RATIO_EXCEEDED` | ERROR / Runner | 候选删除比例超限 |
| `SNAPSHOT_SYNC_LOCK_TIMEOUT` | ERROR / Runner | 严格表锁等待超时 |
| `SNAPSHOT_SYNC_GEOMETRY_COMPARISON_FAILED` | ERROR / Runner | Geometry 无法拓扑比较 |
| `SNAPSHOT_SYNC_MAPPING_INVALID` | ERROR / Runner | 内部目标字段映射无法建立 |
| `SNAPSHOT_SYNC_VALUE_CONVERSION_FAILED` | ERROR / Runner | 真实目标值无法按逻辑 Schema 转换 |
| `SNAPSHOT_SYNC_OUTPUT_FAILED` | ERROR / Runner | 未分类的快照同步写入失败 |

阈值错误可以记录安全的行数、比例和配置阈值，但不得记录具体 Key 或数据内容。

## 11. 测试与验收设计

- Contracts：节点配置、公共删除策略、结果指标的 JSON/Jackson 往返和空数组规范化。
- 版本：Canvas 2.0 统一显式字段映射；Manifest v11、v10 拒绝新节点、Result v3 与 Dispatcher v2/v3。
- Compiler：目标能力、BOUNDED、显式映射、Cast、Key 上限、字段规则、nullable/唯一
  Warning 和删除策略。
- Cast 后比较：STRING→INTEGER、不同 Decimal scale、时间精度、NULL、Binary、浮点和普通字符串。
- Geometry：拓扑相等、真实变化、NULL、无效 Geometry、kind/CRS/dimension 漂移。
- ChangeSet：INSERT、UPDATE、DELETE、UNCHANGED、RETAINED、Key 变化和只映射 Key。
- 安全限制：两侧 NULL/重复 Key、行数限制、估算字节限制、空来源和两类删除阈值。
- PostgreSQL/HighGo/MySQL/openGauss/人大金仓集成：锁等待、并发写阻塞、30 秒超时、DML 顺序、批次、提交、回滚和锁释放；PostgreSQL 家族在具备 PostGIS 兼容扩展时同时覆盖 Geometry。
- 指标：七项计数恒等式、rowsWritten、affectedRows、零变化和失败无指标。
- 前端：创建、配置、Key 推荐、失效值保留、删除确认、默认阈值、摘要和 JSON 导入导出。
- 共享实现：同一组比较/事务契约测试分别使用 JDBC 目标和 Model 目标适配器运行，防止语义分叉。

## 12. 不在范围内

- STREAMING、micro-batch、Checkpoint、Watermark 或增量 CDC。
- Outbox、Kafka、变更历史表、SCD、ChangeSet 输出端口或 before/after 持久化。
- 条件范围同步、行政区/项目过滤或部分目标快照；DELETE 始终按完整目标表解释。
- 自动建表、自动建唯一约束、自动 Schema 演进或自动选择 Key。
- 大数据临时表、数据库 MERGE、Spark 分布式对比、分区事务或跨 Output 原子事务。
- 比较容差、字段忽略、大小写折叠、自定义表达式或自动 Geometry 修复。
