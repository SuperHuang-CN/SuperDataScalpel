# SPATIAL_CLIP · 空间裁剪

[节点索引](index.md) · [Canvas 共用规则](../task-canvas.md)

## 用途与选择

有界批处理空间裁剪配置。使用 Polygon/MultiPolygon Mask 与来源 Geometry 做相交 INNER Join，再计算 Intersection；只保留非 NULL、非 Empty 的交集。输出保留来源属性并追加通用 GEOMETRY，不输出 Mask 属性；一个来源行命中多条 Mask 时会产生多行，节点不去重或合并 Mask。

## 模式与连线

- 模式：BATCH；类别：PROCESSOR。
- 至少一条入边，允许零条或多条出边；从上游 Map 按逻辑表名取表，不按边序号取表。
- 输入必须为 BOUNDED。

## 关键配置与行为

- 相交 INNER Join 后取 Intersection，过滤 NULL/Empty 交集。
- 多个 Mask 命中同一来源行会产生多行；不自动合并 Mask 或去重，不带 Mask 属性。

## 逻辑表 Map 与字段

保留输入 Map，追加命名结果表；outputTableName 必须与输入及其他结果表不同。 保留来源属性并追加通用 GEOMETRY；原几何仍在，行数取决于匹配数量。

## 最小配置示例

前提：features 和 areas 为 BOUNDED，几何 CRS/维度一致；areas.area_geom 为 Polygon/MultiPolygon。

以下仅为节点 `configuration`；资源 UUID、字段与单位应换成已确认的真实元数据。稳定的操作/写入 ID 由当前任务生成。

```json
{
  "sourceTableName": "features",
  "maskTableName": "areas",
  "outputTableName": "clipped_features",
  "sourceGeometryColumnName": "geom",
  "maskGeometryColumnName": "area_geom",
  "outputColumnName": "clipped_geom"
}
```

## 预校验检查与修正

将节点放入完整画布，按 Canvas 指南调用 Engine 预校验，核对输入/输出表及字段；此处没有真实数据测试步骤。

| 诊断或检查点 | 处理方式 |
| --- | --- |
| `SPATIAL_CLIP_MASK_KIND_UNSUPPORTED` | 选 Polygon/MultiPolygon 掩膜。 |
| `SPATIAL_CLIP_CRS_MISMATCH` | 明确相同 CRS，不能只改元数据标记。 |

ERROR 修正后重新预校验最终定义；WARNING 记录影响，不把结构通过表述为数据值或输出结果通过。
