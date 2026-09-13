package cn.superhuang.data.scalpel.business.task.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.task.domain.TaskStreamingConfiguration;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "SPARK_STREAMING_CANVAS 当前唯一无界输入节点的微批触发配置；该配置实际保存在 Canvas 定义中。")

public record TaskStreamingConfigurationResponse(
        @Schema(description = "所属任务 UUID。")
        UUID taskId,
        @Schema(description = "Spark Structured Streaming 微批触发间隔，单位秒，范围 1 到 300；尚无 Canvas 定义和旧配置时返回系统默认值。")
        int triggerIntervalSeconds,
        @Schema(description = "配置来源的创建时间，ISO-8601 UTC 时间戳；读取已保存 Canvas 时为定义创建时间，完全未配置时为空。修改接口的即时响应当前可能与 updatedAt 相同。")
        Instant createdAt,
        @Schema(description = "配置来源最后更新时间，ISO-8601 UTC 时间戳；完全未配置时为空。")
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

    public static TaskStreamingConfigurationResponse fromDefinition(
            UUID taskId,
            int triggerIntervalSeconds,
            Instant createdAt,
            Instant updatedAt
    ) {
        return new TaskStreamingConfigurationResponse(
                taskId, triggerIntervalSeconds, createdAt, updatedAt);
    }
}
