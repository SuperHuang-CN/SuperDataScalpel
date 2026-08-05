# Canvas `GEOMETRY_CONSTRUCT` Processor 设计文档

## 1. 状态、目标与范围

- 实现状态：已实现。
- 目标协议版本：Canvas `1.21`。
- 节点类型：`GEOMETRY_CONSTRUCT`。
- 节点类别：`PROCESSOR`。
- 执行模式：`BATCH`、`STREAMING`。
- 图规则：恰好一条入边，至少一条出边。

`GEOMETRY_CONSTRUCT` 从一张逻辑表的普通字段构造一个 Sedona Geometry 字段，并以新
逻辑表名追加结果。首期支持 WKT、WKB、GeoJSON 和二维 X/Y 坐标四种来源，用于把
JDBC、文件、HTTP API 或 Kafka 中的普通载荷转换为后续空间节点能够消费的稳定
`GEOMETRY` Schema。

本节点只负责“普通字段到 Geometry”的显式转换，不推断 CRS、不修复无效 Geometry、
不转换坐标系，也不从样例数据推断 GeometryKind。CRS 转换继续使用
`SPATIAL_TRANSFORM`，合法性诊断使用 `GEOMETRY_VALIDATE`。

## 2. 稳定配置协议

Java 稳定类型位于 `data-scalpel-contracts`，来源配置使用以 `kind` 为判别字段的 sealed
联合；Business、Task Engine 和 Manifest 直接复用同一组类型。前端使用对应的明确
TypeScript 判别联合，不以 `Record<string, unknown>` 表达配置。

```ts
interface GeometryConstructConfiguration {
  sourceTableName: string;
  outputTableName: string;
  outputColumnName: string;
  source: GeometryConstructSource;
  targetGeometry: GeometryTypeDefinition;
}

type GeometryConstructSource =
  | { kind: 'WKT'; columnName: string }
  | { kind: 'WKB'; columnName: string }
  | { kind: 'GEOJSON'; columnName: string }
  | { kind: 'POINT_FROM_XY'; xColumnName: string; yColumnName: string };
```

WKT 示例：

```json
{
  "sourceTableName": "addresses",
  "outputTableName": "addresses_geometry",
  "outputColumnName": "location",
  "source": {
    "kind": "WKT",
    "columnName": "location_wkt"
  },
  "targetGeometry": {
    "kind": "POINT",
    "crs": {
      "authority": "EPSG",
      "code": 4326
    },
    "dimension": "XY"
  }
}
```

X/Y 示例：

```json
{
  "sourceTableName": "device_events",
  "outputTableName": "device_events_geometry",
  "outputColumnName": "location",
  "source": {
    "kind": "POINT_FROM_XY",
    "xColumnName": "longitude",
    "yColumnName": "latitude"
  },
  "targetGeometry": {
    "kind": "POINT",
    "crs": {
      "authority": "EPSG",
      "code": 4326
    },
    "dimension": "XY"
  }
}
```

协议限制：

- `sourceTableName`、`outputTableName`、`outputColumnName` 必填。
- `source` 必填，只允许四种已声明的判别类型。
- WKT、GeoJSON 来源字段必须为平台 `STRING`。
- WKB 来源字段必须为平台 `BINARY`；首期不把十六进制字符串隐式当作 WKB。
- X/Y 来源字段必须是 BYTE、SHORT、INTEGER、LONG、FLOAT、DOUBLE 或 DECIMAL。
- `targetGeometry` 必须是 `EPSG + XY`，CRS code 必须为正整数。
- 首期必须声明具体 GeometryKind，不接受通用 `GEOMETRY`；允许 POINT、LINESTRING、
  POLYGON、MULTIPOINT、MULTILINESTRING、MULTIPOLYGON 和 GEOMETRYCOLLECTION。
- `POINT_FROM_XY` 的目标 kind 固定为 `POINT`。
- `outputColumnName` 不能与来源表字段或其他已产生字段重名。
- 配置不保存 SQL、Sedona 表达式、样例值、EWKT/EWKB 中的 SRID 或 UI 内部状态。

