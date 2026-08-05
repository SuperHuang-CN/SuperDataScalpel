package cn.superhuang.data.scalpel.dialect;

import cn.superhuang.data.scalpel.dialect.api.DatabaseCapability;
import cn.superhuang.data.scalpel.dialect.api.DatabaseDialect;
import cn.superhuang.data.scalpel.dialect.api.DialectRegistry;
import cn.superhuang.data.scalpel.dialect.builtin.BuiltInDialects;
import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionConfig;
import cn.superhuang.data.scalpel.dialect.model.LogicalType;
import cn.superhuang.data.scalpel.dialect.model.ColumnMetadata;
import cn.superhuang.data.scalpel.dialect.model.JdbcUpsertColumn;
import cn.superhuang.data.scalpel.dialect.model.PrimaryKeyMetadata;
import cn.superhuang.data.scalpel.dialect.model.TableColumnDefinition;
import cn.superhuang.data.scalpel.dialect.model.TableColumnType;
import cn.superhuang.data.scalpel.dialect.model.TableDefinition;
import cn.superhuang.data.scalpel.dialect.model.TableIdentifier;
import cn.superhuang.data.scalpel.dialect.model.TableMetadata;
import cn.superhuang.data.scalpel.dialect.model.TableStructureDifferenceType;
import cn.superhuang.data.scalpel.dialect.model.TableSummary;
import org.junit.jupiter.api.Test;

