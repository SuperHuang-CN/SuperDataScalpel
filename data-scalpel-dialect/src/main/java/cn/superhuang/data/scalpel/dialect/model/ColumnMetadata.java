package cn.superhuang.data.scalpel.dialect.model;

public record ColumnMetadata(
        String name,
        int ordinal,
        int jdbcType,
        String nativeType,
        LogicalType logicalType,
        Integer length,
        Integer precision,
        Integer scale,
        boolean nullable,
        String defaultValue,
        boolean autoIncrement,
        boolean generated,
        String comment
) {
}
