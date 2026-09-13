# JDBC_OUTPUT · JDBC 输出

[节点索引](index.md) · [Canvas 共用规则](../task-canvas.md)

## 用途与选择

JDBC 输出配置；向同一个已启用且具有 DISTRIBUTION 用途的 JDBC 数据源声明一项或多项独立物理表写入。输出不产生下游表；多项写入分别执行且没有跨目标事务，前项可能已成功而后项失败。

## 模式与连线

- 模式：BATCH / STREAMING；类别：OUTPUT。
- 恰好一条入边，无出边；一条入边可包含多张逻辑表。
- 输入有界性及批流差异见下文。

## 关键配置与行为

- writes 共享目标数据源，每项独立目标表；不自动创建物理表，不能写视图或不支持对象。
- UPSERT 才配置 upsertKeyColumns，目标键必须有真实约束并映射；MySQL 多唯一键冲突需明确。实时输出不使用 OVERWRITE。
- 每项单独执行，无跨目标事务；目标生成列/自增列的映射限制以预校验为准。

配置定位（只列关键语义，完整字段读取实时契约）：

- `writes`：按数组顺序准备和执行的独立目标表写入，至少一项；writeId 在节点内必须唯一。实时任务最多 32 项，每项作为独立流式查询按至少一次语义写入。任一批处理写入失败时，已完成项不回滚，后续项跳过。

## 逻辑表 Map 与字段

无下游 Map；按每项 source→target 映射和目标类型写入，不隐式按顺序配列。

## 最小配置示例

前提：目标 UUID 为已启用 DISTRIBUTION JDBC 数据源，现有可写表 public.order_result 与 orders.id:LONG 兼容，其余必填字段有默认/生成值。

以下仅为节点 `configuration`；资源 UUID、字段与单位应换成已确认的真实元数据。稳定的操作/写入 ID 由当前任务生成。

```json
{
  "dataSourceId": "22222222-2222-4222-8222-222222222222",
  "writes": [
    {
      "writeId": "11111111-1111-4111-8111-111111111111",
      "sourceTableName": "orders",
      "targetTableName": "public.order_result",
      "writeMode": "APPEND",
      "columnMappings": [
        {
          "sourceColumnName": "id",
          "targetColumnName": "id"
        }
      ],
      "upsertKeyColumns": []
    }
  ]
}
```

## 预校验检查与修正

将节点放入完整画布，按 Canvas 指南调用 Engine 预校验，核对输入/输出表及字段；此处没有真实数据测试步骤。

| 诊断或检查点 | 处理方式 |
| --- | --- |
| `UPSERT_KEY_NOT_UNIQUE_CONSTRAINT` | 取得真实唯一键约束或报告依赖。 |
| `TARGET_TABLE_NOT_WRITABLE` | 换用现有合法物理目标。 |
| `MYSQL_UPSERT_MULTIPLE_UNIQUE_KEYS` | 说明唯一键歧义，重新审查目标设计。 |

ERROR 修正后重新预校验最终定义；WARNING 记录影响，不把结构通过表述为数据值或输出结果通过。
