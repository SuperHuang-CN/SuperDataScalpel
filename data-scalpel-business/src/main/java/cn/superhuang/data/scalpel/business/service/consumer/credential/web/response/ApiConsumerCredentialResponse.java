package cn.superhuang.data.scalpel.business.service.consumer.credential.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.service.consumer.credential.domain.ApiConsumerCredential;
import cn.superhuang.data.scalpel.business.service.consumer.credential.domain.GatewayCredentialBinding;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Schema(description = "API 调用方凭证的非敏感元数据及网关同步状态。")

public record ApiConsumerCredentialResponse(
        @Schema(description = "API 调用方凭证 UUID。")
        UUID id,
        @Schema(description = "持有该 API Key 的消费者 UUID。")
        UUID consumerId,
        @Schema(description = "凭证显示名称。")
        String name,
        @Schema(description = "凭证秘密的不可逆短提示，不可用于认证。")
        String secretHint,
        @Schema(description = "密钥修订号，创建时为 1，每次轮换后递增；用于判断各网关绑定是否已同步当前密钥，不是请求方提交的乐观锁版本。")
        long revision,
        @Schema(description = "网关绑定列表；没有时为空列表。")
        List<GatewayCredentialBindingResponse> gatewayBindings,
        @Schema(description = "创建时间，ISO-8601 UTC 时间戳。")
        Instant createdAt,
        @Schema(description = "最后更新时间，ISO-8601 UTC 时间戳。")
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
