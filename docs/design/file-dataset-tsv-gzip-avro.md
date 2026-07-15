# TSV、GZIP 与 Avro 文件数据集开发设计

> 状态：已实施。本文保留本批次的设计决策、实现边界和验收依据。

## 1. 目标与范围

本批次在现有 CSV、TXT、JSON、JSONL、XLS、XLSX、Parquet 文件数据集能力上增加：

- TSV：作为独立的业务格式，复用分隔文本解析能力。
- GZIP：作为文件内容的压缩属性，不作为数据格式。
- Avro：支持 Apache Avro Object Container File（`.avro`）的字段提取、抽样解析和预览。

本批次仍保持同步抽样解析：一次解析最多保留 1,000 条有效记录，预览最多返回 100 条。不引入后台任务、版本实体、新 Maven 模块、Flyway、外部 Schema Registry 或新的权限体系。

空间格式 SHP、GDB 不在本批次开发范围内。

## 2. 核心建模决策

文件数据集由两个正交维度描述：

```text
逻辑数据格式 format       = CSV | TSV | TXT | JSON | JSONL | XLS | XLSX | PARQUET | AVRO | ...
物理内容压缩 compression  = NONE | GZIP
```

例如：

| 文件名 | format | compression |
| --- | --- | --- |
| `orders.csv` | `CSV` | `NONE` |
| `orders.csv.gz` | `CSV` | `GZIP` |
| `access.tsv.gz` | `TSV` | `GZIP` |
| `events.avro` | `AVRO` | `NONE` |

这样设计的原因是压缩不决定行列结构，也不决定解析参数。解析过程固定为：

```text
S3 原始对象 -> 可选 GZIP 解压层 -> 对应格式解析器 -> 字段与抽样记录
```

TSV 虽然在技术上是分隔文本，但保留独立 `format`，便于上传识别、列表筛选、默认参数、前端展示和后续 Spark 输入选择。

## 3. 第一版能力边界

### 3.1 GZIP 允许的组合

第一版仅支持下列流式文本格式使用外层 GZIP：

| format | NONE | GZIP |
| --- | --- | --- |
| CSV | 支持 | 支持 |
| TSV | 支持 | 支持 |
| TXT | 支持 | 支持 |
| JSONL | 支持 | 支持 |
| JSON | 支持 | 暂不支持 |
| XLS/XLSX | 支持 | 不支持 |
| PARQUET | 支持 | 不支持 |
| AVRO | 支持 | 不支持 |
| SHP/GDB/OTHER | 保持现状 | 不支持 |

JSON 暂不支持 GZIP，是因为当前 JSON 解析语义允许数组、对象和 JSON Pointer，不能稳定保证只消费少量解压数据。Avro、Parquet 等格式已经有容器级压缩机制，不再叠加外层 GZIP。

支持的双扩展名为：

- `.csv.gz`
- `.tsv.gz`
- `.txt.gz`
- `.jsonl.gz`
- `.ndjson.gz`

`.gz` 不能单独确定逻辑格式；例如 `data.gz` 应拒绝创建并提示需要完整双扩展名。

### 3.2 Avro 边界

第一版只支持 Avro Object Container File：

- 文件扩展名为 `.avro`。
- 通过文件头 magic bytes 校验容器文件，不只信任扩展名。
- Schema 直接读取 Avro 文件头，不接入 Schema Registry。
- 顶层 Schema 必须为 `record`。
- 不支持裸 Avro 二进制、Single-object encoding、`.avsc` Schema 文件以及依赖外部 Schema 才能解码的数据。
- 不支持 `.avro.gz`；Avro 文件内部 codec 负责压缩。
- 遇到递归 Schema 时返回明确的“不支持递归 Avro Schema 预览”错误，不在第一版进行无限递归或随意截断。

