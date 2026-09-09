# Canvas `SPATIAL_CENTER_DISPERSION` Processor 设计

## 1. 定位与审计结论

引入于 Canvas 4.20，仅 BATCH。旧模式只支持投影 CRS XY Point、每组一行多 Geometry；4.31 显式新增线面质心与独立结果模式，详见第 7 节。

本文区分 4.20 历史快照、4.31 独立结果、4.32 原字段投影与后续目标。开发状态以[进度清单](../canvas-spatial-development-progress.md)为准；旧任务不自动切换算法，不以局部测试宣称完整 GA 对齐。

### 当前阅读入口与对齐边界（4.45 汇总）

- **已接入范围**：4.31 独立分析结果见第 7 节，4.32 中央要素投影见第 9 节；第 8 节列明未决时间和公式。
- **官方参照与差异**：参照 GA Summarize Center and Dispersion；标准距离为自有扩展，平均/中位/椭圆时间结果及官方加权椭圆仍待完成。
- **面板修订要求**：逐分析命名结果表；中央要素原时间投影不替代其他分析的时间结果，未实现项不能显示自动继承。

本摘要不替代逐版本契约。下节的“当前”均指 4.20 历史状态；目标线框不作为已实现截图。
参数覆盖、本地验证和官方结果对照分别登记，见[对齐验收规则](../canvas-spatial-analysis-processor-roadmap.md#7-对齐验收)。

## 2. 4.20 配置快照（历史）

```ts
type SpatialCenterDispersionKind =
  | 'MEAN_CENTER'
  | 'MEDIAN_CENTER'
  | 'CENTRAL_FEATURE'
  | 'STANDARD_DISTANCE'
  | 'DIRECTIONAL_ELLIPSE';

interface SpatialCenterDispersionAnalysis {
  analysisId: string;
  kind: SpatialCenterDispersionKind;
  outputColumnName: string;
  standardDeviations: 1 | 2 | 3 | null;
}

interface SpatialCenterDispersionConfiguration {
  sourceTableName: string;
  pointGeometryColumnName: string;
  featureIdColumnName: string | null;
  groupByColumns: string[];
  weightColumnName: string | null;
  analyses: SpatialCenterDispersionAnalysis[];
  outputTableName: string;
}
```

- 当前 analyses=1～16，重复 kind 拒绝；CENTRAL_FEATURE 要求 ID 字段；权重为数值。
- 当前五项中 STANDARD_DISTANCE 是自有扩展，不在 GA Summarize Center and Dispersion 的四种 summaryType 内。
- 当前 Median 使用固定 16 次距离加权迭代，不是坐标分量中位数；但没有据此证明收敛或官方误差等价。
- 当前 collect_list 在单分组内保存全部点，全局组仍有 Executor 内存风险；不能笼统称为无容量限制的分布式算法。
- 当前所有结果清除时间元数据，缺官方按分析类型的时间结果。

## 3. 官方目标与结果表

| summaryType | 分析及输出 |
| --- | --- |
| MeanCenter | 平均中心 Point，可选权重；时间启用时输出平均时间。 |
| MedianCenter | 最小化加权距离和的几何中位中心 Point；时间启用时输出中位时间。 |
| CentralFeature | 与其他代表位置总加权距离最小的**原始要素**，保留其原 Geometry 和时间类型，不只返回质心。 |
| Ellipse | 方向分布 Polygon，ellipseSize=1/2/3；时间区间按官方 ellipseSize 相关规则验收。 |

- 线/面输入以每个要素的质心参与位置分析；CentralFeature 输出仍是选中的原线/面。
- groupFields 支持整数、日期、字符串；weightField 可选，不能对所有标量类型不加区分地宣传官方支持。
- 默认 Ellipse=1 标准差；Standard Distance 独立标为平台扩展，非官方第五项。
- 每个启用分析分配独立结果表名，一个端口输出多个 Map 项。当前多 Geometry 宽表可保留为显式平台模式，
  但不能宣称等于四层官方结果。输出表均不得占用入口名称。
- 椭圆方向约定、轴长（半轴或全轴）、加权分母、退化组、负/零/NULL 权重和中心平局必须定义。
  1/2/3σ 不是任意数据分布的固定覆盖概率，不能在 UI 宣传必然包含 68/95/99.7%。
- Median 需停止准则、误差容差与迭代上限；固定迭代次数不构成收敛证明，不能仅称“中位数已实现”。

## 4. 目标 Inspector UI

设计状态：独立分析结果及中央要素原字段投影已分别在第 7、9 节接入。平均/中位/椭圆的分析时间仍待开发与核实；中央要素原事件时间字段别名传播不等于这些统计时间已实现，详见第 8 节。

```text
来源 / Geometry     [ stores / location ▼]
分组字段            [ city_code ▼]
权重字段            [ sales_amount ▼]（可选）
原要素唯一字段      [ store_id ▼]（中央要素）
分析与结果表                                      [设置]
[✓] 平均中心         → stores_mean
[✓] 中位中心         → stores_median
[ ] 中央要素         → stores_central
[✓] 方向椭圆 [1σ ▼]  → stores_ellipse
平台扩展            标准距离                       [展开]
时间结果            按分析类型（待实现）              [设置] (?)
```

分析项以单行表格展示“类型 / σ / 结果表”；字段名编辑放 Modal。
线/面输入时显示紧凑提示“按要素质心分析，中央要素返回原 Geometry”。
数值算法和空/退化规则放帮助 Popover，不在普通 Inspector 暴露迭代器、分区等运行调优参数。

上图为目标面板，不是当前能力截图。平均/中位/椭圆的时间入口不能显示为“自动继承”或“来源未启用时间”：
这些分析尚未输出统计时间，目标选项禁用并在帮助中说明。中央要素在 4.32 可按显式投影保留原事件时间，见第 9 节；
这不代表已经具备其他分析的时间计算或完整 instant/interval 支持。


### 参数交互与初值（目标设计）

下表是目标面板约定，不修改旧任务默认值；未明确标注为官方默认的初值均为平台推荐。

| 配置组 | 初值与条件显示 | 对齐边界 |
| --- | --- | --- |
| 分析项 | 四种官方分析多选；新建可推荐平均中心，不默认全部计算 | 平台推荐；Standard Distance 独立放扩展区 |
| 结果表 | 每个分析项一个表名，字段设置跟随该分析 | 多 Geometry 宽表只作为明确的平台兼容模式 |
| 权重与分组 | 默认不加权、不分组；选择后保留失效字段并标错 | 不从字段名猜测权重 |
| 方向椭圆 | 仅该行显示 1/2/3σ，默认 1σ | 官方默认，不承诺固定样本覆盖率 |
| 原中央要素 | 仅该分析要求原要素身份；线/面返回原 Geometry | 不能输出代表质心冒充原始要素 |

结果表列表直接显示“分析类型 → 结果表”，时间字段及几何字段在逐项 Modal 配置；不将未核实的迭代收敛或时间规则藏在默认值里。

## 5. 验收

- 对称点集平均中心、不同权重中心、非对称点集几何中位中心、强权重点/迭代退化。
- CentralFeature 对比各候选加权距离和、平局及重复 ID；线/面结果必须保留原 Geometry。
- 椭圆旋转、长短轴、1/2/3σ、单点/共线、各向同性、空组、全零/负/NULL 权重。
- 分析类型独立结果表、Schema/血缘、字段投影、平均/中位/区间时间逐项验证。
- 对大单组内存与算法复杂度设安全边界或改进计划，不能把 collect_list 隐藏在聚合中即称可扩展。
- 当前空输入全局一行 NULL 是平台行为；官方等价性在对照前不作保证。
- 新结果为 BOUNDED，不修改来源数据；Canvas 不显示计算出的中心、范围和权重值。

## 6. 官方依据

- [Summarize Center and Dispersion：四类分析、线面质心及输出层](https://developers.arcgis.com/rest/services-reference/enterprise/geoanalytics/tasks/summarize-center-and-dispersion/)
- [共同规则与版本策略](../canvas-spatial-analysis-processor-roadmap.md)

## 7. 4.31 已实现的显式独立结果模式

```ts
// 可选追加，不移除旧字段。
interface SpatialCenterDispersionConfiguration {
  // 原字段保持不变。
  resultMode?: 'ANALYSIS_TABLES' | 'LEGACY_WIDE' | null;
}
interface SpatialCenterDispersionAnalysis {
  // 原 outputColumnName 仍指定本结果的 Geometry 字段。
  outputTableName?: string | null;
}
```

- resultMode 缺失/null/LEGACY_WIDE 保留旧路径；ANALYSIS_TABLES 同时选择独立表与新的数值/空组语义，切换须确认。
  任意显式 resultMode（包括 LEGACY_WIDE）或非 null 逐项表名要求 4.31；保存使用现行小版本。Manifest/Result/HTTP 不变。
- 新建推荐一个 MEAN_CENTER，表名留空、不猜来源；这是平台初值。节点级旧 outputTableName 隐藏保留，旧模式下逐项表名保留但不生效。
- 每个分析结果是分组字段 + Geometry；中央要素额外保留原类型身份字段（若已作为分组字段不重复追加），Geometry 是被选中的原始要素。
  同组每分析一行，分析按数组顺序追加 Map；结果表不得与入口或其他分析重名。不同结果可以使用同名 Geometry 字段。
- 支持完整元数据的投影 CRS XY Point、MultiPoint、LineString、MultiLineString、Polygon、MultiPolygon；通用 Geometry 在执行时检查，混合集合不支持。
  分组暂保留平台原有非 Geometry 标量能力，整数/日期/字符串为官方参照子集，不能把其他类型标为官方保证。
- 来源 NULL/Empty Geometry、NULL 权重不参与。权重必须有限且非负；零权重要素保留为中央候选，但对平均/中位/离散不贡献权重。
  有效组全零权重或没有有效要素时不输出结果行；这与旧全局 NULL 宽表不同，也是必须显式切换的原因。

### 数值与停止准则

- 以正权重要素代表位置的包络中心平移、`S=max(1, 最大坐标半幅)` 缩放坐标，以最大权重归一化，并使用补偿求和。
  平均中心为 Σw·p/Σw。中央要素最小化 Σw·distance，分数 8 ULP 内视为数值平局，再按原类型 ID 次序选取。
  有效候选 ID 非空且全局唯一，运行失败码 `SPATIAL_CENTER_FEATURE_ID_INVALID`，不回显 ID 值。
- 中位中心为修正 Weiszfeld，处理当前迭代恰落观测点的次梯度条件，也检查强权重点是否是最优顶点。
  凸性给出目标差距上界 `max(0, ||R|| - 重合权重) × 包络半径`；归一化空间中不超过 `1e-10 × 总权重` 才停止。
  对应原坐标量级包含 S；这是目标函数误差，不是位置误差，非唯一最优时不能保证某个特定坐标。
  上限 10000 次，超限以 `SPATIAL_CENTER_MEDIAN_NOT_CONVERGED` 非重试 SCHEMA 错误退出，不返回未认证的迭代点。
- 当前方向椭圆采用人口加权矩 `C=Σw(p-μ)(p-μ)ᵀ/Σw`，半轴为 `k·sqrt(2λmax)` / `k·sqrt(2λmin)`，方向为从 +X 轴逆时针。
  使用 128 条边近似，k 为 1/2/3；标准距离圆半径为 `k·sqrt(trace(C))`。加权公式与 GA 详细算法尚需继续核对，不能称数值完全一致。
  零半轴/半径输出合法 Empty Polygon；不制造极小圆/面，不宣称 1/2/3σ 固定覆盖概率。
- 非有限数值/结果非法以 `SPATIAL_CENTER_NUMERIC_INVALID` 失败；输入 Geometry、权重分别有安全 SCHEMA 错误。

### 容量与交互

- 仍在 Executor 单组处理，不是无上限算法。分组收集前检查：100000 个有效要素、100 万顶点；包含 CENTRAL_FEATURE 时改为最多 5000 个要素。
  中央计算 O(n²)，中位 O(n×迭代次数)，均有边界。容量错误为 `SPATIAL_CENTER_GROUP_LIMIT_EXCEEDED`、CONFIGURATION、非重试。
  这不是 JVM 总内存保证；多组并行和原始数据读取仍受运行资源约束。没有 Driver collect、额外 Action 或自动缓存。
- Inspector 常驻单行 Table 展示所有分析和结果表；设置 Modal 编辑类型、表名、Geometry 字段、σ 和排序。
  取消不提交、删除二次确认、未挂载配置保留、无效业务草稿可应用。分析弹窗和模式切换不静默清空旧值。
  低频算法细节和容量放邻近帮助，Compiler 当前容量风险与具体配置错误按既有紧凑问题入口展示。
- Canvas 预览前两张结果表及计数，按预览数派生尺寸；不展示中心坐标、权重值或真实计算结果。

### 未完成项

- 截至 4.31：各分析的时间输出（尤其 Ellipse 时间区间）、官方加权公式/边界结果对照、原要素完整字段投影与多结果血缘验收仍待完成。
  后续 4.32 已接入字段投影及本地多结果血缘专项，见第 9 节；这不替代官方时间/公式与真实规模验收。
- 真实 ArcGIS 样本、极大坐标/复杂面容量、全页面浏览器与大数据规模验收仍未完成。4.31 不作为节点或完整路线图完成声明。

## 8. 后续参数修订与证据边界

本节是设计修订，不增加当前 JSON 字段，也不改变 4.31 算法。参数范围与数值等价分开验收。

### 官方参数与平台配置

| GA REST 参数 | 平台设计 | 当前边界 |
| --- | --- | --- |
| inputLayer | sourceTableName + Geometry 字段 | 4.31 支持投影 CRS XY 的点/线/面家族；不是官方全部 CRS 范围 |
| summaryType | analyses 中的四种官方分析 | STANDARD_DISTANCE 为平台扩展；不得作为官方第五项 |
| ellipseSize | DIRECTIONAL_ELLIPSE 行的 standardDeviations | 官方默认 1，可选 1/2/3；其他分析不受隐藏 σ 设置影响 |
| weightField | 可选 weightColumnName | 位置权重已实现，不据此推断时间也使用同一权重 |
| groupFields | groupByColumns | 官方列明整数、日期、字符串；平台其他可用标量另标扩展 |
| outputName | 每个分析的逻辑结果表名 | 只映射结果组织，不复制 Portal Feature Service 发布 |
| 无对应参数 | featureIdColumnName、Geometry 输出字段名、旧宽表模式 | 平台身份/投影/兼容配置，不标成官方必填参数 |

### 时间结果：已知要求与未决规则

| 分析 | GA REST 明确说明 | 仍需核实后才能冻结的计算规则 |
| --- | --- | --- |
| 平均中心 | 时间启用的来源产生平均时间值 | interval 来源如何取时间、是否加权、NULL/零权重、精度及舍入 |
| 中位中心 | 时间启用的来源产生中位时间值 | 偶数个时间的取法、是否加权、interval 来源与 NULL；不能拿几何中位中心的迭代结果当时间中位数 |
| 中央要素 | 结果与来源保持相同 Geometry 和时间类型 | 保留选中原要素的 instant 或完整起止字段；字段投影排除/改名与 Schema 的一致性 |
| 方向椭圆 | 时间结果为 interval，参与起止计算的要素受 ellipseSize 影响 | 参与集合如何确定、是否使用空间椭圆包含关系、边界与空集合；不能擅自使用全组 MIN/MAX |
| 标准距离（扩展） | 不属于该 GA 工具的官方分析 | 单独定义是否输出时间，不直接沿用 Ellipse 规则 |

GA REST 链接是滚动文档；以上已知要求来自该接口说明，仍需以 11.3 版本材料或服务样例固定验收。
不能将未决项包装为“自动按 ArcGIS 计算”。在规则确认前保留待实现状态，不新增一个含糊的时间继承开关。

目标交互：常驻只显示“时间结果 · 已配置 N 项 / 未配置”；点击约 680px Modal，按分析逐行显示
“结果类型 / 来源时间字段 / 输出时间字段”。instant 只显示单字段，interval 同行显示起止字段；
分析类型决定允许的结果类型，不能让用户任意切换成不对应的语义。未实现的分析分支禁用并说明原因。
模式切换保留非活动字段；普通缺字段、重名等问题允许保存草稿。结果仍为 BOUNDED，不产生 Streaming Watermark。

### 椭圆与中央要素投影

- 当前加权矩公式是明确的平台公式，不因名称为“方向椭圆”就等同 Esri。
  需分别核对权重一次/平方的使用、分母、旋转角约定、半轴/全轴、σ 倍率与退化组。
  ArcGIS Pro 同名工具的公式只能作为对照线索，未经 GA Server 证据确认不得直接替换现有公式。
- 数值验收至少包括非均匀权重、不对称点集、旋转点集、权重整体乘常数、单点/共线与时间边界样例；
  记录官方输入、结果和容差。只有轴长在自有公式测试中正确，不代表已完成官方对照。
- 中央要素完整属性应从选中的原始记录投影，不能逐字段 FIRST/MAX 拼成并不存在的要素。
  字段设置 Modal 提供显式保留、排除、改名和排序；Geometry、身份和启用的时间字段之间的冲突就地提示。
  血缘按各结果表单独登记：原要素属性为原字段投影，统计中心/椭圆为分析派生，不混写成全部直接来源。
- 后续若新增公式或时间策略会改变旧结果，须采用明确的可选策略及小版本门槛；
  不能把本节设计文字当作已经存在的协议，不能静默改变旧宽表或 4.31 独立结果。

### 来源区分

- [GA REST Summarize Center And Dispersion](https://developers.arcgis.com/rest/services-reference/enterprise/geoanalytics/tasks/summarize-center-and-dispersion/)：本节点的 GA 参数与输出语义依据。
- [Enterprise 11.3 Map Viewer Classic 同名工具](https://enterprise.arcgis.com/en/portal/11.3/use/summarize-center-and-dispersion.htm)：仅作交互参照；该页面未给出上述时间公式，也不能单凭同名将它视为 GA Server 数值算法证据。

## 9. 4.32 显式中央要素原字段投影

```ts
interface SpatialCenterFeatureColumn {
  sourceColumnName: string;
  outputColumnName: string;
  included: boolean;
}
interface SpatialCenterDispersionAnalysis {
  // 原字段保持不变。
  centralFeatureColumns?: SpatialCenterFeatureColumn[] | null;
}
```

- 仅独立结果模式的 CENTRAL_FEATURE 使用该数组，其他分析/旧宽表保留但不使用。缺失/null 不改变旧输出，
  任意显式数组（包括空/非活动数组）要求 4.32。Java 防御性复制，旧四/五参数构造器保留。
- 显式数组控制全部原始属性，包括分组/ID 的保留、排除、改名和顺序；不再自动补回它们。
  结果 Geometry 始终作为末列，以分析 outputColumnName 输出。空数组只输出该 Geometry。
  来源主 Geometry 也可显式投影为额外字段；建议列表不重复添加它，其他 Geometry 保留原类型和维度。
- 启用项来源必须存在、不得重复，输出名称必填、大小写不敏感唯一且不得与结果 Geometry 冲突；
  关闭项的失效来源和别名不参与执行校验。配置错误精确到 analyses[i].centralFeatureColumns[j]，整个节点不传播部分结果。
- 字段来自经过同样有效性过滤的原始关系，以选中 ID 精确关联；不从未过滤来源回连，避免 NULL/Empty 行复用 ID 后放大结果。
  原始属性不进入 collect_list，每列使用独立内部别名而非合并成不透明 Struct，保留可追踪的直接字段依赖。
  不增加外部读取、Driver collect、Action、缓存或持久化。回连是惰性计划，可能重复计算来源，不保证物理上只扫描一次。
- 原字段保留平台类型、长度/精度/Geometry/可空性，生成列与自增写入属性清除；实际值保持选中记录原值，包括 NULL。
  保留或改名 eventTimeColumn 时同步元数据；排除后清除。仍为 BOUNDED 且无 Watermark，未新增 interval 起止元数据。

### Inspector 与 Canvas

```text
要素唯一字段        [store_id ▼]
原要素字段          5 个                            [设置]
分析                中央要素 → stores_central

┌ 中央要素 · 原始字段 ────────────────────────────────────┐
│ 保留 5 个原始字段 (?)                      [重建建议]   │
│ 保留 │ 原始字段          │ 输出字段        │ 顺序       │
│  ☑   │ store_id          │ [store_id    ]  │ ↑ ↓        │
│  ☑   │ name              │ [store_name  ]  │ ↑ ↓        │
│  ☐   │ obsolete（不可用）│ [old_name    ]  │ ↑ ↓        │
│ 另输出结果 Geometry：central_feature                    │
│                              [取消] [保存字段草稿]      │
└────────────────────────────────────────────────────────┘
```

- 默认建议由当前 Compiler 来源字段产生；只在用户打开配置且原数组缺失时构造本地建议，保存前不改变节点。
  已有数组不随上游变化重建。确认式重建会替换排除/别名/顺序；取消弹窗不提交。失效字段和名称冲突就地标错，仍可保存草稿。
- 字段全部列在带内部滚动的紧凑 Table；低频解释放 ContextHelp。主面板只显示数量及设置入口。
  Canvas 与 Runner 摘要只记录启用字段数量，不显示属性值、时间值或 Geometry 内容。
- 平均/中位/椭圆时间、官方椭圆公式和完整规模/官方验收仍未完成，不能因本节投影落地将节点全部勾选。

## 10. 零权重数值隔离修正（2026-09-08，仍为 4.46）

- 复现的原问题：极远零权重点参与包络缩放，使正权重支持点在归一化时被舍入为相同位置，
  平均中心由约 0.6667 错误变为 0；中位停止半径及离散矩也受不贡献权重的要素影响。
- 显式独立结果模式的坐标平移/缩放改为仅依据正权重支持点。零权重不进入平均/中位的求和、
  次梯度与停止半径，也不进入协方差；运算前跳过，避免 `0 × Infinity` 产生 NaN。
- 零权重要素仍参与中央要素候选，保留原 ID/原 Geometry/原字段投影，并继续计入容量限制。
  不通过过滤零权重整行规避数值问题。无法在支持点坐标框中表示的极远候选或其无穷距离和
  不进入 ULP 平局比较；正权重支持点候选具有有限分数，极远候选不可能优于它们。
- 原同名几何、权重合法性、原要素 ID 唯一性、分组/容量、Map 顺序和惰性计划均保持。
  旧宽表未改；这是独立结果模式的数值缺陷修复，不新增统计公式、协议字段或时间策略。
- 本地专项验证有限/极大正负坐标的零权重不改变五类结果，零权重中心候选仍能获选；
  Spark 验证 Shuffle 后五张结果表、原字段回接与零 Action 分析。完整证据记录于开发清单。

本轮再次读取 GA REST 的输出说明，仍只确认平均时间、中位时间、与 ellipseSize 相关的 interval 输出；
页面未补充本文件第 8 节所需的权重/偶数中位数/参与集合规则。因此时间能力和官方加权椭圆仍待完成，
不以此数值修正声称官方时间/公式已对齐。
