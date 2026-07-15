package cn.superhuang.data.scalpel.business.task.web.response;

import cn.superhuang.data.scalpel.business.task.domain.TaskRunStatus;
import cn.superhuang.data.scalpel.business.task.domain.TaskRunTriggerType;
import cn.superhuang.data.scalpel.business.task.domain.TaskRun;

import java.time.Instant;
import java.util.UUID;

public record TaskRunResponse(
        UUID id,
        UUID taskId,
        int definitionVersion,
        TaskRunTriggerType triggerType,
        TaskRunStatus status,
        Instant queuedAt,
        Instant startedAt,
        Instant endedAt,
        Long affectedRows,
        String message,
        String errorDetail,
        Instant createdAt,
        Instant updatedAt
) {

    public static TaskRunResponse from(TaskRun run) {
        return new TaskRunResponse(
                run.getId(), run.getTaskId(), run.getDefinitionVersion(), run.getTriggerType(), run.getStatus(),
                run.getQueuedAt(), run.getStartedAt(), run.getEndedAt(), run.getAffectedRows(), run.getMessage(),
                run.getErrorDetail(), run.getCreatedAt(), run.getUpdatedAt()
        );
    }
}
