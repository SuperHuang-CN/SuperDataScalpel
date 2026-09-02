package cn.superhuang.data.scalpel.contract.task;

import java.util.List;

/** One independently addressable Kafka sink in a Kafka output node. */
public record KafkaOutputWrite(
        String writeId,
        String sourceTableName,
        String topic,
        KafkaOutputValueFormat valueFormat,
        List<String> valueColumnNames,
        String keyColumnName,
        KafkaValueSchema valueSchema,
        List<JdbcColumnMapping> columnMappings
) {
    public KafkaOutputWrite {
        valueColumnNames = valueColumnNames == null ? List.of() : List.copyOf(valueColumnNames);
        columnMappings = columnMappings == null ? List.of() : List.copyOf(columnMappings);
    }

    /** Canvas 4.0-4.5 compatibility constructor for the legacy JSON schema/mapping mode. */
    public KafkaOutputWrite(
            String writeId,
            String sourceTableName,
            String topic,
            KafkaValueSchema valueSchema,
            String keyColumnName,
            List<JdbcColumnMapping> columnMappings
    ) {
        this(
                writeId, sourceTableName, topic,
                null, List.of(), keyColumnName, valueSchema, columnMappings
        );
    }

    public boolean legacyMappingMode() {
        return valueFormat == null;
    }
}
