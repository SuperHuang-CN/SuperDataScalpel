package cn.superhuang.datascalpel.sdk.testkit;

import java.time.Instant;
import java.util.Arrays;

public record TestKafkaRecord(
        byte[] key,
        byte[] value,
        String topic,
        int partition,
        long offset,
        Instant timestamp,
        int timestampType
) {
    public TestKafkaRecord {
        key = key == null ? null : Arrays.copyOf(key, key.length);
        value = value == null ? null : Arrays.copyOf(value, value.length);
        if (topic == null || topic.isBlank()) throw new IllegalArgumentException("topic must not be blank");
        if (partition < 0 || offset < 0 || timestamp == null) {
            throw new IllegalArgumentException("Kafka record metadata is invalid");
        }
    }

    @Override public byte[] key() { return key == null ? null : Arrays.copyOf(key, key.length); }
    @Override public byte[] value() { return value == null ? null : Arrays.copyOf(value, value.length); }
}
