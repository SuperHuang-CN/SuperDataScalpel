package cn.superhuang.data.scalpel.business.service.accesslog.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(
        name = "ds_gateway_access_hour_state",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ds_gateway_access_hour_state",
                columnNames = "hour_start"
        )
)
public class GatewayAccessHourState extends BaseEntity {

    @Column(name = "hour_start", nullable = false, updatable = false)
    private Instant hourStart;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private GatewayAccessHourStatus status;

    @Column(name = "last_event_received_at", nullable = false)
    private Instant lastEventReceivedAt;

    @Column(name = "last_aggregated_at")
    private Instant lastAggregatedAt;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    protected GatewayAccessHourState() {
    }
}
