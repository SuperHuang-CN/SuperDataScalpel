# 空间分析开发进度与验收清单

目标：按[路线图](canvas-spatial-analysis-processor-roadmap.md)及 12 篇节点设计完成开发。
本清单追踪完整范围，不以已有实现或局部测试替代对齐验收；完成项须有代码与验证证据。

## 设计修订状态

- 2026-09-08 全节点阅读入口修订：12 篇 MD 增加当前接入范围、官方产品/差异及面板要求，
  统一指向路线图逐参数验收规则。明确区分参数覆盖、平台语义验证、官方对照与容量证据；
  修正聚类和重建首页仍称 HDBSCAN/测地面完全未接入的过时文字，保留历史快照及未完成范围。
  本次仅修改设计文档，没有运行算法、协议、UI 代码或新的测试结果；完整开发目标及完成复选框保持不变。
- 2026-09-08 聚类设计补充：区分 HDBSCAN 官方参数、四类诊断含义、内部原型公式与尚未核实的 Esri 数值行为；
  明确含自身的核心距离、无半径/时间参数及目标紧凑面板。原型不等于节点开放；详见[聚类第 8 节](canvas-spatial-next-processors/spatial-point-cluster.md#8-hdbscan-参数诊断与开放条件修订)。
  路线图把 4.43 内部测地记录标为历史，避免与 4.44 Reconstruct 已接入状态矛盾；中央要素原事件时间投影
  与平均/中位/椭圆尚未实现的统计时间分开。此次修订仅改文档，不重跑测试、不修改代码或协议。
- 2026-09-07：路线图及 12 篇节点 MD 补充参数初值、生效条件、输出粒度与 UI 联动约定。
- 统一区分 4.20 历史快照、目标设计和局部实现；平台推荐不标为官方默认，未确认语义不标为已对齐。
- 上述设计修订本身不扩大实现范围、不升级协议；随后开发按能力单独记录版本与证据，下方清单不因文档完善而勾选完成。
- 追加设计核对：中心节点列明 GA 参数映射、四类时间输出的已知/未决规则及公式证据边界；
  目标面板不再将尚未实现的时间能力暗示为自动继承。Reconstruct 目标交互同步“固定周期始终 Gap”。
  本次没有代码、协议或算法变更，也没有新增测试通过或节点完成声明。
- 2026-09-08 继续校正设计状态：Reconstruct 固定周期始终 Gap 已是明确规则；测地面采样须独立于
  非活动线轨迹配置，原始观测距离与缓冲足迹分开。Nearest 的搜索阈值、求解精度和连接线采样不得混用。
  本轮仅修订文档，不开放节点组合、不预定新协议版本，也不将内部验证计作完整节点验收。

## 当前执行

当前 Canvas 为 4.77：`SPATIAL_CLIP` 新增逐来源多 Mask 组合，新建节点默认先融合当前来源命中的
所有 Mask 再裁剪一次，避免重叠 Mask 重复覆盖；旧定义缺失/null 时仍逐 Mask 输出。4.76 新增批处理 `SPATIAL_DESCRIBE_DATASET`，保留来源表并输出逐字段统计、
数据集描述、可选样本和可选 XY Envelope 范围。Geometry 可选，输出范围时必须显式选择；
4.76 已补齐四结果完整血缘、20,000 行规模样例和真实页面验收；真实 Enterprise 字段细节、
样本行为、带真实上游的字段选择和生产容量仍开放。4.75 新增批处理 `SPATIAL_SIMILAR_LOCATIONS`，以一个或多个参考位置的标准化平均
属性为目标，对候选位置执行属性值平方差或属性轮廓余弦差异排名，支持最相似、最不相似和两端结果。
4.76 已补齐全输出血缘、20,000 候选/10,000 结果规模样例和真实页面验收；当前仍是 GeoAnalytics
核心子集，Pro Ranked/Scale/Collapse、真实 Enterprise 数值和生产容量仍开放。4.74 新增批处理 `SNAP_TRACKS`，对有界 XY Point 轨迹与有界 XY LineString
道路网络执行距离候选、直接相邻拓扑/方向校验和 Viterbi 联合匹配，输出吸附点、
匹配线和诊断字段。首版不搜索无观测的多条中间道路，真实 ArcGIS Enterprise 路径、
容差和数值仍待完成；4.76 已补齐全输出血缘、20,000 观测/1,000 轨迹规模样例和真实页面验收，
高密度候选、超长单轨迹与生产容量仍开放。4.73 的 `TRACE_PROXIMITY_EVENTS` 继续对带 TIMESTAMP
和 STRING 实体 ID 的有界 XY Point 观测执行时空接触传播，支持显式 ID/起始表、
最大深度、同值属性、首次事件和可选后续轨迹。4.76 已补齐两结果集合字段血缘、
20,000 观测/10,000 事件惰性分布式执行证据及真实页面验收；Enterprise episode、
月年、极区/日期线和高偏斜生产容量对照仍开放。
4.72 的 `SPATIAL_GROUP_BY_PROXIMITY` 对一张有界 XY Point/Line/Polygon
表按 Intersects、Touches、Near Planar 或 Near Geodesic 和可选时间、受控对称属性关系建立无向边，
再以 Connected Components 求传递连通组；每个来源要素保留一行，孤立要素也获得非空组 ID。
4.76 已补齐集合字段血缘、20,000 孤立 Point 惰性预检/分布式连通分量执行证据及真实页面验收；
真实 ArcGIS Enterprise 的容差、月年时间、任意属性表达式和高偏斜生产容量对照仍待完成。4.71 的
`SPATIAL_ENRICH_FROM_GRID` 把已有多变量 Polygon 格网的显式
属性按相交关系回填到有界 XY Point，保留未匹配 Point，并在共享边界或异常重叠时按格网 ID 稳定选择
一个格网，确保每个 Point 恰好输出一行。4.76 已补完整字段血缘、20,000 Point/格网惰性预检与索引化
执行证据及真实页面验收；真实 ArcGIS Enterprise 的边界、字段类型和生产容量对照仍开放。4.70 的
`SPATIAL_MULTI_VARIABLE_GRID` 从多张投影 XY Point/Line/Polygon
来源的共同外包范围生成统一方格或六边形，支持逐变量筛选、最近距离、最近属性，以及相交或中心半径下的
COUNT/数值/字符串关联汇总。真实 ArcGIS Enterprise 的格网边缘、最近并列、不同 Geometry、统计数值、
不同投影轴单位和生产容量对照仍待完成；4.76 已补齐三类变量、九种统计和两来源统一格网的完整字段血缘、
20,000 点惰性预检与分布式执行证据及真实页面验收。4.69 的 `SPATIAL_HOT_SPOTS` 从投影 XY Point 生成完整方格，按固定距离
二元邻域计算 Getis-Ord Gi*，支持点数、单个数值字段求和、原始双侧 p-value、显式 FDR-BH、
`-3..3` 置信分级和时间切片；4.76 已补齐点数/字段总量、FDR/无校正及时间切片的完整字段血缘、
20,000 点惰性预检与分布式执行证据及真实页面验收。真实 ArcGIS Enterprise 边缘、多重检验、
数值、不同投影轴单位和生产容量对照仍待完成。
4.68 的 `SPATIAL_DENSITY` 从投影 XY Point 生成方格/六边形 Polygon
密度格网，支持 Uniform/Kernel、点数与可选数量字段、格网/半径/面积单位和时间切片；平台公式、
NULL/Empty、非有限值、候选展开上限与安全摘要均已显式定义。真实 ArcGIS Enterprise 数值和规模对照
仍待完成。事件检测的 `conditionScalars` 增加 `TRACK_POINT_X_AT/TRACK_POINT_Y_AT`，
可以有符号观测偏移读取当前轨迹片段内 Point X/Y；越界或 Geometry 为 NULL 时返回 NULL，
DOUBLE 结果的数值和单位跟随来源 CRS。4.66 已可把 TrackStartTime、TrackDuration、
TrackCurrentTime 与 TrackIndex 绑定为本节点条件 LONG 字段；时间使用 Epoch 毫秒，持续时间使用毫秒，
观测序号从 0 开始，并随既有轨迹片段重置。4.65 的事件窗口可选择受控 `TRACK_ACCELERATION` 来源，
对左闭右开观测范围内逐观测加速度（米/秒²）聚合；片段首观测为 0，后续值由相邻观测速度差与时间差计算。4.64 的
`TRACK_SPEED` 继续聚合 WGS84 逐观测速度（米/秒），同时间或缺失 Point 的段值为 NULL。4.63 的
`TRACK_DISTANCE` 继续对各 Point 自当前片段首观测起的累计测地距离（米）聚合；缺失/null/FIELD
保持 4.46 原始字段窗口语义。UNION 继续通过 4.62
的非 null `mergingTables` 执行 Merge Layers，第一张输入作为基准层，
后续层默认同名 Match、非同名字段追加、缺失字段补 NULL，并可逐字段 Match/Rename/Remove。数值 Match
显式 Cast；Geometry 必须 Match 到类型、CRS、维度一致的基准字段；无界来源事件时间必须 Match 到
基准事件时间。缺失/null 配置保持旧版严格同 Schema Union。4.61 的 Spatial Aggregate 单 UNION
Dissolve 可继续显式选择按 Polygon/MultiPolygon
相交、重叠或接触关系的传递闭包分组；NULL/Empty Geometry 不进入连通图。缺失/null 分组方式仍保持
4.53 的空分组全局 All，旧定义结果不变。Spatial Join 的独立空间 Near 继续沿用 4.60，PLANAR 使用来源 CRS，GEODESIC 仅支持
EPSG:4326 XY 并计算 Geometry 真实最近位置；它可作为唯一空间条件，也可与拓扑、属性和时间条件按 AND
组合。一对多可分别输出空间距离和时间 Near 区间间隔，一对一仅允许 Near 过滤。4.59 的 15 种有方向时间
关系继续有效，每侧使用开始字段和可选结束字段表达瞬时或闭区间；时间 Near 使用正整数固定时长。4.58 的一对多/
一对一粒度继续有效；一对一支持汇总全部匹配记录，或按
FIRST、数值最大/最小、日期最新/最旧及显式稳定顺序保留一条；缺失/null 粒度仍保持既有一对多结果，
不会因读取旧定义而改变输出。4.56 的 LEFT 可继续保留全部左侧目标要素，未匹配时右侧投影字段为 NULL；
INNER 保持旧结果。4.55 的可选属性等值条件继续与全部空间谓词按 AND 组合；缺失/null 属性条件
保持原空间连接。4.54 的可选显式输出字段投影继续支持按来源侧排除、改名和排序字段，缺失/null 保持旧版
全字段及同名拒绝。4.53 的 Spatial Aggregate 可选单 UNION Dissolve 继续表达 Create Buffers 的
All/List、来源要素计数、九种标量统计及 Multipart/Singlepart；4.52 的 Geometry Buffer 固定值、
数值字段和受控表达式三种逐行距离来源继续有效；
旧缺失/null 来源继续使用固定 distance。4.51 的 Spatial Clip 显式来源家族二维输出继续有效，
其旧缺失/null 策略仍保留通用 Geometry 和低维相交结果。Spatial Measure 的逐项显式输出单位仍由 4.50 提供，Geometry Buffer 的显式距离单位
仍由 4.49 提供。Nearest 的受控非点 WGS84
真实最近位置仍由 4.48 显式模式提供，旧 4.30 定义仍为 Point-only。
4.47 的共享固定周与已有日历周继续分开；详情见下方记录。
4.46 的事件检测受控字段窗口配置及条件入口保持不变，4.63～4.65 只增加轨迹距离、速度和加速度来源；
4.66 的四种时间/序号标量使用独立配置，不允许无意义的聚合函数或窗口偏移；
4.67 只为 Point X/Y 开放单观测偏移，不引入 Geometry 数组或任意 Arcade。
前一阶段 4.45 的 HDBSCAN 从内部管道推进到正式可选诊断对象、节点执行/原行回接、零 Job 集合血缘及 Inspector。
原 DBSCAN 与 Multi-scale 不支持边界保留，Manifest/Result/HTTP 不变；完整节点的规模/官方数值验收仍开放。

### Spatial Clip 多 Mask 组合（2026-09-13，Canvas 4.77）

- `SpatialClipConfiguration` 新增可选 `maskCombination=DISSOLVE_ALL|PAIRWISE`；新建节点默认
  `DISSOLVE_ALL`，缺失/null 保持旧 `PAIRWISE`，任意显式值在保存、导入和 GraphPlan 三处要求 4.77。
- Dissolve All 使用空间 INNER Join 找到候选，再按不暴露的来源技术行 ID 聚合相交 Mask 并回接来源；
  每条来源最多输出一条，重叠覆盖只计算一次，分离片段形成同一个 Multi Geometry。它不在 Driver
  收集或全局物化 Mask，也不会把内容相同的两条来源记录误合并。
- Pairwise 保留每个来源与每条 Mask 独立裁剪的旧行数和结果语义；两种模式都不输出 Mask 属性，
  不增加容差、吸附、自动修复或稳定排序。
- Inspector 增加邻近帮助和组合方式选择；Canvas/摘要只展示有效模式，不展示 Geometry、坐标、
  命中数或结果值。协议升至 4.77，Manifest、Task Result 和 HTTP API 不变。
- Business 的统一小版本升级入口在把旧定义规范化为当前版本之前检查显式 `maskCombination`，因此任务
  保存、试运行草稿、持久化定义读取和独立 Validator 不会把伪装成 4.76 的新语义静默升级；Runner
  安全摘要记录有效组合方式但不记录 Mask 内容。对应 Business 回归已补，当前执行受工作区内既有
  ComputeEngine/GatewayService 测试构造器未同步造成的全模块 `testCompile` 错误阻塞，主代码编译通过。
- Spark 专项验证覆盖旧 Pairwise、重叠/分离 Mask、重复来源行、结果家族/SRID、惰性零 Job、
  空间索引计划和完整字段血缘；`SpatialNodeOperatorSparkTest` 43 项通过。真实 Enterprise 容差、
  边界/数值与生产容量对照仍开放，不声明 Esri 完全等价。

### Describe Dataset（2026-09-13，Canvas 4.76）

- 新增 `SPATIAL_DESCRIBE_DATASET` Contracts、批处理 Operator、Registry/图规则、Runner PROCESS
  阶段、安全摘要、成功消息和稳定失败分类；来源及其他入口表保持原顺序，启用的结果表按固定顺序追加。
- 字段统计排除 Geometry/Binary，输出非空/空值数；数值字段统一为 DOUBLE 并输出 Sum、Mean、Min、
  Max、Range、`stddev_pop` 和 `var_pop`，日期时间输出 Min、Max 与毫秒范围，String/Boolean 使用确定性最小值。
- 数据集描述固定一行，包含记录/字段数、可选 Geometry 和事件时间统计、范围及 `description_json`。
  样本使用 `limit` 并保留来源 Schema；范围为非空 Geometry 的共同 XY Envelope Polygon，继承来源 CRS。
- 前端已接入严格 4.76 Parser、默认配置、专属 SVG/Canvas 卡片和紧凑 Inspector；来源、可选 Geometry、
  两个固定结果、样本和范围配置均保留失效草稿，并继续允许保存普通业务错误。
- Compiler Preview 已为字段统计、数据集描述和范围改用零行集合依赖计划，样本保持直接投影；四张结果表
  均达到 `FIELD_COMPLETE` 且不存在未知来源，分析阶段零 Spark Job。
- 20,000 行完成分布式字段统计和描述，样本严格限制为 10,000 行并输出单个范围 Polygon；Engine
  `SpatialDescribeDatasetNodeOperatorSparkTest` 5 项通过。
- 真实页面已验证必选结果、样本/范围开关、无效草稿应用和问题详情；修复了关闭状态字段未注册导致
  两个开关无法进入 Form 的问题，验收定义未保存。
- 尚未完成真实 ArcGIS Enterprise 11.3 字段级结果、采样边界、带真实上游的字段选择和生产容量对照；
  路线图勾选不表示 Esri 结果完全等价。完整语义见
  [Describe Dataset](canvas-spatial-next-processors/spatial-describe-dataset.md)。

### Geometry Derive / Simplify 页面与扩展回归收口（2026-09-13，仍为 4.76）

- 使用根目录 `./start-local-dev.sh` 重新启动当前源码对应的 Admin、Task Engine、Dispatcher、
  Service Engine 与前端；没有绕过脚本另起服务，也没有保存对现有任务的临时 Canvas 修改。
- `SpatialAnalysisProcessorSparkTest` 99 项真实 Spark 回归全部通过；此前同一轮
  `UnaryGeometrySupportTest`、`CanvasGraphPlanTest`、`CanvasNodeOperatorRegistryTest` 和
  `RunnerFailureClassifierTest` 共 115 项非 Spark 回归全部通过。覆盖两种简化算法、四维度策略、
  NULL/Empty/无效与退化 Geometry、惰性批流计划、容差单位及现有空间节点回归。
- 前端 `unaryGeometryPolicy`、`spatialUnits`、`canvasRegistry` 共 26 项通过。扩展单位用例改用当前
  Canvas 版本作为正常输入，并显式使用 Nearest 的 Point-only 模式，使 4.36 单位门槛测试不被后续
  4.40/4.48 能力门槛污染；Parser 的各能力版本限制没有放宽。
- 真实页面验证 Geometry Derive 的紧凑规则 Modal、函数邻近帮助、结果维度、逐项错误详情、取消不提交；
  Geometry Simplify 的算法帮助、容差与单位同行、EPSG:4326 来源角度警告、业务无效容差保留及
  “应用配置”继续可用。错误汇总可点击查看稳定错误码和路径，Canvas 卡片未显示 WKT、坐标或实际数据值。
- 两节点是自有基础算子，不声明 ArcGIS GeoAnalytics 同名工具或数值等价；测地简化、覆盖层共边简化、
  Concave Hull 等明确排除项仍需独立设计，不作为本轮两个节点完成条件。

### Nearest 当前支持范围收口（2026-09-13，仍为 Canvas 4.76）

- 修复测地日期线 Polygon 被经纬平面 JTS 有效性误拒的问题：平面模式仍使用 JTS 校验；显式测地完整
  Geometry 改由既有 WGS84 连续弧、环和区域拓扑校验。节点级回归覆盖日期线外环与孔洞、面内零距离、
  孔洞内真实正距离，以及连接线长度与排名距离一致。
- 增加连续 Geometry 极近等距回归：两个距离区间无法证明严格顺序时返回
  `GEODESIC_DISTANCE_PRECISION_NOT_REACHED`，不按采样值或候选当前次序猜测排名。
- 增加 256 张来源记录 × 1024 个候选的本地规模样例，结果与构造真值一致；执行计划继续使用索引化
  Distance/BroadcastIndex Join，不出现 Cartesian Product 或 Broadcast Nested Loop Join。该样例不冒充
  Enterprise 服务容量验收，也不承诺任意数据规模。
- Canvas 卡片显示已配置搜索半径；Inspector 帮助明确完整 Geometry、局部 Polygon 域、连续近等距和
  安全失败边界。前端专项 3 项、触及文件 ESLint、后端新增 3 项 Spark Nearest 与 Geometry 支持 4 项均通过。
- 真实页面确认新节点默认“真实距离 · 稳定同距排序”、测地模式显示“完整 Geometry（推荐）”、帮助文案和
  独立连接线 Modal；随后放弃临时编辑，现有任务仍为 11 节点、10 条连线，未保存验收节点。
- 当前明确支持范围据此收口。单个跨局部计算域 Polygon、跨域接触/包含、GeometryCollection、路网和无法
  证明的连续数值结果继续显式拒绝；ArcGIS Online GeometryServer 五例只验证底层距离，不声明 Enterprise
  Find Nearest 异步作业、字段、排名或容量完全等价。

### Summarize Within 当前支持范围收口（2026-09-13，仍为 Canvas 4.76）

- 测地模式现统一要求区域和被汇总 Geometry 均为 EPSG:4326 XY，双方同为 XYZ/XYM 也会在编译期拒绝；
  平面、CRS 一致和区域 Polygon/MultiPolygon 约束保持不变。
- 保留空区域改为“索引化空间 INNER JOIN 获取命中 + 等值 LEFT ANTI JOIN 补未命中 + 类型一致空统计行”，
  避免空间 LEFT OUTER JOIN 退化为 Broadcast Nested Loop Join。反连接不再对匹配键执行无语义 distinct，
  两个原先退化为 FIELD_PARTIAL 的格网/日历关联结果血缘恢复 FIELD_COMPLETE。
- 平台边界语义已固定：区域表使用 ST_Intersects，共边 Point 分别进入每个相交区域；重叠区域各自统计同一来源要素，
  不做跨区域唯一归属或去重。规则格网 Point 继续按稳定格网索引唯一归属。
- 256 个区域 × 1024 个 Point 的本地规模样例结果正确，计划使用 Sedona Range/Broadcast Index Join，
  无 Cartesian Product/Broadcast Nested Loop Join。Within 合并回归 51 项全部通过，失败、错误、跳过均为 0。
- 真实页面复核区域表/规则格网切换、格网、单行统计、关联双表、时间设置、无效草稿应用和问题详情；
  时间切片开关补充可访问名称。验收节点未保存，任务正式定义不变。
- 当前明确支持范围据此收口。Enterprise 11.3 官方加权方差公式与文字算例矛盾、真实异步服务字段、
  官方边界数值及生产容量仍明确不宣称等价；完整边界见
  [Summarize Within](canvas-spatial-next-processors/spatial-summarize-within.md#12-当前明确支持范围收口2026-09-13canvas-476)。

### Overlay 当前支持范围收口（2026-09-13，仍为 Canvas 4.76）

- 五模式、完整点/线/面家族矩阵、二维 Multi 结果、低维接触、字段投影、缺失侧补 NULL、洞、
  Multipart、NULL/Empty、无效 Geometry 惰性失败及 Z/M 降为 XY 继续保持 4.26 的显式语义。
- 独有区计划改为“索引化空间 INNER JOIN 获取匹配遮罩 + 按来源行身份 `ST_Union_Agg` + 等值
  LEFT JOIN 恢复未匹配来源”，避免空间 LEFT OUTER JOIN 退化为 Broadcast Nested Loop Join。
  每个来源要素只对全部匹配遮罩的并集执行一次 Difference，同侧重复/重叠来源仍保持独立。
- 计划内行身份显式标记为技术列，不再把内部 GROUP/JOIN Key 误报成未知业务来源。IDENTITY 的相交与
  差集联合计划达到 `FIELD_COMPLETE`，左右属性和结果 Geometry 均可追溯到真实输入字段。
- 256 个来源要素 × 1024 个遮罩的本地样例结果正确，计划使用 Sedona Range/Broadcast Index Join，
  不含 Cartesian Product/Broadcast Nested Loop Join。Overlay 合并回归 10 项全部通过，失败、错误、
  跳过均为 0；前端 2 个文件 5 项通过，触及文件 ESLint 无错误。
- 真实页面确认五模式、图层家族策略、字段投影入口、无效草稿应用和问题详情；验收内容只留在未保存草稿中。
  当前完成范围明确为 pairwise overlay，不声明 Esri tolerance/snap、全局无重叠平面分区、几何编码、
  Enterprise 异步服务字段或生产容量完全等价。完整边界见
  [Overlay](canvas-spatial-next-processors/spatial-overlay.md#8-当前明确支持范围收口2026-09-13canvas-476)。

### Track Reconstruct 当前支持范围收口（2026-09-13，仍为 Canvas 4.76）

- 有序片段、确定同时间次序、三类拆分、三种连接段归属、固定周期始终 Gap、字段统计、平面/测地路径、
  平面面轨迹、缓冲观测窗口及局部 WGS84 测地面执行链继续保持既有协议和运行语义。
- 测地面 Point/Polygon/MultiPolygon 要求 EPSG:4326 XY；距离 gap 使用原始 Geometry 区域，不使用质心或
  缓冲结果。日期线、极点、孔洞、凹面、不同半径和共享端点均已有真实 Spark/几何回归；大域和未决连续
  数值边界继续稳定拒绝，不增加不可靠回退。
- 新增完整输出血缘回归：轨迹 ID 为直接来源，开始/结束、观测数、Count/Count Field、Sum、Mean、
  First/Last 和 Geometry 均为可解释的真实输入聚合派生；结果达到 `FIELD_COMPLETE`，不存在
  `WRITTEN_UNKNOWN_SOURCE`。计划内部拆分/分段列没有暴露为业务输出，也没有通过放宽 Analyzer 伪造来源。
- 新增 20,000 个 Point 的单轨迹样例：分析阶段 0 个 Spark Job，执行后形成一条 20,000 点轨迹，
  起止、计数和统计正确。Operator 不 collect 到 Driver；Executor 侧仍按组执行 `collect_list + array_sort`，
  单个超大组受内存和排序成本限制，本样例不代表生产容量或 Enterprise 性能等价。
- 收口前后端专项共 91 项通过；新增用例所在 `TrackAreaSparkTest` 30 项通过。前端 4 个文件 20 项通过，
  节点目录 ESLint 无错误。真实页面确认线/面、平面/测地、面轨迹设置、缓冲字段/表达式/窗口、相邻 gap、
  固定周期、表达式拆分、连接段归属、无效草稿应用和问题详情；验收节点只在未保存草稿中。
- 当前完成边界不包含 Arcade 全兼容、任意全球域、ArcGIS 官方采样/容差/统计/窗口公式、Enterprise
  异步服务字段、Geometry 编码或生产容量完全等价。完整语义见
  [Track Reconstruct](canvas-spatial-next-processors/track-reconstruct.md#18-当前明确支持范围收口2026-09-13canvas-476)。

### Track Motion Statistics 当前支持范围收口（2026-09-13，仍为 Canvas 4.76）

- 观测窗口八组 31 项、窗口 1～100、Idle 严格距离/时间双阈值、独立高程来源与垂直单位、平面/WGS84
  测地距离、日期线 Bearing、固定周期和旧 `LEGACY_LAG` 兼容语义继续保持现有协议。
- 缺失值边界已明确：NULL/Empty Point 不跨点连接，非有限高程只影响派生测量，零时长不除零，NULL 时间
  不输出；窗口只汇总完整包含的段，加速度汇总要求两个相邻段。国际码/美国测量制与固定周沿用公共规则。
- 新增完整字段血缘回归：保留字段为直接来源，31 项指标分别追溯到真实 Geometry、时间或高程字段；结果
  达到 `FIELD_COMPLETE` 且不存在 `WRITTEN_UNKNOWN_SOURCE`。同时间唯一性检查改用内部校验时间列，
  原始时间字段不再被覆盖；没有放宽 Catalyst Analyzer。
- 新增 20,000 点单轨迹样例：分析阶段 0 个 Spark Job，执行后逐观测输出 20,000 行，窗口 100 的瞬时和
  汇总结果正确；计划不把轨迹组 collect 到 Driver，也不使用 `collect_list`。该样例不代表生产容量保证。
- 后端 Motion 基线 10 项及新增血缘/规模 2 项通过；前端 Inspector/Parser 2 个文件 6 项通过。
  真实页面确认新节点不立即编译、新旧语义确认切换与草稿保留、八组 31 项、单位/高程、Idle 双阈值、
  固定时间边界、无效草稿应用及问题详情；验收节点未保存。
- 当前完成范围不包含 Enterprise 官方字段别名、全部单位名称、固定月年、服务端容差/数值、Streaming、
  三维测地距离或生产容量等价。完整边界见
  [Track Motion Statistics](canvas-spatial-next-processors/track-motion-statistics.md#8-当前明确支持范围收口2026-09-13canvas-476)。

### Find Similar Locations（2026-09-13，Canvas 4.75）

- 新增 `SPATIAL_SIMILAR_LOCATIONS` Contracts、批处理 Operator、Registry/图规则、Runner PROCESS
  阶段、安全摘要、成功消息和稳定错误分类；入口表保持原顺序，新的有界结果表追加。
- 支持 1～32 个两表同名同数值类型分析字段，以及最多 64 个只用于解释结果的候选附加字段；
  两侧可分别使用已有受控 Filter。NULL/Empty Geometry 跳过，ID 必须非空且唯一。
- 标准化总体包含筛选后的参考与候选全集；多个参考要素使用各标准化字段平均值形成目标。
  `ATTRIBUTE_VALUES` 输出标准化平方差和，`ATTRIBUTE_PROFILES` 输出 `1 - cosine`，0 最相似。
- 最相似和最不相似排名均以候选 ID 字符串解决并列；`BOTH` 自动缩小每端数量以避免同一候选重复。
  分析值 NULL/NaN/Infinity、零轮廓和非有限分数均稳定失败。
- 前端已接入严格 4.75 Parser、默认配置、专属 SVG/Canvas 卡片和紧凑 Inspector；参考/候选筛选、
  分析字段、附加字段和九个固定结果字段均有独立紧凑编辑入口，Canvas 不显示筛选字面量或属性值。
- Compiler Preview 已改为零行集合依赖计划，不构造标准化、全局排名或真实结果计划；全部结果字段
  追溯真实参考/候选字段，达到 `FIELD_COMPLETE` 且不存在未知来源，分析阶段零 Spark Job。
- 20,000 个候选完成分布式标准化与排名并稳定返回 10,000 个最相似候选；Engine
  `SpatialSimilarLocationsNodeOperatorSparkTest` 7 项通过。
- 真实页面已验证默认紧凑 Inspector、分析/附加字段行、九个结果字段 Modal、无效草稿应用和问题详情；
  验收定义未保存。无上游时字段候选和筛选设置按 Schema 依赖正确禁用。
- 仍待真实 ArcGIS Enterprise 11.3 的数值和并列边界、不同 Geometry、带真实上游的完整筛选交互及
  生产容量对照；路线图勾选不表示 Esri 数值完全等价。详见
  [Find Similar Locations](canvas-spatial-next-processors/spatial-similar-locations.md)。

### Snap Tracks（2026-09-13，Canvas 4.74）

- 新增 `SNAP_TRACKS` Contracts、批处理 Operator、Registry/图规则、Runner PROCESS 阶段、
  安全摘要、成功消息和稳定错误分类；Point、Line 与其他入口表保持原顺序，结果表追加。
- Point 轨迹按 1～8 个标识字段分组，再按 TIMESTAMP 和可选同时间顺序确定性排序；
  复用时间 gap、距离 gap 和固定周期切分。单观测片段保守标记未匹配。
- 线网显式配置唯一线 ID 和 From/To 节点，可选四值方向映射；未配置方向时全部双向，
  未命中映射的实际方向值按禁行处理。
- Planar 要求可换算线性单位的投影 CRS；Geodesic 要求 EPSG:4326 XY。每观测候选上限为 32，
  超出稳定失败，不丢弃候选或退化为独立最近线。
- Executor 内使用 Viterbi 联合选择，转移必须位于同线或共享端点的直接相邻线，并满足方向。
  匹配成本同时使用点线距离与观测移动/路网转移距离差异。
- 结果保留 Point 原字段，可选投影至多 32 个道路属性，追加吸附几何、线 ID、M/U 状态、
  原/匹配坐标和米制距离。可选输出全部观测或仅匹配观测。
- 前端已接入严格 4.74 Parser、默认配置、专属 SVG/Canvas 卡片和紧凑 Inspector；
  方向、道路属性、切分和诊断字段用独立 Modal。卡片不展示搜索距离数值、方向值或坐标。
- Compiler Preview 已改为零行集合依赖计划，不执行空间 Join、候选聚合或 Viterbi；Point 原字段保持
  直接血缘，道路属性、匹配线 ID、状态、吸附 Geometry、坐标和距离均关联真实 Point/道路字段，
  输出达到 `FIELD_COMPLETE` 且不存在未知来源。
- 1,000 条独立轨迹、20,000 个观测和 1,000 条道路全部匹配；候选与观测不收集到 Driver。
  Engine `SnapTracksMapMatcherTest` 与 `SnapTracksNodeOperatorSparkTest` 共 12 项通过。
- 真实页面已验证紧凑 Inspector、方向四值配置、轨迹切分、8 个结果字段、无效草稿应用和问题详情；
  验收定义未保存。道路属性依赖有效线表 Schema；Viterbi 仍在 Executor 逐轨迹分组内使用
  `collect_list`，高密度候选、超长单轨迹和复杂路网仍是生产容量开放项。
- 仍待真实 Enterprise 对照多条中间道路搜索、官方候选评分/容差、复杂路口和服务输出；路线图勾选
  表示本地实现、血缘、规模样例与页面收口完成，不表示 Esri 完全等价。详见
  [Snap Tracks](canvas-spatial-next-processors/snap-tracks.md)。

### Trace Proximity Events 当前能力收口（2026-09-13，协议仍为 4.76）

- 新增 `TRACE_PROXIMITY_EVENTS` Contracts、批处理 Operator、Registry/图规则、Runner PROCESS 阶段、
  安全摘要、成功消息和稳定错误分类；入口表保持原顺序，首次接触事件表和可选后续轨迹表追加到 Map 末尾。
- 来源限定为有界 XY Point 观测，实体 ID 为 STRING、观测时间为 TIMESTAMP；Planar 要求投影 CRS，
  Geodesic 复用 EPSG:4326 XY 的真实测地距离实现。NULL Geometry、时间或实体 ID 不参与追踪。
- 起始实体可直接配置 1～256 个 ID 和可选开始时间，也可从另一张有界上游表读取实体 ID 与开始时间；
  最大传播深度为 1～32，可选择最多 8 个必须同时相等的属性字段。
- 只有空间距离、时间距离和全部同值属性同时满足才形成接触；传播事件不能早于上游实体到达时间。
  同一下游实体有多个候选时按事件时间、上游实体 ID 和内部行身份稳定选择第一次接触，每个实体最多一条事件。
- Runner 使用分布式 BFS，并通过 RDD Union 后重建 Dataset 和逐层 Checkpoint 截断计划；Compiler Preview
  使用零行集合依赖计划，事件 5 字段和轨迹深度追溯实际观测/起始表字段，不执行自连接、起始实体查找、BFS 或 Checkpoint。
- 前端已接入严格 4.73 Parser、默认配置、节点注册、专属传播链 SVG、Canvas 卡片和紧凑 Inspector；
  显式 ID 在 Inspector 完整编辑，但 Canvas 卡片和安全摘要只显示数量，不显示具体 ID、距离或数据值。
- 事件表和轨迹表均达到 `FIELD_COMPLETE`，原观测字段保持直接血缘，六个追踪结果字段均有真实来源。
  20,000 条 Point 观测与 10,000 个上游表起始实体生成 10,000 条首层事件；Preview 分析阶段零 Spark Job，
  运行不向 Driver 收集轨迹或接触行。Engine `TraceProximityEventsNodeOperatorSparkTest` 4 项通过。
- 真实页面已验证显式 ID 列表、上游表来源、后续轨迹开关、5 字段 Modal、无效草稿应用和问题详情；
  未保存任务定义。真实 ArcGIS Enterprise 的搜索边界、持续接触 episode、月末/年末和会话时区、
  多上游并列、极区/日期线、官方输出字段类型及高偏斜规模对照仍开放。完整语义和 UI 见
  [Trace Proximity Events](canvas-spatial-next-processors/trace-proximity-events.md)。

### Group By Proximity 当前能力收口（2026-09-13，协议仍为 4.76）

- 新增 `SPATIAL_GROUP_BY_PROXIMITY` Contracts、批处理 Operator、Registry/图规则、Runner PROCESS
  阶段、安全摘要、成功消息和稳定错误分类；来源及其他入口表保留，新有界结果追加到 Map 末尾。
- 接入 Intersects、Touches、Near Planar、Near Geodesic；Near Planar 明确要求投影 CRS，Near Geodesic
  复用 EPSG:4326 XY 的真实 Geometry 最近位置实现。Point 不允许 Touches。
- 可选时间 Intersects/Near 支持瞬时或闭区间，毫秒至周为固定时长，月/年为会话时区日历区间；最多 8
  个受控对称属性条件支持同值和数值绝对差，全部活动关系按 AND 形成边。
- Runner 以 Checkpoint 绑定内部行身份，通过 GraphFrames Connected Components 求传递闭包；NULL/Empty
  Geometry 成为孤立组，无效 Geometry 和倒置时间区间稳定失败。Compiler Preview 使用零行
  集合依赖 Schema 计划，`group_id` 追溯 Geometry、活动时间和属性字段，不执行自连接、Checkpoint 或图算法。
- 前端已增加严格 4.72 Parser、默认配置、节点注册、专属 SVG/Canvas 卡片和紧凑 Inspector；支持来源、
  Geometry、空间距离、时间开关、属性关系增删排序以及失效值保留，业务错误不阻止保存草稿。
- `group_id` 和原字段全部达到 `FIELD_COMPLETE`，无未知来源；集合字段关联 Geometry、活动时间和属性，
  原字段保持直接血缘。20,000 个孤立 Point 的 Preview 分析阶段零 Spark Job，实际输出 20,000 行和
  20,000 个非空独立组；GraphFrames/Checkpoint 分布式执行不向 Driver 收集要素、边或成员。
- 规模执行暴露并修正 Geometry 校验 UDF 捕获不可序列化 `GeometryTypeDefinition` 的问题，
  现只捕获 `GeometryKind`。Engine `SpatialGroupByProximityNodeOperatorSparkTest` 3 项通过。
- 真实页面已验证四类空间关系、Near 距离/单位联动、时间关系、属性关系、无效草稿应用与
  紧凑问题详情；未保存任务定义。当前属性能力是官方任意对称表达式的受控子集；Enterprise 容差、
  月末/年末、任意属性表达式、高偏斜图和生产容量对照仍开放。完整语义和 UI 见
  [Group By Proximity](canvas-spatial-next-processors/spatial-group-by-proximity.md)。

### Enrich From Multi-Variable Grid（2026-09-12，Canvas 4.71）

- 新增 `SPATIAL_ENRICH_FROM_GRID` Contracts、批处理 Operator、注册/图校验、Runner PROCESS 阶段、
  安全摘要、成功消息和运行错误分类。Point、格网及其他入口表保持原顺序，新的有界 Point 结果追加。
- 输入限定有界 XY Point 与同 CRS 的 XY Polygon/MultiPolygon 格网；显式选择并可改名要回填的非
  Geometry 标量字段，不自动跟随格网 Schema 增加字段，也不重新计算格网变量。
- 每个输入 Point 恰好输出一行。未命中仍保留并输出 NULL；共享边界或异常重叠时按格网 ID、Geometry
  和选定属性形成稳定次序选择一格。命中格网 ID 为 NULL 时使用稳定安全错误。
- 前端已增加严格 4.71 配置解析、默认配置、节点注册、专属 SVG/Canvas 卡片及紧凑 Inspector；支持
  Point/格网选择、字段建议、添加全部属性、逐字段改名、排序和失效值保留，业务错误不阻止保存草稿。
- Contracts、Parser、Registry、安全摘要和 Spark 专项已接入；Spark 样例覆盖单格命中、共享边界、
  未匹配保留、字段 nullable Schema 与入口表传播。真实 Enterprise、浏览器和规模验收仍未完成。
- 完整语义和 UI 见 [Enrich From Multi-Variable Grid](canvas-spatial-next-processors/spatial-enrich-from-grid.md)。

### Build Multi-Variable Grid（2026-09-12，Canvas 4.70）

- 新增 `SPATIAL_MULTI_VARIABLE_GRID` Contracts、批处理 Operator、注册/图校验、Runner PROCESS 阶段、
  安全摘要、成功消息和运行错误分类。入口表保持原顺序，统一格网结果作为新的有界表追加。
- 一至 32 个变量可独立选择来源表、投影 XY Point/Line/Polygon 家族 Geometry、筛选和搜索距离；
  全部唯一来源 Geometry 的未筛选共同外包范围决定方格/六边形，变量筛选不改变范围。
- 已接入最近距离、最近属性和关联要素汇总。汇总无半径时按完整格网相交，有半径时按中心平面距离；
  COUNT 无命中为 0，数值统计输出 DOUBLE，ANY 第一版只接受 STRING 并采用确定性最小值。
- 格网最多 100 万，搜索距离与格网大小比例最多 512；无效 Geometry、非有限统计值和容量超限均有稳定安全错误。
  Compiler 只构建惰性计划，不读取真实数据。
- 前端已增加严格 4.70 配置解析、默认配置、节点注册、专属 SVG/Canvas 卡片及 Inspector；变量使用全量紧凑列表、
  独立设置 Modal 和条件树 Modal，业务错误不阻止保存草稿。
- Contracts 4 项（含前序版本断言）、前端配置解析和 Spark 专项已通过；Spark 覆盖两张来源表、最近距离/属性、
  COUNT/SUM、无命中 NULL/0、逐变量筛选不缩小格网、来源表保留和 Polygon Schema。真实 Enterprise、
  不同 Geometry/单位、浏览器及容量验收仍未完成，不能写成 Esri 数值完全一致。
- 完整语义和 UI 见 [Build Multi-Variable Grid](canvas-spatial-next-processors/spatial-multi-variable-grid.md)。

### Find Hot Spots（2026-09-12，Canvas 4.69）

- 新增 `SPATIAL_HOT_SPOTS` Contracts、批处理 Operator、注册/图校验、Runner PROCESS 阶段、
  安全摘要、成功消息和运行错误分类。来源表保留，结果作为新的有界表追加。
- 输入限定投影 XY Point；以固定 `(0, 0)` 原点生成有效点外包矩形内的完整方格，零值格也参与统计。
  格网总数连同所有时间片最多 100 万；固定距离二元邻域包含当前格，距离必须大于格网边长且比例最多 64。
- 默认以每格点数计算 Getis-Ord Gi*；平台扩展可先对一个数值字段求和。原始双侧 p-value 始终输出，
  可选按时间片执行 Benjamini-Hochberg 并输出调整值；置信分级固定为 `-3..3`。
- 前端已增加严格 4.69 配置解析、默认配置、节点注册、专属 SVG/Canvas 卡片及 Inspector；
  时间切片和八个结果字段使用独立设置区，不在画布或日志显示坐标或数据值。
- 点数、字段总量、FDR、无校正和时间切片的全部输出均达到 `FIELD_COMPLETE`，不存在
  `WRITTEN_UNKNOWN_SOURCE`。格网、点数及 Gi* 诊断字段追溯 Geometry；字段总量结果同时追溯
  数值字段；窗口字段追溯时间字段。
- 20,000 点 Preview 分析阶段零 Spark Job；字段总量完成真实执行并输出 20,000 个格网，计划不含
  `CollectLimit`、`collect_list`、Cartesian Product 或 Broadcast Nested Loop Join。
  `SpatialHotSpotsNodeOperatorSparkTest` 3 项通过。
- 真实页面已验证延迟编译、点数/字段总量、FDR/无校正、时间切片、结果字段、无效草稿应用和紧凑
  问题详情；未保存任务定义。当前仍需真实 Enterprise 边缘、FDR、不同投影单位、数值和生产容量
  对照，不能把当前公开公式实现描述为 Esri 数值完全一致。
- 完整语义见 [Find Hot Spots](canvas-spatial-next-processors/spatial-hot-spots.md)。

### Calculate Density（2026-09-12，Canvas 4.68）

- 新增 `SPATIAL_DENSITY` Contracts、批处理 Operator、注册/图校验、Runner 阶段分类、安全摘要和
  `SPATIAL_DENSITY_VALUE_NOT_FINITE` 运行错误分类。来源表保留，结果作为新的有界表追加。
- 输入限定投影 XY Point；方格边长、六边形对边距离、搜索半径和面积单位显式配置。半径必须大于
  格网大小且比例最多 512；NULL/Empty Point 排除，NULL 数量值不贡献。
- Uniform 使用半径圆面积归一化常量贡献，Kernel 使用四次核并在格网中心计算；点数密度始终输出，
  数值数量字段最多 32 项。固定/日历时间切片复用已有空间窗口语义。
- 前端已增加严格 4.68 配置解析、默认配置、节点注册、专属 SVG/Canvas 卡片及 Inspector；数量字段、
  时间切片和输出字段使用独立设置区，不在画布或日志显示数据值。
- Task Engine 与 Business 主源码编译通过；当前仍需补真实 Enterprise 逐格网数值、边缘、不同投影单位
  和规模对照，所以不把本地公式写成 Esri 完全等价。

### Detect Incidents Point 相对观测坐标标量（2026-09-12，Canvas 4.67）

- 依据 Enterprise 11.3 官方 `TrackGeometryWindow(-1,0)[0]["x"]` 示例，在独立
  `TrackIncidentScalar` 中增加 `TRACK_POINT_X_AT/TRACK_POINT_Y_AT` 和有符号 `offset`。
  0 读当前观测，负数回看，正数前看；超出当前 DataScalpel 轨迹片段或目标 Geometry
  为 NULL 时返回 NULL，不跨轨迹、固定边界或 gap 片段。
- 只允许带完整元数据的 Point Geometry；结果是可空 DOUBLE，数值和单位跟随来源
  CRS，不限 WGS84，不做投影换算。坐标来源（含 LEGACY 非活动草稿）要求 4.67，
  版本、缺偏移和缺 Point 分别使用
  `TRACK_INCIDENT_POINT_COORDINATES_REQUIRE_SCHEMA_VERSION`、
  `TRACK_INCIDENT_POINT_COORDINATE_OFFSET_REQUIRED` 和
  `TRACK_INCIDENT_POINT_COORDINATE_REQUIRES_GEOMETRY`。
- Inspector 在原轨迹标量 Modal 中仅对坐标来源显示偏移，切回时间/序号来源时保留
  隐藏草稿。Canvas、定义摘要和 Runner 只显示安全计数，不记录绑定名、偏移、坐标或
  条件字面量。标量只供开始/结束条件使用，不进入输出 Schema。
- 专项验证通过：Contracts 5 项、GraphPlan 60 项、Runner 摘要 11 项、Incident Spark
  19 项（Task Engine 合计 90 项）、前端定义/Inspector/Canvas 18 项，以及前端 TypeScript
  和触及文件 ESLint。Incident Spark 覆盖相对 X/Y、片段重置、越界 NULL、Geometry/offset
  校验及 FIELD_COMPLETE Geometry 血缘。Contracts、Business 和 Task Engine 主代码均已编译；
  Business 定向用例因工作区内既有 Compute/Gateway 测试构造器未同步而在 testCompile 阶段被阻断。
- 未引入 Geometry 数组、`TrackWindow`、整行对象或 Arcade 执行器；未执行真实 ArcGIS
  服务和大轨迹容量对照，因此事件节点完成复选框保持开放。

### Detect Incidents 轨迹时间与序号标量（2026-09-12，Canvas 4.66）

- 依据 Enterprise 11.3 官方定义，新增 `TrackIncidentScalar` 与可选 `conditionScalars`。四种来源为
  TRACK_START_TIME、TRACK_DURATION、TRACK_CURRENT_TIME 和 TRACK_INDEX；不复用窗口的九种聚合。
- 开始/当前时间输出 Unix Epoch 毫秒，持续时间输出片段开始至当前观测的毫秒数，序号从 0 开始。
  当前轨迹、固定边界与 gap 片段共用既有分区，进入新片段后重新计算；四种条件候选均为 LONG。
- 非空数组（包括 LEGACY 非活动草稿）要求 4.66，稳定门槛为
  `TRACK_INCIDENT_SCALARS_REQUIRE_SCHEMA_VERSION`。绑定名与原字段、窗口指标和其他标量大小写不敏感唯一。
- Inspector 使用独立紧凑 Modal，并继续允许保存未完成草稿；Canvas、定义摘要和 Runner 只显示配置数量，
  不暴露绑定名、条件字面量或运行值。Manifest、Task Result、HTTP API 和生产依赖不变。
- 专项验证通过：Contracts 5 项、GraphPlan 59 项、Runner 摘要 11 项、Incident Spark 16 项，
  Task Engine 去重合计 86 项；前端定义、类型、Inspector 与 Canvas 65 项通过。前端 TypeScript、触及文件
  ESLint、Business 主代码编译和 Task Engine 主代码编译均通过；Incident Spark 同时覆盖
  FIELD_COMPLETE 原字段血缘，确认四种临时标量不会形成未知来源字段。
- Distance/Speed/Acceleration 的 Current 和 At 不新增稳定类型：分别用 `FIRST + [0,1)` 与
  `FIRST + [n,n+1)` 表达。Geometry/TrackWindow 复合对象仍不能被当前标量条件树安全消费，继续留待设计。

### Detect Incidents 轨迹加速度窗口（2026-09-12，Canvas 4.65）

- 依据 Enterprise 11.3 官方定义和示例，`TrackIncidentWindow.source` 增加 `TRACK_ACCELERATION`。
  窗口值是逐观测加速度，边界仍为左闭右开；片段首观测为 0，后续值为当前速度与前一观测速度之差
  除以时间差，单位固定为米/秒²。
- 速度继续由 WGS84 测地距离和秒计算。同时间、当前或前一速度为 NULL、缺失 Point 时返回 NULL，不执行
  除零，也不跨轨迹、固定边界或 gap 片段；异常输入处理是平台的确定性约定。
- 复用现有九种聚合及条件入口。保存端、GraphPlan 和前端导入对任意 TRACK_ACCELERATION（含 LEGACY
  非活动草稿）要求 4.65，错误为 `TRACK_INCIDENT_ACCELERATION_WINDOWS_REQUIRE_SCHEMA_VERSION`；
  缺 Geometry 使用 `TRACK_INCIDENT_ACCELERATION_WINDOW_REQUIRES_GEOMETRY`。
- Inspector、Canvas 和 Runner 只显示来源类型、单位与安全计数。Manifest、Task Result、HTTP API 和
  生产依赖不变。专项验证通过：Contracts 4 项；GraphPlan、Runner 摘要与 Incident Spark 共 82 项
  （Incident Spark 13 项，包含 FIELD_COMPLETE Geometry/时间血缘）；前端定义、类型、Inspector 与
  Canvas 60 项及 TypeScript 检查通过。
- 这只接入受控 TrackAccelerationWindow 聚合；Current/At 后续明确由单观测窗口等价表达，不另建类型。
  Geometry/TrackWindow 数组、任意 Arcade 或真实 Enterprise 作业/容量验收仍未包含；Incidents 完成复选框保持开放。

### Detect Incidents 轨迹速度窗口（2026-09-12，Canvas 4.64）

- 依据 Enterprise 11.3 官方定义和示例，`TrackIncidentWindow.source` 增加 `TRACK_SPEED`。窗口边界仍为
  左闭右开观测偏移；窗口值是逐观测速度，不是范围首尾的平均速度。片段首观测为 0，后续速度为前一
  观测到当前观测的 WGS84 测地距离除以时间差，单位固定为米/秒。
- 同时间观测、NULL Point、前一点缺失或无法形成有效时长时返回 NULL，不执行除零，也不跨轨迹、固定
  边界或 gap 片段。该防御规则是平台确定性约定；首点 0、半开窗口及单位来自官方文档。
- 复用现有九种受控聚合及条件入口；绑定仍不进入最终 Schema。保存端、GraphPlan 和前端导入对任意
  TRACK_SPEED（含 LEGACY 非活动草稿）要求 4.64，错误为
  `TRACK_INCIDENT_SPEED_WINDOWS_REQUIRE_SCHEMA_VERSION`。缺 Geometry 使用
  `TRACK_INCIDENT_SPEED_WINDOW_REQUIRES_GEOMETRY`，非 Point/WGS84 XY 复用既有错误。
- Inspector 在原窗口表格中增加“轨迹速度”，只显示“逐观测速度 · 米/秒”；Canvas 和 Runner 只显示
  安全计数。专项已通过 Contracts 4 项、Task Engine 80 项（其中 Incident Spark 12 项）及 TypeScript；
  Manifest、Task Result、HTTP API 和生产依赖不变。
- 这只接入受控 TrackSpeedWindow 聚合；TrackCurrentSpeed/TrackSpeedAt 后续明确由单观测窗口等价表达，
  不另建类型。Geometry/TrackWindow 数组、任意 Arcade 或真实 Enterprise 作业/容量验收仍未包含。

### Detect Incidents 轨迹距离窗口（2026-09-12，Canvas 4.63）

- 依据 Enterprise 11.3 官方表达式文档，`TrackIncidentWindow` 增加可选
  `source=FIELD|TRACK_DISTANCE`。缺失/null/FIELD 保持 4.46 原字段窗口；TRACK_DISTANCE 对
  `[startOffset,endOffset)` 内各观测的累计轨迹距离求值，单位固定为米。累计值从当前轨迹片段首观测的
  0 开始；官方 `[-1,2)` 示例中的 `[0,60,140]` 是三个逐观测累计值，不是两段距离。
- 轨迹距离要求 EPSG:4326 XY Point；先按轨迹、固定边界/gap 和确定次序分段，再在段内累计，不跨边界。
  NULL Point 及其之后无法证明完整累计距离的观测返回 NULL，直到新片段重新从 0 开始。绑定继续只供
  开始/结束条件使用，不进入输出 Schema、日志或 Canvas 详情。
- 保存端、前端导入和 GraphPlan 对任意 TRACK_DISTANCE（包括 LEGACY 非活动草稿）要求 4.63，错误为
  `TRACK_INCIDENT_DISTANCE_WINDOWS_REQUIRE_SCHEMA_VERSION`。Inspector 在原窗口表格中提供“原始字段/
  轨迹距离”来源切换，显示“累计轨迹距离 · 米”；Canvas 只显示安全计数。
- 专项验证通过：Contracts 4 项；GraphPlan/Runner 安全摘要 67 项；Incident Spark 11 项，覆盖半开区间、
  米制真值、单观测累计值 0、Geometry 门槛、完整 Operator 与 FIELD_COMPLETE 血缘；前端定义/类型/UI 61 项，
  TypeScript、触及文件 ESLint 和 Business 主代码编译通过。Manifest、Task Result、HTTP API 与生产依赖不变。
- 这只接入受控 TrackDistanceWindow 聚合；4.64/4.65 后续已补 Speed/Acceleration Window，三者的
  Current/At 后续明确由单观测窗口等价表达。Geometry/TrackWindow 数组、真实 Enterprise 作业结果、
  全页面或大轨迹容量验收仍待完成；Incidents 完成复选框继续保持开放。

### UNION Merge Layers（2026-09-12，Canvas 4.62）

- `UnionConfiguration` 增加可选 `mergingTables`。缺失/null 保持旧版严格同 Schema；空数组启用默认
  Merge Layers；非空数组只需保存偏离默认行为的字段规则。任一非 null 值在保存、前端导入和 GraphPlan
  三处要求 4.62。
- 第一张输入表是基准层并保留全部字段。后续层按顺序扩展输出 Schema：同名字段默认 Match，其他字段
  默认按原名追加；Match/Rename/Remove 可覆盖默认行为，缺失侧使用具有目标类型和元数据的 NULL。
- Match 允许相同平台类型和数值类型间显式 Cast，拒绝字符串与数值等其他跨类型映射。字段按大小写
  不敏感唯一。空间输入必须均为空间层或均为属性表；Geometry 只能 Match 到基准层类型、CRS、维度
  一致的字段。无界事件时间允许来源字段名不同，但必须 Match 到基准事件时间且 Watermark 一致。
- Inspector 增加灵活/严格切换和逐合并层字段设置 Modal；Match 目标过滤明显不兼容类型，切换动作时
  优先当前合法目标、同名目标或唯一兼容目标。基准层变化和删除表会清理失效字段草稿；保存只写自定义
  规则。Canvas 与 Runner 摘要只显示模式、表数和规则数，不记录字段值。
- Contracts、Compiler/Runner Operator、三端协议门槛、前端 Parser/默认配置和 Canvas 卡片均已接入；
  Manifest、Task Result、HTTP API 和生产依赖不变。专项验证通过：Contracts 1 项；Task Engine 非 Spark
  66 项（GraphPlan 55、Runner 安全摘要 11）；空间算子 Spark 40 项；前端定义与默认配置 52 项；前端
  TypeScript 和触及文件 ESLint 通过。Business 主代码编译通过，其保存端专项仍被工作树中既有 Compute
  Engine/Dispatcher/Kong 的 11 个无关测试编译错误阻断。完整设计见
  [UNION Merge Layers](canvas-union-merge-layers-design.md)。
- 当前仍不宣称完整 ArcGIS 对齐：真实 Enterprise 作业结果、Esri 字段类型细分、官方数值、规模和性能
  对照保持开放。

### Spatial Aggregate 无字段空间连通组（2026-09-12，Canvas 4.61）

- `SpatialAggregateDissolveOptions` 增加可选 `groupingMode`。缺失/null 等同 `ALL_OR_FIELDS`，继续保持
  4.53 的空分组全局 All、非空按字段值 List；任意显式值在保存、前端导入和 GraphPlan 三处要求 4.61，
  包括 `enabled=false` 的非活动草稿。
- `CONNECTED_COMPONENTS` 只允许空 `groupByColumns`、恰好一个 UNION，且 Geometry 元数据必须是
  Polygon/MultiPolygon。运行时过滤 NULL/Empty，校验实际面 Geometry 后用 Sedona `ST_Intersects`
  生成空间候选边，再用已有 GraphFrames 计算无向连通分量；A 接触 B、B 接触 C 时 A/B/C 属于同一组。
  全过程保留分布式 Dataset，不把要素或图收集到 Driver。
- 每个连通分量独立 UNION、来源计数和标量统计；Multipart 每个分量最多一行，Singlepart 继续按
  `ST_Dump` 拆分并重复分量统计。Compiler 使用兼容的零行全局聚合只建立相同输出 Schema，不启动
  Checkpoint、空间连接或图计算。集群 Runner 需要共享 `spark.checkpoint.dir`。
- Inspector 增加“全部/按字段值”和“按相交或接触连通组”选择；字段冲突保留并就地标红。
  Canvas/定义摘要/Runner 安全摘要只展示分组方式和数量，不记录 Geometry、坐标、组成员或统计值。
  Manifest、Task Result、HTTP API 和生产依赖不变。
- 验证通过：Contracts 1 项；Task Engine 非 Spark 97 项（GraphPlan 54、Runner 摘要 10、错误分类 33）；
  空间算子 Spark 35 项；前端定义导入与空间聚合 50 项；Task Engine 主代码、前端 TypeScript、触及文件
  ESLint 与 `git diff --check` 通过；Business 主代码编译通过。其既有无关测试错误不在本项修改。
- 本阶段补齐无字段传递连通组的可执行语义，但不宣称完整 ArcGIS 官方等价：Esri 容差、真实服务数值、
  超大要素图容量和浏览器页面验收仍开放。

### Spatial Join 空间 Near 与距离输出（2026-09-12，Canvas 4.60）

- `SpatialJoinConfiguration` 增加可选 `spatialNear` 和 `distanceOutput`。拓扑 `conditions` 可为空，
  但拓扑与空间 Near 至少配置一种；拓扑、空间 Near、属性和时间条件全部按 AND 组合。任一新增非 null
  对象在保存、前端导入和 GraphPlan 三处要求 4.60，包括不完整或关闭的草稿。
- 空间 Near 独立于拓扑谓词：PLANAR 使用来源 CRS 的二维距离；GEODESIC 仅支持 EPSG:4326 XY 的
  Point/MultiPoint/LineString/MultiLineString/Polygon/MultiPolygon，先使用 ECEF XY 包围与独立 Z 区间
  作保守候选召回，再用共享 WGS84 Geometry 真实最近位置算法作最终包含边界的阈值判断。候选索引不决定
  结果，不使用质心或 Sedona 非点 Spheroid 路径；执行计划专项确认未退化为 Cartesian/BroadcastNestedLoop。
- `distanceOutput.enabled=true` 仅支持 `JOIN_ONE_TO_MANY`。空间 Near 输出空间距离，时间
  NEAR/NEAR_BEFORE/NEAR_AFTER 输出闭区间之间的非负间隔；两种 Near 同时启用时输出两个独立
  `DECIMAL(38,12)` 字段。LEFT 未匹配目标保留，右侧字段和距离字段均为 NULL。
- Inspector 使用两个独立紧凑 Modal，分别配置空间 Near 与距离输出；失效字段和无效组合允许保存草稿，
  一对一时明确提示只能过滤。Canvas 和 Runner 仅展示方法、单位与启用状态，不记录 Geometry 字段、
  阈值、坐标或距离结果。Manifest、Task Result、HTTP API 和依赖不变。
- 专项验证：Contracts 8 项；Task Engine 非 Spark 95 项（GraphPlan 53、Runner 安全摘要 10、错误分类 32）；
  Spatial Join Near Spark 6 项；前端定义导入与 Near 工具 48 项，全部通过。Task Engine/Business 主代码、
  TypeScript 和触及文件 ESLint 通过。Business 保存端门槛测试源码已补；该模块完整 testCompile 仍受工作树
  中既有 Compute Engine/Dispatcher/Kong 无关测试错误影响，本阶段不修改这些测试。
- 本阶段完成 Join Features 的空间 Near、Near Geodesic 和一对多距离输出参数接入，不等价完整官方验收；
  Enterprise 作业结果、Esri 数值容差、复杂线面规模和跨全球域能力仍按路线图继续验证。

### Spatial Join 时间关系（2026-09-12，Canvas 4.59）

- `SpatialJoinConfiguration` 增加可选 `temporalCondition`。缺失/null 保持旧空间连接；非 null 对象在保存、
  前端导入和 GraphPlan 三处要求 4.59，包括不完整的非活动草稿。
- 时间关系覆盖 `EQUALS/INTERSECTS/DURING/CONTAINS/FINISHES/FINISHED_BY/MEETS/MET_BY/OVERLAPS/
  OVERLAPPED_BY/STARTS/STARTED_BY` 及 `NEAR/NEAR_BEFORE/NEAR_AFTER`。方向固定以左侧目标表为主体；
  每侧结束字段为 null 时按瞬时处理，非 null 时按闭区间处理。
- 四个时间字段必须是同一种 `DATE/TIMESTAMP/TIMESTAMP_NTZ`。NULL 或开始晚于结束的记录不匹配；
  LEFT 仍保留未匹配目标。Near 使用毫秒至固定周的受控正整数时长，日固定为 24 小时、周固定为 7 日。
- Operator 使用 Spark Column 组合时间表达式，并与全部空间谓词及属性等值条件按 AND 连接；Compiler
  只构造惰性计划。Inspector 使用独立紧凑 Modal，支持瞬时/区间、失效字段回显和无效草稿保存；Canvas
  与 Runner 安全摘要只显示关系枚举，不记录字段名、阈值或实际时间值。
- Manifest、Task Result、HTTP API 与依赖不变。空间距离 Near/容差、`includeDistance`、ArcGIS 官方服务
  结果对照和大规模性能验收仍开放，因此不声明整个 Join Features 已完成对齐。
- 最终专项验证：Contracts 7 项通过；Task Engine 非 Spark 61 项通过（GraphPlan 52、Runner 安全摘要 9），
  空间 Spark 39 项通过（原空间节点回归 33、时间关系专项 6）；前端时间关系 Modal、定义导入和 Canvas
  摘要相关 64 项通过。TypeScript、触及文件 ESLint 与 `git diff --check` 通过。Business 保存端 4.59
  门槛用例已补；该模块完整 `testCompile` 仍被工作树中既有 Compute Engine/Dispatcher/Kong 无关测试的
  11 个编译错误阻断，本阶段未修改这些测试，也不将 Business 专项声明为已运行通过。

### Spatial Join 一对一汇总与确定性保留（2026-09-12，Canvas 4.58）

- `JOIN_ONE_TO_ONE` 增加 `SUMMARIZE_MATCHES` 与 `KEEP_ONE`。汇总模式为每个左侧目标输出一行，Join Count
  统计全部匹配项；可追加至多 32 项右表数值 `SUM/MIN/MAX/MEAN/STDDEV`，统计忽略 NULL，STDDEV 使用
  样本标准差。LEFT 未匹配目标的 Join Count 为 0，统计结果为 NULL；汇总时右侧原字段不能直接投影。
- 保留模式支持 FIRST、数值最大/最小及日期最新/最旧。FIRST 完全由 `stableOrder` 定义，其余策略先按
  主字段排序，再以稳定顺序消除并列；完整排序仍并列时真实执行返回
  `SPATIAL_JOIN_KEEP_ORDER_NOT_UNIQUE`，不使用分区顺序任取一条。
- Inspector 使用独立一对一设置 Modal；Canvas、定义摘要和 Runner 安全摘要只显示模式、策略和数量，
  不记录 Geometry、排序字段值或统计数据。配置及非活动草稿在保存、导入和 GraphPlan 三处要求 4.58。
- Manifest、Task Result 与 HTTP API 不变。本阶段仍不包含时间关系、Near/容差、ArcGIS 官方服务结果及
  大规模性能验收，因此不声明整个 Join Features 已完成对齐。
- 最终专项验证：Contracts 5 项通过；Task Engine 非 Spark 90 项通过（GraphPlan 51、Runner 安全摘要 8、
  错误分类 31），空间 Spark 33 项通过；前端定义导入、Registry 和 Inspector 三文件 60 项通过。
  后端主代码、TypeScript、触及文件 ESLint 与 `git diff --check` 通过。Business 保存端 4.58 门槛用例
  已补；该模块完整 `testCompile` 仍被工作树中既有 Compute Engine/Dispatcher/Kong 无关测试的 11 个
  编译错误阻断，本阶段未修改这些测试。

### Spatial Join 显式一对多粒度（2026-09-12，Canvas 4.57）

- `SpatialJoinConfiguration` 增加可选 `joinOperation`，当前只接受 `JOIN_ONE_TO_MANY`。缺失/null 与显式值
  都输出每个匹配组合，兼容 4.56 及更早定义；非 null 配置在保存、前端导入和 GraphPlan 三处要求 4.57。
- Inspector 显示紧凑“连接粒度”并明确一对多含义；后续一对一能力见 Canvas 4.58。Canvas 使用 `1:N`，
  定义摘要使用“一对多”，Runner 安全摘要只记录枚举名称，不记录 Geometry、坐标或数据值。
- 本阶段没有用任意 `first()` 实现伪一对一；后续 4.58 按“汇总全部匹配记录”与“按确定规则保留一条记录”
  分开建模，并要求显式稳定排序。
- Manifest、Task Result 与 HTTP API 不变。最终专项验证：Contracts 4 项通过；Task Engine 共 86 项通过
  （GraphPlan 50、空间 Spark 29、Runner 安全摘要 7）；前端定义导入、Registry 和 Inspector 三文件
  59 项通过。后端主代码、TypeScript、触及文件 ESLint 与 `git diff --check` 通过。Business 保存端
  4.57 门槛用例源码已补，主代码编译通过；该模块专项仍被工作树中既有 Compute Engine/Dispatcher/Kong
  无关测试的 11 个编译错误阻断，本阶段未修改这些测试。

### Spatial Join 保留全部目标要素（2026-09-12，Canvas 4.56）

- `joinType=LEFT` 将左表明确为目标要素、右表明确为连接要素，并对应 Join Features 的
  Keep all target features；4.55 及更早的 `INNER` 结果不变。LEFT 在保存、前端导入和 GraphPlan
  三处要求 4.56，`RIGHT/FULL` 继续返回 `SPATIAL_JOIN_TYPE_UNSUPPORTED`。
- 运行时使用受控 LEFT 空间连接。未匹配目标保留一行，右侧显式投影字段为 NULL 且 Schema 标记可空；
  一个目标匹配多个连接要素时仍输出全部组合，不隐式转成一对一或执行统计。
- Inspector 使用“目标表 / 连接表”和紧凑结果范围选择；Canvas、定义摘要与 Runner 安全摘要显示
  INNER/LEFT，不记录 Geometry、坐标或数据值。Manifest、Task Result 与 HTTP API 不变。
- 最终专项验证：Contracts 3 项通过；Task Engine 共 85 项通过（GraphPlan 49、空间 Spark 29、Runner
  安全摘要 7）；前端定义导入、Registry 和 Inspector 三文件 58 项通过。后端主代码、TypeScript、
  触及文件 ESLint 与 `git diff --check` 通过。Business 保存端 4.56 门槛用例源码已补，主代码编译通过；
  该模块完整 `testCompile` 仍受工作树中既有无关测试问题影响，本轮未修改这些测试。
- 本项只补 Keep all target features；显式一对多模式后续已在 4.57 建模，时间关系、Near/容差、一对一统计及官方结果/
  规模对照仍开放，不将其标记为完整 Join Features 验收。

### Spatial Join 属性等值组合（2026-09-12，Canvas 4.55）

- `SpatialJoinConfiguration` 增加可选 `attributeConditions`。缺失/null 保持旧空间连接；非 null 数组在
  保存、前端导入和 GraphPlan 三处要求 4.55，最多八项。
- 每项复用普通 Join 的左右字段等值语义，禁止 Geometry；使用 Spark SQL 普通等号，并与全部空间谓词按
  AND 组合。字段比较能力由 Spark Analyzer 判断，Compiler 不读取数据、不另建类型兼容矩阵。
- Inspector 使用紧凑单行字段对，可保留失效字段草稿；Canvas 与 Runner 只展示属性条件数量，不记录字段名
  或数据值。[官方 Enterprise 11.3 Join Features](https://enterprise.arcgis.com/en/portal/11.3/use/join-features.htm)
  明确支持空间关系、属性关系或两者组合，本阶段只接入“与空间关系组合”；纯属性连接继续使用普通 `JOIN`。
- 最终专项验证：Contracts 3 项通过；Task Engine 共 82 项通过（GraphPlan 48、空间 Spark 27、Runner
  安全摘要 7）；前端定义导入、Registry、字段建议、Inspector 和类型五文件 64 项通过。后端主代码、
  TypeScript、触及文件 ESLint 与 `git diff --check` 通过。Business 保存端 4.55 门槛用例源码已补，
  但该模块 `testCompile` 仍被与本项无关的 Compute Engine/Dispatcher/Kong 旧测试 11 个编译错误阻断，
  未修改这些无关测试，也不将保存端专项声明为已运行通过。
- 本项仍是 Join Features 的增量，不新增时间关系、Near/容差、Keep all、一对一统计、一对多选择或官方结果对照。

### Spatial Join 显式输出字段投影（2026-09-12，Canvas 4.54）

- `SpatialJoinConfiguration` 增加可选 `outputColumns`。缺失/null 保持旧版左表全字段后接右表全字段，
  并在同名时拒绝；任何非 null 数组在保存、前端导入和 GraphPlan 三处要求 4.54。
- 显式投影复用普通 Join 校验，按配置顺序使用限定来源的 `select + alias`；支持排除、改名和排序，
  拒绝重复来源、失效字段、空结果及大小写不敏感的最终重名。
- Inspector 使用独立设置 Modal；首次选齐左右表时按“左表原名、右表重名用完整逻辑表名前缀”生成建议，
  已有投影不自动重建，旧版投影须由用户明确启用。Canvas 与 Runner 只记录输出字段数量。
- 最终专项验证全部通过：Contracts 3 项；Task Engine 共 79 项（GraphPlan 47、空间 Spark 25、Runner
  安全摘要 7）；前端定义导入、Registry、字段建议和类型四文件 62 项通过，TypeScript、触及文件 ESLint、
  Business 主代码编译与 `git diff --check` 通过。Spark 首轮仅因沙箱禁止 Driver 绑定回环端口而未进入用例，
  在允许绑定本机端口的环境以相同测试集合重跑后全部通过。
- 本项只补 Join Features 的字段投影差距；仍仅支持 INNER、九种空间谓词和 AND 条件，不新增属性/时间关系、
  一对一统计、一对多选择、容差或 ArcGIS 官方结果对照。

### Spatial Aggregate Dissolve 输出（2026-09-12，Canvas 4.53）

- `SpatialAggregateConfiguration` 增加可选 `dissolve`；缺失/null 保持基础聚合，任意非 null 对象
  在保存、前端导入和 GraphPlan 三处要求 4.53，包括 `enabled=false` 的非活动草稿。
- 启用时要求恰好一个 UNION。空分组对应 Create Buffers All，非空分组对应 List；结果始终增加
  来源要素计数，并可配置 COUNT_FIELD、SUM、MEAN、MIN、MAX、RANGE、STDDEV、VARIANCE、ANY。
- Multipart 使用 `ST_Multi`；Singlepart 使用 `ST_Dump` 并为各部件重复组统计。NULL/Empty UNION
  结果组不产生要素，混合组计数仍按融合前来源行计算；Compiler 只构造计划，不触发 Spark Job。
- Inspector、Canvas 和安全摘要已接入，禁用时安全摘要不显示隐藏统计草稿数量；不记录统计值、Geometry
  或坐标。当前实现也可与 Geometry Buffer 组合，不新增重复 Processor。
- 最终验证：Contracts 1 项、GraphPlan 46 项、空间 Spark 23 项、Runner 安全摘要 6 项通过；
  前端 Dissolve/定义导入/默认配置/Registry 四文件 62 项通过，TypeScript 和触及文件 ESLint 通过。
  Spark 沙箱首轮因 Driver 不能绑定回环端口而未进入用例，在允许绑定本机端口的环境重跑后全部通过。
  Business 主代码编译通过；保存端门槛测试源码已补，但 Business `testCompile` 被本轮无关的
  Compute Engine/Dispatcher/Kong 旧测试 11 个编译错误阻断，未修改这些无关测试，也不将保存端专项声明为通过。
- 本项对齐的是 Create Buffers 的 Dissolve 参数组合，不等价完整 Dissolve Boundaries：无字段的相交/
  重叠连通组、Esri 容差、官方服务数值和大规模容量对照仍开放。负 Buffer 和样式不是官方对齐优先项。

### Geometry Buffer 逐行距离来源（2026-09-12，Canvas 4.52）

- 新建节点默认 `CONSTANT`；旧缺失/null 来源继续使用固定 `distance`。`FIELD` 读取数值字段，
  `EXPRESSION` 使用受控确定性 Spark 数值表达式；任一新增非 null 字段在保存、导入和 GraphPlan
  三处要求 4.52，包括非活动草稿。
- 三种来源共用 4.49 的单位换算和 PLANAR/SPHEROID 规则。Compiler 只验证逐行数值标量，
  不执行表达式或扫描数据；动态 NULL 保留行并输出 NULL Buffer，非正、NaN、Infinity 或溢出在
  Runner 以 `GEOMETRY_BUFFER_DISTANCE_VALUE_INVALID` 安全失败。
- Inspector 保留隐藏分支草稿并提供字段/表达式帮助；Canvas 与安全摘要不展示表达式正文或逐行值。
  负 Buffer、样式、Dissolve、官方数值和页面验收仍开放，本项不宣称完整 Create Buffers 对齐。
- 最终专项验证全部通过：Contracts 1 项、GraphPlan 45 项、Runner 错误分类 30 项、空间 Spark 20 项、
  前端 Geometry Buffer/Spatial Clip/Registry 20 项；Task Engine/Business 主代码编译、前端 TypeScript、
  触及文件 ESLint 与 `git diff --check` 通过。Spark 首轮仅因沙箱禁止 Driver 绑定回环端口而未进入用例，
  在允许绑定本机端口的环境以相同集合重跑后 20 项全部通过。Business 保存端门槛测试源码已同步，
  但整个 Business testCompile 仍被工作树中与本项无关的 Compute Engine/Gateway 旧测试构造器阻断。

新增非 null hdbscan 在保存端、GraphPlan、导入端均要求 4.45，包括非活动草稿。

### Spatial Clip 来源家族输出（2026-09-12，Canvas 4.51）

- 新建节点默认 `SOURCE_FAMILY_2D`，只接受明确的点、线、面来源类型；结果使用
  `ST_Force2D + ST_CollectionExtract + ST_Multi` 输出对应 MultiPoint/MultiLineString/MultiPolygon + XY。
- 仅在 Mask 边界接触产生的低维片段被过滤；多条 Mask 仍逐对输出，不自动 Union/Dissolve。
- 缺失/null 及显式 `LEGACY_ANY_DIMENSION` 保持旧通用 Geometry 结果，不改变旧任务数值；任意非 null
  策略在保存、导入和 GraphPlan 三处要求 4.51。
- Inspector、Canvas 和安全摘要展示有效策略，不展示 Geometry 或坐标。Esri 容差、吸附、Mask 重叠
  的官方边界行为及规模验收仍开放，不将本项标记为完整 Clip Layer 对齐。
- Contracts 往返 1 项、Task Engine GraphPlan 44 项与空间 Spark 18 项、前端导入专项 2 项均通过；
  Spark 覆盖线边界接触的新旧差异及点/线/面三个 Multi 结果家族。Task Engine/Business 主代码、
  前端 TypeScript、触及文件 ESLint 与 `git diff --check` 通过。Business 专项测试仍被工作树中与本项
  无关的 Compute Engine/Gateway 旧测试构造器阻断，保存端门槛测试源码已同步但未能单独执行。

### Spatial Measure 显式输出单位（2026-09-12，Canvas 4.50）

- AREA 增加可选 `SpatialAreaUnit outputUnit`；LENGTH/PERIMETER/DISTANCE 增加可选
  `SpatialDistanceUnit outputUnit`。X/Y 仍返回坐标轴原值，不携带单位配置。
- 缺失/null 保持旧数值：PLANAR 为来源 CRS 单位（面积为平方），SPHEROID 为米/平方米；任何显式值
  在保存、导入和 GraphPlan 三处要求 4.50，并精确定位到测量项。
- 投影 PLANAR 通过 CRS 第一轴线性单位可靠换算；地理 PLANAR 不把角度/角度平方近似为固定单位。
  SPHEROID 从米/平方米换算，并拒绝线性项的 `SOURCE_CRS_UNIT`。不转换 Geometry CRS。
- Inspector 将 mode 与输出单位同行展示，失效组合保留并标红；Canvas 与安全摘要显示有效单位，
  Schema 仍为 nullable DOUBLE，不虚构字段单位元数据。
- Contracts 往返 1 项、Task Engine GraphPlan 43 项与空间 Spark 17 项、前端导入专项 2 项均通过；
  Task Engine/Business 主代码、前端 TypeScript 与触及文件 ESLint 通过。Spark 用例覆盖 Web Mercator
  平方千米、WGS84 椭球千米、null 米兼容及两类失效组合。本项不增加 Spark Action、Manifest、Result、HTTP API 或生产依赖。

### Geometry Buffer 显式距离单位（2026-09-12，Canvas 4.49）

- `GeometryBufferConfiguration.distanceUnit` 增加公共距离单位；缺失/null 保持旧 PLANAR 来源 CRS 单位、
  SPHEROID 米语义，显式值在保存、导入和 GraphPlan 三处要求 4.49。
- PLANAR 对投影 CRS 将固定线性单位换算到来源轴单位；地理 CRS 只允许 `SOURCE_CRS_UNIT` 并提示角度。
  SPHEROID 只接受 EPSG:4326 XY，将明确线性单位换算为米并拒绝 `SOURCE_CRS_UNIT`。
- Inspector 将数值和单位同行展示，Canvas/安全摘要显示用户配置单位；切换模式/单位不修改数值，
  失效组合保留并交给 Compiler 返回稳定问题。不转换 Geometry CRS，不增加 Spark Action、依赖、Manifest/Result/API。
- 本项仅完成现有 Buffer 的单位复核；字段/表达式距离、负 Buffer、样式参数、Dissolve、官方数值与页面验收仍开放。

### Nearest 非点 WGS84 显式接入（2026-09-12，Canvas 4.48）

- `SpatialNearestMatching` 增加 `geodesicGeometryMode`。缺失/null 使用 POINT_ONLY；显式 GEOMETRY 要求 4.48，
  包括 LEGACY_KNN 下的非活动草稿。新建节点默认 GEOMETRY，旧任务结果不变；Manifest/Result/HTTP 不变。
- GEOMETRY 支持 EPSG:4326 XY Point/MultiPoint/LineString/MultiLineString/Polygon/MultiPolygon；
  GeometryCollection 明确拒绝，通用 GEOMETRY 在 Executor 检查实际类型。补齐双方同为非 XY 时的编译拒绝。
- 局部 CROSS 使用 GeographicLib Gnomonic 迭代并复核有限弧投影残差，成功时返回真实公共位置；不稳时
  保留 Boolean 事实而不编造坐标。单次 Match 同时供最终距离、半径、Top N 和连接线使用。
- ECEF 三轴仅召回，最终始终为 WGS84；无业务半径也保持空间 Join，不引入笛卡尔积。半径跨区间、Top N
  未决重叠、局部 Polygon 域外、预算或精度不足均安全失败，不回退质心。
- Task Engine 隔离专项最终 173 项全部通过：Nearest 类型支持 3、边界相交 7、Geometry 包围 4、测地公共交点 3、
  Geometry 距离 7、单次 Match 4、空间 Processor Spark 98、弧拓扑 6、GraphPlan 41；失败、错误、跳过均为 0。
  覆盖日期线 Line/Line 真实交点、Polygon 内含与孔洞、MultiPoint/MultiLineString、Point-only 兼容拒绝、
  GeometryCollection 拒绝、双方 XYZ 拒绝及 4.48 版本门槛。测试辅助构造器首轮多一个右括号导致 testCompile 失败，
  仅修正测试语法后原集合通过，没有为满足断言放宽生产算法。
- Contracts 枚举专项 1 项、前端导入/默认值专项 3 项、前端 TypeScript 与触及文件 ESLint 均通过；
  Business 与 Task Engine 主代码编译通过。Business 测试源码仍被本次未修改的 ComputeEngine/Dispatcher/Kong
  旧构造器阻断，因此新增保存端门槛用例尚未实际运行，不以 Engine GraphPlan 门槛替代其证据。
- 同版本继续拆除 MultiPolygon 的不必要共同参考域限制：原共同局部域路径优先；仅在其范围失败时，逐部件共享
  一个总工作预算验证各自的连续环/孔洞，并要求任意跨域部件的边界严格分离且双向非包含均可证明。
  两个近乎对跖的小面部件现在可以作为同一 Geometry 参与 ECEF 召回和 Nearest；跨域接触、相交、包含和未决关系
  仍失败，不使用 JTS 经纬平面区域或质心补判。补充 Executor 后最终相关回归 113 项通过：Region 8、Geometry
  Distance 7、空间 Processor Spark 98；此前同一路径含 Polygon Topology 8 的 121 项回归也通过，不累计为新增总数。
- Point/MultiPoint 双方为有限点集时，`Wgs84SegmentDistance` 不再达到连续求解容差即停止，而是继续消费同一
  ECEF 分层队列，直到所有可能更优/同距的点对均已求值或由保守下界排除；此时 Match 的上下界相同并标记
  `exactDistance`。树、点对和验证仍共享 25 万次预算，线/面仍保留区间。最终 125 项通过：Segment 11、
  Hierarchy 6、Linear 5、Match 4、空间 Processor Spark 99；Spark 用例覆盖相差约 0.011 毫米的两个 MultiPoint
  候选正确排序，失败、错误、跳过均为 0。
- 使用 ArcGIS Online 官方 GeometryServer `distance`（EPSG:4326、`geodesic=true`、米）取得五个非敏感人工样本：
  Point→Line 1105.7427583286865、日期线 Line→Line 0、面内点 0、孔洞内点 110.57427575225489、全球严格分离
  MultiPolygon 部件内点 0；固化为不访问公网的离线真值测试，1 项通过。此处只对照底层 Geometry 距离，
  不将它称为 Enterprise Find Nearest 完整作业、排名、字段或容量验收。
- 4.48 最终合并回归一次运行 220 项全部通过：上述距离/拓扑/包围/区域/官方真值原语、GraphPlan 41、
  空间 Processor Spark 99；失败、错误、跳过均为 0。合并运行未访问数据库或 ArcGIS，官方响应只作为离线常量。
- 本阶段当时未勾选完整 Nearest；2026-09-13 已补日期线 Polygon、连续近等距严格失败、256×1024 索引样例
  和真实页面验收，并按明确支持/拒绝边界完成收口，见上方最新记录。

### 非点测地距离分层边对搜索（2026-09-12，仍为 4.47）

- `Wgs84SegmentDistance` 不再预展开所有边对：两侧保守 ECEF 树对与原弧长区间共用一个下界队列，
  叶对才创建完整参数域搜索。父下界传播、真实位置上界、严格阈值和全域误差停止规则保持；
  建树、展开与取样共用工作预算，来源数据不修改，不新增 Spark Action、配置或依赖。
- 弧盒改为端点弦各轴范围加曲率/舍入裕量，覆盖原始连续弧；原拓扑索引复用此保守盒，不修改连续关系规则。
  高重叠情况下仍可能二次展开，不声称解决跨表索引、全球面域或官方数值对齐。
- 首轮 `/tmp/datascalpel-geodesic-hierarchy.log` 65 项中 1 项失败：新增测试误把距离误差保证当成精确端点位置。
  实际结果处于有限原始弧上且毫米距离区间已通过；修正为原弧位置检查，保留原距离区间和数值断言，未改生产求解。
  修正专项 `/tmp/datascalpel-geodesic-hierarchy-corrected.log` BUILD SUCCESS，65 项通过，不累计重复运行。
- 新增两侧各 2049 边、20,000 工作预算的约 420 万组合反例；最近位置位于最后一对边内部，
  并验证 10/12 米阈值两侧、小集合穷举对照、排列/交换、全球弧包围与完整校验。
  另外增加真实 Spark 多部件测距、阈值及 Analyzer 零 Job 用例。
- 首次扩展回归 `/tmp/datascalpel-geodesic-hierarchy-regression.log` 共报告 117 项：115 项非 Spark 用例通过，
  两个 Spark 测试类在沙箱内因 Driver 端口绑定权限失败，未进入测试方法，不把该轮记作算法失败或通过。
  使用相同测试集在允许绑定本机端口的环境重跑，219 项全部通过：共享 WGS84/轨迹原语 115、
  GeodesicDistance Executor 10、空间 Processor Spark 94；失败、错误、跳过均为 0。
  最终命令正常退出，未访问外部数据库；本轮不累计前述 65 项重复子集。
- 更新共享距离设计、Nearest 和路线图；本轮不放开 Nearest 非点 EXACT_DISTANCE。
  跨表召回、区间排名、公共交叉位置、全球域、页面与官方验收继续保留，12 个节点完成框不勾选。

### Nearest ECEF 三轴保守候选召回（2026-09-12，仍为 4.47）

- 新增 `Wgs84GeometryBounds`：连续弧使用弦端点范围与曲率裕量；面区域另外检查六个椭球笛卡尔轴极值，
  形成覆盖真实线/面内部的 ECEF XY 包围 Geometry 与 Z 区间。NULL/Empty 保留，非法坐标、集合和区域
  继续返回既有安全错误；不使用质心、采样经纬包围或数据值日志。
- Point EXACT_DISTANCE 的测地候选改用三轴包围。配置半径直接保守召回；无半径的二维 KNN 仅生成
  一个真实 WGS84 距离上界，再恢复全部三轴可能候选，最终距离筛选和排名仍用 `ST_DistanceSpheroid`。
  极区反例中二维 ECEF 最近种子接近对跖，真正最近点在二维更远；有/无半径都恢复正确候选，
  执行计划保持 DistanceJoin/BroadcastIndexJoin，不出现 CartesianProduct/BroadcastNestedLoopJoin。
- 包围盒专项 4 项连同 Geometry 距离/分层搜索共 17 项通过；空间 Processor Spark 回归 95 项通过，
  失败、错误、跳过均为 0。覆盖高纬、日期线、对跖弧、面内部轴极值、孔洞、非法末项及极区召回。
- 编译仅构造 UDF 与空间 Join 并由 Analyzer 验证，没有额外 Spark Action、协议、Manifest、Result、HTTP、
  前端或依赖变更。非点门槛仍保留；距离/位置一次求解、公共交叉位置和区间排名是下一步，不勾选 Nearest。

### Nearest 单次匹配 Struct 与区间判定（2026-09-12，仍为 4.47）

- 新增 `Wgs84NearestMatch`，单次返回取样距离、真实最小距离上下界、两侧最近位置、明确零距离证据和确定距离标记。
  单 Point–Point 与真实共同位置采用确定数值；MultiPoint、线/面保留数值区间，不把小距离或 Boolean 相交事实伪造成交点。
- 当前 Point EXACT_DISTANCE 已改为使用该 Struct：无半径种子上界、最终距离、半径筛选、Top N 检查与连接线端点
  使用同一种 WGS84 求解结果。连接线只加密 Struct 的位置对并切分日期线，不再独立求最近位置；ECEF 仍仅负责召回。
- 半径只在区间可证明落在一侧时决定；Top N 的每个入选项与全部后续候选最小下界比较，避免仅检查相邻项时
  漏掉“取样上界较大、真实下界很小”的后续候选。确定点距离可继续按候选 ID 处理同距，非确定区间重叠返回
  `GEODESIC_DISTANCE_PRECISION_NOT_REACHED`，不以取样顺序猜测。
- 首轮纯算法 17 项中 2 项失败，原因是旧辅助测试传入 SRID=0，而新连接线入口按 Struct 的 4326 约束提前拒绝。
  修正旧兼容入口在测地模式下明确规范化为 4326，未放宽 Struct 入口；补入 MultiPoint 不得标记为确定距离的反例后，
  最终纯算法 18 项全部通过。
  最终空间 Processor Spark 回归 95 项通过，失败、错误、跳过均为 0；包含 Analyzer 零 Job、极区召回、日期线连接、
  同距 ID 排序和无笛卡尔积计划。本轮未访问外部系统，没有协议、Manifest、Result、HTTP、前端或依赖变更。
- 非点纯算法 Struct 已覆盖点到线、面内含及交叉区间，但已知非点类型和通用 Geometry 的运行门槛仍保留。
  公共交叉位置、局部域之外的面关系、复杂近等距预算、页面/官方/规模验收继续在完整范围内，不勾选 Nearest。
  本段记录 4.47 当时的门槛；4.48 当前已开放受控非点，并已将有限 Point/MultiPoint 对升级为精确离散最小值，见上方当前执行记录。

### HDBSCAN 已知归属中间文件清理（2026-09-08，仍为 4.47）

- 全链路显式传递 Checkpoint Scope；最终结果先可靠物化再保留，成功/失败退出清理已登记中间文件。
  不切换共享目录、不使用 ThreadLocal、不增加用于观测的 count/collect，不把路径/文件系统异常写入日志。
  使用弱身份引用登记可返回的快照，避免文件清单把所有旧执行计划强引用到节点结束。
- 首轮 `/tmp/datascalpel-hdbscan-checkpoint-scope.log` 未通过：Spark 的 getCheckpointDir 只有成功后才返回。
  核对当前 Spark 字节码后，使用其自身 checkpointPath 分配函数，在原 doCheckpoint 前登记本次 RDD 路径，
  不猜文件名。修正后的 `/tmp/datascalpel-hdbscan-checkpoint-allocated.log` 3 项通过；原全链路
  `/tmp/datascalpel-hdbscan-owned-lifecycle.log` 50 项通过，不累计为最终新增覆盖。
- 完整节点实际文件检查 `/tmp/datascalpel-hdbscan-checkpoint-node.log` 发现真实遗漏：预期外来输入 + 最终结果 2 份，
  实际 5 份；多出的 3 个目录来自 GraphFrames 的迭代 Parquet。没有放宽“只新增最终结果”断言。
  改为依据成功图结果引用的文件根/RDD Checkpoint 接管明确目录，先排除 vertices/edges 的存储依赖，
  不按共享目录前后差集或名称扫描删除。补入外来同前缀标记文件后，4 项专项通过。
- 新增外来 Parquet 安全反例后，`/tmp/datascalpel-hdbscan-checkpoint-final.log` 共 53 项，1 项真实失败：
  输入的 raw logical 仍为 UnresolvedDataSource，图投影已含已解析文件关系，导致输入依赖漏记。
  修正为两侧都读取 Analyzer 后的计划；保留原“不误删”断言并增加归属分析零 Job 检查。
  不将该失败轮记作通过。
- 修正后的最终回归 `/tmp/datascalpel-hdbscan-checkpoint-analyzed.log` 为 BUILD SUCCESS，53 项通过，
  失败、错误、跳过均为 0：SpanningTree 16、EOM 4、Checkpoint 归属 6、Hierarchy 8、Point/DBSCAN 12、
  CondensedTree 7。包含外来 Parquet 不误删、图阶段完成后失败清理、最终结果重复读取及归属分析零 Job；
  不累计此前重复运行。本轮验证进程已正常结束，未执行外部数据库写入，没有新增 UI 或协议变更。
- 同步节点设计、定义说明与路线图。只承诺已知归属路径的最佳努力清理；GraphFrames 内部未返回前的失败、
  强杀/存储故障残留、运行中磁盘峰值及最终结果的应用级回收仍待完善。无协议、UI、依赖或 HTTP 变更，
  完整 Cluster 和 12 节点开发目标保持开放。详见[聚类第 15 节](canvas-spatial-next-processors/spatial-point-cluster.md#15-hdbscan-中间-checkpoint-的显式归属与清理)。

### 4.47 固定时长周及 Motion 缺失边界（2026-09-08）

- 核对官方 Motion REST 时长单位表，补齐固定周，不据此假定固定月/年的秒数。
  Contracts/UI 同步到 4.47；Manifest/Result/HTTP 不变。`WEEKS` 为 604800 秒，UI 显示“周（固定 7 天）”。
  固定日历边界与 Bins/Within 日历周保留原语义，跨 DST 可以不等于 168 小时。
- 显式类型门槛贯通保存、GraphPlan、前端导入，覆盖七类节点的全部固定时长字段，包括隐藏草稿。
  低版本返回 `SPATIAL_DURATION_WEEKS_REQUIRE_SCHEMA_VERSION` 及精确路径；不扫描任意字符串，日历周不误拦截。
  段间隔、时长输出、Idle 和 Linear DBSCAN 沿用原严格/包含阈值；微秒乘法保留溢出错误。
- Motion 缺失值三专项 `/tmp/datascalpel-motion-missing-boundaries-corrected.log` 已通过。
  首轮测试把可空时间/WKT 声明成非空导致 Spark 优化错误；修正测试 Schema 后保持原断言通过，未改生产算法。
  覆盖不跨 NULL/Empty Geometry 连接、均速有效分母、非有限高程仅测量置 NULL、窗口 2 的瞬时/窗口加速度区别，
  NULL 时间排除、3 分区 Shuffle 下轨迹隔离和 Analyzer 零 Job。
- 固定周第一轮因测试 JSON 缺少 primitive 必填值失败，第二轮因测试缺失断言引用未完成编译；
  均修正测试输入/引用，不关闭反序列化严格校验、不减少断言。
  初步专项 `/tmp/datascalpel-fixed-weeks-verified.log` 为 BUILD SUCCESS，58 项通过（Contracts 6 + Engine 52），
  含固定周 Motion/DBSCAN 真执行、GraphPlan 精确路径及 DST 日历周数值边界；不与后续重复运行累计。
- 最终受影响节点回归 `/tmp/datascalpel-fixed-weeks-regression.log` 为 BUILD SUCCESS，共 162 项通过：
  Contracts 6、空间 Spark 94、Point/DBSCAN 12、日历 10、GraphPlan 40。
  新增固定周切片真实 Spark DST 对照：同一观测在固定周仍属上一窗、日历周已属下一窗；两种计划均为 Analyzer 零 Job。
  本轮不累计初步专项或此前重复运行。
- 最终前端六文件 27 项通过 `/tmp/datascalpel-fixed-weeks-ui-regression.log`，
  覆盖所有固定时长路径、非活动草稿、低版本精确错误、日历周兼容、单位切换保留数值及既有轨迹/DBSCAN 配置。
  TypeScript `/tmp/datascalpel-fixed-weeks-tsc.log` 和触及文件 ESLint `/tmp/datascalpel-fixed-weeks-eslint.log`
  均正常退出且无错误；JSDOM 提示不作为真实页面视觉证据。
- Business 主代码编译 `/tmp/datascalpel-fixed-weeks-business.log` 为 BUILD SUCCESS；跳过测试类编译，
  新增保存端门槛用例未执行，不把主代码编译当成该用例通过。所有本轮验证进程已结束，未访问外部数据库。
- 同步稳定定义、路线图、共享单位及七篇受影响节点设计，标明月年/厂商别名/官方服务数值仍待核实。
  本增量不代表 12 节点完成，不勾选整节点或完整路线图验收。

### 中心统计零权重数值隔离（2026-09-08，仍为 4.46）

- 官方 GA REST 页面再次核对后仍无法确定时间权重、偶数中位数和 ellipseSize 的时间参与集合，
  没有据此猜测并新增协议。已向用户询问是否有可对照样例/服务；这不阻止其他明确能力继续开发。
- 发现独立结果模式的零权重点参与数值缩放，极远点会将有效支持点归一化为相同坐标。
  基线 `/tmp/datascalpel-center-zero-weight-baseline.log` 为真实失败：8 项中 1 项失败，期望均值约 0.6667、实际 0。
- `CenterGeometryStatistics` 仅用正权重支持点确定坐标框，平均/中位/停止半径/离散矩在运算前跳过零权重。
  零权重仍是中央要素候选且计入容量限制，不从原表或候选中整体删除；极远非有限候选分数不进入 ULP 平局。
  保留一个附近零权重中心最终获选的反例，防止错误过滤实现。
- 修正后内核 8 项通过；最终 `/tmp/datascalpel-center-zero-weight-verified.log` 为 BUILD SUCCESS，共 97 项：
  数值内核 8 + 空间 Spark 89。新增真实 Spark 用例经 3 分区 Shuffle，验证五种分析结果、零权重获选者原字段回接、
  Map 顺序、原表保留及预检/Analyzer 零 Job；原多结果血缘与字段投影回归继续通过。
  不累计前置与重复运行；本轮进程全部结束，无外部数据库访问或写入。
- 同步节点设计与稳定定义说明，旧宽表和所有协议版本不变，无 UI 变更，不重复运行前端测试。
  仍未实现或验证统计时间、官方椭圆加权公式、整体规模/页面验收，完整路线图保持未完成。

### 几何简化容差数值与单几何规模回归（2026-09-08，仍为 4.46）

- 先补大折线用例，`/tmp/datascalpel-unary-scale-baseline.log` 的 10 项通过：50,001 顶点平滑线逐顶点满足容差，
  两算法保留 XYZ/端点且不修改原几何；8,001 顶点交替折线的 DP 无栈溢出。未改 JTS 算法或增加顶点截断。
- 容差换算边界专项先得到真实失败：`/tmp/datascalpel-simplify-units-baseline.log` 的 2 项中下溢用例失败，
  原代码允许 `Double.MIN_VALUE` 英尺换算为零后继续传播。现增加换算后正数检查，复用
  `INVALID_GEOMETRY_SIMPLIFY_TOLERANCE` 与 `configuration.tolerance`，覆盖全部策略并验证该预检零 Spark Job。
  原有溢出错误保持，仍可表示的极小正数继续通过；不新增任意最小容差。
- 纠正角度提示：EPSG:4807 为 grad 而非 degree；共享单位拒绝消息、简化告警与面板不再把所有地理 CRS 写为度。
  SOURCE_CRS_UNIT 数值/坐标不变，面板从已有 Compiler 告警识别角度，不复制 CRS 数据库或新增 API 字段。
- 最终后端 `BUILD SUCCESS`：`/tmp/datascalpel-unary-units-scale-verified.log` 共 121 项，
  一元几何 10、单位换算 23、空间节点 Spark 88。新增 Spark 覆盖 3857/2263 下两算法及全部距离单位结果等价、
  4326/4490/4807 来源角度单位、原行/原表/字段/SRID 保留、无效值与换算上下溢。
  前端 `/tmp/datascalpel-unary-angular-ui-tests.log` 8 项通过，包含非 degree CRS 的标签与原容差保存；
  `/tmp/datascalpel-unary-angular-ui-tsc.log`、`/tmp/datascalpel-unary-angular-ui-eslint.log` 均正常退出无错误。
  不累计前置和重复运行。所有本轮进程已结束，无后端全工程构建或外部数据库写入。
- 更新节点设计及稳定定义说明，不改算法、Canvas/Manifest/Result 版本或 HTTP。
  本地大折线不是集群容量/内存预算验收，真实页面仍受开发服务不可用影响；两个节点及完整路线图复选框保持未完成。

### 几何派生与简化面板诊断修正（2026-09-08，仍为 4.46）

- 派生规则函数名缩为明确的中文名称；缺少来源字段现在标红，规则错误使用单行计数及支持悬停/聚焦/点击的详情。
  维度不支持及显式新策略的 GeometryCollection 边界限制在当前规则就地展示，旧策略不新增该预判。
  字段输入增加明确可访问名称及关闭自动填充；预览长字段提供 Tooltip，正常规则不增加诊断高度。
- 两个 Inspector 在 Compiler 尚无上下文时保留已保存表/字段并显示等待解析，不再由空候选错误标记为失效。
  来源表不可用时保留字段并禁用候选；没有增加 Schema 传播逻辑或更改算法、协议和草稿应用边界。
- 最终专项 7 项通过：`/tmp/datascalpel-unary-ui-audit-verified-tests.log`。
  覆盖新规则缺少字段的计数/点击详情、上下文等待状态、高维质心与集合边界错误、取消隔离、无效草稿应用及原版本门槛。
  TypeScript 和触及文件 ESLint 均正常退出且无错误：`/tmp/datascalpel-unary-ui-audit-verified-tsc.log`、
  `/tmp/datascalpel-unary-ui-audit-verified-eslint.log`。重复运行不累计用例；JSDOM 的伪元素样式提示不作为视觉验证证据。
- 使用 Browser 尝试打开现有开发入口 `http://localhost:18887/`，返回 `ERR_CONNECTION_REFUSED`。
  未启动替代前端/模拟应用、未修改用户任务；真实页面布局和交互未验收，不据此勾选两个节点或完整路线图完成。
  本轮未修改后端，无新增算法/规模/官方结果验证。

### 4.46 事件检测受控字段窗口（2026-09-08）

- 核对 Enterprise 11.3 官方事件检测与表达式页，确认 TrackFieldWindow 为左闭右开；
  官方移动均值 `window(-5,0)` 不含当前。将其落实为明确偏移契约，而非任意 Arcade 脚本执行器。
- Contracts 增加 `TrackIncidentWindow` 与可选 `conditionWindows`，默认空、顺序防御性复制、旧构造器保留。
  非空数组（含 LEGACY 非活动草稿）在保存、GraphPlan 和前端导入三处要求 4.46；节点仍从 4.17 引入。
- `IncidentWindowPlan` 同时从原始字段计算九种窗口聚合，按轨迹、片段与时间/次序排序，窗口不跨边界、不互相引用。
  使用 `[start,end-1]` 对应 Spark ROWS；字段类型由 Analyzer 回填，保留 Decimal/整数提升，临时字段不进入结果表。
  原始字段血缘为 FIELD_COMPLETE，事件字段可回溯条件的原始字段，编译零 Job，无新增缓存/Checkpoint/驱动收集。
- Inspector 增加 860px 紧凑窗口指标表；独立草稿、取消、排序、删除确认及失效值保留。
  条件编辑器能引用局部指标，开始/结束 Modal 也改为独立草稿，取消首次结束条件不再创建空条件。
  无编译上下文时显示等待解析而非误标来源字段失效。Canvas/Runner 只增加计数，日志不记录别名、偏移或字面量。
- 最终 `/tmp/datascalpel-incident-windows-verified.log` 为 BUILD SUCCESS：Contracts 3 + Engine 136 = 139 项通过。
  Engine 为窗口 8、原空间节点 84、GraphPlan 39、Runner 摘要 5；覆盖 Decimal/整数实际提升、非活动旧草稿不求值、
  零 Job、FIELD_COMPLETE 和摘要无指标值。此前 131 项及补充回归不另累计。Business 新增保存门槛用例，
  本轮仅主代码编译通过 `/tmp/datascalpel-incident-windows-business.log`；不将主代码编译等同于保存端用例执行。
- 前端早期失败包含静态确认框双标题、图标导致可访问名称不同，以及异步确认根尚未挂载的测试定位问题；
  保留实际确认/取消断言，修正查询与等待。类型检查修正测试小版本参数过窄及 HTMLElement 类型；
  按 Fast Refresh 规则拆开窗口工具函数与 Modal 组件。一次后续全组回归的旧 HDBSCAN 弹窗超过 20 秒测试限时，
  不计为通过；另一次在核对默认空数组期间遇到旧模块缓存与新断言不一致，冻结代码后重新验证。
  冻结代码的 `/tmp/datascalpel-incident-windows-ui-frozen.log` 中事件窗口/Inspector/轨迹 Parser 三文件 13 项通过，
  整组仍因 1 个 HDBSCAN 弹窗 20 秒超时而失败，不能标作全组绿色。
  不修改测试断言，单独以 60 秒测试上限重跑 HDBSCAN：`/tmp/datascalpel-incident-windows-hdbscan-isolated.log` 5 项通过，
  总测试耗时约 13 秒；只作功能证据，不宣称页面性能或全组稳定性通过。最终不同前端用例为 13 + 5 = 18，不累计重复运行。
  最终 TypeScript `/tmp/datascalpel-incident-windows-tsc-complete.log`、触及文件 ESLint
  `/tmp/datascalpel-incident-windows-eslint-complete.log` 均正常退出且无错误；所有本轮进程已结束。
- Manifest/Result/HTTP/生产依赖均不变。4.63～4.65 已补齐受控累计距离、逐观测速度和逐观测加速度
  窗口，4.66 又补四种轨迹时间/序号标量，4.67 补 Point 相对观测 X/Y 标量；返回复合对象的 Geometry/TrackWindow 与更丰富受控表达式、官方服务对照、
  全页面和容量仍在原范围内，因此不勾选整个节点完成。完整契约、NULL/总体统计差异及 UI 图见
  [事件检测第 8 节](canvas-spatial-next-processors/track-detect-incidents.md#8-446-受控字段窗口条件)。

### HDBSCAN EOM 待决子簇工作集（2026-09-08，仍为 4.45）

- `HdbscanDiagnostics.eomDecisions` 将完成历史保存到二进制批次，工作关系只留父簇仍在 pending 的直接子簇。
  每个 ready 父簇对原始子簇贡献汇总一次，父簇完成后消费子簇工作记录；根簇排除、平局选父簇及死亡密度传播不变。
  不使用逐轮浮点累计小计，不向 Driver 收集树或成员，不新增 Compiler Action。
- 新增 `HdbscanEomSparkTest`：20 层非平衡旁支对照独立递归 EOM，精确质量平局、无穷质量、待决记录消费、
  已绑定快照独立、包含已完成分支的环形残余和空关系；原压缩树测试继续核对四诊断。
- 首轮 `/tmp/datascalpel-hdbscan-eom-working-set.log` 为 BUILD FAILURE：11 项中 2 项测试代码错误。
  泛型 `Row.getAs` 被 JUnit 推断为 `BooleanSupplier`，实际布尔值在断言处发生类型转换错误。
  只显式指定断言读取为 Boolean；未改算法或降低对照要求。该轮原压缩树 7 项通过，不作为整轮通过。
- 最终 `/tmp/datascalpel-hdbscan-eom-corrected.log` 为 BUILD SUCCESS，46 项全部通过：
  EOM 4、压缩树/诊断/历史 7、生成树/候选 16、独立内存层次 8、Point 节点 11。
  未累计首轮重复用例；测试进程正常退出。20 层 EOM 与四诊断原结果对照通过，不等同于真实规模或 Esri 对照通过。
- 待决集仍可能因非平衡旁支等待而反复扫描；深树轮数、Checkpoint 文件容量/清理、真实规模和 Esri 数值对照仍开放。
  没有协议、UI、依赖或错误契约变更。实现不变量与边界见[聚类第 14 节](canvas-spatial-next-processors/spatial-point-cluster.md#14-eom-完成历史与待决子簇分离)。

### HDBSCAN 分批历史与规划统计边界（2026-09-08，仍为 4.45）

- `HdbscanRowHistory` 已接入层次簇、退出记录、观测归属及祖先传播，按二进制层级合并批次；
  只保留对数个 Dataset 句柄，每批至多对数次重写。跳过已知空批次，合并分区数不超过当前 shuffle 配置。
  子分量大小的控制标量代替额外 active 扫描与分叉存在性查询，末轮不再写空的下轮活动图。
- 首轮 `/tmp/datascalpel-hdbscan-history.log` 在新长链用例持续计算；线程栈
  `/tmp/datascalpel-hdbscan-history-threads.log` 明确定位到 Spark 连接大小估计的 BigInteger 乘法。
  本次测试进程经定向 TERM 停止（143）并确认退出，没有并行重启或缩短用例。
- 新增 `HdbscanCheckpoint.bind`，先固定数据再经 JavaRDD/原 Schema 重建分布式规划边界，
  不继承指数膨胀的历史估计；无全表 collect、全局配置修改或伪造行数。用于层次循环、历史和诊断循环状态。
- 重跑 `/tmp/datascalpel-hdbscan-history-rebound.log` 完成但 1 项断言失败：把 33 轮切割误认为 33 条退出记录，
  最后同权切割实际产生两个单点退出，正确总数为 34。只修正该计数断言，保留 34 顶点、原权重、逐点密度和诊断对照。
- 修正后层次 7 项全部通过：`/tmp/datascalpel-hdbscan-history-corrected.log` 为 BUILD SUCCESS。
  覆盖历史进位、旧快照独立、51 行源表达式只求值 51 次、分区/估计值边界、33 轮连续退出及完整四诊断。
  最终长链用例约 64 秒；此前运行耗时受环境影响，不将不同运行的总时间作为受控性能基准。
- 补充回归 35 项通过：`/tmp/datascalpel-hdbscan-history-regression.log` 为 BUILD SUCCESS，
  MST/核心/割候选 16、内存层次 8、Point 节点 11。本轮最终证据为 7 + 35 = 42 个不同专项用例，
  不将中断、失败或重复重跑计成新增通过项；未修改前端或据此声明全页面/容量验收。
- 深树分裂轮数仍与深度有关；EOM 底向上完成表由上方后续工作集优化继续推进。文件生命周期/失败清理、真实规模和官方数值仍未完成。
  全部 12 节点及路线图范围保持不变，具体实现边界见[聚类第 13 节](canvas-spatial-next-processors/spatial-point-cluster.md#13-层次历史分批保存避免逐轮重写全部结果)。

### HDBSCAN 逐轮精确割候选（2026-09-08，仍为 4.45）

- `HdbscanSpanningTree.run` 已移除初始完整互达距离图，稠密 `plan` 只留作对照。
  新增 `HdbscanCutCandidates`：代表近邻只给出可行上界，最终从分量全部点恢复候选，
  再按互达距离及原始端点全序确定最小边；下一轮重搜，不把上一轮候选缺边当成原图不存在。
- 代表和种子上界分别 Checkpoint 后再补缺失分量，以两个不同分量锚点保证可行外连；
  上界存在性不被重复求值改变。核心和边权按原始 ID 顺序计算，搜索数值裕量不改变最终权重/零距离判断。
- 最终 40 项通过：`/tmp/datascalpel-hdbscan-cut-verified.log`，MST/核心/割候选 16、内存层次 8、
  Point 节点 11、分布式层次与诊断 5。此前两轮 39 项也通过，未累计为新增覆盖。
  覆盖非代表成员最小边、同权/重复位置、缺种子、全球点集完整 Kruskal 对照，以及微小正距离不能变成零边。
- 追加物理计划检查单项通过：`/tmp/datascalpel-hdbscan-cut-physical.log`，确认格网候选存在 DistanceJoin 或
  BroadcastIndexJoin 且无 CartesianProduct；该项属于上述 16 项中的增强，不另加到 40 项总数。
  格网候选少于 32,640 条无序边的 1/8 且各割最小边相同；不以小格网证明真实集群规模。
- 前端 HDBSCAN 5 项通过：`/tmp/datascalpel-hdbscan-cut-ui.log`，帮助文案更新文件 ESLint 通过：
  `/tmp/datascalpel-hdbscan-cut-eslint.log`。只调整开销描述，没有新增参数、界面布局或浏览器全页面验收。
- 当前算法/证明/开销边界见[聚类第 12 节](canvas-spatial-next-processors/spatial-point-cluster.md#12-逐轮精确割候选移除运行时初始完整图)。
  下方初始完整图的描述保留为历史阶段，不能当作当前执行方式；密集候选 O(n²)、深树、Checkpoint 空间与清理、
  真实规模和官方诊断仍待完成。未修改协议、依赖、公共配置或其他节点范围。

### HDBSCAN 精确核心距离候选恢复（2026-09-08，仍为 4.45）

- 新增 `HdbscanCoreDistances`：KNN 只产生足量不同身份的真实距离上界，DWithin 恢复半径内候选，
  按真实距离取含自身的第 k−1 个其他观测。不足种子的来源使用完整候选，重复坐标不合并身份。
  上界和核心表分别 Checkpoint，MST 两端共享核心表；完整点对 plan 保留为对照。
- 首轮 34 项通过：`/tmp/datascalpel-hdbscan-seeded-cores.log`。最终补格网候选检查后 35 项通过，
  `/tmp/datascalpel-hdbscan-seeded-final.log` 为 BUILD SUCCESS：生成树/核心 11、内存层次 8、
  Point 节点/DBSCAN 11、分布式层次与诊断 5。重复运行不累计为新增覆盖。
- 验证重复点、同距、日期线/极区/全球点集、k 等于点数、非最近但合法上界、缺种子回退、空/不足观测和 Analyzer 零 Job。
  256 点格网的排名候选少于完整 65,280 个有向点对的 1/8，核心距离与完整对照逐项相等；
  这是候选数量证据，不是集群吞吐量或百万点容量验收。
- 初始互达距离图仍为完整无序点对。后续需以分量割的可行出边上界恢复所有最小候选，
  证明 MST 等价后才能移除稠密图，不能把 KNN 种子直接当图。深树、Checkpoint 清理、官方诊断与其他节点仍在范围内。
  无协议、UI、依赖或错误契约变化；文档见[聚类第 11 节](canvas-spatial-next-processors/spatial-point-cluster.md#11-精确核心距离的近邻上界与半径恢复)。

### HDBSCAN 精确商图压缩（2026-09-08，仍为 4.45）

- `HdbscanSpanningTree` 在每次合并后删除内部边、按分量对保留全序最小原始边，
  用 `min(struct(weight,src,dst))` 选出边，避免继续携带所有平行边。
  证明及当前开销边界见[聚类第 10 节](canvas-spatial-next-processors/spatial-point-cluster.md#10-精确商图压缩减少生成树后续迭代的边数)。
- 专项 31 项通过，`/tmp/datascalpel-hdbscan-quotient.log` 为 BUILD SUCCESS：
  MST 7、内存层次 8、Point 节点/DBSCAN 11、分布式压缩与诊断 5。
  新用例验证无 Job 分析、66→3→1→0 的边压缩、连续压缩与直接压缩一致，
  以及多轮 Borůvka 与独立 dense Kruskal 逐边相同（不只是总权重相同）。
- 初始完整点对仍为 O(n²)，本项不解决核心距离空间候选、深树迭代、Checkpoint 失败清理或真实容量。
  没有算法近似回退、协议/UI/依赖变更；本地专项不代表官方数值、全页面或集群容量通过。
  其他 11 节点及路线图其余开发范围保持不变，完成复选框仍开放。

### HDBSCAN 4.45 节点与 Inspector 接入（2026-09-08）

- Contracts 新增 SpatialHdbscanOptions 四字段，保留旧八/九参数构造器，缺失/null 不推测。
  Operator 要求活动 HDBSCAN 明确配置四名，空名/重名路径精确；未实现 Multi-scale 继续拒绝。
- PointHdbscanSupport 校验参与 Point/ID，私有别名隔离全部原列，以稳定内部 ID 回接六项结果。
  隐藏 Linear 时间不筛行或进入派生字段血缘；有界新表保留原 Map，空参与集/噪声有明确输出。
- 预检 Struct 表达式只承载参与 Geometry/身份集合依赖，所有六个追加字段 FIELD_COMPLETE、零 Job，
  不建 Checkpoint 或提前执行真实聚类。Runner 安全摘要只增加诊断计数及非活动模式说明。
- Inspector 开放确认式 HDBSCAN 切换；独立 680px 诊断草稿 Modal 支持取消、缺名/重名提示和无效保存。
  诊断与时间对象跨切换保留，Canvas 仅显示计数，帮助说明 GLOSH 非 1−概率及当前性能/数值边界。
- 首轮测试编译因新 Contracts 测试误用 Jackson 2 导入失败：`/tmp/datascalpel-hdbscan-node.log`；
  改用模块已有 Jackson 3 后，Contracts 2 + Engine 96 共 98 项通过：`/tmp/datascalpel-hdbscan-node-corrected.log`。
  Engine 含 Point 节点 11、GraphPlan 38、层次/生成树 18、Runner 29；现有 DBSCAN 与新 HDBSCAN 同测。
- 前端两文件 15 项通过：`/tmp/datascalpel-hdbscan-ui.log`；TypeScript `/tmp/datascalpel-hdbscan-tsc.log`
  与触及文件 ESLint `/tmp/datascalpel-hdbscan-eslint.log` 通过。未据此声明全浏览器、容量或 ArcGIS 对照完成。
- 补充 Runner 摘要 4 + 现有空间节点 84 + 重复 Contracts 2 项通过：`/tmp/datascalpel-hdbscan-summary-regression.log`。
  新摘要用例确认诊断数量与非活动模式，无隐藏时间/诊断名；不把重复 Contracts 计成新增覆盖。
  保存端同步补四个字段的字符串结构检查，空名/重名仍可保存；Business 门槛/结构测试已补充，
  本轮未执行 Business 模块测试（此前存在无关旧调用编译失败），不以主代码编译替代该边界的运行证据。
  最终 Business 及依赖主代码编译通过：`/tmp/datascalpel-hdbscan-business-final.log`。
- 仍需候选优化/深树性能、Checkpoint 空间与失败清理、官方诊断数值及边界对照、完整单位/全页面验收。
  本阶段没有缩减其他 11 节点和路线图其余范围，也不提前勾选完成。

### 前阶段状态快照（4.44）

当前状态摘要（以下逐段保留历史推进记录）：4.44 Reconstruct 已接入测地面链路，覆盖原始测地来源校验、
原 Geometry 距离阈值、缓冲窗口、Struct 足迹、排序/共享端点和独立面边界采样设置；不使用质心测距或
隐藏线轨迹采样，三端门槛含非活动草稿。平面/线轨迹兼容保留，Manifest/Result/HTTP 不变。
Nearest 非点测地仍未接入。全球域、实际最近位置、候选召回/排名、完整官方/规模与全页面验收仍未完成；
HDBSCAN、Center 时间及路线图其他能力亦保持原范围，全部 12 个节点完整开发目标没有缩减。

HDBSCAN 当前补充：已有互达距离/Borůvka MST，以及 Spark 表上的压缩层次、EOM 与四诊断管道，
不向 Driver 收集树、成员或邻居。内部身份和 Geometry 一起 Checkpoint，纯 Java 层次 helper 仅作为独立对照。
完整点对仍为 O(n²)，逐层迭代最坏与深度有关；不是大规模验收通过。Runner 安全错误分类已贯通，
Operator、诊断契约和 UI 尚未接入，算法继续禁用；官方诊断数值及容量未验收。

### HDBSCAN 分布式层次与诊断（2026-09-08，仍为 4.44）

- 新增 HdbscanCondensedTree：分布式同时切除组件内同权最大边，按最小簇大小区分小分支退出、
  单幸存子分支延续和多分支分裂。所有观测有唯一直接父簇和退出密度，每簇退出数量守恒。
- 新增 HdbscanDiagnostics：叶到根 EOM 比较、根到叶已选祖先传播，返回簇号/概率/GLOSH/代表点/稳定性。
  沿用明确登记的原型归一化/无穷极限，不声明 Esri 数值等价；单独稳定簇编号也不作官方比对依据。
- HdbscanSpanningTree 固定完整私有身份/Geometry 投影；GraphFrames 临时缓存继续及时释放。
  层次图留在分布式表，仅返回控制迭代的标量，不调用内存 helper 执行生产数据。
- Runner 新增三项安全错误分类：内部树不一致 INTERNAL、层次容量 RESOURCE、数值范围 SCHEMA，
  均 PROCESS/不可重试。现有 Dispatcher/Admin/UI 的通用错误协议可传递，无新字段/版本/依赖。
- 压缩树与分类基础 45 项通过：`/tmp/datascalpel-hdbscan-condensed.log`。
  诊断首轮 46 项有 1 失败/1 错误：`/tmp/datascalpel-hdbscan-diagnostics.log`。
  原 GLOSH 反例所有成员都在叶簇最后退出，没有产生预设的离群差异；补入提前退出观测后验证概率 10/11、
  离群 2/11；另修正 Row.getAs 的 JUnit BooleanSupplier 重载推断。没有修改生产公式或放宽误差断言。
  修正后 46 项通过：`/tmp/datascalpel-hdbscan-diagnostics-cases.log`。
- 最终 47 项通过：`/tmp/datascalpel-hdbscan-distributed-final.log`，包括 Runner 29、MST/点管道 5、
  内存层次 8、分布式层次/诊断 5。新管道用例实际经过平面/日期线点、MST、压缩与诊断，逐观测对照；
  非确定性私有 ID 表达式只执行一次。Maven 编译与专项成功，未运行全工程/浏览器/真实 ArcGIS 或集群容量测试。
- 节点、诊断 Contracts/版本门槛、真实输入规则/原行回接/编译血缘、Inspector、候选优化/深树性能、
  规模与官方服务仍待完成。本阶段不改变 Compiler 路径或解除 HDBSCAN 禁用，不将本地管道当成整节点交付。

### 前阶段：Reconstruct 4.44 验证记录

本次后端专项：Engine 298 项 + Contracts 7 项通过，日志 `/tmp/datascalpel-geodesic-area-final-regression.log`。
TrackAreaSparkTest 28 项包括测地面完整 Operator 的零 Job 分析、原始区域而非质心距离、平面 WKT 无效但
真实测地孔洞有效、日期线/单观测、固定周期隔离、共享观测窗口半径不重算、隐藏线参数不执行、
非法几何安全失败与 FIELD_COMPLETE 字段血缘；GraphPlan 37 项含 4.44 非活动对象门槛。
Contracts 保留五/六参数构造器，验证新对象 JSON 往返与非法结构。本地结果不代表完整全球域或官方等价。

Business 主代码编译通过；模块测试编译被本次未修改的 ComputeEngine、DispatcherRegistrationRequest、
GatewayServiceSpec 旧构造器/方法调用阻断（`/tmp/datascalpel-geodesic-area-boundaries.log`）。新增保存端门槛
测试已编写但未执行，不把 Engine 门槛测试代替 Business 的运行证据，也未修改无关模块来制造通过结果。

前端最终 4 文件、20 项通过（`/tmp/datascalpel-geodesic-area-ui-complete.log`）：新对象严格解析、4.44 门槛、
非活动采样保留、模式切换、独立 Modal 取消、保存无效采样草稿、隐藏线配置不修改、原缓冲窗口和安全卡片回归。
首轮旧文案定位与空 InputNumber 的测试预期失败，已按实际标签/空字符串修正；未放宽业务校验来满足测试。
新增测试的可选字段访问经 TypeScript 指出后改为可选链；最终 TypeScript 应用检查及触及文件 ESLint 通过，
日志 `/tmp/datascalpel-geodesic-area-tsc-verified.log`、`/tmp/datascalpel-geodesic-area-eslint-final.log` 与 `...-eslint-last.log`。
没有真实 ArcGIS 或浏览器全页面/容量验收，不以这些局部证据勾选完整节点。

### 历史推进记录

- 内部区域距离增加 Wgs84PolygonRegion / Wgs84GeometryDistance：原始测地环方位绕数、孔洞/多部件、
  真实公共位置零距离及边界距离区间；未决近边界不吸附为零。所有边对进入一个全局下界队列，
  带曲率裕量的 ECEF 投影分离界只用于剪枝。212 项通过，细节及失败修正记录见后文。
  仍未接入 Operator，连续近接触拓扑、局部域外区域、严格阈值及非点候选召回继续待办。

- 非点测地距离内部基础：Wgs84SegmentDistance 通过完整弧长参数域的距离下界细分寻找最近位置，
  Wgs84LinearDistance 遍历原始点/线/多部件并共享计算预算；不使用质心或投影最近位置。
  已有点/线局部测试及安全错误分类证据，面内含/孔洞/接触、Nearest 候选召回和整节点执行仍待继续。
  当前未开放任何新配置组合，精度和候选召回的接入要求见[共享距离设计](canvas-wgs84-geometry-distance.md)。

- 局部 Polygon 测地足迹、孔洞、偏移带与相邻连接已进入内部实现；真实顶点通过显式 Spark Struct
  在 Shuffle/有序聚合后保留，极点渲染闭合不混入候选。176 项专项通过，详见后续记录及 Reconstruct 第 14 节。
  下方点缓冲/凸包记录为开发历史；当前仍待非点 gap、大域和完整 Operator 接入，Canvas 保持 4.43。

- 测地面连接核心已进入内部开发：WGS84 方位排序/逐边支持验证、显式局部参考点、测地边加密及共享边界渲染。
  修正原始极点顶点及边中途过极点的经线表示，避免斜弦或误选补集；相邻连接反例和 Spark 惰性执行已加入专项。
  尚未接入 Operator；Polygon 缓冲、非点 gap 距离和整段路径仍待继续。当前 Sedona 非点球面距离实际取质心，
  不能直接作为完整面距离实现。细节与阶段性范围/计算量保护见 Reconstruct 第 13 节；协议仍为 4.43。

- 测地面轨迹开发中：新增内部 `TrackGeodesicDisk`，按 WGS84 正解生成逐观测点缓冲，
  圆周日期线交点精确求解、极点闭合与真实采样隔离，输出独立 XY MultiPolygon；复用已有依赖。
  原语尚未接入 Operator，当前 UI/编译仍不开放测地面组合；Polygon 缓冲、相邻测地面连接与大区域仍待继续实现。
  不为这个内部步骤升级 4.43，不将完整节点或路线图勾选完成；具体边界见 Reconstruct 第 12 节。

- 4.43 Reconstruct 缓冲表达式增加 windowBindings：数值字段、闭区间观测偏移及十种统计，原始字段独立读取。
  窗口按轨迹/固定周期分组，沿用确定次序；在普通 gap/拆分/共享之前求值，缓冲与拆分绑定作用域隔离。
  字段/线/旧策略下保留但不执行绑定；非空数组含非活动设置门控 4.43，空数组不改变 4.42 语义。
  独立 Modal 草稿、隐藏值保留、排序/删除确认和 Canvas/日志窗口数量已接入，不扩大到 Arcade 兼容或测地面能力。

- 4.42 Reconstruct 接入显式平面 XY 面轨迹：Point 字段/逐行表达式缓冲、Polygon/MultiPolygon 观测、相邻面凸包连接。
  单观测面保留，原 Geometry 决定拆分，固定周期不共享端点；所有统计仍按真实片段成员。
  新可选 areaGeometry 含非活动草稿的三端门槛、旧构造器/线轨迹兼容、独立 Modal 取消和安全摘要贯通。
  修正有序轨迹计数窗口的来源表达式，并限定支持 ArraySort/ArrayTransform 的 lambda 血缘，未知外部来源不伪造完整性。
  测地面轨迹/日期线面切分、缓冲窗口表达式、官方数值/边界、全页面浏览器和容量仍待完成；本轮专项结果见后续记录。

- 4.41 Within 已接入可选 regions：区域表/方格/六边形、投影原点与范围、实际点线面汇总、稳定格网键与主表/组表。
  不以质心替代被汇总 Geometry，分摊分母保留完整来源；Point 唯一归属复用 Bins，内部区域不传播到 Map。
  配置切换确认、隐藏草稿、独立格网 Modal 的取消与无效草稿保存贯通；三端版本门槛含非活动对象。
  惰性范围保护及格网身份血缘有本地专项，官方共边/多点边界、浏览器和容量对照仍未完成，完整路线图不缩减。

- 4.40 Cluster 增加显式空间与 Linear 时空密度连通，空间与时间阈值 AND、邻域含自身、核心点图与边界归属分开。
  旧空间算法以缺失/null/LEGACY_SPATIAL 保留；新模式隔离内部字段、实际有效 ID 非空唯一、NULL/Empty 点和缺时间排除。
  修复 Compiler 直接触发 Sedona Checkpoint/图计算：预检只构造零行 Schema/集合依赖血缘，实测零 Spark Job；
  运行复用已有 GraphFrames，Checkpoint 固定身份/邻域/输出后释放持久图结果，无新依赖或 Driver 全表收集。
  三端 4.40 门槛含非活动对象；Inspector 确认式切换、隐藏时间参数及无效草稿保持，MULTI_SCALE 不作为新建候选。
  HDBSCAN、完整单位、官方服务和规模验收仍未完成，不以本阶段缩减目标。
- 4.39 Bins/Within 增加可选 calendar：固定/日历明确分开，旧固定 DAYS=24h 保持不变；
  月末/闰年同族起止从原参考时刻计算，日周按 IANA 时区、小时以下按实际时长，跨族先起点再加窗宽。
  显式切换确认、隐藏单位保留、独立弹窗取消与无效草稿贯通；三端门槛包括非活动对象。
  单观测最多检查 4096 个候选窗口，超限/算术范围错误均为安全不可重试错误；不新增 Action/依赖/API。
  配置、平台裁决及紧凑 UI 见[共享日历设计](canvas-spatial-calendar-windows.md)。完整路线图目标不收缩。
- 4.38 Bins 增加可选 planarGrid：明确方格角点/六边形中心原点、来源 CRS 坐标、固定方向与跨任务身份哈希。
  显式范围筛点后聚合完整格网；补空保留边界点的原归属，零输入可补空间格网但不虚构时间窗。
  三端门槛含 H3 非活动草稿，旧配置不自动迁移；Modal 独立草稿、取消、失效值保留及恢复旧版确认贯通。
  新模式对候选空间格网作 100 万保护，显式范围静态校验、数据范围惰性执行；不增加 Action/依赖/API。
  日历后续见 4.39；球面范围、官方数值及规模验收仍未完成。

- 4.37 Within 新增原值交叠比例加权方差/标准差，沿用既有统计字段，组合能力独立门槛覆盖三端。
  使用 11.3 公式图的有效记录数修正，四标量可合并中心矩；明确 NULL、非有限、零权重、单条和溢出行为。
  该页文字算例与公式矛盾已记录，不宣称与真实服务数值完全一致。旧均值/普通样本方差、Manifest/Result/HTTP 不变。
  统计 Modal 独立草稿、完成提交、取消丢弃；值血缘包含统计字段及两侧 Geometry，未知来源仍保留诊断。
  日历后续见 4.39；规则格网、官方服务/容量和整节点验收仍未完成。

- 4.36 公共距离/面积单位增加国际码/平方码与独立美国测量制；旧美制海里使用 Esri 109012 的 1853.248 米。
  旧国际制枚举和结果不变；Contracts/Business/Engine/前端统一新单位门槛，包括隐藏草稿。
  各相关单位选择复用同一选项/名称；紧凑帮助说明来源轴和数值不自动换算，配置切换保留非活动值。
  换算、覆盖路径和待完成边界见[公共空间单位](canvas-spatial-units.md)。不改变 Manifest/Result/HTTP 或增加依赖。
  本轮尚不补齐时间/日历切片、原有 Buffer/Measure、官方服务结果和整节点验收。

- 4.35 Reconstruct/Dwell 的 COUNT_FIELD 与 ANY 已接入：重建仅字符串 Any，驻留额外支持数值，保留来源类型。
  字段非空计数与成员点数分离，聚合输出 Schema 回填 Spark 实际类型；不改变旧 COUNT、数值算法及首末 NULL。
  三端门槛含驻留非活动汇总，点级输出保留但不执行统计草稿；统计弹窗本地编辑、取消、保存无效值、排序/字段恢复/删除确认贯通。
  不新增依赖、真实预读、Action、Manifest/Result/HTTP 字段。官方 Any 数值返回类型、统计公式、完整单位/血缘及其他节点范围仍未完成。

- 4.34 Bins 增加 COUNT_FIELD 非 NULL 计数和 ANY 字符串采样，Contracts/Parser/Compiler 三端门槛同步；
  COUNT 仍为点数。输出 Schema 按实际 Spark 聚合类型回填，修正 INTEGER SUM 和 Decimal 精度提升不一致。
  Bins/Within 共享固定时长留空切片，并修复起止分别展开导致的重叠窗口重复统计；不保留错误结果作为兼容语义。
  格网各形状的来源、统计及分组指示器均隔离内部别名；窗口 Expand 按投影追踪血缘，未知分支仍降级。
  Inspector 统计弹窗支持局部草稿/取消、排序、字段切换恢复、失效值和无效草稿；帮助不常驻占用面板。
  Manifest/Result/HTTP API 未改变。单位、原点/范围、共享日历后续见 4.36/4.38/4.39；官方对照及完整路线图仍未完成。

- 4.33 Bins 已接入 H3 原生格网：显式 0～15 分辨率/近似距离、WGS84 XY 点归属、日期线与极区
  MultiPolygon 边界、分组/时间统计及来源别名隔离。旧方格/六边形语义保留。
  Inspector 支持确认式形状切换、两种大小方式、保留隐藏参数、无效草稿以及 Compiler 实际级别回显；
  未应用草稿不展示旧级别。Canvas/Runner 仅安全摘要，三端门槛为 4.33，Manifest/Result/HTTP API 不变。
  近似选级别公式为平台约定，非官方已验证公式；H3 不补空格网，不隐式转换其他 CRS。
  原点/业务范围、完整统计/单位、官方结果和规模/跨架构验收仍未完成。

- 4.32 Center 已接入可选逐分析 centralFeatureColumns：Compiler/Runner 同一原记录投影、类型/NULL 保留、
  字段冲突与失效校验、事件时间别名与无 Watermark、Inspector 本地弹窗/排序/排除/取消/无效草稿、Canvas 安全计数及三端版本门槛。
  旧四/五参数构造器和缺失/null 输出保留；非活动数组不执行但仍校验协议版本。本轮专项证据见下方记录。
  平均/中位/椭圆时间、interval 类型、官方加权公式与完整路线图范围仍未完成。

- 4.31 Center 已接入显式 ANALYSIS_TABLES/LEGACY_WIDE、逐分析输出表、线面质心与原中央要素、原类型身份平局次序。
  新中位中心使用修正 Weiszfeld 与凸目标差距停止证据，固定迭代旧路径保留；不收敛以安全错误退出。
  分组收集前限制 100000 要素/100 万顶点，含中央要素为 5000 要素/100 万顶点；不作无限规模承诺。
  空/全零组不输出、退化圆椭圆 Empty、隐藏配置/取消/模式确认/无效草稿与三端 4.31 门槛已贯通。
  类型时间结果、官方加权公式、完整字段投影/血缘及真实规模与页面验收仍未完成。

- 已实现局部能力：事件检测显式生命周期、状态、逐观测时长、同时间次序；四类轨迹节点的可选固定时间边界。
- 已实现局部能力：六边形显式对边距离与旧边长策略，含空格网一致换算；H3 后续实现见 4.33，原点/业务范围仍未完成。
- 兼容选择：新增能力进入 4.21，事件旧配置默认 LEGACY，不改解释；固定边界缺失/null 保持旧行为。
- 已修正四个轨迹 Inspector 对弹窗字段的监听、草稿提交和边界计数；切换事件语义需确认。
- 4.22 驻留已接入参考点/固定均值中心扩展与四输出；Inspector/Parser/Canvas/安全摘要贯通，旧策略保留。
  增加日期变更线局部凸包切分、内部字段防冲突及输出行按需复制；完整统计类型、极区/大范围官方对照与单轨迹容量仍未完成。
- 4.23 运动统计新增显式 OBSERVATION_WINDOW 策略：八组 31 项指标、包含当前点的历史窗口、Idle 距离/时间双阈值、独立高程来源与输入单位。
  Inspector/Parser/Canvas/安全摘要及版本门槛贯通，缺失策略继续使用旧 LEGACY_LAG；完整官方单位与缺失值/窗口边界服务对照仍未完成。
- 运动与驻留新策略的来源/输出名称为 null 时返回配置问题，不再因 Map 空键抛异常；不完整草稿不传播部分结果。
- 4.24 区域汇总逐统计项支持原值/总量分摊、原值交叠比例加权均值、字段非空 Count 和字符串 Any；Inspector 使用紧凑 Table，保留失效配置及未打开弹窗的字段。
  内部别名避开业务字段；该阶段尚缺的关联分组在 4.25 继续实现。
- 4.25 区域汇总可选主表/关联组表，显式区域键稳定关联，点数/线长/面积计算组占比；主表独立计算总体统计，不聚合组均值代替。
  空区域不伪造组记录，真实 NULL 组保留；时间切片、并列排序、少数/多数组值与比例贯通。保留旧扁平模式及非活动设置，Inspector 切换需确认。
  公共单位和加权方差/标准差分别见 4.36、4.37；格网区域与官方边界/容量对照仍未完成。
- 未执行完整后端构建/真实 ArcGIS 对照；目前没有节点被标记为全部验收完成。
- 4.30 Nearest 已接入显式 EXACT_DISTANCE/LEGACY_KNN、来源唯一身份、全同距候选恢复和可选独立连接线表。
  有半径使用空间距离 Join，无半径使用 KNN 上界后空间距离 Join；最终按真实距离/候选 ID 排序，相同 Geometry 的来源不合并。
  平面端点使用最近位置；测地暂只支持 WGS84 XY Point，连接线加密/日期线切分；非点测地最近位置仍未完成。
  Inspector 切换确认、隐藏投影/连接设置保留、取消不提交、无效草稿保存及 4.30 三端门槛已贯通；旧配置不自动升级算法。
- 4.29 Geometry Derive/Simplify 已接入显式维度策略、实际坐标序列处理、无效输入/结果安全错误及旧语义保留。
  质心/面内点/包络只可靠输出 XY；凸包/边界与单要素拓扑保持可保留 Z/M，DP 仅保留 XY/XYZ。
  DP 新策略无隐式面积修复，共边反例可复现；新规则不猜选字段，简化不预填容差，策略切换确认，业务草稿可保存。
- 4.27 Reconstruct 有序片段、确定同时间次序、受控表达式与前后观测绑定、三种连接段归属、固定周期不跨接和单点跳过已实现。
  Inspector 切换确认，表达式支持关闭保留/取消弹窗/保存无效草稿，安全摘要不输出条件文本。
  面/缓冲轨迹、完整统计/单位及大轨迹容量尚未完成；测地路径补充见 4.28。
- 4.28 Reconstruct 显式 METHOD_PATH 已贯通：WGS84 椭球加密、测地日期线交点切分及 MultiLineString；平面路径保留顶点和实际坐标维度。
  旧配置不自动启用，任何显式路径对象（含非活动设置）要求 4.28；Inspector 切换确认并保留隐藏段长/无效草稿。
  插值不改变观测点数、时间或统计；一百万顶点为输出片段安全上限，不代表无限单组容量。官方精度/极区及容量对照仍待完成。
- 4.26 Overlay 五模式和显式 FAMILY_2D 已贯通 Contracts、Compiler/Runner、Inspector、Parser、Canvas 及安全摘要。
  45 组点/线/面组合、Multi/XY 输出、低维接触过滤和全遮罩差集有本地验证；旧三模式保留，策略切换确认，失效投影不清空。
  成对交叠仍非全局无重叠分区；官方精度/边界及规模性能未完成。

### 当前验证证据

- 4.35 后端联合：Contracts 字段统计往返 1 项，Engine 轨迹统计 5、既有空间 77、图门槛 28，合计 111 项通过。
  日志 `/tmp/datascalpel-track-statistics-tests-final.log`；覆盖新旧重建/驻留、空输入/全 NULL、空字符串、
  COUNT_FIELD/Any、驻留数值 Any 与重建拒绝、精确错误路径/整体无效、首末 NULL、Integer/Decimal 提升，点级输出不执行隐藏规则和 4.35 门槛。
  首轮 Contracts 样例缺 double 必需字段导致失败，补齐样例重跑通过，没有修改 null 反序列化安全边界。
  主代码编译通过（`/tmp/datascalpel-track-statistics-main.log`），不等同完整后端构建或官方服务验收。
- 4.35 前端最终联合 4 文件、14 项通过（`/tmp/datascalpel-track-statistics-ui-verified.log`），涵盖版本/非法结构、
  非活动汇总、取消/保存无效草稿/排序/来源恢复、删除确认，以及 Any 数值候选差异和原有两个 Inspector 的回归。
  删除测试最初受 Ant Design 测试环境重复 title ID 及重复标题文本影响，改为按确认内容定位并等待关闭后通过；不据此宣称生产弹窗故障。
  最后补齐 Form 全量值的明确类型后，TypeScript 应用检查及触及文件 ESLint 均通过，日志
  `/tmp/datascalpel-track-statistics-tsc-complete.log`、`/tmp/datascalpel-track-statistics-eslint-complete.log`。
  本轮中途曾报告无关 Panorama sourceId 类型问题，最终检查已不再报告；未在本任务修改全景模块。
  没有真实 ArcGIS 服务、完整轨迹字段血缘、规模或全页面浏览器验收，不将局部测试折算为整节点完成。

- 4.34 最终联合回归：Contracts 2 项，Engine 格网统计/时间/血缘 10、既有空间 77、图门槛 27、Spark JAR 血缘 3，合计 119 项通过。
  日志 `/tmp/datascalpel-bin-statistics-final-regression.log`。覆盖三种格网的字段非空数/Any、NULL/空字符串、平面空格网、
  INTEGER/Decimal 实际提升、精确字段错误路径、留空窗口的负 Epoch/参考时刻/微秒右边界/NULL，及 Bins/Within 重叠起止配对。
  新血缘测试覆盖三形状 × 重叠/连续/留空，平面另含补空；业务字段/统计输出与内部列同名的统计及分组占比亦通过。
  首轮发现重叠重复展开、时间样例依赖本地时区和 Expand 不受支持，分别修正实现、UTC 样例和逐投影分析后复验；没有放宽 FIELD_COMPLETE。
  最终测试辅助方法整理后，格网专项 10 项再次通过（`/tmp/datascalpel-bin-statistics-last.log`），与上述联合回归重叠，不累加总数。
- 4.34 前端最终 3 文件、12 项通过（`/tmp/datascalpel-bin-statistics-ui-all.log`），覆盖新类型门槛、
  统计弹窗取消/保存无效草稿/排序/切换恢复，以及 H3 和六边形既有交互；触及文件 ESLint 通过。
  日志 `/tmp/datascalpel-bin-statistics-eslint-complete.log`。本轮 TypeScript 应用检查通过（`/tmp/datascalpel-bin-statistics-tsc-final.log`）；
  先前阶段记录的 MCP/GPKG 错误是当时结果，不作为当前阻塞，未在本任务修改无关模块。
  主代码编译在阶段中通过（`/tmp/datascalpel-bin-statistics-main.log`），最终 Engine 改动随上述 Maven 专项重新编译。
  不包含完整后端构建、真实 ArcGIS 服务、规模或浏览器全页面验收，不据此勾选完整节点完成。

- 4.33 H3 联合回归：Contracts 1 项，Engine 空间 76、H3 内核 5、图门槛 26、错误分类 24，合计 132 项通过。
  覆盖 122 个 0 级格网（含五边形/极区）、0/6/15、日期线/极点同位、近似距离选级别、有效 MultiPolygon、
  分组百分比/时间窗、内部同名字段、NULL/Empty/空输入、非法坐标惰性安全失败和原表保留。
  日志 `/tmp/datascalpel-h3-regression-complete.log`。首次两轮测试因样例缺基础字段/COUNT 失败，修正样例后重跑通过，未放宽既有规则。
  Contracts/Business/Engine 主代码编译通过（`/tmp/datascalpel-h3-main-final.log`）；未执行全工程构建。
  制品验证器已补 H3 类、本地库条目和原生调用检查，但本轮未重打三类制品/验证其他操作系统，不能算跨架构验收通过。
- 随后补 H3 字段血缘专项，发现 `distinct` 对应 Catalyst Deduplicate 尚不受分析器支持；仅 H3 范围改为
  结果等价的显式分组聚合，没有放宽公共血缘规则，没有增加 Action。
  最后 5 项 H3 实际计划测试全部通过（4 项与前述回归重叠、1 项新增血缘），含 FIELD_COMPLETE 及 Cell ID/Geometry/Count 的来源点字段、SUM 的数值来源。
  日志 `/tmp/datascalpel-h3-last-tests.log`；原有平面格网路径未改变。
- 4.33 前端 H3 与旧六边形最终 2 文件、9 项通过（`/tmp/datascalpel-h3-ui-last.log`），包括业务草稿、隐藏参数、
  模式切换确认/取消、未选形状不猜填、实际 Compiler 级别回显及编辑后过期值隐藏；触及文件 ESLint 通过。
  TypeScript 最后仍仅报告既有 MCP 索引和 GPKG 元数据类型问题（`/tmp/datascalpel-h3-tsc-final.log`），未修改无关代码。
  官方选级别/精度、规模和浏览器整页验收不包含在这些记录中。

- 4.32 联合回归：Contracts 2 项，Engine 空间 71 项、数值内核 6 项、图门槛 25 项、错误分类 23 项，共 127 项通过。
  新增原记录 NULL/Decimal/Timestamp 保留、原生 ID 选中、同 ID 的无效 Geometry 行不混入回连、NULL 分组、
  空投影、原字段排除/改名/顺序、事件时间别名与排除、重复来源/目标和 Geometry 重名、非活动配置不执行。
  血缘测试通过实际 Catalyst 计划确认中央结果 FIELD_COMPLETE，原 ID/Geometry 为 DIRECT，平均结果为派生且两表资产隔离。
  修正了实际 Spark 自关联歧义；有效字段计数代替无字段依赖的计数/序号表达式，保持数值次序不变且可追踪字段用途。
  日志 `/tmp/datascalpel-center-projection-regression.log`；不是官方服务或规模验收。
- 4.32 前端：中心 7 项 + 最近邻 3 项，合计 10 项通过；覆盖 JSON 往返/4.32 门槛/非法结构、
  原字段弹窗局部草稿/排序/排除/失效值、取消、无效名称保存、Inspector 应用以及非活动配置保留。
  日志 `/tmp/datascalpel-center-projection-ui-final.log`。触及文件 ESLint 通过；TypeScript 仍只报告既有 MCP 与 GPKG 两处错误，未修改无关模块。
  日志 `/tmp/datascalpel-center-projection-eslint-final.log`、`/tmp/datascalpel-center-projection-tsc-final.log`。
- 最终 Contracts/Business/Engine 主代码编译通过（`/tmp/datascalpel-center-projection-main-final.log`）；
  在错误路径使用原始分析数组下标后，投影专项 4 项复验通过，新增“前一分析无效时后一投影仍定位原下标”的反例。
  日志 `/tmp/datascalpel-center-projection-last-tests.log`。上述专项与 127 项联合回归有重叠，不累加为独立测试总数。

- 4.31 联合专项：Contracts 1 项，Engine 空间 68 项、CenterGeometryStatistics 6 项、图门槛 24 项、错误分类 23 项，共 122 项通过。
  覆盖三角形几何中位中心、强权重/重合点、线面质心与原线输出、原数字 ID 平局顺序、独立 Map/同字段名、
  权重/NULL 组/全零/空输入、重复/NULL ID、5001 要素保护、总顶点预算、各输出预检、椭圆轴长/σ/退化及安全错误。
  另有大小不同面要素的实际 Spark 对照：每要素一个质心，不隐式按面积加权，中央结果保留完整原面与原类型 ID。
  日志 `/tmp/datascalpel-center-final-tests.log`；没有真实 ArcGIS 服务或大规模验收，不据此勾选节点全部完成。
- 4.31 前端中心专项 3 项通过，覆盖新旧门槛/非法结构、未挂载分析项、取消不提交、无效草稿和模式确认保留隐藏表名。
  触及文件 ESLint 通过；日志 `/tmp/datascalpel-center-ui-tests.log`、`/tmp/datascalpel-center-eslint.log`。
  随后中心与最近邻联合 2 文件、6 项通过；Contracts/Business/Engine 主代码编译通过。
  TypeScript 仍仅报告既有 MCP 状态索引与 GPKG 元数据联合类型问题，无中心节点新增错误；未修改无关代码。
  日志 `/tmp/datascalpel-center-ui-final-tests.log`、`/tmp/datascalpel-center-main-compile.log`、`/tmp/datascalpel-center-tsc-final.log`。

- 4.30 联合专项：Contracts 1 项，Engine 空间 62 项、NearestGeometrySupport 3 项、图门槛 23 项、错误分类 22 项，共 111 项通过。
  覆盖全部同距恢复、相同位置不同身份、真实距离/最近位置端点、日期线与测地长度一致、NULL/Empty/空候选、零距离线、重复/NULL ID 惰性失败、
  非点测地编译/运行拒绝、共享 Dataset/内部字段冲突、连接线字段/表名冲突、未启用/旧版设置保留及 4.30 门槛。
  搜索专用 Geometry 与原投影分开，未命中来源的 EMPTY 不会改成 NULL；新增实际投影回归已通过。
  固定与动态半径样本的物理计划包含 DistanceJoin 或 BroadcastIndexJoin，无半径另含 KNN；非普通笛卡尔积，不代表大规模验收完成。
- 4.30 前端最近邻专项 3 项通过：新旧/非法结构/版本门槛、未挂载投影保留、策略确认与取消、连接线取消/无效草稿及关闭保留。
  随后最近邻、一元几何及 Overlay 联合串行运行 4 文件、11 项通过；并行时的单项超时未被忽略，改用串行和明确 30 秒测试时限复验。
  两个输出表名 Form 使用独立名称以避免 DOM ID 重复。触及文件 ESLint、Contracts/Business/Engine 主代码编译通过。
  TypeScript 仍仅报告既有 MCP 状态索引与 GPKG 元数据联合类型两处错误；未修改无关代码。
  验证日志：`/tmp/datascalpel-nearest-final-tests.log`、`/tmp/datascalpel-nearest-ui-tests.log`、`/tmp/datascalpel-nearest-index-tests.log`、`/tmp/datascalpel-nearest-main-compile.log`。

- Contracts `TrackConfigurationTest`：5 项通过（含驻留四模式、运动窗口往返、缺失策略、非法结构及次序防御性复制）。
- Engine `SpatialAnalysisProcessorSparkTest`：此前 24 项通过（含旧空间回归、事件/六边形、驻留四输出及运动八组指标、窗口/单位/阈值/日期变更线/固定边界/空输入/无效配置不传播）；区域汇总新增验证见下方。
- `DwellRangeAssignmentTest`：6 项通过（慢速漂移反例、阈值相等、前后扩展、不复用/NULL/Empty、多轨迹、跨日期线中心与凸包及安全错误）。
- `TrackTimeBoundarySupportTest`：3 项通过（月末、闰年、夏令时、负时间和左闭右开）。
- 前端 `trackConfiguration.test.ts`：3 项通过（新旧能力门槛、结构、日历参数往返）。
- 事件 Inspector 专项：3 项通过（未打开字段/条件保留、边界弹窗编辑保存、语义切换确认）。
- 前端六边形专项：2 项通过（尺寸标签、新旧默认、结构与版本门槛）。
- 运动阶段 `CanvasGraphPlanTest` 16 项、`RunnerFailureClassifierTest` 16 项通过（含 4.22 驻留及 4.23 运动门槛和安全 SCHEMA 错误）；该轮 Engine 56 项、Contracts 5 项，共 61 项通过。
- 前端驻留配置与 Inspector：6 项通过（四模式往返、版本门槛、旧语义、无效草稿、未打开字段保留、输出切换与语义确认）。
  驻留及所触及 Parser/摘要 ESLint 通过。
- 前端运动配置与 Inspector：6 项通过；最新联合运行运动、驻留配置及轨迹配置共 4 个文件、12 项通过，运动及触及 Parser/默认值/摘要 ESLint 通过。
- 4.24 区域统计前端配置/Inspector：2 个文件、5 项通过（新旧 JSON/4.24 门槛、非法枚举、失效组合保留与应用、未打开配置字段）；触及文件 ESLint 通过。
- 4.24 区域统计 Maven 专项：`WithinStatisticContractTest` 2 项、`SpatialAnalysisProcessorSparkTest` 28 项、`CanvasGraphPlanTest` 17 项，共 47 项通过。
  覆盖 100→50 分摊、原值与分摊均值区分、缺失值加权分母、字段 Count/Any、零测度/空区域/空输入、内部别名、非法组合整体拒绝、WGS84 与单位独立性及 4.24 门槛。
  Contracts/Business/Engine 主代码编译通过；未执行完整后端构建或官方服务对照。
- 4.25 最新专项：Contracts 3 项，Engine 空间 33 项、图门槛 18 项、错误分类 17 项，共 71 项通过。
  覆盖关联键/重复属性、独立总体均值、线/面 90/10、裁剪 MultiPoint、NULL 组、空区域、时间窗、平局排序、空/重复键安全错误、两表冲突及旧扁平模式回归。
- 4.25 前端 2 个文件、7 项通过，覆盖双表 JSON/版本/非法结构、未打开配置保留、失效草稿应用及确认式切换保留设置；触及文件 ESLint 通过。
- 4.26 联合专项：Contracts 5 项（Overlay 2、Within 3），Engine 空间 40、图门槛 19、错误分类 18，共 82 项通过。
  新增 Overlay 7 项覆盖五模式、45 组家族、全遮罩差集/洞/重复来源、低维接触与旧版跨家族、Point/MultiPoint、NULL/Empty、无效几何惰性错误、实际 XYZ→XY 和共享来源 Dataset。
- 4.26 前端 2 个文件、5 项通过，覆盖五模式 JSON/版本、旧策略保留、非法结构与业务草稿、未打开字段投影保留、ERASE 失效字段及确认式策略切换；触及文件 ESLint 通过。
  Contracts/Business/Engine 主代码编译通过；未执行完整构建或真实 ArcGIS 服务对照。
- 4.27 联合专项：Contracts 8 项（轨迹基础 5、重建 3），Engine 空间 46、图门槛 20、错误分类 19，共 93 项通过。
  新增重建 6 项覆盖 Gap/FinishLast/StartNext、共享观测统计、固定周期不跨接、前序表达式、同时间坐标/First/Last 一致、重复次序错误、NULL 时间/几何和重复坐标、单点跳过、非法/随机表达式、绑定冲突与非活动草稿。
- 4.27 前端 3 文件、7 项通过，覆盖 4.27 门槛及旧 4.14/4.21 语义、绑定结构、未挂载表达式保留、策略确认、取消弹窗与关闭保留/无效草稿保存。
  触及文件 ESLint、Contracts/Business/Engine 主代码编译通过；TypeScript 仍仅报告此前 MCP 与 GPKG 两处无关错误。
- 驻留算法、固定边界、事件 Inspector 与六边形专项为此前通过的验证记录，不计入上述最新运行数量。
- 4.29 联合专项：Contracts 1 项，Engine 空间 53、UnaryGeometrySupport 6、图门槛 22、错误分类 21，共 103 项通过。
  包含实际 Spark XY/XYZ/XYM/XYZM、质心/洞内代表点、退化包络/凸包、边界/集合限制、NULL/Empty、无效输入惰性失败、
  M 支持差异、共边反例、原表/原字段保持、真实 Streaming 惰性计划（未启动查询）及 4.29 门槛。
- 4.29 前端联合 3 文件、9 项通过（一元几何 3、轨迹重建配置 3、Overlay 配置 3），包含未选草稿、取消不提交、显式切换确认、无效容差和旧定义不自动补策略。
  触及文件 ESLint、Contracts/Business/Engine 主代码编译通过。TypeScript 仍只报告既有 MCP 状态索引及 GPKG 联合类型问题。
  未执行完整构建、真实空间数据规模验收或全页面浏览器验收，不以这组专项把完整路线图标记完成。
- 随后 UnaryGeometrySupport 专项扩至 8 项并全部通过：新增 DP 洞越出简化外壳时安全失败、Topology Preserving 保留合法洞、线顶点位移容差及合法 Empty 退化；并覆盖线性单位换算与角度单位限制。
  最后一次 Inspector 专项 3 项及 ESLint 通过，处理 CRS 以紧凑元数据/帮助显示；非 WGS84 的角度判断沿用 Compiler 结果，不在前端新增 CRS 映射。
- 4.28 联合专项：Contracts 9 项（轨迹基础 5、重建 4），Engine 空间 49、测地路径 6、图门槛 21、错误分类 20，共 105 项通过。
  覆盖高纬弧线、双向/反复日期线、精确 ±180°、极区/对跖点有限输出、重复点/空输入、非法坐标/段长/超限、
  旧 LineString 保留、Spark 实际 MultiLineString/XYZ、观测统计不变、非活动草稿和 4.28 门槛。
- 4.28 前端 3 文件、9 项通过，覆盖旧 4.27 不自动插入路径、4.28 JSON/门槛、非法结构、隐藏配置及确认式切换、无效段长草稿。
  触及文件 ESLint 与 Contracts/Business/Engine 主代码编译通过；TypeScript 仍只报告 MCP 状态索引和 GPKG 元数据联合类型两处既有问题。
  未执行完整构建或真实 ArcGIS 服务对照；本次未引入新生产依赖。
- 轨迹及六边形触及文件 ESLint 通过；Business/Contracts/Engine 主代码编译通过。
- 早期区域汇总/叠加阶段 TypeScript 应用检查仍有 MCP 索引类型与 GPKG 元数据联合类型错误；4.34 最新结果见上方，未修改无关代码。
- 联合 Maven 测试因 Business 的 ComputeEngine/GatewayServiceSpec 等旧测试编译错误中断；
  已改用 Contracts + Engine 专项测试，未更改这些无关测试。

## 4.36 验证记录

- Contracts 3 项，Engine 单位换算 22 项、空间节点 80 项、图门槛 29 项，共 134 项通过。
  日志 `/tmp/datascalpel-spatial-units-tests-final.log`；涵盖国际/美国英尺阈值差异、独立距离输出、
  Within 裁剪面积/长度、Motion 美国测量高程与国际速度独立性，以及所有嵌套/非活动单位路径。
  首轮最近邻测试错误地把实际 Decimal 强转为 Double，改为 Number 读取后通过；未改动生产字段类型或距离算法。
- 新单位前端专项 1 文件、3 项通过（`/tmp/datascalpel-spatial-units-ui-recheck.log`），
  覆盖全部选项往返、9 个节点的版本门槛、隐藏配置保留和切换单位不改变数值。
- 扩大前端回归时 4 文件中 12 项通过、轨迹汇总的 3 项弹窗交互超时
  （`/tmp/datascalpel-spatial-units-ui-final.log`）；轨迹统计独立重跑 5 项全部通过
  （`/tmp/datascalpel-spatial-units-track-isolated.log`），未修改超时设置。
  最近邻/运动另组 5 项通过、最近邻策略切换 1 项超时（`/tmp/datascalpel-spatial-units-other-ui.log`），不计为整组通过。
  最近邻超时项按原超时单独重跑通过（1 项、另外 2 项未选执行，`/tmp/datascalpel-spatial-units-nearest-isolated.log`）。
  本轮新增及所选回归用例均有通过记录，但并行执行发生超时，不能宣称整组一次通过。
- Contracts/Business/Engine 主代码编译、TypeScript 应用检查及触及文件 ESLint 通过，
  日志分别为 `/tmp/datascalpel-spatial-units-main.log`、`/tmp/datascalpel-spatial-units-tsc-final.log`、`/tmp/datascalpel-spatial-units-eslint.log`。
- 不包含真实 ArcGIS 服务、完整构建或浏览器全页面/规模验收；公共距离/面积补齐不代表完整路线图完成。

## 4.37 验证记录

- 最终联合 Contracts 4 项、Engine 122 项（中心矩 3、空间节点 84、图门槛 30、Spark JAR 血缘 5），
  共 126 项通过：`/tmp/datascalpel-within-dispersion-tests-final-pass.log`。
  覆盖官方公式与矛盾文字反例、等权/缺失/非有限/零权重、1e12 偏移与分区合并、实际线面交叠、
  空区域、主表/组表分别聚合、字段及两侧 Geometry 聚合血缘，以及逐项 4.37 门槛。
- 首轮双表血缘暴露区域键 count(1) 与 ROW_NUMBER 的来源问题；键计数改为语义等价的 count(key)，
  ROW_NUMBER 仅针对可解析分区/排序建立依赖，未知来源及常量排序反例继续 FIELD_PARTIAL。
  反例最初使用可证明常量的未标记输入，改为真实未标记 Range 来源后通过，未削弱未知来源保护。
- 前端最终联合 3 文件、14 项通过：`/tmp/datascalpel-within-dispersion-ui-verified.log`。
  覆盖加权配置/门槛、非法组合草稿保留、未打开弹窗字段保留、统计弹窗取消隔离与公共单位回归。
  首轮取消用例暴露 Modal 直接写主 Form，已修正为独立草稿；没有通过删除断言或增加超时规避。
- Contracts/Business/Engine 主代码编译通过（`/tmp/datascalpel-within-dispersion-main.log`）；
  最后一次 TypeScript 与触及文件 ESLint 均通过（`/tmp/datascalpel-within-dispersion-tsc-final.log`、
  `/tmp/datascalpel-within-dispersion-eslint.log`）。最后的 Engine 联合测试也重新编译了窗口血缘变更。
- 公式图片核对不等于真实 ArcGIS 服务验收；本轮未做完整构建、全页面浏览器或规模验收。
  不把本阶段通过记录解释为 12 节点或完整路线图完成。

## 4.38 验证记录

- 最终 Contracts 3 项，Engine 156 项（格网/时间/血缘 17、空间节点 84、图门槛 31、错误分类 24），
  共 159 项通过：`/tmp/datascalpel-grid-scope-final-tests.log`。
  覆盖方格/六边形负原点与边界、半开筛点、完整形状不裁剪、跨范围 ID 一致、零输入补空间而不补时间、
  H3 非活动值、非法原点/反转范围/展开上限、实际惰性安全失败和新模式字段血缘。
  六边形顶点及水平边正负坐标另有专项记录；最终补空明确保留实际边界成员，不依赖浮点交叠面积偶然为正。
- 首次代码编译暴露来源 Dataset 重赋值后的 lambda 捕获，改为直接循环；首次测试编译修正泛型 getAs 的断言歧义。
  Contracts 旧样例最初缺少既有 primitive 必填字段，补齐 fixture 后通过；未改变 Jackson 解析策略或旧契约。
- 前端最终 4 文件、21 项通过（`/tmp/datascalpel-grid-scope-final-ui.log`），包含新范围及 H3、统计弹窗和区域统计配置回归。
  恢复旧版确认测试先等待确认对话框实际挂载，再点击确认，避免误点底层同名按钮；未删除确认要求。
- Contracts/Business/Engine 主编译通过（`/tmp/datascalpel-grid-scope-main-verified.log`），
  最终 Engine 联合测试又编译了边界成员保护。普通沙箱的主编译曾被 `.m2` 缓存写权限阻断，经授权重试通过，未替换 Wrapper/settings。
  TypeScript 与触及文件 ESLint 通过（`/tmp/datascalpel-grid-scope-tsc-final.log`、`/tmp/datascalpel-grid-scope-eslint-final.log`）。
- 未执行真实 ArcGIS 服务、全页面浏览器或百万格网规模验收；平面原点/范围是平台适配能力，不声明为 GA 同名参数。
  完整路线图与各节点最终验收仍保持开放。

## 4.39 验证记录

- 最终 Contracts 3 项，Engine 172 项（日历边界 9、格网/时间/血缘 23、空间节点 84、图门槛 32、错误分类 24），
  共 175 项通过：`/tmp/datascalpel-calendar-tests-verified.log`。
  覆盖 Jan31/月末、Feb29/闰年、纽约 23/25 小时日历日与固定 24 小时、混合小时步长/日历窗宽穷举对照、
  重叠/留空/前 Epoch、Apia 跳日、NULL、无偏移歧义/不存在时间、微秒精度、候选超限及算术安全错误。
  实际 Spark 验证月窗计数无重复、空空间仅补实际窗口、Within 主表/组表身份与时间字段完整血缘、
  Java datetime API 开关以及真正触发 Action 后的候选超限；没有放宽未知血缘保护。
- 首次 Spark 专项因普通沙箱禁止回环端口绑定失败，经授权重跑通过；未改 Maven Wrapper/settings 或连接业务数据库。
  新增关联组表样例最初缺少必须的区域字段投影，补齐 fixture 后通过，没有修改生产规则绕过校验。
- 最终前端 4 文件、21 项通过：`/tmp/datascalpel-calendar-ui-verified.log`。
  覆盖两节点 4.39 门槛（含非活动模式）、旧配置不自动开启、结构错误/业务草稿、模式切换确认和隐藏单位保留、
  时间弹窗取消后重新打开与保存无效草稿，以及平面格网和区域统计配置回归。
- Contracts/Business/Engine 主编译通过（`/tmp/datascalpel-calendar-main-final.log`）；
  TypeScript 与触及文件 ESLint 通过（`/tmp/datascalpel-calendar-tsc-final.log`、`/tmp/datascalpel-calendar-eslint.log`）。
  最后 Engine 联合测试又编译了新增测试，生产代码未新增依赖、Action、接口或 Manifest/Result 字段。
- 未执行真实 ArcGIS 服务、完整构建、全页面浏览器或规模验收；明确时区/参考时刻和候选限制是平台裁决，
  不把本地边界样例解释为官方结果完全相等。12 个整节点与路线图剩余项继续保持开放。

## 4.40 验证记录

- 后端联合 154 项通过：Contracts DBSCAN 往返 3 项；Engine 点聚类 9、已有空间节点 84、图门槛 33、错误分类 25。
  日志 `/tmp/datascalpel-dbscan-tests-verified.log`。覆盖三分区时空连续链、同位置不同时刻、非核心边界不合并两簇、
  自身计数/重复位置、无核心/空输入、空间及时间阈值包含边界、日期线测地邻域、缺时间、NULL/Empty 点、
  实际非 Point、旧新算法非空唯一 ID，以及模式/字段/Geometry 元数据的稳定错误。
  Compiler 实际计划验证零 Spark Job、Checkpoint 未改变、FIELD_COMPLETE 及簇号对 Geometry/时间/身份的集合依赖。
  Runner 正式与试运行批处理均使用 execution 运行上下文，不会误用 schema-only 聚类表达式。
- 初轮发现 Spark 自关联的字段身份歧义，改用明确左右限定列后重跑通过；没有关闭 Spark 歧义保护。
  测试泛型断言重载错误也已修正。Contracts/Business/Engine 主代码编译通过：`/tmp/datascalpel-dbscan-main-final.log`。
- 前端最终 3 文件、30 项通过：`/tmp/datascalpel-dbscan-ui-complete.log`。
  包含点聚类 10、日历窗口 5、通用 Registry 15；覆盖旧定义保留/4.40 门槛、非法结构与业务草稿、
  时空语义切换确认/取消、隐藏时间保持、旧 HDBSCAN/MULTI_SCALE 分支恢复、DBSCAN 编辑值恢复，以及卡片模式/时间参数不展示。
  算法分支草稿只在当前面板会话保留，应用仅保存活动分支；不宣称跨关闭持久化所有算法参数。
- 通用 Registry 原先 5 项失败来自旧单表 fixture、固定 40 节点数量及旧展示断言；改为当前 Operation 样例、
  稳定类型全集无重复/无缺失、现有批流矩阵和配置派生尺寸，不修改无关节点运行能力。
  修改测试时另出现括号、枚举拼写及兼容视图类型问题，均已修正；Segmented 增加明确算法泛型。
  最终 TypeScript 与触及文件 ESLint 通过：`/tmp/datascalpel-dbscan-tsc-last.log`、`/tmp/datascalpel-dbscan-eslint-last.log`。
- 未执行真实 ArcGIS 服务、全页面浏览器、全工程构建或规模验收；GraphFrames 分布式执行不代表无限容量。
  HDBSCAN/四诊断、完整时间单位及其他路线图能力仍待开发，12 个节点不因上述局部证据标为全部完成。

## 4.41 验证记录

- 最终后端 Contracts 3 项、Engine 177 项（Within 格网 10、已有格网/时间 23、已有空间节点 84、图门槛 34、错误分类 26），
  合计 180 项通过：`/tmp/datascalpel-within-grid-verified-backend.log`。
  覆盖跨两个格子的线长与 100→50 分摊、跨格网面面积、六边形对边距离、Point 边界唯一归属/跨范围 ID 一致、
  空输入/显式补空、内部区域不进入 Map、自动区域键与主表/组表关联、仅实际时间窗补空及不虚构组记录。
  还覆盖配置错误路径、惰性百万格子保护、实际非法几何安全失败、两形状×数据/显式范围的 FIELD_COMPLETE 血缘与安全错误分类。
- 血缘专项首次发现旧临时区域行号导致 GROUP_KEY 无法可靠归属；GRID 路径改用已有稳定格网 ID 后通过。
  未放宽公共未知血缘保护，不为格网伪造物理来源区域资产。旧区域表路径保持原样。
  初轮测试的 Context 构造器及缺失必需布尔字段也已修正，没有改变生产解析安全规则。
- 前端最终 4 文件、31 项通过：`/tmp/datascalpel-within-grid-final-ui.log`。
  覆盖新旧 JSON/非活动 4.41 门槛、非法结构与可保存草稿、模式切换确认/取消、区域表值保留、独立格网 Modal 取消/保存、
  范围切换后隐藏坐标恢复、只需 summary 表的卡片、配置派生尺寸和不展示坐标，以及 Bins/日历/Registry 回归。
  TypeScript 与触及文件 ESLint 通过：`/tmp/datascalpel-within-grid-verified-tsc.log`、`/tmp/datascalpel-within-grid-verified-eslint.log`。
- Contracts/Business/Engine 主代码编译通过：`/tmp/datascalpel-within-grid-verified-main.log`。
  曾将 Business 纳入测试编译，遇到既有 Kong Gateway 测试与当前 GatewayServiceSpec 接口不匹配，详见
  `/tmp/datascalpel-within-grid-final-backend.log`。未改网关代码；最终后端测试限定 Contracts/Engine 及其依赖，不将 Business 测试声明为通过。
- 未执行全工程构建、真实 ArcGIS 服务、全页面浏览器或容量验收。MultiPoint/线面共边采用既有区域相交语义，
  其官方结果对照仍未完成；本阶段补齐规则格网功能，不勾选整个 Within 或缩小 12 节点目标。

### 4.42 平面面轨迹验证记录（2026-09-08）

- 后端最终 171 项通过：Contracts 5；Engine 166（TrackAreaSparkTest 9、TrackFieldStatisticsSparkTest 5、
  SpatialAnalysisProcessorSparkTest 84、CanvasGraphPlanTest 35、RunnerFailureClassifierTest 27、SparkJarLineageRuntimeTest 6）。
  证据：`/tmp/datascalpel-track-area-final-backend.log`。未连接业务数据库或执行真实 ArcGIS 服务。
- 覆盖可变半径与弯折轨迹（不是全局凸包）、相邻 Polygon 连接/单面孔洞、面单观测与空输入、
  原 Geometry 距离 gap、三种共享端点统计、固定周期始终 GAP、NULL/Empty/缺时间排除；
  数值表达式零作业预检、聚合/生成器/非确定性拒绝、NULL/负/零/非有限点半径的惰性错误、原 Map/CRS/Schema 保持。
- 初次血缘测试暴露有序轨迹 count(1) 窗口及数组 lambda 的未知来源。已改为过滤后非空时间列计数（相同结果），
  并为 ArraySort/ArrayTransform 绑定已知元素依赖；未放宽公共未知来源规则。最终面 Geometry 的原形状/半径 FIELD_COMPLETE
  和未知外部字段仍 FIELD_PARTIAL 均通过，原有 Spark JAR 血缘回归通过。初次失败日志保留用于追溯。
- 前端 5 文件 31 项通过：`/tmp/datascalpel-track-area-verified-ui.log`。
  最后统一缓冲类别标签后再次全数通过：`/tmp/datascalpel-track-area-complete-ui.log`。
  包括 4.42/非活动门槛、旧配置不插入面策略、结构拒绝、形态切换确认/取消、独立弹窗取消、
  无效字段草稿应用、字段/表达式及线/面隐藏分支恢复、Canvas/摘要不泄漏表达式、等待解析与失效字段区分和 Registry 回归。
  交互测试等待确认弹窗异步提交后再断言；多次切换场景单用例允许 60 秒，不扩大全局超时。
- Contracts/Business/Engine 主代码编译通过：`/tmp/datascalpel-track-area-final-main.log`。
  TypeScript 与 ESLint 初检通过（`/tmp/datascalpel-track-area-final-tsc.log`、`/tmp/datascalpel-track-area-final-eslint.log`）；
  末次标签整理后的 TypeScript/ESLint 复核同样通过：`/tmp/datascalpel-track-area-verified-tsc.log`、`/tmp/datascalpel-track-area-verified-eslint.log`。
- 仍未执行全工程构建、浏览器全页面、百万顶点容量或真实 ArcGIS 对照；平面连接精度/孔洞/负半径等平台规则已在节点 MD 标明，
  不将本地功能测试当成官方数值等价。测地面/日期线、轨迹窗口缓冲表达式及其余路线图仍是完整目标的一部分。

### 4.43 缓冲观测窗口验证记录（2026-09-08）

- 后端最终 180 项通过：Contracts 6；Engine 174（TrackAreaSparkTest 16、TrackFieldStatisticsSparkTest 5、
  SpatialAnalysisProcessorSparkTest 84、CanvasGraphPlanTest 36、RunnerFailureClassifierTest 27、SparkJarLineageRuntimeTest 6）。
  日志：`/tmp/datascalpel-buffer-window-final-backend.log`。未连接业务数据库或真实 ArcGIS 服务。
- 覆盖十种窗口统计的 NULL/空帧、前序/后续闭区间、history 均值实际生成不同半径、时间并列的显式排序及歧义拒绝、
  轨迹和固定周期隔离、普通 gap 前求值/共享端点半径保持、缓冲/拆分同名绑定作用域隔离、内部列不泄漏。
  后端校验数量/名称/来源/类型/范围/统计及精确路径；FIELD 分支不执行隐藏错误绑定。
  真正零 Spark Job 预检、缺历史无 fallback 的安全半径失败、窗口半径及原 Geometry 的 FIELD_COMPLETE 血缘均通过。
  旧面/线轨迹与其余轨迹节点专项回归保持通过。
- 前端 6 文件 36 项通过：`/tmp/datascalpel-buffer-window-final-ui.log`。
  覆盖非空/隐藏窗口 4.43 门槛、空/null 4.42 兼容、非法结构拒绝/无效整数草稿保留、窗口 Modal 取消/保存、
  隐藏绑定恢复、完整排序、删除确认、32 项新增限制，以及 Canvas 不展示绑定表达式和数值。
  Runner 安全摘要代码只新增活动窗口数量；不将上述前端测试作为真实运行日志验收的证据。
  初轮删除确认用例依赖环境默认语言失败，改为显式中文“删除窗口/取消”后通过；未取消确认或放松测试断言。
- TypeScript、触及文件 ESLint 通过：`/tmp/datascalpel-buffer-window-final-tsc.log`、`/tmp/datascalpel-buffer-window-final-eslint.log`。
  Contracts/Business/Engine 主代码编译及收尾复核通过：`/tmp/datascalpel-buffer-window-main.log`、`/tmp/datascalpel-buffer-window-final-main.log`。
- 未做全工程构建、全页面浏览器、真实 ArcGIS 服务或容量验收；官方窗口在 gap/周期附近的次序仍待实际对照，
  不以受控数值窗口声明 Arcade API 兼容，也不将测地面轨迹或整个 Reconstruct 标记完成。

### 测地面内部点缓冲基础验证（2026-09-08，协议仍为 4.43）

- 新增 `TrackGeodesicDisk` 及 11 项几何专项；覆盖赤道/高纬 WGS84 半径、日期线真实圆周交点、
  ±180°/极点等价经度、南北极闭合、相切附近、逐观测半径、细采样及不可变快照。
  扩展测试包含 80 个确定性地理样本、接近四分之一子午线的小圆盘及极点附近 1 米缓冲，
  验证有限/有效 XY 几何、原点包含和对跖点不被错误包含；不是完整全球数据域或精度保证。
- 安全校验包括非法坐标/半径、采样上限、超出内部算法范围和 JTS 原始坐标错误不泄漏；
  `TRACK_GEODESIC_BUFFER_RANGE_NOT_SUPPORTED` 已接统一 Runner 分类与安全消息，SCHEMA、不可重试。
- 新增 Spark UDF 专项实际执行两个不同半径，确认分析计划阶段零 Job，Executor 返回日期线 MultiPolygon，
  大缓冲包含小缓冲但不覆盖格林尼治线。该用例直接验证内部原语，不冒充完整 Operator 测地面接入。
- 最终 61 项通过：TrackGeodesicDiskTest 11、TrackGeodesicPathTest 6、TrackAreaSparkTest 17、
  RunnerFailureClassifierTest 27。原平面面轨迹、窗口/统计及测地面组合仍被 Compiler 拒绝的回归保持通过。
  日志：`/tmp/datascalpel-geodesic-disk-final-tests.log`；前两轮日志为
  `/tmp/datascalpel-geodesic-disk-tests.log`（41 项）与 `/tmp/datascalpel-geodesic-disk-extended-tests.log`（44 项）。
  Maven Wrapper 目标模块主/测试代码编译成功，未运行全工程构建或连接业务数据库。
- 未修改前端、Contracts、协议门槛或现有面积 Operator；未做真实 ArcGIS、全页面及规模验收。
  点原语仍未暴露，下一步继续 Polygon/MultiPolygon 缓冲、相邻测地面连接、大区域及完整惰性执行路径。
  本阶段不是测地面轨迹或整个 Reconstruct 的完成声明，12 节点与其余路线图继续保持完整范围。

### 测地面连接核心验证（2026-09-08，协议仍为 4.43）

- 新增 TrackGeodesicHull 的方向构造与全顶点支持校验；点缓冲和连接共享 TrackGeodesicAreaBoundary，
  保留真实采样与渲染闭合的区分。参考点为显式输入，点观测对提供 WGS84 中点；没有平面回退或新增依赖。
- 几何专项覆盖不等半径连接、高纬弧线而非纬度弦、日期线而非跨格林尼治连片、南北极包围、
  相邻连接保留折弯空白、原面顶点与内部采样、重复/排列不变、退化与局部域/计算量保护。
  40 个确定性点云包含最高 8000km 局部范围，检查有限有效渲染及原采样点包含；不代表全球域已经完成。
- 扩展反例发现原始极点及边中途过极点的经度歧义。新增真实经线拆分和北/南极顺时针闭合方向；
  共享 TrackGeodesicPath 的极点起止点改用相邻经线，不改变物理极点或来源 Geometry。
  反例同时检查另一侧极区不得被错误包含；没有放宽断言或将失败样例删除。
- 初轮 26 项通过（`/tmp/datascalpel-geodesic-hull-tests.log`），随后极点用例的失败记录保留在
  `/tmp/datascalpel-geodesic-hull-extended-tests.log`、`/tmp/datascalpel-geodesic-hull-pole-crossing-tests.log` 等文件。
  极点修正后几何 30 项通过：`/tmp/datascalpel-geodesic-hull-meridian-tests.log`。
- 最终后端 159 项通过：TrackGeodesicHullTest 12、TrackGeodesicDiskTest 11、TrackGeodesicPathTest 7、
  TrackAreaSparkTest 18、RunnerFailureClassifierTest 27、SpatialAnalysisProcessorSparkTest 84。
  日志：`/tmp/datascalpel-geodesic-hull-verified-backend.log`。覆盖新连接原语的零 Spark Job 分析与 Executor 实际弧线，
  旧面/窗口/统计及空间节点回归通过；主代码和测试代码通过工程 Maven Wrapper 编译。
- 新增三个 hull 错误归为 SCHEMA、不可重试，安全消息不含实际坐标或半径；没有修改前端或 Contracts。
  检查 Sedona 1.9.0 制品字节码确认 `Spheroid.distance` 对非 Point 使用质心；将非点 gap 的真实距离实现登记为后续接入项。
- 未执行全工程构建、真实 ArcGIS、浏览器或容量验收；Operator 仍拒绝测地面组合。
  Polygon/MultiPolygon 缓冲、非点距离、大域处理和整段接入仍待继续，完整 Reconstruct 与路线图不勾选完成。

### 测地面 Polygon 足迹与有序装配验证（2026-09-08，协议仍为 4.43）

- 新增 TrackGeodesicPolygon：WGS84 环方向、真实测地边加密、法向偏移带、顶点圆形连接和原区域合并。
  保留凹形，正缓冲缩小/闭合孔洞，多部件只有实际相交后才合并；长边不使用端点圆盘凸包近似。
  加密环在局部等距方位图中验证拓扑，避免日期线假边；该图不用于替代测地缓冲或原 Geometry 距离。
- 新增 TrackGeodesicAreaGeometry，独立保存参考、不可变真实顶点及 Geometry 快照；仅连接相邻观测，
  单例保留孔洞。面采样容量统一使用 AREA 错误码，未知底层异常不带坐标或 cause。
- 首轮 41 项中发现两项问题：面采样容量仍返回路径代码，以及 Union 后精确 covers 对约
  `2.473336205982557E-19` 平方度舍入残差判否。前者修正错误映射，后者同时检查丢失面积比例与每个原顶点距离；
  未对生产结果吸附/扩大，也不将该检查容差宣传为算法误差保证。
  初轮与诊断日志：`/tmp/datascalpel-geodesic-polygon-tests.log`、`/tmp/datascalpel-geodesic-polygon-union-diagnostic.log`。
- 扩展几何 42 项通过：`/tmp/datascalpel-geodesic-polygon-extended-tests.log`。
  后续补充南北极真实凹顶点，取消只适合凸面的小于等于 180° 极点转角限制；保留完整拓扑校验，
  正反方向及缺失扇区的反例通过，既有凸包/圆盘/路径不回退。
- 最终 174 项通过：TrackGeodesicPolygonTest 13、TrackGeodesicHullTest 12、TrackGeodesicDiskTest 11、
  TrackGeodesicPathTest 7、TrackAreaSparkTest 20、RunnerFailureClassifierTest 27、SpatialAnalysisProcessorSparkTest 84。
  日志：`/tmp/datascalpel-geodesic-polygon-backend.log`。新增 Spark 用例验证日期线孔洞/多部件足迹和
  三个有序面观测的相邻连接，分析阶段零 Job，Executor 实际输出有效 EPSG:4326 XY MultiPolygon。
- 后续新增 TrackGeodesicAreaColumns，内部 Catalyst Struct 明确携带参考、真实顶点数组和渲染 Geometry，
  在解码时也累计检查顶点容量。不是 Canvas/Manifest 第二套协议，不依赖 Geometry.userData。
  TrackAreaSparkTest 扩至 22 项全部通过：`/tmp/datascalpel-geodesic-area-columns.log`。
  验证 Shuffle 后按观测次序聚合、轨迹隔离、日期线单例孔洞、极点辅助闭合不混入真实顶点及逐观测半径；
  分析阶段零 Job。该内部列尚未接入普通 gap/固定周期/共享端点的 Operator 完整管道。
- 内部列收尾后重新执行上述全部专项，共 176 项通过：`/tmp/datascalpel-geodesic-area-final-backend.log`。
  各专项数量同前，TrackAreaSparkTest 为 22 项；主代码和测试代码通过工程 Maven Wrapper 编译。
- 未接入 TrackAreaPlan，测地面组合的 Compiler 拒绝回归仍通过；未修改 Contracts、前端或协议。
  下一步仍需非点 gap 真距离、大域及整节点接入；未做全工程构建、
  业务数据库联调、真实 ArcGIS 服务、浏览器或容量验收，不将 Reconstruct 或路线图标记完成。

### WGS84 非点距离内部基础（2026-09-08，协议仍为 4.43）

- 新增 Wgs84SegmentDistance：完整有限测地弧长参数域、三角不等式上下界与优先细分，
  不假定单峰、不用端点/质心/投影代替最近位置；返回不可变位置对、实际距离与全局下界。
  反转端点保持规范分支，共享最多 250,000 次距离取样预算，超限或精度未达不返回未验证近似。
  数值裕量是工程边界而非严格区间算术或官方精度保证，详见[共享距离设计](canvas-wgs84-geometry-distance.md)。
- 线段专项 11 项覆盖点/退化、内部最近位置、有限弧端点、内部相交、日期线、高纬真实弧、
  极点/对跖分支、反向/互换、近似平行和预算耗尽；32 组确定性方位/纬度样本分别构造法向足点和交叉点。
  不能以这些本地样本声明全球数据域、实际规模或官方结果已经验收。
- 新增 Wgs84LinearDistance 与 5 项专项：Point/MultiPoint/LineString/MultiLineString 的全部真实边，
  部件不连接、开放线不闭合、空部件不变原点、原数据不修改；先检查两侧全部坐标，再允许近零上界早停。
  只完成线性入口，明确拒绝 Polygon，防止把仅边界距离当成完整面区域距离。
- 安全代码在 Runner 通用空间异常前识别，跨 Reconstruct/Nearest 的包装异常测试确认正确类别、不可重试、无坐标消息。
  非法坐标/类型、搜索超限与精度未达属于 SCHEMA；内部精度/预算参数非法属于 CONFIGURATION。
  初轮 8 项、扩展 39 项及线性 44 项分别通过：`/tmp/datascalpel-geodesic-segment-distance.log`、
  `/tmp/datascalpel-geodesic-segment-distance-extended.log`、`/tmp/datascalpel-geodesic-linear-distance.log`。
- 新增 GeodesicDistanceSparkTest 2 项：距离、下界与同次搜索的位置对通过一个 Struct 返回；
  Shuffle 后实际计算内部足点和日期线位置，NULL 保留，非法后续坐标在消费时返回安全代码，分析阶段零 Job。
- 最终 198 项全部通过：距离线段 11、线性 Geometry 5、距离 Spark 2、Nearest Geometry 3、
  TrackArea Spark 22、Polygon 13、Hull 12、Disk 11、Path 7、Runner 分类 28、SpatialAnalysis Spark 84。
  日志：`/tmp/datascalpel-geodesic-distance-final-backend.log`。主代码和测试代码通过工程 Maven Wrapper 编译。
- 未修改 Contracts、前端、Manifest/Result 或当前 Operator 能力门槛；保留完整路线图和 12 节点目标。
  还需 Polygon 内含/孔洞/接触、区间阈值与排名、非点候选召回、完整管道/血缘、大域性能及官方对照。
  未运行全工程构建、浏览器、真实数据库或 ArcGIS 服务；不把内部原语认定为完整非点节点已可用。

### 局部测地区域距离与全局边对搜索（2026-09-08，协议仍为 4.43）

- 新增 Wgs84PolygonRegion：复用无缓冲域/拓扑检查，定位只使用原始 WGS84 环的方位绕数。
  外环/孔洞/多部件、真实环顶点与未决对向方位分别处理；超出参考强凸球先判外部，避免反向点误选补集。
  定位和距离取样共享预算，不对每个点重置；局部验证域之外和连续近接触拓扑仍待补齐。
- 新增 Wgs84GeometryDistance：两侧全部校验后检查所有独立原顶点，含开放线最后端点；
  有真实公共位置时返回零距离与同一位置对。孔洞内保持正边界距离，无包含顶点的交叉面保留含零距离区间。
  未决区域定位不能被丢弃后返回正下界，也不把接近零的上界直接吸附为零。
- 初轮 28 项出现两项预算错误（交叉面、简单分离面）：`/tmp/datascalpel-geodesic-region-distance.log`。
  将所有边对的初始区间加入统一优先队列后，交叉面通过，分离面仍超限：
  `/tmp/datascalpel-geodesic-region-global-search.log`。未提高 250,000 次共享取样上限或删除失败用例。
- 为近似平行短弧增加 ECEF 弦界和曲率偏离裕量。初次采用 JTS 三维线段距离作为下界时，
  对向交换用例产生约 131 米差异，证明不能将该结果直接作为保守下界；该调用已移除。
  现使用任意单位轴上的两弦投影区间分离量，扣除每段 `L²/(8Rmin)` 及数值裕量，
  未选到最优轴只会少剪枝。最终返回距离和位置仍由 WGS84 取得，不替换成弦或投影结果。
  该失败日志：`/tmp/datascalpel-geodesic-region-chord-bound.log`；修正后 28 项通过：
  `/tmp/datascalpel-geodesic-region-support-bound.log`。
- 更紧的下界也说明原位置测试把“1 厘米距离精度”误当成“1 米位置精度”。保留原坐标断言，
  将对应几何和 Spark 用例请求距离精度收紧至 0.0001 米；未放宽断言。文档继续明确两种误差不等价。
- 新区域定位 6 项包括凹面、孔洞、真实顶点/未决边界、日期线、高纬弧、南北极、孔中岛、多部件和非法配置；
  32 组确定性测地环均验证正反方向、中心孔、四向内部和外部。区域距离 7 项覆盖内含、孔洞、交叉无内含顶点、
  分离面、线末端内含、毫米级外部点不吸附、日期线辅助边不参与以及先校验完整输入。
- GeodesicDistanceSparkTest 扩至 3 项：实际 Executor 区分区域内零距离与孔洞内正距离，位置对与数值一起返回；
  分析阶段零 Job。新增区域错误已接入 Runner，包装异常下稳定分类、不可重试且不含实际坐标。
- 最终 212 项通过：区域定位 6、区域距离 7、距离线段 11、线性 5、距离 Spark 3、Nearest Geometry 3、
  TrackArea Spark 22、Polygon 13、Hull 12、Disk 11、Path 7、Runner 28、SpatialAnalysis Spark 84。
  日志：`/tmp/datascalpel-geodesic-region-final-backend.log`；主代码/测试代码通过工程 Maven Wrapper 编译。
- 本轮仍为内部实现，未放开 TrackAreaPlan/NearestExactPlan 门槛，未改变 Contracts、UI、Manifest/Result 或 HTTP。
  连续拓扑/近接触、全域、严格阈值与区间排序、非点候选召回和整节点/血缘仍未完成。
  未运行全工程构建、真实数据库/ArcGIS、浏览器或容量验收，不将局部测地区域距离当成路线图已完成。

### 连续测地弧检查与合法环接触修正（2026-09-08，协议仍为 4.43）

- Wgs84ArcTopology 增加共同局部域内有限弧关系：原始方位侧向判断、明确端点/赤道/经线接触，
  非轴向近共线保持未决；距离正下界只用于证明分离。失败域检查不缓存为成功位置。
- Wgs84PolygonArcCheck 在采样前检查原始边，交叉/重叠及同环非相邻接触拒绝。
  使用现有 STRtree 和保守 ECEF 弧包围盒减少候选；建盒、所有候选访问、谓词和回退共享 250,000 次预算，
  不是对每个边对重置。1024 边专项在低于全边对数量的预算内通过，不能代替真实规模验收。
- 扩展初轮 65 项有 1 个错误：合法孔洞顶点与外环边内部接触在离散等距方位图中被判无效，
  日志 `/tmp/datascalpel-geodesic-continuous-extended.log`。修复为将已确认公共顶点按弧长插入相应边再采样，
  返回不可变独立环，不改原数据、不扩大或吸附几何。未删除失败测试，修正后 65 项通过：
  `/tmp/datascalpel-geodesic-contact-noding.log`。
- 新增接触点顺序/去重/幂等、不同采样长度与方向、赤道/日期线/小数端点、多个孔洞、多部件的合法接触，
  同时保留孔洞切断内部和共享边的无效用例。区域定位优先检查全体原始顶点，
  修复孔洞顶点的精确边界证据被外环未决扫角遮蔽；毫米级外点不吸附为边界。
- 安全错误 GEODESIC_TOPOLOGY_PRECISION_NOT_REACHED 经 Footprint、区域入口和 Runner 保持，
  属于 SCHEMA、不可重试、不含数据坐标。拓扑准备与后续距离搜索各自有明确预算，未声称复合过程共享一次预算。
- 最终 229 项通过：弧关系 6、原始环检查 8、区域定位 7、区域距离 7、距离线段 11、线性 5、
  距离 Spark 5、Nearest Geometry 3、TrackArea Spark 22、Polygon 13、Hull 12、Disk 11、Path 7、Runner 28、
  SpatialAnalysis Spark 84。日志 `/tmp/datascalpel-geodesic-continuous-final-backend.log`。
  新 Executor 用例验证合法接触实际零距离、连续交叉/未决安全失败，分析阶段零 Job。
- 孔洞归属、内部连通和渲染布尔仍有离散验证，非轴向极近接触、连续交叉公共位置、大域、严格阈值/排名、
  候选召回及 Operator/UI 接入仍待完成。不修改 Contracts、Manifest/Result、HTTP 或现有节点能力门槛，
  不将本地回归当成真实 ArcGIS 等价、浏览器或容量验收；保留整个路线图和 12 节点目标。

### 原始测地区域拓扑与渲染分离（2026-09-08，协议仍为 4.43）

- 新增 Wgs84PolygonTopology：先验证简单原始弧并将明确接触点加入环，再用原始方位绕数校验孔洞归属、
  孔洞嵌套、多部件填充重叠与孔中岛。不相交环用一个可判定原顶点；有接触时检查每段接触间开放弧，
  防止两个环只在公共顶点穿越而躲过边对交叉检查。无法判定时保持安全精度失败。
- 内部连通采用环—接触位置二部图，三环共点是星形而非假环路；真正孔洞闭合链、外环经孔洞链被切断仍拒绝。
  边候选、接触节点化、环定位及连通图共用单次 250,000 预算，后续距离搜索仍有独立预算。
- TrackGeodesicPolygon.prepareSource 独立返回不可变参考/原始节点化环，不生成 Footprint 渲染区域。
  原来的采样等距方位图来源有效性路径已删除；Wgs84PolygonRegion 直接准备原始来源。
  渲染仍负责偏移带、圆形连接、日期线切分与布尔输出，但不再决定距离区域能否被接受。
- 移除两个未公开内部入口的采样步长参数及不再产生的 INVALID_GEODESIC_POLYGON_SAMPLING_STEP；
  同步调用点、Runner 分类及相关文档。没有改变 Canvas/Manifest/Result/HTTP，未增加生产依赖或节点。
- 初轮 41 项通过：`/tmp/datascalpel-geodesic-source-topology.log`；8 项新区域关系用例加入后 36 项通过：
  `/tmp/datascalpel-geodesic-region-topology-cases.log`。新用例覆盖原始平面 WKT 不合法但测地孔洞合法、
  渲染顶点容量失败不影响测距、公共顶点穿越、三环单点星形、真正接触环路、嵌套孔洞、孔中岛、
  环方向/部件排序、日期线规范化、不可变快照与共享预算/坐标保护。
- 最终 238 项通过：原始区域拓扑 8、弧关系 6、原始环检查 8、区域定位 7、区域距离 7、线段 11、线性 5、
  距离 Spark 6、Nearest Geometry 3、TrackArea Spark 22、Polygon 13、Hull 12、Disk 11、Path 7、Runner 28、
  SpatialAnalysis Spark 84。日志 `/tmp/datascalpel-geodesic-region-topology-final.log`。
  新增 Executor 用例实际检查高纬孔洞、接触链断开和公共顶点穿越；所有相关计划分析零 Job。
- 仍保留局部强凸域与浮点保护边界，不把本轮称作完整全球拓扑、解析测地面布尔或 ArcGIS 数值等价。
  非轴向极近接触、跨区域公共交叉位置、严格 gap/排名、非点候选召回及 Operator/UI/血缘管道继续待办。
  未做全工程构建、业务数据库/ArcGIS 服务、浏览器或容量验收；完整路线图和 12 个节点均未标记完成。

### 测地距离阈值的区间判定（2026-09-08，协议仍为 4.43）

- Wgs84SegmentDistance 的距离精度查询和阈值查询复用同一个全局边对队列/细分实现。
  阈值查询不拿粗距离直接比较：只有位置对上界（含舍入裕量）不大于阈值才判内，
  全局下界严格大于阈值才判外；否则继续同一队列。没有按轮次或边对重置 250,000 次预算。
- Wgs84GeometryDistance.withinDistance 先验证两侧完整输入并复用原始区域包含关系。
  真正内含/公共原始位置能证明零，NULL/Empty 保留。未决包含时边界接近仍能证明范围内，
  但边界分离不能证明面区域分离，因此返回精度错误而非猜测 false。
- 数值保护区内的相等/极近阈值不隐式吸附；达到精度/预算边界时明确失败。低于舍入裕量的阈值
  可使用原始共同端点零证据，边内交叉尚不靠近零距离自动确认为相交。
  新 INVALID_GEODESIC_DISTANCE_THRESHOLD 归 CONFIGURATION、不可重试、不含阈值或坐标；Runner 同步分类。
- 基础 18 项通过：`/tmp/datascalpel-geodesic-threshold-baseline.log`。扩展 53 项初轮有 1 个测试前提错误：
  原微小偏移已在区域角度保护区外，实际判定为 OUTSIDE，并非预设 UNRESOLVED；未改生产角度阈值。
  保留原 OUTSIDE/可分離反例，再增加保护区内位置，并验证其边界确实可分离但区域包含未决不能判外。
  修正后 53 项通过：`/tmp/datascalpel-geodesic-threshold-guard.log`。
- 最终 247 项通过：阈值 7、原始拓扑 8、弧关系 6、环检查 8、区域定位 7、区域距离 7、线段 11、线性 5、
  距离 Spark 8、Nearest Geometry 3、TrackArea Spark 22、Polygon 13、Hull 12、Disk 11、Path 7、Runner 28、
  SpatialAnalysis Spark 84。日志 `/tmp/datascalpel-geodesic-threshold-final.log`。
  新 Executor 用例验证每行不同阈值、毫米级内外分支、NULL/零位置与孔洞、数值未决失败，分析阶段零 Job。
- 此次只增加内部谓词，TrackNodeSupport、NearestExactPlan、Contracts、UI 和旧任务语义不变。
  零距离公共交叉位置、极近阈值/排序产品边界、全域、候选召回及整节点接入仍待完成；
  不以这次回归宣称 ArcGIS 等价或整个路线图完成，没有运行全工程/真实服务/浏览器/容量验收。

### 零阈值的连续边界证据（2026-09-08，协议仍为 4.43）

- Wgs84ArcTopology.probeLocal 用四端点提出并验证共同局部域，只做轻量连续关系判定。
  域不成立或非轴向近共线保留未决，不在探测中逐对启动完整距离搜索；原来源拓扑验证继续保留原有距离分离回退。
- 新增 Wgs84BoundaryIntersection：规范化共同原始端点、保守 ECEF 弧盒与现有 STRtree 候选、连续边对探测。
  输出 INTERSECTING / DISJOINT / UNRESOLVED；没有相交证据不等于已分离，后续相交候选可覆盖此前未决。
  端点/建盒/候选/局部验证共用原预算，不增加生产依赖、日志或稳定配置。
- 小于舍入裕量的阈值使用三态证据：相交判内，零阈值的已分离判外；正阈值不能只靠不相交判外，
  未决继续数值区间搜索。Wgs84GeometryDistance 原有未决区域包含保护不变。
  没有将拓扑事实转换为假的最近位置，Result 的数值距离/位置对保持原搜索语义。
- 基础 33 项通过：`/tmp/datascalpel-geodesic-intersection-baseline.log`；阈值接入后 21 项通过：
  `/tmp/datascalpel-geodesic-topological-zero.log`；7 项新边界关系加入后 28 项通过：
  `/tmp/datascalpel-geodesic-topological-zero-cases.log`。
  既有“局部交叉在零阈值耗尽预算”用例改为验证同一交叉现在有正面拓扑证据，仍保留近邻不吸附与预算保护。
- 新专项覆盖高纬真实弧与经纬弦反例、日期线、极点、有限接触/重叠、非轴向未决、未验证的大域、
  先遇到未决不阻断后续相交、无内含顶点的交叉面、真实数值位置对不变，以及两组各 1024 边的保守剪枝。
  最后一项直接零阈值判分离，避免超过百万个边对的完整距离搜索；不把几何内剪枝当成跨表候选召回完成。
- 最终 255 项通过：边界关系 7、阈值 7、原始拓扑 8、弧关系 6、环检查 8、区域定位 7、区域距离 7、线段 11、
  线性 5、距离 Spark 9、Nearest Geometry 3、TrackArea Spark 22、Polygon 13、Hull 12、Disk 11、Path 7、Runner 28、
  SpatialAnalysis Spark 84。日志 `/tmp/datascalpel-geodesic-topological-zero-final.log`。
  新 Executor 用例包含高纬真交叉/分离、日期线、交叉面、极点、NULL 和多部件，分析阶段零 Job。
- 仍未开放 TrackAreaPlan/NearestExactPlan 的测地面/非点能力，未改变 Contracts、UI、Manifest/Result 或 HTTP。
  公共交叉位置及误差边界、全域、严格排名/候选召回与整节点/血缘仍需继续，不以本地回归宣称 ArcGIS 等价。
  没有运行全工程、真实数据库/ArcGIS、浏览器或容量验收，完整路线图目标保持。

### Track Find Dwell 当前能力收口（2026-09-13，协议仍为 4.76）

- 保留 `LEGACY_ADJACENT` 并完成 `REFERENCE_CENTER` 的四类结果闭环：均值中心、凸包、驻留点和全部点。
  Count、字段 Count、Any、First/Last 与数值统计继续按各自结果粒度执行；点级结果不执行隐藏汇总草稿。
- 平面和受控 WGS84、日期变更线中心/凸包、相邻 gap、固定日历/时长边界、NULL/Empty、退化 Geometry
  及确定次序规则均保留。真实页面已验证策略确认、四输出切换、条件字段、边界 Modal、无效草稿应用和问题详情。
- 四类输出均达到 `FIELD_COMPLETE`，点级原字段为直接血缘，聚合字段均有真实来源；为驻留归属增加的受控
  `mapPartitions` 血缘边界只接受显式透传和派生来源声明，未标记的不透明行变换继续为 `FIELD_PARTIAL`。
- 20,000 点单轨迹样例分析阶段零 Spark Job，点级输出一条观测一行且计划不含 `CollectLimit`/`collect_list`。
  Executor 仍一次缓存一个轨迹片段并可能反复扫描候选，不能据此宣称无限容量或生产规模等价。
- 联合专项共 130 项通过：`DwellRangeAssignmentTest` 6、`TrackFieldStatisticsSparkTest` 5、
  `SpatialAnalysisProcessorSparkTest` 112、`SparkJarLineageRuntimeTest` 7；前端驻留配置与 Inspector 2 文件 6 项通过，
  节点目录 ESLint 通过。未执行完整工程、真实 ArcGIS 服务或生产容量验收。
- 当前收口不宣称 ArcGIS 官方测地中心/凸包公式、字段/统计类型、Enterprise 容差与数值或 Streaming 完全等价。

### Track Detect Incidents 当前能力收口（2026-09-13，协议仍为 4.76）

- 保留 `LEGACY` 并完成 `CONDITION_LIFECYCLE` 的 Started/OnGoing/Ended 状态机、逐观测持续时间、
  结束优先及 `INCIDENTS_ONLY`/`ALL_EVENTS` 两种结果范围闭环。
- 4.46 的原字段窗口、4.63～4.65 的累计距离/逐观测速度/加速度、4.66 的轨迹时间/时长/序号和
  4.67 的相对 Point X/Y 坐标继续作为受控条件绑定；左闭右开范围、固定边界/gap 分段、确定次序、
  NULL/空窗和 WGS84 运动单位规则保持明确，临时绑定不进入结果。
- 两种结果范围均达到 `FIELD_COMPLETE`，原字段为直接血缘，六个生命周期结果字段均有真实输入来源；
  20,000 条单轨迹样例分析阶段零 Spark Job、逐观测一行且计划不含 `CollectLimit`/`collect_list`。
- Engine `IncidentWindowSparkTest` 21 项通过；前端窗口/Inspector 2 文件 21 项通过。
  真实页面已验证默认语义、窗口和标量紧凑表格、无效草稿应用及问题详情，未保存任务定义。
- 本轮不实现任意 Arcade、完整 Geometry/TrackWindow 复合对象、整行对象、未核实的第三种结果范围或 Streaming；
  官方字段别名、Enterprise 服务数值/边界和生产容量不声明完全等价。

### Spatial Bin Aggregate 当前能力收口（2026-09-13，协议仍为 4.76）

- 方格、平面六边形和 H3 的当前实现范围保持不变：六边形显式对边距离、H3 分辨率/近似距离、
  平面原点/范围、字段与数值统计、分组指示器、固定/日历窗口和空格网保护已形成可执行闭环。
- 三种格网及平面补空、显式/来源范围的全输出血缘均为 `FIELD_COMPLETE`。配置常量生成的显式空格网
  不伪造字段来源，其余格网、统计、分组和时间字段均追溯到实际输入，输出无未知来源字段。
- 20,000 点样例分析阶段零 Spark Job，Count 和数值总和正确，执行计划不含 Driver 收集或意外笛卡尔积。
  Engine `BinStatisticsAndWindowsSparkTest` 25 项、前端 4 文件 17 项通过。
- 真实页面已验证延迟编译、三种形状、H3 两种大小方式、范围、统计、分组、时间切片、无效草稿应用
  和问题详情；未保存任务定义。
- H3 球面业务范围、Enterprise 官方逐值/容差/边界编码和生产容量仍不声明完全等价。

### Spatial Point Cluster 当前能力收口（2026-09-13，协议仍为 4.76）

- 旧 Sedona DBSCAN、显式空间 DBSCAN、Linear 时空 DBSCAN 与 HDBSCAN/四诊断继续共存；
  算法切换保留隐藏草稿但必须确认，不把 HDBSCAN 强行套入无效半径或时间邻域。
- DBSCAN/HDBSCAN 均达到 `FIELD_COMPLETE`：原字段保持直接血缘，簇号、噪声和诊断字段只关联真实参与字段，
  HDBSCAN 不把隐藏时间草稿误记成来源。Preview 只构造集合依赖 Schema 计划，不执行聚类或 Checkpoint。
- 20,000 个稀疏平面点完成显式 DBSCAN 执行且全部按预期为噪声，同规模 Preview 分析阶段零 Spark Job。
  HDBSCAN 的 256 点规则格网已验证精确候选缩减；生产实现不向 Driver 收集点、边、簇或成员列表，
  但密集候选、大 k 和深树仍可能产生平方级关系与多轮 Shuffle/Checkpoint。
- Engine 点聚类/HDBSCAN 6 个类 54 项通过；前端 DBSCAN/HDBSCAN 2 文件 15 项通过。
  真实页面已验证延迟编译、空间/Linear 条件、算法切换确认、HDBSCAN 隐藏半径/时间及四诊断弹窗；
  未保存任务定义。
- 当前收口不宣称 ArcGIS 四诊断逐值、Enterprise 簇成员、极端候选或生产容量完全等价；
  `MULTI_SCALE` 继续作为明确不支持的旧草稿值保留。

### Spatial Center Dispersion 当前能力收口（2026-09-13，协议仍为 4.76）

- 4.31 的独立结果、线面质心、原中央要素、中位停止证据与容量保护，以及 4.32 的原字段投影和事件时间
  别名传播保持不变；旧 `LEGACY_WIDE` 仍为显式兼容模式。
- 五种当前结果均达到 `FIELD_COMPLETE`：分组字段、中央要素原字段及原 Geometry 保持直接血缘；
  平均/中位/标准距离/方向椭圆 Geometry 关联实际 Geometry 与权重来源，不产生未知来源字段。
- 20,000 点单组样例分析阶段零 Spark Job，平均中心、中位中心、标准距离和方向椭圆均完成真实执行；
  计划不含 Driver 数据收集，但明确保留 Executor 单组 `collect_list`，受 100,000 要素/100 万顶点保护。
  中央要素继续执行 5,000 要素上限，不以 O(n²) 计算冒充大组支持。
- Engine 中心专项共 122 项通过；前端结果配置 7 项通过。真实页面已验证延迟编译、默认独立结果、
  五种分析、逐项结果配置、σ、排序、删除确认、模式切换、隐藏草稿、无效草稿应用和问题详情；未保存任务定义。
- 当前收口不实现平均/中位时间或椭圆 interval，不宣称 ArcGIS 官方加权椭圆、退化边界、Enterprise 数值
  或生产容量完全等价；标准距离继续作为平台扩展。

### Spatial Density 当前能力收口（2026-09-13，协议仍为 4.76）

- 4.68 的投影 XY Point、Uniform/Kernel、方格/六边形、点数与至多 32 个数量字段密度、格网/半径/
  面积单位和固定/日历时间切片保持不变，继续使用公开且可复现的平台公式。
- Uniform 与 Kernel（含时间切片）的全输出血缘均为 `FIELD_COMPLETE`，无未知来源字段；格网、点数
  密度、窗口和数量密度分别追溯到实际 Geometry、时间与数量字段。失败专用 `raise_error` 分支不再
  把正常数量密度误判为未知来源。
- 20,000 个稀疏投影点的 Preview 分析阶段零 Spark Job；Kernel 六边形完成真实执行，计划不含
  Driver 收集、`collect_list` 或意外笛卡尔积。Engine Density 专项 3 项通过。
- 真实页面已验证延迟编译、Kernel/六边形、时间切片、数量字段、结果字段、无效草稿应用和问题详情；
  未保存任务定义。
- 当前收口不宣称 Enterprise 逐格网数值、边缘/六边形编码、不同投影轴单位、重叠日历窗口或生产容量
  完全等价，半径/格网比例接近上限的 Shuffle 与 Executor 内存仍需生产容量验收。

### Spatial Multi-Variable Grid 当前能力收口（2026-09-13，协议仍为 4.76）

- 4.70 的多来源共同范围、方格/六边形、逐变量筛选/搜索半径、最近距离、最近属性和关联汇总保持不变；
  `COUNT/SUM/MEAN/MIN/MAX/RANGE/STDDEV/VARIANCE/ANY` 九种统计继续使用已声明的确定性语义。
- 三类变量、九种统计及两个来源表的统一格网全部达到 `FIELD_COMPLETE`，无未知来源字段。`COUNT`
  追溯实际来源 Geometry，最近属性追溯 Geometry 与属性字段，数值统计追溯 Geometry 与统计字段。
- 20,000 个规则稀疏投影点的 Preview 分析阶段零 Spark Job，真实执行输出 20,000 个格网；计划不含
  Driver 收集、`CollectLimit`、`collect_list`、Cartesian Product 或 Broadcast Nested Loop Join。
  Engine `SpatialMultiVariableGridNodeOperatorSparkTest` 3 项通过。
- 真实页面已验证新增节点延迟编译、方格/六边形入口、三类变量、逐变量筛选与条件树、结果字段、
  无效草稿应用、Canvas 摘要和紧凑问题详情；未保存任务定义。
- Enterprise 格网边缘、最近并列、统计数值/NULL 细节、不同 Geometry/投影轴单位及接近配置上限的
  生产容量仍开放，不声明 Esri 数值完全等价。

### Spatial Enrich From Grid 当前能力收口（2026-09-13，协议仍为 4.76）

- 4.71 的有界 XY Point、同 CRS Polygon/MultiPolygon 格网、显式字段投影、未匹配保留和边界单格稳定
  选择继续保持现有协议与确定性语义。
- Point 原字段/Geometry 和全部丰富字段均追溯真实来源，结果达到 `FIELD_COMPLETE`，无未知来源字段；
  内部 Point 行身份标记为技术列，不进入业务结果或血缘来源。
- 保留未命中 Point 的计划改为索引化空间 INNER JOIN 找命中、窗口排序取一格、等值 LEFT JOIN 回接。
  20,000 个 Point 与 20,000 个格网的 Preview 分析阶段零 Spark Job，真实执行输出 20,000 行且丰富字段
  汇总值正确；计划不含 Driver 收集、`CollectLimit`、`collect_list`、Cartesian Product 或
  Broadcast Nested Loop Join。Engine `SpatialEnrichFromGridNodeOperatorSparkTest` 3 项通过。
- 真实页面已验证新增节点延迟编译、Point/格网来源、格网 ID 帮助、丰富字段紧凑编辑、无效草稿应用、
  问题入口/详情和 Canvas 安全摘要；未保存任务定义。
- Enterprise 共享边界/异常重叠选择、字段类型细节、服务输出和生产容量仍开放，不声明 Esri 完全等价。

## 12 个节点

下列完整单位缺口的距离/面积部分已由 4.36 补充；时间/日历、速度别名和官方服务对照仍按节点继续验收。

### 旧小版本升级入口收口（2026-09-13，当前协议 4.77）

- Business 的保存、试运行草稿和持久化定义读取会先调用 `CanvasDefinitionUpgrader`，再校验规范化后的当前版本。
  因此只在 Validator/Compiler 检查增量字段会丢失原始小版本，旧 JSON 可能夹带新语义后被升级为当前版本。
- 升级入口现已在改写版本号前覆盖 12 个核心节点的增量门槛：固定轨迹边界、驻留/运动语义、Within
  统计与关联结果、Overlay 家族输出、轨迹重建路径/面/缓冲窗口/测地边界、一元 Geometry 策略、Nearest
  真实匹配、Center 独立结果与原字段投影、H3/格网字段统计、公共单位/固定周、平面格网/日历窗口、
  DBSCAN/HDBSCAN，以及 Incidents 的窗口、运动来源和标量。缺失或 `null` 仍保留旧语义；显式新字段使用
  既有稳定错误在升级前拒绝。
- 该修正不改变当前 4.77 定义，不迁移旧任务算法，也不增加节点能力；Business 主代码编译通过。

- [x] Geometry Derive：4.29 函数支持矩阵、实际 Z/M、Empty/退化/集合、紧凑规则、真实页面与扩展回归已完成；属于自有基础算子，不声明 GA 同名工具。
- [x] Geometry Simplify：4.29 两算法维度、无效结果边界、共边反例、容差/单位草稿、实际批流计划、单几何规模与真实页面已验证；不承诺覆盖层共边或测地简化。
- [x] Nearest：4.30/4.48 的真实平面与受控 WGS84 点线面距离、稳定同距排序、来源身份、显式连接线、
  日期线 Polygon、连续近等距安全失败、256×1024 索引样例和真实页面已验证。单个跨域面、跨域接触/包含、
  GeometryCollection、路网及无法证明的数值边界为明确拒绝范围；不声明 Enterprise 作业或容量完全等价。
- [x] Summarize Within：4.24～4.41 的分摊/加权统计、主表/关联组表、日历窗口和规则格网，以及 4.76 收口的 EPSG:4326 XY、空区域索引计划、完整血缘、共边/重叠平台语义、256×1024 样例和真实页面已验证。Enterprise 公式矛盾、真实服务及生产容量不声明完全等价。
- [x] Overlay：4.26 五模式、完整家族矩阵、显式二维 Multi、低维过滤和缺失侧字段，以及 4.76 收口的
  重叠遮罩单次 Difference、pairwise 独立来源、索引计划、完整字段血缘、256×1024 样例和真实页面已验证；
  Esri tolerance/snap、全局无重叠平面分区、几何编码及 Enterprise 容量不声明完全等价。
- [x] Reconstruct：4.27～4.44 的固定边界/次序/拆分/段归属、测地路径、字段统计、平面面轨迹、缓冲窗口及
  局部测地面链路，以及 4.76 收口的完整输出血缘、20,000 观测单轨迹样例和真实页面已验证。大域、Arcade、
  ArcGIS 官方公式/容差/字段与生产容量不声明完全等价。
- [x] Motion：4.23 的观测历史窗口、八组 31 项、Idle 双阈值、垂直单位、平面/WGS84 距离与旧语义兼容，
  以及 4.76 收口的完整输出血缘、20,000 观测逐行样例和真实页面已验证。Enterprise 官方字段别名、
  固定月年、服务端容差/数值与生产容量不声明完全等价。
- [x] Dwell：4.22 参考点/固定均值中心扩展与四输出、4.35 字段 Count/Any（含数值）和实际统计类型，
  以及 4.76 收口的平面/受控 WGS84、固定边界、完整输出血缘、20,000 点单轨迹样例和真实页面已验证。
  官方测地中心/凸包公式、字段/统计类型、Enterprise 容差与数值、生产容量和 Streaming 不声明完全等价。
- [x] Incidents：4.21 生命周期与确定次序、4.46 左闭右开的原字段窗口、4.63～4.65 的累计距离/逐观测
  速度和加速度、4.66 的轨迹时间/时长/序号、4.67 的 Point 相对观测 X/Y 标量，以及 4.76 收口的
  两种结果范围完整血缘、20,000 条单轨迹样例和真实页面已验证。完整 Geometry/TrackWindow 复合对象、
  任意 Arcade、未核实的第三种结果范围、官方字段/数值/边界、生产容量和 Streaming 不声明完全等价。
- [x] Bins：4.21 对边距离、4.33 H3、4.34 字段统计/固定切片、4.38 显式平面原点/范围、4.39 日历窗口，
  以及 4.76 收口的三形状完整字段血缘、20,000 点惰性分布式聚合和真实页面已验证。H3 球面业务范围、
  Enterprise 官方逐值/容差/边界编码及生产容量不声明完全等价。
- [x] Cluster：4.40 显式空间/Linear DBSCAN，4.45 HDBSCAN/四诊断、原行回接、集合血缘和 Inspector，
  以及 4.76 收口的完整输出血缘、20,000 点显式 DBSCAN、HDBSCAN 分布式状态/Checkpoint 与真实页面已验证。
  密集候选、大 k、深树、官方诊断数值、Enterprise 簇成员和生产容量不声明完全等价；MULTI_SCALE 非 GA 算法。
- [x] Center：4.31 线面质心/原中央要素、独立结果、确定身份排序、中位停止证据及容量保护，4.32 原字段投影，
  以及 4.76 收口的五结果完整字段血缘、20,000 点单组统计和真实页面已验证。平均/中位时间、椭圆 interval、
  官方加权椭圆公式/退化边界、Enterprise 数值和生产容量不声明完全等价；标准距离为平台扩展。

## 路线图其余范围

- [ ] 公共单位/日历边界、Geometry/NULL/退化策略、多结果 Schema/血缘和安全摘要。
- [ ] 现有 Clip、Buffer、Measure、Join、Dissolve、Merge、Summarize Attributes、Calculate Field 能力复核/补齐；Clip 4.51 已补来源家族二维输出，4.77 已补逐来源融合相交 Mask 与旧 Pairwise 兼容；Buffer 4.49/4.52 已补显式单位及字段/表达式距离，Measure 4.50 已补逐项输出单位，Spatial Aggregate 4.53 已补 Create Buffers 的 Dissolve All/List、统计和部件方式，4.61 已补 Dissolve Boundaries 无字段连通组；Spatial Join 4.54～4.60 已补字段投影、属性/时间关系、LEFT、一对多及一对一统计/保留、空间 Near/Near Geodesic 和一对多距离输出；UNION 4.62 已补 Merge Layers 字段 Match/Rename/Remove、缺失补 NULL 和 Geometry/时间约束；各项官方容差/数值/容量能力仍待完成。
- [x] Calculate Density：4.68 的矢量方格/六边形、Uniform/Kernel、数量字段、半径/单位/时间切片，
  以及 4.76 收口的完整字段血缘、20,000 点惰性分布式聚合和真实页面已验证。Enterprise 数值/边缘、
  不同投影轴单位、重叠日历窗口与生产容量不声明完全等价。
- [x] Hot Spots：4.69 的投影 Point、完整方格、点数/字段和、固定距离 Gi*、双侧 p-value、FDR-BH、
  `-3..3` 分级和时间切片，以及 4.76 收口的完整字段血缘、20,000 点惰性分布式执行和真实页面已验证。
  Enterprise 边缘/FDR/数值、不同投影轴单位和生产容量不声明完全等价。
- [x] Multi-Variable Grid：4.70 的多输入统一格网、逐变量筛选/半径、最近距离/属性和九种关联汇总，
  以及 4.76 收口的三类变量/九种统计完整血缘、两来源统一格网、20,000 点惰性分布式执行和真实页面已验证。
  Enterprise 边缘/并列/统计、不同 Geometry/投影轴单位及生产容量不声明完全等价。
- [x] Enrich From Multi-Variable Grid：4.71 已接入 Point 与已有变量格网相交、显式字段投影、未匹配保留
  及边界单格选择；4.76 已完成完整字段血缘、20,000 Point/格网惰性索引化执行和真实页面验收。
  Enterprise 边界选择、字段类型、服务输出与生产容量不声明完全等价。
- [x] Group By Proximity：4.72 已接入四种空间关系、可选时间/受控属性关系与传递连通组；
  4.76 已完成集合字段完整血缘、20,000 孤立 Point 惰性分布式执行和真实页面验收。任意属性表达式、
  Enterprise 容差/月年边界、高偏斜图和生产容量不声明完全等价。
- [x] Trace Proximity Events：4.73 已接入有界 XY Point、显式 ID/起始表、时空和同值属性约束、
  最大深度、首次事件及可选后续轨迹；4.76 已完成两结果完整血缘、20,000 观测/
  10,000 事件惰性分布式执行和真实页面验收。Enterprise 搜索/episode/月年边界、极区/日期线、官方字段、
  高偏斜图和生产容量不声明完全等价。
- [x] Snap Tracks：4.74 已接入轨迹/时间次序、显式线网拓扑、方向、Planar/Geodesic 候选和
  Viterbi 联合匹配；4.76 已完成全输出字段血缘、20,000 观测/1,000 轨迹规模样例和真实页面验收。
  多条中间道路搜索、Enterprise 结果/容差、高密度候选、超长单轨迹和生产容量不声明完全等价。
- [x] Similar Locations：4.75 已接入 GeoAnalytics 核心 Attribute Values / Attribute Profiles、
  共同总体标准化、多参考平均和三种返回范围；4.76 已完成全输出字段血缘、20,000 候选/
  10,000 结果规模样例和真实页面验收。Pro Ranked/Scale/Collapse、Enterprise 数值、带真实上游的
  完整筛选交互和生产容量不声明完全等价。
- [ ] Forest/GLR/GWR 专题设计与实现。
- [x] Describe Dataset：4.76 已接入字段统计、描述 JSON、可选样本与可选 XY Envelope 范围，
  并完成四结果完整血缘、20,000 行规模样例和真实页面验收。Enterprise 字段细节、采样边界、
  带真实上游的字段选择和生产容量不声明完全等价。

路线图明确独立设计/不在本轮的路网服务、地理编码、Cube、自定义脚本不伪称已实现。
P2 中尚缺详细参数/算法决策的能力保留为待设计，不用宽泛节点名占位充数。
