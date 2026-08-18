package cn.superhuang.data.scalpel.business.lineage.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.Objects;
import java.util.UUID;

/** Immutable field snapshot belonging to one lineage asset. */
@Entity
@Table(
        name = "task_lineage_asset_field",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_task_lineage_asset_field_key",
                columnNames = {"asset_id", "field_key"}
        ),
        indexes = {
                @Index(name = "idx_task_lineage_asset_field_snapshot", columnList = "snapshot_id"),
                @Index(name = "idx_task_lineage_asset_field_model", columnList = "model_field_id, snapshot_id")
        }
)
public class TaskLineageAssetField extends BaseEntity {

    @Column(name = "snapshot_id", nullable = false, updatable = false)
    private UUID snapshotId;

    @Column(name = "asset_id", nullable = false, updatable = false)
    private UUID assetId;

    @Column(name = "field_key", nullable = false, updatable = false, length = 128)
    private String fieldKey;

    @Column(name = "model_field_id", updatable = false)
    private UUID modelFieldId;

    @Column(name = "field_code_snapshot", nullable = false, updatable = false, length = 128)
    private String fieldCodeSnapshot;

    @Column(name = "field_name_snapshot", nullable = false, updatable = false, length = 100)
    private String fieldNameSnapshot;

    @Column(name = "sort_order", nullable = false, updatable = false)
    private int sortOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "output_effect", updatable = false, length = 32)
    private LineageOutputFieldEffect outputEffect;

    protected TaskLineageAssetField() {
    }

    private TaskLineageAssetField(
            UUID snapshotId, UUID assetId, String fieldKey, UUID modelFieldId,
            String fieldCodeSnapshot, String fieldNameSnapshot, int sortOrder,
            LineageOutputFieldEffect outputEffect
    ) {
        if (sortOrder < 0) throw new IllegalArgumentException("血缘字段顺序不能小于 0");
        this.snapshotId = Objects.requireNonNull(snapshotId, "snapshotId");
        this.assetId = Objects.requireNonNull(assetId, "assetId");
        this.fieldKey = required(fieldKey, "fieldKey");
        this.modelFieldId = modelFieldId;
        this.fieldCodeSnapshot = required(fieldCodeSnapshot, "fieldCodeSnapshot");
        this.fieldNameSnapshot = required(fieldNameSnapshot, "fieldNameSnapshot");
        this.sortOrder = sortOrder;
        this.outputEffect = outputEffect;
    }

    public static TaskLineageAssetField create(
            UUID snapshotId, UUID assetId, String fieldKey, UUID modelFieldId,
            String fieldCodeSnapshot, String fieldNameSnapshot, int sortOrder,
            LineageOutputFieldEffect outputEffect
    ) {
        return new TaskLineageAssetField(
                snapshotId, assetId, fieldKey, modelFieldId,
                fieldCodeSnapshot, fieldNameSnapshot, sortOrder, outputEffect
        );
    }

    public UUID getSnapshotId() { return snapshotId; }
    public UUID getAssetId() { return assetId; }
    public String getFieldKey() { return fieldKey; }
    public UUID getModelFieldId() { return modelFieldId; }
    public String getFieldCodeSnapshot() { return fieldCodeSnapshot; }
    public String getFieldNameSnapshot() { return fieldNameSnapshot; }
    public int getSortOrder() { return sortOrder; }
    public LineageOutputFieldEffect getOutputEffect() { return outputEffect; }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + "不能为空");
        return value.trim();
    }
}
