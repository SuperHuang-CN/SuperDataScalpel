package cn.superhuang.data.scalpel.contract.task;

import java.util.List;

public record ModelOutputConfiguration(
        List<ModelOutputWrite> writes
) {
    public ModelOutputConfiguration {
        writes = writes == null ? List.of() : List.copyOf(writes);
    }

    public ModelOutputConfiguration(
            String sourceTableName,
            String targetModelId,
            JdbcWriteMode writeMode,
            List<JdbcColumnMapping> columnMappings
    ) {
        this(List.of(new ModelOutputWrite(
                java.util.UUID.randomUUID().toString(), sourceTableName,
                targetModelId, writeMode, columnMappings)));
    }

    public String sourceTableName() { return writes.isEmpty() ? null : writes.getFirst().sourceTableName(); }
    public String targetModelId() { return writes.isEmpty() ? null : writes.getFirst().targetModelId(); }
    public JdbcWriteMode writeMode() { return writes.isEmpty() ? null : writes.getFirst().writeMode(); }
    public List<JdbcColumnMapping> columnMappings() { return writes.isEmpty() ? List.of() : writes.getFirst().columnMappings(); }
}
