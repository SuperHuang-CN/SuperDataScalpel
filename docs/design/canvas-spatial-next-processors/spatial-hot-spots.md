# Canvas Find Hot Spots Processor 设计

## 1. 定位与对齐边界

`SPATIAL_HOT_SPOTS` 对齐 ArcGIS GeoAnalytics Server Find Hot Spots 的核心点事件分析能力：
投影 Point 输入、规则方格、固定距离空间邻域、Getis-Ord Gi*、热点/冷点显著性分级和可选时间切片。
节点输出 Polygon 矢量格网，不依赖 Raster、H3、自动投影或外部统计服务。

官方参考：[Find Hot Spots (GeoAnalytics)](https://developers.arcgis.com/rest/services-reference/enterprise/geoanalytics/tasks/find-hot-spots/)。

当前默认以格网点数作为分析值。`FIELD_SUM` 是 DataScalpel 的显式扩展，用于先汇总数值字段再计算
Gi*。当前公式和边界规则按本文固定，尚未与真实 ArcGIS Enterprise 作业完成逐格网数值、边缘、
多重检验和大规模对照，因此“能力和参数方向对齐”不表示结果与 Esri 内部实现逐位相同。

## 2. 配置契约

Canvas 4.69 新增：

```ts
type SpatialHotSpotAnalysisSource = 'POINT_COUNT' | 'FIELD_SUM';
type SpatialHotSpotMultipleTesting = 'NONE' | 'FDR_BH';

interface SpatialHotSpotsConfiguration {
  sourceTableName: string;
  pointGeometryColumnName: string;
  analysisSource: SpatialHotSpotAnalysisSource | null;
  analysisColumnName: string | null;
  binSize: number;
  binSizeUnit: SpatialDistanceUnit;
  neighborhoodDistance: number;
  neighborhoodDistanceUnit: SpatialDistanceUnit;
  temporalSlicing: SpatialTemporalSlicing | null;
  multipleTesting: SpatialHotSpotMultipleTesting | null;
  outputTableName: string;
  binIdColumnName: string;
  binGeometryColumnName: string;
  pointCountColumnName: string;
  analysisValueColumnName: string;
  zScoreColumnName: string;
  pValueColumnName: string;
  adjustedPValueColumnName: string;
  confidenceBinColumnName: string;
}
```

`POINT_COUNT` 忽略并保留非活动的 `analysisColumnName` 草稿；`FIELD_SUM` 要求该字段存在且为数值类型。
所有输出字段名按大小写不敏感唯一。输出表必须是新的 Canvas 逻辑表，来源表和其他入口表保持不变。

## 3. 输入、格网与邻域

- 仅支持 `BATCH/BOUNDED`、带完整 CRS 元数据的投影 XY Point。
- NULL/Empty Point 不参与；来源没有有效点时输出空结果，不推测分析范围。
- 方格边长为 `binSize`，原点固定为 `(0, 0)`。
- 分析范围是全部有效点外包矩形覆盖的完整方格，因此输出包含没有观测值的零值格。
- `neighborhoodDistance` 以方格中心距离定义二元权重，包含当前格；边界格只使用分析范围内的邻居。
- 格网和邻域单位分别换算到来源投影 CRS 第一轴单位。地理 CRS、不可解析或非线性轴单位在编译期拒绝。
- 换算后的邻域距离必须严格大于方格边长，且二者比例最多为 64。
- 输出格网总数（包含所有时间片）最多 1,000,000；超过限制时在生成完整格网前安全失败。

点数始终输出。`FIELD_SUM` 中 NULL 数值贡献 0；NaN、Infinity 或聚合后非有限结果安全失败，
不把异常值当作 0 或静默丢弃。

## 4. Getis-Ord Gi* 与显著性

对一个时间片内的 `n` 个格网，令 `x_j` 为格网分析值、`w_ij` 为固定距离二元权重：

```text
mean = sum(x_j) / n
S = sqrt(sum(x_j²) / n - mean²)

Gi* =
  (sum(w_ij * x_j) - mean * sum(w_ij))
  /
  (S * sqrt((n * sum(w_ij) - sum(w_ij)²) / (n - 1)))
```

原始 `p-value` 是标准正态分布的双侧概率。`NONE` 直接以原始值分级；`FDR_BH` 在每个时间片内
独立执行 Benjamini-Hochberg，并输出调整后的 p-value。置信分级为：

| 调整依据 | 热点 | 冷点 |
| --- | ---: | ---: |
| `p <= 0.01` | `3` | `-3` |
| `p <= 0.05` | `2` | `-2` |
| `p <= 0.10` | `1` | `-1` |
| 其他 | `0` | `0` |

方向由 z-score 正负决定。`n < 2`、总体方差为 0 或分母为 0 时固定输出 `z=0`、`p=1`、
`adjusted p=1`、`bin=0`，不产生 NaN。

## 5. 时间切片、输出与执行

时间切片复用空间分析的左闭右开固定/日历窗口。每个时间片使用相同的全局空间范围生成完整方格，
Gi* 和 FDR 均在片内独立计算；NULL 时间或窗口间隙中的点不参与。

输出列顺序：

1. STRING 格网 ID；
2. 与来源 CRS 一致的 XY Polygon；
3. 可选窗口开始/结束 TIMESTAMP；
4. LONG 点数；
5. DOUBLE 分析值；
6. DOUBLE Gi* z-score；
7. DOUBLE 原始双侧 p-value；
8. DOUBLE 调整后 p-value；
9. INTEGER 置信分级 `-3..3`。

格网 ID 由算法、SRID、配置格网大小、格网索引和可选窗口开始时间确定。节点保留入口完整表 Map，
把热点结果表追加到末尾。Compiler 只构造零行或惰性 Spark 计划；Runner 才执行聚合、完整格网生成、
邻域统计和显著性计算。安全摘要只记录来源/输出表、分析来源、校正策略和是否切片，不记录坐标、
字段值、半径实际数据或时间内容。

## 6. Inspector 与 Canvas

Inspector 依次展示来源表与 Point、分析值、方格大小、空间邻域、多重检验、时间切片、输出表和结果字段。
时间切片与结果字段使用独立设置 Modal；上游失效值保留并标红，普通业务错误允许保存草稿。

Canvas 卡片只展示来源到结果的流向、Gi*、方格/邻域单位、点数或字段和、FDR 状态及是否切片，
不展示数据值、坐标或分析字段内容。

## 7. 尚未完成的验收

- ArcGIS Enterprise 逐格网 z-score、p-value、置信分级与边缘格对照；
- Enterprise 内部多重检验策略与当前显式 Benjamini-Hochberg 的结果对照；
- 米、国际/美国测量英尺等不同投影轴单位真值；
- 时间切片、极稀疏与极密集点集，以及接近 100 万格上限时的 Shuffle 和 Executor 内存规模；

这些验收完成前，不把当前实现描述为 ArcGIS Enterprise 数值完全一致。

## 8. 当前支持范围收口（2026-09-13，协议仍为 4.76）

- 当前明确承诺的热点分析范围已形成闭环：投影 XY Point、完整方格、固定距离二元邻域、
  `POINT_COUNT/FIELD_SUM`、Getis-Ord Gi*、原始双侧 p-value、`FDR_BH/NONE`、`-3..3`
  置信分级和固定/日历时间切片继续使用本文公式和边界。
- 点数、字段总量、FDR、无校正及时间切片的全部结果字段均达到 `FIELD_COMPLETE`，不存在
  `WRITTEN_UNKNOWN_SOURCE`。格网、点数和 Gi* 诊断字段追溯 Point Geometry；`FIELD_SUM`
  的分析值和诊断字段同时追溯实际数值字段；窗口字段追溯时间字段。
- 20,000 个规则稀疏投影点的 Preview 仅建立并分析 Catalyst 计划，提交 Spark Job 数为 0；
  `FIELD_SUM` 完成真实执行并输出 20,000 个格网。计划不含 `CollectLimit`、`collect_list`、
  Cartesian Product 或 Broadcast Nested Loop Join。该样例不等价于生产容量承诺。
- `SpatialHotSpotsNodeOperatorSparkTest` 3 项通过。真实页面已验证新增节点延迟编译、
  `POINT_COUNT/FIELD_SUM`、`FDR_BH/NONE`、时间切片、结果字段、无效草稿应用和紧凑问题详情；
  未保存任务定义。
- 当前收口不扩大第 7 节边界：Enterprise Gi* 与 FDR 逐格网数值、边缘规则、不同投影轴单位真值、
  稀疏/密集点集及接近格网上限时的生产容量仍开放，不声明 Esri 数值完全等价。
