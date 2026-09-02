package cn.superhuang.data.scalpel.business.service.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.Objects;
import java.util.UUID;

/** Design-time model binding for a GeoServer-backed spatial service. */
@Entity
@Table(name = "ds_spatial_data_service_definition", uniqueConstraints = @UniqueConstraint(
        name = "uk_ds_spatial_service_definition_service", columnNames = "data_service_id"
))
public class SpatialDataServiceDefinition extends BaseEntity {

    @Column(name = "data_service_id", nullable = false, updatable = false)
    private UUID dataServiceId;

    @Column(name = "model_id", nullable = false)
    private UUID modelId;

    @Column(nullable = false)
    private int version;

    protected SpatialDataServiceDefinition() {
    }

    private SpatialDataServiceDefinition(UUID dataServiceId, UUID modelId) {
        this.dataServiceId = Objects.requireNonNull(dataServiceId, "数据服务不能为空");
        this.modelId = Objects.requireNonNull(modelId, "空间模型不能为空");
        this.version = 1;
    }

    public static SpatialDataServiceDefinition create(UUID dataServiceId, UUID modelId) {
        return new SpatialDataServiceDefinition(dataServiceId, modelId);
    }

    public void update(UUID modelId) {
        UUID next = Objects.requireNonNull(modelId, "空间模型不能为空");
        if (!next.equals(this.modelId)) {
            this.modelId = next;
            this.version++;
        }
    }

    public UUID getDataServiceId() {
        return dataServiceId;
    }

    public UUID getModelId() {
        return modelId;
    }

    public int getVersion() {
        return version;
    }
}
