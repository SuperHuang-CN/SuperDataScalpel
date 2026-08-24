# Canvas `GEOMETRY_SERIALIZE` Processor 设计文档

## 1. 状态、目标与范围

- 实现状态：已实现。
- 目标协议版本：Canvas `1.21`。
- 节点类型：`GEOMETRY_SERIALIZE`。
- 节点类别：`PROCESSOR`。
- 执行模式：`BATCH`、`STREAMING`。
- 图规则：至少一条入边和一条出边；多个上游表 Map 先执行无覆盖合并。

`GEOMETRY_SERIALIZE` 将一张逻辑表中的一个 Geometry 字段序列化为 WKT、WKB 或标准
GeoJSON 字段，在保留原 Geometry 和其他来源字段的同时追加一个普通标量字段。该节点
用于写入不支持 Geometry 的文件、Kafka、HTTP 载荷或普通数据库字段，也用于与
`GEOMETRY_CONSTRUCT` 形成明确的空间值进出闭环。

节点只改变表示形式，不转换 CRS、不删除 Geometry 字段，也不把空间数据写入日志。

## 2. 稳定配置协议

```ts
interface GeometrySerializeConfiguration {
  sourceTableName: string;
  outputTableName: string;
  geometryColumnName: string;
  outputColumnName: string;
  format: GeometrySerializationFormat;
}

type GeometrySerializationFormat = 'WKT' | 'WKB' | 'GEOJSON';
```

WKT 示例：

```json
{
  "sourceTableName": "service_locations",
  "outputTableName": "service_locations_wkt",
  "geometryColumnName": "location",
  "outputColumnName": "location_wkt",
  "format": "WKT"
}
```

GeoJSON 示例：

```json
{
  "sourceTableName": "districts_4326",
  "outputTableName": "districts_geojson",
  "geometryColumnName": "boundary",
  "outputColumnName": "boundary_geojson",
  "format": "GEOJSON"
}
```

协议限制：

- `sourceTableName`、`outputTableName`、`geometryColumnName`、`outputColumnName` 必填。
- `format` 必填，只允许 WKT、WKB、GEOJSON。
- 来源字段必须是带完整 Geometry 定义的 `GEOMETRY`。
- `outputColumnName` 不能与来源字段重名。
- 一个节点只序列化一个 Geometry 字段并产生一个标量字段。
- 配置不保存序列化结果、样例值、精度参数、任意函数名或 UI 内部状态。

## 3. 序列化语义

Operator 使用 Sedona Column API：

- WKT：`ST_AsText`，输出平台 STRING。
- WKB：`ST_AsBinary`，输出平台 BINARY。
- GEOJSON：`ST_AsGeoJSON`，输出平台 STRING。

统一语义：

- 来源 Geometry 为 NULL 时，序列化结果为 NULL。
- WKT 和 WKB 不携带平台可信 CRS；后续重新构造 Geometry 时仍必须显式配置目标 CRS。
- GeoJSON 首期严格遵循 WGS84 坐标语义，来源必须为 EPSG:4326。
- 非 EPSG:4326 输出 GeoJSON 时返回编译错误，用户必须先显式连接
  `SPATIAL_TRANSFORM`，节点不得自动转换。
- WKT/WKB 允许任意当前受支持的 EPSG CRS 和 GeometryKind。
- 节点不改变原 Geometry 的 kind、CRS、dimension 或数据值。
- 不提供输出文本截断、坐标精度裁剪、字符编码选择或二进制十六进制化。

Compiler 只分析零行表达式和输出 Schema，不读取真实 Geometry，不生成样例序列化值。

## 4. Map、Schema 与批流传播

Map 规则：

- 从输入 Map 按 `sourceTableName` 精确查找来源表。
- 保留输入 Map 中全部表，以 `outputTableName` 追加序列化结果。
- 输出名与任一已有 Key 冲突时返回 `DUPLICATE_TABLE_NAME`。

Schema 规则：

- 来源字段及 Geometry 空间元数据完整保留。
- 新标量字段追加在所有来源字段之后。
- WKT/GEOJSON 字段为 STRING，length 为空；WKB 字段为 BINARY。
- 输出字段 nullable 与来源 Geometry 字段一致。
- 新字段的 precision、scale、geometry、default、autoIncrement、generated 和 comment
  均为空或 false。
- 输出表 `origin=null`，继承来源 `datasetKind`、`eventTimeColumn` 和 `watermarkDelay`。

节点逐行、无状态且不改变行数。BOUNDED 和 UNBOUNDED 输入复用同一 Operator，不改变
事件时间或 Watermark。Kafka Value Schema 继续拒绝 GEOMETRY；实时写 Kafka 时应在本
节点后使用 `SELECT_COLUMNS` 选择序列化字段并排除原 Geometry。

