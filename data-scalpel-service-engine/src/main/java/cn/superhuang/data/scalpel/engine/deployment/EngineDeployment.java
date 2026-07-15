package cn.superhuang.data.scalpel.engine.deployment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

/** Persisted runtime deployment. It intentionally stores a snapshot, not control-plane entities. */
@Entity
@Table(
        name = "ds_engine_deployment",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ds_engine_deployment_service",
                columnNames = {"engine_code", "service_id"}
        )
)
public class EngineDeployment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "engine_code", nullable = false, updatable = false, length = 64)
    private String engineCode;

    @Column(name = "service_id", nullable = false, updatable = false)
    private UUID serviceId;

    @Column(nullable = false)
    private long revision;

    @Column(name = "service_code", nullable = false, length = 64)
    private String serviceCode;

    @Column(name = "route_path", nullable = false, length = 255)
    private String routePath;

    @Column(name = "definition_digest", nullable = false, length = 128)
    private String definitionDigest;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private EngineDeploymentRecordStatus status;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "definition_json", nullable = false)
    private String definitionJson;

    @Column(name = "data_source_id", nullable = false)
    private UUID dataSourceId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected EngineDeployment() {
    }

    public static EngineDeployment create(
            String engineCode,
            UUID serviceId,
            long revision,
            String serviceCode,
            String routePath,
            String definitionDigest,
            String definitionJson,
            UUID dataSourceId
    ) {
        EngineDeployment deployment = new EngineDeployment();
        deployment.engineCode = engineCode;
        deployment.serviceId = serviceId;
        deployment.apply(revision, serviceCode, routePath, definitionDigest, definitionJson, dataSourceId);
        return deployment;
    }

    public void apply(
            long revision,
            String serviceCode,
            String routePath,
            String definitionDigest,
            String definitionJson,
            UUID dataSourceId
    ) {
        this.revision = revision;
        this.serviceCode = serviceCode;
        this.routePath = routePath;
        this.definitionDigest = definitionDigest;
        this.definitionJson = definitionJson;
        this.dataSourceId = dataSourceId;
        this.status = EngineDeploymentRecordStatus.DEPLOYED;
        this.updatedAt = Instant.now();
    }

    public void markRemoved(long revision) {
        this.revision = revision;
        this.status = EngineDeploymentRecordStatus.REMOVED;
        this.updatedAt = Instant.now();
    }

    @PrePersist
    void initializeTimes() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    public UUID getId() {
        return id;
    }

    public String getEngineCode() {
        return engineCode;
    }

    public UUID getServiceId() {
        return serviceId;
    }

    public long getRevision() {
        return revision;
    }

    public String getServiceCode() {
        return serviceCode;
    }

    public String getRoutePath() {
        return routePath;
    }

    public String getDefinitionDigest() {
        return definitionDigest;
    }

    public EngineDeploymentRecordStatus getStatus() {
        return status;
    }

    public String getDefinitionJson() {
        return definitionJson;
    }

    public UUID getDataSourceId() {
        return dataSourceId;
    }
}
