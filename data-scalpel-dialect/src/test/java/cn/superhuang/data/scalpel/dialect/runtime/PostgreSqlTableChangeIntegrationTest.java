package cn.superhuang.data.scalpel.dialect.runtime;

import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.contract.type.PlatformTypeDefinition;
import cn.superhuang.data.scalpel.dialect.api.DatabaseDialect;
import cn.superhuang.data.scalpel.dialect.builtin.BuiltInDialects;
import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionConfig;
import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionFactory;
import cn.superhuang.data.scalpel.dialect.model.TableChangePlan;
import cn.superhuang.data.scalpel.dialect.model.TableChangeExecutionMode;
import cn.superhuang.data.scalpel.dialect.model.TableColumnDefinition;
import cn.superhuang.data.scalpel.dialect.model.TableColumnType;
import cn.superhuang.data.scalpel.dialect.model.TableDefinition;
import cn.superhuang.data.scalpel.dialect.model.TableIdentifier;
import cn.superhuang.data.scalpel.dialect.model.JdbcTypeDescriptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Opt-in smoke test for a real PostgreSQL instance.
 *
 * <p>The test is deliberately disabled by default. To run it, set
 * {@code DATASCALPEL_PG_INTEGRATION=true} and supply the connection variables documented in
 * {@code docs/design/model-physical-table-evolution.md}. It creates only randomly named tables
 * under the configured disposable schema and always removes them afterwards.</p>
 */
@EnabledIfEnvironmentVariable(named = "DATASCALPEL_PG_INTEGRATION", matches = "(?i)true")
class PostgreSqlTableChangeIntegrationTest {

    private static final String DATABASE_TYPE = "POSTGRESQL";
    private static final String DEFAULT_SCHEMA = "datascalpel_adapter_test";

    private final JdbcConnectionFactory connectionFactory = new JdbcConnectionFactory();
    private final DatabaseInspector inspector = new DatabaseInspector(BuiltInDialects.registry(), connectionFactory);
    private final DatabaseTableOperator tableOperator = new DatabaseTableOperator(BuiltInDialects.registry(), connectionFactory);
    private final DatabaseDialect dialect = BuiltInDialects.registry().require(DATABASE_TYPE);

    @Test
    void commitsSafeInPlaceChangeAndRejectsDataThatFailsPrechecks() throws SQLException, ClassNotFoundException {
        JdbcConnectionConfig config = integrationConfig();
        ensureSchema(config);

        String suffix = UUID.randomUUID().toString().replace("-", "");
        TableIdentifier successTable = new TableIdentifier(config.databaseName(), config.schemaName(), "model_change_ok_" + suffix);
        TableIdentifier blockedTable = new TableIdentifier(config.databaseName(), config.schemaName(), "model_change_blocked_" + suffix);
        TableIdentifier rebuildTable = new TableIdentifier(config.databaseName(), config.schemaName(), "model_rebuild_ok_" + suffix);
        TableIdentifier failedRebuildTable = new TableIdentifier(config.databaseName(), config.schemaName(), "model_rebuild_failed_" + suffix);
        TableIdentifier dependentRebuildTable = new TableIdentifier(config.databaseName(), config.schemaName(), "model_rebuild_dependency_" + suffix);

        try {
            assertSuccessfulChange(config, successTable);
            assertRejectedChangeKeepsOriginalStructure(config, blockedTable);
            assertSuccessfulRebuildCopiesData(config, rebuildTable);
            assertFailedRebuildRollsBackTheOriginalTable(config, failedRebuildTable);
            assertRebuildRejectsUnmanagedDependencies(config, dependentRebuildTable);
        } finally {
            dropTable(config, successTable);
            dropTable(config, blockedTable);
            dropTable(config, rebuildTable);
            dropTable(config, failedRebuildTable);
            dropTable(config, dependentRebuildTable);
        }
    }

