package cn.superhuang.data.scalpel.business.dataentry.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
        name = "ds_data_entry_form",
        uniqueConstraints = @UniqueConstraint(name = "uk_ds_data_entry_form_model", columnNames = "model_id")
)
public class DataEntryForm extends BaseEntity {

    @Column(name = "model_id", nullable = false, updatable = false)
    private UUID modelId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DataEntryFormStatus status;

    @Column(name = "published_model_schema_version")
    private Integer publishedModelSchemaVersion;

    protected DataEntryForm() {
    }

    private DataEntryForm(UUID modelId) {
        this.modelId = Objects.requireNonNull(modelId, "模型 ID 不能为空");
        this.status = DataEntryFormStatus.DRAFT;
    }

    public static DataEntryForm create(UUID modelId) {
        return new DataEntryForm(modelId);
    }

    public void publish(int schemaVersion) {
        if (status == DataEntryFormStatus.PUBLISHED) {
            throw new IllegalStateException("填报表单已经发布");
        }
        status = DataEntryFormStatus.PUBLISHED;
        publishedModelSchemaVersion = schemaVersion;
    }

    public void disable() {
        if (status != DataEntryFormStatus.PUBLISHED) {
            throw new IllegalStateException("只有已发布填报表单可以停用");
        }
        status = DataEntryFormStatus.DISABLED;
    }

    public UUID getModelId() {
        return modelId;
    }

    public DataEntryFormStatus getStatus() {
        return status;
    }

    public Integer getPublishedModelSchemaVersion() {
        return publishedModelSchemaVersion;
    }
}
