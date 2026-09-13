# SPATIAL_SUMMARIZE_WITHIN · 范围内汇总

[节点索引](index.md) · [Canvas 共用规则](../task-canvas.md)

## 用途与选择

有界批处理范围内汇总配置。将被汇总要素按真实 Geometry 与 Polygon/MultiPolygon 区域或生成的平面格网相交，按区域、可选时间窗和可选分类计算 1 至 32 项统计。保留输入 Map 并追加主结果，关联分组模式还追加组表；不隐式转换 CRS，也不承诺与 ArcGIS Summarize Within 完全等价。

## 模式与连线

- 模式：BATCH；类别：PROCESSOR。
- 至少一条入边，允许零条或多条出边；从上游 Map 按逻辑表名取表，不按边序号取表。
- 输入必须为 BOUNDED。

## 关键配置与行为

- AREA_TABLE 使用区域表；PLANAR_GRID 在来源投影 CRS 生成方格/六边形，不能同时当成另一套未声明区域来源。
- statistics 1–32 项，COUNT 不指定 sourceColumnName；线面统计需明确原值/按相交比例分摊及权重，不能把相交计数当作面积加权。
- includeEmptyAreas 控制空区域；groupSummary 配合 LINKED_TABLES 可生成主表+关联组表，必须有稳定区域键。可选 temporalSlicing 是有界统计分窗，不是实时状态处理。

配置定位（只列关键语义，完整字段读取实时契约）：

- `includeEmptyAreas`：是否保留没有相交要素的区域。true 时主结果为空区域补行，COUNT/COUNT_FIELD 为 0，其他无测量值统计通常为 null；启用时间切片时只为被汇总数据中实际出现的窗口补行，不凭空生成时间范围。关联组表不为此制造 null 分组行。
- `distanceMethod`：必填空间测量方法，统一用于 LENGTH_WITHIN、AREA_WITHIN、总量分摊、交叠权重和关联组形状比例。PLANAR 在当前 CRS 坐标空间计算；GEODESIC 仅接受两侧 EPSG:4326 XY。节点不自动投影。
- `lengthUnit`：必填长度输出单位，仅改变 LENGTH_WITHIN 最终显示值，不改变无量纲交叠比例。GEODESIC 不允许 SOURCE_CRS_UNIT；PLANAR 按来源 CRS 轴单位换算，地理 CRS 只允许来源角度单位。
- `areaUnit`：必填面积输出单位，仅改变 AREA_WITHIN 最终显示值，不改变无量纲交叠比例。GEODESIC 从平方米换算；PLANAR 投影 CRS 从坐标轴平方单位换算，地理 CRS 的角度平方不能换算为这些面积单位。

## 逻辑表 Map 与字段

保留输入 Map，追加命名结果表；outputTableName 必须与输入及其他结果表不同。 主结果为区域投影和统计列；启用关联分组时另追加不复制 Geometry 的组表。

## 最小配置示例

前提：areas 是带 area_id 和 Polygon area_geom 的 BOUNDED 区域表，features 为同 CRS/XY 的 BOUNDED Point 表；无同名输出字段。

以下仅为节点 `configuration`；资源 UUID、字段与单位应换成已确认的真实元数据。稳定的操作/写入 ID 由当前任务生成。

```json
{
  "areaTableName": "areas",
  "areaGeometryColumnName": "area_geom",
  "summaryTableName": "features",
  "summaryGeometryColumnName": "geom",
  "includeEmptyAreas": true,
  "distanceMethod": "PLANAR",
  "lengthUnit": "SOURCE_CRS_UNIT",
  "areaUnit": "SQUARE_METERS",
  "areaOutputColumns": [
    {
      "sourceSide": "LEFT",
      "sourceColumnName": "area_id",
      "outputColumnName": "area_id",
      "included": true
    }
  ],
  "statistics": [
    {
      "statisticId": "11111111-1111-4111-8111-111111111111",
      "kind": "COUNT",
      "sourceColumnName": null,
      "outputColumnName": "feature_count"
    }
  ],
  "outputTableName": "area_counts",
  "regions": {
    "mode": "AREA_TABLE"
  }
}
```

## 预校验检查与修正

将节点放入完整画布，按 Canvas 指南调用 Engine 预校验，核对输入/输出表及字段；此处没有真实数据测试步骤。

| 诊断或检查点 | 处理方式 |
| --- | --- |
| `SPATIAL_SUMMARIZE_AREA_OUTPUT_ONLY` | 区域投影只引用 LEFT 区域字段。 |
| `SPATIAL_WITHIN_STATISTICS_REQUIRED` | 至少定义一个统计及明确业务口径。 |

ERROR 修正后重新预校验最终定义；WARNING 记录影响，不把结构通过表述为数据值或输出结果通过。
