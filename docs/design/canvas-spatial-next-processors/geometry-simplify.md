# Canvas `GEOMETRY_SIMPLIFY` Processor 设计

## 1. 定位与审计结论

引入于 Canvas 4.10，BATCH/STREAMING。自有逐行简化基础算子，不是 GA Server 同名能力复刻；单要素拓扑保持不等于整图层共边拓扑保持。

本文区分 4.20 审计快照与后续目标设计；快照不是当前完整契约。4.29 显式一元几何策略已实现，见第 7 节；完整验收状态以[进度清单](../canvas-spatial-development-progress.md)为准。

### 4.36 公共单位补充

容差选项与 Canvas 单位摘要统一使用公共距离单位。国际码与美国测量制可用于投影 CRS 换算；地理 CRS 仍只允许来源角度单位。
单位名称明确国际制/美国测量制，切换不自动换算数值，旧单位结果不变。具体枚举、字段和证据见[公共空间单位](../canvas-spatial-units.md)；不代表整节点已完成官方对照。

### 当前阅读入口与对齐边界（4.45 汇总）

- **已接入范围**：4.29 的两类简化算法、维度选择及失败边界见第 7 节。
- **官方参照与差异**：自有单要素简化；拓扑保持不保证相邻要素共边一致，不是整层覆盖简化。
- **面板修订要求**：容差及单位同行，拓扑说明放算法帮助；不得用“保持拓扑”暗示共边保证。

