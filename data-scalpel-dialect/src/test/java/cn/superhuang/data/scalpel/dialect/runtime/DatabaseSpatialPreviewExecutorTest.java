package cn.superhuang.data.scalpel.dialect.runtime;

import cn.superhuang.data.scalpel.dialect.api.DialectRegistry;
import cn.superhuang.data.scalpel.dialect.builtin.PostgreSqlDialect;
import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionConfig;
import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionFactory;
import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionSpec;
import cn.superhuang.data.scalpel.dialect.model.SpatialPreviewColumn;
import cn.superhuang.data.scalpel.dialect.model.SpatialPreviewMetadata;
import cn.superhuang.data.scalpel.dialect.model.TableIdentifier;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseSpatialPreviewExecutorTest {
    private static final JdbcConnectionConfig CONFIG = new JdbcConnectionConfig(
            "db.internal", 5432, "data", "public", "user", "secret", Map.of());
    private static final TableIdentifier TABLE = new TableIdentifier("data", "public", "shapes");

    @Test
    void firstColdReadUsesBinaryWithoutChangingOrdinaryConnectionProperties() throws Exception {
        try (Fixture fixture = new Fixture()) {
            List<byte[]> values = new ArrayList<>();
            fixture.executor.stream("POSTGRESQL", CONFIG, TABLE, null, 1_000_000,
                    Duration.ofSeconds(120), values::add);
            assertEquals("-1", fixture.driver.lastProperties.getProperty("prepareThreshold"));
            assertEquals("extended", fixture.driver.lastProperties.getProperty("preferQueryMode"));
            assertEquals("true", fixture.driver.lastProperties.getProperty("binaryTransfer"));
            assertEquals("bytea", fixture.driver.lastProperties.getProperty("binaryTransferEnable"));
            assertEquals("", fixture.driver.lastProperties.getProperty("binaryTransferDisable"));
            assertEquals("33554432", fixture.driver.lastProperties.getProperty("maxResultBuffer"));
            assertEquals("false", fixture.driver.lastProperties.getProperty("adaptiveFetch"));
            assertEquals("require", fixture.driver.lastProperties.getProperty("sslmode"));
            assertEquals(List.of("readOnly", "readOnly", "repeatableRead", "autoCommitOff", "rollback", "close"),
                    fixture.driver.operations);
            assertArrayEquals(new byte[]{1, 2, 3}, values.getFirst());

            fixture.executor.inspect("POSTGRESQL", CONFIG, TABLE, List.of(), Duration.ofSeconds(5));
            assertEquals("5", fixture.driver.lastProperties.getProperty("prepareThreshold"));
            assertEquals("simple", fixture.driver.lastProperties.getProperty("preferQueryMode"));
            assertEquals("bytea", fixture.driver.lastProperties.getProperty("binaryTransferDisable"));
            assertNull(fixture.driver.lastProperties.getProperty("maxResultBuffer"));
        }
    }

    @Test
    void leavesOtherDriverPropertiesAlone() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.dialect.driverClass = RecordingDriver.class.getName();
            fixture.executor.stream("POSTGRESQL", CONFIG, TABLE, null, 1_000_000,
                    Duration.ofSeconds(120), ignored -> {});
            assertEquals("5", fixture.driver.lastProperties.getProperty("prepareThreshold"));
            assertNull(fixture.driver.lastProperties.getProperty("maxResultBuffer"));
        }
    }

    @Test
    void reportsBufferLimitAndPreservesOriginalFailureWhenRollbackAlsoFails() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.dialect.failure = new SQLException("Result set exceeded maxResultBuffer limit", "08S01");
            fixture.driver.failRollback = true;
            DatabaseAccessException result = assertThrows(DatabaseAccessException.class, () ->
                    fixture.executor.stream("POSTGRESQL", CONFIG, TABLE, null, 1_000_000,
                            Duration.ofSeconds(120), ignored -> {}));
            assertEquals("SPATIAL_PREVIEW_LIMIT_EXCEEDED", result.code());
            assertTrue(result.getMessage().contains("32 MiB"));
            assertSame(fixture.dialect.failure, result.getCause());
            assertEquals(1, result.getCause().getSuppressed().length);
            assertTrue(fixture.driver.operations.contains("close"));
        }
    }

    private static final class Fixture implements AutoCloseable {
        final RecordingDriver driver = new RecordingDriver();
        final PreviewDialect dialect = new PreviewDialect();
        final DatabaseSpatialPreviewExecutor executor = new DatabaseSpatialPreviewExecutor(
                new DialectRegistry(List.of(dialect)), new JdbcConnectionFactory());
        Fixture() throws SQLException { DriverManager.registerDriver(driver); }
        @Override public void close() throws SQLException { DriverManager.deregisterDriver(driver); }
    }

    private static final class PreviewDialect extends PostgreSqlDialect {
        String driverClass = "org.postgresql.Driver";
        SQLException failure;
        @Override public JdbcConnectionSpec createConnectionSpec(JdbcConnectionConfig config) {
            Properties properties = new Properties();
            properties.setProperty("prepareThreshold", "5");
            properties.setProperty("preferQueryMode", "simple");
            properties.setProperty("binaryTransferDisable", "bytea");
            properties.setProperty("sslmode", "require");
            return new JdbcConnectionSpec(driverClass, "jdbc:preview-test:shapes", properties, null);
        }
        @Override public SpatialPreviewMetadata inspectSpatialPreview(Connection connection, TableIdentifier table,
                List<SpatialPreviewColumn> columns, Duration timeout) {
            return new SpatialPreviewMetadata(true, null, List.of());
        }
        @Override public void streamSpatialPreview(Connection connection, TableIdentifier table,
                SpatialPreviewColumn column, int maximumRows, Duration timeout, Consumer<byte[]> consumer)
                throws SQLException {
            if (failure != null) throw failure;
            consumer.accept(new byte[]{1, 2, 3});
        }
    }

    public static final class RecordingDriver implements Driver {
        final List<String> operations = new ArrayList<>();
        Properties lastProperties;
        boolean failRollback;
        @Override public Connection connect(String url, Properties info) {
            if (!acceptsURL(url)) return null;
            lastProperties = new Properties(); lastProperties.putAll(info);
            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "setReadOnly" -> { assertEquals(true, args[0]); operations.add("readOnly"); }
                            case "setTransactionIsolation" -> {
                                assertEquals(Connection.TRANSACTION_REPEATABLE_READ, args[0]); operations.add("repeatableRead");
                            }
                            case "setAutoCommit" -> { assertEquals(false, args[0]); operations.add("autoCommitOff"); }
                            case "rollback" -> {
                                operations.add("rollback");
                                if (failRollback) throw new SQLException("Connection already closed");
                            }
                            case "close" -> operations.add("close");
                            default -> throw new AssertionError("Unexpected JDBC call " + method.getName());
                        }
                        return null;
                    });
        }
        @Override public boolean acceptsURL(String url) { return url.startsWith("jdbc:preview-test:"); }
        @Override public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) { return new DriverPropertyInfo[0]; }
        @Override public int getMajorVersion() { return 1; }
        @Override public int getMinorVersion() { return 0; }
        @Override public boolean jdbcCompliant() { return false; }
        @Override public Logger getParentLogger() { return Logger.getGlobal(); }
    }
}
