package cn.superhuang.data.scalpel.business.systemmcp.web.response;
public record SystemMcpConfigurationResponse(boolean enabled, String endpoint, String catalogStatus, String catalogMessage, java.time.Instant catalogUpdatedAt, long totalApis, long availableApis, long enabledApis) {
}
