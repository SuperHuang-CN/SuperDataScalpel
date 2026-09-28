package cn.superhuang.datascalpel.taskengine.contract;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class RuntimeJdbcConnectionTest {
    @Test
    void postgresHidesErrorValuesEvenWhenUrlOrPropertiesEnableThem() throws Exception {
        var original = Map.of("logServerErrorDetail", "true", "ApplicationName", "runner-test");
        var value = new RuntimeJdbcConnection("org.postgresql.Driver",
                "jdbc:postgresql://localhost/test?logServerErrorDetail=true&sslmode=require",
                "test", "public", "user", "secret", original);
        var effective = org.postgresql.Driver.parseURL(value.jdbcUrl(), new java.util.Properties());
        assertNotNull(effective);
        assertEquals("false", effective.getProperty("logServerErrorDetail"));
        assertEquals("require", effective.getProperty("sslmode"));
        assertEquals("false", value.properties().get("logServerErrorDetail"));
        assertEquals("runner-test", value.properties().get("ApplicationName"));
        assertEquals("true", original.get("logServerErrorDetail"));
    }

    @Test
    void addsPostgresSafetyWithoutChangingOtherDrivers() {
        var pg = new RuntimeJdbcConnection("org.postgresql.Driver", "jdbc:postgresql://localhost/test",
                "test", "public", "user", "secret", null);
        assertTrue(pg.jdbcUrl().endsWith("?logServerErrorDetail=false"));
        var mysql = new RuntimeJdbcConnection("com.mysql.cj.jdbc.Driver", "jdbc:mysql://localhost/test",
                "test", null, "user", "secret", null);
        assertEquals("jdbc:mysql://localhost/test", mysql.jdbcUrl());
        assertTrue(mysql.properties().isEmpty());
    }
}
