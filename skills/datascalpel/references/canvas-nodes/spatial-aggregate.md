# SPATIAL_AGGREGATE · 空间分组聚合

[节点索引](index.md) · [Canvas 共用规则](../task-canvas.md)

## 用途与选择

有界批处理空间聚合配置。按普通标量字段分组，对每组执行 1 至 32 个固定 Geometry 聚合；输出只含分组字段和聚合字段，不保留其他来源字段。基础模式和兼容 Dissolve 中空分组列表表示全局聚合，空来源仍输出一行 NULL 结果；显式连通组 Dissolve 按面要素相交或接触关系的传递闭包分别融合。结果无顺序保证，事件时间和 Watermark 被清空。

## 模式与连线

- 模式：BATCH；类别：PROCESSOR。
- 至少一条入边，允许零条或多条出边；从上游 Map 按逻辑表名取表，不按边序号取表。
- 输入必须为 BOUNDED。

## 关键配置与行为

- 支持 UNION/INTERSECTION/COLLECT/ENVELOPE 的 1–32 项几何聚合；分组字段为普通标量。
- 空分组表示全局聚合：空来源仍有一行 NULL 结果；有分组时空来源为零行。
- 单 UNION 可启用 Dissolve：`ALL_OR_FIELDS` 保持空分组全局 All、非空按字段值 List；
  `CONNECTED_COMPONENTS` 只允许空分组和 Polygon/MultiPolygon，按相交、重叠或接触关系的传递闭包分组。

配置定位（只列关键语义，完整字段读取实时契约）：

- `groupByColumns`：有序分组字段数组，必须存在但可以为空。字段不能重复且不能是 GEOMETRY；空数组把全部记录作为一个全局组。输出先按此顺序保留分组字段，并清空其物理来源、默认值和生成标记。
- `aggregations`：有序空间聚合项数组，必须包含 1 至 32 项且不能含 null。各项可使用不同 Geometry 字段和 CRS，独立继承自身来源 CRS/维度；输出按数组顺序位于分组字段之后。
- `dissolve.groupingMode`：Canvas 4.61 可选；缺失/null 保持 All/List。连通组模式过滤 NULL/Empty，
  每个分量独立计数和统计，且需要共享 Spark Checkpoint 目录。

## 逻辑表 Map 与字段

保留输入 Map，追加命名结果表；outputTableName 必须与输入及其他结果表不同。 结果只含分组列与聚合列，事件时间/Watermark 清空；不保证行顺序。

## 最小配置示例

前提：features 为 BOUNDED，含标量 region_id 和明确 CRS/XY 的 Polygon geom。

以下仅为节点 `configuration`；资源 UUID、字段与单位应换成已确认的真实元数据。稳定的操作/写入 ID 由当前任务生成。

```json
{
  "sourceTableName": "features",
  "outputTableName": "region_geometry",
  "groupByColumns": [
    "region_id"
  ],
  "aggregations": [
    {
      "kind": "UNION",
      "geometryColumnName": "geom",
      "outputColumnName": "merged_geom"
    }
  ]
}
```

## 预校验检查与修正

将节点放入完整画布，按 Canvas 指南调用 Engine 预校验，核对输入/输出表及字段；此处没有真实数据测试步骤。

| 诊断或检查点 | 处理方式 |
| --- | --- |
| `SPATIAL_AGGREGATE_REQUIRES_BOUNDED_INPUT` | 使用批处理有界来源。 |
| `EMPTY_SPATIAL_AGGREGATIONS` | 至少声明一项适用聚合。 |
| `SPATIAL_DISSOLVE_CONNECTED_GROUP_FIELDS_NOT_ALLOWED` | 连通组模式移除分组字段。 |
| `SPATIAL_DISSOLVE_CONNECTED_REQUIRES_POLYGON` | 使用 Polygon/MultiPolygon Geometry。 |

ERROR 修正后重新预校验最终定义；WARNING 记录影响，不把结构通过表述为数据值或输出结果通过。
