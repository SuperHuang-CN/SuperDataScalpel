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

/** One model or physical JDBC table participating in a lineage output flow. */
@Entity
@Table(
        name = "task_lineage_asset",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_task_lineage_asset_key",
                columnNames = {"snapshot_id", "asset_key"}
        ),
        indexes = {
                @Index(name = "idx_task_lineage_asset_snapshot_flow", columnList = "snapshot_id, flow_key"),
                @Index(name = "idx_task_lineage_asset_model", columnList = "model_id, asset_role, snapshot_id"),
                @Index(name = "idx_task_lineage_asset_physical", columnList = "data_source_id, physical_table_name")
        }
)
public class TaskLineageAsset extends BaseEntity {

    @Column(name = "snapshot_id", nullable = false, updatable = false)
    private UUID snapshotId;

    @Column(name = "asset_key", nullable = false, updatable = false, length = 128)
    private String assetKey;

    @Column(name = "flow_key", nullable = false, updatable = false, length = 128)
    private String flowKey;

    @Column(name = "origin_key", nullable = false, updatable = false, length = 128)
    private String originKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_role", nullable = false, updatable = false, length = 16)
    private LineageAssetRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_kind", nullable = false, updatable = false, length = 32)
    private LineageAssetKind assetKind;

    @Enumerated(EnumType.STRING)
    @Column(name = "write_mode", updatable = false, length = 32)
    private LineageWriteMode writeMode;

    @Column(name = "model_id", updatable = false)
    private UUID modelId;

    @Column(name = "model_schema_version", updatable = false)
    private Integer modelSchemaVersion;

    @Column(name = "model_code_snapshot", updatable = false, length = 64)
    private String modelCodeSnapshot;

    @Column(name = "model_name_snapshot", updatable = false, length = 100)
    private String modelNameSnapshot;

    @Column(name = "data_source_id", updatable = false)
    private UUID dataSourceId;

    @Column(name = "data_source_name_snapshot", updatable = false, length = 100)
    private String dataSourceNameSnapshot;

    @Column(name = "catalog_name", updatable = false, length = 128)
    private String catalogName;

    @Column(name = "schema_name", updatable = false, length = 128)
    private String schemaName;

    @Column(name = "physical_table_name", updatable = false, length = 128)
    private String physicalTableName;

    @Enumerated(EnumType.STRING)
    @Column(name = "external_resource_type", updatable = false, length = 48)
    private LineageExternalResourceType externalResourceType;

    @Column(name = "resource_id", updatable = false)
    private UUID resourceId;

    @Column(name = "resource_key", updatable = false, length = 128)
    private String resourceKey;

