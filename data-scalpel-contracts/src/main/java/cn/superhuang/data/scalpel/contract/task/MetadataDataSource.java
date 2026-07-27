package cn.superhuang.data.scalpel.contract.task;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public record MetadataDataSource(
        UUID id,
        boolean enabled,
        ConnectionKind connectionKind,
        Set<DataSourcePurpose> purposes,
        List<MetadataTable> tables
) {
}
