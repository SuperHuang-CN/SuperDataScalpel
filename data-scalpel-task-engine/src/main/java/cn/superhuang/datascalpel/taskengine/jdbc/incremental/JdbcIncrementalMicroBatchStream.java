package cn.superhuang.datascalpel.taskengine.jdbc.incremental;

import cn.superhuang.data.scalpel.dialect.api.DatabaseCapability;
import cn.superhuang.data.scalpel.dialect.api.DatabaseDialect;
import cn.superhuang.data.scalpel.dialect.api.JdbcIncrementalReadDialect;
import cn.superhuang.data.scalpel.dialect.builtin.BuiltInDialects;
import org.apache.spark.sql.connector.read.InputPartition;
import org.apache.spark.sql.connector.read.PartitionReaderFactory;
import org.apache.spark.sql.connector.read.streaming.MicroBatchStream;
import org.apache.spark.sql.connector.read.streaming.Offset;
import org.apache.spark.sql.types.StructType;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Properties;

public final class JdbcIncrementalMicroBatchStream implements MicroBatchStream {
    private final StructType schema;
    private final JdbcIncrementalOptions options;
    private final DatabaseDialect dialect;
    private final JdbcIncrementalReadDialect incrementalDialect;
    private volatile JdbcIncrementalOffset lastCommitted;

    JdbcIncrementalMicroBatchStream(StructType schema, JdbcIncrementalOptions options) {
        this.schema = schema;
        this.options = options;
        this.dialect = BuiltInDialects.registry().require(options.databaseType());
        if (!dialect.definition().capabilities().contains(DatabaseCapability.JDBC_INCREMENTAL_READ)
                || !(dialect instanceof JdbcIncrementalReadDialect supported)) {
            throw new JdbcIncrementalException(
                    "JDBC_INCREMENTAL_DATABASE_UNSUPPORTED",
                    "当前数据库不支持 JDBC 增量读取", false);
        }
        this.incrementalDialect = supported;
    }

    @Override
    public Offset initialOffset() {
        if (lastCommitted != null) return lastCommitted;
        if (options.resumeOffset() != null) {
            lastCommitted = requireOffset(JdbcIncrementalOffset.parse(options.resumeOffset()));
            return lastCommitted;
        }
        lastCommitted = switch (options.startPosition()) {
            case EARLIEST -> JdbcIncrementalOffset.earliest(
                    options.sourceSignature(), options.temporalType().name());
            case AT_TIME -> JdbcIncrementalOffset.at(
                    options.sourceSignature(), options.temporalType().name(), options.startTime());
            case LATEST -> JdbcIncrementalOffset.at(
                    options.sourceSignature(), options.temporalType().name(), safeDatabaseTime());
        };
        return lastCommitted;
    }

    @Override
    public Offset latestOffset() {
        JdbcIncrementalOffset start = lastCommitted == null
                ? (JdbcIncrementalOffset) initialOffset() : lastCommitted;
        Instant safeTime = safeDatabaseTime();
        if (!start.lowerUnbounded() && safeTime.isBefore(start.endTime())) {
            throw new JdbcIncrementalException(
                    "JDBC_INCREMENTAL_CLOCK_REGRESSION",
                    "源数据库安全时间早于已提交 JDBC 增量游标", false);
        }
        return JdbcIncrementalOffset.at(
                options.sourceSignature(), options.temporalType().name(), safeTime);
    }

    @Override
    public Offset deserializeOffset(String json) {
        return requireOffset(JdbcIncrementalOffset.parse(json));
    }

    @Override
    public InputPartition[] planInputPartitions(Offset start, Offset end) {
        JdbcIncrementalOffset startOffset = requireOffset(start);
        JdbcIncrementalOffset endOffset = requireOffset(end);
        if (endOffset.lowerUnbounded()
                || !startOffset.lowerUnbounded() && endOffset.endTime().isBefore(startOffset.endTime())) {
            throw new JdbcIncrementalException(
                    "JDBC_INCREMENTAL_CLOCK_REGRESSION", "JDBC 增量批次结束时间早于开始时间", false);
        }
        return new InputPartition[]{new JdbcIncrementalInputPartition(
                options, schema, startOffset, endOffset)};
    }

