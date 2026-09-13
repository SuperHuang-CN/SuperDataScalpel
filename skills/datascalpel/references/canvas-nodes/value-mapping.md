# VALUE_MAPPING · 值映射

[节点索引](index.md) · [Canvas 共用规则](../task-canvas.md)

## 用途与选择

支持 BATCH 和 STREAMING 的小型静态精确值映射配置；对一个或多个不同上游逻辑表分别执行字段内联映射，保持字段类型、名称和顺序。它不支持范围、正则、模糊或远程字典匹配，任一操作校验失败会使整个节点无效。

## 模式与连线

- 模式：BATCH / STREAMING；类别：PROCESSOR。
- 至少一条入边，允许零条或多条出边；从上游 Map 按逻辑表名取表，不按边序号取表。
- 输入有界性及批流差异见下文。

## 关键配置与行为

- 匹配源值与目标值均为 CanvasLiteral；同一规则源值不重复。
- 未匹配策略 KEEP/SET_NULL/SET_LITERAL/ERROR；SET_LITERAL 才填写 unmatchedValue，不能默默给未知码值新含义。

配置定位（只列关键语义，完整字段读取实时契约）：

- `operations`：独立处理不同来源表的操作数组，至少一项；operationId 和 sourceTableName 在节点内都必须唯一。各项只能读取进入本节点时已有的上游表，不能在同一节点内继续读取其他操作刚生成的结果。

## 逻辑表 Map 与字段

按 output 策略替换源表或追加结果，未处理表透传；多操作读取同一份节点入口 Map。 原字段位置替换值，结构和类型需兼容；可空性可能因 SET_NULL 改变。

## 最小配置示例

前提：orders.status 是 STRING，能容纳 NEW；业务确认 N 的含义。

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
          "columnName": "status",
          "entries": [
            {
              "sourceValue": {
                "dataType": "STRING",
                "value": "N"
              },
              "targetValue": {
                "dataType": "STRING",
                "value": "NEW"
              }
            }
          ],
          "unmatchedStrategy": "KEEP"
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
| `COLUMN_NOT_FOUND` | 核对映射字段。 |
| `REQUIRED_CONFIGURATION` | 补齐 entries 和未匹配策略所需常量。 |

ERROR 修正后重新预校验最终定义；WARNING 记录影响，不把结构通过表述为数据值或输出结果通过。
