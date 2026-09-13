# SPATIAL_OVERLAY · 空间叠加

[节点索引](index.md) · [Canvas 共用规则](../task-canvas.md)

## 用途与选择

有界批处理空间叠加配置；按要素执行相交、擦除、联合、标识或对称差，并显式投影左右属性。结果追加为不带事件时间和 Watermark 的有界表；不保证行顺序，也不把同侧重叠要素整理成全局无重叠分区。

## 模式与连线

- 模式：BATCH；类别：PROCESSOR。
- 至少一条入边，允许零条或多条出边；从上游 Map 按逻辑表名取表，不按边序号取表。
- 输入必须为 BOUNDED。

## 关键配置与行为

- INTERSECTION/ERASE/UNION/IDENTITY/SYMMETRICAL_DIFFERENCE 影响保留范围和左右属性可空性；按业务目的选用。
- FAMILY_2D 保持目标几何家族策略，LEGACY_GEOMETRY 是兼容模式。左右字段显式投影，不保证同侧重叠被变成全局无重叠分区。

配置定位（只列关键语义，完整字段读取实时契约）：

- `leftGeometryColumnName`：必填的左侧 Geometry 字段名；字段必须带完整几何类型、CRS 和坐标维度元数据。
- `rightGeometryColumnName`：必填的右侧 Geometry 字段名；字段必须带完整几何元数据，并与左侧使用相同 CRS 和坐标维度，系统不会隐式重投影。
- `operation`：必填的叠加运算：INTERSECTION 输出成对交叠；ERASE 输出左侧减去全部相交右侧遮罩；UNION 输出成对交叠及双方独有部分；IDENTITY 输出成对交叠及左侧独有部分；SYMMETRICAL_DIFFERENCE 只输出双方独有部分。
- `outputGeometryColumnName`：必填且不能与投影字段重名的结果 Geometry 字段名；NULL 或 Empty 结果不会生成记录。

## 逻辑表 Map 与字段

保留输入 Map，追加命名结果表；outputTableName 必须与输入及其他结果表不同。 输出投影列及新 Geometry，BOUNDED 且不带 Watermark；可能产生一对多片段。

## 最小配置示例

前提：两张不同 BOUNDED 表具有同投影 CRS、XY Polygon 几何及各自标量 ID。

以下仅为节点 `configuration`；资源 UUID、字段与单位应换成已确认的真实元数据。稳定的操作/写入 ID 由当前任务生成。

```json
{
  "leftTableName": "areas",
  "leftGeometryColumnName": "area_geom",
  "rightTableName": "zones",
  "rightGeometryColumnName": "zone_geom",
  "operation": "INTERSECTION",
  "outputTableName": "overlaid",
  "outputGeometryColumnName": "overlay_geom",
  "outputColumns": [
    {
      "sourceSide": "LEFT",
      "sourceColumnName": "area_id",
      "outputColumnName": "area_id",
      "included": true
    },
    {
      "sourceSide": "RIGHT",
      "sourceColumnName": "zone_id",
      "outputColumnName": "zone_id",
      "included": true
    }
  ],
  "geometryPolicy": "FAMILY_2D"
}
```

## 预校验检查与修正

将节点放入完整画布，按 Canvas 指南调用 Engine 预校验，核对输入/输出表及字段；此处没有真实数据测试步骤。

| 诊断或检查点 | 处理方式 |
| --- | --- |
| `SPATIAL_OVERLAY_CRS_MISMATCH` | 先设计真实坐标转换，不篡改 CRS。 |
| `BOUNDED_INPUT_REQUIRED` | 仅用于有界批任务。 |

ERROR 修正后重新预校验最终定义；WARNING 记录影响，不把结构通过表述为数据值或输出结果通过。
