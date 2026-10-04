# 文件数据集管理设计

## 1. 设计目标

文件数据集只保存当前有效数据，不提供历史版本、恢复、回收站或旧来源保留。逻辑模型为：

```text
FileDataset
  ├─ FileDatasetFile
  └─ FileDatasetTable
       ├─ FileDatasetField
       └─ FileDatasetTableSource（有序当前来源）
```

数据集先按类型创建并保存共享解析参数，再上传文件。CSV、TSV、TXT、JSON、JSONL、GeoJSON、GEOJSONL、
GeoParquet、Parquet、Avro 和 SHP 支持表级 `APPEND`、`REPLACE_ALL` 和 `REPLACE_SOURCE`，每次只上传一个文件。
Excel、GDB 和 GeoPackage（GPKG）只支持整文件上传或替换。

数据集级上传表示“创建新的逻辑表”，不会按名称自动追加。多个来源按顺序执行 `UNION ALL`，
不合并文件、不去重、不做 Upsert，也不支持 Schema 演进。

## 2. 数据模型

### 2.1 `ds_file_dataset`

保存名称、目录、类型、描述和共享解析参数。数据集存在文件、逻辑表或非终态解析任务后，
`parsingOptionsLocked=true`，此时只能修改名称、目录和描述。数据集重新清空后可再次修改解析参数。

### 2.2 `ds_file_dataset_file`

保存当前文件或临时待校验文件的原始文件名、格式、压缩方式、对象 Key、存储形态和物化信息。
状态只有：

- `PREPARING`：SHP/GDB 归档正在安全检查和物化，或 GPKG 正在只读校验和发现表；
- `READY`：对象可供逻辑表校验或当前来源读取。

普通格式与 GPKG 直接使用原始对象。SHP/GDB 同时保存原归档对象和已发布物化前缀。准备或校验最终失败
时删除临时文件记录、原始对象和物化目录，不保留失败文件。

### 2.3 `ds_file_dataset_table`

逻辑表保存稳定 ID、code、名称、权威 Schema 摘要和当前解析状态：

- 同一文件数据集内的名称去除首尾空白、将内部连续空白转换为下划线后忽略大小写唯一；历史重名
  保持原状，但新增、文件发现和改名不能继续产生冲突；

- `parse_status`：`QUEUED/PARSING/READY/SCHEMA_READY`；
- `current_load_job_id`：当前唯一装载任务，非空时拒绝第二个装载；
- `parsed_metadata`：当前来源组合的预览摘要和来源元数据。
- `spatial_crs_authority/spatial_crs_code`：可空的表级源 CRS 回退确认，不表示坐标转换。

初始解析最终失败时删除空逻辑表。已有数据的追加或覆盖失败只清理新临时文件，原表、Schema 和
来源不变。

### 2.4 `ds_file_dataset_table_source`

来源表只保存已经生效的数据分片，不存在来源状态机。每条记录包含：

- `file_dataset_table_id/source_file_id`；
- `source_name/source_key/source_order`；
- `row_count/schema_fingerprint/source_metadata/activated_at`。

所有字段均描述当前数据。`APPEND` 在末尾创建来源；`REPLACE_ALL` 删除全部旧来源并创建顺序为
0 的新来源；`REPLACE_SOURCE` 原地更新目标记录，保持来源 ID 和顺序稳定。删除中间来源后将剩余
顺序压缩为 `0..n-1`。

### 2.5 Schema 与解析任务

`ds_file_dataset_field` 是逻辑表的权威 Schema。初始校验成功时创建；后续追加和覆盖只校验并
复用，不自动增列、拓宽类型或重建字段记录。

Geometry 字段额外持久化 `geometry_kind/crs_authority/crs_code/coordinate_dimension`，并通过
规范 `platformTypeDefinition` 返回管理端和 Canvas。Geometry 的完整定义参与来源 Schema 指纹，
CRS、kind 或 dimension 变化都不允许静默兼容。

`ds_file_dataset_parse_job` 是可重试的执行历史，不是业务版本。任务类型为：

