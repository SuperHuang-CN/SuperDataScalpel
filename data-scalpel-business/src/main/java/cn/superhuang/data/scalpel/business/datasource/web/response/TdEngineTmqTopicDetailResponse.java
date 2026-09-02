package cn.superhuang.data.scalpel.business.datasource.web.response;

import cn.superhuang.data.scalpel.dialect.api.DatabaseDialect;
import cn.superhuang.data.scalpel.dialect.model.TdEngineTmqTopic;

import java.time.Instant;
import java.util.List;

public record TdEngineTmqTopicDetailResponse(
        String topicName,
        String databaseName,
        String supertableName,
        Instant createdAt,
        boolean supported,
        String unsupportedReason,
        String definitionFingerprint,
        String legacyDefinitionFingerprint,
        List<ColumnMetadataResponse> columns,
        String timePrecision
) {
    public static TdEngineTmqTopicDetailResponse from(TdEngineTmqTopic topic, DatabaseDialect dialect) {
        List<ColumnMetadataResponse> columns = topic.tableMetadata() == null
                ? List.of()
                : topic.tableMetadata().columns().stream()
                .map(column -> ColumnMetadataResponse.from(column, dialect))
                .toList();
        return new TdEngineTmqTopicDetailResponse(
                topic.topicName(), topic.databaseName(), topic.supertableName(), topic.createdAt(),
                topic.supported(), topic.unsupportedReason(), topic.definitionFingerprint(),
                topic.legacyDefinitionFingerprint(), columns, topic.timePrecision()
        );
    }
}
