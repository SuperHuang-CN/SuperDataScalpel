# Canvas `SPATIAL_SUMMARIZE_WITHIN` Processor 设计

## 1. 定位与审计结论

引入于 Canvas 4.12，仅 BATCH，区域表+被汇总表。4.24 增加显式总量分摊、原值地理加权均值及字段统计；4.25 增加可选主表/关联组表及形状组占比；4.37 增加原值交叠比例加权方差/标准差；4.41 增加规则格网区域。当前明确支持范围已按第 12 节收口；真实 ArcGIS Enterprise 作业、官方矛盾算例及任意规模容量仍未完成对照，不宣称完整等价于 GA Summarize Within。

本文区分 4.20 审计快照与后续目标设计；快照不是当前完整契约。开发状态以[进度清单](../canvas-spatial-development-progress.md)为准，目标参数和 UI 不代表已经可用。4.24 的局部实现见第 7 节，不改变旧任务语义。

### 4.47 固定时长周

固定切片的 `temporalSlicing.intervalUnit/repeatIntervalUnit`新增“周（固定 7 天）”，每周为 604800 秒。
与已有按时区推进的日历周区分；切换单位不换算已填数值，隐藏草稿保留并参与 4.47 门槛。
固定月年不开放，阈值边界、节点算法与输出粒度不变；详见[共享时长规则](../canvas-spatial-units.md#447-固定时长周与日历周)。

### 4.36 公共单位补充

长度与面积单位分别扩充国际码/平方码和美国测量制。最终单位换算不改变裁剪比例、分摊权重或分组比例。
单位名称明确国际制/美国测量制，切换不自动换算数值，旧单位结果不变。具体枚举、字段和证据见[公共空间单位](../canvas-spatial-units.md)；不代表整节点已完成官方对照。

### 当前阅读入口与对齐边界（4.45 汇总）

- **已接入范围**：第 7～11 节分别定义分摊统计、关联分组、加权方差、日历窗口和格网区域。
- **官方参照与差异**：GA Summarize Within 的参数范围与平台实现分开登记；官方统计文案/公式矛盾及边界裁决尚需对照。
- **面板修订要求**：按总量/原值选择统计；主表和关联组表分别命名，不以一个模糊“加权”开关覆盖全部公式。

本摘要不替代逐版本契约。下节的“当前”均指 4.20 历史状态；目标线框不作为已实现截图。
参数覆盖、本地验证和官方结果对照分别登记，见[对齐验收规则](../canvas-spatial-analysis-processor-roadmap.md#7-对齐验收)。

## 2. 4.20 配置快照（历史）

```ts
type SpatialWithinStatisticKind =
  | 'COUNT'
  | 'SUM'
  | 'MEAN'
  | 'MIN'
  | 'MAX'
  | 'RANGE'
  | 'STDDEV'
  | 'VARIANCE'
  | 'LENGTH_WITHIN'
  | 'AREA_WITHIN';

interface SpatialWithinStatistic {
  statisticId: string;
  kind: SpatialWithinStatisticKind;
  sourceColumnName: string | null;
  outputColumnName: string;
}

interface SpatialGroupSummary {
  groupByColumnName: string;
  includeMinorityMajority: boolean;
  includeGroupPercentage: boolean;
  minorityFlagColumnName: string | null;
  majorityFlagColumnName: string | null;
  groupPercentageColumnName: string | null;
}

interface SpatialSummarizeWithinConfiguration {
  areaTableName: string;
  areaGeometryColumnName: string;
  summaryTableName: string;
  summaryGeometryColumnName: string;
  includeEmptyAreas: boolean;
  distanceMethod: SpatialDistanceMethod | null;
  lengthUnit: SpatialDistanceUnit;
  areaUnit: SpatialAreaUnit;
  areaOutputColumns: JoinOutputColumn[];
  statistics: SpatialWithinStatistic[];
  groupSummary: SpatialGroupSummary | null;
  temporalSlicing: SpatialTemporalSlicing | null;
  outputTableName: string;
}
```

- 当前 COUNT 为命中行数，非 COUNT(field 非空)；statistics=1～32。
- LENGTH_WITHIN/AREA_WITHIN 测量相交片段；其他标量统计直接使用来源值，不做按交叠比例分摊。
- 当前分组粒度为区域+时间窗（如有）+组值；少数/多数和百分比都按命中行数比较，线/面不按形状量。
- includeEmptyAreas 是平台扩展；当前空区域产生 group=null 占位，时间切片只为来源实际窗口补齐。
- 当前主表/关联分组表没有独立输出配置。

## 3. 对齐目标：统计含义必须拆开

| 官方能力 | 目标参数 / 行为 |
| --- | --- |
| summaryPolygons 或 binType/Size/Unit | 区域层与格网二选一；也可显式前置格网生成后作为区域输入。当前点聚合格网节点不能替代“为线/面生成汇总格网”。 |
| standardSummaryFields / weightedSummaryFields / rateFields | 每项明确统计函数、字段数量含义（总量/率值）、分摊方式、加权方式和输出字段。 |
| sumShape / shapeUnits | 点数、区域内线长/面积，长度/面积单位独立，按类型收敛。 |
| groupByField | 独立主区域结果与关联组统计表，以稳定区域键关联。 |
| minorityMajority / percentShape | 主表少数/多数的组值和比例，组表每组比例；点按点数、线按相交长度、面按相交面积。 |

### 官方文案差异与设计裁决

Enterprise 11.3 Portal 将“标准统计”描述为不加权，将“加权统计”描述为按相交比例处理；
REST 则明确区分 standardSummaryFields、weightedSummaryFields、rateFields，并说明总量可先分摊。
REST 的部分段落甚至误写比例的分母为“外部”，Portal 数值例子实际用的是**整个来源要素**。

因此不能只给一个含糊的“加权”开关。目标语义分两步显式配置：

1. 总量分摊：p=相交长度或面积/整个来源长度或面积，x'=p·x；率值/指数不分摊，x'=x。
2. 统计：普通统计对 x' 计算；地理加权 Mean 等按明确权重计算。
   若平台显式允许“分摊后再加权”，公式为 Σ(p·x')/Σp，总量字段会出现二次比例作用；这是一个需要单独确认的组合，
   **不默认启用，也不把它直接映射成 ArcGIS weightedSummaryFields**。官方各统计组合的实际分摊顺序仍须用数值案例核实。
   可用函数和是否适用按所选模式限制；点不提供形状加权。
3. 同时保留“不分摊的原值统计”以表达 Portal 常见标准统计；UI 给出公式和官方参数映射，
   不用默认值掩盖两套表述的差别。
4. 加权方差/标准差不能直接等同 Spark var_samp。4.37 已按官方公式图实现，明确有效观测数与缺失值规则；
   该页示例数值与公式存在矛盾，见第 9 节，真实服务对照仍标“待对齐”。

总量 100 的面有一半在区域内：分摊后的总量为 50；率值 100 不分摊仍为 100。
两条线长分别 9km、1km、各属一组：组占比是 90%/10%，不能为 50%/50%。

### 输出目标

- 主表：每区域一行，区域属性、总体统计、少数/多数组值及比例。
- 组表：每区域+组值一行，区域稳定键、组值、组统计、比例，不重复复制完整区域 Geometry。
- 时间切片是平台组合/扩展；启用时上述粒度均加窗口，不能宣传为 Summarize Within REST 的同名参数。
- 扁平分组结果可以作为显式平台模式保留，但不称作官方主表等价。
- 平局、NULL 分组、边界点、重叠面、零面积/长度和空区域都要明确；不把当前“平局全 false”作为官方规则。

## 4. 目标 Inspector UI

设计状态：区域层/规则格网、显式统计权重、主表/组表及时间窗口已有分节实现说明（第 7～11 节）。这些选项不再统一标为待开发；尚未完成的是官方数值/边界对照及规模验收，不能显示“完全对齐”。

```text
汇总区域            [区域层 | 规则格网]
区域 / Geometry     [ districts / boundary ▼]
被汇总 / Geometry   [ roads / centerline ▼]
形状统计            [✓总长度][千米 ▼]
统计项              3 项                          [设置]
字段                 函数    数量含义  分摊  加权
traffic              SUM     总量      是    否
speed_limit          MEAN    率值      否    是
分组字段            [ road_type ▼]
分组结果            [✓少数/多数][✓比例]
主结果表            [ district_summary ]
组统计表            [ district_summary_groups ]
空区域              [保留]（平台扩展）             (?)
```

统计项在 860px Modal 中用紧凑表格配置；公式随模式显示于帮助 Popover。
关联结果模式且有分组时才显示组统计表名，名称冲突即时标红。分摊与多表模式按第 7、8 节的显式配置和版本门槛开放，不能静默改变旧扁平结果。
时间切片放高级设置；空间测量方式/CRS 差异显示紧凑提示，官方 Portal 测量说明采用测地距离。


### 参数交互与初值（目标设计）

下表是目标面板约定，不修改旧任务默认值；未明确标注为官方默认的初值均为平台推荐。

| 配置组 | 初值与条件显示 | 对齐边界 |
| --- | --- | --- |
| 汇总区域 | 区域层 / 规则格网互斥；只显示当前方式需要的字段 | 格网汇总需独立具备线/面支持，不能借点格网近似 |
| 标量统计 | 每项显式选择原值或总量分摊，再选择支持的统计方式；不猜字段是总量还是率值 | 官方文案差异未核定前不设“ArcGIS 加权”快捷默认 |
| 形状统计 | 根据点/线/面显示计数、长度或面积与对应单位 | 分组比例与形状量使用同一测量语义 |
| 分组结果 | 分组默认关闭；启用后显示主表与关联组表名及关联键 | 不是一张扁平表上追加布尔标记 |
| 空区域 | 新建推荐保留，并标为平台选择；组表无真实分组时零关联行 | 不伪造 NULL 组成员 |

未实现的统计组合禁用并解释；切换分摊/分组只改变生效配置，不删除编辑草稿。主表与组表的字段设置分别展示预期行粒度。

## 5. 验收与执行边界

- 同一条入边可带两张表，节点追加显式新表，保留原 Map；任一配置无效不传播部分结果。
- 预检只构建惰性计划；运行阶段空间 Join/相交与聚合，不为摘要额外读数。
- 区域表模式使用 `ST_Intersects`：位于共边上的 Point 会分别进入每个相交区域；区域互相重叠时，同一来源要素由各区域独立汇总，不做跨区域唯一归属或去重。
- 验收：点计数与字段非空 Count、字符串 Any、100→50 总量分摊、率值、地理加权均值、
  线组 90/10、主表/组表关联、重复区域业务属性但不同区域行、空/零测度/重叠及平局。
- 主结果不得只保留一行/组却声称一行/区域；区域键不可用易重复的名称代替。
- Canvas 展示表名、统计/分组数、模式和输出表，不显示组值、区域范围或统计数据。

## 6. 官方依据

- [Summarize Within REST](https://developers.arcgis.com/rest/services-reference/enterprise/geoanalytics/tasks/summarize-within/)
- [Enterprise 11.3：统计公式与数值案例](https://enterprise.arcgis.com/en/portal/11.3/use/geoanalytics-summarize-within.htm)
- [Aggregate Points REST](https://developers.arcgis.com/rest/services-reference/enterprise/geoanalytics/tasks/aggregate-points/)
- [共同规则与版本策略](../canvas-spatial-analysis-processor-roadmap.md)

## 7. 4.24 显式统计处理（局部实现）

每项追加可选字段，旧四参数 Java 构造器保留，节点引入版本仍为 4.12：

```ts
valueTreatment?: 'ORIGINAL_VALUE' | 'APPORTION_TOTAL' | null;
weighting?: 'NONE' | 'INTERSECTION_FRACTION' | null;
// SpatialWithinStatisticKind 另外新增 COUNT_FIELD、ANY
```

缺失/null 时是原值、不加权，旧定义不补填策略。不改变 Manifest、Task Result、HTTP API。
显式设置任一策略（包括 ORIGINAL_VALUE/NONE）或使用新函数均要求 Canvas 4.24。

### 公式与有效组合

- 原值：普通聚合直接使用 x，率值/指数无需分摊；仍可明确选择不分摊的总量字段。
- 总量分摊：仅线/面；p=区域内相交长度或面积/整个来源长度或面积，x′=p×x。
  支持 SUM/MEAN/MIN/MAX/RANGE/STDDEV/VARIANCE；后两者仍采用普通样本方差/标准差，对 x′ 计算，
  不冒充官方 weightedSummaryFields 的方差。点使用原值，混合 Geometry 不推测形状量。
- 4.24 原值交叠比例加权仅开放线/面的 MEAN，Σ(p×x)/Σp；权重不是相交片段的绝对长度/面积。
  继续拒绝“分摊后再加权”，防止二次比例作用。4.37 新增 VARIANCE/STDDEV，见第 9 节，不改变旧均值公式。
- 使用同一个显式平面/测地线方法测量分子和分母，无隐式投影。GEODESIC 仍要求 EPSG:4326 XY。
  比例无单位，截断浮点误差到 [0,1]；不受输出长度/面积显示单位影响。
- 来源零测度/非有限测度时比例为 NULL；只有边界接触、交叠测度为零时比例为 0。
  分摊的数值 NULL/NaN/Infinity 转为 NULL；加权均值排除无效数值或非正权重，且同时从分子和分母排除。
  没有有效权重时结果为 NULL，不返回 0、不除零。原值统计保持 Spark 既有值语义。
- `COUNT` 仍是命中要素数，`COUNT_FIELD` 为所选标量字段非空值数量；`ANY` 仅接受字符串，
  返回任意非空样本，不保证重跑或分区变化后的样本选择顺序。空区域两类计数为 0、ANY 为 NULL。
- 无效配置在构造结果前整体拒绝；统计项仍最多 32 个。计划全惰性，无新增 Action、缓存或外部访问。

### Inspector 与安全

- 860px 统计 Modal 改为紧凑 Table：函数、来源、数量处理、加权、输出字段、排序/删除。
  公式放邻近 ContextHelp；不可用选择禁用，已保存失效组合继续显示并标红，允许应用草稿。
- 改函数或模式不清除字段和其他模式配置；不使用来源字段时允许显式清空。
  删除统计项需要确认。未打开弹窗仍保留全部统计字段及模式。
- Canvas 与安全摘要仅显示统计/分摊/加权数量，不记录原值、组值或统计数据。
  内部别名避开两侧输入及用户配置的输出名，不覆盖同名业务字段。

新增错误：`SPATIAL_WITHIN_STATISTICS_REQUIRE_SCHEMA_VERSION`、
`SPATIAL_WITHIN_STATISTIC_COMBINATION_UNSUPPORTED`、`SPATIAL_WITHIN_SHAPE_WEIGHT_UNSUPPORTED`。
字段必填、存在性、UUID、重名和类型复用既有校验；ANY 非字符串使用 `STRING_COLUMN_REQUIRED`。

4.24 阶段尚缺的主表/组表及形状组比例已在 4.25 局部实现，见第 8 节；公共距离/面积单位见 4.36，加权方差/标准差见第 9 节。规则格网区域仍待实现。
本节的公式和本地样例不能替代官方服务对照；第 3～5 节完整目标保持不变。

## 8. 4.25 主表与关联组表策略

配置追加可选 `groupResult`，旧构造器保留，Manifest/Result/HTTP API 不变：

```ts
groupResult?: {
  mode?: 'LINKED_TABLES' | 'LEGACY_FLAT' | null;
  areaKeyColumnName: string;
  areaKeyOutputColumnName: string;
  outputTableName: string;                 // 关联组表，主表仍用节点 outputTableName
  groupValueColumnName: string;
  minorityValueColumnName: string | null;
  majorityValueColumnName: string | null;
  minorityPercentageColumnName: string | null;
  majorityPercentageColumnName: string | null;
} | null;
```

### 激活与兼容

- 缺失/null 保持原有扁平分组。对象存在且 mode 缺失/null 或 LINKED_TABLES 时，启用 groupSummary 后执行双表策略。
- LEGACY_FLAT 显式使用旧扁平统计，同时保存整份关联配置；禁用 groupSummary 时关联配置也保留但不生效。
- 新建节点初始化 LINKED_TABLES 配置草稿，分组仍默认关闭；区域键和组表名不猜填。
  任意非空 groupResult 对象（即使非活动）要求 Canvas 4.25。
- 不改变旧版按要素行数的组比例或平局全 false 语义。保存旧定义不自动生成关联配置。

### 区域身份与表关系

- 用户选择区域表中的一个非空唯一标量键，不从区域名称/属性组合猜测，不使用跨计划不稳定的临时行号。
  这是平台可靠关联所需的配置，不宣称 ArcGIS 有同名参数。
- 主表包含显式键、所选区域属性、总体统计；组表包含相同类型的键、组值和逐组统计，不重复复制区域 Geometry。
  一个上游端口仍传递整个 Map，结果顺序为原输入表 → 主表 → 组表。
- 键在 Spark 惰性窗口计划中检查非空和唯一；读取参与输出的区域时失败使用安全 `SPATIAL_WITHIN_AREA_KEY_INVALID`。
  不增加查询数据库、Driver collect、缓存或额外 Action。两张结果独立消费，来源变化时不承诺跨输出快照事务。
- 同名区域属性不会合并不同键的区域。组表的真实 NULL 组保留；空区域不会生成虚假 NULL 组行。
  保留空区域时主表计数为 0、其他没有测量值的统计为 NULL。
- 时间切片仍是平台组合能力：主表粒度为区域键+窗口，组表为区域键+窗口+组值，关联时使用键与窗口起止。
  空区域只补来源中实际出现的时间窗，不凭空构造时间范围。
  4.34 共享固定时长切片支持重复间隔大于窗口长度的留空模式；空隙/NULL 时间在区域窗口补齐前排除。
  重叠窗口只展开一次再提取起止，修复原先分别展开导致的无效起止组合与重复统计。
  这是平台组合能力，不新增 Summarize Within REST 同名参数；4.39 另以显式模式增加日历月/年与按时区的日历日，见第 10 节。
- 所有配置校验与两张表 Schema 分析均在传播前完成；任一表名/字段名冲突或配置错误均不传播部分结果。

### 形状比例与少数/多数

- Point 按区域内点数，MultiPoint 按相交部分的点分量数量；线/多线按相交长度，面/多面按相交面积。
  COUNT 仍计来源要素行数，与 MultiPoint 分量数明确区分。混合通用 Geometry 不猜测测量类型。
- 组比例为组形状量 / 所有组形状量之和 × 100；重叠来源分别计量，不先 Union，不以区域面积作分母。
  选择的平面/测地方法与测量统计一致。比例无单位，不随显示单位切换改变。
- 边界相交但测量量为 0 的线/面仍可参与普通统计，组比例在总量为正时为 0；所有组总量为 0 时比例为 NULL。
- 少数/多数在正形状量组中选择；没有正形状量时主表组值和比例为 NULL。
  平局按原组值升序选一个、NULL 排在最后，输出 `SPATIAL_WITHIN_GROUP_TIES_USE_ORDER` 提醒；这是平台策略，官方细节仍需对照。
- 真正的 NULL 组可以参与比较；唯一获选 NULL 组的组值为 NULL、比例仍为该组真实比例。
  主表总均值等直接对全体匹配要素聚合，绝不通过“各组均值再平均”产生错误结果。

### 配置与展示

- 分组 Modal 为 680px；可确认切换“主表 + 关联组表 / 旧版扁平”，切换不清除两套字段配置。
- 双表模式直接显示区域唯一键、关联键输出字段、组字段/别名及组表名；少数/多数组值和比例字段按开关显示。
  缺失键/失效字段/表名冲突就地标红，业务错误仍允许保存草稿；取消 Modal 不提交本次改动。
- Canvas 展示主表、组表和双表模式，动态增加基础高度；安全摘要仅增加双表模式和结果数量，不记录组值、键值或统计数据。

新增配置错误：`SPATIAL_WITHIN_GROUP_RESULT_REQUIRE_SCHEMA_VERSION`、`SPATIAL_WITHIN_GROUP_SHAPE_UNSUPPORTED`；
复用必填、字段不存在、表名/字段名冲突等稳定问题。唯一键错误为运行时 SCHEMA / 不重试。
本节记录 4.25 时的边界；规则格网区域后续已在第 11 节（4.41）接入，日历切片见第 10 节（4.39）。并列、NULL、退化与大型数据容量的官方对照仍未完成。

## 9. 4.37 原值交叠比例加权方差 / 标准差

### 配置与版本边界

复用每项已有字段，不另建统计类型或第二套 Operator：

```ts
kind: 'VARIANCE' | 'STDDEV';
valueTreatment: 'ORIGINAL_VALUE' | null; // 缺失同样表示原值
weighting: 'INTERSECTION_FRACTION';
```

这两种函数与该加权方式的组合要求 Canvas 4.37。低版本由 Business、Compiler 和前端导入一致返回
`SPATIAL_WITHIN_WEIGHTED_DISPERSION_REQUIRE_SCHEMA_VERSION`，定位到 `configuration.statistics[i].weighting`。
原值普通样本方差、分摊值普通样本方差和原值加权均值均保持既有语义；旧有效定义不自动启用新组合。
仍只适用明确线/面及对应 Multi 类型；点、混合 Geometry 和“总量分摊后再加权”继续拒绝。
Manifest、Task Result 和 HTTP API 不变，无新增生产依赖。

### 公式、缺失值与执行

对每一个区域（及当前时间窗/分组），先计算：

```text
pᵢ = 相交长度或面积 / 整个来源长度或面积
μ  = Σ(pᵢ xᵢ) / Σpᵢ
n  = 当前统计字段的有效正权重观测数
V  = Σ[pᵢ (xᵢ − μ)²] / [((n − 1) / n) × Σpᵢ]
SD = sqrt(V)
```

- x 或 p 为 NULL、非有限值，或 p≤0 时，该观测同时从分子、权重和及 n 排除；不是只过滤分子。
- n 是有效记录数，不是 Σp、COUNT 的相交要素数或所有字段共用计数。n<2 返回 NULL；相同有效值返回 0。
- 分母不是 `Σp − Σp²/Σp`，不使用 Spark 原始 `var_samp(x)`；等权时退化为普通样本方差。
- 使用可合并的加权 Welford/Chan 中心矩；每组仅保存 count、weight、mean、m2 四个标量。
  不以平方和相减计算，不收集整组到 Driver，不新增 Action、缓存或外部预读。溢出产生非有限方差时返回 NULL。
- 主表和组表分别对其原始匹配集合聚合，不以组方差的平均值代替主表方差。输出为可空 Double；原 Map 保留。
- 权重比例沿用同一平面/测地方法、CRS/维度校验和 [0,1] 浮点截断；长度/面积输出单位不改变权重。

### 官方证据与已知矛盾

采用 Enterprise 11.3 Portal 的[通用加权标准差公式图](https://enterprise.arcgis.com/en/portal/11.3/use/GUID-15022E2E-F299-4623-8BC9-1DA1BDAB712C-web.png)、
[有效权重个数说明](https://enterprise.arcgis.com/en/portal/11.3/use/GUID-26D545E4-1994-4952-B7FA-C06DD363397B-web.png)
与[线要素方差代入式](https://enterprise.arcgis.com/en/portal/11.3/use/GUID-6AE28E11-2B41-4370-992E-8E723B20DBE8-web.png)。
公式图需要直接查看，不能以未包含图片内容的 HTML 摘要代替核对。

该页线要素示例 x=[1000,600]、p=[1/2,2/3]：公式得到 `V=3840000/49≈78367.34694`，
而页面文字写为 `1268571.4286`，后者等于只保留加权平方和项、遗漏均值修正项的结果。
因此本实现跟随公式，不复制文字算例的矛盾数值；没有真实 ArcGIS 服务验收前，不声称数值完全等价。
上述 NULL/非有限/退化处理是平台明确规则，也须单独进行服务对照。

### Inspector、血缘与安全

- 在同一紧凑统计 Table 中为 VARIANCE/STDDEV 开放交叠比例加权；公式和对齐边界放邻近帮助，不增加常驻说明块。
- 统计 Modal 使用独立草稿；打开时复制当前项，完成才提交，取消/关闭不修改 Inspector。无效组合仍保留并可保存草稿。
- 加权统计值血缘包含数值字段与两侧 Geometry；主表、关联组表各自生成聚合边。分析不触发数据读取。
- 区域唯一键窗口计数显式引用键字段；语义仍是非空且唯一，不因血缘分析改变键校验结果。
  少数/多数内部 ROW_NUMBER 的依赖来自分区和排序字段；未知来源仍保留部分血缘诊断，不伪造完整覆盖。
- Canvas 与 Runner 只记录统计项/分摊/加权数量，不显示原值、计算结果或组值。

本阶段本地验证记录见[开发清单](../canvas-spatial-development-progress.md)；规则格网、官方服务与规模验收仍保留待完成。

## 10. 4.39 共享日历时间窗口

与 Bins 复用可选 `temporalSlicing.calendar`，显式选择固定时长/日历周期；根窗宽和重复数值共用，
单位两模式独立保留。日历日/周/月/年按时区推进，不用 30/365 天代替月年；同族起止锚定原始参考时刻。
旧固定 DAYS=24h 保持不变；非 null 对象要求 4.39，即使处于 FIXED_DURATION 模式。

时间设置 Modal 的 UI、默认值、DST、混合单位及稳定错误见[共享日历窗口](../canvas-spatial-calendar-windows.md)。
Inspector 常驻一行摘要，模式切换确认，取消不修改主配置；失效业务草稿可保存。Canvas 只显示模式，不展示参考时间。

主表和关联组表继续使用区域身份与窗口起止关联，每个窗口内单独汇总，不平均组级结果；空区域只补实际观测窗口，
不会补无来源月份或虚构分组。窗口展开仅一次，不新增 Action/缓存或真实时间范围读取。
单观测候选窗口超过 4096 安全失败，不截断；未知血缘仍保持部分覆盖，不通过放宽来源保护追求完整标记。
日历是平台组合能力；未完成真实 ArcGIS 服务、全页面及规模对照，不因本地专项通过宣称整个 Within 已对齐。

## 11. 4.41 规则格网汇总区域

### 配置与兼容

新增可选字段，不改变节点引入版本 4.12，也不升级 Manifest、Result 或 HTTP API：

```ts
regions?: {
  mode: 'AREA_TABLE' | 'PLANAR_GRID' | null;
  binShape: 'SQUARE' | 'HEXAGON' | null;
  binSize: number | null;
  binSizeUnit: SpatialDistanceUnit | null;
  planarGrid: SpatialPlanarGridOptions | null;
  binIdColumnName: string;
  binGeometryColumnName: string;
} | null;
```

- 缺失/null 继续读取原区域表，不自动更换来源。任何非 null 对象要求 4.41，包含 AREA_TABLE 下未使用的格网草稿。
- 区域表名、区域 Geometry、区域字段投影、groupResult 原区域键保留。GRID 模式不校验/执行这些非活动区域引用。
  `mode=null`、空半径/原点/输出名等业务草稿可保存，Compiler 返回精确配置问题；非法对象、枚举、非有限数值仍拒绝。
- 首次打开格网配置推荐方格、原点 0/0、米、数据范围、输出 `bin_id/bin_geometry`；大小留空，不猜业务分辨率。
  六边形大小始终为对边距离；边长内部为 `size/√3`，没有隐含旧边长模式。H3 不在 Within 本阶段范围中。
- `planarGrid` 复用明确原点和 DATA_BOUNDS/EXPLICIT_BOUNDS 结构。原点/范围坐标使用来源投影 CRS，切换显示单位不换算已填坐标或大小。
  不隐式转换 CRS；地理 CRS 不能生成平面格网。若需测地输出测度，应先按相关节点的明确 CRS 能力处理，不悄悄改算法。

### 真实区域与统计

- 一张被汇总表即可执行：按其空间包络或显式范围在 Spark 中生成 Polygon 区域，然后复用同一 Within Operator 的空间匹配、相交、统计与分组计划。
  点、线、面均保持原 Geometry，不以质心、包络或顶点代替真实统计对象；源 Map 保留，内部区域表不向下游传播。
- 方格原点是 (0,0) 格子的左下角，六边形原点是中心；方向固定。复用 Bins 的几何及点索引公式，ID 由形状/CRS/大小/原点哈希与格子索引组成。
  同一格子的 ID 不因范围、分组或时间窗变化而变化；修改格网定义会改变身份。
- GRID 主表固定投影配置命名的格网 ID、Geometry 与统计字段。双表模式自动用生成的格网键关联，仍由用户配置关联键输出名和组表名；原区域键不被覆盖。
  重复输出名称仍返回稳定错误，不静默改名。Map 顺序仍是原输入 → 主表 → 关联组表。
- Point 按与 Bins 一致的唯一索引归属；方格为 floor、六边形为立方体舍入，恰在边界的点也只计一次。
  MultiPoint、线、面沿用区域相交规则，可命中多个区域；共边线或 MultiPoint 边界分量可能参与相邻区域。
  这些边界裁决明确记录为平台行为，未完成官方服务数值对照，不能宣传与 GA 边界行为完全相同。
- 总量分摊分母始终是完整原要素的长度/面积，分子是当前格网内的相交量；跨两个等面积格子的 100 总量各为 50。
  沿用已有 NULL、零测度、原值/分摊、加权、组形状占比与时间切片规则，不把格网结果再平均一次。

### 范围与执行边界

- DATA_BOUNDS 由真实参与要素的包络惰性推导；空输入不虚构范围。指定范围可在无数据时生成空格网。
- 显式范围对 Point 按 `[min,max)` 筛选，对其他类型按与矩形相交筛选整条要素；不裁剪来源 Geometry，避免改变分摊分母。
  输出为与范围有正面积相交的完整格网，不裁剪格子形状；退化数据范围用相交判断，Point 的实际归属格子始终保留。
  因此部分格子可超出范围，已入选线面的格内统计也可能包含范围外部分；范围筛选不等同几何裁剪。
- 保留空区域只补生成的区域范围；有时间切片时仅补参与要素实际出现的窗口，不虚构月份或组记录。
- 生成前限制 100 万候选格子及可靠数值索引范围；显式范围可静态报错，数据相关超限在真实计算时安全失败，不截断输出。
  时间/分组可能继续增加结果行数；此为安全上限，不代表已完成大规模性能验收。
- 不新增 Driver collect、外部预读、缓存或观测 Action。Compiler 与 Runner 通过同一 Operator 构造惰性计划。
  GRID 的区域行身份直接使用稳定格网 ID，不用临时行号；数据范围生成的 Geometry/ID 血缘依赖来源 Geometry，常量范围不伪造物理区域资产。
- 新增 `SPATIAL_WITHIN_REGIONS_REQUIRE_SCHEMA_VERSION`、`INVALID_SPATIAL_WITHIN_GRID`；大小、原点、范围、表名/字段名复用已有错误。
  `SPATIAL_GRID_GEOMETRY_INVALID` 是运行时 SCHEMA / 不重试，仅安全摘要，不包含坐标、WKT 或原始数据。

### 当前 Inspector 与 Canvas

```text
汇总区域          [区域表 | 规则格网 ▾] (?)
方格 · 边长       100                         [设置]
被汇总要素        [ roads ▾]  Geometry [ geom ▾]
保留空区域        [开]
统计 / 分组 / 时间  沿用既有紧凑配置
主结果表          [ road_grid_summary ]
```

- 区域来源切换需确认，取消不改变配置；两套来源字段均保留。规则格网时不要求选择不存在的区域表，也不显示区域字段投影编辑器。
- 格网设置为独立 680px Modal：形状、大小/单位、原点、范围方式、按需范围坐标、输出 ID/Geometry 字段。
  取消丢弃本次编辑，保存可保留无效业务草稿；范围模式切换不删除隐藏坐标。帮助放 ContextHelp，不用常驻大段说明。
- 双表设置直接提示使用自动格网键，不模拟一张上游区域 Schema；组字段候选仍只用 Compiler 的真实 summary 输入字段。
- Canvas 显示方格/六边形区域、被汇总表、统计/分组/窗口状态和输出表；不展示原点、范围坐标、组值或统计数据。
  动态尺寸从当前配置计算；只读、克隆和导出沿现有 Spec/Parser 路径使用同一可选对象。

该能力对应 GA `binType/binSize/binSizeUnit` 的规则格网场景；原点、稳定键、安全上限及范围边界是平台明确策略。
官方结果、共边/多点边界及 Enterprise 容量验收继续开放，不将本节本身描述为官方结果完全等价。

## 12. 当前明确支持范围收口（2026-09-13，Canvas 4.76）

- 区域 Geometry 明确要求 Polygon/MultiPolygon；两侧 CRS 和坐标维度必须一致。平面模式沿用来源 CRS，测地测量要求双方均为 EPSG:4326 XY，不接受双方同为 XYZ/XYM 的定义。
- 区域表模式以 `ST_Intersects` 为匹配语义：共边 Point 会被相邻区域分别统计；重叠区域各自汇总同一来源要素，不自动选择唯一归属或跨区域去重。规则格网 Point 仍使用第 11 节的唯一格网索引裁决。
- `includeEmptyAreas=true` 不再执行无法索引的空间 `LEFT OUTER JOIN`。真实命中使用 Sedona 空间 `INNER JOIN`，未命中区域或区域窗口使用等值 `LEFT ANTI JOIN` 补齐，再构造类型一致的空统计行。反连接只判断键是否存在，不对匹配键增加无语义的 `Deduplicate`，因此完整字段血缘仍可解释。
- 256 个区域 × 1024 个 Point 的本地构造样例得到预期 256 行、每区 4 个要素；执行计划使用 Range/Broadcast Index Join，没有 Cartesian Product 或 Broadcast Nested Loop Join。该样例只证明当前实现没有明显计划退化，不代表 Enterprise 服务或任意生产规模容量。
- Within 专项合并回归 51 项全部通过，覆盖区域表、规则格网、固定/日历窗口、关联双表、Geometry/测量血缘、空区域、共边、重叠、EPSG:4326 XY 门槛和索引计划；失败、错误、跳过均为 0。
- 真实页面已复核区域表/规则格网确认式切换、格网 Modal、单行统计编辑、关联双表、时间设置、无效草稿应用及可展开问题详情；Canvas 与 Inspector 不显示统计值、Geometry 内容或范围坐标。

当前平台支持范围据此完成。Enterprise 11.3 官方页面与公式图之间的加权方差数值矛盾继续按第 9 节显式记录；未执行真实 Enterprise 异步作业、官方边界数值或生产容量对照，因此不声明服务、字段或数值完全等价。
