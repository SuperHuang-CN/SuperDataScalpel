# SQL_TRANSFORM · Spark SQL 转换

[节点索引](index.md) · [Canvas 共用规则](../task-canvas.md)

## 用途与选择

受控 Spark SQL 转换配置；在隔离的子 Session 中把当前 Canvas 表注册为临时视图，解析一条只读 SELECT 或 WITH…SELECT，并把惰性结果计划作为新的有界逻辑表返回。不能访问 Catalog 表、外部关系或表值函数。

## 模式与连线

- 模式：BATCH；类别：PROCESSOR。
- 至少一条入边，允许零条或多条出边；从上游 Map 按逻辑表名取表，不按边序号取表。
- 输入必须为 BOUNDED。

## 关键配置与行为

- 只读单条 SELECT 或 WITH…SELECT；输入 Map 注册为隔离临时视图，不访问 Catalog 外表、外部关系或表值函数。
- 用于结构化处理器难表达的批逻辑；结果别名唯一，输出类型必须可映射为平台类型。

配置定位（只列关键语义，完整字段读取实时契约）：

- `sql`：1～100000 字符的单条 Spark SQL SELECT 或 WITH…SELECT。关系只能是当前上游逻辑表或本查询 CTE；禁止命令、写入、多语句、Catalog 限定关系和表值函数。含点号等特殊字符的逻辑表名须按 Spark 标识符规则用反引号引用。查询必须至少产生一个字段，且字段名不可重复、类型必须能映射为平台类型。

## 逻辑表 Map 与字段

保留输入 Map，追加命名结果表；outputTableName 必须与输入及其他结果表不同。 输出为 Spark Analyzer 推导的 BOUNDED Schema；不能手工附加 outputColumns 覆盖推导结果。

## 最小配置示例

前提：上游 BOUNDED orders 含 customer_id；名称是 Canvas 逻辑表名，不是外部库表位置。

以下仅为节点 `configuration`；资源 UUID、字段与单位应换成已确认的真实元数据。稳定的操作/写入 ID 由当前任务生成。

```json
{
  "outputTableName": "order_counts",
  "sql": "SELECT customer_id, COUNT(*) AS order_count FROM orders GROUP BY customer_id"
}
```

## 预校验检查与修正

将节点放入完整画布，按 Canvas 指南调用 Engine 预校验，核对输入/输出表及字段；此处没有真实数据测试步骤。

| 诊断或检查点 | 处理方式 |
| --- | --- |
| `SQL_TRANSFORM_STATEMENT_NOT_ALLOWED` | 使用单条受控查询。 |
| `SQL_TRANSFORM_ANALYSIS_ERROR` | 检查上游视图/字段名与 Spark SQL 语法。 |
| `SQL_TRANSFORM_UNSUPPORTED_OUTPUT_TYPE` | 明确报告类型限制并审查改写口径。 |

ERROR 修正后重新预校验最终定义；WARNING 记录影响，不把结构通过表述为数据值或输出结果通过。
