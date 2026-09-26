package cn.superhuang.datascalpel.sdk;

/**
 * 明确表所在的 Catalog、Schema 和表名，避免拼接多段名称。
 * @apiGroup 读写 JDBC
 * @apiExample var table = JdbcTableIdentifier.schemaTable("public", "orders");
 * @param catalog Catalog，可为 null；不指定时沿用连接默认值。
 * @param schema Schema，可为 null；不指定时沿用连接默认值。
 * @param table 物理表名，必填，最长 255 字符，不含换行或空字符。
 */
public record JdbcTableIdentifier(String catalog, String schema, String table) {
    public JdbcTableIdentifier {
        catalog = normalizeOptional(catalog);
        schema = normalizeOptional(schema);
        table = requirePart(table, "table");
    }

    /**
     * 使用 Catalog、Schema 和表名创建表标识。
     * @param catalog Catalog，可为 null；不指定时沿用连接默认值。
     * @param schema Schema，可为 null；不指定时沿用连接默认值。
     * @param table 物理表名，必填，最长 255 字符，不含换行或空字符。
     */
    public static JdbcTableIdentifier of(String catalog, String schema, String table) {
        return new JdbcTableIdentifier(catalog, schema, table);
    }

    /**
     * 仅指定表名，沿用连接默认 Catalog 和 Schema。
     * @param table 物理表名，必填，最长 255 字符，不含换行或空字符。
     */
    public static JdbcTableIdentifier table(String table) {
        return new JdbcTableIdentifier(null, null, table);
    }

    /**
     * 指定 Schema 和表名，沿用默认 Catalog。
     * @param schema Schema，可为 null；不指定时沿用连接默认值。
     * @param table 物理表名，必填，最长 255 字符，不含换行或空字符。
     */
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
