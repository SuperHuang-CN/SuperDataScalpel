package cn.superhuang.data.scalpel.business.mcp.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "已停用的 Server 级旧令牌结构；对应接口当前返回 410，应使用独立 MCP 访问令牌。")

public record McpTokenResponse(
        @Schema(description = "用于识别令牌的不可逆短提示，不是可用秘密。")
        String hint,
        @Schema(description = "旧版 Server 级令牌的秘密轮换版本，创建时为 1，每次轮换后递增。")
        int revision,
        @Schema(description = "旧版令牌当前秘密的生成时间，创建时即有值，每次轮换后更新。")
        Instant rotatedAt,
        @Schema(description = "旧版完整访问令牌；仅历史兼容结构可能返回。")
        String accessToken
) {}
