# DEDUPLICATE · 去重

[节点索引](index.md) · [Canvas 共用规则](../task-canvas.md)

## 用途与选择

仅支持 BATCH 的有界表去重配置；对一个或多个不同上游表分别按全行或业务键识别重复记录，并按 ANY、FIRST 或 LAST 选择保留行。输出继承字段和来源，固定为 BOUNDED 并清除事件时间与 Watermark。

## 模式与连线

- 模式：BATCH；类别：PROCESSOR。
- 至少一条入边，允许零条或多条出边；从上游 Map 按逻辑表名取表，不按边序号取表。
- 输入必须为 BOUNDED。

## 关键配置与行为

- ANY 不保证具体保留行；FIRST/LAST 根据显式 orderBy 选择，不等于按数据库自然顺序。
- 并列排序值仍可能不确定，补充业务认可的稳定排序字段；不通过取样来宣称键唯一。

配置定位（只列关键语义，完整字段读取实时契约）：

- `operations`：独立处理不同来源表的操作数组，至少一项；operationId 和 sourceTableName 在节点内都必须唯一。各项只能读取进入本节点时已有的上游表，不能在同一节点内继续读取其他操作刚生成的结果。

## 逻辑表 Map 与字段

按 output 策略替换源表或追加结果，未处理表透传；多操作读取同一份节点入口 Map。 结果保留来源完整字段结构，每个 key 组合按策略留一行。

## 最小配置示例

前提：orders 是 BOUNDED，含 id、updated_at、seq；排序组合能明确选择同键保留行。

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
      "keyColumns": [
        "id"
      ],
      "keepStrategy": "FIRST",
      "orderBy": [
        {
          "columnName": "updated_at",
          "direction": "DESC",
          "nullOrdering": "LAST"
        },
        {
          "columnName": "seq",
          "direction": "DESC",
          "nullOrdering": "LAST"
        }
      ]
    }
  ]
}
```

## 预校验检查与修正

将节点放入完整画布，按 Canvas 指南调用 Engine 预校验，核对输入/输出表及字段；此处没有真实数据测试步骤。

| 诊断或检查点 | 处理方式 |
| --- | --- |
| `COLUMN_NOT_FOUND` | 检查去重键与排序字段。 |
| `REQUIRED_CONFIGURATION` | 补齐策略要求的键和排序配置。 |

ERROR 修正后重新预校验最终定义；WARNING 记录影响，不把结构通过表述为数据值或输出结果通过。
