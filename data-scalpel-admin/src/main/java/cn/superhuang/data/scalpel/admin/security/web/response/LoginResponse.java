package cn.superhuang.data.scalpel.admin.security.web.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "后台登录成功后签发的 JWT 及其使用方式和到期时间。")
public record LoginResponse(
        @Schema(description = "已签名的登录 JWT；后续请求通过 Authorization: Bearer <accessToken> 携带，不应持久化到服务端日志。")
        String accessToken,
        @Schema(description = "认证方案，当前固定为 Bearer。")
        String tokenType,
        @Schema(description = "JWT 失效时间，ISO-8601 UTC 时间戳；到期后需重新登录。")
        Instant expiresAt
) {
}
