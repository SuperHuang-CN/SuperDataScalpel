# Canvas `SPATIAL_CLIP` Processor 开发文档

## 1. 状态、目标与范围

- 实现状态：基础裁剪已实现；Canvas `4.51` 增加显式来源家族二维输出，`4.77` 增加逐来源合并相交 Mask。ArcGIS 容差、官方数值与生产容量对照仍待验收。
- 初始协议版本：Canvas `1.23`；显式几何策略：Canvas `4.51`；多 Mask 组合：Canvas `4.77`。
- 节点类型：`SPATIAL_CLIP`。
- 节点类别：`PROCESSOR`。
- 执行模式：仅 `BATCH`。
- 图规则：至少一条入边，允许没有出边并给出未消费警告；来源表与 Mask 表从合并后的表 Map 选择。

`SPATIAL_CLIP` 使用一张 Mask 表中的 Polygon/MultiPolygon 裁剪来源表 Geometry。节点保留
来源表全部属性并追加裁剪结果，不把 Mask 属性带入输出。

节点采用 INNER 裁剪语义：只有与 Mask 相交且能够产生非空交集的来源行才进入结果。新建节点默认
对每条来源要素合并全部相交 Mask 后只裁剪一次；旧定义和显式逐 Mask 模式仍按每条 Mask 分别输出，
不保证输出行顺序。

## 2. 稳定配置协议

```ts
interface SpatialClipConfiguration {
  sourceTableName: string;
  maskTableName: string;
  outputTableName: string;
  sourceGeometryColumnName: string;
  maskGeometryColumnName: string;
  outputColumnName: string;
  geometryPolicy?: 'SOURCE_FAMILY_2D' | 'LEGACY_ANY_DIMENSION' | null;
  maskCombination?: 'DISSOLVE_ALL' | 'PAIRWISE' | null;
}
```

示例：

```json
{
  "sourceTableName": "roads",
  "maskTableName": "districts",
  "outputTableName": "district_roads",
  "sourceGeometryColumnName": "centerline",
  "maskGeometryColumnName": "boundary",
  "outputColumnName": "clipped_centerline",
  "geometryPolicy": "SOURCE_FAMILY_2D",
  "maskCombination": "DISSOLVE_ALL"
}
```

协议限制：

- 来源表、Mask 表、输出表和三个字段名全部必填。
- 来源表与 Mask 表不能相同；节点从全部直接上游无覆盖合并后的表 Map 中精确选择两张逻辑表。
- 两个空间字段都必须有完整 Geometry 定义，并且仅支持 `EPSG + XY`。
- 两侧 CRS、坐标维度必须完全一致；不执行隐式 `ST_Transform`。
- Mask 字段的 GeometryKind 只允许 `POLYGON` 或 `MULTIPOLYGON`。
- `SOURCE_FAMILY_2D` 只接受明确的 Point/MultiPoint、LineString/MultiLineString、
  Polygon/MultiPolygon 来源；通用 `GEOMETRY` 和 `GEOMETRYCOLLECTION` 必须先明确家族。
- `LEGACY_ANY_DIMENSION` 以及缺失/null 策略继续接受现有全部 GeometryKind，保持旧结果语义。
- `DISSOLVE_ALL` 对每条来源要素只合并与它相交的 Mask；重叠 Mask 不会重复输出覆盖区域，
  分离 Mask 片段保留在同一个 Multi Geometry 中。新建节点默认使用该模式。
- `PAIRWISE` 让每条 Mask 独立裁剪；缺失/null 保持这一旧版语义，读取旧定义不会改变行数。
- 输出字段不能与来源表已有字段重名。
- 配置不保存 Mask 属性、空间值、任意谓词、SQL、容差或精度参数。

## 3. 裁剪与行语义

Operator 先以空间 INNER Join 缩小候选集，再按 `maskCombination` 选择计划：

1. 以 `ST_Intersects(sourceGeometry, maskGeometry)` 执行 INNER Join，缩小交集计算候选集。
2. `PAIRWISE` 对每对候选直接执行 `ST_Intersection(sourceGeometry, maskGeometry)`。
3. `DISSOLVE_ALL` 为来源 Dataset 增加不暴露给 Canvas 的计划内行 ID，按该 ID 对相交 Mask 执行
   `ST_Union_Agg`，再通过等值 Join 回接来源行并执行一次 `ST_Intersection`。行 ID 不由来源业务字段
   推断，因此内容完全相同的两条来源记录仍分别保留；Mask 不在 Driver 收集或全局物化。
