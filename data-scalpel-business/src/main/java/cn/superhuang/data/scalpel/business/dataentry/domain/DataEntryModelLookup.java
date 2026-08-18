package cn.superhuang.data.scalpel.business.dataentry.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
        name = "ds_data_entry_model_lookup",
        indexes = @Index(name = "idx_ds_data_entry_model_lookup_form", columnList = "form_id"),
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ds_data_entry_model_lookup_target",
                columnNames = {"form_id", "target_field_id"}
        )
)
public class DataEntryModelLookup extends BaseEntity {

    @Column(name = "form_id", nullable = false, updatable = false)
    private UUID formId;

    @Column(name = "target_field_id", nullable = false, updatable = false)
    private UUID targetFieldId;

    @Column(name = "source_model_id", nullable = false)
    private UUID sourceModelId;

    @Column(name = "source_label_field_id", nullable = false)
    private UUID sourceLabelFieldId;

    protected DataEntryModelLookup() {
    }

    private DataEntryModelLookup(UUID formId, UUID targetFieldId, UUID sourceModelId, UUID sourceLabelFieldId) {
        this.formId = Objects.requireNonNull(formId, "表单 ID 不能为空");
        this.targetFieldId = Objects.requireNonNull(targetFieldId, "目标字段 ID 不能为空");
        this.sourceModelId = Objects.requireNonNull(sourceModelId, "来源模型 ID 不能为空");
        this.sourceLabelFieldId = Objects.requireNonNull(sourceLabelFieldId, "来源标签字段 ID 不能为空");
    }

    public static DataEntryModelLookup create(
            UUID formId,
            UUID targetFieldId,
            UUID sourceModelId,
            UUID sourceLabelFieldId
    ) {
        return new DataEntryModelLookup(formId, targetFieldId, sourceModelId, sourceLabelFieldId);
    }

    public UUID getFormId() {
        return formId;
    }

    public UUID getTargetFieldId() {
        return targetFieldId;
    }

    public UUID getSourceModelId() {
        return sourceModelId;
    }

    public UUID getSourceLabelFieldId() {
        return sourceLabelFieldId;
    }
}
