package cn.superhuang.data.scalpel.contract.task;

import java.util.List;

public record KafkaOutputConfiguration(
        String sourceTableName,
        String dataSourceId,
        String topic,
        KafkaValueSchema valueSchema,
        String keyColumnName,
        ColumnMappingMode columnMappingMode,
        List<JdbcColumnMapping> columnMappings
) {
}
