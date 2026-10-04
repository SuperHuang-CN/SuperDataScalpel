package cn.superhuang.superapigateway.controlplane.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.UUID;

@Entity
@Table(
        name = "sag_subscription",
        schema = "super_api_gateway",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_sag_subscription_pair",
                        columnNames = {"consumer_id", "service_id"}
                ),
                @UniqueConstraint(
                        name = "uk_sag_subscription_external",
                        columnNames = {"source", "external_id"}
                )
        }
)
public class GatewaySubscriptionEntity extends BaseEntity {

    @Column(name = "consumer_id", nullable = false)
    private UUID consumerId;

    @Column(name = "service_id", nullable = false)
    private UUID serviceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SubscriptionStatus status;
    private java.time.Instant validFrom;
    private java.time.Instant expiresAt;
    private Integer requestsPerSecond;
    public java.time.Instant getValidFrom() { return validFrom; }
    public java.time.Instant getExpiresAt() { return expiresAt; }
    public int getRequestsPerSecond() { return requestsPerSecond == null ? 0 : requestsPerSecond; }
    public void setValidity(java.time.Instant from, java.time.Instant to, int rate) {
        validFrom = from; expiresAt = to; requestsPerSecond = rate;
    }

    @Column(nullable = false, length = 64)
    private String source;

    @Column(name = "external_id", length = 128)
    private String externalId;

    protected GatewaySubscriptionEntity() {
    }

    public GatewaySubscriptionEntity(UUID consumerId, UUID serviceId, String source, String externalId) {
        this.consumerId = consumerId;
        this.serviceId = serviceId;
        this.source = source;
        this.externalId = externalId;
        this.status = SubscriptionStatus.ACTIVE;
    }

    public void grant() {
        status = SubscriptionStatus.ACTIVE;
    }

    public void revoke() {
        status = SubscriptionStatus.REVOKED;
    }

    public UUID getConsumerId() { return consumerId; }
    public UUID getServiceId() { return serviceId; }
    public SubscriptionStatus getStatus() { return status; }
    public String getSource() { return source; }
    public String getExternalId() { return externalId; }
}
