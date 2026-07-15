package cn.superhuang.data.scalpel.dialect.query;

/** Logical input type used for JDBC parameter binding. */
public enum QueryValueType {
    STRING,
    INTEGER,
    LONG,
    DECIMAL,
    BOOLEAN,
    DATE,
    DATETIME
}
