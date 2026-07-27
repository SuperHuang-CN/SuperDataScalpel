# FileGDB MultiPoint 与 Polyline 游标读取

## 1. 定位

本阶段在纯 Java FileGDB Reader 以及本地/S3 双来源能力之上增加 MultiPoint 与 Polyline Geometry 解码。模块边界保持不变：核心只依赖 JDK，S3 适配器只负责随机 Range 读取，不接入 DataScalpel 业务模块、Spring 或 REST API。

完成后，普通表以及 Point、MultiPoint、Polyline、Polygon 图层可以打开受限游标；Multipatch、Raster、曲线段和未知 Geometry 仍明确拒绝。

## 2. 公开模型

- `FileGdbMultiPoint` 保存 XY envelope 和按源顺序排列的 `FileGdbCoordinateSequence`。
- `FileGdbPolyline` 保存 XY envelope、每个 path 的点数和连续坐标序列。path 点数数组防御性复制，且必须全部为正并恰好覆盖坐标序列。
- 两个类型加入 sealed `FileGdbGeometry`，实现值语义的 `equals/hashCode`。
- `FileGdbLayerType.isCursorReadable()` 表示图层是否允许打开游标，取代带阶段含义的旧方法。
- `FileGdbReadLimits.maxGeometryParts` 同时限制 Polygon ring 与 Polyline path，默认值仍为 100,000；`maxGeometryPoints` 统一限制全部多点 Geometry。

空 Geometry 继续返回 Java `null`。空间参考属于 Schema，不在每条 Geometry 中重复保存。

## 3. 二进制解码

MultiPoint 的读取顺序为 encoded type、点数、XY envelope、XY delta、可选 Z delta、可选 M delta。Polyline 与 Polygon 共用 multipart 读取顺序：点数、part 数、envelope、前 `partCount - 1` 个 part 点数、XY/Z/M delta。

共同规则：

- 点数为零时返回空 Geometry；
- XY/Z/M delta 在整个坐标序列中连续累计，不在 path 或 ring 边界重置；
- M 数组以 `0x42` 开始时全部返回 `Double.NaN`，累计 M 量化值为 `-1` 时只将对应点返回为 `Double.NaN`；
- 数量在分配数组前应用资源限制，part 大小必须为正且总和必须等于点数；
- Geometry blob 必须完全消费，截断、尾随数据、加法溢出和非有限坐标作为损坏格式拒绝；
- encoded type 继续只检查 `0x20000000` 曲线标志，Geometry 分类与 Z/M 维度以表 Schema 为准，兼容参考夹具中的 `PolylineM` 编码 `0x17`。

曲线返回 `UNSUPPORTED_FORMAT`。已完整读取到记录内存后的 Geometry 结构截断返回 `MALFORMED_HEADER`；S3 Range 本身的短响应仍由来源层返回 `TRUNCATED_INPUT`。

## 4. 来源一致性

Geometry 解码只依赖 `FileGdbRandomAccessObject` 提供的记录字节，因此本地、内存来源和 S3 Range 来源共享同一实现。S3 不增加 Geometry 专用请求，不改变 HeadObject、Range GET、VersionId/ETag、LRU 或生命周期契约。

确定性夹具 `multipoint-and-polyline.gdb` 覆盖两类 Geometry 的 XY、XYZ、XYM、XYZM、多 path、空 Geometry、完整 M、整数组缺失 M 和单点缺失 M。本地、内存来源、内存 S3 替身与真实 `S3Client` 本地 HTTP 端点比较相同公开结果。

外部 `FileGDBTest.gdb` 的 `PolylineM` 用于验证三条真实记录：完整 M 数组、`0x42` 缺失数组，以及部分点 M 为 `NaN`。外部夹具不提交仓库，只通过系统属性启用。

## 5. 明确边界

- 不读取 Multipatch、Raster、曲线段或 FileGDB 内部压缩表；
- 不支持 ZIP 输入、坐标转换、空间过滤、空间索引查询；
- 不连接 path、不改变方向、不简化或修复拓扑；
- 不输出 GeoJSON、WKB 或 WKT；
- 不增加生产依赖、Maven 模块或平台相关原生库。
