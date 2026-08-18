package cn.superhuang.data.scalpel.dialect.model;

/** Physical role of a column when the source engine distinguishes metrics, time keys and tags. */
public enum ColumnRole {
    REGULAR,
    TIME_KEY,
    TAG
}
