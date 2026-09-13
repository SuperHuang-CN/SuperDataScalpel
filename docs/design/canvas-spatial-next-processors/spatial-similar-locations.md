# Canvas Find Similar Locations Processor 设计

## 1. 定位与官方边界

`SPATIAL_SIMILAR_LOCATIONS` 对齐 ArcGIS Enterprise 11.3 GeoAnalytics Server
Find Similar Locations 的核心属性匹配语义：以一个或多个参考位置的属性为目标，
从候选位置中返回最相似、最不相似或两端结果。

官方参考：

- [GeoAnalytics Find Similar Locations](https://developers.arcgis.com/rest/services-reference/enterprise/geoanalytics/tasks/find-similar-locations/)
- [ArcGIS Pro Similarity Search](https://pro.arcgis.com/en/pro-app/latest/tool-reference/spatial-statistics/similarity-search.htm)
- [How Similarity Search works](https://pro.arcgis.com/en/pro-app/latest/tool-reference/spatial-statistics/how-similarity-search-works.htm)

首版只实现 GeoAnalytics REST 的 `ATTRIBUTE_VALUES` 和 `ATTRIBUTE_PROFILES`。ArcGIS Pro 的
`RANKED_ATTRIBUTE_VALUES`、`scaleData` 和 Collapse Output 不属于本节点当前契约；不得把它们描述成
隐藏默认或已实现能力。Canvas 逻辑表代替 ArcGIS 图层 URL，CRS 转换由上游空间转换节点显式完成。

## 2. Canvas 4.75 配置

```ts
interface SpatialSimilarLocationsAnalysisField {
  columnName: string;
  outputColumnName: string;
}

interface SpatialSimilarLocationsAppendField {
  sourceColumnName: string;
  outputColumnName: string;
}

interface SpatialSimilarLocationsConfiguration {
  referenceTableName: string;
  referenceIdColumnName: string;
  referenceGeometryColumnName: string;
  referenceFilter: CanvasFilterCondition | null;
  candidateTableName: string;
  candidateIdColumnName: string;
  candidateGeometryColumnName: string;
  candidateFilter: CanvasFilterCondition | null;
  analysisFields: SpatialSimilarLocationsAnalysisField[];
  appendFields: SpatialSimilarLocationsAppendField[];
  matchMethod: 'ATTRIBUTE_VALUES' | 'ATTRIBUTE_PROFILES' | null;
  resultMode: 'MOST_SIMILAR' | 'LEAST_SIMILAR' | 'BOTH' | null;
  numberOfResults: number;
  outputTableName: string;
  outputGeometryColumnName: string;
  locationTypeColumnName: string;
  similarityRankColumnName: string;
  dissimilarityRankColumnName: string;
  similarityIndexColumnName: string;
  cosineIndexColumnName: string;
  labelRankColumnName: string;
  referenceIdOutputColumnName: string;
  searchIdOutputColumnName: string;
}
```

- 分析字段最少 1 项、最多 32 项；属性轮廓至少 2 项。字段必须在两表中同名、同数值类型，
  DECIMAL 的精度和小数位也必须一致。
- 附加字段最多 64 项，只能来自候选表且不能是 Geometry；它们不参与计算。
- 每侧 ID 不能是 Geometry/BINARY，真实执行时必须非空且唯一；候选 ID 同时用于并列排序。
- 每端结果数为 1～10000。`BOTH` 表示两端各取该数量，但候选不足时会缩小为
  `min(numberOfResults, floor(candidateCount / 2))`，保证两端不重叠。
- 两侧 Geometry 必须为同 Kind、同 CRS、同维度的 XY Geometry。当前不要求 Point，
  Geometry 只随结果返回，不参与相似度公式。
- 空值和未完成业务选择允许作为草稿保存；数组或判别值结构不安全时由协议 Parser 拒绝。

## 3. 参与行和标准化总体

执行顺序固定为：

1. 分别应用参考和候选 Filter。
2. 跳过 Geometry 为 NULL 或 Empty 的行。
3. 验证两端实际 ID 非空且唯一。
4. 对所有分析字段验证实际值不是 NULL、NaN 或 Infinity。
5. 将剩余参考与候选行合并为同一个标准化总体，对每个字段计算 `avg` 和 `stddev_pop`。
6. 参考位置逐字段标准化后取平均，形成唯一目标向量。
7. 对每个候选计算分数并排名。

标准差为 0 时该字段的标准化值固定为 0，不做除零。没有参考行时运行失败；没有候选行时仍可输出
参考位置，但没有候选排名结果。Compiler 只基于 Schema 构造惰性计划，不扫描真实 ID、值或行数。

## 4. 两种匹配方法

`ATTRIBUTE_VALUES` 使用标准化平方差之和：

```text
simindex(candidate) = Σ (candidateZ[i] - referenceTargetZ[i])²
```

值为 0 表示全部分析字段与参考目标完全一致，值越小越相似。

`ATTRIBUTE_PROFILES` 比较标准化向量的形状：

```text
cosimindex(candidate) = 1 - cosine(candidateZ, referenceTargetZ)
```

范围为 0～2，0 表示轮廓方向完全一致，2 表示完全相反。GeoAnalytics REST 的
`cosimindex` 是余弦差异，因此是 **0 最相似**；ArcGIS Pro 说明中的原始 cosine 是 **1 最相似**。
两者不能混称。候选或参考目标形成零向量时属性轮廓无法定义，运行稳定失败。

## 5. 排名、输出与 Map 传播

- `simrank` 从 1 开始，分数升序；`dsimrank` 从 -1 开始，分数降序。
- 分数相同按候选 ID 的字符串形式升序，不依赖分区、输入顺序或 Shuffle 输出顺序。
- 结果始终包含筛选后的全部参考位置；参考行两个排名、索引和 `labelrank` 固定为 0。
- 候选行根据返回范围选择。`labelrank` 对最相似候选为正数，对最不相似候选为负数，便于渲染。
- 非当前匹配方法的索引字段输出 NULL：属性值方法只填写 `simindex`，属性轮廓只填写 `cosimindex`。
- 结果依次输出 Geometry、位置类型、参考/候选 ID、分析字段、附加字段、两种排名、两种索引和
  `labelrank`。默认沿用官方紧凑名 `simrank/dsimrank/simindex/cosimindex/labelrank/referenceid/searchid`，
  但全部名称可显式修改且必须大小写不敏感唯一。
- 参考表、候选表和其他入口表保持原 Map 顺序，新的 `BOUNDED` 结果表追加到末尾；不传播 Watermark。

## 6. Inspector 与 Canvas UI

```text
┌ 查找相似位置 ─────────────────────────────┐
│ 参考位置                         筛选 [开] [设置] │
│ 参考表 [reference_places]  唯一 ID [id]         │
│ Geometry [shape · POLYGON · EPSG:3857]          │
│                                                   │
│ 候选位置                         筛选 [开] [设置] │
│ 候选表 [candidate_places]  唯一 ID [id]         │
│ Geometry [shape · POLYGON · EPSG:3857]          │
│                                                   │
│ 分析字段 2                                        │
│ 1 population  → population                  ↑ ↓ × │
│ 2 income      → income                      ↑ ↓ × │
│ [+ 添加分析字段]                                  │
│                                                   │
│ 候选附加字段 1                                    │
│ 1 name        → candidate_name              ↑ ↓ × │
│ [+ 添加附加字段]                                  │
│                                                   │
│ 匹配方法 [属性值]       返回范围 [两端]            │
│ 每端结果数 [10]                                   │
│ 结果 · 12 个字段                    [设置结果字段] │
│ 输出表 [similar_locations]                        │
└───────────────────────────────────┘
```

- 表和字段候选只来自 Compiler `inputTables`；失效值保留并由编译问题就地标红，不自动清空。
- 分析字段选择器只建议两表共有的同名同类型数值字段；Compiler 仍是权威校验边界。
- Filter 使用已有受控条件树 Modal。主面板只显示是否启用和条件数；Canvas 卡片及安全摘要不显示条件内容。
- 九个固定结果字段放入约 680px Modal；普通业务错误不阻止应用草稿。
- Canvas 卡片显示“参考表 + 候选表 → 输出表”、匹配方法、返回范围、每端数量、分析/附加字段计数；
  不显示筛选字面量、分析数据值、Geometry 坐标或候选 ID。

## 7. 稳定失败和安全摘要

配置校验复用表、字段、Geometry、CRS、表名和字段名公共问题，并增加分析字段数量、方法、结果数和
类型不匹配问题。真实数据失败使用：

| 错误码 | 含义 |
| --- | --- |
| `SPATIAL_SIMILAR_LOCATIONS_REFERENCE_ID_INVALID` | 参考 ID 为 NULL |
| `SPATIAL_SIMILAR_LOCATIONS_REFERENCE_ID_DUPLICATE` | 参考 ID 重复 |
| `SPATIAL_SIMILAR_LOCATIONS_CANDIDATE_ID_INVALID` | 候选 ID 为 NULL |
| `SPATIAL_SIMILAR_LOCATIONS_CANDIDATE_ID_DUPLICATE` | 候选 ID 重复 |
| `SPATIAL_SIMILAR_LOCATIONS_REFERENCE_REQUIRED` | 筛选和 Geometry 排除后没有参考位置 |
| `SPATIAL_SIMILAR_LOCATIONS_VALUE_INVALID` | 分析值为 NULL、NaN 或 Infinity |
| `SPATIAL_SIMILAR_LOCATIONS_ZERO_PROFILE` | 属性轮廓的参考或候选向量为零 |
| `SPATIAL_SIMILAR_LOCATIONS_SCORE_INVALID` | 计算结果为非有限数值 |

Runner 安全摘要只记录参考/候选/结果逻辑表名、分析字段数、附加字段数、方法、返回范围和每端数量；
不记录 Filter、属性值、ID 值、Geometry、坐标或数据行。节点执行阶段为 `PROCESS`。

## 8. 已验证范围与开放项

本地专项覆盖 Canvas 4.75 Contracts/Parser、Registry/Runner 接入、多参考平均、参考+候选全集标准化、
属性值、属性轮廓、`BOTH` 不重叠、最不相似结果的负 `labelrank`、逐侧筛选、无参考位置稳定失败、
输出 Schema/Map 顺序和重复候选 ID 运行失败。4.76 收口还验证了：

- Compiler Preview 使用零行集合依赖计划，不构造标准化、全局排名或真实结果计划；Geometry、位置类型、
  参考/候选 ID、分析/附加字段、排名、索引和 `labelrank` 均追溯到真实参考/候选字段，输出达到
  `FIELD_COMPLETE` 且不存在未知来源，分析阶段不提交 Spark Job。
- 20,000 个候选按属性值完成分布式标准化和排名，稳定返回 10,000 个最相似候选及参考行；
  Engine `SpatialSimilarLocationsNodeOperatorSparkTest` 7 项通过。
- 真实页面已验证默认紧凑 Inspector、分析字段和附加字段行、九个固定结果字段 Modal、无效草稿应用
  及紧凑问题详情；验收定义未保存。由于验收节点没有接入上游，依赖有效表 Schema 的筛选 Modal
  和字段候选仍由已有组件测试及 Engine 筛选样例覆盖。

尚未完成真实 ArcGIS Enterprise 11.3 服务逐值对照、并列与空候选边界、所有 Geometry Kind、浏览器
带真实上游 Schema 的完整筛选交互以及生产容量验收。当前实现不得宣称与 Esri 数值完全等价；特别是
`BOTH` 的候选不足调整与候选 ID 并列顺序属于 DataScalpel 的固定确定性规则。
