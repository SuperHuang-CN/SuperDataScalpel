# Canvas `FILE_OUTPUT` Shapefile 输出设计

## 1. 状态与范围

- 状态：已实现。
- 协议版本：Canvas `1.24`。
- 节点：复用现有 `FILE_OUTPUT`，不增加 `SHP_OUTPUT`。
- 执行模式：仅 `BATCH`，来源必须为 `BOUNDED`。
- 目标：已启用、连接类型为 S3、用途包含 `DISTRIBUTION` 的数据源。
- 输出形态：默认 `ZIP`，可选 `COMPONENT_DIRECTORY`。
- Writer：Runner Driver 本地 GeoTools 33.5 流式写出，再通过 S3A 暂存并提交。

本能力不修改后台 HTTP API，不新增 Maven 模块，不改变只读
`data-scalpel-shapefile` 模块的职责。CSV、JSON Lines 和 Parquet 的既有协议与运行语义保持
不变。

## 2. 稳定 Canvas 契约

`FileOutputConfiguration` 继续提供公共字段：

```text
sourceTableName + dataSourceId + targetPath + conflictPolicy + formatOptions
```

`formatOptions` 增加以下严格判别子类型：

```ts
type ShapefilePackageMode = 'ZIP' | 'COMPONENT_DIRECTORY';

type ShapefileShapeType =
  | 'POINT'
  | 'MULTIPOINT'
  | 'POLYLINE'
  | 'POLYGON';

interface ShapefileAttributeMapping {
  sourceColumnName: string;
  targetFieldName: string;
  targetStringByteLength: number | null;
}

interface ShapefileOutputFormatOptions {
  type: 'SHAPEFILE';
  baseName: string;
  packageMode: ShapefilePackageMode;
  geometryColumnName: string;
  targetShapeType: ShapefileShapeType;
  attributeMappings: ShapefileAttributeMapping[];
}
```

Java Contracts 使用对应 record 和 enum，并把 `Shapefile` 加入
`FileOutputFormatOptions` 的 Jackson 子类型和 sealed permits。列表使用防御性复制，不在契约
中保存 CRS 副本、S3 连接、凭据、本地临时路径或运行时校验结果。

完整示例：

```json
{
  "sourceTableName": "district_orders",
  "dataSourceId": "901e8938-bc1d-4bfd-91ec-d26bca38e8f6",
  "targetPath": "exports/district-orders",
  "conflictPolicy": "FAIL_IF_EXISTS",
  "formatOptions": {
    "type": "SHAPEFILE",
    "baseName": "district_orders",
    "packageMode": "ZIP",
    "geometryColumnName": "geom",
    "targetShapeType": "POLYGON",
    "attributeMappings": [
      {
        "sourceColumnName": "district_id",
        "targetFieldName": "DIST_ID",
        "targetStringByteLength": null
      },
      {
        "sourceColumnName": "district_name",
        "targetFieldName": "DIST_NAME",
        "targetStringByteLength": 160
      }
    ]
  }
}
```

## 3. 版本兼容

- Canvas 当前写出版本为 `1.25`，兼容读取 `1.0`～`1.25`。
- `FILE_OUTPUT` 节点本身仍从 `1.6` 可用。
- `schemaMinorVersion < 24` 使用 `formatOptions.type=SHAPEFILE` 时返回
  `FORMAT_OPTION_REQUIRES_SCHEMA_VERSION`。
- 前端导入执行同一门槛；无法安全导入时保留当前画布。
- 新建、保存、Admin 返回、Manifest 和前端导出统一规范化为 `1.25`。

## 4. 文件与目录命名

`baseName` 必填，按 Unicode code point 计数为 `1..64`，允许中文、字母、数字、`_`、`-`，
且必须以中文、字母或数字开头。不得包含空格、控制字符、路径分隔符或扩展名。

物理制品：

```text
ZIP:
{targetPath}/{baseName}.zip
{targetPath}/_SUCCESS

COMPONENT_DIRECTORY:
{targetPath}/{baseName}.shp
{targetPath}/{baseName}.shx
{targetPath}/{baseName}.dbf
{targetPath}/{baseName}.prj
{targetPath}/{baseName}.cpg
{targetPath}/_SUCCESS
```

ZIP 内五个组件直接位于根目录，不增加目录层级。`.cpg` 固定为 `UTF-8`；`.prj` 根据上游
Schema 中的 EPSG 由 GeoTools 生成。

