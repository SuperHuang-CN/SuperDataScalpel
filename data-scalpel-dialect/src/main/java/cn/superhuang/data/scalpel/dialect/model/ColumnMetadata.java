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
        SpatialColumnMetadata spatial,
        ColumnRole role
) {

    public ColumnMetadata {
        role = role == null ? ColumnRole.REGULAR : role;
    }

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
                defaultValue, autoIncrement, generated, comment, null, ColumnRole.REGULAR
        );
    }

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
            String comment,
            SpatialColumnMetadata spatial
    ) {
        this(
                name, ordinal, jdbcType, nativeType, logicalType, length, precision, scale, nullable,
                defaultValue, autoIncrement, generated, comment, spatial, ColumnRole.REGULAR
        );
    }

    public ColumnMetadata withSpatial(SpatialColumnMetadata spatial) {
        return new ColumnMetadata(
                name, ordinal, jdbcType, nativeType, logicalType, length, precision, scale, nullable,
                defaultValue, autoIncrement, generated, comment, spatial, role
        );
    }

    public ColumnMetadata withRole(ColumnRole role) {
        return new ColumnMetadata(
                name, ordinal, jdbcType, nativeType, logicalType, length, precision, scale, nullable,
                defaultValue, autoIncrement, generated, comment, spatial, role
        );
    }

    public ColumnMetadata withDialectDetails(
            String nativeType,
            boolean nullable,
            String comment,
            SpatialColumnMetadata spatial
    ) {
        return new ColumnMetadata(
                name, ordinal, jdbcType, nativeType, logicalType, length, precision, scale, nullable,
                defaultValue, autoIncrement, generated, comment, spatial, role
        );
    }
}
