package cn.superhuang.data.scalpel.business.lineage.service;

import cn.superhuang.data.scalpel.business.lineage.domain.LineageAssetKind;
import cn.superhuang.data.scalpel.business.lineage.domain.LineageAssetRole;
import cn.superhuang.data.scalpel.business.lineage.domain.LineageCoverage;
import cn.superhuang.data.scalpel.business.lineage.domain.LineageFieldDerivationType;
import cn.superhuang.data.scalpel.business.lineage.domain.LineageFieldUsageType;
import cn.superhuang.data.scalpel.business.lineage.domain.LineageExternalResourceType;
import cn.superhuang.data.scalpel.business.lineage.domain.LineageOutputFieldEffect;
import cn.superhuang.data.scalpel.business.lineage.domain.LineageWriteMode;

import java.util.List;
import java.util.UUID;

/**
 * Module-internal ingestion contract for a complete, immutable lineage snapshot.
 * Keys are local to this draft and are resolved to persisted UUIDs by the service.
 */
public record TaskLineageSnapshotDraft(
        UUID taskId,
        int definitionVersion,
        LineageCoverage coverage,
        int generatorVersion,
        List<AssetDraft> assets,
        List<FieldDraft> fields,
        List<FieldEdgeDraft> fieldEdges,
        List<FieldUsageDraft> fieldUsages
) {
    public TaskLineageSnapshotDraft {
        assets = assets == null ? List.of() : List.copyOf(assets);
        fields = fields == null ? List.of() : List.copyOf(fields);
        fieldEdges = fieldEdges == null ? List.of() : List.copyOf(fieldEdges);
        fieldUsages = fieldUsages == null ? List.of() : List.copyOf(fieldUsages);
    }

    public record AssetDraft(
            String assetKey,
            String flowKey,
            String originKey,
            LineageAssetRole role,
            LineageAssetKind kind,
            LineageWriteMode writeMode,
            UUID modelId,
            Integer modelSchemaVersion,
            UUID dataSourceId,
            String catalogName,
            String schemaName,
            String physicalTableName,
            LineageExternalResourceType externalResourceType,
            UUID resourceId,
            String resourceKey,
            String resourceName,
            LineageCoverage flowCoverage
    ) {
        public AssetDraft(
                String assetKey,
                String flowKey,
                String originKey,
                LineageAssetRole role,
                LineageAssetKind kind,
                LineageWriteMode writeMode,
                UUID modelId,
                Integer modelSchemaVersion,
                UUID dataSourceId,
                String catalogName,
                String schemaName,
                String physicalTableName
        ) {
            this(
                    assetKey, flowKey, originKey, role, kind, writeMode,
                    modelId, modelSchemaVersion, dataSourceId, catalogName, schemaName, physicalTableName,
                    null, null, null, null, null
            );
        }

        public static AssetDraft model(
                String assetKey,
                String flowKey,
                String originKey,
                LineageAssetRole role,
                LineageWriteMode writeMode,
                UUID modelId,
                int modelSchemaVersion
        ) {
            return new AssetDraft(
                    assetKey, flowKey, originKey, role, LineageAssetKind.MODEL, writeMode,
                    modelId, modelSchemaVersion, null, null, null, null,
                    null, null, null, null, null
            );
        }

        public static AssetDraft jdbcTable(
                String assetKey,
                String flowKey,
                String originKey,
                LineageAssetRole role,
                LineageWriteMode writeMode,
                UUID dataSourceId,
                String catalogName,
                String schemaName,
                String physicalTableName
        ) {
            return new AssetDraft(
                    assetKey, flowKey, originKey, role, LineageAssetKind.JDBC_TABLE, writeMode,
                    null, null, dataSourceId, catalogName, schemaName, physicalTableName,
                    null, null, null, null, null
            );
        }

        public static AssetDraft externalResource(
                String assetKey,
                String flowKey,
                String originKey,
                LineageAssetRole role,
                LineageWriteMode writeMode,
                LineageExternalResourceType resourceType,
                UUID dataSourceId,
                UUID resourceId,
                String resourceKey,
                String resourceName,
                LineageCoverage flowCoverage
        ) {
            return new AssetDraft(
                    assetKey, flowKey, originKey, role, LineageAssetKind.EXTERNAL_RESOURCE, writeMode,
                    null, null, dataSourceId, null, null, null,
                    resourceType, resourceId, resourceKey, resourceName,
                    role == LineageAssetRole.OUTPUT ? flowCoverage : null
            );
        }
    }

    public record FieldDraft(
            String assetKey,
            String fieldKey,
            UUID modelFieldId,
            String columnCode,
            String columnName,
            int sortOrder,
            LineageOutputFieldEffect outputEffect
    ) {
    }

    public record FieldEdgeDraft(
            String flowKey,
            FieldReference source,
            FieldReference target,
            String derivationKey,
            LineageFieldDerivationType derivationType,
            String transformNodeKey
    ) {
    }

    public record FieldUsageDraft(
            String flowKey,
            FieldReference field,
            String nodeKey,
            LineageFieldUsageType usageType
    ) {
    }

    public record FieldReference(String assetKey, String fieldKey) {
    }
}
