package cn.superhuang.data.scalpel.business.task.web.response;

import cn.superhuang.data.scalpel.business.task.domain.TaskRun;
import cn.superhuang.data.scalpel.contract.execution.ExecutionErrorCategory;
import cn.superhuang.data.scalpel.contract.execution.ExecutionFailurePhase;

import java.util.UUID;

public record TaskRunExecutionErrorResponse(
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
) {
    public static TaskRunExecutionErrorResponse from(TaskRun run) {
        if (run.getErrorCode() == null || run.getErrorCategory() == null
                || run.getErrorRetryable() == null || run.getErrorPhase() == null
                || run.getErrorDiagnosticId() == null) {
            return null;
        }
        return new TaskRunExecutionErrorResponse(
                run.getErrorCode(), run.getMessage(), run.getErrorCategory(), run.getErrorRetryable(),
                run.getErrorNodeId(), run.getErrorNodeType(), run.getErrorNodeName(), run.getErrorPhase(),
                run.getErrorSqlState(), run.getErrorDiagnosticId());
    }
}
