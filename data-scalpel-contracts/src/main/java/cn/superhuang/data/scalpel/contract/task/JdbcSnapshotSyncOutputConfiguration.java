package cn.superhuang.data.scalpel.contract.task;

import java.util.List;

public record JdbcSnapshotSyncOutputConfiguration(
        String sourceTableName,
        String dataSourceId,
        String targetTableName,
        List<String> keyColumns,
        List<JdbcColumnMapping> columnMappings,
        SnapshotDeletePolicy deletePolicy
) implements SnapshotSyncConfiguration {
    public JdbcSnapshotSyncOutputConfiguration {
        keyColumns = keyColumns == null ? List.of() : List.copyOf(keyColumns);
        columnMappings = columnMappings == null ? List.of() : List.copyOf(columnMappings);
    }
}
