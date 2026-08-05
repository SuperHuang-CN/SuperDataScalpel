# Canvas `GEOMETRY_BUFFER` Processor 开发文档

## 1. 状态、目标与范围

- 实现状态：已实现并通过统一验收。
- 目标协议版本：Canvas `1.22`。
- 节点类型：`GEOMETRY_BUFFER`。
- 节点类别：`PROCESSOR`。
- 执行模式：`BATCH`、`STREAMING`。
- 图规则：恰好一条入边，至少一条出边。

`GEOMETRY_BUFFER` 围绕一张逻辑表中的 Geometry 生成缓冲区，在保留来源行和原 Geometry
的同时追加规范化的 MultiPolygon 字段。首版提供明确的 `PLANAR`、`SPHEROID` 两种
距离语义，不猜测单位、不隐式转换 CRS。

首版只接受严格正距离，使用 Sedona 默认的圆角缓冲参数；负距离、零距离修复、端帽、连接
样式和象限分段不进入稳定协议。

## 2. 稳定配置协议

```ts
interface GeometryBufferConfiguration {
  sourceTableName: string;
  outputTableName: string;
  geometryColumnName: string;
  outputColumnName: string;
  distance: number;
  mode: 'PLANAR' | 'SPHEROID';
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
  "mode": "SPHEROID"
}
```

协议限制：

- 表名、字段名、`distance` 和 `mode` 均为正式配置的一部分。
- 来源字段必须是带完整空间定义的平台 `GEOMETRY` 字段。
- 首版只支持 `EPSG + XY`。
- `distance` 必须是有限正数，不能为 0、负数、NaN 或 Infinity。
- `SPHEROID` 只接受 EPSG:4326，距离单位固定为米。
- `PLANAR` 使用来源 CRS 的坐标单位；EPSG:4326 下允许生成计划但产生角度单位 Warning。
- `outputColumnName` 不能与来源字段重名。
- 配置不保存任意 Sedona style 字符串、SQL、单位换算系数或 Geometry 值。

## 3. Buffer 与结果规范化语义

PLANAR：

- 使用 `ST_Buffer(geometry, distance)`。
- 距离使用来源 CRS 坐标单位。
- EPSG:4326 的距离是角度，产生 `PLANAR_BUFFER_USES_ANGULAR_UNITS` Warning。

SPHEROID：

- 使用 `ST_Buffer(geometry, distance, true)`。
- 仅允许 EPSG:4326，距离单位为米。
- 不接受其他地理 CRS，也不在节点内部执行 `ST_Transform`。

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
| `GEOMETRY_BUFFER_FAILED` | ERROR / Runner | 真实 Geometry 无法完成 Buffer |

Warning 不阻断 Schema 和下游传播。Runner 失败信息不得携带原始 Geometry、坐标、缓冲距离
计算结果或数据行。

## 6. 前端设计器

- Palette 名称为“Geometry Buffer”，说明为“按平面或椭球距离生成缓冲区”。
- 位于“空间处理”分组，顺序在 Geometry 修复之后、Geometry 拆分之前。
- Inspector 从 Compiler `inputTables` 选择来源表和 Geometry 字段。
- 模式使用分段选择；PLANAR 明确展示“来源 CRS 坐标单位”，SPHEROID 展示“米”。
- EPSG:4326 + PLANAR 显示角度单位 Warning；非 EPSG:4326 + SPHEROID 原值保留并标红。
- 距离使用大于 0 的数值输入，不提供隐式单位换算。
- 面板提示输出固定为 MultiPolygon，原 Geometry 不被覆盖。
- 节点摘要显示来源表、字段、模式、距离、输出字段和 CRS；不显示 Geometry 或计算结果。

## 7. Task Engine、Runner 与安全边界

新增唯一无状态 `GeometryBufferNodeOperator`，Compiler 与 Runner 共享。节点阶段为
`PROCESS`，成功消息为“Geometry Buffer 已准备”。

Sedona `ST_Buffer(geometry, distance, true)` 的 SPHEROID 实现依赖 GeoTools。Task Engine
固定引入与 Sedona 1.9.0 配套的 `org.datasyslab:geotools-wrapper:1.9.0-33.5`，并将其同时
打入 Local Runner 和 Cluster Runner；制品校验必须确认 GeoTools CRS API 已进入 Cluster
Runner，避免预检成功但真实运行出现类缺失。

安全摘要允许记录表名、字段名、距离、模式和 CRS。距离是用户配置，不是数据值；不得记录
实际 Geometry、坐标、缓冲面积、结果 WKT/WKB 或数据行。异常链使用统一日志脱敏。

## 8. 验收标准

- PLANAR、SPHEROID 配置严格 JSON 往返，并受 Canvas `1.22` 门槛约束。
- Point、LineString、Polygon、Multi 和集合输入均产生 MultiPolygon Schema。
- 正距离校验、WGS84 限制和角度单位 Warning 准确。
- NULL、Empty 和普通 Geometry 行数保持语义正确。
- 原字段保留，Buffer 字段追加，CRS/dimension/nullable 正确传播。
- BATCH、STREAMING、有界性、事件时间和 Watermark 正确传播。
- Inspector 保留失效值，Registry、图规则、摘要、生命周期和错误分类完整接入。
- 日志与错误不包含空间数据值。

## 9. 不在范围内

- 负距离、零距离修复、单侧 Buffer。
- cap style、join style、quadrant segments、mitre limit 等样式参数。
- 自动选择投影 CRS、自动单位换算或任意地理 CRS 椭球 Buffer。
- 合并相邻缓冲区、Dissolve、按字段分组聚合。
