package cn.superhuang.data.scalpel.business.mcp.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@Schema(description = "修改原 MCP 平台 Server 的显示信息和智能体说明；技术编码与工具列表单独维护。")

public record UpdateMcpServerRequest(
        @Schema(description = "MCP Server 显示名称。")
        @NotBlank @Size(max=100) String name,
        @Schema(description = "所属目录 UUID；位于根目录时为空。")
        UUID directoryId,
        @Schema(description = "用途说明；未填写时为空。")
        @Size(max=1000) String description,
        @Schema(description = "随 Server 能力提供给智能体的整体使用约束和操作说明。")
        @Size(max=20000) String instructions
) {}
