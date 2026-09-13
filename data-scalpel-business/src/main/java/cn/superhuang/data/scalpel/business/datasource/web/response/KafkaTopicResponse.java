package cn.superhuang.data.scalpel.business.datasource.web.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Kafka Topic 及当前可取得的分区健康摘要")
public record KafkaTopicResponse(
        @Schema(description = "Topic 名称") String name,
        @Schema(description = "Kafka 返回的 Topic ID；集群版本不支持时为空") String topicId,
        @Schema(description = "是否为 Kafka 内部 Topic") boolean internal,
        @Schema(description = "是否成功读取分区与副本元数据；为 false 时后续统计字段可能为空") boolean metadataAvailable,
        @Schema(description = "Topic 分区数量；元数据不可用时为空") Integer partitionCount,
        @Schema(description = "各分区副本因子的最小值；元数据不可用时为空") Integer minimumReplicationFactor,
        @Schema(description = "各分区副本因子的最大值；元数据不可用时为空") Integer maximumReplicationFactor,
        @Schema(description = "副本未完全同步的分区数量；元数据不可用时为空") Integer underReplicatedPartitionCount,
        @Schema(description = "当前无可用 Leader 的分区数量；元数据不可用时为空") Integer unavailableLeaderPartitionCount
) {
}