## 5. Geometry 规则

### 5.1 CRS、维度与字段

- 来源表必须为 `BOUNDED`。
- `geometryColumnName` 必须指向一个带完整空间元数据的 `GEOMETRY` 字段。
- CRS 只接受 `EPSG`，维度只接受 `XY`。
- Compiler 使用 GeoTools 解码 EPSG，无法解析时返回 `SHAPEFILE_CRS_UNSUPPORTED`。
- 不读取或信任 JTS 对象自己的 SRID 作为 CRS 来源，也不做隐式坐标转换。
- 其他 Geometry 字段可以存在于来源表，但不能加入 DBF 属性映射。

### 5.2 Shape 类型兼容

| 目标 Shape | 可接受的上游 GeometryKind | 写出规范化 |
| --- | --- | --- |
| `POINT` | `POINT` | 保持 Point |
| `MULTIPOINT` | `POINT`、`MULTIPOINT` | Point 包装为单元素 MultiPoint |
| `POLYLINE` | `LINESTRING`、`MULTILINESTRING` | 统一写为 MultiLineString |
| `POLYGON` | `POLYGON`、`MULTIPOLYGON` | 统一写为 MultiPolygon |

上游为具体且不兼容的 kind 时编译失败。上游为通用 `GEOMETRY` 时允许用户明确选择目标
Shape，并产生运行时逐行检查警告。`GEOMETRYCOLLECTION` 不隐式拆解。

NULL Geometry 写为 Null Shape。Empty Geometry 没有稳定等价表示，运行时返回
`SHAPEFILE_EMPTY_GEOMETRY_UNSUPPORTED`。Writer 不做拓扑验证或修复；需要时在上游使用
`GEOMETRY_VALIDATE` 和 `GEOMETRY_REPAIR`。

## 6. DBF Schema

### 6.1 映射约束

首版要求 `1..255` 个属性字段：

- 来源字段必须存在，且同一来源字段只能映射一次。
- Geometry 字段不能作为 DBF 属性。
- `targetFieldName` 必须匹配 `[A-Za-z_][A-Za-z0-9_]{0,9}`。
- 目标字段名按大小写不敏感唯一。
- 输出顺序严格采用 `attributeMappings` 数组顺序。
- Runner 不截断、不重命名、不自动去重。
- STRING 的 `targetStringByteLength` 必须为 `1..254`；其他类型必须为 `null`。

### 6.2 类型映射

| 平台类型 | DBF 表达 | 规则 |
| --- | --- | --- |
| `BOOLEAN` | Logical | NULL 写未知值 |
| `BYTE` | Numeric(4,0) | 包含符号位 |
| `SHORT` | Numeric(6,0) | 包含符号位 |
| `INTEGER` | Numeric(11,0) | 包含符号位 |
| `LONG` | Numeric(20,0) | 包含符号位 |
| `DECIMAL(p,s)` | Numeric | `p + 符号位 + 可选小数点`，总宽度不超过 20 |
| `STRING` | Character | 显式 UTF-8 字节宽度 `1..254` |
| `DATE` | Date | `YYYYMMDD` |

首版拒绝 FLOAT、DOUBLE、TIMESTAMP、TIMESTAMP_NTZ、BINARY 和 Geometry 属性。STRING 每行
写入前计算 UTF-8 字节数，超宽立即失败；数值按显式 scale 写入，不能精确表达或超过宽度时
失败。DBF 对 NULL 与空字符串的区分能力有限，设计器必须持续展示该兼容性提示。

GeoTools 高层 DBF Writer 可能根据 Java binding 收窄数值字段宽度，因此实现使用 GeoTools
`DbaseFileHeader` 生成标准 Header，并由内部严格行 Writer 精确写入字段宽度、scale、NULL 和
字段顺序；Geometry 与 SHX/PRJ 仍由 `ShapefileDataStore` 生成。完成后使用严格 DBF 替换
GeoTools 的占位 DBF。

## 7. 前端字段建议

首次初始化 Shapefile 配置或用户显式点击“重建”时生成建议映射：

1. 加入全部 DBF 支持的非 Geometry 字段。
2. ASCII 字母转大写，非法字符替换为 `_`，连续 `_` 合并。
3. 数字开头增加 `F_`；无法生成名称时使用 `F_001`、`F_002`。
4. 截取到 10 个 ASCII 字符；冲突时缩短主体并增加数字后缀。
5. 已声明 STRING 长度 `L` 时建议 `min(L × 4, 254)`；未声明时建议 `254`。

