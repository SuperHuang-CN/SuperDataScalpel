package cn.superhuang.data.scalpel.dialect.runtime;

import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.contract.type.PlatformTypeDefinition;
import cn.superhuang.data.scalpel.dialect.api.DatabaseDialect;
import cn.superhuang.data.scalpel.dialect.builtin.BuiltInDialects;
import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionConfig;
import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionFactory;
import cn.superhuang.data.scalpel.dialect.query.CompiledSqlServiceQuery;
import cn.superhuang.data.scalpel.dialect.query.NamedParameterSqlCompiler;
import cn.superhuang.data.scalpel.dialect.query.ReadOnlySelectQueryParser;
import cn.superhuang.data.scalpel.dialect.query.SqlQueryParameter;
import cn.superhuang.data.scalpel.dialect.query.SqlQueryResult;
import cn.superhuang.data.scalpel.dialect.query.SqlTemplateCompilation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Opt-in verification of the SQL-service JDBC path against a real disposable PostgreSQL schema. */
@EnabledIfEnvironmentVariable(named = "DATASCALPEL_PG_INTEGRATION", matches = "(?i)true")
class PostgreSqlSqlServiceIntegrationTest {

    private static final String DATABASE_TYPE = "POSTGRESQL";
    private static final String DEFAULT_SCHEMA = "datascalpel_adapter_test";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final JdbcConnectionFactory connectionFactory = new JdbcConnectionFactory();
    private final DatabaseDialect dialect = BuiltInDialects.registry().require(DATABASE_TYPE);
    private final JdbcQueryInspector queryInspector = new JdbcQueryInspector(
            BuiltInDialects.registry(), connectionFactory
    );
    private final JdbcSqlQueryExecutor queryExecutor = new JdbcSqlQueryExecutor();

    @Test
    void describesAndExecutesParameterizedPageAndCountOnReadOnlyConnection()
            throws SQLException, ClassNotFoundException {
        JdbcConnectionConfig config = integrationConfig();
        String table = "sql_service_" + UUID.randomUUID().toString().replace("-", "");
        String qualifiedTable = quoteIdentifier(config.schemaName()) + "." + quoteIdentifier(table);
        ensureSchema(config);
        try {
            execute(config, """
                    CREATE TABLE %s (
                        id BIGINT PRIMARY KEY,
                        name VARCHAR(100),
                        department_id BIGINT NOT NULL,
                        amount NUMERIC(12, 2),
                        event_date DATE,
                        created_at TIMESTAMP WITHOUT TIME ZONE
                    )
                    """.formatted(qualifiedTable));
            execute(config, """
                    INSERT INTO %s (id, name, department_id, amount, event_date, created_at) VALUES
                        (1, 'Alice', 1001, 10.25, DATE '2026-07-15', TIMESTAMP '2026-07-15 08:15:00'),
                        (2, 'Bob',   1001, 20.50, DATE '2026-07-16', TIMESTAMP '2026-07-16 09:30:00'),
                        (3, 'Carol', 1002, 30.75, DATE '2026-07-17', TIMESTAMP '2026-07-17 10:45:00')
                    """.formatted(qualifiedTable));

            SqlTemplateCompilation compilation = NamedParameterSqlCompiler.compile("""
                    SELECT id, name, amount, event_date, created_at
                    FROM %s
                    WHERE department_id = :departmentId
                      AND (CAST(:keyword AS VARCHAR) IS NULL OR name ILIKE :keyword)
                    ORDER BY id
                    """.formatted(qualifiedTable));
            PlatformTypeDefinition longType = PlatformTypeDefinition.of(PlatformDataType.LONG);
            PlatformTypeDefinition stringType = PlatformTypeDefinition.string(100);
            List<SqlQueryParameter> nullKeywordBindings = List.of(
                    new SqlQueryParameter(1001L, longType),
                    new SqlQueryParameter(null, stringType),
                    new SqlQueryParameter(null, stringType)
            );
            CompiledSqlServiceQuery query = dialect.compileSqlServiceQuery(
                    compilation.jdbcSql(), nullKeywordBindings, 1, 1, true
            );

            var inspection = queryInspector.inspectPrepared(
                    DATABASE_TYPE,
                    config,
                    ReadOnlySelectQueryParser.parse(query.dataQuery().sql()),
                    query.dataQuery().parameters(),
                    TIMEOUT
            );
            assertEquals(
                    List.of("id", "name", "amount", "event_date", "created_at"),
                    inspection.columns().stream().map(column -> column.label()).toList()
            );
            assertTrue(inspection.columns().stream()
                    .map(column -> dialect.mapToPlatformType(column.jdbcTypeDescriptor()))
                    .allMatch(mapping -> mapping.acceptable() && mapping.definition() != null));

            try (Connection connection = connectionFactory.open(dialect.createConnectionSpec(config))) {
                connection.setReadOnly(true);
                assertTrue(connection.isReadOnly());
                SqlQueryResult result = queryExecutor.execute(connection, dialect, query, 1, TIMEOUT);
                assertEquals(2L, result.totalCount());
                assertEquals(1, result.rows().size());
                assertEquals(2L, result.rows().getFirst().get("id"));
                assertEquals("Bob", result.rows().getFirst().get("name"));
                assertEquals("20.50", result.rows().getFirst().get("amount"));
                assertEquals("2026-07-16", result.rows().getFirst().get("event_date"));
                assertEquals("2026-07-16T09:30", result.rows().getFirst().get("created_at"));
            }

            List<SqlQueryParameter> repeatedKeywordBindings = List.of(
                    new SqlQueryParameter(1001L, longType),
                    new SqlQueryParameter("%li%", stringType),
                    new SqlQueryParameter("%li%", stringType)
            );
            CompiledSqlServiceQuery filteredQuery = dialect.compileSqlServiceQuery(
                    compilation.jdbcSql(), repeatedKeywordBindings, 0, 20, false
            );
            try (Connection connection = connectionFactory.open(dialect.createConnectionSpec(config))) {
                connection.setReadOnly(true);
                SqlQueryResult result = queryExecutor.execute(connection, dialect, filteredQuery, 20, TIMEOUT);
                assertNull(result.totalCount());
                assertEquals(List.of("Alice"), result.rows().stream().map(row -> row.get("name")).toList());
            }
        } finally {
            execute(config, "DROP TABLE IF EXISTS " + qualifiedTable);
        }
    }

    private void ensureSchema(JdbcConnectionConfig config) throws SQLException, ClassNotFoundException {
        execute(config, "CREATE SCHEMA IF NOT EXISTS " + quoteIdentifier(config.schemaName()));
    }

    private void execute(JdbcConnectionConfig config, String sql) throws SQLException, ClassNotFoundException {
        try (Connection connection = connectionFactory.open(dialect.createConnectionSpec(config));
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
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

    private static String quoteIdentifier(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
