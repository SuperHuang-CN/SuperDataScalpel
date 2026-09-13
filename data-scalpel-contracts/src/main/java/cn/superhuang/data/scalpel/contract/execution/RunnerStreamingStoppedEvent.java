package cn.superhuang.data.scalpel.contract.execution;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.time.Instant;
import java.util.UUID;

public record RunnerStreamingStoppedEvent(
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
        @JsonPropertyDescription("Runner 确认全部实时查询停止的时间。")
        Instant stoppedAt,
        @JsonPropertyDescription("Runner 对实时查询停止结果的可读说明；没有补充信息时为空，最长 1000 个字符。")
        String message
) implements RunnerExecutionEvent {
    public RunnerStreamingStoppedEvent {
        ExecutionContractValidation.envelope(
                messageVersion, messageId, messageType, occurredAt, engineId, executionId, runId, attempt);
        if (messageType != ExecutionMessageType.RUNNER_STREAMING_STOPPED
                || deploymentId == null || stoppedAt == null) {
            throw new IllegalArgumentException("Runner 实时停止事件无效");
        }
        message = ExecutionContractValidation.optional(message, 1000, "停止消息");
    }
}
