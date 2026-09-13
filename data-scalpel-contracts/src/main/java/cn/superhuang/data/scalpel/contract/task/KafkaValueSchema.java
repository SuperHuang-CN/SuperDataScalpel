package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

@JsonClassDescription("Kafka JSON Value 的内联扁平对象 Schema；不表示 Avro、Protobuf 或 Schema Registry 契约。Kafka Input 的 TEXT/BINARY 仍需保留该对象但 columns 必须为空。")

public record KafkaValueSchema(
        @JsonPropertyDescription("Kafka JSON Value 的有序字段定义；输入 JSON 和旧版输出 JSON 至少一项，新版输出 JSON 不使用本对象。TEXT/BINARY 必须为空列表；NULL 列表在构造时规范化为空列表。")
        List<KafkaValueColumn> columns
) {
    public KafkaValueSchema {
        columns = columns == null ? List.of() : List.copyOf(columns);
    }
}
