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
        NodeExecutionMetrics metrics,
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
        if (state == NodeExecutionState.FAILED && metrics != null
                && !(metrics instanceof OutputWritesMetrics)) {
            throw new IllegalArgumentException("失败节点只能包含逐写入指标");
        }
        if (metrics instanceof SnapshotSyncMetrics snapshot
                && (rowsWritten == null || rowsWritten.longValue() != snapshot.rowsWritten())) {
            throw new IllegalArgumentException("Snapshot Sync rowsWritten 与指标不一致");
        }
        if (metrics instanceof OutputWritesMetrics outputWrites) {
            if (!outputWrites.terminalSequenceValid()
                    || rowsWritten != null && !rowsWritten.equals(outputWrites.rowsWritten())) {
                throw new IllegalArgumentException("输出节点 rowsWritten 与逐写入指标不一致");
            }
            if (rowsWritten == null && outputWrites.rowsWritten() != null) {
                throw new IllegalArgumentException("输出节点缺少可确定的 rowsWritten");
            }
            OutputWriteExecutionResult failedWrite = outputWrites.failedWrite();
            if (state == NodeExecutionState.SUCCESS && !outputWrites.allSucceeded()
                    || state == NodeExecutionState.FAILED && failedWrite == null
                    || state == NodeExecutionState.FAILED
                    && !failedWrite.errorCode().equals(error.code())) {
                throw new IllegalArgumentException("输出节点状态与逐写入指标不一致");
            }
        }
    }

    public NodeExecutionResult(
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
        this(nodeId, nodeType, nodeName, state, phase, startedAt, endedAt,
                durationMs, rowsWritten, null, message, error);
    }
}
