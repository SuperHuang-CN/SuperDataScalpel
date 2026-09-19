# 空间文件数据集解析

## 1. 上传与存储

SHP、GDB、GeoJSON、GEOJSONL、GeoParquet 和 GeoPackage 都进入统一文件数据集解析队列：

- SHP 接受 ZIP；
- GDB 接受 ZIP；
- GeoJSON 接受 `.geojson`、`.json` 及其 `.gz` 外层压缩；
- GEOJSONL 接受 `.geojsonl`、`.ndgeojson`、`.jsonl`、`.ndjson` 及其 `.gz` 外层压缩；
- GeoParquet 只接受单个 `.parquet` 文件，允许 Parquet 内部压缩，不接受外层 GZIP、ZIP 或目录上传；
- GeoPackage 只接受单个 `.gpkg` 文件，不接受外层压缩、目录或 WAL/SHM 配套文件；
- 原归档保存在现有 `file-datasets/...` Object Key；
- 准备任务在独立物化前缀发布安全解压后的组件或 GDB 目录；GPKG 只读发现表但不物化，GeoJSON/GEOJSONL/GeoParquet 也不需要物化；
- 不改变 MinIO Key 组织方式。

归档准备完整检查路径穿越、条目数、单条目大小、总展开大小和压缩比。准备最终失败时立即删除
文件记录、原归档和未发布物化目录。

## 2. SHP

一个 SHP 归档表示一个逻辑表，至少包含 `.shp/.shx/.dbf`，可选 `.prj/.cpg`。归档允许携带与核心
组件同目录、同名的常见空间索引和元数据辅助文件（如 `.sbn/.sbx/.qix/.shp.xml`），准备时忽略
这些辅助文件，只物化核心组件。准备成功后创建逻辑表和初始校验 Job；来源只在完整校验成功后创建。

权威校验包括：

- DBF 字段数量、名称、顺序、平台类型和 nullable；
- Shape 类型以及 Z/M 维度；
- Geometry 字段；
- 规范化 PRJ WKT；
- DBF 字符集及必要来源元数据。

Geometry 字段保存为公共 `PlatformTypeDefinition(GEOMETRY)`，其中包含 kind、EPSG CRS 和
coordinate dimension；管理端属性预览不返回 Geometry 字段或坐标值。Canvas 和 Runner 始终使用
Sedona/JTS Geometry，不把 Geometry 降级为 JSON 或 String。

Extent 和记录数不参与 Schema 一致性。SHP 支持表级追加、全量覆盖和单来源替换；所有来源必须
满足同一权威 Schema 和空间元数据约束。

## 3. GDB

一个 GDB 归档可以发现多个图层，每个图层创建独立逻辑表并共享同一个物理文件和物化目录。
当前 GDB 仅支持整文件上传和破坏性替换，不开放图层级追加或覆盖。

GDB 的 `SHAPE` 字段同样保存为公共 Geometry 类型。点、多点、折线和面分别映射为 `POINT`、
`MULTIPOINT`、`MULTILINESTRING` 和 `MULTIPOLYGON`，其他字段继续按无损平台类型保存。

整文件替换提交后立即删除旧逻辑表、来源、原归档和物化目录，再准备新文件。新文件后续失败不
恢复旧数据。共享文件只有在不存在当前来源和其他非终态 Job 时才会删除。

## 4. 当前来源与清理

`FileDatasetTableSource` 只保存已生效来源，不保存准备、校验失败或被替换的来源。SHP 覆盖成功
后立即删除旧来源；GDB 整文件替换立即删除旧表和文件。对象删除失败只记录告警，由运维按日志
清理孤儿对象。

解析参数在数据集存在文件、表或非终态 Job 后锁定，保证所有来源使用相同空间解析约定。数据集
清空后可重新配置。

## 4.1 GeoJSON

GeoJSON 是独立的数据集类型，不沿用普通 JSON 的 JSON Pointer 解析。一个 RFC 7946
`FeatureCollection` 文件对应一张逻辑表，支持追加、全量覆盖和单来源替换。解析器流式扫描每个
Feature：

