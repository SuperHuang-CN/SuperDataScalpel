# Canvas `SPATIAL_NEAREST` Processor 设计

## 1. 定位与审计结论

引入于 Canvas 4.11，仅 BATCH，两张有界表距离关联。参照 Enterprise 标准要素分析 Find Nearest 的部分直线距离能力，**不是 GeoAnalytics Server 的同名工具**。

本文区分 4.20 审计快照、4.30 真实距离基础、4.48 非点测地接入与后续目标设计；快照不是当前完整契约。
开发状态以[进度清单](../canvas-spatial-development-progress.md)为准。新增能力显式选择，不改变旧任务语义。

### 4.36 公共单位补充

最大距离、距离输出及可选连接线段长使用公共距离单位。测地方法仍禁止来源角度单位；隐藏连接线单位也参与版本门槛。
单位名称明确国际制/美国测量制，切换不自动换算数值，旧单位结果不变。具体枚举、字段和证据见[公共空间单位](../canvas-spatial-units.md)；不代表整节点已完成官方对照。

### 当前阅读入口与对齐边界（4.48 汇总）

- **已接入范围**：4.30 的真实距离与连接线见第 3 节；4.48 显式开放点/线/面及 Multi 类型的 WGS84
  真实最近位置。旧定义继续仅 Point；GeometryCollection、全球面域和无法证明的数值边界安全失败。
- **官方参照与差异**：参照 Enterprise 标准 Find Nearest，不是 GA Server 工具；路网不在范围。当前支持范围已完成
  本地功能、索引执行计划、真实页面和 GeometryServer 底层距离真值验收，但不声明 Enterprise 异步作业完全等价。
- **面板修订要求**：明确选择完整 Geometry 或仅 Point 兼容模式；半径、内部精度和连接线采样分别说明。

