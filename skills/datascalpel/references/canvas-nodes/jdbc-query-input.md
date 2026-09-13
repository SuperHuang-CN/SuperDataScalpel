# JDBC_QUERY_INPUT · JDBC 查询输入

[节点索引](index.md) · [Canvas 共用规则](../task-canvas.md)

## 用途与选择

JDBC 只读查询输入配置；保存一条已显式分析的 PostgreSQL、HighGo、MySQL、openGauss 和人大金仓 SELECT，以及对应规范化 SQL 摘要和输出 Schema 快照。Compiler 不访问数据库，运行时按保存的 SQL 读取一张 BOUNDED 表。

## 模式与连线

- 模式：BATCH / STREAMING；类别：INPUT。
- 无入边，至少一条出边；不读取上游 Map。
- 输入有界性见下文。

## 关键配置与行为

- 先发现 POST /api/v1/data-sources/{id}/actions/inspect-query 并读取契约；该调用用于只读结构分析，不调用 table-preview 或取样验证。 结构分析在来源连接上读取查询元数据，驱动需要时可能执行最多一行 fallback，但不返回数据行；不能将这一步表述为任务结果测试。
- 当前支持 PostgreSQL、HighGo、MySQL、openGauss 和人大金仓的受控单条 SELECT / WITH…SELECT；保存 sql、analyzedSqlSha256 与 outputColumns 是本节点特例。SQL 修改后必须重新分析，Compiler 不访问数据库替你补结构。
- 实时中仍为静态 BOUNDED 查询，不能把它用作持续增量或任意外部 SQL 执行器。
- 本手册按当前 Operator 的数据库能力分支校对；若部署环境仍是旧版本或契约说明与实际能力冲突，先报告差异并获取完整契约，不强行提交不支持的配置。

配置定位（只列关键语义，完整字段读取实时契约）：

- `sql`：经过只读语法校验的单条 SELECT 或 WITH ... SELECT，最长 100000 个字符；不支持模板变量、运行参数、多语句或写操作。编辑后必须重新分析并更新摘要与字段快照。
- `analyzedSqlSha256`：对去除可选终止分号并 trim 后的规范化 SQL 计算的 64 位小写 SHA-256；必须与 sql 当前内容匹配，否则视为 Schema 已过期。SQL 正文本身仍保存在 sql 字段。
- `outputColumns`：最近一次查询分析得到的输出列 Schema，至少一项并按 JDBC 结果顺序排列；字段名必须精确唯一，当前不支持 Geometry。运行时不重新比较实际结果与该快照，真实解析失败由 Spark/JDBC 报告。

## 逻辑表 Map 与字段

以 outputTableName 输出一张 BOUNDED 表；字段顺序、别名、类型和可空性使用分析响应。

## 最小配置示例

前提：示例仅展示 PostgreSQL SELECT 常量与其 SQL 文本摘要对应关系；真实使用必须先通过 inspect-query 获得已分析 SQL、摘要及完整输出列，以响应替换示例值，不能本地猜 Schema。

以下仅为节点 `configuration`；资源 UUID、字段与单位应换成已确认的真实元数据。稳定的操作/写入 ID 由当前任务生成。

```json
{
  "dataSourceId": "11111111-1111-4111-8111-111111111111",
  "sql": "SELECT 1 AS id",
  "outputTableName": "query_rows",
  "analyzedSqlSha256": "611f76bb5b4ae387967515bd6b0b59857bf03416a03866cc7b9549829994370e",
  "outputColumns": [
    {
      "name": "id",
      "fieldType": "INTEGER",
      "nullable": false,
      "autoIncrement": false,
      "generated": false
    }
  ]
}
```

## 预校验检查与修正

将节点放入完整画布，按 Canvas 指南调用 Engine 预校验，核对输入/输出表及字段；此处没有真实数据测试步骤。

| 诊断或检查点 | 处理方式 |
| --- | --- |
| `JDBC_QUERY_SCHEMA_STALE` | 对当前 SQL 重新调用结构分析并整体替换摘要/列。 |
| `JDBC_QUERY_NOT_READ_ONLY` | 改成受支持的单条只读查询。 |
| `JDBC_QUERY_SCHEMA_INVALID` | 报告不支持类型或别名问题，不伪造列。 |

ERROR 修正后重新预校验最终定义；WARNING 记录影响，不把结构通过表述为数据值或输出结果通过。
