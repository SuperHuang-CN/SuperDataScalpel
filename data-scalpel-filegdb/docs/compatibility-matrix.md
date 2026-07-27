# FileGDB Reader 兼容性矩阵

## 仓内确定性夹具

测试运行时由 `TestFileGdbBuilder` 生成两个最小、未压缩的真实目录结构，不保存来源不明的二进制数据：

| 夹具 | 覆盖内容 | 自动验收 |
| --- | --- | --- |
| `scalar-and-point.gdb` | 系统目录、4/5/6 字节索引、删除槽位、全部阶段一标量类型、null、空字符串、UTF-8、UUID/GUID、Binary/XML、Point XY/XYZ/XYM/XYZM | `FileGeodatabaseTest`、`GdbTableIndexReaderTest`、`GdbTableHeaderReaderTest` |
| `polygon.gdb` | 双 ring Polygon、XY/XYZ/XYM/XYZM、Z/M delta、缺失 M 数组标记、extent | `GdbGeometryReaderTest` |
| `multipoint-and-polyline.gdb` | MultiPoint 与多 path Polyline、XY/XYZ/XYM/XYZM、空 Geometry、源坐标顺序、Z/M delta、单点与整数组缺失 M、extent | `GdbGeometryReaderTest`、`FileGdbReadLimitsTest` |

同一组生成夹具还会通过内存 `FileGdbSource` 和 S3 Range 来源完整读取。`FileGdbSourceTest`、`RangeCachingS3FileGdbSourceTest` 与 `S3HttpRangeIntegrationTest` 对比本地和抽象来源的图层、Schema、字段、OID、属性以及四类 Geometry 结果。

## 存储来源兼容性

| 来源 | 支持形式 | 自动验收 |
| --- | --- | --- |
| 本地 | 已解包 `.gdb` 目录；默认拒绝符号链接 | 核心模块全部测试与外部参考夹具 |
| AWS S3 | 已解包 `.gdb` 对象前缀；HeadObject + Range GetObject | SDK 请求捕获测试、JDK 本地 HTTP 端点完整夹具测试 |
| MinIO / S3-compatible | 调用方配置 Endpoint、Region、凭证和 path-style | 与 AWS 相同的 HTTP 契约测试；真实服务通过 opt-in 测试验证 |

S3 自动测试覆盖 64 KiB～8 MiB 块约束、共享 LRU、最后短块、跨块读取、无完整对象 GET、VersionId、ETag/If-Match、404/403/412/416/5xx、短响应、对象超限、压缩表和关闭生命周期。真实 S3/MinIO 测试默认跳过且只读现有对象。

损坏输入覆盖压缩签名、缺失物理文件、越界偏移、超大记录、Geometry 点/part 上限、非法 path 大小、Geometry 截断、delta 溢出、尾随字节、曲线标志以及截断/溢出变长整数。

## 外部参考比对

下列文件只在开发机本地使用，不提交到本仓库：

| 参考夹具 | 来源证据 | 本模块比对结果 |
| --- | --- | --- |
| `FileGDB-master/src/test/resources/test.gdb.zip` | Apache-2.0 参考项目 `GDBSuite` 明确断言 `Random` 为 4,710 条 | 图层 `test`、`Random`、Point Schema、首条属性/坐标和 4,710 条计数一致 |
| `FileGDB-master/data/FileGDBTest.gdb.zip` | 参考项目 `FileGDB_MZ.ipynb` 输出目录 ID 21 `PolylineM`、ID 22 `PolygonZ`，并由 `GDBSuite` 完整扫描 `PolygonZ` | 目录 ID/名称/类型一致；PolylineM 三条记录可读，分别验证完整 M、整数组缺失 M 和单点缺失 M；PolygonZ 两条有效记录和 Z 坐标可完整读取 |
| `FileGDB-master/data/Miami.gdb` | 参考项目 `GDBSuite` 完整扫描 `Broadcast`；README 将其作为示例 | 普通表、Timestamp、Point、Polygon 可读；删除槽位后 `Broadcast` 首个有效 OID 为 11 |
| `FileGDB-master/data/World.gdb` | 目录包含 `a0000000b.gdbtable.cdf` | 打开时稳定返回 `UNSUPPORTED_FORMAT`，不把压缩内容当作普通表 |

外部精确断言保存在 `ReferenceFileGdbCompatibilityTest`，默认因未提供路径而跳过；通过 `filegdb.referenceTestFixture`、`filegdb.referenceMzFixture`、`filegdb.referenceMiamiFixture` 和 `filegdb.referenceCompressedFixture` 启用。

## 平台状态

| 平台 | 状态 |
| --- | --- |
| macOS arm64 / Java 21 | 核心与 S3 模块测试、本地 HTTP Range 集成和外部参考夹具比对通过 |
| Linux amd64 / Temurin Java 21 | 同一核心 Jar 打开 `Miami.gdb` 通过；核心模块与全部外部参考夹具比对通过。S3 适配器为纯 Java，仍需在目标环境执行同一测试门禁 |

## 明确边界

- `TABLE`、`POINT`、`MULTIPOINT`、`POLYLINE`、`POLYGON` 可以打开游标；`MULTIPATCH`、`UNKNOWN` 只允许列出 Schema，打开游标返回 `UNSUPPORTED_FORMAT`。
- M extent 允许使用 FileGDB 的 `NaN` 表示未定义；M 坐标数组缺失时每个 M 值返回 `Double.NaN`。
- Timestamp 按 FileGDB/OLE Automation 的 1899-12-30 纪元转换，并舍入到毫秒后返回 UTC `Instant`。
- Geometry 保持源坐标和点/path/ring 顺序，不进行方向修复、拓扑修复、简化或投影转换。
- S3 前缀必须在发布后保持不可变；VersionId/ETag 只提供单对象一致性，不提供多对象原子快照。
- S3 不支持 ZIP、磁盘缓存、整表下载、异步预取或 `ListObjects` 发现。