本摘要不替代逐版本契约。下节的“当前”均指 4.20 历史状态；目标线框不作为已实现截图。
参数覆盖、本地验证和官方结果对照分别登记，见[对齐验收规则](../canvas-spatial-analysis-processor-roadmap.md#7-对齐验收)。

## 2. 4.20 配置快照（历史）

```ts
interface SpatialNearestConfiguration {
  sourceTableName: string;
  sourceGeometryColumnName: string;
  candidateTableName: string;
  candidateGeometryColumnName: string;
  candidateIdColumnName: string;
  distanceMethod: SpatialDistanceMethod | null;
  nearestCount: number;
  maximumDistance: number | null;
  maximumDistanceUnit: SpatialDistanceUnit | null;
  includeUnmatched: boolean;
  outputTableName: string;
  distanceColumnName: string;
  distanceOutputUnit: SpatialDistanceUnit;
  rankColumnName: string | null;
  outputColumns: JoinOutputColumn[];
}
```

- 当前 nearestCount=1～100，最大距离可为空；距离输出单位独立，未命中保留可选，显式 Join 字段投影。
- 当前使用 Sedona ST_KNN，可叠加 ST_DWithin，再按距离和 candidateId 排名；不是已知的无半径 crossJoin。
- candidateId 用于平局排序，但真实重复、NULL 或 KNN 预先截断同距候选都会影响确定性，不能保证全局稳定 Top N。
- 当前 GEODESIC 限制 EPSG:4326 XY，PLANAR 为平台扩展；没有路网/连接线结果配置。

## 3. 官方参照与目标

| 官方能力 | 目标及范围 |
| --- | --- |
| 最近数量与搜索范围 | 保留 Top 1～100、最大距离和单位；平台不限距离是显式扩展，需性能风险提示。 |
| 直线距离 | 官方 Find Nearest 使用测地线；平台平面距离单独命名，不称为其默认模式。 |
| 点/线/面 | 官方支持，但应按最近位置测量；当前 Sedona 非点测地距离/排名必须验收，不能用质心近似冒充。 |
| 最近要素与连接线两类结果 | 保留匹配结果表，增加可选独立连接线表；一个 Map 输出多个显式表名。 |
| Travel modes、交通、障碍、路线层 | 依赖路网/路由服务，列为独立扩展，不在当前几何 KNN 中伪造。 |

官方标准工具有每层 5000 要素等服务限制，它们不是 DataScalpel/Spark 的业务上限，不照搬。
目标结果中距离、排名、未命中 NULL 与来源字段投影明确；连接线端点必须是参与距离计算的位置，不随意连质心。

确定性要求：

- 区分来源行身份与候选业务 ID；同一来源 Geometry 相同仍是独立来源行。
- ID 要求非空唯一或定义明确稳定次序。无法保证时给真实能力提示，不仅靠 Schema 判定唯一。
- 若底层 KNN 先截断同距集合，后续按 ID 排序不足以保证全局同距顺序；
  目标需让平局候选进入最终排序或承认平局不确定，不能擅自声称索引已解决。
- 排名值基于实际距离；近似/质心距离如未来开放，必须是显式不同策略。

### 4.30 局部实现：真实距离与连接线

```ts
matching?: {
  semantics: 'EXACT_DISTANCE' | 'LEGACY_KNN' | null;
  sourceIdColumnName: string;
  connectionLines: {
    enabled: boolean | null;
    outputTableName: string;
    geometryColumnName: string;
    maximumGeodesicSegmentLength: number | null;
    maximumGeodesicSegmentLengthUnit: SpatialDistanceUnit | null;
  } | null;
} | null;
```

- 无 matching/null/LEGACY_KNN 保留旧 KNN 及非点质心测地路径，不自动迁移；显式旧版忽略但保留连接线设置。
  任意 matching 对象要求 4.30；对象内 semantics 缺失/null 是真实距离策略。
- 新建选择 EXACT_DISTANCE、来源身份待选、距离方法待选、Top 1、无半径、输出米、连接线关闭。均为平台初值。
- PLANAR 为有效 XY 几何最近位置；在 4.30 定义中 GEODESIC 仅支持 EPSG:4326 XY Point。
  4.48 的非点能力见下一节；旧定义仍不会被自动切换。
- 来源和候选 ID 非空唯一检查进入惰性计划；编译不触发 Action。同位置不同来源以身份字段分开。
  NULL/Empty Geometry 不参与搜索；来源可按 includeUnmatched 保留，但不生成连接线。
- 配置半径：DWithin 候选 → 真实距离严格筛选 → 距离/候选 ID 排序。无半径：KNN 取得至少 Top N 的距离上界 → 半径恢复所有同距候选 → 同一排序。
  恢复半径扩张一个 ULP 避免浮点边界漏选，显式业务半径最终仍严格筛选；距离结果舍入到 12 位小数，排名使用原始距离。
- 连接线和主表从同一匹配关系派生，主表和连接线表追加在入口 Map 之后；不会重新独立求两次最近邻。
  Spark 多个下游 Action 仍可能重算该惰性关系，不自动缓存，不提供跨输出快照/事务保证。
- 连接线 XY MultiLineString 使用来源 CRS。平面从 JTS 最近位置连线；点测地用 WGS84 加密并切分日期线，不连接质心。
  最大段长初值 10 千米是平台值而非官方默认。仅启用测地连接线时校验段长；关闭或旧版策略不清空设置。
- 真实数据 ID、Geometry、距离超限错误均为安全 SCHEMA 非重试错误；连接线超过 100 万顶点为 CONFIGURATION 非重试错误。
  错误不回显身份值/坐标；结果不会用 NULL 或伪造线掩盖失败。
- 本地 Spark 样本验证不等于大规模/官方服务验收，非点测地与真实规模验收继续在完整范围内。

### 4.48 显式非点测地 Geometry

```ts
type SpatialNearestGeodesicGeometryMode = 'POINT_ONLY' | 'GEOMETRY';

interface SpatialNearestMatching {
  semantics: 'EXACT_DISTANCE' | 'LEGACY_KNN' | null;
  sourceIdColumnName: string;
  connectionLines: SpatialNearestConnectionLines | null;
  geodesicGeometryMode?: SpatialNearestGeodesicGeometryMode | null;
}
```

- 缺失/null 按 `POINT_ONLY`，保持 4.30 行为；显式 `GEOMETRY` 要求 Canvas 4.48，新建节点默认该值。
  字段在 LEGACY_KNN 中不生效，但非活动草稿同样参与版本门槛。
- `GEOMETRY` 接受 EPSG:4326 XY Point/MultiPoint/LineString/MultiLineString/Polygon/MultiPolygon。
  GeometryCollection 明确拒绝；通用 GEOMETRY 在 Executor 检查实际类型。测地模式双方都必须是 XY，不能因维度相同而放行 XYZ/XYM/XYZM。
- 跨表候选使用 ECEF XY 空间 Join 与 Z 区间保守召回；最终距离、半径、排名及连接端点只消费同一份 WGS84 Match。
  面内为零，孔洞内为到孔洞边的正距离，已验证局部交叉使用真实公共位置；不使用质心或 ECEF 距离作为结果。
- MultiPolygon 不能放入同一局部域时，可逐部件验证全球分布的严格分离小区域；各部件共享总预算，只有边界分离
  与双向非包含均可证明才接受。单个跨域 Polygon、跨域接触/相交/包含及未决关系仍拒绝。
- Point/MultiPoint 对属于有限离散搜索；只有 ECEF 分层队列已排除所有可能更优或同距点对后才标记精确，
  因而亚求解容差的近等距点候选仍可按真实距离和候选 ID 排序。线/面不套用该捷径。
- 非点距离通常是有限区间。只有半径侧和 Top N 次序可证明时返回；区间跨界或重叠、未开放的 Polygon 域、
  工作预算/数值精度不足都返回安全错误，不按取样顺序猜测或退回旧质心路径。
- 日期线 Polygon 不使用经纬平面 JTS 有效性作为测地门禁。连续原始弧、环和区域拓扑由 WGS84 路径校验；
  已验证日期线外环与孔洞、面内零距离、孔洞内正距离，以及连接线长度与排名距离使用同一组求解见证点。
- 连续 Geometry 的极近距离区间若无法证明严格次序，返回 `GEODESIC_DISTANCE_PRECISION_NOT_REACHED`；
  该失败语义已用两条几乎等距 LineString 的节点级回归验证。
- 256 张来源记录对 1024 个候选的本地样例已验证索引化 KNN + 保守召回、结果正确且执行计划没有
  Cartesian/Nested Loop Join。它是当前实现的规模烟测，不代表 Enterprise 服务容量或任意大数据上限。

## 4. 目标 Inspector UI

设计状态：参照 Enterprise 标准 Find Nearest，不归类为 GA Server 工具。4.48 已在显式模式开放受控非点测地；
路网/交通不在本节点范围。全球严格分离 MultiPolygon 部件已受控支持，但单个跨域面、官方数值/容量仍不是已完成能力。

```text
来源 / Geometry     [ stores / location ▼]
候选 / Geometry     [ warehouses / location ▼]
匹配策略            [真实距离 · 稳定同距排序 ▼]       切换确认
来源唯一字段        [ store_id ▼]                    真实距离时显示
候选唯一次序字段    [ warehouse_id ▼]
距离方法            [测地线 | 平面（扩展）]
测地 Geometry 范围  [完整 Geometry（推荐）| 仅 Point（兼容）]  仅真实测地显示
最近数量            [3]   最大距离 [20][千米 ▼]
未命中              [✓保留]
距离字段 / 单位     [distance][千米 ▼]
排名字段            [nearest_rank]
输出投影            9 项                          [设置]
匹配结果表          [store_nearest]
连接线结果          [关闭]                          [设置]
```

路网模式不伪装为可选算法；输出投影复用 Join，右侧同名默认完整表名前缀。
无半径、区间平局、局部 Polygon 域和 GeometryCollection 限制放 Tooltip；当前不支持的组合直接标红，原无效值保留。
连接线使用 560px 独立 Modal：启用开关、表名、Geometry 字段；测地时显示段长及单位。
取消不提交，关闭仍保留隐藏值，无效业务草稿可保存。主表与连接线 Form 使用不同名称，避免输出表名字段 DOM ID 冲突。


### 参数交互与初值（目标设计）

下表是目标面板约定，不修改旧任务默认值；未明确标注为官方默认的初值均为平台推荐。

| 配置组 | 初值与条件显示 | 对齐边界 |
| --- | --- | --- |
| 距离模式 | 明确选择测地线或平面，不根据数据悄然换算法 | 测地线参照标准分析；平面为平台扩展 |
| 数量与搜索范围 | 新建推荐 Top 1；半径留空并说明全范围搜索风险 | 平台初值，不冒称官方默认 |
| 输出类型 | 匹配表必填；连接线默认关闭，开启后另填表名与 Geometry 字段 | 一条输出端口承载两张显式表 |
| 同距顺序 | 候选次序字段必填；不使用当前显示顺序作为平局规则 | 稳定排序为平台明确约束 |

连接线与匹配表共享同一匹配集合，不能分别运行两次近邻后拼接。未命中保留时匹配字段为空，不生成伪造连接线。

## 5. 验收与安全

- 两表可从一条边取得；新增结果保留入口 Map，BOUNDED，无 Watermark。
- 测试 Top N、半径内不足 N、未命中、相同几何的两来源、同距多于 N、重复/NULL 候选 ID。
- 测地测试跨日期线、线/面最近位置；对比最终距离和用于筛选/排名的距离一致性。
- 连接线 Geometry/CRS 与距离的关系明确，不能输出线却无法重现距离语义。
- 大数据执行计划验证索引/KNN 行为，不以“采用 Spark”保证任意规模；禁止全表 collect 到 Driver。
- Canvas 可显示 Top N 和配置半径，隐藏真实距离、坐标及候选记录。

当前支持范围已经完成上述本地验收：真实页面确认默认策略、完整 Geometry 选择、邻近帮助和独立连接线 Modal；
退出时放弃临时节点，没有保存用户任务。单个跨局部计算域 Polygon、跨域接触/包含、GeometryCollection、路网
和无法证明的连续近等距结果属于明确拒绝范围，不以近似值替代。Enterprise Find Nearest 作业级官方环境仍不可用，
因此这里只声明 DataScalpel 当前明确支持范围完成，不声明与 Enterprise 的字段、作业编排或容量完全等价。

## 6. 官方依据

- [Enterprise 11.3 Find Nearest：测地、路网与两层结果](https://enterprise.arcgis.com/en/portal/11.3/use/find-nearest.htm)
- [GA 工具目录（用于区分产品）](https://developers.arcgis.com/rest/services-reference/enterprise/geoanalytics/tasks/geoanalytics-tasks/)
- 2026-09-12 使用 ArcGIS Online 官方 GeometryServer `distance`（EPSG:4326、测地、米）记录 Point→Line、
  日期线交叉、面内、孔洞内和全球严格分离 MultiPolygon 五个离线真值，当前实现均通过。该对照验证底层
  Geometry 距离，不冒充 Enterprise Find Nearest 异步作业或排名/字段/容量验收。

## 7. 非点测地最近位置内部开发（协议仍为 4.43）

> 本节保留 4.43～4.47 的内部推进历史；当前可用契约以第 3 节的 4.48 小节为准。

共享 `Wgs84SegmentDistance` 已提供 WGS84 有限线段间最近位置的完整弧长参数域搜索，
`Wgs84LinearDistance` 扩展至 Point/MultiPoint/LineString/MultiLineString。返回位置对、距离和数值下界，
不比较质心，不补多部件连接或折线闭合；详见[内部距离设计及精度边界](../canvas-wgs84-geometry-distance.md)。

**尚未接入 NearestExactPlan，EXACT_DISTANCE 的非点测地限制保持。**
当前质心 KNN/DWithin 无法保证线/面真实最近候选的完整性，不能仅换距离 UDF 就开放此能力。
局部 Polygon 区域/孔洞与连续相交的内部进展见下文；节点仍需完整非点候选召回、区间排名/阈值接入和连接线统一；
接近零的数值上界不自动吸附为零。旧 LEGACY_KNN 结果不在本步骤中改变，不将局部算法宣称为完整官方对齐。

后续 `Wgs84GeometryDistance` 已在内部增加局部 Polygon/MultiPolygon 区域与边界距离，
区分内含、孔洞和未决边界；同一几何的边对共享全局下界队列，不使用质心或渲染辅助边。
仍需连续拓扑/近接触、全域、完整候选召回和严格区间排名策略；本节点的配置限制尚未放开。

区域准备现增加连续原始弧检查和合法 T 接触点节点化，明确未决不吸附；原始孔洞顶点的边界证据
优先于外环未决方位，因此接触点可以返回同一真实位置与零距离。跨区域内部交叉的精确位置、
全域拓扑和严格排名仍待完成，不将该内部修正标成 Nearest 非点测地能力已开放。

原始局部区域的孔洞/多部件关系、公共顶点穿越和接触连通图已继续接入，测距准备不再依赖
采样 Footprint 或平面有效性，并移除内部采样步长参数。非轴向极近接触、全域、公共交叉位置、
严格区间排名及跨表候选召回仍待完成；该分离不改变节点当前能力门槛。

内部最大距离谓词现按上下界决定保留/排除，跨阈值继续同队列求精；未决包含不被当成分离。
该谓词未接入当前 EXACT_DISTANCE 的点过滤，不改变旧任务结果。极近阈值/零距离交叉、全域、
非点候选召回与区间排名仍待完成，详见[共享阈值设计](../canvas-wgs84-geometry-distance.md#距离阈值的区间判定)。

零阈值的局部连续边界证据已接入内部谓词，能区分交叉/接触/重叠、分离和未决；无内含顶点的交叉面
不再仅靠距离逼近零。Boolean 相交事实与最近位置结果保持分开，没有为连接线伪造坐标。
非点测地最近位置和区间排名仍未开放，不能把这一步看作最终 Nearest 距离/连接线输出已对齐。

后续面板与契约必须区分三种设置：搜索半径决定候选是否合格；距离求解精度参与阈值/排名判断；
连接线采样只控制输出显示粒度。调整连接线采样或关闭连接线不能改变选出的近邻。
这些不是一个通用“容差”，内部距离误差界也不能被标成最近位置坐标误差界。
尚未确定的精度契约保持待设计，不为它预填所谓 ArcGIS 默认值。面内相交只有 Boolean 证据时，
不能凭空生成连接端点；真实位置与同距处理完成前，非点测地选项继续禁用并保留失效草稿。

- [共同规则](../canvas-spatial-analysis-processor-roadmap.md)

### 共享搜索推进（4.47 历史）

内部距离求解已用保守 ECEF 包围盒树替代全部边对的预展开。树对与弧长细分共用下界队列，
仍返回真实位置对、距离区间及严格阈值判断，减少复杂多部件 Geometry 在初始化时耗尽预算的问题。
这是单对 Geometry 内部搜索，不是跨表候选召回；高重叠最坏情形仍可能二次展开。
**本次没有开放非点 EXACT_DISTANCE**，公共交叉位置、全域、跨表召回和区间排名继续在完整开发范围内。
算法与验证边界见[分层距离搜索](../canvas-wgs84-geometry-distance.md#完整边对域的分层距离搜索447)。

随后 Point EXACT_DISTANCE 的跨表召回改为 ECEF 三轴保守包围：二维空间 Join 负责索引，Z 区间补足
第三轴；业务半径或由种子取得的真实 WGS84 上界只用于扩大召回范围，最终距离/筛选/排名仍走 WGS84。
极区反例覆盖“二维 ECEF 最近种子实际接近对跖、真正最近候选在二维更远但 Z 相近”，有/无半径均召回正确。
该路径已经为线面候选提供必要包围能力。后续同版本已将距离/上下界/两端位置放入同一匹配 Struct，
当前 Point 路径的半径判定、Top N 检查和连接线端点都消费该结果；非点门槛仍不解除。
详见[ECEF 保守召回](../canvas-wgs84-geometry-distance.md#跨表候选的-ecef-保守召回基础447)。

当前区间规则只接受可证明的半径侧和 Top N 顺序；不确定区间重叠返回精度错误，不按取样值猜测。
Point 距离是确定数值，保留原有按候选 ID 的同距排序。线/面纯算法 Struct 已验证实际最近位置不使用质心，
该阶段公共交叉位置、局部域之外面关系、复杂近等距预算和完整页面/官方/规模验收仍未完成，因此当时保留非点门槛。
后续 4.48 已对局部可验证范围完成显式接入；全球面域、复杂近等距容量和官方对照继续开放。
