package cn.superhuang.superapigateway.controlplane.web.response;

import cn.superhuang.superapigateway.controlplane.domain.SubscriptionStatus;

import java.time.Instant;
import java.util.UUID;

public record SubscriptionResponse(
        UUID id,
        UUID consumerId,
        UUID serviceId,
        SubscriptionStatus status,
        String source,
        String externalId,
        Instant createdAt,
        Instant updatedAt
) {
}
