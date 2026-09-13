# SPATIAL_BIN_AGGREGATE · 空间格网聚合

[节点索引](index.md) · [Canvas 共用规则](../task-canvas.md)

## 用途与选择

有界批处理空间格网聚合配置；把 XY Point 分配到投影方格、投影六边形或 WGS84 H3 单元，再按格网、可选时间窗口和可选分组输出 1 至 32 项统计。结果为不带事件时间和 Watermark 的新有界表；该能力是点聚合，不是带邻域半径的密度计算。

## 模式与连线

- 模式：BATCH；类别：PROCESSOR。
- 至少一条入边，允许零条或多条出边；从上游 Map 按逻辑表名取表，不按边序号取表。
- 输入必须为 BOUNDED。

## 关键配置与行为

- SQUARE 的 binSize 为边长；HEXAGON_FLAT_TO_FLAT 表示六边形对边距离，LEGACY_SIDE_LENGTH 为旧边长。切换语义不能沿用同一数值而声称等价。
- 方格/六边形需要可换算线性 CRS；H3 使用 EPSG:4326 和显式 h3.mode（RESOLUTION 或 APPROXIMATE_SIZE），后者才将 binSize 解释为期望平均对边距离，不能直接把 binSize 当分辨率。
- 统计 1–32 项且至少一个 COUNT；COUNT 不填 sourceColumnName。includeEmptyBins 可能扩张结果，H3 必须 false。groupSummary/temporalSlicing 改变粒度；这是点聚合，不是核密度。
- 未配置 planarGrid 时使用旧原点 (0,0)、数据索引包络和旧格网 ID；需要固定原点/范围时显式设计 planarGrid，不能假设系统会自动选择业务范围。

配置定位（只列关键语义，完整字段读取实时契约）：

- `binShape`：必填的格网形状：SQUARE 和 HEXAGON 构造来源投影 CRS 下的平面 Polygon；H3 使用原生球面单元并输出 EPSG:4326 XY MultiPolygon。
- `binSize`：平面格网必填的有限正数大小；方格始终表示边长，六边形含义由 binSizeSemantics 决定。H3 APPROXIMATE_SIZE 将其视为期望平均对边距离，H3 RESOLUTION 忽略。
- `binSizeUnit`：binSize 的单位；平面格网必须能换算为来源投影 CRS 单位，H3 APPROXIMATE_SIZE 必须是明确线性单位。H3 RESOLUTION 忽略。
- `includeEmptyBins`：是否补齐平面范围内没有参与点的格网；true 可能显著扩展结果，显式范围最多生成 100 万候选单元。H3 只支持 false；有时间切片时只组合实际出现的有效窗口，不凭空生成时间范围。

## 逻辑表 Map 与字段

保留输入 Map，追加命名结果表；outputTableName 必须与输入及其他结果表不同。 新表含格网 ID、统计和可选分组/时间窗字段；平面格网为 XY Polygon，H3 为 EPSG:4326 XY MultiPolygon，不带事件时间/Watermark。

## 最小配置示例

前提：上游逻辑表 features 含 id:LONG 和 geom:GEOMETRY(POINT, EPSG:3857, XY)，字段名称与输出名不冲突。 输入为 BOUNDED。

以下仅为节点 `configuration`；资源 UUID、字段与单位应换成已确认的真实元数据。稳定的操作/写入 ID 由当前任务生成。

```json
{
  "sourceTableName": "features",
  "pointGeometryColumnName": "geom",
  "binShape": "SQUARE",
  "binSize": 1000.0,
  "binSizeUnit": "METERS",
  "includeEmptyBins": false,
  "statistics": [
    {
      "statisticId": "11111111-1111-4111-8111-111111111111",
      "kind": "COUNT",
      "sourceColumnName": null,
      "outputColumnName": "point_count"
    }
  ],
  "outputTableName": "grid_counts",
  "binIdColumnName": "bin_id",
  "binGeometryColumnName": "bin_geom",
  "binSizeSemantics": "HEXAGON_FLAT_TO_FLAT"
}
```

## 预校验检查与修正

将节点放入完整画布，按 Canvas 指南调用 Engine 预校验，核对输入/输出表及字段；此处没有真实数据测试步骤。

| 诊断或检查点 | 处理方式 |
| --- | --- |
| `SPATIAL_BIN_PROJECTED_CRS_REQUIRED` | 方格/六边形使用适当投影，不能把经纬度当米。 |
| `SPATIAL_EMPTY_BINS_MAY_EXPAND_RESULT` | 解释空格范围和容量警告。 |

ERROR 修正后重新预校验最终定义；WARNING 记录影响，不把结构通过表述为数据值或输出结果通过。
