# Canvas `SPATIAL_AGGREGATE` Processor 开发文档

## 1. 状态、目标与范围

- 实现状态：基础空间聚合已实现；Canvas `4.53` 增加可选 Dissolve 输出；Canvas `4.61`
  增加无字段空间连通组。
- 初始协议版本：Canvas `1.23`；Dissolve 选项：Canvas `4.53`；显式分组方式：Canvas `4.61`。
- 节点类型：`SPATIAL_AGGREGATE`，类别为 `PROCESSOR`，仅支持 `BATCH`。
- 图规则：至少一条入边，允许没有出边；多个上游表 Map 先执行无覆盖合并。

节点对一张有界逻辑表按普通标量字段分组，并执行 `UNION`、`INTERSECTION`、`COLLECT`、
`ENVELOPE` 空间聚合。4.53 的 Dissolve 不是新 Processor，而是单个 `UNION` 聚合的受控输出模式：
增加来源要素计数、ArcGIS 同类标量统计和 Multipart/Singlepart 选择。

Create Buffers 可通过节点组合表达：`GEOMETRY_BUFFER → SPATIAL_AGGREGATE`。不需要溶解时只使用
Buffer；Dissolve All 使用空分组；Dissolve List 使用一到多个分组字段。
完整 Dissolve Boundaries 的无字段行为使用显式 `CONNECTED_COMPONENTS`：相交、重叠或接触的面要素
通过传递闭包归为一组，不能与 Create Buffers 的全局 All 混用。

## 2. 稳定配置协议

```ts
interface SpatialAggregateConfiguration {
  sourceTableName: string;
  outputTableName: string;
  groupByColumns: string[];
  aggregations: SpatialAggregation[];
  dissolve?: SpatialAggregateDissolveOptions | null;
}

type SpatialAggregationKind = 'UNION' | 'INTERSECTION' | 'COLLECT' | 'ENVELOPE';

interface SpatialAggregation {
  kind: SpatialAggregationKind;
  geometryColumnName: string;
  outputColumnName: string;
}

interface SpatialAggregateDissolveOptions {
  enabled: boolean;
  multipart: boolean;
  countOutputColumnName: string;
  summaryStatistics: SpatialAggregateStatistic[];
  groupingMode?: 'ALL_OR_FIELDS' | 'CONNECTED_COMPONENTS' | null;
}

type SpatialAggregateStatisticKind =
  | 'COUNT_FIELD' | 'SUM' | 'MEAN' | 'MIN' | 'MAX'
  | 'RANGE' | 'STDDEV' | 'VARIANCE' | 'ANY';

interface SpatialAggregateStatistic {
  statisticId: string;
  kind: SpatialAggregateStatisticKind;
  sourceColumnName: string;
  outputColumnName: string;
}
```

Dissolve 示例：

```json
{
  "sourceTableName": "parcel_buffers",
  "outputTableName": "district_buffers",
  "groupByColumns": ["district_code"],
  "aggregations": [{
    "kind": "UNION",
    "geometryColumnName": "buffer_shape",
    "outputColumnName": "district_shape"
  }],
  "dissolve": {
    "enabled": true,
    "multipart": false,
    "countOutputColumnName": "feature_count",
    "summaryStatistics": [{
      "statisticId": "11111111-1111-4111-8111-111111111111",
      "kind": "SUM",
      "sourceColumnName": "population",
      "outputColumnName": "population_sum"
    }]
  }
}
```

兼容规则：

- 缺失或 `null` 的 `dissolve` 保持 4.52 及更早版本的基础空间聚合语义。
- 非 null `dissolve` 从 4.53 开始支持；`enabled=false` 时其余内容作为非活动草稿保留且不参与校验或执行。
- `groupingMode` 缺失/null 保持 4.53 的 `ALL_OR_FIELDS` 语义；任意显式值从 4.61 开始支持，
  包括 `enabled=false` 的非活动草稿。
- `summaryStatistics` 是有序数组，最多 32 项；`statisticId` 必须是节点内唯一 UUID。
- 新建节点默认 `dissolve=null`；首次启用时默认 `multipart=false`、计数字段 `feature_count`。

## 3. 基础空间聚合

| kind | Sedona API | 语义 |
| --- | --- | --- |
| `UNION` | `ST_Union_Agg` | 合并组内 Geometry 覆盖范围 |
| `INTERSECTION` | `ST_Intersection_Agg` | 计算组内所有 Geometry 的公共部分 |
| `COLLECT` | `ST_Collect_Agg` | 收集 Geometry，不执行拓扑融合 |
| `ENVELOPE` | `ST_Envelope_Agg` | 计算全部非空 Geometry 的总包络 |

