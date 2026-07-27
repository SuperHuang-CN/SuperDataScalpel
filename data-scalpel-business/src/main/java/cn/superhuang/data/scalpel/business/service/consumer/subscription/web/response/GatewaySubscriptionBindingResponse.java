package cn.superhuang.data.scalpel.business.service.consumer.subscription.web.response;

import cn.superhuang.data.scalpel.business.service.consumer.subscription.domain.GatewaySubscriptionBinding;
import cn.superhuang.data.scalpel.business.service.consumer.subscription.domain.GatewaySubscriptionStatus;
import cn.superhuang.data.scalpel.business.service.gateway.GatewayProvider;
import cn.superhuang.data.scalpel.business.service.gateway.reconciliation.GatewayReconciliationReason;
import cn.superhuang.data.scalpel.business.service.gateway.reconciliation.GatewayReconciliationStatus;

import java.time.Instant;
import java.util.UUID;

public record GatewaySubscriptionBindingResponse(
        UUID id,
        GatewayProvider provider,
        String externalMembershipId,
        GatewaySubscriptionStatus status,
        String lastError,
        UUID operationId,
        Instant operationStartedAt,
        Instant grantedAt,
        GatewayReconciliationStatus reconciliationStatus,
        GatewayReconciliationReason reconciliationReason,
        String reconciliationMessage,
        UUID reconciliationOperationId,
        Instant reconciliationStartedAt,
        Instant lastReconciledAt,
        Instant createdAt,
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