    @Test
    void createsAndReadsBackSparkAlignedPlatformTypes() throws SQLException, ClassNotFoundException {
        JdbcConnectionConfig config = integrationConfig();
        ensureSchema(config);
        String suffix = UUID.randomUUID().toString().replace("-", "");
        TableIdentifier table = new TableIdentifier(
                config.databaseName(), config.schemaName(), "platform_types_" + suffix
        );
        Map<String, PlatformTypeDefinition> expectedReadTypes = new LinkedHashMap<>();
        expectedReadTypes.put("byte_value", PlatformTypeDefinition.of(PlatformDataType.SHORT));
        expectedReadTypes.put("short_value", PlatformTypeDefinition.of(PlatformDataType.SHORT));
        expectedReadTypes.put("integer_value", PlatformTypeDefinition.of(PlatformDataType.INTEGER));
        expectedReadTypes.put("long_value", PlatformTypeDefinition.of(PlatformDataType.LONG));
        expectedReadTypes.put("float_value", PlatformTypeDefinition.of(PlatformDataType.FLOAT));
        expectedReadTypes.put("double_value", PlatformTypeDefinition.of(PlatformDataType.DOUBLE));
        expectedReadTypes.put("decimal_value", PlatformTypeDefinition.decimal(38, 9));
        expectedReadTypes.put("bounded_text", PlatformTypeDefinition.string(128));
        expectedReadTypes.put("unbounded_text", PlatformTypeDefinition.string(null));
        expectedReadTypes.put("boolean_value", PlatformTypeDefinition.of(PlatformDataType.BOOLEAN));
        expectedReadTypes.put("binary_value", PlatformTypeDefinition.of(PlatformDataType.BINARY));
        expectedReadTypes.put("date_value", PlatformTypeDefinition.of(PlatformDataType.DATE));
        expectedReadTypes.put("timestamp_value", PlatformTypeDefinition.of(PlatformDataType.TIMESTAMP));
        expectedReadTypes.put("timestamp_ntz_value", PlatformTypeDefinition.of(PlatformDataType.TIMESTAMP_NTZ));

        try {
            List<TableColumnDefinition> columns = new java.util.ArrayList<>();
            columns.add(platformColumn("byte_value", PlatformTypeDefinition.of(PlatformDataType.BYTE)));
            for (var entry : expectedReadTypes.entrySet()) {
                if (!entry.getKey().equals("byte_value")) {
                    columns.add(platformColumn(entry.getKey(), entry.getValue()));
                }
            }
            tableOperator.createTable(DATABASE_TYPE, config, new TableDefinition(table, columns, List.of()));

            var metadata = inspector.readTable(DATABASE_TYPE, config, table);
            assertEquals(expectedReadTypes.size(), metadata.columns().size());
            metadata.columns().forEach(column -> {
                var mapping = dialect.mapToPlatformType(JdbcTypeDescriptor.from(column));
                assertTrue(mapping.acceptable(), () -> column.name() + ": " + mapping.message());
                assertEquals(expectedReadTypes.get(column.name()), mapping.definition(), column.name());
            });
        } finally {
            dropTable(config, table);
        }
    }

    private TableColumnDefinition platformColumn(String name, PlatformTypeDefinition platformType) {
        var mapping = dialect.mapToPhysicalType(platformType);
        assertTrue(mapping.acceptable(), mapping::message);
        return mapping.definition().column(name, true, null);
    }

    private void assertSuccessfulChange(JdbcConnectionConfig config, TableIdentifier table) throws SQLException, ClassNotFoundException {
        UUID columnId = UUID.randomUUID();
        TableDefinition before = stringDefinition(table, "remark", 64, true, columnId);
        TableDefinition target = stringDefinition(table, "note", 20, false, columnId);
        tableOperator.createTable(DATABASE_TYPE, config, before);
        insertString(config, table, "remark", "valid");

        TableChangePlan plan = dialect.planTableChange(before, target, inspector.readTable(DATABASE_TYPE, config, table));
        assertTrue(plan.allowsInPlaceExecution());

        tableOperator.executeTableChange(DATABASE_TYPE, config, plan, TableChangeExecutionMode.IN_PLACE);

        assertTrue(dialect.compareTable(target, inspector.readTable(DATABASE_TYPE, config, table)).compatible());
    }

