package cn.superhuang.data.scalpel.contract.execution;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.time.Instant;
import java.util.UUID;

/** Requests an orderly stop for the currently active streaming attempt. */
public record StopStreamingExecutionCommand(
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
        @JsonPropertyDescription("请求优雅停止当前实时部署的原因，必填且最长 500 个字符；用于运行状态说明和审计。")
        String reason,
        @JsonPropertyDescription("优雅停止实时查询的最长等待秒数，超时后的行为由命令策略决定。")
        int gracePeriodSeconds
) implements ExecutionCommand {
    public StopStreamingExecutionCommand {
        ExecutionContractValidation.envelope(
                messageVersion, messageId, messageType, occurredAt, engineId, executionId, runId, attempt);
        if (messageType != ExecutionMessageType.STOP_STREAMING_EXECUTION
                || deploymentId == null || gracePeriodSeconds < 1 || gracePeriodSeconds > 600) {
            throw new IllegalArgumentException("停止实时执行命令无效");
        }
        reason = ExecutionContractValidation.required(reason, 500, "停止原因");
    }
}
