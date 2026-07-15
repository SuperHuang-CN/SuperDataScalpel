# 文件数据集管理

## 范围

文件数据集是平台托管的原始文件资产，不是 JDBC 数据源的一种变体。当前支持浏览器上传到系统私有的 S3 兼容对象存储，并提供业务 CRUD、内容替换、下载、格式专属解析参数、抽样解析和预览；不读取外部 S3/SFTP/FTP，不创建版本历史，也不接入模型或任务。

文件格式枚举包含 `CSV`、`TSV`、`TXT`、`JSON`、`JSONL`、`XLS`、`XLSX`、`PARQUET`、`AVRO`、`SHP`、`GDB` 和 `OTHER`。文件内容另有 `compression` 属性，当前为 `NONE` 或 `GZIP`；GZIP 不是一种文件格式。SHP、GDB 使用 ZIP 上传，Parquet 与 Avro 仅接受单文件 `.parquet`、`.avro`。

## 数据模型

表：`ds_file_dataset`

| 字段 | 含义 |
| --- | --- |
| `id`、`created_at`、`updated_at` | 继承 `BaseEntity` 的 UUID 和审计时间 |
| `directory_id` | 可选的 `FILE_DATASET` 目录 UUID |
| `name`、`description` | 业务展示信息 |
| `format` | 调用方声明的文件格式 |
| `original_file_name` | 清理客户端路径后的原始文件名 |
| `object_key` | 系统私有 S3 根前缀下的相对 Object Key，不通过 API 返回 |
| `content_type`、`size_bytes`、`storage_etag` | 上传时记录的文件属性和对象存储标识 |
| `parse_status` | `UNPARSED`、`PARSING`、`READY`、`FAILED`；创建、替换内容、修改格式或更新解析参数后回到 `UNPARSED` |
| `parsing_options` | 已校验的格式专属解析参数 JSON；替换内容或修改格式后清空 |
| `parsed_metadata`、`parse_error` | 最近一次成功解析的抽样摘要，以及最近一次失败原因 |

表：`ds_file_dataset_field`

每次成功解析都会替换该文件数据集已有的字段元数据，字段包括名称、顺序、逻辑类型和可空性。文件数据集仅保存 `file_dataset_id` UUID 标量引用，不建立 JPA 实体关联。

文件内容替换不覆盖原 Object Key：先写入新的随机对象，数据库事务中切换 Key，提交后再尽力删除旧对象。创建的数据库保存失败时会补偿删除新对象；提交后的对象删除失败仅记录日志，作为可人工清理的孤儿对象。

## 接口与权限

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| `GET` | `/api/v1/file-datasets` | `filedataset.view` | 统一 Search DSL 分页查询 |
| `GET` | `/api/v1/file-datasets/{id}` | `filedataset.view` | 查询详情 |
| `GET` | `/api/v1/file-datasets/{id}/parsing` | `filedataset.view` | 查询当前解析参数和状态 |
| `GET` | `/api/v1/file-datasets/{id}/preview?limit=50` | `filedataset.view` | 预览已解析文件的最多 100 条记录 |
| `POST` | `/api/v1/file-datasets` | `filedataset.create` | `multipart/form-data` 上传；`request` 是 JSON，`file` 是原始文件 |
| `POST` | `/api/v1/file-datasets/{id}/actions/update` | `filedataset.update` | 修改名称、目录、格式和描述 |
| `POST` | `/api/v1/file-datasets/{id}/actions/configure-parsing` | `filedataset.update` | 校验并保存解析参数，同时清除旧的字段元数据 |
| `POST` | `/api/v1/file-datasets/{id}/actions/parse` | `filedataset.update` | 按已保存参数执行同步抽样解析 |
| `POST` | `/api/v1/file-datasets/{id}/actions/replace-content` | `filedataset.update` | `multipart/form-data` 替换内容并声明格式 |
| `GET` | `/api/v1/file-datasets/{id}/content` | `filedataset.view` | 后端从私有对象存储流式下载 |
| `POST` | `/api/v1/file-datasets/{id}/actions/delete` | `filedataset.delete` | 删除记录，提交后删除对象 |

