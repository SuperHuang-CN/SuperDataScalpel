package cn.superhuang.data.scalpel.dialect.connection;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.util.Properties;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcConnectionFactoryTest {

    private final JdbcConnectionFactory factory = new JdbcConnectionFactory();

    @Test
    void appliesTheConfiguredSessionSchemaBeforeReturningTheConnection() throws Exception {
        RecordingDriver driver = new RecordingDriver();
        DriverManager.registerDriver(driver);
        try {
            try (Connection ignored = factory.open(new JdbcConnectionSpec(
                    RecordingDriver.class.getName(), "jdbc:recording:schema", new Properties(), "task_schema"
            ))) {
                assertEquals("task_schema", driver.schema());
                assertFalse(driver.closed());
            }
            assertTrue(driver.closed());
        } finally {
            DriverManager.deregisterDriver(driver);
        }
    }

    private static final class RecordingDriver implements Driver {

        private String schema;
        private boolean closed;
        private final Connection connection = (Connection) Proxy.newProxyInstance(
                RecordingDriver.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "setSchema" -> {
                        schema = (String) arguments[0];
                        yield null;
                    }
                    case "close" -> {
                        closed = true;
                        yield null;
                    }
                    case "isClosed" -> closed;
                    case "equals" -> proxy == arguments[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "recording JDBC connection";
                    default -> defaultValue(method.getReturnType());
                }
        );

        @Override
        public Connection connect(String url, Properties info) {
            return acceptsURL(url) ? connection : null;
        }

        @Override
        public boolean acceptsURL(String url) {
            return url != null && url.startsWith("jdbc:recording:");
        }

        @Override
        public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) {
            return new DriverPropertyInfo[0];
        }

        @Override
        public int getMajorVersion() {
            return 1;
        }

        @Override
        public int getMinorVersion() {
            return 0;
        }

        @Override
        public boolean jdbcCompliant() {
            return false;
        }

        @Override
        public Logger getParentLogger() {
            return Logger.getGlobal();
        }

        String schema() {
            return schema;
        }

        boolean closed() {
            return closed;
        }

        private static Object defaultValue(Class<?> returnType) {
            if (!returnType.isPrimitive()) {
                return null;
            }
            if (returnType == boolean.class) {
                return false;
            }
            if (returnType == char.class) {
                return '\0';
            }
            if (returnType == byte.class) {
                return (byte) 0;
            }
            if (returnType == short.class) {
                return (short) 0;
            }
            if (returnType == int.class) {
                return 0;
            }
            if (returnType == long.class) {
                return 0L;
            }
            if (returnType == float.class) {
                return 0F;
            }
            if (returnType == double.class) {
                return 0D;
            }
            throw new IllegalArgumentException("Unsupported primitive return type: " + returnType);
        }
    }
}
