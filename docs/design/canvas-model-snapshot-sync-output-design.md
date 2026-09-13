# Canvas `MODEL_SNAPSHOT_SYNC_OUTPUT` 设计文档

## 1. 状态、目标与范围

- 实现状态：已开发。
- 目标协议版本：Canvas `2.0`。
- 目标 Manifest 版本：`v11`。
- 当前正式版本为 Canvas `2.0`、Manifest `v11`、Result `v3`。
- 节点类型：`MODEL_SNAPSHOT_SYNC_OUTPUT`。
- 节点类别：`OUTPUT`。
- 执行模式：仅 `BATCH`。
- 图规则：恰好一条入边，不允许出边。
- 输入要求：来源表必须为 `BOUNDED`。

`MODEL_SNAPSHOT_SYNC_OUTPUT` 把一个上游完整快照同步到已发布模型的受管物理表。它与
`MODEL_OUTPUT` 的 APPEND/OVERWRITE 并存，不作为 `JdbcWriteMode` 的新枚举值：

- 来源独有实体插入模型表。
- Key 相同但映射字段变化的实体更新模型表。
- 完全相同的实体不触发数据库 UPDATE。
- 目标独有实体按配置保留或删除。

本节点只负责把稳定模型引用解析为受控 JDBC 目标。字段 Cast、Key 校验、Geometry 拓扑比较、
ChangeSet、删除保护、规模限制、严格锁表、事务写入和结果指标全部遵循
[JDBC 快照同步 Output 设计](canvas-jdbc-snapshot-sync-output-design.md)，不得复制第二套实现或
产生不同语义。

首版只允许 `MANAGED` 模型。`EXTERNAL` 模型、Outbox、Kafka、变化历史和大数据同步不在范围内。

## 2. 稳定配置协议

公共配置由 JDBC Snapshot Sync 文档定义：

```ts
interface ModelSnapshotSyncOutputConfiguration
  extends SnapshotSyncConfiguration {
  targetModelId: string;
}
```

展开后的稳定结构为：

```ts
interface ModelSnapshotSyncOutputConfiguration {
  sourceTableName: string;
  targetModelId: string;
  keyColumns: string[];
  columnMappings: JdbcColumnMapping[];
  deletePolicy: SnapshotDeletePolicy;
}
```

示例：

```json
{
  "sourceTableName": "district_snapshot",
  "targetModelId": "805c80b3-959e-4690-90d3-5c2d613864c1",
  "keyColumns": ["district_code"],
  "columnMappings": [
    {
      "sourceColumnName": "code",
      "targetColumnName": "district_code"
    },
    {
      "sourceColumnName": "name",
      "targetColumnName": "district_name"
    },
    {
      "sourceColumnName": "geometry",
      "targetColumnName": "boundary"
    }
  ],
  "deletePolicy": {
    "action": "KEEP",
    "maxDeleteRows": null,
    "maxDeleteRatio": null
  }
}
```

协议边界：

- Definition 只保存 `targetModelId`，不保存模型名称、code、schemaVersion、数据源 UUID、Catalog、
  Schema、物理表名、字段或唯一键快照。
- `keyColumns` 保存模型目标字段 code，用户必须显式选择，不自动使用模型主键。
- 用户可以选择任意合法字段组合；主键和唯一索引只用于推荐与 Warning。
- 映射、Key 和删除策略与 JDBC Snapshot Sync 完全一致。
- 默认配置使用空字符串、空 Key、空映射和
  `KEEP + null + null`，允许未配置草稿进入统一 Compiler 诊断。
- Java Record 实现公共 `SnapshotSyncConfiguration` 接口；前端配置类型扩展同一个
  `SnapshotSyncConfiguration`，避免复制字段语义。

## 3. 模型目标解析与生命周期

目标模型必须同时满足：

- `targetModelId` 是合法 UUID，并能在 Metadata Snapshot 中定位。
- 模型状态为 `PUBLISHED`。
- `physicalTableMode` 为 `MANAGED`；`EXTERNAL` 固定返回
  `MODEL_SNAPSHOT_SYNC_REQUIRES_MANAGED_MODEL`。
