package cn.superhuang.datascalpel.taskengine.tdengine.tmq;

import com.taosdata.jdbc.enums.TmqMessageType;
import com.taosdata.jdbc.tmq.ConsumerRecord;
import com.taosdata.jdbc.tmq.ConsumerRecords;
import com.taosdata.jdbc.tmq.TopicPartition;
import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.catalyst.expressions.GenericInternalRow;
import org.apache.spark.sql.connector.read.PartitionReader;
import org.apache.spark.sql.types.BinaryType;
import org.apache.spark.sql.types.BooleanType;
import org.apache.spark.sql.types.ByteType;
import org.apache.spark.sql.types.DataType;
import org.apache.spark.sql.types.DateType;
import org.apache.spark.sql.types.Decimal;
import org.apache.spark.sql.types.DecimalType;
import org.apache.spark.sql.types.DoubleType;
import org.apache.spark.sql.types.FloatType;
import org.apache.spark.sql.types.IntegerType;
import org.apache.spark.sql.types.LongType;
import org.apache.spark.sql.types.ShortType;
import org.apache.spark.sql.types.StringType;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.StructType;
import org.apache.spark.sql.types.TimestampType;
import org.apache.spark.unsafe.types.UTF8String;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

public final class TdEngineTmqPartitionReader implements PartitionReader<InternalRow> {
    private static final Logger LOGGER = LoggerFactory.getLogger(TdEngineTmqPartitionReader.class);
    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(1);
    private static final Duration ASSIGNMENT_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration PROGRESS_TIMEOUT = Duration.ofSeconds(60);

    private final TdEngineTmqInputPartition partition;
    private final Queue<InternalRow> ready = new ArrayDeque<>();
    private final Map<Integer, Long> nextOffsets;
    private TdEngineTmqConsumer consumer;
    private InternalRow current;
    private boolean initialized;
    private boolean closed;
    private long deliveredRows;
    private final long startedAtNanos = System.nanoTime();
    private long lastProgressNanos = System.nanoTime();

    public TdEngineTmqPartitionReader(TdEngineTmqInputPartition partition) {
        this.partition = partition;
        this.nextOffsets = new HashMap<>(partition.startOffsets());
    }