    private void assertRejectedChangeKeepsOriginalStructure(JdbcConnectionConfig config, TableIdentifier table)
            throws SQLException, ClassNotFoundException {
        UUID columnId = UUID.randomUUID();
        TableDefinition before = stringDefinition(table, "remark", 64, true, columnId);
        TableDefinition target = stringDefinition(table, "note", 20, false, columnId);
        tableOperator.createTable(DATABASE_TYPE, config, before);
        insertString(config, table, "remark", "this value is deliberately longer than twenty characters");

        TableChangePlan plan = dialect.planTableChange(before, target, inspector.readTable(DATABASE_TYPE, config, table));
        DatabaseAccessException exception = assertThrows(
                DatabaseAccessException.class,
                () -> tableOperator.executeTableChange(DATABASE_TYPE, config, plan, TableChangeExecutionMode.IN_PLACE)
        );

        assertEquals("PRECHECK_FAILED", exception.code());
        assertTrue(dialect.compareTable(before, inspector.readTable(DATABASE_TYPE, config, table)).compatible());
    }

    private void assertSuccessfulRebuildCopiesData(JdbcConnectionConfig config, TableIdentifier table)
            throws SQLException, ClassNotFoundException {
        UUID columnId = UUID.randomUUID();
        TableDefinition before = stringDefinition(table, "event_text", 64, true, columnId);
        TableDefinition target = definition(table, "event_date", TableColumnType.DATE, null, true, columnId);
        tableOperator.createTable(DATABASE_TYPE, config, before);
        insertString(config, table, "event_text", "2026-07-14");

        TableChangePlan plan = dialect.planTableChange(before, target, inspector.readTable(DATABASE_TYPE, config, table));
        assertTrue(plan.allowsRebuildExecution());
        tableOperator.executeTableChange(DATABASE_TYPE, config, plan, TableChangeExecutionMode.REBUILD);

        assertTrue(dialect.compareTable(target, inspector.readTable(DATABASE_TYPE, config, table)).compatible());
        assertEquals("2026-07-14", readValue(config, table, "event_date"));
    }

    private void assertFailedRebuildRollsBackTheOriginalTable(JdbcConnectionConfig config, TableIdentifier table)
            throws SQLException, ClassNotFoundException {
        UUID columnId = UUID.randomUUID();
        TableDefinition before = stringDefinition(table, "event_text", 64, true, columnId);
        TableDefinition target = definition(table, "event_date", TableColumnType.DATE, null, true, columnId);
        tableOperator.createTable(DATABASE_TYPE, config, before);
        insertString(config, table, "event_text", "not-a-date");

        TableChangePlan plan = dialect.planTableChange(before, target, inspector.readTable(DATABASE_TYPE, config, table));
        DatabaseAccessException exception = assertThrows(
                DatabaseAccessException.class,
                () -> tableOperator.executeTableChange(DATABASE_TYPE, config, plan, TableChangeExecutionMode.REBUILD)
        );

        assertEquals("DATABASE_ERROR", exception.code());
        assertTrue(dialect.compareTable(before, inspector.readTable(DATABASE_TYPE, config, table)).compatible());
        assertEquals("not-a-date", readValue(config, table, "event_text"));
    }

