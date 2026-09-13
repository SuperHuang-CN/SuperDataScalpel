# Canvas `GEOMETRY_BUFFER` Processor 开发文档

## 1. 状态、目标与范围

- 实现状态：基础 Buffer 已实现；Canvas 4.49 补充显式距离单位，4.52 补充固定值、字段和受控表达式三种逐行距离来源。样式与 Dissolve 仍不在当前协议。
- 初始协议版本：Canvas `1.22`；显式距离单位：Canvas `4.49`；逐行距离来源：Canvas `4.52`。
- 节点类型：`GEOMETRY_BUFFER`。
- 节点类别：`PROCESSOR`。
- 执行模式：`BATCH`、`STREAMING`。
- 图规则：至少一条入边，允许没有出边；多个上游表 Map 先执行无覆盖合并。

`GEOMETRY_BUFFER` 围绕一张逻辑表中的 Geometry 生成缓冲区，在保留来源行和原 Geometry
的同时追加规范化的 MultiPolygon 字段。首版提供明确的 `PLANAR`、`SPHEROID` 两种
距离语义。4.49 起单位由用户显式选择并只换算距离数值，不隐式转换 Geometry CRS；4.52
可从数值字段或受控的确定性 Spark 表达式逐行计算距离。

首版只接受严格正距离，使用 Sedona 默认的圆角缓冲参数；负距离、零距离修复、端帽、连接
样式和象限分段不进入稳定协议。

## 2. 稳定配置协议

```ts
interface GeometryBufferConfiguration {
  sourceTableName: string;
  outputTableName: string;
  geometryColumnName: string;
  outputColumnName: string;
  distance: number; // 固定值草稿
  mode: 'PLANAR' | 'SPHEROID';
  distanceUnit?: SpatialDistanceUnit | null;
  distanceSource?: 'CONSTANT' | 'FIELD' | 'EXPRESSION' | null;
  distanceFieldName?: string | null;
  distanceExpression?: string | null;
}
```

示例：

```json
{
  "sourceTableName": "service_points",
  "outputTableName": "service_areas",
  "geometryColumnName": "location",
  "outputColumnName": "service_area",
  "distance": 1000,
  "mode": "SPHEROID",
  "distanceUnit": "METERS",
  "distanceSource": "FIELD",
  "distanceFieldName": "service_radius",
  "distanceExpression": null
}
```

协议限制：

- 表名、字段名、距离来源、距离草稿、单位和模式均为正式配置的一部分。
- 来源字段必须是带完整空间定义的平台 `GEOMETRY` 字段。
- 首版只支持 `EPSG + XY`。
- `distanceSource` 缺失/null 保持 4.51 及以前的 `CONSTANT` 语义。任意非 null 来源以及
  `distanceFieldName/distanceExpression` 的非 null 草稿在低于 4.52 时返回
  `GEOMETRY_BUFFER_DISTANCE_SOURCE_REQUIRE_SCHEMA_VERSION`。
- `CONSTANT` 使用 `distance`，它必须为有限正数；`FIELD` 使用一个可由 Spark Analyzer
  证明为数值的来源字段；`EXPRESSION` 使用一个可解析为数值的确定性逐行表达式。
- 表达式不允许 SQL 语句、聚合、窗口、生成器或子查询，不接受自定义 `OVER`；表达式字段只来自当前行。
- 切换距离来源不清除其他分支草稿；只有当前来源参与执行和业务校验。
- `SPHEROID` 只接受 EPSG:4326，并将明确线性单位换算为米；不接受 `SOURCE_CRS_UNIT`。
- `PLANAR` 在投影 CRS 中将明确线性单位换算为来源轴单位；地理 CRS 只接受
  `SOURCE_CRS_UNIT`，并产生角度单位 Warning。
- `distanceUnit` 缺失/null 保持 4.49 之前的行为：PLANAR 使用来源 CRS 单位，SPHEROID 使用米。
  任何非 null 单位在低于 4.49 的定义中返回 `GEOMETRY_BUFFER_UNIT_REQUIRE_SCHEMA_VERSION`。
- `outputColumnName` 不能与来源字段重名。
- 配置不保存任意 Sedona style 字符串、单位换算系数或 Geometry 值。受控表达式属于稳定配置，
  但不得进入 Canvas 摘要、运行摘要或错误信息。

