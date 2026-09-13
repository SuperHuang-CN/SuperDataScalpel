# Canvas 任务开发

先读所选[批处理](task-batch.md)或[实时](task-streaming.md)指南，再从[节点索引](canvas-nodes/index.md)读取实际使用的节点文档，不加载全部节点。

流程：**需求与元数据 → 画布设计 → Engine 预校验 → 用户确认 → 创建并保存草稿 → 读取核对**。只开发新任务；不执行 Trial、数据预览、真实加工、调度或发布。预校验不读取或写入真实业务数据，不能作为业务结果验证。

## 资料与资源发现

在当前工作目录 `task-development/<任务标识>/` 保存需求、元数据快照、设计、完整 Canvas JSON、预校验诊断、确认版本和执行记录。目录名使用稳定且不含敏感信息的标识。分别记录系统标识、资源 UUID、采集时间、可获取的结构版本、Canvas 协议版本、任务定义版本与 MCP 契约指纹，不把它们当成同一种版本。

使用系统 MCP 的检索、详情和调用工具获取当前契约；下表只用于定位。预校验可能声明为 `EXECUTE`，不能仅检索 `READ`。

| 能力 | 接口定位 |
| --- | --- |
| 来源/目标模型 | `GET /api/v1/models`、`GET /api/v1/models/{id}`；必须读取详情的 `model` 和 `fields` |
| 数据源及物理结构 | 检索 `/api/v1/data-sources` 的详情、命名空间、表元数据；按节点需要发现 API 资源、文件表 Schema、Kafka/TMQ 结构接口 |
| 计算引擎 | `GET /api/v1/compute-engines`、`GET /api/v1/compute-engines/{id}`，确认启用状态、能力和资源边界 |
| 目录与已有任务 | `GET /api/v1/directories` 使用 `scope=TASK`；`GET /api/v1/tasks`、`GET /api/v1/tasks/{id}` |
| 无状态预校验 | `POST /api/v1/task-compilations` |
| 确认后创建 | `POST /api/v1/directories`、`POST /api/v1/tasks` |
| 保存与回读画布 | `POST /api/v1/tasks/{id}/actions/update-canvas-definition`、`GET /api/v1/tasks/{id}/canvas-definition` |

模型名称、模型编码、逻辑表名和物理表名分别保存；分层筛选用标量 `warehouseLayerId`。数据源 `purposes` 是集合，不用通用 Search DSL 查询该集合。物理表列表有 500 条和 `truncated` 限制，缩小范围后读取所需表结构，不编造分页或声称扫描完整。

只获取设计所需结构，不采集真实样本或保存凭据。缺少模型、目标物理表、可用 Engine 或开放接口时报告依赖；不自动创建/发布模型、建表或改基础设施。文件节点只可引用现有已解析资源的 JSON 元数据；系统 MCP 不支持上传下载文件。

## 构造独立元数据快照

读取预校验请求的 `metadataSnapshot` 契约，显式转换所需资源。快照只提交当前图需要的结构与安全状态，不直接塞入模型详情、数据源连接配置或整个业务响应。

| 快照区域 | 转换要点 |
| --- | --- |
| `dataSources` | UUID、enabled、connectionKind、jdbcDatabaseType、purposes，以及实际用到的 `tables` / `tdEngineTmqTopics`；不含 URL、用户名、密码等连接秘密 |
| JDBC `tables` | 完整表标识、objectType、有序 columns 和真实 uniqueKeys；保留类型参数、nullable、默认值、生成/自增属性、几何类型与 CRS |
| `models` | 详情中模型的 id/code/name/schemaVersion/status/physicalTableMode；`storageDataSourceId` 映射为快照 `dataSourceId`；保留 catalog/schema/physicalTableName |
| 模型 `columns` / `uniqueKeys` / `fields` | fields 按 sortOrder 排序，字段 code 转为 columns.name、平台类型转入 fieldType；主键组的 name 可用 `MODEL_PRIMARY_KEY`，type 为 `PRIMARY_KEY`，columns 为完整键列。快照 fields 另保留已有字段 id/code/name/sortOrder 身份，不从示例值推断唯一键 |
| API / 空间服务资源 | 放入所属数据源的 tables，以资源 UUID 为 tableName，并使用对应 `API_RESOURCE` / `SPATIAL_FEATURE_RESOURCE` objectType；字段来自资源结构 |
| `fileDatasetTables` | 表及数据集 UUID、code/name/datasetType、parseStatus/fileStatus 与 columns；字段来自已解析表 Schema |

快照字段和枚举仍以实时 Schema 为准。无法取得必要类型、唯一键或资源状态时停止受影响部分，不用空数组伪装“已检查且没有约束”。Kafka 消息结构由输入节点明确声明；`JDBC_QUERY_INPUT` 的已分析输出 Schema 和 SQL 摘要是允许保存在该节点内的特例。

## 图、逻辑表与配置

当前手册对照 Canvas 4.47 编写。每次从实时契约确认主/次版本和节点能力，不能机械提交旧版本。持久化定义仅含 `schemaVersion`、`schemaMinorVersion`、`nodes`、`edges`；外部快照、预校验结果、凭据和 X6 内部对象单独保存。

