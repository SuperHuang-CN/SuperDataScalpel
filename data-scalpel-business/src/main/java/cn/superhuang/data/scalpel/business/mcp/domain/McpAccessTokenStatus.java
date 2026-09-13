package cn.superhuang.data.scalpel.business.mcp.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "原 MCP 平台访问令牌状态：ENABLED 可在未过期且已获 Server 授权时认证；DISABLED 立即拒绝后续请求。")
public enum McpAccessTokenStatus {
    ENABLED,
    DISABLED
}
