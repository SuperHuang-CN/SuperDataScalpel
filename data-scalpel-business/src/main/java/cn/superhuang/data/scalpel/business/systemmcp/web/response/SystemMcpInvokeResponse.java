package cn.superhuang.data.scalpel.business.systemmcp.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.databind.JsonNode;

@Schema(description = "系统 MCP 调用原业务 API 后的结构化执行结果")
public record SystemMcpInvokeResponse(
        @Schema(description = "本次执行的稳定接口标识") String operationId,
        @Schema(description = "执行状态：RESPONDED 已完整读取响应，RESPONDED_INCOMPLETE 已收到 HTTP 状态但正文不完整或不受支持，UNKNOWN 无法确认是否执行；协议层校验或鉴权拒绝时为 NOT_DISPATCHED") String executionStatus,
        @Schema(description = "原业务 API 的 HTTP 状态；尚未收到业务响应时为空。NOT_DISPATCHED 时可能是工具边界自身返回的 4xx/5xx") Integer httpStatus,
        @Schema(description = "原业务 API 的 JSON 成功响应；仅完整读取且 HTTP 状态小于 400 时返回，204 空响应和其他情况为空") JsonNode data,
        @Schema(description = "原业务 API 返回的 RFC 9457 Problem Detail 或其他 JSON 错误正文；仅完整读取且 HTTP 状态不小于 400 时返回") JsonNode problem,
        @Schema(description = "稳定结果码或错误码；成功的 2xx JSON/空响应通常为空，重定向、业务错误、响应不可读和结果不确定时返回") String code,
        @Schema(description = "需要智能体采取额外动作时的说明；正常完整响应通常为空，结果不确定时应先查询业务状态且不能直接重试变更操作") String message
) {
}
