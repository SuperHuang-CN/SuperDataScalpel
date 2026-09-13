package cn.superhuang.data.scalpel.business.datasource.web.response;

import cn.superhuang.data.scalpel.dialect.api.DatabaseDialect;
import cn.superhuang.data.scalpel.dialect.model.TdEngineTmqTopic;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(description = "TDengine TMQ Topic 详情及对应超级表结构；不返回原始 Topic SQL、子表名或凭据")
public record TdEngineTmqTopicDetailResponse(
        @Schema(description = "Topic 名称") String topicName,
        @Schema(description = "Topic 绑定的数据库名称") String databaseName,
        @Schema(description = "Topic 绑定的完整超级表名称") String supertableName,
        @Schema(description = "Topic 创建时间；数据库未提供时为空") Instant createdAt,
        @Schema(description = "是否满足平台订阅约束；为 false 时必须读取 unsupportedReason") boolean supported,
        @Schema(description = "不支持订阅的原因；supported 为 true 时为空") String unsupportedReason,
        @Schema(description = "当前算法生成的 Topic 定义指纹，用于发布与运行时一致性校验") String definitionFingerprint,
        @Schema(description = "兼容历史任务定义的旧算法指纹；不存在时为空") String legacyDefinitionFingerprint,
        @Schema(description = "Topic 对应超级表的列和标签元数据；无法读取定义时为空列表") List<ColumnMetadataResponse> columns,
        @Schema(description = "TDengine 时间戳精度，例如 ms、us 或 ns") String timePrecision
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
