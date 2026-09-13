package cn.superhuang.data.scalpel.business.mcp.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "原 MCP 在线开发平台 Server 状态：DRAFT 从未发布且不可调用；ENABLED 通过活动 Release 提供调用；DISABLED 已阻止调用但保留活动 Release，可重新启用。草稿修改不会自动改变活动 Release。")
public enum McpServerStatus {
    DRAFT,
    ENABLED,
    DISABLED
}
