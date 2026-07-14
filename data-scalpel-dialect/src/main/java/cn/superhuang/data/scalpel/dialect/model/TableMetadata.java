package cn.superhuang.data.scalpel.dialect.model;

import java.util.List;

public record TableMetadata(
        TableSummary table,
        List<ColumnMetadata> columns,
        PrimaryKeyMetadata primaryKey,
        List<IndexMetadata> indexes
) {
    public TableMetadata {
        columns = List.copyOf(columns);
        indexes = List.copyOf(indexes);
    }
}
