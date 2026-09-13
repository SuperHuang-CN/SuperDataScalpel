# SPATIAL_MEASURE · 空间量测

[节点索引](index.md) · [Canvas 共用规则](../task-canvas.md)

## 用途与选择

逐行空间量测配置，支持批处理和流处理。保留来源表全部字段，并按 measurements 顺序追加 1 至 32 个 nullable DOUBLE 字段；各项只读取进入节点时的原始 Geometry 字段且彼此独立。节点不隐式转换 CRS，不在 Schema 中虚构结果单位。

## 模式与连线

- 模式：BATCH / STREAMING；类别：PROCESSOR。
- 至少一条入边，允许零条或多条出边；从上游 Map 按逻辑表名取表，不按边序号取表。
- 输入有界性及批流差异见下文。

## 关键配置与行为

- 1–32 项 AREA/LENGTH/PERIMETER/DISTANCE/X/Y；各分支配置不同，DISTANCE 需左右列，X/Y 仅 Point 且不传 mode。
- PLANAR 使用来源单位，SPHEROID 使用 WGS84 椭球；不自动投影或把单位写入 Schema，业务说明中记录单位。
- 各项读取原表 Geometry，不能引用同节点刚追加的量测字段。

配置定位（只列关键语义，完整字段读取实时契约）：

- `measurements`：有序量测项列表，必须包含 1 至 32 项且不能含 null；输出字段按数组顺序追加。AREA、LENGTH、PERIMETER、DISTANCE 需要 mode，X/Y 不使用 mode；任何所需 Geometry 为 NULL 时仅对应结果为 NULL。

## 逻辑表 Map 与字段

保留输入 Map，追加命名结果表；outputTableName 必须与输入及其他结果表不同。 按顺序追加 nullable DOUBLE 列，保留原行列；类型不适用或 NULL 的实际值行为由所选量测定义。流模式继承来源有界性；事件时间与 Watermark 的实际传播以预校验 outputTables 为准。

## 最小配置示例

前提：上游逻辑表 features 含 id:LONG 和 geom:GEOMETRY(POLYGON, EPSG:3857, XY)。

以下仅为节点 `configuration`；资源 UUID、字段与单位应换成已确认的真实元数据。稳定的操作/写入 ID 由当前任务生成。

```json
{
  "sourceTableName": "features",
  "outputTableName": "measured_features",
  "measurements": [
    {
      "kind": "AREA",
      "geometryColumnName": "geom",
      "mode": "PLANAR",
      "outputColumnName": "area"
    }
  ]
}
```

## 预校验检查与修正

将节点放入完整画布，按 Canvas 指南调用 Engine 预校验，核对输入/输出表及字段；此处没有真实数据测试步骤。

| 诊断或检查点 | 处理方式 |
| --- | --- |
| `SPATIAL_MEASURE_CRS_MISMATCH` | 使距离两侧 CRS/维度与模式一致。 |
| `SPATIAL_MEASURE_KIND_UNSUPPORTED` | 选择适合 Point/线/面的量测。 |

ERROR 修正后重新预校验最终定义；WARNING 记录影响，不把结构通过表述为数据值或输出结果通过。
