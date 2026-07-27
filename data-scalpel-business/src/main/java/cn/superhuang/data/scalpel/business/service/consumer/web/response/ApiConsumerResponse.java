package cn.superhuang.data.scalpel.business.service.consumer.web.response;

import cn.superhuang.data.scalpel.business.service.consumer.domain.ApiConsumer;
import cn.superhuang.data.scalpel.business.service.consumer.domain.GatewayConsumerBinding;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public record ApiConsumerResponse(
        UUID id,
        String code,
        String name,
        String description,
        long revision,
        List<GatewayConsumerBindingResponse> gatewayBindings,
        Instant createdAt,
        Instant updatedAt
) {

    public static ApiConsumerResponse from(
            ApiConsumer consumer,
            List<GatewayConsumerBinding> bindings
    ) {
        return new ApiConsumerResponse(
                consumer.getId(),
                consumer.getCode(),
                consumer.getName(),
                consumer.getDescription(),
                consumer.getRevision(),
                bindings.stream()
                        .sorted(Comparator.comparing(binding -> binding.getProvider().name()))
                        .map(GatewayConsumerBindingResponse::from)
                        .toList(),
                consumer.getCreatedAt(),
                consumer.getUpdatedAt()
        );
    }
}
