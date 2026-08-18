# 空间文件数据集解析

## 1. 上传与存储

SHP 和 GDB 都以单个归档文件上传，并进入统一文件数据集解析队列：

- SHP 接受 ZIP；
- GDB 接受 ZIP 或 TAR.GZ；
- 原归档保存在现有 `file-datasets/...` Object Key；
- 准备任务在独立物化前缀发布安全解压后的组件或 GDB 目录；
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
coordinate dimension；管理端 JSON 预览只用于可读展示，不是 Canvas 字段类型。Canvas 和 Runner
始终使用 Sedona/JTS Geometry，不把 Geometry 降级为 JSON 或 String。

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

CRS 只按以下顺序确定，不根据名称、范围或坐标值猜测：

1. 文件 WKT 中最后一个明确的 `EPSG AUTHORITY/ID`；
2. 表级“确认 CRS”操作中保存的 EPSG；
3. 数据集解析参数中的默认回退 EPSG。

文件已明确声明 EPSG 时优先采用文件值，表级操作不能覆盖。表级确认用于修正某张逻辑表在通用
数据集回退值之外的声明，因此优先于数据集默认回退值。表级确认会在管理数据库事务外重新
解析全部当前来源；所有来源 Schema 一致后，才在短事务中原子更新字段、Geometry-aware Schema
指纹、来源元数据和表级回退值。解析期间来源或表发生变化时返回 `409`，不提交部分结果。该操作
只声明源坐标参考，不转换坐标。

## 5. 预览与运行

SHP 预览按当前来源的 `sourceOrder` 读取，累计到 limit 后停止；任一来源不支持安全预览时整表
返回 `409`。GDB 图层可以处于 `SCHEMA_READY`，此时允许作为 Canvas 输入但管理端不提供预览。

Canvas Manifest v8 保存逻辑表 Schema、解析参数和有序来源的精确原归档/物化位置及来源键。
Task Engine 不使用修订号，也不保护旧对象。覆盖或删除会立即移除旧对象，因此旧任务允许以文件
不存在错误失败。

Runner 通过现有 S3 Shapefile/FileGDB Reader 读取记录，将几何对象直接转换为带稳定 EPSG SRID 的
JTS Geometry，并交给 Sedona `GeometryUDT`。第一阶段仅执行 EPSG + XY；XYZ、XYM、XYZM 会在
执行前被拒绝。Runner 会重新比较 kind、CRS 和 dimension，漂移时在任何 Output 写入前失败。
SHP/GDB 输入当前使用单 Spark 分区，优先保证读取器边界和错误归一化；本阶段不支持 SHP/GDB
输出，也不引入 GeoTools 或 GDAL。

## 6. 运维要求

MinIO Bucket 必须关闭版本管理和 Object Lock，才能保证破坏性操作后旧对象实际消失。升级当前
来源模型时停止 Admin、Worker、Task Engine 和 Dispatcher，清空
`data-scalpel/file-datasets/` 前缀后执行数据库重建脚本。
