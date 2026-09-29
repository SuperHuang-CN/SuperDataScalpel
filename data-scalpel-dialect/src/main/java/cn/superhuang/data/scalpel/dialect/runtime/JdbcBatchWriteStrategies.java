package cn.superhuang.data.scalpel.dialect.runtime;

import cn.superhuang.data.scalpel.dialect.api.DatabaseDialect;
import cn.superhuang.data.scalpel.dialect.model.TableIdentifier;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Explicit factory using the existing dialect identities; unverified products fail closed. */
public final class JdbcBatchWriteStrategies {
    private JdbcBatchWriteStrategies() { }

    public static boolean supports(String databaseType) {
        return Set.of("POSTGRESQL", "MYSQL", "ORACLE", "SQL_SERVER", "OPENGAUSS").contains(databaseType);
    }

    public static JdbcBatchWriteStrategy require(DatabaseDialect dialect) {
        return switch (dialect.definition().id()) {
            case "POSTGRESQL" -> new PostgreSql(dialect);
            case "MYSQL" -> new MySql(dialect);
            case "ORACLE", "SQL_SERVER", "OPENGAUSS" -> new Merge(dialect);
            default -> throw new UnsupportedOperationException("当前数据库尚未开放原子批量写入");
        };
    }

    private abstract static class Base implements JdbcBatchWriteStrategy {
        final DatabaseDialect dialect;
        Base(DatabaseDialect dialect) { this.dialect = dialect; }
        String q(String name) { return dialect.quoteIdentifier(name); }
        String table(TableIdentifier table) { return dialect.qualifiedName(table); }
        String columns(List<String> columns) { return columns.stream().map(this::q).collect(Collectors.joining(", ")); }
        @Override public void validateTarget(Connection connection, TableIdentifier target, boolean overwrite) throws SQLException {
            if (!connection.getMetaData().supportsTransactions()) {
                throw new SQLException("目标驱动不支持事务", "0A000");
            }
            var metadata = connection.getMetaData();
            String escape = metadata.getSearchStringEscape();
            String catalog = target.catalog() == null ? connection.getCatalog() : target.catalog();
            String schema = target.schema() == null ? connection.getSchema() : target.schema();
            try (var tables = metadata.getTables(catalog, pattern(schema, escape), pattern(target.table(), escape), new String[]{"TABLE", "PARTITIONED TABLE"})) {
                boolean physical = false;
                while (tables.next()) if (target.table().equals(tables.getString("TABLE_NAME"))) physical = true;
                if (!physical) throw new SQLException("原子批写需要可定位的本地物理表，不支持视图或外部表", "0A000");
            }
            if (overwrite) {
                // Keeping the target object does not protect referencing rows from DELETE actions.
                // Refuse cascade/set-null/set-default before staging or any target DML.
                try (var references = metadata.getExportedKeys(catalog, schema, target.table())) {
                    while (references.next()) {
                        short rule = references.getShort("DELETE_RULE");
                        if (references.wasNull() || (rule != java.sql.DatabaseMetaData.importedKeyNoAction
                                && rule != java.sql.DatabaseMetaData.importedKeyRestrict)) {
                            throw new SQLException("原子覆盖不允许引用外键的级联删除、置空或默认值副作用", "0A000");
                        }
                    }
                }
            }
        }
        private static String pattern(String value, String escape) {
            if (value == null || escape == null || escape.isEmpty()) return value;
            return value.replace(escape, escape + escape).replace("_", escape + "_").replace("%", escape + "%");
        }
        @Override public String createStage(TableIdentifier stage, TableIdentifier target, List<String> columns, String attemptColumn) {
            return "CREATE TABLE " + table(stage) + " AS SELECT " + columns(columns)
                    + ", CAST(NULL AS " + varchar() + ") AS " + q(attemptColumn)
                    + " FROM " + table(target) + " WHERE 1=0";
        }
        String varchar() { return "VARCHAR(36)"; }
        @Override public String createWinners(TableIdentifier winners, String attemptColumn) {
            return "CREATE TABLE " + table(winners) + " (" + q(attemptColumn) + " " + varchar() + " PRIMARY KEY)";
        }
        String insert(TableIdentifier target, List<String> columns, String selectedRows) {
            return "INSERT INTO " + table(target) + " (" + columns(columns) + ") " + selectedRows;
        }
    }

