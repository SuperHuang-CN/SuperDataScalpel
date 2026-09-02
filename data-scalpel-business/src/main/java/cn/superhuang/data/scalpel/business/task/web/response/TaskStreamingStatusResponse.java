package cn.superhuang.data.scalpel.business.task.web.response;

import java.util.UUID;

public record TaskStreamingStatusResponse(
        UUID taskId,
        TaskStreamingDeploymentResponse deployment,
        long tmqConsumerGroupCleanupPendingCount,
        long tmqConsumerGroupCleanupFailedCount
) {
}
