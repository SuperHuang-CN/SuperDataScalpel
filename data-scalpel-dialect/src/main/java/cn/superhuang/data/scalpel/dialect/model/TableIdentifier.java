package cn.superhuang.data.scalpel.dialect.model;

public record TableIdentifier(String catalog, String schema, String table) {
    public TableIdentifier {
        catalog = optional(catalog);
        schema = optional(schema);
        if (table == null || table.isBlank()) {
            throw new IllegalArgumentException("Table name is required");
        }
        table = table.trim();
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
