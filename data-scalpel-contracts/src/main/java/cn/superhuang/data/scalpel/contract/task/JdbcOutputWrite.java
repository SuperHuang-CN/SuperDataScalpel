package cn.superhuang.data.scalpel.contract.task;

import java.util.List;

/** One independently addressable JDBC sink in a JDBC output node. */
public record JdbcOutputWrite(
        String writeId,
        String sourceTableName,
        String targetTableName,
        JdbcWriteMode writeMode,
        List<JdbcColumnMapping> columnMappings,
        List<String> upsertKeyColumns
) {
    public JdbcOutputWrite {
        columnMappings = columnMappings == null ? List.of() : List.copyOf(columnMappings);
        upsertKeyColumns = upsertKeyColumns == null ? List.of() : List.copyOf(upsertKeyColumns);
    }
}
