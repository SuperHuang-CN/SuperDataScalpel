package cn.superhuang.data.scalpel.contract.task;

import java.util.List;

public record JdbcOutputConfiguration(
        String dataSourceId,
        List<JdbcOutputWrite> writes
) {
    public JdbcOutputConfiguration {
        writes = writes == null ? List.of() : List.copyOf(writes);
    }

    public JdbcOutputConfiguration(
            String sourceTableName,
            String dataSourceId,
            String targetTableName,
            JdbcWriteMode writeMode,
            List<JdbcColumnMapping> columnMappings,
            List<String> upsertKeyColumns
    ) {
        this(dataSourceId, List.of(new JdbcOutputWrite(
                java.util.UUID.randomUUID().toString(), sourceTableName, targetTableName,
                writeMode, columnMappings, upsertKeyColumns)));
    }

    /** Transitional internal convenience accessors for the per-write operator path. */
    public String sourceTableName() { return writes.isEmpty() ? null : writes.getFirst().sourceTableName(); }
    public String targetTableName() { return writes.isEmpty() ? null : writes.getFirst().targetTableName(); }
    public JdbcWriteMode writeMode() { return writes.isEmpty() ? null : writes.getFirst().writeMode(); }
    public List<JdbcColumnMapping> columnMappings() { return writes.isEmpty() ? List.of() : writes.getFirst().columnMappings(); }
    public List<String> upsertKeyColumns() { return writes.isEmpty() ? List.of() : writes.getFirst().upsertKeyColumns(); }
}
