package cn.superhuang.datascalpel.taskengine.contract;



import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import cn.superhuang.data.scalpel.contract.execution.ExecutionTaskType;
import cn.superhuang.data.scalpel.contract.execution.UserJobObservabilitySnapshot;
import cn.superhuang.data.scalpel.contract.task.TaskLineageEvidence;

public record TaskExecutionResult(
        Integer schemaVersion,
        UUID executionId,
        UUID runId,
        Integer attempt,
        TaskExecutionState state,
        Instant startedAt,
        Instant endedAt,
        Long durationMs,
        Long affectedRows,
        List<NodeExecutionResult> nodeResults,
        ExecutionTaskType taskType,
        ModelQualityExecutionResult qualityResult,
        UserJobObservabilitySnapshot userJobObservability,
        TaskLineageEvidence lineage,
        TaskExecutionError error
) {
    public static final int CURRENT_SCHEMA_VERSION = 8;

    public TaskExecutionResult {
        nodeResults = nodeResults == null ? List.of() : List.copyOf(nodeResults);
        Set<String> nodeIds = new HashSet<>();
        if (nodeResults.stream().anyMatch(node -> node == null
                || !nodeIds.add(node.nodeId().toLowerCase(java.util.Locale.ROOT)))) {
            throw new IllegalArgumentException("任务执行结果包含空节点或重复 nodeId");
        }
        if (schemaVersion == null || schemaVersion != CURRENT_SCHEMA_VERSION || executionId == null
                || runId == null || attempt == null || attempt < 1 || state == null || !state.terminal()
                || startedAt == null || endedAt == null || endedAt.isBefore(startedAt)
                || durationMs == null || durationMs < 0 || affectedRows != null && affectedRows < 0) {
            throw new IllegalArgumentException("任务执行结果字段无效");
        }
        if (state == TaskExecutionState.SUCCESS && error != null
                || state != TaskExecutionState.SUCCESS && error == null) {
            throw new IllegalArgumentException("任务执行状态与错误对象不一致");
        }
        taskType = taskType == null ? ExecutionTaskType.SPARK_CANVAS : taskType;
        boolean quality = taskType == ExecutionTaskType.SPARK_MODEL_QUALITY;
        if (quality && state == TaskExecutionState.SUCCESS
                && (qualityResult == null || qualityResult.conclusion() == null)
                || quality && state != TaskExecutionState.SUCCESS
                && (qualityResult == null || qualityResult.conclusion() != null)
                || quality && !nodeResults.isEmpty() || !quality && qualityResult != null) {
            throw new IllegalArgumentException("任务类型与执行结果载荷不一致");
        }
        if (quality && state != TaskExecutionState.SUCCESS
                && qualityResult.technicalFailure() != null
                && !qualityResult.technicalFailure().diagnosticId().equals(error.diagnosticId())) {
            throw new IllegalArgumentException("质检技术失败规则与顶层错误诊断 ID 不一致");
        }
        if (taskType == ExecutionTaskType.SPARK_JAR && !nodeResults.isEmpty()) {
            throw new IllegalArgumentException("Spark JAR 执行结果不能包含 Canvas 节点结果");
        }
        if (userJobObservability != null && taskType != ExecutionTaskType.SPARK_JAR) {
            throw new IllegalArgumentException("用户作业观测结果只能属于 Spark JAR 批任务");
        }
        if (lineage != null && taskType != ExecutionTaskType.SPARK_JAR
                || taskType == ExecutionTaskType.SPARK_JAR
                && state == TaskExecutionState.SUCCESS && lineage == null) {
            throw new IllegalArgumentException("运行血缘证据只能属于 Spark JAR 批任务，且成功结果必须携带证据");
        }
    }

    public TaskExecutionResult(
            Integer schemaVersion, UUID executionId, UUID runId, Integer attempt,
            TaskExecutionState state, Instant startedAt, Instant endedAt, Long durationMs,
            Long affectedRows, List<NodeExecutionResult> nodeResults, ExecutionTaskType taskType,
            ModelQualityExecutionResult qualityResult,
            UserJobObservabilitySnapshot userJobObservability,
            TaskExecutionError error
    ) {
        this(schemaVersion, executionId, runId, attempt, state, startedAt, endedAt, durationMs,
                affectedRows, nodeResults, taskType, qualityResult, userJobObservability, null, error);
    }

    public TaskExecutionResult(
            Integer schemaVersion, UUID executionId, UUID runId, Integer attempt,
            TaskExecutionState state, Instant startedAt, Instant endedAt, Long durationMs,
            Long affectedRows, List<NodeExecutionResult> nodeResults, ExecutionTaskType taskType,
            ModelQualityExecutionResult qualityResult, TaskExecutionError error
    ) {
        this(schemaVersion, executionId, runId, attempt, state, startedAt, endedAt, durationMs,
                affectedRows, nodeResults, taskType, qualityResult, null, null, error);
    }

    public TaskExecutionResult(
            Integer schemaVersion, UUID executionId, UUID runId, Integer attempt,
            TaskExecutionState state, Instant startedAt, Instant endedAt, Long durationMs,
            Long affectedRows, List<NodeExecutionResult> nodeResults, TaskExecutionError error
    ) {
        this(schemaVersion, executionId, runId, attempt, state, startedAt, endedAt, durationMs,
                affectedRows, nodeResults, ExecutionTaskType.SPARK_CANVAS, null, null, null, error);
    }
}
