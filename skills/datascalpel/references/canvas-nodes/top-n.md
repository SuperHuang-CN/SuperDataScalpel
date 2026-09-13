# TOP_N · 分组前 N 行

[节点索引](index.md) · [Canvas 共用规则](../task-canvas.md)

## 用途与选择

仅支持 BATCH 和 BOUNDED 来源的 Top N 配置；对一个或多个不同上游表分别按显式排序选择全局或各分组前 N 名。排序只决定保留记录，不承诺下游物理行顺序；输出清除事件时间与 Watermark。

## 模式与连线

- 模式：BATCH；类别：PROCESSOR。
- 至少一条入边，允许零条或多条出边；从上游 Map 按逻辑表名取表，不按边序号取表。
- 输入必须为 BOUNDED。

## 关键配置与行为

- EXACT 严格截取 N 行，并列时需要稳定排序；WITH_TIES 会包含边界并列，可能超过 N。
- partitionByColumns=[] 表示全局前 N，limit 必须为受支持正数；不是源读取限额。

配置定位（只列关键语义，完整字段读取实时契约）：

- `operations`：独立处理不同来源表的操作数组，至少一项；operationId 和 sourceTableName 在节点内都必须唯一。各项只能读取进入本节点时已有的上游表，不能在同一节点内继续读取其他操作刚生成的结果。

## 逻辑表 Map 与字段

按 output 策略替换源表或追加结果，未处理表透传；多操作读取同一份节点入口 Map。 保留所有原字段，减少行集合；窗口排序用于选择，不承诺后续 Dataset 全局行顺序。

## 最小配置示例

前提：orders 为 BOUNDED，含 customer_id、amount、id；示例按金额降序并按 id 决胜。

以下仅为节点 `configuration`；资源 UUID、字段与单位应换成已确认的真实元数据。稳定的操作/写入 ID 由当前任务生成。

```json
{
  "operations": [
    {
      "operationId": "11111111-1111-4111-8111-111111111111",
      "sourceTableName": "orders",
      "output": {
        "mode": "CREATE_NEW_TABLE",
        "outputTableName": "processed"
      },
      "partitionByColumns": [
        "customer_id"
      ],
      "orderBy": [
        {
          "columnName": "amount",
          "direction": "DESC",
          "nullOrdering": "LAST"
        },
        {
          "columnName": "id",
          "direction": "ASC",
          "nullOrdering": "LAST"
        }
      ],
      "limit": 3,
      "tieStrategy": "EXACT"
    }
  ]
}
```

## 预校验检查与修正

将节点放入完整画布，按 Canvas 指南调用 Engine 预校验，核对输入/输出表及字段；此处没有真实数据测试步骤。

| 诊断或检查点 | 处理方式 |
| --- | --- |
| `INVALID_TOP_N_LIMIT` | 选择当前契约范围内的正整数。 |
| `INVALID_TOP_N_TIE_STRATEGY` | 明确 EXACT 或 WITH_TIES 的边界并列语义。 |

ERROR 修正后重新预校验最终定义；WARNING 记录影响，不把结构通过表述为数据值或输出结果通过。