4. 来源家族模式对参与计算的两侧表达式执行 `ST_Force2D`，再用
   `ST_CollectionExtract + ST_Multi` 只保留来源家族；旧版策略不做家族提取。
5. 用 `ST_SetSRID` 显式恢复来源 EPSG code。
6. 过滤 NULL 或 `ST_IsEmpty` 的交集结果。
7. 只投影来源表原字段，并把裁剪 Geometry 追加到末尾；来源 Geometry 属性本身不被覆盖。

因此：

- NULL 或 Empty 来源 Geometry 不匹配，不产生输出行。
- NULL 或 Empty Mask Geometry 不匹配，不产生输出行。
- 未命中任何 Mask 的来源行不进入输出。
- `PAIRWISE` 中一个来源行命中 N 条 Mask，最多产生 N 条输出。
- `DISSOLVE_ALL` 中一个来源行只产生零或一条输出；重叠 Mask 的共同区域只保留一次，分离片段保持
  在一个 Multi Geometry 中。它不按来源属性 DISTINCT，重复来源记录不会合并。
- 来源家族模式过滤边界接触产生的低维片段，点、线、面分别输出
  `MULTIPOINT/MULTILINESTRING/MULTIPOLYGON + XY`。
- 旧版策略继续保留 Point/LineString 等低维相交结果，输出 kind 声明为通用 `GEOMETRY`。
- 非法 Geometry、拓扑异常或真实数据计算失败时任务失败；不置 NULL、不跳过错误行。
- Compiler 只构造零行 Spark 计划，不读取真实 Geometry，也不估计匹配数量。

## 4. Map、Schema 与有界性

Map 规则：

- 保留输入 Map 中全部表。
- 以 `outputTableName` 追加裁剪结果表。
- 输出表名与任一输入表同名时返回 `DUPLICATE_TABLE_NAME`。

Schema 规则：

- 来源字段顺序、类型和字段元数据完整保留。
- Mask 表字段不进入输出。
- 结果字段追加到末尾，平台类型为 `GEOMETRY`。来源家族模式的 kind 为对应 Multi 家族且
  dimension 为 `XY`；旧版策略的 kind 为 `GEOMETRY` 且 dimension 继承来源字段。
- 结果 CRS 始终继承来源 Geometry 定义。
- 结果字段为非 nullable；NULL 和 Empty 结果已被 INNER 语义过滤。
- 输出表 `origin=null`、`datasetKind=BOUNDED`，事件时间和 Watermark 清空。

节点选择的两张逻辑表都必须是 BOUNDED。它会改变行数并可能复制来源行，不保留来源表事件时间与
Watermark 的流式语义。

## 5. 校验与稳定错误码

复用必填项、表不存在、字段不存在、重复表名、重复字段名、执行模式、Geometry 定义、CRS、
dimension 和 Spark Analyzer 错误。新增：

| 错误码 | 阶段 | 条件 |
| --- | --- | --- |
| `INVALID_SPATIAL_CLIP_TABLE` | Compiler | 来源表与 Mask 表相同 |
| `SPATIAL_CLIP_REQUIRES_BOUNDED_INPUT` | Compiler | 任一输入不是 BOUNDED |
| `SPATIAL_CLIP_MASK_KIND_UNSUPPORTED` | Compiler | Mask 不是 Polygon/MultiPolygon |
| `SPATIAL_CLIP_CRS_MISMATCH` | Compiler | 两侧 CRS 不一致 |
| `SPATIAL_CLIP_DIMENSION_MISMATCH` | Compiler | 两侧 dimension 不一致 |
| `SPATIAL_CLIP_SOURCE_KIND_UNSUPPORTED` | Compiler | 来源家族策略遇到通用或集合 Geometry |
| `SPATIAL_CLIP_GEOMETRY_POLICY_REQUIRE_SCHEMA_VERSION` | 保存/导入/Compiler | 低于 4.51 携带非 null 策略 |
| `SPATIAL_CLIP_MASK_COMBINATION_REQUIRE_SCHEMA_VERSION` | 保存/导入/Compiler | 低于 4.77 携带非 null 多 Mask 组合方式 |
| `SPATIAL_CLIP_FAILED` | Runner | 真实数据空间裁剪失败 |

