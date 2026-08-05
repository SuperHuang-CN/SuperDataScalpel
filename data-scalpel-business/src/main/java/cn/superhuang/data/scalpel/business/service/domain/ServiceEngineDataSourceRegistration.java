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

/** Relationship and synchronization state between an Admin data source and an Engine. */
@Entity
@Table(
        name = "ds_service_engine_data_source",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ds_service_engine_data_source",
                columnNames = {"engine_id", "data_source_id"}
        )
)
public class ServiceEngineDataSourceRegistration extends BaseEntity {

    @Column(name = "engine_id", nullable = false, updatable = false)
    private UUID engineId;

    @Column(name = "data_source_id", nullable = false, updatable = false)
    private UUID dataSourceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ServiceEngineDataSourceRegistrationStatus status;

    /** Legacy non-null column retained for ddl-auto compatibility; Engine synchronization no longer uses revisions. */
    @Column(nullable = false)
    private long revision;

    @Column(name = "synced_digest", length = 128)
    private String syncedDigest;

    @Column(name = "synchronized_at")
    private Instant synchronizedAt;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    protected ServiceEngineDataSourceRegistration() {
    }

    private ServiceEngineDataSourceRegistration(UUID engineId, UUID dataSourceId) {
        this.engineId = engineId;
        this.dataSourceId = dataSourceId;
        this.status = ServiceEngineDataSourceRegistrationStatus.PENDING;
    }

    public static ServiceEngineDataSourceRegistration pending(UUID engineId, UUID dataSourceId) {
        return new ServiceEngineDataSourceRegistration(engineId, dataSourceId);
    }

    public void beginSync() {
        status = ServiceEngineDataSourceRegistrationStatus.PENDING;
        lastError = null;
    }

    public void ready(String digest) {
        status = ServiceEngineDataSourceRegistrationStatus.READY;
        syncedDigest = digest;
        synchronizedAt = Instant.now();
        lastError = null;
    }

    public void failed(String message) {
        status = synchronizedAt == null
                ? ServiceEngineDataSourceRegistrationStatus.FAILED
                : ServiceEngineDataSourceRegistrationStatus.OUTDATED;
        lastError = limit(message);
    }

    public void markOutdated() {
        if (status != ServiceEngineDataSourceRegistrationStatus.PENDING) {
            status = ServiceEngineDataSourceRegistrationStatus.OUTDATED;
        }
    }

    public UUID getEngineId() {
        return engineId;
    }

    public UUID getDataSourceId() {
        return dataSourceId;
    }

    public ServiceEngineDataSourceRegistrationStatus getStatus() {
        return status;
    }

    public String getSyncedDigest() {
        return syncedDigest;
    }

    public Instant getSynchronizedAt() {
        return synchronizedAt;
    }

    public String getLastError() {
        return lastError;
    }

    private static String limit(String value) {
        if (value == null || value.isBlank()) {
            return "服务引擎调用失败";
        }
        return value.substring(0, Math.min(1000, value.length()));
    }
}
