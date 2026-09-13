package cn.superhuang.datascalpel.taskengine.compiler.lineage;

import cn.superhuang.data.scalpel.contract.task.TaskLineageEvidence;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.types.Metadata;
import org.apache.spark.sql.types.MetadataBuilder;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Stable metadata codec shared by Canvas compilation and Spark JAR runtime analysis. */
public final class CatalystLineageMetadata {
    public static final String PREFIX = "datascalpel.lineage.";
    public static final String MARKER_VERSION = PREFIX + "markerVersion";
    public static final String INPUT_NODE_ID = PREFIX + "inputNodeId";
    public static final String INPUT_ASSET_KEY = PREFIX + "inputAssetKey";
    public static final String ASSET_KIND = PREFIX + "assetKind";
    public static final String EXTERNAL_RESOURCE_TYPE = PREFIX + "externalResourceType";
    public static final String MODEL_ID = PREFIX + "modelId";
    public static final String MODEL_SCHEMA_VERSION = PREFIX + "modelSchemaVersion";
    public static final String MODEL_FIELD_ID = PREFIX + "modelFieldId";
    public static final String DATA_SOURCE_ID = PREFIX + "dataSourceId";
    public static final String RESOURCE_ID = PREFIX + "resourceId";
    public static final String RESOURCE_KEY_HASH = PREFIX + "resourceKeyHash";
    public static final String CATALOG_NAME = PREFIX + "catalogName";
    public static final String SCHEMA_NAME = PREFIX + "schemaName";
    public static final String PHYSICAL_TABLE_NAME = PREFIX + "physicalTableName";
    public static final String COLUMN_KEY = PREFIX + "columnKey";
    public static final String DISPLAY_NAME = PREFIX + "displayName";
    public static final String BOUNDARY_NODE_ID = PREFIX + "boundaryNodeId";
    public static final String BOUNDARY_NODE_TYPE = PREFIX + "boundaryNodeType";
    public static final String TECHNICAL_COLUMN = PREFIX + "technicalColumn";
    public static final String ROW_PRESERVING_OPAQUE_TRANSFORM = PREFIX + "rowPreservingOpaqueTransform";
    public static final String OPAQUE_DERIVED_SOURCE_COLUMNS = PREFIX + "opaqueDerivedSourceColumns";

    private CatalystLineageMetadata() {
    }

    public static Dataset<Row> markInput(
            Dataset<Row> source,
            String producerKey,
            TaskLineageEvidence.Asset asset,
            Map<String, InputField> fields
    ) {
        Column[] columns = Arrays.stream(source.schema().fields()).map(field -> {
            InputField identity = fields.get(field.name());
            String fieldKey = identity == null
                    ? defaultFieldKey(asset.kind(), field.name())
                    : identity.localFieldKey();
            MetadataBuilder builder = new MetadataBuilder()
                    .withMetadata(field.metadata())
                    .putLong(MARKER_VERSION, 1)
                    .putString(INPUT_NODE_ID, producerKey)
                    .putString(INPUT_ASSET_KEY, asset.localAssetKey())
                    .putString(ASSET_KIND, asset.kind().name())
                    .putString(COLUMN_KEY, fieldKey)
                    .putString(DISPLAY_NAME, asset.safeDisplayName());
            put(builder, EXTERNAL_RESOURCE_TYPE, asset.externalResourceType());
            put(builder, MODEL_ID, asset.modelId());
            if (asset.modelSchemaVersion() != null) {
                builder.putLong(MODEL_SCHEMA_VERSION, asset.modelSchemaVersion());
            }
            put(builder, MODEL_FIELD_ID, identity == null ? null : identity.modelFieldId());
            put(builder, DATA_SOURCE_ID, asset.dataSourceId());
            put(builder, RESOURCE_ID, asset.resourceId());
            put(builder, RESOURCE_KEY_HASH, asset.resourceKeyHash());
            put(builder, CATALOG_NAME, asset.catalogName());
            put(builder, SCHEMA_NAME, asset.schemaName());
            put(builder, PHYSICAL_TABLE_NAME, asset.physicalTableName());
            return source.col(quote(field.name())).as(field.name(), builder.build());
        }).toArray(Column[]::new);
        return source.select(columns);
    }

    public static Dataset<Row> markBoundary(
            Dataset<Row> source,
            String nodeId,
            String nodeType
    ) {
        Column[] columns = Arrays.stream(source.schema().fields())
                .map(field -> source.col(quote(field.name())).as(
                        field.name(),
                        new MetadataBuilder()
                                .putLong(MARKER_VERSION, 1)
                                .putString(BOUNDARY_NODE_ID, nodeId)
                                .putString(BOUNDARY_NODE_TYPE, nodeType)
                                .build()
                )).toArray(Column[]::new);
        return source.select(columns);
    }

    /**
     * Marks a plan-local helper column that exists only to implement an operator and is
     * removed before the logical Canvas table is exposed. Such a column is not an unknown
     * physical source field and must not reduce field-lineage coverage when used as an
     * internal grouping or equality-join key.
     */
    public static Column markTechnicalColumn(Column expression, String name) {
        return expression.as(name, new MetadataBuilder()
                .putLong(MARKER_VERSION, 1)
                .putBoolean(TECHNICAL_COLUMN, true)
                .build());
    }

