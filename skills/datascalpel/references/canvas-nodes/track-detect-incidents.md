# TRACK_DETECT_INCIDENTS · 轨迹事件检测

[节点索引](index.md) · [Canvas 共用规则](../task-canvas.md)

## 用途与选择

有界批处理轨迹的事件检测配置；先按轨迹标识、时间和边界形成有序分段，再按开始/结束条件识别事件。输出保留来源字段并追加事件 ID、活动标记、起止时间、持续时间，以及生命周期模式下的状态字段。

## 模式与连线

- 模式：BATCH；类别：PROCESSOR。
- 至少一条入边，允许零条或多条出边；从上游 Map 按逻辑表名取表，不按边序号取表。
- 输入必须为 BOUNDED。

## 关键配置与行为

- CONDITION_LIFECYCLE 用开始/结束条件维护事件生命周期；LEGACY 为兼容语义。条件使用结构化 CanvasFilterCondition。
- ALL_EVENTS 保留所有事件输入行并标记，INCIDENTS_ONLY 仅事件相关行；状态字段用于生命周期模式。无结束条件的含义按当前契约明确，不能猜测自动超时结束。
- 时间/分段边界限制事件延续，orderByColumns 处理同时间观测；可选 conditionWindows 只使用受控窗口绑定。无空间边界时几何字段可按契约省略。

配置定位（只列关键语义，完整字段读取实时契约）：

- `distanceMethod`：空间分段的距离计算方式；配置最大空间间隔时必填，PLANAR 使用来源 CRS 坐标，GEODESIC 使用 EPSG:4326 XY 测地距离。
- `boundaries`：必填的轨迹分段边界；时间间隔、空间间隔和固定时间窗口分别生效，均未配置时仍按轨迹标识形成一个连续分段。
- `startCondition`：必填的事件开始条件；LEGACY 在条件由不成立变为成立时触发新事件，CONDITION_LIFECYCLE 在每个结束区段中取首个满足条件且不同时满足结束条件的记录作为 Started。可引用来源字段及 conditionWindows 产生的绑定字段。
- `endCondition`：在每条轨迹的有序记录上判定事件结束的可选过滤条件；LEGACY 语义下为空表示首次开始后持续到分段末尾，CONDITION_LIFECYCLE 语义下为空表示 startCondition 首次不成立时结束。

## 逻辑表 Map 与字段

保留输入 Map，追加命名结果表；outputTableName 必须与输入及其他结果表不同。 保留原字段并追加事件 ID、标记、起止、时长与状态；不是按事件自动聚合成单行。

## 最小配置示例

前提：上游 observations 为 BOUNDED，含 vehicle_id:STRING、observed_at:TIMESTAMP、seq:LONG 和 geom:GEOMETRY(POINT, EPSG:3857, XY)；每条轨迹的 observed_at+seq 排序键有明确唯一性依据。 另含 speed:DOUBLE，单位和阈值 80 的业务含义已确认。

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
  "startCondition": {
    "kind": "PREDICATE",
    "columnName": "speed",
    "operator": "GREATER_THAN",
    "values": [
      {
        "dataType": "DOUBLE",
        "value": "80"
      }
    ]
  },
  "endCondition": {
    "kind": "PREDICATE",
    "columnName": "speed",
    "operator": "LESS_THAN_OR_EQUALS",
    "values": [
      {
        "dataType": "DOUBLE",
        "value": "80"
      }
    ]
  },
  "resultMode": "ALL_EVENTS",
  "outputTableName": "incidents",
  "incidentIdColumnName": "incident_id",
  "incidentFlagColumnName": "is_incident",
  "incidentStartTimeColumnName": "incident_start",
  "incidentEndTimeColumnName": "incident_end",
  "incidentDurationColumnName": "duration_s",
  "incidentDurationUnit": "SECONDS",
  "incidentSemantics": "CONDITION_LIFECYCLE",
  "incidentStatusColumnName": "incident_status",
  "orderByColumns": [
    "seq"
  ],
  "conditionWindows": []
}
```

## 预校验检查与修正

将节点放入完整画布，按 Canvas 指南调用 Engine 预校验，核对输入/输出表及字段；此处没有真实数据测试步骤。

| 诊断或检查点 | 处理方式 |
| --- | --- |
| `REQUIRED_CONFIGURATION` | 补齐活动模式的条件与状态字段。 |
| `DUPLICATE_COLUMN_NAME` | 事件输出名不能与原观测字段冲突。 |

ERROR 修正后重新预校验最终定义；WARNING 记录影响，不把结构通过表述为数据值或输出结果通过。
