package cn.superhuang.data.scalpel.business.mcp.web.response;

import cn.superhuang.data.scalpel.business.mcp.domain.McpInvocationLog;
import cn.superhuang.data.scalpel.business.mcp.domain.McpInvocationStatus;

import java.time.Instant;
import java.util.UUID;

public record McpInvocationResponse(UUID id, UUID serverId, UUID releaseId, String serverCode, Integer releaseVersion,
        String toolCode, String invocationType, String protocolVersion, String requestId, Instant startedAt,
        long durationMillis, McpInvocationStatus status, long requestBytes, long responseBytes,
        String remoteAddress, String userAgent, UUID accessTokenId, String accessTokenName,
        Integer tokenRevision, String errorSummary) {
    public static McpInvocationResponse from(McpInvocationLog log) {
        return new McpInvocationResponse(log.getId(), log.getServerId(), log.getReleaseId(), log.getServerCode(),
                log.getReleaseVersion(), log.getToolCode(), log.getInvocationType(), log.getProtocolVersion(),
                log.getRequestId(), log.getStartedAt(), log.getDurationMillis(), log.getStatus(), log.getRequestBytes(),
                log.getResponseBytes(), log.getRemoteAddress(), log.getUserAgent(), log.getAccessTokenId(),
                log.getAccessTokenName(), log.getTokenRevision(), log.getErrorSummary());
    }
}
