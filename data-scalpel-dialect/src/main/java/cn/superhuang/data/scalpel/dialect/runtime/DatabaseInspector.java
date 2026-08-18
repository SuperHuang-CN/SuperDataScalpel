package cn.superhuang.data.scalpel.dialect.runtime;

import cn.superhuang.data.scalpel.dialect.api.DatabaseDialect;
import cn.superhuang.data.scalpel.dialect.api.DatabaseMetadataProvider;
import cn.superhuang.data.scalpel.dialect.api.DialectRegistry;
import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionConfig;
import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionFactory;
import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionSpec;
import cn.superhuang.data.scalpel.dialect.model.ConnectionCheck;
import cn.superhuang.data.scalpel.dialect.model.NamespaceInfo;
import cn.superhuang.data.scalpel.dialect.model.TableIdentifier;
import cn.superhuang.data.scalpel.dialect.model.TableList;
import cn.superhuang.data.scalpel.dialect.model.TableMetadata;
import cn.superhuang.data.scalpel.dialect.model.TablePhysicalStatistics;
import cn.superhuang.data.scalpel.dialect.model.TablePreview;
import cn.superhuang.data.scalpel.dialect.model.TableQuery;
import cn.superhuang.data.scalpel.dialect.model.TdEngineTmqTopic;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLTimeoutException;
import java.sql.Statement;
import java.time.Duration;
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
        return execute(databaseType, config, (connection, dialect) -> dialect instanceof DatabaseMetadataProvider provider
                ? provider.listNamespaces(connection, config)
                : new JdbcMetadataReader(dialect).listNamespaces(connection, config));
    }

    public TableList listTables(String databaseType, JdbcConnectionConfig config, TableQuery query) {
        return execute(databaseType, config, (connection, dialect) -> dialect instanceof DatabaseMetadataProvider provider
                ? provider.listTables(connection, config, query)
                : new JdbcMetadataReader(dialect).listTables(connection, config, query));
    }

    public List<TdEngineTmqTopic> listTdEngineTmqTopics(
            String databaseType,
            JdbcConnectionConfig config,
            String keyword
    ) {
        return execute(databaseType, config, (connection, dialect) ->
                new TdEngineTmqMetadataReader(dialect).list(connection, keyword));
    }

    public TableMetadata readTable(String databaseType, JdbcConnectionConfig config, TableIdentifier table) {
        return execute(databaseType, config, (connection, dialect) -> readTable(connection, dialect, table));
    }

    public TablePhysicalStatistics readTablePhysicalStatistics(
            String databaseType,
            JdbcConnectionConfig config,
            TableIdentifier table,
            Duration timeout
    ) {
        try {
            return execute(databaseType, config, (connection, dialect) ->
                    dialect.readTablePhysicalStatistics(connection, table, timeout));
        } catch (DatabaseAccessException exception) {
            if (exception.getCause() instanceof SQLTimeoutException) {
                throw new DatabaseAccessException("QUERY_TIMEOUT", "物理表统计查询超时", exception);
            }
            throw exception;
        }
    }

    /**
     * Reads one table through an already assembled, trusted runtime connection specification.
     * This keeps task manifests from having to reverse-parse a JDBC URL back into host/port fields.
     */
    public TableMetadata readTable(String databaseType, JdbcConnectionSpec spec, TableIdentifier table) {
        DatabaseDialect dialect = registry.require(databaseType);
        try (Connection connection = connectionFactory.open(spec)) {
            try {
                connection.setReadOnly(true);
            } catch (SQLException ignored) {
                // Read-only mode is an optimization and is not supported by every JDBC driver.
            }
            return readTable(connection, dialect, table);
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

    public TablePreview preview(String databaseType, JdbcConnectionConfig config, TableIdentifier table, int limit) {
        return execute(databaseType, config, (connection, dialect) -> {
            if (dialect instanceof DatabaseMetadataProvider) {
                // The provider verifies that the requested object belongs to its supported resource boundary.
                readTable(connection, dialect, table);
            }
            return new JdbcMetadataReader(dialect).preview(connection, table, limit);
        });
    }

    private static TableMetadata readTable(
            Connection connection,
            DatabaseDialect dialect,
            TableIdentifier table
    ) throws SQLException {
        return dialect instanceof DatabaseMetadataProvider provider
                ? provider.readTableMetadata(connection, table)
                : new JdbcMetadataReader(dialect).readTable(connection, table);
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
