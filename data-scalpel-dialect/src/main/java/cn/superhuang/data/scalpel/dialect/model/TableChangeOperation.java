package cn.superhuang.data.scalpel.dialect.model;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** One explainable, dialect-classified structural operation inside a table change plan. */
public record TableChangeOperation(
        TableChangeOperationType type,
        TableColumnDefinition beforeColumn,
        TableColumnDefinition afterColumn,
        List<String> beforePrimaryKeyColumns,
        List<String> afterPrimaryKeyColumns,
        TableChangeStrategy strategy,
        TableChangeRisk risk,
        List<TableChangeReason> reasons,
        List<TableChangeCheck> checks
) {
    public TableChangeOperation {
        if (type == null) {
            throw new IllegalArgumentException("Change operation type is required");
        }
        if (strategy == null || strategy == TableChangeStrategy.METADATA_ONLY) {
            throw new IllegalArgumentException("Physical change operation requires an execution strategy");
        }
        if (risk == null) {
            throw new IllegalArgumentException("Change operation risk is required");
        }
        beforePrimaryKeyColumns = normalizePrimaryKeyColumns(beforePrimaryKeyColumns);
        afterPrimaryKeyColumns = normalizePrimaryKeyColumns(afterPrimaryKeyColumns);
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
        checks = checks == null ? List.of() : List.copyOf(checks);

        if (type.primaryKeyOperation()) {
            validatePrimaryKeyOperation(type, beforeColumn, afterColumn, beforePrimaryKeyColumns, afterPrimaryKeyColumns);
        } else if (type.storageOperation()) {
            require(beforeColumn == null && afterColumn == null,
                    "Storage operation cannot contain column definitions");
            require(beforePrimaryKeyColumns.isEmpty() && afterPrimaryKeyColumns.isEmpty(),
                    "Storage operation cannot contain primary key definitions");
        } else {
            require(beforePrimaryKeyColumns.isEmpty() && afterPrimaryKeyColumns.isEmpty(),
                    "Column operation cannot contain primary key definitions");
            validateColumnOperation(type, beforeColumn, afterColumn);
        }
    }

    private static void validatePrimaryKeyOperation(
            TableChangeOperationType type,
            TableColumnDefinition beforeColumn,
            TableColumnDefinition afterColumn,
            List<String> beforePrimaryKeys,
            List<String> afterPrimaryKeys
    ) {
        require(beforeColumn == null && afterColumn == null,
                "Primary key operation cannot contain column definitions");
        switch (type) {
            case ADD_PRIMARY_KEY -> require(beforePrimaryKeys.isEmpty() && !afterPrimaryKeys.isEmpty(),
                    "Add primary key requires only target primary key columns");
            case DROP_PRIMARY_KEY -> require(!beforePrimaryKeys.isEmpty() && afterPrimaryKeys.isEmpty(),
                    "Drop primary key requires only existing primary key columns");
            case REPLACE_PRIMARY_KEY -> {
                require(!beforePrimaryKeys.isEmpty() && !afterPrimaryKeys.isEmpty(),
                        "Replace primary key requires existing and target primary key columns");
                require(!beforePrimaryKeys.equals(afterPrimaryKeys),
                        "Replace primary key requires a different target primary key");
            }
            default -> throw new IllegalStateException("Unexpected primary key operation: " + type);
        }
    }

    private static void validateColumnOperation(
            TableChangeOperationType type,
            TableColumnDefinition beforeColumn,
            TableColumnDefinition afterColumn
    ) {
        switch (type) {
            case ADD_COLUMN -> require(beforeColumn == null && afterColumn != null,
                    "Add column requires only a target column");
            case DROP_COLUMN -> require(beforeColumn != null && afterColumn == null,
                    "Drop column requires only an existing column");
            case RENAME_COLUMN -> {
                require(beforeColumn != null && afterColumn != null, "Rename column requires both column definitions");
                require(!sameName(beforeColumn, afterColumn), "Rename column requires a different target name");
                requireSameColumnIdentity(beforeColumn, afterColumn);
            }
            case ALTER_COLUMN_TYPE, ALTER_COLUMN_LENGTH, ALTER_COLUMN_PRECISION, ALTER_COLUMN_NULLABILITY -> {
                require(beforeColumn != null && afterColumn != null, type + " requires both column definitions");
                require(sameName(beforeColumn, afterColumn), type + " cannot rename a column");
                requireSameColumnIdentity(beforeColumn, afterColumn);
            }
            default -> throw new IllegalStateException("Unexpected column operation: " + type);
        }
    }

    private static void requireSameColumnIdentity(TableColumnDefinition before, TableColumnDefinition after) {
        if (before.columnId() != null && after.columnId() != null && !before.columnId().equals(after.columnId())) {
            throw new IllegalArgumentException("Column change cannot replace the stable column identity");
        }
    }

    private static boolean sameName(TableColumnDefinition before, TableColumnDefinition after) {
        return before.name().equalsIgnoreCase(after.name());
    }

    private static List<String> normalizePrimaryKeyColumns(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        Set<String> normalized = new HashSet<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Primary key column name is required");
            }
            if (!normalized.add(value.trim().toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("Duplicate primary key column: " + value);
            }
        }
        return values.stream().map(String::trim).toList();
    }

    private static void require(boolean expression, String message) {
        if (!expression) {
            throw new IllegalArgumentException(message);
        }
    }
}