Avro OCF 自带 Schema、数据块、同步标记和 codec 信息，适合从文件自身提取字段。Avro 1.12 规范要求支持 `null`、`deflate`，并定义了 `bzip2`、`snappy`、`xz`、`zstandard` 等可选 codec。本批次目标支持 `null`、`deflate`、`snappy`、`bzip2`、`xz` 和 `zstandard`；缺失 codec 依赖时必须在构建阶段补齐并通过对应测试文件验证。

## 4. 领域模型与数据库

### 4.1 枚举调整

修改 `FileDatasetFormat`：

```java
CSV, TSV, TXT, JSON, JSONL, XLS, XLSX, PARQUET, AVRO, SHP, GDB, OTHER
```

新增 `FileDatasetCompression`：

```java
NONE, GZIP
```

`FileDataset` 新增字段：

```java
@Enumerated(EnumType.STRING)
@Column(nullable = false, length = 16)
private FileDatasetCompression compression;
```

约束：

- 新建和替换内容时由服务端检测并写入，客户端不能指定。
- 普通文件写入 `NONE`，不能使用 `null` 表示未压缩。
- `sizeBytes` 始终表示 S3 中原始对象的物理字节数，即 GZIP 文件的压缩后大小。
- 第一版不计算、不持久化解压后大小和压缩比。
- 更新数据集元信息时不能单独修改 `compression`，因为它是实际文件内容的属性。

第一版继续使用 Hibernate `ddl-auto=update`，不引入 Flyway。若本地已有数据导致非空列无法自动建立，实施时先为存量数据回填 `NONE`，或重建仅用于开发的本地数据库；不得把临时兼容逻辑长期留在领域模型中。

## 5. 文件识别与校验

新增职责集中的文件名/内容识别类，例如 `FileDatasetFileDescriptor`，由 Service 调用，不新增框架或工厂层。它负责：

1. 规范化原始文件名。
2. 先识别末尾 `.gz`，再从基础文件名识别逻辑格式。
3. 读取少量文件头验证 magic bytes。
4. 校验 `format + compression` 是否为允许组合。

服务端是最终权威，前端推断只用于改善交互。

### 5.1 GZIP 校验

- 文件名以 `.gz` 结尾且文件头不是 `1F 8B`：请求失败，提示“文件扩展名为 GZIP，但内容不是有效 GZIP”。
- 文件头为 `1F 8B` 但文件名不以 `.gz` 结尾：请求失败，提示补全 `.gz`，避免对象名与 Spark codec 识别不一致。
- 解压流创建失败、CRC 校验失败或尾部损坏：解析状态写入 `FAILED`，保留可读错误摘要。
- 对 GZIP 解压后内容仍执行原格式解析和参数校验。

### 5.2 Avro 校验

Avro OCF 文件头 magic bytes 为 ASCII `Obj` 加版本字节 `1`。上传时校验扩展名和 magic；真实 Schema、codec 和数据块校验在执行解析时完成。

### 5.3 对象 Key

对象 Key 必须保留末尾扩展名。`orders.csv.gz` 的对象 Key 末尾应为 `.gz`，以便 Spark/Hadoop 根据文件名识别 GZIP codec。当前对象 Key 生成逻辑若只保留最后一个扩展名可继续使用，但需要增加测试锁定这一行为。

## 6. API 契约

### 6.1 文件数据集响应

`FileDatasetResponse` 和 `FileDatasetParsingResponse` 增加：

```json
{
  "format": "CSV",
  "compression": "GZIP",
  "originalFileName": "orders.csv.gz"
}
```

这是向后兼容的新增响应字段。创建、替换和更新请求均不增加可写 `compression` 字段。

### 6.2 TSV 解析参数

对外 API 暂时复用已经稳定的 CSV 解析参数 DTO，不为 TSV 复制一套字段相同的 Web DTO：

```json
{
  "options": {
    "kind": "CSV",
    "charset": "UTF-8",
    "fieldDelimiter": "\t",
    "recordDelimiter": "AUTO",
    "quoteCharacter": "\"",
    "escapeCharacter": "\\",
    "headerPresent": true
  }
}
```

