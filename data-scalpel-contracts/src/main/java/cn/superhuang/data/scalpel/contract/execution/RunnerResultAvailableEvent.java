package cn.superhuang.data.scalpel.contract.execution;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.time.Instant;
import java.util.UUID;

public record RunnerResultAvailableEvent(
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
        @JsonPropertyDescription("本次尝试 result.json 的固定对象存储 Key。")
        String resultKey,
        @JsonPropertyDescription("终态 result.json 内容的 SHA-256 十六进制摘要。")
        String resultSha256
) implements RunnerExecutionEvent {
    public RunnerResultAvailableEvent {
        ExecutionContractValidation.envelope(messageVersion, messageId, messageType, occurredAt, engineId, executionId, runId, attempt);
        if (messageType != ExecutionMessageType.RUNNER_RESULT_AVAILABLE) throw new IllegalArgumentException("Runner 事件类型无效");
        resultKey = ExecutionContractValidation.objectKey(resultKey);
        ExecutionContractValidation.exactArtifactKey(resultKey, runId, attempt, "result.json");
        resultSha256 = ExecutionContractValidation.sha256(resultSha256);
    }
}
