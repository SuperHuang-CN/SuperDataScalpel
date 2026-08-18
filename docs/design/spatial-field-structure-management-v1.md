# 空间字段结构管理 V1

## 目标与范围

V1 完成以下结构管理闭环：

```text
模型 Geometry 定义
  → 目标数据库空间存储能力解析
  → PostGIS / MySQL 8 原生空间列或 ClickHouse WKB 列受控建表
  → 整表空间元数据回读
  → Encoding + GeometryKind + CRS + Dimension + nullable 精确比较
```

支持范围固定为：

- PostgreSQL，并且目标实例已安装 PostGIS；
- MySQL 8.x、InnoDB；
- 单机 ClickHouse、`MergeTree`，使用原始 WKB `String` 列和空间 marker；
- authority 为 `EPSG`、code 为正整数的 CRS；
- `XY` 二维坐标；
- `GEOMETRY`、`POINT`、`LINESTRING`、`POLYGON`、`MULTIPOINT`、
  `MULTILINESTRING`、`MULTIPOLYGON`、`GEOMETRYCOLLECTION`；
- 新建受管表、绑定外部表、从数据源导入受管模型、Excel 元数据交换和物理结构检查。

V1 结构管理不创建空间索引，也不提供 Geometry 值筛选、排序、聚合、空间函数、
WKT/WKB/GeoJSON 值解析或转换、CRS 转换、Geometry 修复、单体/Multi 自动转换、
`geography`、Raster、自定义 WKT CRS 或已建受管表的空间结构变更。ClickHouse 使用
WKB 仅表示物理存储编码，平台不读取、生成或校验 WKB 值。Shapefile/FileGDB 的独立
文件预览协议保持不变，不自动转换为模型 Geometry。

模型详情另提供 PostgreSQL/PostGIS 动态空间预览 MVP。该只读能力识别目标 Geometry
字段上有效、就绪、非 partial、非 expression 的单列 GiST/SP-GiST 索引，但不把索引
纳入模型结构、不自动创建索引。PostGIS 按当前视口在原字段上执行 `&&` 过滤，再转换、
裁剪并简化到 EPSG:3857，返回 WKB 给业务层使用 JTS 和 Java2D 渲染透明 PNG；浏览器的
MapLibre 仅显示图片，不接收 Geometry。没有索引时只允许数据库 `reltuples` 估算不超过
50,000 行的小表预览；统计未知或超过阈值时拒绝。单图限制为 5 秒、5,000 个 Geometry、
500,000 个坐标点和 16 MiB WKB。普通快速预览和条件查询仍排除 Geometry。

## Canvas/Sedona 空间执行扩展

Canvas 协议 `1.20` 在上述结构管理基础上增加真实空间计算闭环：

- Task Engine、Local Runner 和 Cluster Runner使用 Apache Sedona 1.9.0。
- `SPATIAL_TRANSFORM` 通过受控 `ST_Transform` 转换 EPSG CRS。
- `SPATIAL_JOIN` 仅支持 INNER，以及
  `INTERSECTS/CONTAINS/WITHIN/COVERS/COVERED_BY/TOUCHES/OVERLAPS/CROSSES/EQUALS`。
- PostGIS/MySQL 8 输入统一读取 WKB并恢复为 Sedona `GeometryUDT`。
- 含 Geometry 的 JDBC Output统一使用分区 PreparedStatement和数据库空间构造函数。
- Manifest v8保存完整 Geometry Schema；v7只兼容非空间任务。

Canvas 协议 `1.21` 继续补齐空间值的基础处理闭环：

- `GEOMETRY_CONSTRUCT` 从 WKT、WKB、GeoJSON 或 X/Y 构造具体 kind 的 Geometry，并显式设置 SRID；
- `GEOMETRY_VALIDATE` 使用 `ST_IsValid/ST_IsValidReason` 输出诊断但不修复或删行；
- `SPATIAL_MEASURE` 提供面积、长度、周长、距离和 Point X/Y 的平面或 WGS84 椭球测量；
- `GEOMETRY_SERIALIZE` 输出 WKT/WKB/GeoJSON，且 GeoJSON 只允许 EPSG:4326；
- 四个节点均为无状态 Processor，同时支持 BATCH/STREAMING，并继承 boundedness、事件时间和 Watermark。

Canvas空间执行仍只支持 `EPSG + XY`。Geometry可以被普通投影、重命名和标量字段处理透明
携带，但不能作为普通等值 Join、排序、分区、聚合、去重或标量 Cast 的运算字段。
运行时会重新读取数据库空间目录；kind、CRS、dimension或本地SRID发生漂移时，在任何
Output 写入前终止任务。该执行扩展不改变本文件中 ClickHouse WKB仅用于结构管理的边界。

