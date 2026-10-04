package cn.superhuang.data.scalpel.business.service.gateway.web.response;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "网关有效期和所连接节点的配置修订；到期在每次请求检查，不依赖定时清理。")
public record GatewayAccessValidityResponse(
        @Schema(description = "生效时间（包含）；空表示无起点限制。") Instant validFrom,
        @Schema(description = "到期时间（不包含）；空表示无到期限制。") Instant expiresAt,
        @Schema(description = "订阅单节点每秒令牌数，零表示不限；凭证固定零。") int requestsPerSecond,
        @Schema(description = "对象自身的状态：ACTIVE、NOT_YET_VALID、EXPIRED 或 REVOKED；仍须消费者启用且具备服务订阅。") String state,
        @Schema(description = "网关目标修订。") long targetRevision,
        @Schema(description = "本次连接节点已加载修订；小于目标表示待生效。") long loadedRevision
) {}
