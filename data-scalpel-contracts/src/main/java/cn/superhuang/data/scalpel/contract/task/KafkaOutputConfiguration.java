package cn.superhuang.data.scalpel.contract.task;

import java.util.List;
import java.util.UUID;

public record KafkaOutputConfiguration(
        String sourceTableName,
        UUID dataSourceId,
        String topic,
        KafkaValueSchema valueSchema,
        String keyColumnName,
        ColumnMappingMode columnMappingMode,
        List<JdbcColumnMapping> columnMappings
) {
}
