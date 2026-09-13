package cn.superhuang.datascalpel.taskengine.runner;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostgreSqlFamilySparkJdbcDialectTest {

    private final PostgreSqlFamilySparkJdbcDialect dialect = new PostgreSqlFamilySparkJdbcDialect();

    @Test
    void recognizesPostgresqlFamilyVendorUrls() {
        assertTrue(dialect.canHandle("jdbc:highgo://localhost/database"));
        assertTrue(dialect.canHandle("jdbc:opengauss://localhost/database"));
        assertTrue(dialect.canHandle("jdbc:kingbase8://localhost/database"));
        assertTrue(dialect.canHandle("JDBC:KINGBASE8://localhost/database"));
        assertFalse(dialect.canHandle("jdbc:mysql://localhost/database"));
        assertFalse(dialect.canHandle(null));
    }
}
