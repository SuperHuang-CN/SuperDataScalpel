package cn.superhuang.data.scalpel.business.task.web.response;

import cn.superhuang.data.scalpel.business.task.domain.StreamingDeploymentActualState;
import cn.superhuang.data.scalpel.business.task.domain.StreamingDeploymentDesiredState;
import cn.superhuang.data.scalpel.business.task.domain.TaskStreamingDeployment;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TaskStreamingDeploymentResponse(
        UUID id,
        int definitionVersion,
        UUID computeEngineId,
        UUID currentRunId,
        String checkpointKeyPrefix,
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
                deployment.getDesiredState(), deployment.getActualState(),
                run == null ? null : run.getBackendApplicationId(),
                run == null ? null : run.getTrackingUrl(),
                run == null ? null : run.getAttempt(),
                deployment.getStartedAt(), deployment.getStopRequestedAt(), deployment.getStoppedAt(),
                deployment.getLastProgressAt(), deployment.getLastErrorAt(), deployment.getLastError(),
                List.copyOf(queries)
        );
    }
}
