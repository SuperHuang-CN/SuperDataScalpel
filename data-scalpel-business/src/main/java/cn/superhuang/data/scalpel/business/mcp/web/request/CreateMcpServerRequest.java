package cn.superhuang.data.scalpel.business.mcp.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@Schema(description = "在原 MCP 在线开发平台中创建一个可配置工具的 Server 草稿。")

public record CreateMcpServerRequest(
        @Schema(description = "MCP Server 稳定技术编码，创建后不可修改。")
        @NotBlank @Size(max=64) @Pattern(regexp="[a-z][a-z0-9_-]{1,63}") String code,
        @Schema(description = "MCP Server 显示名称。")
        @NotBlank @Size(max=100) String name,
        @Schema(description = "所属目录 UUID；位于根目录时为空。")
        UUID directoryId,
        @Schema(description = "用途说明；未填写时为空。")
        @Size(max=1000) String description,
        @Schema(description = "随 Server 能力提供给智能体的整体使用约束和操作说明。")
        @Size(max=20000) String instructions
) {}
