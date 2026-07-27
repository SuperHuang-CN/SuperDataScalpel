package cn.superhuang.data.scalpel.dispatcher.artifact;

import cn.superhuang.data.scalpel.contract.execution.ExecutionErrorCategory;
import cn.superhuang.data.scalpel.contract.execution.ExecutionFailurePhase;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DispatcherTaskResult(
        Integer schemaVersion,
        UUID executionId,
        UUID runId,
        Integer attempt,
        State state,
        Instant startedAt,
        Instant endedAt,
        Long durationMs,
        Long affectedRows,
        List<NodeResult> nodeResults,
        Error error
) {
    public DispatcherTaskResult {
        nodeResults = nodeResults == null ? List.of() : List.copyOf(nodeResults);
    }

    public enum State {
        ACCEPTED, RUNNING, CANCEL_REQUESTED, SUCCESS, FAILED, TIMED_OUT, CANCELLED;

        public boolean terminal() {
            return this == SUCCESS || this == FAILED || this == TIMED_OUT || this == CANCELLED;
        }
    }

    public enum NodeState { SUCCESS, FAILED }

    public record NodeResult(
            String nodeId,
            String nodeType,
            String nodeName,
            NodeState state,
            ExecutionFailurePhase phase,
            Instant startedAt,
            Instant endedAt,
            Long durationMs,
            Long rowsWritten,
            String message,
            Error error
    ) { }

    public record Error(
            String code,
            String message,
            ExecutionErrorCategory category,
            boolean retryable,
            String nodeId,
            String nodeType,
            String nodeName,
            ExecutionFailurePhase phase,
            String sqlState,
            UUID diagnosticId
    ) { }
}
