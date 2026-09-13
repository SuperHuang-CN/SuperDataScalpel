package cn.superhuang.data.scalpel.business.systemmcp.web.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "系统 MCP 专用访问令牌元数据；不返回令牌摘要或秘密")
public record SystemMcpTokenResponse(
        @Schema(description = "令牌 UUID") UUID id,
        @Schema(description = "令牌名称") String name,
        @Schema(description = "绑定系统用户 UUID") UUID userId,
        @Schema(description = "绑定用户当前登录名；用户已删除时为‘已删除用户’。调用权限在每次请求时按用户当前状态、角色和权限重新读取") String username,
        @Schema(description = "令牌是否启用；用户停用或删除时仍会拒绝调用") boolean enabled,
        @Schema(description = "秘密轮换版本；创建时为 1，每次轮换成功后递增 1") long revision,
        @Schema(description = "令牌过期时间，ISO-8601 UTC 时间；为空表示不过期") Instant expiresAt,
        @Schema(description = "最近一次通过认证的时间，ISO-8601 UTC 时间；从未使用时为空") Instant lastUsedAt,
        @Schema(description = "令牌创建时间，ISO-8601 UTC 时间") Instant createdAt,
        @Schema(description = "DSH 系统托管令牌，不允许通过普通管理接口修改") boolean managed
) {
}
