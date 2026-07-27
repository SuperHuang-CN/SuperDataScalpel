package cn.superhuang.data.scalpel.dialect.query;

import java.util.List;

/** JDBC SQL and binding names produced from one named-parameter SQL template. */
public record SqlTemplateCompilation(String jdbcSql, List<String> bindingOrder) {

    public SqlTemplateCompilation {
        if (jdbcSql == null || jdbcSql.isBlank()) {
            throw new IllegalArgumentException("Compiled SQL is required");
        }
        bindingOrder = List.copyOf(bindingOrder);
    }
}
