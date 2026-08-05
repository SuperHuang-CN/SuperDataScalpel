package cn.superhuang.superapigateway.controlplane.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "sag_gateway_instance", schema = "super_api_gateway")
public class GatewayInstanceEntity {

    @Id
    @Column(length = 128)
    private String id;

    @Column(nullable = false, length = 64)
    private String state;

    @Column(name = "loaded_revision", nullable = false)
    private long loadedRevision;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "application_version", nullable = false, length = 64)
    private String applicationVersion;

    protected GatewayInstanceEntity() {
    }

    public GatewayInstanceEntity(String id, Instant startedAt) {
        this.id = id;
        this.startedAt = startedAt;
        this.lastSeenAt = startedAt;
        this.state = "STARTING";
        this.applicationVersion = "unknown";
    }

    public void heartbeat(String state, long loadedRevision, String lastError, String applicationVersion) {
        this.state = state;
        this.loadedRevision = loadedRevision;
        this.lastError = lastError;
        this.applicationVersion = applicationVersion;
        this.lastSeenAt = Instant.now();
    }

    public String getId() { return id; }
    public String getState() { return state; }
    public long getLoadedRevision() { return loadedRevision; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public String getLastError() { return lastError; }
    public String getApplicationVersion() { return applicationVersion; }
}