    @Override
    public PartitionReaderFactory createReaderFactory() {
        return partition -> {
            if (!(partition instanceof JdbcIncrementalInputPartition incremental)) {
                throw new IllegalArgumentException("Unexpected JDBC incremental partition");
            }
            return new JdbcIncrementalPartitionReader(incremental);
        };
    }

    @Override
    public void commit(Offset end) {
        JdbcIncrementalOffset committed = requireOffset(end);
        if (committed.lowerUnbounded()) {
            throw new JdbcIncrementalException(
                    "JDBC_INCREMENTAL_CHECKPOINT_INVALID", "不能提交无下界 JDBC 增量 Offset", false);
        }
        if (lastCommitted != null && !lastCommitted.lowerUnbounded()
                && committed.endTime().isBefore(lastCommitted.endTime())) {
            throw new JdbcIncrementalException(
                    "JDBC_INCREMENTAL_CLOCK_REGRESSION", "Spark 提交的 JDBC 增量 Offset 发生回退", false);
        }
        lastCommitted = committed;
    }

    @Override
    public void stop() {
        // Connections are batch-scoped.
    }

    private Instant safeDatabaseTime() {
        try {
            Class.forName(options.driverClassName());
            try (Connection connection = DriverManager.getConnection(
                    options.jdbcUrl(), jdbcProperties(options))) {
                connection.setReadOnly(true);
                return incrementalDialect.readDatabaseCurrentTime(connection)
                        .minus(options.visibilityDelaySeconds(), ChronoUnit.SECONDS);
            }
        } catch (SQLException exception) {
            throw classifySql("读取源数据库当前时间失败", exception);
        } catch (ClassNotFoundException exception) {
            throw new JdbcIncrementalException(
                    "JDBC_INCREMENTAL_DRIVER_UNAVAILABLE", "JDBC 增量驱动不可用", false, exception);
        }
    }

    private JdbcIncrementalOffset requireOffset(Offset offset) {
        if (!(offset instanceof JdbcIncrementalOffset incremental)
                || !options.sourceSignature().equals(incremental.sourceSignature())
                || !options.temporalType().name().equals(incremental.temporalType())) {
            throw new JdbcIncrementalException(
                    "JDBC_INCREMENTAL_SOURCE_CHANGED", "Checkpoint 来源与当前增量节点不一致", false);
        }
        return incremental;
    }

    static Properties jdbcProperties(JdbcIncrementalOptions options) {
        Properties properties = new Properties();
        properties.putAll(options.jdbcProperties());
        properties.setProperty("user", options.username());
        if (options.password() != null) properties.setProperty("password", options.password());
        return properties;
    }

    static JdbcIncrementalException classifySql(String message, SQLException exception) {
        String state = exception.getSQLState();
        String detail = exception.getMessage() == null
                ? "" : exception.getMessage().toLowerCase(java.util.Locale.ROOT);
        if (state != null && state.startsWith("28") || detail.contains("authentication")
                || detail.contains("invalid password") || detail.contains("access denied")) {
            return new JdbcIncrementalException(
                    "JDBC_INCREMENTAL_AUTHENTICATION_FAILED", "JDBC 增量源认证失败", false, exception);
        }
        if (state != null && state.startsWith("08") || detail.contains("connection refused")
                || detail.contains("connection reset") || detail.contains("unknown host")) {
            return new JdbcIncrementalException(
                    "JDBC_INCREMENTAL_NETWORK_ERROR", "JDBC 增量源网络连接失败", true, exception);
        }
        if (detail.contains("timeout") || detail.contains("timed out")) {
            return new JdbcIncrementalException(
                    "JDBC_INCREMENTAL_TIMEOUT", "JDBC 增量源查询超时", true, exception);
        }
        if (state != null && state.startsWith("42")) {
            return new JdbcIncrementalException(
                    "JDBC_INCREMENTAL_SCHEMA_CHANGED", "JDBC 增量源表结构已变化", false, exception);
        }
        return new JdbcIncrementalException(
                "JDBC_INCREMENTAL_QUERY_FAILED", message, true, exception);
    }
}
