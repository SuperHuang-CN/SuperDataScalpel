# WINDOW · 批处理窗口函数

[节点索引](index.md) · [Canvas 共用规则](../task-canvas.md)

## 用途与选择

批处理窗口计算配置；只接受 BOUNDED 来源，保留来源表的全部行与字段，并按 functions 顺序追加分析字段。所有分区、排序和函数表达式都只读取进入节点时的原始来源字段，不能引用同节点刚生成的窗口字段。

## 模式与连线

- 模式：BATCH；类别：PROCESSOR。
- 至少一条入边，允许零条或多条出边；从上游 Map 按逻辑表名取表，不按边序号取表。
- 输入必须为 BOUNDED。

## 关键配置与行为

- 用于排名、Lag/Lead 或 ROWS frame 计算，不是事件时间窗口或实时状态节点。
- 排名/偏移需要排序；聚合窗口需按所选函数配置受支持 frame。相同排序值的行号不保证稳定，先明确并列规则。

配置定位（只列关键语义，完整字段读取实时契约）：

- `partitionByColumns`：窗口分区字段列表；允许空数组，表示把全部输入记录作为一个分区。字段必须存在、互不重复且不能是 Geometry。
- `orderBy`：窗口内部的多字段排序规则；至少一项，字段不能重复或使用 Geometry。数组顺序决定比较优先级，完全并列时不添加隐式稳定终结字段。该排序只影响窗口计算，不保证结果表或最终 Sink 的物理行顺序。
- `functions`：按数组顺序追加到全部来源字段之后的窗口函数；必须包含 1 至 100 项。输出字段名不能与来源字段或其他窗口输出重复，每一项只能读取原始来源字段。

## 逻辑表 Map 与字段

保留输入 Map，追加命名结果表；outputTableName 必须与输入及其他结果表不同。 保留原列，按函数顺序追加结果列；不要把 ROW_NUMBER 和 RANK 的并列语义混为一谈。

## 最小配置示例

前提：orders 为 BOUNDED，含 customer_id、id，分区内 id 排序明确。

以下仅为节点 `configuration`；资源 UUID、字段与单位应换成已确认的真实元数据。稳定的操作/写入 ID 由当前任务生成。

```json
{
  "sourceTableName": "orders",
  "outputTableName": "ranked_orders",
  "partitionByColumns": [
    "customer_id"
  ],
  "orderBy": [
    {
      "columnName": "id",
      "direction": "ASC",
      "nullOrdering": "LAST"
    }
  ],
  "functions": [
    {
      "kind": "ROW_NUMBER",
      "outputColumnName": "row_no"
    }
  ]
}
```

## 预校验检查与修正

将节点放入完整画布，按 Canvas 指南调用 Engine 预校验，核对输入/输出表及字段；此处没有真实数据测试步骤。

| 诊断或检查点 | 处理方式 |
| --- | --- |
| `COLUMN_NOT_FOUND` | 核对分区/排序/函数来源字段。 |
| `WINDOW_OUTPUT_COLUMN_CONFLICT` | 窗口输出不能覆盖来源字段。 |

ERROR 修正后重新预校验最终定义；WARNING 记录影响，不把结构通过表述为数据值或输出结果通过。
