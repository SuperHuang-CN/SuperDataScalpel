package cn.superhuang.data.scalpel.contract.task;

import java.util.UUID;

public record KafkaInputConfiguration(
        UUID dataSourceId,
        String topic,
        KafkaValueSchema valueSchema,
        String outputTableName,
        KafkaStartingOffsets startingOffsets
) {
}
