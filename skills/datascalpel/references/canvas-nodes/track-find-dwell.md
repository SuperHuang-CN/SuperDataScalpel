# TRACK_FIND_DWELL · 轨迹驻留识别

[节点索引](index.md) · [Canvas 共用规则](../task-canvas.md)

## 用途与选择

仅支持有界批处理的 Point 轨迹驻留分析。LEGACY_ADJACENT 把相邻距离未超阈值的连续观测聚为候选；REFERENCE_CENTER 以候选首点和冻结种子中心确定不重用的驻留范围，并支持驻留级或点级四种结果。单个超大轨迹片段仍有 Executor 内存和反复扫描风险。

## 模式与连线

- 模式：BATCH；类别：PROCESSOR。
- 至少一条入边，允许零条或多条出边；从上游 Map 按逻辑表名取表，不按边序号取表。
- 输入必须为 BOUNDED。

## 关键配置与行为

- 显式 REFERENCE_CENTER，以首点和冻结种子中心识别不重用的连续驻留；LEGACY_ADJACENT 的相邻距离链不是相同算法。
- MEAN_CENTERS/CONVEX_HULLS 每驻留一行；DWELL_FEATURES 仅成员原行，ALL_FEATURES 含非成员标记。按结果模式设置聚合字段或 dwellFlagColumnName。
- minimumDuration 输入单位与输出 durationUnit 分开；meanDistance 是成员之间 N−1 段平均距离，不是到中心平均半径。大轨迹仍有内存与扫描成本。

配置定位（只列关键语义，完整字段读取实时契约）：

- `distanceMethod`：距离计算方式，决定采用平面距离或测地线距离。
- `distanceThreshold`：有限正数距离容差。LEGACY_ADJACENT 比较相邻观测；REFERENCE_CENTER 先比较候选首点，再用冻结的种子均值中心向前、向后扩展；距离等于阈值仍纳入。
- `distanceThresholdUnit`：距离阈值的单位。
- `minimumDuration`：判定为驻留事件所需的最短持续时长，必须是有限正数；单位由 minimumDurationUnit 指定。

## 逻辑表 Map 与字段

保留输入 Map，追加命名结果表；outputTableName 必须与输入及其他结果表不同。 本例输出轨迹键、驻留 ID、时间范围、时长、成员数、平均距离及中心 Geometry；不继承流状态。

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
  "distanceThreshold": 100.0,
  "distanceThresholdUnit": "METERS",
  "minimumDuration": 20.0,
  "minimumDurationUnit": "MINUTES",
  "summaryStatistics": [],
  "outputGeometryKind": "CENTROID",
  "outputTableName": "dwells",
  "dwellIdColumnName": "dwell_id",
  "startTimeColumnName": "started_at",
  "endTimeColumnName": "ended_at",
  "durationColumnName": "duration_s",
  "pointCountColumnName": "point_count",
  "outputGeometryColumnName": "dwell_geom",
  "dwellSemantics": "REFERENCE_CENTER",
  "rangeOptions": {
    "resultMode": "MEAN_CENTERS",
    "orderByColumns": [
      "seq"
    ],
    "durationUnit": "SECONDS",
    "meanDistanceColumnName": "mean_distance",
    "meanDistanceUnit": "METERS"
  }
}
```

## 预校验检查与修正

将节点放入完整画布，按 Canvas 指南调用 Engine 预校验，核对输入/输出表及字段；此处没有真实数据测试步骤。

| 诊断或检查点 | 处理方式 |
| --- | --- |
| `INVALID_DWELL_DISTANCE_THRESHOLD` | 核对有限正距离及 CRS 单位。 |
| `INVALID_DWELL_DURATION` | 明确合法最短驻留时间和单位。 |

ERROR 修正后重新预校验最终定义；WARNING 记录影响，不把结构通过表述为数据值或输出结果通过。
