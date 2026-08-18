package cn.superhuang.data.scalpel.dialect.runtime;

import cn.superhuang.data.scalpel.dialect.api.DatabaseDialect;
import cn.superhuang.data.scalpel.dialect.api.DialectRegistry;
import cn.superhuang.data.scalpel.dialect.api.SpatialPreviewDialect;
import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionConfig;
import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionFactory;
import cn.superhuang.data.scalpel.dialect.model.SpatialPreviewColumn;
import cn.superhuang.data.scalpel.dialect.model.SpatialPreviewData;
import cn.superhuang.data.scalpel.dialect.model.SpatialPreviewLimits;
import cn.superhuang.data.scalpel.dialect.model.SpatialPreviewMetadata;
import cn.superhuang.data.scalpel.dialect.model.SpatialPreviewViewport;
import cn.superhuang.data.scalpel.dialect.model.TableIdentifier;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.time.Duration;
import java.util.List;

/** Opens one short-lived read-only connection for a controlled dialect spatial preview operation. */
public final class DatabaseSpatialPreviewExecutor {

    private final DialectRegistry registry;
    private final JdbcConnectionFactory connectionFactory;

    public DatabaseSpatialPreviewExecutor(DialectRegistry registry, JdbcConnectionFactory connectionFactory) {
        this.registry = registry;
        this.connectionFactory = connectionFactory;
    }

    public SpatialPreviewMetadata inspect(
            String databaseType,
            JdbcConnectionConfig config,
            TableIdentifier table,
            List<SpatialPreviewColumn> columns,
            Duration timeout
    ) {
        return execute(databaseType, config, (connection, dialect) ->
                dialect.inspectSpatialPreview(connection, table, columns, timeout));
    }

    public SpatialPreviewData read(
            String databaseType,
            JdbcConnectionConfig config,
            TableIdentifier table,
            SpatialPreviewColumn column,
            SpatialPreviewViewport viewport,
            SpatialPreviewLimits limits,
            Duration timeout
    ) {
        return execute(databaseType, config, (connection, dialect) ->
                dialect.readSpatialPreview(connection, table, column, viewport, limits, timeout));
    }

    private <T> T execute(String databaseType, JdbcConnectionConfig config, Operation<T> operation) {
        try {
            DatabaseDialect databaseDialect = registry.require(databaseType);
            if (!(databaseDialect instanceof SpatialPreviewDialect spatialDialect)) {
                throw new DatabaseAccessException(
                        "SPATIAL_PREVIEW_UNSUPPORTED", "当前数据库不支持动态空间预览", null
                );
            }
            try (Connection connection = connectionFactory.open(databaseDialect.createConnectionSpec(config))) {
                try {
                    connection.setReadOnly(true);
                } catch (SQLException ignored) {
                    // Read-only mode is an optimization and is not supported by every JDBC driver.
                }
                return operation.execute(connection, spatialDialect);
            }
        } catch (DatabaseAccessException exception) {
            throw exception;
        } catch (ClassNotFoundException | LinkageError exception) {
            throw new DatabaseAccessException("DRIVER_NOT_AVAILABLE", "数据库驱动未安装", exception);
        } catch (IllegalArgumentException exception) {
            throw new DatabaseAccessException("INVALID_SPATIAL_PREVIEW", exception.getMessage(), exception);
        } catch (SQLTimeoutException exception) {
            throw new DatabaseAccessException("QUERY_TIMEOUT", "空间预览查询超时", exception);
        } catch (SQLException exception) {
            if ("57014".equals(exception.getSQLState())) {
                throw new DatabaseAccessException("QUERY_TIMEOUT", "空间预览查询超时", exception);
            }
            throw new DatabaseAccessException("DATABASE_ERROR", "空间预览数据库访问失败", exception);
        }
    }

    @FunctionalInterface
    private interface Operation<T> {
        T execute(Connection connection, SpatialPreviewDialect dialect) throws SQLException;
    }
}
