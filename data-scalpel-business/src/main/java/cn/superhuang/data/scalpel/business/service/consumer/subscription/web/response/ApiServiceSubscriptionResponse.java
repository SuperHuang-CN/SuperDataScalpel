package cn.superhuang.data.scalpel.business.service.consumer.subscription.web.response;

import cn.superhuang.data.scalpel.business.service.consumer.domain.ApiConsumer;
import cn.superhuang.data.scalpel.business.service.consumer.subscription.domain.ApiServiceSubscription;
import cn.superhuang.data.scalpel.business.service.consumer.subscription.domain.ApiServiceSubscriptionDesiredState;
import cn.superhuang.data.scalpel.business.service.consumer.subscription.domain.GatewaySubscriptionBinding;
import cn.superhuang.data.scalpel.business.service.domain.DataService;
import cn.superhuang.data.scalpel.business.service.domain.DataServiceAccessMode;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public record ApiServiceSubscriptionResponse(
        UUID id,
        UUID consumerId,
        String consumerCode,
        String consumerName,
        UUID dataServiceId,
        String dataServiceCode,
        String dataServiceName,
        String routePath,
        DataServiceAccessMode accessMode,
        ApiServiceSubscriptionDesiredState desiredState,
        List<GatewaySubscriptionBindingResponse> gatewayBindings,
        Instant createdAt,
        Instant updatedAt
) {

    public static ApiServiceSubscriptionResponse from(
            ApiServiceSubscription subscription,
            ApiConsumer consumer,
            DataService dataService,
            List<GatewaySubscriptionBinding> bindings
    ) {
        return new ApiServiceSubscriptionResponse(
                subscription.getId(),
                subscription.getConsumerId(),
                consumer == null ? "已删除" : consumer.getCode(),
                consumer == null ? "已删除的消费者" : consumer.getName(),
                subscription.getDataServiceId(),
                dataService == null ? "已删除" : dataService.getCode(),
                dataService == null ? "已删除的数据服务" : dataService.getName(),
                dataService == null ? null : dataService.getRoutePath(),
                dataService == null ? null : dataService.getAccessMode(),
                subscription.getDesiredState(),
                bindings.stream()
                        .sorted(Comparator.comparing(binding -> binding.getProvider().name()))
                        .map(GatewaySubscriptionBindingResponse::from)
                        .toList(),
                subscription.getCreatedAt(),
                subscription.getUpdatedAt()
        );
    }
}
