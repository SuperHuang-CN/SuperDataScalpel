package cn.superhuang.data.scalpel.business.service.accesslog.domain;

import cn.superhuang.data.scalpel.business.service.gateway.GatewayProvider;
import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.MappedSuperclass;

import java.time.Instant;

@MappedSuperclass
public abstract class GatewayAccessHourlyStat extends BaseEntity {

    @Column(name = "hour_start", nullable = false, updatable = false)
    private Instant hourStart;

    @Enumerated(EnumType.STRING)
    @Column(name = "gateway_provider", nullable = false, updatable = false, length = 32)
    private GatewayProvider gatewayProvider;

    @Column(name = "request_count", nullable = false)
    private long requestCount;

    @Column(name = "status_2xx_count", nullable = false)
    private long status2xxCount;

    @Column(name = "status_3xx_count", nullable = false)
    private long status3xxCount;

    @Column(name = "status_4xx_count", nullable = false)
    private long status4xxCount;

    @Column(name = "status_5xx_count", nullable = false)
    private long status5xxCount;

    @Column(name = "status_401_count", nullable = false)
    private long status401Count;

    @Column(name = "status_403_count", nullable = false)
    private long status403Count;

    @Column(name = "status_429_count", nullable = false)
    private long status429Count;

    @Column(name = "gateway_rejected_count", nullable = false)
    private long gatewayRejectedCount;

    @Column(name = "gateway_error_count", nullable = false)
    private long gatewayErrorCount;

    @Column(name = "upstream_error_count", nullable = false)
    private long upstreamErrorCount;

    @Column(name = "request_bytes_sum", nullable = false)
    private long requestBytesSum;

    @Column(name = "response_bytes_sum", nullable = false)
    private long responseBytesSum;

    @Column(name = "request_latency_sample_count", nullable = false)
    private long requestLatencySampleCount;

    @Column(name = "request_latency_sum_ms", nullable = false)
    private long requestLatencySumMs;

    @Column(name = "request_latency_max_ms")
    private Long requestLatencyMaxMs;

    @Column(name = "request_latency_p95_ms")
    private Double requestLatencyP95Ms;

    @Column(name = "request_latency_p99_ms")
    private Double requestLatencyP99Ms;

    @Column(name = "proxy_latency_sample_count", nullable = false)
    private long proxyLatencySampleCount;

    @Column(name = "proxy_latency_sum_ms", nullable = false)
    private long proxyLatencySumMs;

    @Column(name = "proxy_latency_p95_ms")
    private Double proxyLatencyP95Ms;

    @Column(name = "proxy_latency_p99_ms")
    private Double proxyLatencyP99Ms;

    protected GatewayAccessHourlyStat() {
    }
}
