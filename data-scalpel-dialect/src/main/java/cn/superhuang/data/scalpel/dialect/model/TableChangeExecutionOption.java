package cn.superhuang.data.scalpel.dialect.model;

import java.util.List;

/** One complete, dialect-rendered execution alternative. SQL is controlled by the dialect only. */
public record TableChangeExecutionOption(
        TableChangeExecutionMode mode,
        TableDdlAtomicity atomicity,
        List<String> statements
) {
    public TableChangeExecutionOption {
        if (mode == null || atomicity == null) {
            throw new IllegalArgumentException("Change execution mode and atomicity are required");
        }
        if (atomicity == TableDdlAtomicity.NOT_APPLICABLE) {
            throw new IllegalArgumentException("Physical change execution requires DDL atomicity");
        }
        if (statements == null || statements.isEmpty()) {
            throw new IllegalArgumentException("Change execution requires at least one statement");
        }
        statements = List.copyOf(statements);
        if (statements.stream().anyMatch(statement -> statement == null || statement.isBlank())) {
            throw new IllegalArgumentException("Change execution statement must not be blank");
        }
    }
}
