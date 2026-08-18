package cn.superhuang.datascalpel.taskengine.runner;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeType;
import cn.superhuang.data.scalpel.contract.task.SnapshotTargetOnlyAction;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.dialect.api.DatabaseDialect;
import cn.superhuang.data.scalpel.dialect.model.JdbcSnapshotColumn;
import cn.superhuang.data.scalpel.dialect.model.JdbcSnapshotSyncSql;
import cn.superhuang.datascalpel.taskengine.canvas.CanvasPreparedSnapshotSyncOutput;
import cn.superhuang.datascalpel.taskengine.contract.RuntimeJdbcConnection;
import cn.superhuang.datascalpel.taskengine.contract.SnapshotSyncLimits;
import cn.superhuang.datascalpel.taskengine.contract.SnapshotSyncMetrics;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.storage.StorageLevel;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKBReader;
import org.locationtech.jts.io.WKBWriter;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/** Small-data, single-connection snapshot comparison and atomic JDBC writer. */
final class JdbcSnapshotSyncExecutor {
    private static final int JDBC_BATCH_SIZE = 500;
    private static final WKBWriter WKB_WRITER = new WKBWriter(2);

    SnapshotSyncMetrics execute(
            CanvasPreparedSnapshotSyncOutput output,
            SnapshotSyncLimits limits
    ) {
        Dataset<Row> cached = output.dataset().persist(StorageLevel.MEMORY_AND_DISK());
        try {
            SourceSnapshot source = readSource(output, cached, limits);
            return executeLocked(output, limits, source);
        } finally {
            cached.unpersist();
        }
    }

    private static SourceSnapshot readSource(
            CanvasPreparedSnapshotSyncOutput output,
            Dataset<Row> cached,
            SnapshotSyncLimits limits
    ) {
        long count = cached.count();
        if (count > limits.maxRowsPerSide()) {
            throw failure(output, "SNAPSHOT_SYNC_SOURCE_ROW_LIMIT_EXCEEDED",
                    "来源行数超过快照同步单侧限制");
        }
        List<SnapshotColumn> columns = columns(output, cached.schema().fields());
        List<Integer> keyIndexes = keyIndexes(output, columns);
        Map<SnapshotKey, SnapshotRow> rows = new LinkedHashMap<>();
        long estimatedBytes = 0L;
        // Iterate the cached Dataset instead of materializing an additional full List on the
        // Driver. The guarded Map below is the only intentional in-memory snapshot copy.
        java.util.Iterator<Row> iterator = cached.toLocalIterator();
        while (iterator.hasNext()) {
            Row row = iterator.next();
            SnapshotRow snapshotRow = sourceRow(output, columns, keyIndexes, row);
            if (rows.putIfAbsent(snapshotRow.key(), snapshotRow) != null) {
                throw failure(output, "SNAPSHOT_SYNC_SOURCE_KEY_DUPLICATE",
                        "来源数据包含重复 Key");
            }
            estimatedBytes = addEstimatedBytes(output, estimatedBytes, snapshotRow.estimatedBytes());
            if (estimatedBytes > limits.maxEstimatedBytes()) {
                throw failure(output, "SNAPSHOT_SYNC_MEMORY_LIMIT_EXCEEDED",
                        "来源与目标估算内存超过快照同步限制");
            }
        }
        return new SourceSnapshot(columns, keyIndexes, rows, estimatedBytes);
    }

