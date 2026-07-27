package cn.superhuang.datascalpel.taskengine.contract;



import java.time.Instant;
import java.util.List;
import java.util.UUID;

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
        TaskExecutionError error
) {
    public static final int CURRENT_SCHEMA_VERSION = 2;

    public TaskExecutionResult {
        nodeResults = nodeResults == null ? List.of() : List.copyOf(nodeResults);
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
    }
}