## 5. 校验与稳定错误码

复用错误码：

- `CONFIGURATION_REQUIRED`
- `REQUIRED_CONFIGURATION`
- `TABLE_NOT_FOUND`
- `COLUMN_NOT_FOUND`
- `DUPLICATE_TABLE_NAME`
- `DUPLICATE_COLUMN_NAME`
- `GEOMETRY_FIELD_OPERATION_UNSUPPORTED`
- `GEOMETRY_TYPE_DEFINITION_REQUIRED`
- `UNSUPPORTED_GEOMETRY_CRS`
- `UNSUPPORTED_GEOMETRY_DIMENSION`
- `SPARK_ANALYSIS_ERROR`
- `NODE_EXECUTION_MODE_NOT_SUPPORTED`
- `UPSTREAM_INVALID`

新增错误码：

| 错误码 | 条件 |
| --- | --- |
| `INVALID_GEOMETRY_SERIALIZATION_FORMAT` | format 缺失或不是 WKT/WKB/GEOJSON |
| `GEOJSON_REQUIRES_WGS84` | GEOJSON 来源 Geometry 不是 EPSG:4326 |

未知 format 属于无法安全解释的协议问题，JSON 导入时拒绝。已知格式但上游字段、CRS 或
类型失效属于业务无效定义，允许在 Inspector 中回显并由 Compiler 给出节点错误。

## 6. 前端设计器

- Palette 名称为“Geometry 序列化”，说明为“将空间字段转换为 WKT、WKB 或 GeoJSON”。
- 节点位于“空间处理”分组，批处理和实时模式均展示。
- Inspector 的来源表和字段候选只使用 Compiler `inputTables`，字段列表只显示 GEOMETRY。
- 格式使用三个明确选项，并在选项旁展示输出平台类型。
- 选择 GEOJSON 时显示“来源必须为 EPSG:4326”的说明；非 4326 已保存值保留并标红。
- 面板明确提示节点保留原 Geometry；写 Kafka 时还需通过 `SELECT_COLUMNS` 移除 Geometry。
- 上游变化导致表、字段或 Geometry 定义失效时不得自动切换格式、字段或 CRS。
- 节点摘要显示来源表/字段、输出表/字段和格式，不显示序列化结果。

## 7. Task Engine、Runner 与安全边界

新增唯一无状态 `GeometrySerializeNodeOperator`，Compiler 与 Runner 通过同一内置 Registry
调用。执行阶段固定为 `PROCESS`，成功消息固定为“Geometry 序列化已准备”。

安全摘要只允许记录来源表、输出表、Geometry 字段、输出字段、格式和来源 Geometry 的
kind/CRS/dimension。WKT、WKB、GeoJSON 内容属于业务数据，禁止进入节点摘要、日志、异常、
执行结果、编译响应或 Kafka 生命周期事件。

节点成功只表示序列化表达式已准备，不得为日志生成样例值、长度统计或 `count()`。

## 8. 行为验收标准

- 三种格式配置可稳定 JSON 往返，未知格式被严格拒绝。
- WKT、WKB、GeoJSON 输出分别具有 STRING、BINARY、STRING 平台类型。
- NULL 传播、输出字段顺序、nullable 和来源 Geometry 元数据保持稳定。
- GeoJSON 对 EPSG:4326 可执行，对其他 CRS 返回准确错误且不自动转换。
- 输入 Map 完整保留，输出表和序列化字段只追加不覆盖。
- BATCH 与 STREAMING 使用同一 Operator，并继承有界性、事件时间和 Watermark。
- Compiler 不读取真实 Geometry、不生成预览、不触发 Spark Action。
- Inspector 只使用权威 `inputTables` 并保留失效上游值。
- 安全摘要和日志不包含任何序列化空间值。

## 9. 不在范围内

- EWKT、EWKB、HEXEWKB、KML、GML、GeoHash 和自定义序列化格式。
- GeoJSON Feature/FeatureCollection 包装、属性映射和完整文件生成。
- 精度裁剪、坐标压缩、字符编码、Pretty Print 和输出长度限制。
- 隐式 CRS 转换、Geometry 修复、简化或类型转换。
- 一次序列化多个 Geometry 字段。

## 10. 协议发布

本节点已与 `GEOMETRY_CONSTRUCT`、`GEOMETRY_VALIDATE`、`SPATIAL_MEASURE` 一起在
Canvas `1.21` 引入。当前实现兼容读取 `1.0`～`1.22`，并统一规范化写出 `1.22`；现有
`SPATIAL_TRANSFORM` 和 `SPATIAL_JOIN` 的协议及批处理限制不变。
