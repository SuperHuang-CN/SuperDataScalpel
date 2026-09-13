package cn.superhuang.data.scalpel.business.service.consumer.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.service.consumer.domain.ApiConsumer;
import cn.superhuang.data.scalpel.business.service.consumer.domain.GatewayConsumerBinding;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Schema(description = "API 调用方主数据及其在各网关中的同步绑定；本地修订、网关同步状态和对账结论需分别判断。")

public record ApiConsumerResponse(
        @Schema(description = "API 调用方 UUID。")
        UUID id,
        @Schema(description = "调用方稳定技术编码，创建后不可修改。")
        String code,
        @Schema(description = "调用方显示名称。")
        String name,
        @Schema(description = "用途说明；未填写时为空。")
        String description,
        @Schema(description = "消费者内容修订号，创建时为 1，名称或说明每次更新后递增；用于判断网关绑定是否已同步当前内容，不是请求方必须提交的乐观锁版本。")
        long revision,
        @Schema(description = "网关绑定列表；没有时为空列表。")
        List<GatewayConsumerBindingResponse> gatewayBindings,
        @Schema(description = "创建时间，ISO-8601 UTC 时间戳。")
        Instant createdAt,
        @Schema(description = "最后更新时间，ISO-8601 UTC 时间戳。")
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
