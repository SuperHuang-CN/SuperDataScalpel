package cn.superhuang.data.scalpel.business.mcp.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.Set;
import java.util.UUID;

@Schema(description = "整体替换原 MCP 平台访问令牌允许连接的 Server 范围。")

public record UpdateMcpAccessTokenServersRequest(
        @Schema(description = "更新后获准访问的现有 MCP Server UUID 集合；可包含尚未发布或已停用的 Server，空集合会移除全部 Server 授权。")
        @NotNull Set<@NotNull UUID> serverIds
) {}
