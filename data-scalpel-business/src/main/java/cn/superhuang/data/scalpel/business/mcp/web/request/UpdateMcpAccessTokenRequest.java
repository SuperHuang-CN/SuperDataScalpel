package cn.superhuang.data.scalpel.business.mcp.web.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record UpdateMcpAccessTokenRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 1000) String description,
        Instant expiresAt
) {}