后台规则：

- `format=CSV` 与 `format=TSV` 都接受 `kind=CSV`。
- TSV 的 `fieldDelimiter` 必须是单个制表符 `\t`；不允许通过配置把 TSV 改成逗号或其他分隔文本。
- TSV 默认值为 UTF-8、制表符、记录分隔符 AUTO、双引号、反斜线转义、首行表头。

`kind=CSV` 是现有外部契约名称；解析包内部类型应改成更准确的 `DelimitedText`，避免解析内核继续以 CSV 命名承载 TSV。

### 6.3 Avro 解析参数

新增无参数配置类型：

```json
{
  "options": {
    "kind": "AVRO"
  }
}
```

对应修改：

- `FileDatasetParsingOptionsKind` 增加 `AVRO`。
- Request/Response 的多态解析配置增加明确的 `Avro` DTO。
- 解析包内部 `FileDatasetParsingConfiguration` 增加 `Avro` 类型。
- 不接受用户覆盖文件内 Schema 或 codec。

### 6.4 下载行为

下载接口返回上传时的原始对象。对于 `.gz` 文件：

- 不由后台自动解压。
- 下载文件名保留 `.gz`。
- 不设置 HTTP `Content-Encoding: gzip`，避免浏览器透明解压并改变文件语义。
- Content-Type 优先使用上传值；检测为 GZIP 时可规范为 `application/gzip`。

## 7. 解析器设计

### 7.1 TSV：复用分隔文本解析器

为避免复制 CSV 解析逻辑，现有 `CsvFileDatasetParser` 与内部 `FileDatasetParsingConfiguration.Csv` 同时接受 CSV 和 TSV；它们的名称保留是为了与已实现的 CSV 配置保持一致，行为已经是分隔文本解析。后续若有独立的内部重构批次，可改名为 `DelimitedText`，但不改变对外 `kind=CSV` 契约。

解析器根据领域格式执行默认值和约束。Web 层 DTO 名称保持 CSV，避免无收益的 API 破坏。

字段推断、空值判断、表头去重、样本限制等行为与 CSV 完全一致。

### 7.2 GZIP：放在解析器外层

不创建 `GzipFileDatasetParser`。`FileDatasetService` 在选择格式解析器之前完成解压包装：

```text
FileObjectStorage.open
  -> raw InputStream
  -> GZIPInputStream（compression=GZIP 时）
  -> 解压字节上限流
  -> DelimitedText/Text/JsonLines parser
```

这样每个格式解析器只关心解压后的内容，未来增加其他压缩方式时也不需要复制业务解析器。

增加配置：

| 配置项 | 环境变量 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `data-scalpel.file-parsing.max-sampled-uncompressed-size` | `DATASCALPEL_FILE_PARSING_MAX_SAMPLED_UNCOMPRESSED_SIZE` | `64MB` | 流式抽样最多允许消费的解压后字节数，防止压缩炸弹和异常超长记录。 |

超过上限时本次解析失败，并提示调整文件或配置。该上限统计解压后、字符解码前的字节，不影响已存在的随机访问格式落盘大小上限。

### 7.3 Avro 解析器

新增 `AvroFileDatasetParser`：

- `inputMode()` 返回 `STREAM`。
- 使用 `DataFileStream<GenericRecord>` 和 `GenericDatumReader<GenericRecord>`。
- 从文件 Schema 提取字段，因此零数据行的 Avro 文件仍可以得到字段列表。
- 最多读取 `recordLimit + 1` 条记录，用额外一条判断 `truncated`。
- 字段顺序保持 Avro record schema 声明顺序。
- 解析器不得关闭由 Service 传入的底层 S3 流。

建议把 Avro 值转换放在同一解析包中的小型辅助类，例如 `AvroValueConverter`；不要新增通用映射框架。

### 7.4 Avro 类型映射

