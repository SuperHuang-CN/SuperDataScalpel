package cn.superhuang.superapigateway.controlplane.web.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record GrantSubscriptionRequest(
        @NotNull UUID consumerId,
        @NotNull UUID serviceId,
        @Size(max = 64) String source,
        @Size(max = 128) String externalId
) {
}
