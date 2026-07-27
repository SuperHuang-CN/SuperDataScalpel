package cn.superhuang.data.scalpel.business.service.consumer.gateway;

import java.util.UUID;

public record GatewayConsumerReference(
        UUID id,
        String code,
        String externalId
) {
}
