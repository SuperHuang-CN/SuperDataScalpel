package cn.superhuang.data.scalpel.business.service.consumer.domain;

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
import java.util.UUID;

/** Synchronization state for one DataScalpel consumer in one gateway provider. */
@Entity
@Table(name = "ds_gateway_consumer_binding", uniqueConstraints = @UniqueConstraint(
        name = "uk_ds_gateway_consumer_binding", columnNames = {"consumer_id", "provider"}
))
public class GatewayConsumerBinding extends BaseEntity {

    @Column(name = "consumer_id", nullable = false, updatable = false)
    private UUID consumerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 32)
    private GatewayProvider provider;

    @Column(name = "external_id", length = 200)
    private String externalId;

    @Column(name = "synced_revision", nullable = false)
    private long syncedRevision;

    @Enumerated(EnumType.STRING)
    @Column(name = "sync_status", nullable = false, length = 32)
    private GatewayConsumerSyncStatus syncStatus;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "operation_started_at")
    private Instant operationStartedAt;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

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

    protected GatewayConsumerBinding() {
    }

    private GatewayConsumerBinding(UUID consumerId, GatewayProvider provider) {
        this.consumerId = java.util.Objects.requireNonNull(consumerId, "Consumer ID is required");
        if (provider == null || provider == GatewayProvider.NONE) {
            throw new IllegalArgumentException("Gateway provider is required");
        }
        this.provider = provider;
        this.syncStatus = GatewayConsumerSyncStatus.SYNC_PENDING;
    }

    public static GatewayConsumerBinding pending(UUID consumerId, GatewayProvider provider) {
        return new GatewayConsumerBinding(consumerId, provider);
    }

    public void beginSync() {
        resetReconciliation();
        syncStatus = GatewayConsumerSyncStatus.SYNC_PENDING;
        operationStartedAt = Instant.now();
        lastError = null;
    }

    public void synchronizedWith(String externalId, long revision) {
        if (externalId == null || externalId.isBlank()) {
            throw new IllegalArgumentException("Gateway consumer external ID is required");
        }
        this.externalId = externalId.trim();
        this.syncedRevision = revision;
        this.syncStatus = GatewayConsumerSyncStatus.SYNCED;
        this.lastSyncedAt = Instant.now();
        this.operationStartedAt = null;
        this.lastError = null;
    }

    public void syncFailed(String message) {
        syncStatus = GatewayConsumerSyncStatus.SYNC_FAILED;
        operationStartedAt = null;
        lastError = limit(message, "网关消费者同步失败");
    }

    public void beginDelete() {
        resetReconciliation();
        syncStatus = GatewayConsumerSyncStatus.DELETE_PENDING;
        operationStartedAt = Instant.now();
        lastError = null;
    }

    public void deleteFailed(String message) {
        syncStatus = GatewayConsumerSyncStatus.DELETE_FAILED;
        operationStartedAt = null;
        lastError = limit(message, "网关消费者删除失败");
    }

    public boolean hasRecentOperation(Instant now, Duration timeout) {
        if (reconciliationStatus == GatewayReconciliationStatus.CHECKING
                && reconciliationStartedAt != null
                && reconciliationStartedAt.isAfter(now.minus(timeout))) {
            return true;
        }
        if (operationStartedAt == null
                || syncStatus != GatewayConsumerSyncStatus.SYNC_PENDING
                && syncStatus != GatewayConsumerSyncStatus.DELETE_PENDING) {
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

    public UUID getConsumerId() {
        return consumerId;
    }

    public GatewayProvider getProvider() {
        return provider;
    }

    public String getExternalId() {
        return externalId;
    }

    public long getSyncedRevision() {
        return syncedRevision;
    }

    public GatewayConsumerSyncStatus getSyncStatus() {
        return syncStatus;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getOperationStartedAt() {
        return operationStartedAt;
    }

    public Instant getLastSyncedAt() {
        return lastSyncedAt;
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

    private static String limit(String value, String fallback) {
        String normalized = value == null || value.isBlank() ? fallback : value.trim();
        return normalized.substring(0, Math.min(1000, normalized.length()));
    }
}
