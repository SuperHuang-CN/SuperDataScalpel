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

/**
 * Opt-in PostGIS acceptance test. The configured database/schema must be disposable and PostGIS
 * must already be installed; the test never creates extensions.
 */
@EnabledIfEnvironmentVariable(named = "DATASCALPEL_POSTGIS_INTEGRATION", matches = "(?i)true")
class PostGisGeometryIntegrationTest {

    private static final String DATABASE_TYPE = "POSTGRESQL";
    private static final String DEFAULT_SCHEMA = "datascalpel_adapter_test";

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
        ensureSchema(config);
        String suffix = UUID.randomUUID().toString().replace("-", "");
        TableIdentifier table = new TableIdentifier(
                config.databaseName(), config.schemaName(), "geometry_types_" + suffix
        );
        TableDefinition definition = definition(table, 4326);

        try {
            tableOperator.createTable(DATABASE_TYPE, config, definition);
            var metadata = inspector.readTable(DATABASE_TYPE, config, table);

            assertTrue(dialect.compareTable(definition, metadata).compatible());
            assertEquals(
                    definition.structureFingerprint(),
                    dialect.snapshotTableDefinition(metadata).structureFingerprint()
            );
            dialect.snapshotTableDefinition(metadata).columns()
                    .forEach(column -> assertNull(column.geometry()));
        } finally {
            dropTable(config, table);
        }
    }

    private static TableDefinition definition(TableIdentifier table, int epsg) {
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
                    new GeometryTypeDefinition(kind, CrsReference.epsg(epsg), CoordinateDimension.XY)
            ));
        }
        return new TableDefinition(table, columns, List.of());
    }

    private void ensureSchema(JdbcConnectionConfig config) throws SQLException, ClassNotFoundException {
        execute(config, "CREATE SCHEMA IF NOT EXISTS " + quoteIdentifier(config.schemaName()));
    }

    private void dropTable(JdbcConnectionConfig config, TableIdentifier table)
            throws SQLException, ClassNotFoundException {
        execute(config, "DROP TABLE IF EXISTS "
                + quoteIdentifier(table.schema()) + "." + quoteIdentifier(table.table()));
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
                requiredEnvironment("DATASCALPEL_POSTGIS_HOST"),
                integerEnvironment("DATASCALPEL_POSTGIS_PORT", 5432),
                requiredEnvironment("DATASCALPEL_POSTGIS_DATABASE"),
                System.getenv().getOrDefault("DATASCALPEL_POSTGIS_SCHEMA", DEFAULT_SCHEMA),
                requiredEnvironment("DATASCALPEL_POSTGIS_USERNAME"),
                requiredEnvironment("DATASCALPEL_POSTGIS_PASSWORD"),
                Map.of("sslmode", System.getenv().getOrDefault("DATASCALPEL_POSTGIS_SSLMODE", "disable"))
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

    private static String quoteIdentifier(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
