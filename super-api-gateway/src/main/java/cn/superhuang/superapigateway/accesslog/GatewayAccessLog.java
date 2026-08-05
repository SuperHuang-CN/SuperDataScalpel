package cn.superhuang.superapigateway.accesslog;

import java.time.Instant;

public record GatewayAccessLog(
        int schemaVersion,
        Instant timestamp,
        String requestId,
        String instanceId,
        String serviceId,
        String serviceCode,
        String routeId,
        String routeCode,
        String consumerId,
        String consumerCode,
        String method,
        String pathTemplate,
        int status,
        long durationMs
) {
}
