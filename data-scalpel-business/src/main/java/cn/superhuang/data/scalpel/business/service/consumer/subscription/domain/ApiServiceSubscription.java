package cn.superhuang.data.scalpel.business.service.consumer.subscription.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
        name = "ds_api_service_subscription",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ds_api_service_subscription",
                columnNames = {"consumer_id", "data_service_id"}
        ),
        indexes = @Index(
                name = "idx_ds_api_service_subscription_service",
                columnList = "data_service_id"
        )
)
public class ApiServiceSubscription extends BaseEntity {

    @Column(name = "consumer_id", nullable = false, updatable = false)
    private UUID consumerId;

    @Column(name = "data_service_id", nullable = false, updatable = false)
    private UUID dataServiceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "desired_state", nullable = false, length = 16)
    private ApiServiceSubscriptionDesiredState desiredState;

    protected ApiServiceSubscription() {
    }

    private ApiServiceSubscription(UUID consumerId, UUID dataServiceId) {
        this.consumerId = Objects.requireNonNull(consumerId, "Consumer ID is required");
        this.dataServiceId = Objects.requireNonNull(dataServiceId, "Data service ID is required");
        this.desiredState = ApiServiceSubscriptionDesiredState.GRANTED;
    }

    public static ApiServiceSubscription create(UUID consumerId, UUID dataServiceId) {
        return new ApiServiceSubscription(consumerId, dataServiceId);
    }

    public UUID getConsumerId() {
        return consumerId;
    }

    public UUID getDataServiceId() {
        return dataServiceId;
    }

    public ApiServiceSubscriptionDesiredState getDesiredState() {
        return desiredState;
    }

    public void requestGrant() {
        desiredState = ApiServiceSubscriptionDesiredState.GRANTED;
    }

    public void requestRevoke() {
        desiredState = ApiServiceSubscriptionDesiredState.REVOKED;
    }
}
