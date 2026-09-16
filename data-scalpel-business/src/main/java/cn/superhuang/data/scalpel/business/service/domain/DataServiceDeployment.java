package cn.superhuang.data.scalpel.business.service.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/** Actual remote deployment state, intentionally distinct from the service definition state. */
@Entity
@Table(name = "ds_data_service_deployment", uniqueConstraints = @UniqueConstraint(
        name = "uk_ds_data_service_deployment_service", columnNames = "data_service_id"
))
public class DataServiceDeployment extends BaseEntity {

    @Column(name = "data_service_id", nullable = false, updatable = false)
    private UUID dataServiceId;

    @Column(nullable = false)
    private long revision;

    @Column(name = "engine_id", nullable = false)
    private UUID engineId;

    @Column(name = "definition_digest", nullable = false, length = 128)
    private String definitionDigest;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "definition_json", nullable = false)
    private String definitionJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DataServiceDeploymentStatus status;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "deployed_at")
    private Instant deployedAt;

    @Column(name = "operation_id")
    private UUID operationId;

    public UUID getOperationId() { return operationId; }

    protected DataServiceDeployment() {
    }

    private DataServiceDeployment(
            UUID dataServiceId,
            long revision,
            UUID engineId,
            String definitionDigest,
            String definitionJson
    ) {
        this.dataServiceId = dataServiceId;
        begin(revision, engineId, definitionDigest, definitionJson);
    }

    public static DataServiceDeployment pending(
            UUID dataServiceId,
            long revision,
            UUID engineId,
            String definitionDigest,
            String definitionJson
    ) {
        return new DataServiceDeployment(dataServiceId, revision, engineId, definitionDigest, definitionJson);
    }

    public void begin(long revision, UUID engineId, String definitionDigest, String definitionJson) {
        this.revision = revision;
        this.engineId = java.util.Objects.requireNonNull(engineId, "Engine is required");
        this.definitionDigest = required(definitionDigest, "Definition digest");
        this.definitionJson = required(definitionJson, "Definition JSON");
        this.operationId = UUID.randomUUID();
        this.status = DataServiceDeploymentStatus.PENDING;
        this.lastError = null;
    }

    public void deployed() {
        this.status = DataServiceDeploymentStatus.DEPLOYED;
        this.lastError = null;
        this.deployedAt = Instant.now();
    }

    public void beginRemoval() {
        this.operationId = UUID.randomUUID();
        this.status = DataServiceDeploymentStatus.REMOVING;
        this.lastError = null;
    }

    public void removed() {
        this.status = DataServiceDeploymentStatus.REMOVED;
        this.lastError = null;
    }

    public void failed(String message) {
        this.status = DataServiceDeploymentStatus.FAILED;
        this.lastError = message == null ? "服务引擎调用失败" : message.substring(0, Math.min(1000, message.length()));
    }

    public UUID getDataServiceId() {
        return dataServiceId;
    }

    public long getRevision() {
        return revision;
    }

    public UUID getEngineId() {
        return engineId;
    }

    public String getDefinitionDigest() {
        return definitionDigest;
    }

    public String getDefinitionJson() {
        return definitionJson;
    }

    public DataServiceDeploymentStatus getStatus() {
        return status;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getDeployedAt() {
        return deployedAt;
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required");
        return value;
    }
}