## 3. Buffer 与结果规范化语义

PLANAR：

- 使用 `ST_Buffer(geometry, resolvedDistance)`。
- 投影 CRS 将所选线性单位换算到来源第一坐标轴单位；`SOURCE_CRS_UNIT` 原值传入。
- 地理 CRS 只允许 `SOURCE_CRS_UNIT`，距离是来源角度单位，产生
  `PLANAR_BUFFER_USES_ANGULAR_UNITS` Warning；不使用隐藏的米转角度近似。

SPHEROID：

- 使用 `ST_Buffer(geometry, resolvedMetres, true)`。
- 仅允许 EPSG:4326；所选固定线性单位统一换算为米。
- 不接受其他地理 CRS，也不在节点内部执行 `ST_Transform`。

距离来源先得到当前行的数值，再按同一 `distanceUnit + mode` 规则换算。动态来源中：

- NULL 值产生 NULL Buffer，不删除来源行。
- 非 NULL 值必须能转换为有限正 DOUBLE；0、负数、NaN、Infinity 或转换溢出在真实 Action 时以
  `GEOMETRY_BUFFER_DISTANCE_VALUE_INVALID` 失败，不静默置 NULL 或跳过。
- Compiler 只通过 Analyzer 验证逐行标量和数值类型，不扫描数据或执行表达式。

两种模式均在 Buffer 后调用 `ST_Multi`，并用 `ST_SetSRID` 显式恢复来源 EPSG code，使稳定
输出 GeometryKind 固定为 `MULTIPOLYGON`。统一行为：

- NULL Geometry 输出 NULL。
- 非 NULL 输入可能产生 Empty MultiPolygon，但不会因此删除来源行。
- 来源 GeometryKind 可以是现有任意 kind，包括通用 Geometry 和 GeometryCollection。
- Buffer 执行失败时任务失败，不置 NULL、不跳过行。
- Compiler 只构造零行计划，不读取真实 Geometry，也不判断实际缓冲是否为空。

## 4. Map、Schema 与批流传播

Map 规则：

- 按 `sourceTableName` 精确选择来源表。
- 保留输入 Map 中全部表和来源表。
- 以 `outputTableName` 追加结果；同名返回 `DUPLICATE_TABLE_NAME`。

Schema 规则：

- 来源字段和元数据完整保留。
- Buffer 字段追加到末尾，平台类型为 `GEOMETRY`。
- 输出 Geometry kind 固定为 `MULTIPOLYGON`，CRS 和 dimension 继承来源定义。
- 输出字段 nullable 继承来源 Geometry 字段。
- 输出表 `origin=null`，继承来源 `datasetKind`、`eventTimeColumn` 和 `watermarkDelay`。

节点逐行无状态处理，不改变输入行数，不增加流式状态。BOUNDED 与 UNBOUNDED 输入都由同一
Operator 处理，事件时间和 Watermark 保持不变。

## 5. 校验与稳定错误码

复用空间字段、表名、字段名、Schema、模式和 Spark 分析相关错误码。新增：

| 错误码 | 级别/阶段 | 条件 |
| --- | --- | --- |
| `INVALID_GEOMETRY_BUFFER_DISTANCE` | ERROR / Compiler | distance 不是有限正数 |
| `SPHEROID_BUFFER_REQUIRES_WGS84` | ERROR / Compiler | SPHEROID 来源不是 EPSG:4326 |
| `PLANAR_BUFFER_USES_ANGULAR_UNITS` | WARNING / Compiler | EPSG:4326 使用 PLANAR |
| `SPATIAL_DISTANCE_UNIT_UNSUPPORTED` | ERROR / Compiler | 当前 CRS/模式无法解释或换算所选距离单位 |
| `GEOMETRY_BUFFER_UNIT_REQUIRE_SCHEMA_VERSION` | ERROR / 保存、导入、Compiler | 低于 4.49 却携带显式单位 |
| `GEOMETRY_BUFFER_DISTANCE_SOURCE_REQUIRE_SCHEMA_VERSION` | ERROR / 保存、导入、Compiler | 低于 4.52 携带距离来源或其草稿 |
| `INVALID_GEOMETRY_BUFFER_DISTANCE_SOURCE` | ERROR / Compiler | FIELD 不存在或不能分析为逐行数值字段 |
| `INVALID_GEOMETRY_BUFFER_EXPRESSION` | ERROR / Compiler | EXPRESSION 不安全、不可解析、非数值或不是逐行标量 |
| `GEOMETRY_BUFFER_DISTANCE_VALUE_INVALID` | ERROR / Runner | 动态距离真实值为 0、负数、NaN、Infinity 或转换溢出 |
| `GEOMETRY_BUFFER_FAILED` | ERROR / Runner | 真实 Geometry 无法完成 Buffer |

