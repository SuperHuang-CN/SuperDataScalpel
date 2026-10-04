package cn.superhuang.data.scalpel.business.service.gateway.web.request;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import java.time.Instant;

@Schema(description = "自研网关有效期；修改不改变凭证或订阅的启停/撤回状态。")
public record GatewayAccessValidityRequest(
        @Schema(description = "生效时间（包含），ISO-8601；空表示立即生效。") Instant validFrom,
        @Schema(description = "到期时间（不包含），ISO-8601；空表示不自动到期。必须晚于生效时间。") Instant expiresAt,
        @Schema(description = "单节点此订阅每秒请求令牌数，0～1000000，零不额外限制；凭证必须为零。与服务限流共同生效。")
        @Min(0) @Max(1_000_000) int requestsPerSecond
) {}
