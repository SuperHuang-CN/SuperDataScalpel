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

/** Value derivation from one input asset field to one output asset field. */
@Entity
@Table(
        name = "task_lineage_field_edge",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_task_lineage_field_edge",
                columnNames = {"snapshot_id", "flow_key", "derivation_key", "source_asset_field_id", "target_asset_field_id"}
        ),
        indexes = {
                @Index(name = "idx_task_lineage_field_edge_source", columnList = "source_asset_field_id, snapshot_id"),
                @Index(name = "idx_task_lineage_field_edge_target", columnList = "target_asset_field_id, snapshot_id"),
                @Index(name = "idx_task_lineage_field_edge_flow", columnList = "snapshot_id, flow_key")
        }
)
public class TaskLineageFieldEdge extends BaseEntity {

    @Column(name = "snapshot_id", nullable = false, updatable = false)
    private UUID snapshotId;

    @Column(name = "flow_key", nullable = false, updatable = false, length = 128)
    private String flowKey;

    @Column(name = "source_asset_field_id", nullable = false, updatable = false)
    private UUID sourceAssetFieldId;

    @Column(name = "target_asset_field_id", nullable = false, updatable = false)
    private UUID targetAssetFieldId;

    @Column(name = "derivation_key", nullable = false, updatable = false, length = 160)
    private String derivationKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "derivation_type", nullable = false, updatable = false, length = 32)
    private LineageFieldDerivationType derivationType;

    @Column(name = "transform_node_key", updatable = false, length = 128)
    private String transformNodeKey;

    protected TaskLineageFieldEdge() {
    }

    private TaskLineageFieldEdge(
            UUID snapshotId, String flowKey, UUID sourceAssetFieldId, UUID targetAssetFieldId,
            String derivationKey, LineageFieldDerivationType derivationType, String transformNodeKey
    ) {
        this.snapshotId = Objects.requireNonNull(snapshotId, "snapshotId");
        this.flowKey = required(flowKey, "flowKey");
        this.sourceAssetFieldId = Objects.requireNonNull(sourceAssetFieldId, "sourceAssetFieldId");
        this.targetAssetFieldId = Objects.requireNonNull(targetAssetFieldId, "targetAssetFieldId");
        this.derivationKey = required(derivationKey, "derivationKey");
        this.derivationType = Objects.requireNonNull(derivationType, "derivationType");
        this.transformNodeKey = optional(transformNodeKey);
    }

    public static TaskLineageFieldEdge create(
            UUID snapshotId, String flowKey, UUID sourceAssetFieldId, UUID targetAssetFieldId,
            String derivationKey, LineageFieldDerivationType derivationType, String transformNodeKey
    ) {
        return new TaskLineageFieldEdge(
                snapshotId, flowKey, sourceAssetFieldId, targetAssetFieldId,
                derivationKey, derivationType, transformNodeKey
        );
    }

    public UUID getSnapshotId() { return snapshotId; }
    public String getFlowKey() { return flowKey; }
    public UUID getSourceAssetFieldId() { return sourceAssetFieldId; }
    public UUID getTargetAssetFieldId() { return targetAssetFieldId; }
    public String getDerivationKey() { return derivationKey; }
    public LineageFieldDerivationType getDerivationType() { return derivationType; }
    public String getTransformNodeKey() { return transformNodeKey; }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + "不能为空");
        return value.trim();
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
