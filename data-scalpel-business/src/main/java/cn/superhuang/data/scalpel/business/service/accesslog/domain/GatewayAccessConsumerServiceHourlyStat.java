package cn.superhuang.data.scalpel.business.service.accesslog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.UUID;

@Entity
@Table(
        name = "ds_gateway_access_consumer_service_hourly",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ds_gateway_access_consumer_service_hourly",
                columnNames = {"hour_start", "gateway_provider", "data_service_id", "consumer_id"}
        ),
        indexes = {
                @Index(
                        name = "idx_ds_gateway_access_consumer_hourly_time",
                        columnList = "hour_start"
                ),
                @Index(
                        name = "idx_ds_gateway_access_consumer_hourly_consumer_time",
                        columnList = "consumer_id,hour_start"
                ),
                @Index(
                        name = "idx_ds_gateway_access_consumer_hourly_service_time",
                        columnList = "data_service_id,hour_start"
                )
        }
)
public class GatewayAccessConsumerServiceHourlyStat extends GatewayAccessHourlyStat {

    @Column(name = "data_service_id", nullable = false, updatable = false)
    private UUID dataServiceId;

    @Column(name = "consumer_id", nullable = false, updatable = false)
    private UUID consumerId;

    protected GatewayAccessConsumerServiceHourlyStat() {
    }
}
