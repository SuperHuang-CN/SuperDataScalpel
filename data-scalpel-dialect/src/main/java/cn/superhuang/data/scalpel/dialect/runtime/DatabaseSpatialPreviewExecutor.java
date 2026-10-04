package cn.superhuang.data.scalpel.dialect.runtime;

import cn.superhuang.data.scalpel.dialect.api.DatabaseDialect;
import cn.superhuang.data.scalpel.dialect.api.DialectRegistry;
import cn.superhuang.data.scalpel.dialect.api.SpatialPreviewDialect;
import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionConfig;
import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionFactory;
import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionSpec;
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
import java.util.Properties;

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

    public void stream(String databaseType, JdbcConnectionConfig config, TableIdentifier table,
            SpatialPreviewColumn column, int maximumRows, Duration timeout,
            java.util.function.Consumer<byte[]> consumer) {
        execute(databaseType, config, true, (connection, dialect) -> {
            connection.setReadOnly(true);
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            connection.setAutoCommit(false);
            try {
                dialect.streamSpatialPreview(connection, table, column, maximumRows, timeout, consumer);
            } catch (SQLException | RuntimeException failure) {
                try {
                    connection.rollback();
                } catch (SQLException cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
                throw failure;
            }
            connection.rollback();
            return null;
        });
    }

    private <T> T execute(String databaseType, JdbcConnectionConfig config, Operation<T> operation) {
        return execute(databaseType, config, false, operation);
    }

    private <T> T execute(String databaseType, JdbcConnectionConfig config, boolean streaming,
            Operation<T> operation) {
        try {
            DatabaseDialect databaseDialect = registry.require(databaseType);
            if (!(databaseDialect instanceof SpatialPreviewDialect spatialDialect)) {
                throw new DatabaseAccessException(
                        "SPATIAL_PREVIEW_UNSUPPORTED", "当前数据库不支持动态空间预览", null
                );
            }
            JdbcConnectionSpec spec = databaseDialect.createConnectionSpec(config);
            if (streaming) {
                spec = streamingConnectionSpec(spec);
            }
            try (Connection connection = connectionFactory.open(spec)) {
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
            if (streaming && exception.getMessage() != null
                    && exception.getMessage().contains("maxResultBuffer")) {
                throw new DatabaseAccessException(
                        "SPATIAL_PREVIEW_LIMIT_EXCEEDED",
                        "单批几何超过 32 MiB 读取缓冲上限，请使用已发布的空间服务", exception
                );
            }
            if ("57014".equals(exception.getSQLState())) {
                throw new DatabaseAccessException("QUERY_TIMEOUT", "空间预览查询超时", exception);
            }
            if ("42P01".equals(exception.getSQLState())
                    || "42703".equals(exception.getSQLState())
                    || "42883".equals(exception.getSQLState())) {
                throw new DatabaseAccessException(
                        "SPATIAL_PREVIEW_UNAVAILABLE",
                        "目标数据库缺少空间预览所需的 PostGIS 兼容系统表、字段或函数",
                        exception
                );
            }
            throw new DatabaseAccessException("DATABASE_ERROR", "空间预览数据库访问失败", exception);
        }
    }

    private static JdbcConnectionSpec streamingConnectionSpec(JdbcConnectionSpec spec) {
        if (!"org.postgresql.Driver".equals(spec.driverClassName())) {
            return spec;
        }
        Properties properties = new Properties();
        properties.putAll(spec.properties());
        // A cold preview executes the geometry statement only once. The default threshold of 5
        // otherwise sends bytea as hexadecimal text, doubling geometry traffic and decoding work.
        properties.setProperty("prepareThreshold", "-1");
        properties.setProperty("preferQueryMode", "extended");
        properties.setProperty("binaryTransfer", "true");
        properties.setProperty("binaryTransferEnable", "bytea");
        properties.setProperty("binaryTransferDisable", "");
        // Bound driver allocations as well as the downstream WKB budget. A fixed, modest batch
        // avoids adaptiveFetch permanently shrinking the cursor after one unusually large shape.
        properties.setProperty("maxResultBuffer", "33554432");
        properties.setProperty("adaptiveFetch", "false");
        return new JdbcConnectionSpec(spec.driverClassName(), spec.jdbcUrl(), properties, spec.schemaName());
    }

    @FunctionalInterface
    private interface Operation<T> {
        T execute(Connection connection, SpatialPreviewDialect dialect) throws SQLException;
    }
}
