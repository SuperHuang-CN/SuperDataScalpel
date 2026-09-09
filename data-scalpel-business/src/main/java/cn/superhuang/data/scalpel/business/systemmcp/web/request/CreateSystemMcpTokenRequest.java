package cn.superhuang.data.scalpel.business.systemmcp.web.request;
public record CreateSystemMcpTokenRequest(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=100) String name, @jakarta.validation.constraints.NotNull java.util.UUID userId, java.time.Instant expiresAt) {
}
