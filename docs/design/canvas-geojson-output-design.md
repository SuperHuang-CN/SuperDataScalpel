# Canvas `FILE_OUTPUT` GeoJSON 输出设计

## 1. 状态与范围

- 状态：已开发。
- 协议版本：Canvas `1.25`。
- 节点：复用现有 `FILE_OUTPUT`，不增加 `GEOJSON_OUTPUT`。
- 执行模式：仅 `BATCH`，来源必须为 `BOUNDED`。
- 目标：已启用、连接类型为 S3、用途包含 `DISTRIBUTION` 的数据源。
- 规范：固定写出符合 RFC 7946 的单文件 GeoJSON `FeatureCollection`。
- 实现：Runner Driver 使用 Jackson `JsonGenerator` 和 `Dataset.toLocalIterator()` 逐行流式写出，
  不使用 Spark `DataFrameWriter`，不增加生产依赖。

本能力不修改后台 HTTP API、不新增 Maven 模块，也不改变现有 `JSON_LINES`、`PARQUET`、
`SHAPEFILE` 格式的协议和运行行为。正式 Canvas 当前版本已升级为 `1.25`。

这里的 `GEOJSON` 是标准单文件 GeoJSON，不是逐行 JSON，也不把 NDJSON/GeoJSON Text
Sequences 冒充为 GeoJSON。需要大规模并行空间文件输出时应选择 `GEOPARQUET`。

## 2. 稳定 Canvas 契约

`FileOutputConfiguration` 继续复用：

```text
sourceTableName + dataSourceId + targetPath + conflictPolicy + formatOptions
```

`FileOutputFormatOptions` 增加以下判别子类型：

```ts
interface GeoJsonOutputFormatOptions {
  type: 'GEOJSON';
  baseName: string;
  geometryColumnName: string;
  idColumnName: string | null;
  ignoreNullProperties: boolean;
}
```

Java Contracts 增加对应 record，并将 `GeoJson` 加入 `FileOutputFormatOptions` 的 Jackson 子类型
与 sealed permits。配置不保存 CRS、GeometryKind、维度、属性字段列表、坐标精度或 Writer
私有参数；这些内容均从编译时来源 Schema 推导。

新建配置默认值：

```text
baseName = 来源逻辑表名经过合法化后的建议值
idColumnName = null
ignoreNullProperties = false
```

完整示例：

```json
{
  "sourceTableName": "district_orders",
  "dataSourceId": "901e8938-bc1d-4bfd-91ec-d26bca38e8f6",
  "targetPath": "exports/district-orders-geojson",
  "conflictPolicy": "FAIL_IF_EXISTS",
  "formatOptions": {
    "type": "GEOJSON",
    "baseName": "district_orders",
    "geometryColumnName": "geom",
    "idColumnName": "district_id",
    "ignoreNullProperties": false
  }
}
```

`idColumnName` 只控制 GeoJSON Feature 顶层 `id`；该字段仍保留在 `properties`，避免选择 ID 后
悄悄改变属性 Schema。

## 3. 协议兼容

- `FILE_OUTPUT` 节点本身继续从 Canvas `1.6` 可用。
- `GEOJSON` 格式从 Canvas `1.25` 开始可用。
- `schemaMinorVersion < 25` 携带 `formatOptions.type=GEOJSON` 时拒绝，并返回
  `FORMAT_OPTION_REQUIRES_SCHEMA_VERSION`。
- Canvas `1.0`～`1.24` 的已有定义继续兼容；既有 `JSON_LINES` 不自动升级成 `GEOJSON`。
- 新建、保存、Admin 返回、Manifest 和前端导出统一规范化为 `1.25`。
- 前端导入执行相同门槛；无法安全导入时保留当前画布。

## 4. 文件命名与物理输出

`baseName`：

- 必填，长度为 `1..64` 个 Unicode 字符。
- 允许中文、字母、数字、`_`、`-`。
- 必须以中文、字母或数字开头。
- 不允许路径分隔符、控制字符、空格和扩展名。
- Inspector 输入示例为 `district_orders`，而不是 `district_orders.geojson`。

规则与 Shapefile 的 `baseName` 保持一致，但 GeoJSON 使用独立稳定错误码。物理输出固定为：

```text
{targetPath}/{baseName}.geojson
{targetPath}/_SUCCESS
```

一个节点始终生成一个 GeoJSON 文件。首版不提供 Spark 分片、动态文件名、分区目录、gzip、
pretty print、最大记录数或每文件大小配置。`_SUCCESS` 只在完整文件已经提交到最终位置后创建。

