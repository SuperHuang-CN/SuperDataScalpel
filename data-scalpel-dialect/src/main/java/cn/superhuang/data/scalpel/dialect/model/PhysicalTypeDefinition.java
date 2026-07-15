package cn.superhuang.data.scalpel.dialect.model;

/** Dialect-internal physical type family and its DDL parameters. */
public record PhysicalTypeDefinition(
        TableColumnType type,
        Integer length,
        Integer precision,
        Integer scale
) {

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
    }

    public TableColumnDefinition column(String name, boolean nullable, java.util.UUID columnId) {
        return new TableColumnDefinition(name, type, length, precision, scale, nullable, columnId);
    }
}
