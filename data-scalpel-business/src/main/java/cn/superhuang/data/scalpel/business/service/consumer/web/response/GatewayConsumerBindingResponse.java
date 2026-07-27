package cn.superhuang.data.scalpel.business.service.consumer.web.response;

import cn.superhuang.data.scalpel.business.service.consumer.domain.GatewayConsumerBinding;
import cn.superhuang.data.scalpel.business.service.consumer.domain.GatewayConsumerSyncStatus;
import cn.superhuang.data.scalpel.business.service.gateway.GatewayProvider;
import cn.superhuang.data.scalpel.business.service.gateway.reconciliation.GatewayReconciliationReason;
import cn.superhuang.data.scalpel.business.service.gateway.reconciliation.GatewayReconciliationStatus;

import java.time.Instant;
import java.util.UUID;

public record GatewayConsumerBindingResponse(
        UUID id,
        GatewayProvider provider,
        String externalId,
        long syncedRevision,
        GatewayConsumerSyncStatus syncStatus,
        String lastError,
        Instant operationStartedAt,
        Instant lastSyncedAt,
        GatewayReconciliationStatus reconciliationStatus,
        GatewayReconciliationReason reconciliationReason,
        String reconciliationMessage,
        UUID reconciliationOperationId,
        Instant reconciliationStartedAt,
        Instant lastReconciledAt,
        Instant createdAt,
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
