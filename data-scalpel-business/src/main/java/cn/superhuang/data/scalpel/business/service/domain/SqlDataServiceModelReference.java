package cn.superhuang.data.scalpel.business.service.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.UUID;

/** One ordered model associated with a SQL data service for lineage and editing context. */
@Entity
@Table(name = "ds_sql_data_service_model", uniqueConstraints = {
        @UniqueConstraint(name = "uk_ds_sql_service_model", columnNames = {"data_service_id", "model_id"}),
        @UniqueConstraint(name = "uk_ds_sql_service_model_order", columnNames = {"data_service_id", "sort_order"})
})
public class SqlDataServiceModelReference extends BaseEntity {

    @Column(name = "data_service_id", nullable = false, updatable = false)
    private UUID dataServiceId;

    @Column(name = "model_id", nullable = false, updatable = false)
    private UUID modelId;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected SqlDataServiceModelReference() {
    }

    public static SqlDataServiceModelReference create(UUID dataServiceId, UUID modelId, int sortOrder) {
        if (dataServiceId == null || modelId == null) {
            throw new IllegalArgumentException("SQL 服务和模型不能为空");
        }
        if (sortOrder < 0) {
            throw new IllegalArgumentException("SQL 服务模型顺序不能小于 0");
        }
        SqlDataServiceModelReference reference = new SqlDataServiceModelReference();
        reference.dataServiceId = dataServiceId;
        reference.modelId = modelId;
        reference.sortOrder = sortOrder;
        return reference;
    }

    public UUID getDataServiceId() {
        return dataServiceId;
    }

    public UUID getModelId() {
        return modelId;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