目标存在性的判断单位是整个 `targetPath`：只要目标前缀下存在业务文件或 `_SUCCESS`，就视为
目标已存在，不能只检查同名 `.geojson` 对象。

## 5. GeoJSON 文档结构

输出顶层始终是 RFC 7946 `FeatureCollection`：

```json
{
  "type": "FeatureCollection",
  "features": [
    {
      "type": "Feature",
      "id": 1001,
      "geometry": {
        "type": "Point",
        "coordinates": [116.397, 39.908]
      },
      "properties": {
        "district_id": 1001,
        "district_name": "示例区域"
      }
    }
  ]
}
```

- Dataset 每一行生成一个 Feature。
- 空 Dataset 生成合法的 `{"type":"FeatureCollection","features":[]}`。
- `features` 数组按 `toLocalIterator()` 返回顺序逐项写出，但输出记录顺序不是稳定契约。
- 顶层和 Feature 均不输出 `bbox`、`crs`、`links` 或其他 foreign member。
- 始终输出 `properties` 对象；没有标量字段时输出空对象 `{}`。
- JSON 使用 UTF-8、紧凑格式和标准转义，不写 BOM。

## 6. Geometry 规则

### 6.1 单 Geometry 边界

首版要求来源表恰好包含一个 Geometry 字段，并且该字段必须等于
`geometryColumnName`：

- 没有 Geometry 字段、选择字段不存在或选择字段不是 Geometry 时编译失败。
- 存在两个及以上 Geometry 字段时编译失败；用户应先使用 `SELECT_COLUMNS` 保留需要输出的
  Geometry。
- Geometry 字段只写入 Feature 的 `geometry`，不重复出现在 `properties`。
- 不自动选择所谓“第一个” Geometry，也不把额外 Geometry 隐式序列化成字符串属性。

### 6.2 CRS 与维度

- Geometry 必须具有完整的 `GeometryTypeDefinition`。
- 首版只接受 `authority=EPSG`、`code=4326` 和 `dimension=XY`。
- 不进行隐式坐标转换、轴顺序交换、Z/M 降维或坐标裁剪。
- 坐标顺序固定为 `longitude, latitude`，不采用 EPSG 注册表中的纬度优先轴顺序。
- 不输出已被 RFC 7946 移除的 `crs` member。
- CRS 始终来自上游 Canvas Schema，不读取或信任 JTS Geometry 对象的 SRID。
- 非 EPSG:4326 时编译失败，用户必须先使用 `SPATIAL_TRANSFORM`。

每个 XY 坐标必须满足：longitude 位于 `[-180, 180]`，latitude 位于 `[-90, 90]`。越界值在
运行时失败，不自动 wrap、clip 或交换经纬度。

### 6.3 GeometryKind 与运行时值

- 支持 `POINT`、`MULTIPOINT`、`LINESTRING`、`MULTILINESTRING`、`POLYGON`、
  `MULTIPOLYGON`、`GEOMETRYCOLLECTION` 和通用 `GEOMETRY`。
- 上游为具体 GeometryKind 时，Runner 逐行确认真实 JTS 类型没有发生运行时漂移。
- 上游为通用 `GEOMETRY` 时允许不同行具有不同的 RFC 7946 GeometryKind。
- GeometryCollection 允许，并递归校验其所有成员；不隐式拆解成多个 Feature。
- NULL Geometry 输出 `"geometry": null`。
- Empty Geometry 在 RFC 7946 中没有可互操作的统一坐标表达，首版运行时失败。
- 坐标中的 NaN、正负 Infinity 或损坏的 CoordinateSequence 运行时失败。

Polygon 和 MultiPolygon 写出时规范化为 RFC 7946 right-hand rule：外环逆时针、内环顺时针。
规范化只作用于序列化过程中的副本或输出坐标顺序，不修改上游 JTS Geometry，不改变 Canvas
Schema，也不自动修复自相交、未闭合等拓扑问题。需要诊断或修复时由用户在上游使用
`GEOMETRY_VALIDATE`、`GEOMETRY_REPAIR`。

## 7. Feature ID 与属性映射

### 7.1 Feature ID

`idColumnName` 为 `null` 时不输出 Feature `id`。选择 ID 时：

