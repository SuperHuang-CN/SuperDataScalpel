# Canvas Snap Tracks Processor 设计

## 1. 定位与 ArcGIS 对齐边界

`SNAP_TRACKS` 对齐 ArcGIS Enterprise 11.3 GeoAnalytics Server Snap Tracks 的核心产品语义：
将按轨迹和时间排序的 Point 观测联合匹配到 LineString 道路网络，匹配时同时
考虑点到线距离、前后观测距离与路网转移可达性，不使用“每个点独立选最近道路”代替路网匹配。

官方参考：

- [Enterprise REST Snap Tracks](https://developers.arcgis.com/rest/services-reference/enterprise/geoanalytics/tasks/snap-tracks/)
- [ArcGIS Pro Snap Tracks](https://pro.arcgis.com/en/pro-app/latest/tool-reference/big-data-analytics/snap-tracks.htm)

DataScalpel 用 Canvas 逻辑表代替 ArcGIS 图层 URL，用显式的 `fromNodeColumnName` / `toNodeColumnName`
表达线网连通关系。不复制 ArcGIS Data Store、服务发布、`context` 处理范围、`processSR`
或 `outSR`；CRS 变换由上游 `SPATIAL_TRANSFORM` 显式完成。

当前是可执行的首版子集，不声明与 ArcGIS 结果完全等价：

- 只支持 `BATCH` 和有界 XY Point + XY LineString。
- 连续观测只能在同一条线，或共享一个端点的直接相邻线之间转移。
- 首版不搜索“两个观测之间没有观测点的多条中间道路”，稀疏观测可能因此保守标记为未匹配。
- 不使用外部路网服务、交通成本或转向禁行表；通行方向只由线属性的四值映射表达。

## 2. Canvas 4.74 配置契约

```ts
interface SnapTracksDirectionMatching {
  directionColumnName: string;
  forwardValue: string;
  backwardValue: string;
  bothValue: string;
  noneValue: string;
}

interface SnapTracksLineField {
  sourceColumnName: string;
  outputColumnName: string;
}

interface SnapTracksConfiguration {
  pointTableName: string;
  pointGeometryColumnName: string;
  trackIdColumns: string[];
  timeColumnName: string;
  orderByColumns: string[];
  lineTableName: string;
  lineGeometryColumnName: string;
  lineIdColumnName: string;
  fromNodeColumnName: string;
  toNodeColumnName: string;
  searchDistance: number | null;
  searchDistanceUnit: SpatialDistanceUnit | null;
  distanceMethod: 'PLANAR' | 'GEODESIC' | null;
  boundaries: TrackBoundaryConfiguration;
  directionMatching: SnapTracksDirectionMatching | null;
  lineFields: SnapTracksLineField[];
  outputMode: 'ALL_FEATURES' | 'MATCHED_FEATURES' | null;
  outputTableName: string;
  snappedGeometryColumnName: string;
  matchedLineIdColumnName: string;
  matchStatusColumnName: string;
  originalXColumnName: string;
  originalYColumnName: string;
  matchXColumnName: string;
  matchYColumnName: string;
  matchDistanceColumnName: string;
}
```

- `trackIdColumns` 必须有 1～8 个非 Geometry 字段，共同标识一条轨迹。
- 轨迹先按 `timeColumnName`，再按 `orderByColumns` 排序；同一轨迹的完整排序键必须唯一。
- `lineIdColumnName` 运行时必须非空且唯一。`fromNodeColumnName` 与
  `toNodeColumnName` 必须非空、同类型；BINARY 不可作为线 ID、网络节点或方向字段。
- `directionMatching=null` 表示所有线均可双向通行。存在映射时，顺向表示
  From → To，逆向表示 To → From，双向允许两者，禁行两者均不允许；
  未命中四个显式值的实际属性按禁行处理。四个配置值必须互不相同。
- `boundaries` 复用轨迹节点的相邻时间 gap、相邻距离 gap 和固定时间周期；
  任一条件命中即开始新匹配片段。
- `lineFields` 可投影 0～32 个非 Geometry 道路属性，不能重复投影 Geometry、线 ID
  或 From/To 连通字段。来源字段和结果字段名按大小写不敏感规则唯一。
- 空字符串和未完成业务选择可作为草稿保存；非法对象或数组结构由三端严格解析拒绝。

## 3. 距离、候选与轨迹切分

- `PLANAR` 要求点线使用相同的、可换算线性单位的投影 CRS；空间候选和线上投影
  都在该 CRS 的二维平面中计算。
- `GEODESIC` 首版只允许 EPSG:4326 XY；候选召回复用 WGS84 保守空间候选，
  最终距离、线上最近位置和线长使用 WGS84 测地线计算。
- `searchDistance` 必须是可换算的有限正数。一个观测在搜索距离内最多允许 32 个道路候选；
  超过时稳定失败，不静默截断或仅保留最近 32 条。
- NULL/Empty Point 可作为未匹配观测保留；NULL 时间不参与分析。非空但非 Point、
  非法或非有限坐标稳定失败。线必须是非空、有效、至少两个点的 LineString。
- 单观测轨迹片段没有前后转移证据，固定标记为未匹配，不因恰好靠近某条线而自动吸附。

## 4. Viterbi 联合匹配与网络转移

每个轨迹片段在 Spark Executor 内按顺序执行 Viterbi 动态规划：

1. 每个观测的状态由全部空间候选线和一个“未匹配”状态组成。
2. 发射成本由观测到候选线距离相对搜索距离的归一化值决定。
3. 转移成本比较前后 Point 实际移动距离与道路网可达距离。
4. 同一条线使用候选在线上的比例位置计算距离；不同线只在 From/To 共享同一个节点时
   可直接转移，并按已配置方向检查退出前一条线和进入后一条线。
5. 不连通、方向不允许的转移不是高成本候选，而是不可达。允许通过“未匹配”状态中断，
   并在后续观测重新进入可达道路。
6. 以线 ID 字符串形式和内部行身份作为确定性并列顺序，不依赖数据分区或候选 Join 输出顺序。

这是简化的路网隐马尔可夫式联合选择，但当前转移图只包含直接相邻线。因此它比
独立最近道路更可靠，但还不是 ArcGIS 全部网络路径搜索的完整对照实现。

## 5. 输出与 Map 传播

结果保留 Point 输入的全部字段，按 `lineFields` 顺序追加匹配道路属性，再追加：

| 配置字段 | 类型 | 语义 |
| --- | --- | --- |
| `matchedLineIdColumnName` | 继承线 ID 类型 | 匹配道路 ID，未匹配时为 NULL |
| `matchStatusColumnName` | STRING | `M` 表示匹配，`U` 表示未匹配 |
| `snappedGeometryColumnName` | Point | 匹配时为道路上最近点；未匹配时保留原 Point |
| `originalXColumnName` / `originalYColumnName` | DOUBLE | 原 Point 坐标 |
| `matchXColumnName` / `matchYColumnName` | DOUBLE | 匹配点坐标，未匹配时为 NULL |
| `matchDistanceColumnName` | DOUBLE | 原 Point 到匹配点的距离，固定为米；未匹配时为 NULL |

- `ALL_FEATURES` 输出所有参与分析的非 NULL 时间观测；`MATCHED_FEATURES` 只输出 `M`。
- Point 表、Line 表和其他入口表均保持原 Map 顺序，新的有界结果表追加到 Map 末尾。
- 结果表继承 Point 表的事件时间字段名，但不传播 Watermark。
- Compiler Preview 只构造零行投影 Schema，不执行空间 Join、候选聚合或 Viterbi。

## 6. Inspector 与 Canvas UI

```text
┌ 吸附轨迹 ──────────────────────────────┐
│ 轨迹点                                             │
│ 点表 [vehicle_observations                  ] │
│ Point Geometry [shape]  观测时间 [observed_at] │
│ 轨迹标识 [vehicle_id]  同时间顺序 [sequence_no] │
│                                                    │
│ 道路网络                                           │
│ 线表 [road_network]                            │
│ LineString [shape]       唯一线 ID [road_id]       │
│ From Node [from_node]    To Node [to_node]       │
│ 方向匹配  [已启用]                            [设置] │
│ 输出道路属性  2 / 32                        [设置] │
│                                                    │
│ 匹配                                               │
│ 距离方法 [平面 | 测地线]                         │
│ 搜索距离 [50] [米]                              │
│ 轨迹切分  2 项                                [设置] │
│ 输出范围 [全部观测 | 仅匹配观测]                   │
│                                                    │
│ 结果表 [snapped_tracks]                        │
│ 匹配诊断字段 8 项                         [设置] │
└──────────────────────────────┘
```

- 表和字段候选只来自 Compiler `inputTables`；上游失效值保留并就地标红。
- 选择 Point 表时只为尚未填写的 Geometry、时间和输出表生成建议；选择线表时
  只为尚未填写的 Geometry、线 ID 及 From/To 节点生成建议，不覆盖用户配置。
- 方向值映射、道路属性投影、轨迹切分和 8 个诊断字段收入独立 Modal，主面板保持紧凑。
- 普通业务校验错误不阻止应用草稿；不安全 JSON 结构仍在定义边界拒绝。
- Canvas 卡片只显示 Point 表 → 结果表、线表、平面/测地线、单位、轨迹标识数、
  方向是否启用、道路属性数和输出范围。不显示搜索距离数值、方向映射值、坐标或数据内容。

## 7. 稳定错误与安全摘要

| 错误码 | 含义 |
| --- | --- |
| `SNAP_TRACKS_INPUT_TABLES_MUST_DIFFER` | Point 表与线网表相同 |
| `SNAP_TRACKS_LINE_GEOMETRY_REQUIRED` | 道路 Geometry 不是带完整元数据的 LineString |
| `SNAP_TRACKS_NODE_TYPE_MISMATCH` | From/To 节点类型不一致 |
| `SNAP_TRACKS_NETWORK_FIELD_TYPE_INVALID` | 线 ID、节点或方向使用不支持的 BINARY |
| `INVALID_SNAP_TRACKS_SEARCH_DISTANCE` | 搜索距离不是可换算的有限正数 |
| `SNAP_TRACKS_PROJECTED_CRS_REQUIRED` | 平面匹配未使用可换算线性单位的投影 CRS |
| `DUPLICATE_SNAP_TRACKS_DIRECTION_VALUE` | 四个方向映射值不唯一 |
| `SNAP_TRACKS_LINE_FIELD_COUNT_EXCEEDED` | 要投影的道路属性超过 32 项 |
| `SNAP_TRACKS_LINE_FIELD_INVALID` | 把 Geometry 或网络连接字段重复当作道路属性输出 |
| `SNAP_TRACKS_LINE_ID_INVALID` / `SNAP_TRACKS_LINE_ID_DUPLICATE` | 实际线 ID 为 NULL 或重复 |
| `SNAP_TRACKS_NETWORK_NODE_INVALID` | 实际 From/To 节点为 NULL |
| `SNAP_TRACKS_CANDIDATE_COUNT_EXCEEDED` | 单观测在搜索范围内的道路候选超过 32 |
| `SNAP_TRACKS_POINT_GEOMETRY_INVALID` / `SNAP_TRACKS_LINE_GEOMETRY_INVALID` | 实际几何类型、有效性或坐标非法 |
| `SNAP_TRACKS_MATCH_GEOMETRY_INVALID` | 实际投影位置、距离、比例或线长无法安全生成 |
| `SNAP_TRACKS_MATCH_STATE_INVALID` | Viterbi 状态链内部不一致 |

表、字段、轨迹排序、边界、CRS、单位和结果名冲突继续复用公共稳定问题。Runner
安全摘要只记录逻辑 Point/线/结果表名、轨迹标识数、距离方法、方向是否启用、
道路属性数和输出范围；不记录搜索距离数值、方向映射值、坐标或数据行。节点归属
`PROCESS` 阶段。

## 8. 验证证据与仍待完成范围

已有本地专项覆盖：4.74 Contracts/Parser 门槛与 Registry 完整性；相邻道路联合匹配；
“更近但不连通”候选不能击败连通路径；单向路逆行；gap 分段；两种输出范围；
单观测轨迹；单观测 32 候选上限；日期线测地匹配；道路属性投影。4.76 收口还验证了：

- Compiler Preview 使用零行集合依赖计划，不执行空间 Join、候选聚合或 Viterbi；Point 原字段保持
  直接来源，道路属性、匹配线 ID、状态、吸附 Geometry、原始/匹配坐标和距离均追溯到真实
  Point/道路字段，输出达到 `FIELD_COMPLETE`，不存在未知来源字段。
- 1,000 条彼此独立的轨迹、20,000 个观测和 1,000 条道路全部匹配；候选和观测不会收集到
  Driver。Viterbi 仍在 Executor 的逐轨迹分组内使用 `collect_list`，超长单轨迹是明确容量边界。
- Engine `SnapTracksMapMatcherTest` 与 `SnapTracksNodeOperatorSparkTest` 共 12 项通过。
- 真实页面已验证紧凑 Inspector、方向匹配及四值配置、轨迹切分、8 个结果字段、无效草稿应用和
  紧凑问题详情；验收定义未保存。

仍待与真实 ArcGIS Enterprise 11.3 对照：多条中间道路路径搜索、曲线候选评分与并列、
官方方向字段容差、复杂路口、搜索距离边界、时间/距离/固定周期切分的服务结果、
官方输出字段类型、高密度候选、复杂路网和超长单轨迹生产容量。因此路线图中的本地实现项可以
标记完成，但不把“节点可用”写成“已与 ArcGIS 完全对齐”。
