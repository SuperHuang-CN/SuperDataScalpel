package cn.superhuang.data.scalpel.business.operations.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

@Schema(description = "创建或整体修改一个 Webhook 告警渠道及其可选认证和签名密钥。")

public record SaveAlertChannelRequest(
        @Schema(description = "告警渠道显示名称。")
        @NotBlank @Size(max=150) String name,
        @Schema(description = "接收告警 POST 请求的绝对 HTTP(S) Webhook URL；必须包含 host，且不得包含用户凭据或 fragment。")
        @NotBlank @Size(max=2000) String url,
        @Schema(description = "是否启用；false 时不参与后续执行。")
        boolean enabled,
        @Schema(description = "调用 Webhook 时使用的新 Bearer Token；更新时为空表示保留原值，响应不返回明文。")
        @Size(max=4000) String bearerToken,
        @Schema(description = "Webhook HMAC 签名秘密；创建或更新时写入，响应不返回明文。")
        @Size(max=4000) String hmacSecret,
        @Schema(description = "true 时清除已保存的 Bearer 令牌；与 bearerToken 同时提供时拒绝请求。")
        Boolean clearBearerToken,
        @Schema(description = "true 时清除已保存的 HMAC 签名秘密；与 hmacSecret 同时提供时拒绝请求。")
        Boolean clearHmacSecret
) {
    public SaveAlertChannelRequest {
        clearBearerToken = Boolean.TRUE.equals(clearBearerToken);
        clearHmacSecret = Boolean.TRUE.equals(clearHmacSecret);
    }
}
