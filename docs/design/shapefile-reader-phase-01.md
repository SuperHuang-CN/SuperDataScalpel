# 纯 Java Shapefile Reader 第一版设计

> 状态：已实现。本文记录独立模块 `data-scalpel-shapefile` 的公开契约、格式范围、安全边界和验证结论。核心后来增加了通用随机访问 Source，并由独立模块提供 S3 Range 来源，见[本地与 S3 双来源设计](shapefile-reader-local-s3-sources.md)。平台文件数据集接入仍未开发，见[空间文件数据集解析决策](geospatial-file-dataset-parsing.md)。

## 目标与模块边界

第一版提供 Java 21、只读、受限、运行时零第三方依赖的 Shapefile Reader。本地入口接收已解包的 `.shp` 路径，在同一目录定位同名 `.shx/.dbf`，并可选读取 `.cpg/.prj`。格式解析现统一依赖 `ShapefileSource`，核心运行时依赖仍为零。

模块不依赖 `data-scalpel-filegdb`，也没有新增共享 Geometry 模块。两个 Reader 的模型在各自格式边界内独立，避免在平台接入需求尚未出现时提前冻结跨格式抽象。

第一版不包含：

- ZIP 解包、图层发现、文件数据集适配、异步任务、REST API 或前端；S3 只在独立适配模块提供底层来源，不表示平台接入；
- Spark、Scala、Hadoop、GDAL、JNI/JNA、Esri SDK 或 Java GIS 框架；
- 坐标转换、空间过滤、空间索引查询、几何/拓扑修复和写入；
- GeoJSON、WKT、WKB 输出；`.prj` 的 WKT 仅原样暴露，不解释 EPSG；
- `.sbn/.sbx/.shp.xml` 等扩展文件。

## 公开 API

`ShapefileDataset` 代表一组同名组件。打开时形成不可变 `ShapefileSchema`，Cursor 使用 SHX 物理槽位流式读取：

```java
try (ShapefileDataset dataset = ShapefileDataset.open(shpPath, options)) {
    ShapefileSchema schema = dataset.schema();
    try (ShapefileFeatureCursor cursor = dataset.openCursor(
            ShapefileReadOptions.limit(1_000))) {
        while (cursor.hasNext()) {
            ShapefileFeature feature = cursor.next();
        }
    }
}
```

行为契约：

- `open(Path)` 与 `open(Path, ShapefileOpenOptions)` 只接受 `.shp` 文件；
- `open(ShapefileSource, ShapefileOpenOptions)` 是本地和远程的统一入口，Dataset 在调用后取得并关闭 Source；
- 必需组件缺失、同名大小写冲突、非普通文件或默认禁止的符号链接会在打开阶段失败；
- Cursor 必须指定正数 Feature 上限且不得超过 Dataset 的限制；没有“读取全部”入口；
- Feature 的 `recordNumber` 是从 1 开始的物理 SHX/DBF 槽位，不因删除记录重排；
- Dataset/Cursor 均可幂等关闭；Dataset 先关闭所属 Cursor，再关闭 DBF、SHX、SHP 对象与 Source；
- 单实例不承诺线程安全；独立实例可读取同一组不可变文件；
- 单条记录失败后 Cursor 保存同一个 `ShapefileException`，后续读取稳定失败，不返回部分 Feature。

## 公开模型

Schema 公开精确 `ShapefileShapeType`、SHX 物理槽位数、DBF 字段、文件 Header 范围、最终 DBF Charset 和可选 `ShapefileSpatialReference`。

`ShapefileFeature` 包含物理记录号、保持 Schema 顺序的不可修改属性 Map，以及可空 `ShapefileGeometry`。Null Shape 使用 Java `null` 表达。

`ShapefileGeometry` 是 sealed interface：

- Point 保存 `x/y` 以及可选 `z/m`；
- MultiPoint 保存一个连续 `ShapefileCoordinateSequence`；
- Polyline/Polygon 保存 part/ring 点数数组和连续坐标；
- primitive array 在构造和公开访问时防御性复制；
- 源坐标、点顺序、part/ring 边界和 ring 方向保持不变；
- M 小于 Shapefile NoData 阈值 `-10^38` 时转换为 `Double.NaN`。

## 组件解析与输入安全

入口路径先转为绝对规范化路径，再枚举父目录。扩展名和同 stem 匹配不区分 ASCII 大小写；每种已知扩展只允许一个候选。`.shp/.shx/.dbf` 必需，`.cpg/.prj` 可选。

默认使用 `NOFOLLOW_LINKS` 验证普通文件并拒绝符号链接。调用方显式允许符号链接时，仍要求链接目标为可读普通文件。所有组件在打开前检查配置大小上限。

本地来源只使用只读 `FileChannel`，不使用内存映射。`RandomAccessObjectReader` 包装 `ShapefileRandomAccessObject`，使用绝对 offset 循环执行 `readFully`，不共享可变 position。位置、长度和 word→byte 换算使用 `long` 与 checked arithmetic；短读、负位置、越界和溢出立即归类。`.cpg/.prj` 也通过相同 Source 读取，不再使用 Path 旁路。

## SHP/SHX 验证

打开阶段解析两个 100 字节 Header：

- 大端 file code 必须为 `9994`，五个保留整数必须为零；
- 小端版本必须为 `1000`；
- 无符号 32 位 16-bit word 文件长度换算后必须等于实际大小；
- Shape 类型必须属于 Null、Point、MultiPoint、Polyline、Polygon 的 XY/Z/M 集合；MultiPatch 和未知类型明确拒绝；
- SHP/SHX Shape 类型与八项 XY/Z/M 主范围必须一致；
- SHX Header 后必须是完整的 8 字节项序列。

