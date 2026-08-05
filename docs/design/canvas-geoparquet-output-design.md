# Canvas `FILE_OUTPUT` GeoParquet 输出设计

## 1. 状态与范围

- 状态：已开发。
- 协议版本：Canvas `1.25`。
- 节点：复用现有 `FILE_OUTPUT`，不增加 `GEOPARQUET_OUTPUT`。
- 执行模式：仅 `BATCH`，来源必须为 `BOUNDED`。
- 目标：已启用、连接类型为 S3、用途包含 `DISTRIBUTION` 的数据源。
- 规范：固定写出 GeoParquet `1.1.0`，Geometry 固定使用 `WKB` 编码。
- 物理形态：Spark 分布式目录数据集，允许多个 `part-*.parquet`，不生成单文件。
- 实现：复用 Apache Sedona 1.9.0 内置的 `geoparquet` Writer，不增加生产依赖。

本能力不修改后台 HTTP API、不新增 Maven 模块，也不改变现有 `PARQUET`、`SHAPEFILE`
格式的协议和运行行为。正式 Canvas 当前版本已升级为 `1.25`。

## 2. 稳定 Canvas 契约

`FileOutputConfiguration` 继续复用：

```text
sourceTableName + dataSourceId + targetPath + conflictPolicy + formatOptions
```

`FileOutputFormatOptions` 增加以下判别子类型：

```ts
type GeoParquetCompressionCodec = 'SNAPPY' | 'ZSTD';

type GeoParquetCoveringMode = 'NONE' | 'ROW_BBOX';

interface GeoParquetOutputFormatOptions {
  type: 'GEOPARQUET';
  geometryColumnName: string;
  compression: GeoParquetCompressionCodec;
  coveringMode: GeoParquetCoveringMode;
}
```

Java Contracts 增加对应 record 和 enum，并将 `GeoParquet` 加入
`FileOutputFormatOptions` 的 Jackson 子类型与 sealed permits。配置不保存 CRS、GeometryKind、
维度、PROJJSON、bbox 字段名或 Spark/Sedona 私有参数；这些内容均从编译时来源 Schema 推导。

新建配置默认值：

```text
compression = SNAPPY
coveringMode = ROW_BBOX
```

完整示例：

```json
{
  "sourceTableName": "district_orders",
  "dataSourceId": "901e8938-bc1d-4bfd-91ec-d26bca38e8f6",
  "targetPath": "exports/district-orders-geoparquet",
  "conflictPolicy": "FAIL_IF_EXISTS",
  "formatOptions": {
    "type": "GEOPARQUET",
    "geometryColumnName": "geom",
    "compression": "SNAPPY",
    "coveringMode": "ROW_BBOX"
  }
}
```

## 3. 协议兼容

- `FILE_OUTPUT` 节点本身继续从 Canvas `1.6` 可用。
- `GEOPARQUET` 格式从 Canvas `1.25` 开始可用。
- `schemaMinorVersion < 25` 携带 `formatOptions.type=GEOPARQUET` 时拒绝，并返回
  `FORMAT_OPTION_REQUIRES_SCHEMA_VERSION`。
- Canvas `1.0`～`1.24` 的已有定义继续兼容，既有 `PARQUET` 不自动升级成 `GEOPARQUET`。
- 新建、保存、Admin 返回、Manifest 和前端导出统一规范化为 `1.25`。
- 前端导入执行相同门槛；无法安全导入时保留当前画布。

## 4. 物理输出与目录语义

GeoParquet 保持 Spark 目录数据集形态：

```text
{targetPath}/
  part-00000-....snappy.parquet
  part-00001-....snappy.parquet
  ...
  _SUCCESS
```

选择 ZSTD 时 part 文件扩展名仍为 `.parquet`，实际压缩编码由 Parquet Footer 描述。文件数量由
上游 Dataset 分区数和 Spark Writer 决定；首版不提供 `baseName`、`singleFile`、`coalesce`、
`partitionBy`、目标文件大小或最大记录数配置。

一个 `FILE_OUTPUT` 节点生成一个目录数据集。读取方必须把 `targetPath` 作为整体读取，不应依赖
具体 part 文件名、数量或顺序。`_SUCCESS` 表示 Spark 提交成功；不承诺全目录在 S3 上原子替换。

## 5. Geometry 规则

### 5.1 单 Geometry 边界

首版要求来源表恰好包含一个 Geometry 字段，并且该字段必须等于
`geometryColumnName`：

