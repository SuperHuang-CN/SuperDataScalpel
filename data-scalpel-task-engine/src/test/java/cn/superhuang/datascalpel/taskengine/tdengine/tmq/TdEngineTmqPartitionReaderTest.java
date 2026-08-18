package cn.superhuang.datascalpel.taskengine.tdengine.tmq;

import com.taosdata.jdbc.enums.TmqMessageType;
import com.taosdata.jdbc.tmq.ConsumerRecord;
import com.taosdata.jdbc.tmq.ConsumerRecords;
import com.taosdata.jdbc.tmq.TopicPartition;
import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.DecimalType;
import org.apache.spark.sql.types.StructType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TdEngineTmqPartitionReaderTest {
    private static final String TOPIC = "meters_topic";
    private static final TopicPartition VGROUP_ZERO = new TopicPartition(TOPIC, 0);
    private static final StructType SCHEMA = new StructType()
            .add("ts", DataTypes.TimestampType, false)
            .add("active", DataTypes.BooleanType, true)
            .add("count", DataTypes.IntegerType, true)
            .add("amount", DataTypes.createDecimalType(8, 2), true)
            .add("name", DataTypes.StringType, true)
            .add("payload", DataTypes.BinaryType, true);

    @Test
    void assignsSeeksReadsBoundedRangeConvertsRowsAndClosesConsumer() throws Exception {
        ConsumerRecords<Map<String, Object>> records = records(
                record(TOPIC, 0, 5, values()),
                record(TOPIC, 0, 6, values())
        );
        FakeConsumer consumer = new FakeConsumer(Set.of(VGROUP_ZERO), records);
        FakeFactory factory = new FakeFactory(consumer);
        TdEngineTmqPartitionReader reader = new TdEngineTmqPartitionReader(
                partition(Map.of(0, 5L), Map.of(0, 7L), factory)
        );

        assertTrue(reader.next());
        InternalRow row = reader.get();
        assertEquals(1_704_067_200_123_456L, row.getLong(0));
        assertTrue(row.getBoolean(1));
        assertEquals(7, row.getInt(2));
        assertEquals(new BigDecimal("12.30"),
                row.getDecimal(3, ((DecimalType) SCHEMA.fields()[3].dataType()).precision(), 2)
                        .toJavaBigDecimal());
        assertEquals("meter-a", row.getUTF8String(4).toString());
        assertArrayEquals(new byte[]{1, 2, 3}, row.getBinary(5));
        assertTrue(reader.next());
        assertFalse(reader.next());

        assertEquals(Map.of(VGROUP_ZERO, 5L), consumer.seekOffsets);
        reader.close();
        assertTrue(consumer.unsubscribed);
        assertTrue(consumer.closed);
    }

    @Test
    void emptySparkBatchDoesNotCreateConsumer() throws Exception {
        FakeFactory factory = new FakeFactory(new FakeConsumer(Set.of(VGROUP_ZERO)));
        TdEngineTmqPartitionReader reader = new TdEngineTmqPartitionReader(
                partition(Map.of(0, 8L), Map.of(0, 8L), factory)
        );

        assertFalse(reader.next());
        reader.close();

        assertEquals(0, factory.created);
    }

    @Test
    void rejectsSchemaMismatchAndUnexpectedTopic() throws Exception {
        FakeConsumer missingFieldConsumer = new FakeConsumer(
                Set.of(VGROUP_ZERO), records(record(TOPIC, 0, 5, Map.of("ts", Timestamp.from(Instant.EPOCH))))
        );
        TdEngineTmqPartitionReader missingFieldReader = new TdEngineTmqPartitionReader(
                partition(Map.of(0, 5L), Map.of(0, 6L), new FakeFactory(missingFieldConsumer))
        );
        IOException mismatch = assertThrows(IOException.class, missingFieldReader::next);
        assertEquals("TDENGINE_TMQ_SCHEMA_MISMATCH",
                assertInstanceOf(TdEngineTmqException.class, mismatch.getCause()).code());
        missingFieldReader.close();

        FakeConsumer unexpectedTopicConsumer = new FakeConsumer(
                Set.of(VGROUP_ZERO), records(record("other_topic", 0, 5, values()))
        );
        TdEngineTmqPartitionReader unexpectedTopicReader = new TdEngineTmqPartitionReader(
                partition(Map.of(0, 5L), Map.of(0, 6L), new FakeFactory(unexpectedTopicConsumer))
        );
        IOException changed = assertThrows(IOException.class, unexpectedTopicReader::next);
        assertEquals("TDENGINE_TMQ_TOPIC_CHANGED",
                assertInstanceOf(TdEngineTmqException.class, changed.getCause()).code());
        unexpectedTopicReader.close();
    }

    private static TdEngineTmqInputPartition partition(
            Map<Integer, Long> start,
            Map<Integer, Long> end,
            TdEngineTmqConsumerFactory factory
    ) {
        return new TdEngineTmqInputPartition(
                new TdEngineTmqOptions(
                        "11111111-1111-1111-1111-111111111111", "node-1", "execution-1", 1,
                        "localhost:6041", "root", "secret", false, TOPIC, "earliest", 10_000
                ),
                SCHEMA,
                "safe-group",
                start,
                end,
                factory
        );
    }

    private static Map<String, Object> values() {
        Map<String, Object> values = new HashMap<>();
        values.put("ts", Timestamp.from(Instant.parse("2024-01-01T00:00:00.123456Z")));
        values.put("active", 1);
        values.put("count", "7");
        values.put("amount", new BigDecimal("12.30"));
        values.put("name", "meter-a".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        values.put("payload", ByteBuffer.wrap(new byte[]{1, 2, 3}));
        return Map.copyOf(values);
    }

    @SafeVarargs
    private static ConsumerRecords<Map<String, Object>> records(ConsumerRecord<Map<String, Object>>... records) {
        ConsumerRecords<Map<String, Object>> result = new ConsumerRecords<>();
        Arrays.stream(records).forEach(record -> result.put(
                new TopicPartition(record.getTopic(), record.getVGroupId()), record));
        return result;
    }

    private static ConsumerRecord<Map<String, Object>> record(
            String topic,
            int vGroup,
            long offset,
            Map<String, Object> values
    ) {
        return new ConsumerRecord.Builder<Map<String, Object>>()
                .topic(topic)
                .dbName("meters")
                .vGroupId(vGroup)
                .offset(offset)
                .messageType(TmqMessageType.TMQ_RES_DATA)
                .value(values)
                .build();
    }

    private static final class FakeFactory implements TdEngineTmqConsumerFactory {
        private final FakeConsumer consumer;
        private int created;

        private FakeFactory(FakeConsumer consumer) {
            this.consumer = consumer;
        }

        @Override
        public TdEngineTmqConsumer create(TdEngineTmqOptions options, String groupId, String clientId) {
            created++;
            return consumer;
        }
    }

    private static final class FakeConsumer implements TdEngineTmqConsumer {
        private final Set<TopicPartition> assignments;
        private final Queue<ConsumerRecords<Map<String, Object>>> polls = new ArrayDeque<>();
        private final Map<TopicPartition, Long> positions = new HashMap<>();
        private final Map<TopicPartition, Long> seekOffsets = new HashMap<>();
        private boolean assignmentEstablished;
        private boolean unsubscribed;
        private boolean closed;

        @SafeVarargs
        private FakeConsumer(Set<TopicPartition> assignments, ConsumerRecords<Map<String, Object>>... polls) {
            this.assignments = assignments;
            this.polls.addAll(Arrays.asList(polls));
        }

        @Override public void subscribe(Collection<String> topics) {}

        @Override public void unsubscribe() {
            unsubscribed = true;
        }

        @Override
        public ConsumerRecords<Map<String, Object>> poll(Duration timeout) {
            if (!assignmentEstablished) {
                assignmentEstablished = true;
                return ConsumerRecords.emptyRecord();
            }
            ConsumerRecords<Map<String, Object>> records = polls.poll();
            if (records == null) return ConsumerRecords.emptyRecord();
            for (ConsumerRecord<Map<String, Object>> record : records) {
                positions.put(
                        new TopicPartition(record.getTopic(), record.getVGroupId()),
                        record.getOffset() + 1
                );
            }
            return records;
        }

        @Override
        public void seek(TopicPartition partition, long offset) {
            seekOffsets.put(partition, offset);
            positions.put(partition, offset);
        }

        @Override public long position(TopicPartition partition) { return positions.getOrDefault(partition, 0L); }
        @Override public Map<TopicPartition, Long> beginningOffsets(String topic) { return Map.of(); }
        @Override public Map<TopicPartition, Long> endOffsets(String topic) { return Map.of(); }
        @Override public Set<TopicPartition> assignment() { return assignmentEstablished ? assignments : Set.of(); }

        @Override
        public void close() throws SQLException {
            closed = true;
        }
    }
}