- 来源字段必须存在且不是 Geometry。
- 只接受 `STRING`、`BYTE`、`SHORT`、`INTEGER`、`LONG`。
- STRING 按 JSON string 写出；整数类型按 JSON number 写出。
- 值为 NULL 时仅省略该 Feature 的 `id`，同字段仍按空值规则处理 properties。
- 不把 DECIMAL、FLOAT、DOUBLE、BOOLEAN、日期、时间或 BINARY 隐式转成 ID。
- LONG 按完整十进制整数写出；Inspector 提示部分 JavaScript 消费端对超出安全整数范围的值可能
  丢失精度，需严格跨端兼容时应在上游转为 STRING。

### 7.2 Properties

除唯一 Geometry 外，来源的全部字段按原名称和 Schema 顺序写入 `properties`，不在
`FILE_OUTPUT` 内提供字段选择、重命名或类型转换。需要调整时使用上游 Processor。

平台类型到 JSON 的映射固定为：

| 平台类型 | JSON 表达 | 规则 |
|---|---|---|
| `BOOLEAN` | boolean | 保持 true/false |
| `BYTE`、`SHORT`、`INTEGER`、`LONG` | number | 十进制整数，不字符串化 |
| `FLOAT`、`DOUBLE` | number | 必须是有限数值 |
| `DECIMAL(p,s)` | number | 使用 BigDecimal 原值，不转 double，不使用科学计数法 |
| `STRING` | string | UTF-8，交由 JsonGenerator 标准转义 |
| `DATE` | string | ISO `yyyy-MM-dd` |
| `TIMESTAMP` | string | 转为 UTC 后输出 ISO-8601 Instant，固定 `Z` 后缀 |
| `TIMESTAMP_NTZ` | string | ISO-8601 本地日期时间，不附加时区或偏移量 |
| `BINARY` | 不支持 | 编译失败，要求先显式转换或移除 |

`TIMESTAMP` 的转换必须使用执行上下文中的固定 UTC 规则，不得依赖 Runner JVM 默认时区；
`TIMESTAMP_NTZ` 不得被错误附加 UTC。属性名称和 STRING 内容可包含中文及其他 Unicode。

`ignoreNullProperties=false` 时，NULL 标量字段写为 JSON `null`；为 `true` 时仅从该 Feature 的
`properties` 中省略 NULL 字段。该开关不影响 `geometry`、Feature `id` 规则或属性 Schema 的
编译期判断。

## 8. Compiler 设计

`FileOutputNodeOperator` 继续是唯一 FILE_OUTPUT Operator，并增加 GeoJSON 分支：

1. 复用 FILE_OUTPUT 公共来源表、数据源、路径和冲突策略校验。
2. 验证来源为 `BOUNDED`。
3. 验证 `baseName`。
4. 验证唯一 Geometry、选择字段、EPSG:4326 和 XY。
5. 验证所有 properties 字段均可映射，拒绝 BINARY 和额外 Geometry。
6. 验证可选 ID 字段存在且类型受支持。
7. 构造零行 Spark 投影，确认 Runner 能按声明的平台类型读取 Row。
8. 只准备延迟写出计划，不创建本地文件、S3 临时对象或目标对象，不触发 Spark Action。

真实 Geometry 类型、Empty、坐标范围、FLOAT/DOUBLE 非有限值和对象存储状态不在 Compiler
读取数据验证；这些风险由 Runner 在唯一写出 Action 中逐行检查。

## 9. Runner 与专用 Writer

新增内部 `GeoJsonFileOutputWriter`，仅负责标准 GeoJSON：

1. 为输出节点注册现有 `Dataset.observe`。
2. 在 Runner 本地临时目录创建 `{baseName}.geojson`，使用受限权限，绝不记录绝对路径。
3. 创建 UTF-8 Jackson `JsonGenerator`，写入 FeatureCollection 头和 `features` 数组。
4. 使用 `Dataset.toLocalIterator()` 按分区逐行拉取 Row；不调用 `collect()`、`count()` 或第二个
   Spark Action。
5. 每行先验证 Geometry kind、Empty、坐标有限性和范围，再流式写入一个 Feature。
6. 按平台 Schema 明确写入 properties，不使用 Row 的 `toString()` 或通用对象反射序列化。
7. 完成数组和顶层对象，flush、close 后重新确认文件大小不超过安全限制。
8. 将完整本地文件上传到运行级 S3 临时前缀。
9. 按冲突策略提交到最终 `{targetPath}/{baseName}.geojson`，最后创建零字节 `_SUCCESS`。
10. finally 清理本地临时文件和 S3 临时前缀。

