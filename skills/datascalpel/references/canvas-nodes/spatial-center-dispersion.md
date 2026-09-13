# SPATIAL_CENTER_DISPERSION · 空间中心与离散分析

[节点索引](index.md) · [Canvas 共用规则](../task-canvas.md)

## 用途与选择

有界批处理空间中心与离散分析配置。按最多 8 个标量字段分组，使用可选非负权重执行 1 至 16 种不重复分析。ANALYSIS_TABLES 为每项生成独立表并支持点、线、面家族，线面以质心参与位置计算；LEGACY_WIDE 只支持 Point 并把所有 Geometry 放入一张宽表。结果使用来源投影 CRS 的 XY 平面距离，不支持地理 CRS。

## 模式与连线

- 模式：BATCH；类别：PROCESSOR。
- 至少一条入边，允许零条或多条出边；从上游 Map 按逻辑表名取表，不按边序号取表。
- 输入必须为 BOUNDED。

## 关键配置与行为

- ANALYSIS_TABLES 每项独立逻辑表，1–16 种不重复分析；MEAN_CENTER/MEDIAN_CENTER/CENTRAL_FEATURE/STANDARD_DISTANCE/DIRECTIONAL_ELLIPSE 按业务目的选择。
- 最多 8 个标量分组字段，可选非负权重；线面在新模式中以质心参与位置计算，不支持地理 CRS。
- 标准距离/椭圆需合法 standardDeviations，中央要素需身份及属性选择；LEGACY_WIDE 仅支持 Point，并非新模式输出的同义写法。

配置定位（只列关键语义，完整字段读取实时契约）：

- `groupByColumns`：有序分组字段数组，必须存在、最多 8 项，字段按大小写不敏感规则不能重复且不能是 GEOMETRY；空数组把全部有效观测作为一个组。ANALYSIS_TABLES 每个正总权重组在每张分析表输出一行。
- `analyses`：有序分析数组，必须包含 1 至 16 项且不能含不完整项；analysisId 必须唯一 UUID，同一 kind 最多一次。ANALYSIS_TABLES 按数组顺序向 Map 追加独立结果表；LEGACY_WIDE 按数组顺序向同一结果行追加 Geometry 字段。
- `resultMode`：结果组织模式。ANALYSIS_TABLES 使用新版数值、容量和原要素语义，每项产生独立 BOUNDED 表；LEGACY_WIDE 或 null 使用旧算法并在一张 BOUNDED 宽表输出，来源必须为 POINT。两种模式都清除 Watermark，且都不计算平均/中位/椭圆时间结果。

## 逻辑表 Map 与字段

保留输入 Map；本例追加 mean_centers 一表。ANALYSIS_TABLES 使用每项 outputTableName，顶层 outputTableName 属于旧宽表模式；不同分析有不同结果列。

## 最小配置示例

前提：上游逻辑表 features 含 id:LONG 和 geom:GEOMETRY(POINT, EPSG:3857, XY)，字段名称与输出名不冲突。 输入 BOUNDED；示例为全局无权重均值中心。

以下仅为节点 `configuration`；资源 UUID、字段与单位应换成已确认的真实元数据。稳定的操作/写入 ID 由当前任务生成。

```json
{
  "sourceTableName": "features",
  "pointGeometryColumnName": "geom",
  "groupByColumns": [],
  "analyses": [
    {
      "analysisId": "11111111-1111-4111-8111-111111111111",
      "kind": "MEAN_CENTER",
      "outputColumnName": "mean_center",
      "outputTableName": "mean_centers"
    }
  ],
  "resultMode": "ANALYSIS_TABLES"
}
```

## 预校验检查与修正

将节点放入完整画布，按 Canvas 指南调用 Engine 预校验，核对输入/输出表及字段；此处没有真实数据测试步骤。

| 诊断或检查点 | 处理方式 |
| --- | --- |
| `SPATIAL_CENTER_GROUP_CAPACITY` | 说明分组规模与容量警告，不采样宣称安全。 |
| `SPATIAL_CENTER_ANALYSES_REQUIRED` | 至少提供一个当前可执行分析。 |

ERROR 修正后重新预校验最终定义；WARNING 记录影响，不把结构通过表述为数据值或输出结果通过。
