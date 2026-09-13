# Canvas `TRACK_RECONSTRUCT` Processor 设计

## 1. 定位与审计结论

引入于 Canvas 4.14，仅 BATCH；4.20 历史实现是 Point→LineString 与 gap 拆分子集。后续已增加有序片段、测地线、平面面轨迹和缓冲观测窗口；4.44 接入测地面执行链路（第 17 节），当前平台明确支持范围已在第 18 节收口。该状态不代表任意全球域或 ArcGIS Enterprise Reconstruct Tracks 完全等价。

第 2 节保留 Canvas 4.20 审计快照。4.21 已新增可选 `boundaries.fixedTimeBoundary`：
正整数周期、日历/时长单位、参考时间和 IANA 时区；与原有 gap 独立生效，未启用时旧定义行为不变。
完整字段及默认值见[Canvas Definition](../canvas-task-definition.md)。Inspector 的边界 Modal 已提供配置入口。
4.27 有序片段、受控表达式、连接段归属及单点跳过见第 7 节；4.28 显式测地路径见第 8 节。后续增量见第 9～17 节；各小版本能力不能直接用于更早的旧小版本 JSON。
4.35 字段 Count/Any、聚合类型修正和隔离式统计弹窗见第 9 节。
4.42 显式平面面轨迹、字段/逐行表达式缓冲和独立设置弹窗见第 10 节；4.43 数值观测窗口见第 11 节。4.44 局部测地面链路已在第 17 节接入，全球域与完整官方对照仍未完成。

### 4.47 固定时长周

