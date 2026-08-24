# 血缘接入契约 V3

## 1. 目的与边界

本契约规定任务定义生成器如何向 DataScalpel 血缘基础层发布静态血缘。当前唯一写入口是 `data-scalpel-business` 内部 Java 服务 `TaskLineageSnapshotService`，不提供 HTTP 写接口，也不允许前端直接维护血缘。

任务快照写入契约继续只表达任务产生的以下静态关系：

- 纳管模型 `MODEL`；
- 未纳管 JDBC 物理表 `JDBC_TABLE`；
- Kafka Topic、文件数据集表、HTTP/空间资源、对象存储路径和 JDBC 查询结果等 `EXTERNAL_RESOURCE`；
- 表级读写链路；
- 可证明的字段值来源；
- 字段参与 Join、过滤、分组、排序或分区的非值来源用途；
- 输出字段没有来源边时的明确写入行为。

运行证据、任务调度依赖、行级血缘、历史快照查询及人工维护关系不属于 V3。静态血缘不得从运行日志、Checkpoint、Offset 或输出数据猜测，也不得保存数据值、SQL 参数值、对象路径原文或任何凭据。

V3 新增标准数据服务的查询期组合语义，但数据服务不是任务资产，也不写入 `TaskLineageSnapshot`。查询层使用当前标准服务定义、生命周期和最后成功部署快照，将服务作为模型的终端消费者展示。

## 2. 快照语义

一次 `publish(TaskLineageSnapshotDraft)` 必须提交一个任务定义版本的完整血缘快照，不是相对上一版本的增量。

快照包含：

- `taskId`：现有任务 UUID；
- `definitionVersion`：当前已保存任务定义版本；
- `coverage`：本快照的覆盖程度；
- `generatorVersion`：生成器自身的正整数契约版本；
- `assets`、`fields`、`fieldEdges`、`fieldUsages`：完整内容。

服务会锁定任务行，验证任务和定义版本，补齐任务、模型、字段及数据源名称快照，规范化内容并计算 SHA-256。相同任务版本和相同内容幂等返回原快照；同版本内容变化会增加 `generation`，并关闭旧当前快照。发布中任一校验或保存失败会整体回滚。

`retiredAt = null` 表示任务当前生效的血缘快照。新快照发布时旧快照只关闭有效期，内容继续保留。任务重复运行不调用血缘发布，也不改变快照。

## 3. 资产与输出链路

### 3.1 输出链路

`flowKey` 表示一个独立输出链路。同一 `flowKey` 必须且只能有一个 `OUTPUT` 资产。一般链路包含一个或多个 `INPUT` 资产；完全由常量、默认值或空值产生的可靠输出允许没有输入资产。多输出任务必须为每个输出建立不同 `flowKey`，每个输出仅提交真实参与该输出的输入，禁止把所有输入与所有输出做笛卡尔积。

输出资产必须声明 `writeMode`：

| 值 | 含义 |
| --- | --- |
| `APPEND` | 追加写入 |
| `FULL_OVERWRITE` | 全量覆盖 |
| `UPSERT` | 按业务键更新或插入 |
| `PARTITION_OVERWRITE` | 分区覆盖 |
| `SNAPSHOT_SYNC` | 目标快照与来源同步 |
| `CREATE_NEW` | 创建新的外部文件/对象输出 |

输入资产不得声明写入模式。

### 3.2 MODEL

模型资产必须提交：

- `modelId`：模型 UUID；
- `modelSchemaVersion`：生成时模型的当前 Schema 版本。

模型字段必须提交字段 UUID。生成器不得用模型编码或字段编码替代 UUID。模型名称、编码和字段名称由发布服务从管理库保存为历史解释快照。

### 3.3 JDBC_TABLE

未纳管 JDBC 表必须提交：

- `dataSourceId`；
- `catalogName`，没有时为 `null`；
- `schemaName`，没有时为 `null`；
- `physicalTableName`；
- 每个已声明字段的真实物理列名。

