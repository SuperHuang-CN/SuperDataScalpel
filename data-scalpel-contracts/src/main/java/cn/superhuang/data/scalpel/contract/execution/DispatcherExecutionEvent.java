package cn.superhuang.data.scalpel.contract.execution;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DispatcherExecutionEvent(
        int messageVersion,
        UUID messageId,
        ExecutionMessageType messageType,
        Instant occurredAt,
        UUID engineId,
        UUID executionId,
        UUID runId,
        int attempt,
        long sequence,
        ExecutionBackendType backendType,
        String externalExecutionId,
        String trackingUrl,
        Instant startedAt,
        Instant endedAt,
        Long affectedRows,
        SafeExecutionError error,
        UUID streamingDeploymentId,
        List<StreamingQueryProgress> streamingProgress
) implements ExecutionMessageEnvelope {
    public DispatcherExecutionEvent {
        ExecutionContractValidation.envelope(messageVersion, messageId, messageType, occurredAt, engineId, executionId, runId, attempt);
        if (!messageType.isDispatcherEvent() || sequence < 1 || backendType == null || (affectedRows != null && affectedRows < 0)) {
            throw new IllegalArgumentException("Dispatcher 执行事件无效");
        }
        externalExecutionId = ExecutionContractValidation.optional(externalExecutionId, 300, "外部执行标识");
        trackingUrl = ExecutionContractValidation.trackingUrl(trackingUrl);
        if (endedAt != null && startedAt != null && endedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("执行结束时间不能早于开始时间");
        }
        boolean failed = messageType == ExecutionMessageType.EXECUTION_REJECTED
                || messageType == ExecutionMessageType.EXECUTION_FAILED
                || messageType == ExecutionMessageType.EXECUTION_TIMED_OUT
                || messageType == ExecutionMessageType.EXECUTION_LOST;
        if (failed && error == null) throw new IllegalArgumentException("失败事件必须包含安全错误");
        streamingProgress = streamingProgress == null ? List.of() : List.copyOf(streamingProgress);
        if (messageType == ExecutionMessageType.STREAMING_PROGRESS && streamingDeploymentId == null) {
            throw new IllegalArgumentException("实时进度事件必须包含 deploymentId");
        }
    }

    public DispatcherExecutionEvent(
            int messageVersion,
            UUID messageId,
            ExecutionMessageType messageType,
            Instant occurredAt,
            UUID engineId,
            UUID executionId,
            UUID runId,
            int attempt,
            long sequence,
            ExecutionBackendType backendType,
            String externalExecutionId,
            String trackingUrl,
            Instant startedAt,
            Instant endedAt,
            Long affectedRows,
            SafeExecutionError error
    ) {
        this(messageVersion, messageId, messageType, occurredAt, engineId, executionId, runId, attempt,
                sequence, backendType, externalExecutionId, trackingUrl, startedAt, endedAt,
                affectedRows, error, null, List.of());
    }
}
