package cn.superhuang.data.scalpel.business.systemmcp.web.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "系统 MCP 接口目录中的 OpenAPI Tag 分类统计")
public record SystemMcpModuleResponse(
        @Schema(description = "来源于 Resource 类 OpenAPI Tag 的业务模块名称") String module,
        @Schema(description = "该模块下的接口总数") long totalApis,
        @Schema(description = "该模块下当前支持开放的接口数") long availableApis,
        @Schema(description = "该模块下已经开放的接口数") long enabledApis
) {
}
