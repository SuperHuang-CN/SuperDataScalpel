package cn.superhuang.data.scalpel.admin.security.web.response;

import java.time.Instant;

public record LoginResponse(String accessToken, String tokenType, Instant expiresAt) {
}
