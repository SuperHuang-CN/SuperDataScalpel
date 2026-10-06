package cn.superhuang.datascalpel.taskengine.runner;

import cn.superhuang.data.scalpel.dialect.api.DatabaseDialect;
import cn.superhuang.data.scalpel.dialect.model.TableIdentifier;
import cn.superhuang.data.scalpel.dialect.query.*;
import cn.superhuang.data.scalpel.dialect.runtime.JdbcBatchWriteStrategies;
import cn.superhuang.datascalpel.taskengine.contract.RuntimeDataSource;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

/** One batch output: retry-isolated Spark staging, then one target transaction. */
final class BatchJdbcWriter {
    private static final Logger LOG = LoggerFactory.getLogger(BatchJdbcWriter.class);
    private BatchJdbcWriter() { }

    static long write(RuntimeDataSource source, TableIdentifier target, Dataset<Row> dataset,
                      String mode, List<String> keys, Map<String, Integer> geometrySrids,
                      QueryPredicate condition, boolean allowEmptyOverwrite) {
        DatabaseDialect dialect = SpatialJdbcRuntimeSupport.dialect(source);
        var strategy = JdbcBatchWriteStrategies.require(dialect);
        Dataset<Row> encodedDataset = SpatialJdbcRuntimeSupport.encodeGeometry(dataset, geometrySrids);
        List<String> columns = List.of(dataset.columns());
        PreparedQuery predicate = validate(source, dataset, mode, keys, geometrySrids, condition, allowEmptyOverwrite);
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        TableIdentifier stage = new TableIdentifier(target.catalog(), target.schema(), "dsw_" + suffix);
        TableIdentifier winners = new TableIdentifier(target.catalog(), target.schema(), "dsw_" + suffix + "_w");
        String marker = "ds_attempt_" + suffix.substring(0, 8);
        while (columns.contains(marker)) marker += "x";
        String stageName = dialect.qualifiedName(stage);
        String winnerName = dialect.qualifiedName(winners);
        String quotedMarker = dialect.quoteIdentifier(marker);
        String selected = "SELECT " + columns.stream().map(c -> "s." + dialect.quoteIdentifier(c)).collect(Collectors.joining(", "))
                + " FROM " + stageName + " s JOIN " + winnerName + " w ON s." + quotedMarker + "=w." + quotedMarker;
        boolean stageCreated = false, winnersCreated = false, preserve = false;
        Connection connection = null;
        long started = System.nanoTime();
        try {
            connection = connect(source);
            strategy.validateTarget(connection, target, "OVERWRITE".equals(mode));
            execute(connection, strategy.createStage(stage, target, columns, marker));
            stageCreated = true;
            execute(connection, strategy.createWinners(winners, marker));
            winnersCreated = true;
            List<String> expressions = new ArrayList<>();
            for (String column : columns) {
                Integer srid = geometrySrids.get(column);
                if (srid != null) {
                    if (srid <= 0) throw new RunnerExecutionException("SPATIAL_TARGET_METADATA_UNAVAILABLE", "目标 Geometry 缺少 EPSG CRS", null);
                    expressions.add(SpatialJdbcRuntimeSupport.geometryWriteExpression(dialect, srid));
                } else {
                    expressions.add("?");
                }
            }
            String insert = "INSERT INTO " + stageName + " (" + columns.stream().map(dialect::quoteIdentifier)
                    .collect(Collectors.joining(", ")) + ", " + quotedMarker + ") VALUES ("
                    + String.join(", ", expressions) + ", ?)";
            LoadSpec spec = new LoadSpec(source.connection().driverClassName(), source.connection().jdbcUrl(),
                    SpatialJdbcRuntimeSupport.jdbcProperties(source.connection()), insert, columns.size());
            List<LoadedAttempt> accepted = encodedDataset.javaRDD()
                    .mapPartitions(spec::load).collect();
            long rows = 0;
            try (PreparedStatement statement = connection.prepareStatement("INSERT INTO " + winnerName + " VALUES (?)")) {
                for (LoadedAttempt attempt : accepted) {
                    rows = Math.addExact(rows, attempt.rows());
                    statement.setString(1, attempt.token()); statement.addBatch();
                }
                statement.executeBatch();
            }
            if ("OVERWRITE".equals(mode) && rows == 0 && !allowEmptyOverwrite) {
                throw new RunnerExecutionException("BATCH_WRITE_EMPTY_INPUT", "覆盖输入为空，已保留目标数据", null);
            }
            if (predicate != null && exists(connection, "SELECT 1 FROM (" + selected
                    + ") ds_scope WHERE CASE WHEN " + predicate.sql() + " THEN 1 ELSE 0 END=0", predicate.parameters())) {
                throw new RunnerExecutionException("BATCH_WRITE_OUTSIDE_SCOPE", "输入包含覆盖范围外或条件结果为 NULL 的数据，目标未修改", null);
            }
            if ("UPSERT".equals(mode)) validateKeys(connection, dialect, selected, keys);
            long loadedAt = System.nanoTime();
            connection.setAutoCommit(false);
            boolean commitStarted = false;
            try {
                checkInterrupted();
                if ("OVERWRITE".equals(mode)) {
                    String delete = "DELETE FROM " + dialect.qualifiedName(target) + (predicate == null ? "" : " WHERE " + predicate.sql());
                    try (PreparedStatement statement = connection.prepareStatement(delete)) {
                        if (predicate != null) JdbcWritePredicate.bind(statement, predicate.parameters());
                        statement.executeLargeUpdate();
                    }
                }
                String publish = "UPSERT".equals(mode) ? strategy.merge(target, columns, keys, selected)
                        : "INSERT INTO " + dialect.qualifiedName(target) + " (" + columns.stream().map(dialect::quoteIdentifier)
                        .collect(Collectors.joining(", ")) + ") " + selected;
                execute(connection, publish);
                checkInterrupted();
                commitStarted = true;
                connection.commit();
                LOG.info("BATCH_WRITE_COMMITTED processedRows={} loadAndValidateMs={} commitPhaseMs={}", rows,
                        (loadedAt - started) / 1_000_000, (System.nanoTime() - loadedAt) / 1_000_000);
                return rows;
            } catch (Exception failure) {
                if (commitStarted) {
                    preserve = true;
                    LOG.warn("BATCH_WRITE_COMMIT_UNKNOWN stageId={}", suffix);
                    throw new RunnerExecutionException("BATCH_WRITE_COMMIT_UNKNOWN", "数据库提交结果待确认，请勿自动重跑；暂存证据已保留", null, failure);
                }
                try { connection.rollback(); }
                catch (SQLException rollbackFailure) { failure.addSuppressed(rollbackFailure); }
                throw failure;
            }
        } catch (RunnerExecutionException failure) { throw failure; }
        catch (Exception failure) { throw new RunnerExecutionException("BATCH_WRITE_FAILED", "原子批量写入失败", null, failure); }
        finally {
            // Never setAutoCommit(true) on a failed transaction: that could commit it.
            close(connection);
            if (!preserve && (stageCreated || winnersCreated)) {
                try (Connection cleanup = connect(source)) {
                    if (stageCreated) drop(cleanup, stageName, suffix);
                    if (winnersCreated) drop(cleanup, winnerName, suffix);
                } catch (Exception failure) {
                    LOG.warn("BATCH_WRITE_CLEANUP_PENDING stageId={} reason={}", suffix, failure.getClass().getSimpleName());
                }
            }
        }
    }
    static PreparedQuery validate(RuntimeDataSource source, Dataset<Row> dataset, String mode, List<String> keys,
                                  Map<String, Integer> geometrySrids, QueryPredicate condition, boolean allowEmptyOverwrite) {
        var dialect = SpatialJdbcRuntimeSupport.dialect(source);
        JdbcBatchWriteStrategies.require(dialect);
        SpatialJdbcRuntimeSupport.encodeGeometry(dataset, geometrySrids);
        List<String> columns = List.of(dataset.columns());
        if (columns.isEmpty() || new HashSet<>(columns).size() != columns.size()) {
            throw new RunnerExecutionException("BATCH_WRITE_INVALID", "原子写入字段为空或重复", null);
        }
        if (!Set.of("APPEND", "OVERWRITE", "UPSERT").contains(mode)
                || (!"OVERWRITE".equals(mode) && (condition != null || allowEmptyOverwrite))) {
            throw new RunnerExecutionException("BATCH_WRITE_INVALID", "原子写入模式与覆盖配置不匹配", null);
        }
        if ("UPSERT".equals(mode) && (keys.isEmpty() || !columns.containsAll(keys)
                || keys.stream().anyMatch(geometrySrids::containsKey))) {
            throw new RunnerExecutionException("BATCH_WRITE_INVALID", "UPSERT 需要完整已映射的非空间键", null);
        }
        Set<String> predicateColumns = new HashSet<>(columns);
        predicateColumns.removeAll(geometrySrids.keySet());
        for (var field : dataset.schema().fields()) {
            if (field.dataType().sameType(org.apache.spark.sql.types.DataTypes.BinaryType)) predicateColumns.remove(field.name());
        }
        return condition == null ? null : JdbcWritePredicate.compile(dialect, condition, predicateColumns);
    }

