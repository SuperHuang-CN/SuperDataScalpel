package cn.superhuang.data.scalpel.dispatcher.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "dispatcher_identity")
public class DispatcherIdentity {
    @Id
    @Column(length = 32)
    private String identityKey;

    @Column(name = "instance_id", nullable = false, unique = true, updatable = false)
    private UUID instanceId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected DispatcherIdentity() {
    }

    public static DispatcherIdentity create() {
        DispatcherIdentity identity = new DispatcherIdentity();
        identity.identityKey = "singleton";
        identity.instanceId = UUID.randomUUID();
        identity.createdAt = Instant.now();
        return identity;
    }

    public UUID getInstanceId() { return instanceId; }
}
