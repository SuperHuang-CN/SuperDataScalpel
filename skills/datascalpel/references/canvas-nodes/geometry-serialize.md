# GEOMETRY_SERIALIZE · 几何序列化

[节点索引](index.md) · [Canvas 共用规则](../task-canvas.md)

## 用途与选择

逐行 Geometry 序列化配置，支持批处理和流处理。保留原 Geometry 及全部来源字段，并追加一个普通 STRING 或 BINARY 字段；NULL 输入返回 NULL且不改变行数。节点只改变表示形式，不转换 CRS、不修复或简化 Geometry，也不生成 GeoJSON Feature/FeatureCollection。

## 模式与连线

- 模式：BATCH / STREAMING；类别：PROCESSOR。
- 至少一条入边，允许零条或多条出边；从上游 Map 按逻辑表名取表，不按边序号取表。
- 输入有界性及批流差异见下文。

## 关键配置与行为

- WKT/GEOJSON 输出 STRING，WKB 输出 BINARY；按下游需要选择，不伪装空间类型仍被保留在序列化列中。
- 只生成单几何表示，不生成 GeoJSON Feature/FeatureCollection、不改 CRS、不修复。

配置定位（只列关键语义，完整字段读取实时契约）：

- `format`：必填序列化格式。WKT 输出文本但不携带可信 CRS；WKB 输出原始二进制而不是十六进制文本且不携带平台可信 CRS；GEOJSON 输出单个 Geometry JSON 文本并要求来源为 EPSG:4326。

## 逻辑表 Map 与字段

保留输入 Map，追加命名结果表；outputTableName 必须与输入及其他结果表不同。 原 Geometry 保留，追加普通字符串/二进制字段，NULL 保留。流模式继承来源有界性；事件时间与 Watermark 的实际传播以预校验 outputTables 为准。

## 最小配置示例

前提：上游逻辑表 features 含 id:LONG 和 geom:GEOMETRY(POINT, EPSG:3857, XY)，字段名称与输出名不冲突。

以下仅为节点 `configuration`；资源 UUID、字段与单位应换成已确认的真实元数据。稳定的操作/写入 ID 由当前任务生成。

```json
{
  "sourceTableName": "features",
  "outputTableName": "serialized_features",
  "geometryColumnName": "geom",
  "outputColumnName": "geom_wkt",
  "format": "WKT"
}
```

## 预校验检查与修正

将节点放入完整画布，按 Canvas 指南调用 Engine 预校验，核对输入/输出表及字段；此处没有真实数据测试步骤。

| 诊断或检查点 | 处理方式 |
| --- | --- |
| `INVALID_GEOMETRY_SERIALIZATION_FORMAT` | 选受支持格式。 |
| `DUPLICATE_COLUMN_NAME` | 不要覆盖原几何列。 |

ERROR 修正后重新预校验最终定义；WARNING 记录影响，不把结构通过表述为数据值或输出结果通过。
