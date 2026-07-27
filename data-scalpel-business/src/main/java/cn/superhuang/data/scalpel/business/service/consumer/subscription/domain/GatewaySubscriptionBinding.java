package cn.superhuang.data.scalpel.business.service.consumer.subscription.domain;

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
@Table(name = "ds_gateway_subscription_binding", uniqueConstraints = @UniqueConstraint(
        name = "uk_ds_gateway_subscription_binding", columnNames = {"subscription_id", "provider"}
))
public class GatewaySubscriptionBinding extends BaseEntity {

    @Column(name = "subscription_id", nullable = false, updatable = false)
    private UUID subscriptionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 32)
    private GatewayProvider provider;

    @Column(name = "external_membership_id", length = 200)
    private String externalMembershipId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private GatewaySubscriptionStatus status;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "operation_started_at")
    private Instant operationStartedAt;

    @Column(name = "operation_id")
    private UUID operationId;

    @Column(name = "granted_at")
    private Instant grantedAt;

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

    protected GatewaySubscriptionBinding() {
    }

    private GatewaySubscriptionBinding(UUID subscriptionId, GatewayProvider provider) {
        this.subscriptionId = Objects.requireNonNull(subscriptionId, "Subscription ID is required");
        if (provider == null || provider == GatewayProvider.NONE) {
            throw new IllegalArgumentException("Gateway provider is required");
        }
        this.provider = provider;
        this.status = GatewaySubscriptionStatus.GRANT_PENDING;
    }

    public static GatewaySubscriptionBinding pending(UUID subscriptionId, GatewayProvider provider) {
        return new GatewaySubscriptionBinding(subscriptionId, provider);
    }

    public void beginGrant() {
        resetReconciliation();
        status = GatewaySubscriptionStatus.GRANT_PENDING;
        operationId = UUID.randomUUID();
        operationStartedAt = Instant.now();
        lastError = null;
    }

    public void granted(String externalMembershipId) {
        this.externalMembershipId = required(externalMembershipId, "Gateway membership external ID");
        this.status = GatewaySubscriptionStatus.GRANTED;
        this.operationId = null;
        this.operationStartedAt = null;
        this.grantedAt = Instant.now();
        this.lastError = null;
    }

    public void grantFailed(String message) {
        status = GatewaySubscriptionStatus.GRANT_FAILED;
        operationId = null;
        operationStartedAt = null;
        lastError = limit(message, "网关订阅授权失败");
    }

    public void beginRevoke() {
        resetReconciliation();
        status = GatewaySubscriptionStatus.REVOKE_PENDING;
        operationId = UUID.randomUUID();
        operationStartedAt = Instant.now();
        lastError = null;
    }

    public void revokeFailed(String message) {
        status = GatewaySubscriptionStatus.REVOKE_FAILED;
        operationId = null;
        operationStartedAt = null;
        lastError = limit(message, "网关订阅撤回失败");
    }

    public boolean hasRecentOperation(Instant now, Duration timeout) {
        if (reconciliationStatus == GatewayReconciliationStatus.CHECKING
                && reconciliationStartedAt != null
                && reconciliationStartedAt.isAfter(now.minus(timeout))) {
            return true;
        }
        if (operationStartedAt == null
                || status != GatewaySubscriptionStatus.GRANT_PENDING
                && status != GatewaySubscriptionStatus.REVOKE_PENDING) {
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

    public UUID getSubscriptionId() {
        return subscriptionId;
    }

    public GatewayProvider getProvider() {
        return provider;
    }

    public String getExternalMembershipId() {
        return externalMembershipId;
    }

    public GatewaySubscriptionStatus getStatus() {
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

    public Instant getGrantedAt() {
        return grantedAt;
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