- 没有 Geometry 字段或选择字段不存在时编译失败。
- 存在两个及以上 Geometry 字段时编译失败；用户应先使用 `SELECT_COLUMNS` 保留需要输出的
  Geometry。
- 不把其他 Geometry 隐式序列化成 WKB 属性，也不自动选择所谓“第一个” Geometry。

该限制是有意的：GeoParquet 支持多个 Geometry，但 Sedona 1.9.0 Writer 没有稳定的显式
`primary_column` 配置，依赖内部集合顺序会使协议不可预测。后续只有在 Writer 能显式指定
primary column，或平台实现自己的 Footer 元数据写出后，才扩展多 Geometry。

### 5.2 CRS 与维度

- Geometry 必须具有完整的 `GeometryTypeDefinition`。
- 首版只接受 `authority=EPSG` 和 `dimension=XY`。
- 不进行隐式坐标转换、轴顺序交换或 Z/M 降维。
- EPSG 先通过 GeoTools 随 Runner 制品提供的本地 EPSG 数据库解析，再把本地 CRS WKT 交给
  Proj4Sedona 转换为明确的 PROJJSON；Compiler 不允许为 CRS 解析访问网络。PROJJSON 显式补充
  EPSG `id` 并写入每个 part 文件的 `geo` Footer 元数据；即使 EPSG 为 4326，也不得依赖
  GeoParquet 缺省 CRS 语义。
- CRS 始终来自上游 Canvas Schema，不读取或信任 JTS Geometry 对象的 SRID 作为元数据来源。
- EPSG 无法生成合法 PROJJSON 时编译失败，提示先修正元数据或使用
  `SPATIAL_TRANSFORM`。

`XYZ`、`XYM`、`XYZM` 本轮均拒绝。Sedona 1.9.0 的 WKB Writer 当前只根据 Z 判断 2D/3D，不能
可靠保留 M；在完成四种维度的真实制品互操作验证前，不扩散不完整支持。

### 5.3 GeometryKind 与运行时值

- 支持 `POINT`、`LINESTRING`、`POLYGON`、三种 Multi 类型、`GEOMETRYCOLLECTION` 和通用
  `GEOMETRY`。
- 上游为具体 GeometryKind 时，Runner 逐行确认真实 JTS 类型没有发生运行时漂移。
- 上游为通用 `GEOMETRY` 时允许混合 GeometryKind，实际出现的类型由 Writer 写入
  `geometry_types`。
- NULL Geometry 写为 Parquet NULL。
- 首版拒绝 Empty Geometry。Sedona 1.9.0 对只包含 Empty Geometry 的 part 文件会产生不可靠的
  bbox 元数据，因此运行时返回 `GEOPARQUET_EMPTY_GEOMETRY_UNSUPPORTED`，不得写出可疑 Footer。
- 所有 XY 坐标必须是有限数值；NaN 或 Infinity 运行时失败。
- 不自动验证、修复拓扑或规范化 GeometryKind；需要时使用现有空间 Processor。

## 6. 标量字段与物理 Schema

除唯一 Geometry 外，来源字段按原名称、顺序、nullable 和 Spark/Parquet 原生类型写出：

- 支持 BOOLEAN、整数、FLOAT、DOUBLE、DECIMAL、STRING、BINARY、DATE、TIMESTAMP 和
  TIMESTAMP_NTZ。
- 不做字段重命名、类型转换、精度裁剪或时间格式字符串化。
- 字段选择、排序和转换继续由 `SELECT_COLUMNS`、`RENAME`、`TYPE_CAST` 等上游 Processor
  完成。
- Geometry 在 Parquet 物理层为 WKB Binary，并通过文件 Footer 中的 `geo` 元数据恢复空间
  语义；不得退化成普通 `PARQUET` 的无元数据 Binary。

当 `coveringMode=ROW_BBOX` 时，Sedona Writer 在物理 Schema 末尾增加：

```text
{geometryColumnName}_bbox:
  struct<xmin: double, ymin: double, xmax: double, ymax: double>
```

NULL Geometry 对应 NULL bbox。该字段只存在于输出制品，不进入 Canvas 的来源 Schema，也不向
下游传播。若来源已经存在同名字段，Compiler 必须返回
`GEOPARQUET_COVERING_COLUMN_CONFLICT`，不得让 Sedona 静默复用或跳过。`NONE` 只关闭逐行
covering 字段，不关闭每个 Parquet part Footer 中的整体 bbox 元数据。

## 7. GeoParquet Footer 元数据

