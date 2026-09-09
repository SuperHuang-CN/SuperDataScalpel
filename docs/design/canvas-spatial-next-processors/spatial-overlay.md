# Canvas `SPATIAL_OVERLAY` Processor 设计

## 1. 定位与审计结论

引入于 Canvas 4.13，仅 BATCH，两表有界输入。4.20 时仅有 INTERSECTION/ERASE/UNION；4.26 补齐 Identity、Symmetrical Difference 与显式家族策略，见第 7 节。五种模式可用不等于精度及全部边界已与官方完全对齐。

本文区分 4.20 审计快照、目标设计及第 7 节的后续实现。开发状态以[进度清单](../canvas-spatial-development-progress.md)为准；前期设计修订本身不改变协议，后续开发通过显式可选策略保留旧任务语义。

### 当前阅读入口与对齐边界（4.45 汇总）

- **已接入范围**：4.26 的五种模式、输入组合与 FAMILY_2D 输出见第 7 节。
- **官方参照与差异**：参照 GA Overlay Layers；成对叠置不代表全局无重叠分区，Esri 精度和拓扑边界尚未对照完成。
- **面板修订要求**：按所选模式展示可用 Geometry 组合及字段投影；不设置未经实现的官方容差参数。

本摘要不替代逐版本契约。下节的“当前”均指 4.20 历史状态；目标线框不作为已实现截图。
参数覆盖、本地验证和官方结果对照分别登记，见[对齐验收规则](../canvas-spatial-analysis-processor-roadmap.md#7-对齐验收)。

## 2. 4.20 配置快照（历史）

```ts
type SpatialOverlayOperation =
  | 'INTERSECTION'
  | 'ERASE'
  | 'UNION';

interface SpatialOverlayConfiguration {
  leftTableName: string;
  leftGeometryColumnName: string;
  rightTableName: string;
  rightGeometryColumnName: string;
  operation: SpatialOverlayOperation | null;
  outputTableName: string;
  outputGeometryColumnName: string;
  outputColumns: JoinOutputColumn[];
}
```

- 当前 outputColumns 复用 Join 投影；ERASE 只允许左侧属性，右侧重名建议完整右表名前缀。
- 当前同 CRS/dimension，结果通用 GEOMETRY；不要求两条物理入边。
- 当前配置没有几何组合/精度策略字段，也没有 Identity/Symmetrical Difference；新增模式不是本轮协议变更。

## 3. 官方五模式及输入组合

下表几何家族是拓扑类型，与 XY/XYZ 坐标维度不是同一概念。

| 模式 | 目标结果 | 官方允许的输入家族（左→右） |
| --- | --- | --- |
| Intersect | 双方交叠，附双方投影属性 | 点/线/面任意组合 |
| Erase | 左侧减去右侧覆盖范围 | 点→点、线→线、面→面 |
| Union | 两层覆盖及交叠分区，附对应属性 | 面→面 |
| Identity | 保留左层覆盖，交叠部分附右属性 | 点→点/面、线→线/面、面→面 |
| Symmetrical Difference | 双方各自独有、不重叠部分 | 点→点、线→线、面→面 |

- Identity 和 Symmetrical Difference 是官方能力，应纳入补齐范围，不再视作“不必对齐”。
- Erase 允许组合比通用 JTS difference 少；如果保留额外组合，应明确为自有扩展而非 GA 行为。
- 每种组合确定输出家族、Multipart/碎片与低维接触行为；不能用 GEOMETRY 泛型掩盖规则缺失。
- 官方提示精度容差可能排除细碎面；当前 JTS/Sedona 精度不等于 ArcGIS 容差模型。
  不暗加 tolerance 或 snap，先以交叉案例量化差异。
- NULL/Empty、仅接触、重叠右要素、同侧重叠和无效面必须分别规定。
  不以“双方所有切分片段”笼统保证 pairwise 结果已完成全局拓扑分区。

## 4. 目标 Inspector UI

设计状态：五模式已在第 7 节（4.26）接入；可选输入组合及实际二维结果以该节为准。Esri 精度容差和全局分区等价性仍未验收，不为它们绘制可用的兼容开关。

```text
左层 / Geometry     [ parcels / boundary ▼]
右层 / Geometry     [ planning / zone_geometry ▼]
叠加方式            [相交 | 擦除 | 联合 | 标识 | 对称差]
允许输入            面 → 面                       (?)
结果字段            [ overlay_geometry ]
输出投影            8 项                          [设置]
输出表              [ parcel_planning_overlay ]
```

根据家族禁用不支持模式并解释原因，旧失效模式仍回显标红；未实现新模式不能提前开放。
模式帮助以小示意和一句话解释行保留，不常驻大段说明。
投影 Modal 支持排除/改名/排序；切换模式保留用户投影，不自动清除右属性，失效项就地标错。
若精度政策尚未完成，只提示差异，不添加无实现的“ArcGIS 兼容”开关。


### 参数交互与初值（目标设计）

下表是目标面板约定，不修改旧任务默认值；未明确标注为官方默认的初值均为平台推荐。

| 配置组 | 初值与条件显示 | 对齐边界 |
| --- | --- | --- |
| 叠加方式 | 由用户明确选择五种模式之一；帮助显示左/右保留区域 | 几何组合按第 3 节矩阵约束 |
| 字段投影 | 显式选择来源侧、字段、包含与输出名；保持既有完整右表名前缀建议 | 缺失侧字段可空；不是内连接的非空保证 |
| 结果 Geometry | 按模式及输入家族说明输出家族和退化规则 | 不要将坐标维度 XY 与点/线/面维数混淆 |
| 精度 | 仅提供已实现的数值语义及差异帮助，不放无实现的兼容开关 | Esri 容差与 Sedona/JTS 不能直接画等号 |

选择 ERASE 后右侧属性仍保留在草稿，但标为不适用并要求用户处理；再切回其他模式时恢复，不能静默丢字段。

## 5. 执行和验收

- 预检全部字段、名称、CRS、坐标维度和几何组合；创建新 BOUNDED 表并保留入口 Map。
- 检查实际空间分区/索引与聚合计划，不要求不存在的优化已完成；禁止全表右 Geometry collect 到 Driver。
- 验收五模式的几何/属性：相交、分离、包含、接触边/点、洞、重叠、多部件、退化/Empty。
- 特别测试多个右要素覆盖同一左要素，Erase 不能按 pairwise 差集错误重复保留；Union 不得把左/右独有区遗漏。
- Identity 左侧未匹配部分应保留；缺失侧属性为 NULL，输出字段顺序按投影。
- 几何组合矩阵中的拒绝项逐项验证；低维接触输出与微小碎片按官方样例确定后才声明等价。
- 清除事件时间/Watermark；不承诺行顺序或与 Esri 完全相同的顶点编码。
- Canvas 显示模式/表/字段数，不显示 Geometry、交叠面积和片段数据。

## 6. 官方依据

- [Overlay Layers：五种模式、几何组合与精度提示](https://developers.arcgis.com/rest/services-reference/enterprise/geoanalytics/tasks/overlay-layers/)
- [共同规则与版本策略](../canvas-spatial-analysis-processor-roadmap.md)

本目标不扩展三层以上一次叠加、Streaming、自动 Repair 或隐式 CRS 转换。

## 7. Canvas 4.26 实现：五模式与显式几何家族

### 配置与兼容

```ts
type SpatialOverlayOperation =
  | 'INTERSECTION' | 'ERASE' | 'UNION' | 'IDENTITY' | 'SYMMETRICAL_DIFFERENCE';

// 在第 2 节配置中新增可选字段；没有删除或改变已有字段。
geometryPolicy?: 'FAMILY_2D' | 'LEGACY_GEOMETRY' | null;
```

- 旧三模式的缺失/null 策略仍采用旧版通用 Geometry 路径，保留原先的跨家族差集等平台扩展。
- 新建 Inspector 默认 FAMILY_2D。新两模式缺失/null 策略按 FAMILY_2D；显式 LEGACY_GEOMETRY 不适用于新两模式，但允许保存草稿。
- 任意非 null geometryPolicy 或新两模式都要求 4.26；低版本返回 `SPATIAL_OVERLAY_FAMILY_REQUIRE_SCHEMA_VERSION`。
  节点引入版本仍为 4.13，Manifest、Task Result、HTTP API 不变。
- Java 保留八参数便利构造器和投影数组防御性复制；前端不为旧定义补写策略，不在打开时升级执行语义。
- operation=null 是合法未完成草稿；发布时返回必填配置问题。

### 家族、维度和退化结果

FAMILY_2D 校验第 3 节完整组合矩阵，Single/Multi 均归入点/线/面家族；通用 GEOMETRY/GEOMETRYCOLLECTION 不能作为该策略的确定家族。

| 模式 | 输出 GeometryKind | 缺失侧属性 |
| --- | --- | --- |
| INTERSECTION | 两侧中较低家族对应 Multi 类型 | 无额外空侧 |
| ERASE | 左侧家族对应 Multi 类型 | 不允许启用右侧字段 |
| IDENTITY | 左侧家族对应 Multi 类型 | 未匹配部分右侧为 NULL |
| UNION | MULTIPOLYGON | 任一缺失侧为 NULL |
| SYMMETRICAL_DIFFERENCE | 左侧家族对应 Multi 类型 | 任一缺失侧为 NULL |

- 以上结果字段实际归一化为 XY，与 Schema 一致；来源为 XYZ/XYM/XYZM 时警告 `SPATIAL_OVERLAY_OUTPUT_XY`，不保留 Z/M。
  投影中直接选出的原 Geometry 属性不被改写，上游 Dataset 不变。两侧仍要求同 CRS、同坐标维度；没有隐式投影或 snap。
- 提取指定家族后转 Multi。面与面仅接触边/点不产生面记录；线与线仅点接触不产生线记录；点与线相交产生 MultiPoint。
  这是明确的平台片段政策，尚不宣称已通过全部官方退化边界对照。
- 一个来源对或来源独有区保留为一个 Multi 记录，不自动 Explode；碎片与洞保留，无数值容差或碎片面积阈值。
- NULL/Empty 输入不参加拓扑运算，NULL/Empty 输出不生成记录。无效几何不自动修复，消费实际计划时以 `SPATIAL_OVERLAY_INVALID_GEOMETRY` 失败。
  分类为不可重试 SCHEMA，消息不含 WKT、坐标或来源字段值。Compiler 仅建计划，不额外扫描证明所有行有效。

### 执行粒度与限制

- 相交为左/右要素成对交叠；Identity 是相交加左独有；Union 是相交加双方独有；对称差只有双方独有。
- 每个来源要素以计划内局部行身份聚合其命中的全部遮罩，ST_Union_Agg 后只执行一次 Difference。
  不使用“每个右要素各减一次再拼接”的错误算法，不 collect 到 Driver，不增加 Spark Action 或缓存。
- 同侧重复/重叠要素仍独立，右侧多要素重叠可使成对相交记录在空间上重叠。输出不等于全局无重叠平面分区，不能直接对重叠片段求和当作 Union 面积。
- 不保证行顺序，投影字段顺序明确；左右来源保留在入口 Map 原位，新表追加；结果 BOUNDED 且不继承时间列/Watermark。
- 按别名限定未解析列，支持左右 Dataset 共享上游 Spark 计划；不关闭 Spark 自连接歧义检查。

### 已实现 UI

```text
左图层 / Geometry   [ parcels / boundary ▼]
右图层 / Geometry   [ zones / shape ▼]
叠加方式            [ 标识 ▼ ]                  (?)
几何输出            [ 图层家族 · 二维多部件 ▼ ]  (?)
                    MultiPolygon · XY
结果 Geometry       [ overlay_geometry ]
输出表              [ parcel_zones ]
输出字段            8 / 10                      [设置]
```

- 五模式改用紧凑 Select，避免五个分段按钮撑宽 Inspector；不支持组合禁用并带 Tooltip，已保存失效值继续回显和标错。
- 几何策略切换需确认；说明中明确结果类型、Z/M 丢弃、低维片段与 Esri 精度差异。没有“ArcGIS 完全兼容”开关。
- 右侧属性在 ERASE 中保留并提示排除；不因切换方式重建投影。按当前方式重建建议需确认。
- 未挂载字段弹窗仍完整保存投影；无效业务配置允许应用。Canvas 与日志只记录模式、策略、来源/输出安全名称及字段数。

### 仍未完成

没有真实 Enterprise 11.3 环境交叉验收；Esri 容差、微小碎片、同侧重叠/全局分区、几何编码和大数据空间分区/索引性能仍待对照，不在进度清单勾选全部完成。
