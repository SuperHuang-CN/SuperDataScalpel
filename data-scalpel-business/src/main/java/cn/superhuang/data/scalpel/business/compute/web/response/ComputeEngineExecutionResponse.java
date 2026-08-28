package cn.superhuang.data.scalpel.business.compute.web.response;

import cn.superhuang.data.scalpel.business.task.domain.TaskRunStatus;
import cn.superhuang.data.scalpel.business.task.domain.TaskRunTriggerType;
import cn.superhuang.data.scalpel.contract.execution.DispatcherExecutionState;
import cn.superhuang.data.scalpel.contract.execution.ExecutionTaskType;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/** Dispatcher execution enriched with Admin task-run information when it has already synchronized. */
public record ComputeEngineExecutionResponse(
        UUID executionId,
        UUID executionRunId,
        UUID taskId,
        String taskName,
        ExecutionTaskType taskType,
        int definitionVersion,
        DispatcherExecutionState dispatcherState,
        UUID taskRunId,
        TaskRunStatus taskRunStatus,
        TaskRunTriggerType triggerType,
        @JsonProperty("synchronized") boolean synchronizedWithAdmin,
        String backendExecutionId,
        String trackingUrl,
        Instant deadlineAt,
        Instant queuedAt,
        Instant submissionStartedAt,
        Instant submittedAt,
        Instant startedAt,
        Instant endedAt,
        Instant lastObservedAt,
        String errorCode,
        String errorMessage,
        Long queuePosition
) {
}
