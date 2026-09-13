package cn.superhuang.data.scalpel.business.service.accesslog.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.service.gateway.GatewayProvider;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "网关访问日志按小时、数据服务和消费者聚合的统计结果。")

public record GatewayAccessHourlyStatResponse(
        @Schema(description = "小时统计桶的起始时间。")
        Instant hourStart,
        @Schema(description = "网关提供方。")
        GatewayProvider gatewayProvider,
        @Schema(description = "本小时聚合对应的数据服务 UUID；按全局或消费者聚合时为空。")
        UUID dataServiceId,
        @Schema(description = "本小时聚合对应的 API 消费者 UUID；按全局或服务聚合时为空。")
        UUID consumerId,
        @Schema(description = "该小时、服务和消费者维度组合下的请求总数。")
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
        @Schema(description = "网关拒绝数量。")
        long gatewayRejectedCount,
        @Schema(description = "网关错误数量。")
        long gatewayErrorCount,
        @Schema(description = "上游错误数量。")
        long upstreamErrorCount,
        @Schema(description = "请求体大小，单位字节。")
        long requestBytes,
        @Schema(description = "响应体大小，单位字节。")
        long responseBytes,
        @Schema(description = "从网关接收请求到完成响应的平均总延迟，单位毫秒；没有样本时为空。")
        Double averageRequestLatencyMs,
        @Schema(description = "从网关接收请求到完成响应的最大总延迟，单位毫秒；没有样本时为空。")
        Long maximumRequestLatencyMs,
        @Schema(description = "请求总延迟的第 95 百分位数，单位毫秒；没有样本时为空。")
        Double requestLatencyP95Ms,
        @Schema(description = "请求总延迟的第 99 百分位数，单位毫秒；没有样本时为空。")
        Double requestLatencyP99Ms,
        @Schema(description = "网关转发到上游服务并等待响应的平均延迟，单位毫秒；没有样本时为空。")
        Double averageProxyLatencyMs,
        @Schema(description = "网关代理延迟的第 95 百分位数，单位毫秒；没有样本时为空。")
        Double proxyLatencyP95Ms,
        @Schema(description = "网关代理延迟的第 99 百分位数，单位毫秒；没有样本时为空。")
        Double proxyLatencyP99Ms
) {
}
