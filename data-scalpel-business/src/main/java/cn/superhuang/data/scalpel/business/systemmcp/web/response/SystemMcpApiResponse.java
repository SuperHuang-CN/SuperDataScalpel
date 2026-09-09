package cn.superhuang.data.scalpel.business.systemmcp.web.response;
public record SystemMcpApiResponse(java.util.UUID id, String operationId, String method, String path, String module, String summary, String description, String effect, String status, String unavailableReason, boolean enabled, String fingerprint, java.time.Instant updatedAt, tools.jackson.databind.JsonNode contract) {
}
