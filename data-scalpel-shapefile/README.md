# DataScalpel Shapefile Reader

`data-scalpel-shapefile` 是独立的 Java 21 只读 Shapefile 解析库。运行时只依赖 JDK，不依赖 DataScalpel 业务模块、Spring、Spark、Scala、Hadoop、GDAL、JNI/JNA 或 Esri Geometry API。

模块通过位置无关的 `ShapefileSource` 读取已解包的同名 `.shp/.shx/.dbf` 组件，可选读取 `.cpg/.prj`。核心自带本地 Path 来源；S3 Range 来源位于可选的 `data-scalpel-shapefile-s3` 模块。核心不处理 ZIP、远程 URL、坐标转换、几何修复、空间查询或写入。

## 使用方式

```java
Path shp = Path.of("roads.shp");

try (ShapefileDataset dataset = ShapefileDataset.open(shp)) {
    ShapefileSchema schema = dataset.schema();
    try (ShapefileFeatureCursor cursor = dataset.openCursor(
            ShapefileReadOptions.limit(1_000))) {
        while (cursor.hasNext()) {
            ShapefileFeature feature = cursor.next();
            long physicalRecordNumber = feature.recordNumber();
            Map<String, Object> attributes = feature.attributes();
            ShapefileGeometry geometry = feature.geometry(); // Null Shape 时为 null
        }
    }
}
```

每个 Cursor 都必须给出正数 Feature 上限。上限按未删除的有效 DBF 记录计数；DBF 删除记录仍占物理槽位，但不会返回 Feature。Dataset 与 Cursor 都应使用 try-with-resources 关闭，重复关闭是安全的；关闭 Dataset 会先关闭尚未关闭的 Cursor。单个实例不承诺线程安全，多个独立实例可读取同一组不再变化的文件。

自定义或远程来源通过统一入口打开：

```java
ShapefileSource source = ...;
try (ShapefileDataset dataset = ShapefileDataset.open(source, options)) {
    ShapefileSourceInfo sourceInfo = dataset.sourceInfo();
}
```

调用 `open(source, options)` 后 Dataset 取得 Source 所有权；打开失败也会关闭 Source。Source 必须拥有并幂等关闭其创建的 `ShapefileRandomAccessObject`。AWS SDK 类型不会进入核心 API，S3 调用示例见 [`data-scalpel-shapefile-s3`](../data-scalpel-shapefile-s3/README.md)。

## 输入和编码

- `open(Path)` 只接受 `.shp` 路径，扩展名按 ASCII 大小写不敏感匹配。
- `.shp`、`.shx`、`.dbf` 必须位于同一目录并具有同名 stem；`.cpg`、`.prj` 可选。
- 同一组件存在大小写冲突时拒绝打开。
- 默认拒绝任何组件为符号链接；只有显式设置 `allowSymbolicLinks=true` 才允许。
- `.prj` 作为未解释的 WKT 字符串公开，不识别 EPSG，也不转换坐标。
- `.sbn`、`.sbx`、`.shp.xml` 和其他扩展文件被忽略。

DBF Charset 按以下固定顺序解析：

1. `ShapefileOpenOptions.dbfCharsetOverride()`；
2. `.cpg` 声明；
3. 已知 DBF Language Driver ID；
4. `dbfFallbackCharset`，默认 `GB18030`。

`.cpg` 支持 JDK Charset 名称、UTF-8 BOM，以及常见的数字/`CP` 代码页写法。存在但为空、不是有效 UTF-8 文本或声明 JDK 不支持的 Charset 时抛出 `INVALID_ENCODING`，不会静默降级。

## 支持矩阵

