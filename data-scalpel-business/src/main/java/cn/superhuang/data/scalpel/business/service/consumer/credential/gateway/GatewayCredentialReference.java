package cn.superhuang.data.scalpel.business.service.consumer.credential.gateway;

import java.util.UUID;

public record GatewayCredentialReference(
        UUID id,
        UUID consumerId,
        String consumerCode,
        String consumerExternalId,
        String externalId
) {
}
