package cn.superhuang.data.scalpel.business.service.gateway.domain;

import cn.superhuang.data.scalpel.business.service.gateway.GatewayProvider;
import cn.superhuang.data.scalpel.business.service.gateway.reconciliation.GatewayInspectionResult;
import cn.superhuang.data.scalpel.business.service.gateway.reconciliation.GatewayReconciliationReason;
import cn.superhuang.data.scalpel.business.service.gateway.reconciliation.GatewayReconciliationStatus;
import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.ColumnDefault;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Publication state for one DataScalpel service in one gateway provider. */
@Entity
@Table(name = "ds_gateway_service_binding", uniqueConstraints = @UniqueConstraint(
        name = "uk_ds_gateway_service_binding", columnNames = {"data_service_id", "provider"}
))
public class GatewayServiceBinding extends BaseEntity {

    @Column(name = "data_service_id", nullable = false, updatable = false)
    private UUID dataServiceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 32)
    private GatewayProvider provider;

    @Column(name = "external_service_id", length = 200)
    private String externalServiceId;

    @Column(name = "external_route_id", length = 200)
    private String externalRouteId;

    @Column(name = "published_revision", nullable = false)
    private long publishedRevision;

    @Enumerated(EnumType.STRING)
    @Column(name = "publication_status", nullable = false, length = 32)
    private GatewayServicePublicationStatus publicationStatus;

    @Column(name = "gateway_url", length = 1000)
    private String gatewayUrl;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "operation_started_at")
    private Instant operationStartedAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "reconciliation_status", nullable = false, length = 32)
    @ColumnDefault("'NOT_CHECKED'")
    private GatewayReconciliationStatus reconciliationStatus = GatewayReconciliationStatus.NOT_CHECKED;

    @Enumerated(EnumType.STRING)
    @Column(name = "reconciliation_reason", length = 64)
    private GatewayReconciliationReason reconciliationReason;

    @Column(name = "reconciliation_message", length = 1000)
    private String reconciliationMessage;

    @Column(name = "reconciliation_operation_id")
    private UUID reconciliationOperationId;

    @Column(name = "reconciliation_started_at")
    private Instant reconciliationStartedAt;

    @Column(name = "last_reconciled_at")
    private Instant lastReconciledAt;

    protected GatewayServiceBinding() {
    }

    private GatewayServiceBinding(UUID dataServiceId, GatewayProvider provider) {
        this.dataServiceId = Objects.requireNonNull(dataServiceId, "Data service ID is required");
        if (provider == null || provider == GatewayProvider.NONE) {
            throw new IllegalArgumentException("Gateway provider is required");
        }
        this.provider = provider;
        this.publicationStatus = GatewayServicePublicationStatus.PUBLISHING;
    }

    public static GatewayServiceBinding publishing(UUID dataServiceId, GatewayProvider provider) {
        return new GatewayServiceBinding(dataServiceId, provider);
    }

    public void beginPublish() {
        resetReconciliation();
        publicationStatus = GatewayServicePublicationStatus.PUBLISHING;
        operationStartedAt = Instant.now();
        lastError = null;
    }

    public void publishedWith(
            String externalServiceId,
            String externalRouteId,
            String gatewayUrl,
            long revision
    ) {
        this.externalServiceId = required(externalServiceId, "Gateway service external ID");
        this.externalRouteId = required(externalRouteId, "Gateway route external ID");
        this.gatewayUrl = required(gatewayUrl, "Gateway URL");
        this.publishedRevision = revision;
        this.publicationStatus = GatewayServicePublicationStatus.PUBLISHED;
        this.publishedAt = Instant.now();
        this.operationStartedAt = null;
        this.lastError = null;
    }

    public void publishFailed(String message) {
        publicationStatus = GatewayServicePublicationStatus.PUBLISH_FAILED;
        operationStartedAt = null;
        lastError = limit(message, "网关服务发布失败");
    }

    public void beginRemoval() {
        resetReconciliation();
        publicationStatus = GatewayServicePublicationStatus.REMOVING;
        operationStartedAt = Instant.now();
        lastError = null;
    }

    public void removalFailed(String message) {
        publicationStatus = GatewayServicePublicationStatus.REMOVE_FAILED;
        operationStartedAt = null;
        lastError = limit(message, "网关服务撤回失败");
    }

    public boolean hasRecentOperation(Instant now, Duration timeout) {
        if (reconciliationStatus == GatewayReconciliationStatus.CHECKING
                && reconciliationStartedAt != null
                && reconciliationStartedAt.isAfter(now.minus(timeout))) {
            return true;
        }
        if (operationStartedAt == null
                || publicationStatus != GatewayServicePublicationStatus.PUBLISHING
                && publicationStatus != GatewayServicePublicationStatus.REMOVING) {
            return false;
        }
        return operationStartedAt.isAfter(now.minus(timeout));
    }

    public UUID beginReconciliation() {
        reconciliationStatus = GatewayReconciliationStatus.CHECKING;
        reconciliationReason = null;
        reconciliationMessage = null;
        reconciliationOperationId = UUID.randomUUID();
        reconciliationStartedAt = Instant.now();
        return reconciliationOperationId;
    }

    public void reconciled(UUID operationId, GatewayInspectionResult result) {
        if (!isCurrentReconciliation(operationId)) return;
        reconciliationStatus = result.status();
        reconciliationReason = result.reason();
        reconciliationMessage = result.message();
        reconciliationOperationId = null;
        reconciliationStartedAt = null;
        lastReconciledAt = Instant.now();
    }

    public void reconciliationFailed(UUID operationId, String message) {
        if (!isCurrentReconciliation(operationId)) return;
        reconciliationStatus = GatewayReconciliationStatus.CHECK_FAILED;
        reconciliationReason = null;
        reconciliationMessage = limit(message, "网关状态检查失败");
        reconciliationOperationId = null;
        reconciliationStartedAt = null;
        lastReconciledAt = Instant.now();
    }

    public UUID getDataServiceId() {
        return dataServiceId;
    }

    public GatewayProvider getProvider() {
        return provider;
    }

    public String getExternalServiceId() {
        return externalServiceId;
    }

    public String getExternalRouteId() {
        return externalRouteId;
    }

    public long getPublishedRevision() {
        return publishedRevision;
    }

    public GatewayServicePublicationStatus getPublicationStatus() {
        return publicationStatus;
    }

    public String getGatewayUrl() {
        return gatewayUrl;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getOperationStartedAt() {
        return operationStartedAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public GatewayReconciliationStatus getReconciliationStatus() {
        return reconciliationStatus;
    }

    public GatewayReconciliationReason getReconciliationReason() {
        return reconciliationReason;
    }

    public String getReconciliationMessage() {
        return reconciliationMessage;
    }

    public UUID getReconciliationOperationId() {
        return reconciliationOperationId;
    }

    public Instant getReconciliationStartedAt() {
        return reconciliationStartedAt;
    }

    public Instant getLastReconciledAt() {
        return lastReconciledAt;
    }

    private boolean isCurrentReconciliation(UUID operationId) {
        return reconciliationStatus == GatewayReconciliationStatus.CHECKING
                && operationId != null
                && operationId.equals(reconciliationOperationId);
    }

    private void resetReconciliation() {
        reconciliationStatus = GatewayReconciliationStatus.NOT_CHECKED;
        reconciliationReason = null;
        reconciliationMessage = null;
        reconciliationOperationId = null;
        reconciliationStartedAt = null;
        lastReconciledAt = null;
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
        return value.trim();
    }

    private static String limit(String value, String fallback) {
        String normalized = value == null || value.isBlank() ? fallback : value.trim();
        return normalized.substring(0, Math.min(1000, normalized.length()));
    }
}