| Avro Schema | 文件数据集字段类型 | 样本值 |
| --- | --- | --- |
| `boolean` | `BOOLEAN` | Boolean |
| `int`、`long` | `INTEGER` | Long |
| `float`、`double` | `DECIMAL` | BigDecimal 或稳定数值表示 |
| `string`、`enum`、logical `uuid` | `STRING` | String |
| `bytes`、`fixed` | `BINARY` | Base64 String |
| logical `decimal` | `DECIMAL` | BigDecimal |
| logical `date` | `DATE` | ISO-8601 date String |
| logical `time-millis`、`time-micros` | `TIME` | ISO-8601 time String |
| logical `timestamp-millis`、`timestamp-micros` | `DATETIME` | ISO-8601 UTC datetime String |
| logical `local-timestamp-*` | `DATETIME` | 无时区 ISO-8601 datetime String |
| `array` | `ARRAY` | 紧凑 JSON 数组 String |
| `map`、嵌套 `record` | `JSON` | 紧凑 JSON 对象 String |

Union 规则：

- `["null", T]` 映射为 `T`，字段 `nullable=true`。
- 多个兼容数值分支取共同数值类型。
- 其他多分支 Union 映射为 `JSON`，样本值使用紧凑 JSON 表示。
- Schema 不含 `null` 时 `nullable=false`；样本中暂未出现值不能改变 Schema 定义的可空性。

转换注意事项：

- `Utf8` 显式转成 Java String。
- `ByteBuffer` 复制剩余字节后编码 Base64，不改变原缓冲区位置。
- `GenericFixed` 编码为 Base64。
- 嵌套 record、map、array 递归转换为 Jackson 可序列化的 Java 值后再输出紧凑 JSON。
- 使用已访问 Schema 的集合检测递归，不依赖任意深度截断。

## 8. S3 流生命周期

这是 GZIP 抽样能否真正避免下载整个对象的关键改造。

当前存储接口只暴露普通 `InputStream`。AWS SDK 在 Apache HTTP Client 下关闭 `ResponseInputStream` 时可能先读取剩余响应体以复用连接；因此只读取 1,000 行后调用普通 `close()`，仍可能把整个 S3 对象下载完。

本批次调整 `FileObjectStorage.FileObjectContent`，让它明确具备正常关闭和提前中止能力。可以将其改为实现 `AutoCloseable` 的类，至少包含：

```java
InputStream inputStream();
long contentLength();
String contentType();
void abort();
void close();
```

规则：

- S3 实现的 `abort()` 调用 AWS `ResponseInputStream.abort()`。
- 测试/内存实现的 `abort()` 可以关闭流并记录调用，不依赖 AWS 类型。
- 格式解析器不拥有输入流，不得关闭传入流。
- Service 是流生命周期的唯一所有者。
- `ParseResult` 的契约必须明确：`truncated=false` 表示解析器已经确认到达 EOF；`truncated=true` 表示达到抽样上限后仍存在数据。各流式解析器都要用测试证明该语义，不能只根据 `rows.size()` 猜测。
- 抽样结果 `truncated=true` 时，Service 中止原始 S3 响应，而不是普通关闭。
- 解析异常发生在 EOF 前时同样中止。
- 已消费到 EOF 时正常关闭。
- 需要完整落盘的 XLS/XLSX/Parquet 继续正常读完并关闭。

CSV、TXT、JSONL 等现有流式解析器必须同步审计，移除会连带关闭传入流的 `Reader`/包装流所有权。必要时使用 non-closing wrapper，但最终所有权规则必须由测试锁定。

第一版不使用 S3 Range GET 优化 GZIP。压缩字节偏移无法直接对应解压后的记录边界，流式读取后显式 `abort` 更简单可靠。

## 9. Spark 衔接

### 9.1 GZIP 文本

后续任务执行器通过文件数据集 ID 解析得到内部输入契约：

