# GEOMETRY_VALIDATE · 几何有效性标记

[节点索引](index.md) · [Canvas 共用规则](../task-canvas.md)

## 用途与选择

Geometry 有效性校验配置；保留来源表全部行与字段，并追加 Sedona/JTS 有效性布尔值及可选无效原因。它只诊断，不过滤、修复或替换来源 Geometry。

## 模式与连线

- 模式：BATCH / STREAMING；类别：PROCESSOR。
- 至少一条入边，允许零条或多条出边；从上游 Map 按逻辑表名取表，不按边序号取表。
- 输入有界性及批流差异见下文。

## 关键配置与行为

- 追加有效性标记和可选原因，不过滤坏几何，不修复、不替换原列。
- 这是一种未来运行的业务处理器；设计期只做 Engine 预校验，不能因其名称含 Validate 就执行真实数据检查。

配置定位（只列关键语义，完整字段读取实时契约）：

- `validColumnName`：追加的 BOOLEAN 结果字段名，不能与任何来源字段或 reasonColumnName 同名。true 表示有效，false 表示无效，来源 Geometry 为 NULL 时为 NULL。
- `reasonColumnName`：可选追加的 STRING 原因字段名；传 null 表示不生成。仅在合法性结果为 false 时写 ST_IsValidReason，几何有效或为 NULL 时结果为 NULL；空字符串无效。

## 逻辑表 Map 与字段

保留输入 Map，追加命名结果表；outputTableName 必须与输入及其他结果表不同。 保留原行列，追加布尔标记及可选字符串原因；不改变来源 Geometry。流模式继承来源有界性；事件时间与 Watermark 的实际传播以预校验 outputTables 为准。

## 最小配置示例

前提：上游逻辑表 features 含 id:LONG 和 geom:GEOMETRY(POLYGON, EPSG:3857, XY)。

以下仅为节点 `configuration`；资源 UUID、字段与单位应换成已确认的真实元数据。稳定的操作/写入 ID 由当前任务生成。

```json
{
  "sourceTableName": "features",
  "outputTableName": "checked_features",
  "geometryColumnName": "geom",
  "validColumnName": "geom_valid",
  "reasonColumnName": "geom_reason"
}
```

## 预校验检查与修正

将节点放入完整画布，按 Canvas 指南调用 Engine 预校验，核对输入/输出表及字段；此处没有真实数据测试步骤。

| 诊断或检查点 | 处理方式 |
| --- | --- |
| `GEOMETRY_FIELD_OPERATION_UNSUPPORTED` | 确认字段为支持的 Geometry。 |
| `DUPLICATE_COLUMN_NAME` | 标记与原因字段应是不同新名称。 |

ERROR 修正后重新预校验最终定义；WARNING 记录影响，不把结构通过表述为数据值或输出结果通过。