遍历 Dataset 是唯一 Spark Action。即使来源有多个 Spark 分区，也只生成一个 FeatureCollection；
不调用 `coalesce(1)`，由 Driver 顺序消费分区，避免把所有数据先压入单个 Executor 分区。内存中
只保留当前 Row、当前 Geometry 的序列化状态和固定大小输出缓冲，不保存完整 Feature 列表。

当前保持 GeoJSON Writer 内部的职责集中提交流程；若后续从 Shapefile Writer 提取内部 `S3FileOutputCommitter`，只复用本地
制品上传、双重冲突检查、最终复制、`_SUCCESS` 和清理逻辑。不得把格式序列化细节放进该帮助类，
也不为两个 Writer 引入新的框架、Maven 模块或公共扩展 API。

## 10. S3 冲突与提交语义

临时前缀复用系统保留结构：

```text
{rootPrefix}/_temporary/datascalpel-file-output/
  {executionId}/{nodeId}/{randomId}/
    {baseName}.geojson
```

`FAIL_IF_EXISTS`：

- Spark Action 前检查目标前缀。
- 完整本地文件上传到临时对象后、提交前再次检查。
- 目标存在时保留原内容并失败，同时清理本次临时对象。
- S3 不提供目录级原子创建，并发写入同一路径仍是尽力保护。

`OVERWRITE`：

- 先完整生成本地文件并上传临时对象。
- 提交阶段才删除最终目标前缀。
- 从临时对象复制 `.geojson` 到最终对象。
- `_SUCCESS` 始终最后写入。
- S3 覆盖不是原子替换；提交中断可能留下无 `_SUCCESS` 的不完整目录。

认证、权限、连接、目标冲突和通用提交错误继续复用 `FILE_OUTPUT_*` 分类。GeoJSON 专用错误只
表达格式数据、单文件写出和临时制品问题。

## 11. 容量与适用边界

GeoJSON 是文本单文件且由 Driver 串行生成，首版设置以下硬限制：

- 最终 `.geojson` 达到 `1,800,000,000` 字节时立即失败。
- 写出过程中定期检查已落盘字节数，不等上传后才检查。
- 本地临时目录可用空间不足时返回稳定错误，不回退到内存缓存。
- 不自动拆成多个 FeatureCollection，不在超限后切换成 NDJSON。

Inspector 必须把 GeoJSON 标为适合交换、下载和中小规模 GIS 数据的格式，不适合作为大规模分析
数据集。大数据量或需要并行读写时推荐：

```text
FILE_OUTPUT(GEOPARQUET)
```

若对方只接受文本流，应另行设计 GeoJSON Sequence/NDJSON 格式，不能改变本格式的标准
FeatureCollection 语义。

## 12. Frontend Inspector

选择 `GeoJSON` 后展示：

- 来源表。
- Geometry 字段，只列出 Geometry 类型字段；当上游有多个 Geometry 时仍保留选择值并展示
  Compiler 错误。
- 只读 GeometryKind、CRS 和维度。
- 文件基础名。
- 可选 Feature ID 字段，只列出 STRING 和整数类型字段。
- “忽略属性中的 NULL 值”开关，默认关闭。
- 最终目标预览：`{targetPath}/{baseName}.geojson + _SUCCESS`。

上游变化后保留已有失效值并就地标红，不自动切换 Geometry、ID 或基础名。首次选择来源表时可
建议合法化后的 `baseName`，已有非空值不得被静默覆盖。

固定提示：

- 输出是 RFC 7946 单文件 FeatureCollection，不是 JSON Lines。
- 只支持一个 EPSG:4326 + XY Geometry；其他 CRS 需先空间转换。
- Geometry 字段进入 Feature geometry，其余字段进入 properties。
- NULL Geometry 可输出，Empty Geometry 不支持。
- 当前为 Driver 单文件串行写出，达到 1.8GB 会失败；大数据优先使用 GeoParquet。
- LONG Feature ID 在部分 JavaScript 消费端可能丢失精度。
- S3 OVERWRITE 非原子。

Palette 不增加节点，只为“文件输出”补充 `GeoJSON`、`FeatureCollection`、`空间 JSON`、
`RFC 7946` 搜索关键词。

## 13. 错误、日志与安全

新增的稳定编译错误：

- `GEOJSON_UNBOUNDED_SOURCE_UNSUPPORTED`
- `GEOJSON_INVALID_BASE_NAME`
- `GEOJSON_GEOMETRY_REQUIRED`
- `GEOJSON_MULTIPLE_GEOMETRY_COLUMNS_UNSUPPORTED`
- `GEOJSON_CRS_REQUIRES_WGS84`
- `GEOJSON_DIMENSION_UNSUPPORTED`
- `GEOJSON_PROPERTY_TYPE_UNSUPPORTED`
- `GEOJSON_ID_FIELD_INVALID`

