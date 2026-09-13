# GEOMETRY_DERIVE · 几何派生

[节点索引](index.md) · [Canvas 共用规则](../task-canvas.md)

## 用途与选择

逐行几何派生配置，支持批处理和流处理。保留来源表全部字段，并按 derivations 顺序追加 1 至 32 个 Geometry 字段；每项都只读取进入节点时的原始来源 Schema，不能引用同一节点刚生成的字段。NULL 来源产生 NULL 结果，不删除原行；输出表继承来源有界性、事件时间和 Watermark。

## 模式与连线

- 模式：BATCH / STREAMING；类别：PROCESSOR。
- 至少一条入边，允许零条或多条出边；从上游 Map 按逻辑表名取表，不按边序号取表。
- 输入有界性及批流差异见下文。

## 关键配置与行为

- 支持 CENTROID/POINT_ON_SURFACE/ENVELOPE/CONVEX_HULL/BOUNDARY，按用途选择；质心不保证位于面内。
- 1–32 项，derivationId 唯一；同节点各项只能读取原来源列。明确 PRESERVE_DIMENSION/OUTPUT_XY 等策略，不能暗中丢 Z/M。

配置定位（只列关键语义，完整字段读取实时契约）：

- `derivations`：有序派生项列表，必须包含 1 至 32 项且不能含 null；输出字段按此顺序追加。各项相互独立，只能读取来源表原有 Geometry 字段。

## 逻辑表 Map 与字段

保留输入 Map，追加命名结果表；outputTableName 必须与输入及其他结果表不同。 按顺序追加派生 Geometry，不删除 NULL 行；输出类型依算法和维度策略决定。流模式继承来源有界性；事件时间与 Watermark 的实际传播以预校验 outputTables 为准。

## 最小配置示例

前提：上游逻辑表 features 含 id:LONG 和 geom:GEOMETRY(POLYGON, EPSG:3857, XY)。

以下仅为节点 `configuration`；资源 UUID、字段与单位应换成已确认的真实元数据。稳定的操作/写入 ID 由当前任务生成。

```json
{
  "sourceTableName": "features",
  "outputTableName": "centers",
  "derivations": [
    {
      "derivationId": "11111111-1111-4111-8111-111111111111",
      "kind": "CENTROID",
      "sourceColumnName": "geom",
      "outputColumnName": "center",
      "geometryPolicy": "OUTPUT_XY"
    }
  ]
}
```

## 预校验检查与修正

将节点放入完整画布，按 Canvas 指南调用 Engine 预校验，核对输入/输出表及字段；此处没有真实数据测试步骤。

| 诊断或检查点 | 处理方式 |
| --- | --- |
| `GEOMETRY_DERIVATION_COUNT_EXCEEDED` | 控制项数，按依赖拆分节点。 |
| `INVALID_GEOMETRY_DERIVATION` | 检查种类、源类型及维度策略组合。 |

ERROR 修正后重新预校验最终定义；WARNING 记录影响，不把结构通过表述为数据值或输出结果通过。
