package cn.superhuang.data.scalpel.business.mcp.web.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record CreateMcpAccessTokenRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 1000) String description,
        Instant expiresAt,
        Set<@jakarta.validation.constraints.NotNull UUID> serverIds
) {}
