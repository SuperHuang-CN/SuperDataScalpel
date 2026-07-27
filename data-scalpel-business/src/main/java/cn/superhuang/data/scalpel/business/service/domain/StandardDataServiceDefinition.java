package cn.superhuang.data.scalpel.business.service.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.UUID;

/** Mutable design-time definition of a standard table service. */
@Entity
@Table(name = "ds_standard_data_service_definition", uniqueConstraints = @UniqueConstraint(
        name = "uk_ds_standard_service_definition_service", columnNames = "data_service_id"
))
public class StandardDataServiceDefinition extends BaseEntity {

    @Column(name = "data_service_id", nullable = false, updatable = false)
    private UUID dataServiceId;

    @Column(name = "model_id", nullable = false)
    private UUID modelId;

    @Column(nullable = false)
    private int version;

    protected StandardDataServiceDefinition() {
    }

    public static StandardDataServiceDefinition create(UUID dataServiceId, UUID modelId) {
        StandardDataServiceDefinition definition = new StandardDataServiceDefinition();
        definition.dataServiceId = require(dataServiceId, "数据服务");
        definition.modelId = require(modelId, "模型");
        definition.version = 1;
        return definition;
    }

    public void update(UUID modelId) {
        UUID normalized = require(modelId, "模型");
        if (!normalized.equals(this.modelId)) {
            this.modelId = normalized;
            incrementVersion();
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

    private void incrementVersion() {
        if (version == Integer.MAX_VALUE) throw new IllegalStateException("数据服务定义版本已达到最大值");
        version++;
    }

    private static UUID require(UUID value, String label) {
        if (value == null) throw new IllegalArgumentException(label + "不能为空");
        return value;
    }
}
