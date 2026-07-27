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
        String comment,
        SpatialColumnMetadata spatial
) {

    public ColumnMetadata(
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
        this(
                name, ordinal, jdbcType, nativeType, logicalType, length, precision, scale, nullable,
                defaultValue, autoIncrement, generated, comment, null
        );
    }

    public ColumnMetadata withSpatial(SpatialColumnMetadata spatial) {
        return new ColumnMetadata(
                name, ordinal, jdbcType, nativeType, logicalType, length, precision, scale, nullable,
                defaultValue, autoIncrement, generated, comment, spatial
        );
    }
}
