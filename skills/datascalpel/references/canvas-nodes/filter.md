# FILTER · 行筛选

[节点索引](index.md) · [Canvas 共用规则](../task-canvas.md)

## 用途与选择

支持 BATCH 和 STREAMING 的行筛选配置；对一个或多个不同上游表分别使用结构化条件树或受控 Spark SQL 布尔谓词过滤记录。输出完整继承来源字段、Origin、有界性、事件时间和 Watermark，任一操作校验失败会使整个节点无效。

## 模式与连线

- 模式：BATCH / STREAMING；类别：PROCESSOR。
- 至少一条入边，允许零条或多条出边；从上游 Map 按逻辑表名取表，不按边序号取表。
- 输入有界性及批流差异见下文。

## 关键配置与行为

- SQL_EXPRESSION 只写受控 Spark SQL 布尔谓词，不写 WHERE/SELECT、分号、多语句或注释。
- STRUCTURED 使用 kind=GROUP/PREDICATE 条件树和类型化 literal；仅活动模式的条件生效，NULL 比较使用 IS_NULL/IS_NOT_NULL。

配置定位（只列关键语义，完整字段读取实时契约）：

- `operations`：独立处理不同来源表的操作数组，至少一项；operationId 和 sourceTableName 在节点内都必须唯一。各项只能读取进入本节点时已有的上游表，不能在同一节点内继续读取其他操作刚生成的结果。

## 逻辑表 Map 与字段

按 output 策略替换源表或追加结果，未处理表透传；多操作读取同一份节点入口 Map。 不改变列与顺序，保留来源有界性、事件时间和 Watermark；只设计未来行过滤。

## 最小配置示例

前提：orders 含数值字段 amount。

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
      "mode": "SQL_EXPRESSION",
      "sqlExpression": "amount > 0"
    }
  ]
}
```

## 预校验检查与修正

将节点放入完整画布，按 Canvas 指南调用 Engine 预校验，核对输入/输出表及字段；此处没有真实数据测试步骤。

| 诊断或检查点 | 处理方式 |
| --- | --- |
| `INVALID_FILTER_SQL_EXPRESSION` | 修正布尔表达式、字段引用或不支持函数。 |
| `TABLE_NOT_FOUND` | 使用真实上游逻辑表名。 |

ERROR 修正后重新预校验最终定义；WARNING 记录影响，不把结构通过表述为数据值或输出结果通过。
