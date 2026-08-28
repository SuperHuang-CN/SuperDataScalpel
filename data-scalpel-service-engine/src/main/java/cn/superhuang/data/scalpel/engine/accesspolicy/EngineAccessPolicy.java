package cn.superhuang.data.scalpel.engine.accesspolicy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/** Last successfully applied data-plane source-address policy for this Engine. */
@Entity
@Table(name = "ds_engine_access_policy", uniqueConstraints = @UniqueConstraint(
        name = "uk_ds_engine_access_policy_code", columnNames = "engine_code"
))
public class EngineAccessPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "engine_code", nullable = false, updatable = false, length = 64)
    private String engineCode;

    @Column(nullable = false)
    private long revision;

    @Column(name = "policy_hash", nullable = false, length = 64)
    private String policyHash;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "allow_cidrs_json", nullable = false)
    private String allowCidrsJson;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "deny_cidrs_json", nullable = false)
    private String denyCidrsJson;

    @Column(name = "applied_at", nullable = false)
    private Instant appliedAt;

    protected EngineAccessPolicy() {
    }

    public EngineAccessPolicy(
            String engineCode,
            long revision,
            String policyHash,
            String allowCidrsJson,
            String denyCidrsJson
    ) {
        this.engineCode = engineCode;
        this.revision = revision;
        this.policyHash = policyHash;
        this.allowCidrsJson = allowCidrsJson;
        this.denyCidrsJson = denyCidrsJson;
        this.appliedAt = Instant.now();
    }

    public void apply(long revision, String policyHash, String allowCidrsJson, String denyCidrsJson) {
        this.revision = revision;
        this.policyHash = policyHash;
        this.allowCidrsJson = allowCidrsJson;
        this.denyCidrsJson = denyCidrsJson;
        this.appliedAt = Instant.now();
    }

    @PrePersist
    void initializeAppliedAt() {
        if (appliedAt == null) appliedAt = Instant.now();
    }

    public String getEngineCode() { return engineCode; }
    public long getRevision() { return revision; }
    public String getPolicyHash() { return policyHash; }
    public String getAllowCidrsJson() { return allowCidrsJson; }
    public String getDenyCidrsJson() { return denyCidrsJson; }
}