- `properties` 映射为普通属性，嵌套对象和数组保留为 JSON 文本；
- 顶层 `id` 以可空 STRING `_feature_id` 保存，绝不覆盖 `properties.id`；
- `geometry` 映射为 Geometry，支持 Point、LineString、Polygon、Multi 类型和 GeometryCollection；
- 保留字段 `_feature_id` 与 `geometry` 不能出现在 properties；
- 只接受二维 XY。第三、第四坐标、无效坐标结构和非有限数值会使整个装载失败。

解析参数必须提供正整数 EPSG code，管理端默认 `4326`。该设置只解释坐标，不会变换坐标值；旧式
`crs`、`bbox` 和其他扩展成员不进入逻辑表。属性预览流式跳过 Geometry 坐标，但 Schema 和 Canvas
运行始终保留 Geometry 值。

上述规则仅适用于 GeoJSON。它的 CRS 唯一来自数据集解析参数；不提供表级“确认 CRS”操作，
也不会读取旧式 `crs`。GDB/SHP 的 CRS 则按以下顺序确定，不根据名称、范围或坐标值猜测：

1. 文件 WKT 中最后一个明确的 `EPSG AUTHORITY/ID`；
2. 表级“确认 CRS”操作中保存的 EPSG；
3. 数据集解析参数中的默认回退 EPSG。

文件已明确声明 EPSG 时优先采用文件值，表级操作不能覆盖。表级确认用于修正某张 GDB/SHP 逻辑表在通用
数据集回退值之外的声明，因此优先于数据集默认回退值。表级确认会在管理数据库事务外重新
解析全部当前来源；所有来源 Schema 一致后，才在短事务中原子更新字段、Geometry-aware Schema
指纹、来源元数据和表级回退值。解析期间来源或表发生变化时返回 `409`，不提交部分结果。该操作
只声明源坐标参考，不转换坐标。

## 4.2 GEOJSONL

GEOJSONL 是独立类型，不沿用普通 JSONL 的字符集或记录分隔符设置。一个文件对应一张逻辑表；每个
非空物理行必须是一个完整的 GeoJSON `Feature`。固定 UTF-8，支持 LF/CRLF，末行可没有换行符，空白行
会忽略。FeatureCollection、独立 Geometry、跨行 Feature、同一行多个 JSON 值和 RS 分隔的 GeoJSON Text
Sequence 都会使装载失败，并以物理行号报告错误。

属性、`_feature_id`、Geometry、EPSG、二维 XY、严格 Schema 校验及表级追加/覆盖语义完全沿用 GeoJSON；
预览只解析属性和 `_feature_id`，跳过 Geometry 坐标。GEOJSONL 的解析参数也只包含正整数 EPSG，默认
`4326`，不猜测、变换或交换坐标。

## 4.3 GeoParquet

GeoParquet 是独立数据集类型。首版只接受 GeoParquet 1.0.0/1.1.0 的单根级 WKB Geometry 列；Footer
必须包含 `geo.version`、`geo.primary_column` 和 `geo.columns`，且 `primary_column` 必须对应唯一的根级
`BYTE_ARRAY` Geometry 列。普通属性保留原名称和顺序，Geometry 保留原列名和位置，不创建 `_feature_id`。

缺少 `crs` 按 CRS84 规范化为 EPSG:4326；显式 `OGC:CRS84` 同样规范化为 EPSG:4326；显式 CRS 只接受根级
`id.authority=EPSG` 和正整数（或等价数字字符串）`id.code`。`crs: null`、无法识别的根级 CRS、动态 CRS、
coordinate epoch 和 spherical edges 均拒绝。坐标始终按 X/Y 解释，不重投影、不交换坐标、不提供手工 EPSG 覆盖。

解析器完整扫描 WKB 的字节序、类型、二维维度、长度、集合成员、坐标有限性、Polygon 环闭合和尾随字节；
允许 null、合法 Empty Geometry、零行文件和全 null Geometry，不以 JTS 的拓扑修复或 `isValid()` 作为导入条件。
`geometry_types` 必须存在且不能重复；非空声明必须覆盖实际类型，空数组通过完整扫描推断。Footer bbox 与合规
covering 是物理元数据，不参与 Schema 比较；只有被 covering 明确引用并通过物理结构校验的独立 bbox 列才会隐藏。

