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

/** Non-value usage of a field by task logic, such as a join or filter predicate. */
@Entity
@Table(
        name = "task_lineage_field_usage",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_task_lineage_field_usage",
                columnNames = {"snapshot_id", "flow_key", "asset_field_id", "node_key", "usage_type"}
        ),
        indexes = {
                @Index(name = "idx_task_lineage_field_usage_field", columnList = "asset_field_id, snapshot_id"),
                @Index(name = "idx_task_lineage_field_usage_flow", columnList = "snapshot_id, flow_key")
        }
)
public class TaskLineageFieldUsage extends BaseEntity {

    @Column(name = "snapshot_id", nullable = false, updatable = false)
    private UUID snapshotId;

    @Column(name = "flow_key", nullable = false, updatable = false, length = 128)
    private String flowKey;

    @Column(name = "asset_field_id", nullable = false, updatable = false)
    private UUID assetFieldId;

    @Column(name = "node_key", nullable = false, updatable = false, length = 128)
    private String nodeKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "usage_type", nullable = false, updatable = false, length = 32)
    private LineageFieldUsageType usageType;

    protected TaskLineageFieldUsage() {
    }

    private TaskLineageFieldUsage(
            UUID snapshotId, String flowKey, UUID assetFieldId,
            String nodeKey, LineageFieldUsageType usageType
    ) {
        this.snapshotId = Objects.requireNonNull(snapshotId, "snapshotId");
        this.flowKey = required(flowKey, "flowKey");
        this.assetFieldId = Objects.requireNonNull(assetFieldId, "assetFieldId");
        this.nodeKey = required(nodeKey, "nodeKey");
        this.usageType = Objects.requireNonNull(usageType, "usageType");
    }

    public static TaskLineageFieldUsage create(
            UUID snapshotId, String flowKey, UUID assetFieldId,
            String nodeKey, LineageFieldUsageType usageType
    ) {
        return new TaskLineageFieldUsage(snapshotId, flowKey, assetFieldId, nodeKey, usageType);
    }

    public UUID getSnapshotId() { return snapshotId; }
    public String getFlowKey() { return flowKey; }
    public UUID getAssetFieldId() { return assetFieldId; }
    public String getNodeKey() { return nodeKey; }
    public LineageFieldUsageType getUsageType() { return usageType; }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + "不能为空");
        return value.trim();
    }
}