- 绑定数据源已启用、属于 JDBC、具有 `STORAGE` 用途。
- 数据库类型为 PostgreSQL 或 MySQL。
- 模型物理位置配置完整；不比较物理表与模型当前 Schema 是否完全一致。

发布、重新启用和每次运行准备阶段继续使用现有模型引用快照机制：

1. 短事务读取模型、字段和数据源版本快照。
2. 直接从已保存模型字段和主键标记构造 Manifest Metadata Snapshot。
3. 在短事务中重新检查模型 `schemaVersion/updatedAt/status` 和数据源 `updatedAt`。
4. 任一变化都拒绝当前准备，不自动改写 Canvas Definition。

Runner 只能使用 Manifest 中解析后的模型与受保护连接信息，不允许根据 Canvas JSON 临时查询
管理数据库，也不允许信任导入定义中不存在的物理信息。

## 4. 模型 Metadata 扩展

为复用 JDBC 目标的 Key 推荐和 Warning，`MetadataModel` 增加：

```ts
interface MetadataModel {
  // existing fields
  uniqueKeys: MetadataUniqueKey[];
}
```

生成规则：

- 数据直接来自按 `sortOrder` 排列的已保存模型字段。
- 所有 `primaryKey=true` 字段按字段顺序组成一组名为 `MODEL_PRIMARY_KEY` 的逻辑主键。
- 不读取物理唯一约束或索引；模型未标记主键时 `uniqueKeys` 为空。
- Metadata Snapshot 只用于编译、运行和 Inspector 上下文，不回写 Canvas Definition。
- `uniqueKeys` 为空不阻止用户选择任意 Key，但会产生非数据库唯一 Warning。

模型自身字段中的 `primaryKey` 仍是模型管理契约，不塞入 `CanvasColumnSchema`。快照同步的公共
实现只消费统一 `MetadataUniqueKey`，避免为模型单独维护 Key 逻辑。

## 5. 映射、Cast 和 Key

### 5.1 字段映射

节点使用现有 Model/JDBC Output 共享字段映射能力：

- 始终按 `columnMappings` 把来源字段显式映射为模型字段 code。
- 设计器可以自动填充完全同名、忽略大小写及驼峰/下划线等价的空白映射。
- 模型非空、无默认值且非自动生成的可写字段必须被覆盖。
- 未映射来源字段直接忽略。
- 所有映射字段在比较前显式 Cast 为模型目标平台类型。
- 实际物理表 Schema 漂移继续返回运行准备或 Runner Schema 错误，不降级为值比较。

模型字段定义是稳定目标 Schema。来源字符串、数值或时间只有在 Spark 成功 Cast 为模型类型后
才参与比较；Analyzer 不支持的 Cast 在 Compiler 阶段失败。

### 5.2 Key

- 数量固定为 `1..32`，保存目标模型字段 code。
- 字段必须存在、完成映射、可写，不能是 Geometry、自增或生成字段。
- 用户选择可以不等于模型主键或物理唯一约束。
- nullable Key 返回 Warning，真实 NULL 在 Runner 阶段失败。
- Key 字段集合不匹配 `MetadataModel.uniqueKeys` 中任何完整字段集合时返回
  `SNAPSHOT_SYNC_KEY_NOT_DATABASE_UNIQUE` Warning。
- 来源和目标 Key 在真实数据中都必须非 NULL 且唯一；不唯一时在任何 DML 前失败。

模型主键只显示“推荐”标识，不自动写入配置。这样用户可以使用模型中的其他稳定业务字段做
对比，同时通过运行时唯一性校验防止误更新和误删除。

## 6. 比较、删除和事务语义

本节点不定义模型专属比较器，固定复用 JDBC Snapshot Sync：

