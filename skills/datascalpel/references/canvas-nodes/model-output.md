# MODEL_OUTPUT · 模型输出

[节点索引](index.md) · [Canvas 共用规则](../task-canvas.md)

## 用途与选择

模型输出配置；声明一项或多项到已发布纳管模型的独立写入。输出不产生下游表；多项写入按顺序执行且没有跨目标事务，前项可能已成功而后项失败。

## 模式与连线

- 模式：BATCH / STREAMING；类别：OUTPUT。
- 恰好一条入边，无出边；一条入边可包含多张逻辑表。
- 输入有界性及批流差异见下文。

## 关键配置与行为

- 每项写入独立，最多项数以当前契约为准；同一输出节点一条入边能提供多个逻辑表。
- APPEND/OVERWRITE/UPSERT 需目标能力支持；OVERWRITE 只适用 MANAGED，实时禁止 OVERWRITE。模型 UPSERT 映射完整模型主键。
- 不同目标没有跨目标事务；部分写入成功不能声称整体回滚。空间字段需目标方言明确支持。

配置定位（只列关键语义，完整字段读取实时契约）：

- `writes`：按数组顺序准备和执行的模型写入，至少一项；writeId 在节点内必须唯一。实时任务最多 32 项，每项作为独立流式查询按至少一次语义写入。任一批处理写入失败时，已完成项不回滚，后续项跳过。

## 逻辑表 Map 与字段

无下游 Map；columnMappings 显式 sourceColumnName→targetColumnName，不用字段 UUID。未映射非空必填列需默认值或生成能力。

## 最小配置示例

前提：orders 含 id:LONG；目标已发布模型和物理表存在，字段映射覆盖全部必填非生成列，存储可用。

以下仅为节点 `configuration`；资源 UUID、字段与单位应换成已确认的真实元数据。稳定的操作/写入 ID 由当前任务生成。

```json
{
  "writes": [
    {
      "writeId": "11111111-1111-4111-8111-111111111111",
      "sourceTableName": "orders",
      "targetModelId": "22222222-2222-4222-8222-222222222222",
      "writeMode": "APPEND",
      "columnMappings": [
        {
          "sourceColumnName": "id",
          "targetColumnName": "id"
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
| `MODEL_NOT_PUBLISHED` | 等待用户准备已发布目标，不自动发布模型。 |
| `UPSERT_KEY_NOT_MAPPED` | 映射完整目标模型主键。 |
| `STREAMING_MODEL_OUTPUT_OVERWRITE_NOT_SUPPORTED` | 重新确认 APPEND/UPSERT 输出语义。 |

ERROR 修正后重新预校验最终定义；WARNING 记录影响，不把结构通过表述为数据值或输出结果通过。
