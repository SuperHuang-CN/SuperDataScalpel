package cn.superhuang.data.scalpel.business.mcp.web.request;

import jakarta.validation.constraints.NotNull;

import java.util.Set;
import java.util.UUID;

public record UpdateMcpAccessTokenServersRequest(@NotNull Set<@NotNull UUID> serverIds) {}
