# SPATIAL_JOIN · 空间关联

[节点索引](index.md) · [Canvas 共用规则](../task-canvas.md)

## 用途与选择

批处理两表空间连接配置。当前固定执行 INNER Join，并用 1 至 8 个有方向的 Sedona 空间谓词按 AND 组合。每个匹配的左右行组合都输出，不去重或聚合；结果字段为左表全部字段后接右表全部字段，不支持选择、改名或自动解决同名字段。

## 模式与连线

- 模式：BATCH；类别：PROCESSOR。
- 至少一条入边，允许零条或多条出边；从上游 Map 按逻辑表名取表，不按边序号取表。
- 输入必须为 BOUNDED。

## 关键配置与行为

- 固定 INNER，1–8 项有方向的空间谓词 AND 组合；WITHIN/CONTAINS 不能随意调换左右。
- 输出固定左全部字段再右全部字段；不能添加并不存在的 outputColumns 投影配置。先用 RENAME/SELECT_COLUMNS 解决重名。

配置定位（只列关键语义，完整字段读取实时契约）：

- `joinType`：必填且只能为 INNER。只输出全部空间条件都为 true 的左右记录组合；LEFT、RIGHT、FULL 虽属于共享 JoinType 枚举，但本节点明确拒绝。
- `conditions`：空间条件数组，必须包含 1 至 8 项且不能含不完整项；全部条件固定使用 AND。完全相同的左字段、谓词、右字段三元组不能重复，不支持 OR、NOT、距离、容差或任意表达式。

## 逻辑表 Map 与字段

保留输入 Map，追加命名结果表；outputTableName 必须与输入及其他结果表不同。 输出每个匹配组合，可能扩张行数；不去重或聚合。

## 最小配置示例

前提：两张 BOUNDED 表 CRS 与维度兼容；features 含 geom，areas 含 area_geom，左右所有字段名均不重名。

以下仅为节点 `configuration`；资源 UUID、字段与单位应换成已确认的真实元数据。稳定的操作/写入 ID 由当前任务生成。

```json
{
  "leftTableName": "features",
  "rightTableName": "areas",
  "outputTableName": "located_features",
  "joinType": "INNER",
  "conditions": [
    {
      "leftGeometryColumnName": "geom",
      "predicate": "WITHIN",
      "rightGeometryColumnName": "area_geom"
    }
  ]
}
```

## 预校验检查与修正

将节点放入完整画布，按 Canvas 指南调用 Engine 预校验，核对输入/输出表及字段；此处没有真实数据测试步骤。

| 诊断或检查点 | 处理方式 |
| --- | --- |
| `SPATIAL_JOIN_TYPE_UNSUPPORTED` | 当前不能使用外连接。 |
| `DUPLICATE_COLUMN_NAME` | 在上游先改名，输出没有自动前缀。 |

ERROR 修正后重新预校验最终定义；WARNING 记录影响，不把结构通过表述为数据值或输出结果通过。
