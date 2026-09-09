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

### 当前阅读入口与对齐边界（4.46 汇总）

- **已接入范围**：4.21 的显式生命周期、状态字段、确定次序及边界见第 7 节；4.46 的受控字段窗口见第 8 节。
- **官方参照与差异**：参照 GA Detect Incidents；字段窗口已接入，完整 Arcade 几何/运动/时间表达式和官方边界对照仍待完成。
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
完整 Arcade 几何/运动/时间表达式和未核实的结果模式仍为目标，不添加假可用入口；事件成员、Ended 标记行及最终汇总不是同一种输出粒度。

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
该阶段之后，4.46 增加下述受控字段窗口。完整 Arcade 轨迹表达式、真实 ArcGIS 服务对照（特别是 Incidents 输出范围）仍待完成。

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
这是受控字段窗口的接入，不是完整 Arcade 兼容声明。TrackGeometryWindow、TrackWindow 原始复合对象、
TrackDistance/Speed/Acceleration、时间范围窗口和更丰富受控计算仍在路线图范围内；没有将未实现组合改写成已完成。
真实 ArcGIS 服务结果、全页面交互与大轨迹容量尚未验收，本节点完成复选框保持开放。