基础模式中四种聚合均忽略 NULL；全 NULL 组输出 NULL。Empty 不是 NULL，按 Sedona/JTS 函数
本身处理：全 Empty 的 UNION 输出 Empty，INTERSECTION 遇 Empty 输出 Empty，COLLECT 保留集合
语义，ENVELOPE 忽略 Empty。所有非 NULL 结果通过 `ST_SetSRID` 恢复来源 EPSG。

## 4. Dissolve 语义与统计

启用 Dissolve 时必须恰好配置一个 `UNION` Geometry 聚合：

- `groupByColumns=[]` 对应 Create Buffers 的 `dissolveOption=All`，全部记录作为一个组。
- 非空分组对应 `dissolveOption=List + dissolveFields`，字段值相同的记录作为一组。
- `multipart=true` 使用 `ST_Multi`，每组最多输出一行。
- `multipart=false` 使用 `ST_Dump`，每个独立部件输出一行，并重复该组分组字段、计数和统计。
- UNION 结果为 NULL 或 Empty 的组不产生 Dissolve 输出要素；混合组的 `feature_count` 仍统计融合前组内全部来源行。
- `countOutputColumnName` 始终输出 LONG 的来源行总数；它不同于统计项 `COUNT_FIELD`。

4.61 的 `CONNECTED_COMPONENTS` 是另一种明确的无字段语义：

- 只允许 `groupByColumns=[]`，且单个 UNION 来源字段必须声明为 Polygon/MultiPolygon。
- NULL 和 Empty Geometry 不进入连通图，也不计入任何结果组；实际非面、无效 Geometry 或非有限 XY
  坐标在运行时以安全错误拒绝。
- 两个要素满足二维 `ST_Intersects` 时建立无向边，因此重叠、包含、共边或仅接触于一点均可连通；
  不增加隐藏距离或吸附容差。
- 分组使用传递闭包：A 与 B 相交、B 与 C 相交时，即使 A 与 C 不直接相交，三者仍属于同一组。
- 每个连通分量独立计算 UNION、来源要素计数及相同的标量统计；Multipart/Singlepart 行为不变。

统计在融合前按同一组来源记录计算：

| kind | 字段约束 | 结果 |
| --- | --- | --- |
| `COUNT_FIELD` | 任意非 Geometry 字段 | 非 NULL 数，LONG |
| `SUM` | 数值字段 | Spark `sum` |
| `MEAN` | 数值字段 | Spark `avg`，DOUBLE |
| `MIN` / `MAX` | 数值字段 | 最小值 / 最大值 |
| `RANGE` | 数值字段 | `max - min` |
| `STDDEV` | 数值字段 | 样本标准差 `stddev_samp` |
| `VARIANCE` | 数值字段 | 样本方差 `var_samp` |
| `ANY` | STRING | 任一非 NULL 字符串；跨重跑不保证选择相同记录 |

所有统计忽略来源字段的 NULL；单样本的样本标准差和方差为 NULL。节点不提供 DISTINCT、过滤统计、
自定义表达式或中位数。

## 5. ArcGIS 参数映射和边界

ArcGIS Enterprise GeoAnalytics Create Buffers 的相关参数映射如下：

| ArcGIS 参数 | DataScalpel 表达 |
| --- | --- |
| `distance` / `field` | `GEOMETRY_BUFFER` 的固定值 / 字段 / 受控表达式距离来源 |
| `method=Planar|Geodesic` | `GEOMETRY_BUFFER.mode=PLANAR|SPHEROID` |
| `dissolveOption=None` | 不连接 `SPATIAL_AGGREGATE` |
| `dissolveOption=All` | 启用 Dissolve 且 `groupByColumns=[]` |
| `dissolveOption=List` / `dissolveFields` | 启用 Dissolve 并配置分组字段 |
| `summaryFields` | `summaryStatistics` |
| `multipart` | `dissolve.multipart`，新建默认 false |

该组合不把 ArcGIS 未提供的负距离、cap/join style 当作对齐要求。4.61 的
`CONNECTED_COMPONENTS` 对应 Dissolve Boundaries 未指定 dissolve 字段时的空间连通组；原
`ALL_OR_FIELDS` 空分组继续对应 Create Buffers 的 All（全局 UNION）。两者显式分开，避免旧任务结果变化。
Esri 容差、官方服务数值和大规模执行对照仍未完成。

