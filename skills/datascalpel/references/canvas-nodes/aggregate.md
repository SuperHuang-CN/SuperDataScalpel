# AGGREGATE · 分组聚合

[节点索引](index.md) · [Canvas 共用规则](../task-canvas.md)

## 用途与选择

批处理分组聚合配置；读取一张 BOUNDED 上游逻辑表，按零到多个字段分组并生成一张新的 BOUNDED 结果表。输出先按 groupByColumns 顺序排列分组字段，再按 aggregations 顺序排列指标字段；结果不继承物理来源、事件时间或 Watermark。

## 模式与连线

- 模式：BATCH；类别：PROCESSOR。
- 至少一条入边，允许零条或多条出边；从上游 Map 按逻辑表名取表，不按边序号取表。
- 输入必须为 BOUNDED。

## 关键配置与行为

- COUNT/SUM/AVG/MIN/MAX；COUNT 全行使用 sourceColumnName=null、distinct=false，不填字符串 *。
- groupByColumns=[] 为全局聚合；明确字段非空计数与全行计数区别，不能用来替代流式窗口聚合。

配置定位（只列关键语义，完整字段读取实时契约）：

- `groupByColumns`：按配置顺序参与分组并出现在结果最前方的来源字段；使用空数组表示把整张输入表作为一个组。字段必须存在、互不重复，且不能是 Geometry。
- `aggregations`：按配置顺序生成指标字段的聚合项；至少一项。各输出字段名必须互不重复，也不能与任何分组字段同名。

## 逻辑表 Map 与字段

保留输入 Map，追加命名结果表；outputTableName 必须与输入及其他结果表不同。 结果只包含分组字段与聚合字段，类型由 Analyzer 推导，行粒度改变。

## 最小配置示例

前提：orders 为 BOUNDED，含 customer_id。

以下仅为节点 `configuration`；资源 UUID、字段与单位应换成已确认的真实元数据。稳定的操作/写入 ID 由当前任务生成。

```json
{
  "sourceTableName": "orders",
  "outputTableName": "order_counts",
  "groupByColumns": [
    "customer_id"
  ],
  "aggregations": [
    {
      "function": "COUNT",
      "sourceColumnName": null,
      "outputColumnName": "order_count",
      "distinct": false
    }
  ]
}
```

## 预校验检查与修正

将节点放入完整画布，按 Canvas 指南调用 Engine 预校验，核对输入/输出表及字段；此处没有真实数据测试步骤。

| 诊断或检查点 | 处理方式 |
| --- | --- |
| `COLUMN_NOT_FOUND` | 核对分组与聚合来源列。 |
| `AGGREGATE_OUTPUT_COLUMN_CONFLICT` | 聚合输出不能与分组字段同名。 |

ERROR 修正后重新预校验最终定义；WARNING 记录影响，不把结构通过表述为数据值或输出结果通过。
