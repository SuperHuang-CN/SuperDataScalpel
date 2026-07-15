package cn.superhuang.data.scalpel.business.service.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DataServiceDeploymentStatus status;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "deployed_at")
    private Instant deployedAt;

    protected DataServiceDeployment() {
    }

    private DataServiceDeployment(UUID dataServiceId, long revision) {
        this.dataServiceId = dataServiceId;
        this.revision = revision;
        this.status = DataServiceDeploymentStatus.PENDING;
    }

    public static DataServiceDeployment pending(UUID dataServiceId, long revision) {
        return new DataServiceDeployment(dataServiceId, revision);
    }

    public void begin(long revision) {
        this.revision = revision;
        this.status = DataServiceDeploymentStatus.PENDING;
        this.lastError = null;
    }

    public void deployed() {
        this.status = DataServiceDeploymentStatus.DEPLOYED;
        this.lastError = null;
        this.deployedAt = Instant.now();
    }

    public void beginRemoval() {
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

    public DataServiceDeploymentStatus getStatus() {
        return status;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getDeployedAt() {
        return deployedAt;
    }
}
