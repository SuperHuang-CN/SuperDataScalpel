package cn.superhuang.data.scalpel.business.service.web.response;

import cn.superhuang.data.scalpel.business.service.gateway.GatewayProvider;
import cn.superhuang.data.scalpel.business.service.domain.DataServiceAccessMode;
import cn.superhuang.data.scalpel.business.service.gateway.domain.GatewayServiceBinding;
import cn.superhuang.data.scalpel.business.service.gateway.domain.GatewayServicePublicationStatus;
import cn.superhuang.data.scalpel.business.service.gateway.reconciliation.GatewayReconciliationReason;
import cn.superhuang.data.scalpel.business.service.gateway.reconciliation.GatewayReconciliationStatus;

import java.time.Instant;
import java.util.UUID;

public record GatewayServiceBindingResponse(
        UUID id,
        GatewayProvider provider,
        String externalServiceId,
        String externalRouteId,
        String gatewayRoutePath,
        DataServiceAccessMode accessMode,
        long publishedRevision,
        GatewayServicePublicationStatus publicationStatus,
        String gatewayUrl,
        String lastError,
        Instant operationStartedAt,
        Instant publishedAt,
        GatewayReconciliationStatus reconciliationStatus,
        GatewayReconciliationReason reconciliationReason,
        String reconciliationMessage,
        UUID reconciliationOperationId,
        Instant reconciliationStartedAt,
        Instant lastReconciledAt,
        Instant createdAt,
        Instant updatedAt
) {

    public static GatewayServiceBindingResponse from(GatewayServiceBinding binding) {
        return new GatewayServiceBindingResponse(
                binding.getId(),
                binding.getProvider(),
                binding.getExternalServiceId(),
                binding.getExternalRouteId(),
                binding.getGatewayRoutePath(),
                binding.getAccessMode(),
                binding.getPublishedRevision(),
                binding.getPublicationStatus(),
                binding.getGatewayUrl(),
                binding.getLastError(),
                binding.getOperationStartedAt(),
                binding.getPublishedAt(),
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
