package cn.superhuang.data.scalpel.business.datasource.web.response;

public record KafkaTopicResponse(
        String name,
        String topicId,
        boolean internal,
        boolean metadataAvailable,
        Integer partitionCount,
        Integer minimumReplicationFactor,
        Integer maximumReplicationFactor,
        Integer underReplicatedPartitionCount,
        Integer unavailableLeaderPartitionCount
) {
}
