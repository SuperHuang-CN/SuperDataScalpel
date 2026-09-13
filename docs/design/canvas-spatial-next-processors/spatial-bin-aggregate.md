# Canvas `SPATIAL_BIN_AGGREGATE` Processor 设计

## 1. 定位与审计结论

引入于 Canvas 4.18，仅 BATCH，有界 Point 输入。对应 Aggregate Points 的规则格网子集；4.20 旧六边形尺寸含义与官方不同，4.21 的显式对边距离已作局部修正，不能据此标记整个节点已对齐。

第 2 节保留 Canvas 4.20 审计快照。4.21 已新增显式尺寸语义并保留旧边长行为，见第 7 节；
4.33 已接入 H3 分辨率/近似距离与对应 Inspector，见第 8 节；4.34 补字段 Count/Any 与留空时间切片，见第 9 节。
4.38 接入显式平面原点/业务范围，见第 10 节；4.39 共享日历时间见第 11 节。球面范围和官方对照仍待完成，不能宣称完整对齐。

### 4.47 固定时长周

固定切片的 `temporalSlicing.intervalUnit/repeatIntervalUnit`新增“周（固定 7 天）”，每周为 604800 秒。
与已有按时区推进的日历周区分；切换单位不换算已填数值，隐藏草稿保留并参与 4.47 门槛。
固定月年不开放，阈值边界、节点算法与输出粒度不变；详见[共享时长规则](../canvas-spatial-units.md#447-固定时长周与日历周)。

### 4.36 公共单位补充

格网大小及 H3 近似大小使用公共距离单位。H3 显式分辨率不执行大小配置，但保存的扩展单位仍要求 4.36；格网形状/级别算法不变。
单位名称明确国际制/美国测量制，切换不自动换算数值，旧单位结果不变。具体枚举、字段和证据见[公共空间单位](../canvas-spatial-units.md)；不代表整节点已完成官方对照。

### 当前阅读入口与对齐边界（4.45 汇总）

- **已接入范围**：第 7～11 节分别定义六边形尺寸、H3、统计、平面范围与日历窗口。
- **官方参照与差异**：参照 GA Aggregate Points 格网场景；H3 近似距离选级为平台规则，球面业务范围及官方边界尚待完成。
- **面板修订要求**：方格边长与六边形对边距离明确区分；H3 不执行隐藏平面范围，格网统计不冒充密度分析。

本摘要不替代逐版本契约。下节的“当前”均指 4.20 历史状态；目标线框不作为已实现截图。
参数覆盖、本地验证和官方结果对照分别登记，见[对齐验收规则](../canvas-spatial-analysis-processor-roadmap.md#7-对齐验收)。

## 2. 4.20 配置快照（历史）

```ts
type SpatialBinShape = 'SQUARE' | 'HEXAGON';

interface SpatialBinStatistic {
  statisticId: string;
  kind: 'COUNT' | 'SUM' | 'MEAN' | 'MIN' | 'MAX' | 'RANGE' | 'STDDEV' | 'VARIANCE';
  sourceColumnName: string | null;
  outputColumnName: string;
}

interface SpatialBinAggregateConfiguration {
  sourceTableName: string;
  pointGeometryColumnName: string;
  binShape: SpatialBinShape | null;
  binSize: number;
  binSizeUnit: SpatialDistanceUnit;
  includeEmptyBins: boolean;
  statistics: SpatialBinStatistic[];
  groupSummary: SpatialGroupSummary | null;
  temporalSlicing: SpatialTemporalSlicing | null;
  outputTableName: string;
  binIdColumnName: string;
  binGeometryColumnName: string;
}
```

- 当前 SQUARE 的 binSize 为边长，HEXAGON 的 binSize 为边长/外接半径；公式中心 x=1.5·size·q，y=√3·size·(r+q/2)。
- 要求投影 CRS + XY，至少 COUNT，统计 1～32；groupSummary、空格网为平台扩展。
- includeEmptyBins 按占用格网索引最小/最大包络补齐；六边形是轴向索引包络，不是用户业务 extent。
- 无来源点时输出零行；时间只补实际形成的窗口，不生成中间空窗口；当前不含 H3。

## 3. 官方目标与尺寸修正

| 官方参数 | 目标 |
| --- | --- |
| binType | Square / Hexagon / H3（Aggregate Points 从 11.2 支持 H3）。 |
| binSize / binSizeUnit | Square 边长；Hexagon **对边距离**，不是边长。 |
| binResolution | H3 分辨率 0～15；也可按 size 选择最接近的分辨率，两个输入方式明确互斥。 |
| summaryFields | 总 Count、字段非空 Count、数值统计、字符串 Any。 |
| timeStepInterval / Unit | 时间窗宽。 |
| timeStepRepeatInterval / Unit / Reference | 重复间隔和参考时间；可以有重叠、无重叠或间隙。 |

目标六边形：对边距离 d，边长 s=d/√3，面积=(√3/2)·d²。
旧 size=100 的六边形实际对边距离约 173.205；不得把旧 JSON 直接按 d=100 解释。
必须引入显式尺寸含义/版本门槛并说明转换，维持旧任务格网位置及结果可追溯。

- Square/Hexagon 明确处理 CRS；H3 使用 WGS84，不能当普通平面六边形替代。
- 格网原点、方向、范围、边界点归属及 ID 组成必须明确，供密度/多变量格网重复使用。
  显式原点/extent 是平台适配目标，不宣称是 Aggregate Points 同名参数。
- 空格网范围要可复现并有规模风险提示；业务边界筛选/裁剪与索引包络分开。
- 统计方差、字段 Count、Any 和 Null 规则按官方对照；分组比例与少数/多数非此 GA REST 的标准参数。

## 4. 目标 Inspector UI

设计状态：方格/六边形尺寸、H3、字段统计、平面原点/范围及日历窗口的当前契约见第 7～11 节。H3 的原生点归属不代表球面业务范围已经可配置；隐藏的平面范围不应暗中用于 H3。

```text
来源 / 点字段       [ orders / location ▼]
格网类型            [ 方格 | 六边形 | H3 ]
对边距离            [1000][米 ▼]                   (?) 六边形
H3 尺寸             [分辨率 | 近似距离]               条件显示
分辨率              [请输入 0～15]                  不猜填级别
实际分辨率          应用后解析                     近似距离模式
格网范围 / 对齐     数据范围 · 原点约定             [设置] 4.38 平面
时间切片            关闭                           [设置]
统计项              3 项                           [设置]
空格网              [不输出]（平台扩展）
结果字段            bin_id · bin_geometry           [设置]
输出表              [ order_bins ]
```

尺寸标签随类型变化，帮助 Popover 画尺寸含义；H3 隐藏平面边长输入。
统计项完整表格放 Modal；分组扩展放高级设置，不挤占核心参数区。
旧 HEXAGON 配置必须提示“当前按边长解释”，不因目标 UI 改标签就改变实际值。


### 参数交互与初值（目标设计）

下表是目标面板约定，不修改旧任务默认值；未明确标注为官方默认的初值均为平台推荐。

| 配置组 | 初值与条件显示 | 对齐边界 |
| --- | --- | --- |
| 格网类型 | 新建推荐方格；方格、六边形、H3 显式选择 | 平台初值，不宣称官方默认 |
| 尺寸 | 方格显示边长，六边形显示对边距离；不预设业务尺寸 | 旧六边形边长语义保留并标识 |
| H3 | 分辨率与近似尺寸互斥；近似尺寸需显示解析后的实际分辨率 | 不得将分辨率 7 示例写成所有场景默认 |
| 时间切片 | 默认关闭；窗宽、步长、参考时间分开 | 步长不是窗口内相邻点间隔 |
| 空格网与范围 | 空格网默认关闭；开启时必须明确可复现范围 | 业务 extent、占用索引包络、裁剪不是同一选项 |

形状、尺寸、CRS、原点任一改变会影响 bin ID；已有配置切换需提示影响，但不自动换算或改变旧数值。
H3 从 4.33 可配置；4.38 原点/业务范围仅对平面格网生效，不伪装成 H3 参数。H3 空格网保留失效草稿并可关闭，不静默清空。

## 5. 验收与边界

- d=1000m 的六边形对边测量应为 1000m，面积约 866025.404m²。
- 覆盖负坐标、边/角上点、跨分区点唯一归属、索引包络与业务 extent 差异、空输入、极大空格网。
- 验证相同原点/CRS/尺寸下跨任务 bin ID 对齐；旧边长转换后 Geometry 与聚合结果不变。
- H3 验证 0/15 分辨率、尺寸近似选择、日期变更线，不用平面公式计算面积。
- 时间窗大/小于重复间隔，月/年不能按固定 30/365 天替代；保持固定和日历单位区别。
- 平面格网输出 Polygon，H3 输出 MultiPolygon；均为 bin ID+统计的 BOUNDED 新表。分组/窗口改变行粒度时明确显示。
- 此节点不是 Calculate Density：后者还要邻域半径和 Uniform/Kernel；矢量形式不妨碍密度能力。
- Canvas 可显示配置尺寸，隐藏点坐标、数据范围与结果值。

## 6. 官方依据

- [Aggregate Points：H3 与对边距离](https://developers.arcgis.com/rest/services-reference/enterprise/geoanalytics/tasks/aggregate-points/)
- [Calculate Density：独立邻域分析](https://developers.arcgis.com/rest/services-reference/enterprise/geoanalytics/tasks/calculate-density-geoanalytics/)
- [共同规则与版本策略](../canvas-spatial-analysis-processor-roadmap.md)

## 7. 4.21 显式尺寸语义

- 配置新增可选 `binSizeSemantics: LEGACY_SIDE_LENGTH | HEXAGON_FLAT_TO_FLAT | null`。
- 缺失/null 和旧便利构造器保持 LEGACY_SIDE_LENGTH；旧任务格网位置和 ID 不变。
- 新建节点默认 HEXAGON_FLAT_TO_FLAT。Square 在两种策略下均按边长；Hexagon 在新策略下
  将配置对边距离除以 √3 后进入既有格网定位与多边形生成，两条路径（已占用/补齐空格网）统一换算。
- 非空显式策略要求 Canvas 4.21；未知策略拒绝导入，低于 4.21 不得携带该字段。
- Inspector 按形状和策略显示“边长”“对边距离”或“边长（旧版）”；切换策略需确认，不自动改尺寸数值。
  要保持旧 Geometry，边长切换为对边距离时由用户将数值乘以 √3。切换形状不清除已配置语义。
- Canvas 显示配置尺寸及其含义，Runner 只记策略与单位，不记输入坐标或数据范围。
- 专项验证覆盖 d=1000m 的对边距离/面积、负坐标及空格网，旧边长=s 与新对边距=s√3 的结果应等价。

## 8. 4.33 H3 格网与实际对齐边界

### 配置与兼容性

```ts
type SpatialBinShape = 'SQUARE' | 'HEXAGON' | 'H3';
interface SpatialH3Options {
  mode: 'RESOLUTION' | 'APPROXIMATE_SIZE' | null;
  resolution: number | null;
}
// SpatialBinAggregateConfiguration 新增 h3?: SpatialH3Options | null
```

- 仅 `binShape=H3` 生效。`RESOLUTION` 使用 0～15 整数，忽略根 `binSize/binSizeUnit`；
  `APPROXIMATE_SIZE` 使用根大小及线性单位，忽略但保留 `h3.resolution`。
- 首次切换 H3 时初始化分辨率模式、分辨率为 null；业务大小由用户填写。不把示例级别 7 当成默认。
- 非活动 H3 对象保留。缺失/null 不启用 H3，不改变旧 SQUARE/HEXAGON 的 ID、Geometry 或尺寸语义。
  H3 形状或任意非 null `h3` 都要求 Canvas 4.33，低版本返回 `SPATIAL_H3_REQUIRE_SCHEMA_VERSION`。
  Manifest、Result、HTTP API 和生产依赖不变；使用现有 Sedona 制品携带的 H3 运行库。
- 前端拒绝数组/错误枚举/非整数等结构；未选 mode（null）、分辨率越界、无效大小等业务草稿可保存，由 Operator 报错。

### 与 ArcGIS 参数的映射

| 官方参数/语义 | 平台实现 | 差异与状态 |
| --- | --- | --- |
| `binType=H3`（11.2+） | `binShape=H3` | 原生 H3 点归属，非平面六边形近似 |
| `binResolution` 0～15 | `h3.mode=RESOLUTION` + resolution | 不自动猜填；0 为有效配置 |
| `binSize/binSizeUnit` 选最接近对边距离级别 | `APPROXIMATE_SIZE` + 根大小/单位 | 平台以 `√3 × H3 平均边长(米)` 比较绝对差，平局选较粗级别；官方未公布同一平均宽度公式，不能宣称选级别完全等价 |
| H3 要求 WKID 4326，其他 CRS 自动转换 | EPSG:4326 + XY Point；其他 CRS 要先接 Transform | 平台显式转换，不暗中重投影 |
| 统计和时间切片 | 复用现有统计/分组/时间窗，4.34 补字段 Count/Any、留空时间窗 | 完整时间单位和官方统计公式对照仍有缺口；分组比例/少多数为平台扩展 |
| 输出格网 | H3 字符串 ID + EPSG:4326 XY MultiPolygon | 存在五边形；跨日期线/极区切分后的 Multi 不等于官方输出字节一致 |

### 运行与边界

- NULL/Empty 点不参加聚合，空输入零行；其他非 Point、非有限或经纬度越界点在运行时以
  `SPATIAL_H3_POINT_INVALID` 失败（SCHEMA），不在编译期扫描数据。
- +180/-180 及极点不同经度规范化为相同位置。分组依据 H3 原生 Cell ID，边界展示不反向决定点归属。
- H3 原生球面边界按球面最短弧、最长 25 km 间隔离散；在日期线求球面交点，极区闭合并切分为合法 MultiPolygon。
  这是当前边界表示精度，不是 Esri XY 容差或完整测地曲线；不用经纬度面积冒充米制面积。
- H3 只输出有点的格网，不根据平面索引包络补齐；`includeEmptyBins=true` 返回
  `SPATIAL_H3_EMPTY_BINS_UNSUPPORTED`。球面业务范围/空格网仍待设计；4.38 平面原点不应用于 H3。
- 聚合按单元/时间窗/分组形成结果，保留入口表。来源字段先独立别名，避免与内部 Cell ID、时间窗字段冲突。
  Compiler 和 Runner 共用惰性计划；不引入 Action、缓存、外部读取或 Driver 收集。
  已占用 H3 范围用显式分组计划生成；Cell ID/边界/Count 追溯到来源 Geometry，数值统计追溯到对应来源字段，零行血缘专项通过。
- 模式缺失、级别越界、尺寸非法分别为 `SPATIAL_H3_MODE_REQUIRED`、`INVALID_SPATIAL_H3_RESOLUTION`、
  `INVALID_SPATIAL_H3_SIZE`；非 4326 为 `SPATIAL_H3_WGS84_REQUIRED`。
  近似尺寸选级别产生非阻断提示 `SPATIAL_H3_RESOLUTION_SELECTED`。
- 原生库加载失败 `SPATIAL_H3_RUNTIME_UNAVAILABLE` 属于 CONFIGURATION；边界失败
  `SPATIAL_H3_BOUNDARY_INVALID` 属于 SCHEMA。错误摘要不包含坐标或格网 ID；底层拓扑异常不直接进入 Spark 日志。

### 当前面板

```text
来源点表 / Geometry  [ points / shape ▼ ]
格网形状             [ 方格 | 六边形 | H3 ]        切换确认
H3 大小方式          [ 分辨率 | 近似对边距离 ]  (?)
  分辨率模式         [ 0～15 ]                     必填、不猜填
  近似距离模式       [ 大小 ][线性单位 ▼]
                     H3 分辨率 8 · 估算平均对边距离 … 米
输出空格网           [关闭]                       H3 开启时就地报错
统计 / 分组 / 时间   N 项 / 关闭 / 关闭             [设置]
格网结果字段         bin_id · bin_shape             [设置]
输出表               [ bins ]
```

- 两种大小方式仅显示当前生效字段。模式和形状切换保留隐藏参数；应用未完成草稿不被普通业务错误阻断。
- 实际分辨率读取 Compiler 输出 bin ID 字段的只读注释，不在前端复制选级别算法。
  草稿与已应用配置不同时显示“应用后解析分辨率”，不展示旧计算值误导用户。
- Canvas 显示 H3、配置级别/近似大小和统计数量；不展示点坐标、格网范围和统计结果。
- 当前为平台实现及本地验证阶段；官方样例/服务对照、规模、跨架构制品验收仍须单独记录，
  不据此勾选完整节点或路线图完成。验证记录见[进度清单](../canvas-spatial-development-progress.md)。

## 9. 4.34 字段统计与固定时长窗口修复

### 统计字段与 Schema

- `statistics[i].kind` 增加 `COUNT_FIELD`、`ANY`；非空 `sourceColumnName` 指定来源字段。
  新类型从 4.34 开始支持，低小版本返回 `SPATIAL_BIN_FIELD_STATISTICS_REQUIRE_SCHEMA_VERSION`。
  已有 `COUNT` 不改名、不改语义，仍无需来源字段，并至少保留一项。新节点只默认提供点数统计，
  不自动为所有字段生成统计；这是平台的显式输出配置，不等同官方省略 summaryFields 的默认行为。
- `COUNT_FIELD` 使用指定字段的非 NULL 数量，空字符串和重复值计数；空格网或全 NULL 分组为 0，输出 LONG。
  不做 DISTINCT。平台允许 Spark 可计数的字段类型，不仅限于官方 summaryFields 中的数值/字符串。
- `ANY` 取一个非 NULL 字符串样本；全 NULL/空格网为 NULL，保留字符串类型及长度。
  不承诺跨分区、重跑选中同一个样本，也不把它当排序后的 FIRST；非字符串返回 `STRING_COLUMN_REQUIRED`。
- 所有统计输出的类型、Decimal 精度/小数位及可空性由实际 Spark Analyzer 回填，不再复制来源类型。
  例如 INTEGER SUM 为 LONG，Decimal SUM/MEAN 按 Spark 规则提升精度。
  此为修正逻辑 Schema 与实际 Dataset 不符的错误，不改变既有统计数值算法。
- 现有 STDDEV/VARIANCE 仍为样本统计（n−1，n≤1 时 NULL）；与 Esri 的具体公式/退化规则仍须独立对照，
  不因为补齐枚举就宣称所有统计算法已对齐。

### 留空、连续及重叠窗口

- `repeatInterval` 小于/等于/大于窗口长度分别对应重叠、连续和留空。重复间隔缺失时等于窗口长度。
  原有配置结构和固定时长单位不变；允许大于窗口长度是新增能力，不改变此前合法配置的窗口定义。
- 窗口统一左闭右开。例如参考时刻 00:00:02、长度 3 秒、重复 10 秒，窗口为
  `[-8,-5)、[2,5)、[12,15)…`（秒，相对 Epoch）；落在空隙、右边界或 NULL 时间的观测不参加该窗口。
  范围补齐只使用实际参与的窗口，不因一条仅落在空隙内的点生成新时间窗。
- 实现复用 Spark 固定窗口对齐：留空模式先生成长度等于重复间隔的桶，再保留每桶前一段有效时长。
  编译仅构造惰性计划，不扫描真实时间值、触发 Action 或在 Driver 收集。
- 同时修复旧重叠窗口重复展开：窗口 STRUCT 只生成一次，然后拆成开始/结束字段。
  旧实现分别展开起止字段会形成无效组合、重复计数；升级后可能纠正受该缺陷影响的历史结果，
  这是错误修复，不保留“重复展开”作为兼容算法。区域汇总共享同一修复。
- 固定模式 `DAYS` 仍为 24 小时，不能当按 IANA 时区的日历日；4.39 另以显式模式增加日历切片，见第 11 节。
- 字段血缘按 Catalyst Expand 的每个输出位置解析全部分支，而非将其当透明节点；无法解析的分支仍保留未知来源。
  方格/六边形/H3 的占用范围及平面空格网时间范围使用结果等价的显式分组计划，避免 Deduplicate 导致不必要的部分血缘。
  三种格网在重叠/连续/留空窗口下的 COUNT_FIELD、ANY、起止时间已通过 FIELD_COMPLETE 专项；平面补空路径也覆盖。
- 所有形状均在进入计算前隔离来源列名，统计和分组指示器最后一次投影才使用用户输出名。
  允许业务字段恰好命中内部临时列名称，不覆盖原值、不误用格网索引计数；上游表保持原对象和原字段。

### 统计弹窗交互

```text
统计项  N 项                                        [设置]
┌ 设置格网统计项                                      ┐
│ 至少保留一个点数统计 (?)                   [+ 添加] │
│ COUNT · 点数            —                 point_count │
│ COUNT_FIELD · 非空数     customer_name     name_count  │
│ ANY · 字符串样本         customer_name     example     │
│ 每行可设置来源/输出、排序和确认式删除                  │
│                          [取消] [保存统计草稿]       │
└────────────────────────────────────────────────────┘
```

- 弹窗使用本地副本，取消不改 Inspector；保存才提交这一组统计。普通业务错误仍允许保存草稿。
- 失效来源和重复/空输出名保留并标红；Compiler 未返回时不据此认定已配置字段不存在。
  同一次编辑中切换 COUNT 再切回字段统计会恢复之前的来源选择；COUNT 保存时来源仍按协议写 null。
- 帮助解释点数与字段非空数、NULL/空字符串、不去重、Any 不确定采样及样本方差。
  Canvas/日志只显示配置计数或字段名，不显示 Any 样本或任何统计结果。
- 本轮不新增 Manifest、Result 或 HTTP 字段。4.38 补平面原点和业务范围，4.39 补共享日历；球面范围和官方验收仍在开发清单中。

## 10. 4.38 显式平面原点与业务范围

### 配置与兼容

```ts
interface SpatialPlanarGridOptions {
  originX: number | null;
  originY: number | null;
  extent: {
    mode: 'DATA_BOUNDS' | 'EXPLICIT_BOUNDS' | null;
    minX: number | null;
    minY: number | null;
    maxX: number | null;
    maxY: number | null;
  } | null;
}
// SpatialBinAggregateConfiguration 增加 planarGrid?: SpatialPlanarGridOptions | null
```

- 缺失/null 保留旧版 (0,0)、来源索引包络和原有 `SHAPE:q:r` ID。不自动迁移旧任务。
- 显式对象只在 SQUARE/HEXAGON 中生效。H3 隐藏并保留整份平面配置，不执行平面范围筛选、原点平移或校验其业务值。
  任意非 null planarGrid（含 H3 下的草稿）要求 4.38；三端低版本统一报 `SPATIAL_PLANAR_GRID_REQUIRE_SCHEMA_VERSION`。
- Java 保留旧 12/13/14 参数构造器，默认 planarGrid=null。缺失坐标可反序列化为空草稿；发布/编译由 Operator 校验。
  Manifest、Task Result、HTTP API 不变，无新增生产依赖。

### 原点、方向和身份

- 原点使用来源投影 CRS 的坐标单位，**不是 binSizeUnit**；切换米/英尺等显示单位不移动原点或范围。
- 方格原点为索引 (0,0) 的左下角。索引按 `floor((x−originX)/side)` 和 Y 同式计算，含负坐标；格网不旋转。
- 六边形为既有 flat-top 方向，原点为轴向索引 (0,0) 的中心；平移后沿用 cube rounding 和既有 tie 规则。
  中心为 `originX+1.5·side·q`、`originY+√3·side·(r+q/2)`；新对边距离/旧边长先统一为实际边长。
- 显式对象使用 `SHAPE:<SHA-256网格身份>:q:r`。身份取形状、EPSG、实际来源 CRS 边长、原点 X/Y 的稳定表达，
  不包含节点/任务 ID、数据内容或业务范围，正负零规范化。不同任务相同对齐配置的同一格网 ID 一致。
  范围变化不会重新编号；原点、CRS、实际大小或形状变化会改变身份。旧模式仍不改 ID。
  启用显式 (0,0) 也会进入新 ID 命名空间，UI 明确提示，不假装与旧 ID 相同。

### 范围、空格网和边界

- extent 缺失/null 或 DATA_BOUNDS 时，继续根据参与计算点的占用索引最小/最大值补齐。
  六边形此模式仍是轴向索引包络，不冒充业务矩形。DATA_BOUNDS 下保留未生效的四个范围值。
- EXPLICIT_BOUNDS 要求四个有限坐标，且 minX<maxX、minY<maxY；范围在来源 CRS 内，不自动重投影。
- 点采用 `[minX,maxX) × [minY,maxY)` 筛选；之后按完整格网分配和统计。范围筛选不是 Geometry 裁剪。
- 不补空时只输出有点格网。补空时生成与矩形有正面积相交的完整方格/六边形；仅接触边界的空格网不保留。
  被选中边界点的原分配格网始终保留，即使舍入或浮点几何将它放到仅接触范围的位置；补空不能改变点数或重新分配边界点。
- 显式范围且补空时，即使输入零行也输出该范围的空间格网，Count=0、无有效样本的统计为 NULL。
  时间切片只与范围内实际出现的有效窗口组合：不凭空生成时间范围；无有效时间窗时仍零行。
  分组/时间参数沿用既有粒度，不把空格网组伪装成实际业务分组。
- 新显式对齐模式对一次空间范围最多展开 100 万候选格网。六边形先生成保守轴向包络，再排除范围外单元，
  因此上限检查候选数而非最终数；时间/分组仍可能增加结果行数。显式范围在编译时检查，数据范围在惰性运行计划中检查。
  旧模式不新增此门槛；超限提示用户缩小范围/增大大小，不静默截断结果。

### 数值与安全边界

- 原点及有效范围必须有限；原点加边长不能溢出或因精度完全不变化。有效范围必须能形成可靠索引。
- 新模式跳过 NULL/Empty；参与计算坐标相对原点的格网索引绝对值不超过 2^50，不允许非有限索引。
  超限在真实执行时返回安全 `SPATIAL_GRID_POINT_INVALID`（SCHEMA，不重试），不打印实际坐标。
- 配置问题：`INVALID_SPATIAL_GRID_ORIGIN`、`INVALID_SPATIAL_GRID_EXTENT`；空格网超限为
  `SPATIAL_GRID_CELL_LIMIT_EXCEEDED`（运行时 CONFIGURATION，不重试；显式范围可提前报告同码编译错误）。
- 所有计划共用当前 Operator；无外部预读、Driver collect、缓存或额外 Action。范围由配置常量或惰性聚合得到，
  格网在 Spark 中展开。静态血缘沿真实 Catalyst 字段/用途推导，不用数据值补猜；配置常量生成的边界不是物理来源字段。
- Canvas 与安全摘要只显示“指定原点/业务范围”等模式，不显示原点坐标、范围、点值或统计结果。

### Inspector

```text
范围与对齐       指定原点 · 数据范围       (?) [设置]
┌ 格网范围与对齐                                  ┐
│ 原点 X [      ]          原点 Y [      ]          │
│ 范围 [ 来源索引包络 | 显式业务范围 ]              │
│ 最小 X [      ]          最小 Y [      ]          │
│ 最大 X [      ]          最大 Y [      ]          │
│                    以上四项仅显式范围显示         │
│       [恢复旧版] [取消] [保存范围草稿]             │
└──────────────────────────────────────────────────┘
```

- 680px 独立 Modal，不打开时不丢失配置；取消不提交，普通业务错误仍允许保存草稿。
- 切换范围方式保留四个隐藏坐标；切换 H3 保留平面对象。坐标单位/方向/边界/容量放邻近帮助，错误直接标红。
- 恢复旧版需要确认：清除新对象并恢复旧 ID/范围，不以静默清空代替用户操作。
- 显式原点/范围是平台适配能力，不宣称 Aggregate Points 具有同名参数。球面范围、完整官方数值与规模验收仍待完成。

## 11. 4.39 显式日历时间窗口

时间配置新增可选 `calendar`，在固定时长与日历周期之间显式选择；旧定义仍为固定模式，不自动迁移。
日历日/周/月/年按时区推进，毫秒至小时仍按实际时长；窗宽/步长数值共用但单位各模式保留。
月末与闰年同族起止从原参考时刻推导，混合族先起点再加窗宽；左闭右开，支持重叠与留空。
无参考时间使用 Epoch 对应本地时刻，无偏移的歧义/不存在时间拒绝；这些是明确的平台边界，不宣称已通过真实 GA 对照。

常驻行显示 `时间切片 · 2 日历月 · 日历 [开关] [设置]`；620px Modal 放模式、时间字段、窗宽/步长、
参考时间/时区、结果起止字段。切换模式先确认，取消只丢弃当前弹窗副本；缺失/非法业务值仍可保存草稿。
Canvas 只标记“固定切片/日历切片”，不显示参考时刻。完整 UI 和协议见[共享日历窗口](../canvas-spatial-calendar-windows.md)。

格网范围只按实际参与的窗口补空，空输入不虚构月份；候选时间窗每观测最多检查 4096 个，超限安全失败。
编译只生成惰性计划；同一 UDF 结果展开一次，再拆起止，防止重叠窗口重复计数。三端 gate 为 4.39，含非活动对象。
本地验证不替代真实服务、球面范围及规模验收。

## 12. 当前能力收口（2026-09-13，协议仍为 4.76）

- 当前节点完整支持方格、平面六边形和 H3 三种点格网；方格使用边长，六边形同时保留旧边长和显式
  对边距离语义，H3 支持 0～15 分辨率及按近似对边距离选级。平面模式可使用显式原点、来源范围或
  显式业务范围；H3 继续只输出有观测的 Cell，不执行隐藏的平面范围配置。
- COUNT、COUNT_FIELD、ANY 和七种数值统计可与分组少数/多数、组百分比、固定或日历时间切片组合。
  重叠、连续、留空窗口、空格网、NULL/Empty、显式范围和 100 万候选保护均沿用前述明确语义。
- 三种格网、平面补空路径、显式范围和来源范围的所有输出字段均达到 `FIELD_COMPLETE`。
  格网 ID/Geometry 从来源 Geometry 生成时具有真实字段边；显式业务范围生成的空格网来自配置常量，
  保持已知计算输出且不伪造来源。点数、分组指示器、字段统计和时间边界均追溯到实际参与字段，
  输出不存在 `WRITTEN_UNKNOWN_SOURCE`。
- 20,000 点样例在分析阶段不启动 Spark Job，分布式聚合后的 Count 与数值总和正确；执行计划不包含
  `CollectLimit`、`collect_list`、`CartesianProduct` 或 Cross Join。显式空格网的受控范围展开仍可能
  产生大量结果，因此这不是无限容量或生产规模等价承诺。
- Engine `BinStatisticsAndWindowsSparkTest` 25 项通过；前端尺寸、H3、平面范围和统计 4 个文件 17 项通过。
  真实页面已验证新节点延迟编译、方格/六边形/H3 条件面板、H3 两种大小方式、范围、统计、分组、
  时间切片、无效草稿应用和问题详情，未保存任务定义。
- 当前收口不包含 H3 球面业务范围、ArcGIS Enterprise 官方逐值结果、Esri 容差/边界编码或生产容量
  完全等价；这些差异不能用当前本地验证替代。
