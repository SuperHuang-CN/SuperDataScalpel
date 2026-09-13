# GEOMETRY_EXPLODE · 几何部件拆分

[节点索引](index.md) · [Canvas 共用规则](../task-canvas.md)

## 用途与选择

逐行 Geometry 拆分配置，支持批处理和流处理。使用外层展开把 MultiGeometry 或 GeometryCollection 的部件拆成多行并复制全部来源字段；单部件输入产生一行，NULL 或 Empty 输入也保留一行且部件为空。节点可能增加行数，不设置每行展开上限。

## 模式与连线

- 模式：BATCH / STREAMING；类别：PROCESSOR。
- 至少一条入边，允许零条或多条出边；从上游 Map 按逻辑表名取表，不按边序号取表。
- 输入有界性及批流差异见下文。

## 关键配置与行为

- MultiGeometry/GeometryCollection 按部件展开并复制属性；没有每行展开上限，说明可能放大行数。
- 单部件一行；NULL 或 Empty 仍保留一行空部件，不能当作自动清洗。partIndexColumnName 可不配置。

配置定位（只列关键语义，完整字段读取实时契约）：

- `partIndexColumnName`：可选部件序号字段名。null 表示不输出；非 null 时必须为非空且不重名，追加 nullable INTEGER，正常部件按 ST_Dump 顺序从 0 开始，NULL 或 Empty 输入时为 null。

## 逻辑表 Map 与字段

保留输入 Map，追加命名结果表；outputTableName 必须与输入及其他结果表不同。 原列保留，追加部件 Geometry 和可选序号；结果粒度是来源行×部件。流模式继承来源有界性；事件时间与 Watermark 的实际传播以预校验 outputTables 为准。

## 最小配置示例

前提：features 含 geom:GEOMETRY(MULTIPOLYGON,EPSG:3857,XY)；可接受一条来源生成多条结果。

以下仅为节点 `configuration`；资源 UUID、字段与单位应换成已确认的真实元数据。稳定的操作/写入 ID 由当前任务生成。

```json
{
  "sourceTableName": "features",
  "outputTableName": "geometry_parts",
  "geometryColumnName": "geom",
  "outputColumnName": "part_geom",
  "partIndexColumnName": "part_index"
}
```

## 预校验检查与修正

将节点放入完整画布，按 Canvas 指南调用 Engine 预校验，核对输入/输出表及字段；此处没有真实数据测试步骤。

| 诊断或检查点 | 处理方式 |
| --- | --- |
| `DUPLICATE_COLUMN_NAME` | 部件和序号字段需使用新名称。 |
| `GEOMETRY_FIELD_OPERATION_UNSUPPORTED` | 核对受支持 Geometry。 |

ERROR 修正后重新预校验最终定义；WARNING 记录影响，不把结构通过表述为数据值或输出结果通过。