    private void assertRebuildRejectsUnmanagedDependencies(JdbcConnectionConfig config, TableIdentifier table)
            throws SQLException, ClassNotFoundException {
        UUID columnId = UUID.randomUUID();
        TableDefinition before = stringDefinition(table, "event_text", 64, true, columnId);
        TableDefinition target = definition(table, "event_date", TableColumnType.DATE, null, true, columnId);
        tableOperator.createTable(DATABASE_TYPE, config, before);
        execute(config, "CREATE INDEX \"idx_" + table.table() + "\" ON " + qualifiedName(table) + " (\"event_text\")");

        TableChangePlan plan = dialect.planTableChange(before, target, inspector.readTable(DATABASE_TYPE, config, table));
        DatabaseAccessException exception = assertThrows(
                DatabaseAccessException.class,
                () -> tableOperator.executeTableChange(DATABASE_TYPE, config, plan, TableChangeExecutionMode.REBUILD)
        );

        assertEquals("PRECHECK_FAILED", exception.code());
        assertTrue(dialect.compareTable(before, inspector.readTable(DATABASE_TYPE, config, table)).compatible());
    }

    private static TableDefinition stringDefinition(
            TableIdentifier table,
            String columnName,
            int length,
            boolean nullable,
            UUID columnId
    ) {
        return definition(table, columnName, TableColumnType.STRING, length, nullable, columnId);
    }

    private static TableDefinition definition(
            TableIdentifier table,
            String columnName,
            TableColumnType type,
            Integer length,
            boolean nullable,
            UUID columnId
    ) {
        return new TableDefinition(
                table,
                List.of(new TableColumnDefinition(columnName, type, length, null, null, nullable, columnId)),
                List.of()
        );
    }

    private void ensureSchema(JdbcConnectionConfig config) throws SQLException, ClassNotFoundException {
        execute(config, "CREATE SCHEMA IF NOT EXISTS " + quoteIdentifier(config.schemaName()));
    }

    private void insertString(JdbcConnectionConfig config, TableIdentifier table, String column, String value)
            throws SQLException, ClassNotFoundException {
        execute(config, "INSERT INTO " + qualifiedName(table) + " (" + quoteIdentifier(column) + ") VALUES ('"
                + value.replace("'", "''") + "')");
    }

    private void dropTable(JdbcConnectionConfig config, TableIdentifier table) throws SQLException, ClassNotFoundException {
        execute(config, "DROP TABLE IF EXISTS " + qualifiedName(table));
    }

    private void execute(JdbcConnectionConfig config, String sql) throws SQLException, ClassNotFoundException {
        try (Connection connection = connectionFactory.open(dialect.createConnectionSpec(config));
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private String readValue(JdbcConnectionConfig config, TableIdentifier table, String column)
            throws SQLException, ClassNotFoundException {
        try (Connection connection = connectionFactory.open(dialect.createConnectionSpec(config));
             Statement statement = connection.createStatement();
             var resultSet = statement.executeQuery("SELECT " + quoteIdentifier(column) + " FROM " + qualifiedName(table))) {
            assertTrue(resultSet.next());
            return resultSet.getString(1);
        }
    }

    private static JdbcConnectionConfig integrationConfig() {
        return new JdbcConnectionConfig(
                requiredEnvironment("DATASCALPEL_PG_HOST"),
                integerEnvironment("DATASCALPEL_PG_PORT", 5432),
                requiredEnvironment("DATASCALPEL_PG_DATABASE"),
                System.getenv().getOrDefault("DATASCALPEL_PG_SCHEMA", DEFAULT_SCHEMA),
                requiredEnvironment("DATASCALPEL_PG_USERNAME"),
                requiredEnvironment("DATASCALPEL_PG_PASSWORD"),
                Map.of("sslmode", System.getenv().getOrDefault("DATASCALPEL_PG_SSLMODE", "disable"))
        );
    }

    private static String requiredEnvironment(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing integration-test environment variable: " + key);
        }
        return value;
    }

    private static int integerEnvironment(String key, int defaultValue) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? defaultValue : Integer.parseInt(value);
    }

    private static String qualifiedName(TableIdentifier table) {
        return quoteIdentifier(table.schema()) + "." + quoteIdentifier(table.table());
    }

    private static String quoteIdentifier(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