## 3. 构造与失败语义

Operator 使用 Sedona Column API，不拼接用户 SQL：

- WKT：`ST_GeomFromWKT`。
- WKB：`ST_GeomFromWKB`。
- GeoJSON：`ST_GeomFromGeoJSON`。
- X/Y：将两个数值字段显式转换为 DOUBLE 后使用 `ST_Point`。
- 构造后统一使用 `ST_SetSRID` 设置 `targetGeometry.crs.code`。

统一语义：

- WKT、WKB 和 GeoJSON 不携带平台可信 CRS；即使载荷包含扩展 CRS 信息，也不能覆盖
  `targetGeometry`。
- 普通来源字段为 SQL NULL 时，输出 Geometry 为 NULL。
- `POINT_FROM_XY` 任一坐标为 NULL 时，输出 Geometry 为 NULL。
- 非 NULL 载荷解析失败时任务失败，不返回 NULL，也不跳过当前行。
- 构造结果的实际 GeometryKind 必须与 `targetGeometry.kind` 完全一致；不匹配时运行失败。
- 首期固定使用 `ERROR` 语义，定义中不增加只有单一取值的 `failureStrategy` 字段。
- 不引入自定义安全解析 UDF；未来确有坏值置空需求时，通过新协议版本显式增加策略。

Compiler 只使用零行 Dataset 构造并分析表达式，能够验证来源字段类型、输出 Schema 和
Sedona 函数可分析性，但不能读取真实载荷、证明每行可解析或统计 GeometryKind 分布。

## 4. Map、Schema 与批流传播

Map 规则：

- 从输入 Map 按 `sourceTableName` 精确查找来源表。
- 保留输入 Map 中全部表，不覆盖或修改来源表。
- 以 `outputTableName` 追加构造结果。
- 输出表名与任一现有 Key 冲突时返回 `DUPLICATE_TABLE_NAME`。

Schema 规则：

- 来源字段名称、顺序、类型和元数据完整保留。
- 新 Geometry 字段固定追加在末尾。
- 新字段使用 `PlatformDataType.GEOMETRY`，空间定义取 `targetGeometry`。
- WKT/WKB/GeoJSON 输出字段的 nullable 继承来源字段；X/Y 输出字段在任一坐标字段
  nullable 时为 nullable。
- 新字段的 default、autoIncrement、generated 和 comment 清空。
- 输出表 `origin=null`，因为它已经不是原始物理表的直接镜像。
- 输出继承来源 `datasetKind`、`eventTimeColumn` 和 `watermarkDelay`。

节点是逐行无状态变换，既可处理 BOUNDED，也可处理 UNBOUNDED Dataset，不改变行数、
事件时间或 Watermark。实时任务中的 Geometry 仍不能直接进入 Kafka Value Schema；写
Kafka 前必须经过 `GEOMETRY_SERIALIZE`，并按需使用 `SELECT_COLUMNS` 移除 Geometry 字段。

## 5. 校验与稳定错误码

复用错误码：

- `CONFIGURATION_REQUIRED`
- `REQUIRED_CONFIGURATION`
- `TABLE_NOT_FOUND`
- `COLUMN_NOT_FOUND`
- `DUPLICATE_TABLE_NAME`
- `DUPLICATE_COLUMN_NAME`
- `UNSUPPORTED_GEOMETRY_CRS`
- `UNSUPPORTED_GEOMETRY_DIMENSION`
- `SPARK_ANALYSIS_ERROR`
- `NODE_EXECUTION_MODE_NOT_SUPPORTED`
- `UPSTREAM_INVALID`

新增错误码：

