package cn.superhuang.data.scalpel.business.mcp.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.mcp.domain.McpInvocationLog;
import cn.superhuang.data.scalpel.business.mcp.domain.McpInvocationStatus;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "一条原 MCP 平台协议请求的路由、发布快照、令牌和执行审计元数据。")

public record McpInvocationResponse(
        @Schema(description = "MCP 调用审计记录 UUID。")
        UUID id,
        @Schema(description = "调用发生时对应的 MCP 服务器 UUID；服务器记录已删除时仍保留快照字段，关联 UUID 可能为空。")
        UUID serverId,
        @Schema(description = "本次调用实际使用的不可变发布快照 UUID；无法解析发布记录时为空。")
        UUID releaseId,
        @Schema(description = "MCP 服务器稳定编码。")
        String serverCode,
        @Schema(description = "MCP 发布快照的版本号。")
        Integer releaseVersion,
        @Schema(description = "MCP 工具稳定编码。")
        String toolCode,
        @Schema(description = "收到的 JSON-RPC 方法，例如 initialize、ping、tools/list、tools/call 或通知方法；无法解析时为 unknown。")
        String invocationType,
        @Schema(description = "本次 MCP 调用使用的协议版本。")
        String protocolVersion,
        @Schema(description = "客户端 JSON-RPC 请求 id 的文本表示；通知没有 id 时为空。")
        String requestId,
        @Schema(description = "实际开始时间；尚未开始时为空。")
        Instant startedAt,
        @Schema(description = "执行耗时，单位毫秒。")
        long durationMillis,
        @Schema(description = "调用状态：SUCCESS 正常处理，ERROR 已进入协议处理但返回错误，REJECTED 在认证、限额或协议版本等 HTTP 边界被拒绝。")
        McpInvocationStatus status,
        @Schema(description = "请求体大小，单位字节。")
        long requestBytes,
        @Schema(description = "响应体大小，单位字节。")
        long responseBytes,
        @Schema(description = "Servlet 容器看到的调用方网络地址，仅用于运维审计。")
        String remoteAddress,
        @Schema(description = "调用方提交的 User-Agent 请求头；未提交时为空。")
        String userAgent,
        @Schema(description = "本次认证使用的 MCP 访问令牌 UUID；令牌记录已删除或认证前拒绝时可能为空。")
        UUID accessTokenId,
        @Schema(description = "MCP 访问令牌名称。")
        String accessTokenName,
        @Schema(description = "调用发生时令牌的修订号，用于区分轮换前后的使用记录。")
        Integer tokenRevision,
        @Schema(description = "经安全处理的错误摘要；没有错误时为空。")
        String errorSummary
) {
    public static McpInvocationResponse from(McpInvocationLog log) {
        return new McpInvocationResponse(log.getId(), log.getServerId(), log.getReleaseId(), log.getServerCode(),
                log.getReleaseVersion(), log.getToolCode(), log.getInvocationType(), log.getProtocolVersion(),
                log.getRequestId(), log.getStartedAt(), log.getDurationMillis(), log.getStatus(), log.getRequestBytes(),
                log.getResponseBytes(), log.getRemoteAddress(), log.getUserAgent(), log.getAccessTokenId(),
                log.getAccessTokenName(), log.getTokenRevision(), log.getErrorSummary());
    }
}
