# Canvas `GEOMETRY_DERIVE` Processor 设计

## 1. 定位与审计结论

引入于 Canvas 4.9，BATCH/STREAMING。逐行一元 Geometry 派生，是 DataScalpel 自有基础算子，不对应 GA Server 单一同名任务，也不是按整层统计的 Mean Center。

本文区分 4.20 审计快照与后续目标设计；快照不是当前完整契约。4.29 显式一元几何策略已实现，见第 7 节；完整验收状态以[进度清单](../canvas-spatial-development-progress.md)为准。

### 当前阅读入口与对齐边界（4.45 汇总）

- **已接入范围**：4.29 的显式维度策略、函数限制和退化行为见第 7 节。
- **官方参照与差异**：自有几何基础能力，没有对应的 GA Server 同名工具；函数可调用不代表全部 Z/M 或 GeometryCollection 组合可用。
- **面板修订要求**：函数旁说明维度与退化限制；不要增加“ArcGIS 等价”标记。

本摘要不替代逐版本契约。下节的“当前”均指 4.20 历史状态；目标线框不作为已实现截图。
参数覆盖、本地验证和官方结果对照分别登记，见[对齐验收规则](../canvas-spatial-analysis-processor-roadmap.md#7-对齐验收)。

## 2. 4.20 配置快照（历史）

```ts
type GeometryDeriveKind =
  | 'CENTROID'
  | 'POINT_ON_SURFACE'
  | 'ENVELOPE'
  | 'CONVEX_HULL'
  | 'BOUNDARY';

interface GeometryDerivation {
  derivationId: string;
  kind: GeometryDeriveKind;
  sourceColumnName: string;
  outputColumnName: string;
}

interface GeometryDeriveConfiguration {
  sourceTableName: string;
  outputTableName: string;
  derivations: GeometryDerivation[];
}
```

- derivations=1～32，ID 为节点内唯一 UUID；输出字段按顺序追加，不覆盖来源字段，同节点不能引用刚派生的字段。
- 当前 CENTROID/POINT_ON_SURFACE 声明 POINT，其余通用 GEOMETRY；实现调用对应 Sedona 函数。
- 当前 Schema 直接继承来源 dimension；这不能证明所有函数实际保留 Z/M，是需要核实并修正的风险。

## 3. 目标语义

| 派生 | 结果含义及边界 |
| --- | --- |
| CENTROID | 单要素质心，不保证位于凹面/带洞面的内部，不等于点图层按组的平均中心。 |
| POINT_ON_SURFACE | 对有效非空面提供内部代表点，适合标注；不对非法/Empty/任意 Geometry 无条件保证。 |
| ENVELOPE | 轴对齐包络；点/线/空输入可能退化，不能一律声明 Polygon。 |
| CONVEX_HULL | 覆盖输入要素的凸包；单点/共线会退化为点/线。 |
| BOUNDARY | 拓扑边界；面通常线、线通常点、点可能 Empty，集合须核对支持范围。 |

- CRS 不隐式改变；坐标维度必须按函数能力推导。计算型点可能没有可靠 Z/M，不能生成 XYZ 标记但 Z=NaN。
- 实现前逐函数列出 XY/XYZ/XYM/XYZM 支持矩阵；无法保持时拒绝并提示显式降维，
  或新增明确目标维度配置。不得悄然丢弃维度，亦不得伪造维度。
- NULL→NULL；Empty、无效面与集合支持逐函数明确，错误安全分类，不能将“交给 JTS”作为最终契约。
- 保留所有原表，追加新结果表和派生字段；只处理一张选择的来源，派生项相互独立。

## 4. 目标 Inspector UI

设计状态：逐行基础能力，不标记为 GA Server 同名工具。当前维度选择、函数限制与草稿交互以第 7 节（4.29）为准；下方线框中的业务字段仅为示例，不作为默认值。

```text
来源表              [ parcels ▼]
派生字段             2 项                       [设置] [＋]
类型                来源字段        输出字段
质心                boundary        centroid
面内点              boundary        label_point
Geometry 信息       POLYGON · EPSG:4490 · XY      (?)
输出表              [ parcels_geometry ]
```

常驻预览两项单行，不再用多行卡片；720px Modal 展示全部规则及类型帮助、排序、删除。
“面内点”帮助限定有效非空面；低频 Geometry/维度解释在 Popover，当前问题单行展示。
失效字段原样回显，普通错误允许草稿应用。


### 参数交互与初值（目标设计）

下表是目标面板约定，不修改旧任务默认值；未明确标注为官方默认的初值均为平台推荐。

| 配置组 | 初值与条件显示 | 对齐边界 |
| --- | --- | --- |
| 派生类型 | 每次新增规则由用户选择；帮助列出五种函数的几何含义 | 自有能力，无 GA 默认值可复制 |
| 来源与输出字段 | 来源字段不猜选；输出名可建议，重复时标红，不自动追加序号 | 表字段编辑为平台适配 |
| 坐标维度 | 显示推导结果；不支持的 Z/M 直接定位到该规则 | 不得用继承 Schema 冒充函数支持 |

规则 Modal 每行放“函数 / 来源字段 / 输出字段 / 帮助”，不把质心与整层平均中心放在同一个算法选项中。

## 5. 验收与安全

- 凹面、带洞面：质心与面内点位置差异；单点/共线凸包、点/线包络、闭合线边界、GeometryCollection。
- 覆盖 NULL、Empty、无效 Geometry 以及全部声明维度，检查实际 WKB 维度与 Schema 一致。
- 多项顺序、重复字段名、同节点字段引用拒绝；保留入口 Map。
- 逐行惰性 select，不增加 Action、状态或物化；继承有界性，保留原事件时间及 Watermark。
- Canvas 只显示函数类型/字段/数量，不显示 WKT、坐标或派生结果。

## 6. 依据与范围

- [GA Server 工具目录（用于限定产品范围）](https://developers.arcgis.com/rest/services-reference/enterprise/geoanalytics/tasks/geoanalytics-tasks/)
- [当前函数调用与 Schema 实现](../../../data-scalpel-task-engine/src/main/java/cn/superhuang/datascalpel/taskengine/canvas/GeometryDeriveNodeOperator.java)
- [共同规则](../canvas-spatial-analysis-processor-roadmap.md)

本轮不扩展 Concave Hull、Voronoi、Delaunay，不重复已有 Buffer/Repair/Transform/Serialize。

## 7. Canvas 4.29 显式维度与函数契约

每个 `GeometryDerivation` 增加可选 `geometryPolicy: PRESERVE_DIMENSION | OUTPUT_XY | LEGACY | null`。
旧四参数 Java 构造器保留，默认 null；缺失/null/LEGACY 保持旧 Sedona 路径和既有 XY 编译限制。
**历史快照中“继承维度”并不表示旧节点已能编译高维来源；本轮只在显式新策略下开放经验证的能力。**
任何显式值（含 LEGACY）要求 4.29，旧小版本携带时 `GEOMETRY_UNARY_POLICY_REQUIRE_SCHEMA_VERSION`；保存/导出使用当前小版本，见 [Canvas Definition](../canvas-task-definition.md)，Manifest/Result/HTTP 不变。

### 保留维度能力矩阵

以下是 PRESERVE_DIMENSION 的函数保证，不是按个别退化案例推测支持：

| 函数 | XY | XYZ | XYM | XYZM |
| --- | --- | --- | --- | --- |
| CENTROID | 是 | 否 | 否 | 否 |
| POINT_ON_SURFACE | 是 | 否 | 否 | 否 |
| ENVELOPE | 是 | 否 | 否 | 否 |
| CONVEX_HULL | 是 | 是 | 是 | 是 |
| BOUNDARY | 是 | 是 | 是 | 是 |

- 计算质心、面内点、包络不伪造 Z/M，已知不支持组合在编译期定位到该规则的 geometryPolicy。
- 凸包/边界保留被选中原顶点的 Z/M，不是三维凸包或对 M 聚合；重合 XY 的不同 Z/M 不表示多个拓扑顶点。
- OUTPUT_XY 对所有受支持输入维度显式构造二维坐标序列，高维来源显示 `GEOMETRY_UNARY_OUTPUT_XY` 警告，原 Geometry 不变。
  输出 Schema 与实际 Spark Geometry 的维度一致；没有坐标的 Empty 不伪造 Z/M 数值。

### GeometryKind、NULL 与退化

- 有效 Point/LineString/Polygon、对应 Multi 类型以及 GeometryCollection 均支持前四个函数；集合质心按几何维度及测度计算，不按所有顶点等权平均。
- BOUNDARY 支持上述具体六类，但不支持 GeometryCollection：已声明集合在 Compiler 拒绝，通用 GEOMETRY 中出现集合在真实执行拒绝。
- NULL→NULL；Empty 保留为空结果及原数据行。点包络可为 Point，竖直/水平线包络可为 LineString；凸包单点/共线退化不强转 Polygon。
- Point、闭合线的 Boundary 允许 Empty；面边界含外环和洞。CENTROID/POINT_ON_SURFACE 结果声明 Point，其余通用 Geometry。
- 新策略拒绝无效几何和非有限 XY；无法产生可靠 Z/M 时明确失败，不用 NaN 占位。
  对有效非空面才承诺 POINT_ON_SURFACE 内部点，不把非法/空面包含在保证中。
- 安全运行错误：`GEOMETRY_UNARY_INPUT_INVALID`、`GEOMETRY_UNARY_KIND_UNSUPPORTED`、`GEOMETRY_UNARY_DIMENSION_UNSUPPORTED`，SCHEMA、不可重试，不含坐标/WKT。
- 每项独立引用原来源列，单次惰性 select + UDF，未选表继续传播；不新增 Action、缓存、物化或 StreamingQuery，保持事件时间/Watermark。

### 已实现 UI

720px 紧凑规则编辑：`函数 (?) / 来源字段 / 输出字段 / 结果维度 / 排序与删除`。
常驻只预览前两项。新增规则为空函数、空来源、空输出和 PRESERVE_DIMENSION；选函数后可建议输出名，不自动选第一个字段。
每个函数的邻近帮助解释单要素含义、退化和维度；不支持的保留组合就地标红。
切换维度策略确认，说明仅结果丢弃 Z/M；取消 Modal 不提交，业务无效草稿可保存，删除需二次确认。
不与 Mean Center 混为同一函数，也不以主面板常驻长段说明替代规则帮助。

本地证据覆盖空/无效、带洞面质心与面内点差异、包络/凸包退化、集合限制、实际四维度和真实 Streaming 惰性计划；
未将这些自有函数宣称为 ArcGIS GeoAnalytics Server 的同名任务。

### 面板诊断补充（2026-09-08，协议不变）

- 规则使用短中文函数名，避免窄列中的英文枚举挤掉含义；精确语义仍在邻近帮助中解释。
- 缺少函数/来源、输出名冲突、不支持的维度和新策略集合边界限制均在对应行定位。
  有问题的行增加单行“ N 个配置问题”，悬停、键盘聚焦或点击查看完整详情；正常规则不增加诊断高度。
- 编译上下文缺失时已存表/字段显示“等待解析”，不误标失效；来源表不可用时保留字段并禁用候选选择。
- 不阻止保存上述业务无效草稿；规则弹窗取消仍不提交。真实页面与扩展回归已于 2026-09-13 完成，
  证据见[进度清单](../canvas-spatial-development-progress.md)。
