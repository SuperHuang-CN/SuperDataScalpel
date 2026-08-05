# Canvas `SPATIAL_CLIP` Processor 开发文档

## 1. 状态、目标与范围

- 实现状态：已实现并通过统一验收。
- 目标协议版本：Canvas `1.23`。
- 节点类型：`SPATIAL_CLIP`。
- 节点类别：`PROCESSOR`。
- 执行模式：仅 `BATCH`。
- 图规则：恰好两条入边，至少一条出边。

`SPATIAL_CLIP` 使用一张 Mask 表中的 Polygon/MultiPolygon 裁剪来源表 Geometry。节点保留
来源表全部属性并追加裁剪结果，不把 Mask 属性带入输出。

首版采用 INNER 裁剪语义：只有与 Mask 相交且能够产生非空交集的来源行才进入结果。一个来源
Geometry 命中多个 Mask 行时产生多条输出，不在节点内自动合并 Mask，也不保证输出行顺序。

## 2. 稳定配置协议

```ts
interface SpatialClipConfiguration {
  sourceTableName: string;
  maskTableName: string;
  outputTableName: string;
  sourceGeometryColumnName: string;
  maskGeometryColumnName: string;
  outputColumnName: string;
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
  "outputColumnName": "clipped_centerline"
}
```

协议限制：

- 来源表、Mask 表、输出表和三个字段名全部必填。
- 来源表与 Mask 表不能相同；节点从两个直接上游传播的表 Map 中精确选择两张逻辑表。
- 两个空间字段都必须有完整 Geometry 定义，并且仅支持 `EPSG + XY`。
- 两侧 CRS、坐标维度必须完全一致；不执行隐式 `ST_Transform`。
- Mask 字段的 GeometryKind 只允许 `POLYGON` 或 `MULTIPOLYGON`。
- 来源字段接受现有全部 GeometryKind，包括通用 `GEOMETRY` 和 `GEOMETRYCOLLECTION`。
- 输出字段不能与来源表已有字段重名。
- 配置不保存 Mask 属性、空间值、任意谓词、SQL、容差或精度参数。

## 3. 裁剪与行语义

Operator 使用以下固定计划：

1. 以 `ST_Intersects(sourceGeometry, maskGeometry)` 执行 INNER Join，缩小交集计算候选集。
2. 对候选行执行 `ST_Intersection(sourceGeometry, maskGeometry)`。
3. 用 `ST_SetSRID` 显式恢复来源 EPSG code。
4. 过滤 NULL 或 `ST_IsEmpty` 的交集结果。
5. 只投影来源表字段，并把裁剪 Geometry 追加到末尾。

因此：

- NULL 或 Empty 来源 Geometry 不匹配，不产生输出行。
- NULL 或 Empty Mask Geometry 不匹配，不产生输出行。
- 未命中任何 Mask 的来源行不进入输出。
- 一个来源行命中 N 条 Mask，最多产生 N 条输出；节点不做 DISTINCT 或 dissolve。
- 边界相交可能产生 Point/LineString，Polygon 裁剪也可能降维，输出 kind 固定声明为通用
  `GEOMETRY`。
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
- 结果字段追加到末尾，平台类型为 `GEOMETRY`，kind 为 `GEOMETRY`。
- 结果 CRS 和 dimension 继承来源 Geometry 定义。
- 结果字段为非 nullable；NULL 和 Empty 结果已被 INNER 语义过滤。
- 输出表 `origin=null`、`datasetKind=BOUNDED`，事件时间和 Watermark 清空。

节点只接受两张 BOUNDED 输入。它会改变行数并可能复制来源行，不保留来源表事件时间与
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
| `SPATIAL_CLIP_FAILED` | Runner | 真实数据空间裁剪失败 |

错误路径必须指向具体表或字段配置。Runner 错误不得包含 Geometry、坐标、WKT/WKB、Mask
值或来源数据行。

## 6. 前端设计器

- Palette 名称为“空间裁剪”，说明为“使用面要素裁剪来源 Geometry”。
- 位于“空间处理”分组，在 Geometry 序列化之后、空间聚合之前。
- Inspector 分为“来源”“Mask”“输出”三个紧凑分区。
- 表和字段候选只来自 Compiler `inputTables`；来源字段显示全部 Geometry，Mask 字段只突出
  Polygon/MultiPolygon，但已有失效值必须保留并标红。
- 选中两个字段后展示其 kind、CRS 和 dimension，并即时提示不匹配项。
- 面板固定提示“仅保留相交结果”“一个来源可能输出多行”“Mask 属性不会输出”。
- 节点摘要只显示来源表/字段、Mask 表/字段、输出表/字段和 CRS，不显示空间数据值。

## 7. Task Engine、Runner 与安全边界

新增唯一无状态 `SpatialClipNodeOperator`，Compiler 与 Runner 共享，并使用 Sedona
`ST_Intersects`、`ST_Intersection`、`ST_IsEmpty`、`ST_SetSRID` 构造计划。节点阶段为
`PROCESS`，成功消息为“空间裁剪已准备”。

安全摘要只允许记录表名、字段名、输出字段、CRS 和固定裁剪语义。不得记录 Geometry、坐标、
匹配数量、交集面积、结果 WKT/WKB 或任何数据行；成功日志不得为统计行数额外触发 Spark
Action。

## 8. 验收标准

- 配置能够严格 JSON 往返，并受 Canvas `1.23` 门槛约束。
- Polygon、MultiPolygon Mask 和不同来源 GeometryKind 能建立合法计划。
- NULL、Empty、未命中、单命中、多 Mask 命中的行语义正确。
- 边界相交产生的降维结果以通用 `GEOMETRY` 安全表达。
- Mask kind、CRS、dimension、有界性和字段冲突校验准确。
- 输出只包含来源字段与裁剪字段，Map、Schema、CRS 和 nullable 正确。
- Registry、图规则、Inspector、摘要、生命周期和失败分类完整接入。
- 日志与错误不包含空间数据值。

## 9. 不在范围内

- LEFT/OUTER 裁剪、保留未命中来源行、保留 Empty 结果。
- 输出 Mask 属性、Mask ID、命中数量或相交比例。
- 自动 union/dissolve Mask、去重来源行或稳定排序。
- 自动 CRS 转换、容差、snap、grid precision 或错误行跳过策略。
- 流式空间裁剪、Broadcast 参数或空间分区调优配置。
