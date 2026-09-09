package cn.superhuang.data.scalpel.business.operations.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import cn.superhuang.data.scalpel.search.SearchExcluded;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ops_engine_observation")
public class EngineObservation extends BaseEntity {
    @Column(unique = true, nullable = false)
    private UUID engineId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EngineObservationState state = EngineObservationState.UNKNOWN;

    @Column()
    private Boolean dependenciesReady;

    @Column()
    private Instant attemptedAt;

    @Column()
    private Instant observedAt;

    @Column()
    private Instant lastHealthyAt;

    @Column()
    private Instant unreachableSince;

    @Column()
    private Instant notReadySince;

    @Column(nullable = false)
    private int reachableSamples;

    @Column(nullable = false)
    private int readySamples;

    @SearchExcluded
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column()
    private String snapshotJson;

    @Column(length = 300)
    private String summary;

    @Column()
    private Instant leaseUntil;

    @Column()
    private UUID claimToken;

    @Column()
    private Instant engineConfigurationAt;

    public UUID getEngineId() { return engineId; }
    public void setEngineId(UUID value) { engineId = value; }
    public EngineObservationState getState() { return state; }
    public void setState(EngineObservationState value) { state = value; }
    public Boolean getDependenciesReady() { return dependenciesReady; }
    public void setDependenciesReady(Boolean value) { dependenciesReady = value; }
    public Instant getAttemptedAt() { return attemptedAt; }
    public void setAttemptedAt(Instant value) { attemptedAt = value; }
    public Instant getObservedAt() { return observedAt; }
    public void setObservedAt(Instant value) { observedAt = value; }
    public Instant getLastHealthyAt() { return lastHealthyAt; }
    public void setLastHealthyAt(Instant value) { lastHealthyAt = value; }
    public Instant getUnreachableSince() { return unreachableSince; }
    public void setUnreachableSince(Instant value) { unreachableSince = value; }
    public Instant getNotReadySince() { return notReadySince; }
    public void setNotReadySince(Instant value) { notReadySince = value; }
    public int getReachableSamples() { return reachableSamples; }
    public void setReachableSamples(int value) { reachableSamples = value; }
    public int getReadySamples() { return readySamples; }
    public void setReadySamples(int value) { readySamples = value; }
    public String getSnapshotJson() { return snapshotJson; }
    public void setSnapshotJson(String value) { snapshotJson = value; }
    public String getSummary() { return summary; }
    public void setSummary(String value) { summary = value; }
    public Instant getLeaseUntil() { return leaseUntil; }
    public void setLeaseUntil(Instant value) { leaseUntil = value; }
    public UUID getClaimToken() { return claimToken; }
    public void setClaimToken(UUID value) { claimToken = value; }
    public Instant getEngineConfigurationAt() { return engineConfigurationAt; }
    public void setEngineConfigurationAt(Instant value) { engineConfigurationAt = value; }
}