本摘要不替代逐版本契约。下节的“当前”均指 4.20 历史状态；目标线框不作为已实现截图。
参数覆盖、本地验证和官方结果对照分别登记，见[对齐验收规则](../canvas-spatial-analysis-processor-roadmap.md#7-对齐验收)。

## 2. 4.20 配置快照（历史）

```ts
type GeometrySimplifyAlgorithm =
  | 'DOUGLAS_PEUCKER'
  | 'TOPOLOGY_PRESERVING';

interface GeometrySimplifyConfiguration {
  sourceTableName: string;
  geometryColumnName: string;
  outputTableName: string;
  outputColumnName: string;
  algorithm: GeometrySimplifyAlgorithm | null;
  tolerance: number;
  toleranceUnit: SpatialDistanceUnit;
}
```

- tolerance 有限正数且显式单位；地理 CRS 当前仅 SOURCE_CRS_UNIT，显示角度警告。
- 当前两算法为 Douglas-Peucker 与 Topology Preserving，产生新表及新字段，原 Geometry 保留。
- 当前输出继承 CRS/dimension、通用 GEOMETRY；维度实际保持能力需逐算法验收。

## 3. 目标语义

- Douglas-Peucker 减少顶点；不能只以点数减少率衡量质量，应检验误差和退化。
- Topology Preserving 约束单个输入 Geometry 的拓扑结构，不保证相邻两块面的公共边仍一致，
  更不保证无缝行政区覆盖。后者属于独立覆盖层简化能力。
- 容差是在处理 CRS 坐标空间的误差控制；米/千米仅在可靠线性轴单位下换算，不把“度”当“米”。
- 明确每种 GeometryKind、Empty、零维、非法面/集合、Z/M 的能力；不能只复制来源维度声明。
- 目标不做隐式 Repair、Transform 或丢弃 Empty。需更改维度时显式转换/拒绝，不能静默降维。
- 地理 CRS 角度容差作为平台扩展保留并警告，推荐前置投影；不声称是测地线简化。

## 4. 目标 Inspector UI

设计状态：自有单要素简化面板，不提供“ArcGIS 等价”或“整层共边保持”开关。当前两算法和维度组合见第 7 节（4.29）；容差示例不是预填业务参数。

```text
来源 / Geometry     [ roads / centerline ▼]
算法                [Douglas-Peucker | 单要素拓扑保持] (?)
容差                [0.5][米 ▼]
处理坐标系          EPSG:3857 · XY                 (?)
结果字段            [ simplified_geometry ]
输出表              [ roads_simplified ]
⚠ 1 个配置问题                                     [详情]
```

算法帮助明确“不会保持不同要素之间的共边”；地理 CRS 时标明来源角度单位，不将所有角度单位统称为度。
普通参数常驻，单位及维度限制按字段就近提示，不增加大段 Alert。


### 参数交互与初值（目标设计）

下表是目标面板约定，不修改旧任务默认值；未明确标注为官方默认的初值均为平台推荐。

| 配置组 | 初值与条件显示 | 对齐边界 |
| --- | --- | --- |
| 算法 | 新建建议单要素拓扑保持；用户可改为 Douglas-Peucker | 平台推荐，不是 GA 默认 |
| 容差与单位 | 容差必填、不预设业务数值；单位根据处理 CRS 的可靠单位提供 | 不做未经声明的自动投影 |
| 结果 | 原字段保留，新 Geometry 字段与新逻辑表分别命名 | 简化误差与实际维度须单独验收 |

算法切换保留容差；不因选中“拓扑保持”隐藏共边风险。面板帮助同时列出保留的性质和不能保证的性质。

## 5. 验收与执行

- 单条复杂线、带洞面、共享边界双面、退化/Empty/NULL/非法输入、Z/M；
  检查坐标实际维度与 Schema，不以顶点保留推断新生成坐标的维度。
- 容差单位换算、非法值、地理角度警告；输出名和字段名冲突。
- 对单要素拓扑保持与跨要素出现缝隙做明确反例，防止错误宣传。
- 惰性逐行表达式，来源字段保留、结果追加；原表仍在 Map 中，事件时间/Watermark 与有界性保留。
- Canvas 可展示配置容差，不显示简化前后坐标、WKT 或实际误差数据。

## 6. 依据与边界

- [GA Server 工具目录（本节点非同名工具）](https://developers.arcgis.com/rest/services-reference/enterprise/geoanalytics/tasks/geoanalytics-tasks/)
- [当前算法实现](../../../data-scalpel-task-engine/src/main/java/cn/superhuang/datascalpel/taskengine/canvas/GeometrySimplifyNodeOperator.java)
- [共同规则](../canvas-spatial-analysis-processor-roadmap.md)

测地线简化、Visvalingam-Whyatt、覆盖层共边简化、自动按比例尺选择容差另行设计。

## 7. Canvas 4.29 显式结果维度与简化失败边界

配置增加可选 `geometryPolicy: PRESERVE_DIMENSION | OUTPUT_XY | LEGACY | null`，原七参数 Java 构造器保留，默认为 null。
缺失/null/LEGACY 保持原 Sedona 路径和既有 XY 编译限制，包括旧 Douglas-Peucker 的有效面积后处理。
任何显式策略（含 LEGACY）要求 4.29；旧定义不会自动补策略。写出使用当前小版本，见 [Canvas Definition](../canvas-task-definition.md)，Manifest/Result/HTTP 不变。

| 算法 / PRESERVE_DIMENSION | XY | XYZ | XYM | XYZM |
| --- | --- | --- | --- | --- |
| Douglas-Peucker | 是 | 是 | 否 | 否 |
| 单要素拓扑保持 | 是 | 是 | 是 | 是 |

- 当前 JTS Douglas-Peucker 复制坐标时会丢失 M，故 XYM/XYZM 保留组合在编译期拒绝；可显式输出 XY 或使用单要素拓扑保持。
- OUTPUT_XY 为显式二维结果，原字段保持；不是三维距离或测地简化。高维输入在 Compiler 给出当前输出 XY 的警告。
- 有效 Point/LineString/Polygon、Multi 与 GeometryCollection 支持；点类通常不变，线/面的退化按算法处理。
  NULL→NULL，合法 Empty 结果及原行保留，不构造伪坐标；空 Geometry 不凭空添加 Z/M。
- 新策略先校验真实输入有效性，不自动 Repair。Douglas-Peucker 关闭隐式面积修复；若结果无效则
  `GEOMETRY_SIMPLIFY_RESULT_INVALID`，建议减小容差或更换算法，不返回无效面或静默修复。
- 单要素拓扑保持在有效输入下维持单个 Geometry 的拓扑约束，不保证独立行之间的共边。
  本地相邻面反例证明：两个输出都有效，仍可存在重叠/缝隙；它不是行政区覆盖层简化。
- 容差是二维处理坐标空间的控制量，不是地图显示比例尺。来源线性单位按方言无关 CRS 换算；经纬度仅允许来源角度单位并警告，绝不自动投影。
  非有限/非正容差、缺单位及换算溢出在 Compiler 拒绝，不执行真实数据计算。
- 运行错误与派生共用无效输入/维度安全码，简化结果无效另用上述专属码，均为不可重试 SCHEMA 错误，不回显数据值。
- UDF 与原计划一样逐行惰性执行，保持来源字段、入口 Map、有界性、事件时间和 Watermark；不增加 Action、缓存、状态或 StreamingQuery。

### 草稿与面板

- 新建默认单要素拓扑保持、PRESERVE_DIMENSION；容差初值 null，不擅自填业务数值。类型改为 `Double` / `number|null`。
- null/非正容差、空算法等业务草稿允许导入、保存和应用；错误由 Compiler 定位，结构或非有限数值仍拒绝。
- 结果维度选择与帮助紧邻算法，M 不支持组合直接标红。切换需确认，不清空容差或来源字段。
- 算法标签明确“单要素拓扑保持”；帮助明确新 DP 无自动修复及跨要素共边限制。
- Canvas 未填容差显示“待填容差”，不展示 null 或用默认 1 伪装已配置。

本地验证包含四维度的实际 Spark Geometry、几何族及面洞、NULL/Empty/无效输入、共边反例、草稿与版本门槛；
真实 Streaming 计划保持流属性但不启动查询。未声称三维拓扑、测地简化或覆盖层共边支持。

### 面板上下文补充（2026-09-08，协议不变）

Compiler 上下文尚未返回时，已保存来源表及 Geometry 字段显示“等待解析”，不将空候选误认为失效。
已解析但来源表不可用时保留原字段并显示“来源表不可用”；只有对应来源 Schema 已可用时才判断字段失效。
不新增前端 Schema 推导、不清空草稿。真实页面验收已于 2026-09-13 完成，证据见
[进度清单](../canvas-spatial-development-progress.md)。

### 容差数值与角度单位修正（2026-09-08，仍为 4.46）

- 正数容差在换算后必须仍为有限正数。极小英尺值换成米可能下溢为零；此时使用现有
  `INVALID_GEOMETRY_SIMPLIFY_TOLERANCE`，路径为 `configuration.tolerance`，不静默执行零容差。
  换算溢出继续使用既有 `SPATIAL_DISTANCE_UNIT_UNSUPPORTED`。仍可表示的正数不增加人为最小阈值。
- 来源角度单位不一定是度：EPSG:4326/4490 为度，EPSG:4807 为 grad（百分度）。SOURCE_CRS_UNIT
  使用原坐标空间数值，不转换坐标、不把 grad 当 degree；告警和面板统一说明来源角度单位。
- 本次补充普通折线在米制/美国测量英尺 CRS 下所有距离单位的等价结果，以及三种地理 CRS 的
  实际 Spark 结果、来源字段/原表保留、原 SRID 和输入错误无部分传播。单几何测试包含 50,001 顶点
  平滑线的逐顶点误差与 XYZ 保留、8,001 顶点交替折线；这些局部规模证据不代表分布式容量验收。
- 未改变简化算法、结果维度策略、旧定义的正常数值结果、Manifest、Result 或 HTTP。真实页面已完成；
  该单几何规模证据不等同于任意分布式覆盖层容量承诺，后者属于未提供的覆盖层共边简化能力。