| 错误码 | 条件 |
| --- | --- |
| `INVALID_GEOMETRY_CONSTRUCT_SOURCE` | 来源判别类型缺失、未知或字段组合不完整 |
| `GEOMETRY_CONSTRUCT_SOURCE_TYPE_MISMATCH` | 来源字段不是对应格式要求的平台类型 |
| `GEOMETRY_CONSTRUCT_KIND_UNSUPPORTED` | 目标为通用 GEOMETRY、非 XY 或 POINT_FROM_XY 目标不是 POINT |
| `GEOMETRY_CONSTRUCT_PARSE_FAILED` | 非 NULL WKT/WKB/GeoJSON 在运行时无法解析 |
| `GEOMETRY_CONSTRUCT_KIND_MISMATCH` | 实际 GeometryKind 与声明目标不一致 |

解析和 kind 不匹配属于真实数据运行时失败。对外错误只能返回稳定通用描述，不得携带
原始 WKT、WKB、GeoJSON、坐标值或失败值所在数据行。

## 6. 前端设计器

- Palette 名称为“Geometry 构造”，说明为“从 WKT、WKB、GeoJSON 或 X/Y 字段构造空间字段”。
- 节点位于 Processor 的“空间处理”分组，批处理和实时模式均展示。
- Inspector 的来源表和字段候选只读取 Compiler 返回的 `inputTables`。
- 来源格式使用四个明确选项；切换格式后只提交当前判别分支需要的字段。
- WKT/GEOJSON 只列出 STRING 字段，WKB 只列出 BINARY 字段，X/Y 只列出数值字段。
- 目标空间定义编辑具体 GeometryKind、EPSG code；dimension 固定展示为 XY。
- 选择 POINT_FROM_XY 时 kind 固定为 POINT 并禁用修改。
- 上游表、来源字段或字段类型变化后保留已保存值，并在原位置标记失效原因。
- 节点摘要显示来源表、来源类型、输出字段、目标 kind 和 EPSG，不显示空间载荷值。

## 7. Task Engine、Runner 与安全边界

新增唯一无状态 `GeometryConstructNodeOperator`，Compiler 与 Runner 通过同一内置 Registry
调用。节点阶段固定为 `PROCESS`，成功消息固定为“Geometry 构造已准备”。

安全摘要只允许记录：

- 来源表和输出表。
- 来源类型和字段名；X/Y 只记录字段名。
- 输出字段名。
- 目标 GeometryKind、CRS 和 dimension。

Runner 对解析失败和 kind 不匹配的异常链必须输出脱敏后的通用信息。日志、结果、事件、
诊断消息和编译响应禁止包含 WKT、WKB、GeoJSON、坐标、原始二进制或数据行。Spark 惰性
执行导致错误在下游 Action 触发时，仍不得根据异常文本回显实际失败值。

## 8. 行为验收标准

- 四种来源配置可稳定 JSON 往返，未知来源类型被拒绝。
- WKT、WKB、GeoJSON 和 X/Y 均能生成声明 Schema 的 GeometryUDT 字段。
- NULL 传播、来源类型限制、具体 kind 限制和字段重名规则一致。
- 畸形载荷和实际 kind 不匹配不会被静默置空或跳过。
- 输入 Map 完整保留，输出表和 Geometry 字段只追加不覆盖。
- BATCH 与 STREAMING 使用同一 Operator，并继承有界性、事件时间和 Watermark。
- Compiler 不读取真实载荷、不执行样例解析或 Spark Action。
- Inspector 只使用权威 `inputTables`，上游失效配置得到保留。
- 安全摘要和失败日志不包含任何空间数据值。

## 9. 不在范围内

- EWKT、EWKB、HEX WKB、KML、GML、GeoHash 或自定义格式。
- CRS 自动识别、嵌入式 SRID 信任、坐标轴自动交换和隐式坐标转换。
- 坏值置空、错误行旁路、解析成功率统计和样例预览。
- Geometry 修复、简化、单体/Multi 转换和通用 GEOMETRY 混合类型构造。
- 一次构造多个 Geometry 字段。

## 10. 协议发布

本节点已与 `GEOMETRY_VALIDATE`、`SPATIAL_MEASURE`、`GEOMETRY_SERIALIZE` 一起在 Canvas
`1.21` 引入。当前实现兼容读取 `1.0`～`1.22`，并统一规范化写出 `1.22`；低版本定义
不得携带本节点。
