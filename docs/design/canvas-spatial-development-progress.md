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

当前 Canvas 为 4.47：共享固定时长增加周单位，与已有日历周分开；详情见下方本轮记录。
4.46 的事件检测受控字段窗口配置及条件入口保持不变。
前一阶段 4.45 的 HDBSCAN 从内部管道推进到正式可选诊断对象、节点执行/原行回接、零 Job 集合血缘及 Inspector。
原 DBSCAN 与 Multi-scale 不支持边界保留，Manifest/Result/HTTP 不变；完整节点的规模/官方数值验收仍开放。
新增非 null hdbscan 在保存端、GraphPlan、导入端均要求 4.45，包括非活动草稿。

### 非点测地距离分层边对搜索（2026-09-08，仍为 4.47）

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
  另外增加真实 Spark 多部件测距、阈值及 Analyzer 零 Job 用例，扩展回归正在执行，最终结果待登记。
- 更新共享距离设计、Nearest 和路线图；本轮不放开 Nearest 非点 EXACT_DISTANCE。
  跨表召回、区间排名、公共交叉位置、全球域、页面与官方验收继续保留，12 个节点完成框不勾选。

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
- Manifest/Result/HTTP/生产依赖均不变。完整 Arcade 几何/运动/时间窗口、官方服务对照、全页面与容量仍在原范围内，
  不勾选整个节点完成。完整契约、NULL/总体统计差异及 UI 图见[事件检测第 8 节](canvas-spatial-next-processors/track-detect-incidents.md#8-446-受控字段窗口条件)。

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

## 12 个节点

下列完整单位缺口的距离/面积部分已由 4.36 补充；时间/日历、速度别名和官方服务对照仍按节点继续验收。

- [ ] Geometry Derive：4.29 函数支持矩阵、实际 Z/M、Empty/退化/集合及紧凑规则已实现并完成专项；全页面/扩展回归验收待收口。
- [ ] Geometry Simplify：4.29 两算法维度、无效结果边界、共边反例、容差草稿及实际批流计划已验证；全页面与扩展容差/规模验收待收口。
- [ ] Nearest：4.30 真实平面/点测地距离、全同距排序、来源身份、显式连接线结果已实现并本地验证；非点测地最近位置、真实规模/官方及全页面验收仍未完成。
- [ ] Summarize Within：4.24 原值/总量分摊、交叠比例加权均值与字段 Count/Any，4.25 主表/组表、显式区域键与形状组比例，4.37 原值加权方差/标准差，4.39 日历窗口、4.41 规则格网已实现；官方公式/边界、浏览器及容量对照尚未完成。
- [ ] Overlay：4.26 五模式、输入组合、显式二维 Multi 与低维过滤、缺失侧字段已实现；官方容差/边界、全局分区差异与大数据性能对照仍待验收。
- [ ] Reconstruct：4.27 固定边界/次序/拆分绑定/段归属，4.28 测地线，4.35 字段统计，4.42 平面面轨迹、4.43 缓冲窗口及 4.44 测地面链路已接入；全球域、完整单位/血缘审计、容量和官方统计/窗口次序/精度验收未完成。
- [ ] Motion：4.23 八组指标、观测历史窗口、Idle 双阈值与垂直单位已做本地验证；完整官方单位、缺失值/窗口边界及官方服务对照尚未完成。
- [ ] Dwell：参考点/均值中心扩展与四输出、4.35 字段 Count/Any（含数值）与实际统计类型已做本地验证；官方公式/类型/细节对照、完整单位/血缘及大单轨迹容量尚未完成。
- [ ] Incidents：4.21 生命周期与确定次序、4.46 左闭右开的九种受控字段窗口、独立条件/指标草稿及原字段血缘已接入；完整 Arcade 几何/运动/时间窗口、官方结果范围/边界、全页面及容量仍待完成。
- [ ] Bins：4.21 对边距离、4.33 H3、4.34 字段统计/固定切片、4.38 显式平面原点/范围、4.39 日历窗口及安全展开已接入；球面范围、官方统计数值及规模验收待完成。
- [ ] Cluster：4.40 显式空间/Linear DBSCAN；4.45 HDBSCAN/四诊断节点与契约、原行回接、集合血缘和 Inspector 已接入并本地验证；候选/深树容量、完整单位、官方诊断数值及全页面验收仍待完成。
- [ ] Center：4.31 线面质心/原中央要素、独立结果、确定身份排序、中位停止证据及容量保护，4.32 原字段投影/血缘已实现；各类型时间、官方椭圆加权公式及规模/页面验收仍未完成。

## 路线图其余范围

- [ ] 公共单位/日历边界、Geometry/NULL/退化策略、多结果 Schema/血缘和安全摘要。
- [ ] 现有 Clip、Buffer、Join、Dissolve、Merge、Summarize Attributes、Calculate Field 能力复核/补齐。
- [ ] Calculate Density（矢量格网、Uniform/Kernel、半径/单位/时间）。
- [ ] Hot Spots、Multi-Variable Grid 与 Enrich、Group By Proximity。
- [ ] Trace Proximity Events、Snap Tracks、Similar Locations、Forest/GLR/GWR 专题设计与实现。
- [ ] Describe Dataset 的剖析/范围/样本；需要新外部服务、依赖或框架的项先讨论确认。

路线图明确独立设计/不在本轮的路网服务、地理编码、Cube、自定义脚本不伪称已实现。
P2 中尚缺详细参数/算法决策的能力保留为待设计，不用宽泛节点名占位充数。