    private static SnapshotSyncMetrics executeLocked(
            CanvasPreparedSnapshotSyncOutput output,
            SnapshotSyncLimits limits,
            SourceSnapshot source
    ) {
        RuntimeJdbcConnection runtime = output.runtimeDataSource().connection();
        DatabaseDialect dialect = SpatialJdbcRuntimeSupport.dialect(output.runtimeDataSource());
        List<JdbcSnapshotColumn> jdbcColumns = source.columns().stream()
                .map(column -> new JdbcSnapshotColumn(
                        column.schema().name(),
                        column.geometry() ? output.geometryLocalSrids().get(column.schema().name()) : null
                ))
                .toList();
        if (jdbcColumns.stream().anyMatch(column ->
                column.geometry() && column.geometrySpatialReferenceId() == null)) {
            throw failure(
                    output,
                    "SPATIAL_TARGET_METADATA_UNAVAILABLE",
                    "目标 Geometry 字段缺少数据库本地 SRID"
            );
        }
        JdbcSnapshotSyncSql sql = dialect.renderSnapshotSyncSql(
                output.targetTable(), jdbcColumns, output.keyColumns(),
                Duration.ofSeconds(limits.lockTimeoutSeconds()));
        try {
            Class.forName(runtime.driverClassName());
        } catch (ClassNotFoundException exception) {
            throw failure(output, fallbackFailureCode(output), "JDBC 驱动不可用", exception);
        }
        Properties properties = SpatialJdbcRuntimeSupport.jdbcProperties(runtime);
        try (Connection connection = DriverManager.getConnection(runtime.jdbcUrl(), properties)) {
            boolean originalAutoCommit = connection.getAutoCommit();
            boolean originalReadOnly = connection.isReadOnly();
            Throwable transactionFailure = null;
            boolean locked = false;
            try {
                if (originalReadOnly) connection.setReadOnly(false);
                connection.setAutoCommit(false);
                locked = acquireLock(output, connection, sql);
                TargetSnapshot target = readTarget(output, connection, sql, source, limits);
                SnapshotChanges changes = compare(output, source, target);
                validateDeleteProtection(
                        output, source.rows().size(), target.rows().size(), changes.deletes().size());
                writeChanges(connection, sql, source, changes);
                connection.commit();
                return metrics(source, target, changes);
            } catch (Throwable throwable) {
                transactionFailure = throwable;
                try {
                    connection.rollback();
                } catch (SQLException rollbackFailure) {
                    throwable.addSuppressed(rollbackFailure);
                }
                if (throwable instanceof RunnerExecutionException runnerFailure) {
                    throw runnerFailure;
                }
                throw failure(output, fallbackFailureCode(output),
                        "快照同步事务执行失败", throwable);
            } finally {
                if (locked && sql.unlockSql() != null) {
                    try (Statement statement = connection.createStatement()) {
                        statement.execute(sql.unlockSql());
                    } catch (SQLException unlockFailure) {
                        if (transactionFailure != null) {
                            transactionFailure.addSuppressed(unlockFailure);
                        }
                        // Closing the connection releases a remaining MySQL table lock. If the
                        // transaction already committed, do not report a false data-write failure.
                    }
                }
                restoreConnection(connection, originalAutoCommit, originalReadOnly, transactionFailure);
            }
        } catch (RunnerExecutionException exception) {
            throw exception;
        } catch (SQLException exception) {
            throw failure(output, fallbackFailureCode(output),
                    "无法连接快照同步目标", exception);
        }
    }

    private static boolean acquireLock(
            CanvasPreparedSnapshotSyncOutput output,
            Connection connection,
            JdbcSnapshotSyncSql sql
    ) throws SQLException {
        for (String lockStatement : sql.lockStatements()) {
            try (Statement statement = connection.createStatement()) {
                statement.execute(lockStatement);
            } catch (SQLException exception) {
                if (lockStatement.stripLeading().toUpperCase(java.util.Locale.ROOT).startsWith("LOCK ")) {
                    throw failure(output, "SNAPSHOT_SYNC_LOCK_TIMEOUT",
                            "等待目标表写锁失败或超时", exception);
                }
                throw exception;
            }
        }
        return true;
    }

