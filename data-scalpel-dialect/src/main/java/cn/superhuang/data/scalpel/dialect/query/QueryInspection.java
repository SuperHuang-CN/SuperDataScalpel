package cn.superhuang.data.scalpel.dialect.query;

import java.util.List;

/** JDBC metadata for a validated read-only query. */
public record QueryInspection(List<QueryColumn> columns) {

    public QueryInspection {
        columns = columns == null ? List.of() : List.copyOf(columns);
        if (columns.isEmpty()) {
            throw new IllegalArgumentException("Query must expose at least one column");
        }
    }
}
