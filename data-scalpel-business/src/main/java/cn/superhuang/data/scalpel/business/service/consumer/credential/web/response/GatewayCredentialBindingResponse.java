package cn.superhuang.data.scalpel.business.service.consumer.credential.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.service.consumer.credential.domain.GatewayCredentialBinding;
import cn.superhuang.data.scalpel.business.service.consumer.credential.domain.GatewayCredentialStatus;
import cn.superhuang.data.scalpel.business.service.gateway.GatewayProvider;
import cn.superhuang.data.scalpel.business.service.gateway.reconciliation.GatewayReconciliationReason;
import cn.superhuang.data.scalpel.business.service.gateway.reconciliation.GatewayReconciliationStatus;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "本地 API Key 凭证在一个网关中的远端同步和对账状态；不包含密钥明文或摘要。")

public record GatewayCredentialBindingResponse(
        @Schema(description = "网关凭证绑定 UUID。")
        UUID id,
        @Schema(description = "保存该凭证投影的网关提供方。")
        GatewayProvider provider,
        @Schema(description = "网关侧凭证对象标识；尚未同步成功或远端缺失时为空。")
        String externalId,
        @Schema(description = "最近一次成功同步时网关确认的密钥修订号；初始为 0。仅 status=ACTIVE 且等于凭证 revision 时表示当前密钥已生效。")
        long syncedRevision,
        @Schema(description = "凭证同步状态：SYNC_PENDING 待同步，ACTIVE 网关已生效，SYNC_FAILED 同步失败，DELETE_PENDING 待删除，DELETE_FAILED 删除失败。")
        GatewayCredentialStatus status,
        @Schema(description = "最近一次安全错误摘要；没有错误时为空。")
        String lastError,
        @Schema(description = "当前异步同步或删除操作 UUID；没有在途操作时为空。")
        UUID operationId,
        @Schema(description = "当前同步或删除操作开始时间，ISO-8601 UTC 时间戳；仅 SYNC_PENDING 或 DELETE_PENDING 时有值，进入终态后为空。")
        Instant operationStartedAt,
        @Schema(description = "最近一次成功把该密钥修订同步到网关的时间，ISO-8601 UTC 时间戳；从未成功时为空。后续失败不会清除此历史时间。")
        Instant lastSyncedAt,
        @Schema(description = "最近一次显式对账状态：NOT_CHECKED、CHECKING、IN_SYNC、DRIFTED 或 CHECK_FAILED。")
        GatewayReconciliationStatus reconciliationStatus,
        @Schema(description = "发现漂移时的稳定原因，包括远端缺失、配置或秘密摘要不一致等。")
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

    public static GatewayCredentialBindingResponse from(GatewayCredentialBinding binding) {
        return new GatewayCredentialBindingResponse(
                binding.getId(),
                binding.getProvider(),
                binding.getExternalId(),
                binding.getSyncedRevision(),
                binding.getStatus(),
                binding.getLastError(),
                binding.getOperationId(),
                binding.getOperationStartedAt(),
                binding.getLastSyncedAt(),
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
