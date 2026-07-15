package cn.superhuang.data.scalpel.dialect.model;

/** Atomic structural changes from one {@link TableDefinition} to another. */
public enum TableChangeOperationType {
    ADD_COLUMN,
    DROP_COLUMN,
    RENAME_COLUMN,
    ALTER_COLUMN_TYPE,
    ALTER_COLUMN_LENGTH,
    ALTER_COLUMN_PRECISION,
    ALTER_COLUMN_NULLABILITY,
    ALTER_STORAGE_LAYOUT,
    ADD_PRIMARY_KEY,
    DROP_PRIMARY_KEY,
    REPLACE_PRIMARY_KEY;

    public boolean primaryKeyOperation() {
        return this == ADD_PRIMARY_KEY || this == DROP_PRIMARY_KEY || this == REPLACE_PRIMARY_KEY;
    }

    public boolean storageOperation() {
        return this == ALTER_STORAGE_LAYOUT;
    }
}
