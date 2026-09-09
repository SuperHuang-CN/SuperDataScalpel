package cn.superhuang.data.scalpel.business.mcp.web.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record McpAccessTokenGrantRequest(@NotNull UUID accessTokenId) {}
