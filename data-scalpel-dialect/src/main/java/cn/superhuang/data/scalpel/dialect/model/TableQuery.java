package cn.superhuang.data.scalpel.dialect.model;

public record TableQuery(
        String catalog,
        String schema,
        String keyword,
        boolean includeViews,
        int limit
) {
    public TableQuery {
        catalog = optional(catalog);
        schema = optional(schema);
        keyword = optional(keyword);
        if (limit < 1 || limit > 2000) {
            throw new IllegalArgumentException("Table list limit must be between 1 and 2000");
        }
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