每个 part 文件必须包含顶层 `geo` Key，语义固定为：

```text
version = 1.1.0
primary_column = geometryColumnName
columns[geometryColumnName].encoding = WKB
columns[geometryColumnName].geometry_types = 当前 part 实际出现的类型
columns[geometryColumnName].crs = 来源 EPSG 对应的显式 PROJJSON
columns[geometryColumnName].bbox = 当前 part 非 NULL Geometry 的 XY 包络
columns[geometryColumnName].covering = ROW_BBOX 时指向生成的 bbox struct
```

空 Dataset 可以生成零记录 part 或仅 `_SUCCESS`，验收时必须确认所有实际 part 的 Footer 都合法。
首版不生成或合并 `_metadata`、`_common_metadata`，不计算整个目录的全局 bbox，也不增加自定义
DataScalpel Footer Key。

## 8. Compiler 设计

`FileOutputNodeOperator` 继续是唯一 FILE_OUTPUT Operator，并增加 GeoParquet 分支：

1. 复用 FILE_OUTPUT 公共来源表、数据源、路径和冲突策略校验。
2. 验证来源为 `BOUNDED`。
3. 验证唯一 Geometry、选择字段、EPSG 和 XY。
4. 使用本地 EPSG 数据库验证 EPSG 能转换为 PROJJSON，全程不得访问外部 CRS 服务。
5. 验证 compression、coveringMode 和 bbox 字段冲突。
6. 构造零行 Spark 验证投影，确认 GeometryUDT 和所有标量字段可由 GeoParquet Writer 支持。
7. 只准备延迟写出计划，不创建 S3 路径、不生成 part 文件、不触发 Spark Action。

真实 Geometry 类型、Empty、非有限坐标和对象存储状态不在 Compiler 读取数据验证；这些风险由
Runner 在同一次写出 Action 中检查。

## 9. Runner 设计

GeoParquet 继续使用分布式 Spark Writer，而不是 Driver `toLocalIterator()`：

1. 为输出节点注册现有 `Dataset.observe`，不得额外 `count()`。
2. 在 Geometry 列上增加返回原对象的运行时校验表达式，检查具体 kind、Empty 和有限 XY；校验
   随唯一写出 Action 执行。
3. 从 Canvas Schema 生成该列的显式 EPSG PROJJSON。
4. 调用 `DataFrameWriter.format("geoparquet")`。
5. 固定传入 `geoparquet.version=1.1.0`、逐列 CRS、compression 和 covering mode。
6. 使用来源分区并行写出，不调用 `collect()`、`toLocalIterator()`、`count()` 或
   `coalesce(1)`。
7. Spark Writer 成功后由现有指标收集器取得影响行数。

计划使用的 Sedona 参数映射：

```text
geoparquet.version = 1.1.0
geoparquet.crs.{geometryColumnName} = <PROJJSON>
geoparquet.covering.mode = auto    # ROW_BBOX
geoparquet.covering.mode = legacy  # NONE
compression = snappy | zstd
```

Sedona 私有参数不得进入 Canvas JSON。实现时增加 `GeoParquetCrsSupport` 之类职责单一的内部
辅助类，Compiler 与 Runner 复用同一 PROJJSON 生成逻辑，不复制 CRS 映射。

## 10. S3 冲突与提交语义

GeoParquet 与现有 CSV、JSON Lines、Parquet 一样使用 Spark/Hadoop 目录提交：

- `FAIL_IF_EXISTS` 映射为 `SaveMode.ErrorIfExists`。
- `OVERWRITE` 映射为 `SaveMode.Overwrite`。
- Spark/Hadoop 可以在目标下使用内部 `_temporary`，成功后生成 `_SUCCESS`。
- S3 上的 OVERWRITE 不是原子替换；失败可能留下无 `_SUCCESS` 的目录。
- 并发任务写入相同 `targetPath` 不提供分布式锁或 Exactly Once 保证。

首版不把 GeoParquet改造成 SHP 的 Driver 临时前缀提交方式，也不为一个格式引入新的通用提交
框架。认证、权限、连接和目标已存在错误继续复用 `FILE_OUTPUT_*` 分类。

## 11. Frontend Inspector

选择 `GeoParquet` 后展示：

- 来源表。
- Geometry 字段，只列出 Geometry 类型字段；当上游有多个 Geometry 时仍保留选择值并展示
  Compiler 错误。
