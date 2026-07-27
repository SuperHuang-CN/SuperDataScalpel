package cn.superhuang.data.scalpel.contract.task;

import java.util.List;
import java.util.UUID;

public record ModelOutputConfiguration(
        String sourceTableName,
        UUID targetModelId,
        JdbcWriteMode writeMode,
        ColumnMappingMode columnMappingMode,
        List<JdbcColumnMapping> columnMappings
) {
}