GeoParquet 物化后按 Footer ColumnChunk 的未压缩大小执行完整校验限制。预览使用 Parquet 列投影，跳过 WKB
Geometry 和已隐藏 covering 列；因此超过通用 64MB 流式抽样限制、但未超过完整校验限制的已导入文件仍可预览。

## 4.4 GeoPackage（GPKG）

GPKG 是独立的单文件多表数据集类型。准备任务先校验 SQLite 头、GeoPackage `application_id`、必要系统表和
元数据引用，再按 `gpkg_contents.table_name` 的二进制顺序发现登记为 `features` 或 `attributes` 的业务表。瓦片、
栅格和系统辅助表不会导入；没有可导入表会使整个上传失败。

每个发现表建立独立逻辑表。`features` 必须在 `gpkg_geometry_columns` 中登记唯一 Geometry 列；该列保留原名称和
物理位置。Geometry CRS 通过 `srs_id` 关联 `gpkg_spatial_ref_sys`，仅接受可识别的 `organization=EPSG` 正整数
`organization_coordsys_id`。内部 SRS ID 绝不直接当作 EPSG；自定义、动态或携带历元的 CRS 被拒绝。Geometry 只接受
二维 XY、null、合法 Empty 和七种基础 WKB 类型；Header、Envelope、SRS、字节序、长度、成员、有限坐标和 Polygon 环
闭合都会完整校验，但不以 JTS 拓扑 `isValid()` 作为导入门槛。

普通字段按 SQLite 声明类型建立平台 Schema，并完整扫描每行的实际值、可空性和值域；`DATETIME` 映射为保留
时区时间点语义的 `TIMESTAMP`。属性预览用按主键（或 rowid）
排序的 SQL 投影读取，不查询 Geometry BLOB；Geometry 只在 Schema 和 Canvas 中保留。GPKG 不支持表级追加、覆盖、
来源替换或来源删除，只能整文件替换或删除；替换成功后旧表与旧对象立即删除且不可恢复。

## 5. 预览与运行

SHP/GDB/GeoJSON/GEOJSONL/GeoParquet/GPKG 属性预览按当前来源的 `sourceOrder` 读取，累计到 limit 后停止，不返回 Geometry 字段和
坐标值；任一来源不支持安全预览时整表返回 `409`。GDB 图层可以处于 `SCHEMA_READY`，此时允许
作为 Canvas 输入但管理端不提供预览。

SHP 的单要素及累计 Geometry 点数预览上限只控制样本预览；完整校验和导入使用 Shapefile Reader
的正式安全上限。合法要素超过预览点数上限时保留 Schema 并将预览标记为不可用，不得阻断导入。

Canvas Manifest v27 保存逻辑表 Schema、解析参数和有序来源的精确原归档/物化位置及来源键。
Task Engine 不使用修订号，也不保护旧对象。覆盖或删除会立即移除旧对象，因此旧任务允许以文件
不存在错误失败。

Runner 通过现有 S3 Shapefile/FileGDB Reader、GeoJSON/GEOJSONL 流式 Reader、Sedona GeoParquet Reader 或 GPKG 的只读 SQLite JDBC cursor 读取记录，将几何对象直接转换为
带稳定 EPSG SRID 的 JTS Geometry，并交给 Sedona `GeometryUDT`。第一阶段仅执行 EPSG + XY；
XYZ、XYM、XYZM 会在执行前被拒绝。GeoParquet Reader 按 Footer 读取并将已保存 EPSG 设置为 SRID，但不转换坐标；
它保留 Spark 的分区读取能力。SHP/GDB/GeoJSON/GEOJSONL/GPKG 输入当前使用单 Spark 分区，优先保证读取器边界和错误归一化；本阶段
不支持 SHP/GDB 输出，也不引入 GeoTools 或 GDAL。

## 6. 运维要求

MinIO Bucket 必须关闭版本管理和 Object Lock，才能保证破坏性操作后旧对象实际消失。升级当前
来源模型时停止 Admin、Worker、Task Engine 和 Dispatcher，清空
`data-scalpel/file-datasets/` 前缀后执行数据库重建脚本。
