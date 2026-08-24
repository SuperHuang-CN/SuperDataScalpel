package cn.superhuang.data.scalpel.contract.task;

import java.util.List;

/** One independently addressable Kafka sink in a Kafka output node. */
public record KafkaOutputWrite(
        String writeId,
        String sourceTableName,
        String topic,
        KafkaValueSchema valueSchema,
        String keyColumnName,
        List<JdbcColumnMapping> columnMappings
) {
    public KafkaOutputWrite {
        columnMappings = columnMappings == null ? List.of() : List.copyOf(columnMappings);
    }
}