- 节点使用稳定 UUID 作为 id，配置位于 `configuration`，JSON 类型判别字段为 `type`（例如 `FILTER`），不是 Java 方法名 nodeType；边使用独立稳定 UUID 和 `sourceNodeId` / `targetNodeId`。布局只包含契约的坐标/尺寸。
- INPUT：0 条入边、至少 1 条出边；PROCESSOR：至少 1 条入边、允许没有出边；OUTPUT：恰好 1 条入边、没有出边。图必须无环、引用存在且 ID 唯一。
- 实时图必须且只能有一个 Kafka/TMQ/JDBC 增量无界输入，至少一个合法输出，所有输出必须从该输入可达。JDBC 增量输入当前还要求只有一个终端输出节点。未使用的处理器结果可以产生 WARNING，不能替代可执行输出。
- 一条边传递逻辑表 Map，不等于一张表。多上游合并不能产生同名逻辑表；双表处理器可能从同一上游 Map 取两张表，不机械要求两条入边。
- 输出节点没有下游 Map。其他节点对表 Map 的保留、替换和追加规则看节点手册；不要默认处理器消费或删除所有输入表。
- 字段使用平台类型，几何字段带 kind/CRS/dimension。用上游 `outputTables` 核对下游可见字段、顺序、可空性和有界性，不能把数据库物理类型当作平台类型。
- `operations` 类处理器的每个操作有稳定 `operationId`、`sourceTableName` 和 `output`。`REPLACE_SOURCE` 替换源表，通常不填 outputTableName；`CREATE_NEW_TABLE` 保留源表并创建命名结果。同节点的操作都读取进入节点时的 Map，不能读取该节点另一操作刚生成的表；源表和操作 ID 不重复，任何一项失败则整个节点失败。特殊行为见 RENAME、DERIVE_COLUMNS 手册。
- `writes` 类输出每项有稳定 `writeId`。Canvas `columnMappings` 是显式 `{sourceColumnName,targetColumnName}` 列表；JAR SDK 的 `.map(target,source)` 参数方向不同。不能隐含按字段顺序匹配。

节点手册中的 JSON 是 `configuration`，不是完整节点或整个预校验请求。示例 UUID 和字段只表达前提；按已读取的真实资源替换。引用、union discriminator 和枚举不能凭名称猜测。

## Engine 预校验与确认

1. 组装 `POST /api/v1/task-compilations`：独立 `requestId` UUID；`task.type=CANVAS`，`task.definition` 为完整定义，`task.executionMode` 显式为 `BATCH` 或 `STREAMING`；同时传独立 `metadataSnapshot`。不传 `canvasTrial` 或任何试运行范围，不先创建任务试错。
2. 检查真实工具状态和业务响应。HTTP 200 不等于通过；读取 `valid`、`canvasIssues`、每个 `nodeResults` 的 `issues`、`inputTables`、`outputTables`。按问题的节点、字段路径、code、severity 和 message 修正。
3. ERROR 必须解决并对最终完整定义重新预校验；WARNING 保留并解释其影响。先修上游资源/Schema，再修下游，不用删除业务所需字段、缩小输出或更改口径来隐瞒问题。
4. 将最终图、输入/输出、各节点配置、目标映射、布局、设计理由、依赖、未验证范围与诊断绑定为明确版本。请求或响应超出 MCP 预算时报告能力缺口，不能截断图或诊断当作完整验证。
5. 展示此版本供用户确认。确认前只保存本地设计并调用上述预校验。用户修改业务设计后更新版本并重新预校验；旧结果不能证明新定义通过。

预校验只证明这份定义和快照可通过当前 Engine 分析；不证明来源可实际读取、数据值合法、写入约束满足或业务结果正确。部署接口契约改变时重新读取；影响设计的变化需要重新确认。

## 保存与恢复

用户确认明确版本后，复核必要契约、来源结构、目标冲突及 Engine 状态。按 scope、父目录 UUID 和名称定位并复用 `TASK` 目录，只创建已批准的必要目录。

`POST /api/v1/tasks` 提交实际契约允许的 name、directoryId、type、description、computeEngineId。批为 `SPARK_CANVAS`，流为 `SPARK_STREAMING_CANVAS`；任务没有独立 code 字段。成功立即保存 UUID，再通过 `actions/update-canvas-definition` 提交 `{definition: 完整定义}`。

创建与保存不是原子事务。每步记录确认版本、操作、UUID、状态和定义版本。请求结果不确定时先读任务与定义：已保存相同内容则继续核对，仍为空或内容不一致则辨别请求是否结束；无法确定时暂停该项，不直接重建或覆盖。任务没有 UUID 时按目录、名称、类型等缩小候选，再核对详情，不能只靠同名认领资源。

回读任务和 Canvas，核对规范化后的完整配置、图、版本、computeEngineId 和 `DRAFT`，交付 UUID、可确定的管理页面入口、本地资料位置与预校验报告。保存后的实质定义若变化，重新校验受影响定义；不自动删除回滚，不把部分成功称为全部完成。

恢复执行必须核实当前系统状态及当前会话授权，本地“已确认”标记不能自行产生授权。缺少本地文件能力或完整必要契约时明确说明，不能声称已保存资料或已经通过预校验。