import java.sql.Types;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuiltInDialectsTest {

    private final DialectRegistry registry = BuiltInDialects.registry();

    @Test
    void registersAllSupportedDatabaseTypesWithMetadataCapabilities() {
        assertEquals(8, registry.all().size());
        assertEquals(
                java.util.Set.of("POSTGRESQL", "MYSQL", "ORACLE", "SQL_SERVER", "CLICKHOUSE", "DAMENG", "KINGBASE", "OPENGAUSS"),
                registry.all().stream().map(dialect -> dialect.definition().id()).collect(java.util.stream.Collectors.toSet())
        );
        assertTrue(registry.require("POSTGRESQL").definition().capabilities().contains(DatabaseCapability.CREATE_TABLE));
        assertTrue(registry.require("MYSQL").definition().capabilities().contains(DatabaseCapability.CREATE_TABLE));
        assertTrue(registry.require("CLICKHOUSE").definition().capabilities().contains(DatabaseCapability.CREATE_TABLE));
        assertFalse(registry.require("ORACLE").definition().capabilities().contains(DatabaseCapability.CREATE_TABLE));
    }

    @Test
    void buildsVendorSpecificUrlsWithoutPuttingThePasswordInTheUrl() {
        JdbcConnectionConfig config = config(Map.of());

        assertEquals("jdbc:postgresql://db.internal:5432/business", registry.require("POSTGRESQL").createConnectionSpec(config).jdbcUrl());
        assertEquals("jdbc:mysql://db.internal:5432/business", registry.require("MYSQL").createConnectionSpec(config).jdbcUrl());
        assertEquals("jdbc:oracle:thin:@//db.internal:5432/business", registry.require("ORACLE").createConnectionSpec(config).jdbcUrl());
        assertEquals("jdbc:sqlserver://db.internal:5432", registry.require("SQL_SERVER").createConnectionSpec(config).jdbcUrl());
        assertEquals("jdbc:clickhouse:http://db.internal:5432/business", registry.require("CLICKHOUSE").createConnectionSpec(config).jdbcUrl());
        assertEquals("jdbc:dm://db.internal:5432/business", registry.require("DAMENG").createConnectionSpec(config).jdbcUrl());
        assertEquals("jdbc:kingbase8://db.internal:5432/business", registry.require("KINGBASE").createConnectionSpec(config).jdbcUrl());
        assertEquals("jdbc:opengauss://db.internal:5432/business", registry.require("OPENGAUSS").createConnectionSpec(config).jdbcUrl());

        assertEquals("sales", registry.require("POSTGRESQL").createConnectionSpec(config).schemaName());
        assertEquals("sales", registry.require("ORACLE").createConnectionSpec(config).schemaName());
        assertEquals("sales", registry.require("DAMENG").createConnectionSpec(config).schemaName());
        assertEquals("sales", registry.require("KINGBASE").createConnectionSpec(config).schemaName());
        assertEquals("sales", registry.require("OPENGAUSS").createConnectionSpec(config).schemaName());
        assertNull(registry.require("MYSQL").createConnectionSpec(config).schemaName());
        assertNull(registry.require("CLICKHOUSE").createConnectionSpec(config).schemaName());
        assertNull(registry.require("SQL_SERVER").createConnectionSpec(config).schemaName());

        assertTrue(registry.all().stream().allMatch(dialect ->
                !dialect.createConnectionSpec(config).jdbcUrl().contains("do-not-leak")));
    }

    @Test
    void handlesOracleSidAndDatabaseSpecificPreviewSyntax() {
        JdbcConnectionConfig oracleSid = config(Map.of("connectionMode", "SID"));
        assertEquals("jdbc:oracle:thin:@db.internal:5432:business", registry.require("ORACLE").createConnectionSpec(oracleSid).jdbcUrl());

        TableIdentifier table = new TableIdentifier("business", "sales", "order");
        assertEquals("SELECT * FROM \"sales\".\"order\" LIMIT 11", registry.require("POSTGRESQL").previewSql(table, 11));
        assertEquals("SELECT TOP (11) * FROM [business].[sales].[order]", registry.require("SQL_SERVER").previewSql(table, 11));
        assertEquals("SELECT * FROM \"sales\".\"order\" WHERE ROWNUM <= 11", registry.require("DAMENG").previewSql(table, 11));
    }

    @Test
    void passesCustomConnectionOptionsThroughPropertiesWithoutChangingTheUrl() {
        DatabaseDialect postgres = registry.require("POSTGRESQL");
        var spec = postgres.createConnectionSpec(config(Map.of(
                "sslmode", "REQUIRE",
                "tcpKeepAlive", "true"
        )));

        assertEquals("jdbc:postgresql://db.internal:5432/business", spec.jdbcUrl());
        assertEquals("require", spec.properties().getProperty("sslmode"));
        assertEquals("true", spec.properties().getProperty("tcpKeepAlive"));
        assertEquals("reader", spec.properties().getProperty("user"));
        assertEquals("do-not-leak", spec.properties().getProperty("password"));

        assertEquals(LogicalType.JSON, postgres.logicalType(Types.OTHER, "jsonb"));
        assertEquals(LogicalType.INTEGER, postgres.logicalType(Types.BIGINT, "int8"));
        assertEquals(LogicalType.DATETIME, postgres.logicalType(Types.TIMESTAMP_WITH_TIMEZONE, "timestamptz"));
        assertFalse(postgres.definition().connectionOptions().isEmpty());
    }

    @Test
    void rejectsProtectedSensitiveAndInvalidTypedConnectionOptions() {
        DatabaseDialect postgres = registry.require("POSTGRESQL");

        assertThrows(IllegalArgumentException.class, () ->
                postgres.createConnectionSpec(config(Map.of("connectTimeout", "60"))));
        assertThrows(IllegalArgumentException.class, () ->
                postgres.createConnectionSpec(config(Map.of("apiToken", "do-not-store"))));
        assertThrows(IllegalArgumentException.class, () ->
                postgres.createConnectionSpec(config(Map.of("apiKey", "do-not-store"))));
        assertThrows(IllegalArgumentException.class, () ->
                postgres.createConnectionSpec(config(Map.of("sslmode", "unexpected"))));
        assertThrows(IllegalArgumentException.class, () ->
                registry.require("MYSQL").createConnectionSpec(config(Map.of("useSSL", "sometimes"))));
        assertThrows(IllegalArgumentException.class, () ->
                postgres.createConnectionSpec(config(Map.of("tcpKeepAlive", ""))));
    }

    @Test
    void consumesOracleConnectionModeWithoutPassingItToTheDriver() {
        var spec = registry.require("ORACLE").createConnectionSpec(config(Map.of(
                "connectionMode", "sid",
                "oracle.net.keepAlive", "true"
        )));

        assertEquals("jdbc:oracle:thin:@db.internal:5432:business", spec.jdbcUrl());
        assertNull(spec.properties().getProperty("connectionMode"));
        assertEquals("true", spec.properties().getProperty("oracle.net.keepAlive"));
    }

    @Test
    void rendersControlledCreateTableSqlForPostgresAndMySqlOnly() {
        TableDefinition definition = new TableDefinition(
                new TableIdentifier("warehouse", "public", "order_fact"),
                java.util.List.of(
                        new TableColumnDefinition("order_id", TableColumnType.LONG, null, null, null, false),
                        new TableColumnDefinition("amount", TableColumnType.DECIMAL, null, 18, 2, true),
                        new TableColumnDefinition("remark", TableColumnType.STRING, 120, null, null, true)
                ),
                java.util.List.of("order_id")
        );

        assertEquals(
                "CREATE TABLE \"public\".\"order_fact\" (\"order_id\" bigint NOT NULL, \"amount\" numeric(18,2), \"remark\" varchar(120), PRIMARY KEY (\"order_id\"))",
                registry.require("POSTGRESQL").planCreateTable(definition).statements().getFirst()
        );
        assertEquals(
                "CREATE TABLE `warehouse`.`order_fact` (`order_id` bigint NOT NULL, `amount` decimal(18,2), `remark` varchar(120), PRIMARY KEY (`order_id`))",
                registry.require("MYSQL").planCreateTable(definition).statements().getFirst()
        );
        assertThrows(UnsupportedOperationException.class, () -> registry.require("ORACLE").planCreateTable(definition));
    }

    @Test
    void rendersControlledPostgresAndMySqlRowUpsertSql() {
        TableIdentifier table = new TableIdentifier("warehouse", "public", "order_fact");
        var columns = java.util.List.of(
                new JdbcUpsertColumn("tenant_id", null),
                new JdbcUpsertColumn("order_no", null),
                new JdbcUpsertColumn("amount", null)
        );
        var keys = java.util.List.of("tenant_id", "order_no");

        assertEquals(
                "INSERT INTO \"public\".\"order_fact\" (\"tenant_id\", \"order_no\", \"amount\") "
                        + "VALUES (?, ?, ?) ON CONFLICT (\"tenant_id\", \"order_no\") "
                        + "DO UPDATE SET \"amount\" = EXCLUDED.\"amount\"",
                registry.require("POSTGRESQL").renderRowUpsert(table, columns, keys)
        );
        assertEquals(
                "INSERT INTO `warehouse`.`order_fact` (`tenant_id`, `order_no`, `amount`) "
                        + "VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE `amount` = VALUES(`amount`)",
                registry.require("MYSQL").renderRowUpsert(table, columns, keys)
        );
        assertEquals(
                "INSERT INTO \"public\".\"order_fact\" (\"tenant_id\", \"order_no\") "
                        + "VALUES (?, ?) ON CONFLICT (\"tenant_id\", \"order_no\") DO NOTHING",
                registry.require("POSTGRESQL").renderRowUpsert(table, columns.subList(0, 2), keys)
        );
        assertEquals(
                "INSERT INTO `warehouse`.`order_fact` (`tenant_id`, `order_no`) VALUES (?, ?) "
                        + "ON DUPLICATE KEY UPDATE `tenant_id` = `tenant_id`",
                registry.require("MYSQL").renderRowUpsert(table, columns.subList(0, 2), keys)
        );
        assertThrows(
                UnsupportedOperationException.class,
                () -> registry.require("ORACLE").renderRowUpsert(table, columns, keys)
        );
    }

    @Test
    void comparesPhysicalTableStructureWithoutDependingOnBusinessModels() {
        TableIdentifier table = new TableIdentifier("warehouse", "public", "order_fact");
        TableDefinition definition = new TableDefinition(
                table,
                java.util.List.of(
                        new TableColumnDefinition("order_id", TableColumnType.LONG, null, null, null, false),
                        new TableColumnDefinition("title", TableColumnType.STRING, 120, null, null, true),
                        new TableColumnDefinition("amount", TableColumnType.DECIMAL, null, 18, 2, true)
                ),
                java.util.List.of("order_id")
        );
        TableMetadata matched = new TableMetadata(
                new TableSummary(table, "TABLE", null),
                java.util.List.of(
                        column("order_id", 1, Types.BIGINT, "int8", null, null, null, false),
                        column("title", 2, Types.VARCHAR, "varchar", 120, null, null, true),
                        column("amount", 3, Types.NUMERIC, "numeric", null, 18, 2, true)
                ),
                new PrimaryKeyMetadata("pk_order_fact", java.util.List.of("order_id")),
                java.util.List.of()
        );
        assertTrue(registry.require("POSTGRESQL").compareTable(definition, matched).compatible());
        assertEquals(
                definition.structureFingerprint(),
                registry.require("POSTGRESQL").snapshotTableDefinition(matched).structureFingerprint()
        );

        TableMetadata drifted = new TableMetadata(
                matched.table(),
                java.util.List.of(
                        column("order_id", 1, Types.BIGINT, "int8", null, null, null, false),
                        column("title", 2, Types.VARCHAR, "varchar", 100, null, null, true),
                        column("amount", 3, Types.NUMERIC, "numeric", null, 18, 2, true),
                        column("unexpected", 4, Types.INTEGER, "int4", null, null, null, true)
                ),
                new PrimaryKeyMetadata("pk_order_fact", java.util.List.of("order_id")),
                java.util.List.of()
        );
        var differences = registry.require("POSTGRESQL").compareTable(definition, drifted).differences();
        assertTrue(differences.stream().anyMatch(item -> item.type() == TableStructureDifferenceType.LENGTH_MISMATCH));
        assertTrue(differences.stream().anyMatch(item -> item.type() == TableStructureDifferenceType.EXTRA_COLUMN));
    }

    private static ColumnMetadata column(
            String name,
            int ordinal,
            int jdbcType,
            String nativeType,
            Integer length,
            Integer precision,
            Integer scale,
            boolean nullable
    ) {
        return new ColumnMetadata(
                name, ordinal, jdbcType, nativeType, LogicalType.OTHER,
                length, precision, scale, nullable, null, false, false, null
        );
    }

    private static JdbcConnectionConfig config(Map<String, String> options) {
        return new JdbcConnectionConfig(
                "db.internal",
                5432,
                "business",
                "sales",
                "reader",
                "do-not-leak",
                options
        );
    }
}