- 比较全部已映射、可写的非 Key 模型字段。
- NULL、数值、字符串、Binary、日期时间使用 Cast 后目标类型的公共相等规则。
- Geometry 使用 JTS/Sedona 拓扑相等；无效 Geometry 使任务失败，不自动修复。
- 只有发生字段变化的匹配行进入 UPDATE；UPDATE 设置全部已映射非 Key 字段。
- Key 变化表现为 DELETE + INSERT。
- 未映射模型字段不比较、不更新；INSERT 必须满足物理表默认值和 nullable 约束。

删除策略：

- 默认 `KEEP`，目标独有模型行保持不变。
- `DELETE` 表示来源是模型物理表的完整快照，不支持局部范围。
- DELETE 模式默认最大 1000 行、20%；任一超限即整次失败。
- 来源为空且模型表非空时禁止删除。
- 所有保护在第一条 DML 前完成。

事务语义：

- 来源 Cast、缓存、规模和来源 Key 校验在数据库锁外完成。
- 模型物理表在单连接中取得严格写锁，再读取目标、计算 ChangeSet 并执行 DML。
- `DELETE → UPDATE → INSERT` 使用 500 行 PreparedStatement batch。
- 全部 DML 在一个目标数据库事务中提交或回滚。
- 不复用 Spark 分区 Writer，不与同任务其他 Output 建立跨节点事务。

PostgreSQL/HighGo/MySQL/openGauss/人大金仓锁、SQL、值绑定、连接恢复和失败分类由同一个
`JdbcSnapshotSyncExecutor` 与方言能力完成。

## 7. 规模限制、结果和安全边界

节点使用 Manifest v11 的公共 `snapshotSyncLimits`：

- 来源和目标各最多 100,000 行。
- 两侧合计估算最大 256 MiB。
- 严格目标锁默认等待 30 秒。

这些值由 `data-scalpel.task.snapshot-sync.*` 部署配置生成，不进入模型或 Canvas Definition。
超限不自动切换其他执行模式。

成功节点返回 `result.json v3` 的 `SnapshotSyncMetrics`：

- `sourceRows`
- `targetRows`
- `insertedRows`
- `updatedRows`
- `deletedRows`
- `unchangedRows`
- `retainedTargetOnlyRows`

`rowsWritten` 和顶层 `affectedRows` 使用实际提交的新增、更新、删除总数。失败或回滚时不返回
成功指标。运行详情通过现有 result 制品接口展示，不新增模型或任务 HTTP API。

安全摘要只允许记录模型 UUID、模型 code、模型 schemaVersion、物理模式、Key 字段名、映射
模式、映射数量、删除策略和阈值。不得记录模型数据、Key 值、before/after、Geometry、WKB、
数据库凭据或 Manifest 全文。

## 8. Task Engine 和前端复用设计

### 8.1 后端

- `ModelSnapshotSyncOutputNodeOperator` 只校验模型状态/模式，解析数据源和物理目标。
- 解析后构造与 JDBC 节点相同的目标描述并调用 `SnapshotSyncOperatorSupport`。
- 共享 `CanvasPreparedSnapshotSyncOutput`、`SnapshotChangeSet`、值规范化、值比较、规模限制、
  `JdbcSnapshotSyncExecutor` 和方言 SQL。
- 模型差异不得进入 Runner 比较循环；Runner 不应根据节点类型复制 JDBC 分支。
- 节点仍作为独立稳定类型注册，生命周期阶段为 `WRITE`，成功消息为“模型快照同步完成”。

### 8.2 前端

- Palette 名称为“模型快照同步”，分组为模型输出，说明为“对比完整快照并同步受管模型”。
- 只在 BATCH 模式显示。
- Inspector 顺序固定为：来源表 → 目标模型 → 只读模型/物理位置摘要 → 字段映射 → 对比 Key
  → 目标独有行策略 → 删除保护。
- 模型选择器只列出已发布 MANAGED 模型；导入定义引用 EXTERNAL 或失效模型时保留原值并标红。
- Key 候选、推荐标记、删除确认、默认阈值、Warning 和运行指标展示复用
  `SnapshotSyncConfigurationFields`。
