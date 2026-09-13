package cn.superhuang.data.scalpel.contract.execution;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RunnerStreamingStartedEvent(
        @JsonPropertyDescription("Kafka 执行消息协议版本；发送方必须使用接收方支持的版本。")
        int messageVersion,
        @JsonPropertyDescription("本条命令或事件的唯一 UUID，用于去重和审计。")
        UUID messageId,
        @JsonPropertyDescription("消息类型判别值，必须与当前命令或事件结构一致。")
        ExecutionMessageType messageType,
        @JsonPropertyDescription("事件发生时间。")
        Instant occurredAt,
        @JsonPropertyDescription("服务或计算引擎 UUID。")
        UUID engineId,
        @JsonPropertyDescription("外部执行 UUID。")
        UUID executionId,
        @JsonPropertyDescription("任务运行 UUID。")
        UUID runId,
        @JsonPropertyDescription("本次任务运行的执行尝试序号，从 1 开始。")
        int attempt,
        @JsonPropertyDescription("实时任务部署 UUID，用于关联启动、进度、停止和 Checkpoint。")
        UUID deploymentId,
        @JsonPropertyDescription("Spark 提交后分配的 Application ID；尚未获得或本地模式不提供时为空。")
        String sparkApplicationId,
        @JsonPropertyDescription("该实时部署当前启动的 Structured Streaming 查询描述列表。")
        List<StreamingQueryDescriptor> queries
) implements RunnerExecutionEvent {
    public RunnerStreamingStartedEvent {
        queries = queries == null ? List.of() : List.copyOf(queries);
        ExecutionContractValidation.envelope(
                messageVersion, messageId, messageType, occurredAt, engineId, executionId, runId, attempt);
        if (messageType != ExecutionMessageType.RUNNER_STREAMING_STARTED || deploymentId == null) {
            throw new IllegalArgumentException("Runner 实时启动事件无效");
        }
        sparkApplicationId = ExecutionContractValidation.optional(sparkApplicationId, 200, "Spark Application ID");
    }

    public RunnerStreamingStartedEvent(
            int messageVersion, UUID messageId, ExecutionMessageType messageType, Instant occurredAt,
            UUID engineId, UUID executionId, UUID runId, int attempt, UUID deploymentId,
            String sparkApplicationId
    ) {
        this(messageVersion, messageId, messageType, occurredAt, engineId, executionId, runId,
                attempt, deploymentId, sparkApplicationId, List.of());
    }
}
