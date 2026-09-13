package cn.superhuang.data.scalpel.business.mcp.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "原 MCP 平台调用记录状态：SUCCESS 协议请求正常完成；ERROR 已进入 MCP 协议处理并返回错误；REJECTED 在认证、授权、限额或请求边界被拒绝。")
public enum McpInvocationStatus {
    SUCCESS,
    ERROR,
    REJECTED
}
