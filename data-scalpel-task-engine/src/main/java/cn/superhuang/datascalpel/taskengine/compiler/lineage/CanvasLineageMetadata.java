package cn.superhuang.datascalpel.taskengine.compiler.lineage;

import cn.superhuang.data.scalpel.contract.task.CanvasLineageCompilation;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasTableOrigin;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.JdbcQueryInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.MetadataModelField;
import cn.superhuang.data.scalpel.contract.task.TaskLineageEvidence;
import cn.superhuang.datascalpel.taskengine.compiler.MetadataIndex;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.functions;
import org.apache.spark.sql.types.Metadata;
import org.apache.spark.sql.types.MetadataBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Injects compiler-only source identity into Catalyst attributes. */
public final class CanvasLineageMetadata {
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

    private CanvasLineageMetadata() {
    }

    public static SparkCanvasTable markInput(
            CanvasNodeDefinition node,
            SparkCanvasTable table,
            MetadataIndex metadataIndex
    ) {
        CanvasLineageCompilation.Asset asset = inputAsset(node, table.schema(), metadataIndex);
        Map<String, MetadataModelField> modelFields = asset.kind() == CanvasLineageCompilation.AssetKind.MODEL
                ? metadataIndex.model(asset.modelId()).metadata().fields().stream()
                .collect(Collectors.toMap(MetadataModelField::code, Function.identity()))
                : Map.of();
        Map<String, CatalystLineageMetadata.InputField> fields = modelFields.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> new CatalystLineageMetadata.InputField(
                                "model-field:" + entry.getValue().id(), entry.getValue().id()),
                        (left, right) -> left,
                        java.util.LinkedHashMap::new
                ));
        TaskLineageEvidence.Asset genericAsset = new TaskLineageEvidence.Asset(
                asset.localAssetKey(), TaskLineageEvidence.AssetRole.INPUT,
                TaskLineageEvidence.AssetKind.valueOf(asset.kind().name()),
                asset.externalResourceType() == null ? null
                        : TaskLineageEvidence.ExternalResourceType.valueOf(asset.externalResourceType().name()),
                null, asset.modelId(), asset.modelSchemaVersion(), asset.dataSourceId(),
                asset.catalogName(), asset.schemaName(), asset.physicalTableName(),
                asset.resourceId(), asset.resourceKeyHash(), asset.safeDisplayName());
        return new SparkCanvasTable(table.schema(), CatalystLineageMetadata.markInput(
                table.dataset(), node.id(), genericAsset, fields));
    }

    public static SparkCanvasTable markBoundary(
            CanvasNodeDefinition node,
            SparkCanvasTable table
    ) {
        return new SparkCanvasTable(table.schema(), CatalystLineageMetadata.markBoundary(
                table.dataset(), node.id(), node.nodeType().name()));
    }

    public static CanvasLineageCompilation.Asset readAsset(Metadata metadata, String localAssetKey) {
        CanvasLineageCompilation.AssetKind kind = CanvasLineageCompilation.AssetKind.valueOf(
                metadata.getString(ASSET_KIND));
        return new CanvasLineageCompilation.Asset(
                localAssetKey, CanvasLineageCompilation.AssetRole.INPUT, kind,
                enumValue(metadata, EXTERNAL_RESOURCE_TYPE, CanvasLineageCompilation.ExternalResourceType.class),
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

    public static String boundaryNodeId(Metadata metadata) {
        return metadata.getString(BOUNDARY_NODE_ID);
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

    private static CanvasLineageCompilation.Asset inputAsset(
            CanvasNodeDefinition node,
            CanvasTableSchema schema,
            MetadataIndex metadataIndex
    ) {
        CanvasTableOrigin origin = schema.origin();
        if (origin == null) throw new IllegalArgumentException("Input lineage origin is missing: " + node.id());
        String localAssetKey = inputAssetKey(node, schema);
        return switch (origin.kind()) {
            case "MODEL" -> {
                var model = metadataIndex.model(origin.modelId()).metadata();
                yield new CanvasLineageCompilation.Asset(
                        localAssetKey, CanvasLineageCompilation.AssetRole.INPUT,
                        CanvasLineageCompilation.AssetKind.MODEL, null, null,
                        model.id(), model.schemaVersion(),
                        null, null, null, null,
                        null, null, model.name());
            }
            case "JDBC", "JDBC_INCREMENTAL" -> {
                var table = metadataIndex.dataSource(origin.dataSourceId()).table(origin.tableName());
                yield new CanvasLineageCompilation.Asset(
                        localAssetKey, CanvasLineageCompilation.AssetRole.INPUT,
                        CanvasLineageCompilation.AssetKind.JDBC_TABLE, null, null,
                        null, null, origin.dataSourceId(), table.catalogName(), table.schemaName(),
                        table.physicalTableName(), null, null, table.tableName());
            }
            case "TDENGINE_TMQ" -> new CanvasLineageCompilation.Asset(
                    localAssetKey, CanvasLineageCompilation.AssetRole.INPUT,
                    CanvasLineageCompilation.AssetKind.JDBC_TABLE, null, null,
                    null, null, origin.dataSourceId(), origin.catalogName(), null,
                    origin.supertableName(), null, null, origin.supertableName());
            case "JDBC_QUERY" -> external(localAssetKey, origin.dataSourceId(), null,
                    CanvasLineageCompilation.ExternalResourceType.JDBC_QUERY_RESULT,
                    jdbcQueryResourceKey(node), node.name());
            case "KAFKA" -> external(localAssetKey, origin.dataSourceId(), null,
                    CanvasLineageCompilation.ExternalResourceType.KAFKA_TOPIC,
                    sha256(origin.tableName()), origin.tableName());
            case "FILE_DATASET" -> external(localAssetKey, null, origin.fileDatasetTableId(),
                    CanvasLineageCompilation.ExternalResourceType.FILE_DATASET_TABLE,
                    sha256(origin.fileDatasetTableId().toString()), node.name());
            case "HTTP_API" -> external(localAssetKey, origin.dataSourceId(), uuid(origin.tableName()),
                    CanvasLineageCompilation.ExternalResourceType.HTTP_API_RESOURCE,
                    sha256(origin.tableName()), node.name());
            case "SPATIAL_SERVICE" -> external(localAssetKey, origin.dataSourceId(), uuid(origin.tableName()),
                    CanvasLineageCompilation.ExternalResourceType.SPATIAL_SERVICE_RESOURCE,
                    sha256(origin.tableName()), node.name());
            default -> throw new IllegalArgumentException("Unsupported input lineage origin: " + origin.kind());
        };
    }

    private static String inputAssetKey(CanvasNodeDefinition node, CanvasTableSchema schema) {
        String nodeKey = "input:" + node.id();
        CanvasTableOrigin origin = schema.origin();
        if (origin == null) return nodeKey;
        // 一个 Input 节点可以产生多张表。资源 Origin 是稳定来源身份，不能让它们共享同一血缘资产。
        String originKey = String.join("\0",
                origin.kind(), String.valueOf(origin.dataSourceId()), String.valueOf(origin.tableName()),
                String.valueOf(origin.modelId()), String.valueOf(origin.fileDatasetTableId()),
                String.valueOf(origin.topicName()), String.valueOf(origin.catalogName()),
                String.valueOf(origin.supertableName()));
        return nodeKey + ":origin:" + sha256(originKey);
    }

    private static CanvasLineageCompilation.Asset external(
            String localAssetKey,
            UUID dataSourceId,
            UUID resourceId,
            CanvasLineageCompilation.ExternalResourceType type,
            String resourceKey,
            String name
    ) {
        return new CanvasLineageCompilation.Asset(
                localAssetKey, CanvasLineageCompilation.AssetRole.INPUT,
                CanvasLineageCompilation.AssetKind.EXTERNAL_RESOURCE, type, null,
                null, null, dataSourceId, null, null, null,
                resourceId, resourceKey, name);
    }

    private static String jdbcQueryResourceKey(CanvasNodeDefinition node) {
        if (node instanceof JdbcQueryInputNodeDefinition query
                && query.configuration() != null
                && query.configuration().analyzedSqlSha256() != null
                && !query.configuration().analyzedSqlSha256().isBlank()) {
            return query.configuration().analyzedSqlSha256();
        }
        return sha256(node.id());
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

    private static UUID uuid(String value) {
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

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
