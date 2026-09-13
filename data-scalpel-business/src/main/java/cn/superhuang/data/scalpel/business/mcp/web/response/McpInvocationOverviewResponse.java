package cn.superhuang.data.scalpel.business.mcp.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "原 MCP 平台最近 24 小时协议请求的成功率和平均耗时概览。")

public record McpInvocationOverviewResponse(
        @Schema(description = "统计窗口起始时间；当前实现统计最近 24 小时。")
        Instant since,
        @Schema(description = "统计窗口内记录的 MCP 协议请求总数，包含成功、协议错误和传输拒绝。")
        long total,
        @Schema(description = "统计窗口内状态为 SUCCESS 的请求数。")
        long succeeded,
        @Schema(description = "统计窗口内状态为 ERROR 或 REJECTED 的请求数。")
        long failed,
        @Schema(description = "成功请求数除以请求总数，范围 0 到 1；窗口内无请求时为 0。")
        double successRate,
        @Schema(description = "统计窗口内全部请求从接收到生成响应的平均耗时，单位毫秒。")
        double averageDurationMillis
) {}
