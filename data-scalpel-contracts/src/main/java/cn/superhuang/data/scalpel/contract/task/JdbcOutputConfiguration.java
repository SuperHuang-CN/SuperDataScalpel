package cn.superhuang.data.scalpel.contract.task;

import java.util.List;

public record JdbcOutputConfiguration(
        String sourceTableName,
        String dataSourceId,
        String targetTableName,
        JdbcWriteMode writeMode,
        ColumnMappingMode columnMappingMode,
        List<JdbcColumnMapping> columnMappings
) {
}
