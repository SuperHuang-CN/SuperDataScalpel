package cn.superhuang.data.scalpel.dialect.model;

/** Portable column types that can be rendered by a writable database dialect. */
public enum TableColumnType {
    BYTE,
    SHORT,
    STRING,
    TEXT,
    INTEGER,
    LONG,
    FLOAT,
    DOUBLE,
    DECIMAL,
    BOOLEAN,
    DATE,
    TIMESTAMP,
    TIMESTAMP_NTZ,
    /** Legacy persisted change-plan value. New mappings must use TIMESTAMP_NTZ. */
    @Deprecated
    DATETIME,
    BINARY,
    GEOMETRY
}