## 稳定平台契约

共享契约使用以下类型：

- `PlatformDataType.GEOMETRY`
- `GeometryKind`
- `CrsReference(authority, code)`
- `CoordinateDimension`
- `GeometryTypeDefinition(kind, crs, dimension)`

`CrsReference` 将 authority 去除首尾空白并规范化为大写。共享结构允许表达其他 authority
和 `XYZ/XYM/XYZM`，但 V1 业务和方言只接受 `EPSG + XY`，从而避免将版本能力限制混入
可长期演进的基础值对象。

`PlatformTypeDefinition` 的 JSON 结构为：

```json
{
  "type": "GEOMETRY",
  "length": null,
  "precision": null,
  "scale": null,
  "geometry": {
    "kind": "POINT",
    "crs": {
      "authority": "EPSG",
      "code": 4326
    },
    "dimension": "XY"
  }
}
```

`type=GEOMETRY` 时 `geometry` 必填，长度、精度和小数位必须为空；其他类型禁止携带
`geometry`。原四参数 Java 构造器继续保留，旧标量 JSON 缺少 `geometry` 时仍可读取。
通用 `GeometryKind.GEOMETRY` 是精确子类型，不作为任意具体子类型的通配符。Geometry
字段不能作为主键。

## 模型持久化与 API

`ds_data_model_field` 增加四个可空标量列：

| 列 | 含义 |
| --- | --- |
| `geometry_kind` | 规范化 GeometryKind |
| `crs_authority` | 稳定 CRS authority |
| `crs_code` | 稳定 CRS code |
| `coordinate_dimension` | 坐标维度 |

现有标量字段四列均为空；第一版继续由 Hibernate `ddl-auto=update` 增列。对外模型字段、
外部表导入、受管导入和物理变更目标快照均使用嵌套 `geometry`，不暴露数据库本地
SRID/SRS ID。

字段请求示例：

```json
{
  "code": "shape",
  "name": "空间位置",
  "fieldType": "GEOMETRY",
  "geometry": {
    "kind": "POINT",
    "crs": {
      "authority": "EPSG",
      "code": 4326
    },
    "dimension": "XY"
  },
  "nullable": true,
  "primaryKey": false,
  "sortOrder": 10
}
```

`GET /api/v1/models/platform-types` 在 PostgreSQL、MySQL 和 ClickHouse 能力响应中返回
Geometry 支持状态、GeometryKind、坐标维度和 CRS authority。ClickHouse 的映射说明
明确展示 WKB `String` 存储和 comment 声明语义；其他方言保留 `GEOMETRY` 能力项但
明确标记为不支持，由前端禁用。

Excel 模型元数据当前写出 V2：`字段` 工作表在原标量列后增加“几何类型、CRS Authority、
CRS Code、坐标维度”。导入继续识别 V1 标量文件；模板和新导出统一使用 V2。文件保存
平台 Geometry 定义，不保存 PostGIS SRID 或 MySQL SRS ID。

## 方言元数据边界

方言内部使用 `TableColumnType.GEOMETRY`。`PhysicalTypeDefinition`、`TableColumnDefinition`、
`ColumnMetadata` 和 `JdbcTypeDescriptor` 在空间列上携带规范化后的 Geometry 定义或
`SpatialColumnMetadata`。后者至少包含：

- 原生 Geometry 类型；
- 数据库本地空间参考 ID；
- 目录反查后的 CRS authority/code；
- 坐标维度；
- subtype 和 CRS 是否受到物理列约束。
- 空间存储编码 `NATIVE/WKB`；
- 空间元数据强度 `NONE/DECLARED/ENFORCED` 以及无法映射的具体原因。

普通 JDBC 列元数据先按整表读取，然后调用 `DatabaseDialect.enrichColumnMetadata` 一次性
补充整张表的空间信息。实现不得逐列查询 catalog。建表同样使用连接感知的
`planCreateTable(Connection, TableDefinition)`，DDL 预览与执行共享同一目标连接上的
能力检查和 CRS 解析结果。

数据库本地空间参考 ID 只在原生空间方言边界存在。PostGIS/MySQL 必须通过目标库目录
把 `EPSG:<code>` 反查为唯一的本地 ID；缺失、重复或非 EPSG 映射均拒绝导入或建表，
不能假设本地 ID 等于 EPSG code。ClickHouse WKB 不使用数据库本地空间参考 ID，EPSG
直接作为列 marker 中的平台声明保存。

## PostGIS

PostGIS 是 PostgreSQL 数据源的可选运行能力，不是新的数据源类型。

