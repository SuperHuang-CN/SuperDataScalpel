# Canvas `SPATIAL_POINT_CLUSTER` Processor 设计

## 1. 定位与审计结论

引入于 Canvas 4.19，仅 BATCH，Point 输入、保留参与行并追加簇标记。4.40 增加显式空间密度连通与 Linear 时空 DBSCAN，见第 7 节；4.45 接入 HDBSCAN 及四类诊断（第 9 节）。官方 GA Find Point Clusters 是 DBSCAN/HDBSCAN，不含 MULTI_SCALE。

本文区分 4.20 审计快照、目标设计及分节记录的后续实现；开发状态以[进度清单](../canvas-spatial-development-progress.md)为准。旧配置不会自动切换 4.40 的新密度连通策略。

### 4.47 固定时长周

Linear DBSCAN 的 `dbscan.searchDurationUnit`新增“周（固定 7 天）”，每周为 604800 秒。
与已有按时区推进的日历周区分；切换单位不换算已填数值，隐藏草稿保留并参与 4.47 门槛。
固定月年不开放，阈值边界、节点算法与输出粒度不变；详见[共享时长规则](../canvas-spatial-units.md#447-固定时长周与日历周)。

### 4.36 公共单位补充

DBSCAN 搜索距离使用公共距离单位。4.36 的单位补齐本身不开放新算法；后续空间/Linear 时空 DBSCAN 已在第 7 节（4.40）接入，HDBSCAN 已在第 9 节（4.45）接入，但规模与官方诊断数值对照未完成。原有距离方法/坐标系限制继续适用。
单位名称明确国际制/美国测量制，切换不自动换算数值，旧单位结果不变。具体枚举、字段和证据见[公共空间单位](../canvas-spatial-units.md)；不代表整节点已完成官方对照。

### 当前阅读入口与对齐边界（4.45 汇总）

- **已接入范围**：4.40 空间/Linear DBSCAN 见第 7 节；4.45 HDBSCAN 节点、四诊断与面板见第 9 节。
- **官方参照与差异**：参照 GA Find Point Clusters；HDBSCAN 诊断已实现但 Esri 数值归一化未核实，候选/深树规模未验收；MULTI_SCALE 仍不支持。
- **面板修订要求**：HDBSCAN 隐藏半径和时间邻域；帮助说明诊断数值与容量差异，不把保护上限说成已验证容量。

本摘要不替代逐版本契约。下节的“当前”均指 4.20 历史状态；目标线框不作为已实现截图。
参数覆盖、本地验证和官方结果对照分别登记，见[对齐验收规则](../canvas-spatial-analysis-processor-roadmap.md#7-对齐验收)。

## 2. 4.20 配置快照（历史）

```ts
type SpatialPointClusterParameters =
  | {
      algorithm: 'DBSCAN';
      searchDistance: number;
      searchDistanceUnit: SpatialDistanceUnit;
      minimumFeatures: number;
    }
  | {
      algorithm: 'HDBSCAN';
      minimumFeatures: number;
    }
  | {
      algorithm: 'MULTI_SCALE';
      minimumFeatures: number;
      sensitivity: number;
    };

interface SpatialPointClusterConfiguration {
  sourceTableName: string;
  pointGeometryColumnName: string;
  featureIdColumnName: string;
  distanceMethod: SpatialDistanceMethod | null;
  parameters: SpatialPointClusterParameters;
  outputTableName: string;
  clusterIdColumnName: string;
  noiseColumnName: string;
}
```

- 当前 featureId 为标量，最少要素 2～100000；DBSCAN 有搜索距离和单位。
- HDBSCAN/MULTI_SCALE 当前返回 SPATIAL_CLUSTER_ALGORITHM_NOT_AVAILABLE，旧值保留并标错，不静默改为 DBSCAN。
- MULTI_SCALE/sensitivity 只是开发期协议占位，不代表已定义可靠算法；不继续声称来自 GA Server。
- 当前噪声 clusterId=NULL、noise=true；官方 CLUSTER_ID=-1，属于需要显式映射的差异。

## 3. 官方参数与算法目标

| 参数 / 结果 | DBSCAN | HDBSCAN |
| --- | --- | --- |
| minFeaturesCluster | 给定邻域内形成核心点的密度门槛，不能简单解释为每个输出簇的硬性最小行数 | 密度估计邻域数（含自身）及提取时最小簇大小 |
| searchDistance / Unit | 空间搜索距离 | 不使用，不显示 |
| timeMethod=Linear | 可选时空聚类；instant 时间字段必填 | 不支持时间聚类 |
| searchDuration / Unit | 启用 Linear 时必填，空间和时间阈值同时满足 | 不使用 |
| CLUSTER_ID / COLOR_ID | 簇标识、噪声与显示分组 | 同左 |
| PROB / OUTLIER / EXEMPLAR / STABILITY | 不适用 | 成员概率、离群程度、代表点、跨尺度稳定性 |

- 时空 DBSCAN 是空间距离 AND 时间邻近，不是先按不相交时间窗分别做空间聚类。
- HDBSCAN 不得简化成多次 DBSCAN 后随意选簇，也不要求用户填无效 radius。
- GA REST 不提供当前自有 distanceMethod 同名参数；平台 Planar/Geodesic 的距离模型须明确并验收。
- Noise=-1 和平台 NULL+flag 的映射记录在结果规范中；颜色编号是展示信息，可由前端派生，不用于分析。
- 簇编号只在本次结果内有效；比对成员关系/噪声/统计，不比较编号相同。
- MULTI_SCALE 不列入 GA 对齐目标，新建目标 UI 移除；导入旧枚举仍显示不支持并允许草稿。
  如将来参考 Pro OPTICS，要独立定义算法与参数，不复用含糊的 sensitivity。

## 4. 目标 Inspector UI

设计状态：空间/Linear DBSCAN 见第 7 节（4.40）；HDBSCAN 及四类诊断在第 9 节（4.45）接入，规模与官方数值验收仍开放。MULTI_SCALE 不作为 GA 算法展示，不能用它替代 HDBSCAN。

```text
来源 / 点字段       [ device_points / location ▼]
要素唯一字段        [ event_id ▼]
算法                [ DBSCAN | HDBSCAN ]            (?)
最少要素            [5]
搜索距离            [200][米 ▼]                     DBSCAN
时空聚类            [关闭 | Linear]                 DBSCAN
时间字段 / 邻域     [event_time ▼] [30][分钟 ▼]      Linear
输出字段            cluster_id · is_noise           [设置]
HDBSCAN 诊断        概率 · 离群 · 代表点 · 稳定性    [设置]
输出表              [ device_clusters ]
```

每种算法只显示生效参数。尚未具备实现的选项禁用并提示能力缺口，不预计算“合适半径”。
Help 区分 DBSCAN 的密度门槛与 HDBSCAN 的最小簇参数，不能用统一解释误导。


### 参数交互与初值（目标设计）

下表是目标面板约定，不修改旧任务默认值；未明确标注为官方默认的初值均为平台推荐。

| 配置组 | 初值与条件显示 | 对齐边界 |
| --- | --- | --- |
| 算法 | 新建推荐 DBSCAN；HDBSCAN 作为独立参数分支 | 平台初值；MULTI_SCALE 不作为 GA 算法选项 |
| DBSCAN | 密度门槛及空间半径必填；时间邻域默认关闭 | 开启 Linear 后同时要求时间字段、时长和单位 |
| HDBSCAN | 只显示该算法适用的最少要素及诊断字段，不显示半径 | 诊断含概率、离群、代表点、稳定性 |
| 噪声结果 | 字段设置说明平台 NULL + flag 与官方 -1 的映射 | 噪声不是无效输入或运行失败 |
| 算法切换 | 保留各分支草稿；未实现分支禁用并说明 | 不回退执行另一种算法 |

“最少要素”的帮助随算法变化。时间范围筛选和时空邻域分开，不能让用户以为按时间桶分别 DBSCAN 等价于 Linear 时空聚类。

## 5. 执行及验收

- 旧空间 DBSCAN 保留 Sedona 实现；4.40 显式空间/Linear 复用 Sedona 已携带的 GraphFrames，实现核心连通与边界归属，详见第 7 节。4.45 HDBSCAN 复用现有 Spark/GraphFrames 管道，详见第 9 节；后续优化仍不得未经确认增加 ML 框架或全表 collect。
- 当前 Sedona DBSCAN 依赖 RDD Checkpoint：Local 应用隔离目录；Cluster 共享 HDFS/ViewFS/S3A；
  缺失返回 SPATIAL_CLUSTER_CHECKPOINT_NOT_CONFIGURED。此为运行基础设施，不是 ArcGIS 参数对齐证据。
- 验收：两簇+噪声、边界点、重复点/ID、邻域是否含自身、相同位置不同时刻、时空连续链跨窗口。
- HDBSCAN 验证不均匀密度、噪声和四类诊断，不以“生成 clusterId”当作完成。
- featureId 唯一性真实检查在 Runner，重复值不能只靠字段类型保证。
- 保留所有有效原行、追加字段，新表 BOUNDED；不输出 Streaming Watermark。
- Canvas 仅显示算法/参数配置/表名，日志不记录点、簇成员或坐标。

## 6. 官方依据

- [GA Find Point Clusters：两个算法与时空参数](https://developers.arcgis.com/rest/services-reference/enterprise/geoanalytics/tasks/find-point-clusters/)
- [共同规则与版本策略](../canvas-spatial-analysis-processor-roadmap.md)

## 7. 4.40 显式空间 / Linear 密度连通

```ts
interface SpatialDbscanOptions {
  mode: 'LEGACY_SPATIAL' | 'SPATIAL' | 'LINEAR' | null;
  timeColumnName: string;
  searchDuration: number | null;
  searchDurationUnit: 'MILLISECONDS' | 'SECONDS' | 'MINUTES' | 'HOURS' | 'DAYS' | null;
}
// SpatialPointClusterConfiguration 新增：
dbscan?: SpatialDbscanOptions | null;
```

### 兼容与生效条件

- 节点引入版本仍为 4.19。任意非 null dbscan（含旧模式和非 DBSCAN 算法下的草稿）要求 4.40；
  低版本三端统一返回 `SPATIAL_DBSCAN_OPTIONS_REQUIRE_SCHEMA_VERSION`。Manifest/Result/API 不变，不新增依赖。
- 缺失/null/LEGACY_SPATIAL 使用原 Sedona 空间算法；SPATIAL/LINEAR 使用显式核心密度连通。
  新建平台推荐 SPATIAL，半径/最少要素沿用原初值；时间字段为空、邻域 null、单位分钟，不猜业务时间范围。
- 非 DBSCAN 算法保留但不执行 dbscan 参数；HDBSCAN/MULTI_SCALE 仍返回明确不支持，不能用 DBSCAN 偷偷代替。
- Linear 才要求 TIMESTAMP、正整数时间邻域和明确时长单位；其他模式隐藏并保留这些参数。
  当前支持毫秒至日，天为固定 24 小时，不使用上一节的日历聚合桶；更长时间单位和完整官方单位对照仍待补齐。
- 空模式、缺字段、0 邻域/半径及非法密度门槛为可保存业务草稿，由 Operator 校验；对象/枚举/非有限数值等结构错误拒绝。

### 算法与输出

1. 逻辑输入要求 XY Point，实际距离使用 Point 的 X/Y。新模式跳过 NULL/Empty 点，Linear 再排除 NULL 时间；非 Point、非法或非有限 X/Y 安全失败。实际额外 Z/M 不参与距离，也不增加物理维度相等门禁；原 Geometry 随原字段保留。
2. 参与点的 featureId 必须非空且唯一；相同位置、不同 ID 是不同观测，不能按坐标或整行哈希合并。
   旧空间路径也补充原配置已声明的 ID 约束，不改变合法唯一 ID 数据的旧聚类算法。
3. 建立包含自身的邻域：`distance(a,b) <= searchDistance`；Linear 额外要求
   `abs(time(a)-time(b)) <= searchDuration`。时间微秒差用 Decimal 计算，避免极端时间 Long 相减溢出。
4. 邻居数量达到 minimumFeatures 才是核心点；只在核心点间计算连通分量。非核心边界点不能桥接两个簇。
5. 边界点归属于相邻核心簇；同时邻接多个簇时取本次最小组件编号。编号只在本次结果有效，不承诺重跑编号/歧义边界归属不变。
6. 噪声为 `clusterId=NULL, noise=true`，对应官方 -1；其他参与点为 long 簇号及 false。保留参与行的所有原字段、类型和值。
   无参与点输出零行；无核心点保留参与点并全部标记噪声。结果为有界新表，原入口 Map 对象不变、无 Watermark。

SPATIAL/LINEAR 的 Planar 使用来源 CRS 距离；Geodesic 使用 WGS84 椭球点距离，仅 EPSG:4326 XY，支持日期线两侧邻近点。
平台距离方法、NULL 排除和歧义边界裁决不声明为官方完全等价；不是按不相交时间桶运行多次空间 DBSCAN。

### 编译与运行边界

- 原代码直接调用 Sedona DBSCAN，其 Dataset.checkpoint 与 GraphFrames 连通分量会立即提交作业。
  4.40 修复编译路径：同一 Operator 校验配置，预检只构造零行 Schema 与全体参与字段的集合血缘表达式，
  不调用聚类、不设置 Checkpoint、不提交 Spark Job。Schema-only 的聚类表达式不可用于实际写入，误用安全失败。
- 时间、Geometry、身份字段作为集合依赖进入 cluster/noise 血缘，原字段仍为原字段投影；不把簇号说成某一行的直接复制。
  预检不能保证真实 ID/几何合法，数据相关检查在执行侧完成。
- 运行复用 Sedona 已打包且原 DBSCAN 已在使用的 GraphFrames；没有新 ML 框架或生产依赖。
  核心身份、邻域和最终结果按算法阶段做可靠 Checkpoint，GraphFrames 的持久化结果在最终结果 Checkpoint 后释放。
  这些是实际聚类执行，不是为日志/指标增加 Action；不向 Driver collect 全表、点或邻居列表。
- Local 沿用应用隔离目录；Cluster 仍要求共享 `spark.checkpoint.dir`，未配时清晰拒绝。
  全邻域最坏 O(n²) 条边，Checkpoint 空间与图迭代资源仍需按任务规模配置；分布式不等于无限容量。
- 所有用户列先隔离内部别名，`id/src/dst/geometry` 等原字段不会被内部图身份覆盖。内部单调 ID 在 Checkpoint 时绑定一次。
- 安全错误：`SPATIAL_CLUSTER_POINT_INVALID`、`SPATIAL_CLUSTER_FEATURE_ID_INVALID` 为不可重试 SCHEMA；
  `SPATIAL_CLUSTER_PREVIEW_NOT_EXECUTABLE` 为不可重试 CONFIGURATION。编译单位/时间错误使用精确 dbscan 字段路径，摘要不含 ID 值、时间值或坐标。

### 4.40 Inspector UI（历史，4.45 接入见第 9 节）

```text
来源点表 / Point       [ observations / location ]
要素唯一字段           [ event_id                 ]
距离方法               [ 平面 | 测地线 ]
算法                   [ DBSCAN | HDBSCAN（未完成） ] (?)
搜索距离               [200][米 ▾]   最少要素 [5] (?)
聚类语义               [ Linear 时空密度连通      ▾] (?)
时间字段               [ event_time ▾] 邻域 [30][分钟 ▾]
簇 / 噪声字段          [cluster_id] [is_noise]
输出表                 [ device_clusters ]
```

- 时间行只在 Linear 出现；模式变更由 Inspector 内确认 Modal 管理，取消不修改草稿，确认保留隐藏字段/数值。
- 密度门槛帮助明确包含自身和“核心”含义；单位、Checkpoint 和规模说明使用邻近帮助，不常驻堆叠大段说明。
- 未配置/失效时间及非正数就地标红，允许应用草稿；上游不可用时不推测 Schema。
- 新建 UI 不再展示 MULTI_SCALE；旧导入值仍明确显示不支持且不静默改写。HDBSCAN 保留禁用项，不暗示已有诊断输出。
- 导入已有 HDBSCAN/MULTI_SCALE 时，允许在当前 Inspector 内经确认切到 DBSCAN，也允许经确认恢复原算法草稿。
  各算法的编辑值在本面板会话内独立保留，取消确认不修改配置；应用只保存当前 parameters 分支，关闭面板后不承诺保留其他算法草稿。
  非活动 dbscan 时间对象则仍按正式可选字段持久化。恢复未实现算法只用于草稿，不开放运行能力。
- Canvas 展示旧空间/空间密度连通/Linear 时空、最少要素和噪声字段；安全摘要只记录模式，不记录时间值或成员数据。

本阶段并非整节点完成：HDBSCAN 与四诊断、完整官方单位/服务结果、全页面和规模验收继续保留在路线图中。

## 8. HDBSCAN 参数、诊断与开放条件修订

修订日期：2026-09-08；本节记录 4.44 阶段的目标设计和内部实现证据，当时不是可导入配置。
该阶段 Operator 返回 `SPATIAL_CLUSTER_ALGORITHM_NOT_AVAILABLE`、HDBSCAN 面板禁用；
随后 4.45 接入正式字段和节点，见第 9 节。下方原型阶段的待办/禁用描述不覆盖后续节点状态。

### 官方参数与平台适配

| 项目 | 冻结设计 | 依据或差异 |
| --- | --- | --- |
| 最少要素 k | 同时用于密度估计和压缩层次树最小簇大小；包含观测自身 | Enterprise 11.3 GA 文档；不是仅设置输出簇最小行数 |
| 核心距离 | 排除自身后，取第 k−1 个其他观测的距离；相同坐标、不同 ID 分别计数 | 对“含自身”的算法适配，不能直接沿用其他库的 k 个其他邻居约定 |
| 空间半径 | 不配置、不使用 | HDBSCAN 从密度层次提取簇，不借用隐藏的 DBSCAN 半径 |
| 时间邻域 | 不支持 Linear，不执行隐藏时间设置 | 时空聚类保留在 DBSCAN 分支；不是分别按时间桶做 HDBSCAN |
| 距离方法 | 平台 Planar / WGS84 Point 距离 | 平台显式适配，不标成 GA REST 同名参数或官方默认 |
| 算法 | 互达距离 → 精确最小生成树 → 层次树压缩 → EOM 稳定性选簇 | HDBSCAN 算法依据；重复 DBSCAN 或截断 KNN 森林不是等价实现 |
| 输出身份 | 保留原有效观测，追加簇与诊断字段；噪声仍采用 NULL 簇号 + noise=true | 与官方 -1 显式映射；不同运行的簇编号不作为等价比较依据 |

2～100000 是现有平台 minimumFeatures 校验范围，不是从官方抄录的业务上限。
HDBSCAN 的内部容量保护也不作为面板可选择的“最少要素”范围或规模承诺。

### 四类诊断：含义、结果与证据分开

目标设置弹窗以四行展示诊断类型、输出字段名与帮助；字段名与原表字段、簇/噪声字段及其他诊断字段
按平台列名规则判重，失效草稿保留。所有诊断仍需新增明确 Contracts 配置及版本门槛后才能使用，
本节不提前规定未落地的 JSON 字段。以下类型和噪声规则是设计/内部实现状态，不冒称官方完整结果契约。

| 诊断 | 目标平台类型 | 含义及当前内部原型 | 尚需核对 |
| --- | --- | --- | --- |
| PROB | DOUBLE | 0～1 的簇成员强度；内部以退出密度与所属选中簇死亡密度计算，噪声为 0 | 不解释为分类准确率；需对照 GA 边界与重复点结果 |
| OUTLIER | DOUBLE | 0～1，越大越离群；内部使用 GLOSH、参考直接父簇的最大后代死亡密度 | 不简单写成 1−PROB；不足最少要素时当前原型返回 0，公开语义待核对 |
| EXEMPLAR | BOOLEAN | 代表观测；内部取选中簇之下叶簇中最大退出密度的观测，噪声为 false | 可能有多名代表，不等于唯一中心点；与官方表示方式显式映射 |
| STABILITY | DOUBLE，可空 | 同簇相同的跨尺度持久性；当前原型作归一化处理，噪声为 null | GA 文档未据此证明相同归一化公式；零距离与无穷密度处理待对照 |

当前原型对有限密度使用 `stability / clusterBirthSize / globalMaximumLambda`。
重复点导致全局最大密度为无穷时，采用共同有限上限趋于无穷的极限：
`infiniteExitMembers / clusterBirthSize`。这会使无无穷密度成员的有限密度簇得到 0，
与某些 Python 实现的特殊分支不同；只登记为当前原型决策，**不是已确认的 Esri STABILITY 定义**。
在语义与反例审查完成前，不将该诊断开放成“ArcGIS 等价”能力。

其他需明确验收的选择：等权边同时合并、EOM 完全平局优先父簇、根簇不参与选取。
全零距离且仅有根簇时，当前原型结果为噪声；不通过隐式开启单簇选取来改变它。
不能只用两个明显分离的簇证明这些边界已对齐。

### 目标配置面板（尚未开放）

```text
算法                 [ HDBSCAN ▾ ]                         (?)
最少要素             [ 5 ]                                 (?)
距离方法             [ 平面 | 测地线 ]
输出字段             簇 · 噪声                         [设置]
诊断输出             概率 · 离群 · 代表点 · 稳定性       [设置]
输出表               [ observations_clustered ]
```

数值 5 和表名仅为线框示例。最少要素帮助说明“含自身，且同时影响密度估计与最小簇大小”。
算法切换保留各分支草稿的范围遵循第 7 节，不凭目标线框承诺未序列化的分支可跨面板会话保留。
DBSCAN 半径、Linear 时间行在 HDBSCAN 下隐藏且不执行；四诊断的公式差异和噪声含义放在帮助中，
不常驻展开。尚未开放时沿用当前禁用项，不提前渲染一个看似可保存并执行的诊断表单。

### 内部实现与规模边界

- `HdbscanSpanningTree` 已有互达距离计划与分布式 Borůvka MST。统一 `(weight, src, dst)`
  边序避免同权候选形成环；仅标量数量返回 Driver，图阶段复用现有 GraphFrames 与 Checkpoint。
- 当前候选是完整点对，成本为 O(n²)，仅作为正确性基准。百万顶点保护不意味着百万点可实际运行；
  不把“使用 Spark”写成规模验收通过。后续候选优化须证明保持同一互达距离 MST，不能任意截断 KNN。
- 后续 `HdbscanCondensedTree` 在 Spark 表上拆除每个活动组件的所有同权最大边，再按子组件大小压缩，
  单个幸存子组件继承父簇身份，多幸存子组件分别形成新簇；保存簇关系、带数量的退出层级及每个观测的直接父簇。
  `HdbscanDiagnostics` 分布式自底向上比较 EOM，再自顶向下继承已选祖先，生成四诊断和最小内部身份簇号。
  没有将树/成员 collect 到 Driver 或集中到单个 Executor；纯 Java `HdbscanHierarchy` 留作独立的小样例对照算法。
- 上述层次迭代最坏轮数与树深有关，仍需大量 Shuffle/Checkpoint。它解决内存 helper 的接线问题，
  不等于已解决深树性能或集群容量；没有通过限制分组后各自聚类来悄悄改变整体聚类语义。
- MST 入口将内部身份和 Geometry 一起 Checkpoint，避免只固定顶点 ID 而让边计划重新计算身份。
  GraphFrames 的临时缓存在阶段输出 Checkpoint 后释放，仍需审计应用级 Checkpoint 空间与失败清理。
- 无效/重复来源 ID、真实 Point 校验、稳定内部身份与原行回接尚须通过节点统一入口落实；
  单独验证生成树拓扑不能证明调用方传入的一定是最小生成树。
- Compiler 继续仅构造零行 Schema 和集合血缘；四诊断依赖参与点集合，不是原行直接复制字段。
  真实聚类、Checkpoint 和数据检查仅在 Runner。不得为了摘要、日志或面板运行算法。

### 开放清单与现有证据

- 内部已验证：`HdbscanHierarchyTest` 8 项、`HdbscanSpanningTreeSparkTest` 4 项；
  既有日志 `/tmp/datascalpel-hdbscan-mst.log` 为 BUILD SUCCESS。涵盖独立 dense Prim 对照、
  含自身的核心距离、等权/重复位置、密度尺度、深树、日期线点距和零 Job 计划分析。
  这些是本地内部测试，不是完整 Operator、浏览器、集群容量或 ArcGIS 服务验收。
- 后续分布式层次/诊断专项与 Runner 共 46 项通过：`/tmp/datascalpel-hdbscan-diagnostics-cases.log`。
  新增小分支退出、父/子 EOM、等权/零距离、空/不足观测与四诊断逐点对照；
  GLOSH 反例明确概率 10/11、离群 2/11，而不是 1−概率。对应失败与修正记录见进度清单。
- 最终内部串联回归 47 项通过：`/tmp/datascalpel-hdbscan-distributed-final.log`。
  实际平面/跨日期线点输入经过 MST、压缩层次与四诊断，并逐观测对照内存参考；
  非确定性身份表达式的计数器确认每个输入仅求值一次。仅此小样例串联，不替代真实节点/规模/官方验收。
- 内部错误已接入统一 Runner：TREE_INVALID 为 INTERNAL，HIERARCHY_LIMIT_EXCEEDED 为 RESOURCE，
  NUMERIC_RANGE_INVALID 为 SCHEMA；完整前缀均为 `SPATIAL_HDBSCAN_`，PROCESS 阶段且不可重试。
  摘要不含观测或密度值，各消费端继续使用现有通用错误字段。
- 待完成：候选优化、深树/资源容量方案、诊断 Contracts 与三端门槛、
  Operator/原行回接/字段血缘、精确问题路径、Inspector/Canvas、可复现规模与官方结果对照。
- 不预定新小版本号。只有完整管道具备明确契约时引入对应版本；当前 4.44、Manifest、Result、HTTP 均不因本节改变。

来源：[Enterprise 11.3 GA Find Point Clusters](https://enterprise.arcgis.com/en/portal/11.3/use/geoanalytics-find-point-clusters.htm)
用于官方能力/参数；[HDBSCAN 算法说明](https://hdbscan.readthedocs.io/en/latest/how_hdbscan_works.html)
用于算法背景，后者是独立实现资料，不能替代 Esri 输出数值证据。

## 9. Canvas 4.45 HDBSCAN 节点接入

本节为当前节点契约。第 8 节保留算法/公式依据及 4.44 内部阶段记录，其中“未接入/禁用”已由本节更新；
节点仍仅 BATCH，原始入口 Map 保留，输出是一张带原始有效观测和六个追加字段的有界表。

### 配置与兼容

新增可选 `hdbscan: SpatialHdbscanOptions | null`，四个字符串字段依次为
`probabilityColumnName`、`outlierColumnName`、`exemplarColumnName`、`stabilityColumnName`。
Java 保留原八/九参数构造器，缺失/null 不补对象。默认 DBSCAN 不创建 HDBSCAN 设置；
UI 首次确认切换或打开诊断弹窗时才提出字段名建议，不以来源字段自动覆盖用户配置。

- 任意非 null 对象（含 DBSCAN/MULTI_SCALE 下的非活动草稿）要求 4.45。
  保存端、GraphPlan、前端导入统一使用 `SPATIAL_HDBSCAN_OPTIONS_REQUIRE_SCHEMA_VERSION`。
- HDBSCAN 运行必须有该对象，否则 `SPATIAL_HDBSCAN_DIAGNOSTICS_REQUIRED`。
  四字段必填，与原表、簇/噪声及彼此按大小写不敏感判重；问题精确到 `configuration.hdbscan.<field>`。
  缺名/重名等业务草稿允许保存，数组等不可解析结构拒绝。
  保存端要求四字段为字符串；前端导入缺失/null 字段名会补为空字符串，直接提交 null 字段不是合法保存结构。
- 参数仍为 `{algorithm:'HDBSCAN',minimumFeatures:number}`，不增加半径、时间、隐式分组或分区参数。
  非 HDBSCAN 时保存但不执行诊断对象；HDBSCAN 时完全忽略 dbscan 的时间模式、字段、值与单位。
- 当前写出 4.45；节点引入版本仍为 4.19，Manifest/Result/HTTP API 不变，不增加生产依赖。

### 输入、执行与结果

- 与显式 DBSCAN 保持同一参与规则：逻辑 XY Point；实际 NULL/Empty 排除，其余非 Point、非法或非有限坐标失败，
  测地经纬范围显式检查。参与 featureId 非空且唯一，重复坐标保留为多个观测。
- 原列先全部投影到私有 `value_N` 别名，再生成并 Checkpoint 内部 ID；原列名是 id/src/dst/geometry/cluster
  也不会覆盖内部图列。图与诊断计算只接收私有投影，按同一内部 ID 回接全部有效原行。
- 三阶段为 HdbscanSpanningTree → HdbscanCondensedTree → HdbscanDiagnostics，最终结果 Checkpoint 后才传播。
  单个整体点集执行，不按任意分组独立计算再拼接；原理/公式与安全限制见第 8 节。
- 按用户配置追加 LONG 簇 ID、BOOLEAN 噪声、DOUBLE 概率、DOUBLE GLOSH、BOOLEAN 代表点、可空 DOUBLE 稳定性。
  噪声为 null/true/0/算法离群分数/false/null；不足最少要素时离群分数为 0，空参与集输出零行。
  原事件时间字段保留、Watermark 清空，隐藏 DBSCAN 时间不是 HDBSCAN 的筛选条件或血缘来源。
- Compiler 使用同一 Operator 校验配置，只生成零行集合依赖计划，所有六个追加字段依赖参与 Geometry/身份集合。
  不执行数据质量检查、聚类、Checkpoint 或 Spark Job。误将该预检表达式作为真实数据执行仍以安全错误拒绝。
- Runner 沿用统一生命周期、PROCESS 与三项 HDBSCAN 安全错误；摘要仅增加 diagnosticCount，并将非活动 dbscanMode
  记为 INACTIVE，不记录诊断值、成员或时间设置正文。

### 当前面板

```text
算法                 [ DBSCAN | HDBSCAN ]                  (?)
最少要素             [ 5 ]                                 (?)
诊断输出             已配置 4 项诊断                 [设置] (?)
簇 ID / 噪声字段      [ cluster_id ] [ is_noise ]
输出表               [ observations_clustered ]
```

以上名称/数值是展示示例，不是官方默认。距离方法与输入字段沿用公共面板；HDBSCAN 下不显示搜索半径或 Linear 时间行。
诊断 Modal 为 680px，四行各有字段名和就近帮助，说明概率、GLOSH、代表点及平台持久性规则。
修改采用独立草稿，取消不修改 Inspector，保存空名/重名仍可应用并继续，问题计数常驻显示。
算法切换需要确认；已保存的诊断和 dbscan 对象均跨切换保留，parameters 的非活动分支仅在本面板会话内保留。
MULTI_SCALE 仍只回显旧草稿并明确不支持。Canvas 显示 HDBSCAN、最少要素与诊断计数，不展示诊断结果或隐藏时间。

### 验证与剩余范围

- Contracts 2 项、Engine 96 项通过：`/tmp/datascalpel-hdbscan-node-corrected.log`。
  覆盖新对象往返/旧构造器、4.45 非活动对象门槛、原行与保留列、隐藏时间不生效、缺名/重名、
  NULL/Empty、无效实际点/身份、噪声与单点、零 Job 与六输出的 FIELD_COMPLETE 集合血缘，以及既有 DBSCAN 回归。
- 前端 15 项通过：`/tmp/datascalpel-hdbscan-ui.log`；TypeScript 和触及文件 ESLint 通过。
  覆盖导入门槛/结构、切换确认、隐藏草稿保留、诊断取消/无效保存、字段提示与卡片安全摘要。
- 补充 Runner 摘要 4 项、现有空间节点 84 项及重复 Contracts 2 项通过：`/tmp/datascalpel-hdbscan-summary-regression.log`。
  摘要实际验证 diagnosticCount=4、dbscanMode=INACTIVE 且无隐藏时间/诊断字段名；空间回归没有因 HDBSCAN 接入放宽其他节点能力。
- 第 12 节已移除显式初始完整图，但密集候选仍可能 O(n²)；深树迭代、Checkpoint 空间与真实规模验收未完成。
  归一化/重复点边界的官方数值对照、完整单位核对及浏览器全页面仍待收口，不将节点接入当成整条路线图已完成。

## 10. 精确商图压缩：减少生成树后续迭代的边数

本节保留先压缩稠密图的实现阶段；后续第 12 节已改为逐轮恢复割候选，不再物化初始完整图。
商图最小边归约继续用于当前轮候选，不能把历史完整图开销当作最新执行方式。

本项只优化内部执行，Canvas 仍为 4.45；不修改最少要素、核心距离、诊断、噪声或参与规则，
不增加配置、依赖、Manifest/Result/API 字段。初始核心距离和完整互达距离图仍是 O(n²)，不是空间候选优化已完成。

### 执行与正确性

1. 仍以完整互达距离图启动 Borůvka；每个分量选择全序 `(weight, src, dst)` 下的最小出边。
   选择改为 Spark `min(struct(weight, src, dst))` 聚合，可进行分区内局部归约；不改变同权边裁决。
2. 本轮森林合并后，按新的连通分量删除内部边。每对不同分量只保留全序最小的一条原始边，
   保留原始顶点端点，不将端点替换成分量编号。分量编号顺序不同不影响原始边序。
3. 压缩结果 Checkpoint 后进入下一轮；有 c 个分量时，最多保留 c(c−1)/2 条边。
   不向 Driver 收集边、成员或分量表；编译预检不执行本管道。

正确性依据：后续分量只会合并，不会拆分。被丢弃的平行边与其更小的保留边连接同一对当前分量，
对任意后续割，要么两者都跨割，要么两者都在割内。因此被丢弃边不可能成为后续割的全序最小边。
这个归约保持完整图的每轮最小候选和确定性 MST，不是“只保留 K 个近邻”的近似森林。

### 证据与剩余规模工作

- 专项加入多轮压缩与独立稠密 Kruskal 逐边对照；包含重复位置、零/同权边、反序分量标签，
  以及从完整图直接压缩和多次压缩得到相同候选的校验。测试执行记录见开发清单。
- 12 顶点、66 条边的构造图在三个给定分量下压缩为 3 条，继续合并后为 1 条；
  全合并为空图，完整图和压缩图的最小出边相同。这是边数/正确性证据，不是吞吐量基准。
- 初始稠密图的计算与 Checkpoint 仍需资源；新增商图也产生阶段 Checkpoint。
  本项不宣称整体内存、磁盘或耗时必然降低。初始空间候选召回、深层压缩树、失败清理和真实容量仍待完成。

## 11. 精确核心距离的近邻上界与半径恢复

内部新增 `HdbscanCoreDistances`，运行在已固定身份和 Point 的 Checkpoint 投影上；
旧完整点对 `HdbscanSpanningTree.plan` 保留为独立参照。无新增协议、配置、依赖或界面选项，仍为 Canvas 4.45。

### 算法和失败边界

1. 用 Sedona KNN 请求 k 个种子（可能包含自身），按私有来源/目标 ID 去重，排除自身。
   只有至少 k−1 个不同其他观测时才生成搜索上界；不按 Geometry 去重观测。
2. 上界为这些种子的最大真实距离：平面 Point 距离或 WGS84 Point 椭球距离。
   即使 KNN 的内部排名不是最终距离顺序，只要种子是足量的不同观测，该最大值也不会小于真实第 k−1 邻居距离。
3. 用带候选数值裕量的半径执行同一距离模型的 DWithin，恢复全部候选和边界平局（现行裕量见第 12 节），
   然后按真实 `(distance, targetId)` 排序求第 k−1 个其他观测距离。零半径也恢复全部同位置身份。
4. 没有足量种子的来源使用完整其他观测候选，不返回部分核心距离或静默减少 minimumFeatures。
   此回退只改变开销、不改变算法；Spark/连接器真正执行异常不被吞掉，也不以近似算法补结果。
5. 种子上界与核心距离分别 Checkpoint，固定来源与回退集合；核心表供互达距离图两端共用，
   不再让两个端点分支分别构造完整候选排序。样本数不足 k 时仍由原 MST 入口直接返回不足观测结果。

这些操作只在真实运行执行。Compiler 仍使用节点的零 Job Schema/集合血缘路径；内部计划构造也保持惰性。
候选与算法诊断不进入 Canvas、日志或新 HTTP 响应。距离无效沿用 `SPATIAL_HDBSCAN_NUMERIC_RANGE_INVALID`。

### 验证范围与剩余性能工作

- 对照完整点对：重复坐标、对称同距、跨日期线、南北极邻域、全球点集，以及 k 等于全部点数。
- 独立注入非最近但合法的上界、零上界和缺失种子，验证精确恢复/完整回退与空、不足观测；验证 Analyzer 零 Job。
- 256 点规则格网另检查候选数量及完整核心距离一致性；实际运行结果和专项数量登记在开发清单，
  不将小格网案例当作百万点或集群吞吐量证据。
- **本阶段 MST 初始互达距离图仍使用完整无序点对；后续由第 12 节替代**。本项减少核心距离排名候选及重复计算，
  不代表全链路摆脱 O(n²)。密集重复点、大 k、欠佳种子与回退仍可能形成大量候选。
- 下一步须按连通分量割的可行出边上界恢复全部可能最小候选，证明保留同一 MST，
  才能去掉初始完整互达距离图；不得直接把本节 KNN 种子作为 MST 的全部边。
  深树迭代、阶段 Checkpoint 空间/清理、官方数值及真实规模验收继续保留。

## 12. 逐轮精确割候选：移除运行时初始完整图

Canvas 仍为 4.45，配置/诊断不变。`HdbscanSpanningTree.run` 不再构建或物化初始完整互达距离图；
`plan` 保留为稠密小样例参照。核心距离、割搜索与层次均留在 Spark 表上，不收集全体点或树到 Driver。

### 每轮执行

1. 固定每点的原始私有身份、Geometry、核心距离和当前分量。每分量取最小私有 ID 作为一个搜索代表。
2. 所有点向代表集合查询两个 KNN 种子，排除同分量配对，用真实互达距离求每分量一个可行上界 U。
   代表点只用于界定搜索范围，不替代分量几何、不决定最终连接端点，也不把 KNN 图当作 MST。
3. 代表与种子上界各自 Checkpoint，先固定上界存在性，再为缺失分量补充：以本分量代表连接全局最小 ID 的
   两个不同分量代表，至少一个目标在分量外。只用两行锚点关系，不做全点对回退；保留足够宽的真实可行上界。
4. 对分量内每个核心距离不超过 U 的点，恢复距离不超过搜索半径的全部其他分量候选，
   再用目标核心距离和真实互达距离不超过 U 作最终过滤。边端点规范为原始 `src < dst`，恢复平局后去重。
5. 对本轮候选作分量对最小边归约，再选各分量全序 `(weight,src,dst)` 最小出边并合并森林。
   下一轮从完整点集重新计算上界/恢复候选，不能继续沿用上一轮缺失的候选集合。

正确性依据：分量 C 的可行出边上界 U 不小于该割的真实最小互达距离 w。
达到 w 的边两端核心距离及空间距离均不超过 w，因此该边必被 C 的有界搜索恢复。
完整恢复同权边后按原始端点全序选择，与稠密图的割最小边相同；Borůvka 的安全边和唯一全序 MST 因而保持。
不能只证明候选图连通或最终总权重相同，验收要比较逐边结果及原节点诊断。

### 数值、编译与开销边界

- 核心距离、上界及互达距离的反解顺序统一按原始私有 ID 排序，与稠密图原 `src < dst` 计算方向一致。
- DWithin 搜索半径仅为候选召回增加 `max(radius × 1e-12, 测地时 1e-6 米)` 并向上取相邻浮点数，
  溢出夹到 Double.MAX_VALUE；平面模式没有固定米制增量。用于缓解搜索方向/反解舍入差异，不是业务容差或 Esri 精度。
  排名和边权使用原距离；MST 最终仍要求 `weight <= U`，不把微小正距离吸附成零。
- 未引入编译 Action、用户参数或新错误契约；样本不足、Point/ID 校验和节点生命周期保持原规则。
  Inspector 帮助改为“精确半径候选恢复，候选过密仍可能 O(n²)”，不承诺无限容量。
- 候选密集、重复点、大核心距离或欠佳上界仍可产生 O(n²) 候选；多轮索引、Shuffle 与 Checkpoint 也有开销。
  不再显式物化完整图不等于已经证明整体耗时或磁盘占用降低，仍需真实规模、偏斜、深树和失败清理验收。

### 本地证据

- 逐分量最小边与稠密图比较，覆盖非代表成员形成最小边、同权/同位置、日期线与高纬点；
  缺失种子使用两锚点补齐上界仍得到同一割最小边。Analyzer 路径零 Job。
- 固定随机全球点集的完整 MST 与稠密 Kruskal 逐边相同；原分布式层次、诊断和节点用例继续回归。
- 256 点规则格网首轮候选少于 32,640 条完整无序边的 1/8，逐分量最小边相同；这只是局部候选数量证据。
- 补充微小正测地距离位于搜索裕量内但大于零上界的反例，要求最终只保留真实零距离边。
  测试数量、最终状态与日志统一见开发清单，不据这些小样例声明完整 ArcGIS 数值或集群容量通过。

## 13. 层次历史分批保存，避免逐轮重写全部结果

本项仍为 Canvas 4.45 的内部执行优化，树拆分轮次、簇身份、密度、选簇规则和公开输出不变。
不增加配置、依赖或新错误码，不改变 Compiler 零 Job 边界。

### 历史保存

- 新增 `HdbscanRowHistory`，为簇、退出事件、最终观测归属及诊断阶段的祖先归属分别保存批次。
  每批先 Checkpoint，之后仅在同层有另一批时合并并进到下一层，类似二进制进位。
- 有 B 个非空批次时，只保留 O(log B) 个活动 Dataset 句柄；一批记录最多参与 O(log B) 次层级合并，
  不再因后来每一轮新增少量记录就重写此前全部历史。完成时只合并这些层级并物化一次。
- 合并使用原 Schema 的 unionByName，不收集簇或观测到 Driver，也不在单个 Executor 组装全树。
  coalesce 以当前 Spark shuffle 分区配置为上限，避免 Union 的分区数随批次数累计；不承诺消除数据偏斜。
- 旧 Checkpoint 文件仍由应用级生命周期管理。清除层级句柄不等于文件立即删除，不能主动删除调用方或共享目录。
  本项减少历史重写，不代表成功/失败后的文件清理问题已经解决。

### 层次推进与空批次

- 每轮从已计算的子分量大小返回三个控制标量：下轮活动观测数、新簇数、退出观测数。
  验证活动与退出数量之和等于上一轮，并继续要求每轮真实退出或分叉。
- 不再为上述推进单独扫描全部 active 观测并再发起一次分叉存在性查询；没有新簇或新观测归属时不保存空批次。
- 最后没有活动观测时，不再计算下一轮空 active/edges Checkpoint。
- 本阶段的 EOM 自底向上完成表归并与扫描由第 14 节继续优化；层次最大值切割仍最坏随树深度迭代。
  两项优化均未把深树轮数降为对数，也没有以组内收集或截断树替代算法；这些性能问题继续待办。

### Spark 统计估计膨胀修复

首次运行 34 顶点长链时，测试 JVM 持续占用 CPU，线程栈定位到
`SizeInBytesOnlyStatsPlanVisitor.visitJoin → LogicalRDD.rewriteStatsAndConstraints → BigInteger.multiply`，
不是等待外部数据或聚类死锁。SQL Checkpoint 继承上游连接大小估计，连续迭代将估计值反复相乘；
即使真实表很小，Driver 也可能耗在巨大整数的估计计算上。

- 新增内部 `HdbscanCheckpoint.bind`：先完成可靠 SQL Checkpoint，再通过公开 JavaRDD/同一实际 Schema
  创建新的分布式 DataFrame，切断继承估计，不伪造行数、不修改全局 Spark 配置，不 collect 数据。
- 用于层次活动表/边、子分量/决策、分批历史，以及 EOM/祖先传播的循环状态边界。
  保留先 Checkpoint 再重建的顺序，不能先将非确定性源转 RDD 后重复执行来完成快照。
- 这只影响真实运行计划，不影响 Compiler 字段血缘或预检；仍需处理各阶段文件的应用级生命周期。
  新默认估计不是实测数据大小，不用于容量承诺；专项同时检查输出估计不继续增长为超大整数。

### 验证范围

- 批次测试覆盖跨多次进位合并、保留句柄数、空历史、旧快照独立、重复物化及分区上限。
  通过非确定性源表达式的累计计数，确认每个原始历史行只求值一次；合并不能重新读取它。
- 34 顶点递增权重链触发 33 次连续分支退出，逐项核对直接父簇、退出密度、质量守恒和四诊断，
  与独立内存层次算法对照；该小规模深链不是全局容量验收。
- 原 MST、切割候选、同权/零距离、分叉与 EOM、Compiler/节点回归继续覆盖，实际结果登记于开发清单。

## 14. EOM 完成历史与待决子簇分离

仍为 Canvas 4.45 内部执行优化，不增加公开参数、不改变四诊断公式，也不宣称已与 Esri 数值等价。
原 EOM 每轮按父簇汇总全部已完成簇，已被父簇消费的记录仍反复参与扫描与历史重写。
现在分别保存以下两类分布式关系：

- 完成历史：每簇的 `choose/best/descendantDeath` 追加到 `HdbscanRowHistory`，最后物化供祖先选择传播使用。
- 待决子簇：只保留父簇尚未完成的直接子簇贡献；父簇完成后移出工作集，但完成历史仍保留。

### 每轮处理与不变量

1. 从 pending 中找出没有未完成子簇的 ready 父簇。子簇就绪轮次不同不影响该条件。
2. 待决关系仅连接到本轮 ready 父簇，然后按父簇对原始 `best` 求和、对 `descendantDeath` 取最大值。
3. 非根簇在自身稳定质量大于或等于子簇总质量时选择自身；否则保留子簇最佳总质量。根簇始终不选。
4. 本轮结果追加到历史，从 pending 删除；将新增贡献和原待决关系一起筛到仍在 pending 的父簇。
5. pending 未减少时返回原有 `SPATIAL_HDBSCAN_TREE_INVALID`，不返回已经完成的局部结果。

每轮开始时，waiting 恰好包含“已经完成且父簇仍未完成”的直接子簇，一簇一条。
ready 的全部直接子簇已完成，因此上述求和没有遗漏；父簇完成后，其直接子簇不会再参与更高层聚合，
更高层只使用父簇自己的 `best`。这与完整树的自底向上递推一致。

不维护逐轮浮点累计小计，避免因先到/后到子簇被分批相加而改变公式；每个父簇对原始贡献执行一次 Spark 聚合。
这不承诺分布式浮点归约在任意分区布局下逐位一致。精确可表示的平局仍选择父簇，无限质量平局也不通过相减判断。

### 仍未解决的开销

- 每轮仍扫描 pending 和 waiting，深度仍决定轮数。非平衡树中早完成的旁支可能等待多轮，最坏工作量仍可为平方级。
- 历史按二进制批次保存，旧文件并不会随待决关系被消费而立即删除；不据此宣称 Checkpoint 清理或容量已验收。
- 祖先传播仍是分层连接。没有引入 Driver 全树收集、Executor 全树列表、树截断或近似选簇。

### 专项验证设计

- 20 层梳状树：旁支叶簇先就绪，主分支晚就绪；逐簇与独立递归 EOM 对照，覆盖父簇平局、子簇胜出和深层死亡密度传播。
- 无穷质量父子平局与根簇排除，结果不能变成 NaN；待决关系直接检查已消费记录移除、剩余贡献原值及快照独立。
- 包含可完成分支的环形残余返回稳定错误，不发布部分历史；空内部关系行为明确。
- 原压缩树、质量守恒和四诊断继续与独立层次实现对照。实际测试结果记于开发清单，不将测试设计等同于通过。

## 15. HDBSCAN 中间 Checkpoint 的显式归属与清理

本项为 Canvas 4.47 下的运行时生命周期修正，不升级协议，不改变聚类参数、MST、EOM 或四诊断公式。
第 13/14 节“清除句柄不删除文件”保留为此前阶段记录；现在节点完成/失败后对已知归属文件执行清理。

### 归属和交付

- `PointHdbscanSupport` 为一次节点运行创建 `HdbscanCheckpoint.Scope`，显式传入核心距离、割候选、
  MST、压缩树、分批历史和诊断阶段；没有全局/ThreadLocal 状态，不切换 SparkContext 的共享目录。
- 可靠 Checkpoint 分为 `Dataset.checkpoint(false)` 与该 RDD 的 `doCheckpoint()`：沿用 Spark 原来的
  materialization Action，只在两步之间使用 Spark 自身路径分配函数登记新 RDD 的文件归属。
  因而写入失败前已分配的目录也可清理；不增加 count、collect 或真实来源重读。
  这依赖当前固定 Spark 的内部执行 API，不进入 SDK/Contracts；依赖升级时必须重新验证。
- 内部 bind 仍先完成可靠 Checkpoint，再以 JavaRDD/实际 Schema 重建规划边界，保持旧统计估计隔离。
  文件清单只保存路径/配置和 Dataset 弱身份引用，不把所有旧执行计划强引用到节点结束。
- 最终结果先完成独立可靠 Checkpoint，再显式保留这一份结果，其余已登记中间文件在作用域关闭时删除。
  不提前删除仍被最终写入计划依赖的快照，不把返回惰性 Dataset 当作 materialization 完成。
  单独调用内部阶段时，只保留其返回表；整节点调用共享作用域，阶段输出仍属于可清理中间结果。

### GraphFrames 目录边界

- 初始生命周期专项发现：只清理自有 RDD 文件后，完整节点仍多出三个 `connected-components-*` 目录。
  这是库写出的迭代 Parquet，不会因 GraphFrames 结果 unpersist 自动删除。
- 只读取**成功返回的本次图结果**所引用的已分析文件根/RDD Checkpoint 元数据，不读取数据行，
  不做共享目录“运行前后差集”，不把同前缀文件一概视为本节点所有。
- 输入与图结果均经过 Analyzer 再比较。原始 logical() 可能仍是 UnresolvedDataSource，
  而其上方投影已解析成文件关系；混用这两个阶段会漏记外来输入依赖，安全反例必须覆盖。
- 先排除 vertices/edges 的全部存储依赖，再限定为当前 Spark Checkpoint 根直属的库专用目录或可靠 RDD 文件。
  若同目录包含输入依赖，也不接管其父目录。未知目录布局不猜测删除；库升级须重新验证。
- 已确认归属的图目录与其他中间快照一起在最终结果固定后释放。仍保留 GraphFrames 缓存的原有 finally/unpersist。

### 成功、失败与尚未完成的生命周期范围

| 情况 | 当前行为 |
| --- | --- |
| 节点成功 | 保留最终可重复读取的结果快照，清理已知中间文件；不删除上游、其他节点或共享根目录 |
| 自有 Checkpoint materialization 失败 | 已登记分配路径仍进入 finally 清理，主计算异常不被覆盖 |
| 图阶段完成后，后继阶段失败 | 清理此前已登记自有/库目录，不发布部分结果 |
| 清理遇到文件系统错误 | 最佳努力继续其他路径，仅记录失败数量，不输出路径、连接错误或凭据；不改写主结果/主异常 |
| GraphFrames 在返回结果前内部失败 | 尚无可靠归属证据的库目录不猜测删除，仍需应用级残留回收方案 |
| 进程强杀、存储不可达、迟到的失败任务写入 | 不承诺 finally 可执行或绝对无残留，需应用级清理/运维验收 |

本项减少节点结束后的遗留，不证明运行中磁盘峰值下降；深树迭代期间的阶段释放、最终输出的应用级回收、
不同 GraphFrames 运行配置及真实共享存储失败/容量仍在路线图范围内，不标记完整 Cluster 已完成。
正常清理不增加 UI 文案或公开参数；内部 `HDBSCAN_CHECKPOINT_CLEANUP_INCOMPLETE` 事件只包含失败数量。

专项检查：实际文件数量、最终结果多次读取/Shuffle、上游快照与外来同前缀目录保留、
外来 Parquet 输入排除、部分写入失败、图阶段之后失败和作用域隔离；深树/历史/四诊断回归结果见开发清单。
