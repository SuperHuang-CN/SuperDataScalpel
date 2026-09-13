# SPATIAL_NEAREST · 最近要素

[节点索引](index.md) · [Canvas 共用规则](../task-canvas.md)

## 用途与选择

有界批处理最近要素分析配置；为每条来源记录查找另一张表中最近的 1 至 100 条候选记录，输出显式投影字段、距离和可选排名。Canvas 4.30 的精确距离模式还可输出独立连接线表；本节点不执行路网或交通时间分析。

## 模式与连线

- 模式：BATCH；类别：PROCESSOR。
- 至少一条入边，允许零条或多条出边；从上游 Map 按逻辑表名取表，不按边序号取表。
- 输入必须为 BOUNDED。

## 关键配置与行为

- nearestCount=1–100；maximumDistance 与单位成对配置，省略则不限制。includeUnmatched 控制未找到候选的来源行。
- 显式 EXACT_DISTANCE 恢复同距候选并按实际距离排名，需来源 ID；LEGACY_KNN 为旧候选截断行为，不能当作等价。
- 可选 connectionLines 输出独立连线表；不是路网、驾驶时间或路径规划。未核实的唯一性保留警告。

配置定位（只列关键语义，完整字段读取实时契约）：

- `candidateIdColumnName`：必填且不能为 Geometry 的候选身份字段名，用于同距候选的稳定排序。EXACT_DISTANCE 会在惰性执行时要求每条候选值非 NULL 且唯一；LEGACY_KNN 仅假定其唯一，无法在同距且同 ID 时保证顺序。
- `distanceMethod`：必填的距离方式：PLANAR 在来源 CRS 的 XY 平面计算最近位置；GEODESIC 使用 EPSG:4326 测地距离。EXACT_DISTANCE 的测地模式当前只支持 Point，不以线面质心替代最近位置。
- `nearestCount`：每条来源记录最多返回的候选数，必须在 1 至 100 之间；候选按实际距离升序、candidateIdColumnName 升序排名。
- `includeUnmatched`：是否保留未匹配的来源记录；true 时每条未匹配来源保留一行，候选侧投影、距离和排名为 NULL。NULL/Empty 来源 Geometry 不参与搜索，但可按此开关保留。

## 逻辑表 Map 与字段

保留输入 Map，追加命名结果表；outputTableName 必须与输入及其他结果表不同。 主表为显式投影+距离+可选排名；可选连接线再追加一表。

## 最小配置示例

前提：features 与 stations 为不同 BOUNDED 表，均为 EPSG:3857 XY 几何；id 和 station_id 有非空唯一性依据。

以下仅为节点 `configuration`；资源 UUID、字段与单位应换成已确认的真实元数据。稳定的操作/写入 ID 由当前任务生成。

```json
{
  "sourceTableName": "features",
  "sourceGeometryColumnName": "geom",
  "candidateTableName": "stations",
  "candidateGeometryColumnName": "station_geom",
  "candidateIdColumnName": "station_id",
  "distanceMethod": "PLANAR",
  "nearestCount": 1,
  "includeUnmatched": false,
  "outputTableName": "nearest_stations",
  "distanceColumnName": "distance_m",
  "distanceOutputUnit": "METERS",
  "rankColumnName": "nearest_rank",
  "outputColumns": [
    {
      "sourceSide": "LEFT",
      "sourceColumnName": "id",
      "outputColumnName": "id",
      "included": true
    },
    {
      "sourceSide": "RIGHT",
      "sourceColumnName": "station_id",
      "outputColumnName": "station_id",
      "included": true
    }
  ],
  "matching": {
    "semantics": "EXACT_DISTANCE",
    "sourceIdColumnName": "id"
  }
}
```

## 预校验检查与修正

将节点放入完整画布，按 Canvas 指南调用 Engine 预校验，核对输入/输出表及字段；此处没有真实数据测试步骤。

| 诊断或检查点 | 处理方式 |
| --- | --- |
| `CANDIDATE_ID_UNIQUENESS_NOT_VERIFIED` | 说明身份唯一性未被元数据证明，不用取样宣称验证。 |
| `INVALID_SPATIAL_DISTANCE_CONFIGURATION` | 统一 CRS、距离方法和单位。 |

ERROR 修正后重新预校验最终定义；WARNING 记录影响，不把结构通过表述为数据值或输出结果通过。
