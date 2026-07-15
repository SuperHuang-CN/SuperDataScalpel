package cn.superhuang.data.scalpel.business.service.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.Locale;
import java.util.UUID;

/** One V1 standard service backed by exactly one published data model. */
@Entity
@Table(
        name = "ds_data_service",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_ds_data_service_code", columnNames = "code"),
                @UniqueConstraint(name = "uk_ds_data_service_engine_route", columnNames = {"engine_id", "route_path"})
        }
)
public class DataService extends BaseEntity {

    @Column(nullable = false, updatable = false, length = 64)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "directory_id")
    private UUID directoryId;

    @Column(name = "model_id", nullable = false)
    private UUID modelId;

    @Column(name = "engine_id", nullable = false)
    private UUID engineId;

    @Column(name = "route_path", nullable = false, length = 255)
    private String routePath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DataServiceStatus status;

    @Column(nullable = false)
    private long revision;

    @Column(length = 1000)
    private String description;

    protected DataService() {
    }

    private DataService(
            String code,
            String name,
            UUID directoryId,
            UUID modelId,
            UUID engineId,
            String routePath,
            String description
    ) {
        this.code = normalizeCode(code);
        this.status = DataServiceStatus.DRAFT;
        update(name, directoryId, modelId, engineId, routePath, description);
    }

    public static DataService create(
            String code,
            String name,
            UUID directoryId,
            UUID modelId,
            UUID engineId,
            String routePath,
            String description
    ) {
        return new DataService(code, name, directoryId, modelId, engineId, routePath, description);
    }

    public void update(
            String name,
            UUID directoryId,
            UUID modelId,
            UUID engineId,
            String routePath,
            String description
    ) {
        this.name = required(name, "名称");
        this.directoryId = directoryId;
        this.modelId = requireId(modelId, "模型");
        this.engineId = requireId(engineId, "服务引擎");
        this.routePath = ServiceRoutePath.normalize(routePath);
        this.description = optional(description);
    }

    public long nextRevision() {
        revision++;
        return revision;
    }

    public void markPublished() {
        status = DataServiceStatus.PUBLISHED;
    }

    public void markDisabled() {
        status = DataServiceStatus.DISABLED;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public UUID getDirectoryId() {
        return directoryId;
    }

    public UUID getModelId() {
        return modelId;
    }

    public UUID getEngineId() {
        return engineId;
    }

    public String getRoutePath() {
        return routePath;
    }

    public DataServiceStatus getStatus() {
        return status;
    }

    public long getRevision() {
        return revision;
    }

    public String getDescription() {
        return description;
    }

    private static UUID requireId(UUID id, String label) {
        if (id == null) {
            throw new IllegalArgumentException(label + "不能为空");
        }
        return id;
    }

    private static String normalizeCode(String value) {
        return required(value, "编码").toLowerCase(Locale.ROOT);
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + "不能为空");
        }
        return value.trim();
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
