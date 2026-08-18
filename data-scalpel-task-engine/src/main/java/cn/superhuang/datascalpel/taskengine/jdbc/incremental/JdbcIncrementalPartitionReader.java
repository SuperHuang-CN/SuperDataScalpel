package cn.superhuang.datascalpel.taskengine.jdbc.incremental;

import cn.superhuang.data.scalpel.dialect.api.DatabaseDialect;
import cn.superhuang.data.scalpel.dialect.api.JdbcIncrementalReadDialect;
import cn.superhuang.data.scalpel.dialect.builtin.BuiltInDialects;
import cn.superhuang.data.scalpel.dialect.model.TableIdentifier;
import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.catalyst.expressions.GenericInternalRow;
import org.apache.spark.sql.connector.read.PartitionReader;
import org.apache.spark.sql.types.*;
import org.apache.spark.unsafe.types.UTF8String;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;

public final class JdbcIncrementalPartitionReader implements PartitionReader<InternalRow> {
    private final JdbcIncrementalInputPartition partition;
    private Connection connection;
    private PreparedStatement statement;
    private ResultSet resultSet;
    private InternalRow current;
    private boolean initialized;

    JdbcIncrementalPartitionReader(JdbcIncrementalInputPartition partition) {
        this.partition = partition;
    }

    @Override
    public boolean next() throws IOException {
        try {
            initialize();
            if (!resultSet.next()) {
                current = null;
                return false;
            }
            current = row(resultSet, partition.schema());
            return true;
        } catch (SQLException exception) {
            JdbcIncrementalException classified = JdbcIncrementalMicroBatchStream.classifySql(
                    "读取 JDBC 增量时间窗口失败", exception);
            throw new IOException(classified.getMessage(), classified);
        } catch (JdbcIncrementalException exception) {
            throw new IOException(exception.getMessage(), exception);
        }
    }

    @Override
    public InternalRow get() {
        if (current == null) throw new IllegalStateException("next() has not produced a row");
        return current;
    }