| 内容 | 第一版行为 |
| --- | --- |
| Null Shape | 支持，`feature.geometry()` 返回 `null` |
| Point | 支持 XY、Z、M；PointZ 的 M 可选 |
| MultiPoint | 支持 XY、Z、M；Z 类型的 M block 可选 |
| Polyline | 支持 XY、Z、M 和 multipart；Z 类型的 M block 可选 |
| Polygon | 支持 XY、Z、M 和多 ring；Z 类型的 M block 可选 |
| MultiPatch | 识别后抛出 `UNSUPPORTED_FORMAT` |
| Polygon ring | 保留源顺序和方向；仅要求至少四点且首尾 XY 闭合 |
| M NoData | 小于 `-10^38` 的值返回 `Double.NaN` |
| ZIP | 不支持，调用方必须提供已解包组件 |
| 远程对象 | 核心通过 Source SPI 支持；S3 由独立适配模块提供 |
| 输出格式 | 仅返回模块自有模型，不输出 GeoJSON、WKT 或 WKB |

Geometry 使用 primitive array 保存连续坐标。构造和数组访问均执行防御性复制；Polyline/Polygon 以每个 part/ring 的点数数组保留原始边界，不重排 shell/hole，也不执行拓扑检查或修复。

## DBF 类型

| DBF 类型 | Schema 类型 | Java 值 |
| --- | --- | --- |
| `C` | `STRING` | `String` |
| `N`，小数位为 0 | `INTEGER` | `BigInteger` |
| `N`，有小数位 | `DECIMAL` | `BigDecimal` |
| `F` | `DECIMAL` | `BigDecimal` |
| `L` | `BOOLEAN` | `Boolean` |
| `D` | `DATE` | `LocalDate` |

字符字段只删除右侧 ASCII 填充空格；数值使用精确类型解析。空白值返回 `null`。第一版接受使用相同字段描述布局且不带 memo 的 dBASE III/IV/V 版本；需要 DBT/FPT 的版本以及不支持的字段类型会明确失败，不退化为字符串。

## 内建安全上限

| 限制 | 默认上限 |
| --- | ---: |
| 单组件文件大小 | 4 GiB |
| DBF 字段数 | 1,024 |
| 单条 SHP 内容 | 64 MiB |
| `.cpg/.prj` 字节数 | 1 MiB |
| 单 Geometry part/ring 数 | 100,000 |
| 单 Geometry 坐标点数 | 10,000,000 |
| SHX 物理记录槽位数 | 100,000,000 |
| 单 Cursor 返回 Feature 数 | 100,000 |

`ShapefileOpenOptions` 可通过 `ShapefileReadLimits` 收紧这些上限，不能扩大或绕过内建上限。所有计数和长度在分配数组、字符串或进入不可信循环前验证。

## 错误与验证

解析失败抛出 `ShapefileException`，并通过 `ShapefileErrorCode` 提供稳定分类：`INVALID_SOURCE`、`MISSING_COMPONENT`、`UNSUPPORTED_FORMAT`、`MALFORMED_HEADER`、`RECORD_MISMATCH`、`INVALID_OFFSET`、`INVALID_ENCODING`、`LIMIT_EXCEEDED`、`TRUNCATED_INPUT`、`SOURCE_CHANGED`、`IO_ERROR`、`CLOSED`。

默认测试使用运行时生成的确定性组件：

```bash
./mvnw -pl data-scalpel-shapefile test
```

Apache-2.0 参考项目的外部 Point 夹具不提交仓库，可通过系统属性启用逐字段、逐坐标兼容性测试：

```bash
./mvnw -pl data-scalpel-shapefile \
  -Dshapefile.referenceFixture=/path/to/test.shp \
  -Dtest=ReferenceShapefileCompatibilityTest test
```

实现与格式边界见[第一版设计文档](../docs/design/shapefile-reader-phase-01.md)，本地/S3 来源设计见[双来源设计](../docs/design/shapefile-reader-local-s3-sources.md)，参考来源和验证结果见[兼容性矩阵](docs/compatibility-matrix.md)与[第三方通知](THIRD-PARTY-NOTICES.md)。
