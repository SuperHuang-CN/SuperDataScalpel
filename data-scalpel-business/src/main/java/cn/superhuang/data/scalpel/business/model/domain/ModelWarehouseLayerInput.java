package cn.superhuang.data.scalpel.business.model.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.UUID;

/** One configured input-layer allowance for a target warehouse layer. */
@Entity
@Table(
        name = "ds_model_warehouse_layer_input",
        indexes = {
                @Index(
                        name = "idx_ds_model_warehouse_layer_input_target",
                        columnList = "target_layer_id"
                ),
                @Index(
                        name = "idx_ds_model_warehouse_layer_input_input",
                        columnList = "input_layer_id"
                )
        },
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ds_model_warehouse_layer_input",
                columnNames = {"target_layer_id", "input_layer_id"}
        )
)
public class ModelWarehouseLayerInput extends BaseEntity {

    @Column(name = "target_layer_id", nullable = false)
    private UUID targetLayerId;

    @Column(name = "input_layer_id", nullable = false)
    private UUID inputLayerId;

    protected ModelWarehouseLayerInput() {
    }

    private ModelWarehouseLayerInput(UUID targetLayerId, UUID inputLayerId) {
        this.targetLayerId = targetLayerId;
        this.inputLayerId = inputLayerId;
    }

    public static ModelWarehouseLayerInput create(UUID targetLayerId, UUID inputLayerId) {
        if (targetLayerId == null || inputLayerId == null) {
            throw new IllegalArgumentException("数仓分层输入关系不能为空");
        }
        return new ModelWarehouseLayerInput(targetLayerId, inputLayerId);
    }

    public UUID getTargetLayerId() {
        return targetLayerId;
    }

    public UUID getInputLayerId() {
        return inputLayerId;
    }
}
