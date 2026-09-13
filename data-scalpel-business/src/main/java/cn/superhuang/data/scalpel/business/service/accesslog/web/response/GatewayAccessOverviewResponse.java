package cn.superhuang.data.scalpel.business.service.accesslog.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "指定统计窗口内 API 网关请求量、状态码、错误分类、流量和延迟聚合。")

public record GatewayAccessOverviewResponse(
        @Schema(description = "统计时间范围起点，包含该时刻。")
        Instant fromInclusive,
        @Schema(description = "统计时间范围终点，不包含该时刻。")
        Instant toExclusive,
        @Schema(description = "统计时间范围内接收到的网关请求总数。")
        long requestCount,
        @Schema(description = "HTTP 2xx 成功响应数量。")
        long status2xxCount,
        @Schema(description = "HTTP 3xx 重定向响应数量。")
        long status3xxCount,
        @Schema(description = "HTTP 4xx 客户端错误响应数量。")
        long status4xxCount,
        @Schema(description = "HTTP 5xx 服务端错误响应数量。")
        long status5xxCount,
        @Schema(description = "HTTP 401 未认证响应数量；同时包含在 status4xxCount 中。")
        long status401Count,
        @Schema(description = "HTTP 403 禁止访问响应数量；同时包含在 status4xxCount 中。")
        long status403Count,
        @Schema(description = "HTTP 429 请求过多响应数量；同时包含在 status4xxCount 中。")
        long status429Count,
        @Schema(description = "在到达上游服务前被网关鉴权、限流或路由策略拒绝的请求数量。")
        long gatewayRejectedCount,
        @Schema(description = "被归类为网关自身处理错误的请求数量。")
        long gatewayErrorCount,
        @Schema(description = "被归类为数据服务上游错误的请求数量。")
        long upstreamErrorCount,
        @Schema(description = "HTTP 2xx/3xx 请求数除以请求总数，范围 0 到 1；窗口内无请求时为 0。")
        double successRate,
        @Schema(description = "HTTP 4xx 请求数除以请求总数，范围 0 到 1；窗口内无请求时为 0。")
        double clientErrorRate,
        @Schema(description = "HTTP 5xx 请求数除以请求总数，范围 0 到 1；窗口内无请求时为 0。")
        double serverErrorRate,
        @Schema(description = "统计范围内所有请求大小的合计，单位字节。")
        long requestBytes,
        @Schema(description = "统计范围内所有响应大小的合计，单位字节。")
        long responseBytes,
        @Schema(description = "统计范围内全部请求的平均总延迟，单位毫秒；没有样本时为空。")
        Double averageRequestLatencyMs,
        @Schema(description = "统计范围内观测到的最大请求总延迟，单位毫秒；没有样本时为空。")
        Long maximumRequestLatencyMs,
        @Schema(description = "统计范围内各小时请求延迟 P95 的最大值，单位毫秒；没有样本时为空。")
        Double peakHourlyRequestLatencyP95Ms,
        @Schema(description = "统计范围内各小时请求延迟 P99 的最大值，单位毫秒；没有样本时为空。")
        Double peakHourlyRequestLatencyP99Ms,
        @Schema(description = "统计范围内网关转发到上游服务并等待响应的平均延迟，单位毫秒；没有样本时为空。")
        Double averageProxyLatencyMs,
        @Schema(description = "统计范围内各小时代理延迟 P95 的最大值，单位毫秒；没有样本时为空。")
        Double peakHourlyProxyLatencyP95Ms,
        @Schema(description = "统计范围内各小时代理延迟 P99 的最大值，单位毫秒；没有样本时为空。")
        Double peakHourlyProxyLatencyP99Ms
) {
}
