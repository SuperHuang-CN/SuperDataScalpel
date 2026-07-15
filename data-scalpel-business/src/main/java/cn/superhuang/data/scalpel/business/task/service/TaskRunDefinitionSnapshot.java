package cn.superhuang.data.scalpel.business.task.service;

import cn.superhuang.data.scalpel.business.datasource.domain.DataSourceType;
import cn.superhuang.data.scalpel.business.task.domain.LocalSqlWriteMode;

import java.util.List;
import java.util.UUID;

/** Credential-free, immutable execution input persisted with every task run. */
public record TaskRunDefinitionSnapshot(
        UUID dataSourceId,
        DataSourceType dataSourceType,
        List<UUID> inputModelIds,
        UUID outputModelId,
        PhysicalTarget outputTarget,
        String sql,
        LocalSqlWriteMode writeMode,
        int timeoutSeconds,
        List<String> targetColumns
) {

    public TaskRunDefinitionSnapshot {
        inputModelIds = inputModelIds == null ? List.of() : List.copyOf(inputModelIds);
        targetColumns = targetColumns == null ? List.of() : List.copyOf(targetColumns);
    }

    public record PhysicalTarget(String catalogName, String schemaName, String tableName) {
    }
}
