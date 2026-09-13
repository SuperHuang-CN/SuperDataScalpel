package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("Kafka 输入 Value 格式：JSON 按内联扁平 Schema 解析；TEXT 使用 UTF-8 解码为 value STRING；BINARY 保留原始字节为 value BINARY。TEXT/BINARY 的 tombstone 为 NULL value，均不自动推断字符集或业务编码。")
public enum KafkaInputValueFormat {
    JSON,
    TEXT,
    BINARY
}
