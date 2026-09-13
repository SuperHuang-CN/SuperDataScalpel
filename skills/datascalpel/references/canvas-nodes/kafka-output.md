# KAFKA_OUTPUT · Kafka 输出

[节点索引](index.md) · [Canvas 共用规则](../task-canvas.md)

## 用途与选择

流式 Kafka 输出配置；把一张或多张无界逻辑表编码后写入同一 Kafka 数据源的最多 32 个独立 Topic Sink。每个写入拥有稳定 writeId，并由 Runner 使用独立 StreamingQuery 和 Checkpoint。

## 模式与连线

- 模式：STREAMING；类别：OUTPUT。
- 恰好一条入边，无出边；一条入边可包含多张逻辑表。
- 输入有界性及批流差异见下文。

## 关键配置与行为

- 最多 32 项，每项独立查询和 Checkpoint；writeId 稳定，不能因调整布局而换 ID。
- JSON 使用 valueColumnNames 投影并编码对象；TEXT/BINARY 恰好一个对应类型字段。可选 keyColumnName 必须满足类型约束。
- 没有旧版 valueSchema/columnMappings 配置；Geometry 先经批准序列化后选择普通列。

配置定位（只列关键语义，完整字段读取实时契约）：

- `writes`：必填的 1 至 32 项 Topic 写入；按配置顺序准备，每项 writeId 必须是节点内唯一 UUID。NULL 列表规范化为空列表并在编译时作为缺失配置拒绝。

## 逻辑表 Map 与字段

无下游 Map；每项消耗一张无界表，消息字段仅按显式选择输出；不保证端到端 Exactly Once。

## 最小配置示例

前提：events 是 UNBOUNDED，含普通 LONG id；目标 UUID 为启用且有 DISTRIBUTION 用途的 Kafka 数据源，Topic 已准备。

以下仅为节点 `configuration`；资源 UUID、字段与单位应换成已确认的真实元数据。稳定的操作/写入 ID 由当前任务生成。

```json
{
  "dataSourceId": "22222222-2222-4222-8222-222222222222",
  "writes": [
    {
      "writeId": "11111111-1111-4111-8111-111111111111",
      "sourceTableName": "events",
      "topic": "orders.cleaned",
      "valueFormat": "JSON",
      "valueColumnNames": [
        "id"
      ],
      "keyColumnName": null
    }
  ]
}
```

## 预校验检查与修正

将节点放入完整画布，按 Canvas 指南调用 Engine 预校验，核对输入/输出表及字段；此处没有真实数据测试步骤。

| 诊断或检查点 | 处理方式 |
| --- | --- |
| `KAFKA_OUTPUT_REQUIRES_UNBOUNDED` | 选择真实无界来源。 |
| `KAFKA_VALUE_COLUMN_COUNT_INVALID` | TEXT/BINARY 只选一列。 |
| `KAFKA_JSON_GEOMETRY_REQUIRES_SERIALIZATION` | 用几何序列化节点形成普通字段。 |

ERROR 修正后重新预校验最终定义；WARNING 记录影响，不把结构通过表述为数据值或输出结果通过。
