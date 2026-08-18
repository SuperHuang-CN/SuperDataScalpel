package cn.superhuang.data.scalpel.contract.task;

import java.util.List;

public record MetadataTdEngineTmqTopic(
        String topicName,
        String catalogName,
        String supertableName,
        String definitionFingerprint,
        String timePrecision,
        List<CanvasColumnSchema> columns
) {
    public MetadataTdEngineTmqTopic {
        columns = columns == null ? List.of() : List.copyOf(columns);
    }
}