```json
{
  "uri": "s3a://datascalpel/data-scalpel/.../orders.csv.gz",
  "format": "CSV",
  "compression": "GZIP",
  "parsingOptions": {},
  "fields": []
}
```

Spark 直接读取保存在 S3 上的原始 `.gz` 对象，凭证和 S3A 参数由任务运行环境注入，不返回前端。解析得到的字段可用于构造显式 Schema，CSV/TSV 参数用于构造读取选项。

需要明确的性能边界：单个 GZIP 流通常不可切分，一个很大的 `.csv.gz`/`.tsv.gz` 不会因放到 S3 就自动获得文件内并行读取。推荐的数据分层方式是：

- 原始层保留用户上传的 GZIP 文件。
- 标准化或分析层由 Spark 转换为 Parquet。
- 大批量上游数据优先提供多个合理大小的 GZIP 对象，而不是单个超大对象。

### 9.2 Avro

Spark 使用 `.format("avro")` 读取 Avro OCF。Spark 的 Avro 数据源是独立模块，任务运行环境必须带与 Spark 版本匹配的 `spark-avro` 包；后台 Maven 中加入 Apache Avro Java 库并不能替代 Spark 运行时依赖。

第一版让 Spark 使用文件内 Schema。平台解析保存的字段用于页面展示、血缘和输入校验，不向 Spark 传入用户覆盖的 `avroSchema`，避免不完整地实现 Schema evolution。

## 10. 后端实施清单

按以下顺序实施，每一步完成有针对性的测试后再进入下一步：

1. **领域模型与响应契约**
   - 增加 TSV、AVRO、GZIP 枚举与实体字段。
   - 响应 DTO 增加 `compression`。
   - 完成双扩展名、magic bytes 和允许组合校验。
2. **流所有权与 S3 中止**
   - 改造 `FileObjectStorage.FileObjectContent`。
   - S3 Adapter 映射 `ResponseInputStream.abort()`。
   - 修正现有流式解析器关闭底层流的问题。
3. **GZIP 解压层**
   - 在 Service 中增加解压包装和解压后字节限制。
   - 解析失败、抽样截断和正常 EOF 分别处理流生命周期。
4. **TSV**
   - 重命名内部 CSV 解析器/配置为分隔文本语义。
   - 支持 TSV 默认参数和制表符强校验。
5. **Avro**
   - 根 POM 统一管理 Apache Avro 版本，目标使用 1.12.1。
   - `data-scalpel-business` 增加 Avro 与必要 codec 依赖。
   - 实现 Avro Parser、Schema 映射和值转换。
6. **配置与文档**
   - 增加解压抽样上限配置。
   - 更新文件数据集主设计和运行配置说明。

主要修改位置：

- `data-scalpel-business/.../filedataset/domain`
- `data-scalpel-business/.../filedataset/service`
- `data-scalpel-business/.../filedataset/service/parse`
- `data-scalpel-business/.../filedataset/storage`
- `data-scalpel-business/.../filedataset/web/request`
- `data-scalpel-business/.../filedataset/web/response`
- 根 `pom.xml` 与 `data-scalpel-business/pom.xml`
- `data-scalpel-admin/src/main/resources/application.yml`

不新增 Maven 模块，不把业务实现放到 `data-scalpel-admin`。

## 11. 前端实施清单

1. `FileDatasetFormat` 增加 `TSV`、`AVRO`，模型增加 `FileDatasetCompression`。
2. 文件名推断支持双扩展名，先剥离 `.gz` 再识别基础格式。
3. 创建页显示服务端最终识别的压缩方式；不提供手工修改压缩类型的表单项。
4. 列表和详情显示 `GZIP` 标签，格式仍显示 CSV/TSV/TXT/JSONL。
5. TSV 使用 CSV 参数表单，但字段分隔符固定为制表符并禁用编辑。
6. Avro 解析配置页显示“Schema 将从 Avro 文件读取”，不展示无效参数。
7. 替换文件时使用同一套文件名推断；最终以后台响应为准。
8. 格式筛选增加 TSV、AVRO；压缩方式可通过已有标量 Search DSL 过滤，不新增专用查询机制。