相邻时间 gap 的 `boundaries.maximumTimeGapUnit`新增“周（固定 7 天）”，每周为 604800 秒。
与已有按时区推进的日历周区分；切换单位不换算已填数值，隐藏草稿保留并参与 4.47 门槛。
固定月年不开放，阈值边界、节点算法与输出粒度不变；详见[共享时长规则](../canvas-spatial-units.md#447-固定时长周与日历周)。

### 4.36 公共单位补充

相邻距离边界与测地路径最大段长使用公共距离单位。隐藏的旧路径/平面加密参数保留并参与版本门槛；不补齐面/缓冲轨迹。
单位名称明确国际制/美国测量制，切换不自动换算数值，旧单位结果不变。具体枚举、字段和证据见[公共空间单位](../canvas-spatial-units.md)；不代表整节点已完成官方对照。

### 当前阅读入口与对齐边界（4.45 汇总）

- **已接入范围**：有序片段、路径、统计、面缓冲与窗口见第 7～11 节；4.44 局部测地面节点接入见第 17 节。
- **官方参照与差异**：参照 GA Reconstruct Tracks；当前局部测地面范围已收口，但不承诺全球域、官方公式/精度、生产容量或 Arcade 兼容。
- **面板修订要求**：固定周期始终 Gap；面边界采样独立于隐藏的线段长，采样段长不能标成位置误差。

本摘要不替代逐版本契约。下节的“当前”均指 4.20 历史状态；目标线框不作为已实现截图，现行完成边界以第 18 节为准。
参数覆盖、本地验证和官方结果对照分别登记，见[对齐验收规则](../canvas-spatial-analysis-processor-roadmap.md#7-对齐验收)。

## 2. 4.20 配置快照（历史）

```ts
interface TrackSummaryStatistic {
  statisticId: string;
  kind: 'COUNT' | 'SUM' | 'MEAN' | 'MIN' | 'MAX' | 'RANGE' | 'STDDEV' | 'VARIANCE' | 'FIRST' | 'LAST';
  sourceColumnName: string | null;
  outputColumnName: string;
}

interface TrackReconstructConfiguration {
  sourceTableName: string;
  pointGeometryColumnName: string;
  trackIdColumns: string[];
  timeColumnName: string;
  distanceMethod: SpatialDistanceMethod | null;
  boundaries: TrackBoundaryConfiguration;
  summaryStatistics: TrackSummaryStatistic[];
  outputTableName: string;
  outputGeometryColumnName: string;
  startTimeColumnName: string;
  endTimeColumnName: string;
  pointCountColumnName: string;
}
```

- 当前 trackIdColumns=1～8，summaryStatistics=0～32；时间必须已解析，时间/距离 gap 任一超限拆分。
- 只有点轨迹，不含面轨迹、bufferField、arcadeSplit、splitBoundaryOption 或固定时间重置。
- FIRST/LAST 按时间排序；重复时间顺序尚不确定，不能声称运行会自动保证稳定。
- 当前少于两个有效点可能返回运行错误，这是待修正/验收的退化行为，不是官方要求。

## 3. 官方参数与补齐目标

| 官方参数 | 目标设计 |
| --- | --- |
| inputLayer / trackFields | Point 或 Polygon、轨迹标识和 instant 时间；增加次序策略。 |
| method | Planar / Geodesic，同时影响连接/缓冲；测地轨迹须核对跨日期变更线。 |
| bufferField | 可选数值字段或受控距离表达式，明确单位；生成面轨迹。 |
| summaryFields | Count 总数、字段非空 Count、数值统计、Any、First/Last。 |
| timeSplit / distanceSplit | 相邻 gap 数值及单位，任一超限拆分。 |
| timeBoundarySplit / Unit / Reference | 固定周期重置，独立配置，不与 gap 混用。 |
| arcadeSplit | 表达式为 true 时拆分；受控表达式需覆盖具体字段/时间/轨迹窗口用例，不宣称 Arcade API 兼容。 |
| splitBoundaryOption | Gap（默认）、FinishLast、StartNext；拆分处跨点线段分别不生成/归前段/归后段。 |

输出 Point 无缓冲→线；缓冲点或 Polygon→面轨迹。不能简单将面做 Union 当作按时间构造的轨迹。
固定时间边界始终 Gap；splitBoundaryOption 仅作用于适用的 gap/表达式拆分，不跨固定周期共享观测。
此规则已进入 4.27，官方样例的结果对照仍须验收，不能把尚未验收写成规则尚未确定。

## 4. 目标 Inspector UI

设计状态：线轨迹、平面 XY 面轨迹及缓冲观测窗口见第 7～11 节，4.44 测地面接入见第 17 节。测地面须配置独立边界采样并满足 WGS84 XY，不能任意组合 CRS 或回退为平面计算。面轨迹名称同时涵盖缓冲点与原始面观测。

```text
来源 / Geometry     [ vehicle_points / location ▼]
轨迹标识 / 时间     [ vehicle_id ][ event_time ]
同时间顺序          [ event_id ▼]
距离方法            [ 平面 | 测地线 ]
输出形态            [ 线轨迹 | 面轨迹 ]
缓冲距离            [ distance_field ▼][米 ▼]  [表达式]
相邻时间 / 距离     [30][分钟 ▼]  [5][千米 ▼]
固定时间边界        关闭                          [设置]
表达式拆分          未配置                        [设置]
拆分连接段          [留空 | 归前段 | 归后段]        (?)
片段统计            2 项                          [设置]
结果字段            Geometry · 起止 · 点数         [设置]
输出表              [ vehicle_tracks ]
```

面输入时显示面轨迹模式；缓冲参数按需出现。统计编辑为单行表格。
所有拆分选项分别显示，不能用一行“轨迹边界”隐藏不同语义；说明放 Tooltip。


### 参数交互与初值（目标设计）

下表是目标面板约定，不修改旧任务默认值；未明确标注为官方默认的初值均为平台推荐。

| 配置组 | 初值与条件显示 | 对齐边界 |
| --- | --- | --- |
| 来源形态 | 点 / 面按 Schema 展示；点的线轨迹与缓冲面轨迹显式选择 | 不把任意面 Union 等同重建轨迹 |
| 拆分 | gap、固定周期、表达式各自默认关闭；可同时启用 | 三个条件不是同一时间窗口 |
| 连接段归属 | Gap 为官方参数默认；FinishLast / StartNext 按拆分适用性显示 | 固定周期始终 Gap；4.27 已实现，不能被该选项覆盖 |
| 缓冲距离 | 启用缓冲才显示字段或受控表达式及单位，业务数值不猜填 | 不意味着支持任意 Arcade |
| 次序与统计 | 明确同时间次序；统计默认空，总点数为独立结果 | FIRST/LAST 必须使用同一排序规则 |

每个拆分入口显示“关闭 / 已配置”，帮助分别解释 gap 和周期；面输入下禁止用仅适用于点的字段名称误导用户。
若只启用固定周期，连接段归属显示“固定周期：留空”，不让 FinishLast / StartNext 看起来对其生效；
若同时有 gap/表达式，归属选择器只作用于这些边界，并在邻近帮助说明。已保存的非活动选项继续保留。

测地面设置的接入约定（4.44 已按第 17 节接入）：

- 测地面组合不因内部原语存在就开放；4.44 已贯通节点链路，仍须满足实际计算域和数值能力限制。
- 在面轨迹 Modal 独立配置“边界采样最大段长”及单位，不复用非活动的线轨迹参数。
  帮助说明其控制输出离散粒度，不保证整个曲面的最大位置误差；不宣称为 ArcGIS 官方参数或默认值。
  对应可选 `geodesicBoundary` 从 4.44 引入，未设置时保留空值，不猜填业务采样粒度。
- 相邻距离拆分使用原始观测 Geometry；改变缓冲半径或边界采样粒度不得改变 gap 的距离对象。
  同一真实观测被共享到两段时，保留拆分前计算的同一足迹和半径，固定周期仍独立。
- 发布开放前需贯通来源测地有效性、真实非点距离阈值、逐观测足迹、排序/共享端点、Schema 与血缘。
  不能仅删除方法限制后沿用原始经纬度平面有效性检查或非点质心距离。

第 12～16 节为内部开发的历史记录，“尚未接入”对应各阶段状态；当前节点链路以第 17 节为准。

## 5. 输出与验收

- 每轨迹片段一行，标识、起止时间、Count、统计和 Geometry；结果为时间区间、BOUNDED，无 Watermark。
- 保留入口 Map，结果表新建且名称无冲突；不返回点数组冒充线/面。
- 单点、重复点、NULL 时间/Geometry、共线缓冲退化的处理必须明确；旧线策略中的退化组错误不作为最终对齐目标。
  有序线策略的单观测片段跳过、面策略的单观测保留及无效半径失败见第 7、10 节；官方未明确的边界仍须对照，不将平台裁决写成官方保证。
- 验收：两点/多点、两个轨迹、gap 相等与超限、两种 gap 的 OR、日界重置、表达式拆分、
  三种段归属、面/缓冲轨迹、跨日期线、First/Last 同时间排序。
- 大轨迹不得全表 collect 到 Driver；还需评估单组内存/排序，不以“分布式”替代容量边界。
- Canvas 只显示轨迹字段、模式、配置参数和新表，不显示实体、坐标或事件时间值。

## 6. 官方依据与边界

- [Reconstruct Tracks：面/缓冲轨迹与拆分参数](https://developers.arcgis.com/rest/services-reference/enterprise/geoanalytics/tasks/reconstruct-tracks/)
- [Enterprise 11.3 Portal：固定周期始终 Gap、Finish after/Start before 示例与单点线不输出](https://enterprise.arcgis.com/en/portal/11.3/use/geoanalytics-reconstruct-tracks.htm)
- [共同规则与版本策略](../canvas-spatial-analysis-processor-roadmap.md)

本节点仍以有界批处理为目标；Streaming、迟到修正、路网匹配独立设计。

## 7. Canvas 4.27 有序片段、表达式和连接段归属

### 配置

在既有 TrackReconstructConfiguration 中增加可选 `reconstruction`，原 12 参数 Java 构造器保留。

```ts
interface TrackReconstructOptions {
  semantics: 'ORDERED_SEGMENTS' | 'LEGACY_POINTS' | null;
  orderByColumns: string[];
  splitBoundaryOption: 'GAP' | 'FINISH_LAST' | 'START_NEXT' | null;
  splitExpression: {
    expression: string;
    bindings: { name: string; sourceColumnName: string; offset: number | null }[];
    enabled?: boolean | null;
  } | null;
}
reconstruction?: TrackReconstructOptions | null;
```

- 缺失/null reconstruction 保持原点连线语义，不改变旧任务的次序和单点错误行为。
- 非 null 对象的 semantics 缺失/null 等同 ORDERED_SEGMENTS；显式 LEGACY_POINTS 保留选项但暂不生效。
- 新建默认 ORDERED_SEGMENTS、orderByColumns=[]、GAP、splitExpression=null。semantics 切换需确认，不清空旧选项。
- 任意 reconstruction 对象（包括非活动配置）要求 4.27。Contracts、Business、Compiler/Runner、前端同步；节点引入版本仍为 4.14，Manifest/Result/HTTP API 不变。
- 数组缺失/null 规范化空数组并防御性复制。结构不合法不导入；空字段、非法表达式/绑定属于可保存草稿，编译时标错。

### 次序、拆分与统计粒度

- 有序模式先排除无时间、NULL/Empty Geometry；无效几何仅在消费实际计划时以 `TRACK_RECONSTRUCT_GEOMETRY_INVALID` 失败。
- 按轨迹标识分区，先时间升序，再按 orderByColumns 声明顺序升序（NULL 在前）。同轨迹相同完整次序不唯一时运行失败 `TRACK_OBSERVATION_ORDER_NOT_UNIQUE`，不任意挑选顺序。
- 坐标排列、FIRST/LAST 使用同一复合排序键。相同时间可由明确 seq/event_id 区分；空次序列表要求同轨迹时间唯一。
- 时间 gap、距离 gap、固定周期和表达式为 OR。Gap 恰好等于阈值不拆分；表达式 true 从当前观测前拆分，NULL/false 不拆分。
- 默认 GAP 不生成跨拆分段；FINISH_LAST 把后段首观测同时纳入前段；START_NEXT 把前段末观测同时纳入后段。
  共享端点的时间、坐标和属性作为同一个观测复制，参与两段起止时间、点数和片段汇总，不伪造插值属性。
  官方示例确认两段共享端点；统计在共享端点下的完整官方服务对照仍待验收。
- **固定时间周期总是 GAP**，即使同时满足表达式/gap 条件也不得跨接。日历边界继续使用 4.21 的周期、参考时间和 IANA 时区。
- 最终少于两个观测的线片段跳过，不让单点组使整个任务失败。不同时间的重复坐标仍视为不同观测，允许零长度线；不增加去重或修复。
- 每片段一行，新增 BOUNDED 表，保留入口 Map，不继承 Watermark；不保证结果行顺序。

### 受控表达式与观测窗口绑定

复用现有 FILTER 的 Spark SQL 单表达式边界，不引入第二个脚本引擎。不支持 Arcade 变量、脚本语句或 TrackFieldWindow API 名称兼容。

示例：

```json
{
  "expression": "previous_speed * 2 < speed",
  "bindings": [{ "name": "previous_speed", "sourceColumnName": "speed", "offset": -1 }],
  "enabled": true
}
```

- 来源字段可直接引用；绑定引用同轨迹的偏移观测（-1 前一个，0 当前，+1 后一个），越界为 NULL。
- 最多 32 项绑定，offset 为 -1000～1000 整数；绑定名 `[A-Za-z_][A-Za-z0-9_]{0,127}`，大小写不敏感唯一，不得覆盖来源字段。
- 绑定只能引用入口来源字段，不能引用其他绑定；标量、时间及 Geometry 均可绑定。
  因而可表达时间差、前后字段比较及 Sedona 空间函数条件，而不是仅支持当前行字段。
- 绑定窗口只按轨迹和固定周期分区，不依据尚未生成的表达式/gap 片段循环计算；不会跨固定周期，也不会跨轨迹。
- 单表达式最多 8192 字符；禁止 SQL 语句、语句分隔符、注释、用户定义 OVER/WINDOW；Spark Analyzer 验证最终为确定性 BOOLEAN/NULL。
  随机条件、缺失字段和不可解析类型均返回 `INVALID_TRACK_SPLIT_EXPRESSION`。不在错误消息中回显表达式内容。
- enabled=false 保留表达式和绑定且不执行、不做业务校验；重新启用仍显示原草稿。
- 编译只构造惰性/零行计划，不执行表达式、扫描或收集轨迹到 Driver；未启用表达式不计算窗口绑定。
- 内部字段避开来源、用户绑定与输出字段；不会因用户绑定名类似内部命名而覆盖片段身份。

### 已实现 Inspector

```text
来源 / 点 Geometry / 时间    [ … ]
轨迹标识                    [ vehicle_id ]
距离方法                    [ 平面 | 测地线 ]
重建策略                    [ 有序片段 ▼ ]         (?)
同时间顺序                  [ event_id ▼ ]
拆分连接段                  [ 留空 / 归前段 / 归后段 ▼ ] (?)
表达式拆分                  已配置                 [设置]
轨迹拆分                    2 项边界               [设置]
片段汇总                    2 项                   [设置]
结果字段 / 输出表           [ … ]
```

表达式 Modal 为 860px：启用开关、布尔表达式、紧凑绑定 Table（绑定名/来源/偏移/问题/删除）。
取消丢弃本次弹窗修改；保存草稿允许表达式和绑定业务错误。关闭表达式保留内容，删除绑定二次确认。
主 Inspector 不展示表达式正文，Canvas/安全摘要只显示有序策略、连接段选项、表达式是否启用、边界/规则数量。

### 4.27 当时尚未完成的范围

以下记录保留 4.27 阶段的历史缺口；后续接入及当前收口状态见第 8～18 节。

- Point 缓冲/Polygon 顺序面轨迹的平面 XY 与逐行表达式已在 4.42 接入，4.43 提供数值观测窗口；测地面轨迹仍待实现。
- 4.28 已提供显式测地路径（下节）；ArcGIS 服务的精度/极区/近对跖点结果对照仍待验收。
- 字段非空 Count、字符串 Any 已在第 9 节（4.35）接入；完整时间单位及统计/NULL 的真实服务对照仍待完成。
- 单个超大轨迹的组内排序、内存和容量验收；当前继承组内 collect_list，不将“不 collect 到 Driver”作为无限容量承诺。
- 缓冲形态及独立设置已在第 10、11 节接入；目标 UI 中分离 gap/固定周期入口仍须与现有共享边界 Modal 核对，不因局部交付将节点整体勾选完成。

## 8. Canvas 4.28 显式路径几何

目标是按所选距离方法构造点轨迹，而不把 GEODESIC 仅用于距离 gap。该能力不改变旧任务，仍不是官方算法精度兼容声明。

```ts
interface TrackPathGeometryOptions {
  mode: 'METHOD_PATH' | 'LEGACY_VERTEX_LINE' | null;
  maximumGeodesicSegmentLength: number | null;
  maximumGeodesicSegmentLengthUnit: SpatialDistanceUnit | null;
}
// TrackReconstructOptions 新增：
pathGeometry?: TrackPathGeometryOptions | null;
```

- 原四参数 Java TrackReconstructOptions 构造器保留，pathGeometry=null。缺失/null 不加密且保持 LineString。
- 非空对象的 mode 缺失/null 为 METHOD_PATH；仅 ORDERED_SEGMENTS 生效。LEGACY_POINTS 或 LEGACY_VERTEX_LINE 保留非活动参数。
- 任何 pathGeometry 对象要求 4.28，包括非活动设置；低版本携带时返回 `TRACK_PATH_GEOMETRY_REQUIRE_SCHEMA_VERSION`。
  加载 4.27 有序策略不插入新对象；路径能力引入版本为 4.28，当前写出版本以 Contracts 常量为准，Manifest、Result 和 HTTP 不变。
- 新建默认 METHOD_PATH、10 KILOMETERS；10 千米是平台初值，不是 ArcGIS 默认或误差上限。

### 几何与数据粒度

- METHOD_PATH + GEODESIC：仅 EPSG:4326 XY；使用现有 GeographicLib 的 WGS84 逆解最短测地线，
  每对观测以 `ceil(距离 / 最大段长)` 等距离采样。日期线交点纬度从测地线求解，不用纬度线性插值。
  日期线两侧分别以 ±180° 结束/开始部件，输出 MultiLineString，SRID=4326、XY。
  极区沿日期线的浮点数仅做 1e-12 度边界归一化，不构成输入修复或业务精度容差。
- METHOD_PATH + PLANAR：原始有序顶点组成线并包装 MultiLineString，保留来源 CRS/坐标维度；不读取非活动测地段长。
- 插值点仅用于 Geometry；不产生虚构时间/字段值，不增加点数、不影响 FIRST/LAST 或其他统计。
  4.27 的共享端点仍是真实观测并参与两段统计；固定时间周期仍不跨接。
- 点重复（包括 ±180° 等价位置）可构成零长度线；有序片段少于两个观测仍跳过。
  非有限坐标、经度越界、纬度越界在真实消费计划时返回 `TRACK_GEODESIC_COORDINATE_INVALID`。
- 最大段长必须为有限正数，单位只能为米/千米/英尺/英里/海里，不接受来源经纬度单位；
  Compiler 返回精确字段路径的 `INVALID_TRACK_GEODESIC_SEGMENT_LENGTH`，不执行加密、不读真实数据。
- 每个输出片段最多一百万顶点（含日期线切分端点），超过后 `TRACK_GEODESIC_VERTEX_LIMIT_EXCEEDED`，
  不截断；建议增大段长或配置拆分。此保护不能消除既有 collect_list 的组内内存边界，不能宣称任意大轨迹都可处理。
- Runtime UDF 随现有惰性计划执行，不增加 Action/缓存/Driver collect；错误不可重试，摘要只记录路径策略，不记录坐标或表达式。

### Inspector 与 Canvas

```text
距离方法       [平面 | 测地线]
重建策略       [有序片段 ▼]
路径几何       [按距离方法 · 多部件线 ▼] (?)
测地最大段长   [10] [千米 ▼]             (?)  ← 仅测地 METHOD_PATH
同时间顺序     [event_id ▼]
拆分连接段     [留空 ▼]
表达式 / 边界 / 统计                     [设置]
```

- 路径策略切换需确认，明确 LineString/MultiLineString 对下游的影响。
- 切换平面、旧路径或旧重建策略不清空段长；重新启用恢复原值。业务无效草稿仍可应用。
- 正数/单位错误直接就地显示；平台初值、插值粒度、容量限制放在邻近帮助，不占常驻大段说明。
- Canvas 展示实际目标几何种类，摘要记录“多部件路径”；不展示坐标/属性值。

本地验收覆盖高纬弧线、日期线双向/精确端点/反复跨越、极区/对跖点有限输出、重复点、非法坐标/超限、
Spark 原观测统计不变及平面 XYZ。后续字段统计、平面面轨迹和缓冲窗口见第 9～11 节；测地面、完整时间单位、官方服务精度和大轨迹容量仍未完成。

## 9. Canvas 4.35 字段 Count/Any 与统计草稿

`TrackSummaryStatistic.kind` 新增 `COUNT_FIELD`、`ANY`，现有字段和节点引入版本不变；
使用新枚举（包括驻留非活动配置）要求 4.35，低版本返回 `TRACK_FIELD_STATISTICS_REQUIRE_SCHEMA_VERSION`。
本项引入于 Canvas 4.35，当前写出版本见 Canvas Definition 常量；Manifest/Result/HTTP API 不变，旧 COUNT、首末及统计数学语义不变。

| 配置 | 当前执行语义 | 官方对照/差异 |
| --- | --- | --- |
| COUNT | 当前片段成员数，与固定点数字段一致；不使用来源字段 | 独立于 summaryFields 字段 Count；可不额外配置 COUNT |
| COUNT_FIELD | 非 NULL 字段值计数，空字符串及重复值计数；全 NULL 为 0，LONG | 对应 summaryFields Count；允许非 Geometry 标量是平台扩展 |
| ANY | 取一个非 NULL 字符串值；全 NULL 为 NULL，保留字符串类型 | 重建 REST 指明字符串；不是排序 FIRST，不保证重跑样本相同 |
| FIRST/LAST | 时间及已配置同时间次序的首末观测字段值，首末 NULL 不跳过 | 旧策略时间平局仍不保证确定，不改变原有 min_by/max_by |
| SUM/MEAN 等 | 数值算法保持；Schema 使用 Spark 实际提升后的类型、精度和可空性 | INTEGER SUM 为 LONG、Decimal 聚合扩精度；不伪报来源类型 |

汇总只统计最终片段成员：FinishLast/StartNext 的共享观测会分别参加对应片段统计；
不另扫原输入计算隐藏总数。空输入不生成虚构统计行。STDDEV/VARIANCE 仍为样本公式 n−1，
全 NULL 或不足两项为 NULL；官方精确分母/退化结果仍待服务对照。MIN、首末支持其他可比较标量属于平台扩展。
新枚举不改变重建顺序、路径、Geometry 类型或事件时间/Watermark 规则；本轮不是完整轨迹血缘验收。

### 当前统计面板

```text
片段汇总  N 项                                  [设置]
┌ 设置轨迹片段汇总                                    ┐
│ 片段字段统计 (?)                         [+ 添加汇总]│
│ COUNT_FIELD · 非空数   speed         speed_count     │
│ ANY · 样本值          vehicle_type  example_type     │
│ 每行：类型 / 来源 / 输出 / 上下移 / 删除              │
│                           [取消] [保存汇总草稿]      │
└─────────────────────────────────────────────────────┘
```

- 打开时复制当前配置，取消不改变 Inspector；保存才提交这一组汇总，普通业务错误仍可保存。
- 切换 COUNT 再切回保留本次编辑的原来源；失效字段不清空，Compiler 尚不可用时显示等待解析。
- 输出空白/重复、来源缺失或已知类型不适用就地标红；删除需确认下游字段影响。
- 帮助按需说明计数、NULL、采样不确定性、首末和方差。Canvas/日志只显示汇总数量，不显示样本或计算值。
- 面/缓冲轨迹、完整单位、容量及官方结果对照继续按路线图推进，不因统计枚举补齐标记节点全部完成。

## 10. Canvas 4.42 显式平面面轨迹

本阶段补齐可独立使用的平面面轨迹，不代表整节点已与 ArcGIS 对齐。官方证据：

- [Enterprise 11.3 Portal](https://enterprise.arcgis.com/en/portal/11.3/use/geoanalytics-reconstruct-tracks.htm)：
  支持点/面 instant 观测；线结果需多于一个点，缓冲结果保留全部观测；先缓冲每个输入，再构造凸包形成面轨迹。
- [GeoAnalytics REST](https://developers.arcgis.com/rest/services-reference/enterprise/geoanalytics/tasks/reconstruct-tracks/)：
  按时间顺序连接缓冲点/面，bufferField 可为字段或表达式。REST 的 Planar 默认与 Portal 的 Geodesic 默认不同，
  因而不把平台初值标注为跨入口通用的官方默认。
- 官方文字未完整规定相邻凸包的精确顶点/容差、零/负半径与含洞面处理。下述为明确的平台规则，仍需真实服务对照。

### 契约与兼容

```ts
interface TrackAreaGeometryOptions {
  enabled?: boolean | null;
  bufferMode: 'NONE' | 'FIELD' | 'EXPRESSION' | null;
  bufferField: string | null;
  bufferExpression: string | null;
  bufferUnit: SpatialDistanceUnit | null;
}
// TrackReconstructOptions 增加 areaGeometry?: TrackAreaGeometryOptions | null
```

- 旧配置缺失/null 不启用。对象 enabled 缺失/null 等同 true，仅 ORDERED_SEGMENTS 生效。
  新建线轨迹不插入此对象；首次切为面轨迹才创建草稿，选中 Point 时默认 FIELD、已知 Polygon 时默认 NONE。
  默认单位 METERS 为平台初值，不猜填业务距离字段或常量。常量距离可用数值表达式显式输入。
- 任意非 null 对象（包括 enabled=false、旧重建策略、非活动字段）要求 4.42，Business、Compiler 与前端导入均门控。
  保存/导出规范化为当前小版本，旧线轨迹行为不变。保留 Java 原四/五参数构造器，areaGeometry 默认 null。
- 不改已有 pointGeometryColumnName / pointCountColumnName 字段名，UI 改称“观测 Geometry / 观测数字段”；
  不改变节点引入版本、Manifest、Result 或 HTTP API。

### 算法与执行边界

1. 沿用有序重建：NULL 时间、NULL/Empty Geometry 排除；有效性、完整排序键和原 Geometry 距离拆分继续沿用。
2. FIELD 读取当前原始观测字段；EXPRESSION 使用单个确定性 Spark SQL 数值表达式，如 `radius * 2`。
   不允许完整 SQL、子查询、聚合、生成器、OVER、随机函数，也不引用拆分表达式的窗口绑定或内部字段。
   距离先按单位换算到来源 CRS，构造每观测缓冲；bufferMode=NONE 仅适用于 Polygon/MultiPolygon。
3. 每段按确定次序取相邻两个观测面凸包，最后合并相邻连接面。不会对整个片段做一次全局凸包，
   也不是只 Union 离散观测面；转弯轨迹不被整体填平。单个面观测保留原面（包括孔洞）；多观测凸包会填平连接区孔洞。
4. 只有真实片段成员参加起止、COUNT、FIRST/LAST 和汇总。FinishLast/StartNext 的共享观测在两个片段中保留同一缓冲值；
   固定周期始终 GAP。单个有效面观测也输出一行；空输入不生成结果，线轨迹的单观测过滤不变。
5. 始终输出来源 CRS 的 MultiPolygon、XY、BOUNDED，无事件时间/Watermark；结果附加到输入 Map，不泄漏内部缓冲列或覆盖上游。
   Compiler 只分析惰性计划，不执行缓冲或 Spark Action、不读外部系统；真实数据问题在下游消费计划时失败。

当前只支持 PLANAR 和 XY，明确拒绝测地面轨迹及 Z/M，不静默投影/降维。
投影 CRS 可用线性单位；地理 CRS 的平面缓冲只能显式使用 SOURCE_CRS_UNIT（度），不会跨日期线走短路。
圆弧离散固定每象限 16 段，是平台近似参数，不是 ArcGIS 精度承诺。
点缓冲必须是有限正数；面缓冲可为零，不接受 NULL、负数、NaN、无穷值；不静默删除半径无效的观测。

每个片段的观测缓冲面顶点总数及最终结果分别限制为一百万；超限失败，不截断。
仍使用组内 collect_list 和 JTS 合并，保护并不能避免收集阶段的内存边界，不承诺无限规模。
JTS 拓扑错误不附带含坐标的原始消息到安全异常链。

| 稳定错误 | 发生位置 |
| --- | --- |
| TRACK_AREA_GEOMETRY_REQUIRE_SCHEMA_VERSION | 三端版本检查，含隐藏草稿 |
| TRACK_AREA_METHOD_NOT_SUPPORTED / TRACK_AREA_XY_REQUIRED | 编译配置：不支持的距离方法或维度 |
| TRACK_AREA_GEOMETRY_REQUIRED / TRACK_POINT_BUFFER_REQUIRED | 编译配置：输入形态或 Point 缺缓冲 |
| INVALID_TRACK_BUFFER_EXPRESSION | 编译配置：字段/表达式不可作为确定性逐行数值；精确到字段路径 |
| TRACK_BUFFER_DISTANCE_INVALID | 实际半径不合法，SCHEMA、不可重试 |
| TRACK_AREA_GEOMETRY_INVALID | 实际几何或面连接无法计算，SCHEMA、不可重试 |
| TRACK_AREA_VERTEX_LIMIT_EXCEEDED | 实际片段顶点超过保护，SCHEMA、不可重试 |

### 本阶段 UI

```text
观测 Geometry / 时间       [ shape ▼ ][ event_time ▼ ]
轨迹标识 / 同时间次序      [ track_id ][ event_id ]
距离方法                  [ 平面 | 测地线 ]
重建策略                  [ 有序片段 ▼ ]
输出形态                  [ 面轨迹 · 平面 XY ▼ ]
面轨迹设置                字段缓冲 · N 个问题 (?)   [设置]
拆分连接段 / 表达式 / 边界 / 汇总                   …

┌ 设置面轨迹 · 680px ───────────────────────────────┐
│ 缓冲距离来源 (?) [ 数值字段 ▼ ]                    │
│ 距离字段         [ radius ▼ ]                      │
│ 距离单位         [ 米 ▼ ]                          │
│ 或：受控数值表达式（自增高文本框、按需说明）        │
│ N 个配置问题 (?)               [取消] [保存草稿]   │
└───────────────────────────────────────────────────┘
```

- 形态切换先确认，说明 Geometry/单观测行为对下游的影响；路径和缓冲配置双向保留。
- 独立 Modal 草稿，取消不提交；切换 FIELD/EXPRESSION/NONE 不删除其他分支值，缺失字段继续保留并提示失效。
  Compiler 不可用时显示等待解析，不假定上游无字段；业务无效仍可保存。
- 面轨迹时隐藏线几何/测地加密设置；与测地方法组合时直接显示配置问题，不自动切方法或悄悄改平面。
- Canvas 只显示 MultiPolygon、缓冲来源类别、轨迹标识/统计/边界数量；安全摘要不展示距离值、表达式、坐标或轨迹 ID 值。

### 4.42 当时继续待完成的范围

以下记录保留 4.42 阶段的历史缺口；测地面链路与当前收口状态见第 17、18 节。

- 测地缓冲、相邻测地面连接、日期线面切分；当前明确不可选用该能力组合，不作为完整 GA 支持交付。
- 缓冲表达式的数值观测窗口已在 4.43 接入（下节）；不是 Arcade API，完整官方行为仍待对照。
- 官方服务对照：相邻/整体凸包定义、容差、孔洞、负半径/零半径、极区及统计公式。
- 全页面浏览器、百万级轨迹/大多边形组的容量与数值精度验收。整个 Reconstruct 仍保持未勾选状态。

## 11. Canvas 4.43 缓冲观测窗口绑定

对应 Enterprise 11.3 Portal 的 track-aware buffer expression（例如按前几次 wind_speed 的均值缓冲）。
平台沿用受控 Spark 数值表达式，不仿造 `$track`/Arcade API。偏移采用明确的闭区间，
不能将 Portal 的 history(-4,-1) 文本直接拷贝为平台窗口；前 3 次观测明确写为 -3～-1。

```ts
interface TrackBufferWindowBinding {
  name: string;
  sourceColumnName: string;
  startOffset: number | null;
  endOffset: number | null;
  statistic: TrackSummaryStatisticKind | null;
}
// TrackAreaGeometryOptions 新增：
windowBindings?: TrackBufferWindowBinding[] | null;
```

### 语义与作用域

- 所有窗口绑定读取原始数值字段，不按绑定数组顺序形成链，也不能引用其他绑定或平台临时列。
  示例：history_mean → wind_speed / MEAN / -3～-1；表达式填写 `coalesce(history_mean, wind_speed)`。
- 仅 `ORDERED_SEGMENTS + areaGeometry.enabled + EXPRESSION` 生效；切到 FIELD、NONE、线或旧策略保留全部草稿而不执行。
  缺失/null 规范化为空数组，不自动为旧配置添加绑定。原五参数 Java 构造器保留且绑定为空。
- 非空数组含非活动值要求 4.43；空数组无需新语义。Business、GraphPlan、前端导入一致门控，旧任务保存为当前小版本。
  不改变 Manifest、Result、HTTP API 或节点引入版本。
- 每项按轨迹标识和可选固定周期分组，用原时间和同时间顺序排序；沿用唯一完整次序的运行校验。
  NULL/Empty Geometry 与无时间记录先排除，不占偏移位置。偏移表示有效观测的序号，不是时间间隔。
- 开始/结束均包含：0 当前；负数为过去；正数为后续。允许 -1000～1000，开始不得大于结束，最多 32 个独立窗口。
  新增行给出 -3～-1 / MEAN 作为平台草稿初值，字段和绑定名留空，不猜业务字段。
- 窗口在普通时间/距离 gap、拆分表达式以及 FinishLast/StartNext 共享端点之前计算；固定周期始终隔离。
  因而一个真实观测被共享到两段时保持同一缓冲半径，不因目标片段不同重算历史。
  缓冲窗口与拆分窗口是两个作用域，可以同名，但不会互相引用；缓冲绑定列在形成 footprint 后即从计划输出中移除。
  不改变输入 Map、统计样本数或输出 Schema，不新增缓存、Action 或 Driver 收集。

### 数值与缺失数据

| 统计 | NULL 与空窗口语义 |
| --- | --- |
| COUNT_FIELD | 统计非空数值；空窗口/全 NULL 为 0 |
| SUM / MEAN / MIN / MAX / RANGE | 忽略 NULL；空窗口/全 NULL 为 NULL |
| STDDEV / VARIANCE | 样本公式 n−1，少于两个非空值为 NULL |
| FIRST / LAST | 包含 NULL，按相同确定次序取窗口首末；空窗口为 NULL |

窗口函数由平台配置构造，表达式只能组合已计算绑定与原始字段，不允许用户自行写 OVER、聚合、子查询、生成器或随机函数。
不隐式补零、补业务默认值或丢弃历史不足的观测。最终半径为 NULL、非有限或不满足点/面半径规则时，
仍在真实消费计划时返回安全的 `TRACK_BUFFER_DISTANCE_INVALID`；用户可显式使用 coalesce、case 等处理。
单位对最终表达式结果统一换算，不分别变换窗口中的原始值。
字段、名称、范围和统计类型错误使用数组下标路径；业务无效草稿可保存，结构非法则拒绝导入/应用。

### 当前设置面板

```text
设置面轨迹 · 680px
缓冲距离来源          [受控数值表达式 ▼]
距离表达式 (?)        [coalesce(history_mean, wind_speed)]
观测窗口绑定  1 项 (?)                            [+ 添加]
┌ 绑定 / 字段 ─────┬ 统计 ────┬ 起止偏移 ───┬ 操作 ─┐
│ history_mean     │ 均值     │ [-3] [-1]    │ ↑ ↓ × │
│ wind_speed ▼     │          │              │       │
└─────────────────┴──────────┴──────────────┴───────┘
距离单位              [米 ▼]
N 个配置问题 (?)                       [取消] [保存草稿]
```

- 沿用独立面轨迹 Modal 草稿，取消不提交；窗口完整排序，删除需确认并提醒表达式引用不会自动改写。
- FIELD/EXPRESSION/NONE 切换不清空绑定或表达式；无效/上游失效值保留，等待 Compiler 时不误报字段已删除。
- 规则表内部滚动，超过 32 项的导入草稿保留并标错；达到上限后禁用新增。
- Canvas/摘要/Runner 只增加活动窗口数量，不展示表达式、窗口计算值或轨迹数据。

### 对齐与待验收

已覆盖“基于观测历史/后续范围的数值统计驱动缓冲”这一业务能力，但不承诺 Arcade 字符串/数组函数、动态窗口边界或脚本兼容。
固定周期隔离、窗口在 gap 拆分前计算、首末 NULL 和样本方差均为明确平台规则，真实 ArcGIS 服务的顺序及数值对照仍待验收。
测地面几何、日期线面切分、单轨迹容量和全页面验收仍保留在完整路线图中。

## 12. 测地面轨迹开发中：WGS84 点缓冲边界

状态：内部几何基础已实现，**尚未接入 TrackAreaPlan**。Canvas 仍为 4.43，没有新增 JSON 字段、
UI 可选项或 Manifest/Result 版本。测地面组合继续按现有 `TRACK_AREA_METHOD_NOT_SUPPORTED` 拒绝，
不能把本节作为已支持完整测地面轨迹的证据。

### 已实现的几何基础

- `TrackGeodesicDisk` 使用现有 GeographicLib WGS84 正解，按每个点自己的米制半径生成圆周采样，
  不把经纬度作平面 Buffer，不用轨迹线的统一后置缓冲替代逐观测缓冲。
- 采样粒度是内部方法参数：至少 64 个方位，按 `ceil(2πr / 最大弧长)` 向四的倍数取整。
  这是边界离散粒度，不是对整个多边形声明的最大位置误差，也不是 ArcGIS 默认值。
- 日期线交点沿圆周方位角用 WGS84 正解二分求解；不是两采样点间纬度的线性插值，
  也不是把圆周边界换成端点间最短测地线后再求交。
- 独立保存不可变真实圆周采样；渲染时才增加包围极点所需的闭合边，按日期线切分并输出 EPSG:4326 XY MultiPolygon。
  人工极点闭合边不得成为后续凸包输入；边界加密、日期线交点及极点闭合均不得增加观测数或片段统计。
- 等价的 ±180° 经度与极点任意经度规范化到同一表示；每次渲染构造独立 Geometry，不修改来源或采样快照。
- 当前内部原语只处理半径小于 WGS84 四分之一子午线的小圆盘；达到该范围返回
  `TRACK_GEODESIC_BUFFER_RANGE_NOT_SUPPORTED`（SCHEMA、不可重试、安全摘要不含半径）。
  这是避免错误选择球面补集的阶段性边界，**不是将完整目标缩减为小半径点轨迹**；
  大区域、半球/对跖点域仍须在完整面几何方案中处理或依据明确产品要求裁决。
- 非法半径、坐标及百万顶点保护复用现有安全错误。顶点数量在采样前及构造中检查；
  JTS 渲染失败不保留可能包含坐标的原始消息或 cause。

### 数值边界与后续接入

- 方位采用半步偏移，避免圆周恰好与极点相切时采样到经度未定义的极点；离散边界不承诺精确保留这种单点相切拓扑。
  容差、极点相切和大区域的最终结果语义仍须验收，不能把有限采样当成精确连续曲面。
- 下一步仍需 Polygon/MultiPolygon 观测的测地缓冲（含孔洞）、相邻观测的测地面连接以及整段面合并；
  不使用拆成日期线片段后的平面凸包，也不使用整个轨迹的全局凸包代替相邻连接。
- 接入时必须保留 4.43 的逐观测字段/窗口表达式、原 Geometry 拆分、共享端点半径、Map 顺序和血缘；
  仅当完整执行路径就绪后才同步协议门槛、Inspector、Compiler/Runner 和安全摘要。
- 本地几何验证与 Spark UDF 执行证据记录在[进度清单](../canvas-spatial-development-progress.md)；
  它们不证明已完成 ArcGIS 服务结果、完整节点或规模验收。

## 13. 测地面连接核心与极点经线修正（内部开发）

本节继续第 12 节：新增 `TrackGeodesicHull`，并把不可变真实采样与 MultiPolygon 渲染抽到
`TrackGeodesicAreaBoundary`，供点缓冲和连接边界共同使用。尚未接入 TrackAreaPlan，协议仍为 4.43；
未开放 UI 选项，也没有将完整测地面轨迹缩减为只支持点或小区域。

### 相邻连接

- 连接输入为两次观测的真实缓冲/形状采样顶点；不得输入日期线切分后的平面多边形闭合边。
  包围极点时渲染补入的极点边也不参与凸包。
- 调用方显式提供局部参考点；点观测对使用 WGS84 最短测地线中点，不平均经纬度。
- 使用 WGS84 逆解方位排序和方向判断构造顺时针凸边界；每条结果边都验证所有原采样点位于其内侧。
  不以经纬度平面凸包或投影凸包直接代替测地凸包；出现无法验证的边界返回安全错误，不返回部分近似结果。
- 最终边沿 WGS84 最短测地线加密，并复用测地交点求解、日期线切分与极点闭合，输出 EPSG:4326 XY MultiPolygon。
  加密参数限制段长，不是完整曲面位置误差保证；方向判定的 `2e-14` 正弦容差为浮点工程边界，非 ArcGIS 官方容差。
- 连接只作用于相邻两次观测。折弯轨迹应合并相邻连接面，不能对整段所有观测再取一个全局凸包；
  原半径与真实观测统计不因边界加密或连接改变。
- 当前内部验证域要求所有采样点距参考点小于 `π × WGS84 半短轴 / 2`；域外不自动选球面补集。
  该局部原语不代表大区域/半球/对跖点域已经完成；完整域处理仍留在开发目标中。
- 采样顶点最多一百万，逐边支持检查最多两千万次；在二次验证循环前检查计算量。
  该算法没有 Driver Action，但不能据此宣称大单轨迹无限容量或与官方服务性能等价。

新增实际几何错误均为 SCHEMA、不可重试，Runner 摘要不含坐标：

| 错误 | 含义 |
| --- | --- |
| TRACK_GEODESIC_HULL_INVALID | 退化或无法验证包含全部采样点的凸边界 |
| TRACK_GEODESIC_HULL_RANGE_NOT_SUPPORTED | 不满足当前局部凸域或近对跖点限制 |
| TRACK_GEODESIC_HULL_WORK_LIMIT_EXCEEDED | 逐边支持检查超过计算量保护 |

### 极点边界修正

- 极点经度无定义，面边界在入射和出射经线上分别表示同一个物理极点；边中途经过极点时也先显式拆分。
  经度差恰好 180° 时按顺时针小区域选择北极向东/南极向西的零物理长度闭合边，避免误选极区补集。
- 修正共享 `TrackGeodesicPath` 的极点端点表示：出发使用下一点所在经线，到达使用上一段经线，
  不让 GeographicLib 在极点选择的等价经度产生斜向经纬度弦。来源 Geometry 不修改，物理极点、观测数、
  统计和非极点路径不变；这是现有测地线渲染修正，不增加配置开关或协议字段。

### 仍需完成的接入边界

- Polygon/MultiPolygon 测地缓冲及孔洞、整段连接合并、范围与单组容量、完整 Operator/Compiler/Inspector 接入。
- 非点距离 gap 不能直接沿用现有 `ST_DistanceSpheroid`：当前 Sedona 1.9.0 的
  `org.apache.sedona.common.sphere.Spheroid.distance` 对非 Point 取 `getCentroid()` 后测距（已检查本地制品字节码）。
  这不是面边界最短距离；在确认官方多边形距离语义并补齐对应距离实现前，不能把该调用当成“原 Geometry 测地距离”已完成。
- 官方数值/容差、极区和大域服务对照、全页面与规模验收继续开放。内部 UDF 的零 Job 预检和本地测试
  不作为整节点可发布或完整 ArcGIS 对齐证据。

## 14. Polygon/MultiPolygon 测地足迹与有序连接（内部开发）

继续第 12、13 节，增加 `TrackGeodesicPolygon` 和 `TrackGeodesicAreaGeometry`。两者仍未接入
`TrackAreaPlan`，Canvas 保持 4.43，配置面板和 Compiler 仍不开放测地面组合。本节记录内部实现，
不替代完整节点验收，也不将全球域或非点距离从目标中删除。

### 观测足迹

- 输入 EPSG:4326 XY Point、Polygon、MultiPolygon；来源 Geometry 不修改。点使用独立正半径圆盘，
  面支持 NONE 或非负半径；NONE 忽略非活动半径，零半径保留原测地面与孔洞。
- 面环采用 WGS84 最短测地边，依椭球有向面积统一环方向。真实边加密后再进行日期线切分，
  不把原始经纬度 WKT 的平面拓扑检查当成地理拓扑结论。
- 对每条外环/内环边生成测地法向偏移带：沿原 WGS84 边采样位置，在该处前向方位的 ±90° 方向
  按米制半径求正解；每个原顶点增加圆形连接。合并原区域、偏移带和圆形连接，正缓冲使孔洞缩小或闭合。
  不用端点圆盘凸包代替长边等距缓冲（高纬长边会因此过度扩张），也不把凹面替换为凸包。
- 多部件保持独立，只有实际缓冲相交后才合并。南北极凹顶点允许大于 180° 的内部转角，
  该经度闭合表示零物理长度，而非长测地边；拓扑检查仍拒绝自交、孔洞越界和部件共享边等无效结构。
- 以外环顶点单位向量均值确定局部参考，仅用于算法域与拓扑检查，不用于代替原面测距或缓冲。
  初期使用的局部等距方位图拓扑检查已由原始连续弧、环关系和接触连通检查替代，见第 16 节。
  不将人工日期线/极点闭合边当成真实边，渲染布尔仍单独检查输出有效性。

### 有序装配与内部数据

- 每次观测的 `Footprint` 保存三部分：局部参考、不可变真实凸包候选顶点、独立的 MultiPolygon 区域快照。
  不依赖 `Geometry.userData` 保存真实顶点；不能从已切分的 Geometry 逆推凸包输入。
- `connect` 保留各观测原区域，并仅对相邻两次观测的真实候选生成测地凸包，最后合并。
  单观测不生成连接面，保留其孔洞；折弯轨迹不生成整个轨迹的全局凸包。
- 顶点采样、偏移带和装配均累计检查一百万顶点保护，连接继续遵循两千万次支持检查。
  面路径中的 `TRACK_GEODESIC_VERTEX_LIMIT_EXCEEDED` 统一转为 `TRACK_AREA_VERTEX_LIMIT_EXCEEDED`；
  JTS 错误仅保留安全代码，不携带实际坐标或原始 cause。
- `TrackGeodesicAreaColumns` 使用明确 Catalyst Struct 携带参考经纬度、真实顶点数组和 Geometry 区域，
  提供惰性 footprint 与有序 connect 表达式；没有新增稳定 JSON 或运行 Manifest 字段。
  解码累计检查顶点数量，避免只在全部观测展开后才触发容量保护。
- 已验证该结构经过 Shuffle、collect_list、按真实观测次序排序后仍能正确连接；不同轨迹独立，
  日期线单观测保留孔洞，极点渲染闭合不混入真实顶点，不同观测保持各自半径。
  这是内部列传输证据，尚未接入 Operator 的普通 gap/固定周期/共享端点整条管道。

### 精度、范围与验收状态

- 最大段长约束仍是采样粒度，不是整个连续曲面的误差界。布尔运算发生在日期线切分后的离散区域上；
  不宣称解析测地布尔运算、官方默认精度或 ArcGIS 数值等价。
- 局部域沿用第 13 节，正缓冲的偏移采样也要满足该域；大区域、半球/对跖域仍未完成。
  非点 gap 不得回退为 Sedona 的质心距离，真实距离和完整 Operator 接入继续待办。
- 局部 Union 测试观察到约 `2.47×10⁻¹⁹` 平方度浮点残差，验证同时检查丢失面积比例与原顶点到结果的距离；
  没有通过扩大、吸附或改写生产结果来满足精确 `covers` 布尔断言。这不是算法的总体精度保证。
- 13 项 Polygon 专项与 2 项新增 Spark 专项覆盖凹面/孔洞、长边法向偏移、日期线、南北极、反转方向、
  多部件、相邻连接、非法拓扑、安全错误和快照隔离；Spark 分析阶段零 Job，真实 Executor 返回有效 MultiPolygon。
  连同点/凸包/路径、既有面轨迹和空间节点、Runner 分类，共 174 项通过，详见[进度记录](../canvas-spatial-development-progress.md)。
  未完成真实 ArcGIS 服务、浏览器、全球域或规模验收。
- Spark 内部 Struct 补充后 `TrackAreaSparkTest` 共 22 项通过（`/tmp/datascalpel-geodesic-area-columns.log`），
  包括零 Job 分析与上述 Shuffle/顺序/极点隔离用例；不以内部列已具备替代完整节点接入验收。
  最终重跑全部上述专项共 176 项通过：`/tmp/datascalpel-geodesic-area-final-backend.log`。

## 15. 非点距离接入准备

新增共享内部 WGS84 线段最近位置搜索与 Point/线性 Geometry 入口，返回实际位置对距离及未采样位置下界。
有限测地弧参数域全局细分，不用质心、投影或只比较端点；共享预算超限时不返回未验证结果。
设计、数值裕量、算法边界与安全代码见[WGS84 非点距离](../canvas-wgs84-geometry-distance.md)。

第一步完成内部线性入口，Polygon 区域/孔洞/接触语义进入后续实现。未修改 TrackNodeSupport 的运行行为，
未开放测地面组合；接入时仍须保证 gap 使用原观测、处理区间与阈值重叠的精度问题、保留共享端点半径及统计。
官方文档未明确多边形质心规则，不将现有 Sedona 调用当成已对齐证据；Canvas 仍为 4.43。

后续内部区域入口已补 WGS84 原始环方位绕数、孔洞/多部件、真实内含位置距离零及全局边界区间搜索；
全局队列与带曲率裕量的弦下界修正了简单面边对提前耗尽预算的问题。212 项回归通过，详见共享距离设计。
仍未接入 TrackAreaPlan：连续近接触拓扑、局部域外区域、严格 gap 阈值与共享端点/血缘完整管道继续待办，
不能把已有内部面距离当成测地面节点已可发布。

连续拓扑准备新增原始 WGS84 弧交叉/重叠检查、保守 ECEF 候选剪枝与明确未决错误。
合法外环/孔洞 T 接触先插入真实公共顶点再采样，避免离散弦错误排除孔洞；不修改来源 Geometry，
不把近接触吸附为接触。孔洞切断内部仍拒绝。该阶段剩余的域内区域包含与内部连通离散检查
已由下节替代；全域、严格 gap 阈值及完整节点接入仍未完成，详见[共享距离设计](../canvas-wgs84-geometry-distance.md)。

## 16. 来源区域拓扑与渲染分离（内部开发）

原始局部区域检查现由 `prepareSource` 与 `Wgs84PolygonTopology` 完成：孔洞归属/嵌套、
公共顶点穿越、多部件填充区域重叠和孔中岛、环—接触位置图的内部连通均使用原始边。
不同环同点相遇不自动构成断开；真正接触环路仍拒绝。边对检查与区域检查共享一次预算。

已删除来源区域的采样等距方位图有效性检查。测距准备不再创建 Footprint 渲染面，
原始合法面不因输出采样粒度或渲染顶点容量而被拒绝；测距入口移除内部采样参数。
Footprint 的偏移带与圆形连接仍需采样、日期线切分与平面布尔，不能把本次分离当成解析测地面输出已完成。

新增 8 项源区域专项与 1 项实际 Executor 专项，累计 238 项回归通过；分析阶段零 Job。
测地面 Operator/Inspector 仍未开放，严格距离阈值、公共交叉位置、局部域外区域及完整血缘/管道继续待办。

阈值内部入口后续已增加 `distance <= threshold` 区间判定，区间跨阈值时在同一搜索队列继续求精，
不是先算粗距离再用 `>` 决定 gap。原始内含位置可证明零；近数值边界和无法证明的零阈值接触仍安全失败。
实际 TrackNodeSupport 调用及测地面完整链路尚未替换，不能将内部谓词当成面轨迹已开放。
专项进度见[共享距离阈值语义](../canvas-wgs84-geometry-distance.md#距离阈值的区间判定)。

零阈值后续增加连续边界 INTERSECTING / DISJOINT / UNRESOLVED 证据，可在已验证局部域内
确认没有原始公共顶点的边内相交，并以保守弧盒排除分离边对。它只决定阈值 Boolean，
不虚构相交坐标；真实最近位置、大域、数值未决边界与整条测地面链路继续待办。

## 17. Canvas 4.44 测地面链路接入

可选 `areaGeometry.geodesicBoundary` 独立包含 `maximumSegmentLength` 与 `maximumSegmentLengthUnit`。
缺失/null 不升级旧平面或线配置；任何非空对象，包括 LEGACY、enabled=false、PLANAR 下的隐藏草稿，
均要求 4.44。Business、GraphPlan 和前端导入同步门控；旧五/六参数 Java 构造器仍可用。
Manifest、Result、HTTP API、节点引入版本与 BATCH 模式不变。

运行链路：原始测地来源校验 → 原观测距离/时间边界 → 固定周期内缓冲窗口 → 每观测足迹 →
表达式拆分与段归属 → 有序足迹装配。全部为惰性计划；Compiler 不执行 UDF 或触发 Spark Job。

- Point/Polygon/MultiPolygon 均须实际 EPSG:4326 XY。面环检查原始测地弧/孔洞/部件关系；
  合法测地面不因原始经纬度 WKT 的平面 IsValid=false 而被拒绝。
- 距离 gap 比较原 Geometry 的完整区域，不用质心或缓冲面。直接求 `distance <= threshold` 证据，
  区间跨阈值时继续求精；未决按现有安全精度/计算量错误退出，不擅自扩大阈值。
- 缓冲值先按显式线性单位换算成米。原始足迹的真实顶点与渲染辅助边分开，以私有 Spark Struct
  穿过 Shuffle、排序和共享端点；共享观测的窗口半径不按目标片段重新计算。
- 固定周期仍始终 Gap，缓冲窗口不跨周期；普通 gap/表达式保留三种归属。NULL/Empty 与无时间观测
  不参加轨迹，单个有效面观测保留，统计依旧按真实片段成员，不增加插值样本。
- 结果 EPSG:4326 XY MultiPolygon、有界且无 Watermark；原表及入口 Map 顺序保持，结果追加。
  原几何和数值字段经过 UDF/Struct 的字段血缘继续由 Catalyst 分析，不改为手工伪造完整来源。

当前面板：在原 680px 面轨迹 Modal 中，GEODESIC 才显示“边界采样最大段长 [数值][单位]”。
初始数值留空，单位可显式选择；字段帮助解释离散粒度、局部域和容量。PLANAR/线分支隐藏但保留草稿，
取消不提交，业务无效配置仍可保存。常驻只显示问题数量；Canvas 增加 WGS84 测地面标签，
Runner 只记录距离方法与是否配置采样，不记录采样数值、半径、坐标或表达式。

本次不是全球域或官方数值等价交付：大区域/半球/对跖域、未决数值边界、官方窗口/统计对照、
容量和真实页面验收继续在完整路线图中。面布尔仍针对离散渲染区域；采样最大段长不是连续曲面位置误差保证。

## 18. 当前明确支持范围收口（2026-09-13，Canvas 4.76）

本次按平台已经声明且可验证的范围完成收口，不把安全拒绝的大域或未完成的 ArcGIS 官方对照伪装成支持：

- Point 线轨迹支持显式平面或 WGS84 测地路径；Point、Polygon、MultiPolygon 的 XY 面轨迹支持平面与
  EPSG:4326 局部测地执行链。测地面要求独立边界采样段长，不复用隐藏的线轨迹加密参数。
- 时间/距离 gap、固定周期和受控表达式是三个独立拆分条件；固定周期始终 Gap，普通 gap 与表达式支持
  Gap、FinishLast、StartNext。NULL/Empty Geometry 和无时间观测不参加轨迹，同时间顺序必须可确定。
- 缓冲可来自数值字段或受控逐行表达式；表达式可使用显式观测窗口。窗口按轨迹和固定周期隔离，并在
  gap/表达式拆分及共享端点之前计算。测地 gap 始终比较原始 Geometry 区域，不使用质心或缓冲结果。
- 轨迹标识、开始/结束时间、总观测数、Count/Count Field、Any、First/Last 和数值统计均使用同一确定
  次序与真实片段成员。未处理入口表保持原顺序，新的有界结果表追加且无 Watermark。
- 完整输出血缘回归覆盖轨迹 ID、开始/结束、观测数、Count、Count Field、Sum、Mean、First、Last 与
  Geometry。结果为 `FIELD_COMPLETE`；轨迹 ID 是直接来源，其余聚合结果均追溯到实际输入字段，所有输出均无
  `WRITTEN_UNKNOWN_SOURCE`。`bucket/split/fixed_split/segment/neighbor` 等内部列没有暴露为逻辑输出，
  Catalyst 能由其输入表达式解释用途，不需要把未知业务来源伪装成完整血缘。
- 20,000 个 Point 的单轨迹本地样例在分析阶段触发 0 个 Spark Job，执行后得到一条 20,000 点轨迹，
  起止、观测数和统计正确。Operator 不把组数据 collect 到 Driver；组内仍使用 Executor 侧
  `collect_list + array_sort`，因此单个极大轨迹受 Executor 内存和单组排序成本约束。本样例不是无限容量或
  Enterprise 生产性能承诺。
- Track Reconstruct 后端专项共 91 项通过；新增完整血缘与 20,000 观测规模用例所在
  `TrackAreaSparkTest` 30 项通过。前端 Inspector、Parser、面轨迹与缓冲窗口 4 个文件共 20 项通过，
  节点目录 ESLint 无错误。
- 真实页面确认新节点未应用前不触发 Task Engine 编译，线/面切换、平面/测地、面轨迹独立设置、缓冲
  字段/表达式/窗口、相邻 gap、固定周期、表达式拆分、三种连接段归属、无效草稿应用和问题详情均可用；
  验收只留在当前未保存草稿，没有保存任务定义。

据此，进度清单中的 Reconstruct 按当前明确支持范围完成。大区域、半球/对跖域、无法证明的连续距离边界
继续返回既有安全错误；不增加质心、经纬平面或采样猜测回退。仍不声明 Arcade 全兼容、ArcGIS 官方采样/
容差/统计/窗口公式、Enterprise 异步服务字段、Geometry 顶点编码或生产容量完全等价。