发布服务要求数据源存在且为 JDBC 类型，并保存数据源名称快照。发布血缘不会自动创建模型、物理表或来源绑定。

### 3.4 EXTERNAL_RESOURCE

外部资源用于表达不应伪装成 JDBC 表或模型的资产，当前类型固定为 `KAFKA_TOPIC`、`FILE_DATASET_TABLE`、`HTTP_API_RESOURCE`、`SPATIAL_SERVICE_RESOURCE`、`OBJECT_STORAGE_PATH` 和 `JDBC_QUERY_RESULT`。

必须提交 `externalResourceType`、稳定 `resourceKey` 和安全名称快照；存在平台资源 UUID 时同时提交 `resourceId`，属于某个数据源时提交 `dataSourceId`。Topic 名、文件表 UUID、API/空间资源 UUID可参与稳定键；对象存储路径和 JDBC 查询文本只允许提交 SHA-256，禁止保存原始路径、SQL、URL、认证参数或凭据。

## 4. 稳定键规则

所有键均为调用期局部键，必须使用 UTF-8、区分大小写、首尾无空白并在同一任务定义版本重新生成时保持稳定。不得包含显示名称。原始稳定标识超过字段长度时使用小写十六进制 SHA-256，不得截断原文制造碰撞。

推荐固定算法如下：

| 键 | 范围 | 推荐生成规则 |
| --- | --- | --- |
| `originKey` | 同一逻辑/物理资产跨输出链路 | 模型为 `model:<modelUuid>`；JDBC 表为 `jdbc:<sha256(dataSource\0catalog\0schema\0table)>`；外部资源为 `external:<sha256(type\0dataSource\0resourceId\0resourceKey)>` |
| `flowKey` | 一个输出链路 | Local SQL 固定输出为 `local-sql:output`；Canvas 为 `canvas:<稳定输出节点ID>`；过长时为 `flow:<sha256(raw)>` |
| `assetKey` | 快照内唯一 | `asset:<sha256(flowKey\0role\0originKey)>`；同一资产参与不同输出链路时会得到不同键 |
| `fieldKey` | 资产内唯一 | 模型字段为 `model-field:<fieldUuid>`；非模型字段为 `field:<sha256(生成器局部稳定字段键)>` |
| `derivationKey` | 一个目标字段的一次派生表达式 | `derive:<sha256(flowKey\0transformNodeKey\0targetAssetKey\0targetFieldKey\0稳定表达式)>` |

一个目标字段由多个源字段共同计算时，多条边必须使用相同 `derivationKey`；源字段不得进入该键的计算。表达式规范化必须由对应生成器版本固定，禁止使用随机 UUID、遍历顺序或可变显示名称生成键。

`transformNodeKey` 和字段用途的 `nodeKey` 使用任务定义中的稳定节点 ID。Local SQL 没有节点 ID 时使用契约固定值，例如 `local-sql:select`、`local-sql:where`，不得使用 SQL 片段全文作为节点键。

## 5. 字段关系

### 5.1 值来源边

字段边只能在同一 `flowKey` 内从 `INPUT` 字段指向 `OUTPUT` 字段。目标字段必须标记 `DERIVED`。

派生类型：

- `DIRECT`：无语义变化的直接映射或重命名；
- `CALCULATED`：表达式、函数、类型转换或多字段计算；
- `AGGREGATED`：聚合函数产生的结果。

多源一目标提交多条边，共用同一 `derivationKey`。Join 键、过滤字段等处理条件不是输出值来源，不得为了让图“更完整”而伪造成字段边。

### 5.2 字段用途

字段用途独立保存在 `fieldUsages`：

- `JOIN_KEY`；
- `FILTER_CONDITION`；
- `GROUP_KEY`；
- `SORT_KEY`；
- `PARTITION_KEY`。

用途必须引用本输出链路内已经声明的字段和稳定处理节点。一个字段可以同时有值来源边和多个用途；查询层会使用 `FIELD_EFFECT` 单独展示用途，不与 `DERIVES` 混合。

