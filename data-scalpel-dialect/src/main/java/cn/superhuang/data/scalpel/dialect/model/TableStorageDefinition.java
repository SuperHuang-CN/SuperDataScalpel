package cn.superhuang.data.scalpel.dialect.model;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * SQL-free storage attributes that are part of a managed physical-table definition.
 *
 * <p>The first supported non-relational storage form is a single-node ClickHouse MergeTree
 * table. The model intentionally exposes only simple column sorting keys, never free SQL
 * expressions or engine settings.</p>
 */
public record TableStorageDefinition(TableStorageEngine engine, List<String> orderByColumns) {

    private static final TableStorageDefinition NONE = new TableStorageDefinition(TableStorageEngine.NONE, List.of());

    public TableStorageDefinition {
        engine = engine == null ? TableStorageEngine.NONE : engine;
        orderByColumns = orderByColumns == null ? List.of() : List.copyOf(orderByColumns);
        Set<String> seen = new HashSet<>();
        for (String column : orderByColumns) {
            if (column == null || column.isBlank()) {
                throw new IllegalArgumentException("Storage sorting key column is required");
            }
            if (!seen.add(column.trim().toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("Duplicate storage sorting key column: " + column);
            }
        }
        orderByColumns = orderByColumns.stream().map(String::trim).toList();
        if (engine == TableStorageEngine.NONE && !orderByColumns.isEmpty()) {
            throw new IllegalArgumentException("Tables without a storage engine cannot define an ordering key");
        }
    }

    public static TableStorageDefinition none() {
        return NONE;
    }

    public static TableStorageDefinition mergeTree(List<String> orderByColumns) {
        return new TableStorageDefinition(TableStorageEngine.MERGE_TREE, orderByColumns);
    }

    public boolean isNone() {
        return engine == TableStorageEngine.NONE;
    }
}