新增的稳定运行错误：

- `GEOJSON_GEOMETRY_TYPE_MISMATCH`
- `GEOJSON_EMPTY_GEOMETRY_UNSUPPORTED`
- `GEOJSON_COORDINATE_INVALID`
- `GEOJSON_COORDINATE_OUT_OF_RANGE`
- `GEOJSON_NON_FINITE_NUMBER`
- `GEOJSON_SIZE_LIMIT_EXCEEDED`
- `GEOJSON_LOCAL_STORAGE_EXHAUSTED`
- `GEOJSON_WRITE_FAILED`
- `GEOJSON_UPLOAD_FAILED`

`GEOJSON_COORDINATE_INVALID` 表达损坏的坐标结构；非有限坐标或属性数值统一使用
`GEOJSON_NON_FINITE_NUMBER`；有限但超出经纬度范围使用
`GEOJSON_COORDINATE_OUT_OF_RANGE`。RunnerFailureClassifier 把这些值错误归为不可重试的数据
错误，把本地格式写出错误归为不可重试写出错误；可恢复的 S3 连接错误继续沿用现有分类和重试
策略。

安全摘要只记录来源表、Geometry 字段、GeometryKind、EPSG、维度、是否配置 ID、是否忽略
NULL properties、目标相对路径和冲突策略。不得记录 Feature ID、Geometry、坐标、属性值、
GeoJSON 片段、S3 凭据、完整 S3 URI、临时本地路径或临时对象 Key。运行值错误只报告节点、
安全的分区/行定位和字段名，不报告实际值。

## 14. 测试与验收计划

- Contracts：新配置 Jackson/JSON 往返、未知字段、缺失字段、默认值及 Canvas `1.25` 门槛。
- Compiler：来源表、合法基础名、唯一 Geometry、字段不存在、多 Geometry、EPSG:4326、XY、
  BINARY 属性、ID 类型和 S3 DISTRIBUTION 数据源。
- RFC 7946：FeatureCollection、全部 GeometryKind、GeometryCollection、NULL Geometry、空 Dataset、
  right-hand rule、无 `crs`/`bbox` 和 UTF-8 无 BOM。
- 坐标：边界经纬度、越界、NaN、Infinity、损坏 CoordinateSequence、Empty Geometry 和具体 kind
  漂移。
- Properties：全部支持类型、NULL 保留/忽略、中文字段名和值、JSON 转义、DECIMAL 精度、
  DATE、TIMESTAMP UTC、TIMESTAMP_NTZ 和非有限浮点数。
- Feature ID：STRING、四种整数、NULL 省略、字段仍保留在 properties 和不支持类型拒绝。
- 制品：使用 Jackson 和至少一种独立 GIS Reader 重新读取，核对 Feature 数、Geometry、属性、
  ID 和 CRS 语义。
- 容量：通过可注入测试阈值验证文件超限和本地空间不足，不实际创建 1.8GB 文件。
- S3 提交：验证 `FAIL_IF_EXISTS`、`OVERWRITE`、双重检查、临时前缀清理、失败不写
  `_SUCCESS`、成功标记最后提交。
- Spark：多分区 Dataset 仍只生成一个文件，遍历是唯一 Action，影响行数准确且不 `collect()`。
- 前端：格式切换、Geometry 和 ID 候选、失效值保留、基础名、NULL 开关、路径预览和导入导出。
- 安全：摘要、日志和异常不包含坐标、Feature/属性值、S3 临时对象或本地路径。

## 15. 本阶段不包含

- GeoJSON Lines、NDJSON、GeoJSON Text Sequences、TopoJSON 或 JSONP。
- Streaming 输出、持续追加或 Exactly Once 文件 Sink。
- 多 Geometry、用户自定义 properties 映射、字段重命名或嵌套 JSON 属性。
- 非 EPSG:4326、XYZ、XYM、XYZM、隐式坐标转换或坐标精度裁剪。
- bbox、foreign members、样式、链接、扩展 CRS 或自定义 Feature 类型。
- gzip、pretty print、单文件自动拆分或超过 1.8GB 的 GeoJSON。
- GeoJSON 下载接口和 `FILE_DATASET_INPUT` 的 GeoJSON 读取；读取端应独立设计。
