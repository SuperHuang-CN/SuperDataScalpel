package cn.superhuang.data.scalpel.contract.execution;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import cn.superhuang.data.scalpel.contract.quality.QualitySummary;

public record DispatcherExecutionEvent(
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
        @JsonPropertyDescription("同一执行尝试内严格递增的事件序号，从 1 开始。")
        long sequence,
        @JsonPropertyDescription("实际执行后端类型，例如本地 Docker、YARN 或 Kubernetes。")
        ExecutionBackendType backendType,
        @JsonPropertyDescription("执行后端分配的作业标识；尚未分配时为空。")
        String externalExecutionId,
        @JsonPropertyDescription("执行引擎提供的跟踪页面地址；不可用时为空。")
        String trackingUrl,
        @JsonPropertyDescription("开始时间；尚未开始时为空。")
        Instant startedAt,
        @JsonPropertyDescription("结束时间；尚未结束时为空。")
        Instant endedAt,
        @JsonPropertyDescription("已确认成功写入或影响的行数；无法确认或不适用时为空。")
        Long affectedRows,
        @JsonPropertyDescription("失败事件携带的结构化安全错误；非失败事件为空。")
        SafeExecutionError error,
        @JsonPropertyDescription("实时进度所属部署 UUID；非实时进度事件为空。")
        UUID streamingDeploymentId,
        @JsonPropertyDescription("实时作业进入 RUNNING 时已启动的查询清单。")
        List<StreamingQueryDescriptor> streamingQueries,
        @JsonPropertyDescription("各实时输出查询的最新微批次进度。")
        List<StreamingQueryProgress> streamingProgress,
        @JsonPropertyDescription("实时输入来源的最新 Offset、轮询和延迟摘要。")
        StreamingSourceProgress streamingSourceProgress,
        @JsonPropertyDescription("模型质检任务成功时生成的不可变质量汇总；其他任务或状态为空。")
        QualitySummary qualitySummary,
        @JsonPropertyDescription("任务类型，决定定义和执行协议。")
        ExecutionTaskType taskType,
        @JsonPropertyDescription("Spark JAR 任务的用户级指标和安全日志快照。")
        UserJobObservabilitySnapshot userJobObservability,
        @JsonPropertyDescription("终态 result.json 内容的 SHA-256 十六进制摘要。")
        String resultSha256
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
        resultSha256 = ExecutionContractValidation.optional(resultSha256, 64, "结果 SHA-256");
        boolean terminal = messageType == ExecutionMessageType.EXECUTION_SUCCEEDED
                || messageType == ExecutionMessageType.EXECUTION_FAILED
                || messageType == ExecutionMessageType.EXECUTION_TIMED_OUT
                || messageType == ExecutionMessageType.EXECUTION_CANCELLED;
        if (resultSha256 != null && (!terminal || !resultSha256.matches("[0-9a-f]{64}"))) {
            throw new IllegalArgumentException("结果 SHA-256 只能出现在终态事件中");
        }
    }

    public DispatcherExecutionEvent(
            int messageVersion, UUID messageId, ExecutionMessageType messageType, Instant occurredAt,
            UUID engineId, UUID executionId, UUID runId, int attempt, long sequence,
            ExecutionBackendType backendType, String externalExecutionId, String trackingUrl,
            Instant startedAt, Instant endedAt, Long affectedRows, SafeExecutionError error,
            UUID streamingDeploymentId, List<StreamingQueryDescriptor> streamingQueries,
            List<StreamingQueryProgress> streamingProgress,
            StreamingSourceProgress streamingSourceProgress, QualitySummary qualitySummary,
            ExecutionTaskType taskType, UserJobObservabilitySnapshot userJobObservability
    ) {
        this(messageVersion, messageId, messageType, occurredAt, engineId, executionId, runId,
                attempt, sequence, backendType, externalExecutionId, trackingUrl, startedAt, endedAt,
                affectedRows, error, streamingDeploymentId, streamingQueries, streamingProgress,
                streamingSourceProgress, qualitySummary, taskType, userJobObservability, null);
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
                streamingSourceProgress, qualitySummary, taskType, null, null);
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
