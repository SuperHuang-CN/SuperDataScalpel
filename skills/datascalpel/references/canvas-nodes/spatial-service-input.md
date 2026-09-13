# SPATIAL_SERVICE_INPUT · 空间服务资源输入

[节点索引](index.md) · [Canvas 共用规则](../task-canvas.md)

## 用途与选择

批处理空间服务输入配置；从一个已启用且具有 SOURCE 用途的空间服务数据源读取一个或多个 ArcGIS REST/WFS 要素资源，每项产生一张带明确 EPSG、Geometry 类型和 XY 维度的 BOUNDED 表。

## 模式与连线

- 模式：BATCH；类别：INPUT。
- 无入边，至少一条出边；不读取上游 Map。
- 输入有界性见下文。

## 关键配置与行为

- 沿用资源的协议与字段映射，不把任意 WMS 瓦片或图片接口当作要素来源。
- 快照必须含空间字段与 CRS；不能凭地图显示效果猜坐标系。

配置定位（只列关键语义，完整字段读取实时契约）：

- `resources`：按顺序读取的空间要素资源；至少一项，同一资源 UUID 不能重复且必须属于 dataSourceId。各输出表名必须唯一；任一资源无效会使整个节点失败。

## 逻辑表 Map 与字段

每项按 outputTableName 输出 BOUNDED 要素表；快照使用资源 UUID 与 SPATIAL_FEATURE_RESOURCE。

## 最小配置示例

前提：已有启用的空间服务来源及 ArcGIS REST/WFS 资源，字段 Schema 含明确 EPSG、GeometryKind 和 XY 维度。

以下仅为节点 `configuration`；资源 UUID、字段与单位应换成已确认的真实元数据。稳定的操作/写入 ID 由当前任务生成。

```json
{
  "dataSourceId": "11111111-1111-4111-8111-111111111111",
  "resources": [
    {
      "resourceId": "22222222-2222-4222-8222-222222222222",
      "outputTableName": "areas"
    }
  ]
}
```

## 预校验检查与修正

将节点放入完整画布，按 Canvas 指南调用 Engine 预校验，核对输入/输出表及字段；此处没有真实数据测试步骤。

| 诊断或检查点 | 处理方式 |
| --- | --- |
| `SPATIAL_RESOURCE_NOT_FOUND` | 补齐所属数据源的资源结构。 |
| `DUPLICATE_TABLE_NAME` | 显式选择不同输出表名。 |

ERROR 修正后重新预校验最终定义；WARNING 记录影响，不把结构通过表述为数据值或输出结果通过。
