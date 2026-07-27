package cn.superhuang.data.scalpel.business.service.consumer.subscription.gateway;

import java.util.UUID;

public record GatewaySubscriptionSpec(
        UUID id,
        UUID consumerId,
        String consumerCode,
        String consumerExternalId,
        UUID dataServiceId,
        String dataServiceCode,
        String serviceExternalId,
        String routeExternalId
) {
}
