package cn.superhuang.data.scalpel.contract.task;

public record TdEngineTmqInputConfiguration(
        String dataSourceId,
        String topicName,
        String catalogName,
        String supertableName,
        String topicDefinitionFingerprint,
        String outputTableName,
        TdEngineTmqStartingOffsets startingOffsets,
        Integer maxOffsetsPerVGroupPerTrigger,
        Integer triggerIntervalSeconds
) {
    public static final int DEFAULT_MAX_OFFSETS_PER_VGROUP_PER_TRIGGER = 10_000;
    public static final int MAX_OFFSETS_PER_VGROUP_PER_TRIGGER = 1_000_000;
    public static final int DEFAULT_TRIGGER_INTERVAL_SECONDS = 10;

    public TdEngineTmqInputConfiguration {
        startingOffsets = startingOffsets == null ? TdEngineTmqStartingOffsets.EARLIEST : startingOffsets;
        maxOffsetsPerVGroupPerTrigger = maxOffsetsPerVGroupPerTrigger == null
                ? DEFAULT_MAX_OFFSETS_PER_VGROUP_PER_TRIGGER
                : maxOffsetsPerVGroupPerTrigger;
    }

    public TdEngineTmqInputConfiguration(
            String dataSourceId,
            String topicName,
            String catalogName,
            String supertableName,
            String topicDefinitionFingerprint,
            String outputTableName,
            TdEngineTmqStartingOffsets startingOffsets,
            Integer maxOffsetsPerVGroupPerTrigger
    ) {
        this(
                dataSourceId, topicName, catalogName, supertableName,
                topicDefinitionFingerprint, outputTableName, startingOffsets,
                maxOffsetsPerVGroupPerTrigger, DEFAULT_TRIGGER_INTERVAL_SECONDS
        );
    }
}
