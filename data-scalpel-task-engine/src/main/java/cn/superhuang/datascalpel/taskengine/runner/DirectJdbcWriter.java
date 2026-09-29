package cn.superhuang.datascalpel.taskengine.runner;

import cn.superhuang.data.scalpel.dialect.model.TableIdentifier;
import cn.superhuang.datascalpel.taskengine.contract.RuntimeDataSource;
import cn.superhuang.datascalpel.taskengine.contract.RuntimeJdbcConnection;
import org.apache.spark.sql.DataFrameWriter;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SaveMode;
import org.apache.spark.sql.functions;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Arrays;

/**
 * Shared legacy JDBC execution for Canvas and SDK writes without batchWrite.
 * OVERWRITE deliberately retains separate TRUNCATE + insert, not a whole-write transaction.
 * Callers own authorization, mapping, validation/cache lifecycle and output metrics.
 */
final class DirectJdbcWriter {
    private DirectJdbcWriter() {
    }

    static void write(RuntimeDataSource source, TableIdentifier table, String qualifiedTableName,
                      Dataset<Row> dataset, String mode, List<String> keys,
                      Map<String, Integer> geometrySrids) throws Exception {
        if (!List.of("APPEND", "OVERWRITE", "UPSERT").contains(mode)) {
            throw new IllegalArgumentException("Unsupported JDBC write mode: " + mode);
        }
        if ("OVERWRITE".equals(mode)) {
            requireOverwriteSupported(source);
            truncate(source, qualifiedTableName);
        }
        if ("UPSERT".equals(mode)) {
            SpatialJdbcRuntimeSupport.writeDataset(source, table, qualifiedTableName, dataset, keys, geometrySrids);
        } else if (!geometrySrids.isEmpty()) {
            SpatialJdbcRuntimeSupport.writeDataset(source, table, qualifiedTableName, dataset, List.of(), geometrySrids);
        } else {
            append(source, qualifiedTableName, dataset);
        }
    }
    static void requireSupported(RuntimeDataSource source, String mode, boolean geometry, String nodeId) {
        if ("OVERWRITE".equals(mode)) requireOverwriteSupported(source);
        if ("UPSERT".equals(mode)) {
            switch (source.databaseType()) {
                case POSTGRESQL, HIGHGO, MYSQL, OPENGAUSS, KINGBASE, DAMENG, ORACLE, SQL_SERVER -> { }
                default -> throw new RunnerExecutionException("UPSERT_DATABASE_NOT_SUPPORTED",
                        "当前目标数据库未开放 UPSERT", nodeId);
            }
        }
        if (geometry) {
            switch (source.databaseType()) {
                case POSTGRESQL, HIGHGO, OPENGAUSS, KINGBASE, MYSQL -> { }
                default -> throw new RunnerExecutionException("SPATIAL_JDBC_UNSUPPORTED",
                        "Geometry JDBC 读写只支持 PostgreSQL 家族的 PostGIS 兼容扩展和 MySQL 8", nodeId);
            }
        }
    }

    static void validateUpsertKeys(Dataset<Row> dataset, List<String> keys, String nodeId) {
        if (keys.isEmpty()) {
            throw new RunnerExecutionException("UPSERT_KEY_REQUIRED", "UPSERT Key 不能为空", nodeId);
        }
        if (!Arrays.asList(dataset.columns()).containsAll(keys)) {
            throw new RunnerExecutionException("UPSERT_KEY_NOT_MAPPED", "UPSERT Key 未完成映射", nodeId);
        }
        Column[] columns = keys.stream()
                .map(key -> functions.col("`" + key.replace("`", "``") + "`"))
                .toArray(Column[]::new);
        Column nullKey = functions.lit(false);
        for (Column column : columns) nullKey = nullKey.or(column.isNull());
        if (dataset.filter(nullKey).limit(1).count() > 0) {
            throw new RunnerExecutionException("UPSERT_KEY_NULL", "UPSERT Key 不能包含 NULL", nodeId);
        }
        if (dataset.groupBy(columns).count().filter(functions.col("count").gt(1)).limit(1).count() > 0) {
            throw new RunnerExecutionException("UPSERT_DUPLICATE_KEY",
                    "当前批次存在重复 UPSERT Key" + (nodeId == null ? "" : "，请先使用 DEDUPLICATE"), nodeId);
        }
    }

    static void append(RuntimeDataSource source, String qualifiedTableName, Dataset<Row> dataset) {
        PostgreSqlFamilySparkJdbcDialect.ensureRegistered();
        RuntimeJdbcConnection connection = source.connection();
        DataFrameWriter<Row> writer = dataset.write().format("jdbc")
                .mode(SaveMode.Append)
                .option("url", connection.jdbcUrl())
                .option("dbtable", qualifiedTableName)
                .option("driver", connection.driverClassName())
                .option("user", connection.username())
                .option("password", connection.password());
        connection.properties().forEach(writer::option);
        writer.save();
    }

    static void truncate(RuntimeDataSource source, String qualifiedTableName) throws Exception {
        PostgreSqlFamilySparkJdbcDialect.ensureRegistered();
        RuntimeJdbcConnection runtime = source.connection();
        Class.forName(runtime.driverClassName());
        Properties properties = new Properties();
        properties.setProperty("user", runtime.username());
        if (runtime.password() != null) properties.setProperty("password", runtime.password());
        runtime.properties().forEach(properties::setProperty);
        try (Connection connection = DriverManager.getConnection(runtime.jdbcUrl(), properties);
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("TRUNCATE TABLE " + qualifiedTableName);
        }
    }

    static void requireOverwriteSupported(RuntimeDataSource source) {
        switch (source.databaseType()) {
            case TDENGINE_WEBSOCKET, TDENGINE_RESTFUL -> throw new RunnerExecutionException(
                    "OVERWRITE_DATABASE_NOT_SUPPORTED",
                    "TDengine 不支持普通 JDBC OVERWRITE 输出",
                    null
            );
            default -> {
                return;
            }
        }
    }

}
