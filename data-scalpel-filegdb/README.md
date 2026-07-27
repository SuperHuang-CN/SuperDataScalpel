# DataScalpel FileGDB Reader

`data-scalpel-filegdb` 是独立的 Java 21 只读 File Geodatabase 解析库。运行时只依赖 JDK，不依赖 DataScalpel 业务模块、Spring、Spark、Hadoop、GDAL、JNI、JNA 或 Esri SDK。核心模块提供本地目录读取和精简的随机访问来源 SPI；可选的 S3 Range 适配器位于 `data-scalpel-filegdb-s3`。

## 使用方式

```java
Path directory = Path.of("sample.gdb");

try (FileGeodatabase database = FileGeodatabase.open(directory)) {
    for (FileGdbLayer layer : database.layers()) {
        FileGdbSchema schema = database.schema(layer.id());
        if (!layer.type().isCursorReadable()) {
            continue;
        }
        try (FileGdbFeatureCursor cursor = database.openCursor(
                layer.id(), FileGdbReadOptions.limit(1_000))) {
            while (cursor.hasNext()) {
                FileGdbFeature feature = cursor.next();
            }
        }
    }
}
```

也可以传入 `FileGdbSource`：

```java
try (FileGeodatabase database = FileGeodatabase.open(source, FileGdbOpenOptions.defaults())) {
    FileGdbSourceInfo sourceInfo = database.sourceInfo();
}
```

`FileGeodatabase.open(source, options)` 从调用开始即取得来源所有权；打开失败或数据库关闭时都会关闭来源。数据库先关闭尚未关闭的游标，再关闭来源。`allowSymbolicLinks` 只影响本地来源。

游标必须提供正数上限，且不能超过 `FileGdbReadLimits`。数据库和游标都实现 `AutoCloseable`。

## 当前读取范围

支持：

- 已解包、未使用 FileGDB 内部表压缩的 `.gdb`；
- 本地目录，以及通过 `FileGdbSource` 提供的随机访问对象集合；
- 系统目录、`.gdbtable`、`.gdbtablx` 及 4/5/6 字节记录偏移；
- 普通表、Point、MultiPoint、Polyline 和 Polygon 图层发现与 Schema；
- `INT16`、`INT32`、`FLOAT32`、`FLOAT64`、`STRING`、`TIMESTAMP`、`OID`、`UUID/GUID`、`BINARY`、`XML`；
- Point、MultiPoint、Polyline、Polygon 的 XY、XYZ、XYM、XYZM；
- MultiPoint 源点顺序、Polyline path 边界、Polygon ring 边界，以及缺失 M 的 `Double.NaN` 表达；
- 空值位图、删除槽位、稳定 OID、资源上限和分类异常。

暂不支持：

- FileGDB 内部压缩表、ZIP 输入、加密内容或损坏修复；
- Multipatch、Raster 和曲线段；
- 属性/空间过滤、坐标转换、空间索引查询；
- GeoJSON、WKB、WKT 输出；
- ZIP、通用 HTTP、`InputStream` 或业务系统集成；S3 对象前缀由可选适配模块支持。

遇到目录结构错误、压缩表、越界偏移、截断内容或资源超限时，读取器抛出带稳定 `FileGdbErrorCode` 的 `FileGdbException`。单条记录解析失败会终止游标，不返回全空或部分成功记录。

## 验证

```bash
./mvnw -pl data-scalpel-filegdb test
```

本地与 S3 双来源验证：

```bash
./mvnw -pl data-scalpel-filegdb,data-scalpel-filegdb-s3 -am test
```

外部参考夹具不会提交到本仓库。得到授权并解包后，可额外运行：

```bash
./mvnw -pl data-scalpel-filegdb \
  -Dfilegdb.referenceTestFixture=/path/to/test.gdb \
  -Dfilegdb.referenceMzFixture=/path/to/FileGDBTest.gdb \
  -Dfilegdb.referenceMiamiFixture=/path/to/Miami.gdb \
  -Dfilegdb.referenceCompressedFixture=/path/to/World.gdb \
  -Dtest=ReferenceFileGdbCompatibilityTest test
```

S3 使用方式见 [`data-scalpel-filegdb-s3/README.md`](../data-scalpel-filegdb-s3/README.md)。Geometry 解码决定见 [MultiPoint 与 Polyline 游标读取设计](../docs/design/filegdb-reader-multipoint-polyline.md)；参考来源、夹具结果和已知边界见 [兼容性矩阵](docs/compatibility-matrix.md) 与 [第三方通知](THIRD-PARTY-NOTICES.md)。