- 建表前检查 `postgis` 扩展及其安装 schema，不自动执行 `CREATE EXTENSION`。
- EPSG 通过扩展目录 `spatial_ref_sys` 反查本地 SRID。
- DDL 使用扩展 schema 限定的 `"schema"."geometry"(KIND,localSrid)`。
- 元数据通过 PostGIS catalog、`geometry_columns` 和 typmod 信息整表读取，不依赖 JDBC
  `TYPE_NAME` 推断 subtype、SRID 或维度。
- `geography`、Raster、无固定 subtype、无固定 SRID、非 EPSG 或非 XY 返回
  `UNSUPPORTED`。

## MySQL 8

Geometry 结构能力只对数据库产品确认为 MySQL 8.x 时开放；MySQL 5.7 和 MariaDB 明确拒绝。

- 使用 `INFORMATION_SCHEMA.ST_GEOMETRY_COLUMNS` 读取 subtype 和 SRS restriction。
- 使用 `INFORMATION_SCHEMA.ST_SPATIAL_REFERENCE_SYSTEMS` 反查 EPSG authority/code。
- DDL 使用 `POINT SRID localSrsId` 等原生语法。
- 含 Geometry 的受管表显式生成 `ENGINE=InnoDB`。
- 未声明 SRID restriction、未知 SRS、非 EPSG 或非 XY 返回 `UNSUPPORTED`。

## ClickHouse 单机 WKB

ClickHouse Geometry 不使用原生 `Point`、`Polygon` 或其他 Geo 类型。方言将标准二维
OGC WKB 原始字节存入普通字节序列可承载的列：

- `nullable=false`：`String`
- `nullable=true`：`Nullable(String)`

空间语义由以下单行、版本化且顺序固定的列 comment marker 声明：

```text
@datascalpel-spatial-v1 encoding=WKB kind=POLYGON crs=EPSG:4326 dimension=XY
```

marker 必须完整且在一个 comment 中恰好出现一次；encoding、kind、CRS 和 dimension
均使用规范值。marker 可以与人工说明分行共存。`ClickHouseDialect.enrichColumnMetadata`
按整张表一次查询 `system.columns` 的 `name/type/comment`，用查询结果替代 JDBC 返回的
不可靠类型和备注。导入对外字段描述前移除保留 marker，只保留人工说明。

`String/Nullable(String) + 合法 marker` 精确还原为平台 Geometry，并标记为
`WKB + DECLARED`；marker 出现在其他物理类型、重复、格式非法或参数不完整时返回
`UNSUPPORTED`。无 marker 的现有 `String` 仍是普通 `STRING`，平台不提供自动写 comment
的接管接口。管理员可手工补充规范 marker 后重新绑定或导入，无需重建物理表。

Geometry 不得作为 ClickHouse `MergeTree ORDER BY` 字段。受管 DDL 不生成原生 Geo
类型、空间索引、辅助列或值级校验；marker 是结构声明，不保证第三方写入的每一行字节
都符合声明的 WKB kind、CRS 或维度。

## 映射、比较与指纹

PostGIS/MySQL 空间映射只有在 subtype、EPSG CRS、XY 和原生物理约束都可精确确认时
才返回 `EXACT`。ClickHouse 只有在物理列为 String、marker 完整并声明 WKB、具体 subtype、
EPSG CRS 和 XY 时返回 `EXACT`；其元数据强度为 `DECLARED`，不声称存在值级数据库约束。
Geometry 不降级为普通 `STRING`、`JSON` 或 `BINARY`，也没有允许导入/建表的 `LOSSY`
路径。

结构比较包含：

- 精确 GeometryKind；
- 规范化 `authority + code`；
- CoordinateDimension；
- SpatialStorageEncoding；
- nullable；
- 现有字段名、主键和其他标量结构规则。

含 Geometry 的表使用 V3 结构指纹。纯标量表继续使用既有 V2 算法，使已保存的标量
物理变更计划不因本功能失效。

V1 不扩展 `IndexMetadata`、`TableDefinition` 索引定义或 DDL 原子性。建表 SQL不得包含
`GiST`、`SPATIAL INDEX` 或其他空间索引；受管导入仍统一提示源表索引未导入。

## 业务行为

草稿可以保存合法 Geometry。查看 DDL、创建物理表、外部绑定和受管导入预览时，才连接
目标数据库校验 PostGIS/MySQL 版本、运行能力和具体 EPSG 映射，或读取 ClickHouse
`system.columns` 中的 WKB marker。外部调用继续采用“短事务读取快照 → 事务外 JDBC
→ 短事务提交”的边界。

包含 Geometry 的受管物理表创建并匹配后，只允许修改字段显示名称、说明和展示顺序。
不允许新增、删除、改名物理 Geometry 列，也不允许修改 kind、CRS、dimension、nullable
或字段类型；整表物理结构变更入口在前端禁用，后端同样拒绝生成计划。