自动建议只在初始化或用户明确重建时执行。上游变化后，来源字段、Geometry 字段和 Shape
类型均保留已有值并标红，不得静默清空或覆盖。Compiler 暂不可用时前端展示等待/失败状态，
不得把缺少 `inputTables` 解释为字段全部失效。

## 8. Runner Writer

`ShapefileFileOutputWriter` 的执行顺序：

1. Compiler 已使用零行 Spark 计划验证来源 Schema、Geometry、CRS、Shape 和 DBF 映射。
2. Runner 使用输出节点已有的 `Dataset.observe` 统计本次实际遍历的输出行数。
3. `Dataset.toLocalIterator()` 按 Spark 分区顺序将 Row 流送到 Driver，不调用
   `collect()`、`count()` 或 `coalesce(1)`。
4. `ShapefileDataStore`、FeatureWriter 与严格 DBF Writer 在 Runner 本地临时目录逐行写入。
5. 每行校验 Geometry 运行时类型、Empty、STRING 字节宽度和数值宽度。
6. 关闭并 dispose 所有 Writer 后检查五个组件完整性。
7. ZIP 模式使用 UTF-8 文件名打包，所有组件位于归档根目录。
8. 完整本地制品上传到运行级 S3 临时前缀。
9. 按冲突策略提交最终目录，并最后创建零字节 `_SUCCESS`。
10. finally 清理本地目录和 S3 临时前缀。

无论来源有多少 Spark 分区，一个节点只建立一个 Driver Writer 并生成一套 Shapefile。输出
记录顺序不承诺稳定；需要业务标识时必须显式映射来源 ID。

## 9. S3 暂存与提交

临时前缀为：

```text
{rootPrefix}/_temporary/datascalpel-file-output/
  {executionId}/{nodeId}/{randomId}/
```

`FAIL_IF_EXISTS` 在 Spark Action 前和最终提交前各检查一次目标目录。目标存在时保留原内容并
失败；S3 不提供目录级原子创建，因此并发写入相同目标仍是尽力保护。

`OVERWRITE` 先完整生成并上传临时制品，提交阶段才删除最终目标，随后按固定顺序复制制品，
最后写 `_SUCCESS`。S3 覆盖不是原子替换；中断可能留下没有完成标记的目录。

认证、权限、连接和目标存在错误继续复用 `FILE_OUTPUT_*` 分类。Shapefile 本地生成和制品
上传错误使用独立稳定错误码。

## 10. 容量与资源边界

- `.shp`、`.shx`、`.dbf` 任一达到 `1,800,000,000` 字节时硬失败。
- ZIP 达到相同限制时硬失败。
- 写出过程中定期检查，并在最终打包或提交前再次检查。
- 本地磁盘不足返回 `SHAPEFILE_LOCAL_STORAGE_EXHAUSTED`，不回退为内存缓存。
- 不自动拆分多个 Shapefile，也不提供 Spark 分片 SHP。

超大空间数据应先 `GEOMETRY_SERIALIZE(WKB)`，再移除原 Geometry 并写 Parquet。GeoParquet
属于未来独立格式能力，不在本实现中隐式提供。

## 11. GeoTools 与制品依赖

Task Engine 使用统一版本属性：

```text
Sedona 1.9.0
GeoTools 33.5
geotools-wrapper 1.9.0-33.5
gt-shapefile 33.5
```

`gt-shapefile` 排除传递的 `gt-main`，GeoTools 核心类继续来自 Sedona
`geotools-wrapper`。Task Engine POM 只增加启用 releases 的 OSGeo Repository，并通过
Maven Enforcer 禁止额外的 `gt-main`、`gt-referencing`、`gt-metadata`、`gt-api` 和
`gt-epsg-*`。Cluster Runner Shade allow-list 只加入 `gt-shapefile` 与确认必需且无重复核心类
的传递制品。

构建期必须分别对 local Runner、cluster Runner 和 distribution 制品执行真实类加载检查：