上传接口不接受 Object Key、Bucket、Endpoint 或凭证。下载通过业务 API 代理，以免浏览器获得对象存储凭证或内部 Key。

## 解析参数交互

解析配置位于文件上传之后，是文件数据集自身的设置，不属于任务节点。配置完成后保持 `UNPARSED`；用户显式执行解析时，服务会从对象存储读取文件，临时置为 `PARSING`，最终写入 `READY` 或 `FAILED`。

| 文件格式 | 参数 |
| --- | --- |
| CSV | 编码、字段分隔符、记录分隔符、引号字符、转义字符、首行是否表头 |
| TSV | 与 CSV 相同；字段分隔符固定为制表符 |
| TXT | 编码、记录分隔符 |
| JSON | 编码、可选 JSON Pointer 根路径 |
| JSONL | 编码、记录分隔符 |
| XLS/XLSX | 工作表名称、表头行、数据起始行 |
| Parquet | 无额外参数，保留显式的 Parquet 配置类型 |
| Avro | 无额外参数，读取 Object Container File 内的 Schema 与 codec |
| SHP | 编码、可选图层名称 |
| GDB | 可选图层名称 |
| OTHER | 第一版不支持解析配置 |

请求使用带 `kind` 判别字段的明确 DTO。后台同时校验参数类型与数据集格式、Java 支持的字符集、JSON Pointer 形式，以及 Excel 数据起始行必须位于表头之后。

当前真实解析范围是 CSV、TSV、TXT、JSON、JSONL、XLS、XLSX、Parquet 和 Avro：

- CSV 支持字段分隔符、首行表头、引号、转义和记录分隔符；空列会标为可空，文本样本会保守推断布尔、整数、小数、日期和日期时间类型。
- TSV 复用 CSV 的分隔文本解析能力，但服务端和前端均固定字段分隔符为制表符。
- TXT 将每条记录解析为一个 `value` 字段。
- JSON 支持可选 JSON Pointer，根节点可以是对象或数组；JSON 对象/数组字段保留为紧凑 JSON 文本，并标注为 `JSON`/`ARRAY` 类型。
- JSONL 按配置的记录分隔符读取，忽略空行，并逐条使用 JSON 规则解析。
- XLS/XLSX 以事件流读取选定工作表，不将整个工作簿加载到堆内存；表头行和数据起始行均从 0 开始计数。空白表头自动命名为 `column_N`，重复表头自动加后缀。数值、布尔、日期、时间、日期时间与可用的公式缓存值会保留为相应逻辑类型；不执行公式计算，不支持加密工作簿。
- Parquet 从文件 schema 直接取得字段名称、可空性和逻辑类型，并读取至多 1,000 行。`DATE`、`TIME`、`TIMESTAMP`、`DECIMAL`、字符串和数值类型保持为对应预览值；二进制字段以 Base64 展示，嵌套 `LIST` 以 JSON 数组文本展示，`MAP`/结构体以 JSON 对象文本展示。不支持加密 Parquet 文件。
- Avro 读取 Object Container File 内的顶层 `record` Schema；支持 `null`、`deflate`、`snappy`、`bzip2`、`xz`、`zstandard` 内部 codec。日期、时间、时间戳、decimal、数组、Map、嵌套 record、nullable union 与多分支 union 分别映射为对应的逻辑字段类型和预览值；不支持递归 Schema、裸 Avro 二进制和外层 `.avro.gz`。

GZIP 外层压缩只支持 CSV、TSV、TXT、JSONL：文件名必须保留 `.csv.gz`、`.tsv.gz`、`.txt.gz`、`.jsonl.gz` 或 `.ndjson.gz` 双扩展名，并校验 `1F 8B` 文件头。解析时服务从 S3 流式读取、先解压再抽样；达到抽样上限或发生异常时显式中止 S3 响应，避免继续下载对象剩余内容。单次流式抽样的解压后读取上限由以下配置控制。

