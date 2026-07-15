package cn.superhuang.data.scalpel.dialect.model;

/** Typed preconditions rendered and evaluated by a database dialect; never user-supplied SQL. */
public enum TableChangeCheckType {
    STRUCTURE_FINGERPRINT_MATCH,
    TABLE_EMPTY,
    COLUMNS_HAVE_NO_NULLS,
    COLUMNS_ARE_UNIQUE,
    MAX_STRING_LENGTH,
    DECIMAL_VALUES_FIT,
    NO_EXTERNAL_DEPENDENCIES,
    NO_REBUILD_DEPENDENCIES,
    DATABASE_RUNTIME_SUPPORTED
}
