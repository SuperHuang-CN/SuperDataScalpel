package cn.superhuang.data.scalpel.dialect.model;

import java.util.List;

/** Rendered, controlled DDL statements. Callers never supply arbitrary SQL. */
public record DdlPlan(TableIdentifier table, List<String> statements) {
    public DdlPlan {
        if (table == null) {
            throw new IllegalArgumentException("Table identifier is required");
        }
        if (statements == null || statements.isEmpty()) {
            throw new IllegalArgumentException("At least one DDL statement is required");
        }
        statements = List.copyOf(statements);
    }
}
