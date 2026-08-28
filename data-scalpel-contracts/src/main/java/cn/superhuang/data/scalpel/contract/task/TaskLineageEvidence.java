package cn.superhuang.data.scalpel.contract.task;

import java.util.List;
import java.util.UUID;

/**
 * Spark-free, producer-neutral lineage evidence emitted by static compilation or a task run.
 * It deliberately contains only stable resource identities and never SQL text or data values.
 */
public record TaskLineageEvidence(
        AnalysisStatus analysisStatus,
        Coverage coverage,
        List<Flow> flows,
        List<Warning> warnings
) {
    public TaskLineageEvidence {
        flows = flows == null ? List.of() : List.copyOf(flows);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public static TaskLineageEvidence unavailable(String code, String message) {
        return new TaskLineageEvidence(
                AnalysisStatus.UNAVAILABLE,
                null,
                List.of(),
                List.of(new Warning(code, message, null, null, null))
        );
    }

    public enum AnalysisStatus { COMPLETE, PARTIAL, UNAVAILABLE }
    public enum Coverage { MODEL_ONLY, FIELD_PARTIAL, FIELD_COMPLETE }
    public enum AssetRole { INPUT, OUTPUT }
    public enum AssetKind { MODEL, JDBC_TABLE, EXTERNAL_RESOURCE }
    public enum ExternalResourceType {
        KAFKA_TOPIC,
        FILE_DATASET_TABLE,
        HTTP_API_RESOURCE,
        SPATIAL_SERVICE_RESOURCE,
        OBJECT_STORAGE_PATH,
        JDBC_QUERY_RESULT
    }
    public enum WriteMode { APPEND, FULL_OVERWRITE, UPSERT, SNAPSHOT_SYNC, CREATE_NEW }
    public enum OutputEffect {
        DERIVED,
        WRITTEN_UNKNOWN_SOURCE,
        CONSTANT,
        DEFAULT_VALUE,
        NULL_FILLED,
        PRESERVED,
        NOT_WRITTEN
    }
    public enum DerivationType { DIRECT, CALCULATED, AGGREGATED }
    public enum UsageType { JOIN_KEY, FILTER_CONDITION, GROUP_KEY, SORT_KEY, PARTITION_KEY }

    public record Flow(
            String flowKey,
            String producerKey,
            String producerType,
            Coverage coverage,
            Asset outputAsset,
            List<Asset> inputAssets,
            List<Field> fields,
            List<FieldEdge> fieldEdges,
            List<FieldUsage> fieldUsages,
            List<Warning> warnings
    ) {
        public Flow {
            inputAssets = inputAssets == null ? List.of() : List.copyOf(inputAssets);
            fields = fields == null ? List.of() : List.copyOf(fields);
            fieldEdges = fieldEdges == null ? List.of() : List.copyOf(fieldEdges);
            fieldUsages = fieldUsages == null ? List.of() : List.copyOf(fieldUsages);
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
        }
    }

    public record Asset(
            String localAssetKey,
            AssetRole role,
            AssetKind kind,
            ExternalResourceType externalResourceType,
            WriteMode writeMode,
            UUID modelId,
            Integer modelSchemaVersion,
            UUID dataSourceId,
            String catalogName,
            String schemaName,
            String physicalTableName,
            UUID resourceId,
            String resourceKeyHash,
            String safeDisplayName
    ) {
    }

    public record Field(
            String localAssetKey,
            String localFieldKey,
            UUID modelFieldId,
            String columnCode,
            String columnName,
            int ordinal,
            OutputEffect outputEffect
    ) {
    }

    public record FieldReference(String localAssetKey, String localFieldKey) {
    }

    public record FieldEdge(
            FieldReference source,
            FieldReference target,
            String derivationKey,
            DerivationType derivationType,
            String transformNodeKey
    ) {
    }

    public record FieldUsage(FieldReference field, String nodeKey, UsageType usageType) {
    }

    public record Warning(
            String code,
            String message,
            String producerKey,
            String flowKey,
            Integer outputOrdinal
    ) {
    }
}
