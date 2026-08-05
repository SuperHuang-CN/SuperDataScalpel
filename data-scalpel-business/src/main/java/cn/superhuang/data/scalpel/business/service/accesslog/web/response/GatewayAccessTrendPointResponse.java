package cn.superhuang.data.scalpel.business.service.accesslog.web.response;

import java.time.Instant;

public record GatewayAccessTrendPointResponse(
        Instant hourStart,
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
        long requestBytes,
        long responseBytes,
        Double averageRequestLatencyMs,
        Long maximumRequestLatencyMs,
        Double peakGroupedRequestLatencyP95Ms,
        Double peakGroupedRequestLatencyP99Ms,
        Double averageProxyLatencyMs,
        Double peakGroupedProxyLatencyP95Ms,
        Double peakGroupedProxyLatencyP99Ms
) {
}
