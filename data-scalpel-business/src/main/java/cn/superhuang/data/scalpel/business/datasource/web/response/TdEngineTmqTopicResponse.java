package cn.superhuang.data.scalpel.business.datasource.web.response;

import cn.superhuang.data.scalpel.dialect.model.TdEngineTmqTopic;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "TDengine TMQ Topic 摘要；仅不含元数据消息且定义为 SELECT * FROM database.supertable、所有字段均可无损映射的平台支持 Topic 可用于任务订阅")
public record TdEngineTmqTopicResponse(
        @Schema(description = "Topic 名称") String topicName,
        @Schema(description = "Topic 绑定的数据库名称") String databaseName,
        @Schema(description = "Topic 绑定的完整超级表名称") String supertableName,
        @Schema(description = "Topic 创建时间；数据库未提供时为空") Instant createdAt,
        @Schema(description = "是否满足平台订阅约束；为 false 时必须读取 unsupportedReason") boolean supported,
        @Schema(description = "不支持订阅的原因；supported 为 true 时为空") String unsupportedReason,
        @Schema(description = "当前算法生成的 Topic 定义指纹，用于发布与运行时一致性校验") String definitionFingerprint,
        @Schema(description = "兼容历史任务定义的旧算法指纹；不存在时为空") String legacyDefinitionFingerprint
) {
    public static TdEngineTmqTopicResponse from(TdEngineTmqTopic topic) {
        return new TdEngineTmqTopicResponse(
                topic.topicName(), topic.databaseName(), topic.supertableName(), topic.createdAt(),
                topic.supported(), topic.unsupportedReason(), topic.definitionFingerprint(),
                topic.legacyDefinitionFingerprint()
        );
    }
}
