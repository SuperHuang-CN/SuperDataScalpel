package cn.superhuang.data.scalpel.business.task.web.response;

import cn.superhuang.data.scalpel.business.task.domain.TaskStreamingConfiguration;

import java.time.Instant;
import java.util.UUID;

public record TaskStreamingConfigurationResponse(
        UUID taskId,
        int triggerIntervalSeconds,
        Instant createdAt,
        Instant updatedAt
) {
    public static TaskStreamingConfigurationResponse from(TaskStreamingConfiguration configuration) {
        return new TaskStreamingConfigurationResponse(
                configuration.getTaskId(),
                configuration.getTriggerIntervalSeconds(),
                configuration.getCreatedAt(),
                configuration.getUpdatedAt()
        );
    }
}
