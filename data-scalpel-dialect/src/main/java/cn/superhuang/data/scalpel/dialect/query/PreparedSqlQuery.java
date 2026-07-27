package cn.superhuang.data.scalpel.dialect.query;

import java.util.List;

/** Parameterized SQL query using the stable platform scalar type system. */
public record PreparedSqlQuery(String sql, List<SqlQueryParameter> parameters) {

    public PreparedSqlQuery {
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("Prepared SQL is required");
        }
        parameters = List.copyOf(parameters);
    }
}