    private static TargetSnapshot readTarget(
            CanvasPreparedSnapshotSyncOutput output,
            Connection connection,
            JdbcSnapshotSyncSql sql,
            SourceSnapshot source,
            SnapshotSyncLimits limits
    ) throws SQLException {
        Map<SnapshotKey, SnapshotRow> rows = new LinkedHashMap<>();
        long estimatedBytes = source.estimatedBytes();
        try (PreparedStatement statement = connection.prepareStatement(sql.selectSql());
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                if (rows.size() >= limits.maxRowsPerSide()) {
                    throw failure(output, "SNAPSHOT_SYNC_TARGET_ROW_LIMIT_EXCEEDED",
                            "目标行数超过快照同步单侧限制");
                }
                SnapshotRow row = targetRow(output, source.columns(), source.keyIndexes(), resultSet);
                if (rows.putIfAbsent(row.key(), row) != null) {
                    throw failure(output, "SNAPSHOT_SYNC_TARGET_KEY_DUPLICATE",
                            "目标数据包含重复 Key");
                }
                estimatedBytes = addEstimatedBytes(output, estimatedBytes, row.estimatedBytes());
                if (estimatedBytes > limits.maxEstimatedBytes()) {
                    throw failure(output, "SNAPSHOT_SYNC_MEMORY_LIMIT_EXCEEDED",
                            "来源与目标估算内存超过快照同步限制");
                }
            }
        }
        return new TargetSnapshot(rows, estimatedBytes - source.estimatedBytes());
    }

    private static SnapshotChanges compare(
            CanvasPreparedSnapshotSyncOutput output,
            SourceSnapshot source,
            TargetSnapshot target
    ) {
        List<SnapshotRow> inserts = new ArrayList<>();
        List<UpdateChange> updates = new ArrayList<>();
        List<SnapshotRow> deletes = new ArrayList<>();
        long unchanged = 0L;
        Set<Integer> keyIndexes = Set.copyOf(source.keyIndexes());
        for (Map.Entry<SnapshotKey, SnapshotRow> entry : source.rows().entrySet()) {
            SnapshotRow targetRow = target.rows().get(entry.getKey());
            if (targetRow == null) {
                inserts.add(entry.getValue());
                continue;
            }
            List<String> changedColumns = changedColumns(
                    output, source.columns(), keyIndexes, entry.getValue(), targetRow);
            if (changedColumns.isEmpty()) {
                unchanged++;
            } else {
                updates.add(new UpdateChange(entry.getValue(), targetRow, changedColumns));
            }
        }
        for (Map.Entry<SnapshotKey, SnapshotRow> entry : target.rows().entrySet()) {
            if (!source.rows().containsKey(entry.getKey())) deletes.add(entry.getValue());
        }
        boolean deleteEnabled = output.deletePolicy().action() == SnapshotTargetOnlyAction.DELETE;
        long retained = deleteEnabled ? 0L : deletes.size();
        return new SnapshotChanges(inserts, updates, deletes, unchanged, retained, deleteEnabled);
    }

    private static List<String> changedColumns(
            CanvasPreparedSnapshotSyncOutput output,
            List<SnapshotColumn> columns,
            Set<Integer> keyIndexes,
            SnapshotRow source,
            SnapshotRow target
    ) {
        List<String> changed = new ArrayList<>();
        for (int index = 0; index < columns.size(); index++) {
            if (keyIndexes.contains(index)) continue;
            if (!sameValue(output, columns.get(index),
                    source.normalized()[index], target.normalized()[index])) {
                changed.add(columns.get(index).schema().name());
            }
        }
        return List.copyOf(changed);
    }

    private static boolean sameValue(
            CanvasPreparedSnapshotSyncOutput output,
            SnapshotColumn column,
            Object left,
            Object right
    ) {
        if (left == null || right == null) return left == right;
        if (!column.geometry()) return left.equals(right);
        try {
            return ((Geometry) left).equalsTopo((Geometry) right);
        } catch (RuntimeException exception) {
            throw failure(output, "SNAPSHOT_SYNC_GEOMETRY_COMPARISON_FAILED",
                    "Geometry 拓扑比较失败，请先校验或修复 Geometry", exception);
        }
    }

    private static void validateDeleteProtection(
            CanvasPreparedSnapshotSyncOutput output,
            int sourceRows,
            int targetRows,
            int candidateDeletes
    ) {
        if (output.deletePolicy().action() == SnapshotTargetOnlyAction.KEEP) return;
        if (sourceRows == 0 && targetRows > 0) {
            throw failure(output, "SNAPSHOT_SYNC_EMPTY_SOURCE_DELETE_BLOCKED",
                    "来源为空时禁止删除非空目标");
        }
        if (candidateDeletes > output.deletePolicy().maxDeleteRows()) {
            throw failure(output, "SNAPSHOT_SYNC_DELETE_ROWS_EXCEEDED",
                    "候选删除数量超过配置阈值");
        }
        double ratio = targetRows == 0 ? 0D : (double) candidateDeletes / targetRows;
        if (ratio > output.deletePolicy().maxDeleteRatio()) {
            throw failure(output, "SNAPSHOT_SYNC_DELETE_RATIO_EXCEEDED",
                    "候选删除比例超过配置阈值");
        }
    }

    private static void writeChanges(
            Connection connection,
            JdbcSnapshotSyncSql sql,
            SourceSnapshot source,
            SnapshotChanges changes
    ) throws SQLException {
        if (changes.deleteEnabled()) {
            executeBatch(connection, sql.deleteSql(), changes.deletes(), row ->
                    keyBindingValues(source, row));
        }
        if (!changes.updates().isEmpty() && sql.updateSql() != null) {
            Set<Integer> keys = Set.copyOf(source.keyIndexes());
            executeBatch(connection, sql.updateSql(), changes.updates(), change -> {
                List<Object> values = new ArrayList<>();
                for (int index = 0; index < source.columns().size(); index++) {
                    if (!keys.contains(index)) values.add(change.source().bindValues()[index]);
                }
                values.addAll(keyBindingValues(source, change.target()));
                return values;
            });
        }
        executeBatch(connection, sql.insertSql(), changes.inserts(), row ->
                Arrays.asList(row.bindValues()));
    }

    private static List<Object> keyBindingValues(SourceSnapshot source, SnapshotRow row) {
        List<Object> values = new ArrayList<>(source.keyIndexes().size());
        for (int index : source.keyIndexes()) values.add(row.bindValues()[index]);
        return values;
    }

    private static <T> void executeBatch(
            Connection connection,
            String sql,
            List<T> rows,
            java.util.function.Function<T, List<Object>> values
    ) throws SQLException {
        if (rows.isEmpty()) return;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int batchSize = 0;
            for (T row : rows) {
                List<Object> bindingValues = values.apply(row);
                for (int index = 0; index < bindingValues.size(); index++) {
                    bind(statement, index + 1, bindingValues.get(index));
                }
                statement.addBatch();
                batchSize++;
                if (batchSize == JDBC_BATCH_SIZE) {
                    statement.executeBatch();
                    batchSize = 0;
                }
            }
            if (batchSize > 0) statement.executeBatch();
        }
    }

    private static void bind(PreparedStatement statement, int index, Object value) throws SQLException {
        if (value instanceof byte[] bytes) {
            statement.setBytes(index, bytes);
        } else if (value instanceof Instant instant) {
            statement.setObject(index, OffsetDateTime.ofInstant(instant, ZoneOffset.UTC));
        } else {
            statement.setObject(index, value);
        }
    }

    private static SnapshotSyncMetrics metrics(
            SourceSnapshot source,
            TargetSnapshot target,
            SnapshotChanges changes
    ) {
        long deleted = changes.deleteEnabled() ? changes.deletes().size() : 0L;
        return new SnapshotSyncMetrics(
                source.rows().size(), target.rows().size(), changes.inserts().size(),
                changes.updates().size(), deleted, changes.unchanged(), changes.retained());
    }

    private static List<SnapshotColumn> columns(
            CanvasPreparedSnapshotSyncOutput output,
            StructField[] selectedFields
    ) {
        Map<String, CanvasColumnSchema> target = new LinkedHashMap<>();
        for (CanvasColumnSchema column : output.targetSchema().columns()) target.put(column.name(), column);
        List<SnapshotColumn> columns = new ArrayList<>();
        for (int index = 0; index < selectedFields.length; index++) {
            CanvasColumnSchema schema = target.get(selectedFields[index].name());
            if (schema == null) {
                throw failure(output, "SNAPSHOT_SYNC_MAPPING_INVALID",
                        "快照同步来源包含目标中不存在的字段：" + selectedFields[index].name());
            }
            columns.add(new SnapshotColumn(schema, index));
        }
        if (columns.isEmpty()) {
            throw failure(output, "SNAPSHOT_SYNC_MAPPING_INVALID", "快照同步没有可比较或写入字段");
        }
        return List.copyOf(columns);
    }

    private static List<Integer> keyIndexes(
            CanvasPreparedSnapshotSyncOutput output,
            List<SnapshotColumn> columns
    ) {
        Map<String, Integer> indexes = new LinkedHashMap<>();
        for (int index = 0; index < columns.size(); index++) {
            indexes.put(columns.get(index).schema().name(), index);
        }
        List<Integer> result = new ArrayList<>();
        for (String key : output.keyColumns()) {
            Integer index = indexes.get(key);
            if (index == null) {
                throw failure(output, "SNAPSHOT_SYNC_KEY_NOT_MAPPED", "快照同步 Key 未被映射");
            }
            result.add(index);
        }
        return List.copyOf(result);
    }

    private static SnapshotRow sourceRow(
            CanvasPreparedSnapshotSyncOutput output,
            List<SnapshotColumn> columns,
            List<Integer> keyIndexes,
            Row row
    ) {
        Object[] normalized = new Object[columns.size()];
        Object[] bindings = new Object[columns.size()];
        long bytes = 0L;
        for (int index = 0; index < columns.size(); index++) {
            Object value = row.isNullAt(index) ? null : row.get(index);
            normalized[index] = normalizeSource(output, columns.get(index), value);
            bindings[index] = bindingValue(output, columns.get(index), value);
            bytes = addEstimatedBytes(output, bytes, estimate(normalized[index], bindings[index]));
        }
        SnapshotKey key = key(output, keyIndexes, normalized, "SNAPSHOT_SYNC_SOURCE_KEY_NULL");
        return new SnapshotRow(key, normalized, bindings, bytes);
    }

    private static SnapshotRow targetRow(
            CanvasPreparedSnapshotSyncOutput output,
            List<SnapshotColumn> columns,
            List<Integer> keyIndexes,
            ResultSet resultSet
    ) throws SQLException {
        Object[] normalized = new Object[columns.size()];
        Object[] bindings = new Object[columns.size()];
        long bytes = 0L;
        for (int index = 0; index < columns.size(); index++) {
            SnapshotColumn column = columns.get(index);
            Object value = targetValue(resultSet, index + 1, column);
            normalized[index] = normalizeTarget(output, column, value);
            bindings[index] = value;
            bytes = addEstimatedBytes(output, bytes, estimate(normalized[index], value));
        }
        SnapshotKey key = key(output, keyIndexes, normalized, "SNAPSHOT_SYNC_TARGET_KEY_NULL");
        return new SnapshotRow(key, normalized, bindings, bytes);
    }

    private static Object targetValue(ResultSet resultSet, int index, SnapshotColumn column)
            throws SQLException {
        return switch (column.schema().fieldType()) {
            case GEOMETRY, BINARY -> resultSet.getBytes(index);
            case STRING -> resultSet.getString(index);
            case DECIMAL -> resultSet.getBigDecimal(index);
            case DATE -> resultSet.getObject(index);
            case TIMESTAMP, TIMESTAMP_NTZ -> resultSet.getObject(index);
            default -> resultSet.getObject(index);
        };
    }

    private static SnapshotKey key(
            CanvasPreparedSnapshotSyncOutput output,
            List<Integer> indexes,
            Object[] normalized,
            String nullCode
    ) {
        List<Object> values = new ArrayList<>(indexes.size());
        for (int index : indexes) {
            Object value = normalized[index];
            if (value == null) {
                throw failure(output, nullCode, "快照同步 Key 不能包含 NULL");
            }
            values.add(value);
        }
        return new SnapshotKey(values);
    }

    private static Object normalizeSource(
            CanvasPreparedSnapshotSyncOutput output,
            SnapshotColumn column,
            Object value
    ) {
        if (value == null) return null;
        if (column.geometry()) {
            if (!(value instanceof Geometry geometry)) {
                throw failure(output, "SNAPSHOT_SYNC_GEOMETRY_COMPARISON_FAILED",
                        "来源 Geometry 运行时类型无效");
            }
            return validateGeometry(output, geometry);
        }
        return normalizeScalar(output, column.schema().fieldType(), value);
    }

    private static Object normalizeTarget(
            CanvasPreparedSnapshotSyncOutput output,
            SnapshotColumn column,
            Object value
    ) {
        if (value == null) return null;
        if (column.geometry()) {
            try {
                return validateGeometry(output, new WKBReader().read((byte[]) value));
            } catch (ParseException | RuntimeException exception) {
                if (exception instanceof RunnerExecutionException runnerFailure) throw runnerFailure;
                throw failure(output, "SNAPSHOT_SYNC_GEOMETRY_COMPARISON_FAILED",
                        "目标 Geometry 无法解析或比较", exception);
            }
        }
        return normalizeScalar(output, column.schema().fieldType(), value);
    }

    private static Geometry validateGeometry(
            CanvasPreparedSnapshotSyncOutput output,
            Geometry geometry
    ) {
        try {
            if (!geometry.isValid()) {
                throw failure(output, "SNAPSHOT_SYNC_GEOMETRY_COMPARISON_FAILED",
                        "Geometry 无效，请先使用 GEOMETRY_VALIDATE 或 GEOMETRY_REPAIR");
            }
            return geometry;
        } catch (RunnerExecutionException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw failure(output, "SNAPSHOT_SYNC_GEOMETRY_COMPARISON_FAILED",
                    "Geometry 有效性检查失败，请先校验或修复 Geometry", exception);
        }
    }

    private static Object normalizeScalar(
            CanvasPreparedSnapshotSyncOutput output,
            PlatformDataType type,
            Object value
    ) {
        try {
            return switch (type) {
                case BYTE -> ((Number) value).byteValue();
                case SHORT -> ((Number) value).shortValue();
                case INTEGER -> ((Number) value).intValue();
                case LONG -> ((Number) value).longValue();
                case FLOAT -> normalizeFloat(((Number) value).floatValue());
                case DOUBLE -> normalizeDouble(((Number) value).doubleValue());
                case DECIMAL -> value instanceof BigDecimal decimal
                        ? decimal.stripTrailingZeros()
                        : new BigDecimal(value.toString()).stripTrailingZeros();
                case BOOLEAN -> value instanceof Boolean bool
                        ? bool : ((Number) value).intValue() != 0;
                case STRING -> value.toString();
                case BINARY -> ByteBuffer.wrap((byte[]) value).asReadOnlyBuffer();
                case DATE -> normalizeDate(value);
                case TIMESTAMP_NTZ -> normalizeLocalDateTime(value);
                case TIMESTAMP -> normalizeInstant(value);
                case GEOMETRY -> throw new IllegalStateException("Geometry must use the spatial normalizer");
            };
        } catch (RuntimeException exception) {
            if (exception instanceof RunnerExecutionException runnerFailure) throw runnerFailure;
            throw failure(output, "SNAPSHOT_SYNC_VALUE_CONVERSION_FAILED",
                    "目标类型规范化失败：" + type, exception);
        }
    }

    private static Float normalizeFloat(float value) {
        return Float.isNaN(value) ? Float.NaN : value == 0F ? 0F : value;
    }

    private static Double normalizeDouble(double value) {
        return Double.isNaN(value) ? Double.NaN : value == 0D ? 0D : value;
    }

    private static LocalDate normalizeDate(Object value) {
        if (value instanceof LocalDate date) return date;
        if (value instanceof java.sql.Date date) return date.toLocalDate();
        return LocalDate.parse(value.toString());
    }

    private static LocalDateTime normalizeLocalDateTime(Object value) {
        if (value instanceof LocalDateTime dateTime) return dateTime;
        if (value instanceof Timestamp timestamp) return timestamp.toLocalDateTime();
        if (value instanceof OffsetDateTime dateTime) return dateTime.toLocalDateTime();
        return LocalDateTime.parse(value.toString().replace(' ', 'T'));
    }

    private static Instant normalizeInstant(Object value) {
        if (value instanceof Instant instant) return instant;
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        if (value instanceof OffsetDateTime dateTime) return dateTime.toInstant();
        if (value instanceof ZonedDateTime dateTime) return dateTime.toInstant();
        if (value instanceof LocalDateTime dateTime) return dateTime.toInstant(ZoneOffset.UTC);
        return Instant.parse(value.toString());
    }

    private static Object bindingValue(
            CanvasPreparedSnapshotSyncOutput output,
            SnapshotColumn column,
            Object value
    ) {
        if (value == null || !column.geometry()) return value;
        try {
            return WKB_WRITER.write((Geometry) value);
        } catch (RuntimeException exception) {
            throw failure(output, "SNAPSHOT_SYNC_GEOMETRY_COMPARISON_FAILED",
                    "来源 Geometry 无法序列化", exception);
        }
    }

    private static long estimate(Object normalized, Object binding) {
        if (normalized == null) return 1L;
        if (binding instanceof byte[] bytes) return 16L + bytes.length;
        if (normalized instanceof String text) {
            return 16L + text.getBytes(StandardCharsets.UTF_8).length;
        }
        if (normalized instanceof BigDecimal decimal) return 32L + decimal.precision();
        if (normalized instanceof ByteBuffer buffer) return 16L + buffer.remaining();
        if (normalized instanceof Geometry geometry) return 64L + WKB_WRITER.write(geometry).length;
        return 24L;
    }

    private static long addEstimatedBytes(
            CanvasPreparedSnapshotSyncOutput output,
            long current,
            long addition
    ) {
        try {
            return Math.addExact(current, addition);
        } catch (ArithmeticException overflow) {
            throw failure(output, "SNAPSHOT_SYNC_MEMORY_LIMIT_EXCEEDED",
                    "快照同步估算内存溢出");
        }
    }

    private static void restoreConnection(
            Connection connection,
            boolean originalAutoCommit,
            boolean originalReadOnly,
            Throwable originalFailure
    ) {
        SQLException restorationFailure = null;
        try {
            connection.setAutoCommit(originalAutoCommit);
        } catch (SQLException exception) {
            restorationFailure = exception;
        }
        try {
            connection.setReadOnly(originalReadOnly);
        } catch (SQLException exception) {
            if (restorationFailure == null) restorationFailure = exception;
            else restorationFailure.addSuppressed(exception);
        }
        if (restorationFailure != null && originalFailure != null) {
            originalFailure.addSuppressed(restorationFailure);
        }
    }

    private static RunnerExecutionException failure(
            CanvasPreparedSnapshotSyncOutput output,
            String code,
            String message
    ) {
        return new RunnerExecutionException(code, message, output.node().id());
    }

    private static RunnerExecutionException failure(
            CanvasPreparedSnapshotSyncOutput output,
            String code,
            String message,
            Throwable cause
    ) {
        return new RunnerExecutionException(code, message, output.node().id(), cause);
    }

    private static String fallbackFailureCode(CanvasPreparedSnapshotSyncOutput output) {
        return output.node().nodeType() == CanvasNodeType.MODEL_SNAPSHOT_SYNC_OUTPUT
                ? "MODEL_SNAPSHOT_SYNC_OUTPUT_FAILED"
                : "SNAPSHOT_SYNC_OUTPUT_FAILED";
    }

    private record SnapshotColumn(CanvasColumnSchema schema, int index) {
        boolean geometry() {
            return schema.fieldType() == PlatformDataType.GEOMETRY;
        }
    }

    private record SnapshotKey(List<Object> values) {
        private SnapshotKey {
            values = List.copyOf(values);
        }
    }

    private record SnapshotRow(
            SnapshotKey key,
            Object[] normalized,
            Object[] bindValues,
            long estimatedBytes
    ) {
    }

    private record SourceSnapshot(
            List<SnapshotColumn> columns,
            List<Integer> keyIndexes,
            Map<SnapshotKey, SnapshotRow> rows,
            long estimatedBytes
    ) {
        private SourceSnapshot {
            columns = List.copyOf(columns);
            keyIndexes = List.copyOf(keyIndexes);
            rows = Collections.unmodifiableMap(new LinkedHashMap<>(rows));
        }
    }

    private record TargetSnapshot(Map<SnapshotKey, SnapshotRow> rows, long estimatedBytes) {
        private TargetSnapshot {
            rows = Collections.unmodifiableMap(new LinkedHashMap<>(rows));
        }
    }

    private record UpdateChange(
            SnapshotRow source,
            SnapshotRow target,
            List<String> changedColumns
    ) {
        private UpdateChange {
            changedColumns = List.copyOf(changedColumns);
        }
    }

    private record SnapshotChanges(
            List<SnapshotRow> inserts,
            List<UpdateChange> updates,
            List<SnapshotRow> deletes,
            long unchanged,
            long retained,
            boolean deleteEnabled
    ) {
        private SnapshotChanges {
            inserts = List.copyOf(inserts);
            updates = List.copyOf(updates);
            deletes = List.copyOf(deletes);
        }
    }
}