    private void initialize() throws SQLException {
        if (initialized) return;
        initialized = true;
        JdbcIncrementalOptions options = partition.options();
        try {
            Class.forName(options.driverClassName());
        } catch (ClassNotFoundException exception) {
            throw new JdbcIncrementalException(
                    "JDBC_INCREMENTAL_DRIVER_UNAVAILABLE", "JDBC 增量驱动不可用", false, exception);
        }
        connection = DriverManager.getConnection(
                options.jdbcUrl(), JdbcIncrementalMicroBatchStream.jdbcProperties(options));
        connection.setReadOnly(true);
        DatabaseDialect dialect = BuiltInDialects.registry().require(options.databaseType());
        if (!(dialect instanceof JdbcIncrementalReadDialect incrementalDialect)) {
            throw new JdbcIncrementalException(
                    "JDBC_INCREMENTAL_DATABASE_UNSUPPORTED", "当前数据库不支持 JDBC 增量读取", false);
        }
        TableIdentifier table = new TableIdentifier(
                options.catalogName(), options.schemaName(), options.tableName());
        String sql = incrementalDialect.renderIncrementalWindowQuery(
                dialect,
                table,
                Arrays.stream(partition.schema().fieldNames()).toList(),
                options.incrementalTimeColumn(),
                !partition.startOffset().lowerUnbounded()
        );
        statement = connection.prepareStatement(sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
        int parameter = 1;
        if (!partition.startOffset().lowerUnbounded()) {
            incrementalDialect.bindTime(
                    statement, parameter++, partition.startOffset().endTime(),
                    options.temporalType(), options.cursorTimeZone());
        }
        incrementalDialect.bindTime(
                statement, parameter, partition.endOffset().endTime(),
                options.temporalType(), options.cursorTimeZone());
        resultSet = statement.executeQuery();
    }

    private static InternalRow row(ResultSet resultSet, StructType schema) throws SQLException {
        Object[] values = new Object[schema.size()];
        for (int index = 0; index < schema.size(); index++) {
            StructField field = schema.fields()[index];
            values[index] = value(resultSet, index + 1, field);
        }
        return new GenericInternalRow(values);
    }

    private static Object value(ResultSet resultSet, int index, StructField field) throws SQLException {
        DataType type = field.dataType();
        Object value;
        if (type.equals(DataTypes.BinaryType)) value = resultSet.getBytes(index);
        else if (type.equals(DataTypes.TimestampType)) value = resultSet.getTimestamp(index);
        else if (type.equals(DataTypes.TimestampNTZType)) value = resultSet.getObject(index);
        else if (type.equals(DataTypes.DateType)) value = resultSet.getDate(index);
        else value = resultSet.getObject(index);
        if (resultSet.wasNull() || value == null) return null;
        try {
            if (type.equals(DataTypes.StringType)) return UTF8String.fromString(value.toString());
            if (type.equals(DataTypes.BooleanType)) return value instanceof Boolean bool
                    ? bool : Boolean.parseBoolean(value.toString());
            if (type.equals(DataTypes.ByteType)) return ((Number) value).byteValue();
            if (type.equals(DataTypes.ShortType)) return ((Number) value).shortValue();
            if (type.equals(DataTypes.IntegerType)) return ((Number) value).intValue();
            if (type.equals(DataTypes.LongType)) return ((Number) value).longValue();
            if (type.equals(DataTypes.FloatType)) return ((Number) value).floatValue();
            if (type.equals(DataTypes.DoubleType)) return ((Number) value).doubleValue();
            if (type instanceof DecimalType decimalType) {
                BigDecimal decimal = value instanceof BigDecimal source
                        ? source : new BigDecimal(value.toString());
                decimal = decimal.setScale(decimalType.scale(), java.math.RoundingMode.UNNECESSARY);
                if (decimal.precision() > decimalType.precision()) throw new ArithmeticException("decimal overflow");
                return Decimal.apply(decimal);
            }
            if (type.equals(DataTypes.BinaryType)) return value;
            if (type.equals(DataTypes.DateType)) {
                LocalDate date = value instanceof java.sql.Date sqlDate
                        ? sqlDate.toLocalDate() : (LocalDate) value;
                return Math.toIntExact(ChronoUnit.DAYS.between(LocalDate.ofEpochDay(0), date));
            }
            if (type.equals(DataTypes.TimestampType)) {
                Instant instant = value instanceof Timestamp timestamp
                        ? timestamp.toInstant() : (Instant) value;
                return micros(instant);
            }
            if (type.equals(DataTypes.TimestampNTZType)) {
                LocalDateTime local = value instanceof LocalDateTime dateTime ? dateTime
                        : value instanceof Timestamp timestamp ? timestamp.toLocalDateTime()
                        : LocalDateTime.parse(value.toString().replace(' ', 'T'));
                return micros(local.toInstant(ZoneOffset.UTC));
            }
        } catch (RuntimeException exception) {
            throw new JdbcIncrementalException(
                    "JDBC_INCREMENTAL_SCHEMA_CHANGED",
                    "JDBC 增量字段无法转换：" + field.name(), false, exception);
        }
        throw new JdbcIncrementalException(
                "JDBC_INCREMENTAL_SCHEMA_CHANGED",
                "JDBC 增量字段类型不受支持：" + field.name(), false);
    }

    private static long micros(Instant instant) {
        return Math.addExact(Math.multiplyExact(instant.getEpochSecond(), 1_000_000L), instant.getNano() / 1_000L);
    }

    @Override
    public void close() throws IOException {
        SQLException failure = close(resultSet, null);
        failure = close(statement, failure);
        failure = close(connection, failure);
        if (failure != null) throw new IOException("关闭 JDBC 增量读取连接失败", failure);
    }

    private static SQLException close(AutoCloseable closeable, SQLException failure) {
        if (closeable == null) return failure;
        try {
            closeable.close();
        } catch (Exception exception) {
            SQLException sql = exception instanceof SQLException source
                    ? source : new SQLException(exception);
            if (failure == null) return sql;
            failure.addSuppressed(sql);
        }
        return failure;
    }
}