- 模型目标区域只负责模型选择和只读摘要，不复制 JDBC 数据源/表选择器。
- 节点摘要显示目标模型、Key 数量和 KEEP/DELETE，不显示物理凭据或数据值。

## 9. 稳定错误码

模型节点复用全部 `SNAPSHOT_SYNC_*` 错误，并保留现有模型 UUID、模型不存在、未发布和数据源
不可用错误；不产生物理 Schema 漂移错误。新增模型专属错误：

| 错误码 | 阶段 | 条件 |
| --- | --- | --- |
| `MODEL_SNAPSHOT_SYNC_REQUIRES_MANAGED_MODEL` | Compiler | 目标模型是 EXTERNAL |
| `MODEL_SNAPSHOT_SYNC_DATABASE_NOT_SUPPORTED` | Compiler | MANAGED 模型不是 PostgreSQL/HighGo/MySQL/openGauss/人大金仓 |
| `MODEL_SNAPSHOT_SYNC_OUTPUT_FAILED` | Runner | 无法归类的模型快照同步失败 |

真实 JDBC、锁、Key、删除保护和 Geometry 失败仍使用公共错误码，不能仅因目标通过模型解析就
改名，保证两个节点诊断和运维行为一致。

## 10. 版本、注册与文档同步

- `MODEL_SNAPSHOT_SYNC_OUTPUT` 与 `JDBC_SNAPSHOT_SYNC_OUTPUT` 均为 Canvas `2.0` 的正式节点。
- Canvas `1.x` 定义按大版本不兼容处理，不解析节点配置，也不转换旧字段映射。
- Canvas 稳定 NodeType 数量从 38 增加到 40。
- Manifest `10 → 11`；Runner 支持 v10/v11，v10 不得携带新节点。
- Result `2 → 3`；Runner 只写 v3，Dispatcher 在滚动升级期读取 v2/v3。
- Canvas Definition、Canvas 执行、Manifest、Result、Registry 和模型节点设计文档随实现同步更新。

## 11. 测试与验收设计

- Contracts：配置、公共删除策略、节点联合、`MetadataModel.uniqueKeys` 和严格 JSON 往返。
- 版本：Canvas 2.0 统一映射、Manifest v11、Result v3 和旧大版本拒绝规则。
- Compiler：PUBLISHED/MANAGED/STORAGE/支持快照同步的 JDBC 数据库、BOUNDED、映射、Cast、Key 和 Warning。
- 模型生命周期：schemaVersion、updatedAt、状态和数据源在运行准备阶段发生变化。
- 共享比较：使用与 JDBC 节点相同的参数化用例覆盖所有标量和 Geometry 拓扑相等。
- Runtime：INSERT、UPDATE、DELETE、UNCHANGED、RETAINED、空来源、Key 变化和整体回滚。
- 安全限制：两侧 NULL/重复 Key、行数/估算字节、删除数量/比例和锁超时。
- 模型边界：MANAGED 成功、EXTERNAL 拒绝、不支持快照同步的数据库拒绝；物理结构差异不作为门禁。
- 结果：七项指标、rowsWritten、affectedRows、失败无指标和运行详情展示。
- 前端：模型筛选、失效值保留、共享 Key/删除交互、默认阈值、摘要和 JSON 导入导出。
- 复用验收：JDBC 与 Model 两个 Operator 只做目标解析，共享 Executor 的关键路径不允许复制。

## 12. 不在范围内

- EXTERNAL 模型或不支持快照同步的数据库模型同步。
- 普通 MODEL_OUTPUT 的 APPEND/OVERWRITE 行为调整。
- 自动使用模型主键、自动创建 Key 约束或自动修复重复 Key。
- 局部模型范围同步、条件删除、软删除或逻辑状态映射。
- Outbox、Kafka、变化历史、SCD、ChangeSet 输出或审计表。
- Streaming、大数据临时表模式、分布式比较和跨 Output 事务。
