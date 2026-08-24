# Canvas `GEOMETRY_EXPLODE` Processor 开发文档

## 1. 状态、目标与范围

- 实现状态：已实现并通过统一验收。
- 目标协议版本：Canvas `1.22`。
- 节点类型：`GEOMETRY_EXPLODE`。
- 节点类别：`PROCESSOR`。
- 执行模式：`BATCH`、`STREAMING`。
- 图规则：至少一条入边和一条出边；多个上游表 Map 先执行无覆盖合并。

`GEOMETRY_EXPLODE` 将一行中的 MultiGeometry 或 GeometryCollection 拆成多行，每个部件
一行，并复制该行的全部原始属性。它用于规范 Repair、Buffer、Clip 等空间操作产生的多
部件结果，使后续测量、序列化和输出可以按单部件处理。

节点固定使用外层展开语义：NULL 或 Empty 来源不会静默丢行，而是保留一行并产生 NULL
部件。首版不增加可能造成数据丢失的 drop-null/drop-empty 开关。

## 2. 稳定配置协议

```ts
interface GeometryExplodeConfiguration {
  sourceTableName: string;
  outputTableName: string;
  geometryColumnName: string;
  outputColumnName: string;
  partIndexColumnName: string | null;
}
```

示例：

```json
{
  "sourceTableName": "parcels_repaired",
  "outputTableName": "parcel_parts",
  "geometryColumnName": "repaired_boundary",
  "outputColumnName": "boundary_part",
  "partIndexColumnName": "part_index"
}
```

协议限制：

- 来源表、输出表、来源 Geometry 字段和部件输出字段必填。
- `partIndexColumnName` 为 NULL 时不输出序号；非 NULL 时必须为非空字段名。
- 输出字段和可选序号字段不能与来源字段或彼此重名。
- 来源必须是带完整空间定义的平台 `GEOMETRY`，首版只支持 `EPSG + XY`。
- 配置不保存最大展开数量、数据采样、任意表达式或 Geometry 值。

## 3. 展开语义

Operator 使用 `ST_Dump` 得到组件数组：

- 未配置部件序号时使用 `explode_outer(ST_Dump(geometry))`。
- 配置部件序号时使用 `posexplode_outer(ST_Dump(geometry))`。
- 部件序号从 0 开始，按照 Sedona `ST_Dump` 返回顺序生成。
- MultiPoint、MultiLineString、MultiPolygon 和 GeometryCollection 按 Sedona 组件展开。
- Point、LineString、Polygon 等单体 Geometry 产生一行同类部件。
- NULL 或 Empty 输入保留一行，部件为 NULL；配置序号时序号也为 NULL。
- 原 Geometry 字段继续保留，不被部件字段替换。

输出 GeometryKind 根据来源元数据静态推导：

| 来源 kind | 部件 kind |
| --- | --- |
| `MULTIPOINT` | `POINT` |
| `MULTILINESTRING` | `LINESTRING` |
| `MULTIPOLYGON` | `POLYGON` |
| `POINT` / `LINESTRING` / `POLYGON` | 与来源相同 |
| `GEOMETRY` / `GEOMETRYCOLLECTION` | `GEOMETRY` |

Compiler 只分析零行生成器计划，不能根据真实 Geometry 估计输出行数或组件分布。

## 4. Map、Schema、行数与批流传播

Map 规则：

- 按 `sourceTableName` 精确选择来源表。
- 保留输入 Map 中全部表，以 `outputTableName` 追加展开表。
- 同名输出表返回 `DUPLICATE_TABLE_NAME`。

Schema 规则：

- 来源字段、顺序及元数据完整保留。
- 部件 Geometry 字段追加在来源字段之后，CRS 和 dimension 继承来源定义。
- 部件字段固定 nullable，因为 NULL/Empty 会由 outer 展开产生 NULL。
- 可选 `partIndexColumnName` 最后追加为 nullable `INTEGER`，从 0 开始。
- 输出表 `origin=null`，继承来源 `datasetKind`、`eventTimeColumn` 和 `watermarkDelay`。

节点会把一行扩展成一至多行，但仍是无状态逐行变换，不聚合、不跨行关联。流式输入允许
使用；每个展开结果继承来源行的事件时间，Watermark 定义保持不变。

## 5. 校验与稳定错误码

复用表、字段、Geometry 定义、重复名称、Spark 分析、模式和上游错误码。新增：

| 错误码 | 阶段 | 条件 |
| --- | --- | --- |
| `GEOMETRY_EXPLODE_FAILED` | Runner | 真实 Geometry 无法由 Sedona 展开 |

首版不设置静态展开上限，因为 Compiler 不读取真实数据；任务资源控制由 Spark 运行配置和
平台任务级限制负责。错误和日志不得包含被展开的 Geometry 或任一部件值。

## 6. 前端设计器

- Palette 名称为“Geometry 拆分”，说明为“将 MultiGeometry 或集合拆成多行部件”。
- 位于“空间处理”分组，顺序在 Buffer 之后、空间测量之前。
- Inspector 从 Compiler `inputTables` 获取表与 Geometry 字段候选。
- 使用开关控制是否输出部件序号；关闭时协议写入 NULL，而不是空字符串。
- 面板明确展示“可能增加行数”“NULL/Empty 保留一行”“序号从 0 开始”。
- 根据所选字段的 GeometryKind 展示预计部件 kind；通用或集合显示 `GEOMETRY`。
- 上游失效值继续显示并标红。
- 节点摘要显示来源表、字段、输出字段、序号字段和预计部件 kind。

## 7. Task Engine、Runner 与安全边界

新增唯一无状态 `GeometryExplodeNodeOperator`。节点阶段为 `PROCESS`，成功消息为
“Geometry 拆分已准备”。Compiler 与 Runner 使用同一 Operator 和显式 Registry。

安全摘要只记录表名、字段名、是否输出序号和静态部件 kind；不得记录组件数量统计、
Geometry、坐标、WKT/WKB 或数据行。成功日志不得为了统计展开行数额外触发 Action。

## 8. 验收标准

- 配置和可选序号能够严格 JSON 往返，并受 Canvas `1.22` 门槛约束。
- MultiPoint、MultiLineString、MultiPolygon、GeometryCollection 和单体 Geometry 正确展开。
- NULL 与 Empty 保留一行，部件和序号 nullable 语义正确。
- 来源属性复制、字段顺序、部件 kind、CRS 和 dimension 正确传播。
- 输出 Map 追加、表名和字段名冲突校验正确。
- BATCH、STREAMING、有界性、事件时间和 Watermark 正确传播。
- Inspector、Registry、图规则、摘要、生命周期和安全错误分类完整接入。
- 日志和结果不包含 Geometry 或部件值。

## 9. 不在范围内

- 拆分 Polygon rings、LineString vertices 或输出坐标点。
- 丢弃 NULL/Empty、限制每行最大部件数或截断结果。
- 递归层级选择、保留 Sedona dump path 数组或按部件类型过滤。
- 自动转换 CRS、修复部件、聚合部件或合并相邻 Geometry。
