package cn.superhuang.data.scalpel.business.service.accesslog.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "API 网关访问趋势中的一个小时聚合点。")

public record GatewayAccessTrendPointResponse(
        @Schema(description = "小时统计桶的起始时间。")
        Instant hourStart,
        @Schema(description = "该时间桶内的网关请求总数。")
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
        @Schema(description = "该小时内在到达上游前被网关策略拒绝的请求数量。")
        long gatewayRejectedCount,
        @Schema(description = "该小时内被归类为网关自身处理错误的请求数量。")
        long gatewayErrorCount,
        @Schema(description = "该小时内被归类为数据服务上游错误的请求数量。")
        long upstreamErrorCount,
        @Schema(description = "该小时内所有请求大小的合计，单位字节。")
        long requestBytes,
        @Schema(description = "该小时内所有响应大小的合计，单位字节。")
        long responseBytes,
        @Schema(description = "该小时内所有分组的加权平均请求总延迟，单位毫秒；没有样本时为空。")
        Double averageRequestLatencyMs,
        @Schema(description = "该小时内观测到的最大请求总延迟，单位毫秒；没有样本时为空。")
        Long maximumRequestLatencyMs,
        @Schema(description = "该小时内各服务或消费者分组请求延迟 P95 的最大值，单位毫秒；没有样本时为空。")
        Double peakGroupedRequestLatencyP95Ms,
        @Schema(description = "该小时内各服务或消费者分组请求延迟 P99 的最大值，单位毫秒；没有样本时为空。")
        Double peakGroupedRequestLatencyP99Ms,
        @Schema(description = "该小时内所有分组的加权平均网关代理延迟，单位毫秒；没有样本时为空。")
        Double averageProxyLatencyMs,
        @Schema(description = "该小时内各服务或消费者分组代理延迟 P95 的最大值，单位毫秒；没有样本时为空。")
        Double peakGroupedProxyLatencyP95Ms,
        @Schema(description = "该小时内各服务或消费者分组代理延迟 P99 的最大值，单位毫秒；没有样本时为空。")
        Double peakGroupedProxyLatencyP99Ms
) {
}