一次解析最多读取 1,000 条有效记录，用于推断字段和保存抽样摘要。预览会从原文件重新读取最多 100 条，因此不会把样本行写入数据库；预览字段始终使用最近一次成功解析所保存的字段契约。SHP、GDB 仍可保存配置，但暂不能执行真实解析。

## 解析临时文件

CSV、TSV、TXT、JSON、JSONL 与 Avro 直接消费对象存储的输入流；其中 GZIP 文本先经过解压层再交给对应解析器。XLS、XLSX 与 Parquet 需要可随机读取的文件，因此服务先将对象存储流写入应用服务器的受控临时目录，再将本地文件路径交给对应解析器；解析结束后立即删除。这样解析内核不依赖 S3 SDK，也不会把完整文件放入 JVM 堆内存。

| 配置项 | 环境变量 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `data-scalpel.file-parsing.temporary-directory` | `DATASCALPEL_FILE_PARSING_TEMPORARY_DIRECTORY` | `${java.io.tmpdir}/data-scalpel/file-parsing` | 临时文件所在目录；生产环境应使用有足够空间且受操作系统权限保护的本地磁盘。 |
| `data-scalpel.file-parsing.max-materialized-size` | `DATASCALPEL_FILE_PARSING_MAX_MATERIALIZED_SIZE` | `1GB` | 单个需要落盘解析的文件最大大小；超过上限时解析失败，不会继续写入磁盘。 |
| `data-scalpel.file-parsing.orphan-retention` | `DATASCALPEL_FILE_PARSING_ORPHAN_RETENTION` | `24h` | 进程异常留下的同前缀临时文件的保留期限；每次新建临时文件时顺带清理到期文件。 |
| `data-scalpel.file-parsing.max-sampled-uncompressed-size` | `DATASCALPEL_FILE_PARSING_MAX_SAMPLED_UNCOMPRESSED_SIZE` | `64MB` | 流式抽样最多允许读取的解压后字节数，避免压缩炸弹和异常超长记录。 |

## S3 运行配置

系统文件存储与“数据源管理”中可登记的外部 S3 数据源互不依赖。运行时通过环境变量或不提交的本地 Profile 配置：

```bash
export DATASCALPEL_FILE_STORAGE_ENDPOINT="http://home.superhuang.cn:9000"
export DATASCALPEL_FILE_STORAGE_BUCKET="datascalpel"
export DATASCALPEL_FILE_STORAGE_ACCESS_KEY="<access-key>"
export DATASCALPEL_FILE_STORAGE_SECRET_KEY="<secret-key>"
```

可选项包括 `DATASCALPEL_FILE_STORAGE_REGION`（默认 `us-east-1`）、`DATASCALPEL_FILE_STORAGE_ROOT_PREFIX`（默认 `data-scalpel`）和 `DATASCALPEL_FILE_STORAGE_PATH_STYLE_ACCESS`（默认 `true`）。未配置 Endpoint 时应用仍可启动，但所有文件数据集内容操作会返回“文件对象存储尚未配置”。

对象使用 AWS SDK for Java 2.x 的 S3 Client 访问。后续 Spark 任务只保存文件数据集 ID，由服务端解析为 `s3a://{bucket}/{root-prefix}/{object-key}` 并从运行环境注入 S3A Endpoint、Region、Path-style 和凭证。

## 后续阶段

SHP 和 GDB 的设计见 [空间文件解析设计](geospatial-file-dataset-parsing.md)，当前仅冻结设计、不开发，统一放到最后的空间数据阶段。届时先确认 GDAL/OGR 是否作为部署运行时前置条件，再按设计顺序实施。当前阶段继续推进非空间文件数据集能力；同步抽样解析是否迁移为可恢复的后台任务，待真实文件规模和任务执行需求出现后再决定。

[TSV、GZIP 与 Avro 文件数据集开发设计](file-dataset-tsv-gzip-avro.md)已实施；该文档保留业务建模、API、S3 流式抽样、Avro 类型映射和 Spark 衔接决策，作为后续维护依据。