    private static final class PostgreSql extends Base {
        PostgreSql(DatabaseDialect dialect) { super(dialect); }
        @Override public String merge(TableIdentifier target, List<String> columns, List<String> keys, String selectedRows) {
            List<String> updates = columns.stream().filter(c -> !keys.contains(c)).toList();
            return insert(target, columns, selectedRows) + " ON CONFLICT (" + columns(keys) + ") "
                    + (updates.isEmpty() ? "DO NOTHING" : "DO UPDATE SET " + updates.stream()
                    .map(c -> q(c) + " = EXCLUDED." + q(c)).collect(Collectors.joining(", ")));
        }
    }

    private static final class MySql extends Base {
        MySql(DatabaseDialect dialect) { super(dialect); }
        @Override public void validateTarget(Connection connection, TableIdentifier target, boolean overwrite) throws SQLException {
            super.validateTarget(connection, target, overwrite);
            try (var statement = connection.prepareStatement(
                    "SELECT ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA=? AND TABLE_NAME=? AND TABLE_TYPE='BASE TABLE'")) {
                statement.setString(1, target.catalog() != null ? target.catalog()
                        : target.schema() != null ? target.schema() : connection.getCatalog());
                statement.setString(2, target.table());
                try (var result = statement.executeQuery()) {
                    if (!result.next() || !"InnoDB".equalsIgnoreCase(result.getString(1))) {
                        throw new SQLException("原子批写仅支持 MySQL InnoDB 物理表", "0A000");
                    }
                }
            }
        }
        @Override public String createStage(TableIdentifier stage, TableIdentifier target, List<String> columns, String attemptColumn) {
            return super.createStage(stage, target, columns, attemptColumn)
                    .replace(" AS SELECT ", " ENGINE=InnoDB AS SELECT ")
                    .replace("CAST(NULL AS VARCHAR(36))", "CAST(NULL AS CHAR(36))");
        }
        @Override public String createWinners(TableIdentifier winners, String attemptColumn) {
            return super.createWinners(winners, attemptColumn) + " ENGINE=InnoDB";
        }
        @Override public String merge(TableIdentifier target, List<String> columns, List<String> keys, String selectedRows) {
            List<String> updates = columns.stream().filter(c -> !keys.contains(c)).toList();
            if (updates.isEmpty()) updates = List.of(keys.getFirst());
            return insert(target, columns, selectedRows) + " ON DUPLICATE KEY UPDATE " + updates.stream()
                    .map(c -> q(c) + " = VALUES(" + q(c) + ")").collect(Collectors.joining(", "));
        }
    }

    private static final class Merge extends Base {
        Merge(DatabaseDialect dialect) { super(dialect); }
        boolean sqlServer() { return "SQL_SERVER".equals(dialect.definition().id()); }
        @Override String varchar() { return "ORACLE".equals(dialect.definition().id()) ? "VARCHAR2(36)" : super.varchar(); }
        @Override public String createStage(TableIdentifier stage, TableIdentifier target, List<String> columns, String attemptColumn) {
            if (!sqlServer()) return super.createStage(stage, target, columns, attemptColumn);
            // The join prevents SELECT INTO from copying an IDENTITY property.
            return "SELECT " + columns.stream().map(c -> "t." + q(c)).collect(Collectors.joining(", "))
                    + ", CAST(NULL AS VARCHAR(36)) AS " + q(attemptColumn) + " INTO " + table(stage)
                    + " FROM " + table(target) + " t CROSS JOIN (SELECT 1 AS n) x WHERE 1=0";
        }
        @Override public String merge(TableIdentifier target, List<String> columns, List<String> keys, String selectedRows) {
            List<String> updates = columns.stream().filter(c -> !keys.contains(c)).toList();
            return "MERGE INTO " + table(target) + (sqlServer() ? " WITH (HOLDLOCK)" : "") + " t USING ("
                    + selectedRows + ") s ON (" + keys.stream().map(c -> "t." + q(c) + " = s." + q(c))
                    .collect(Collectors.joining(" AND ")) + ")"
                    + (updates.isEmpty() ? "" : " WHEN MATCHED THEN UPDATE SET " + updates.stream()
                    .map(c -> (sqlServer() ? "" : "t.") + q(c) + " = s." + q(c)).collect(Collectors.joining(", ")))
                    + " WHEN NOT MATCHED THEN INSERT (" + columns(columns) + ") VALUES ("
                    + columns.stream().map(c -> "s." + q(c)).collect(Collectors.joining(", ")) + ")" + (sqlServer() ? ";" : "");
        }
    }
}
