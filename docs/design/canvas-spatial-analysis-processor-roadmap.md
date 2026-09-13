# Canvas 空间分析 Processor 路线图与 ArcGIS 对齐审计

## 1. 状态与产品基线

- 审计日期：2026-09-07。审计基线为 Canvas 4.20，后续已进入开发；实现进度见[开发清单](canvas-spatial-development-progress.md)。
- 当前写出版本为 4.77：`SPATIAL_CLIP` 新增逐来源多 Mask 组合，默认先融合每条来源要素命中的
  所有 Mask 再裁剪一次，避免重叠 Mask 重复覆盖；缺失/null 保持旧 Pairwise 行为。4.76 新增批处理 `SPATIAL_DESCRIBE_DATASET`，保留来源表并输出逐字段统计、
  数据集描述、可选样本和可选 XY Envelope 范围。Geometry 可选，范围输出时必须显式选择；
  4.76 已完成四结果完整血缘、20,000 行规模样例和真实页面验收；真实 Enterprise 字段细节、
  带真实上游的字段选择和生产容量仍开放。4.75 新增批处理 `SPATIAL_SIMILAR_LOCATIONS`，以参考位置和候选位置共同总体
  标准化 1～32 个同名数值字段，支持属性值平方差、属性轮廓余弦差异及最相似/最不相似/两端返回。
  4.76 已完成全输出血缘、20,000 候选/10,000 结果规模样例和真实页面验收；当前不包含 Pro 的
  Ranked、Scale 或 Collapse 扩展，真实 Enterprise 数值和生产容量仍开放。
  4.74 新增批处理 `SNAP_TRACKS`，将有界 XY Point 轨迹按时间、
  搜索距离、直接相邻路网拓扑和可选通行方向执行 Viterbi 联合匹配，输出吸附点、
  匹配线 ID、状态与可选道路属性。首版不搜索无观测的多条中间道路；真实 Enterprise
  网络路径、评分、容差和服务结果仍待完成。4.76 已完成全输出血缘、20,000 观测/1,000 轨迹
  规模样例和真实页面验收；高密度候选、超长单轨迹和生产容量仍开放，不声明 Esri 等价。
  4.73 的 `TRACE_PROXIMITY_EVENTS` 从显式实体 ID 或另一张上游表出发，
  按 Point 空间距离、时间距离和可选同值属性向下游逐层传播，输出每个下游实体的首次接触事件及可选后续轨迹。
  当前强制最大深度 1～32、PLANAR 投影 CRS 和 GEODESIC EPSG:4326 XY。4.76 已补齐两结果完整血缘、
  20,000 观测/10,000 事件惰性分布式执行证据及真实页面验收；真实 Enterprise 持续事件、月年边界、
  极区/日期线、官方字段和高偏斜生产容量对照仍开放。
  4.72 的 `SPATIAL_GROUP_BY_PROXIMITY` 按 Intersects、Touches、Near Planar
  或 Near Geodesic 及可选时间/受控属性关系建立无向邻接图并求传递连通组。每个来源要素保留一行并追加
  非空组 ID；孤立要素也形成单独组。4.76 已补齐集合字段完整血缘、20,000 孤立 Point 惰性/分布式执行证据及真实页面验收；
  真实 Enterprise 容差、月年时间、任意属性表达式和高偏斜生产容量对照仍开放。
  4.71 的 `SPATIAL_ENRICH_FROM_GRID` 把已有多变量 Polygon 格网的
  显式属性按相交关系回填到有界 XY Point，未匹配 Point 保留；边界多格命中使用格网 ID 稳定选择。
  4.76 已补完整字段血缘、20,000 Point/格网惰性预检与索引化执行证据及真实页面验收；真实 Enterprise
  边界、字段类型与生产容量对照仍开放。4.70 新增批处理 `SPATIAL_MULTI_VARIABLE_GRID`，以多张投影 XY Point/Line/Polygon
  来源的共同外包范围生成统一方格或六边形，支持逐变量筛选、最近距离、最近属性和关联要素
  COUNT/数值/字符串汇总。4.76 已补齐三类变量、九种统计与两来源统一格网的完整字段血缘、20,000 点
  惰性预检与分布式执行证据及真实页面验收。真实 Enterprise 的格网边缘、最近并列、统计数值、不同
  Geometry/投影轴单位和生产容量对照仍开放，不宣称 Esri 数值等价。4.69 新增批处理 `SPATIAL_HOT_SPOTS`，以投影 XY Point、完整方格和固定距离
  二元邻域计算 Getis-Ord Gi*，支持点数、单个数值字段求和、原始双侧 p-value、显式 FDR-BH、
  `-3..3` 置信分级和可选时间切片。4.76 已补齐点数/字段总量、FDR/无校正及时间切片的完整字段血缘、
  20,000 点惰性预检与分布式执行证据及真实页面验收。真实 Enterprise 的逐格网数值、边缘、
  多重检验、不同投影轴单位和生产容量对照仍待完成，不宣称 Esri 数值等价。
  4.68 的 `SPATIAL_DENSITY` 以投影 XY Point 生成方格或六边形
  Polygon 格网，支持 Uniform/Kernel、点数及至多 32 个数量字段密度、独立格网/半径单位、
  面积单位和可选时间切片。当前使用公开公式定义的平台确定性实现；4.76 已补齐 Uniform/Kernel
  含时间切片的完整字段血缘、20,000 点惰性预检与分布式执行证据及真实页面验收。真实 Enterprise
  的逐格网数值、边缘、不同投影轴单位和生产容量对照仍待完成，不宣称 Esri 数值等价。
  `TRACK_DETECT_INCIDENTS` 在 4.66 轨迹标量上增加
  `TRACK_POINT_X_AT/TRACK_POINT_Y_AT`，以有符号观测偏移读取当前片段内 Point 坐标，越界或
  Geometry 为 NULL 时返回 NULL，DOUBLE 结果单位跟随来源 CRS。4.66 已接入 TrackStartTime、
  TrackDuration、TrackCurrentTime 与 TrackIndex，统一以 LONG 暴露 Epoch 毫秒、
  持续毫秒或零基观测序号，并随 DataScalpel 轨迹片段重置。既有受控条件窗口可显式选择
  `source=TRACK_ACCELERATION`，按官方左闭右开观测范围聚合逐观测加速度；片段首观测为 0，后续值为
  当前与前一观测速度之差除以时长，单位固定为米/秒²。4.64 的 `source=TRACK_SPEED` 按同一范围聚合
  逐观测速度；片段首观测速度为 0，后续值为前一
  观测到当前观测的 WGS84 测地距离除以时长，单位固定为米/秒。同时间、缺失 Point 或无法形成有效段时
  返回 NULL。4.63 的 `source=TRACK_DISTANCE` 按同一窗口边界聚合各 Point 自当前片段首观测起的累计
  测地距离，单位固定为米；
  缺失/null/FIELD 继续保持 4.46 原始字段窗口语义。当前只接入 Distance/Speed/Acceleration 三种
  Window 的受控标量聚合，不把它扩展解释成完整 Arcade 轨迹窗口能力。4.62 复用 `UNION` 接入
  Merge Layers。第一张输入作为基准层，后续层默认同名
  Match、非同名字段追加、缺失字段补 NULL，并可逐字段配置 Match/Rename/Remove；数值 Match 显式
  Cast，Geometry 必须 Match 到类型、CRS、维度一致的基准字段，无界事件时间必须 Match 到基准事件
  时间。缺失/null `mergingTables` 保持旧版严格同 Schema Union，不改变旧任务结果。4.61 的
  `SPATIAL_AGGREGATE` 单 UNION Dissolve 可显式选择
  `CONNECTED_COMPONENTS`，在不配置分组字段时按 Polygon/MultiPolygon 的二维相交、重叠或接触关系
  建立分布式连通分量，再按传递闭包分别 UNION、计数和统计。NULL/Empty Geometry 不进入连通图；
  Multipart/Singlepart 继续沿用 4.53 语义。缺失/null 分组方式继续保持 4.53 的空分组全局 All，
  不改变旧任务结果。4.60 的 `SPATIAL_JOIN` 独立空间 `Near/Near Geodesic` 继续有效。PLANAR 使用来源 CRS；
  GEODESIC 仅支持 EPSG:4326 XY，并以 ECEF 三轴保守召回候选后使用 Geometry 真实最近位置判断，
  不使用质心。空间 Near 可单独使用，也可与拓扑、属性和时间条件按 AND 组合；一对多可分别输出空间距离
  和时间 Near 间隔，两种 Near 同时存在时输出两个字段，一对一仅允许过滤。4.59 的 15 种可选有方向时间
  关系继续有效；每侧可使用一个时间字段表达瞬时，或使用开始/结束字段表达闭区间，
  `NEAR/NEAR_BEFORE/NEAR_AFTER` 支持受控固定时长。4.58 在 4.57 显式一对多基础上增加
  `JOIN_ONE_TO_ONE`；一对一可汇总
  全部匹配记录，或按 FIRST、数值最大/最小、日期最新/最旧及显式稳定顺序保留一条；
  缺失/null `joinOperation` 仍保持既有一对多结果；
  4.56 可通过 LEFT 保留全部左侧目标要素，未匹配时右侧投影字段为 NULL；
  4.55 的至多八组属性等值条件与空间谓词 AND 组合继续有效；
  4.54 的显式投影、排除、改名和排序左右字段继续解决普通同名字段；
  旧缺失/null 投影继续保持全字段和同名拒绝语义。4.53 的 `SPATIAL_AGGREGATE` 可为单 UNION 启用 Create Buffers 风格的
  Dissolve All/List、来源要素计数、九种标量统计及 Multipart/Singlepart 输出；
  4.52 的 Geometry Buffer 固定值、数值字段和受控表达式三种逐行距离来源继续有效；
  缺失/null 来源保持旧固定距离语义，动态 NULL 输出 NULL，非正或非有限实际值稳定失败。
  4.51 的 Spatial Clip 来源家族二维输出继续有效；
  4.50 的 Spatial Measure 逐项输出单位继续有效，投影 PLANAR 与 WGS84 SPHEROID 可靠换算；
  缺失单位保持旧结果。4.49 的 Geometry Buffer 显式距离单位、4.48 的 Nearest 非点 WGS84 真实最近位置模式及 4.47 的
  [周（固定 7 天）](canvas-spatial-units.md#447-固定时长周与日历周)继续贯通时长阈值与输出单位。
  已有日历周不改变；固定月年、完整官方单位/数值对照仍待完成。
- 4.46 事件检测增加[受控字段窗口条件](canvas-spatial-next-processors/track-detect-incidents.md#8-446-受控字段窗口条件)，
  按官方左闭右开偏移、原始字段、轨迹片段和确定次序计算。九种字段窗口聚合可供开始/结束条件引用；
  4.63～4.65 已接入距离/速度/加速度窗口，4.66 已接入四种轨迹时间/序号标量，
  4.67 已接入 Point 相对观测 X/Y 标量。返回复合对象的 Geometry/TrackWindow、整行对象和
  更丰富受控表达式仍未完成，不将这些增量当成整个事件节点验收。
- 4.45 的 HDBSCAN 已通过显式四诊断配置接入节点、集合血缘和 Inspector；保留 DBSCAN 旧语义。
  [聚类第 9 节](canvas-spatial-next-processors/spatial-point-cluster.md#9-canvas-445-hdbscan-节点接入)列明配置、参与规则与证据，规模/官方数值对照仍未完成。
- HDBSCAN 新增[中间文件归属与清理](canvas-spatial-next-processors/spatial-point-cluster.md#15-hdbscan-中间-checkpoint-的显式归属与清理)：
  保留最终可靠结果，清理自有快照及成功图阶段可确认归属的目录，不扫描共享目录。该增量未单独升级协议；
  图内部失败/强杀的未知残留、运行磁盘峰值和应用级最终结果回收仍需完善。
- HDBSCAN 后续 MST 迭代增加[精确商图压缩](canvas-spatial-next-processors/spatial-point-cluster.md#10-精确商图压缩减少生成树后续迭代的边数)，
  保持最小候选及原始端点；不以截断 KNN 代替 MST。后续割恢复已替代初始稠密图，整体容量仍待验收，版本不变。
- 核心距离增加[近邻上界与完整半径恢复](canvas-spatial-next-processors/spatial-point-cluster.md#11-精确核心距离的近邻上界与半径恢复)，
  不足种子的来源使用完整候选，重复身份与同距边界不丢失；核心表单独固定供两端共用。
  此阶段只优化核心距离；后续[逐轮精确割候选](canvas-spatial-next-processors/spatial-point-cluster.md#12-逐轮精确割候选移除运行时初始完整图)
  已移除运行时初始完整图，通过可行上界恢复各割全部最小候选。候选过密仍可能 O(n²)，不构成全链路容量或官方诊断数值对照通过。
- 层次输出与祖先归属增加[分批 Checkpoint 历史](canvas-spatial-next-processors/spatial-point-cluster.md#13-层次历史分批保存避免逐轮重写全部结果)，
  不再每轮重写全部历史；每批只作对数次层级合并。
- EOM 增加[完成历史与待决子簇分离](canvas-spatial-next-processors/spatial-point-cluster.md#14-eom-完成历史与待决子簇分离)，
  父簇只汇总一次原始直接子簇贡献，完成后消费子簇工作记录；不再逐轮聚合全部完成历史。
  深树迭代及不平衡旁支等待仍可能产生平方级开销，文件清理、真实规模和官方数值对照仍待完成。
- 前一增量：4.44 已接入 Reconstruct 测地面节点链路与独立面边界采样配置，原始区域校验/距离 gap 不再使用平面拓扑或质心。
  三端门槛、草稿和安全摘要同步；全球域、官方服务与规模验收仍未完成。以下分版本条目保留历史进度。
- 2026-09-08 对齐状态修订及后续开发：HDBSCAN 从内部管道推进到 4.45 节点接入。
  [聚类第 8 节](canvas-spatial-next-processors/spatial-point-cluster.md#8-hdbscan-参数诊断与开放条件修订)记录原型阶段的算法/诊断差异，不将第 9 节实现或本地测试当成 Esri 数值等价。
- 可选事件生命周期、轨迹固定时间边界和六边形尺寸语义已进入 Canvas 4.21；4.22 已接入驻留参考中心策略与四输出，4.23 已接入运动观测窗口、八组指标和 Idle 双阈值。这些能力通过本地专项，仍非完整 ArcGIS 对齐。当前协议以 Contracts 常量为准。
- 4.24 区域汇总新增逐项总量分摊、原值交叠比例加权均值、字段非空 Count 与字符串 Any；后续关联分组见 4.25 进度，完整官方统计公式仍未完成。
- 4.25 区域汇总新增显式区域键、主表/关联组表和形状组比例，旧扁平模式及非活动设置保留；单位、加权方差/标准差、格网区域后续分别见 4.36、4.37、4.41；官方边界对照仍未完成。
- 4.26 Overlay 新增 Identity/对称差与显式 FAMILY_2D：五模式组合校验、Multi/XY 输出、低维接触过滤、缺失侧可空及全遮罩差集。旧三模式保留；官方精度/边界与全局分区对照仍未完成。
- 4.27 Reconstruct 新增有序片段、前后观测绑定及布尔表达式、Gap/FinishLast/StartNext 和单点跳过；固定周期始终 Gap。
- 4.28 Reconstruct 新增显式 METHOD_PATH：WGS84 测地加密及日期线切分，平面路径保持原顶点，统一 MultiLineString；旧顶点线保留。字段统计、平面面轨迹、缓冲窗口和局部测地面后续见 4.35、4.42、4.43、4.44；全球域、完整时间单位与官方精度/容量对照仍未完成。
- 4.29 Geometry Derive/Simplify 新增显式 PRESERVE_DIMENSION/OUTPUT_XY：函数维度矩阵、真实 Z/M、无效输入与 Empty、DP 无隐式修复及共边反例；旧策略保留。它们是自有基础能力，不包装成 GA 同名工具。
- 4.30 Nearest 新增显式真实距离策略、来源唯一身份、全同距候选恢复及可选独立连接线表；旧 KNN/非点质心路径保留。
  该版本的真实测地仅支持 Point；4.48 后续显式开放受控非点，官方对照及大规模验收仍未完成。
- 4.31 Center 新增显式独立分析结果、线面质心/原中央要素、带停止证据的中位中心与分组要素/顶点保护；旧宽表算法保留。4.32 后续补原字段投影；4.76 完成五结果完整字段血缘、20,000 点单组统计及真实页面收口。类型时间结果和官方椭圆加权公式仍未完成。
- 4.32 Center 新增显式原字段投影：同一原记录属性的保留/排除/改名/排序及来源事件时间字段别名传播；旧配置保持不变。
  这不补齐平均/中位/椭圆时间和 interval 元数据，也不代表完整官方对照。
- 12 个节点已有实现入口，引入版本为 4.9～4.20；**有实现不等于参数、算法、输出和边界行为已对齐**。
- 4.33 Bins 新增 H3 显式分辨率/近似距离、WGS84 原生点归属和日期线/极区 MultiPolygon 边界；
  Inspector 保留非活动参数。近似选级别公式是平台约定，非官方已验证公式；原点/范围与完整统计仍待完成。
- 4.34 Bins 新增字段 Count/Any，统计输出使用 Spark 实际提升后的类型；Bins/Within 共享固定时长切片
  支持留空并修复重叠窗口起止重复展开。原点/范围与日历后续见 4.38/4.39；官方统计公式对照仍待完成。
- 4.35 Reconstruct/Dwell 增加字段 Count/Any 与实际统计类型回填，统计弹窗支持独立草稿与取消；
  重建 Any 限字符串，驻留额外支持数值。保留旧计数/首末语义及点级输出不执行隐藏汇总；官方类型/分母对照仍待完成。
- 4.37 Within 增加原值交叠比例加权方差/标准差，采用 11.3 公式图的有效样本数修正与可合并中心矩；
  官方线要素文字算例与公式矛盾，明确跟随公式而非矛盾数值。NULL/退化为平台显式规则，尚未完成真实服务对照。
  统计弹窗改为独立草稿；详见[区域汇总第 9 节](canvas-spatial-next-processors/spatial-summarize-within.md#9-437-原值交叠比例加权方差--标准差)。
- 4.38 Bins 增加显式投影原点/业务范围：固定方向、跨任务身份、范围筛点与完整单元、空输入补空间格网、
  边界成员保留和 100 万候选格网保护；H3 保留但不执行平面设置。此为平台适配，不宣称 GA 同名参数。
- 分布式矢量/时空分析以 **ArcGIS Enterprise 11.3 GeoAnalytics Server** 为冻结参照。
  GeoAnalytics Server 随 Enterprise 11.4 弃用，最后支持版本为 11.3。
- 4.39 Bins/Within 增加显式日历切片：月末/闰年原始锚点、DST、混合单位、重叠/留空及安全候选展开；
  旧固定窗口保留，切换确认与取消隔离贯通。详见[共享日历设计与面板](canvas-spatial-calendar-windows.md)。
  这是参数语义的可执行补齐，尚不声明与真实 ArcGIS 服务的边界行为完全一致。
- 4.40 Cluster 增加显式空间/Linear 密度连通：核心图与边界归属分开，空间与时间阈值同时满足、含自身，
  参与点 ID/几何运行校验、内部字段隔离；编译改为零行 Schema/集合血缘，不再直接启动 Sedona Checkpoint 作业。
  沿用现有 GraphFrames 与共享 Checkpoint，无新依赖；旧空间策略保留。HDBSCAN、完整单位、官方服务/规模对照仍待完成。
- 4.41 Within 增加显式区域表/规则格网选择，支持方格/六边形内点线面真实汇总，复用既有统计、分组与时间逻辑。
  原区域配置保留，独立设置/取消及确认式切换；稳定格网身份与惰性范围展开，不向 Driver 收集或生成质心近似。
  详见[规则格网区域](canvas-spatial-next-processors/spatial-summarize-within.md#11-441-规则格网汇总区域)。官方边界/数值、浏览器及规模验收仍开放。
- 4.42 Reconstruct 增加显式平面 XY 面轨迹：Point 字段/逐行表达式缓冲、Polygon/MultiPolygon 观测、相邻凸包连接及单观测保留。
  原几何决定拆分，双分支草稿保留、独立弹窗取消、零 Action 预检和安全错误贯通；窗口缓冲表达式后续见 4.43，测地面与官方精度仍待完成。
  详见[平面面轨迹](canvas-spatial-next-processors/track-reconstruct.md#10-canvas-442-显式平面面轨迹)，不以本阶段声明完整 GA 支持。
- 4.43 Reconstruct 增加缓冲表达式的数值观测窗口：显式闭区间偏移、十种统计、固定周期隔离及拆分前计算。
  隐藏分支保留、独立设置草稿、绑定排序/删除确认、精确校验与三端版本门槛贯通；不承诺 Arcade 脚本兼容。
  详见[缓冲观测窗口](canvas-spatial-next-processors/track-reconstruct.md#11-canvas-443-缓冲观测窗口绑定)；局部测地面后续于 4.44 接入，官方精度/规模仍待完成。

### 4.43 阶段的内部测地开发记录（历史）

下列“尚未接入”“未放开”和“协议仍为 4.43”均描述当时状态，不是当前能力结论。
Reconstruct 的局部测地面节点链路已在 4.44 接入，现行范围见节点第 17 节；Nearest 非点测地尚未接入。
保留这些记录用于解释实现选择，不以历史待办覆盖后续成果，也不据此承诺全球域已支持。

- 测地面开发已增加 WGS84 点缓冲边界内部原语，日期线/极点渲染与真实采样隔离，尚未接入 Operator。
  [内部实现及剩余连接工作](canvas-spatial-next-processors/track-reconstruct.md#12-测地面轨迹开发中wgs84-点缓冲边界)不构成新可用节点能力或协议升级。
- 后续内部连接核心采用 WGS84 方位与逐边支持验证，已补极点入射/出射经线及边中途过极点的表示修正。
  [连接范围与剩余接入](canvas-spatial-next-processors/track-reconstruct.md#13-测地面连接核心与极点经线修正内部开发)中的局部 Polygon 缓冲后续见第 14 节。
- 内部 Polygon/MultiPolygon 足迹已增加原测地面、法向偏移带、孔洞收缩与圆形连接，有序装配仅连接相邻观测。
  日期线/极点拓扑及 Spark 原语测试已通过；[当前范围与证据](canvas-spatial-next-processors/track-reconstruct.md#14-polygonmultipolygon-测地足迹与有序连接内部开发)
  不代表面节点已开放。真实顶点的 Spark Struct 已通过 Shuffle/有序聚合与极点辅助边隔离测试；
  非点 gap 距离、大域、完整 Operator/Inspector 和官方验收仍待完成。
- 非点距离内部基础新增 WGS84 线段完整参数域搜索及点/多点/折线/多部件线入口，返回实际位置对与数值上下界。
  不用质心、不补虚拟连接，共享预算超限拒绝未经验证的结果；[实现与接入边界](canvas-wgs84-geometry-distance.md)
  仍包括 Polygon 区域、非点候选召回、精度决策与完整节点。未放开 Reconstruct/Nearest 的现有配置限制。
- 后续内部区域入口增加原始测地环定位、内含零距离、孔洞正距离、多部件及全局边界搜索；
  全局队列和带曲率裕量的 ECEF 投影分离下界改善近乎平行边预算，最终距离仍为 WGS84。
  212 项专项通过；连续近接触拓扑、局部域外区域、严格阈值、非点候选召回与完整节点仍需继续，协议仍为 4.43。
- 连续原始弧检查后续增加交叉/重叠拒绝、保守 ECEF 候选剪枝与未决安全错误；合法环间 T 接触
  先插入真实公共顶点再采样，修复孔洞单点接触被离散弦误判的问题。完整连续区域拓扑仍待补齐，
  该内部进展不开放 Operator/UI 或改变协议，验证与剩余范围见[开发清单](canvas-spatial-development-progress.md)。
- 后续 `Wgs84PolygonTopology` 补充原始孔洞/多部件关系、公共顶点穿越与接触二部图连通性，
  来源有效性已不依赖采样等距方位图，测距准备与 Footprint 渲染完全分开。238 项专项通过，
  局部域外、近接触数值边界、公共交叉位置、严格阈值/候选召回及完整节点接入仍待完成。
- 内部距离阈值查询已复用同一全局边对队列，只有区间可证明时才返回范围内/外；跨阈值继续求精，
  未决包含不当成分离，舍入边界安全失败。247 项专项通过；未改现有点距离过滤，零距离交叉、
  区间排名、非点候选召回与实际 Operator/Inspector 接入仍需继续，完整节点范围不缩减。
- 零阈值内部查询后续增加连续边界三态证据与保守 ECEF 候选索引，可直接确认局部域内真实交叉，
  或证明边界分离；未决不当成分离、不提前耗尽逐边距离预算，不伪造交点。255 项专项通过，
  全球域、位置误差边界、区间排名与完整节点接入仍未完成。

### 当前对齐范围与兼容策略

4.47 共享非点距离搜索改为[完整边对域的分层队列](canvas-wgs84-geometry-distance.md#完整边对域的分层距离搜索447)，
避免先展开全部边对。此项不开放 Nearest 的非点真实测地；跨表候选、位置/排名精度、全球域和规模验收仍待完成。
同版本随后将 Point EXACT_DISTANCE 的跨表候选改为[ECEF 三轴保守召回](canvas-wgs84-geometry-distance.md#跨表候选的-ecef-保守召回基础447)，
验证二维种子错误时仍可恢复真实最近项；随后把 WGS84 距离、上下界、两端位置和零距离证据合入单次匹配 Struct，
当前 Point 路径的半径、Top N 与连接线已消费该结果。4.48 进一步用经验证的局部公共交叉位置、同一 Struct
和区间门槛显式开放点/线/面及 Multi 类型，并允许各部件独立局部、跨域关系可证明严格分离的 MultiPolygon；
Point/MultiPoint 有限集合已用分层下界证明精确最小值，避免亚容差近等距误判；旧 4.30 定义仍为 POINT_ONLY。
单个跨域面、连续线面复杂近等距、跨域接触关系、官方结果与规模验收仍未完成。

- Enterprise 标准要素分析、GeoAnalytics Server、ArcGIS Pro、GeoAnalytics Engine 是不同产品面；
  工具同名也可能参数不同，不把其他产品能力自动计入 Server 对齐范围。
- DataScalpel 采用 Spark/Sedona、多表 Map，对齐业务能力、参数语义和结果，不复制 Portal 服务发布、
  存储和作业 API，也不承诺 Esri API/算法实现的二进制兼容。

节点 MD 第 2 节保留 4.20 历史审计快照，不是当前完整契约；标为“目标”的字段、模式和线框是待开发设计，不能直接用于当前 JSON。
改变旧任务结果的修正（驻留、事件、历史窗口、六边形尺寸等）须采用显式新策略或协议门槛，
采用可选显式策略保留旧行为，不通过调整默认值静默改变旧任务；每项新增能力单独标记引入小版本。

4.36 公共距离/面积单位补充国际码/平方码及独立美国测量制，并统一紧凑选择、隐藏草稿门槛和换算。
旧单位结果不变；详见[单位映射与边界](canvas-spatial-units.md)。下方早期“完整单位”缺口中的距离/面积部分已补充；
时间/日历、速度别名核对、Measure 的三维/方位角等扩展、完整 Dissolve Boundaries、Buffer 与 Dissolve 的真实官方对照仍未完成，不能据此勾选整节点。

## 2. 当前覆盖与必须补齐项

| 节点 / 引入版本 | 官方参照 | 审计结论 |
| --- | --- | --- |
| GEOMETRY_DERIVE / 4.9 | 自有基础算子 | 4.29 显式维度与实际 Spark Z/M、函数/集合限制、Empty/退化、紧凑规则、扩展回归和真实页面验收已落实；非 GA 同名工具。 |
| GEOMETRY_SIMPLIFY / 4.10 | 自有基础算子 | 4.29 两算法维度矩阵及显式输出 XY、无隐式修复、共边反例、容差/单位边界、单几何规模和真实页面验收已落实；单要素拓扑保持不是覆盖层简化，容差由用户填写。 |
| SPATIAL_NEAREST / 4.11 | Enterprise 标准 Find Nearest | 4.30 新增真实距离/稳定同距排序、来源身份及独立连接线；4.48 显式开放受控 WGS84 点/线/面及 Multi 真实最近位置，并支持全球严格分离的局部 MultiPolygon 部件，保留旧 Point-only/KNN。已有 ArcGIS Online GeometryServer 五例底层距离真值，但 Enterprise Find Nearest 作业、单个跨域面和规模验收未完成。非 GA；路网/交通不在几何近邻范围，平面模式为自有扩展。 |
| SPATIAL_SUMMARIZE_WITHIN / 4.12 | GA Summarize Within / Aggregate Points 区域场景 | 4.24 显式分摊/原值、加权均值、字段 Count/Any；4.25 主表/组表及形状比例；4.36 距离/面积单位；4.37 按公式图的原值加权方差/标准差；4.39 共享日历窗口；4.41 规则格网区域。官方数值/边界、浏览器及规模对照仍待验收。 |
| SPATIAL_OVERLAY / 4.13 | GA Overlay Layers | 4.26 五模式、输入组合及显式家族二维输出已接入。成对交叠不宣称全局无重叠分区；Esri 容差、官方边界和规模性能仍待验收。 |
| TRACK_RECONSTRUCT / 4.14 | GA Reconstruct Tracks | 4.27～4.44 的确定次序/拆分/段归属、测地路径、字段统计、平面面、缓冲窗口与局部测地面链路已接入；4.76 完成完整输出血缘、20,000 观测单轨迹样例和真实页面收口。大域、Arcade、官方公式/容差/字段与生产容量不声明完全等价。 |
| TRACK_MOTION_STATISTICS / 4.15 | GA Calculate Motion Statistics | 4.23 新增观测历史窗口、八组 31 项、Idle 双阈值与垂直单位，旧 lag(N) 保留；4.76 完成缺失值/窗口边界、完整字段血缘、20,000 观测逐行样例和真实页面收口。官方字段别名、固定月年、服务端容差/数值与生产容量不声明完全等价。 |
| TRACK_FIND_DWELL / 4.16 | GA Find Dwell Locations | 4.22 参考点/固定均值中心扩展及四输出、4.35 字段 Count/Any 与实际统计类型已接入，旧相邻连段保留；4.76 完成平面/受控 WGS84、固定边界、完整输出血缘、20,000 点单轨迹样例和真实页面收口。官方测地中心/凸包公式、字段/统计类型、Enterprise 容差与数值、生产容量和 Streaming 不声明完全等价。 |
| TRACK_DETECT_INCIDENTS / 4.17 | GA Detect Incidents | 4.21 生命周期状态/时长、结束边界与确定排序，4.46 原字段窗口，4.63～4.65 累计距离/逐观测速度和加速度，4.66 轨迹时间/时长/序号，4.67 Point 相对观测 X/Y 已接入，旧策略保留；4.76 完成两种结果范围完整血缘、20,000 条单轨迹样例和真实页面收口。任意 Arcade、完整 Geometry/TrackWindow 复合对象、第三种结果范围、官方字段/数值/边界、生产容量和 Streaming 不声明完全等价。 |
| SPATIAL_BIN_AGGREGATE / 4.18 | GA Aggregate Points 格网场景 | 4.21 对边距离、4.33 H3、4.34 字段统计/留空切片、4.38 显式平面原点/业务范围、4.39 共享日历窗口已接入；4.76 完成三形状及平面补空路径的完整字段血缘、20,000 点惰性分布式聚合和真实页面收口。H3 球面业务范围、Enterprise 官方逐值/容差/边界编码及生产容量不声明完全等价。 |
| SPATIAL_POINT_CLUSTER / 4.19 | GA Find Point Clusters | 4.40 增加空间/Linear DBSCAN；4.45 HDBSCAN/四诊断、原行回接、集合血缘及 Inspector；4.76 完成全输出血缘、20,000 点显式 DBSCAN、HDBSCAN 分布式状态/Checkpoint 与真实页面收口。密集候选、大 k、深树、官方诊断数值、Enterprise 簇成员和生产容量不声明完全等价；MULTI_SCALE 非 GA 算法。 |
| SPATIAL_CENTER_DISPERSION / 4.20 | GA Summarize Center and Dispersion | 4.31 独立结果/线面质心/中位中心收敛/容量，4.32 原记录完整字段投影已实现；4.76 完成五结果完整字段血缘、20,000 点单组统计和真实页面收口。平均/中位/椭圆时间、interval 元数据、官方加权椭圆/退化边界、Enterprise 数值及生产容量仍未对齐；标准距离为自有扩展。 |

特别是驻留、事件、运动窗口、分摊与格网尺寸，完成语义修正及结果对照验收之前，不能标记“已对齐”。

## 3. 已有节点的复用边界

| 官方能力 | 可复用节点 | 不能忽略的差距 |
| --- | --- | --- |
| Clip Layer | SPATIAL_CLIP | 4.51 已补来源家族二维输出和低维接触过滤；4.77 已补逐来源融合相交 Mask，重叠覆盖不重复、分离片段保留为 Multi，旧定义保持 Pairwise。Esri 容差、官方边界/数值与生产容量对照仍未完成。 |
| Create Buffers | GEOMETRY_BUFFER + SPATIAL_AGGREGATE | 4.52 已支持固定值、字段和受控逐行表达式距离；4.53 通过可选单 UNION Dissolve 支持 None/All/List、Count、九种统计及 Multipart/Singlepart。官方数值、容差和规模对照仍缺。 |
| [Join Features（Enterprise 11.3）](https://enterprise.arcgis.com/en/portal/11.3/use/join-features.htm) | SPATIAL_JOIN、JOIN | 4.54 已补空间连接显式输出字段投影和同名处理；4.55 增加属性等值条件；4.56 实现 Keep all target features；4.57 显式一对多；4.58 增加一对一的 Join Count、五种数值统计及 FIRST/最大/最小/最新/最旧保留策略，并要求稳定并列排序；4.59 接入 12 种 Allen 区间关系和三种固定时长时间 Near；4.60 接入空间 Near/Near Geodesic、距离阈值/单位及一对多距离输出。纯属性连接继续复用 JOIN；官方服务结果、Esri 数值容差和规模验收仍未完成。 |
| Dissolve Boundaries | SPATIAL_AGGREGATE 的 UNION + Dissolve | 4.53 已覆盖按字段分组、Count、统计和部件方式；4.61 显式补充无字段相交/重叠/接触连通组及传递闭包。Esri 容差、真实服务数值和规模验收仍缺。 |
| [Merge Layers](https://developers.arcgis.com/rest/services-reference/enterprise/geoanalytics/tasks/merge-layers/) | UNION + 显式字段处理 | 4.62 已接入基准层、默认同名 Match/新字段追加、缺失补 NULL、自定义 Match/Rename/Remove、数值转换及 Geometry/无界时间约束；ArcGIS 服务结果、字段类型细分、规模和性能对照仍待完成。 |
| Summarize Attributes | AGGREGATE | 分组统计子集；时间步长、字符串 Any 与字段非空 Count 单独映射。 |
| Calculate Field | DERIVE_COLUMNS | 标量表达式子集，不等价 Arcade Geometry/轨迹窗口表达式。 |
| Append Data / Copy To Data Store | Input / Output | 归入读取写入，不为工具名重复建立 Processor。 |

优先扩展已有节点，但要列出实际可组合链路和缺失参数，不再用“已有节点完全覆盖”作笼统结论。

## 4. 实施顺序

### P0：修正会影响结果正确性的差异

1. 驻留候选范围算法、事件状态机、运动窗口与 Idle。
2. 区域分摊/加权、分组比例及输出粒度；六边形对边距离与兼容路径。
3. Geometry 类型/维度、同时间排序歧义、中心统计收敛和退化结果。

完成门槛是各节点反例、默认值、输出粒度和边界真值表验收，不只是编译或冒烟通过。

### P1：补齐核心矢量与时空能力

- Overlay 五模式；轨迹重建固定边界、表达式拆分、段归属、面/缓冲轨迹。
- Motion 八组指标；Dwell 四类输出；Center 四种官方分析及对应结果表。
- DBSCAN 时空邻域、HDBSCAN 及诊断字段；Aggregate Points 的 H3。
- 继续复核 Join 与 Merge 的官方数值和规模；Merge Layers 字段对齐已由 4.62 接入，Dissolve Boundaries
  无字段连通组已由 4.61 接入，Create Buffers 的 Dissolve 组合不另造重复节点。
- Calculate Density 已由 4.68 接入节点、Compiler、Runner 与 Inspector：点输入、fields、Uniform/Kernel、方格/六边形、
  binSize + unit、radius + unit、面积单位与时间切片。官方要求 radius 大于 binSize。
  **结果是矢量面格网及密度字段，不依赖 Raster**；4.76 已补完整血缘、20,000 点惰性/执行计划与
  真实页面证据。不能以 COUNT/格网面积或普通格网分级冒充密度分析；真实 Enterprise 数值、边缘、
  不同投影轴单位和生产容量验收仍开放。

### P2：扩大分析范围

| 能力 | 最低设计范围 |
| --- | --- |
| Find Hot Spots | 4.69 已接入投影 Point、完整方格、点数/字段和、固定距离 Gi*、原始双侧 p-value、显式 FDR-BH、`-3..3` 分级和时间切片；4.76 完成全输出血缘、20,000 点惰性/执行计划与真实页面收口，不等于密度分级。Enterprise 边缘、FDR、数值、不同投影轴单位和生产容量对照仍开放。 |
| Build Multi-Variable Grid | 4.70 已接入多输入统一格网、DistanceToNearest、AttributeOfNearest、AttributeSummaryOfRelated、逐变量半径/筛选和九种关联汇总；4.76 完成三类变量/九种统计完整血缘、两来源统一格网、20,000 点惰性分布式执行与真实页面收口。Enterprise 边缘、并列、统计数值、不同 Geometry/投影轴单位和生产容量对照仍开放。 |
| Enrich From Multi-Variable Grid | 4.71 已接入有界 XY Point 与已有 Polygon 变量格网相交、显式字段选择/改名、未匹配保留和边界单格稳定选择；不重新计算变量。4.76 完成完整字段血缘、20,000 Point/格网惰性索引化执行和真实页面收口。Enterprise 边界选择、字段类型、服务输出和生产容量对照仍开放。 |
| Group By Proximity | 4.72 已接入 Point/Line/Polygon 的 Intersects、Touches、Near Planar/Geodesic，可选时间 Intersects/Near 和受控同值/绝对差属性关系，并以 Connected Components 输出传递连通组；4.76 已完成完整集合血缘、20,000 孤立 Point 惰性分布式执行和真实页面收口。任意属性表达式、Enterprise 容差/月年边界、高偏斜图和生产容量对照仍开放。 |
| Trace Proximity Events | 4.73 已接入有界 XY Point 观测、显式 ID/起始表、Planar/Geodesic 距离、时间和同值属性约束、最大深度、首次事件和可选后续轨迹。4.76 已完成两结果完整血缘、20,000 观测/10,000 事件惰性分布式执行和真实页面收口。Enterprise episode/月年、极区/日期线、官方字段、高偏斜图和生产容量对照仍开放。 |
| Snap Tracks | 4.74 已接入有界 XY Point + LineString、轨迹/时间次序、线 ID 与 From/To 拓扑、可选方向四值映射、Planar/Geodesic 候选及 Viterbi 联合匹配；4.76 已完成全输出字段血缘、20,000 观测/1,000 轨迹规模样例和真实页面验收。首版只在同线或直接相邻线转移；多中间道路搜索、官方数值/容差、高密度候选、超长单轨迹和生产容量仍开放。 |
| Find Similar Locations | 4.75 已接入 GeoAnalytics 核心的 Attribute Values / Attribute Profiles、共同总体标准化、多参考平均、最相似/最不相似/两端结果和显式附加字段；4.76 已完成全输出字段血缘、20,000 候选/10,000 结果规模样例和真实页面验收。Pro 的 Ranked/Scale/Collapse、真实 Enterprise 数值、带真实上游的完整筛选交互和生产容量仍开放。 |
| Describe Dataset | 4.76 已接入字段统计、数据集描述 JSON、可选样本层和可选 XY Envelope 范围层，并完成四结果完整血缘、20,000 行规模样例和真实页面验收；来源及其他入口表保留。真实 Enterprise 字段细节、样本行为、服务结果、带真实上游的字段选择和生产容量仍开放。 |
| Forest、GLR、GWR | 独立统计/机器学习专题，训练/预测、诊断及模型制品。 |

Describe Dataset 的四类结果已采用显式逻辑表供下游消费；后续仍可在剖析/试运行入口复用其结果，
但不得引入另一套统计语义。
地理编码和路网分析需要外部服务/数据，独立设计。Create Space Time Cube 须先确定具体产品和 Cube 契约，
不把 Pro 同名工具自动计入 GA Server。Run Python Script 本轮不新增，SDK 仅复用自定义作业能力，不承诺 Python API 兼容。

## 5. 公共语义

### 图、输出与环境

- 一条边传完整表 Map；至少一条入边，两表计算不要求两条边；候选来自 Compiler inputTables。
- 产生明确新结果表并保留原表；无下游仅警告。
- 一个分析允许显式多表结果，例如区域主表+分组表、中心点表+椭圆表，仍用一个端口。
  各表名称可配置、无冲突、Schema/血缘明确；不生成隐藏表，不强制扁平化。
- 处理 CRS 对应分析前 Transform，输出 CRS 对应分析后 Transform，不能混淆。
- ArcGIS extent 通常是相交范围筛选，不等于裁掉范围外 Geometry；Filter 与 Clip 不能直接互换。
  无法通过现有节点等价表达时登记缺口。
- 存储/格式归 Output；分区、Broadcast、索引和 Executor 参数归运行环境。

### 单位、Geometry 与数据质量

- 距离、面积、时长、速度、加速度显式数值+单位；当前枚举仅为官方单位子集。
- yards、国际英尺/美国测量英尺等须精确映射，不能统一使用同一个 FEET 换算常量。
- SOURCE_CRS_UNIT 为自有选项，地理 CRS 中可能是度；米制面积/长度必须可靠换算。
- 当前 GEODESIC 只支持 EPSG:4326 + XY，这是平台限制，不是 ArcGIS 限制。
- Z/M 保留由具体函数决定，不因输入是 XYZ 就无条件继承；不支持时拒绝或要求显式维度转换。
- 每个节点分别规定 NULL、Empty、无效 Geometry、零长度/面积、单点/共线、重复时间和权重行为。
  “交给 JTS”不是最终契约，也不统一禁止官方允许的正常无结果情形。

### 时间模型不能混用

| 概念 | 官方参数 | 目标语义 |
| --- | --- | --- |
| 相邻观测间隔 | timeSplit / distanceSplit | 比较相邻事件，任一超阈值拆轨迹。 |
| 固定时间边界 | timeBoundarySplit / Unit / Reference | 以参考时刻对齐的重置周期，不是相邻 gap。 |
| 聚合时间步长 | timeStepInterval / RepeatInterval / Reference | 窗宽与步长独立，可重叠、相接或有空隙。 |
| 运动历史窗口 | trackHistoryWindow | 包含当前观测的 N 个点，瞬时与窗口指标分开。 |

4.20 的 TrackBoundaryConfiguration 只有 maximumTimeGap/maximumDistanceGap 及单位。
4.21 新增可选 fixedTimeBoundary（周期、单位、参考时刻和时区），四类轨迹节点共享；日/周/月/年
按日历，小时及更小单位按实际时长。此能力不修改未启用它的旧定义。
SpatialTemporalSlicing 包含时间字段、窗宽/步长及单位、参考时刻、时区、结果起止字段；
4.39 Bins/Within 另以可选 calendar 区分固定时长与日历周期，补周/月/年及 DST 边界；不定义 month=30 days。
它们不构成全部官方时间能力，真实服务边界仍待对照，Center 类型时间等其他节点缺口不由此补齐。
时间边界默认参考 Epoch；IANA 时区为平台显式适配；月末、闰年、夏令时需有样例验收。
轨迹同时间多观测须明确次序键或拒绝歧义，不能承诺现有 FIRST/LAST 和事件 ID 跨重跑确定。

### 统计与结果粒度

- 区分 COUNT_ROWS、COUNT(field 非空)、数值统计与字符串 Any。
- 区分总量分摊、率值不分摊、地理加权；分组占比按点数/裁剪长度/裁剪面积计算。
- Portal 与 REST 的 Summarize Within 对标准/加权统计文案有差异，详见节点文档：
  以显式模式、公式和官方样例验收，不猜测 MEAN 默认语义。
- 主表保存少数/多数值及比例，关联表保存各组统计；现有扁平布尔标记不是同一结果契约。
- 时间区间结果不等于 Streaming Watermark。当前聚合为 BOUNDED，时间元数据按结果粒度设计。

## 6. Inspector、Canvas 与安全

- 输入→分析→输出；常用参数直接显示，重复项用单行表格，复杂详情用 680～860px Modal。
- UI 线框为目标交互，未实现选项在交付前禁用并提示差距；已保存的失效值保留。
- “N 个配置问题”单行展示，悬停/点击看详情；普通业务错误允许保存草稿。
- 数值与单位同行，按模式显示关联字段；算法解释和官方参数名放帮助 Popover。
- Canvas 前两项预览，只显示算法/模式、计数和逻辑表名，不显示数据库/schema、坐标或运行数据。
- 用户配置的容差、半径、Top N 可以显示，它们不同于运行计算出的距离/速度/位置。
  条件字面量、映射值、凭据始终隐藏；日志采用安全白名单，不记录配置正文或数据。
- Compiler 仅零行/惰性计划；真实唯一性、NULL/退化和范围检查属于 Runner，不为卡片或日志额外触发 Action。

### 本次设计修订的统一交互约定

- 阅读顺序：节点第 1 节判断范围，第 3 节看官方参数与目标映射，第 4 节看目标 UI；第 7 节起的版本分节确定实际配置与当前面板。第 2 节为 4.20 历史快照，不能作为当前 JSON 模板。
- 12 个目标 UI 均就近标注当前能力与未实现组合；历史版本中的“尚未完成”如已被后续补齐，改为指向对应分节。不因修改文案而勾选节点完成或官方对照通过。
- 12 篇节点 MD 均补充“参数交互与初值”表，与各自第 4 节线框配套。线框里的表名、字段名和业务数值只是示例，不是节点默认配置。
- 逐项区分官方默认、平台推荐和用户必填；未经来源核实的值不得标为官方默认。距离容差、缓冲距离、Idle 阈值等业务参数不猜填。
- 常驻 Inspector 只显示当前模式生效参数；切换模式保留各分支草稿。非生效字段不参与当前语义校验，不能由隐藏字段暗中改变结果；真正不允许的字段组合就地标错。
- 输出按结果粒度设计：点级标记、每段一行、每区域一行及多分析多表不能合并成一个万能输出表单。需要多结果时直接列出每张结果表名，仍只使用一个输出端口。
- 问题入口仅一行“⚠ N 个配置问题”：悬停查看摘要，点击固定 Popover，按字段路径列出并可定位字段或打开对应 Modal；键盘 Enter/Space 可打开，Esc 可关闭。普通业务错误允许保存草稿，结构/安全错误仍阻止应用。
- 参数帮助统一包含“用途、单位/边界、小例子、官方参数名、平台差异”，不把解释常驻展开。算法未实现的选项禁用，目标线框必须保留设计状态标记。
- 不新增统一“ArcGIS 兼容”开关。已通过本地样例、参数已实现和已完成官方结果对照是不同状态，分别记录。

### 参数对齐登记与验收状态

每项能力使用以下登记格式；节点中的目标参数表先记录已知映射，开发时逐项补齐证据：

| 登记项 | 必填内容 |
| --- | --- |
| 参照 | 产品、版本、工具和官方链接；滚动 REST 文档的新参数需核实是否属于 11.3 |
| 输入参数 | 官方名称/枚举/默认值 → 平台字段/单位/初值；不适用项说明替代链路或不支持原因 |
| 生效条件 | 所属模式、必填条件、互斥关系、范围；切换后草稿保留方式 |
| 结果 | 表数量、行粒度、字段/单位、NULL 与退化规则、时间和 Geometry 元数据 |
| 证据 | 正常样例、边界和反例、数值容差；官方未说明处标为待对照，不补写成保证 |
| 状态 | 待设计 / 设计明确待开发 / 实现待验证 / 本地验证通过 / 官方样例或服务对照通过 |

没有官方运行环境时可完成设计和平台实现，但不得据此升级为“官方服务对照通过”。需要新增依赖、框架或外部服务时按工程约定另行确认。

### 本轮补充：同名工具与未决参数的展示

- 工具页面必须先确认产品面，再用于参数或算法依据。Enterprise 的 Map Viewer 同名工具、Pro 算法帮助和 GA REST
  不能互相替代；11.3 页面存在，也不自动证明它就是 GeoAnalytics Server 页面。
- 中心节点补充[逐参数与逐分析时间核对表](canvas-spatial-next-processors/spatial-center-dispersion.md#8-后续参数修订与证据边界)。
  当前平均/中位/椭圆的统计时间结果未实现，目标 UI 不得显示“自动继承”；平均/中位时间、Ellipse 起止集合及加权公式分别保留待核实项。
  中央要素的原事件时间字段可在 4.32 通过显式投影保留，这不等同于其他分析的统计时间输出。
- “目标有此选项”“当前版本可配置”“本地结果已验证”“官方结果已对照”分开记录。
  此规则适用于全部 12 个节点：目标线框可以保留未实现选项，但要明确标记，实际 Inspector 禁用未实现分支。
- 已落地规则应同步目标面板，避免历史审计文字继续造成歧义：例如 Reconstruct 固定周期始终 Gap，
  FinishLast / StartNext 不覆盖该规则；设置说明与当前选择的生效范围必须一致。
- 本次仅修订设计与交互约定，不新增协议字段、不修改运行代码，不将实现或验收清单提前勾选。

### 测地能力的面板开放条件

- 原语、节点与界面分别登记：内部距离/拓扑测试通过，不自动开放节点。Reconstruct 4.44 已接入完整局部链路，
  Nearest 4.48 已接入受控非点测地，Spatial Join 4.60 复用同一真实最近位置能力；已保存的无效组合回显并
  允许业务草稿保存，不静默换成平面或质心算法。单个跨域面、复杂近等距容量及官方作业对照仍保留为验收项。
- 面轨迹边界采样参数放在面轨迹设置内；不能读取隐藏的线轨迹段长来改变面结果。
  参数名称为“边界采样最大段长”，不是“最大位置误差”或“ArcGIS 精度”；具体契约从 4.44 引入。
- Nearest 的距离阈值、排名精度和连接线采样是三个不同概念，不合并成一个“容差”参数。
  内部 Boolean 相交证据不提供交点坐标，不能据此生成零长度连接线或虚构最近位置。
- 局部计算域、预算保护与数值未决行为属于平台能力边界，帮助中明确说明；不标为 ArcGIS 官方限制。
  执行失败时给安全问题码，不返回未经验证的近似结果，也不以隐藏回退扩大可用范围。

## 7. 对齐验收

### 逐参数记录，不使用整节点笼统结论

12 篇节点设计第 1 节新增“当前阅读入口与对齐边界”，以 4.45 汇总实际入口、产品差异和面板要求。
第 2 节保留历史，不因当前已接入而删除旧协议语义；旧快照不能直接用于生成新任务。

验收记录以“节点 + 模式/参数 + 结果”为单位。每条至少区分以下三项，不用一个百分比代替：

| 维度 | 可以得出的结论 | 不能据此推断 |
| --- | --- | --- |
| 参数覆盖 | 指定模式的字段、单位、默认值和联动已映射/实现 | 同名参数必然使用相同算法，或目标面板选项已经可用 |
| 平台语义验证 | 在明确输入、公式、容差及边界下，本地结果通过 | ArcGIS 的未公开公式、退化行为或全部数值与平台相同 |
| 官方对照 | 指定 11.3 工具、配置与样例结果在约定容差内一致 | 所有输入规模、地理范围和模式均已对齐 |

具体记录须包含官方来源及版本、平台引入版本、输入/期望结果、容差、已验证范围和剩余差异。
文档未说明的规则标为“平台裁决，待官方对照”；自有扩展标为“不适用官方等价”，不能标为对齐通过。
容量另外记录实际观测数、分组偏斜、几何复杂度、运行资源和耗时/Checkpoint 使用量；
配置允许 100 万点、不向 Driver collect 或使用 Spark，都不是大规模性能证据。

界面只给需要解释的参数放邻近帮助，不常驻展示整张审计表；未实现选项禁用，业务无效草稿保留。
HDBSCAN 四诊断公式、Center 时间与加权椭圆、Nearest 跨域面/连续精度边界以及 Reconstruct 全球域等未决项继续保留在开发范围。
清单勾选仅表示当前明确承诺范围已经形成实现、血缘、规模证据和页面闭环，不表示这些未决能力或 Enterprise
数值已对齐。本次收口不改算法、依赖、协议或 HTTP API。

### 完成判据

每项声明“对齐”前需具备：工具及版本来源、参数与默认值映射、算法/输出规范、正常/边界/反例、
明确的未对齐清单。空间比较用拓扑/距离及声明的容差，聚类比较成员关系而非簇编号；
不要求 ID 与 Esri 字节一致。官方文档矛盾、未说明的平局/退化标为“待对照验收”，不补写成官方保证。

优先样例：缓慢远离不是驻留；start-only 变 false 后结束；折线总路程不是端点位移；
半面积总量分摊、率值不分摊；线组按长度占比；六边形对边距离；日历边界；相同时间排序。
文档审计与开发验收分开记录；已通过的本地真值表测试不等同于完成 ArcGIS 服务实测对照。

## 8. 节点配置与 UI 索引

- [GEOMETRY_DERIVE](canvas-spatial-next-processors/geometry-derive.md)
- [GEOMETRY_SIMPLIFY](canvas-spatial-next-processors/geometry-simplify.md)
- [SPATIAL_NEAREST](canvas-spatial-next-processors/spatial-nearest.md)
- [SPATIAL_SUMMARIZE_WITHIN](canvas-spatial-next-processors/spatial-summarize-within.md)
- [SPATIAL_OVERLAY](canvas-spatial-next-processors/spatial-overlay.md)
- [TRACK_RECONSTRUCT](canvas-spatial-next-processors/track-reconstruct.md)
- [TRACK_MOTION_STATISTICS](canvas-spatial-next-processors/track-motion-statistics.md)
- [TRACK_FIND_DWELL](canvas-spatial-next-processors/track-find-dwell.md)
- [TRACK_DETECT_INCIDENTS](canvas-spatial-next-processors/track-detect-incidents.md)
- [SPATIAL_BIN_AGGREGATE](canvas-spatial-next-processors/spatial-bin-aggregate.md)
- [SPATIAL_POINT_CLUSTER](canvas-spatial-next-processors/spatial-point-cluster.md)
- [SPATIAL_CENTER_DISPERSION](canvas-spatial-next-processors/spatial-center-dispersion.md)
- [SPATIAL_DENSITY](canvas-spatial-next-processors/spatial-density.md)
- [SPATIAL_HOT_SPOTS](canvas-spatial-next-processors/spatial-hot-spots.md)
- [SPATIAL_MULTI_VARIABLE_GRID](canvas-spatial-next-processors/spatial-multi-variable-grid.md)
- [SPATIAL_SIMILAR_LOCATIONS](canvas-spatial-next-processors/spatial-similar-locations.md)
- [SPATIAL_DESCRIBE_DATASET](canvas-spatial-next-processors/spatial-describe-dataset.md)
- [SPATIAL_ENRICH_FROM_GRID](canvas-spatial-next-processors/spatial-enrich-from-grid.md)
- [SPATIAL_GROUP_BY_PROXIMITY](canvas-spatial-next-processors/spatial-group-by-proximity.md)
- [TRACE_PROXIMITY_EVENTS](canvas-spatial-next-processors/trace-proximity-events.md)
- [SNAP_TRACKS](canvas-spatial-next-processors/snap-tracks.md)

## 9. 官方资料

- [GA 工具目录](https://developers.arcgis.com/rest/services-reference/enterprise/geoanalytics/tasks/geoanalytics-tasks/)
- [GeoAnalytics Server 弃用声明](https://support.esri.com/en-us/knowledge-base/deprecation-arcgis-geoanalytics-server-000032771)
- [Calculate Density](https://developers.arcgis.com/rest/services-reference/enterprise/geoanalytics/tasks/calculate-density-geoanalytics/)
- [Build Multi-Variable Grid](https://developers.arcgis.com/rest/services-reference/enterprise/geoanalytics/tasks/build-multi-variable-grid/)
- [Enrich From Multi-Variable Grid](https://developers.arcgis.com/rest/services-reference/enterprise/geoanalytics/tasks/enrich-from-multi-variable-grid/)
- [Describe Dataset](https://developers.arcgis.com/rest/services-reference/enterprise/geoanalytics/tasks/describe-dataset/)
- [Dissolve Boundaries](https://developers.arcgis.com/rest/services-reference/enterprise/geoanalytics/tasks/dissolve-boundaries/)
- [Merge Layers](https://developers.arcgis.com/rest/services-reference/enterprise/geoanalytics/tasks/merge-layers/)
- [Find Hot Spots](https://developers.arcgis.com/rest/services-reference/enterprise/geoanalytics/tasks/find-hot-spots/)

其余工具官方链接位于对应节点 MD；REST 示例中的明显拼写错误不作为实现依据。
