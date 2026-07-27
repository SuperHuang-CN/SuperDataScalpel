package cn.superhuang.data.scalpel.dialect.model;

/** Raw JDBC and driver metadata at the physical-to-platform type mapping boundary. */
public record JdbcTypeDescriptor(
        int jdbcType,
        String nativeTypeName,
        Integer length,
        Integer precision,
        Integer scale,
        Boolean signed,
        SpatialColumnMetadata spatial
) {

    public JdbcTypeDescriptor(
            int jdbcType,
            String nativeTypeName,
            Integer length,
            Integer precision,
            Integer scale,
            Boolean signed
    ) {
        this(jdbcType, nativeTypeName, length, precision, scale, signed, null);
    }

    public JdbcTypeDescriptor {
        nativeTypeName = nativeTypeName == null ? "" : nativeTypeName.trim();
    }

    public static JdbcTypeDescriptor from(ColumnMetadata column) {
        return new JdbcTypeDescriptor(
                column.jdbcType(), column.nativeType(), column.length(), column.precision(), column.scale(), null,
                column.spatial()
        );
    }
}
