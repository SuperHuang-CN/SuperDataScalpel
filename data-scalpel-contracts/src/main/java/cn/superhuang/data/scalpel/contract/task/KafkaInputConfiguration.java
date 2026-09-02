package cn.superhuang.data.scalpel.contract.task;

import java.util.List;

public record KafkaInputConfiguration(
        String dataSourceId,
        String topic,
        KafkaValueSchema valueSchema,
        String outputTableName,
        KafkaStartingOffsets startingOffsets,
        Integer triggerIntervalSeconds,
        KafkaInputValueFormat valueFormat,
        List<KafkaInputMetadataField> metadataFields
) {
    public static final int DEFAULT_TRIGGER_INTERVAL_SECONDS = 10;

    public KafkaInputConfiguration {
        metadataFields = metadataFields == null ? null : List.copyOf(metadataFields);
    }

    public KafkaInputConfiguration(
            String dataSourceId,
            String topic,
            KafkaValueSchema valueSchema,
            String outputTableName,
            KafkaStartingOffsets startingOffsets,
            Integer triggerIntervalSeconds
    ) {
        this(
                dataSourceId, topic, valueSchema, outputTableName, startingOffsets,
                triggerIntervalSeconds, null, null
        );
    }

    public KafkaInputConfiguration(
            String dataSourceId,
            String topic,
            KafkaValueSchema valueSchema,
            String outputTableName,
            KafkaStartingOffsets startingOffsets
    ) {
        this(
                dataSourceId, topic, valueSchema, outputTableName, startingOffsets,
                DEFAULT_TRIGGER_INTERVAL_SECONDS, null, null
        );
    }

    public KafkaInputValueFormat effectiveValueFormat() {
        return valueFormat == null ? KafkaInputValueFormat.JSON : valueFormat;
    }

    public List<KafkaInputMetadataField> effectiveMetadataFields() {
        return metadataFields == null ? List.of() : metadataFields;
    }
}
