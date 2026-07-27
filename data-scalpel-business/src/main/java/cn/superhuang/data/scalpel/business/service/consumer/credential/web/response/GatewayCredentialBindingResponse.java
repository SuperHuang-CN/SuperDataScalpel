package cn.superhuang.data.scalpel.business.service.consumer.credential.web.response;

import cn.superhuang.data.scalpel.business.service.consumer.credential.domain.GatewayCredentialBinding;
import cn.superhuang.data.scalpel.business.service.consumer.credential.domain.GatewayCredentialStatus;
import cn.superhuang.data.scalpel.business.service.gateway.GatewayProvider;
import cn.superhuang.data.scalpel.business.service.gateway.reconciliation.GatewayReconciliationReason;
import cn.superhuang.data.scalpel.business.service.gateway.reconciliation.GatewayReconciliationStatus;

import java.time.Instant;
import java.util.UUID;

public record GatewayCredentialBindingResponse(
        UUID id,
        GatewayProvider provider,
        String externalId,
        long syncedRevision,
        GatewayCredentialStatus status,
        String lastError,
        UUID operationId,
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
