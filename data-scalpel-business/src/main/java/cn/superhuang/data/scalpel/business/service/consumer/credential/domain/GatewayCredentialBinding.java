package cn.superhuang.data.scalpel.business.service.consumer.credential.domain;

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

@Entity
@Table(name = "ds_gateway_credential_binding", uniqueConstraints = @UniqueConstraint(
        name = "uk_ds_gateway_credential_binding", columnNames = {"credential_id", "provider"}
))
public class GatewayCredentialBinding extends BaseEntity {

    @Column(name = "credential_id", nullable = false, updatable = false)
    private UUID credentialId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 32)
    private GatewayProvider provider;

    @Column(name = "external_id", length = 200)
    private String externalId;

    @Column(name = "synced_revision", nullable = false)
    private long syncedRevision;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private GatewayCredentialStatus status;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "operation_started_at")
    private Instant operationStartedAt;

    @Column(name = "operation_id")
    private UUID operationId;

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

    protected GatewayCredentialBinding() {
    }

    private GatewayCredentialBinding(UUID credentialId, GatewayProvider provider) {
        this.credentialId = Objects.requireNonNull(credentialId, "Credential ID is required");
        if (provider == null || provider == GatewayProvider.NONE) {
            throw new IllegalArgumentException("Gateway provider is required");
        }
        this.provider = provider;
        this.status = GatewayCredentialStatus.SYNC_PENDING;
    }

    public static GatewayCredentialBinding pending(UUID credentialId, GatewayProvider provider) {
        return new GatewayCredentialBinding(credentialId, provider);
    }

    public void beginSync() {
        resetReconciliation();
        status = GatewayCredentialStatus.SYNC_PENDING;
        operationId = UUID.randomUUID();
        operationStartedAt = Instant.now();
        lastError = null;
    }

    public void activated(String externalId, long revision) {
        this.externalId = required(externalId, "Gateway credential external ID");
        this.syncedRevision = revision;
        this.status = GatewayCredentialStatus.ACTIVE;
        this.operationId = null;
        this.operationStartedAt = null;
        this.lastSyncedAt = Instant.now();
        this.lastError = null;
    }

    public void syncFailed(String message) {
        status = GatewayCredentialStatus.SYNC_FAILED;
        operationId = null;
        operationStartedAt = null;
        lastError = limit(message, "网关凭证同步失败");
    }

    public void beginDelete() {
        resetReconciliation();
        status = GatewayCredentialStatus.DELETE_PENDING;
        operationId = UUID.randomUUID();
        operationStartedAt = Instant.now();
        lastError = null;
    }

    public void deleteFailed(String message) {
        status = GatewayCredentialStatus.DELETE_FAILED;
        operationId = null;
        operationStartedAt = null;
        lastError = limit(message, "网关凭证删除失败");
    }

    public boolean hasRecentOperation(Instant now, Duration timeout) {
        if (reconciliationStatus == GatewayReconciliationStatus.CHECKING
                && reconciliationStartedAt != null
                && reconciliationStartedAt.isAfter(now.minus(timeout))) {
            return true;
        }
        if (operationStartedAt == null
                || status != GatewayCredentialStatus.SYNC_PENDING
                && status != GatewayCredentialStatus.DELETE_PENDING) {
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

    public UUID getCredentialId() {
        return credentialId;
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

    public GatewayCredentialStatus getStatus() {
        return status;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getOperationStartedAt() {
        return operationStartedAt;
    }

    public UUID getOperationId() {
        return operationId;
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

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required");
        return value.trim();
    }

    private static String limit(String value, String fallback) {
        String normalized = value == null || value.isBlank() ? fallback : value.trim();
        return normalized.substring(0, Math.min(1000, normalized.length()));
    }
}
