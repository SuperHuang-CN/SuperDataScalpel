package cn.superhuang.datascalpel.taskengine.tdengine.tmq;

import com.taosdata.jdbc.tmq.ConsumerRecords;
import com.taosdata.jdbc.tmq.TopicPartition;
import org.apache.spark.sql.connector.read.InputPartition;
import org.apache.spark.sql.connector.read.streaming.ReadLimit;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructType;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TdEngineTmqMicroBatchStreamTest {
    private static final StructType SCHEMA = new StructType()
            .add("ts", DataTypes.TimestampType, false)
            .add("value", DataTypes.IntegerType, true);

    @Test
    void usesStartingOffsetsOnlyForNewCheckpointAndClosesOffsetConsumer() {
        FakeFactory factory = new FakeFactory(offsets(10, 20), offsets(100, 200));
        TdEngineTmqMicroBatchStream stream = stream("earliest", 10, factory);

        assertEquals(Map.of(0, 10L, 1, 20L), offset(stream.initialOffset()).vGroups());
        assertEquals(1, factory.created);
        assertTrue(factory.last.closed);

        FakeFactory latestFactory = new FakeFactory(offsets(10, 20), offsets(100, 200));
        assertEquals(
                Map.of(0, 100L, 1, 200L),
                offset(stream("latest", 10, latestFactory).initialOffset()).vGroups()
        );
    }

    @Test
    void boundsEveryVGroupFromSparkCheckpointStart() {
        FakeFactory factory = new FakeFactory(offsets(10, 20), offsets(100, 200));
        TdEngineTmqMicroBatchStream stream = stream("earliest", 25, factory);
        TdEngineTmqOffset restored = new TdEngineTmqOffset("meters_topic", Map.of(0, 40L, 1, 180L));

        TdEngineTmqOffset end = offset(stream.latestOffset(restored, ReadLimit.allAvailable()));

        assertEquals(Map.of(0, 65L, 1, 200L), end.vGroups());
        assertTrue(factory.last.closed);
    }

    @Test
    void deserializeDoesNotReplaceCheckpointAuthorityAndCommitNeverCallsTmqCommit() {
        FakeFactory factory = new FakeFactory(offsets(0, 0), offsets(100, 100));
        TdEngineTmqMicroBatchStream stream = stream("earliest", 10, factory);
        TdEngineTmqOffset committed = new TdEngineTmqOffset("meters_topic", Map.of(0, 50L, 1, 60L));
        stream.commit(committed);

        stream.deserializeOffset(new TdEngineTmqOffset("meters_topic", Map.of(0, 5L, 1, 6L)).json());

        assertEquals(Map.of(0, 60L, 1, 70L), offset(stream.latestOffset()).vGroups());
        assertFalse(factory.last.commitCalled);
    }

    @Test
    void rejectsExpiredOffsetsAndVGroupChanges() {
        TdEngineTmqMicroBatchStream expired = stream(
                "earliest", 10, new FakeFactory(offsets(50, 20), offsets(100, 100)));
        TdEngineTmqException expiredFailure = assertThrows(
                TdEngineTmqException.class,
                () -> expired.latestOffset(
                        new TdEngineTmqOffset("meters_topic", Map.of(0, 40L, 1, 30L)),
                        ReadLimit.allAvailable()
                )
        );
        assertEquals("TDENGINE_TMQ_OFFSET_EXPIRED", expiredFailure.code());

        TdEngineTmqMicroBatchStream changed = stream(
                "earliest", 10,
                new FakeFactory(Map.of(new TopicPartition("meters_topic", 0), 0L),
                        Map.of(new TopicPartition("meters_topic", 0), 10L))
        );
        TdEngineTmqException changedFailure = assertThrows(
                TdEngineTmqException.class,
                () -> changed.latestOffset(
                        new TdEngineTmqOffset("meters_topic", Map.of(0, 0L, 1, 0L)),
                        ReadLimit.allAvailable()
                )
        );
        assertEquals("TDENGINE_TMQ_VGROUP_CHANGED", changedFailure.code());
    }

    @Test
    void plansOneInputPartitionWithSparkBatchRange() {
        FakeFactory factory = new FakeFactory(offsets(0, 0), offsets(10, 10));
        TdEngineTmqMicroBatchStream stream = stream("earliest", 10, factory);
        TdEngineTmqOffset start = new TdEngineTmqOffset("meters_topic", Map.of(0, 1L, 1, 2L));
        TdEngineTmqOffset end = new TdEngineTmqOffset("meters_topic", Map.of(0, 5L, 1, 8L));

        InputPartition[] partitions = stream.planInputPartitions(start, end);

        assertEquals(1, partitions.length);
        TdEngineTmqInputPartition partition = (TdEngineTmqInputPartition) partitions[0];
        assertEquals(start.vGroups(), partition.startOffsets());
        assertEquals(end.vGroups(), partition.endOffsets());
        assertEquals(factory, partition.consumerFactory());
    }

    @Test
    void classifiesDriverFailuresWithoutLeakingDriverMessage() {
        assertEquals(
                "TDENGINE_TMQ_AUTHENTICATION_FAILED",
                TdEngineTmqMicroBatchStream.classifySql(
                        "ignored", new SQLException("invalid password", (String) null)).code()
        );
        TdEngineTmqException network = TdEngineTmqMicroBatchStream.classifySql(
                "ignored", new SQLException("WebSocket connect failed", (String) null));
        assertEquals("TDENGINE_TMQ_NETWORK_ERROR", network.code());
        assertTrue(network.retryable());
    }

    @Test
    void keepsCredentialsOutOfSparkDiagnosticStrings() {
        TdEngineTmqOptions options = options("earliest", 10);
        TdEngineTmqInputPartition partition = new TdEngineTmqInputPartition(
                options, SCHEMA, "safe-group", Map.of(0, 1L), Map.of(0, 2L),
                new FakeFactory(offsets(0, 0), offsets(10, 10))
        );

        assertFalse(options.toString().contains("secret"));
        assertFalse(options.toString().contains("root"));
        assertFalse(partition.toString().contains("secret"));
        assertFalse(partition.toString().contains("/safe/checkpoint"));
    }

    private static TdEngineTmqMicroBatchStream stream(
            String startingOffsets,
            int maximum,
            FakeFactory factory
    ) {
        return new TdEngineTmqMicroBatchStream(
                SCHEMA,
                options(startingOffsets, maximum),
                "/safe/checkpoint",
                factory
        );
    }

    private static TdEngineTmqOptions options(String startingOffsets, int maximum) {
        return new TdEngineTmqOptions(
                "11111111-1111-1111-1111-111111111111",
                "node-1",
                "execution-1",
                1,
                "localhost:6041",
                "root",
                "secret",
                false,
                "meters_topic",
                startingOffsets,
                maximum
        );
    }

    private static TdEngineTmqOffset offset(Object offset) {
        return (TdEngineTmqOffset) offset;
    }

    private static Map<TopicPartition, Long> offsets(long zero, long one) {
        Map<TopicPartition, Long> offsets = new LinkedHashMap<>();
        offsets.put(new TopicPartition("meters_topic", 0), zero);
        offsets.put(new TopicPartition("meters_topic", 1), one);
        return Map.copyOf(offsets);
    }

    private static final class FakeFactory implements TdEngineTmqConsumerFactory {
        private final Map<TopicPartition, Long> beginning;
        private final Map<TopicPartition, Long> end;
        private int created;
        private FakeConsumer last;

        private FakeFactory(Map<TopicPartition, Long> beginning, Map<TopicPartition, Long> end) {
            this.beginning = beginning;
            this.end = end;
        }

        @Override
        public TdEngineTmqConsumer create(TdEngineTmqOptions options, String groupId, String clientId) {
            created++;
            last = new FakeConsumer(beginning, end);
            return last;
        }
    }

    private static final class FakeConsumer implements TdEngineTmqConsumer {
        private final Map<TopicPartition, Long> beginning;
        private final Map<TopicPartition, Long> end;
        private boolean closed;
        private boolean commitCalled;

        private FakeConsumer(Map<TopicPartition, Long> beginning, Map<TopicPartition, Long> end) {
            this.beginning = beginning;
            this.end = end;
        }

        @Override public void subscribe(Collection<String> topics) {}
        @Override public void unsubscribe() {}
        @Override public ConsumerRecords<Map<String, Object>> poll(Duration timeout) {
            return ConsumerRecords.emptyRecord();
        }
        @Override public void seek(TopicPartition partition, long offset) {}
        @Override public long position(TopicPartition partition) { return end.get(partition); }
        @Override public Map<TopicPartition, Long> beginningOffsets(String topic) { return beginning; }
        @Override public Map<TopicPartition, Long> endOffsets(String topic) { return end; }
        @Override public Set<TopicPartition> assignment() { return beginning.keySet(); }
        @Override public void close() { closed = true; }
    }
}
