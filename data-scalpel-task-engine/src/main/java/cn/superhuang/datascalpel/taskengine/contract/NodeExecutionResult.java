package cn.superhuang.datascalpel.taskengine.contract;



import cn.superhuang.data.scalpel.contract.execution.ExecutionFailurePhase;

import java.time.Instant;

public record NodeExecutionResult(
        String nodeId,
        String nodeType,
        String nodeName,
        NodeExecutionState state,
        ExecutionFailurePhase phase,
        Instant startedAt,
        Instant endedAt,
        Long durationMs,
        Long rowsWritten,
        String message,
        TaskExecutionError error
) {
    public NodeExecutionResult {
        if (nodeId == null || nodeId.isBlank() || nodeType == null || nodeType.isBlank()
                || nodeName == null || nodeName.isBlank() || state == null || phase == null
                || startedAt == null || endedAt == null || endedAt.isBefore(startedAt)
                || durationMs == null || durationMs < 0 || rowsWritten != null && rowsWritten < 0
                || message == null || message.isBlank()) {
            throw new IllegalArgumentException("节点执行结果字段无效");
        }
        if (state == NodeExecutionState.SUCCESS && error != null
                || state == NodeExecutionState.FAILED && error == null) {
            throw new IllegalArgumentException("节点执行状态与错误对象不一致");
        }
    }
}
