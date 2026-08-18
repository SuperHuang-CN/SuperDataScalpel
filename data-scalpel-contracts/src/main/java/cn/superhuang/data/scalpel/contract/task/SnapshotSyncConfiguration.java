package cn.superhuang.data.scalpel.contract.task;

import java.util.List;

public sealed interface SnapshotSyncConfiguration
        permits JdbcSnapshotSyncOutputConfiguration, ModelSnapshotSyncOutputConfiguration {
    String sourceTableName();

    List<String> keyColumns();

    List<JdbcColumnMapping> columnMappings();

    SnapshotDeletePolicy deletePolicy();
}
