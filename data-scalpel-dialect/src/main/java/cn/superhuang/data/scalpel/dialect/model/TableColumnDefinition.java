package cn.superhuang.data.scalpel.dialect.model;

import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;

import java.util.UUID;

/** One portable column in a table definition. */
public record TableColumnDefinition(
        String name,
        TableColumnType type,
        Integer length,
        Integer precision,
        Integer scale,
        boolean nullable,
        UUID columnId,
        GeometryTypeDefinition geometry
) {
    public TableColumnDefinition(
            String name,
            TableColumnType type,
            Integer length,
            Integer precision,
            Integer scale,
            boolean nullable,
            UUID columnId
    ) {
        this(name, type, length, precision, scale, nullable, columnId, null);
    }

    /** Creates a column without a stable logical identity, for example from JDBC metadata. */
    public TableColumnDefinition(
            String name,
            TableColumnType type,
            Integer length,
            Integer precision,
            Integer scale,
            boolean nullable
    ) {
        this(name, type, length, precision, scale, nullable, null, null);
    }

    public TableColumnDefinition {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Column name is required");
        }
        name = name.trim();
        if (type == null) {
            throw new IllegalArgumentException("Column type is required");
        }
        if (type == TableColumnType.STRING && (length == null || length < 1)) {
            throw new IllegalArgumentException("String column length must be positive");
        }
        if (type == TableColumnType.DECIMAL) {
            if (precision == null || precision < 1) {
                throw new IllegalArgumentException("Decimal precision must be positive");
            }
            if (scale == null || scale < 0 || scale > precision) {
                throw new IllegalArgumentException("Decimal scale must be between zero and precision");
            }
        }
        if (type == TableColumnType.GEOMETRY) {
            if (geometry == null) {
                throw new IllegalArgumentException("Geometry column definition is required");
            }
        } else if (geometry != null) {
            throw new IllegalArgumentException("Only GEOMETRY columns accept a geometry definition");
        }
    }
}
