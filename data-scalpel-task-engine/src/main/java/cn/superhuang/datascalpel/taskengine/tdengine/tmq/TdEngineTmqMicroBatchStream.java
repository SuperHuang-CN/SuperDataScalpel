package cn.superhuang.datascalpel.taskengine.tdengine.tmq;

import com.taosdata.jdbc.tmq.TopicPartition;
import org.apache.spark.sql.connector.read.InputPartition;
import org.apache.spark.sql.connector.read.PartitionReaderFactory;
import org.apache.spark.sql.connector.read.streaming.MicroBatchStream;
import org.apache.spark.sql.connector.read.streaming.Offset;
import org.apache.spark.sql.connector.read.streaming.ReadLimit;
import org.apache.spark.sql.connector.read.streaming.SupportsAdmissionControl;
import org.apache.spark.sql.types.StructType;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public final class TdEngineTmqMicroBatchStream implements MicroBatchStream, SupportsAdmissionControl {
    private final StructType schema;
    private final TdEngineTmqOptions options;
    private final String groupId;
    private final TdEngineTmqConsumerFactory consumerFactory;
    private volatile TdEngineTmqOffset lastCommitted;

    TdEngineTmqMicroBatchStream(StructType schema, TdEngineTmqOptions options, String checkpointLocation) {
        this(schema, options, checkpointLocation, DriverTdEngineTmqConsumerFactory.INSTANCE);
    }

    TdEngineTmqMicroBatchStream(
            StructType schema,
            TdEngineTmqOptions options,
            String checkpointLocation,
            TdEngineTmqConsumerFactory consumerFactory
    ) {
        this.schema = schema;
        this.options = options;
        this.groupId = options.groupId(checkpointLocation);
        this.consumerFactory = consumerFactory;
    }

    @Override
    public Offset initialOffset() {
        if (lastCommitted != null) return lastCommitted;
        if (options.initialSourceOffset() != null) {
            TdEngineTmqOffset restored = TdEngineTmqOffset.parse(options.initialSourceOffset());
            if (!options.topic().equals(restored.topic())) {
                throw new TdEngineTmqException(
                        "TDENGINE_TMQ_TOPIC_CHANGED",
                        "继承的 TMQ Offset 不属于当前 Topic",
                        false
                );
            }
            lastCommitted = restored;
            return restored;
        }
        try (TdEngineTmqConsumer consumer = consumerFactory.create(options,
                groupId, options.clientId("offsets-" + TdEngineTmqOptions.attemptId()))) {
            Map<Integer, Long> offsets = vGroupOffsets(options.startingOffsets().equals("earliest")
                    ? consumer.beginningOffsets(options.topic())
                    : consumer.endOffsets(options.topic()));
            if (offsets.isEmpty()) {
                throw new TdEngineTmqException(
                        "TDENGINE_TMQ_TOPIC_NOT_FOUND", "TMQ Topic 不存在或没有可用 VGroup", false);
            }
            lastCommitted = new TdEngineTmqOffset(options.topic(), offsets);
            return lastCommitted;
        } catch (SQLException exception) {
            throw classifySql("读取 TMQ 初始 Offset 失败", exception);
        }
    }

    @Override
    public Offset latestOffset() {
        TdEngineTmqOffset start = lastCommitted == null
                ? (TdEngineTmqOffset) initialOffset() : lastCommitted;
        return latestOffset(start, ReadLimit.allAvailable());
    }

    @Override
    public ReadLimit getDefaultReadLimit() {
        // The source applies its per-VGroup limit while constructing the end offset.
        return ReadLimit.allAvailable();
    }

    @Override
    public Offset latestOffset(Offset startOffset, ReadLimit readLimit) {
        TdEngineTmqOffset start = requireOffset(startOffset);
        try (TdEngineTmqConsumer consumer = consumerFactory.create(options,
                groupId, options.clientId("latest-" + TdEngineTmqOptions.attemptId()))) {
            Map<Integer, Long> beginning = vGroupOffsets(consumer.beginningOffsets(options.topic()));
            Map<Integer, Long> latest = vGroupOffsets(consumer.endOffsets(options.topic()));
            requireSameVGroups(start.vGroups().keySet(), beginning.keySet(), latest.keySet());
            Map<Integer, Long> bounded = new TreeMap<>();
            for (int vGroup : start.vGroups().keySet()) {
                long current = start.vGroups().get(vGroup);
                if (beginning.get(vGroup) > current) {
                    throw new TdEngineTmqException(
                            "TDENGINE_TMQ_OFFSET_EXPIRED",
                            "TMQ Checkpoint Offset 已超出当前 WAL 保留范围", false);
                }
                if (latest.get(vGroup) < current) {
                    throw new TdEngineTmqException(
                            "TDENGINE_TMQ_VGROUP_CHANGED", "TMQ VGroup Offset 已回退", false);
                }
                bounded.put(vGroup, Math.min(
                        latest.get(vGroup), current + options.maxOffsetsPerVGroupPerTrigger()));
            }
            return new TdEngineTmqOffset(options.topic(), bounded);
        } catch (SQLException exception) {
            throw classifySql("读取 TMQ 最新 Offset 失败", exception);
        }
    }

    @Override
    public Offset deserializeOffset(String json) {
        TdEngineTmqOffset offset = TdEngineTmqOffset.parse(json);
        if (!options.topic().equals(offset.topic())) {
            throw new TdEngineTmqException(
                    "TDENGINE_TMQ_TOPIC_CHANGED", "Checkpoint Topic 与当前节点配置不一致", false);
        }
        return offset;
    }

    @Override
    public InputPartition[] planInputPartitions(Offset start, Offset end) {
        TdEngineTmqOffset startOffset = requireOffset(start);
        TdEngineTmqOffset endOffset = requireOffset(end);
        requireSameVGroups(startOffset.vGroups().keySet(), endOffset.vGroups().keySet());
        for (int vGroup : startOffset.vGroups().keySet()) {
            if (endOffset.vGroups().get(vGroup) < startOffset.vGroups().get(vGroup)) {
                throw new TdEngineTmqException(
                        "TDENGINE_TMQ_VGROUP_CHANGED", "TMQ 批次结束 Offset 小于开始 Offset", false);
            }
        }
        return new InputPartition[]{new TdEngineTmqInputPartition(
                options, schema, groupId, startOffset.vGroups(), endOffset.vGroups(), consumerFactory)};
    }

    @Override
    public PartitionReaderFactory createReaderFactory() {
        return partition -> {
            if (!(partition instanceof TdEngineTmqInputPartition tmqPartition)) {
                throw new IllegalArgumentException("Unexpected TDengine TMQ partition");
            }
            return new TdEngineTmqPartitionReader(tmqPartition);
        };
    }

    @Override
    public void commit(Offset end) {
        TdEngineTmqOffset committed = requireOffset(end);
        if (lastCommitted != null) {
            requireSameVGroups(lastCommitted.vGroups().keySet(), committed.vGroups().keySet());
            for (int vGroup : lastCommitted.vGroups().keySet()) {
                if (committed.vGroups().get(vGroup) < lastCommitted.vGroups().get(vGroup)) {
                    throw new TdEngineTmqException(
                            "TDENGINE_TMQ_VGROUP_CHANGED",
                            "Spark 提交的 TMQ Offset 小于已提交位置",
                            false
                    );
                }
            }
        }
        lastCommitted = committed;
        // Spark checkpoint is authoritative. Do not commit TMQ consumer offsets here.
    }

    @Override
    public void stop() {
        // Consumers are batch-scoped and closed by offset lookup or the partition reader.
    }

    private TdEngineTmqOffset requireOffset(Offset offset) {
        if (!(offset instanceof TdEngineTmqOffset tmq) || !options.topic().equals(tmq.topic())) {
            throw new TdEngineTmqException(
                    "TDENGINE_TMQ_TOPIC_CHANGED", "Spark Offset 不属于当前 TMQ Topic", false);
        }
        return tmq;
    }

    @SafeVarargs
    private static void requireSameVGroups(Set<Integer> expected, Set<Integer>... actualSets) {
        for (Set<Integer> actual : actualSets) {
            if (!expected.equals(actual)) {
                throw new TdEngineTmqException(
                        "TDENGINE_TMQ_VGROUP_CHANGED", "TMQ VGroup 集合已变化，需要新建部署", false);
            }
        }
    }

    private Map<Integer, Long> vGroupOffsets(Map<TopicPartition, Long> source) {
        Map<Integer, Long> result = new LinkedHashMap<>();
        source.forEach((partition, offset) -> {
            if (!options.topic().equals(partition.getTopic())
                    || result.putIfAbsent(partition.getVGroupId(), offset) != null) {
                throw new TdEngineTmqException(
                        "TDENGINE_TMQ_VGROUP_CHANGED",
                        "TMQ Offset 查询返回了不一致的 Topic 或重复 VGroup",
                        false
                );
            }
        });
        return Map.copyOf(result);
    }

    static TdEngineTmqException classifySql(String message, SQLException exception) {
        String state = exception.getSQLState();
        String detail = exception.getMessage() == null ? "" : exception.getMessage().toLowerCase(java.util.Locale.ROOT);
        if (state != null && state.startsWith("28")
                || detail.contains("authentication")
                || detail.contains("unauthorized")
                || detail.contains("invalid user")
                || detail.contains("invalid password")) {
            return new TdEngineTmqException(
                    "TDENGINE_TMQ_AUTHENTICATION_FAILED", "TDengine TMQ 认证失败", false, exception);
        }
        if (detail.contains("topic") && (detail.contains("not exist") || detail.contains("not found"))) {
            return new TdEngineTmqException(
                    "TDENGINE_TMQ_TOPIC_NOT_FOUND", "TMQ Topic 不存在", false, exception);
        }
        if (state != null && state.startsWith("08")
                || detail.contains("connection refused")
                || detail.contains("connection reset")
                || detail.contains("connection timed out")
                || detail.contains("unknown host")
                || detail.contains("websocket") && detail.contains("connect")) {
            return new TdEngineTmqException(
                    "TDENGINE_TMQ_NETWORK_ERROR", "TDengine TMQ 网络连接失败", true, exception);
        }
        return new TdEngineTmqException("TDENGINE_TMQ_POLL_FAILED", message, true, exception);
    }
}