模型快速预览和标准条件查询默认排除 Geometry。客户端显式选择、筛选、排序、分组或
聚合 Geometry 时返回查询参数错误。PostGIS 动态空间预览是独立的受控 PNG 渲染边界，
不改变普通数据查询契约，也不向浏览器返回 Geometry 值。

以下执行/服务边界明确区分 Geometry 能力，任何场景都不能把 Geometry 降级映射为 Spark
String/Binary：

- Spark Canvas 的 JDBC、`MODEL_INPUT` / `MODEL_OUTPUT` 支持 PostgreSQL/PostGIS 和
  MySQL 8 Geometry，并通过 Sedona `GeometryUDT` 执行；Kafka 内联 Value Schema 仍返回
  `SPATIAL_FIELD_UNSUPPORTED`；
- Local SQL 输入/输出模型仍返回 `SPATIAL_FIELD_UNSUPPORTED`；
- 后续的空间文件输入阶段已经扩展 `FILE_DATASET_INPUT`：SHP/GDB Geometry 以同一公共
  `GeometryTypeDefinition` 进入 Canvas Schema、Manifest v8 和 Sedona `GeometryUDT`，不再降级为
  JSON String；详细运行边界见 [空间文件数据集解析](geospatial-file-dataset-parsing.md)和
  [Canvas 任务执行设计](canvas-task-execution.md)。
- 标准数据服务：Geometry 字段不可返回、筛选、排序、分组或聚合，默认投影排除；
- SQL 服务：禁止 Geometry 参数；输出元数据映射为 Geometry 时以
  `SPATIAL_FIELD_UNSUPPORTED` 阻止测试和发布。

## 前端

字段编辑器提供 GeometryKind、固定的 `EPSG` authority、正整数 EPSG code 和只读
`XY`。选择 Geometry 时自动关闭并禁用主键。外部/受管导入和 Excel V2 校对保持嵌套
Geometry 定义，不转换为数据库原生字符串。

字段列表展示 `Kind · EPSG:code · XY`。包含 Geometry 的已匹配受管表显示 V1 限制说明，
隐藏新增、删除和物理变更计划入口。模型数据查询的投影、条件和排序选择器均排除 Geometry。
界面不提供空间索引控件。

## 测试与真实库验收

后续恢复验证时，自动化场景应覆盖契约 JSON/非法组合、八种 GeometryKind 双向映射、
PostGIS/MySQL 原生 DDL、ClickHouse WKB DDL 与 marker、空间元数据增强、精确结构比较、
无空间索引、标量 V2 指纹兼容、模型持久化/API、外部与受管导入、Excel V1/V2、查询排除、
物理变更阻止、Canvas/Local SQL/服务边界和前端联动。

ClickHouse 验收需确认八种 kind 均生成 `String` 或 `Nullable(String)`，合法 marker 可
回读；普通 String 不误判；非法、重复、缺失参数或位于非 String 列的 marker 被拒绝；
encoding、kind、CRS、dimension 或 nullable 任一变化产生结构不匹配；Geometry 不能作为
主键或排序键；DDL 不包含原生 Geo 类型或空间索引。当前工程暂时禁用自动化测试、构建
和联调要求，本次实现不把上述清单表述为已经完成的真实数据库兼容性证明。

真实数据库验收默认跳过，只能在可销毁的隔离数据库中开启：

```bash
# PostGIS 已安装，测试用户可在测试 Schema 建表/删表
export DATASCALPEL_POSTGIS_INTEGRATION=true
export DATASCALPEL_POSTGIS_HOST=127.0.0.1
export DATASCALPEL_POSTGIS_PORT=5432
export DATASCALPEL_POSTGIS_DATABASE=spatial_test
export DATASCALPEL_POSTGIS_SCHEMA=datascalpel_adapter_test
export DATASCALPEL_POSTGIS_USERNAME=postgres
export DATASCALPEL_POSTGIS_PASSWORD=...

# MySQL 8.x，可销毁测试库
export DATASCALPEL_MYSQL8_INTEGRATION=true
export DATASCALPEL_MYSQL8_HOST=127.0.0.1
export DATASCALPEL_MYSQL8_PORT=3306
export DATASCALPEL_MYSQL8_DATABASE=spatial_test
export DATASCALPEL_MYSQL8_USERNAME=root
export DATASCALPEL_MYSQL8_PASSWORD=...

./mvnw -pl data-scalpel-dialect -am test
```

未提供以上隔离实例时，不能宣称完成真实 PostGIS/MySQL 兼容性验收。ClickHouse 同样
需要可销毁的单机实例，才能验证 `system.columns` 回读和实际 DDL 行为。
