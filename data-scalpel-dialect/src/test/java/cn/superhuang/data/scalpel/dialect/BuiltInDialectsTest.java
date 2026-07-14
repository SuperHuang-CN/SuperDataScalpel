package cn.superhuang.data.scalpel.dialect;

import cn.superhuang.data.scalpel.dialect.api.DatabaseCapability;
import cn.superhuang.data.scalpel.dialect.api.DatabaseDialect;
import cn.superhuang.data.scalpel.dialect.api.DialectRegistry;
import cn.superhuang.data.scalpel.dialect.builtin.BuiltInDialects;
import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionConfig;
import cn.superhuang.data.scalpel.dialect.model.LogicalType;
import cn.superhuang.data.scalpel.dialect.model.TableIdentifier;
import org.junit.jupiter.api.Test;

import java.sql.Types;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        assertTrue(registry.all().stream().allMatch(dialect ->
                dialect.definition().capabilities().containsAll(java.util.EnumSet.allOf(DatabaseCapability.class))));
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
    void rejectsUnknownConnectionOptionsAndMapsJdbcTypes() {
        DatabaseDialect postgres = registry.require("POSTGRESQL");
        assertThrows(IllegalArgumentException.class, () ->
                postgres.createConnectionSpec(config(Map.of("unexpected", "value"))));
        assertEquals(LogicalType.JSON, postgres.logicalType(Types.OTHER, "jsonb"));
        assertEquals(LogicalType.INTEGER, postgres.logicalType(Types.BIGINT, "int8"));
        assertEquals(LogicalType.DATETIME, postgres.logicalType(Types.TIMESTAMP_WITH_TIMEZONE, "timestamptz"));
        assertFalse(postgres.definition().connectionOptions().isEmpty());
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
