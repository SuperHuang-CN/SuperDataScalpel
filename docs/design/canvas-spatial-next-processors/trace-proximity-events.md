# Canvas Trace Proximity Events Processor 设计

## 1. 定位与 ArcGIS 对齐边界

`TRACE_PROXIMITY_EVENTS` 对齐 ArcGIS Enterprise 11.3 GeoAnalytics Server
Trace Proximity Events 的核心语义：从一组起始实体出发，只有两条 Point 观测同时满足
空间距离、时间距离和可选同值属性时才形成接触，然后按首次接触时间向下游逐层传播。

官方参考：

- [Enterprise REST Trace Proximity Events](https://developers.arcgis.com/rest/services-reference/enterprise/geoanalytics/tasks/trace-proximity-events/)
- [ArcGIS Pro Trace Proximity Events](https://pro.arcgis.com/en/pro-app/latest/tool-reference/big-data-analytics/trace-proximity-events.htm)

DataScalpel 使用 Canvas 逻辑表代替服务 URL，并把起始实体图层表达为第二张上游表。
不复制 ArcGIS 的 Data Store、服务发布、处理范围、`processSR` 或 `outSR` 参数；CRS 转换由上游
Spatial Transform 显式完成。

## 2. 配置契约

Canvas 4.73 新增：

```ts
type TraceProximityInterestSource = 'ENTITY_IDS' | 'TABLE';

interface TraceProximityEntityOfInterest {
  entityId: string;
  startEpochMillis: number | null;
}

interface TraceProximityEventsConfiguration {
  sourceTableName: string;
  pointGeometryColumnName: string;
  entityIdColumnName: string;
  timeColumnName: string;
  distanceMethod: 'PLANAR' | 'GEODESIC' | null;
  spatialSearchDistance: number | null;
  spatialSearchDistanceUnit: SpatialDistanceUnit | null;
  temporalSearchDistance: number | null;
  temporalSearchDistanceUnit:
    | 'MILLISECONDS' | 'SECONDS' | 'MINUTES' | 'HOURS'
    | 'DAYS' | 'WEEKS' | 'MONTHS' | 'YEARS' | null;
  interestSource: TraceProximityInterestSource | null;
  entitiesOfInterest: TraceProximityEntityOfInterest[];
  entitiesOfInterestTableName: string;
  interestEntityIdColumnName: string;
  interestStartTimeColumnName: string | null;
  maxTraceDepth: number | null;
  attributeMatchColumns: string[];
  includeTracks: boolean;
  outputTableName: string;
  tracksOutputTableName: string;
  fromEntityIdColumnName: string;
  toEntityIdColumnName: string;
  depthColumnName: string;
  durationMinutesColumnName: string;
  eventTimeColumnName: string;
}
```

- `ENTITY_IDS` 模式要求 1～256 个大小写敏感的 STRING ID；每项可以指定 Unix Epoch 毫秒开始时间，
  `null` 按 1970-01-01T00:00:00Z 处理。
- `TABLE` 模式从另一张有界上游表读取 STRING 实体 ID 和可选 TIMESTAMP 开始时间。
  同一 ID 有多行时取最早开始时间；NULL 开始时间按 Unix Epoch 处理。
- `maxTraceDepth` 必填，范围 1～32；起始实体深度为 0，其首层下游实体深度为 1。
- `attributeMatchColumns` 最多 8 个；两条观测的所有已选字段均使用普通等号匹配，NULL 不形成匹配。
- 未启用轨迹输出时仍保留 `tracksOutputTableName` 草稿，但不校验其业务完整性，也不生成该表。
- 空字符串和缺失业务选择可以作为草稿保存；非法 JSON 结构由严格 Parser 拒绝。

## 3. 接触与传播语义

- 节点仅支持 `BATCH`；观测表和可选起始表均必须为 `BOUNDED`。
- 观测 Geometry 必须是带完整 CRS 的 XY Point，实体 ID 必须是 STRING，观测时间必须是 TIMESTAMP。
- `PLANAR` 使用投影 CRS 中的二维距离，不允许以地理 CRS 角度近似。`GEODESIC` 仅支持
  EPSG:4326 XY，不允许 `SOURCE_CRS_UNIT`，使用已有 WGS84 真实 Geometry 最近位置能力。
- 时间搜索距离是非负整数。毫秒至周按固定时长，月/年按 Spark 会话时区的日历区间计算。
- 不同实体的观测同时满足空间、时间和全部同值属性时形成双向接触。
- 同一实体对的连续接触观测按时间搜索距离聚成 episode；`duration_minutes` 是 episode 的
  首末接触时间差，单次接触为 0。
- 传播只能使用不早于上游实体到达时间的接触。同一下游实体有多个候选时，依次按事件时间、
  上游实体 ID 和内部观测身份稳定选择首次事件。已到达实体不再被后续事件覆盖。
- NULL/Empty Geometry、NULL 时间或 NULL 实体 ID 观测不参与接触、事件或轨迹输出。非空但不合法的
  Point 或非有限坐标稳定失败，不静默跳过。实体 ID 区分大小写。

## 4. 输出和执行计划

首次接触事件表中，每个下游实体最多一行。该行保留下游实体首次接触观测的全部原字段，
再追加：

| 字段 | 类型 | 语义 |
| --- | --- | --- |
| `fromEntityIdColumnName` | STRING | 上游实体 ID |
| `toEntityIdColumnName` | STRING | 下游实体 ID |
| `depthColumnName` | LONG | 下游实体与起始实体的分隔度数 |
| `durationMinutesColumnName` | DOUBLE | 持续接触 episode 时长，单位分钟 |
| `eventTimeColumnName` | TIMESTAMP | 首次满足条件的接触时间 |

起始实体是深度 0 的传播根，不是下游“首次接触事件”，因此不会伪造一行 `from_id/to_id`
事件。启用轨迹表时，起始实体从配置的开始时间输出并标记深度 0；下游实体从其首次接触观测
及之后输出，每行追加同一 `depthColumnName`。

观测表、起始表和其他入口表继续保留；事件表和可选轨迹表按配置顺序追加到 Map 末尾。两张结果均为
`BOUNDED`，不传播事件时间或 Watermark。

Compiler Preview 使用零行集合依赖计划：5 个事件结果字段和轨迹深度追溯实际参与的 Point、
实体 ID、时间、同值属性和可选起始表 ID/时间；原观测字段保持直接血缘。两张结果均为
`FIELD_COMPLETE`，不包含未知来源字段，分析阶段不执行自连接、起始实体查找、BFS 或 Checkpoint。
Runner 中每层 BFS 通过分布式 RDD Union 和 Dataset Checkpoint 截断计划，不向 Driver 收集轨迹或接触行。
集群执行必须配置全部执行器可访问的 `spark.checkpoint.dir`；本地模式使用应用隔离的临时目录。

## 5. Inspector 与 Canvas UI

```text
┌ 追踪邻近事件 ────────────────────────────────┐
│ 轨迹观测表 [device_events             ] │
│ Point Geometry [shape]  实体 ID [device_id] │
│ 观测时间 [observed_at]                       │
│                                                    │
│ 距离方法 [平面 | 测地线]                     │
│ 空间 [15] [米]       时间 [5] [分钟]          │
│ 同值属性 [building, floor]                  │
│                                                    │
│ 起始定义 [直接填写 ID | 从上游表读取]     │
│ 起始实体 2 / 256                           + │
│ 1 [ID] [开始时间，可空]                    ↑↓× │
│ 2 [ID] [开始时间，可空]                    ↑↓× │
│ 最大传播深度 [3]                              │
│                                                    │
│ 首次接触事件表 [trace_events]             │
│ 输出后续轨迹 ●   [trace_tracks]            │
│ 结果字段 5 个                              [设置] │
└────────────────────────────────────┘
```

- 表和字段候选只来自 Compiler `inputTables`；失效值保留并标红，Compiler 不可用时不自行推测 Schema。
- 选择观测表时只在字段或输出名为空时生成建议，不覆盖用户已有配置。
- 显式 ID 始终完整列出，可添加、排序和删除；不设新的人为展示上限。普通业务错误不阻止应用草稿。
- 结果字段收入 680px 设置 Modal，主面板只保留结果表和轨迹开关。
- Canvas 卡片只显示输入到事件表、平面/测地判定、距离单位、起始实体数量或起始表、最大深度、
  同值字段数和是否输出轨迹；不显示任何实体 ID 值。

## 6. 稳定问题与安全摘要

| 错误码 | 含义 |
| --- | --- |
| `TRACE_PROXIMITY_POINT_GEOMETRY_REQUIRED` | 观测 Geometry 不是带完整 CRS 的 XY Point |
| `TRACE_PROXIMITY_ENTITY_ID_STRING_REQUIRED` | 观测或起始 ID 不是 STRING |
| `INVALID_TRACE_PROXIMITY_SPATIAL_DISTANCE` | 空间搜索距离不是有限正数 |
| `INVALID_TRACE_PROXIMITY_TEMPORAL_DISTANCE` | 时间距离非法或超出可执行范围 |
| `TRACE_PROXIMITY_PROJECTED_CRS_REQUIRED` | 平面追踪未使用投影 CRS |
| `INVALID_TRACE_PROXIMITY_MAX_DEPTH` | 传播深度不在 1～32 |
| `TRACE_PROXIMITY_INTEREST_REQUIRED` | 显式 ID 模式没有起始实体 |
| `TRACE_PROXIMITY_INTEREST_COUNT_EXCEEDED` | 起始实体超过 256 |
| `DUPLICATE_TRACE_PROXIMITY_INTEREST_ID` | 显式起始实体 ID 重复 |
| `TRACE_PROXIMITY_ATTRIBUTE_COUNT_EXCEEDED` | 同值字段超过 8 |
| `DUPLICATE_TRACE_PROXIMITY_ATTRIBUTE_COLUMN` | 同值字段重复 |
| `TRACE_PROXIMITY_CHECKPOINT_NOT_CONFIGURED` | 集群执行未配置可共享 Checkpoint 目录 |
| `TRACE_PROXIMITY_GEOMETRY_INVALID` | 实际 Point 类型、有效性、有限坐标或 WGS84 范围非法 |

表、字段、TIMESTAMP、单位、WGS84 和结果名冲突继续复用公共稳定问题。Runner 安全摘要只记录逻辑表、
字段名、距离方法、起始来源、起始数量、同值字段数、最大深度、轨迹开关和输出表；不记录实体 ID 值、
距离值、坐标或数据行。节点归属 PROCESS 阶段。

## 7. 对齐与验收边界

2026-09-13 已完成当前实现收口：

- Spark 专项 4 项通过，覆盖 A→B→C 分层传播、到达时间之前不传播、同值属性 AND、首次事件、
  显式 ID/起始表两种入口、起始表最早时间、起始/下游轨迹深度、NULL 排除和 Map 顺序。
- 事件表和轨迹表 Preview 全部达到 `FIELD_COMPLETE`，集合字段只关联真实观测/起始表来源，
  分析阶段零 Spark Job。
- 20,000 条 Point 观测与 10,000 个上游表起始实体实际生成 10,000 条首层接触事件；
  实体、接触、前沿和输出均保持分布式，不把轨迹或接触行收集到 Driver。
- 真实页面已验证显式 ID 紧凑列表、上游表起始来源、轨迹开关、5 个结果字段 Modal、
  无效草稿应用和紧凑问题详情；验收过程未保存任务定义。

仍待真实 ArcGIS Enterprise 11.3 对照：月末/年末及会话时区边界、持续接触 episode 分割容差、
搜索距离边界包含规则、极区/日期线、多上游候选并列、官方服务输出字段类型以及高偏斜大规模轨迹性能。
当前强制最大深度 1～32，事件结果保留下游首次接触观测全部原字段，平面模式不允许地理 CRS 角度近似。
这些是明确的平台裁决，不宣称与 Esri 服务完全等价。
