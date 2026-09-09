package cn.superhuang.data.scalpel.business.systemmcp.web.request;
public record UpdateSystemMcpTokenRequest(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=100) String name, java.time.Instant expiresAt) {
}
