# GEOMETRY_BUFFER · 几何缓冲

[节点索引](index.md) · [Canvas 共用规则](../task-canvas.md)

## 用途与选择

逐行 Geometry 缓冲配置，支持批处理和流处理。保留来源字段并追加 MULTIPOLYGON 结果；NULL 输入返回 NULL，非空输入也可能产生 Empty，但不删除来源行。节点不转换 CRS、不合并相邻缓冲区，也不支持负距离、零距离或样式参数。

## 模式与连线

- 模式：BATCH / STREAMING；类别：PROCESSOR。
- 至少一条入边，允许零条或多条出边；从上游 Map 按逻辑表名取表，不按边序号取表。
- 输入有界性及批流差异见下文。

## 关键配置与行为

- distance 必须有限且大于 0，不支持负缓冲、零距离修复或样式参数。
- PLANAR 使用 CRS 坐标单位；SPHEROID 要求当前支持的 WGS84 条件，距离按米，不能忽略 CRS。相邻缓冲不自动融合。

配置定位（只列关键语义，完整字段读取实时契约）：

- `distance`：必填缓冲距离，必须为有限正数。PLANAR 时使用来源 CRS 坐标轴单位；SPHEROID 时固定为米。当前没有独立单位字段，也不会自动换算。
- `mode`：必填缓冲模式。PLANAR 直接使用来源 CRS 坐标空间，EPSG:4326 时距离为角度并产生警告；SPHEROID 使用 WGS84 椭球和米，只接受 EPSG:4326。

## 逻辑表 Map 与字段

保留输入 Map，追加命名结果表；outputTableName 必须与输入及其他结果表不同。 追加 MULTIPOLYGON，保留原列；NULL/Empty 情况不删除来源行。流模式继承来源有界性；事件时间与 Watermark 的实际传播以预校验 outputTables 为准。

## 最小配置示例

前提：上游逻辑表 features 含 id:LONG 和 geom:GEOMETRY(POINT, EPSG:3857, XY)，字段名称与输出名不冲突。

以下仅为节点 `configuration`；资源 UUID、字段与单位应换成已确认的真实元数据。稳定的操作/写入 ID 由当前任务生成。

```json
{
  "sourceTableName": "features",
  "outputTableName": "buffered_features",
  "geometryColumnName": "geom",
  "outputColumnName": "buffer_geom",
  "distance": 100.0,
  "mode": "PLANAR"
}
```

## 预校验检查与修正

将节点放入完整画布，按 Canvas 指南调用 Engine 预校验，核对输入/输出表及字段；此处没有真实数据测试步骤。

| 诊断或检查点 | 处理方式 |
| --- | --- |
| `INVALID_GEOMETRY_BUFFER_DISTANCE` | 修正距离，负缓冲需求另报限制。 |
| `PLANAR_BUFFER_USES_ANGULAR_UNITS` | 解释角度单位警告，米制需求需重新确定投影/模式。 |

ERROR 修正后重新预校验最终定义；WARNING 记录影响，不把结构通过表述为数据值或输出结果通过。
