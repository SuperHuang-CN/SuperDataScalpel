package cn.superhuang.data.scalpel.contract.task;

import java.util.List;

/** One independently addressable model sink in a model output node. */
public record ModelOutputWrite(
        String writeId,
        String sourceTableName,
        String targetModelId,
        JdbcWriteMode writeMode,
        List<JdbcColumnMapping> columnMappings
) {
    public ModelOutputWrite {
        columnMappings = columnMappings == null ? List.of() : List.copyOf(columnMappings);
    }
}
