# KAFKA_INPUT · Kafka 输入

[节点索引](index.md) · [Canvas 共用规则](../task-canvas.md)

## 用途与选择

流式 Kafka 输入配置；持续订阅一个 Topic，把 Value 按 JSON、UTF-8 文本或原始二进制解码为无界逻辑表，并可按固定名称追加 Kafka 元数据字段。节点本身不配置 Broker、认证或 Schema Registry。

## 模式与连线

- 模式：STREAMING；类别：INPUT。
- 无入边，至少一条出边；不读取上游 Map。
- 输入有界性见下文。

## 关键配置与行为

- valueFormat=JSON 时使用明确 valueSchema；TEXT/BINARY 仍必须提供 valueSchema={columns: []}，不能省略或置 NULL。起始位置仅设计首次消费，已有 Checkpoint 接管恢复。
- metadataFields 固定 KEY→_kafka_key:BINARY、TOPIC→_kafka_topic:STRING、PARTITION→_kafka_partition:INTEGER、OFFSET→_kafka_offset:LONG、TIMESTAMP→_kafka_timestamp:TIMESTAMP；按此固定顺序追加，均可空。避免与 Value 列冲突；Kafka timestamp 不自动成为事件时间。
- 不填 Broker、认证或 Schema Registry；触发间隔不是 Watermark。

配置定位（只列关键语义，完整字段读取实时契约）：

- `topic`：必填的单个 Kafka Topic 名称。
- `valueSchema`：Value 的内联结构；JSON 必须提供至少一个合法字段，TEXT/BINARY 必须提供 columns=[]，不能为 NULL。该 Schema 不连接 Schema Registry。
- `startingOffsets`：必填的首次消费位置：EARLIEST 从可用最早 Offset 开始，LATEST 从启动时末尾开始；已有 Spark Checkpoint 时由 Checkpoint Offset 接管。
- `triggerIntervalSeconds`：必填的流式微批触发间隔秒数，范围 1 至 300；旧构造器默认 10 秒。实时任务只允许一个无界输入，其触发间隔成为任务触发间隔。

## 逻辑表 Map 与字段

输出 UNBOUNDED 表。TEXT 为 value:STRING，BINARY 为 value:BINARY；JSON 为声明列，按需追加元数据列。

## 最小配置示例

前提：UUID 为启用且有 SOURCE 用途的 Kafka 数据源，Topic 已存在；示例消息为 UTF-8 文本。

以下仅为节点 `configuration`；资源 UUID、字段与单位应换成已确认的真实元数据。稳定的操作/写入 ID 由当前任务生成。

```json
{
  "dataSourceId": "11111111-1111-4111-8111-111111111111",
  "topic": "orders.events",
  "valueFormat": "TEXT",
  "outputTableName": "events",
  "startingOffsets": "EARLIEST",
  "triggerIntervalSeconds": 10,
  "metadataFields": [],
  "valueSchema": {
    "columns": []
  }
}
```

## 预校验检查与修正

将节点放入完整画布，按 Canvas 指南调用 Engine 预校验，核对输入/输出表及字段；此处没有真实数据测试步骤。

| 诊断或检查点 | 处理方式 |
| --- | --- |
| `DATA_SOURCE_UNAVAILABLE` | 核对 Kafka 类型、启用与 SOURCE 用途。 |
| `REQUIRED_CONFIGURATION` | 补齐 Topic、格式、起始位置和合法消息 Schema。 |

ERROR 修正后重新预校验最终定义；WARNING 记录影响，不把结构通过表述为数据值或输出结果通过。
