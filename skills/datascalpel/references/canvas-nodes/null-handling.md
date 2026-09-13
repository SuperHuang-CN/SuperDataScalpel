# NULL_HANDLING · 空值处理

[节点索引](index.md) · [Canvas 共用规则](../task-canvas.md)

## 用途与选择

支持 BATCH 和 STREAMING 的空值处理节点配置；对一个或多个不同上游逻辑表分别执行有序的 SQL NULL 删行或常量填充规则。每项操作可替换来源表或保留来源并创建新表，任一操作校验失败会使整个节点无效。空字符串、NaN 和零值不视为 NULL。

## 模式与连线

- 模式：BATCH / STREAMING；类别：PROCESSOR。
- 至少一条入边，允许零条或多条出边；从上游 Map 按逻辑表名取表，不按边序号取表。
- 输入有界性及批流差异见下文。

## 关键配置与行为

- 规则通过 kind 判别：FILL_LITERAL 使用类型化常量；DROP_ROW 使用 columnNames 与 ANY_NULL/ALL_NULL。
- 填充值需适配原类型，空字符串不等于 NULL；流事件时间字段的修改限制由 Compiler 检查。

配置定位（只列关键语义，完整字段读取实时契约）：

- `operations`：独立处理不同来源表的操作数组，至少一项；operationId 和 sourceTableName 在节点内都必须唯一。各项只能读取进入本节点时已有的上游表，不能在同一节点内继续读取其他操作刚生成的结果。

## 逻辑表 Map 与字段

按 output 策略替换源表或追加结果，未处理表透传；多操作读取同一份节点入口 Map。 FILL_LITERAL 原位填值；DROP_ROW 改变行集合。未处理字段保留，nullable 变化以分析结果为准。

## 最小配置示例

前提：orders.status 是允许空的 STRING，填充值长度适配。

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
      "rules": [
        {
          "kind": "FILL_LITERAL",
          "columnName": "status",
          "value": {
            "dataType": "STRING",
            "value": "UNKNOWN"
          }
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
| `COLUMN_NOT_FOUND` | 核对规则引用列。 |
| `REQUIRED_CONFIGURATION` | 使用完整的活动规则分支，不能混合不同 kind 字段。 |

ERROR 修正后重新预校验最终定义；WARNING 记录影响，不把结构通过表述为数据值或输出结果通过。