- 加载 `ShapefileDataStore`，确认 Shapefile Writer 可用。
- 调用 GeoTools `CRS` 解码 `EPSG:4326`，确认坐标参考系能力可用。
- 确认每个制品中只有一套 GeoTools 核心类来源，以及一个 `geotools-wrapper` 和一个
  `gt-shapefile`；不得包含独立的 `gt-main`、`gt-referencing`、`gt-metadata`、`gt-api` 或
  `gt-epsg-*`。
- cluster Runner 还必须确认没有把 Spark、Scala 或 Hadoop 禁止类打入 Shade 制品。

现有纯 Java `data-scalpel-shapefile` 继续负责只读解析，不依赖 GeoTools Writer。

## 12. Inspector

正式 `FileOutputInspector` 从 Legacy Inspector 拆出。选择 Shapefile 后展示：

- 来源表与 S3 目标。
- Geometry 字段，以及只读 kind、CRS 和维度。
- 目标 Shape、文件基础名、ZIP/组件目录。
- 可添加、删除、排序和重建的 DBF 映射。
- STRING UTF-8 字节宽度。
- 最终目标路径预览。
- Compiler 节点问题和失效上游值。

面板固定提示五组件、10 位 ASCII 字段名、NULL/空字符串限制、UTF-8 字节宽度、Driver 串行
写出、1.8GB 限制和 S3 OVERWRITE 非原子语义。Palette 不新增节点，只增加 `SHP`、
`Shapefile`、`空间文件` 搜索关键词。

## 13. 错误与日志安全

主要编译错误：

- `FORMAT_OPTION_REQUIRES_SCHEMA_VERSION`
- `SHAPEFILE_INVALID_BASE_NAME`
- `SHAPEFILE_GEOMETRY_REQUIRED`
- `SHAPEFILE_GEOMETRY_KIND_INCOMPATIBLE`
- `SHAPEFILE_CRS_UNSUPPORTED`
- `SHAPEFILE_ATTRIBUTE_MAPPING_INVALID`
- `SHAPEFILE_ATTRIBUTE_TYPE_UNSUPPORTED`
- `SHAPEFILE_DBF_FIELD_NAME_INVALID`
- `SHAPEFILE_DBF_FIELD_NAME_DUPLICATE`
- `SHAPEFILE_DBF_STRING_WIDTH_INVALID`
- `SHAPEFILE_DBF_NUMERIC_WIDTH_UNSUPPORTED`

主要运行错误：

- `SHAPEFILE_GEOMETRY_TYPE_MISMATCH`
- `SHAPEFILE_EMPTY_GEOMETRY_UNSUPPORTED`
- `SHAPEFILE_ATTRIBUTE_VALUE_TOO_LONG`
- `SHAPEFILE_NUMERIC_OVERFLOW`
- `SHAPEFILE_SIZE_LIMIT_EXCEEDED`
- `SHAPEFILE_LOCAL_STORAGE_EXHAUSTED`
- `SHAPEFILE_WRITE_FAILED`
- `SHAPEFILE_UPLOAD_FAILED`

安全摘要只记录来源表、Geometry 字段、Shape 类型、EPSG、包装模式、DBF 字段数量和字段
名、目标相对路径及冲突策略。不得记录 Geometry、坐标、属性值、S3 凭据、本地临时路径或带
凭据连接信息。运行时值错误只报告行序号和字段名，不报告实际值。

## 14. 验收覆盖

- Contracts：SHAPEFILE JSON/Jackson 往返和 Canvas `1.24` 门槛。
- Compiler：有界性、S3 用途、Geometry、EPSG/XY、Shape 兼容与 DBF Schema。
- Writer：组件目录、ZIP 根目录结构、NULL Shape、中文 UTF-8、BOOLEAN、整数、DECIMAL、
  DATE、字段顺序和可注入的小容量阈值。
- 制品读回：使用 GeoTools 重新读取 SHP/SHX/DBF/PRJ/CPG，核对 Schema、CRS、记录数、
  Geometry 和属性。
- 前端：严格导入导出、1.23 拒绝、Inspector 映射与失效值保留。
- 构建：Maven Enforcer、Task Engine 编译、前端 lint/build，以及 local Runner、cluster
  Runner、distribution 三类制品的 GeoTools 类加载和重复依赖检查。

本阶段不实现 GeoPackage、GeoJSON 文件包、GeoParquet、FileGDB 写出、SHP 下载接口、隐式
坐标转换、Z/M 降维、GeometryCollection 拆解、属性截断或自动分片。
