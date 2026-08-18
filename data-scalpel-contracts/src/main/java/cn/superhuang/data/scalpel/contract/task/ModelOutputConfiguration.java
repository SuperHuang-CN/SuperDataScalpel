package cn.superhuang.data.scalpel.contract.task;

import java.util.List;

public record ModelOutputConfiguration(
        String sourceTableName,
        String targetModelId,
        JdbcWriteMode writeMode,
        List<JdbcColumnMapping> columnMappings
) {
    public ModelOutputConfiguration {
        columnMappings = columnMappings == null ? List.of() : List.copyOf(columnMappings);
    }
}
