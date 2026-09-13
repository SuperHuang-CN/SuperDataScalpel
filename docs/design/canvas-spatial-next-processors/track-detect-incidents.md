# Canvas `TRACK_DETECT_INCIDENTS` Processor 设计

## 1. 定位与审计结论

引入于 Canvas 4.17，仅 BATCH，表/事件输入，Geometry 可选。4.20 旧事件生命周期存在与 ArcGIS Detect Incidents 相反的边界行为；4.21 已增加显式新策略作局部修正，旧语义仅为兼容保留。

第 2 节保留 4.20 审计快照。4.21 已新增可选生命周期策略、状态字段和确定次序，以及固定时间边界；
缺失策略的旧定义继续按 LEGACY 执行，不静默升级。其余目标与未完成项见第 7 节。

### 4.47 固定时长周

相邻时间 gap 和事件累计时长 `incidentDurationUnit`新增“周（固定 7 天）”，每周为 604800 秒。
与已有按时区推进的日历周区分；切换单位不换算已填数值，隐藏草稿保留并参与 4.47 门槛。
固定月年不开放，阈值边界、节点算法与输出粒度不变；详见[共享时长规则](../canvas-spatial-units.md#447-固定时长周与日历周)。

### 4.36 公共单位补充

相邻观测距离边界使用公共距离单位。事件状态机、时间单位与固定时间边界不因距离单位扩充而改变。
单位名称明确国际制/美国测量制，切换不自动换算数值，旧单位结果不变。具体枚举、字段和证据见[公共空间单位](../canvas-spatial-units.md)；不代表整节点已完成官方对照。

### 当前阅读入口与对齐边界（4.67 汇总）

- **已接入范围**：4.21 的显式生命周期、状态字段、确定次序及边界见第 7 节；4.46 的受控字段窗口见第 8 节；4.63～4.65 的距离、速度和加速度窗口来源见第 9～11 节；4.66 的轨迹时间/序号标量见第 12 节；4.67 的相对观测 Point X/Y 标量见第 13 节。
- **官方参照与差异**：参照 GA Detect Incidents；字段、累计轨迹距离、逐观测速度和加速度窗口，开始/当前时间、时长和序号标量，以及 Point 的受控相对观测 X/Y 访问已接入；完整 Arcade Geometry/TrackWindow、整行对象、其他表达式和官方边界对照仍待完成。
- **面板修订要求**：开始条件、可选结束条件及结果模式分开；条件值不进入 Canvas 摘要。

本摘要不替代逐版本契约。下节的“当前”均指 4.20 历史状态；目标线框不作为已实现截图。
参数覆盖、本地验证和官方结果对照分别登记，见[对齐验收规则](../canvas-spatial-analysis-processor-roadmap.md#7-对齐验收)。

## 2. 4.20 配置快照（旧语义保留）

```ts
type TrackIncidentResultMode =
  | 'INCIDENTS_ONLY'
  | 'ALL_EVENTS';

interface TrackDetectIncidentsConfiguration {
  sourceTableName: string;
  pointGeometryColumnName: string | null;
  trackIdColumns: string[];
  timeColumnName: string;
  distanceMethod: SpatialDistanceMethod | null;
  boundaries: TrackBoundaryConfiguration;
  startCondition: CanvasFilterCondition;
  endCondition: CanvasFilterCondition | null;
  resultMode: TrackIncidentResultMode | null;
  outputTableName: string;
  incidentIdColumnName: string;
  incidentFlagColumnName: string;
  incidentStartTimeColumnName: string;
  incidentEndTimeColumnName: string;
  incidentDurationColumnName: string;
  incidentDurationUnit: SpatialDurationUnit;
}
```

- 当前无 endCondition 时，首次开始之后的行持续标记为事件，直到片段结束；再次 start 上升沿会改变序号。
- 当前显式结束命中行仍被计入事件；事件内行重复写整段总持续时间。这三项均非官方目标语义。
- 当前仅 INCIDENTS_ONLY / ALL_EVENTS，条件树复用 Filter；不等价 Arcade 轨迹窗口表达式。
- 当前 incidentFlag 不能替代官方 Started/OnGoing/Ended 状态。

## 3. 官方目标状态机

按轨迹及确定次序处理，固定时间边界重置状态。开始/结束条件是“逐观测判定”，不允许事件里重复命中开始条件就再次开段。

| 之前状态 | 条件 | 当前标记 / 新状态 |
| --- | --- | --- |
| 未激活 | start=true 且 end 不为 true | isIncident=true，Started，激活 |
| 未激活 | 其他 | false，状态空，保持未激活 |
| 激活，无 end 配置 | start=true | true，OnGoing |
| 激活，无 end 配置 | start=false | false，Ended，关闭 |
| 激活，有 end 配置 | end 不为 true | true，OnGoing，不重复开段 |
| 激活，有 end 配置 | end=true | false，Ended，关闭 |

- start/end 同时 true：结束优先，该行不是事件成员。
- Ended 是事件关闭观测的状态，不表示该行仍 isIncident=true；ALL_EVENTS 中可见。
- 官方 IncidentDuration 是“当前观测时间−事件开始时间”，Started=0，OnGoing/Ended 递增；
  不是对所有行重复填写整段总时长。平台的整段时长如保留，应独立命名为扩展字段。
- 官方输入支持无 Geometry 的表、点、线、面；不启用距离拆分时不应强制 Point。
- 增加 timeBoundarySplit/Unit/Reference；保留 gap 拆分但标为自有扩展。
- 缺时间的输入官方不使用且不输出。平台若采用失败策略须显式标明差别；不得猜测时间。
- NULL 条件建议按非 true 处理并给出受控表达式规范，此为平台选择，不宣称已验证 Arcade 等价。

## 4. 目标 Inspector UI

设计状态：第 7 节（4.21）明确生命周期、次序与逐观测时长，第 8 节（4.46）已接入受控字段窗口。
完整 Arcade Geometry/TrackWindow、其他受控表达式和未核实的结果模式仍为目标，不添加假可用入口；事件成员、Ended 标记行及最终汇总不是同一种输出粒度。

```text
来源表              [ vehicle_events ▼]
轨迹标识 / 时间     [ vehicle_id ][ event_time ]
同时间顺序          [ event_id ▼]
开始条件            speed > · 1 个配置值            [编辑]
结束条件            未配置                         [编辑] (?)
固定时间边界        关闭                           [设置]
结果范围            [仅事件成员 | 全部并标记]
事件字段            ID · 状态 · 当前已持续时间      [设置]
持续时间单位        [毫秒 ▼]
输出表              [ speeding_incidents ]
```

结束条件帮助：“未配置时，开始条件不成立即结束”；不能再提示“下一次开始或轨迹结束”。
字段设置增加状态与逐点持续时间，条件 Modal 复用紧凑受控编辑器，原始字面量不出现在摘要。
受控字段窗口已经按第 8 节接入；完整 Arcade 轨迹表达式仍是显式差距，不直接新增任意脚本执行器。
REST 描述段提及“含事件的全部轨迹”，但 outputMode 参数表仅列 AllFeatures/Incidents；
该第三模式暂列待核对，不直接写入当前枚举。


### 参数交互与初值（目标设计）

下表是目标面板约定，不修改旧任务默认值；未明确标注为官方默认的初值均为平台推荐。

| 配置组 | 初值与条件显示 | 对齐边界 |
| --- | --- | --- |
| 事件语义 | 新建使用条件生命周期；旧 LEGACY 保留并明确提示，切换需确认 | 4.21 已具备局部实现，仍非完整对齐 |
| 开始 / 结束 | 开始必配；结束默认未配置，此时开始条件不成立即结束 | NULL 与同时命中规则见第 3 节 |
| 结果范围 | 明确选择仅事件成员或全部观测，不隐含第三种输出模式 | Ended 行是否被 Incidents 保留仍待服务核对 |
| 时长与状态 | 新策略显示状态字段和逐观测持续时间，建议毫秒 | 不能用整段时长或布尔字段替代 |
| 时间与次序 | 固定边界默认关闭；同时间次序字段独立配置 | 无时间观测排除，不伪造结束观测 |

条件编辑窗口保留实际用户配置，常驻摘要只显示字段、操作符和值数量；帮助区别“Ended 状态”与“属于事件成员”。

## 5. 验收与输出

官方序列 [0,10,15,20,40,10,12,-2,-12]，start > 15：

- 无 end：成员标记 F,F,F,T,T,F,F,F,F；
  状态 null,null,null,Started,OnGoing,Ended,null,null,null。
- end < 0：成员标记 F,F,F,T,T,T,T,F,F；-2 为 Ended，不属于事件成员。
- 等时间间隔时，Started 时长 0，后续按观测时刻增加；不能每行得到相同总时长。
- 必须覆盖：激活期间重复 start、结束后 start 仍为 true、start/end 同时成立、轨迹尾未关闭、
  边界重置、NULL 条件/时间和同时间多行。
- INCIDENTS_ONLY 是否保留 Ended 标记行须用服务结果核对；目标 UI“仅事件成员”默认只保留 isIncident=true，
  不把此取舍伪称已与官方输出过滤完全一致。
- 输出为 BOUNDED 新表，保留原字段；不添加虚构的结束观测，不引入 Watermark。
- 条件值、实体标识值、运行数据不进入 Canvas 或日志。

## 6. 官方依据

- [Detect Incidents REST](https://developers.arcgis.com/rest/services-reference/enterprise/geoanalytics/tasks/detect-incidents/)
- [Enterprise 11.3：示例、状态与逐观测持续时间](https://enterprise.arcgis.com/en/portal/11.3/use/geoanalytics-detect-incidents.htm)
- [共同规则与版本策略](../canvas-spatial-analysis-processor-roadmap.md)

## 7. 4.21 开发进度（非完整对齐声明）

新增可选配置：

```ts
incidentSemantics?: 'LEGACY' | 'CONDITION_LIFECYCLE' | null;
incidentStatusColumnName?: string | null;
orderByColumns?: string[] | null;
```

CONDITION_LIFECYCLE 使用第 3 节状态机；新建节点默认该策略，旧定义缺失/null 仍为 LEGACY。
状态字段在新策略必填；Started=0，后续持续时间为当前时间减开始时间；轨迹末尾未关闭的事件 end 为 null。
原字段与原输入表保留，ALL_EVENTS 排除无时间观测，INCIDENTS_ONLY 再过滤非成员。
无距离拆分时可选 Geometry 不限 Point；距离拆分仍要求 Point。排序键全部按升序，NULL 键在前。
真实重复次序由运行时惰性检查报 TRACK_OBSERVATION_ORDER_NOT_UNIQUE；编译不试读。

边界新增 `boundaries.fixedTimeBoundary`，字段及版本门槛见[稳定定义](../canvas-task-definition.md#713d713g-track-节点)。
窗口重置不会伪造结束观测；相邻 gap 与固定边界任一满足即开启新片段。
Inspector 用确认框切换新旧策略，条件/字段/边界设置按需打开，切换保留原配置。

已通过本地 Spark 官方示例真值表、start/end 同时成立、重复开始、结束后重开、NULL、次序歧义、
固定边界重置和旧策略回归；月末、闰年、夏令时另有单元验证。
该阶段之后，4.46 增加下述受控字段窗口，4.63～4.65 又加入受控距离、速度和加速度窗口来源，
4.66 加入轨迹时间与序号标量，4.67 加入 Point 的受控相对观测 X/Y 标量。完整 Arcade Geometry/TrackWindow、其他受控表达式及真实 ArcGIS 服务对照
（特别是 Incidents 输出范围）仍待完成。

## 8. 4.46 受控字段窗口条件

参考 [Enterprise 11.3 条件表达式](https://enterprise.arcgis.com/en/portal/11.3/use/geoanalytics-detect-incidents-expression.htm)
与[事件检测移动均值示例](https://enterprise.arcgis.com/en/portal/11.3/use/geoanalytics-detect-incidents.htm)。2026-09-08 核对原文：
`TrackFieldWindow(field,start,end)` 包含起点、不包含终点；0 为当前，负数回看，正数前看。
例如官方 `Mean($track.field["speed"].window(-5,0)) < 10` 使用前 5 条速度，不是“前 5 条加当前”。

### 配置与条件引用

```ts
interface TrackIncidentWindow {
  bindingName: string;
  sourceColumnName: string;
  kind: 'COUNT' | 'SUM' | 'MEAN' | 'MIN' | 'MAX' | 'FIRST' | 'LAST' | 'STDDEV_POP' | 'VARIANCE_POP' | null;
  startOffset: number | null;
  endOffset: number | null;
}
// TrackDetectIncidentsConfiguration 可选追加
conditionWindows?: TrackIncidentWindow[];
```

配置 `{ bindingName:'past_mean', sourceColumnName:'speed', kind:'MEAN', startOffset:-5, endOffset:0 }`，
再用原有条件编辑器配置 `past_mean < 10`，即可表达上述移动均值场景。条件字面量只在条件弹窗内显示。
每个指标只读取入口原字段，全部同时求值；不能引用另一个指标，不按数组排序形成计算链。
绑定名大小写不敏感唯一，不能覆盖入口或平台内部字段；限定 ASCII 标识符长度 1～128。

### 执行与精度边界

- 仅 CONDITION_LIFECYCLE 生效；LEGACY 隐藏并保留指标草稿，但不为其创建窗口。开始/结束条件不会因切换自动改写。
- 先去掉无时间观测，按轨迹 ID、固定边界/gap 片段划分，按时间及 orderByColumns 确定次序。
  之后计算观测位置窗口；不跨轨迹/拆分片段，不在 start/end 状态变化处重新切窗口。
- 将 `[start,end)` 转为 Spark ROWS `[start,end-1]`。两端为有界 32 位整数，起点不得为 MIN_VALUE 且必须小于终点。
  超出片段部分自然裁剪；负范围不表示持续时间，正偏移不支持流式实时计算，本节点仍仅 Batch。
- SUM/MEAN/MIN/MAX 使用 Spark 原生聚合及实际提升类型，不额外转为 DOUBLE 或用前端类型矩阵限制。
  COUNT 是**字段非空值数**，不是含 NULL 的数组长度；FIRST/LAST 取窗口首末观测值，NULL 不跳过。
  STDDEV_POP/VARIANCE_POP 明确为总体统计，不冒充官方未确认的样本公式。空窗 COUNT 为 0，其他结果 NULL。
- 临时指标仅供开始/结束条件，输出仍为原字段 + 六个事件字段，不输出内部片段/指标列。
  Catalyst 对指标和事件字段追溯原始字段，Compiler 不运行 Action，不 collect 到 Driver，不增加缓存或 Checkpoint。
- 门槛为 `TRACK_INCIDENT_WINDOWS_REQUIRE_SCHEMA_VERSION`；包括 LEGACY 非活动草稿的非空数组均要求 4.46。
  缺失/null 规范化为 `[]`，旧便利构造器保留；名称/范围/来源等业务错误允许保存，发布由 Operator 拒绝。
  Canvas/Runner 只显示数量，不显示窗口配置值、条件字面量或运行数据。

### Inspector 与目标交互图

```text
同时间顺序          [ event_id ▼ ]
窗口指标            2 项                              [设置]
开始条件            1 个条件                          [编辑]
结束条件            未配置                            [配置]

┌ 配置事件窗口指标 · 860px ──────────────────────────────────────┐
│ 观测偏移 [起点, 终点) (?)                     [+ 添加窗口指标] │
│ 指标名      原始字段       函数       起点含  终点不含   操作   │
│ past_mean   speed         均值        -5       0       ↑↓×    │
│ next_value  temperature   窗口首值     1       2       ↑↓×    │
│ 1 个窗口配置问题 (?)                                          │
│                                    [取消] [保存窗口草稿]      │
└──────────────────────────────────────────────────────────────┘
```

弹窗使用独立草稿，取消不提交；支持添加、改名、函数/原字段、排序和确认删除。删指标不清空引用它的条件。
缺字段或范围错误就地标红，问题详情按需查看，仍允许保存草稿。开始/结束条件弹窗也隔离草稿，
取消首次配置结束条件不会意外创建空结束条件。Inspector 不额外传播 Schema，指标候选仅供本节点条件输入；
最终聚合类型和可执行性由 Compiler 决定。Canvas 增加指标计数，旧模式明确显示未启用。

### 验证与剩余能力

专项覆盖官方 [-1,2) 示例、[-5,0) 不含当前、未来单点窗口、片段/轨迹重置、同时间次序、全部九函数的 NULL/空窗、
草稿门槛、别名冲突、禁止指标互引、输出隔离、零 Job 与 FIELD_COMPLETE 原字段血缘；执行结果记于开发清单。
这是受控字段窗口的接入，不是完整 Arcade 兼容声明。后续 4.63～4.65 已补距离、速度与加速度窗口；
TrackGeometryWindow、TrackWindow 原始复合对象和更丰富受控计算仍在路线图范围内。Distance/Speed/
Acceleration 的 Current 可由对应来源的 `FIRST + [0,1)` 表达，At(n) 可由 `FIRST + [n,n+1)` 表达，
不再作为独立协议缺口；没有将复合对象或任意 Arcade 改写成已完成。
真实 ArcGIS 服务结果、全页面交互与大轨迹容量尚未验收，本节点完成复选框保持开放。

## 9. 4.63 受控轨迹距离窗口

2026-09-12 再次核对 Enterprise 11.3 官方表达式文档：`TrackDistanceWindow(start,end)` 返回
以当前观测为 0 的左闭右开范围内、各观测对应的累计轨迹距离；官方统一使用 WGS84 测地线，单位为米。
例如官方 `[-1,2)` 示例在第二个观测返回 `[0,60,140]`：这是片段首点、当前点和下一点的三个累计值，
不是两个相邻段距离。

`TrackIncidentWindow` 增加可选 `source: FIELD|TRACK_DISTANCE|null`：

- 缺失/null/FIELD 完全保留 4.46 的 `sourceColumnName` 字段窗口。
- TRACK_DISTANCE 忽略保留的 `sourceColumnName` 草稿，对范围内逐观测累计距离使用现有九种聚合；
  片段首观测累计值为 0，单观测窗口同样包含该值。
- 轨迹距离只支持 EPSG:4326 XY Point，距离固定为米，不复用轨迹分段的平面/测地线选项；NULL Point
  及其之后无法证明完整累计距离的观测返回 NULL，直到新片段重新从 0 开始，并沿用所选聚合的 NULL 规则。
- 先完成轨迹、固定边界/gap 分段和确定次序，再累计轨迹距离；窗口不跨轨迹或片段。所有绑定仍一次性
  从准备后的原观测求值，不能引用其他绑定，也不进入结果 Schema。
- 任何 TRACK_DISTANCE（包括 LEGACY 下的非活动草稿）要求 Canvas 4.63；稳定门槛为
  `TRACK_INCIDENT_DISTANCE_WINDOWS_REQUIRE_SCHEMA_VERSION`。缺 Geometry 为
  `TRACK_INCIDENT_DISTANCE_WINDOW_REQUIRES_GEOMETRY`，非 Point 或非 WGS84 XY 复用既有空间错误码。
- Inspector 的同一窗口表格增加“原始字段/轨迹距离”来源选择；选择轨迹距离时只显示“累计轨迹距离 · 米”，
  实际坐标、距离和条件字面量不进入 Canvas 或日志。Canvas 仅显示轨迹距离窗口数量。

该阶段只接入官方 TrackDistanceWindow 的受控标量聚合。Current/At 后续明确由对应来源的单观测窗口
等价表达，不另建类型；Geometry 数组或任意 Arcade 执行仍未支持。

## 10. 4.64 受控轨迹速度窗口

2026-09-12 核对同一份 Enterprise 11.3 官方表达式文档：`TrackCurrentSpeed()` 是前一观测到当前观测
的速度，`TrackSpeedWindow(start,end)` 返回以当前观测为 0 的左闭右开范围内逐观测速度数组。所有距离
使用 WGS84 测地线，速度单位固定为米/秒；官方示例的片段首观测速度为 0，`[-1,2)` 在第二个观测返回
首点、当前点和下一点的三个速度值。

`TrackIncidentWindow.source` 增加 `TRACK_SPEED`：

- 复用九种受控聚合，窗口直接覆盖 `[startOffset,endOffset)` 中的逐观测速度；不把范围内总距离除以
  总时长，也不聚合相邻段数组。
- 片段首观测速度固定为 0。后续速度为上一观测到当前观测的 WGS84 测地距离除以秒；同时间、NULL Point、
  前一点缺失或无法形成有效时长时返回 NULL。这是平台防除零与缺失值约定，不伪称官方已覆盖异常输入。
- 只支持 EPSG:4326 XY Point，不复用 `distanceMethod`，不跨轨迹、固定边界或 gap 片段；范围边缘自然裁剪。
- 任意 TRACK_SPEED（包括 LEGACY 非活动草稿）要求 Canvas 4.64；稳定门槛为
  `TRACK_INCIDENT_SPEED_WINDOWS_REQUIRE_SCHEMA_VERSION`。缺 Geometry 为
  `TRACK_INCIDENT_SPEED_WINDOW_REQUIRES_GEOMETRY`，其他 Geometry 限制复用既有错误码。
- Inspector 的同一窗口表格增加“轨迹速度”来源，显示“逐观测速度 · 米/秒”；条件、Canvas 和 Runner
  继续只暴露安全计数，不记录坐标、实际速度、偏移或条件字面量。

该阶段只接入官方 TrackSpeedWindow 的受控标量聚合。TrackCurrentSpeed/TrackSpeedAt 后续明确由
单观测窗口等价表达，不另建类型；Geometry 数组或任意 Arcade 执行仍未支持。

## 11. 4.65 受控轨迹加速度窗口

Enterprise 11.3 将 `TrackCurrentAcceleration()` 定义为前一观测与当前观测之间的加速度，
`TrackAccelerationWindow(start,end)` 返回左闭右开范围内的逐观测加速度数组；官方示例使用米/秒²，
片段首观测为 0。平台以当前速度减前一观测速度，再除以两次观测的秒数实现该定义。

`TrackIncidentWindow.source` 增加 `TRACK_ACCELERATION`：

- 窗口直接聚合 `[startOffset,endOffset)` 中逐观测加速度，复用九种受控聚合，不将整个范围简化成首尾
  速度差除以总时长。
- 片段首观测加速度为 0；后续值使用 4.64 相同的 WGS84 逐观测速度。同时间、当前或前一速度为 NULL、
  当前或前一 Point 缺失时返回 NULL，不除以 0。
- 只支持 EPSG:4326 XY Point，不读取 `distanceMethod`，不跨轨迹、固定边界或 gap 片段。
- 任意 TRACK_ACCELERATION（包括 LEGACY 非活动草稿）要求 Canvas 4.65；稳定门槛为
  `TRACK_INCIDENT_ACCELERATION_WINDOWS_REQUIRE_SCHEMA_VERSION`。缺 Geometry 为
  `TRACK_INCIDENT_ACCELERATION_WINDOW_REQUIRES_GEOMETRY`，其他 Geometry 限制复用既有错误码。
- Inspector 显示“逐观测加速度 · 米/秒²”；条件、Canvas 与 Runner 继续只保留安全计数。

该阶段只接入官方 TrackAccelerationWindow 的受控标量聚合。TrackCurrentAcceleration/
TrackAccelerationAt 后续明确由单观测窗口等价表达，不另建类型；Geometry 数组或任意 Arcade 执行仍未支持。

## 12. 4.66 受控轨迹时间与序号标量

2026-09-12 核对 Enterprise 11.3 官方条件表达式文档：`TrackStartTime()` 返回轨迹开始时间的 Unix
Epoch 毫秒，`TrackDuration()` 返回轨迹开始至当前观测的毫秒数，`TrackCurrentTime()` 返回当前观测
时间的 Epoch 毫秒，`TrackIndex` 在轨迹首观测返回 0。这四项不是窗口数组，也不应配置 SUM/MEAN 等
聚合函数，因此没有继续扩张 `TrackIncidentWindow.source`。

配置新增独立数组：

```ts
interface TrackIncidentScalar {
  bindingName: string;
  source: 'TRACK_START_TIME' | 'TRACK_DURATION' | 'TRACK_CURRENT_TIME' | 'TRACK_INDEX' | null;
}

// TrackDetectIncidentsConfiguration 可选追加
conditionScalars?: TrackIncidentScalar[];
```

- 缺失/null 规范化为 `[]`；非空数组（含 LEGACY 非活动草稿）要求 Canvas 4.66，低版本使用
  `TRACK_INCIDENT_SCALARS_REQUIRE_SCHEMA_VERSION` 拒绝。
- 四种结果统一为 LONG。开始/当前时间精确到 Epoch 毫秒，时长为当前时间减片段首观测时间，序号使用
  确定排序后的零基行号；不使用 DATE/TIMESTAMP，避免条件字面量单位含糊。
- DataScalpel 的固定时间边界、相邻时间 gap 或距离 gap 会形成新的执行片段，因此开始时间、时长和序号
  随片段重置。这是平台在已有分段扩展下的明确语义，不伪称 ArcGIS 对自有 gap 参数作过同样定义。
- 标量名遵循窗口指标相同的 ASCII 标识符规则，并与来源字段、窗口指标和其他标量大小写不敏感唯一。
  空名或空来源允许保存草稿，Compiler 分别使用 `TRACK_INCIDENT_SCALAR_NAME_INVALID` 或
  `REQUIRED_CONFIGURATION` 拒绝执行。
- Inspector 使用独立 680px 紧凑表格，不展示无意义的字段、窗口偏移或聚合函数。四项均进入本节点
  开始/结束条件候选，但不进入最终 Schema、Canvas 详情或日志；Canvas/Runner 只显示安全数量。
- Spark 计划使用同一轨迹/片段分区和确定次序计算，全程惰性，不增加 Action、缓存或 Checkpoint。
  条件通过 Catalyst 追溯原始时间/轨迹边界字段，最终输出仍只含来源字段和六个事件结果字段。

运动 Current/At 不另建协议：对 TRACK_DISTANCE、TRACK_SPEED 或 TRACK_ACCELERATION 选择窗口首值，
`[0,1)` 等价 Current，`[n,n+1)` 等价 At(n)。Inspector 帮助明确这一换算；未来若提供快捷模板，也只生成
现有窗口配置，不创建新的稳定类型。Geometry/TrackWindow 返回复合对象，当前标量条件树不能安全消费，
仍需独立设计，不能机械加入九种聚合。

## 13. 4.67 Point 相对观测坐标标量

Enterprise 11.3 官方条件表达式以 `TrackGeometryWindow(-1,0)[0]["x"]` 示例读取轨迹
Geometry 窗口中某个观测的坐标。DataScalpel 不把 Geometry 数组或整行对象放入现有标量条件树，
只增加 Point X/Y 的受控单观测入口：

```ts
interface TrackIncidentScalar {
  bindingName: string;
  source:
    | 'TRACK_START_TIME'
    | 'TRACK_DURATION'
    | 'TRACK_CURRENT_TIME'
    | 'TRACK_INDEX'
    | 'TRACK_POINT_X_AT'
    | 'TRACK_POINT_Y_AT'
    | null;
  offset?: number | null;
}
```

- `TRACK_POINT_X_AT/TRACK_POINT_Y_AT` 必须配置 32 位整数 `offset`：0 为当前观测，负数回看，
  正数前看。访问只在当前 DataScalpel 轨迹片段内生效，不跨轨迹、固定边界或 gap 边界；
  超出片段或目标 Geometry 为 NULL 时返回 NULL。
- 只接受拥有完整 Geometry 元数据的 Point 字段。返回类型为可空 DOUBLE，X/Y 数值和单位
  跟随来源 CRS；不要求 WGS84，也不做投影换算。
- 坐标来源（包括 LEGACY 下的非活动草稿）要求 Canvas 4.67，低版本使用
  `TRACK_INCIDENT_POINT_COORDINATES_REQUIRE_SCHEMA_VERSION` 拒绝。缺偏移或 Point Geometry 分别使用
  `TRACK_INCIDENT_POINT_COORDINATE_OFFSET_REQUIRED` 和
  `TRACK_INCIDENT_POINT_COORDINATE_REQUIRES_GEOMETRY`。
- 时间/序号来源忽略但保留隐藏 `offset` 草稿，切换来源不静默清空。Inspector 只在
  Point 坐标来源时显示偏移；Canvas、定义摘要和 Runner 只显示坐标标量数量，不显示绑定名、
  偏移或坐标值。
- 结果仅供开始/结束条件引用，不进入节点输出 Schema。Spark 使用同一轨迹/片段窗口的单行
  frame 读取坐标，不增加 Action、缓存、Checkpoint 或任意 Arcade 执行器。

这是官方 TrackGeometryWindow Point 坐标访问的受控子集，不等于完整 Geometry 窗口、
`TrackWindow`、整行字段访问或任意 Arcade 支持。

## 14. 当前明确支持范围收口（2026-09-13，Canvas 4.76）

- `LEGACY` 旧语义继续用于兼容，`CONDITION_LIFECYCLE` 已按 Started/OnGoing/Ended 状态机执行：
  无结束条件时开始条件首次不成立即结束；有结束条件时结束优先，Ended 观测不属于事件成员。
  `INCIDENTS_ONLY` 和 `ALL_EVENTS` 两种结果范围均已贯通，事件时长按当前观测累计而不是复制整段总时长。
- 原始字段窗口、WGS84 累计轨迹距离、逐观测速度/加速度、轨迹开始/当前时间、时长、序号，
  以及相对观测 Point X/Y 坐标均可作为受控条件绑定。窗口保持左闭右开、按轨迹/固定边界/gap 片段重置，
  同时间观测由显式附加字段确定次序；临时绑定不进入输出 Schema。
- 两种结果范围均已验证为 `FIELD_COMPLETE`：来源字段保持 `DIRECT`，事件 ID、活动标记、状态、
  起止时间和逐观测持续时间均能追溯到真实来源字段，不把临时绑定或平台内部列登记为未知物理来源。
- 20,000 条单轨迹样例在分析阶段不触发 Spark Job，输出保持一条参与观测一行；计划不含 Driver
  `CollectLimit` 或 `collect_list`。实现使用 Spark 分区窗口和排序，仍会产生单轨迹分区、排序和窗口状态开销，
  因而不能据此宣称无限单轨容量或 Enterprise 生产容量等价。
- 真实 Inspector 已验证新节点不立即请求编译、条件生命周期默认值、窗口指标/轨迹标量紧凑表格、
  无效草稿保存、结果字段和问题详情入口；Canvas 与问题摘要不暴露条件字面量、坐标或运行数据。

当前收口不实现任意 Arcade 解释器，也不承诺完整 `TrackGeometryWindow`、`TrackWindow` 复合对象、
整行对象、Portal 文案中尚未核实的第三种结果范围、官方字段别名或 Enterprise 服务端数值/边界完全一致。
本节点仍仅支持 BATCH；若未来需要实时状态机，应单独设计 Watermark、迟到修正和状态保留语义。
