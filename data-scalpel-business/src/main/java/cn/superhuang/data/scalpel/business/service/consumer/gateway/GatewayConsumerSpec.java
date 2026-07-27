package cn.superhuang.data.scalpel.business.service.consumer.gateway;

import java.util.UUID;

public record GatewayConsumerSpec(
        UUID id,
        String code,
        String name,
        String description,
        long revision
) {
}
