package cn.superhuang.data.scalpel.business.service.accesslog.web.response;

import java.time.Instant;

public record GatewayAccessOverviewResponse(
        Instant fromInclusive,
        Instant toExclusive,
        long requestCount,
        long status2xxCount,
        long status3xxCount,
        long status4xxCount,
        long status5xxCount,
        long status401Count,
        long status403Count,
        long status429Count,
        long gatewayRejectedCount,
        long gatewayErrorCount,
        long upstreamErrorCount,
        double successRate,
        double clientErrorRate,
        double serverErrorRate,
        long requestBytes,
        long responseBytes,
        Double averageRequestLatencyMs,
        Long maximumRequestLatencyMs,
        Double peakHourlyRequestLatencyP95Ms,
        Double peakHourlyRequestLatencyP99Ms,
        Double averageProxyLatencyMs,
        Double peakHourlyProxyLatencyP95Ms,
        Double peakHourlyProxyLatencyP99Ms
) {
}
