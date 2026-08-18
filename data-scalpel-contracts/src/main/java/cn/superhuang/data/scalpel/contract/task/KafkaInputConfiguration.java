package cn.superhuang.data.scalpel.contract.task;

public record KafkaInputConfiguration(
        String dataSourceId,
        String topic,
        KafkaValueSchema valueSchema,
        String outputTableName,
        KafkaStartingOffsets startingOffsets,
        Integer triggerIntervalSeconds
) {
    public static final int DEFAULT_TRIGGER_INTERVAL_SECONDS = 10;

    public KafkaInputConfiguration(
            String dataSourceId,
            String topic,
            KafkaValueSchema valueSchema,
            String outputTableName,
            KafkaStartingOffsets startingOffsets
    ) {
        this(
                dataSourceId, topic, valueSchema, outputTableName, startingOffsets,
                DEFAULT_TRIGGER_INTERVAL_SECONDS
        );
    }
}
