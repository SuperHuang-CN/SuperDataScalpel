# Canvas `GEOMETRY_REPAIR` Processor 开发文档

## 1. 状态、目标与范围

- 实现状态：已实现并通过统一验收。
- 目标协议版本：Canvas `1.22`。
- 节点类型：`GEOMETRY_REPAIR`。
- 节点类别：`PROCESSOR`。
- 执行模式：`BATCH`、`STREAMING`。
- 图规则：恰好一条入边，至少一条出边。

`GEOMETRY_REPAIR` 对一张逻辑表中的 Geometry 字段执行拓扑修复，在保留原字段的同时
追加修复结果。它与 `GEOMETRY_VALIDATE` 形成明确分工：Validate 只诊断，Repair 才改变
Geometry；节点不删除行、不覆盖原字段，也不隐式转换 CRS。

首版固定使用 Apache Sedona `ST_MakeValid`，不提供多个修复算法、容差、精度网格或失败
置空策略。修复结果可能改变 GeometryKind，因此稳定 Schema 必须如实声明为通用
`GEOMETRY`，不能沿用来源字段的具体 kind。

## 2. 稳定配置协议

```ts
interface GeometryRepairConfiguration {
  sourceTableName: string;
  outputTableName: string;
  geometryColumnName: string;
  outputColumnName: string;
}
```

示例：

```json
{
  "sourceTableName": "parcels_validated",
  "outputTableName": "parcels_repaired",
  "geometryColumnName": "boundary",
  "outputColumnName": "repaired_boundary"
}
```

协议限制：

- 四个字段均必填，字段名按上游 Schema 原始值精确匹配。
- 来源字段必须是带完整空间定义的平台 `GEOMETRY` 字段。
- 首版只支持 `EPSG + XY`。
- `outputColumnName` 不能与来源字段重名。
- 配置不保存 Sedona 函数、SQL、Geometry 值、修复原因、UI 状态或执行统计。
- 首版不增加只有一个合法取值的 `method`、`failureStrategy` 或 `keepCollapsed` 字段。

## 3. 修复语义

Operator 使用类型安全的 Sedona Column API：

1. 对来源字段调用 `ST_MakeValid(geometry, false)`。
2. `keepCollapsed=false`，不保留因修复而降维的塌缩部件。
3. 对结果显式调用 `ST_SetSRID`，使用来源空间定义中的 EPSG code。
4. 原 Geometry 字段保持不变，修复字段追加到结果表末尾。

统一行为：

- NULL Geometry 输出 NULL。
- Empty Geometry 仍按 Sedona `ST_MakeValid` 语义产生 Empty 结果，不删除来源行。
- 已经合法的 Geometry 允许由 Sedona 返回拓扑等价结果；不承诺 WKB 字节完全不变。
- 无法完成修复时任务失败，不跳过行、不返回原值，也不静默置 NULL。
- 修复后可能从 Polygon 变为 MultiPolygon，或从其他输入变为集合，因此输出字段固定声明
  为 `GeometryKind.GEOMETRY`。
- Compiler 只在零行 Dataset 上构造并分析计划，不读取真实 Geometry，也不承诺每个值都
  一定能够修复。

## 4. Map、Schema 与批流传播

Map 规则：

- 从输入 Map 按 `sourceTableName` 精确查找来源表。
- 保留输入 Map 中全部表，不修改来源表。
- 以 `outputTableName` 追加修复结果表。
- 输出表名与任一现有 Key 冲突时返回 `DUPLICATE_TABLE_NAME`。

Schema 规则：

- 来源字段名称、顺序、类型及元数据完整保留。
- 修复字段追加到末尾，平台类型为 `GEOMETRY`。
- 输出 Geometry 定义继承来源 CRS 和 dimension，kind 固定为 `GEOMETRY`。
- 输出字段 nullable 继承来源 Geometry 字段。
- 输出字段的 default、autoIncrement、generated 和 comment 清空。
- 输出表 `origin=null`，继承来源 `datasetKind`、`eventTimeColumn` 和 `watermarkDelay`。

节点逐行处理且不改变行数，不引入聚合、状态或 Shuffle。BOUNDED 与 UNBOUNDED 输入均
使用同一个 Operator，事件时间和 Watermark 原样传播。

## 5. 校验与稳定错误码

复用：

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

新增：

| 错误码 | 阶段 | 条件 |
| --- | --- | --- |
| `GEOMETRY_REPAIR_FAILED` | Runner | 真实 Geometry 无法由 Sedona 完成修复 |

Runner 对外只返回安全的通用失败描述。异常日志不得包含原始 WKT、WKB、GeoJSON、坐标、
Geometry `toString()` 或所在数据行。

## 6. 前端设计器

- Palette 名称为“Geometry 修复”，说明为“修复无效 Geometry，并保留原空间字段”。
- 位于 Processor 的“空间处理”分组，顺序在 Geometry 校验之后、Geometry Buffer 之前。
- BATCH 与 STREAMING 均展示，协议引入版本为 `1.22`。
- Inspector 的表和字段候选只来自 Compiler `inputTables`。
- Geometry 下拉只展示平台 `GEOMETRY` 字段。
- 上游表或字段失效时保留原值并标红，不静默清空。
- 面板明确说明结果 kind 会泛化为 `GEOMETRY`，需要按部件处理时可继续连接
  `GEOMETRY_EXPLODE`。
- 节点摘要只显示来源表、Geometry 字段、输出表和输出字段。

## 7. Task Engine、Runner 与安全边界

新增唯一无状态 `GeometryRepairNodeOperator`，Compiler 和 Runner 通过同一内置 Registry
调用。节点阶段为 `PROCESS`，成功消息为“Geometry 修复已准备”。

安全摘要只允许记录：来源表、输出表、来源字段和输出字段。不得记录 Geometry 值、修复前后
差异、无效原因或修复结果。Compiler 不触发 Spark Action，Runner 不为日志额外扫描数据。

## 8. 验收标准

- 配置能够严格 JSON 往返，低于 `1.22` 的定义不能携带本节点。
- Polygon 自相交等无效 Geometry 可以修复；合法、NULL 和 Empty 输入符合既定语义。
- 原字段保留，修复字段追加，输出 kind 为通用 `GEOMETRY`。
- 输入 Map 完整保留，同名表和字段冲突得到稳定错误。
- BATCH、STREAMING 继承有界性、事件时间和 Watermark。
- Inspector 使用权威 `inputTables` 并保留失效值。
- Registry、图规则、生命周期日志、安全摘要和失败分类完整接入。
- 日志、结果和错误信息不包含任何空间数据值。

## 9. 不在范围内

- Snap、Reduce Precision、零宽 Buffer、Simplify 等替代修复策略。
- 修改原 Geometry 字段、删除无法修复的行、失败置 NULL 或错误行旁路。
- 保留降维塌缩部件、选择修复后具体 GeometryKind 或强制 Multi 类型。
- 输出修复标记、修复原因、修复前后面积差等诊断列。
