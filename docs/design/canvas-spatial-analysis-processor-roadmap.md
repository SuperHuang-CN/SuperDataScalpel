# Canvas 空间分析 Processor 路线图与 ArcGIS 对齐审计

## 1. 状态与产品基线

- 审计日期：2026-09-07。审计基线为 Canvas 4.20，后续已进入开发；实现进度见[开发清单](canvas-spatial-development-progress.md)。
- 当前写出版本为 4.47，共享固定时长新增[周（固定 7 天）](canvas-spatial-units.md#447-固定时长周与日历周)，
  贯通时长阈值与输出单位。已有日历周不改变；固定月年、完整官方单位/数值对照仍待完成。
- 4.46 事件检测增加[受控字段窗口条件](canvas-spatial-next-processors/track-detect-incidents.md#8-446-受控字段窗口条件)，
  按官方左闭右开偏移、原始字段、轨迹片段和确定次序计算。九种字段窗口聚合可供开始/结束条件引用；
  完整 Arcade 几何/运动/时间窗口仍未完成，不将此增量当成整个事件节点验收。
- 4.45 的 HDBSCAN 已通过显式四诊断配置接入节点、集合血缘和 Inspector；保留 DBSCAN 旧语义。
  [聚类第 9 节](canvas-spatial-next-processors/spatial-point-cluster.md#9-canvas-445-hdbscan-节点接入)列明配置、参与规则与证据，规模/官方数值对照仍未完成。
- HDBSCAN 新增[中间文件归属与清理](canvas-spatial-next-processors/spatial-point-cluster.md#15-hdbscan-中间-checkpoint-的显式归属与清理)：
  保留最终可靠结果，清理自有快照及成功图阶段可确认归属的目录，不扫描共享目录。协议仍为 4.47；
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
- 4.30 Nearest 新增显式真实距离策略、来源唯一身份、全同距候选恢复及可选独立连接线表；旧 KNN/非点质心路径保留。真实测地暂仅支持 Point；非点测地最近位置、官方对照及大规模验收仍未完成。
- 4.31 Center 新增显式独立分析结果、线面质心/原中央要素、带停止证据的中位中心与分组要素/顶点保护；旧宽表算法保留。4.32 后续补原字段投影及其本地血缘验证；类型时间结果、官方椭圆加权公式和规模验收仍未完成。
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

- Enterprise 标准要素分析、GeoAnalytics Server、ArcGIS Pro、GeoAnalytics Engine 是不同产品面；
  工具同名也可能参数不同，不把其他产品能力自动计入 Server 对齐范围。
- DataScalpel 采用 Spark/Sedona、多表 Map，对齐业务能力、参数语义和结果，不复制 Portal 服务发布、
  存储和作业 API，也不承诺 Esri API/算法实现的二进制兼容。

节点 MD 第 2 节保留 4.20 历史审计快照，不是当前完整契约；标为“目标”的字段、模式和线框是待开发设计，不能直接用于当前 JSON。
改变旧任务结果的修正（驻留、事件、历史窗口、六边形尺寸等）须采用显式新策略或协议门槛，
采用可选显式策略保留旧行为，不通过调整默认值静默改变旧任务；每项新增能力单独标记引入小版本。

4.36 公共距离/面积单位补充国际码/平方码及独立美国测量制，并统一紧凑选择、隐藏草稿门槛和换算。
旧单位结果不变；详见[单位映射与边界](canvas-spatial-units.md)。下方早期“完整单位”缺口中的距离/面积部分已补充；
时间/日历、速度别名核对、现有 Buffer/Measure 复核和真实官方对照仍未完成，不能据此勾选整节点。

## 2. 当前覆盖与必须补齐项

| 节点 / 引入版本 | 官方参照 | 审计结论 |
| --- | --- | --- |
| GEOMETRY_DERIVE / 4.9 | 自有基础算子 | 4.29 显式维度与实际 Spark Z/M、函数/集合限制、Empty/退化和紧凑规则已落实；非 GA 同名工具。扩展回归验收继续按清单。 |
| GEOMETRY_SIMPLIFY / 4.10 | 自有基础算子 | 4.29 两算法维度矩阵及显式输出 XY、无隐式修复、共边反例已落实；单要素拓扑保持不是覆盖层简化。容差由用户填写。 |
| SPATIAL_NEAREST / 4.11 | Enterprise 标准 Find Nearest | 4.30 新增真实距离/稳定同距排序、来源身份及独立连接线结果；点测地与平面最近位置有本地验证，非点测地与规模验收未完成。非 GA；路网/交通不在几何近邻范围，平面模式为自有扩展。 |
| SPATIAL_SUMMARIZE_WITHIN / 4.12 | GA Summarize Within / Aggregate Points 区域场景 | 4.24 显式分摊/原值、加权均值、字段 Count/Any；4.25 主表/组表及形状比例；4.36 距离/面积单位；4.37 按公式图的原值加权方差/标准差；4.39 共享日历窗口；4.41 规则格网区域。官方数值/边界、浏览器及规模对照仍待验收。 |
| SPATIAL_OVERLAY / 4.13 | GA Overlay Layers | 4.26 五模式、输入组合及显式家族二维输出已接入。成对交叠不宣称全局无重叠分区；Esri 容差、官方边界和规模性能仍待验收。 |
| TRACK_RECONSTRUCT / 4.14 | GA Reconstruct Tracks | 4.27 确定次序/段归属，4.28 测地线，4.35 字段统计，4.42 平面面，4.43 缓冲窗口，4.44 测地面节点链路已接入。全球域、完整单位/血缘审计、官方统计/窗口/精度和容量验收仍待完成。 |
| TRACK_MOTION_STATISTICS / 4.15 | GA Calculate Motion Statistics | 4.23 新增观测历史窗口、八组 31 项指标、Idle 双阈值与垂直单位，旧 lag(N) 策略保留；完整官方单位、缺失值/窗口边界及官方服务对照仍待完成。 |
| TRACK_FIND_DWELL / 4.16 | GA Find Dwell Locations | 4.22 参考点/固定均值中心扩展及四输出、4.35 字段 Count/Any 与实际统计类型已接入，旧相邻连段保留；测地/官方统计公式及类型对照、完整单位/血缘与大单轨迹容量仍待补齐。 |
| TRACK_DETECT_INCIDENTS / 4.17 | GA Detect Incidents | 4.21 新策略已实现逐观测状态/时长、明确结束边界与排序；4.46 增加左闭右开的受控字段窗口。旧策略保留，完整 Arcade 几何/运动/时间表达式和官方服务对照仍待完成。 |
| SPATIAL_BIN_AGGREGATE / 4.18 | GA Aggregate Points 格网场景 | 4.21 对边距离、4.33 H3、4.34 字段统计/留空切片、4.38 显式平面原点/业务范围、4.39 共享日历窗口已接入；修复统计 Schema/重叠窗口。球面范围、官方数值及规模对照仍待补。 |
| SPATIAL_POINT_CLUSTER / 4.19 | GA Find Point Clusters | 4.40 增加空间/Linear DBSCAN；4.45 HDBSCAN/四诊断、原行回接、集合血缘及 Inspector 已接入。候选/深树规模、单位和官方诊断数值对照未完成；MULTI_SCALE 非 GA 算法。 |
| SPATIAL_CENTER_DISPERSION / 4.20 | GA Summarize Center and Dispersion | 4.31 独立结果/线面质心/中位中心收敛/容量已实现；4.32 增加原记录完整字段投影及本地血缘验证。平均/中位/椭圆时间、interval 元数据、官方加权椭圆及规模验收仍待完成。标准距离为自有扩展。 |

特别是驻留、事件、运动窗口、分摊与格网尺寸，完成语义修正及结果对照验收之前，不能标记“已对齐”。

## 3. 已有节点的复用边界

| 官方能力 | 可复用节点 | 不能忽略的差距 |
| --- | --- | --- |
| Clip Layer | SPATIAL_CLIP | 核对输入类型、片段、空几何，不凭存在 ST_Intersection 判定等价。 |
| Create Buffers | GEOMETRY_BUFFER | 常量距离子集；字段/表达式距离、溶解与统计另行核对。 |
| Join Features | SPATIAL_JOIN、JOIN | 空间/属性/时间关系、一对一统计与一对多、输出投影均需核对。 |
| Dissolve Boundaries | SPATIAL_AGGREGATE 的 UNION 聚合 | 分组融合子集；无字段时连通组、multipart=false、统计与 Count 不能省略。 |
| Merge Layers | UNION + 显式字段处理 | Schema 对齐后可组合；还需 Match/Rename/Remove、缺字段补 NULL、Geometry/时间类型约束。 |
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
- 复核 Buffer、Join、Dissolve、Merge，不另造重复基础节点。
- 新增 Calculate Density 设计：点输入、fields、Uniform/Kernel、方格/六边形、
  binSize + unit、radius + unit、面积单位与时间切片。官方要求 radius 大于 binSize。
  **结果是矢量面格网及密度字段，不依赖 Raster**；须验证邻域权重、核函数归一化、单位和边缘行为，
  不能以 COUNT/格网面积或普通格网分级冒充密度分析。

### P2：扩大分析范围

| 能力 | 最低设计范围 |
| --- | --- |
| Find Hot Spots | 分析字段、邻域、时间切片、Gi*、显著性和多重检验，不等于密度分级。 |
| Build Multi-Variable Grid | 多输入、统一格网；DistanceToNearest、AttributeOfNearest、AttributeSummaryOfRelated，逐变量半径/筛选。 |
| Enrich From Multi-Variable Grid | 点与现有变量格网关联、字段选择；不重新计算变量。 |
| Group By Proximity | 属性/空间/时间关系的连通分组和传递闭包，不等于两两 Join。 |
| Trace Proximity Events | 轨迹接触、时间和传播状态，独立输出契约。 |
| Snap Tracks | 路网拓扑、方向和匹配质量，不能用最近道路替代。 |
| Find Similar Locations、Forest、GLR、GWR | 独立统计/机器学习专题，训练/预测、诊断及模型制品。 |

Describe Dataset 不只有报告，还可返回范围要素、样本层和描述 JSON。优先在剖析/试运行入口提供；
需要下游消费时可显式输出逻辑表，不能以“污染 Map”为理由排除。
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
  Nearest 非点测地仍待接入；已保存的无效组合回显并允许业务草稿保存，不静默换成平面或质心算法。
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
HDBSCAN 四诊断公式、Center 时间与加权椭圆、Nearest 非点测地以及 Reconstruct 全球域等未决项继续保留在开发范围，
不因文档修订、参数接入或局部测试而勾选整节点完成。本次修订不改算法、依赖、协议或 HTTP API。

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

## 9. 官方资料

- [GA 工具目录](https://developers.arcgis.com/rest/services-reference/enterprise/geoanalytics/tasks/geoanalytics-tasks/)
- [GeoAnalytics Server 弃用声明](https://support.esri.com/en-us/knowledge-base/deprecation-arcgis-geoanalytics-server-000032771)
- [Calculate Density](https://developers.arcgis.com/rest/services-reference/enterprise/geoanalytics/tasks/calculate-density-geoanalytics/)
- [Build Multi-Variable Grid](https://developers.arcgis.com/rest/services-reference/enterprise/geoanalytics/tasks/build-multi-variable-grid/)
- [Describe Dataset](https://developers.arcgis.com/rest/services-reference/enterprise/geoanalytics/tasks/describe-dataset/)
- [Dissolve Boundaries](https://developers.arcgis.com/rest/services-reference/enterprise/geoanalytics/tasks/dissolve-boundaries/)
- [Merge Layers](https://developers.arcgis.com/rest/services-reference/enterprise/geoanalytics/tasks/merge-layers/)
- [Find Hot Spots](https://developers.arcgis.com/rest/services-reference/enterprise/geoanalytics/tasks/find-hot-spots/)

其余工具官方链接位于对应节点 MD；REST 示例中的明显拼写错误不作为实现依据。
