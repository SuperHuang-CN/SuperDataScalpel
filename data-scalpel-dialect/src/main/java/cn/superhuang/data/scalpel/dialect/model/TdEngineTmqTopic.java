package cn.superhuang.data.scalpel.dialect.model;

import java.time.Instant;

/** Safe TDengine TMQ topic metadata. The original topic SQL is deliberately not exposed. */
public record TdEngineTmqTopic(
        String topicName,
        String databaseName,
        String supertableName,
        Instant createdAt,
        boolean supported,
        String unsupportedReason,
        String definitionFingerprint,
        String legacyDefinitionFingerprint,
        String timePrecision,
        TableMetadata tableMetadata
) {
}
