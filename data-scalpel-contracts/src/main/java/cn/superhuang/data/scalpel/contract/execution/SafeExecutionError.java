package cn.superhuang.data.scalpel.contract.execution;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.UUID;

public record SafeExecutionError(
        @JsonPropertyDescription("稳定执行错误码，用于调用方分类处理失败。")
        String code,
        @JsonPropertyDescription("经安全处理的执行失败说明，不包含异常堆栈、凭据或原始敏感数据。")
        String message,
        @JsonPropertyDescription("错误分类，用于区分用户定义、资源、外部系统和平台故障。")
        ExecutionErrorCategory category,
        @JsonPropertyDescription("外部条件恢复后，相同请求是否可能成功；不表示平台自动重试。")
        boolean retryable,
        @JsonPropertyDescription("错误关联的 Canvas 节点 ID；非节点错误为空。")
        String nodeId,
        @JsonPropertyDescription("错误关联的 Canvas 节点类型；nodeId 为空时必须为空。")
        String nodeType,
        @JsonPropertyDescription("错误关联的 Canvas 节点显示名称；nodeId 为空时必须为空。")
        String nodeName,
        @JsonPropertyDescription("失败发生的执行阶段。")
        ExecutionFailurePhase phase,
        @JsonPropertyDescription("JDBC SQLState；非数据库错误或驱动未提供时为空。")
        String sqlState,
        @JsonPropertyDescription("服务端诊断 UUID，用于运维定位。")
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
