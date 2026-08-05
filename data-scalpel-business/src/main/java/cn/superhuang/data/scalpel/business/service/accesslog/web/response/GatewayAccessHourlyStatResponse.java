package cn.superhuang.data.scalpel.business.service.accesslog.web.response;

import cn.superhuang.data.scalpel.business.service.gateway.GatewayProvider;

import java.time.Instant;
import java.util.UUID;

public record GatewayAccessHourlyStatResponse(
        Instant hourStart,
        GatewayProvider gatewayProvider,
        UUID dataServiceId,
        UUID consumerId,
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
        Double requestLatencyP95Ms,
        Double requestLatencyP99Ms,
        Double averageProxyLatencyMs,
        Double proxyLatencyP95Ms,
        Double proxyLatencyP99Ms
) {
}
