package cn.superhuang.data.scalpel.contract.task;

import java.util.List;

public record ModelSnapshotSyncOutputConfiguration(
        String sourceTableName,
        String targetModelId,
        List<String> keyColumns,
        List<JdbcColumnMapping> columnMappings,
        SnapshotDeletePolicy deletePolicy
) implements SnapshotSyncConfiguration {
    public ModelSnapshotSyncOutputConfiguration {
        keyColumns = keyColumns == null ? List.of() : List.copyOf(keyColumns);
        columnMappings = columnMappings == null ? List.of() : List.copyOf(columnMappings);
    }
}
