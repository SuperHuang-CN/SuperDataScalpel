package cn.superhuang.data.scalpel.dialect.builtin;

import cn.superhuang.data.scalpel.dialect.model.JdbcUpsertColumn;
import cn.superhuang.data.scalpel.dialect.model.TableIdentifier;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Controlled scalar MERGE rendering shared by databases with compatible single-row semantics. */
final class MergeRowUpsertSupport {

    private MergeRowUpsertSupport() {
    }

    static String renderOracleLike(
            AbstractJdbcDialect dialect,
            TableIdentifier target,
            List<JdbcUpsertColumn> columns,
            List<String> keyColumns,
            String databaseName
    ) {
        validate(columns, keyColumns, databaseName);
        rejectGeometry(columns, databaseName);
        String sourceColumns = columns.stream()
                .map(column -> "? AS " + dialect.quoteIdentifier(column.name()))
                .collect(Collectors.joining(", "));
        return renderMerge(
                dialect, target, columns, keyColumns,
                "(SELECT " + sourceColumns + " FROM DUAL) incoming",
                false
        );
    }

    static String renderSqlServer(
            AbstractJdbcDialect dialect,
            TableIdentifier target,
            List<JdbcUpsertColumn> columns,
            List<String> keyColumns
    ) {
        validate(columns, keyColumns, "SQL Server");
        rejectGeometry(columns, "SQL Server");
        String placeholders = columns.stream().map(column -> "?").collect(Collectors.joining(", "));
        String aliases = columns.stream().map(column -> dialect.quoteIdentifier(column.name()))
                .collect(Collectors.joining(", "));
        return renderMerge(
                dialect, target, columns, keyColumns,
                "(VALUES (" + placeholders + ")) AS incoming (" + aliases + ")",
                true
        );
    }

    private static String renderMerge(
            AbstractJdbcDialect dialect,
            TableIdentifier target,
            List<JdbcUpsertColumn> columns,
            List<String> keyColumns,
            String sourceSql,
            boolean sqlServer
    ) {
        Set<String> keys = Set.copyOf(keyColumns);
        String match = keyColumns.stream()
                .map(key -> "target_row." + dialect.quoteIdentifier(key)
                        + " = incoming." + dialect.quoteIdentifier(key))
                .collect(Collectors.joining(" AND "));
        List<JdbcUpsertColumn> updates = columns.stream()
                .filter(column -> !keys.contains(column.name()))
                .toList();
        String updateClause = updates.isEmpty() ? "" : " WHEN MATCHED THEN UPDATE SET "
                + updates.stream()
                .map(column -> "target_row." + dialect.quoteIdentifier(column.name())
                        + " = incoming." + dialect.quoteIdentifier(column.name()))
                .collect(Collectors.joining(", "));
        String names = columns.stream().map(column -> dialect.quoteIdentifier(column.name()))
                .collect(Collectors.joining(", "));
        String values = columns.stream().map(column -> "incoming." + dialect.quoteIdentifier(column.name()))
                .collect(Collectors.joining(", "));
        String targetSql = dialect.qualifiedName(target)
                + (sqlServer ? " WITH (HOLDLOCK) AS target_row" : " target_row");
        return "MERGE INTO " + targetSql + " USING " + sourceSql + " ON (" + match + ")"
                + updateClause + " WHEN NOT MATCHED THEN INSERT (" + names + ") VALUES (" + values + ")"
                + (sqlServer ? ";" : "");
    }

    private static void validate(
            List<JdbcUpsertColumn> columns,
            List<String> keyColumns,
            String databaseName
    ) {
        if (columns == null || columns.isEmpty() || keyColumns == null || keyColumns.isEmpty()) {
            throw new IllegalArgumentException(databaseName + " UPSERT 字段和 Key 不能为空");
        }
        Set<String> names = columns.stream().map(JdbcUpsertColumn::name).collect(Collectors.toSet());
        if (names.size() != columns.size() || !names.containsAll(keyColumns)) {
            throw new IllegalArgumentException(databaseName + " UPSERT Key 必须完整包含在目标字段中");
        }
    }

    private static void rejectGeometry(List<JdbcUpsertColumn> columns, String databaseName) {
        if (columns.stream().anyMatch(column -> column.geometrySpatialReferenceId() != null)) {
            throw new UnsupportedOperationException(databaseName + "当前未开放 Geometry UPSERT");
        }
    }
}
