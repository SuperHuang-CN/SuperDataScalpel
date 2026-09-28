# FileGDB 格式兼容性

## 范围

本专题维护 `data-scalpel-filegdb` 的实际能力，不承诺覆盖所有 ArcGIS 地理数据库功能。
核心是只依赖 JDK 的只读解析库；Business 负责 ZIP 安全物化、平台类型和 CRS 解释，
Task Engine 负责将已读取值转换为任务运行类型。本次不引入 GDAL 原生生产依赖，
也不通过改写用户 GDB 字节绕过解析问题。

## 编码检查与支持矩阵

| 层次 | 当前支持 | 明确边界 |
| --- | --- | --- |
| 容器 | 本地/S3 的 `.gdb` 组件目录；Business 接受 GDB ZIP | 核心不直接解压；不是 `.mdb`、企业 GDB 或 mobile geodatabase 读取器 |
| 文件头与字段区 | version 3 普通表；完整无符号字段数量；字段区无尾标记或标准 `DE AD BE EF` 尾标记 | 任意未知尾部、截断和越界仍拒绝；不是“忽略剩余字节” |
| 文本 | UTF-8、由表标志声明的 UTF-16LE；中文、空串、emoji；XML 始终 UTF-8 | 非法 UTF-16 字节长度拒绝，不靠猜测编码 |
| 普通字段 | Int16、Int32、Float32、Float64、String、传统 DateTime、OID、Binary、GUID/GlobalID、XML | 新版字段 code 13–16（Int64、DateOnly、TimeOnly、TimestampOffset）暂不支持，明确报 code |
| 索引 | 4/5/6 字节偏移；连续索引；稀疏页 bitmap；删除槽；保留原始 OID | version 4 / 64 位 OID 未支持；所有数量仍受资源限制 |
| 核心几何 | Point、MultiPoint、Polyline、Polygon；XY、XYZ、XYM、XYZM；多 path/ring | 曲线、Multipatch、Raster 不支持；不修复拓扑、不重新组织坐标 |
| 空值与范围 | NULL/空几何、空点两种编码、缺失 Point Z/M、空图层全 NaN 范围、零网格大小 | 部分 NaN 的 XY 范围、倒置范围、非有限坐标和尾随垃圾仍拒绝 |
| 业务 Geometry | 二维 XY；已知 EPSG 或显式手工 EPSG | 核心可读取 Z/M 不等于业务允许 Z/M；不得静默降维或猜测 CRS |
| 压缩 | Business 解压 ZIP 外层容器 | GDB 内部 CDF/SDC 压缩表不支持 |
| 高级 GIS 语义 | 按普通表/要素类读取记录 | 不执行拓扑规则、网络、关系类、子类型/域校验及栅格分析 |

总数扫描实际存储的索引页而不解析几何；稀疏空页不贡献记录数。
读取记录时按逻辑页映射物理页，不能把紧凑存储的页号当成 OID 页号。
1000 仍是预览上限，不是总数。业务初始解析只做样本读取和索引计数，不能据此保证
任意后续记录都可解析；任务完整读取仍保持遇到不支持或损坏记录即失败。

传统 DateTime 的核心公开返回值仍为 `Instant`，不更改库的公开契约；当任务目标为
`TIMESTAMP_NTZ` 时，按 UTC 基准恢复为 `LocalDateTime`，保留原年月日时分秒。

## 2026-09-23 原始数据核验

用户原始 `ghyd.gdb.zip` 未改动；解压到临时目录后直接读取，未修改 `.gdbtable` 长度或尾部。

- 原始表头包含 GDAL 3.9.3 标记；标准字段尾部 `DE AD BE EF` 被旧 Reader 当成多余字节，
  导致业务统一显示“GDB 目录内容无效或当前读取器不支持”。这不是数据不标准的证据。
- 图层 `ghydv`：73,086 条 MultiPolygon XY；索引总数与完整遍历一致。
- 与独立 GDAL 3.12.4 逐条对照：全部属性、OID、ring 点数及顺序、2,228,291 个 XY 坐标点一致。
  坐标比较绝对容差 `1e-8`；浮点属性 `rtol=1e-12, atol=1e-10`。
- GDAL 识别 CRS 为 EPSG:4549；源 WKT 缺少 authority/code。平台不依赖猜测匹配，
  上传时手工设置 EPSG `4549`。这一设置只解释坐标，不做坐标转换。
- 独立生成且未修改的 GDAL 文件覆盖 11 层：点/多点/多线/面各 XY、XYZ，
  UTF-16 中文与 emoji、空图层、普通属性表；27 条记录的属性和坐标均与 GDAL 一致。
- 另用 GDAL 原生 API 生成 OID 为 1、4097 的稀疏索引表：计数为 2，OID 与属性完整一致。

仓库内确定性测试覆盖尾标记、UTF-16/XML、NaN/零网格、稀疏页和损坏 bitmap、
空几何、缺失 Point Z/M、几何类型冲突以及新字段的显式拒绝；既有测试继续覆盖
XY/XYZ/XYM/XYZM、多段线、多环面、标量、来源一致性与读取限制。

验证环境缺少根 `.mvn/maven.config` 指定的 `settings-superhuang.xml`，根 Wrapper
测试命令在进入构建前失败，未替换或绕过 Maven settings。使用 Java 21 与本地现有
JUnit 6 引擎直接编译并执行测试：核心 42 项通过、5 项外部参考夹具测试因未提供路径跳过，
队列分类器 4 项通过；此方式不等于完整 Reactor 构建。
任务 DateTime 转换补充了独立行转换回归测试，但未运行 Task Engine 测试或页面上传联调。
应用未重启，现有已失败的上传任务不会因源码修改自动恢复。

## 后续扩展原则

仍不支持的格式必须准确失败，不以丢字段、丢 Z/M、曲线直线化或数值降精度换取“成功”。
64 位 OID 涉及当前公开 `int oid` 契约；新版时间类型涉及平台语义；曲线、Multipatch、
Raster 涉及模型与执行链路。扩展这些能力需单独确定契约和验收夹具，不能作为本次编码
容错的隐式行为变化。采用 GDAL/Esri SDK 作为生产后备读取器属于新增生产依赖，需先确认。

参考：[GDAL OpenFileGDB 官方文档](https://gdal.org/en/stable/drivers/vector/openfilegdb.html)、
[本模块格式实现参考](../../data-scalpel-filegdb/THIRD-PARTY-NOTICES.md)。
