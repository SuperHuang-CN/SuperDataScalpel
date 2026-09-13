package cn.superhuang.data.scalpel.contract.execution;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.time.Instant;
import java.util.UUID;

public record RunnerUserObservabilityEvent(
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
        @JsonPropertyDescription("用户 Spark JAR 通过公开 SDK 上报的有界指标和日志快照。")
        UserJobObservabilitySnapshot observability
) implements RunnerExecutionEvent {
    public RunnerUserObservabilityEvent {
        ExecutionContractValidation.envelope(
                messageVersion, messageId, messageType, occurredAt,
                engineId, executionId, runId, attempt);
        if (messageType != ExecutionMessageType.RUNNER_USER_OBSERVABILITY || observability == null) {
            throw new IllegalArgumentException("Runner 用户作业观测事件无效");
        }
    }
}
