package cn.superhuang.data.scalpel.business.task.web.response;

import cn.superhuang.data.scalpel.business.task.domain.TaskRunStatus;
import cn.superhuang.data.scalpel.business.task.domain.TaskRunTriggerType;
import cn.superhuang.data.scalpel.business.task.domain.TaskRun;
import cn.superhuang.data.scalpel.business.task.domain.TaskRunExecutionMode;
import cn.superhuang.data.scalpel.business.task.domain.TaskType;

import java.time.Instant;
import java.util.UUID;
import cn.superhuang.data.scalpel.contract.quality.QualityConclusion;
import cn.superhuang.data.scalpel.contract.execution.UserJobMetricSnapshot;
import cn.superhuang.data.scalpel.contract.execution.UserJobStatus;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import java.util.List;

public record TaskRunResponse(
        UUID id,
        UUID taskId,
        UUID scheduleId,
        UUID streamingDeploymentId,
        TaskType taskType,
        UUID externalExecutionId,
        UUID computeEngineId,
        String backendApplicationId,
        String trackingUrl,
        Integer attempt,
        int definitionVersion,
        TaskRunTriggerType triggerType,
        TaskRunExecutionMode executionMode,
        TaskRunStatus status,
        Instant scheduledFireAt,
        Instant queuedAt,
        Instant startedAt,
        Instant endedAt,
        Instant deadlineAt,
        Long affectedRows,
        String userJarFileName,
        String userJarSha256,
        Long userJarSizeBytes,
        QualityConclusion qualityConclusion,
        Long qualityTotalRules,
        Long qualityPassedRules,
        Long qualityFailedRules,
        Long qualitySkippedRules,
        Long qualityCheckedRows,
        UserJobObservabilityResponse userJobObservability,
        String message,
        String errorDetail,
        TaskRunExecutionErrorResponse executionError,
        Instant createdAt,
        Instant updatedAt
) {

    public static TaskRunResponse from(TaskRun run) {
        return new TaskRunResponse(
                run.getId(), run.getTaskId(), run.getScheduleId(), run.getStreamingDeploymentId(),
                run.getTaskType(), run.getExternalExecutionId(),
                run.getComputeEngineId(), run.getBackendApplicationId(), run.getTrackingUrl(),
                run.getAttempt(), run.getDefinitionVersion(), run.getTriggerType(),
                run.getExecutionMode(), run.getStatus(), run.getScheduledFireAt(), run.getQueuedAt(), run.getStartedAt(),
                run.getEndedAt(), run.getDeadlineAt(), run.getAffectedRows(),
                run.getUserJarFileName(), run.getUserJarSha256(), run.getUserJarSizeBytes(),
                run.getQualityConclusion(), run.getQualityTotalRules(), run.getQualityPassedRules(),
                run.getQualityFailedRules(), run.getQualitySkippedRules(), run.getQualityCheckedRows(),
                observabilityFrom(run),
                run.getMessage(), run.getErrorDetail(),
                TaskRunExecutionErrorResponse.from(run), run.getCreatedAt(), run.getUpdatedAt()
        );
    }

    private static final ObjectMapper OBSERVABILITY_MAPPER = JsonMapper.builderWithJackson2Defaults()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    public static UserJobObservabilityResponse observabilityFrom(TaskRun run) {
        if (run == null || run.getUserJobMetrics() == null || run.getUserJobMetrics().isBlank()) return null;
        List<UserJobMetricSnapshot> metrics;
        try {
            metrics = OBSERVABILITY_MAPPER.readValue(
                    run.getUserJobMetrics(), new TypeReference<List<UserJobMetricSnapshot>>() { });
        } catch (RuntimeException exception) {
            throw new IllegalStateException("无法读取用户作业指标快照", exception);
        }
        UserJobStatus status = run.getUserJobPhase() == null ? null : new UserJobStatus(
                run.getUserJobPhase(), run.getUserJobStatusMessage(), run.getUserJobStatusAt());
        return new UserJobObservabilityResponse(status, metrics);
    }
}