打开阶段遍历 SHX 项，但不解析 Geometry 内容。每项验证：

- 无符号 word offset/content length 的换算不溢出；
- 记录头不落入 SHP Header，记录范围不越界；
- offset 不倒退、不与前一记录重叠；
- 指向的 SHP 8 字节记录头存在，且其中内容长度与 SHX 一致；
- 记录长度和总槽位数不超过配置上限。

Cursor 访问槽位时再验证 SHP 记录号为 `slot + 1`、读取并解析该记录。这样抽样读取不必在打开时扫描全部 Geometry 和属性值。

## DBF Schema、编码与值

第一版读取使用相同字段描述布局、无 memo 的 dBASE III/IV/V (`0x03/0x04/0x05`) Header。需要 DBT/FPT 的版本和 `M` 等未支持字段明确返回 `UNSUPPORTED_FORMAT`。

Header 验证记录数、Header 长度、记录长度、32 字节字段描述符布局、`0x0D` 终止符、字段总长度以及声明记录范围。DBF 记录数必须与 SHX 槽位数一致；末尾 `0x1A` 可有可无。

Charset 优先级固定为：调用方覆盖 → `.cpg` → 已知 Language Driver ID → 默认 `GB18030`。`.cpg` 先按严格 UTF-8 解码，删除 BOM 和首尾空白，再解析 JDK Charset 名称或常见数字代码页；存在但非法时不降级。

字段名按最终 Charset 严格解码并保留源大小写。解码后空白或重复字段名在打开阶段失败。

| DBF | Schema | Java |
| --- | --- | --- |
| `C` | `STRING` | `String` |
| `N` 且小数位 0 | `INTEGER` | `BigInteger` |
| `N` 且有小数位 | `DECIMAL` | `BigDecimal` |
| `F` | `DECIMAL` | `BigDecimal` |
| `L` | `BOOLEAN` | `Boolean` |
| `D` | `DATE` | `LocalDate` |

字符值只删除右侧 ASCII 填充空格，保留有意义的左侧空格。数值完整解析为精确类型。逻辑值只接受 `T/Y/F/N/?/空白`，日期严格按 `yyyyMMdd` 形成有效日历日期。空字段返回 `null`。非法值错误只携带安全字段名和物理记录号，不包含记录内容。

DBF 删除标志为 `0x2A` 时 Cursor 跳过返回，但 SHP/SHX/DBF 仍按同一物理槽位推进。其他非活动标志视为损坏。

## Geometry 结构验证

支持类型码：

- `0` Null Shape；
- `1/11/21` Point / PointZ / PointM；
- `8/18/28` MultiPoint / MultiPointZ / MultiPointM；
- `3/13/23` Polyline / PolylineZ / PolylineM；
- `5/15/25` Polygon / PolygonZ / PolygonM；
- `31` MultiPatch：识别后稳定拒绝。

非 Null 记录类型必须与文件 Header 一致，且内容必须完全消费。point/part 数、坐标数组以及 Z/M block 的字节需求在分配前用 checked arithmetic 验证。

Multipart 的首个 part start 必须为 0，后续 start 严格递增且全部落在 point 范围内。Polyline 每个 part 至少两点；Polygon 每个 ring 至少四点且首尾 XY 数值相等。第一版不判断 ring 方向、shell/hole 归属或拓扑有效性。

Z 类型必须包含 Z range 与 Z 数组，可以完全省略 M block；只要出现 M 数据，就必须包含完整 range 和数组。M 类型必须包含完整 M block。range 只用于结构验证和 Geometry envelope，不用来改写坐标。

## 资源上限

| 限制 | 固定内建上限 |
| --- | ---: |
| 单组件大小 | 4 GiB |
| 字段数 | 1,024 |
| 单 SHP 记录内容 | 64 MiB |
| CPG/PRJ | 1 MiB |
| part/ring | 100,000 |
| 单 Geometry 点数 | 10,000,000 |
| SHX 槽位数 | 100,000,000 |
| 单 Cursor Feature | 100,000 |

调用方只能收紧，不能扩大这些上限。格式本身限制更小时取更小值。

## 异常契约

所有格式与 I/O 失败使用 `ShapefileException`：

- `INVALID_SOURCE`、`MISSING_COMPONENT`；
- `UNSUPPORTED_FORMAT`、`MALFORMED_HEADER`、`RECORD_MISMATCH`；
- `INVALID_OFFSET`、`TRUNCATED_INPUT`、`LIMIT_EXCEEDED`；
- `INVALID_ENCODING`、`IO_ERROR`、`CLOSED`。
- 远程来源版本或 ETag 在读取期间变化时使用 `SOURCE_CHANGED`。

打开失败会关闭已取得的随机访问对象与 Source，并将关闭失败加入原异常的 suppressed 列表。错误消息只说明文件角色、字段名和记录号等定位信息，不输出属性原文。

## 来源与验证结论

二进制实现依据公开 Esri Shapefile 与 dBASE 布局重新表达。`spark-shp-master` 作为 Apache-2.0 参考实现，仅用于理解读取顺序和外部 Point 夹具比对；未复制 Spark DataSource、RDD、Hadoop、Scala 或 Esri Geometry 代码。通知与许可证随 Jar 写入 `META-INF`。

默认测试运行时生成全部组件，覆盖 12 种非空 Shape 变体、Null、DBF 类型/编码/删除、损坏 Header/索引/记录、数组分配前检查、生命周期、内存 Source 与架构门禁。外部 Apache Point 夹具逐字段、逐坐标比对通过。S3 适配模块另行验证真实同步 SDK 的 Head/Range 请求和本地/S3 逐值一致性。完整结果见模块[兼容性矩阵](../../data-scalpel-shapefile/docs/compatibility-matrix.md)。
