package cn.superhuang.datascalpel.taskengine.tdengine.tmq;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.spark.sql.connector.read.streaming.Offset;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

public final class TdEngineTmqOffset extends Offset implements Serializable {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final String topic;
    private final Map<Integer, Long> vGroups;

    public TdEngineTmqOffset(String topic, Map<Integer, Long> vGroups) {
        if (topic == null || topic.isBlank() || vGroups == null || vGroups.isEmpty()) {
            throw invalid("TMQ Offset 缺少 Topic 或 VGroup");
        }
        TreeMap<Integer, Long> normalized = new TreeMap<>();
        vGroups.forEach((vGroup, offset) -> {
            if (vGroup == null || vGroup < 0 || offset == null || offset < 0) {
                throw invalid("TMQ Offset 包含非法 VGroup 或位置");
            }
            normalized.put(vGroup, offset);
        });
        this.topic = topic;
        this.vGroups = Map.copyOf(normalized);
    }

    public String topic() {
        return topic;
    }

    public Map<Integer, Long> vGroups() {
        return vGroups;
    }

    @Override
    public String json() {
        try {
            Map<String, Long> serialized = new LinkedHashMap<>();
            new TreeMap<>(vGroups).forEach((key, value) -> serialized.put(Integer.toString(key), value));
            return MAPPER.writeValueAsString(new Payload(1, topic, serialized));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize TDengine TMQ offset", exception);
        }
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof TdEngineTmqOffset offset
                && topic.equals(offset.topic)
                && vGroups.equals(offset.vGroups);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(topic, vGroups);
    }

    @Override
    public String toString() {
        return json();
    }

    public static TdEngineTmqOffset parse(String json) {
        try {
            Payload payload = MAPPER.readValue(json, Payload.class);
            if (payload.version() != 1 || payload.topic() == null || payload.vGroups() == null) {
                throw invalid("TMQ Checkpoint Offset 版本或结构无效");
            }
            Map<Integer, Long> offsets = new LinkedHashMap<>();
            payload.vGroups().forEach((key, value) -> {
                try {
                    int vGroup = Integer.parseInt(key);
                    if (offsets.putIfAbsent(vGroup, value) != null) {
                        throw invalid("TMQ Checkpoint 包含重复 VGroup");
                    }
                } catch (NumberFormatException exception) {
                    throw invalid("TMQ Checkpoint VGroup 编号无效");
                }
            });
            if (offsets.isEmpty()) {
                throw invalid("TMQ Checkpoint 必须包含至少一个 VGroup");
            }
            return new TdEngineTmqOffset(payload.topic(), offsets);
        } catch (TdEngineTmqException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new TdEngineTmqException(
                    "TDENGINE_TMQ_VGROUP_CHANGED", "TMQ Checkpoint Offset 无法解析", false, exception);
        }
    }

    private static TdEngineTmqException invalid(String message) {
        return new TdEngineTmqException("TDENGINE_TMQ_VGROUP_CHANGED", message, false);
    }

    private record Payload(int version, String topic, Map<String, Long> vGroups) {
    }
}
