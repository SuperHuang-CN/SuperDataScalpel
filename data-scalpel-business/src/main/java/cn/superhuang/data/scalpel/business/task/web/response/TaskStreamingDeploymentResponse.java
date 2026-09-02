package cn.superhuang.data.scalpel.business.task.web.response;

import cn.superhuang.data.scalpel.business.task.domain.StreamingDeploymentActualState;
import cn.superhuang.data.scalpel.business.task.domain.StreamingDeploymentDesiredState;
import cn.superhuang.data.scalpel.business.task.domain.TaskStreamingDeployment;
import cn.superhuang.data.scalpel.business.task.domain.StreamingDeploymentExecutionMode;
import cn.superhuang.data.scalpel.contract.execution.StreamingSourceKind;
import cn.superhuang.data.scalpel.contract.execution.StreamingCheckpointMode;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TaskStreamingDeploymentResponse(
        UUID id,
        int definitionVersion,
        UUID computeEngineId,
        UUID currentRunId,
        String checkpointKeyPrefix,
        int checkpointGeneration,
        StreamingCheckpointMode checkpointStartMode,
        UUID checkpointSourceDeploymentId,
        StreamingDeploymentExecutionMode executionMode,
        StreamingDeploymentDesiredState desiredState,
        StreamingDeploymentActualState actualState,
        String applicationId,
        String trackingUrl,
        Integer attempt,
        Instant startedAt,
        Instant stopRequestedAt,
        Instant stoppedAt,
        Instant lastProgressAt,
        Instant lastErrorAt,
        String lastError,
        UUID sourceNodeId,
        String sourceSignature,
        String committedOffset,
        Instant windowStart,
        Instant windowEnd,
        Long rowCount,
        Long pollDurationMillis,
        Instant pollTime,
        Long cursorLagMillis,
        StreamingSourceKind sourceKind,
        Integer vGroupCount,
        Long batchOffsetSpan,
        UserJobObservabilityResponse userJobObservability,
        List<TaskStreamingQueryResponse> queries
) {
    public static TaskStreamingDeploymentResponse from(
            TaskStreamingDeployment deployment,
            cn.superhuang.data.scalpel.business.task.domain.TaskRun run,
            List<TaskStreamingQueryResponse> queries
    ) {
        return new TaskStreamingDeploymentResponse(
                deployment.getId(), deployment.getDefinitionVersion(), deployment.getComputeEngineId(),
                deployment.getCurrentRunId(), deployment.getCheckpointKeyPrefix(),
                deployment.getCheckpointGeneration(), deployment.getCheckpointStartMode(),
                deployment.getCheckpointSourceDeploymentId(),
                deployment.getExecutionMode(),
                deployment.getDesiredState(), deployment.getActualState(),
                run == null ? null : run.getBackendApplicationId(),
                run == null ? null : run.getTrackingUrl(),
                run == null ? null : run.getAttempt(),
                deployment.getStartedAt(), deployment.getStopRequestedAt(), deployment.getStoppedAt(),
                deployment.getLastProgressAt(), deployment.getLastErrorAt(), deployment.getLastError(),
                deployment.getSourceNodeId(), deployment.getSourceSignature(),
                deployment.getLastCommittedOffset(), deployment.getLastWindowStart(),
                deployment.getLastWindowEnd(), deployment.getLastWindowRowCount(),
                deployment.getLastPollDurationMillis(), deployment.getLastPollAt(),
                deployment.getCursorLagMillis(), deployment.getSourceKind(),
                deployment.getLastVGroupCount(), deployment.getLastBatchOffsetSpan(),
                TaskRunResponse.observabilityFrom(run),
                List.copyOf(queries)
        );
    }
}
