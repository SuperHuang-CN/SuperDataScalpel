package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

/** Serialization applied to one Kafka output Value. */
@JsonClassDescription("Kafka 输出 Value 格式：JSON 把所选上游字段按上游 Schema 顺序组成对象并保留 NULL 字段；TEXT 原样发送唯一 STRING 字段的 UTF-8 内容；BINARY 原样发送唯一 BINARY 字段。TEXT/BINARY 的 NULL 值产生 Kafka tombstone；不负责 Avro、Protobuf、压缩、加密或 Schema Registry。")
public enum KafkaOutputValueFormat {
    JSON,
    TEXT,
    BINARY
}
