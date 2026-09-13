# GEOMETRY_REPAIR · 几何修复

[节点索引](index.md) · [Canvas 共用规则](../task-canvas.md)

## 用途与选择

Geometry 修复配置；保留来源表全部行与字段，并用 Sedona ST_MakeValid(keepCollapsed=false) 追加修复结果。结果 CRS 和维度继承来源，但 GeometryKind 固定声明为通用 GEOMETRY，因为修复可能改变具体类型。

## 模式与连线

- 模式：BATCH / STREAMING；类别：PROCESSOR。
- 至少一条入边，允许零条或多条出边；从上游 Map 按逻辑表名取表，不按边序号取表。
- 输入有界性及批流差异见下文。

## 关键配置与行为

- 通过 ST_MakeValid 且 keepCollapsed=false 生成修复结果；修复可能改变具体几何类别。
- 保留原几何供后续明确选择，不隐式替换业务来源。具体值是否可修复不在本流程验收。

## 逻辑表 Map 与字段

保留输入 Map，追加命名结果表；outputTableName 必须与输入及其他结果表不同。 追加通用 GEOMETRY 类型，继承 CRS 和维度，保留原行列。流模式继承来源有界性；事件时间与 Watermark 的实际传播以预校验 outputTables 为准。

## 最小配置示例

前提：上游逻辑表 features 含 id:LONG 和 geom:GEOMETRY(POLYGON, EPSG:3857, XY)。

以下仅为节点 `configuration`；资源 UUID、字段与单位应换成已确认的真实元数据。稳定的操作/写入 ID 由当前任务生成。

```json
{
  "sourceTableName": "features",
  "outputTableName": "repaired_features",
  "geometryColumnName": "geom",
  "outputColumnName": "repaired_geom"
}
```

## 预校验检查与修正

将节点放入完整画布，按 Canvas 指南调用 Engine 预校验，核对输入/输出表及字段；此处没有真实数据测试步骤。

| 诊断或检查点 | 处理方式 |
| --- | --- |
| `GEOMETRY_FIELD_OPERATION_UNSUPPORTED` | 核对来源 Geometry 类型。 |
| `DUPLICATE_COLUMN_NAME` | 为修复结果选择不冲突新列。 |

ERROR 修正后重新预校验最终定义；WARNING 记录影响，不把结构通过表述为数据值或输出结果通过。
