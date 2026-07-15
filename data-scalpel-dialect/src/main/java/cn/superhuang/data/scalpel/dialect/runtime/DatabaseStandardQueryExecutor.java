package cn.superhuang.data.scalpel.dialect.runtime;

import cn.superhuang.data.scalpel.dialect.api.DatabaseDialect;
import cn.superhuang.data.scalpel.dialect.api.DialectRegistry;
import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionConfig;
import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionFactory;
import cn.superhuang.data.scalpel.dialect.query.StandardQuery;
import cn.superhuang.data.scalpel.dialect.query.StandardQueryResult;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;

/**
 * Opens a short-lived read-only JDBC connection and executes one already-validated standard query.
 * This is suitable for administrative metadata/model queries; pooled service traffic has its own executor boundary.
 */
public final class DatabaseStandardQueryExecutor {

    private final DialectRegistry registry;
    private final JdbcConnectionFactory connectionFactory;
    private final JdbcStandardQueryExecutor queryExecutor = new JdbcStandardQueryExecutor();

    public DatabaseStandardQueryExecutor(DialectRegistry registry, JdbcConnectionFactory connectionFactory) {
        this.registry = registry;
        this.connectionFactory = connectionFactory;
    }

    public StandardQueryResult execute(
            String databaseType,
            JdbcConnectionConfig config,
            StandardQuery query,
            int maximumRows,
            Duration timeout
    ) {
        try {
            DatabaseDialect dialect = registry.require(databaseType);
            try (Connection connection = connectionFactory.open(dialect.createConnectionSpec(config))) {
                try {
                    connection.setReadOnly(true);
                } catch (SQLException ignored) {
                    // Some JDBC drivers do not expose a read-only connection mode.
                }
                return queryExecutor.execute(connection, dialect.compileStandardQuery(query), maximumRows, timeout);
            }
        } catch (DatabaseAccessException exception) {
            throw exception;
        } catch (ClassNotFoundException | LinkageError exception) {
            throw new DatabaseAccessException("DRIVER_NOT_AVAILABLE", "数据库驱动未安装", exception);
        } catch (IllegalArgumentException exception) {
            throw new DatabaseAccessException("INVALID_QUERY", exception.getMessage(), exception);
        } catch (SQLException exception) {
            throw new DatabaseAccessException(errorCode(exception), safeMessage(exception), exception);
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
}
