package cn.superhuang.data.scalpel.contract.task;

import java.util.List;

/**
 * One physical table selected by a JDBC input node.
 *
 * <p>The object boundary keeps each physical table's read tuning independent. Partitioned reads
 * intentionally remain a future, dedicated configuration instead of raw JDBC options.</p>
 */
public record JdbcInputTableSelection(
        String tableName,
        List<JdbcInputReadOption> readOptions
) {
    public JdbcInputTableSelection {
        readOptions = readOptions == null ? List.of() : List.copyOf(readOptions);
    }

    public JdbcInputTableSelection(String tableName) {
        this(tableName, List.of());
    }
}
