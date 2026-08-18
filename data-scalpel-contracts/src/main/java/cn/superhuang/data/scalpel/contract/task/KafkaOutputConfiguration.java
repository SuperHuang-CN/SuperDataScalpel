package cn.superhuang.data.scalpel.contract.task;

import java.util.List;

public record KafkaOutputConfiguration(
        String sourceTableName,
        String dataSourceId,
        String topic,
        KafkaValueSchema valueSchema,
        String keyColumnName,
        List<JdbcColumnMapping> columnMappings
) {
    public KafkaOutputConfiguration {
        columnMappings = columnMappings == null ? List.of() : List.copyOf(columnMappings);
    }
}