    @Column(name = "resource_name_snapshot", updatable = false, length = 255)
    private String resourceNameSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "flow_coverage", updatable = false, length = 32)
    private LineageCoverage flowCoverage;

    protected TaskLineageAsset() {
    }

    private TaskLineageAsset(
            UUID snapshotId, String assetKey, String flowKey, String originKey,
            LineageAssetRole role, LineageAssetKind assetKind, LineageWriteMode writeMode
    ) {
        this.snapshotId = Objects.requireNonNull(snapshotId, "snapshotId");
        this.assetKey = required(assetKey, "assetKey");
        this.flowKey = required(flowKey, "flowKey");
        this.originKey = required(originKey, "originKey");
        this.role = Objects.requireNonNull(role, "role");
        this.assetKind = Objects.requireNonNull(assetKind, "assetKind");
        this.writeMode = writeMode;
    }

    public static TaskLineageAsset model(
            UUID snapshotId, String assetKey, String flowKey, String originKey,
            LineageAssetRole role, LineageWriteMode writeMode,
            UUID modelId, int modelSchemaVersion, String modelCode, String modelName
    ) {
        TaskLineageAsset asset = new TaskLineageAsset(
                snapshotId, assetKey, flowKey, originKey, role, LineageAssetKind.MODEL, writeMode
        );
        asset.modelId = Objects.requireNonNull(modelId, "modelId");
        asset.modelSchemaVersion = modelSchemaVersion;
        asset.modelCodeSnapshot = required(modelCode, "modelCode");
        asset.modelNameSnapshot = required(modelName, "modelName");
        return asset;
    }

    public static TaskLineageAsset jdbcTable(
            UUID snapshotId, String assetKey, String flowKey, String originKey,
            LineageAssetRole role, LineageWriteMode writeMode,
            UUID dataSourceId, String dataSourceName, String catalogName,
            String schemaName, String physicalTableName
    ) {
        TaskLineageAsset asset = new TaskLineageAsset(
                snapshotId, assetKey, flowKey, originKey, role, LineageAssetKind.JDBC_TABLE, writeMode
        );
        asset.dataSourceId = Objects.requireNonNull(dataSourceId, "dataSourceId");
        asset.dataSourceNameSnapshot = required(dataSourceName, "dataSourceName");
        asset.catalogName = optional(catalogName);
        asset.schemaName = optional(schemaName);
        asset.physicalTableName = required(physicalTableName, "physicalTableName");
        return asset;
    }

    public static TaskLineageAsset externalResource(
            UUID snapshotId, String assetKey, String flowKey, String originKey,
            LineageAssetRole role, LineageWriteMode writeMode,
            LineageExternalResourceType resourceType, UUID dataSourceId,
            String dataSourceName, UUID resourceId, String resourceKey,
            String resourceName, LineageCoverage flowCoverage
    ) {
        TaskLineageAsset asset = new TaskLineageAsset(
                snapshotId, assetKey, flowKey, originKey, role,
                LineageAssetKind.EXTERNAL_RESOURCE, writeMode
        );
        asset.externalResourceType = Objects.requireNonNull(resourceType, "resourceType");
        asset.dataSourceId = dataSourceId;
        asset.dataSourceNameSnapshot = optional(dataSourceName);
        asset.resourceId = resourceId;
        asset.resourceKey = required(resourceKey, "resourceKey");
        asset.resourceNameSnapshot = required(resourceName, "resourceName");
        asset.flowCoverage = role == LineageAssetRole.OUTPUT
                ? Objects.requireNonNull(flowCoverage, "flowCoverage") : null;
        return asset;
    }

    public void useFlowCoverage(LineageCoverage flowCoverage) {
        if (role != LineageAssetRole.OUTPUT) {
            throw new IllegalStateException("只有输出资产可以声明链路覆盖程度");
        }
        this.flowCoverage = Objects.requireNonNull(flowCoverage, "flowCoverage");
    }

    public UUID getSnapshotId() { return snapshotId; }
    public String getAssetKey() { return assetKey; }
    public String getFlowKey() { return flowKey; }
    public String getOriginKey() { return originKey; }
    public LineageAssetRole getRole() { return role; }
    public LineageAssetKind getAssetKind() { return assetKind; }
    public LineageWriteMode getWriteMode() { return writeMode; }
    public UUID getModelId() { return modelId; }
    public Integer getModelSchemaVersion() { return modelSchemaVersion; }
    public String getModelCodeSnapshot() { return modelCodeSnapshot; }
    public String getModelNameSnapshot() { return modelNameSnapshot; }
    public UUID getDataSourceId() { return dataSourceId; }
    public String getDataSourceNameSnapshot() { return dataSourceNameSnapshot; }
    public String getCatalogName() { return catalogName; }
    public String getSchemaName() { return schemaName; }
    public String getPhysicalTableName() { return physicalTableName; }
    public LineageExternalResourceType getExternalResourceType() { return externalResourceType; }
    public UUID getResourceId() { return resourceId; }
    public String getResourceKey() { return resourceKey; }
    public String getResourceNameSnapshot() { return resourceNameSnapshot; }
    public LineageCoverage getFlowCoverage() { return flowCoverage; }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + "不能为空");
        return value.trim();
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
