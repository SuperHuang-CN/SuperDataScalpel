package cn.superhuang.data.scalpel.business.operations.service;
import cn.superhuang.data.scalpel.business.task.domain.*;
import cn.superhuang.data.scalpel.contract.quality.QualityConclusion;
import java.time.Instant;
import java.util.UUID;
/** Scalar-only projection: global polling never loads definition, metrics JSON or artifact contents. */
public record RuntimeRunProjection(UUID id, UUID taskId, UUID parentRunId, String workflowNodeId, TaskType taskType, UUID computeEngineId, UUID streamingDeploymentId,
                                   TaskRunStatus status, TaskRunExecutionMode executionMode, TaskRunTriggerType triggerType,
                                   Instant queuedAt, Instant startedAt, Instant endedAt, QualityConclusion qualityConclusion,
                                   Long qualityFailedRules, String errorCode, UUID errorDiagnosticId) {}
