package cn.superhuang.data.scalpel.business.service.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import cn.superhuang.data.scalpel.contract.service.DataServiceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/** Common lifecycle and routing metadata for one data service. */
@Entity
@Table(name = "ds_data_service")
public class DataService extends BaseEntity {

    @Column(nullable = false, updatable = false, length = 64)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "directory_id")
    private UUID directoryId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 32)
    private DataServiceType type;

    @Column(name = "engine_id", nullable = false)
    private UUID engineId;

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
            DataServiceType type,
            UUID engineId,
            String description
    ) {
        this.code = normalizeCode(code);
        this.type = Objects.requireNonNull(type, "数据服务类型不能为空");
        this.status = DataServiceStatus.DRAFT;
        update(name, directoryId, engineId, description);
    }

    public static DataService create(
            String code,
            String name,
            UUID directoryId,
            DataServiceType type,
            UUID engineId,
            String description
    ) {
        return new DataService(code, name, directoryId, type, engineId, description);
    }

    public void update(
            String name,
            UUID directoryId,
            UUID engineId,
            String description
    ) {
        this.name = required(name, "名称");
        this.directoryId = directoryId;
        this.engineId = requireId(engineId, "服务引擎");
        this.description = optional(description);
    }

    public long nextRevision() {
        revision++;
        return revision;
    }

    public void markEnabled() {
        status = DataServiceStatus.ENABLED;
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

    public DataServiceType getType() {
        return type;
    }

    public UUID getEngineId() {
        return engineId;
    }

    public String getEngineRoutePath() {
        return ServiceEngineRoutePath.forService(getId());
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