官方依据：[Create Buffers](https://developers.arcgis.com/rest/services-reference/enterprise/geoanalytics/tasks/create-buffers/)、
[Dissolve Boundaries](https://developers.arcgis.com/rest/services-reference/enterprise/geoanalytics/tasks/dissolve-boundaries/)。

## 6. Map、Schema 与执行

- 保留输入 Map 中全部表，并以 `outputTableName` 追加 BOUNDED 结果表；名称不得占用入口表名。
- 基础模式 Schema 为分组字段 → 空间聚合字段。
- Dissolve 模式 Schema 为分组字段 → UNION Geometry → 来源要素计数 → 有序统计字段。
- 分组字段保留来源平台类型、参数、nullable 和 comment，清空物理 origin/default/generated。
- Geometry 输出声明为 nullable 通用 `GEOMETRY`，继承来源 CRS/dimension；Singlepart 的实际行为是部件行，稳定类型仍保持通用 Geometry。
- 输出 `origin=null`，事件时间和 Watermark 清空；不承诺行顺序。
- Compiler 和 Runner 共用 `SpatialAggregateNodeOperator`。Compiler 只构造零行惰性计划并执行 Analyzer，不读取或计数真实数据。
- `CONNECTED_COMPONENTS` 运行时使用 Sedona 空间连接构造候选边，并通过仓内既有 GraphFrames 计算
  分布式连通分量；不把要素或边收集到 Driver。Compiler 不执行 Checkpoint、空间连接或图算法。
  集群 Runner 必须配置所有执行器可访问的 `spark.checkpoint.dir`。

## 7. 校验与稳定错误

除基础必填、表/字段、BOUNDED、Geometry 和 Analyzer 错误外，Dissolve 增加：

| 错误码 | 条件 |
| --- | --- |
| `SPATIAL_AGGREGATE_DISSOLVE_REQUIRE_SCHEMA_VERSION` | 低于 4.53 携带非 null Dissolve 对象 |
| `SPATIAL_DISSOLVE_GROUPING_MODE_REQUIRE_SCHEMA_VERSION` | 低于 4.61 携带非 null 分组方式 |
| `SPATIAL_DISSOLVE_REQUIRES_SINGLE_UNION` | 启用时不是恰好一个 UNION |
| `SPATIAL_DISSOLVE_CONNECTED_GROUP_FIELDS_NOT_ALLOWED` | 连通组模式仍配置分组字段 |
| `SPATIAL_DISSOLVE_CONNECTED_REQUIRES_POLYGON` | 连通组来源不是 Polygon/MultiPolygon |
| `SPATIAL_DISSOLVE_CHECKPOINT_NOT_CONFIGURED` | 集群运行缺少共享 Spark Checkpoint 目录 |
| `SPATIAL_DISSOLVE_GEOMETRY_INVALID` | 实际 Geometry 非面、无效或含非有限 XY 坐标 |
| `SPATIAL_DISSOLVE_STATISTIC_LIMIT_EXCEEDED` | 标量统计超过 32 项 |
| `INVALID_SPATIAL_DISSOLVE_STATISTIC_ID` | 统计 ID 不是 UUID |
| `DUPLICATE_SPATIAL_DISSOLVE_STATISTIC_ID` | 统计 ID 重复 |
| `INVALID_SPATIAL_DISSOLVE_STATISTIC_KIND` | 统计类型缺失或未知 |
| `NUMERIC_COLUMN_REQUIRED` / `STRING_COLUMN_REQUIRED` | 统计来源类型不适用 |
| `DUPLICATE_COLUMN_NAME` | 分组、Geometry、计数或统计输出名大小写不敏感冲突 |

错误路径精确定位到配置项。真实 Geometry、统计值、坐标和数据行不得进入错误或日志。

## 8. Inspector、Canvas 与安全摘要

- Inspector 在来源、输出、分组和空间聚合之后提供 Dissolve 开关。
- Dissolve 启用后选择“全部/按字段值”或“按相交或接触连通组”；连通组与已有分组字段冲突时
  保留字段草稿并就地标红，不静默清除。
- 启用后配置计数字段、Multipart/Singlepart 和有序标量统计；字段候选随统计类型过滤。
- 上游字段失效、类型不适用或输出重名时保留原值并标红，允许应用草稿。
- Canvas 卡片显示 Dissolve 分组方式（All/List 或空间连通组）、部件方式和统计数量，
  不显示 Geometry、坐标、组成员或统计值。
- Runner 安全摘要只记录来源/输出表、分组与聚合元数据、是否启用、部件方式和统计数量；禁用时不泄露隐藏草稿数量。
- 成功日志不为统计组数或输出部件数额外触发 Spark Action。

## 9. 验收与剩余范围

4.53 已覆盖协议往返、缺失/非活动兼容、三端版本门槛、All/List、Multipart/Singlepart、来源行计数、
九种统计、NULL/Empty 组、类型/UUID/数量/重复名校验、Analyzer 零 Job、Inspector、Canvas 和安全摘要。
4.61 进一步覆盖显式兼容门槛、无字段 Polygon 连通组、传递闭包、孤立要素、NULL/Empty 排除、
按分量统计、分布式图计算及 Compiler 零图作业。

仍不在当前范围：流式空间聚合、Esri 容差、官方服务数值与容量验收、自动修复/CRS 转换、
聚合分区参数、负 Buffer 和样式参数。
