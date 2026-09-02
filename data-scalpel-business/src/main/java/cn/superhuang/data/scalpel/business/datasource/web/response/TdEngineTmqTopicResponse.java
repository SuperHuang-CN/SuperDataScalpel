package cn.superhuang.data.scalpel.business.datasource.web.response;

import cn.superhuang.data.scalpel.dialect.model.TdEngineTmqTopic;

import java.time.Instant;

public record TdEngineTmqTopicResponse(
        String topicName,
        String databaseName,
        String supertableName,
        Instant createdAt,
        boolean supported,
        String unsupportedReason,
        String definitionFingerprint,
        String legacyDefinitionFingerprint
) {
    public static TdEngineTmqTopicResponse from(TdEngineTmqTopic topic) {
        return new TdEngineTmqTopicResponse(
                topic.topicName(), topic.databaseName(), topic.supertableName(), topic.createdAt(),
                topic.supported(), topic.unsupportedReason(), topic.definitionFingerprint(),
                topic.legacyDefinitionFingerprint()
        );
    }
}
