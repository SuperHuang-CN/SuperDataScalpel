# TRACK_MOTION_STATISTICS · 轨迹运动统计

[节点索引](index.md) · [Canvas 共用规则](../task-canvas.md)

## 用途与选择

仅支持有界批处理的轨迹运动统计配置；按轨迹标识、分段边界和时间顺序为 Point 观测追加运动字段。LEGACY_LAG 按固定历史偏移计算旧指标，OBSERVATION_WINDOW 同时提供相邻观测值和包含当前观测的滚动窗口统计。

## 模式与连线

- 模式：BATCH；类别：PROCESSOR。
- 至少一条入边，允许零条或多条出边；从上游 Map 按逻辑表名取表，不按边序号取表。
- 输入必须为 BOUNDED。

## 关键配置与行为

- OBSERVATION_WINDOW 的 observationCount 包含当前观测，最多 N−1 完整段、N−2 加速度；历史不足使用已有观测。
- statistics 选择某指标组时必须包含全组固定成员，不能只取 SPEED 组中的 AVG_SPEED；示例 BEARING 是单成员组。LEGACY_LAG 才使用 metrics 和 historyPoints 的旧偏移指标。
- 高程可取 Z 或明确 elevationColumnName，需显式输入单位；静止分类同时检查严格距离/时间阈值，不能将速度为零简单等同驻留。

配置定位（只列关键语义，完整字段读取实时契约）：

- `boundaries`：相邻时间/距离 gap 与固定周期边界；任一条件成立就重置历史窗口，未配置的条件不参与。
- `motionSemantics`：运动统计语义；null 为 LEGACY_LAG，OBSERVATION_WINDOW 使用 windowOptions 和完整排序键。切换模式不会删除另一套配置。
- `windowOptions`：OBSERVATION_WINDOW 模式的窗口、统计项和单位；其他模式不执行该对象。

## 逻辑表 Map 与字段

保留输入 Map，追加命名结果表；outputTableName 必须与输入及其他结果表不同。 保留每条来源观测及字段，追加运动指标，结果有界；段首/不足历史的指标可为 NULL。

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
  "historyPoints": 1,
  "metrics": [],
  "outputTableName": "motion",
  "motionSemantics": "OBSERVATION_WINDOW",
  "windowOptions": {
    "observationCount": 3,
    "orderByColumns": [
      "seq"
    ],
    "statistics": [
      {
        "statisticId": "11111111-1111-4111-8111-111111111111",
        "kind": "BEARING",
        "outputColumnName": "bearing"
      }
    ],
    "distanceUnit": "METERS",
    "durationUnit": "SECONDS",
    "speedUnit": "METERS_PER_SECOND",
    "accelerationUnit": "METERS_PER_SECOND_SQUARED"
  }
}
```

## 预校验检查与修正

将节点放入完整画布，按 Canvas 指南调用 Engine 预校验，核对输入/输出表及字段；此处没有真实数据测试步骤。

| 诊断或检查点 | 处理方式 |
| --- | --- |
| `INVALID_TRACK_HISTORY_WINDOW` | 配置包含当前观测的 1–100 个位置。 |
| `TRACK_MOTION_GROUP_INCOMPLETE` | 补齐选中指标组的全部 kind。 |

ERROR 修正后重新预校验最终定义；WARNING 记录影响，不把结构通过表述为数据值或输出结果通过。
