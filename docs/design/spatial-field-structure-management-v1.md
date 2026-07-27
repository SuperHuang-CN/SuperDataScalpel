# 空间字段结构管理 V1

## 目标与范围

V1 完成以下结构管理闭环：

```text
模型 Geometry 定义
  → 目标数据库运行能力与 EPSG 解析
  → PostGIS / MySQL 8 受控建表
  → 整表空间元数据回读
  → GeometryKind + CRS + Dimension + nullable 精确比较
```

支持范围固定为：

- PostgreSQL，并且目标实例已安装 PostGIS；
- MySQL 8.x、InnoDB；
- authority 为 `EPSG`、code 为正整数的 CRS；
- `XY` 二维坐标；
- `GEOMETRY`、`POINT`、`LINESTRING`、`POLYGON`、`MULTIPOINT`、
  `MULTILINESTRING`、`MULTIPOLYGON`、`GEOMETRYCOLLECTION`；
- 新建受管表、绑定外部表、从数据源导入受管模型、Excel 元数据交换和物理结构检查。

V1 不创建或识别空间索引，也不提供 Geometry 值预览、筛选、排序、聚合、空间函数、
WKT/WKB/GeoJSON 转换、CRS 转换、Geometry 修复、单体/Multi 自动转换、`geography`、
Raster、自定义 WKT CRS 或已建受管表的空间结构变更。Shapefile/FileGDB 的独立文件预览
协议保持不变，不自动转换为模型 Geometry。

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

`GET /api/v1/models/platform-types` 在 PostgreSQL/MySQL 能力响应中返回 Geometry 支持状态、
GeometryKind、坐标维度和 CRS authority。其他方言保留 `GEOMETRY` 能力项但明确标记为
不支持，由前端禁用。

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

普通 JDBC 列元数据先按整表读取，然后调用 `DatabaseDialect.enrichColumnMetadata` 一次性
补充整张表的空间信息。实现不得逐列查询 catalog。建表同样使用连接感知的
`planCreateTable(Connection, TableDefinition)`，DDL 预览与执行共享同一目标连接上的
能力检查和 CRS 解析结果。

数据库本地空间参考 ID 只在方言边界存在。平台必须通过目标库目录把 `EPSG:<code>`
反查为唯一的本地 ID；缺失、重复或非 EPSG 映射均拒绝导入或建表，不能假设本地 ID
等于 EPSG code。

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

## 映射、比较与指纹

空间映射只有在 subtype、EPSG CRS、XY 和物理约束都可精确确认时才返回 `EXACT`。
Geometry 不降级为 `STRING`、`JSON` 或 `BINARY`，也没有允许导入/建表的 `LOSSY`
路径。

结构比较包含：

- 精确 GeometryKind；
- 规范化 `authority + code`；
- CoordinateDimension；
- nullable；
- 现有字段名、主键和其他标量结构规则。

含 Geometry 的表使用 V3 结构指纹。纯标量表继续使用既有 V2 算法，使已保存的标量
物理变更计划不因本功能失效。

V1 不扩展 `IndexMetadata`、`TableDefinition` 索引定义或 DDL 原子性。建表 SQL不得包含
`GiST`、`SPATIAL INDEX` 或其他空间索引；受管导入仍统一提示源表索引未导入。

## 业务行为

草稿可以保存合法 Geometry。查看 DDL、创建物理表、外部绑定和受管导入预览时，才连接
目标数据库校验 PostGIS/MySQL 版本、运行能力和具体 EPSG 映射。外部调用继续采用
“短事务读取快照 → 事务外 JDBC → 短事务提交”的边界。

包含 Geometry 的受管物理表创建并匹配后，只允许修改字段显示名称、说明和展示顺序。
不允许新增、删除、改名物理 Geometry 列，也不允许修改 kind、CRS、dimension、nullable
或字段类型；整表物理结构变更入口在前端禁用，后端同样拒绝生成计划。

模型快速预览和标准条件查询默认排除 Geometry。客户端显式选择、筛选、排序、分组或
聚合 Geometry 时返回查询参数错误。当前不读取 Geometry 值。

以下执行/服务边界明确拒绝 Geometry，不能映射为 Spark String/Binary：

- Canvas `MODEL_INPUT` / `MODEL_OUTPUT` 和 Kafka 内联 Value Schema：
  `SPATIAL_FIELD_UNSUPPORTED`；
- Local SQL 输入/输出模型：`SPATIAL_FIELD_UNSUPPORTED`；
- Spark 类型映射和文件 Canvas Schema：`SPATIAL_FIELD_UNSUPPORTED`；
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

自动化测试覆盖契约 JSON/非法组合、八种 GeometryKind 双向映射、PostGIS/MySQL DDL、
空间元数据增强、精确结构比较、无空间索引、标量 V2 指纹兼容、模型持久化/API、外部与
受管导入、Excel V1/V2、查询排除、物理变更阻止、Canvas/Local SQL/服务边界和前端联动。

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

未提供以上隔离实例时，构建只证明契约、方言和业务逻辑测试通过，不能宣称完成真实
PostGIS/MySQL 兼容性验收。
