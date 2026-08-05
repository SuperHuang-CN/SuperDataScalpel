package cn.superhuang.data.scalpel.dialect.model;

import java.util.List;

public record TableMetadata(
        TableSummary table,
        List<ColumnMetadata> columns,
        PrimaryKeyMetadata primaryKey,
        List<IndexMetadata> indexes,
        List<UniqueKeyMetadata> uniqueKeys,
        TableStorageMetadata storage
) {
    public TableMetadata(
            TableSummary table,
            List<ColumnMetadata> columns,
            PrimaryKeyMetadata primaryKey,
            List<IndexMetadata> indexes
    ) {
        this(table, columns, primaryKey, indexes, defaultUniqueKeys(primaryKey, indexes), TableStorageMetadata.none());
    }

    public TableMetadata(
            TableSummary table,
            List<ColumnMetadata> columns,
            PrimaryKeyMetadata primaryKey,
            List<IndexMetadata> indexes,
            TableStorageMetadata storage
    ) {
        this(table, columns, primaryKey, indexes, defaultUniqueKeys(primaryKey, indexes), storage);
    }

    public TableMetadata {
        columns = List.copyOf(columns);
        primaryKey = primaryKey == null ? new PrimaryKeyMetadata(null, List.of()) : primaryKey;
        indexes = List.copyOf(indexes);
        uniqueKeys = uniqueKeys == null ? defaultUniqueKeys(primaryKey, indexes) : List.copyOf(uniqueKeys);
        storage = storage == null ? TableStorageMetadata.none() : storage;
    }

    private static List<UniqueKeyMetadata> defaultUniqueKeys(
            PrimaryKeyMetadata primaryKey,
            List<IndexMetadata> indexes
    ) {
        java.util.ArrayList<UniqueKeyMetadata> result = new java.util.ArrayList<>();
        java.util.Set<List<String>> observed = new java.util.HashSet<>();
        if (primaryKey != null && !primaryKey.columns().isEmpty()) {
            List<String> columns = List.copyOf(primaryKey.columns());
            result.add(new UniqueKeyMetadata(primaryKey.name(), UniqueKeyKind.PRIMARY_KEY, columns));
            observed.add(columns);
        }
        if (indexes != null) {
            indexes.stream()
                    .filter(IndexMetadata::unique)
                    .filter(IndexMetadata::usableAsUniqueKey)
                    .filter(index -> !index.columns().isEmpty())
                    .forEach(index -> {
                        List<String> columns = List.copyOf(index.columns());
                        if (observed.add(columns)) {
                            result.add(new UniqueKeyMetadata(
                                    index.name(), UniqueKeyKind.UNIQUE_INDEX, columns));
                        }
                    });
        }
        return List.copyOf(result);
    }
}
