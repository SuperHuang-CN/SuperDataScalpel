package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("Kafka 输入可选元数据：KEY→_kafka_key BINARY、TOPIC→_kafka_topic STRING、PARTITION→_kafka_partition INTEGER、OFFSET→_kafka_offset LONG、TIMESTAMP→_kafka_timestamp TIMESTAMP，字段均按保守规则声明可空。Key 不做文本解码，Timestamp 不会自动成为事件时间或 Watermark。")
public enum KafkaInputMetadataField {
    KEY,
    TOPIC,
    PARTITION,
    OFFSET,
    TIMESTAMP
}
