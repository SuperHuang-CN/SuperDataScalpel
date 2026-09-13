# Canvas `TRACK_FIND_DWELL` Processor 设计

## 1. 定位与审计结论

引入于 Canvas 4.16，仅 BATCH，有界 Point 轨迹输入。旧相邻距离连段实现不等价于 ArcGIS Find Dwell Locations；这不是仅缺高级参数，而是会造成误判的核心算法差异。

第 2 节保留 Canvas 4.20 审计快照。4.21 已新增可选 `boundaries.fixedTimeBoundary`：
正整数周期、日历/时长单位、参考时间和 IANA 时区；与原有 gap 独立生效，未启用时旧定义行为不变。
完整字段及默认值见[Canvas Definition](../canvas-task-definition.md)。Inspector 的边界 Modal 已提供配置入口。
4.22 已接入参考中心策略、四输出配置与前端解析；专项验收状态见进度清单，不作为官方等价声明。
以下目标参数与算法不能直接用于旧小版本 JSON；已接入的新契约和实际边界见第 7 节。

### 4.47 固定时长周

相邻时间 gap、最短驻留 `minimumDurationUnit` 和结果时长 `rangeOptions.durationUnit`新增“周（固定 7 天）”，每周为 604800 秒。
与已有按时区推进的日历周区分；切换单位不换算已填数值，隐藏草稿保留并参与 4.47 门槛。
固定月年不开放，阈值边界、节点算法与输出粒度不变；详见[共享时长规则](../canvas-spatial-units.md#447-固定时长周与日历周)。

### 4.36 公共单位补充

相邻距离边界、驻留距离阈值及平均距离输出使用公共距离单位。点级输出隐藏的平均距离单位仍保留且参与版本门槛。
单位名称明确国际制/美国测量制，切换不自动换算数值，旧单位结果不变。具体枚举、字段和证据见[公共空间单位](../canvas-spatial-units.md)；不代表整节点已完成官方对照。

### 当前阅读入口与对齐边界（4.45 汇总）

- **已接入范围**：4.22 的参考中心扩展及四类输出见第 7 节，4.35 字段统计见第 8 节。
- **官方参照与差异**：参照 GA Find Dwell Locations；旧相邻连段不等价，当前参考中心算法仍需官方测地、统计与边界对照。
- **面板修订要求**：按点级/驻留级输出切换字段；切换策略确认并保留草稿，不将未验收公式标为官方等价。

本摘要不替代逐版本契约。下节的“当前”均指 4.20 历史状态；目标线框不作为已实现截图。
参数覆盖、本地验证和官方结果对照分别登记，见[对齐验收规则](../canvas-spatial-analysis-processor-roadmap.md#7-对齐验收)。

## 2. 4.20 配置快照（历史）

```ts
type DwellGeometryKind = 'CENTROID' | 'CONVEX_HULL';

interface TrackFindDwellConfiguration {
  sourceTableName: string;
  pointGeometryColumnName: string;
  trackIdColumns: string[];
  timeColumnName: string;
  distanceMethod: SpatialDistanceMethod | null;
  distanceThreshold: number;
  distanceThresholdUnit: SpatialDistanceUnit;
  minimumDuration: number;
  minimumDurationUnit: SpatialDurationUnit;
  boundaries: TrackBoundaryConfiguration;
  summaryStatistics: TrackSummaryStatistic[];
  outputGeometryKind: DwellGeometryKind | null;
  outputTableName: string;
  dwellIdColumnName: string;
  startTimeColumnName: string;
  endTimeColumnName: string;
  durationColumnName: string;
  pointCountColumnName: string;
  outputGeometryColumnName: string;
}
```

- 当前按相邻点距离超阈值拆段，再按片段时长筛选；只有 CENTROID/CONVEX_HULL，每驻留段一行。
- 距离/最短持续时间为有限正数，汇总 0～32 项。boundaries 只有相邻时间/距离 gap，不包含官方固定时间边界。
- 当前凸包可能退化为点/线，通用 GEOMETRY 声明是平台形式，不等于官方面图层保证。

## 3. ArcGIS 目标语义与参数

| 官方参数 | 目标配置 / 当前差距 |
| --- | --- |
| inputLayer / trackFields | 来源 Point、Geometry、轨迹标识、时间字段；增加确定的同时间次序策略。 |
| distanceMethod / distanceTolerance / Unit | 平面/测地线、驻留距离容差+单位；不是“相邻步长上限”。 |
| timeTolerance / Unit | 最短驻留持续时间+单位，不能用点数代替。 |
| timeBoundarySplit / Unit / Reference | 单独的固定重置周期；与 maximumTimeGap 分开。 |
| summaryFields | 数值统计、非空字段 Count、Any、First/Last；4.35 补齐字段 Count/Any，公式与类型对照边界见第 8 节。 |
| outputType | DwellMeanCenters、DwellConvexHulls、DwellFeatures、AllFeatures；4.22 已接入四输出，实际边界见第 7 节。 |

必须替换的算法目标：

1. 按轨迹和时间处理观测，按照官方距离/时长条件形成候选；候选首点作参考点。
2. 向后扩展处于参考点距离容差内的连续观测，计算该候选的平均中心。
3. 检查当前候选之前和之后的观测，处于平均中心容差内时纳入；按官方描述继续处理剩余轨迹。
4. 在固定时间边界重置；同一观测归属、扩展边界、相等阈值和重复时间用官方对照样例确定。

不能以相邻点间距均小于 R 推出全部点处于驻留范围。例：每分钟向东 50m，共 20 分钟，
容差 100m、最短 10 分钟；当前会连成长段，目标不能将整段 1km 的移动标为一个驻留。

## 4. 目标 Inspector UI

设计状态：参考中心扩展与四类输出已在第 7 节（4.22）接入，字段统计见第 8 节（4.35）；旧相邻连段不是当前目标算法。真实官方结果对照仍待完成，四类输出必须按各自粒度显示字段，不能共用一个含糊的“驻留结果”配置。

```text
来源 / 点字段       [ person_events / location ▼]
轨迹标识 / 时间     [ person_id ][ event_time ]
同时间顺序          [ event_id ▼]                 (?)
距离方法            [ 平面 | 测地线 ]
驻留距离容差        [100][米 ▼]                    (?)
最短持续时间        [20 ][分钟 ▼]
固定时间边界        关闭                         [设置]
额外间隔拆分        未设置                       [设置]
输出类型            [均值中心 | 凸包 | 驻留点 | 全部点]
汇总字段            2 项                        [设置]
输出字段            ID · 时长 · 点数 · 位置      [设置]
输出表              [ person_dwells ]
```

距离帮助解释“参考点、平均中心与连续范围”，不再标为“空间半径”暗示仅画圆即可完成。
均值中心/凸包输出显示汇总设置；点级输出显示标记字段，不能把中心统计强行复制成同一结果模式。
打开旧相邻连段策略时需提示它与参考中心策略的差异；切换须确认并保留草稿。当前参考中心策略不再标为未实现，但不得标为“已与 ArcGIS 数值等价”。


### 参数交互与初值（目标设计）

下表是目标面板约定，不修改旧任务默认值；未明确标注为官方默认的初值均为平台推荐。

| 配置组 | 初值与条件显示 | 对齐边界 |
| --- | --- | --- |
| 识别语义 | 旧相邻连段与目标参考中心明确区分，切换需确认 | 新策略不继承旧策略的算法宣传 |
| 距离与持续时间 | 两项必填，不从数据分布猜值；单位同行 | 距离容差不是相邻点步长 |
| 输出类型 | 新建推荐均值中心；凸包、驻留点、全部点显式互斥 | 平台初值；四类输出均在对齐目标内 |
| 聚合输出 | 显示起止时间、时长、点数、平均相邻距离与汇总字段 | 时长默认毫秒对应官方结果；其他输出单位显式配置 |
| 点级输出 | 显示驻留 ID 和标记字段；隐藏但保留聚合配置 | 全部点中的非驻留观测需明确 NULL ID + false 标记 |

参考中心扩展还须确定中心是否更新、前向/后向连续性、是否复用观测及日期变更线处理；这些是算法验收项，不能由 UI 标签代替。未完成集成与对照时不得显示“ArcGIS 等价”。

## 5. 输出与验收

- 均值中心/凸包：每驻留一行，轨迹标识、ID、起止时间、持续时间、Count、均值位置、平均相邻距离和汇总字段。
- 驻留点/全部点：保留相应原始观测并追加归属标记；非驻留点表示方式单独定义，不伪造驻留结果。
- 官方 DwellDuration 为毫秒；平台如允许自选单位须显式配置并记录输出映射。
- 输出 BOUNDED，新表保留入口 Map，不输出 Streaming Watermark；中心/凸包结果按区间表达时间。
- 验收：缓慢长距离移动反例、静止抖动、候选前后扩展、恰等阈值、跨日界、孤立点、重复时间、退化凸包。
- NULL 时间/Geometry、单点凸包与观测重用边界在实现前明确策略；未实测前不写“与 ArcGIS 一致”。
- Canvas 可显示配置容差和最短时长，但不显示实体 ID 值、坐标或驻留统计结果。

## 6. 范围与官方依据

四类输出及候选范围算法属于本节点对齐目标，不能继续列为“首版永久不含”。
Streaming 状态、路网距离、迟到纠正和地点围栏关联不在本次目标中。

- [Find Dwell Locations REST](https://developers.arcgis.com/rest/services-reference/enterprise/geoanalytics/tasks/find-dwell-locations/)
- [共同规则与版本策略](../canvas-spatial-analysis-processor-roadmap.md)

## 7. 4.22 参考中心策略与实现边界

- 新增可选 `dwellSemantics` 与 `rangeOptions`，完整字段见[稳定定义](../canvas-task-definition.md#713d713g-track-节点)。
  缺失策略保持 LEGACY_ADJACENT；REFERENCE_CENTER 只接受有界 XY Point。新能力要求 4.22，节点引入版本仍为 4.16。
- 先按轨迹标识、片段、时间和次序排序。候选首点为参考点，收集距离 ≤ 容差的连续点；
  至少两点且首末时长 ≥ 最短时长才成为种子；失败时从下一点重新尝试。
- 计算种子均值中心并固定，在当前尚未归属的连续区间内向前、向后扩展；遇到超距离或缺 Geometry 即停止。
  不随每次扩展移动中心，不复用先前驻留成员。输出均值中心则根据扩展后的全部成员重新计算。
  这些扩展细节是平台明确选择，官方描述未穷尽的重用/平局仍需实际对照。
- NULL 时间不参与且不输出；NULL/Empty Geometry 不属于驻留并阻断连续范围，ALL_FEATURES 保留其原行并标 false。
  非 Point、非有限坐标、测地模式非法经纬度报安全 SCHEMA 错误。单点不成驻留，凸包可退化为点/线，Schema 为 GEOMETRY。
- 平面距离使用处理 CRS 单位换算，平均中心为算术均值；测地距离使用 WGS84 椭球距离，
  中心使用球面单位向量均值处理日期变更线，抵消到无法确定中心时报错。
  测地凸包采用以均值经度展开的局部经纬度凸包，再在 ±180° 切分并回绕；
  不能将跨日期变更线的小范围误画成横跨 Greenwich 的大面。它不是 Esri 椭球凸包实现的等价证明，极区/大范围仍须对照。
- MEAN_CENTERS/CONVEX_HULLS 输出起止时间、显式单位时长、成员数、平均相邻距离、汇总和 Geometry。
  平均相邻距离对成员按时间和次序计算（N 个成员有 N−1 段），不是到中心的平均半径。
  DWELL_FEATURES/ALL_FEATURES 保留原字段，追加 ID 与布尔标记；非成员 ID=NULL、flag=false。
- ID 基于结构化轨迹键、片段与成员序号生成摘要；在相同输入和明确次序下各输出模式共享归属身份，
  不保证追加、更改观测后 ID 不变，也不承诺等于 Esri ID。
- 惰性分区处理在 Executor 一次缓存一个轨迹片段，输出行按需复制；不会 collect 全表到 Driver，也不为预检触发 Action。
  超大单轨迹仍存在内存和候选反复扫描风险；固定边界会改变分析语义，不能为避免内存压力自动开启。
  大单组容量评估和可溢写方案仍在验收清单，不宣称仅因分布式就无容量限制。
- Inspector 切换策略需确认，点级/聚合字段条件显示且保留草稿。Canvas/Runner 仅显示模式、字段与数量，
  不显示坐标、轨迹身份值或任何计算结果。

## 8. Canvas 4.35 驻留字段统计

- `summaryStatistics` 新增 `COUNT_FIELD`、`ANY`，三端要求 4.35；结构、空数组和旧算法策略不变。
  缺省仍为空汇总数组，固定 pointCount 始终由聚合驻留结果输出，不隐式为全部字段创建统计。
  这是平台显式配置，与 REST 未提供 summaryFields 时的默认统计集合不同。
- COUNT_FIELD 统计当前驻留成员的非 NULL 字段值，重复值及空字符串计数，全 NULL 为 0；不做去重。
- 驻留 REST 的 Any 描述明确允许字符串和数值（与重建不同）。平台取一个非 NULL 样本并保留原字段类型，
  Decimal 精度/小数位不转字符串；官方文字同时使用“sample string”，最终数值返回类型仍需服务对照。
  全 NULL 为 NULL，不能当排序 FIRST，也不保证跨分区/重跑选中同值。
- FIRST/LAST 取首末观测，即使对应值为 NULL 也保留 NULL。参考中心策略使用已配置次序；
  旧相邻策略时间平局保持原边界，不通过采样替代明确次序。
- 均值中心和凸包对驻留成员统计；原始点/全部点继续忽略但保留隐藏汇总草稿，不把汇总字段复制到每个点。
  隐藏的新类型仍受版本门槛约束。无驻留或空输入不虚构统计行。
- SUM/MEAN、Decimal 等输出按 Spark Analyzer 结果回填逻辑 Schema，修正原来的类型提升不一致，
  不改变既有数值算法；STDDEV/VARIANCE 的样本分母与官方边界仍待对照。
- 统计弹窗采用独立副本、取消/保存草稿、字段恢复、排序、删除确认和错误保留；
  UI 结构与[重建统计面板](track-reconstruct.md#9-canvas-435-字段-countany-与统计草稿)相同，Any 候选额外允许数值。
  Canvas/日志不展示 Any 样本、轨迹身份或统计结果，不增加真实预读、Action 或缓存。
- 本轮不等于完整驻留验收；官方距离/中心细节、时区/单位、单轨迹容量和完整字段血缘仍须继续处理。

## 9. 当前明确支持范围收口（2026-09-13，Canvas 4.76）

- `LEGACY_ADJACENT` 旧相邻连段和 `REFERENCE_CENTER` 参考中心两种策略均继续支持；新建节点推荐参考中心，
  切换策略需确认且保留另一策略的草稿。参考中心候选、固定中心扩展和成员不复用规则仍按第 7 节的平台明确语义执行。
- `MEAN_CENTERS`、`CONVEX_HULLS`、`DWELL_FEATURES`、`ALL_FEATURES` 四类结果均已贯通。
  聚合结果支持固定轨迹/驻留/起止/时长/点数/平均距离字段，以及 `COUNT`、`COUNT_FIELD`、`SUM`、`MEAN`、
  `ANY`、`FIRST`、`LAST`；点级结果保留原字段并追加驻留 ID 与布尔标记。
- 平面距离和受控 EPSG:4326 XY 测地距离继续有效；测地中心采用球面单位向量均值，日期变更线凸包采用局部展开、
  切分和回绕。相邻 gap、固定日历/时长边界、NULL 时间、NULL/Empty Geometry、阈值相等及退化凸包均有明确处理规则。
- 四类结果均已验证为 `FIELD_COMPLETE`。点级原字段保持 `DIRECT`；驻留 ID、统计、时间、距离和 Geometry 等派生字段
  都能追溯到真实输入字段。驻留归属的 `mapPartitions` 只通过显式、受控的“同名字段透传 + 新字段来源声明”边界恢复血缘，
  未标记的普通不透明行变换仍保持部分血缘。
- 20,000 点单轨迹样例在分析阶段不触发 Spark Job，点级结果保持一条观测一行；计划不含 Driver `CollectLimit`
  或 `collect_list`。实现仍会在 Executor 一次缓存一个完整轨迹片段，候选扩展也可能反复扫描该片段，
  因而不能据此宣称无限单轨容量或生产容量等价。

当前收口表示平台契约、主要边界、字段血缘、惰性计划和真实 Inspector 已形成可用闭环，不表示与 ArcGIS Enterprise
服务完全等价。尚不承诺 Esri 官方测地中心/凸包公式、官方字段名和统计返回类型、服务端容差与数值、
跨实现驻留 ID、生产容量或 Streaming 行为完全一致。
