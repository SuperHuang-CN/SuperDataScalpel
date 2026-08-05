package cn.superhuang.data.scalpel.contract.task;

import java.util.List;

public record ModelOutputConfiguration(
        String sourceTableName,
        String targetModelId,
        JdbcWriteMode writeMode,
        ColumnMappingMode columnMappingMode,
        List<JdbcColumnMapping> columnMappings
) {
}
