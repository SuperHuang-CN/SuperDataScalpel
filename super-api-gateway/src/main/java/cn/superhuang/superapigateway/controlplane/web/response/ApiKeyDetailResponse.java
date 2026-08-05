package cn.superhuang.superapigateway.controlplane.web.response;

import cn.superhuang.superapigateway.controlplane.domain.ApiKeyStatus;

import java.time.Instant;
import java.util.UUID;

public record ApiKeyDetailResponse(
        UUID id,
        UUID consumerId,
        String name,
        String prefix,
        String lastFour,
        ApiKeyStatus status,
        Instant rotatedAt,
        String source,
        String externalId,
        String secretDigest,
        Instant createdAt,
        Instant updatedAt
) {
}
