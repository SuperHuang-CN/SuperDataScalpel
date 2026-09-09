package cn.superhuang.data.scalpel.business.operations.web.response;
import cn.superhuang.data.scalpel.business.task.domain.*;
import cn.superhuang.data.scalpel.contract.quality.QualityConclusion;
import java.time.Instant;
import java.util.UUID;
public record RuntimeRunResponse(UUID id, UUID taskId, UUID parentRunId, String workflowNodeId, String taskName, UUID directoryId, boolean sourceExists,
                                 TaskType taskType, UUID computeEngineId, String engineName, UUID streamingDeploymentId,
                                 TaskRunStatus status, TaskRunExecutionMode executionMode, TaskRunTriggerType triggerType,
                                 Instant queuedAt, Instant startedAt, Instant endedAt, QualityConclusion qualityConclusion,
                                 Long qualityFailedRules, String errorCode, UUID diagnosticId) {}
