package cn.superhuang.data.scalpel.contract.execution;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RunnerStreamingProgressEvent(
        int messageVersion,
        UUID messageId,
        ExecutionMessageType messageType,
        Instant occurredAt,
        UUID engineId,
        UUID executionId,
        UUID runId,
        int attempt,
        UUID deploymentId,
        List<StreamingQueryProgress> queries,
        StreamingSourceProgress sourceProgress
) implements RunnerExecutionEvent {
    public RunnerStreamingProgressEvent {
        ExecutionContractValidation.envelope(
                messageVersion, messageId, messageType, occurredAt, engineId, executionId, runId, attempt);
        if (messageType != ExecutionMessageType.RUNNER_STREAMING_PROGRESS || deploymentId == null) {
            throw new IllegalArgumentException("Runner 实时进度事件无效");
        }
        queries = queries == null ? List.of() : List.copyOf(queries);
    }

    public RunnerStreamingProgressEvent(
            int messageVersion,
            UUID messageId,
            ExecutionMessageType messageType,
            Instant occurredAt,
            UUID engineId,
            UUID executionId,
            UUID runId,
            int attempt,
            UUID deploymentId,
            List<StreamingQueryProgress> queries
    ) {
        this(messageVersion, messageId, messageType, occurredAt, engineId, executionId, runId,
                attempt, deploymentId, queries, null);
    }
}
