package cn.superhuang.data.scalpel.business.service.consumer.credential.gateway;

import java.util.UUID;

public record GatewayCredentialInspectionSpec(
        UUID id,
        UUID consumerId,
        String consumerCode,
        String consumerExternalId,
        String externalId,
        String secretDigest,
        boolean expectedPresent
) {

    public GatewayCredentialInspectionSpec {
        if (id == null) throw new IllegalArgumentException("Credential ID is required");
        if (consumerId == null) throw new IllegalArgumentException("Consumer ID is required");
        if (consumerCode == null || consumerCode.isBlank()) {
            throw new IllegalArgumentException("Consumer code is required");
        }
        if (secretDigest == null || secretDigest.isBlank()) {
            throw new IllegalArgumentException("Credential secret digest is required");
        }
    }
}
