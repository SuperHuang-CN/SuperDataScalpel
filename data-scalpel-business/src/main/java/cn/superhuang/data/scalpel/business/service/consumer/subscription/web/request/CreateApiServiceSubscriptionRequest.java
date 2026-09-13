package cn.superhuang.data.scalpel.business.service.consumer.subscription.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "授予一个 API 调用方访问订阅制数据服务的资格。")

public record CreateApiServiceSubscriptionRequest(
        @Schema(description = "获得调用资格的 API 消费者 UUID。")
        @NotNull UUID consumerId,
        @Schema(description = "要订阅的已通过网关发布且访问模式为 SUBSCRIPTION_REQUIRED 的数据服务 UUID。")
        @NotNull UUID dataServiceId
) {
}
