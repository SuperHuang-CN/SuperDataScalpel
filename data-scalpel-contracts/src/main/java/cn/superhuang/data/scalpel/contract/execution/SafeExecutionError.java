package cn.superhuang.data.scalpel.contract.execution;

import java.util.UUID;

public record SafeExecutionError(
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
    public SafeExecutionError(String code, String message) {
        this(code, message, ExecutionErrorCategory.INTERNAL, false, null, null, null,
                ExecutionFailurePhase.DISPATCH, null, UUID.randomUUID());
    }

    public SafeExecutionError {
        code = ExecutionContractValidation.errorCode(code);
        message = ExecutionContractValidation.required(message, 1000, "错误消息");
        if (category == null || phase == null || diagnosticId == null) {
            throw new IllegalArgumentException("执行错误分类、阶段和诊断 ID 不能为空");
        }
        nodeId = ExecutionContractValidation.optional(nodeId, 100, "错误节点 ID");
        nodeType = ExecutionContractValidation.optional(nodeType, 64, "错误节点类型");
        nodeName = ExecutionContractValidation.optional(nodeName, 200, "错误节点名称");
        if (nodeId == null && (nodeType != null || nodeName != null)) {
            throw new IllegalArgumentException("没有错误节点 ID 时不能携带节点类型或名称");
        }
        if (nodeId != null && (nodeType == null || nodeName == null)) {
            throw new IllegalArgumentException("节点错误必须包含完整节点身份");
        }
        sqlState = ExecutionContractValidation.optional(sqlState, 5, "SQLState");
        if (sqlState != null && !sqlState.matches("[0-9A-Z]{5}")) {
            throw new IllegalArgumentException("SQLState 必须是 5 位大写字母或数字");
        }
    }
}