    /**
     * Marks a deliberately opaque row transform whose existing columns are copied without
     * changing their values. Appended columns must list the input columns that determine
     * them. The lineage analyzer only trusts this explicit boundary; arbitrary map/mapPartitions
     * plans remain unsupported and partial.
     */
    public static Dataset<Row> markRowPreservingOpaqueTransform(
            Dataset<Row> output,
            Map<String, List<String>> derivedSourceColumns
    ) {
        Column[] columns = Arrays.stream(output.schema().fields()).map(field -> {
            MetadataBuilder builder = new MetadataBuilder()
                    .withMetadata(field.metadata())
                    .putLong(MARKER_VERSION, 1)
                    .putBoolean(ROW_PRESERVING_OPAQUE_TRANSFORM, true);
            List<String> sources = derivedSourceColumns.get(field.name());
            if (sources != null) {
                builder.putStringArray(OPAQUE_DERIVED_SOURCE_COLUMNS, sources.toArray(String[]::new));
            }
            return output.col(quote(field.name())).as(field.name(), builder.build());
        }).toArray(Column[]::new);
        return output.select(columns);
    }

    public static TaskLineageEvidence.Asset readAsset(Metadata metadata, String localAssetKey) {
        TaskLineageEvidence.AssetKind kind = TaskLineageEvidence.AssetKind.valueOf(
                metadata.getString(ASSET_KIND));
        return new TaskLineageEvidence.Asset(
                localAssetKey, TaskLineageEvidence.AssetRole.INPUT, kind,
                enumValue(metadata, EXTERNAL_RESOURCE_TYPE, TaskLineageEvidence.ExternalResourceType.class),
                null, uuid(metadata, MODEL_ID), integer(metadata, MODEL_SCHEMA_VERSION),
                uuid(metadata, DATA_SOURCE_ID), string(metadata, CATALOG_NAME),
                string(metadata, SCHEMA_NAME), string(metadata, PHYSICAL_TABLE_NAME),
                uuid(metadata, RESOURCE_ID), string(metadata, RESOURCE_KEY_HASH),
                metadata.getString(DISPLAY_NAME)
        );
    }

    public static boolean isInput(Metadata metadata) {
        return metadata != null && metadata.contains(MARKER_VERSION) && metadata.contains(INPUT_NODE_ID);
    }

    public static boolean isBoundary(Metadata metadata) {
        return metadata != null && metadata.contains(MARKER_VERSION)
                && metadata.contains(BOUNDARY_NODE_ID);
    }

    public static boolean isTechnicalColumn(Metadata metadata) {
        return metadata != null && metadata.contains(MARKER_VERSION)
                && metadata.contains(TECHNICAL_COLUMN)
                && metadata.getBoolean(TECHNICAL_COLUMN);
    }

    public static boolean isRowPreservingOpaqueTransform(Metadata metadata) {
        return metadata != null && metadata.contains(MARKER_VERSION)
                && metadata.contains(ROW_PRESERVING_OPAQUE_TRANSFORM)
                && metadata.getBoolean(ROW_PRESERVING_OPAQUE_TRANSFORM);
    }

    public static String boundaryNodeKey(Metadata metadata) {
        return "canvas:node:" + metadata.getString(BOUNDARY_NODE_ID);
    }

    public static String inputNodeId(Metadata metadata) {
        return metadata.getString(INPUT_NODE_ID);
    }

    public static String inputAssetKey(Metadata metadata) {
        return metadata.contains(INPUT_ASSET_KEY)
                ? metadata.getString(INPUT_ASSET_KEY)
                : "input:" + inputNodeId(metadata);
    }

    public static String columnKey(Metadata metadata) {
        return metadata.getString(COLUMN_KEY);
    }

    public static UUID modelFieldId(Metadata metadata) {
        return uuid(metadata, MODEL_FIELD_ID);
    }

    private static String defaultFieldKey(TaskLineageEvidence.AssetKind kind, String column) {
        return switch (kind) {
            case MODEL -> "model-code:" + column;
            case JDBC_TABLE -> "jdbc-column:" + LineageHash.sha256(column);
            case EXTERNAL_RESOURCE -> "external-field:" + LineageHash.sha256(column);
        };
    }

    private static void put(MetadataBuilder builder, String key, Object value) {
        if (value != null) builder.putString(key, value.toString());
    }

    private static String quote(String value) {
        return "`" + value.replace("`", "``") + "`";
    }

    private static UUID uuid(Metadata metadata, String key) {
        String value = string(metadata, key);
        return value == null ? null : UUID.fromString(value);
    }

    private static Integer integer(Metadata metadata, String key) {
        return metadata.contains(key) ? Math.toIntExact(metadata.getLong(key)) : null;
    }

    private static String string(Metadata metadata, String key) {
        return metadata.contains(key) ? metadata.getString(key) : null;
    }

    private static <E extends Enum<E>> E enumValue(Metadata metadata, String key, Class<E> type) {
        String value = string(metadata, key);
        return value == null ? null : Enum.valueOf(type, value);
    }

    public record InputField(String localFieldKey, UUID modelFieldId) {
    }
}
