# SPATIAL_TRANSFORM · 坐标转换

[节点索引](index.md) · [Canvas 共用规则](../task-canvas.md)

## 用途与选择

批处理坐标转换配置；在新逻辑表中以 Sedona ST_Transform 原位替换一个 Geometry 字段的坐标和 CRS 元数据，保留其他字段、GeometryKind、XY 维度和可空性。来源表仍保留。

## 模式与连线

- 模式：BATCH；类别：PROCESSOR。
- 至少一条入边，允许零条或多条出边；从上游 Map 按逻辑表名取表，不按边序号取表。
- 输入必须为 BOUNDED。

## 关键配置与行为

- 来源和目标 CRS 必须可解析，targetCrs 只描述坐标参考系，不重新声明几何类别。
- 当前仅批处理，流式图不能插入此节点；目标与源 CRS 相同会产生无效果诊断。

配置定位（只列关键语义，完整字段读取实时契约）：

- `targetCrs`：目标坐标参考系；当前只支持 authority=EPSG 且 code>0。与来源 CRS 相同时坐标不变并产生无效果警告。

## 逻辑表 Map 与字段

保留输入 Map，追加命名结果表；outputTableName 必须与输入及其他结果表不同。 在新表中原位替换 geom 坐标与 CRS，其他字段、几何类别、维度和可空性保留。

## 最小配置示例

前提：features 含 geom:GEOMETRY(POINT,EPSG:4326,XY)，来源坐标系已证实。

以下仅为节点 `configuration`；资源 UUID、字段与单位应换成已确认的真实元数据。稳定的操作/写入 ID 由当前任务生成。

```json
{
  "sourceTableName": "features",
  "outputTableName": "projected",
  "geometryColumnName": "geom",
  "targetCrs": {
    "authority": "EPSG",
    "code": 3857
  }
}
```

## 预校验检查与修正

将节点放入完整画布，按 Canvas 指南调用 Engine 预校验，核对输入/输出表及字段；此处没有真实数据测试步骤。

| 诊断或检查点 | 处理方式 |
| --- | --- |
| `SPATIAL_TRANSFORM_HAS_NO_EFFECT` | 目标与来源相同时移除无意义转换。 |
| `UNSUPPORTED_GEOMETRY_DIMENSION` | 使用支持的 XY 结构，不能静默降维。 |

ERROR 修正后重新预校验最终定义；WARNING 记录影响，不把结构通过表述为数据值或输出结果通过。
