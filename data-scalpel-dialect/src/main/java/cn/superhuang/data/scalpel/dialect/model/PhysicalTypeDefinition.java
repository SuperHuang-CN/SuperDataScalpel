package cn.superhuang.data.scalpel.dialect.model;

import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;

/** Dialect-internal physical type family and its DDL parameters. */
public record PhysicalTypeDefinition(
        TableColumnType type,
        Integer length,
        Integer precision,
        Integer scale,
        GeometryTypeDefinition geometry
) {

    public PhysicalTypeDefinition(
            TableColumnType type,
            Integer length,
            Integer precision,
            Integer scale
    ) {
        this(type, length, precision, scale, null);
    }

    public PhysicalTypeDefinition {
        if (type == null) {
            throw new IllegalArgumentException("Physical column type is required");
        }
        if (type == TableColumnType.STRING && (length == null || length < 1)) {
            throw new IllegalArgumentException("Bounded physical string requires a positive length");
        }
        if (type == TableColumnType.DECIMAL) {
            if (precision == null || precision < 1) {
                throw new IllegalArgumentException("Physical decimal precision must be positive");
            }
            if (scale == null || scale < 0 || scale > precision) {
                throw new IllegalArgumentException("Physical decimal scale must be between 0 and precision");
            }
        }
        if (type == TableColumnType.GEOMETRY) {
            if (geometry == null) {
                throw new IllegalArgumentException("Physical GEOMETRY requires a geometry definition");
            }
        } else if (geometry != null) {
            throw new IllegalArgumentException("Only physical GEOMETRY accepts a geometry definition");
        }
    }

    public TableColumnDefinition column(String name, boolean nullable, java.util.UUID columnId) {
        return new TableColumnDefinition(name, type, length, precision, scale, nullable, columnId, geometry);
    }
}
