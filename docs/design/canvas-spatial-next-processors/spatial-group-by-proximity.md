# Canvas Group By Proximity Processor 设计

## 1. 定位与 ArcGIS 对齐边界

`SPATIAL_GROUP_BY_PROXIMITY` 对齐 ArcGIS Enterprise 11.3 GeoAnalytics Server
Group By Proximity 的核心语义：在同一空间图层内按空间关系以及可选时间、属性关系建立无向邻接边，
再求连通分量。A 与 B 相邻、B 与 C 相邻时，即使 A 与 C 不直接相邻，三者仍属于同一组。

官方参考：[Group By Proximity](https://developers.arcgis.com/rest/services-reference/enterprise/geoanalytics/tasks/group-by-proximity/)。
官方输入支持 Point、Line、Polygon，空间关系为 Intersects、Touches、Near Planar、Near Geodesic；
可附加 Intersects/Near 时间关系和一项对称属性表达式。输出复制输入要素并新增 `group_id`，组号只表示
成员关系，不保证连续或重复运行一致。

DataScalpel 使用 Canvas 逻辑表代替服务 URL，并把 `outputName` 映射为 `outputTableName`；不复制
ArcGIS 的 Data Store、处理范围、processSR/outSR 或服务发布参数。处理/输出 CRS 继续由上游和下游
Spatial Transform 显式表达。

官方属性参数接受任意对称表达式。当前首版只开放两种受控、可校验的对称关系：同一字段值相等，以及
同一数值字段绝对差不超过阈值。它覆盖官方文档的区域同值和精度差示例，但不宣称已经覆盖任意 Arcade
表达式。

## 2. 配置契约

Canvas 4.72 新增：

```ts
type SpatialRelationship =
  | 'INTERSECTS'
  | 'TOUCHES'
  | 'NEAR_PLANAR'
  | 'NEAR_GEODESIC';

interface TemporalCondition {
  relationship: 'INTERSECTS' | 'NEAR';
  startColumnName: string;
  endColumnName: string | null;
  nearDistance: number | null;
  nearDistanceUnit:
    | 'MILLISECONDS' | 'SECONDS' | 'MINUTES' | 'HOURS'
    | 'DAYS' | 'WEEKS' | 'MONTHS' | 'YEARS' | null;
}

interface AttributeCondition {
  columnName: string;
  relationship: 'EQUALS' | 'ABSOLUTE_DIFFERENCE_AT_MOST';
  maximumDifference: number | null;
}

interface SpatialGroupByProximityConfiguration {
  sourceTableName: string;
  geometryColumnName: string;
  spatialRelationship: SpatialRelationship;
  spatialNearDistance: number | null;
  spatialNearDistanceUnit: SpatialDistanceUnit | null;
  temporalCondition: TemporalCondition | null;
  attributeConditions: AttributeCondition[];
  groupIdColumnName: string;
  outputTableName: string;
}
```

- `spatialNearDistance/spatialNearDistanceUnit` 仅在两种 Near 下活动；非 Near 下保留草稿但不参与计算。
- `temporalCondition=null` 表示不使用时间关系。开始字段单独使用时表示瞬时；同时选择结束字段时表示
  闭区间，实际开始晚于结束时运行稳定失败。
- 时间 Near 要求正整数。毫秒至周按固定时长；月、年按 Spark 会话时区中的日历区间推进。
  ArcGIS 没有公开月末和时区细节，因此该裁决仍需真实服务对照。
- `attributeConditions` 最多 8 项，全部条件与空间、时间关系按 AND 组合；同一字段在列表中不得重复。
- `groupIdColumnName` 与来源字段按大小写不敏感规则唯一；`outputTableName` 不得占用入口表名。
- 空字符串、缺失业务选择和空属性数组可保存草稿；非法 JSON 结构由严格 Parser 拒绝。

JSON 示例：

```json
{
  "sourceTableName": "gps_events",
  "geometryColumnName": "shape",
  "spatialRelationship": "NEAR_PLANAR",
  "spatialNearDistance": 1,
  "spatialNearDistanceUnit": "MILES",
  "temporalCondition": {
    "relationship": "NEAR",
    "startColumnName": "observed_at",
    "endColumnName": null,
    "nearDistance": 10,
    "nearDistanceUnit": "MINUTES"
  },
  "attributeConditions": [
    { "columnName": "region", "relationship": "EQUALS", "maximumDifference": null },
    {
      "columnName": "accuracy",
      "relationship": "ABSOLUTE_DIFFERENCE_AT_MOST",
      "maximumDifference": 2
    }
  ],
  "groupIdColumnName": "group_id",
  "outputTableName": "gps_groups"
}
```

## 3. 空间、时间与连通分组语义

- 节点仅支持 `BATCH`，来源必须为 `BOUNDED`，Geometry 必须有完整 CRS 且为 XY Point/MultiPoint、
  LineString/MultiLineString 或 Polygon/MultiPolygon。
- `INTERSECTS` 支持三类要素；`TOUCHES` 只支持 Line 和 Polygon 家族。
- `NEAR_PLANAR` 使用来源投影 CRS 中的二维真实 Geometry 最近距离，并按明确单位换算；地理 CRS
  不允许以角度近似，必须先显式投影。
- `NEAR_GEODESIC` 只支持 EPSG:4326 XY，使用现有 WGS84 Geometry 最近位置精确判断；
  `SOURCE_CRS_UNIT` 不可用。
- 时间 Intersects 在两个瞬时/闭区间重叠时成立；时间 Near 还允许两个区间之间的间隔不超过阈值。
  NULL 时间不与其他要素形成时间边；NULL 属性不满足同值或绝对差关系。
- 只有全部活动空间、时间和属性关系均满足的不同要素才形成边。随后使用分布式 Connected Components
  求传递闭包，不把两两匹配结果直接输出。
- NULL 或 Empty Geometry 不形成空间边，但仍作为单独顶点输出，因此孤立要素也有非空组 ID。
  非空但类型、有效性、有限坐标或 WGS84 范围不合法的 Geometry 稳定失败，不静默丢弃。
- 内部要素身份在来源计划 Checkpoint 后生成；组 ID 使用连通分量代表值，类型为非空 LONG。
  它不具有排序含义，也不是来源资产主键。

## 4. 输出与执行计划

- 每个来源要素恰好输出一行，全部原字段和顺序保持不变，末尾追加 `groupIdColumnName`。
- 来源表及其他入口表保持原 Map 顺序；新的有界结果表追加到末尾，不传播事件时间或 Watermark。
- Compiler 使用零行集合依赖计划构造 Schema；`group_id` 同时追溯 Geometry、活动时间字段和属性
  字段，但不执行 Checkpoint、自连接或 Connected Components。原字段保持直接血缘，全部输出均为
  `FIELD_COMPLETE`，不使用未知来源填充集合计算字段。
- Runner 在真实执行时先绑定内部身份和数据校验，再生成空间自连接边并运行 GraphFrames Connected
  Components。集群模式必须配置所有执行器可访问的 `spark.checkpoint.dir`；本地模式使用应用隔离的临时目录。
- 节点复用现有 Spark、Sedona、GraphFrames 和 WGS84 距离能力，不增加生产依赖、Manifest、Task Result
  或 HTTP API。

## 5. Inspector 与 Canvas UI

```text
┌ 按邻近分组 ────────────────────────────────────┐
│ 来源表 [gps_events      ]  Geometry [shape   ] │
│                                                │
│ 空间关系 [平面邻近 Near Planar              ?] │
│ 邻近距离 [1             ]  单位 [国际英里    ] │
│                                                │
│ 时间关系                                      ●│
│ [时间邻近] [observed_at] [结束：不选=瞬时]     │
│ [10] [分钟]                                    │
│                                                │
│ 属性关系 2 项                               [+] │
│ 1 region    [同值]                         ↑↓× │
│ 2 accuracy  [绝对差 ≤] [2]                ↑↓× │
│                                                │
│ 分组字段 [group_id]  输出表 [gps_groups      ] │
└────────────────────────────────────────────────┘
```

- 表和字段候选只来自 Compiler `inputTables`；失效值保留并标红，Compiler 不可用时不自行推测 Schema。
- 选择来源表时仅在相关字段为空时建议第一个合适的 XY Geometry 和 `{source}_groups` 输出名。
- 时间关系用紧凑开关控制；关闭不把时间字段写入配置，重新打开时保留当前 Inspector 会话中的草稿。
- 属性关系始终完整列出，支持添加、排序和删除；绝对差只推荐数值字段并显示阈值输入。
- 普通业务错误允许应用草稿。帮助入口解释传递闭包、投影/测地要求、时间单位和当前属性表达式边界。
- Canvas 卡片只显示来源到结果、空间关系、是否启用时间、属性关系数量、分组字段和“传递闭包”；
  不展示属性阈值、时间阈值对应的实际数据、坐标或任何字段值。

## 6. 稳定问题与安全摘要

主要稳定问题：

| 错误码 | 含义 |
| --- | --- |
| `SPATIAL_GROUP_GEOMETRY_UNSUPPORTED` | Schema 中的 Geometry 类型、CRS 或维度不支持 |
| `SPATIAL_GROUP_TOUCHES_GEOMETRY_UNSUPPORTED` | Point 使用了 Touches |
| `INVALID_SPATIAL_GROUP_NEAR_DISTANCE` | 空间 Near 距离不是有限正数 |
| `SPATIAL_GROUP_PROJECTED_CRS_REQUIRED` | Near Planar 未使用投影 CRS |
| `INVALID_SPATIAL_GROUP_TEMPORAL_NEAR_DISTANCE` | 时间 Near 缺少正整数阈值 |
| `SPATIAL_GROUP_TEMPORAL_TYPE_MISMATCH` | 时间开始和结束字段类型不同 |
| `SPATIAL_GROUP_ATTRIBUTE_CONDITION_COUNT_EXCEEDED` | 属性关系超过 8 项 |
| `DUPLICATE_SPATIAL_GROUP_ATTRIBUTE_COLUMN` | 同一属性字段重复配置 |
| `INVALID_SPATIAL_GROUP_ATTRIBUTE_DIFFERENCE` | 绝对差阈值非法 |
| `SPATIAL_GROUP_CHECKPOINT_NOT_CONFIGURED` | 集群运行缺少共享 Checkpoint 目录 |
| `SPATIAL_GROUP_GEOMETRY_INVALID` | 实际 Geometry 无效、类型不符或坐标非法 |
| `SPATIAL_GROUP_TEMPORAL_INTERVAL_INVALID` | 实际时间范围开始晚于结束 |

表、字段、类型、输出字段和输出表冲突继续复用 `TABLE_NOT_FOUND`、`COLUMN_NOT_FOUND`、
`TEMPORAL_COLUMN_REQUIRED`、`NUMERIC_COLUMN_REQUIRED`、`DUPLICATE_COLUMN_NAME` 与
`DUPLICATE_TABLE_NAME`。

Runner 摘要只记录来源/输出逻辑表、Geometry/组 ID 字段、空间关系、是否启用时间及属性条件数量；
不记录距离、时间阈值、属性阈值、属性值、坐标或数据行。节点归属 PROCESS 阶段。

## 7. 对齐与验收边界

2026-09-13 已完成当前实现收口：

- Spark 专项 3 项通过，覆盖 A–B–C 传递连通组、时间/属性 AND、孤立要素、输出行数、
  非空 LONG 组 ID 和入口 Map 顺序。
- Preview 输出完整集合血缘且分析阶段为零 Spark Job；20,000 个孤立 Point 实际输出
  20,000 行和 20,000 个非空独立组，执行路径使用 GraphFrames/Checkpoint 的分布式计算，
  不把顶点、边或分组成员收集到 Driver。
- 真实页面已验证四类空间关系、Near 距离/单位联动、时间关系、属性关系、无效草稿应用与
  紧凑问题详情；验收过程未保存任务定义。
- 规模运行中同时修正 Geometry 校验 UDF 捕获不可序列化 `GeometryTypeDefinition` 的问题；
  现仅捕获可序列化的 `GeometryKind`。

仍待真实 ArcGIS Enterprise 11.3 对照：Touches 容差与细小要素排除、月末/年末和时区行为、Near 的
边界包含规则、Geometry 数值容差、组成员关系及大规模/高偏斜连通图性能。当前不支持任意属性表达式、
处理范围或隐式 processSR/outSR；因此只能声明核心分组语义和已开放参数对齐，不能声明完整服务等价。
