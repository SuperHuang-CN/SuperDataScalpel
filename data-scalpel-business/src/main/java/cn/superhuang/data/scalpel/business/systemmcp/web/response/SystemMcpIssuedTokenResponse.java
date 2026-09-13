package cn.superhuang.data.scalpel.business.systemmcp.web.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "新创建或轮换后的系统 MCP 令牌；完整秘密只在本次响应展示一次")
public record SystemMcpIssuedTokenResponse(
        @Schema(description = "令牌元数据") SystemMcpTokenResponse token,
        @Schema(description = "带 dssmcp_ 前缀的完整令牌秘密；只在创建或轮换成功的本次响应展示，服务端不保留可恢复明文，轮换后旧秘密立即失效", accessMode = Schema.AccessMode.READ_ONLY)
        String secret
) {
}
