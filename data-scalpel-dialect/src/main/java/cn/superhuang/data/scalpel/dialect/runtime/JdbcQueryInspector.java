package cn.superhuang.data.scalpel.dialect.runtime;

import cn.superhuang.data.scalpel.dialect.api.DatabaseCapability;
import cn.superhuang.data.scalpel.dialect.api.DatabaseDialect;
import cn.superhuang.data.scalpel.dialect.api.DialectRegistry;
import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionConfig;
import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionFactory;
import cn.superhuang.data.scalpel.dialect.query.InsertSelectQuery;
import cn.superhuang.data.scalpel.dialect.query.QueryInspection;
import cn.superhuang.data.scalpel.dialect.query.SqlQueryParameter;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;

/** Reads output metadata for an already-validated read-only query without accepting arbitrary SQL text. */
public final class JdbcQueryInspector {

    private final DialectRegistry registry;
    private final JdbcConnectionFactory connectionFactory;

    public JdbcQueryInspector(DialectRegistry registry, JdbcConnectionFactory connectionFactory) {
        this.registry = registry;
        this.connectionFactory = connectionFactory;
    }

    public QueryInspection inspect(
            String databaseType,
            JdbcConnectionConfig config,
            InsertSelectQuery query,
            Duration timeout
    ) {
        return inspect(databaseType, config, query, List.of(), timeout, true);
    }

    /** Inspects a prepared SQL-service query without executing it as a metadata fallback. */
    public QueryInspection inspectPrepared(
            String databaseType,
            JdbcConnectionConfig config,
            InsertSelectQuery query,
            List<SqlQueryParameter> parameters,
            Duration timeout
    ) {
        return inspect(databaseType, config, query, parameters, timeout, false);
    }

    private QueryInspection inspect(
            String databaseType,
            JdbcConnectionConfig config,
            InsertSelectQuery query,
            List<SqlQueryParameter> parameters,
            Duration timeout,
            boolean allowExecutionFallback
    ) {
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("Inspection timeout must be positive");
        }
        try {
            DatabaseDialect dialect = registry.require(databaseType);
            if (!dialect.definition().capabilities().contains(DatabaseCapability.QUERY_METADATA)) {
                throw new UnsupportedOperationException(dialect.definition().displayName() + " does not support query metadata inspection");
            }
            try (Connection connection = connectionFactory.open(dialect.createConnectionSpec(config))) {
                try {
                    connection.setReadOnly(true);
                } catch (SQLException ignored) {
                    // Read-only mode is advisory for some JDBC drivers; the query object is still prevalidated.
                }
                String initializationSql = dialect.readOnlySessionInitializationSql();
                if (initializationSql != null && !initializationSql.isBlank()) {
                    try (Statement initialization = connection.createStatement()) {
                        initialization.execute(initializationSql);
                    }
                }
                try (PreparedStatement statement = connection.prepareStatement(query.sql())) {
                    statement.setMaxRows(1);
                    JdbcPlatformParameterBinder.bind(statement, parameters);
                    try {
                        statement.setQueryTimeout(Math.max(1, Math.toIntExact(timeout.toSeconds())));
                    } catch (SQLException ignored) {
                        // Some drivers do not expose a JDBC statement timeout.
                    }
                    ResultSetMetaData metadata = statement.getMetaData();
                    if (metadata != null) {
                        return JdbcQueryMetadata.inspect(metadata, dialect);
                    }
                    if (!allowExecutionFallback) {
                        throw new SQLException("JDBC driver did not provide prepared query metadata");
                    }
                    try (ResultSet resultSet = statement.executeQuery()) {
                        return JdbcQueryMetadata.inspect(resultSet.getMetaData(), dialect);
                    }
                }
            }
        } catch (DatabaseAccessException exception) {
            throw exception;
        } catch (ClassNotFoundException | LinkageError exception) {
            throw new DatabaseAccessException("DRIVER_NOT_AVAILABLE", "数据库驱动未安装", exception);
        } catch (IllegalArgumentException exception) {
            throw new DatabaseAccessException("INVALID_QUERY", exception.getMessage(), exception);
        } catch (SQLException exception) {
            throw new DatabaseAccessException("DATABASE_ERROR", "SQL 校验失败（SQLState: "
                    + (exception.getSQLState() == null ? "未知" : exception.getSQLState()) + "）", exception);
        }
    }

}
