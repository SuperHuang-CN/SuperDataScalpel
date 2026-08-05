package cn.superhuang.data.scalpel.business.service.accesslog.domain;

import cn.superhuang.data.scalpel.business.service.gateway.GatewayProvider;
import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "ds_gateway_access_log",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_ds_gateway_access_log_event",
                        columnNames = "event_id"
                ),
                @UniqueConstraint(
                        name = "uk_ds_gateway_access_log_request",
                        columnNames = {"gateway_provider", "gateway_request_id"}
                )
        },
        indexes = {
                @Index(
                        name = "idx_ds_gateway_access_log_occurred",
                        columnList = "occurred_at"
                ),
                @Index(
                        name = "idx_ds_gateway_access_log_service_time",
                        columnList = "data_service_id,occurred_at"
                ),
                @Index(
                        name = "idx_ds_gateway_access_log_consumer_time",
                        columnList = "consumer_id,occurred_at"
                )
        }
)
public class GatewayAccessLog extends BaseEntity {

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "schema_version", nullable = false, updatable = false, length = 16)
    private String schemaVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "gateway_provider", nullable = false, updatable = false, length = 32)
    private GatewayProvider gatewayProvider;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "observed_at", updatable = false)
    private Instant observedAt;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @Column(name = "data_service_id", updatable = false)
    private UUID dataServiceId;

    @Column(name = "gateway_service_id", nullable = false, updatable = false, length = 200)
    private String gatewayServiceId;

    @Column(name = "gateway_service_name", nullable = false, updatable = false, length = 200)
    private String gatewayServiceName;

    @Column(name = "gateway_route_id", updatable = false, length = 200)
    private String gatewayRouteId;

    @Column(name = "gateway_route_name", updatable = false, length = 200)
    private String gatewayRouteName;

    @Column(name = "consumer_id", updatable = false)
    private UUID consumerId;

    @Column(name = "gateway_consumer_id", updatable = false, length = 200)
    private String gatewayConsumerId;

    @Column(name = "consumer_code", updatable = false, length = 64)
    private String consumerCode;

    @Column(name = "gateway_credential_external_id", updatable = false, length = 200)
    private String gatewayCredentialExternalId;

    @Column(name = "gateway_request_id", nullable = false, updatable = false, length = 200)
    private String gatewayRequestId;

    @Column(name = "request_method", nullable = false, updatable = false, length = 16)
    private String requestMethod;

    @Column(name = "request_path", nullable = false, updatable = false, length = 2048)
    private String requestPath;

    @Column(name = "response_status", nullable = false, updatable = false)
    private int responseStatus;

    @Column(name = "upstream_status", updatable = false, length = 64)
    private String upstreamStatus;

    @Column(name = "request_size_bytes", updatable = false)
    private Long requestSizeBytes;

    @Column(name = "response_size_bytes", updatable = false)
    private Long responseSizeBytes;

    @Column(name = "request_latency_ms", updatable = false)
    private Long requestLatencyMs;

    @Column(name = "kong_latency_ms", updatable = false)
    private Long kongLatencyMs;

    @Column(name = "proxy_latency_ms", updatable = false)
    private Long proxyLatencyMs;

    @Column(name = "receive_latency_ms", updatable = false)
    private Long receiveLatencyMs;

    @Column(name = "client_ip", updatable = false, length = 64)
    private String clientIp;

    @Enumerated(EnumType.STRING)
    @Column(name = "identity_resolution_status", nullable = false, updatable = false, length = 32)
    private GatewayAccessIdentityResolutionStatus identityResolutionStatus;

    @Column(name = "gateway_rejected", nullable = false, updatable = false)
    private boolean gatewayRejected;

    @Column(name = "gateway_error", nullable = false, updatable = false)
    private boolean gatewayError;

    @Column(name = "upstream_error", nullable = false, updatable = false)
    private boolean upstreamError;

    @Column(name = "kafka_topic", nullable = false, updatable = false, length = 200)
    private String kafkaTopic;

    @Column(name = "kafka_partition", nullable = false, updatable = false)
    private int kafkaPartition;

    @Column(name = "kafka_offset", nullable = false, updatable = false)
    private long kafkaOffset;

    protected GatewayAccessLog() {
    }
}
