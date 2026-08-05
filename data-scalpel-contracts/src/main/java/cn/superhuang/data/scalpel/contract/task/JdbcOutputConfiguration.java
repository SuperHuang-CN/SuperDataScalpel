package cn.superhuang.data.scalpel.contract.task;

import java.util.List;

public record JdbcOutputConfiguration(
        String sourceTableName,
        String dataSourceId,
        String targetTableName,
        JdbcWriteMode writeMode,
        ColumnMappingMode columnMappingMode,
        List<JdbcColumnMapping> columnMappings,
        List<String> upsertKeyColumns
) {
    public JdbcOutputConfiguration {
        columnMappings = columnMappings == null ? List.of() : List.copyOf(columnMappings);
        upsertKeyColumns = upsertKeyColumns == null ? List.of() : List.copyOf(upsertKeyColumns);
    }

    public JdbcOutputConfiguration(
            String sourceTableName,
            String dataSourceId,
            String targetTableName,
            JdbcWriteMode writeMode,
            ColumnMappingMode columnMappingMode,
            List<JdbcColumnMapping> columnMappings
    ) {
        this(sourceTableName, dataSourceId, targetTableName, writeMode,
                columnMappingMode, columnMappings, List.of());
    }
}
