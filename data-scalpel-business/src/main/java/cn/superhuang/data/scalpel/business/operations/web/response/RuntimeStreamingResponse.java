package cn.superhuang.data.scalpel.business.operations.web.response;
import cn.superhuang.data.scalpel.business.task.domain.*;
import cn.superhuang.data.scalpel.contract.execution.StreamingSourceKind;
import java.time.Instant;
import java.util.*;
public record RuntimeStreamingResponse(UUID id, UUID taskId, String taskName, TaskType taskType, boolean sourceExists,
    UUID currentRunId, UUID computeEngineId, String engineName, StreamingDeploymentActualState actualState,
    StreamingDeploymentDesiredState desiredState, Instant startedAt, Instant stoppedAt, Instant lastProgressAt,
    boolean progressStale, StreamingSourceKind sourceKind, Long cursorLagMillis, Instant pollTime,
    List<RuntimeStreamingQueryResponse> queries, Instant lastErrorAt, String errorSummary) {}
