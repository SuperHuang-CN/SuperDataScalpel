# Canvas `TRACK_MOTION_STATISTICS` Processor 设计

## 1. 定位与审计结论

引入于 Canvas 4.15，仅 BATCH，Point 轨迹输入、逐点追加指标。旧实现使用 lag(historyPoints) 计算两端距离/时长，与 Calculate Motion Statistics 的历史窗口不同；4.23 增加独立观测窗口策略。

第 2 节保留 Canvas 4.20 审计快照。4.21 已新增可选 `boundaries.fixedTimeBoundary`：
正整数周期、日历/时长单位、参考时间和 IANA 时区；与原有 gap 独立生效，未启用时旧定义行为不变。
完整字段及默认值见[Canvas Definition](../canvas-task-definition.md)。Inspector 的边界 Modal 已提供配置入口。
4.23 的八组指标、窗口与 Idle 双阈值实现见第 7 节；不代表已经完成全部官方单位、缺失值及服务结果对照，也不能用于旧小版本 JSON。

### 4.36 公共单位补充

相邻距离边界、Idle 距离阈值、Distance/ElevationChange 指标及窗口距离/输入高程/输出高程使用公共距离单位。速度与加速度保持原独立枚举和换算。
单位名称明确国际制/美国测量制，切换不自动换算数值，旧单位结果不变。具体枚举、字段和证据见[公共空间单位](../canvas-spatial-units.md)；不代表整节点已完成官方对照。

### 4.47 固定时长单位与缺失值回归