- `FILE_PREPARATION`：SHP/GDB 归档检查、物化和表发现，或 GPKG 的只读表发现；
- `TABLE_SOURCE_VALIDATE`：执行 `INITIAL/APPEND/REPLACE_ALL/REPLACE_SOURCE` 的完整校验。

Job 保存数据集、表、文件名称快照，以及可空的 `load_mode/target_source_id/source_name/
source_key`。队列状态为 `QUEUED/RUNNING/SUCCEEDED/FAILED/CANCELLED`，并保留领取租约、
心跳、自动重试和有界历史清理。

## 3. 校验与提交

初始来源按格式校验并建立字段、解析器报告的行数、最多 1000 条预览样本和来源元数据。CSV、TSV、TXT、
JSON、JSONL、GeoJSON 和 GEOJSONL 必须逐条解码；Parquet 和 Avro 校验内置 Schema；GeoParquet 与 GPKG 还会完整扫描
WKB Geometry 和 GeoParquet Footer；SHP 还比较 Shape 类型、Z/M
维度、Geometry 字段和规范化 PRJ WKT。GeoJSON 固定为 RFC 7946 `FeatureCollection`：`properties`
构成属性字段，顶层 `Feature.id` 保存为可空 `_feature_id`，`geometry` 保存为 EPSG + XY 的 Geometry；
数据集解析参数指定 EPSG（默认 `4326`），不会根据旧式 `crs` 成员或坐标值推测、转换坐标。
GDB 按 `.gdbtablx` 中非空索引槽统计图层总记录数，不读取全部记录内容；字段和预览只解码有界样本。
因此 GDB 总数反映索引中的非空记录槽，但上传解析不对样本之外的每条记录做内容校验。

Excel（XLS/XLSX）在现有工作表事件流遍历中独立累计完整有效数据行数，不增加第二遍扫描；
表头、数据起始行之前的内容和全空行不计数，行号间隙也不计数。只保留最多 1000 条预览样本，
总数不受样本上限影响；字段类型推断仍沿用现有抽样逻辑。本修复不自动回填历史 Excel 来源
保存的样本计数，更新程序后需重新上传，或按整文件替换流程重建统计；替换的破坏性语义不变。

后续来源固定比较字段数量、名称、顺序、完整 `PlatformTypeDefinition` 和 nullable。任一不一致
都使 Job 失败并清理临时数据，不修改当前来源。

异步提交同时校验当前 Job ID、未过期租约及 leaseOwner，防止旧结果覆盖当前状态：

- 表结果必须满足 `table.currentLoadJobId == job.id`；
- 文件准备结果必须满足 `file.currentPreparationJobId == job.id`。

Worker 在事务外读取和校验，在短事务内提交。业务变更和 Worker 的成功/失败提交先锁定数据集，
再操作 Job、文件和表；领取与过期恢复使用 PostgreSQL 联表 `FOR UPDATE OF dataset, job SKIP LOCKED`，
同时取得数据集和 Job 的锁，遇到正在变更的数据集跳过，不在持有 Job 锁时阻塞等待数据集。
领取后在持有数据集锁的情况下再次检查同一来源是否有 RUNNING Job，避免候选查询的旧语句快照
造成同文件多图层同时领取。心跳仅更新 Job，不反向申请数据集锁；解析、解压和对象存储读写
不因上述互斥而进入数据库事务。该规则防止删除持有文件/表锁、Worker 持有 Job/表锁时循环等待。
同数据集仅串行化短状态事务，不串行化整个解析过程；不同数据集仍可并行。
过期结果不会修改当前数据；
其临时对象按无引用规则清理。GDB/SHP 每次领取使用独立的
`file-datasets/materialized/{fileId}/{jobId}-attempt-{attemptCount}[.gdb]` 前缀，
attemptCount 在持久化领取时递增。不同尝试与旧版 Job 级前缀互为兄弟目录，不嵌套；
准备失败、过期结果丢弃及重试开始的清理只作用于本次尝试，不能删除其他尝试已发布的对象。
已发布文件仍按保存的 materializedPrefix 读取，无需迁移既有来源。被强制终止而无法清理的
未发布尝试可能留下孤儿目录，由技术人员核对当前引用后处理，不在接管时删除其他尝试目录。