    @Override
    public boolean next() throws IOException {
        try {
            initialize();
            while (ready.isEmpty() && !complete()) {
                poll();
            }
            current = ready.poll();
            if (current != null) deliveredRows++;
            return current != null;
        } catch (TdEngineTmqException exception) {
            throw new IOException(exception.getMessage(), exception);
        } catch (SQLException exception) {
            TdEngineTmqException classified = TdEngineTmqMicroBatchStream.classifySql(
                    "TMQ 消费轮询失败", exception);
            throw new IOException(classified.getMessage(), classified);
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
        if (complete()) return;
        consumer = partition.consumerFactory().create(
                partition.options(),
                partition.groupId(),
                partition.options().clientId("read-" + TdEngineTmqOptions.attemptId())
        );
        consumer.subscribe(java.util.List.of(partition.options().topic()));
        Set<Integer> expected = partition.startOffsets().keySet();
        long deadline = System.nanoTime() + ASSIGNMENT_TIMEOUT.toNanos();
        Set<Integer> observed = Set.of();
        while (System.nanoTime() < deadline) {
            // Poll records used to establish assignment are deliberately discarded.
            consumer.poll(POLL_TIMEOUT);
            observed = vGroups(consumer.assignment());
            if (observed.equals(expected)) break;
        }
        if (!observed.equals(expected)) {
            if (observed.isEmpty()) {
                throw new TdEngineTmqException(
                        "TDENGINE_TMQ_ASSIGNMENT_TIMEOUT", "等待 TMQ VGroup 分配超时", true);
            }
            throw new TdEngineTmqException(
                    "TDENGINE_TMQ_VGROUP_CHANGED", "TMQ VGroup 集合与 Checkpoint 不一致", false);
        }
        for (Map.Entry<Integer, Long> offset : partition.startOffsets().entrySet()) {
            consumer.seek(topicPartition(offset.getKey()), offset.getValue());
        }
        lastProgressNanos = System.nanoTime();
    }

    private void poll() throws SQLException {
        ConsumerRecords<Map<String, Object>> records = consumer.poll(POLL_TIMEOUT);
        boolean progressed = false;
        for (ConsumerRecord<Map<String, Object>> record : records) {
            if (record.getMessageType() != TmqMessageType.TMQ_RES_DATA) {
                throw new TdEngineTmqException(
                        "TDENGINE_TMQ_MESSAGE_TYPE_UNSUPPORTED",
                        "TMQ Topic 返回了非数据消息", false);
            }
            if (!partition.options().topic().equals(record.getTopic())) {
                throw new TdEngineTmqException(
                        "TDENGINE_TMQ_TOPIC_CHANGED", "TMQ 返回了未预期的 Topic", false);
            }
            int vGroup = record.getVGroupId();
            Long start = partition.startOffsets().get(vGroup);
            Long end = partition.endOffsets().get(vGroup);
            if (start == null || end == null) {
                throw new TdEngineTmqException(
                        "TDENGINE_TMQ_VGROUP_CHANGED", "TMQ 返回了未预期的 VGroup", false);
            }
            long recordOffset = record.getOffset();
            if (recordOffset < start || recordOffset >= end) continue;
            ready.add(toRow(record.value(), partition.schema()));
            long next = recordOffset + 1;
            if (next > nextOffsets.get(vGroup)) {
                nextOffsets.put(vGroup, Math.min(next, end));
                progressed = true;
            }
        }
        for (int vGroup : partition.startOffsets().keySet()) {
            long position = consumer.position(topicPartition(vGroup));
            long bounded = Math.min(position, partition.endOffsets().get(vGroup));
            if (bounded > nextOffsets.get(vGroup)) {
                nextOffsets.put(vGroup, bounded);
                progressed = true;
            }
        }
        if (progressed) {
            lastProgressNanos = System.nanoTime();
        } else if (!complete() && System.nanoTime() - lastProgressNanos > PROGRESS_TIMEOUT.toNanos()) {
            throw new TdEngineTmqException(
                    "TDENGINE_TMQ_POLL_FAILED", "TMQ 批次 Offset 连续 60 秒没有进展", true);
        }
    }

    private boolean complete() {
        for (Map.Entry<Integer, Long> end : partition.endOffsets().entrySet()) {
            if (nextOffsets.getOrDefault(end.getKey(), -1L) < end.getValue()) return false;
        }
        return true;
    }

    private static InternalRow toRow(Map<String, Object> values, StructType schema) {
        if (values == null) {
            throw new TdEngineTmqException(
                    "TDENGINE_TMQ_SCHEMA_MISMATCH", "TMQ 数据消息为空", false);
        }
        Set<String> expected = new HashSet<>();
        for (StructField field : schema.fields()) expected.add(field.name());
        if (!values.keySet().equals(expected)) {
            throw new TdEngineTmqException(
                    "TDENGINE_TMQ_SCHEMA_MISMATCH", "TMQ 消息字段与超级表快照不一致", false);
        }
        Object[] converted = new Object[schema.size()];
        for (int index = 0; index < schema.size(); index++) {
            StructField field = schema.fields()[index];
            converted[index] = convert(values.get(field.name()), field.dataType(), field.name());
        }
        return new GenericInternalRow(converted);
    }

    private static Object convert(Object value, DataType type, String fieldName) {
        if (value == null) return null;
        try {
            if (type instanceof StringType) {
                if (value instanceof byte[] bytes) return UTF8String.fromBytes(bytes);
                if (value instanceof ByteBuffer buffer) {
                    ByteBuffer copy = buffer.slice();
                    byte[] bytes = new byte[copy.remaining()];
                    copy.get(bytes);
                    return UTF8String.fromBytes(bytes);
                }
                return UTF8String.fromString(value.toString());
            }
            if (type instanceof BooleanType) {
                if (value instanceof Boolean bool) return bool;
                String text = value.toString();
                if (text.equalsIgnoreCase("true") || text.equals("1")) return true;
                if (text.equalsIgnoreCase("false") || text.equals("0")) return false;
                throw new IllegalArgumentException("invalid boolean");
            }
            if (type instanceof ByteType) return number(value).byteValueExact();
            if (type instanceof ShortType) return number(value).shortValueExact();
            if (type instanceof IntegerType) return number(value).intValueExact();
            if (type instanceof LongType) return number(value).longValueExact();
            if (type instanceof FloatType) return value instanceof Number number
                    ? number.floatValue() : Float.parseFloat(value.toString());
            if (type instanceof DoubleType) return value instanceof Number number
                    ? number.doubleValue() : Double.parseDouble(value.toString());
            if (type instanceof DecimalType decimalType) {
                BigDecimal decimal = number(value).setScale(decimalType.scale(), java.math.RoundingMode.UNNECESSARY);
                if (decimal.precision() > decimalType.precision()) throw new ArithmeticException("decimal overflow");
                return Decimal.apply(decimal);
            }
            if (type instanceof BinaryType) {
                if (value instanceof byte[] bytes) return bytes;
                if (value instanceof ByteBuffer buffer) {
                    ByteBuffer copy = buffer.slice();
                    byte[] bytes = new byte[copy.remaining()];
                    copy.get(bytes);
                    return bytes;
                }
                throw new IllegalArgumentException("invalid binary");
            }
            if (type instanceof TimestampType) return timestampMicros(value);
            if (type instanceof DateType) return dateDays(value);
        } catch (RuntimeException exception) {
            throw new TdEngineTmqException(
                    "TDENGINE_TMQ_SCHEMA_MISMATCH", "TMQ 字段无法转换：" + fieldName, false, exception);
        }
        throw new TdEngineTmqException(
                "TDENGINE_TMQ_SCHEMA_MISMATCH", "TMQ 字段类型不受支持：" + fieldName, false);
    }

    private static BigDecimal number(Object value) {
        return value instanceof BigDecimal decimal ? decimal : new BigDecimal(value.toString());
    }

    private static long timestampMicros(Object value) {
        Instant instant;
        if (value instanceof Timestamp timestamp) instant = timestamp.toInstant();
        else if (value instanceof Instant input) instant = input;
        else if (value instanceof LocalDateTime input) instant = input.toInstant(ZoneOffset.UTC);
        else instant = Instant.parse(value.toString());
        return Math.addExact(Math.multiplyExact(instant.getEpochSecond(), 1_000_000L), instant.getNano() / 1_000L);
    }

    private static int dateDays(Object value) {
        LocalDate date = value instanceof java.sql.Date sqlDate ? sqlDate.toLocalDate()
                : value instanceof LocalDate localDate ? localDate : LocalDate.parse(value.toString());
        return Math.toIntExact(ChronoUnit.DAYS.between(LocalDate.ofEpochDay(0), date));
    }

    private TopicPartition topicPartition(int vGroup) {
        return new TopicPartition(partition.options().topic(), vGroup);
    }

    private static Set<Integer> vGroups(Set<TopicPartition> partitions) {
        Set<Integer> result = new HashSet<>();
        for (TopicPartition partition : partitions) result.add(partition.getVGroupId());
        return Set.copyOf(result);
    }

    @Override
    public void close() throws IOException {
        if (closed) return;
        closed = true;
        SQLException failure = null;
        if (consumer != null) {
            try {
                consumer.unsubscribe();
            } catch (SQLException exception) {
                failure = exception;
            }
            try {
                consumer.close();
            } catch (SQLException exception) {
                if (failure == null) failure = exception;
                else failure.addSuppressed(exception);
            }
        }
        LOGGER.info(
                "TDengine TMQ batch reader closed: dataSourceId={}, topic={}, vGroupCount={}, "
                        + "startOffsets={}, endOffsets={}, rowCount={}, elapsedMs={}",
                partition.options().dataSourceId(),
                partition.options().topic(),
                partition.startOffsets().size(),
                partition.startOffsets(),
                partition.endOffsets(),
                deliveredRows,
                Math.max(0L, (System.nanoTime() - startedAtNanos) / 1_000_000L)
        );
        if (failure != null) throw new IOException("关闭 TMQ Consumer 失败", failure);
    }
}
