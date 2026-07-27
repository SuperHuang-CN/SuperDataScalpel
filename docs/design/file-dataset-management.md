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

数据集先按类型创建并保存共享解析参数，再上传文件。CSV、TSV、TXT、JSON、JSONL、Parquet、
Avro 和 SHP 支持表级 `APPEND`、`REPLACE_ALL` 和 `REPLACE_SOURCE`，每次只上传一个文件。
Excel 和 GDB 只支持整文件上传或替换。

数据集级上传表示“创建新的逻辑表”，不会按名称自动追加。多个来源按顺序执行 `UNION ALL`，
不合并文件、不去重、不做 Upsert，也不支持 Schema 演进。

## 2. 数据模型

### 2.1 `ds_file_dataset`

保存名称、目录、类型、描述和共享解析参数。数据集存在文件、逻辑表或非终态解析任务后，
`parsingOptionsLocked=true`，此时只能修改名称、目录和描述。数据集重新清空后可再次修改解析参数。

### 2.2 `ds_file_dataset_file`

保存当前文件或临时待校验文件的原始文件名、格式、压缩方式、对象 Key、存储形态和物化信息。
状态只有：

- `PREPARING`：SHP/GDB 归档正在安全检查和物化；
- `READY`：对象可供逻辑表校验或当前来源读取。

普通格式直接使用原始对象。SHP/GDB 同时保存原归档对象和已发布物化前缀。准备或校验最终失败
时删除临时文件记录、原始对象和物化目录，不保留失败文件。

### 2.3 `ds_file_dataset_table`

逻辑表保存稳定 ID、code、名称、权威 Schema 摘要和当前解析状态：

- `parse_status`：`QUEUED/PARSING/READY/SCHEMA_READY`；
- `current_load_job_id`：当前唯一装载任务，非空时拒绝第二个装载；
- `parsed_metadata`：当前来源组合的预览摘要和来源元数据。

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

`ds_file_dataset_parse_job` 是可重试的执行历史，不是业务版本。任务类型为：

- `FILE_PREPARATION`：SHP/GDB 归档检查、物化和表发现；
- `TABLE_SOURCE_VALIDATE`：执行 `INITIAL/APPEND/REPLACE_ALL/REPLACE_SOURCE` 的完整校验。

Job 保存数据集、表、文件名称快照，以及可空的 `load_mode/target_source_id/source_name/
source_key`。队列状态为 `QUEUED/RUNNING/SUCCEEDED/FAILED/CANCELLED`，并保留领取租约、
心跳、自动重试和有界历史清理。

## 3. 校验与提交

初始来源完整扫描并建立字段、总行数、最多 1000 条预览样本和来源元数据。CSV、TSV、TXT、
JSON 和 JSONL 必须逐条解码；Parquet 和 Avro 校验内置 Schema；SHP 还比较 Shape 类型、Z/M
维度、Geometry 字段和规范化 PRJ WKT。

后续来源固定比较字段数量、名称、顺序、完整 `PlatformTypeDefinition` 和 nullable。任一不一致
都使 Job 失败并清理临时数据，不修改当前来源。

异步提交只依赖 Job ID 防止旧结果覆盖当前状态：

- 表结果必须满足 `table.currentLoadJobId == job.id`；
- 文件准备结果必须满足 `file.currentPreparationJobId == job.id`。

Worker 在事务外读取和校验，在短事务内锁定 Job、文件和表并提交。过期结果不会修改当前数据；
其临时对象按无引用规则清理。

## 4. 覆盖、删除与对象清理

- `REPLACE_ALL` 校验期间继续使用旧来源；成功事务删除旧来源并发布新来源。
- `REPLACE_SOURCE` 成功事务原地更新来源。
- 删除来源时，多来源表直接删除并压缩顺序；最后一个来源有下游引用时返回 `409`，无引用时
  删除表和字段。
- 事务提交后立即删除不再被任何当前来源或非终态 Job 引用的文件记录、原始对象和物化目录。
- 对象删除失败只记录告警，极少数孤儿对象由技术人员按日志人工处理。

Excel/GDB 整文件替换采用破坏性语义：新文件提交后立即删除旧表和旧对象，再重新发现表。
后续解析失败不恢复旧文件，数据集允许变为空。

## 5. 预览和 Canvas

预览按 `source_order` 读取当前来源，累计到请求 limit 后停止。任一来源不能安全预览时整表返回
`409`，不得返回部分结果。

状态为 `READY` 或 `SCHEMA_READY` 且存在来源的表可以作为 Canvas 输入。运行准备直接快照权威
Schema、解析参数和有序 Object Key 列表，生成 Manifest v6：

- 表输入保存数据集 ID、表 ID、Schema 指纹和目标 Schema；
- 来源输入保存稳定来源 ID、文件 ID、格式、压缩、存储位置和来源键；
- 不包含数据或解析修订号，也不建立旧对象读取保护。

APPEND 不影响已经生成的 Manifest；覆盖、替换和删除会立即删除旧对象，已排队或运行任务允许
因对象不存在而失败。Task Engine 严格只接受 v6，并对多个来源使用同一 Schema 和 FAILFAST
Reader 后执行 `unionByName`。

## 6. API 与管理端

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/v1/file-datasets/{datasetId}/tables/{tableId}/sources` | 查询当前来源 |
| `POST` | `/api/v1/file-datasets/{datasetId}/tables/{tableId}/actions/append` | 追加单文件 |
| `POST` | `/api/v1/file-datasets/{datasetId}/tables/{tableId}/actions/replace-data` | 全量覆盖 |
| `POST` | `/api/v1/file-datasets/{datasetId}/tables/{tableId}/sources/{sourceId}/actions/replace` | 替换来源 |
| `POST` | `/api/v1/file-datasets/{datasetId}/tables/{tableId}/sources/{sourceId}/actions/delete` | 删除来源 |

装载接口返回 `202 Accepted` 和 `jobId/file/table`，校验成功前不存在来源记录。初始上传响应增加
`jobIds`。表响应使用 `sourceCount/totalRowCount/currentLoadJobId/previewSupported`。

表级 `actions/parse`、来源 `actions/retry` 和文件 `actions/prepare` 不再提供。具体失败信息统一
在解析队列抽屉查看。来源页签只展示当前来源及下载、替换和删除操作；危险确认明确提示不可恢复、
旧对象立即删除以及旧 Canvas 任务可能失败。

## 7. 破坏性重建

旧结构不迁移。升级操作见
[文件数据表当前来源模型重建](../operations/file-dataset-table-source-rebuild.md)。部署时必须停止
Admin、解析 Worker、Task Engine 和 Dispatcher，清空 MinIO 的 `data-scalpel/file-datasets/`
前缀，再执行重建 SQL 并启动应用。MinIO Bucket 必须关闭版本管理和 Object Lock。

引用旧 `tableId` 的 Canvas 节点需要重新选择。
