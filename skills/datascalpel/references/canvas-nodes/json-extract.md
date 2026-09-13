# JSON_EXTRACT · JSON 字段提取

[节点索引](index.md) · [Canvas 共用规则](../task-canvas.md)

## 用途与选择

支持 BATCH 和 STREAMING 的 JSON 标量提取配置；对一个或多个不同上游表分别从单个 STRING 字段解析 JSON，并按 Spark VARIANT Path 追加最多 100 个类型化标量字段。它不推断 Schema、不展开数组，也不生成 STRUCT、ARRAY、MAP 或 GEOMETRY。

## 模式与连线

- 模式：BATCH / STREAMING；类别：PROCESSOR。
- 至少一条入边，允许零条或多条出边；从上游 Map 按逻辑表名取表，不按边序号取表。
- 输入有界性及批流差异见下文。

## 关键配置与行为

- jsonPath 使用当前 Spark VARIANT 提取路径语法，不是任意 JSONPath 实现；每个输出明确 targetType。
- ERROR 拒绝不合法值，SET_NULL 允许解析或转换失败返回 NULL；不自动展开数组为多行。

配置定位（只列关键语义，完整字段读取实时契约）：

- `operations`：独立处理不同来源表的操作数组，至少一项；operationId 和 sourceTableName 在节点内都必须唯一。各项只能读取进入本节点时已有的上游表，不能在同一节点内继续读取其他操作刚生成的结果。

## 逻辑表 Map 与字段

按 output 策略替换源表或追加结果，未处理表透传；多操作读取同一份节点入口 Map。 保留来源列并追加提取列；输出名唯一，nullable 和映射类型由分析确认。

## 最小配置示例

前提：orders.payload 是保存 JSON 文本的 STRING；order_id 不与上游列冲突。

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
      "sourceColumnName": "payload",
      "extractions": [
        {
          "jsonPath": "$.order_id",
          "outputColumnName": "order_id",
          "targetType": {
            "type": "LONG"
          }
        }
      ],
      "failureStrategy": "SET_NULL"
    }
  ]
}
```

## 预校验检查与修正

将节点放入完整画布，按 Canvas 指南调用 Engine 预校验，核对输入/输出表及字段；此处没有真实数据测试步骤。

| 诊断或检查点 | 处理方式 |
| --- | --- |
| `COLUMN_NOT_FOUND` | 核对 JSON 文本来源列。 |
| `DUPLICATE_JSON_OUTPUT_COLUMN` | 提取项必须使用不与来源及其他项重名的新列。 |

ERROR 修正后重新预校验最终定义；WARNING 记录影响，不把结构通过表述为数据值或输出结果通过。
