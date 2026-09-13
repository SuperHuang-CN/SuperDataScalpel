package cn.superhuang.data.scalpel.business.service.consumer.subscription.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.service.consumer.subscription.domain.GatewaySubscriptionBinding;
import cn.superhuang.data.scalpel.business.service.consumer.subscription.domain.GatewaySubscriptionStatus;
import cn.superhuang.data.scalpel.business.service.gateway.GatewayProvider;
import cn.superhuang.data.scalpel.business.service.gateway.reconciliation.GatewayReconciliationReason;
import cn.superhuang.data.scalpel.business.service.gateway.reconciliation.GatewayReconciliationStatus;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "一个 API 服务订阅在单个网关中的成员关系、异步操作和对账状态。")

public record GatewaySubscriptionBindingResponse(
        @Schema(description = "网关订阅绑定 UUID。")
        UUID id,
        @Schema(description = "保存该订阅成员关系的网关提供方。")
        GatewayProvider provider,
        @Schema(description = "网关侧消费者与服务成员关系标识；尚未授权成功或远端缺失时为空。")
        String externalMembershipId,
        @Schema(description = "订阅授权状态：GRANT_PENDING 待授权，GRANTED 已生效，GRANT_FAILED 授权失败，REVOKE_PENDING 待撤销，REVOKE_FAILED 撤销失败。")
        GatewaySubscriptionStatus status,
        @Schema(description = "最近一次安全错误摘要；没有错误时为空。")
        String lastError,
        @Schema(description = "当前异步授权或撤销操作 UUID；没有在途操作时为空。")
        UUID operationId,
        @Schema(description = "当前授权或撤回操作开始时间，ISO-8601 UTC 时间戳；仅 GRANT_PENDING 或 REVOKE_PENDING 时有值，进入终态后为空。")
        Instant operationStartedAt,
        @Schema(description = "最近一次成功授权的时间，ISO-8601 UTC 时间戳；从未成功授权时为空。后续撤回尝试或失败不会清除此历史时间。")
        Instant grantedAt,
        @Schema(description = "最近一次显式对账状态：NOT_CHECKED、CHECKING、IN_SYNC、DRIFTED 或 CHECK_FAILED。")
        GatewayReconciliationStatus reconciliationStatus,
        @Schema(description = "发现漂移时的稳定原因，例如成员关系缺失、意外存在或归属不一致。")
        GatewayReconciliationReason reconciliationReason,
        @Schema(description = "最近一次网关对账的安全结果说明。")
        String reconciliationMessage,
        @Schema(description = "当前对账操作 UUID，用于防止较早检查结果覆盖较新的状态；仅 reconciliationStatus=CHECKING 时有值。")
        UUID reconciliationOperationId,
        @Schema(description = "当前对账开始时间，ISO-8601 UTC 时间戳；仅 reconciliationStatus=CHECKING 时有值。")
        Instant reconciliationStartedAt,
        @Schema(description = "最近一次完成或失败的对账时间，ISO-8601 UTC 时间戳；从未完成对账时为空。")
        Instant lastReconciledAt,
        @Schema(description = "创建时间，ISO-8601 UTC 时间戳。")
        Instant createdAt,
        @Schema(description = "最后更新时间，ISO-8601 UTC 时间戳。")
        Instant updatedAt
) {

    public static GatewaySubscriptionBindingResponse from(GatewaySubscriptionBinding binding) {
        return new GatewaySubscriptionBindingResponse(
                binding.getId(),
                binding.getProvider(),
                binding.getExternalMembershipId(),
                binding.getStatus(),
                binding.getLastError(),
                binding.getOperationId(),
                binding.getOperationStartedAt(),
                binding.getGrantedAt(),
                binding.getReconciliationStatus(),
                binding.getReconciliationReason(),
                binding.getReconciliationMessage(),
                binding.getReconciliationOperationId(),
                binding.getReconciliationStartedAt(),
                binding.getLastReconciledAt(),
                binding.getCreatedAt(),
                binding.getUpdatedAt()
        );
    }
}