`READY/SCHEMA_READY` 逻辑表可以在模型列表通过“从文件数据集创建”复制 Schema。该操作只读取
管理数据库中的当前字段定义并创建独立的 `MANAGED + DRAFT` 模型，不读取或复制文件数据，不保存
来源关系，也不创建物理表。真正的数据写入由后续任务负责，任务关系和运行血缘才表达模型与文件
数据集之间的实际数据流。

## 4. 覆盖、删除与对象清理

- `REPLACE_ALL` 校验期间继续使用旧来源；成功事务删除旧来源并发布新来源。
- `REPLACE_SOURCE` 成功事务原地更新来源。
- 删除来源时，多来源表直接删除并压缩顺序；最后一个来源有下游引用时返回 `409`，无引用时
  删除表和字段。
- 所有格式都允许删除物理文件。该操作删除文件贡献的全部来源；仍有其他来源的表保留并压缩顺序，
  失去最后来源的表连同字段删除。只有将被删除的表检查 Canvas 引用。
- 事务提交后立即删除不再被任何当前来源或非终态 Job 引用的文件记录、原始对象和物化目录。
- 对象删除失败只记录告警，极少数孤儿对象由技术人员按日志人工处理。

Excel/GDB/GPKG 整文件替换采用破坏性语义：新文件提交后立即删除旧表和旧对象，再重新发现表。
后续解析失败不恢复旧文件，数据集允许变为空。

## 5. 预览和 Canvas

