package cn.superhuang.data.scalpel.dialect.query;

import java.util.List;

/** SQL text assembled solely from the AST plus ordered JDBC bindings. */
public record PreparedQuery(String sql, List<QueryParameter> parameters) {

    public PreparedQuery {
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("SQL is required");
        }
        parameters = parameters == null ? List.of() : List.copyOf(parameters);
    }
}