Warning 不阻断 Schema 和下游传播。Runner 失败信息不得携带原始 Geometry、坐标、缓冲距离
计算结果或数据行。

## 6. 前端设计器

- Palette 名称为“Geometry Buffer”，说明为“按平面或椭球距离生成缓冲区”。
- 位于“空间处理”分组，顺序在 Geometry 修复之后、Geometry 拆分之前。
- Inspector 从 Compiler `inputTables` 选择来源表和 Geometry 字段。
- 模式和距离来源使用紧凑分段选择；固定值、数值字段或表达式只显示当前分支，单位复用公共名称和帮助。
- 字段候选只展示数值字段；表达式提供邻近帮助和多行输入，失效字段/表达式保留并标红。
- EPSG:4326 + PLANAR 只允许来源 CRS 角度单位并显示 Warning；非 EPSG:4326 + SPHEROID、
  SPHEROID + SOURCE_CRS_UNIT 以及地理平面 + 固定线性单位均保留原值并标红。
- 切换模式或单位不自动修改距离数值。
- 面板提示输出固定为 MultiPolygon，原 Geometry 不被覆盖。
- 节点摘要显示来源表、Geometry 字段、模式、距离来源、单位和输出字段；固定值可显示配置距离，
  FIELD 可显示字段名，EXPRESSION 只显示“逐行表达式”，不显示正文或字面量。

## 7. Task Engine、Runner 与安全边界

新增唯一无状态 `GeometryBufferNodeOperator`，Compiler 与 Runner 共享。节点阶段为
`PROCESS`，成功消息为“Geometry Buffer 已准备”。

Sedona `ST_Buffer(geometry, distance, true)` 的 SPHEROID 实现依赖 GeoTools。Task Engine
固定引入与 Sedona 1.9.0 配套的 `org.datasyslab:geotools-wrapper:1.9.0-33.5`，并将其同时
打入 Local Runner 和 Cluster Runner；制品校验必须确认 GeoTools CRS API 已进入 Cluster
Runner，避免预检成功但真实运行出现类缺失。

安全摘要允许记录表名、字段名、固定距离、距离来源、单位、模式和 CRS。动态表达式和逐行距离属于数据值；不得记录
实际 Geometry、坐标、缓冲面积、结果 WKT/WKB 或数据行。异常链使用统一日志脱敏。

## 8. 验收标准

- PLANAR、SPHEROID、显式单位和三种距离来源严格 JSON 往返；旧缺失字段语义兼容，新增来源受 Canvas 4.52 门槛约束。
- 字段和表达式分别按每行数值产生不同 Buffer；NULL 保留行并输出 NULL；非正/非有限动态值稳定失败。
- 非数值字段、聚合/窗口/子查询和不可解析表达式在 Compiler 阶段拒绝，预检不触发 Spark Action。
- Point、LineString、Polygon、Multi 和集合输入均产生 MultiPolygon Schema。
- 正距离校验、WGS84 限制和角度单位 Warning 准确。
- NULL、Empty 和普通 Geometry 行数保持语义正确。
- 原字段保留，Buffer 字段追加，CRS/dimension/nullable 正确传播。
- BATCH、STREAMING、有界性、事件时间和 Watermark 正确传播。
- Inspector 保留失效值，Registry、图规则、摘要、生命周期和错误分类完整接入。
- 日志与错误不包含空间数据值。

## 9. 不在范围内

- 负距离、零距离修复、单侧 Buffer；动态距离中的非正值也不会启用这些语义。
- cap style、join style、quadrant segments、mitre limit 等样式参数。
- 自动选择/转换 Geometry CRS、任意地理 CRS 椭球 Buffer；仅对明确固定线性单位做数值换算。
- 合并相邻缓冲区、Dissolve、按字段分组聚合。