    private static void validateKeys(Connection connection, DatabaseDialect dialect, String selected, List<String> keys) throws SQLException {
        String nulls = keys.stream().map(c -> dialect.quoteIdentifier(c) + " IS NULL").collect(Collectors.joining(" OR "));
        if (exists(connection, "SELECT 1 FROM (" + selected + ") ds_keys WHERE " + nulls, List.of())) {
            throw new RunnerExecutionException("UPSERT_KEY_NULL", "UPSERT Key 不能包含 NULL", null);
        }
        String grouped = keys.stream().map(dialect::quoteIdentifier).collect(Collectors.joining(", "));
        if (exists(connection, "SELECT " + grouped + " FROM (" + selected + ") ds_keys GROUP BY " + grouped + " HAVING COUNT(*)>1", List.of())) {
            throw new RunnerExecutionException("UPSERT_DUPLICATE_KEY", "当前批次存在重复 UPSERT Key", null);
        }
    }
    private static boolean exists(Connection connection, String sql, List<QueryParameter> parameters) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setMaxRows(1);
            JdbcWritePredicate.bind(statement, parameters);
            try (ResultSet rows = statement.executeQuery()) { return rows.next(); }
        }
    }
    private static Connection connect(RuntimeDataSource source) throws Exception {
        Class.forName(source.connection().driverClassName());
        return DriverManager.getConnection(source.connection().jdbcUrl(), SpatialJdbcRuntimeSupport.jdbcProperties(source.connection()));
    }
    private static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) { statement.executeUpdate(sql); }
    }
    private static void checkInterrupted() {
        if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException("批写已取消");
    }
    private static void close(Connection connection) {
        if (connection != null) try { connection.close(); } catch (SQLException ignored) { LOG.warn("BATCH_WRITE_CONNECTION_CLOSE_FAILED"); }
    }
    private static void drop(Connection connection, String ownedTable, String stageId) {
        try { execute(connection, "DROP TABLE " + ownedTable); }
        catch (SQLException failure) { LOG.warn("BATCH_WRITE_CLEANUP_PENDING stageId={} sqlState={}", stageId, failure.getSQLState()); }
    }
    // Spark's Sedona/Kryo serializer cannot use Unsafe field offsets on Java records.
    private static final class LoadedAttempt implements Serializable {
        @java.io.Serial private static final long serialVersionUID = 1L;
        private String token;
        private long rows;
        private LoadedAttempt() { }
        LoadedAttempt(String token, long rows) { this.token = token; this.rows = rows; }
        String token() { return token; }
        long rows() { return rows; }
    }
    private record LoadSpec(String driver, String url, Properties properties, String sql, int columns) implements Serializable {
        Iterator<LoadedAttempt> load(Iterator<Row> rows) throws Exception {
            // New token even for a retry after an ambiguous staging commit; only accepted tokens publish.
            String token = UUID.randomUUID().toString();
            long count = 0;
            Class.forName(driver);
            try (Connection connection = DriverManager.getConnection(url, properties)) {
                connection.setAutoCommit(false);
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    while (rows.hasNext()) {
                        checkInterrupted();
                        Row row = rows.next();
                        for (int i = 0; i < columns; i++) {
                            Object value = row.isNullAt(i) ? null : row.get(i);
                            if (value instanceof byte[] bytes) statement.setBytes(i + 1, bytes);
                            else statement.setObject(i + 1, value);
                        }
                        statement.setString(columns + 1, token);
                        statement.addBatch(); count++;
                        if (count % 500 == 0) { statement.executeBatch(); connection.commit(); }
                    }
                    if (count % 500 != 0) statement.executeBatch();
                    connection.commit();
                } catch (Exception failure) {
                    try { connection.rollback(); } catch (SQLException rollback) { failure.addSuppressed(rollback); }
                    throw failure;
                }
            }
            return List.of(new LoadedAttempt(token, count)).iterator();
        }
    }
}