### 5.3 输出字段行为

每个已声明的输出字段必须选择一种行为：

| 值 | 语义 |
| --- | --- |
| `DERIVED` | 有一条或多条可靠来源边 |
| `WRITTEN_UNKNOWN_SOURCE` | 确认被写入，但当前生成器无法证明来源 |
| `CONSTANT` | 由常量产生，不保存常量值 |
| `DEFAULT_VALUE` | 由目标默认值产生，不保存默认值内容 |
| `NULL_FILLED` | 明确写入空值 |
| `PRESERVED` | 更新过程中保留目标已有值 |
| `NOT_WRITTEN` | 当前写入链路不写该字段 |

`DERIVED` 至少需要一条来源边。其他行为不得伪造来源边。

## 6. 覆盖程度

| 覆盖程度 | 判定 |
| --- | --- |
| `MODEL_ONLY` | 只可靠识别表级输入和输出；不得提交字段、字段边或字段用途 |
| `FIELD_PARTIAL` | 有可靠字段信息，但未覆盖全部输出字段，或包含 `WRITTEN_UNKNOWN_SOURCE` |
| `FIELD_COMPLETE` | 每个模型输出资产覆盖当前模型全部字段，且没有未知来源字段 |

`FIELD_COMPLETE` 不代表每个输出字段都有源字段：常量、默认值、空值、保留值和未写入均可完整表达。生成器不确定时必须降级为 `FIELD_PARTIAL` 或 `MODEL_ONLY`，不得猜测字段映射。

## 7. 生命周期与陈旧判定

- 发布新快照：关闭同一任务旧当前快照，保留历史内容；
- 任务重复运行：只产生 `TaskRun`，不改血缘；
- 任务停用：保留当前血缘，查询节点展示停用状态；
- 任务删除：业务删除流程调用 `retireCurrent(taskId)`，历史快照保留；
- 模型删除：当前快照引用会阻止删除，历史快照不阻止；
- 模型 Schema 变化：快照不自动删除，查询比较 `modelSchemaVersion` 并标记 `STALE`；
- 重新生成：读取当前任务定义和资源结构，构造完整草稿，再调用 `publish`。

生成器不得直接写 Repository、修改 `retiredAt` 或分批保存子实体。

## 8. 内部 Java 接入

调用方只依赖：

```java
TaskLineageSnapshotResult publish(TaskLineageSnapshotDraft draft);
void retireCurrent(UUID taskId);
```

推荐调用顺序：

1. 在任务定义保存或正式发布边界取得稳定 `taskId` 和 `definitionVersion`；
2. 在业务事务外完成纯内存解析、编译及 provenance 传播，不访问或修改目标数据；
3. 构造完整 `TaskLineageSnapshotDraft`；
4. 调用 `publish`，由服务在一个短管理库事务中验证并保存；
5. 发布失败时任务发布方按自身规则处理，不得降级为部分 Repository 写入。

当前没有外部 HTTP 接入规范。确需跨进程写入时必须先设计认证、幂等和契约版本，不得直接暴露当前 Draft DTO。

## 9. 后续生成器要求

### 9.1 Local SQL

Local SQL 使用 JSqlParser 5.3 AST 与 JDBC 实际结果列共同生成血缘，固定 `flowKey=local-sql:output`、`generatorVersion=1`。显式输入模型始终作为表级输入资产；AST 可靠发现的基础表必须唯一匹配其中一个输入模型，否则发布校验阻断。系统不会按 AST 自动增删任务输入。

AST 在别名、通配符、CTE、子查询、集合运算、函数、聚合和字段歧义下只提交能够证明的来源。裸字段为 `DIRECT`，计算为 `CALCULATED`，聚合为 `AGGREGATED`；Join、过滤、分组、排序和窗口字段作为用途提交。AST 不可用、输出数量与 JDBC 不一致或单个输出无法证明时使用 `FIELD_PARTIAL` 和 `WRITTEN_UNKNOWN_SOURCE`，不得通过字段同名、字符串匹配或运行日志补猜。完整可靠且覆盖全部输出字段时才使用 `FIELD_COMPLETE`；Local SQL 生成器不再创建新的 `MODEL_ONLY` 快照。

