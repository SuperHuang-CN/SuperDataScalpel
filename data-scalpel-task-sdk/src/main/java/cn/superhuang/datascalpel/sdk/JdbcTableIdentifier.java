package cn.superhuang.datascalpel.sdk;

/** Database-independent table identity; catalog and schema may be absent. */
public record JdbcTableIdentifier(String catalog, String schema, String table) {
    public JdbcTableIdentifier {
        catalog = normalizeOptional(catalog);
        schema = normalizeOptional(schema);
        table = requirePart(table, "table");
    }

    public static JdbcTableIdentifier of(String catalog, String schema, String table) {
        return new JdbcTableIdentifier(catalog, schema, table);
    }

    public static JdbcTableIdentifier table(String table) {
        return new JdbcTableIdentifier(null, null, table);
    }

    public static JdbcTableIdentifier schemaTable(String schema, String table) {
        return new JdbcTableIdentifier(null, schema, table);
    }

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : requirePart(normalized, "identifier");
    }

    private static String requirePart(String value, String label) {
        if (value == null || value.isBlank() || value.length() > 255
                || value.indexOf('\0') >= 0 || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("Invalid JDBC " + label);
        }
        return value.trim();
    }
}
