package cn.superhuang.superapigateway.controlplane.web.response;

import java.time.Instant;

public record AuthResponse(String accessToken, String tokenType, Instant expiresAt, String username) {
}
