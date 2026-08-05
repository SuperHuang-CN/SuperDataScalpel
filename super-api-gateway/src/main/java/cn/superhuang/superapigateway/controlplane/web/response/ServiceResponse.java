package cn.superhuang.superapigateway.controlplane.web.response;

import cn.superhuang.superapigateway.controlplane.domain.AccessMode;

import java.time.Instant;
import java.util.UUID;

public record ServiceResponse(
        UUID id,
        String code,
        String name,
        String upstreamUri,
        AccessMode accessMode,
        int connectTimeoutMs,
        int responseTimeoutMs,
        boolean enabled,
        String description,
        String source,
        String externalId,
        long revision,
        long routeCount,
        Instant createdAt,
        Instant updatedAt
) {
}
