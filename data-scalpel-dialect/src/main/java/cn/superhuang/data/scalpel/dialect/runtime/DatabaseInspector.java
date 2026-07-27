package cn.superhuang.data.scalpel.dialect.runtime;

import cn.superhuang.data.scalpel.dialect.api.DatabaseDialect;
import cn.superhuang.data.scalpel.dialect.api.DialectRegistry;
import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionConfig;
import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionFactory;
import cn.superhuang.data.scalpel.dialect.model.ConnectionCheck;
import cn.superhuang.data.scalpel.dialect.model.NamespaceInfo;
import cn.superhuang.data.scalpel.dialect.model.TableIdentifier;
import cn.superhuang.data.scalpel.dialect.model.TableList;
import cn.superhuang.data.scalpel.dialect.model.TableMetadata;
import cn.superhuang.data.scalpel.dialect.model.TablePreview;
import cn.superhuang.data.scalpel.dialect.model.TableQuery;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.util.List;

public class DatabaseInspector {

    private final DialectRegistry registry;
    private final JdbcConnectionFactory connectionFactory;

    public DatabaseInspector(DialectRegistry registry, JdbcConnectionFactory connectionFactory) {
        this.registry = registry;
        this.connectionFactory = connectionFactory;
    }

    public boolean isDriverAvailable(String databaseType) {
        return connectionFactory.isDriverAvailable(registry.require(databaseType).driverClassName());
    }

    public ConnectionCheck test(String databaseType, JdbcConnectionConfig config) {
        long startedAt = System.nanoTime();
        try {
            DatabaseDialect dialect = registry.require(databaseType);
            try (Connection connection = connectionFactory.open(dialect.createConnectionSpec(config))) {
                validate(connection, dialect);
                DatabaseMetaData metadata = connection.getMetaData();
                return new ConnectionCheck(
                        true,
                        "SUCCESS",
                        "连接测试成功",
                        elapsedMs(startedAt),
                        metadata.getDatabaseProductName(),
                        metadata.getDatabaseProductVersion(),
                        metadata.getDriverName()
                );
            }
        } catch (ClassNotFoundException | LinkageError exception) {
            throw new DatabaseAccessException("DRIVER_NOT_AVAILABLE", "数据库驱动未安装", exception);
        } catch (IllegalArgumentException exception) {
            throw new DatabaseAccessException("INVALID_CONNECTION_CONFIG", exception.getMessage(), exception);
        } catch (SQLException exception) {
            throw new DatabaseAccessException(errorCode(exception), safeMessage(exception), exception);
        }
    }

    public List<NamespaceInfo> listNamespaces(String databaseType, JdbcConnectionConfig config) {
        return execute(databaseType, config, (connection, dialect) ->
                new JdbcMetadataReader(dialect).listNamespaces(connection, config));
    }

    public TableList listTables(String databaseType, JdbcConnectionConfig config, TableQuery query) {
        return execute(databaseType, config, (connection, dialect) ->
                new JdbcMetadataReader(dialect).listTables(connection, config, query));
    }

    public TableMetadata readTable(String databaseType, JdbcConnectionConfig config, TableIdentifier table) {
        return execute(databaseType, config, (connection, dialect) ->
                new JdbcMetadataReader(dialect).readTable(connection, table));
    }

    public TablePreview preview(String databaseType, JdbcConnectionConfig config, TableIdentifier table, int limit) {
        return execute(databaseType, config, (connection, dialect) ->
                new JdbcMetadataReader(dialect).preview(connection, table, limit));
    }

    private <T> T execute(String databaseType, JdbcConnectionConfig config, SqlOperation<T> operation) {
        try {
            DatabaseDialect dialect = registry.require(databaseType);
            try (Connection connection = connectionFactory.open(dialect.createConnectionSpec(config))) {
                try {
                    connection.setReadOnly(true);
                } catch (SQLException ignored) {
                    // Read-only mode is an optimization and is not supported by every JDBC driver.
                }
                return operation.execute(connection, dialect);
            }
        } catch (DatabaseAccessException exception) {
            throw exception;
        } catch (ClassNotFoundException | LinkageError exception) {
            throw new DatabaseAccessException("DRIVER_NOT_AVAILABLE", "数据库驱动未安装", exception);
        } catch (IllegalArgumentException exception) {
            throw new DatabaseAccessException("INVALID_CONNECTION_CONFIG", exception.getMessage(), exception);
        } catch (SQLException exception) {
            throw new DatabaseAccessException(errorCode(exception), safeMessage(exception), exception);
        }
    }

    private static void validate(Connection connection, DatabaseDialect dialect) throws SQLException {
        try {
            if (connection.isValid(5)) {
                return;
            }
        } catch (SQLFeatureNotSupportedException | AbstractMethodError ignored) {
            // Fall through to a lightweight validation query.
        }
        try (Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(5);
            statement.execute(dialect.validationQuery());
        }
    }

    private static String errorCode(SQLException exception) {
        String state = exception.getSQLState();
        if (state != null && state.startsWith("28")) {
            return "AUTHENTICATION_FAILED";
        }
        if (state != null && state.startsWith("3D")) {
            return "DATABASE_NOT_FOUND";
        }
        if (state != null && state.startsWith("08")) {
            return "NETWORK_ERROR";
        }
        return "DATABASE_ERROR";
    }

    private static String safeMessage(SQLException exception) {
        return switch (errorCode(exception)) {
            case "AUTHENTICATION_FAILED" -> "认证失败，请检查用户名和密码";
            case "DATABASE_NOT_FOUND" -> "数据库不存在或当前用户无权访问";
            case "NETWORK_ERROR" -> "连接失败，请检查主机、端口和网络";
            default -> "数据库访问失败（SQLState: " + (exception.getSQLState() == null ? "未知" : exception.getSQLState()) + "）";
        };
    }

    private static long elapsedMs(long startedAt) {
        return Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L);
    }

    @FunctionalInterface
    private interface SqlOperation<T> {
        T execute(Connection connection, DatabaseDialect dialect) throws SQLException;
    }
}
