package cn.superhuang.data.scalpel.business.service.consumer.subscription.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.service.consumer.domain.ApiConsumer;
import cn.superhuang.data.scalpel.business.service.consumer.subscription.domain.ApiServiceSubscription;
import cn.superhuang.data.scalpel.business.service.consumer.subscription.domain.ApiServiceSubscriptionDesiredState;
import cn.superhuang.data.scalpel.business.service.consumer.subscription.domain.GatewaySubscriptionBinding;
import cn.superhuang.data.scalpel.business.service.domain.DataService;
import cn.superhuang.data.scalpel.business.service.domain.DataServiceAccessMode;
import cn.superhuang.data.scalpel.business.service.gateway.domain.GatewayServiceBinding;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Schema(description = "API 调用方对一个订阅制数据服务的期望授权及各网关实际状态。")

public record ApiServiceSubscriptionResponse(
        @Schema(description = "API 服务订阅 UUID。")
        UUID id,
        @Schema(description = "获得或曾获得该服务调用资格的 API 消费者 UUID。")
        UUID consumerId,
        @Schema(description = "API 调用方当前稳定编码；遗留引用无法解析时返回“已删除”。")
        String consumerCode,
        @Schema(description = "API 调用方当前名称；遗留引用无法解析时返回“已删除的消费者”。")
        String consumerName,
        @Schema(description = "被订阅的数据服务 UUID。")
        UUID dataServiceId,
        @Schema(description = "数据服务当前稳定编码；遗留引用无法解析时返回“已删除”。")
        String dataServiceCode,
        @Schema(description = "数据服务当前名称；遗留引用无法解析时返回“已删除的数据服务”。")
        String dataServiceName,
        @Schema(description = "当前可找到的已发布网关服务绑定中的对外路由路径；多提供方同时存在时取 publishedAt 较新的绑定。绑定缺失时为空，不是 Service Engine 的 contextPath。")
        String routePath,
        @Schema(description = "与 routePath 同一已发布网关绑定的访问模式；绑定缺失时为空。该摘要不代表每个 gatewayBindings 的实时远端状态。")
        DataServiceAccessMode accessMode,
        @Schema(description = "DataScalpel 期望各网关最终收敛到的状态。成功撤回后订阅会被删除，因此 REVOKED 通常只在撤回未完成或失败时可见。")
        ApiServiceSubscriptionDesiredState desiredState,
        @Schema(description = "该订阅在各目标网关中的授权或撤销状态。")
        List<GatewaySubscriptionBindingResponse> gatewayBindings,
        @Schema(description = "创建时间，ISO-8601 UTC 时间戳。")
        Instant createdAt,
        @Schema(description = "最后更新时间，ISO-8601 UTC 时间戳。")
        Instant updatedAt
) {

    public static ApiServiceSubscriptionResponse from(
            ApiServiceSubscription subscription,
            ApiConsumer consumer,
            DataService dataService,
            GatewayServiceBinding serviceBinding,
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
                serviceBinding == null ? null : serviceBinding.getGatewayRoutePath(),
                serviceBinding == null ? null : serviceBinding.getAccessMode(),
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