只有发布和重新启用会调用快照服务。静态分析与 JDBC 检查在管理事务外完成，最终短事务内发布血缘并更新任务状态；保存、普通校验、停用和重复运行不修改正式快照。

### 9.2 Spark Canvas

Spark Canvas 使用编译阶段已经建立的 Schema-only Dataset，在不触发 Spark Action 的前提下读取每个 Output Writer 前 Dataset 的 `queryExecution().analyzed()`。Input Operator 为 Catalyst Attribute metadata 写入资产和字段身份；Processor 产生新逻辑表时写入稳定节点边界；Output Operator 保存完成最终字段映射和 Cast 后的 Dataset。分析器从输出计划反向解析到 Input 标记，并在 Task Engine 边界转换成无 Spark 类型的 `CanvasLineageCompilation`。

支持的可靠核心节点为 `Project/Alias/AttributeReference`、`Filter`、`Join`、`Aggregate/AggregateExpression`、`Sort`、`Window`、`Union` 和 `Generate`。字段选择和重命名保持 `DIRECT`，表达式和 Cast 为 `CALCULATED`，聚合为 `AGGREGATED`；Join、过滤、分组、排序和窗口分区单独形成字段用途。窗口聚合保留 `AGGREGATED`，排名等无字段值来源的动态表达式使用 `WRITTEN_UNKNOWN_SOURCE`。字面量只记录 `CONSTANT/NULL_FILLED`，不保存值。表达式指纹保留 Catalyst 子表达式顺序，但只记录字面量种类而不记录值，避免把 `a-b` 与 `b-a` 等不同表达式合并。无法可靠解释的计划或表达式必须将对应链路降级为 `FIELD_PARTIAL`，不得保存部分猜测来源边；整条字段分析异常时可降级为 `MODEL_ONLY`，但仍应从 Input Marker 保留能够安全识别的表级输入。

每个 Output 固定 `flowKey=canvas:<稳定输出节点ID>`。多输出 Canvas 分别分析各自 Writer 前计划，只提交该输出真实可达的 Input，覆盖程度也按 flow 独立保存；快照级覆盖程度为所有 flow 中最差值。批任务和实时任务共用这套静态编译逻辑，实时输入 Schema 可以来自零行流式 Dataset，但分析遇到 Input Marker 后必须停止下钻，不解释 `rate`、Checkpoint、Trigger 或 Offset。

同一 flow 内多个 Input 节点若引用同一资产，转为草稿时按 `assetKey` 和 `fieldKey` 去重，但仍保留能区分变换节点的派生关系。输出模型或物理表的自增列、生成列及有目标默认值且未显式映射的列使用 `DEFAULT_VALUE`；其他未写入列使用 `NOT_WRITTEN`。JDBC Query Input 的外部资源稳定键包含已分析 SQL 的 SHA-256，但不保存 SQL 原文。

普通设计期编译只返回血缘预览，不写管理库。只有任务发布和重新启用会在事务外完成编译及草稿构造，并在最终短事务内调用 `TaskLineageSnapshotService.publish` 后更新任务状态。任务保存、停用、批任务重复运行、实时启动/停止/恢复均不生成或修改正式血缘。Spark 类、`StructType`、Catalyst AST 或 ExprId 不得进入 JPA 实体、REST 或共享契约；ExprId 只允许在单次分析过程中关联属性。

## 10. 查询语义

模型页面只查询 `retiredAt = null` 的当前快照：

- `GET /api/v1/models/{modelId}/lineage/table`；
- `GET /api/v1/models/{modelId}/lineage/fields/{fieldId}`。
- `POST /api/v1/models/{modelId}/lineage/actions/query-fields`，请求携带 `fieldIds`、方向和 1～2 层深度。

