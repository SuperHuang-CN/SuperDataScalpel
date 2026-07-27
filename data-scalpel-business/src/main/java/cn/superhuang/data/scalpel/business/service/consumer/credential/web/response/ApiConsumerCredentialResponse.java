package cn.superhuang.data.scalpel.business.service.consumer.credential.web.response;

import cn.superhuang.data.scalpel.business.service.consumer.credential.domain.ApiConsumerCredential;
import cn.superhuang.data.scalpel.business.service.consumer.credential.domain.GatewayCredentialBinding;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public record ApiConsumerCredentialResponse(
        UUID id,
        UUID consumerId,
        String name,
        String secretHint,
        long revision,
        List<GatewayCredentialBindingResponse> gatewayBindings,
        Instant createdAt,
        Instant updatedAt
) {

    public static ApiConsumerCredentialResponse from(
            ApiConsumerCredential credential,
            List<GatewayCredentialBinding> bindings
    ) {
        return new ApiConsumerCredentialResponse(
                credential.getId(),
                credential.getConsumerId(),
                credential.getName(),
                credential.getSecretHint(),
                credential.getRevision(),
                bindings.stream()
                        .sorted(Comparator.comparing(binding -> binding.getProvider().name()))
                        .map(GatewayCredentialBindingResponse::from)
                        .toList(),
                credential.getCreatedAt(),
                credential.getUpdatedAt()
        );
    }
}
