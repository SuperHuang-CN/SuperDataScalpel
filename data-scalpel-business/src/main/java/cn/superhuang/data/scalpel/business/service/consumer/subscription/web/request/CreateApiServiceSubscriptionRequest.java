package cn.superhuang.data.scalpel.business.service.consumer.subscription.web.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateApiServiceSubscriptionRequest(
        @NotNull UUID consumerId,
        @NotNull UUID dataServiceId
) {
}