- 只读 GeometryKind、CRS 和维度。
- 压缩：`Snappy（默认，读取快）`、`ZSTD（体积更小）`。
- 空间 covering：`生成逐行 bbox（默认）`、`不生成逐行 bbox`。
- `ROW_BBOX` 对应的物理字段名预览。
- 最终目录预览，明确显示 `part-*.parquet + _SUCCESS`，而不是单文件名。

固定提示：

- 这是 GeoParquet 1.1.0，不是普通 Parquet。
- 首版只支持一个 EPSG + XY Geometry；多 Geometry 需先选择字段。
- `ROW_BBOX` 会增加一个 `{geometryColumnName}_bbox` 物理字段，但有利于空间过滤。
- 输出是可并行的大数据目录，不保证 part 数量和记录顺序。
- S3 OVERWRITE 非原子。

Palette 不增加节点，只为“文件输出”补充 `GeoParquet`、`空间 Parquet`、`WKB Parquet` 搜索
关键词。

## 12. 错误、日志与安全

新增的稳定编译错误：

- `GEOPARQUET_UNBOUNDED_SOURCE_UNSUPPORTED`
- `GEOPARQUET_GEOMETRY_REQUIRED`
- `GEOPARQUET_MULTIPLE_GEOMETRY_COLUMNS_UNSUPPORTED`
- `GEOPARQUET_CRS_UNSUPPORTED`
- `GEOPARQUET_DIMENSION_UNSUPPORTED`
- `GEOPARQUET_COVERING_COLUMN_CONFLICT`
- `GEOPARQUET_FORMAT_OPTION_INVALID`

新增的稳定运行错误：

- `GEOPARQUET_GEOMETRY_TYPE_MISMATCH`
- `GEOPARQUET_EMPTY_GEOMETRY_UNSUPPORTED`
- `GEOPARQUET_COORDINATE_INVALID`
- `GEOPARQUET_WRITE_FAILED`

RunnerFailureClassifier 把 Geometry 值错误归为不可重试的数据错误，把格式 Writer 错误归为
不可重试写出错误；S3 认证、权限、连接和目标冲突继续复用既有 FILE_OUTPUT 分类。

安全摘要只记录来源表、Geometry 字段、GeometryKind、EPSG、维度、压缩、covering mode、
目标相对路径和冲突策略。不得记录坐标、WKB、bbox 值、属性值、S3 凭据、完整 S3 URI 或 Spark
临时路径。运行值错误只报告节点、字段和安全的分区/行定位，不报告实际 Geometry 或坐标。

## 13. 测试与验收计划

- Contracts：新配置 Jackson/JSON 往返、未知枚举、缺失字段及 Canvas `1.25` 门槛。
- Compiler：唯一 Geometry、字段不存在、多 Geometry、EPSG、XY、PROJJSON、covering 冲突和
  S3 DISTRIBUTION 数据源。
- Geometry：全部 GeometryKind、通用 GEOMETRY 混合类型、NULL、Empty 拒绝、kind 漂移和
  非有限坐标。
- Schema：所有平台标量类型、nullable、DECIMAL、时间类型、BINARY、字段顺序及 ROW_BBOX
  物理字段。
- Footer：使用 Parquet Reader 读取每个 part 的 `geo` Key，核对 1.1.0、primary column、WKB、
  geometry_types、显式 PROJJSON、bbox 和 covering。
- 压缩：Snappy、ZSTD 均可由 Sedona 和至少一种外部 GeoParquet Reader 读回。
- Spark：多分区仍生成多个合法 part，写出是唯一 Action，影响行数准确。
- 冲突：`FAIL_IF_EXISTS`、`OVERWRITE`、失败无成功标记和成功 `_SUCCESS`。
- 前端：格式切换、Geometry 候选、失效值保留、压缩、covering、目录预览和导入导出。
- 制品：local Runner、cluster Runner、distribution 均能加载 Sedona GeoParquet Writer；不增加
  第二套 Spark、Parquet、Sedona 或 GeoTools 类。

## 14. 本阶段不包含

- 多 Geometry 与用户指定 primary column。
- GeoParquet Native Geometry Encoding。
- XYZ、XYM、XYZM。
- 单文件 GeoParquet、分区列、排序、空间分区或 Hilbert/GeoHash 聚簇。
- 目录级全局 bbox、`_metadata`、`_common_metadata` 或空间索引清单。
- 隐式 CRS 转换、Geometry 修复、Empty 自动替换或坐标裁剪。
- 读取端 `FILE_DATASET_INPUT` 的 GeoParquet 自动识别；应在独立输入能力设计中处理。
