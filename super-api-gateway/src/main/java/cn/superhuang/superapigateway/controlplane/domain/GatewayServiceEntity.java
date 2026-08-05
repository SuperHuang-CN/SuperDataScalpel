package cn.superhuang.superapigateway.controlplane.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "sag_service",
        schema = "super_api_gateway",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_sag_service_code", columnNames = "code"),
                @UniqueConstraint(name = "uk_sag_service_external", columnNames = {"source", "external_id"})
        }
)
public class GatewayServiceEntity extends BaseEntity {

    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "upstream_uri", nullable = false, length = 1000)
    private String upstreamUri;

    @Enumerated(EnumType.STRING)
    @Column(name = "access_mode", nullable = false, length = 32)
    private AccessMode accessMode;

    @Column(name = "connect_timeout_ms", nullable = false)
    private int connectTimeoutMs;

    @Column(name = "response_timeout_ms", nullable = false)
    private int responseTimeoutMs;

    @Column(nullable = false)
    private boolean enabled;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false, length = 64)
    private String source;

    @Column(name = "external_id", length = 128)
    private String externalId;

    @Column(nullable = false)
    private long revision;

    protected GatewayServiceEntity() {
    }

    public GatewayServiceEntity(
            String code,
            String name,
            String upstreamUri,
            AccessMode accessMode,
            int connectTimeoutMs,
            int responseTimeoutMs,
            boolean enabled,
            String description,
            String source,
            String externalId
    ) {
        this.code = code;
        update(name, upstreamUri, accessMode, connectTimeoutMs, responseTimeoutMs, enabled, description);
        this.revision = 1;
        this.source = source;
        this.externalId = externalId;
    }

    public void update(
            String name,
            String upstreamUri,
            AccessMode accessMode,
            int connectTimeoutMs,
            int responseTimeoutMs,
            boolean enabled,
            String description
    ) {
        this.name = name;
        this.upstreamUri = upstreamUri;
        this.accessMode = accessMode;
        this.connectTimeoutMs = connectTimeoutMs;
        this.responseTimeoutMs = responseTimeoutMs;
        this.enabled = enabled;
        this.description = description;
        if (revision > 0) revision++;
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled != enabled) {
            this.enabled = enabled;
            revision++;
        }
    }

    public String getCode() { return code; }
    public String getName() { return name; }
    public String getUpstreamUri() { return upstreamUri; }
    public AccessMode getAccessMode() { return accessMode; }
    public int getConnectTimeoutMs() { return connectTimeoutMs; }
    public int getResponseTimeoutMs() { return responseTimeoutMs; }
    public boolean isEnabled() { return enabled; }
    public String getDescription() { return description; }
    public String getSource() { return source; }
    public String getExternalId() { return externalId; }
    public long getRevision() { return revision; }
}
