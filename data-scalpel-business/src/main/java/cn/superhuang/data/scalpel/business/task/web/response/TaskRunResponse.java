package cn.superhuang.data.scalpel.business.task.web.response;

import cn.superhuang.data.scalpel.business.task.domain.TaskRunStatus;
import cn.superhuang.data.scalpel.business.task.domain.TaskRunTriggerType;
import cn.superhuang.data.scalpel.business.task.domain.TaskRun;
import cn.superhuang.data.scalpel.business.task.domain.TaskRunExecutionMode;
import cn.superhuang.data.scalpel.business.task.domain.TaskType;

import java.time.Instant;
import java.util.UUID;

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
                run.getEndedAt(), run.getDeadlineAt(), run.getAffectedRows(), run.getMessage(), run.getErrorDetail(),
                TaskRunExecutionErrorResponse.from(run), run.getCreatedAt(), run.getUpdatedAt()
        );
    }
}