空间地图已接入“数据表 → 选择逻辑表 → 空间预览”，模型和文件共用[统一预览流程](model-management.md#spatial-preview-design)。地图独立读取完整几何准备副本，属性样本接口和 Canvas Manifest 不变。产品实测见[实施验收](../verification/spatial-preview-implementation-20261003.md)。

初始 GDB/SHP 缺失可识别 EPSG 时是一般失败清理规则的例外：逻辑表进入 `WAITING_CRS`，保存待确认的文件 UUID、图层键及原 WKT，保留安全物化后的文件；原解析任务记录失败原因，不创建生效来源。确认 CRS 后重新提交初始校验任务，沿用原文件，成功才进入 READY。期间不能用于 Canvas 运行；删除文件/数据集仍会清理待确认表。启动时同步旧 PostgreSQL 状态检查约束，不要求重建数据集。

预览按 `source_order` 读取当前来源，累计到请求 limit 后停止。任一来源不能安全预览时整表返回
`409`，不得返回部分结果。GDB、SHP、GeoJSON、GEOJSONL、GeoParquet 和 GPKG 的管理端预览只返回属性字段，不返回 Geometry 字段及其
坐标值；权威 Schema、Canvas 元数据和任务运行仍保留完整 Geometry 定义和值读取能力。

状态为 `READY` 或 `SCHEMA_READY` 且存在来源的表可以作为 Canvas 输入。运行准备直接快照权威
Schema、解析参数和有序 Object Key 列表，生成当前严格兼容的 Manifest v27：

- 表输入保存数据集 ID、表 ID、Schema 指纹和目标 Schema；
- 来源输入保存稳定来源 ID、文件 ID、格式、压缩、存储位置和来源键；
- 不包含数据或解析修订号，也不建立旧对象读取保护。

APPEND 不影响已经生成的 Manifest；覆盖、替换和删除会立即删除旧对象，已排队或运行任务允许
因对象不存在而失败。Task Engine 只接受 v27；多个来源使用同一 Schema 和 FAILFAST Reader 后执行
`unionByName`。

## 6. API 与管理端

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/v1/file-datasets/{datasetId}/tables/{tableId}/sources` | 查询当前来源 |
| `POST` | `/api/v1/file-datasets/{datasetId}/tables/{tableId}/actions/append` | 追加单文件 |
| `POST` | `/api/v1/file-datasets/{datasetId}/tables/{tableId}/actions/replace-data` | 全量覆盖 |
| `POST` | `/api/v1/file-datasets/{datasetId}/tables/{tableId}/sources/{sourceId}/actions/replace` | 替换来源 |
| `POST` | `/api/v1/file-datasets/{datasetId}/files/{fileId}/actions/delete` | 删除物理文件及其贡献的来源，按剩余来源决定是否删除逻辑表 |
| `POST` | `/api/v1/file-datasets/{datasetId}/tables/{tableId}/sources/{sourceId}/actions/delete` | 删除来源 |
| `POST` | `/api/v1/file-datasets/{datasetId}/tables/{tableId}/actions/update-spatial-reference` | 确认 EPSG；就绪表重新解析 Schema，WAITING_CRS 表继续后台初始校验 |
| `GET` | `/api/v1/file-datasets/{datasetId}/tables/{tableId}/spatial-preview`、`/spatial-preview/status`、`/spatial-preview/map` | 地图能力、准备状态、PNG |
| `POST` | `/api/v1/file-datasets/{datasetId}/tables/{tableId}/actions/prepare-spatial-preview` | 准备/强制刷新地图，202；权限沿用 filedataset.view |

装载接口返回 `202 Accepted` 和 `jobId/file/table`，校验成功前不存在来源记录。初始上传响应增加
`jobIds`。表响应使用 `sourceCount/totalRowCount/currentLoadJobId/previewSupported`。

表级 `actions/parse`、来源 `actions/retry` 和文件 `actions/prepare` 不再提供。具体失败信息可在数据集详情的“最近解析”和“解析记录”中查看，也保留全局解析队列入口。详情复用现有队列查询，固定附加 `fileDatasetId` 条件，支持状态筛选、分页及完整错误展开；只展示系统保留期内任务，自动尝试次数不等同于逐次重试日志。列表可直接打开上传窗口，复用文件页上传逻辑并在提交前重新核对单文件限制。来源页签只展示当前来源及下载、替换和删除操作；GPKG 来源只能下载，替换和
删除必须从整文件操作发起。危险确认明确提示不可恢复、
旧对象立即删除以及旧 Canvas 任务可能失败。

空间参考确认只接受 `EPSG` 和正整数 code。管理端会重新读取全部当前来源并在成功后刷新表、Schema、
预览和 Canvas 元数据；文件 WKT 已明确声明不同 EPSG 时拒绝覆盖。
字段 Schema 重建在同一提交事务内先删除并 `flush` 旧字段，再插入新字段，避免 Hibernate
先插入后删除导致 `(tableId, fieldName)` 唯一约束冲突；失败仍整体回滚。此操作不重写来源数据、
不改变来源行数，也不会把完整总数改成重解析样本数。

## 7. 破坏性重建

旧结构不迁移。升级操作见
[文件数据表当前来源模型重建](../operations/file-dataset-table-source-rebuild.md)。部署时必须停止
Admin、解析 Worker、Task Engine 和 Dispatcher，清空 MinIO 的 `data-scalpel/file-datasets/`
前缀，再执行重建 SQL 并启动应用。MinIO Bucket 必须关闭版本管理和 Object Lock。

引用旧 `tableId` 的 Canvas 节点需要重新选择。

## 2026-09 Canvas 文件引用投影

保存 Canvas 时，在同一事务替换 `task_canvas_file_reference` 中该任务引用的不同文件表 UUID，并标记投影对应的定义版本；空 UUID 字符串表示草稿尚未选择资源，不产生引用。删除任务时同步删除投影。文件表删除/替换及解析参数保护查询目标 UUID 的投影，不扫描解析全部 Canvas JSON。

启动时按每批 100 个任务 ID 修复旧定义投影，每个定义单独加锁提交。无法解析的定义保留明确索引错误；未知或版本不匹配的投影仍阻止破坏性文件操作，修复并保存定义后解除。此保守处理用于无法证明引用完整性的情形，不将合法空选项当作损坏定义。
