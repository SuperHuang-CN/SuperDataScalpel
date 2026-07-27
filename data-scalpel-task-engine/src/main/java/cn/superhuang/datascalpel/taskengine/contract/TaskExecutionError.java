package cn.superhuang.datascalpel.taskengine.contract;



import cn.superhuang.data.scalpel.contract.execution.ExecutionErrorCategory;
import cn.superhuang.data.scalpel.contract.execution.ExecutionFailurePhase;

import java.util.UUID;

public record TaskExecutionError(
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
    public TaskExecutionError {
        if (code == null || !code.matches("[A-Z][A-Z0-9_]{0,99}")
                || message == null || message.isBlank() || message.length() > 1000
                || category == null || phase == null || diagnosticId == null
                || sqlState != null && !sqlState.matches("[0-9A-Z]{5}")) {
            throw new IllegalArgumentException("执行错误字段无效");
        }
        boolean hasNode = nodeId != null;
        if (hasNode != (nodeType != null) || hasNode != (nodeName != null)) {
            throw new IllegalArgumentException("执行错误节点身份无效");
        }
    }
}
