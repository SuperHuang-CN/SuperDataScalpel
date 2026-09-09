package cn.superhuang.data.scalpel.business.mcp.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ds_mcp_invocation_log", indexes = {
        @Index(name = "idx_ds_mcp_invocation_started", columnList = "started_at"),
        @Index(name = "idx_ds_mcp_invocation_server", columnList = "server_id,started_at"),
        @Index(name = "idx_ds_mcp_invocation_tool", columnList = "tool_code,started_at"),
        @Index(name = "idx_ds_mcp_invocation_token", columnList = "access_token_id,started_at")
})
public class McpInvocationLog extends BaseEntity {
    @Column(name = "server_id") private UUID serverId;
    @Column(name = "release_id") private UUID releaseId;
    @Column(name = "server_code", length = 64) private String serverCode;
    @Column(name = "release_version") private Integer releaseVersion;
    @Column(name = "tool_code", length = 64) private String toolCode;
    @Column(name = "invocation_type", nullable = false, length = 64) private String invocationType;
    @Column(name = "protocol_version", length = 32) private String protocolVersion;
    @Column(name = "request_id", length = 128) private String requestId;
    @Column(name = "started_at", nullable = false) private Instant startedAt;
    @Column(name = "duration_millis", nullable = false) private long durationMillis;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private McpInvocationStatus status;
    @Column(name = "request_bytes", nullable = false) private long requestBytes;
    @Column(name = "response_bytes", nullable = false) private long responseBytes;
    @Column(name = "remote_address", length = 128) private String remoteAddress;
    @Column(name = "user_agent", length = 500) private String userAgent;
    @Column(name = "token_revision") private Integer tokenRevision;
    @Column(name = "access_token_id") private UUID accessTokenId;
    @Column(name = "access_token_name", length = 100) private String accessTokenName;
    @Column(name = "error_summary", length = 1000) private String errorSummary;

    protected McpInvocationLog() {}
    public static McpInvocationLog create(UUID serverId, UUID releaseId, String serverCode, Integer releaseVersion,
            String toolCode, String invocationType, String protocolVersion, String requestId, Instant startedAt,
            long durationMillis, McpInvocationStatus status, long requestBytes, long responseBytes,
            String remoteAddress, String userAgent, UUID accessTokenId, String accessTokenName,
            Integer tokenRevision, String errorSummary) {
        McpInvocationLog log = new McpInvocationLog();
        log.serverId=serverId; log.releaseId=releaseId; log.serverCode=clip(serverCode,64); log.releaseVersion=releaseVersion;
        log.toolCode=clip(toolCode,64); log.invocationType=clip(invocationType,64); log.protocolVersion=clip(protocolVersion,32);
        log.requestId=clip(requestId,128); log.startedAt=startedAt; log.durationMillis=durationMillis; log.status=status;
        log.requestBytes=requestBytes; log.responseBytes=responseBytes; log.remoteAddress=clip(remoteAddress,128);
        log.userAgent=clip(userAgent,500); log.accessTokenId=accessTokenId; log.accessTokenName=clip(accessTokenName,100);
        log.tokenRevision=tokenRevision; log.errorSummary=clip(errorSummary,1000);
        return log;
    }
    private static String clip(String value, int size) { return value == null ? null : value.substring(0, Math.min(value.length(), size)); }
    public UUID getServerId(){return serverId;} public UUID getReleaseId(){return releaseId;} public String getServerCode(){return serverCode;}
    public Integer getReleaseVersion(){return releaseVersion;} public String getToolCode(){return toolCode;} public String getInvocationType(){return invocationType;}
    public String getProtocolVersion(){return protocolVersion;} public String getRequestId(){return requestId;} public Instant getStartedAt(){return startedAt;}
    public long getDurationMillis(){return durationMillis;} public McpInvocationStatus getStatus(){return status;} public long getRequestBytes(){return requestBytes;}
    public long getResponseBytes(){return responseBytes;} public String getRemoteAddress(){return remoteAddress;} public String getUserAgent(){return userAgent;}
    public Integer getTokenRevision(){return tokenRevision;} public String getErrorSummary(){return errorSummary;}
    public UUID getAccessTokenId(){return accessTokenId;} public String getAccessTokenName(){return accessTokenName;}
}
