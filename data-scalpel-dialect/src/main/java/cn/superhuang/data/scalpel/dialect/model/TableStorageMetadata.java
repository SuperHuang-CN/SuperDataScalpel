package cn.superhuang.data.scalpel.dialect.model;

import java.util.List;

/** Raw, read-only table storage attributes obtained from a target database. */
public record TableStorageMetadata(String engine, List<String> orderByColumns) {

    private static final TableStorageMetadata NONE = new TableStorageMetadata(null, List.of());

    public TableStorageMetadata {
        engine = engine == null || engine.isBlank() ? null : engine.trim();
        orderByColumns = orderByColumns == null ? List.of() : orderByColumns.stream().map(String::trim).toList();
    }

    public static TableStorageMetadata none() {
        return NONE;
    }
}
