# Shapefile Reader 兼容性矩阵

## 仓内确定性夹具

测试运行时由 `TestShapefileBuilder` 在临时目录生成最小 `.shp/.shx/.dbf/.cpg/.prj`，生产 Jar 不包含 Builder 或二进制测试数据。

| 能力 | 覆盖内容 | 自动验收 |
| --- | --- | --- |
| 组件与生命周期 | 必需组件、可选元数据、大小写冲突、符号链接、普通文件、重复关闭、Dataset 关闭 Cursor/Source | `ShapefileOptionsAndSourceTest`、`ShapefileDatasetTest`、`ShapefileSourceTest` |
| Header 与 SHX | 大小端、签名、保留字段、版本、文件长度、Shape 类型、范围一致、完整索引项、偏移、重叠、SHP 记录长度 | `ShapefileStructuralValidationTest`、`ShapefileMalformedInputTest` |
| DBF Schema | 字段顺序和源大小写、Charset 优先级、字段类型、重复/空字段、记录数、描述符终止符 | `ShapefileDatasetTest`、`ShapefileOptionsAndSourceTest`、`ShapefileStructuralValidationTest` |
| DBF 记录 | 精确整数/小数、中文、前导空格、null、逻辑、闰日、删除记录、非法 marker/数值/日期/逻辑值 | `ShapefileDatasetTest`、`ShapefileStructuralValidationTest` |
| Geometry | Point/MultiPoint/Polyline/Polygon 的 XY/Z/M，Z 的可选 M，多 part/ring，Null Shape，M NoData，源顺序 | `ShapefileGeometryTest`、`ShapefileDatasetTest` |
| 损坏输入与上限 | 截断、尾随字节、记录号/类型不一致、非法 part start、未闭合 ring、恶意 count、字段/记录/元数据/part/point 上限 | `ShapefileStructuralValidationTest`、`ShapefileMalformedInputTest` |
| 架构门禁 | 公开 API 不泄露内部二进制类型；运行时无 Spring/Spark/Scala/Hadoop/GDAL/JNA/Esri 类 | `ShapefileArchitectureTest` |

## S3 来源确定性验收

| 能力 | 覆盖内容 | 自动验收 |
| --- | --- | --- |
| 组件快照 | 三个必需对象、两个可选对象、显式 Key 覆盖、Head 一次、VersionId/ETag | `S3ShapefileConfigurationTest`、`RangeCachingS3ShapefileSourceTest` |
| 条件 Range | 精确 Range、VersionId、If-Match、Content-Length/Content-Range、客户端所有权 | `AwsS3ObjectAccessTest`、`S3HttpRangeIntegrationTest` |
| 缓存 | 块内命中、跨块、最后短块、跨组件共享 LRU、内存上限和淘汰 | `RangeCachingS3ShapefileSourceTest` |
| 一致性与错误 | 缺失组件、403、404、412、416、5xx、短响应、ETag/总长度变化、Source 失败锁存 | `AwsS3ObjectAccessTest`、`RangeCachingS3ShapefileSourceTest` |
| 公开结果 | Point/MultiPoint/Polyline/Polygon 的 Z/M、中文、CPG、PRJ、删除记录、Null Shape与本地逐值一致 | `S3ShapefileDatasetCompatibilityTest` |
| SDK 集成 | JDK 本地 HTTP 端点配合真实同步 `S3Client`，只出现 HEAD 和带 If-Match 的 Range GET | `S3HttpRangeIntegrationTest` |
| 架构门禁 | S3 模块不引入 CRT、Netty 异步客户端、JNI/JNA；核心不包含 AWS 类 | `S3ShapefileArchitectureTest`、`ShapefileArchitectureTest` |

## 外部参考比对

外部夹具仅在开发机本地使用，不提交仓库：

| 参考夹具 | 来源与证据 | 本模块结果 |
| --- | --- | --- |
| `spark-shp-master/src/test/resources/test.shp` | Apache-2.0 项目 `ShpSuite` 使用的三记录 Point 数据；同目录包含 DBF、CPG、PRJ | Java 21 / macOS arm64 下通过：3 个物理/有效记录，7 个字段名称与类型、全部 21 个属性值、3 组 XY 双精度位值、UTF-8 CPG 和原始 PRJ WKT 逐值一致 |

启用命令：

```bash
./mvnw -pl data-scalpel-shapefile \
  -Dshapefile.referenceFixture=/path/to/test.shp \
  -Dtest=ReferenceShapefileCompatibilityTest test
```

Polyline、Polygon、MultiPoint 及 Z/M 的第一版预期清单由仓内确定性夹具表达。发布到新的 JDK/操作系统组合前，应使用 GDAL/QGIS 或 ArcGIS 对同一份本地夹具复核字段、坐标、part/ring 边界和 M NoData；这些工具只用于开发验收，不是运行时或 Maven 依赖。

## 平台状态

| 平台 | 状态 |
| --- | --- |
| macOS arm64 / Java 21 | 默认确定性测试、真实 SDK 本地 HTTP Range 集成与 Apache Point 外部夹具逐值比对通过 |
| Linux amd64 / Temurin Java 21 | 纯 Java 实现无平台专属代码；目标交付环境仍需执行同一模块测试和外部夹具门禁 |

macOS 默认文件系统大小写不敏感，无法同时创建 `roads.shx` 与 `roads.SHX`；该项测试在这种文件系统上按条件跳过。生产解析器仍会枚举目录并在可表示这种冲突的大小写敏感文件系统上明确拒绝。

## 明确边界

- 仅支持已解包且读取期间不变化的组件集合；本地使用同目录同 stem，S3 使用显式或派生的对象 Key。
- S3 的 VersionId/ETag 只能固定单个对象，不能证明 `.shp/.shx/.dbf/.cpg/.prj` 属于同一原子业务版本。
- 使用相同字段描述布局且无 memo 的 dBASE III/IV/V 版本以及 `C/N/F/L/D` 字段可读；memo 版本、外部 DBT/FPT 和其他字段类型明确拒绝。
- Geometry 不执行坐标转换、ring 方向更改、shell/hole 分组、拓扑验证或修复。
- `.prj` 只作为 UTF-8 WKT 文本读取；不推断 EPSG。
- `.sbn/.sbx/.shp.xml` 不参与打开、Schema 或 Cursor 行为。
