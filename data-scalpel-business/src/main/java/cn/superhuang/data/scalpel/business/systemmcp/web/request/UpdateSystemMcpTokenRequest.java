package cn.superhuang.data.scalpel.business.systemmcp.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

@Schema(description = "修改系统 MCP 访问令牌的显示信息；不会改变令牌秘密或绑定用户")
public record UpdateSystemMcpTokenRequest(
        @Schema(description = "令牌名称，用于区分客户端或使用场景")
        @NotBlank @Size(max = 100) String name,
        @Schema(description = "新的过期时间，ISO-8601 UTC 时间；必须晚于当前时间，为空表示改为永不过期")
        Instant expiresAt
) {
}
