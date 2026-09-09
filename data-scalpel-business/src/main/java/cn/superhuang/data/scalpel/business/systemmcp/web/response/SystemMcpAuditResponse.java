package cn.superhuang.data.scalpel.business.systemmcp.web.response;
public record SystemMcpAuditResponse(java.util.UUID id, String eventType, String username, java.util.UUID tokenId, String operationId, String toolName, String status, String errorCode, long durationMs, String changesJson, java.time.Instant createdAt) {
}