前端修改后运行 `pnpm check`。本批次不涉及 X6 Canvas。

## 12. 测试设计

### 12.1 后端

最低覆盖：

- 普通 TSV：默认参数、中文、空列、重复表头、类型推断和预览。
- TSV 非制表符配置被拒绝。
- `.csv.gz`、`.tsv.gz`、`.txt.gz`、`.jsonl.gz` 成功解析。
- `.gz` 无基础扩展名、扩展名与 magic 不一致、CRC 损坏、截断文件失败。
- JSON/XLS/XLSX/Parquet/Avro 外层 GZIP 被拒绝。
- `sizeBytes` 保持压缩后物理大小，下载仍为原始 GZIP 字节。
- 抽样截断后调用 storage `abort()`，完整消费后调用正常 `close()`。
- 超过解压抽样上限失败，且不继续读取 S3 对象。
- Avro 零记录文件仍提取 Schema。
- Avro 基础类型、logical types、nullable union、多分支 union、嵌套 record/array/map、bytes/fixed。
- Avro `null`、`deflate`、`snappy`、`bzip2`、`xz`、`zstandard` codec 文件。
- Avro 非 record 顶层、递归 Schema、错误 magic、损坏数据块返回受控错误。
- 替换内容后 format/compression 正确更新，旧解析状态和字段按现有规则重置。
- API 响应包含 `compression`，旧请求不需要增加该字段。

Avro fixture 优先由测试代码使用官方 Avro API生成；codec fixture 必须真正写入并读回，不能只断言依赖存在。

### 12.2 前端

- `.tsv`、`.avro` 和所有支持的双扩展名推断。
- `.avro.gz` 和只有 `.gz` 的文件显示不支持。
- TSV 表单始终提交 `kind=CSV` 和 `fieldDelimiter="\t"`。
- Avro 提交 `kind=AVRO`。
- 列表、详情和替换后的 compression 展示。

### 12.3 完整验证

```bash
./mvnw verify
cd data-scalpel-ui && pnpm check
```

若 codec 依赖下载或外部 Maven 仓库不可用，应分别说明已经通过的本地测试和未完成的验证，不得绕过工程 Maven Wrapper 或 `settings-superhuang.xml`。

## 13. 验收标准

满足以下条件才算本批次完成：

- 用户可创建、查看、配置、解析、预览、替换和下载 TSV、GZIP 文本、Avro 文件数据集。
- GZIP 在领域和 API 中表现为压缩属性，不出现 `format=GZIP`。
- S3 抽样解析在达到记录上限时显式中止响应，不继续下载对象剩余部分。
- 不支持的格式/压缩组合在上传或替换时被明确拒绝。
- Avro 字段来自文件 Schema，零记录文件也可解析字段。
- Avro 常用内部 codec 有真实测试覆盖。
- 原始对象 Key 保留 `.gz`/`.avro` 后缀，能够形成 Spark 可读取的 `s3a://` URI。
- 文档明确单个 GZIP 文件不可切分的性能限制，以及后续转 Parquet 的推荐路径。
- 后端完整 `verify` 和前端 `pnpm check` 通过。

## 14. 参考资料

- [Apache Avro 1.12.0 Specification](https://avro.apache.org/docs/1.12.0/specification/)
- [Apache Avro 1.12.1 Release](https://avro.apache.org/blog/releases/)
- [Spark Avro Data Source Guide](https://spark.apache.org/docs/latest/sql-data-sources-avro.html)
- [AWS SDK ResponseInputStream](https://sdk.amazonaws.com/java/api/latest/software/amazon/awssdk/core/ResponseInputStream.html)
- [Hadoop FileInputFormat](https://hadoop.apache.org/docs/current/api/org/apache/hadoop/mapreduce/lib/input/FileInputFormat.html)
