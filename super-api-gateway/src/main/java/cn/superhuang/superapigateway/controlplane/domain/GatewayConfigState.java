package cn.superhuang.superapigateway.controlplane.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "sag_config_state", schema = "super_api_gateway")
public class GatewayConfigState {

    public static final long SINGLETON_ID = 1L;

    @Id
    private Long id;

    @Column(name = "current_revision", nullable = false)
    private long currentRevision;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected GatewayConfigState() {
    }

    public GatewayConfigState(long id) {
        this.id = id;
        this.currentRevision = 0;
        this.updatedAt = Instant.now();
    }

    public long increment() {
        currentRevision++;
        updatedAt = Instant.now();
        return currentRevision;
    }

    public long getCurrentRevision() {
        return currentRevision;
    }
}
