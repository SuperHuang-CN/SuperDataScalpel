package cn.superhuang.data.scalpel.business.mcp.web.response;

import java.time.Instant;

public record McpTokenResponse(String hint, int revision, Instant rotatedAt, String accessToken) {}
