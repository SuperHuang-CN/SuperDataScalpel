package cn.superhuang.data.scalpel.business.systemmcp.web.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "系统 MCP 全局配置、连接地址和接口目录同步概况")
public record SystemMcpConfigurationResponse(
        @Schema(description = "系统 MCP 总开关；关闭时三个工具均不可调用") boolean enabled,
        @Schema(description = "供 MCP 客户端连接的公开地址；与内部回环调用地址分开") String endpoint,
        @Schema(description = "接口目录同步状态：NOT_READY 尚未同步、SYNCING 正在同步、READY 已就绪、ERROR 最近同步失败") String catalogStatus,
        @Schema(description = "同步进行中或失败时的结果说明；READY 时为空") String catalogMessage,
        @Schema(description = "接口目录最后成功更新时间，ISO-8601 UTC 时间；尚未成功同步时为空") Instant catalogUpdatedAt,
        @Schema(description = "目录中记录的接口总数，包括不可用接口") long totalApis,
        @Schema(description = "当前代码中契约完整且受支持的接口数") long availableApis,
        @Schema(description = "当前已开放并可用的接口数") long enabledApis
) {
}
