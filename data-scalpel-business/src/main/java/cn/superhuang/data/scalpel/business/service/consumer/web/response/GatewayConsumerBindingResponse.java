package cn.superhuang.data.scalpel.business.service.consumer.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.service.consumer.domain.GatewayConsumerBinding;
import cn.superhuang.data.scalpel.business.service.consumer.domain.GatewayConsumerSyncStatus;
import cn.superhuang.data.scalpel.business.service.gateway.GatewayProvider;
import cn.superhuang.data.scalpel.business.service.gateway.reconciliation.GatewayReconciliationReason;
import cn.superhuang.data.scalpel.business.service.gateway.reconciliation.GatewayReconciliationStatus;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "本地 API 调用方在一个网关中的远端同步和对账状态。")

public record GatewayConsumerBindingResponse(
        @Schema(description = "网关消费者绑定 UUID。")
        UUID id,
        @Schema(description = "保存该消费者投影的网关提供方。")
        GatewayProvider provider,
        @Schema(description = "网关侧消费者对象标识；尚未同步成功或远端缺失时为空。")
        String externalId,
        @Schema(description = "最近一次成功同步时网关确认的消费者修订号；初始为 0。仅 syncStatus=SYNCED 且等于消费者 revision 时表示当前内容已同步。")
        long syncedRevision,
        @Schema(description = "消费者同步状态：SYNC_PENDING、SYNCED、SYNC_FAILED、DELETE_PENDING 或 DELETE_FAILED。")
        GatewayConsumerSyncStatus syncStatus,
        @Schema(description = "最近一次安全错误摘要；没有错误时为空。")
        String lastError,
        @Schema(description = "当前同步或删除操作开始时间，ISO-8601 UTC 时间戳；仅 SYNC_PENDING 或 DELETE_PENDING 时有值，进入终态后为空。")
        Instant operationStartedAt,
        @Schema(description = "最近一次成功同步消费者到网关的时间，ISO-8601 UTC 时间戳；从未成功时为空。后续失败不会清除此历史时间。")
        Instant lastSyncedAt,
        @Schema(description = "最近一次显式对账状态：NOT_CHECKED、CHECKING、IN_SYNC、DRIFTED 或 CHECK_FAILED。")
        GatewayReconciliationStatus reconciliationStatus,
        @Schema(description = "发现漂移时的稳定原因，例如远端缺失、配置不一致或归属不一致。")
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

    public static GatewayConsumerBindingResponse from(GatewayConsumerBinding binding) {
        return new GatewayConsumerBindingResponse(
                binding.getId(),
                binding.getProvider(),
                binding.getExternalId(),
                binding.getSyncedRevision(),
                binding.getSyncStatus(),
                binding.getLastError(),
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
