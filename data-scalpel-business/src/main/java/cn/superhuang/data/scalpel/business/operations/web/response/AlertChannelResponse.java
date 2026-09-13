package cn.superhuang.data.scalpel.business.operations.web.response;

import io.swagger.v3.oas.annotations.media.Schema;import cn.superhuang.data.scalpel.business.operations.domain.AlertChannel;
import java.util.UUID;
@Schema(description = "Webhook 告警渠道的非敏感配置和凭据配置状态。")
public record AlertChannelResponse(
        @Schema(description = "告警渠道 UUID。")
        UUID id,
        @Schema(description = "告警渠道显示名称。")
        String name,
        @Schema(description = "接收告警 POST 请求的 HTTP(S) Webhook URL；认证凭据不会拼入或随响应返回。")
        String url,
        @Schema(description = "是否启用；false 时不参与后续执行。")
        boolean enabled,
        @Schema(description = "Webhook 执行配置版本，创建时为 1；URL、启停状态或凭据变化时递增，已排队投递仅在版本仍匹配时发送。")
        long configurationVersion,
        @Schema(description = "是否已保存用于调用 Webhook 的 Bearer 令牌；不返回令牌明文。")
        boolean bearerTokenConfigured,
        @Schema(description = "是否已保存用于签名 Webhook 请求的 HMAC 秘密；不返回秘密明文。")
        boolean hmacSecretConfigured
) {
    public static AlertChannelResponse from(AlertChannel c) {
        return new AlertChannelResponse(c.getId(), c.getName(), c.getUrl(), c.getEnabled(), c.getConfigurationVersion(),
                c.getBearerCiphertext() != null, c.getHmacCiphertext() != null);
    }
}
