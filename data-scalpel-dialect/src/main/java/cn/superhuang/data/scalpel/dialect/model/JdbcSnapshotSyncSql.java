package cn.superhuang.data.scalpel.dialect.model;

import java.util.List;

/** Controlled SQL statements used by one locked snapshot synchronization transaction. */
public record JdbcSnapshotSyncSql(
        List<String> lockStatements,
        String selectSql,
        String deleteSql,
        String updateSql,
        String insertSql,
        String unlockSql
) {
    public JdbcSnapshotSyncSql {
        lockStatements = lockStatements == null ? List.of() : List.copyOf(lockStatements);
        if (lockStatements.isEmpty() || selectSql == null || selectSql.isBlank()
                || deleteSql == null || deleteSql.isBlank()
                || insertSql == null || insertSql.isBlank()) {
            throw new IllegalArgumentException("Snapshot synchronization SQL is incomplete");
        }
    }
}
