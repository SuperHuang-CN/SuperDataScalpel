# Canvas Enrich From Multi-Variable Grid Processor 设计

## 1. 定位与 ArcGIS 对齐边界

`SPATIAL_ENRICH_FROM_GRID` 对齐 ArcGIS Enterprise 11.3 GeoAnalytics Server
Enrich From Multi-Variable Grid 的核心能力：把已有多变量格网的属性按空间相交关系回填到 Point 要素，
不重新计算最近距离、最近属性或汇总变量。

官方参考：[Enrich From Multi-Variable Grid](https://developers.arcgis.com/rest/services-reference/enterprise/geoanalytics/tasks/enrich-from-multi-variable-grid/)。
官方 REST 参数为 Point `inputFeatures`、由 Build Multi-Variable Grid 产生的 `gridLayer` 及可选
`enrichAttributes`；结果是带格网属性的新 Point 图层。

DataScalpel 使用 Canvas 逻辑表代替 Portal 图层 URL，不复制 `outputName`、Data Store 或服务发布参数。
为避免格网 Schema 后续增加字段时悄悄改变任务结果，当前必须显式选择丰富字段；这比 ArcGIS
“不传 enrichAttributes 时加入全部字段”的默认行为更严格。

## 2. 配置契约

Canvas 4.71 新增：

```ts
interface SpatialEnrichFromGridField {
  sourceColumnName: string;
  outputColumnName: string;
}

interface SpatialEnrichFromGridConfiguration {
  pointTableName: string;
  pointGeometryColumnName: string;
  gridTableName: string;
  gridGeometryColumnName: string;
  gridIdColumnName: string;
  enrichFields: SpatialEnrichFromGridField[];
  outputTableName: string;
}
```

- Point 表、格网表和输出表必须互不占用同一逻辑表名；结果始终创建新表。
- `enrichFields` 保持配置顺序，至少一项。来源字段在列表内不得重复，且只能选择格网中的非 Geometry
  标量字段。
- 输出字段名与 Point 原字段及其他丰富字段按大小写不敏感规则唯一；允许显式改名解决同名。
- `gridIdColumnName` 是已有格网的稳定格网 ID，用于共享边界或异常重叠时的确定性单格选择。
- 空字符串和空数组可保存为草稿；Compiler 返回精确字段路径的问题。

JSON 示例：

```json
{
  "pointTableName": "incidents",
  "pointGeometryColumnName": "shape",
  "gridTableName": "city_variable_grid",
  "gridGeometryColumnName": "bin_geometry",
  "gridIdColumnName": "bin_id",
  "enrichFields": [
    { "sourceColumnName": "nearest_hospital", "outputColumnName": "nearest_hospital" },
    { "sourceColumnName": "population_sum", "outputColumnName": "grid_population_sum" }
  ],
  "outputTableName": "enriched_incidents"
}
```

## 3. 输入、匹配与输出粒度

- 节点仅支持 `BATCH`，Point 表和格网表都必须为 `BOUNDED`。
- Point Geometry 必须是带完整 CRS 元数据的 XY Point；格网 Geometry 必须是 XY Polygon 或
  MultiPolygon。两侧 CRS 必须完全一致，不自动投影；需要变换时在上游显式使用 Spatial Transform。
- 使用 Point 与完整格网 Polygon 的 `INTERSECTS` 关系，与官方“features intersect the grid”一致。
- 每个输入 Point 恰好输出一行：
  - 命中一个格网时，回填该格网选定字段。
  - 未命中时仍保留 Point，全部丰富字段为 NULL。
  - 位于共享边界或异常重叠区域、命中多个格网时，按 `gridIdColumnName` 转为字符串后的升序选择第一格；
    再以 Geometry 和选定属性形成稳定次序。官方没有公开并列选择细节，因此这是 DataScalpel 的确定性规则。
- 匹配格网的 ID 为 NULL 时真实执行返回 `SPATIAL_ENRICH_GRID_ID_NULL`，不随机选择。
- 结果先保留 Point 的全部原字段及顺序，再按 `enrichFields` 顺序追加可空字段；字段类型和类型参数继承
  格网来源字段，自增、生成列和默认值标记清除。
- 来源 Point、格网及其他入口表保持原 Map 顺序，新的 `BOUNDED` 结果表追加到末尾；不传播事件时间或
  Watermark。

## 4. Inspector UI

Inspector 使用一个紧凑面板，不再打开第二个全屏资源选择器：

```text
┌ 从多变量格网丰富 ───────────────────────────────┐
│ Point 来源                                       │
│ [Point 表：incidents      ] [Geometry：shape   ] │
│                                                  │
│ 多变量格网                                      │
│ [格网表：city_variable_grid] [Geometry：bin_geometry]
│ [格网唯一标识：bin_id                         ?] │
│                                                  │
│ 丰富字段                         [添加全部属性] [+]│
│ 1  nearest_hospital  → nearest_hospital      ↑↓× │
│ 2  population_sum    → grid_population_sum   ↑↓× │
│                                                  │
│ 输出表 [enriched_incidents                     ] │
│ 未命中 Point 保留，丰富字段为 NULL               │
└──────────────────────────────────────────────────┘
```

交互规则：

- 表和字段候选只来自 Compiler `inputTables`；Compiler 暂不可用时显示等待，不自行推导 Schema。
- 选择 Point/格网表时，仅在对应字段为空时建议唯一合适的 Geometry、`bin_id/grid_id/id` 和输出表名；
  已有值即使失效也保留并标红。
- “添加全部属性”只加入当前尚未选择的非 Geometry 字段，并排除格网 ID；不会覆盖或重排已有项。
- 新字段默认保持原名；与 Point 原字段或已配置输出重名时建议 `完整格网表名_字段名`。建议后仍冲突时由
  用户处理，不自动追加序号。
- 字段支持添加、删除、改名和排序。普通业务错误允许应用草稿；结构无法安全解析时拒绝导入。
- 帮助 Tooltip 说明边界点只保留一格以及格网 ID 的用途；Canvas 卡片只预览前两项映射。

## 5. 校验与安全摘要

主要稳定错误：

| 错误码 | 含义 |
| --- | --- |
| `INVALID_SPATIAL_ENRICH_GRID_TABLES` | Point 表与格网表相同 |
| `SPATIAL_ENRICH_POINT_GEOMETRY_REQUIRED` | Point Geometry 类型、维度或元数据不满足要求 |
| `SPATIAL_ENRICH_GRID_GEOMETRY_REQUIRED` | 格网 Geometry 不是 XY Polygon 家族 |
| `SPATIAL_ENRICH_GRID_CRS_MISMATCH` | 两侧 CRS 不同 |
| `INVALID_SPATIAL_ENRICH_FIELDS` | 丰富字段不是数组 |
| `EMPTY_SPATIAL_ENRICH_FIELDS` | 未选择任何丰富字段 |
| `DUPLICATE_SPATIAL_ENRICH_SOURCE_COLUMN` | 同一格网字段重复配置 |
| `SPATIAL_ENRICH_GRID_ID_NULL` | 实际命中的格网缺少稳定 ID |

表、字段不存在、标量字段和输出名冲突继续复用 `TABLE_NOT_FOUND`、`COLUMN_NOT_FOUND`、
`SCALAR_COLUMN_REQUIRED`、`DUPLICATE_COLUMN_NAME` 与 `DUPLICATE_TABLE_NAME`。

Runner 摘要只记录两张逻辑表、Geometry/格网 ID 字段名、丰富字段数量和输出表；不记录格网属性值、
Point 数据、坐标或任何数据值。节点属于 PROCESS 阶段，不增加 Manifest、Task Result、HTTP API、
Spark Action、缓存或生产依赖。

## 6. 验证边界

专项至少覆盖：单格命中、共享边界按格网 ID 选择、未匹配 Point 保留、输入表继续传播、字段顺序与
nullable Schema、来源/结果字段重名、重复来源字段、CRS/Geometry/有界性错误、4.71 版本门槛、
Canvas 卡片和安全摘要。

当前实现的接口和粒度与官方文档一致，但尚未使用真实 ArcGIS Enterprise 对边界点、异常重叠格网、
字段类型细节、执行计划和大规模性能进行对照，不能宣称内部并列选择或数值实现与 Esri 完全一致。

## 7. 当前支持范围收口（2026-09-13，协议仍为 4.76）

- 当前明确承诺的格网丰富范围已形成闭环：有界 XY Point、同 CRS Polygon/MultiPolygon 格网、显式字段
  选择/改名、未命中保留，以及共享边界或异常重叠时按格网 ID 和稳定后备字段选择唯一格网。
- Point 原字段及 Geometry 保持直接来源，所有丰富字段追溯其格网来源字段；结果达到
  `FIELD_COMPLETE`，不存在 `WRITTEN_UNKNOWN_SOURCE`。计划内 Point 行身份显式标记为技术列，
  不作为未知业务来源字段。
- 保留未命中 Point 的执行计划改为“索引化空间 INNER JOIN 找真实命中 → 每 Point 稳定排序取一格 →
  等值 LEFT JOIN 回接原 Point”，避免空间 LEFT OUTER JOIN 退化为 Broadcast Nested Loop Join。
- 20,000 个 Point 与 20,000 个一一对应格网的 Preview 仅构造并分析 Catalyst 计划，提交 Spark Job 数为 0；
  真实执行输出 20,000 行，丰富字段汇总值与构造真值一致。计划不含 Driver 收集、`CollectLimit`、
  `collect_list`、Cartesian Product 或 Broadcast Nested Loop Join。该样例不等价于生产容量承诺。
- `SpatialEnrichFromGridNodeOperatorSparkTest` 3 项通过。真实页面已验证新增节点延迟编译、Point/格网双来源、
  格网 ID 邻近帮助、丰富字段紧凑编辑、无效草稿应用、问题入口/详情和 Canvas 安全摘要；未保存任务定义。
- 当前收口不声明 Enterprise 共享边界/异常重叠选择、字段类型细节、服务输出或生产容量完全等价；
  这些真实 ArcGIS Enterprise 对照仍开放。
