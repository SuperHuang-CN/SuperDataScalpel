package cn.superhuang.data.scalpel.business.mcp.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.mcp.domain.McpAccessToken;
import cn.superhuang.data.scalpel.business.mcp.domain.McpAccessTokenStatus;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "原 MCP 平台访问令牌的非敏感元数据、状态和授权 Server 数量。")

public record McpAccessTokenResponse(
        @Schema(description = "MCP 访问令牌记录 UUID。")
        UUID id,
        @Schema(description = "便于管理员识别用途的令牌显示名称。")
        String name,
        @Schema(description = "用途说明；未填写时为空。")
        String description,
        @Schema(description = "令牌状态：ENABLED 可在有效期内认证，DISABLED 立即拒绝后续请求。")
        McpAccessTokenStatus status,
        @Schema(description = "认证过期时间，ISO-8601 UTC 时间戳；为空表示不过期。即使状态为 ENABLED，到达该时间后也会拒绝请求。")
        Instant expiresAt,
        @Schema(description = "用于识别令牌的不可逆短提示，不是可用秘密。")
        String hint,
        @Schema(description = "令牌秘密轮换版本，创建时为 1，每次轮换后递增；修改名称、过期时间、状态或 Server 授权不会改变该值，也不是请求方提交的乐观锁版本。")
        int revision,
        @Schema(description = "当前令牌秘密生成时间，ISO-8601 UTC 时间戳；创建时即有值，每次轮换后更新。")
        Instant rotatedAt,
        @Schema(description = "令牌最近成功使用时间；从未使用时为空。")
        Instant lastUsedAt,
        @Schema(description = "当前令牌可访问的 MCP 服务器数量。")
        long authorizedServerCount,
        @Schema(description = "创建时间，ISO-8601 UTC 时间戳。")
        Instant createdAt,
        @Schema(description = "最后更新时间，ISO-8601 UTC 时间戳。")
        Instant updatedAt
) {
    public static McpAccessTokenResponse from(McpAccessToken token, long authorizedServerCount) {
        return new McpAccessTokenResponse(token.getId(), token.getName(), token.getDescription(), token.getStatus(),
                token.getExpiresAt(), token.getTokenHint(), token.getRevision(), token.getRotatedAt(), token.getLastUsedAt(),
                authorizedServerCount, token.getCreatedAt(), token.getUpdatedAt());
    }
}
