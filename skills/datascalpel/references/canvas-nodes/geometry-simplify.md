# GEOMETRY_SIMPLIFY · 几何简化

[节点索引](index.md) · [Canvas 共用规则](../task-canvas.md)

## 用途与选择

逐行 Geometry 简化配置，支持批处理和流处理。保留来源字段并追加一个通用 GEOMETRY 结果字段；容差在来源 CRS 的二维坐标空间中执行，不自动投影、修复或按地图比例尺换算。NULL 输入返回 NULL，输出表继承来源有界性、事件时间和 Watermark。

## 模式与连线

- 模式：BATCH / STREAMING；类别：PROCESSOR。
- 至少一条入边，允许零条或多条出边；从上游 Map 按逻辑表名取表，不按边序号取表。
- 输入有界性及批流差异见下文。

## 关键配置与行为

- DOUGLAS_PEUCKER 与 TOPOLOGY_PRESERVING 语义不同，按是否需保持拓扑选择；容差为有限正数。
- 在来源二维 CRS 空间执行，不自动投影、修复或根据地图比例尺换算；角度单位不能当作米。

配置定位（只列关键语义，完整字段读取实时契约）：

- `algorithm`：必填简化算法。DOUGLAS_PEUCKER 按容差减少顶点，显式策略下不自动修复无效结果；TOPOLOGY_PRESERVING 保持单个要素的拓扑约束，但不保证不同行之间的共边一致。
- `tolerance`：必填容差，必须是有限正数；先按 toleranceUnit 换算到来源 CRS 轴单位，再作为二维简化距离。它不是输出精度、地图比例尺或测地距离。
- `toleranceUnit`：tolerance 的单位。投影 CRS 可使用 SOURCE_CRS_UNIT 或受支持线性单位并换算到坐标轴单位；地理 CRS 只允许 SOURCE_CRS_UNIT，其值为来源角度单位并产生警告，不自动换算为米。
- `geometryPolicy`：结果维度和有效性策略。PRESERVE_DIMENSION 校验输入并保留可支持维度，Douglas-Peucker 不支持保留 M；OUTPUT_XY 校验输入并只将新结果降为 XY；LEGACY 或 null 使用旧 Sedona 兼容路径。

## 逻辑表 Map 与字段

保留输入 Map，追加命名结果表；outputTableName 必须与输入及其他结果表不同。 保留原列并追加通用 GEOMETRY，NULL 输入返回 NULL；维度由策略决定。流模式继承来源有界性；事件时间与 Watermark 的实际传播以预校验 outputTables 为准。

## 最小配置示例

前提：上游逻辑表 features 含 id:LONG 和 geom:GEOMETRY(POLYGON, EPSG:3857, XY)。

以下仅为节点 `configuration`；资源 UUID、字段与单位应换成已确认的真实元数据。稳定的操作/写入 ID 由当前任务生成。

```json
{
  "sourceTableName": "features",
  "geometryColumnName": "geom",
  "outputTableName": "simplified_features",
  "outputColumnName": "simple_geom",
  "algorithm": "TOPOLOGY_PRESERVING",
  "tolerance": 1.0,
  "toleranceUnit": "SOURCE_CRS_UNIT",
  "geometryPolicy": "PRESERVE_DIMENSION"
}
```

## 预校验检查与修正

将节点放入完整画布，按 Canvas 指南调用 Engine 预校验，核对输入/输出表及字段；此处没有真实数据测试步骤。

| 诊断或检查点 | 处理方式 |
| --- | --- |
| `INVALID_GEOMETRY_SIMPLIFY_TOLERANCE` | 提供符合 CRS 单位的有限正容差。 |
| `SPATIAL_DISTANCE_UNIT_UNSUPPORTED` | 更正单位或先设计支持的投影转换。 |

ERROR 修正后重新预校验最终定义；WARNING 记录影响，不把结构通过表述为数据值或输出结果通过。
