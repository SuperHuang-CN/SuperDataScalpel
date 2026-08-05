package cn.superhuang.data.scalpel.contract.task;

public record KafkaInputConfiguration(
        String dataSourceId,
        String topic,
        KafkaValueSchema valueSchema,
        String outputTableName,
        KafkaStartingOffsets startingOffsets
) {
}
