package cn.superhuang.data.scalpel.business.service.consumer.credential.gateway;

import java.util.UUID;

public record GatewayCredentialSpec(
        UUID id,
        UUID consumerId,
        String consumerCode,
        String consumerExternalId,
        String secret,
        long revision
) {
}
