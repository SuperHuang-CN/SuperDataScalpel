package cn.superhuang.superapigateway.controlplane.web.response;

import cn.superhuang.superapigateway.controlplane.domain.GatewayHttpMethod;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record RouteResponse(
        UUID id,
        UUID serviceId,
        String code,
        String name,
        String pathPattern,
        Set<GatewayHttpMethod> methods,
        int order,
        int stripPrefixSegments,
        String upstreamPath,
        boolean enabled,
        String source,
        String externalId,
        long revision,
        Instant createdAt,
        Instant updatedAt
) {
}
