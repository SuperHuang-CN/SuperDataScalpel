package cn.superhuang.data.scalpel.contract.task;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public record MetadataDataSource(
        UUID id,
        boolean enabled,
        ConnectionKind connectionKind,
        CanvasJdbcDatabaseType jdbcDatabaseType,
        Set<DataSourcePurpose> purposes,
        List<MetadataTable> tables
) {
    public MetadataDataSource(
            UUID id,
            boolean enabled,
            ConnectionKind connectionKind,
            Set<DataSourcePurpose> purposes,
            List<MetadataTable> tables
    ) {
        this(id, enabled, connectionKind, null, purposes, tables);
    }
}
