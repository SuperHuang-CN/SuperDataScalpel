package cn.superhuang.data.scalpel.dialect.model;

import java.util.List;

public record TableMetadata(
        TableSummary table,
        List<ColumnMetadata> columns,
        PrimaryKeyMetadata primaryKey,
        List<IndexMetadata> indexes,
        TableStorageMetadata storage
) {
    public TableMetadata(
            TableSummary table,
            List<ColumnMetadata> columns,
            PrimaryKeyMetadata primaryKey,
            List<IndexMetadata> indexes
    ) {
        this(table, columns, primaryKey, indexes, TableStorageMetadata.none());
    }

    public TableMetadata {
        columns = List.copyOf(columns);
        primaryKey = primaryKey == null ? new PrimaryKeyMetadata(null, List.of()) : primaryKey;
        indexes = List.copyOf(indexes);
        storage = storage == null ? TableStorageMetadata.none() : storage;
    }
}
