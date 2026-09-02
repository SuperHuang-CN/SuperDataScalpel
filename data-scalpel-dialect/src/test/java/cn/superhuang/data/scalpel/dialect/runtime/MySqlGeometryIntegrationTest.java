package cn.superhuang.data.scalpel.dialect.runtime;

import cn.superhuang.data.scalpel.contract.type.CoordinateDimension;
import cn.superhuang.data.scalpel.contract.type.CrsReference;
import cn.superhuang.data.scalpel.contract.type.GeometryKind;
import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;
import cn.superhuang.data.scalpel.dialect.api.DatabaseDialect;
import cn.superhuang.data.scalpel.dialect.builtin.BuiltInDialects;
import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionConfig;
import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionFactory;
import cn.superhuang.data.scalpel.dialect.model.TableColumnDefinition;
import cn.superhuang.data.scalpel.dialect.model.TableColumnType;
import cn.superhuang.data.scalpel.dialect.model.TableDefinition;
import cn.superhuang.data.scalpel.dialect.model.TableIdentifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Opt-in acceptance test for a disposable MySQL 8.x database. */
@EnabledIfEnvironmentVariable(named = "DATASCALPEL_MYSQL8_INTEGRATION", matches = "(?i)true")
class MySqlGeometryIntegrationTest {

    private static final String DATABASE_TYPE = "MYSQL";

    private final JdbcConnectionFactory connectionFactory = new JdbcConnectionFactory();
    private final DatabaseDialect dialect = BuiltInDialects.registry().require(DATABASE_TYPE);
    private final DatabaseInspector inspector =
            new DatabaseInspector(BuiltInDialects.registry(), connectionFactory);
    private final DatabaseTableOperator tableOperator =
            new DatabaseTableOperator(BuiltInDialects.registry(), connectionFactory);

    @Test
    void createsGenericGeometryColumnsAndMatchesTheirPhysicalType()
            throws SQLException, ClassNotFoundException {
        JdbcConnectionConfig config = integrationConfig();
        String suffix = UUID.randomUUID().toString().replace("-", "");
        TableIdentifier managedTable = new TableIdentifier(
                config.databaseName(), null, "geometry_types_" + suffix
        );
        TableDefinition definition = definition(managedTable);

        try {
            tableOperator.createTable(DATABASE_TYPE, config, definition);
            var metadata = inspector.readTable(DATABASE_TYPE, config, managedTable);

            assertTrue(dialect.compareTable(definition, metadata).compatible());
            assertEquals(
                    definition.structureFingerprint(),
                    dialect.snapshotTableDefinition(metadata).structureFingerprint()
            );
            dialect.snapshotTableDefinition(metadata).columns()
                    .forEach(column -> assertNull(column.geometry()));
        } finally {
            dropTable(config, managedTable);
        }
    }

    private static TableDefinition definition(TableIdentifier table) {
        List<TableColumnDefinition> columns = new ArrayList<>();
        for (GeometryKind kind : GeometryKind.values()) {
            columns.add(new TableColumnDefinition(
                    kind.name().toLowerCase(java.util.Locale.ROOT),
                    TableColumnType.GEOMETRY,
                    null,
                    null,
                    null,
                    true,
                    null,
                    new GeometryTypeDefinition(kind, CrsReference.epsg(4326), CoordinateDimension.XY)
            ));
        }
        return new TableDefinition(table, columns, List.of());
    }

    private void dropTable(JdbcConnectionConfig config, TableIdentifier table)
            throws SQLException, ClassNotFoundException {
        execute(config, "DROP TABLE IF EXISTS " + qualified(table));
    }

    private void execute(JdbcConnectionConfig config, String sql)
            throws SQLException, ClassNotFoundException {
        try (Connection connection = connectionFactory.open(dialect.createConnectionSpec(config));
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static JdbcConnectionConfig integrationConfig() {
        return new JdbcConnectionConfig(
                requiredEnvironment("DATASCALPEL_MYSQL8_HOST"),
                integerEnvironment("DATASCALPEL_MYSQL8_PORT", 3306),
                requiredEnvironment("DATASCALPEL_MYSQL8_DATABASE"),
                null,
                requiredEnvironment("DATASCALPEL_MYSQL8_USERNAME"),
                requiredEnvironment("DATASCALPEL_MYSQL8_PASSWORD"),
                Map.of(
                        "useSSL", System.getenv().getOrDefault("DATASCALPEL_MYSQL8_USE_SSL", "false"),
                        "serverTimezone", System.getenv().getOrDefault("DATASCALPEL_MYSQL8_TIMEZONE", "UTC")
                )
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

    private static String qualified(TableIdentifier table) {
        return quoteIdentifier(table.catalog()) + "." + quoteIdentifier(table.table());
    }

    private static String quoteIdentifier(String value) {
        return "`" + value.replace("`", "``") + "`";
    }
}
