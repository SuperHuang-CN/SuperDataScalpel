package cn.superhuang.data.scalpel.dialect.model;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Portable, SQL-free description of a relational table structure.
 *
 * <p>The definition is used both for controlled creation and for later physical-table change
 * planning. It deliberately contains no database-specific SQL, storage engine, index, comment,
 * or default-value details.</p>
 */
public record TableDefinition(
        TableIdentifier table,
        List<TableColumnDefinition> columns,
        List<String> primaryKeyColumns,
        TableStorageDefinition storage
) {
    public TableDefinition(TableIdentifier table, List<TableColumnDefinition> columns, List<String> primaryKeyColumns) {
        this(table, columns, primaryKeyColumns, TableStorageDefinition.none());
    }

    public TableDefinition {
        if (table == null) {
            throw new IllegalArgumentException("Table identifier is required");
        }
        if (columns == null || columns.isEmpty()) {
            throw new IllegalArgumentException("At least one column is required");
        }
        columns = List.copyOf(columns);
        primaryKeyColumns = primaryKeyColumns == null ? List.of() : List.copyOf(primaryKeyColumns);
        storage = storage == null ? TableStorageDefinition.none() : storage;

        Set<String> columnNames = new HashSet<>();
        for (TableColumnDefinition column : columns) {
            String normalized = normalize(column.name());
            if (!columnNames.add(normalized)) {
                throw new IllegalArgumentException("Duplicate column name: " + column.name());
            }
        }
        Set<String> primaryKeys = new HashSet<>();
        for (String primaryKeyColumn : primaryKeyColumns) {
            String normalized = normalize(primaryKeyColumn);
            if (!columnNames.contains(normalized)) {
                throw new IllegalArgumentException("Primary key column is not declared: " + primaryKeyColumn);
            }
            if (!primaryKeys.add(normalized)) {
                throw new IllegalArgumentException("Duplicate primary key column: " + primaryKeyColumn);
            }
        }
        for (String orderByColumn : storage.orderByColumns()) {
            if (!columnNames.contains(normalize(orderByColumn))) {
                throw new IllegalArgumentException("Storage sorting key column is not declared: " + orderByColumn);
            }
        }
    }

    /** Returns a stable signature of structural attributes only. */
    public TableStructureFingerprint structureFingerprint() {
        return TableStructureFingerprintCalculator.calculate(this);
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Column name is required");
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
