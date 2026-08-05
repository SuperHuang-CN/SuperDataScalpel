package cn.superhuang.superapigateway.controlplane.web.response;

import java.time.Instant;
import java.util.UUID;

public record ConsumerResponse(
        UUID id,
        String code,
        String name,
        boolean enabled,
        String description,
        String source,
        String externalId,
        long revision,
        Instant createdAt,
        Instant updatedAt
) {
}
