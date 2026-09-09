package cn.superhuang.data.scalpel.dialect.runtime;

import cn.superhuang.data.scalpel.dialect.api.DatabaseCapability;
import cn.superhuang.data.scalpel.dialect.api.DatabaseDialect;
import cn.superhuang.data.scalpel.dialect.api.DialectRegistry;
import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionConfig;
import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionFactory;
import cn.superhuang.data.scalpel.dialect.model.TableIdentifier;
import cn.superhuang.data.scalpel.dialect.query.InsertSelectQuery;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;

/** Executes only a dialect-rendered insert-select statement; it never accepts a complete SQL command from callers. */
public final class JdbcInsertSelectExecutor {

    private final DialectRegistry registry;
    private final JdbcConnectionFactory connectionFactory;

    public JdbcInsertSelectExecutor(DialectRegistry registry, JdbcConnectionFactory connectionFactory) {
        this.registry = registry;
        this.connectionFactory = connectionFactory;
    }

    public InsertSelectExecution execute(
            String databaseType,
            JdbcConnectionConfig config,
            TableIdentifier target,
            List<String> targetColumns,
            InsertSelectQuery query,
            boolean overwrite,
            Duration timeout
    ) {
        return execute(databaseType, config, target, targetColumns, query, overwrite, timeout, new JdbcExecutionCancellation());
    }

    public InsertSelectExecution execute(String databaseType, JdbcConnectionConfig config, TableIdentifier target,
                                         List<String> targetColumns, InsertSelectQuery query, boolean overwrite,
                                         Duration timeout, JdbcExecutionCancellation cancellation) {
        cancellation.check();
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("Execution timeout must be positive");
        }
        try {
            DatabaseDialect dialect = registry.require(databaseType);
            if (!dialect.definition().capabilities().contains(DatabaseCapability.INSERT_SELECT)) {
                throw new UnsupportedOperationException(dialect.definition().displayName() + " does not support insert-select tasks");
            }
            if (overwrite && !dialect.definition().capabilities().contains(DatabaseCapability.OVERWRITE_INSERT_SELECT)) {
                throw new UnsupportedOperationException(dialect.definition().displayName() + " does not support transactional task overwrite");
            }
            try (Connection connection = connectionFactory.open(dialect.createConnectionSpec(config))) {
                cancellation.register(connection);
                if (overwrite) {
                    return executeOverwrite(connection, dialect, target, targetColumns, query, timeout, cancellation);
                }
                return new InsertSelectExecution(executeStatement(
                        connection, dialect.renderInsertSelect(target, targetColumns, query), timeout, cancellation
                ));
            }
        } catch (DatabaseAccessException exception) {
            throw exception;
        } catch (ClassNotFoundException | LinkageError exception) {
            throw new DatabaseAccessException("DRIVER_NOT_AVAILABLE", "数据库驱动未安装", exception);
        } catch (IllegalArgumentException exception) {
            throw new DatabaseAccessException("INVALID_EXECUTION_REQUEST", exception.getMessage(), exception);
        } catch (UnsupportedOperationException exception) {
            throw new DatabaseAccessException("WRITE_MODE_UNSUPPORTED", exception.getMessage(), exception);
        } catch (SQLTimeoutException exception) {
            cancellation.check();
            throw new DatabaseAccessException("QUERY_TIMEOUT", "SQL 执行超时", exception);
        } catch (SQLException exception) {
            cancellation.check();
            if ("57014".equals(exception.getSQLState())) {
                // PostgreSQL reports a JDBC statement timeout as a generic PSQLException with
                // SQLSTATE query_canceled instead of SQLTimeoutException.
                throw new DatabaseAccessException("QUERY_TIMEOUT", "SQL 执行超时", exception);
            }
            throw new DatabaseAccessException("DATABASE_ERROR", "SQL 执行失败（SQLState: "
                    + (exception.getSQLState() == null ? "未知" : exception.getSQLState()) + "）", exception);
        } finally {
            cancellation.clear();
        }
    }

    private static InsertSelectExecution executeOverwrite(
            Connection connection,
            DatabaseDialect dialect,
            TableIdentifier target,
            List<String> targetColumns,
            InsertSelectQuery query,
            Duration timeout,
            JdbcExecutionCancellation cancellation
    ) throws SQLException {
        boolean originalAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            executeStatement(connection, dialect.renderOverwriteCleanup(target), timeout, cancellation);
            long affectedRows = executeStatement(connection, dialect.renderInsertSelect(target, targetColumns, query), timeout, cancellation);
            cancellation.check();
            connection.commit();
            return new InsertSelectExecution(affectedRows);
        } catch (SQLException | RuntimeException exception) {
            try {
                connection.rollback();
            } catch (SQLException rollbackException) {
                exception.addSuppressed(rollbackException);
            }
            throw exception;
        } finally {
            try {
                connection.setAutoCommit(originalAutoCommit);
            } catch (SQLException ignored) {
                // The connection is closing immediately after this controlled execution.
            }
        }
    }

    private static long executeStatement(Connection connection, String sql, Duration timeout, JdbcExecutionCancellation cancellation) throws SQLException {
        cancellation.check();
        try (Statement statement = connection.createStatement()) {
            cancellation.register(statement);
            try {
                statement.setQueryTimeout(Math.max(1, Math.toIntExact(timeout.toSeconds())));
            } catch (SQLException ignored) {
                // Some drivers do not support JDBC statement timeouts.
            }
            cancellation.check();
            return statement.executeUpdate(sql);
        } finally {
            cancellation.clearStatement();
        }
    }
}