错误路径必须指向具体表或字段配置。Runner 错误不得包含 Geometry、坐标、WKT/WKB、Mask
值或来源数据行。

## 6. 前端设计器

- Palette 名称为“空间裁剪”，说明为“使用面要素裁剪来源 Geometry”。
- 位于“空间处理”分组，在 Geometry 序列化之后、空间聚合之前。
- Inspector 分为“来源”“Mask”“输出”三个紧凑分区；可选择“合并相交 Mask 后裁剪”或“每条 Mask
  分别裁剪”，输出区可选择保持来源家族或旧版通用 Geometry。
- 表和字段候选只来自 Compiler `inputTables`；来源字段显示全部 Geometry，Mask 字段只突出
  Polygon/MultiPolygon，但已有失效值必须保留并标红。
- 选中两个字段后展示其 kind、CRS 和 dimension，并即时提示不匹配项。
- 来源家族模式即时展示将得到的 MultiPoint/MultiLineString/MultiPolygon，并在来源为通用
  Geometry/GeometryCollection 时保留配置且标红。
- 面板根据组合方式提示“一条来源最多一条结果”或“一个来源可能输出多行”，并固定说明仅保留
  相交结果、Mask 属性不会输出。
- 节点摘要只显示来源表/字段、Mask 表/字段、输出表/字段、有效组合方式和结果家族，不显示空间数据值。

## 7. Task Engine、Runner 与安全边界

新增唯一无状态 `SpatialClipNodeOperator`，Compiler 与 Runner 共享，并使用 Sedona
`ST_Intersects`、`ST_Intersection`、`ST_Force2D`、`ST_CollectionExtract`、`ST_Multi`、
`ST_Union_Agg`、`ST_IsEmpty`、`ST_SetSRID` 构造计划。计划内来源行 ID 标记为技术列，既不进入
输出 Schema，也不被血缘误判为未知物理字段。节点阶段为
`PROCESS`，成功消息为“空间裁剪已准备”。

安全摘要只允许记录表名、字段名、输出字段、有效几何策略、Mask 组合方式、CRS 和固定裁剪语义。不得记录 Geometry、坐标、
匹配数量、交集面积、结果 WKT/WKB 或任何数据行；成功日志不得为统计行数额外触发 Spark
Action。

## 8. 验收标准

- 配置能够严格 JSON 往返；节点受 Canvas `1.23` 门槛约束，非 null 几何策略受 `4.51` 门槛约束，
  非 null 多 Mask 组合方式受 `4.77` 门槛约束。
- Polygon、MultiPolygon Mask 和不同来源 GeometryKind 能建立合法计划。
- NULL、Empty、未命中、单命中、多 Mask 命中的行语义正确；旧定义保持 Pairwise，新建节点默认
  Dissolve All。
- 重叠 Mask 不重复覆盖区域，分离 Mask 形成一个 Multi 结果，内容相同的来源记录不被误合并。
- 来源家族模式过滤低维边界接触并输出对应 Multi 家族；缺失/null 策略仍以通用 `GEOMETRY`
  保留历史降维结果。
- Mask kind、CRS、dimension、有界性和字段冲突校验准确。
- 输出只包含来源字段与裁剪字段，Map、Schema、CRS 和 nullable 正确；惰性计划保持空间 Join，
  预检零 Spark Job，技术行 ID 不降低字段血缘完整度。
- Registry、图规则、Inspector、摘要、生命周期和失败分类完整接入。
- 日志与错误不包含空间数据值。

## 9. 不在范围内

- LEFT/OUTER 裁剪、保留未命中来源行、保留 Empty 结果。
- 输出 Mask 属性、Mask ID、命中数量或相交比例。
- 跨全部来源全局物化或收集 Mask、按 Mask 属性分组融合、来源行去重或稳定排序。
- 自动 CRS 转换、容差、snap、grid precision 或错误行跳过策略。
- 流式空间裁剪、Broadcast 参数或空间分区调优配置。
