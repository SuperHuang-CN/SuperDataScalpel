# Canvas Build Multi-Variable Grid Processor 设计

## 1. 定位与 ArcGIS 对齐边界

`SPATIAL_MULTI_VARIABLE_GRID` 对齐 ArcGIS Enterprise 11.3 GeoAnalytics Server
Build Multi-Variable Grid 的核心业务能力：从多张要素表的共同范围生成统一规则格网，并把到最近要素的距离、
最近要素的属性和关联要素属性汇总写入同一张格网结果表。

官方参考：[Build Multi-Variable Grid](https://developers.arcgis.com/rest/services-reference/enterprise/geoanalytics/tasks/build-multi-variable-grid/)。

DataScalpel 保留 ArcGIS 的三类变量方向和逐变量来源、筛选、搜索距离，但使用 Canvas 的
`Map<tableName, CanvasTableSchema>` 选择逻辑表，不复制 Portal 图层参数、服务发布或数据存储 API。
当前只开放投影 XY 矢量分析，不自动投影、不生成 Raster，也不把本节点解释成后续的
Enrich From Multi-Variable Grid。

## 2. 配置契约

Canvas 4.70 新增：

```ts
type SpatialMultiVariableGridVariableKind =
  | 'DISTANCE_TO_NEAREST'
  | 'ATTRIBUTE_OF_NEAREST'
  | 'ATTRIBUTE_SUMMARY_OF_RELATED';

type SpatialMultiVariableGridStatisticKind =
  | 'COUNT' | 'SUM' | 'MEAN' | 'MIN' | 'MAX'
  | 'RANGE' | 'STDDEV' | 'VARIANCE' | 'ANY';

interface SpatialMultiVariableGridVariable {
  variableId: string;
  sourceTableName: string;
  geometryColumnName: string;
  kind: SpatialMultiVariableGridVariableKind | null;
  attributeColumnName: string | null;
  statisticKind: SpatialMultiVariableGridStatisticKind | null;
  statisticColumnName: string | null;
  searchDistance: number | null;
  searchDistanceUnit: SpatialDistanceUnit | null;
  filter: CanvasFilterCondition | null;
  outputColumnName: string;
}

interface SpatialMultiVariableGridConfiguration {
  variables: SpatialMultiVariableGridVariable[];
  binShape: 'SQUARE' | 'HEXAGON' | null;
  binSize: number;
  binSizeUnit: SpatialDistanceUnit;
  outputTableName: string;
  binIdColumnName: string;
  binGeometryColumnName: string;
}
```

- `variables` 按配置顺序形成结果字段，最少 1 项、最多 32 项；`variableId` 是节点内唯一 UUID。
- 多个变量可以引用同一张表，也可以来自不同表；每项独立选择 Geometry、字段、筛选和搜索距离。
- 输出表必须是新的 Canvas 逻辑表。格网 ID、格网 Geometry 和所有变量结果名按大小写不敏感唯一。
- 空数组和未完成项可作为草稿保存；Compiler 在发布或预检时返回精确到
  `configuration.variables[index]` 的问题。

## 3. 输入、共同范围与格网

- 节点仅支持 `BATCH`；所有实际引用表必须为 `BOUNDED`。
- Geometry 必须带完整元数据，维度为 XY，类型属于 Point、MultiPoint、LineString、
  MultiLineString、Polygon 或 MultiPolygon。
- 所有变量来源 Geometry 的 CRS 必须完全一致，且必须是轴单位可解析的投影 CRS；需要变换时在上游显式使用
  Spatial Transform。
- 共同分析范围由所有唯一“来源表 + Geometry 字段”的有效 Geometry 外包矩形合并得出。
  NULL/Empty Geometry 不参与；变量筛选只影响当前变量计算，不能缩小共同范围。
- SQUARE 的 `binSize` 表示边长；HEXAGON 表示对边距离。当前原点固定为 `(0, 0)`，输出与共同范围
  相交的完整格网 Polygon，不裁剪格网边界。
- 格网候选最多 1,000,000；坐标非有限、Geometry 无效或索引超出可靠范围时在真实执行中安全失败。

## 4. 三类变量语义

### 4.1 `DISTANCE_TO_NEAREST`

以格网中心到候选 Geometry 的平面最短距离选择最近要素。`searchDistance` 必填；半径内无候选时输出
NULL。结果为可空 DOUBLE，单位与该变量的 `searchDistanceUnit` 相同。

### 4.2 `ATTRIBUTE_OF_NEAREST`

使用相同最近关系返回候选要素的一个非 Geometry 标量属性。半径内无候选时输出 NULL，结果平台类型
继承所选字段并改为可空。距离并列时按 Geometry WKB 哈希和属性字符串形成确定性顺序；这只是平台稳定
规则，不宣称等同于 Esri 未公开的并列选择顺序。

### 4.3 `ATTRIBUTE_SUMMARY_OF_RELATED`

- 配置搜索距离时，以格网中心到来源 Geometry 的平面距离不大于半径作为关联关系。
- 不配置搜索距离时，以来源 Geometry 与完整格网 Polygon 相交作为关联关系。
- `COUNT` 不选字段，输出非空 LONG；无匹配时为 `0`。
- `SUM/MEAN/MIN/MAX/RANGE/STDDEV/VARIANCE` 要求数值字段，统一输出可空 DOUBLE；标准差和方差使用样本统计。
- `ANY` 第一版只接受 STRING，并以确定性最小字符串实现；无匹配时为 NULL。
- NULL 数值不参与统计；NaN、Infinity 或转换后的非有限数值不被静默丢弃，而是以
  `SPATIAL_MULTI_VARIABLE_GRID_VALUE_NOT_FINITE` 失败。

搜索距离必须是有限正数并能换算到共同 CRS，且与格网大小之比最多为 512。

## 5. 执行、Schema 与安全

节点先校验全部变量，再构造共同格网和各变量惰性关系；任一变量无效时不传播部分结果。入口表 Map 保持原顺序，
新结果表追加到末尾。结果列顺序固定为：STRING 格网 ID、共同 CRS 的 XY Polygon、按变量数组顺序生成的字段。
结果为 `BOUNDED`，没有事件时间或 Watermark。

Compiler 只基于逻辑 Schema 构造零行或惰性 Spark 计划，不读取真实数据。Runner 才执行范围聚合、格网生成、
空间关联和汇总。安全摘要只记录变量数、来源表数、有筛选的变量数、格网形状和输出表名；不记录字段名、
筛选字面量、搜索距离、坐标或数据值。

稳定运行错误包括：

- `SPATIAL_MULTI_VARIABLE_GRID_CELL_LIMIT_EXCEEDED`：候选格网超过上限，归类为配置错误；
- `SPATIAL_MULTI_VARIABLE_GRID_GEOMETRY_INVALID`：真实 Geometry 无效或坐标不可计算，归类为 Schema 错误；
- `SPATIAL_MULTI_VARIABLE_GRID_VALUE_NOT_FINITE`：真实统计值非有限，归类为 Schema 错误。

## 6. Inspector 与 Canvas UI

Inspector 常驻展示公共格网和全部变量摘要；每个变量通过独立设置 Modal 编辑，条件树再使用独立 Modal，
避免把不同表的字段规则堆叠在主面板。上游失效值保留并标红，普通业务错误允许保存草稿。

```text
┌ 构建多变量格网 ─────────────────────────────┐
│ 格网形状 [方格 | 六边形]   格网大小 [1000][米] │
│                                              │
│ 格网变量 (3)                         [添加变量] │
│ 1 医院.shape → hospital_distance      [↑][↓][设置][删] │
│   到最近要素的距离 · 中心半径                 │
│ 2 医院.shape → hospital_level         [↑][↓][设置][删] │
│   最近要素的属性 · 中心半径 · 1 个筛选条件     │
│ 3 人口区.shape → population_sum       [↑][↓][设置][删] │
│   关联要素属性汇总 · 格网相交                  │
│                                              │
│ 结果字段 (5)                          [设置]   │
│ 输出表名 [city_variable_grid_______________] │
└──────────────────────────────────────────────┘

┌ 配置格网变量 ────────────────────────────────┐
│ 来源表 [医院]       Geometry [shape]          │
│ 变量类型 [最近要素的属性]                     │
│ 最近属性 [level]                              │
│ 中心搜索半径 [2000][米]                       │
│ 变量筛选 [开启]                        [设置]  │
│ 结果字段名 [hospital_level]                   │
│                              [取消][保存变量草稿] │
└──────────────────────────────────────────────┘
```

Canvas 卡片预览前两个“来源表 → 变量字段”，并显示来源表数、变量总数、形状、半径变量数和筛选变量数；
不展示筛选内容、属性值或距离数值。

## 7. 尚未完成的验收与后续范围

- 使用真实 ArcGIS Enterprise 11.3 作业对照方格/六边形边缘、线面相交、最近距离、属性并列和统计 NULL 行为；
- 核实 ArcGIS 对边距离、固定原点、范围扩展和候选边界的未公开细节；
- 验证米、国际英尺、美国测量英尺等不同投影轴单位，以及 Point/Line/Polygon 混合来源；
- 验证接近 32 变量、100 万格和大半径比例上限时的 Shuffle、Executor 内存与运行时间；
- 在真实浏览器中验收变量 Modal、条件树、失效上游、Canvas 50%～200% 缩放和错误详情；
- Enrich From Multi-Variable Grid 仍是独立后续 Processor，本节点不接收已有变量格网并回填点要素。

完成这些对照前，只表述为核心能力和参数方向对齐，不声明与 Esri 内部实现数值完全一致。

## 8. 当前支持范围收口（2026-09-13，协议仍为 4.76）

- 当前明确承诺的多变量格网范围已形成闭环：多张投影 XY Point/Line/Polygon 来源、共同范围、方格/
  六边形，以及 `DISTANCE_TO_NEAREST`、`ATTRIBUTE_OF_NEAREST` 和
  `ATTRIBUTE_SUMMARY_OF_RELATED` 三类变量继续使用本文语义。
- 三类变量和 `COUNT/SUM/MEAN/MIN/MAX/RANGE/STDDEV/VARIANCE/ANY` 九种统计的全部结果字段均达到
  `FIELD_COMPLETE`，不存在 `WRITTEN_UNKNOWN_SOURCE`。`COUNT` 追溯实际来源 Geometry；最近属性追溯
  Geometry 与被选属性字段；数值统计追溯 Geometry 与统计字段；格网 ID 和 Geometry 追溯参与共同范围的
  来源 Geometry。
- 两张来源表生成同一格网的字段血缘已验证。20,000 个规则稀疏投影点的 Preview 仅构造并分析 Catalyst
  计划，提交 Spark Job 数为 0；真实执行输出 20,000 个格网。计划不含 Driver 收集、`CollectLimit`、
  `collect_list`、Cartesian Product 或 Broadcast Nested Loop Join。该样例不等价于生产容量承诺。
- `SpatialMultiVariableGridNodeOperatorSparkTest` 3 项通过。真实页面已验证新增节点延迟编译、方格/
  六边形入口、三种变量类型、逐变量筛选与条件树、结果字段、无效草稿应用、Canvas 摘要和紧凑问题详情；
  未保存任务定义。
- 当前收口不扩大第 7 节边界：Enterprise 格网边缘、最近并列、统计数值与 NULL 细节，不同 Geometry/
  投影轴单位，以及接近 32 变量、100 万格和大半径时的生产容量仍开放，不声明 Esri 数值完全等价。