模型接口均要求 `model.view`、`task.view` 和 `service.view`，默认查询上下游两次任务转换。批量字段请求中，`fieldIds=null` 表示按模型顺序默认前 20 个，空数组表示明确不展示字段，显式选择去重后最多 50 个。旧单字段接口继续保留并委托批量查询。图固定最多 200 个节点和 600 条边，超限返回 `truncated=true` 和字段级警告。当前没有血缘生成器接入时返回正式空图，不生成示例数据。

批量字段响应在节点和边上携带查询期计算的 `focusFieldKeys`；模型和服务使用模型字段 UUID 字符串，任务使用快照中的稳定 `fieldKey`。共享节点、共享边合并全部路径键，路径键不写入血缘快照，也不改变生成器接入契约。前端利用该索引在字段悬停时临时高亮路径、点击时锁定，点击空白或按 Esc 清除聚焦。

字段节点同时返回 `fieldOwner`，包含所属资产的稳定键、资产类型、表头名称、表头说明和字段顺序。该信息只用于查询展示分组：前端按所属模型、JDBC 表或外部资源生成动态高度的表卡片，字段作为卡片内的独立行和连接端口；不得根据字段 `subtitle` 或显示名称猜测所属表，也不得把该查询期分组信息写回任务血缘快照。

模型下游还会在查询期组合当前标准数据服务关系：

- 图节点使用 `DATA_SERVICE`，模型或字段通过 `EXPOSES` 指向服务；该边不表示任务读写，也不增加任务转换深度；
- 服务至少成功启用到 Service Engine 一次后才进入模型正式血缘，网关发布和撤回不影响关系；
- 已停用服务继续展示，草稿服务不进入模型正式血缘；停用后修改关联模型会立即按当前定义切换关系；
- 已启用服务的字段暴露根据当前 Revision 的部署快照字段白名单与模型当前字段精确匹配，不按名称相似或字段同名补猜；不一致时保留表级节点并标记陈旧；
- 标准服务视为暴露其字段白名单中的全部字段。SQL 和脚本服务不在 V3 中生成血缘。

服务详情使用同一份组合图：

- `GET /api/v1/data-services/{serviceId}/lineage/table?depth=1|2`；
- `GET /api/v1/data-services/{serviceId}/lineage/fields/{fieldId}?depth=1|2`。
- `POST /api/v1/data-services/{serviceId}/lineage/actions/query-fields`，一次查询当前关联模型的多个字段。

接口要求 `service.view`、`model.view` 和 `task.view`，以服务为当前根节点，只向上展开关联模型及其最多两次任务转换。草稿标准服务允许在自身详情查看当前定义关系；SQL、脚本或未配置标准定义返回正式空状态。

任务详情查询同一份当前快照：

- `GET /api/v1/tasks/{taskId}/lineage/table?flowKey=...`；
- `GET /api/v1/tasks/{taskId}/lineage/fields?flowKey=...&outputFieldKey=...`。
- `POST /api/v1/tasks/{taskId}/lineage/actions/query-fields`，请求固定一个 `flowKey`，并可携带最多 50 个 `outputFieldKeys`。

服务和任务批量接口同样以 `null` 表示默认前 20 个、空数组表示空选择。启用标准服务的默认字段只从部署快照仍能精确匹配的暴露字段中选择；草稿和停用服务使用当前模型字段。任务批量查询不会合并不同 `flowKey`，查询边、对端字段和字段用途均按当前链路与当前字段集合批量读取。

响应同时返回可选 flow 和输出字段，任务页按输出链路隔离展示模型、JDBC 表与外部资源。任务字段图以选中输出字段的值来源为主路径，同时以独立 `FIELD_EFFECT` 边展示该 flow 中影响输出行的 Join、过滤、分组、排序和窗口分区字段，不将它们伪造成所选字段的值来源。没有当前快照时返回正式空状态；字段链路只有 `MODEL_ONLY` 时明确提示只有表级覆盖。
