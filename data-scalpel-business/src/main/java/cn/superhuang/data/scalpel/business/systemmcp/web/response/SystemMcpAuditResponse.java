package cn.superhuang.data.scalpel.business.systemmcp.web.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "系统 MCP 配置、令牌管理或工具调用审计摘要；不包含请求体和响应正文")
public record SystemMcpAuditResponse(
        @Schema(description = "审计记录 UUID") UUID id,
        @Schema(description = "审计事件类型：CONFIGURATION、CATALOG_REFRESH、TOOL_CALL，或 TOKEN_CREATE、TOKEN_UPDATE、TOKEN_ENABLE、TOKEN_DISABLE、TOKEN_ROTATE、TOKEN_DELETE") String eventType,
        @Schema(description = "管理操作人的登录名，或工具调用时令牌绑定用户的登录名") String username,
        @Schema(description = "相关系统 MCP 令牌 UUID；配置事件无令牌时为空") UUID tokenId,
        @Schema(description = "相关稳定接口标识；非接口事件为空") String operationId,
        @Schema(description = "调用的 MCP 工具名称；非工具调用事件为空") String toolName,
        @Schema(description = "事件或调用结果状态：SUCCESS 或 ERROR") String status,
        @Schema(description = "稳定错误分类码；成功时为空") String errorCode,
        @Schema(description = "工具或业务调用耗时，单位毫秒；配置事件通常为 0") long durationMs,
        @Schema(description = "序列化后的 JSON 文本：配置和令牌事件记录脱敏变更摘要，工具调用记录 httpStatus 与 executionStatus；没有附加元数据时为空") String changesJson,
        @Schema(description = "事件发生时间，ISO-8601 UTC 时间") Instant createdAt
) {
}
