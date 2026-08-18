package cn.superhuang.data.scalpel.contract.execution;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import cn.superhuang.data.scalpel.contract.quality.QualitySummary;

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
        List<StreamingQueryDescriptor> streamingQueries,
        List<StreamingQueryProgress> streamingProgress,
        StreamingSourceProgress streamingSourceProgress,
        QualitySummary qualitySummary,
        ExecutionTaskType taskType,
        UserJobObservabilitySnapshot userJobObservability
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
        streamingQueries = streamingQueries == null ? List.of() : List.copyOf(streamingQueries);
        if (!streamingQueries.isEmpty() && messageType != ExecutionMessageType.EXECUTION_RUNNING) {
            throw new IllegalArgumentException("实时查询清单只能出现在执行运行事件中");
        }
        if (messageType == ExecutionMessageType.STREAMING_PROGRESS && streamingDeploymentId == null) {
            throw new IllegalArgumentException("实时进度事件必须包含 deploymentId");
        }
        if (streamingSourceProgress != null
                && messageType != ExecutionMessageType.STREAMING_PROGRESS) {
            throw new IllegalArgumentException("来源进度只能出现在实时进度事件中");
        }
        if (qualitySummary != null && messageType != ExecutionMessageType.EXECUTION_SUCCEEDED) {
            throw new IllegalArgumentException("质量汇总只能出现在执行成功事件中");
        }
        if (qualitySummary != null && taskType != ExecutionTaskType.SPARK_MODEL_QUALITY) {
            throw new IllegalArgumentException("质量汇总只能属于模型质检任务");
        }
        boolean observabilityEvent = messageType == ExecutionMessageType.USER_OBSERVABILITY;
        boolean terminalObservability = messageType == ExecutionMessageType.EXECUTION_SUCCEEDED
                || messageType == ExecutionMessageType.EXECUTION_FAILED
                || messageType == ExecutionMessageType.EXECUTION_TIMED_OUT
                || messageType == ExecutionMessageType.EXECUTION_CANCELLED;
        if (observabilityEvent && userJobObservability == null
                || userJobObservability != null && !observabilityEvent && !terminalObservability) {
            throw new IllegalArgumentException("用户作业观测载荷与事件类型不一致");
        }
        if (userJobObservability != null && taskType != ExecutionTaskType.SPARK_JAR
                && taskType != ExecutionTaskType.SPARK_STREAMING_JAR) {
            throw new IllegalArgumentException("用户作业观测载荷只能属于 Spark JAR 任务");
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
            SafeExecutionError error,
            UUID streamingDeploymentId,
            List<StreamingQueryDescriptor> streamingQueries,
            List<StreamingQueryProgress> streamingProgress,
            StreamingSourceProgress streamingSourceProgress,
            QualitySummary qualitySummary,
            ExecutionTaskType taskType
    ) {
        this(messageVersion, messageId, messageType, occurredAt, engineId, executionId, runId,
                attempt, sequence, backendType, externalExecutionId, trackingUrl, startedAt, endedAt,
                affectedRows, error, streamingDeploymentId, streamingQueries, streamingProgress,
                streamingSourceProgress, qualitySummary, taskType, null);
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
            SafeExecutionError error,
            UUID streamingDeploymentId,
            List<StreamingQueryProgress> streamingProgress
    ) {
        this(messageVersion, messageId, messageType, occurredAt, engineId, executionId, runId,
                attempt, sequence, backendType, externalExecutionId, trackingUrl, startedAt, endedAt,
                affectedRows, error, streamingDeploymentId, List.of(), streamingProgress, null, null, null, null);
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
            SafeExecutionError error,
            UUID streamingDeploymentId,
            List<StreamingQueryProgress> streamingProgress,
            StreamingSourceProgress streamingSourceProgress
    ) {
        this(messageVersion, messageId, messageType, occurredAt, engineId, executionId, runId,
                attempt, sequence, backendType, externalExecutionId, trackingUrl, startedAt, endedAt,
                affectedRows, error, streamingDeploymentId, List.of(), streamingProgress, streamingSourceProgress,
                null, null, null);
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
                affectedRows, error, null, List.of(), List.of(), null, null, null, null);
    }

    /** Compatibility constructor for existing in-process callers; new Dispatcher events carry taskType. */
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
            SafeExecutionError error,
            UUID streamingDeploymentId,
            List<StreamingQueryProgress> streamingProgress,
            StreamingSourceProgress streamingSourceProgress,
            QualitySummary qualitySummary
    ) {
        this(messageVersion, messageId, messageType, occurredAt, engineId, executionId, runId, attempt,
                sequence, backendType, externalExecutionId, trackingUrl, startedAt, endedAt,
                affectedRows, error, streamingDeploymentId, List.of(), streamingProgress, streamingSourceProgress,
                qualitySummary, qualitySummary == null ? null : ExecutionTaskType.SPARK_MODEL_QUALITY, null);
    }
}
