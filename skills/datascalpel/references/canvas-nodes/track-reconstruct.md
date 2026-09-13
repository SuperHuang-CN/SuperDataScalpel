# TRACK_RECONSTRUCT · 轨迹重建

[节点索引](index.md) · [Canvas 共用规则](../task-canvas.md)

## 用途与选择

有界批处理轨迹重建配置；过滤时间或 Geometry 为空的观测后，按轨迹标识和完整排序键把观测切分为片段，并为每个片段生成一行路径或活动区域及摘要。该节点不保留事件时间或 Watermark，组内排序仍受 collect_list 容量约束。

## 模式与连线

- 模式：BATCH；类别：PROCESSOR。
- 至少一条入边，允许零条或多条出边；从上游 Map 按逻辑表名取表，不按边序号取表。
- 输入必须为 BOUNDED。

## 关键配置与行为

- 显式 ORDERED_SEGMENTS，时间后按 orderByColumns 排序；缺少 reconstruction 整体会走 LEGACY_POINTS，不能当作相同默认行为。
- 时间/距离 gap、固定周期、启用的分段表达式按 OR；gap 严格大于阈值才切分。GAP 不共享端点，FINISH_LAST/START_NEXT 改变端点归属，固定周期不共享。
- pathGeometry 控制新路径，areaGeometry 启用时改为活动区域并覆盖线配置；未配置 pathGeometry 保留 LineString 形态。需说明大轨迹 collect_list 容量边界。

配置定位（只列关键语义，完整字段读取实时契约）：

- `distanceMethod`：距离及几何构造方法。PLANAR 使用来源 CRS 坐标和可换算单位；GEODESIC 仅接受 EPSG:4326 XY，并使用 WGS84 椭球。
- `boundaries`：时间 gap、空间距离 gap 和固定周期边界；与启用的表达式条件按 OR 切分，等于 gap 阈值不切分，未配置的条件不参与。
- `summaryStatistics`：对每个最终轨迹片段计算的 0～32 个摘要统计项；共享到相邻片段的端点会分别参与两段统计。
- `outputGeometryColumnName`：结果 Geometry 字段名。旧路径输出 LineString；METHOD_PATH 输出 MultiLineString；面轨迹输出 MultiPolygon。

## 逻辑表 Map 与字段

保留输入 Map，追加命名结果表；outputTableName 必须与输入及其他结果表不同。 每轨迹片段一行：轨迹键、起止时间、计数、摘要与路径/区域；不保留全部观测行，清除事件时间/Watermark。

## 最小配置示例

前提：上游 observations 为 BOUNDED，含 vehicle_id:STRING、observed_at:TIMESTAMP、seq:LONG 和 geom:GEOMETRY(POINT, EPSG:3857, XY)；每条轨迹的 observed_at+seq 排序键有明确唯一性依据。

以下仅为节点 `configuration`；资源 UUID、字段与单位应换成已确认的真实元数据。稳定的操作/写入 ID 由当前任务生成。

```json
{
  "sourceTableName": "observations",
  "pointGeometryColumnName": "geom",
  "trackIdColumns": [
    "vehicle_id"
  ],
  "timeColumnName": "observed_at",
  "distanceMethod": "PLANAR",
  "boundaries": {
    "maximumTimeGap": 30,
    "maximumTimeGapUnit": "MINUTES"
  },
  "summaryStatistics": [],
  "outputTableName": "tracks",
  "outputGeometryColumnName": "track_geom",
  "startTimeColumnName": "started_at",
  "endTimeColumnName": "ended_at",
  "pointCountColumnName": "point_count",
  "reconstruction": {
    "semantics": "ORDERED_SEGMENTS",
    "orderByColumns": [
      "seq"
    ],
    "splitBoundaryOption": "GAP"
  }
}
```

## 预校验检查与修正

将节点放入完整画布，按 Canvas 指南调用 Engine 预校验，核对输入/输出表及字段；此处没有真实数据测试步骤。

| 诊断或检查点 | 处理方式 |
| --- | --- |
| `TRACK_ORDER_TIE_NOT_VERIFIED` | 记录完整排序键依据，未证实时保留警告。 |
| `INVALID_TRACK_GEODESIC_SEGMENT_LENGTH` | 按测地路径选项补齐合法段长和单位。 |

ERROR 修正后重新预校验最终定义；WARNING 记录影响，不把结构通过表述为数据值或输出结果通过。