窗口时长/累计 Idle 时长、Idle 时间阈值、相邻时间 gap 及旧 DURATION 输出可选择“周（固定 7 天）”。
一周为 604800 秒，不是按时区推进的日历周；切换仅改单位，不换算当前数值。
非活动窗口/旧指标中的固定周同样要求 4.47，不能借切换策略绕过版本门槛。
枚举与字段见[共享时长规则](../canvas-spatial-units.md#447-固定时长周与日历周)。固定月年仍待明确依据，不假定 30/365 天。

新增缺失值与窗口边界专项核对第 7 节：NULL/Empty Geometry 不连接更早有效点、均速分母只计可计算速度的段、
非有限高程仅在测量中视为 NULL、原字段不变；窗口 2 保留瞬时加速度但无窗口加速度统计。
NULL 时间排除，Shuffle 后轨迹间互不影响，预检/Analyzer 零 Job。具体测试证据见开发清单，不等同于官方数值对照。

### 当前阅读入口与对齐边界（4.47 汇总）

- **已接入范围**：4.23 的观测历史窗口、八组指标与 Idle 双阈值见第 7 节。
- **官方参照与差异**：参照 GA Calculate Motion Statistics；旧 lag 偏移不是历史窗口，单位、缺失值和官方数值仍需逐指标核对。
- **面板修订要求**：区分瞬时与窗口指标；窗口标注包含当前观测，输入高程单位与输出单位分别设置。

本摘要不替代逐版本契约。下节的“当前”均指 4.20 历史状态；目标线框不作为已实现截图。
参数覆盖、本地验证和官方结果对照分别登记，见[对齐验收规则](../canvas-spatial-analysis-processor-roadmap.md#7-对齐验收)。

## 2. 4.20 配置快照（历史）

```ts
interface TrackMotionMetricBase {
  metricId: string;
  outputColumnName: string;
}

type TrackMotionMetric =
  | (TrackMotionMetricBase & {
      kind: 'DISTANCE' | 'ELEVATION_CHANGE';
      outputUnit: SpatialDistanceUnit;
    })
  | (TrackMotionMetricBase & {
      kind: 'DURATION';
      outputUnit: SpatialDurationUnit;
    })
  | (TrackMotionMetricBase & {
      kind: 'SPEED';
      outputUnit: SpatialSpeedUnit;
    })
  | (TrackMotionMetricBase & {
      kind: 'ACCELERATION';
      outputUnit: SpatialAccelerationUnit;
    })
  | (TrackMotionMetricBase & {
      kind: 'BEARING';
      outputUnit: 'DEGREES';
    })
  | (TrackMotionMetricBase & {
      kind: 'SLOPE';
      outputUnit: 'PERCENT';
    })
  | (TrackMotionMetricBase & {
      kind: 'IDLE';
      outputUnit: null;
    });

interface TrackMotionStatisticsConfiguration {
  sourceTableName: string;
  pointGeometryColumnName: string;
  trackIdColumns: string[];
  timeColumnName: string;
  distanceMethod: SpatialDistanceMethod | null;
  boundaries: TrackBoundaryConfiguration;
  historyPoints: number;
  idleDistanceThreshold: number | null;
  idleDistanceThresholdUnit: SpatialDistanceUnit | null;
  metrics: TrackMotionMetric[];
  outputTableName: string;
}
```

- 当前 historyPoints=1～100 是 lag 偏移量，不是包含当前点的滚动观测数；不能称为官方 trackHistoryWindow。
- metrics=1～16，含距离、时长、速度、加速度、方向、高差、坡度、IDLE；不是官方八组完整统计。
- 当前 IDLE 只有距离阈值，没有时间阈值；当前高差/坡度读取 Z，输入有 Z 不代表已知垂直单位。
- 当前来源字段保留、指标追加；数据不足的 lag 结果为 NULL；boundaries 为额外 gap 拆分。

## 3. ArcGIS 目标参数和计算

| 官方参数 | 目标设计 |
| --- | --- |
| trackHistoryWindow | 默认 3，包含当前点及前两点；只影响非瞬时统计，不改变瞬时指标和 Idle 分类。 |
| motionStatistics | 八组多选；每组展开显示将产生的字段及单位，支持显式改名。 |
| idleDistanceTolerance / Unit | Idle 相邻观测距离阈值。 |
| idleTimeTolerance / Unit | Idle 相邻观测时长阈值，当前缺失。 |
| timeBoundarySplit / Unit / Reference | 独立固定周期，跨边界重新开始历史。 |
| distanceMethod | 官方默认 Geodesic；平台 EPSG:4326 XY 限制明确标为子集。 |
| distance/duration/speed/acceleration/elevationUnit | 显式输出单位；另须明确输入高程来源和垂直单位。 |

| 指标组 | 官方结果 |
| --- | --- |
| Distance | Distance、TotDistance、MinDistance、MaxDistance、AvgDistance |
| Duration | Duration、TotDuration、MinDuration、MaxDuration、AvgDuration |
| Speed | Speed、MinSpeed、MaxSpeed、AvgSpeed |
| Acceleration | Acceleration、MinAcceleration、MaxAcceleration |
| Elevation | Elevation、ElevChange、TotElevChange、MinElevation、MaxElevation、AvgElevation |
| Slope | Slope、MinSlope、MaxSlope、AvgSlope |
| Idle | Idling、TotIdleTime、PctIdleTime |
| Bearing | Bearing |

- 瞬时距离/时长为上一点到当前点；窗口总距离是窗口内逐段距离之和，不是首尾直线位移。
- AvgSpeed=窗口距离和/窗口时长和，不是逐段速度算术平均。
- Acceleration=(当前速度−上次速度)/当前段时长，首段不足两次速度时不能伪造 0。
- 官方 Idle 计算表规定相邻距离小于容差、相邻时长大于容差；同时统计窗口内空闲总时长与比例。
  阈值相等边界按官方对照验收，不能用 Dwell 算法替代。
- 官方 Slope 为高差/水平距离的比值；当前 PERCENT 是乘 100 的平台表示，必须记录映射。
- Z 轴和 XY 轴不可混用单位；输入缺垂直单位时不能算可靠高差/坡度。
- window=3、点为 (0,0)→(3,0)→(3,4)：第三点 Distance=4，TotDistance=7，不是 5。

## 4. 目标 Inspector UI

设计状态：第 7 节（4.23）是当前观测窗口、八组指标和 Idle 双阈值契约；旧 lag(N) 仅兼容保留。帮助必须说明“包含当前观测的窗口”与“两端差值”不同，完整官方单位及缺失值对照仍待完成。

```text
来源 / 点字段       [ vehicle_points / location ▼]
轨迹标识 / 时间     [ vehicle_id ][ event_time ]
同时间顺序          [ event_id ▼]                  (?)
距离方法            [ 测地线 | 平面 ]
历史窗口            [3] 个观测（含当前）            (?)
固定时间边界        关闭                          [设置]
指标组              [✓距离][✓速度][时长][加速度]
                    [高程][坡度][✓Idle][方向]
输出字段            12 项                         [设置]
输出单位            米 · 秒 · 千米/小时           [设置]
Idle 距离 / 时长    [5][米 ▼]  [30][秒 ▼]
高程来源 / 单位     [Z][米 ▼]（启用高程/坡度后显示）
输出表              [ vehicle_motion ]
```

输出字段 Modal 用“组 / 指标 / 输出字段 / 单位”紧凑表格；瞬时与窗口汇总分区。
历史窗口帮助必须说明“不影响瞬时指标”。低频 gap 拆分折叠显示为平台扩展。


### 参数交互与初值（目标设计）

下表是目标面板约定，不修改旧任务默认值；未明确标注为官方默认的初值均为平台推荐。

| 配置组 | 初值与条件显示 | 对齐边界 |
| --- | --- | --- |
| 计算语义 | 旧 lag 模式单独保留；切换历史窗口需确认 | 不将旧 historyPoints 就地改解释 |
| 历史窗口 | 目标默认 3 个观测，包含当前观测 | 来源为官方 trackHistoryWindow 默认 |
| 指标 | 以八组选择，输出 Modal 展开组内全部字段；不只给八个单值 | 瞬时与窗口结果各有说明 |
| Idle | 仅启用 Idle 组后显示距离与时长，两项均由用户配置 | 距离和时长都满足才分类，严格边界待对照 |
| 高程与单位 | 仅启用高程/坡度时要求高程来源及输入垂直单位 | 输入高程单位不等于输出距离单位 |

历史窗口帮助附 3-4 折线反例；单位 Modal 将输入垂直单位与各类输出单位分区。模式切换不删除旧指标草稿，但提交时必须明确当前生效语义。

## 5. 输出、验收与安全

- 来源字段不变，追加各组字段，输出 BOUNDED；时间仍为观测瞬时，不产生 Watermark。
- 验收上述 3-4 折线；不等时长平均速度；正负加速度；单位转换；Idle 距离+时长同时判定。
- 补足部分历史、相同时间导致零时长、NULL/Empty 点、重复时间、Z 单位、坡度零距离与日历边界样例。
- 空间数值容差、Bearing 方位约定及 stationary Bearing、窗口缺失值分母策略必须在实现前固定。
- 当前 lag 策略不能就地改解释为窗口；通过显式语义升级避免旧任务数值悄然变化。
- Canvas 只显示指标组/数量和配置，不显示轨迹坐标、真实速度、ID 或时间样本。

## 6. 官方依据

- [Calculate Motion Statistics：参数及八组公式](https://developers.arcgis.com/rest/services-reference/enterprise/geoanalytics/tasks/calculate-motion-statistics/)
- [共同规则与版本策略](../canvas-spatial-analysis-processor-roadmap.md)

本目标不扩展 Streaming、滤波或三维测地距离；不把文档中的示例单位笔误复制到协议。

## 7. 4.23 观测历史窗口策略

保留旧字段及旧便利构造器；新字段均可选，不静默迁移旧任务：

```ts
motionSemantics?: 'LEGACY_LAG' | 'OBSERVATION_WINDOW' | null;
windowOptions?: {
  observationCount: number | null;                 // 1..100，包含当前点
  orderByColumns: string[];
  statistics: {
    statisticId: string;                          // 稳定 UUID
    kind: TrackMotionStatistic;                   // 第 3 节八组 31 项，枚举采用大写下划线
    outputColumnName: string;
  }[];
  distanceUnit: SpatialDistanceUnit | null;
  durationUnit: SpatialDurationUnit | null;
  speedUnit: SpatialSpeedUnit | null;
  accelerationUnit: SpatialAccelerationUnit | null;
  elevationColumnName: string | null;             // null/空：Geometry Z；非空：独立高程字段
  inputElevationUnit: SpatialDistanceUnit | null;
  elevationUnit: SpatialDistanceUnit | null;
  idleTimeThreshold: number | null;
  idleTimeThresholdUnit: SpatialDurationUnit | null;
} | null;
```

### 参数与 UI

- 缺失/null 为 LEGACY_LAG，旧 historyPoints、metrics 和旧 Idle 行为保留。新策略使用 windowOptions，
  旧字段不生效但继续保存；新语义字段或对象非空时要求 Canvas 4.23，Manifest/Result/HTTP API 不变。
- 新建默认窗口 3、Geodesic、距离与速度组；单位为米、秒、米/秒、米/秒²。默认只选两组是平台推荐，
  不宣称官方默认（官方默认全部组）。高程输入单位和 Idle 业务阈值不猜填。
- 每个选中组产生该组全部字段，可改名、排序；ID、指标类型、输出名唯一，输出名不能占用原字段。
  缺组内指标报 TRACK_MOTION_GROUP_INCOMPLETE，空组允许存草稿但编译报 TRACK_MOTION_METRICS_REQUIRED。
- 900px 指标 Modal 用单行 Table 编辑字段；切换新旧策略确认并保留两份配置。
  移除指标组需确认，会删除该组字段配置但保留单位。指标组条件显示对应单位、高程来源和 Idle 双阈值。
- 高程可从 Z 或独立字段取得；输入线性单位必须明确，SOURCE_CRS_UNIT 不可用于高程单位。
  XY 点可以使用独立高程字段；GEODESIC 仍要求 EPSG:4326 XY，不扩展三维测地距离。
- Canvas 显示“窗口 N 个观测”或“旧版偏移 N 点”，最多预览两个指标，显示总数；不显示任何实际统计值。

### 计算与边界裁决

- 按轨迹/片段、时间和次序升序；未启用拆分时是一整条轨迹。固定时间边界与 gap 均重置历史；
  相同时间仍需可区分的次序键，否则惰性报 TRACK_OBSERVATION_ORDER_NOT_UNIQUE。NULL 时间不参与且不输出。
- 原表保留，结果为逐观测 BOUNDED 新表；保留原字段和事件时间列，不添加 Watermark、不增加 Action。
- 窗口 N 个点：点值统计取 N 个位置，段统计最多取完全位于窗口内的 N−1 段；
  加速度统计最多取两段均在窗口内的 N−2 个加速度。最后一项是平台明确边界裁决，官方模糊处仍需对照。
  N=1 时瞬时距离/时长/速度照算，段汇总为 NULL；N<3 时加速度窗口汇总为 NULL，瞬时加速度不受影响。
- 历史不足时使用已有观测。NULL/Empty Geometry 保留原行，空间指标为 NULL，且不跳过该点连接更早的 Geometry；
  纯时间差仍计算。非法 Point/非有限 XY/非法经纬度报 TRACK_MOTION_POINT_INVALID，摘要不含数据。
- 相同时间有确定次序时 duration=0，speed/acceleration/Idle=NULL；不以 0 代替未知，也不除零。
  静止同位置 Bearing=NULL；平面 Bearing 为从北顺时针 [0,360)，测地 Bearing 用 WGS84 椭球正向方位角，处理日期变更线。
- 累计距离与高差为段值求和，高差保留正负；AvgSpeed 对速度可计算的段累加距离和时长后相除。
  高程/坡度中的非有限高程转为 NULL，原高程字段不变。其余 min/max/avg 忽略缺失测量值，全部缺失则 NULL；
  有效值作为均值分母，不将缺失测量值视为 0。这是平台缺失值约定，待官方服务对照。
- 坡度为换算到相同线性单位后的高差/水平距离，不乘 100；距离为 0 时坡度 NULL。
  旧 PERCENT 坡度语义仍保留在 LEGACY_LAG 中。
- Idle 判定：距离严格小于距离阈值 **且** 时长严格大于时间阈值，等值不判静止。
  TotIdleTime 累计符合条件的段时长；PctIdleTime=静止时长/可分类段总时长×100，非静止段贡献 0，
  没有可分类段则 NULL。TotIdleTime 使用 durationUnit。
- 实现为 Spark 惰性窗口表达式，窗口上限 100 是平台现有容量约束，不是 ArcGIS 限制。
  单位按逐版本能力开放：4.36 已增加国际码/美国测量制，4.47 增加固定周；固定月年和官方别名不宣称已支持。

新增稳定配置错误：TRACK_MOTION_WINDOW_REQUIRE_SCHEMA_VERSION、INVALID_TRACK_HISTORY_WINDOW、
TRACK_MOTION_GROUP_INCOMPLETE、DUPLICATE_TRACK_MOTION_STATISTIC、TRACK_ELEVATION_UNIT_REQUIRED、
INVALID_IDLE_TIME_THRESHOLD；来源/字段/名称/UUID/单位继续复用已有错误。

## 8. 当前明确支持范围收口（2026-09-13，Canvas 4.76）

本次按已经落地且能够稳定验证的平台语义完成收口，不把 ArcGIS 服务未公开或尚未对照的细节补写成保证：

- 新建节点使用 `OBSERVATION_WINDOW`，默认 3 个观测并选择距离、速度两组；旧定义继续按
  `LEGACY_LAG` 解释。两套配置在确认式切换中分别保留，不对旧 `historyPoints` 和 `metrics` 做静默迁移。
- 观测窗口完整支持八组 31 项输出。点值窗口含当前观测；段汇总只含完整落入窗口的 N−1 段，
  加速度汇总最多含 N−2 个加速度。窗口 1/2、历史不足、不同持续时长、零时长和固定周期重置均有回归。
- 距离支持平面和受控 WGS84 测地计算；Bearing 使用从北顺时针角度并覆盖日期变更线。
  高程可来自 Geometry Z 或独立数值字段，输入垂直单位和输出高程单位独立；坡度是同单位高差除以
  水平距离，不乘 100。国际码与美国测量制、固定 7 天周均沿用公共单位语义。
- Idle 同时要求相邻距离严格小于距离阈值、相邻时长严格大于时间阈值；等值不判静止。
  `TotIdleTime` 和 `PctIdleTime` 只统计可分类段。NULL/Empty Geometry 不跨点连接，非有限高程只在
  测量中视为 NULL，原始字段值不被修改；NULL 时间不输出，同时间必须由显式顺序字段唯一确定。
- 所有 31 个指标以及保留的原字段均达到 `FIELD_COMPLETE`，不存在 `WRITTEN_UNKNOWN_SOURCE`。
  距离/时长/速度/加速度/高程/坡度/Idle/Bearing 分别追溯到真实 Geometry、时间和高程来源字段。
  同时间唯一性检查改用内部校验时间列，原始时间字段继续是直接来源；没有放宽 Catalyst Analyzer。
- 20,000 个 Point 的单轨迹样例在分析阶段触发 0 个 Spark Job，执行后仍逐观测输出 20,000 行，
  窗口 100 的末行瞬时距离、累计距离和时长正确。计划使用 Spark Window，不在 Driver 收集轨迹组，
  也不使用 `collect_list`；该样例不是任意偏斜或生产容量承诺。
- Motion 后端基线 10 项及新增血缘/规模 2 项通过；前端 Inspector 与 Parser 2 个文件 6 项通过。
  真实页面确认新节点未应用前不触发 Task Engine 编译、两种语义确认切换且各自草稿保留、八组 31 项、
  单位/高程、Idle 双阈值、固定时间边界、无效草稿应用和问题详情均可用；验收节点只留在未保存草稿。

据此，进度清单中的 Motion 按当前明确支持范围完成。仍不声明 ArcGIS Enterprise 官方字段别名、
所有单位名称、未公开的缺失值/平局处理、固定月年、服务端容差与数值或生产容量完全等价；节点仍只支持
有界批处理 Point 轨迹，不扩展 Streaming、任意三维测地距离或 Arcade。
