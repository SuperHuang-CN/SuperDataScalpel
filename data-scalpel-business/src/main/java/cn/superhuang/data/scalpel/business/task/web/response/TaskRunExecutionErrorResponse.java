package cn.superhuang.data.scalpel.business.task.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.task.domain.TaskRun;
import cn.superhuang.data.scalpel.contract.execution.ExecutionErrorCategory;
import cn.superhuang.data.scalpel.contract.execution.ExecutionFailurePhase;

import java.util.UUID;

@Schema(description = "结构化执行错误；用于判断失败阶段和是否适合由用户重新提交。")

public record TaskRunExecutionErrorResponse(
        @Schema(description = "稳定执行错误码，用于程序识别具体失败原因。")
        String code,
        @Schema(description = "经安全处理的执行失败说明；不包含内部异常堆栈或凭据。")
        String message,
        @Schema(description = "错误分类，用于区分用户定义、资源、外部系统和平台故障。")
        ExecutionErrorCategory category,
        @Schema(description = "相同定义和参数在外部条件恢复后是否可能成功；不代表平台会自动重试。")
        boolean retryable,
        @Schema(description = "错误关联的 Canvas 或工作流节点稳定 ID；非节点错误时为空，不是数据库 UUID。")
        String nodeId,
        @Schema(description = "失败 Canvas 节点类型；非节点错误为空。")
        String nodeType,
        @Schema(description = "关联节点名称；不适用或未命名时为空。")
        String nodeName,
        @Schema(description = "发生错误的执行阶段。")
        ExecutionFailurePhase phase,
        @Schema(description = "下游数据库 SQLSTATE；非数据库错误为空。")
        String sqlState,
        @Schema(description = "服务端诊断 UUID，可用于运维定位，不暴露内部堆栈。")
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
