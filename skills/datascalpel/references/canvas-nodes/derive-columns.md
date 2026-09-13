# DERIVE_COLUMNS · 派生字段

[节点索引](index.md) · [Canvas 共用规则](../task-canvas.md)

## 用途与选择

支持 BATCH 和 STREAMING 的派生字段配置；每张已选表执行 globalDerivations 加该表局部 derivations，并通过一次最终投影覆盖原字段或追加新字段。所有表达式只读取该表进入节点时的原始 Schema，不能引用本节点刚派生的字段；任一表不兼容会使整个节点无效。

## 模式与连线

- 模式：BATCH / STREAMING；类别：PROCESSOR。
- 至少一条入边，允许零条或多条出边；从上游 Map 按逻辑表名取表，不按边序号取表。
- 输入有界性及批流差异见下文。

## 关键配置与行为

- globalDerivations 应用于所有已选表，再结合局部 derivations；各表必须都能解析共享表达式。
- 同一节点表达式只读进入节点时的列；派生字段依赖链要拆节点。目标原列可覆盖，新列追加；共享与局部目标不能冲突。
- 表达式用 kind= COLUMN/LITERAL/BINARY/FUNCTION/CASE_WHEN/RUNTIME_VALUE；禁止在流任务改写事件时间列。

配置定位（只列关键语义，完整字段读取实时契约）：

- `globalDerivations`：自动应用到 operations 中每一张来源表的有序派生规则；NULL 规范化为空数组。每条规则必须对所有已选表有效，并且目标字段不能与任一表自己的 derivations 冲突。
- `operations`：独立处理不同来源表的操作数组，至少一项；operationId 和 sourceTableName 在节点内都必须唯一。各项只能读取进入本节点时已有的上游表，不能在同一节点内继续读取其他操作刚生成的结果。每表有效规则总数最多 100。

## 逻辑表 Map 与字段

按 output 策略替换源表或追加结果，未处理表透传；多操作读取同一份节点入口 Map。 类型由表达式分析推导；既有列在原位置覆盖，新列按声明顺序追加。

## 最小配置示例

前提：orders 含 amount，amount_copy 为新输出字段。

以下仅为节点 `configuration`；资源 UUID、字段与单位应换成已确认的真实元数据。稳定的操作/写入 ID 由当前任务生成。

```json
{
  "globalDerivations": [],
  "operations": [
    {
      "operationId": "11111111-1111-4111-8111-111111111111",
      "sourceTableName": "orders",
      "output": {
        "mode": "CREATE_NEW_TABLE",
        "outputTableName": "processed"
      },
      "derivations": [
        {
          "targetColumnName": "amount_copy",
          "expression": {
            "kind": "COLUMN",
            "columnName": "amount"
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
| `GLOBAL_DERIVATION_TARGET_CONFLICT` | 移除共享与局部重复目标。 |
| `INVALID_DERIVATION_EXPRESSION` | 按原始上游 Schema 修正类型和表达式。 |
| `STREAM_EVENT_TIME_COLUMN_IMMUTABLE` | 保留原事件时间字段，重新审查新时间需求。 |

ERROR 修正后重新预校验最终定义；WARNING 记录影响，不把结构通过表述为数据值或输出结果通过。
