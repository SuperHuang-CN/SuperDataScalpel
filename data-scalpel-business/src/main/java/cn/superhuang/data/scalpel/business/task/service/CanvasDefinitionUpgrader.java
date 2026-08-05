package cn.superhuang.data.scalpel.business.task.service;

import cn.superhuang.data.scalpel.contract.task.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Validates Canvas protocol compatibility and normalizes supported definitions to the current writer version. */
@Component
public class CanvasDefinitionUpgrader {

    public void requireSupportedSource(CanvasDefinition definition) {
        if (definition.schemaVersion() == null
                || definition.schemaVersion() != CanvasDefinition.CURRENT_SCHEMA_VERSION) {
            invalid("Canvas schemaVersion 仅支持 " + CanvasDefinition.CURRENT_SCHEMA_VERSION);
        }
        int schemaMinorVersion = definition.effectiveSchemaMinorVersion();
        if (schemaMinorVersion < CanvasDefinition.LEGACY_SCHEMA_MINOR_VERSION
                || schemaMinorVersion > CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION) {
            invalid("Canvas schemaMinorVersion 仅支持 "
                    + CanvasDefinition.LEGACY_SCHEMA_MINOR_VERSION + " 到 "
                    + CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION);
        }
        if (schemaMinorVersion == CanvasDefinition.LEGACY_SCHEMA_MINOR_VERSION
                && definition.nodes() != null
                && definition.nodes().stream().anyMatch(CanvasDefinitionUpgrader::isModelNode)) {
            invalid("MODEL_INPUT 和 MODEL_OUTPUT 从 Canvas 1.1 开始支持");
        }
        if (schemaMinorVersion < 2
                && definition.nodes() != null
                && definition.nodes().stream().anyMatch(CanvasDefinitionUpgrader::isRenameNode)) {
            invalid("RENAME 从 Canvas 1.2 开始支持");
        }
        if (schemaMinorVersion < 3
                && definition.nodes() != null
                && definition.nodes().stream().anyMatch(CanvasDefinitionUpgrader::isStreamJoinNode)) {
            invalid("STREAM_JOIN 从 Canvas 1.3 开始支持");
        }
        if (schemaMinorVersion < 4
                && definition.nodes() != null
                && definition.nodes().stream().anyMatch(CanvasDefinitionUpgrader::isFileDatasetNode)) {
            invalid("FILE_DATASET_INPUT 从 Canvas 1.4 开始支持");
        }
        if (schemaMinorVersion < 5
                && definition.nodes() != null
                && definition.nodes().stream().anyMatch(CanvasDefinitionUpgrader::isKafkaNode)) {
            invalid("KAFKA_INPUT 和 KAFKA_OUTPUT 的内联 Value Schema 从 Canvas 1.5 开始支持");
        }
        if (schemaMinorVersion < 6
                && definition.nodes() != null
                && definition.nodes().stream().anyMatch(CanvasDefinitionUpgrader::isFileOutputNode)) {
            invalid("FILE_OUTPUT 从 Canvas 1.6 开始支持");
        }
        if (schemaMinorVersion < 7
                && definition.nodes() != null
                && definition.nodes().stream().anyMatch(CanvasDefinitionUpgrader::isFilterNode)) {
            invalid("FILTER 从 Canvas 1.7 开始支持");
        }
        if (schemaMinorVersion < 8
                && definition.nodes() != null
                && definition.nodes().stream().anyMatch(CanvasDefinitionUpgrader::isSelectColumnsNode)) {
            invalid("SELECT_COLUMNS 从 Canvas 1.8 开始支持");
        }
        if (schemaMinorVersion < 9
                && definition.nodes() != null
                && definition.nodes().stream().anyMatch(CanvasDefinitionUpgrader::isDeriveColumnsNode)) {
            invalid("DERIVE_COLUMNS 从 Canvas 1.9 开始支持");
        }
        if (schemaMinorVersion < 10
                && definition.nodes() != null
                && definition.nodes().stream().anyMatch(CanvasDefinitionUpgrader::isTypeCastNode)) {
            invalid("TYPE_CAST 从 Canvas 1.10 开始支持");
        }
        if (schemaMinorVersion < 11
                && definition.nodes() != null
                && definition.nodes().stream().anyMatch(CanvasDefinitionUpgrader::isAggregateNode)) {
            invalid("AGGREGATE 从 Canvas 1.11 开始支持");
        }
        if (schemaMinorVersion < 12
                && definition.nodes() != null
                && definition.nodes().stream().anyMatch(CanvasDefinitionUpgrader::isUnionNode)) {
            invalid("UNION 从 Canvas 1.12 开始支持");
        }
        if (schemaMinorVersion < 13
                && definition.nodes() != null
                && definition.nodes().stream().anyMatch(CanvasDefinitionUpgrader::isDeduplicateNode)) {
            invalid("DEDUPLICATE 从 Canvas 1.13 开始支持");
        }
        if (schemaMinorVersion < 14
                && definition.nodes() != null
                && definition.nodes().stream().anyMatch(CanvasDefinitionUpgrader::isNullHandlingNode)) {
            invalid("NULL_HANDLING 从 Canvas 1.14 开始支持");
        }
        if (schemaMinorVersion < 15
                && definition.nodes() != null
                && definition.nodes().stream().anyMatch(CanvasDefinitionUpgrader::isValueMappingNode)) {
            invalid("VALUE_MAPPING 从 Canvas 1.15 开始支持");
        }
        if (schemaMinorVersion < 16
                && definition.nodes() != null
                && definition.nodes().stream().anyMatch(CanvasDefinitionUpgrader::isWindowNode)) {
            invalid("WINDOW 从 Canvas 1.16 开始支持");
        }
        if (schemaMinorVersion < 17
                && definition.nodes() != null
                && definition.nodes().stream().anyMatch(CanvasDefinitionUpgrader::isTopNNode)) {
            invalid("TOP_N 从 Canvas 1.17 开始支持");
        }
        if (schemaMinorVersion < 18
                && definition.nodes() != null
                && definition.nodes().stream().anyMatch(CanvasDefinitionUpgrader::isMaskFieldsNode)) {
            invalid("MASK_FIELDS 从 Canvas 1.18 开始支持");
        }
        if (schemaMinorVersion < 19
                && definition.nodes() != null
                && definition.nodes().stream().anyMatch(CanvasDefinitionUpgrader::isJsonExtractNode)) {
            invalid("JSON_EXTRACT 从 Canvas 1.19 开始支持");
        }
        if (schemaMinorVersion < 20
                && definition.nodes() != null
                && definition.nodes().stream().anyMatch(CanvasDefinitionUpgrader::isSpatialNode)) {
            invalid("SPATIAL_TRANSFORM 和 SPATIAL_JOIN 从 Canvas 1.20 开始支持");
        }
        if (schemaMinorVersion < 21
                && definition.nodes() != null
                && definition.nodes().stream().anyMatch(CanvasDefinitionUpgrader::isSpatialFoundationNode)) {
            invalid("GEOMETRY_CONSTRUCT、GEOMETRY_VALIDATE、SPATIAL_MEASURE 和 "
                    + "GEOMETRY_SERIALIZE 从 Canvas 1.21 开始支持");
        }
        if (schemaMinorVersion < 22
                && definition.nodes() != null
                && definition.nodes().stream().anyMatch(CanvasDefinitionUpgrader::isSpatialEnrichmentNode)) {
            invalid("GEOMETRY_REPAIR、GEOMETRY_BUFFER 和 GEOMETRY_EXPLODE "
                    + "从 Canvas 1.22 开始支持");
        }
        if (schemaMinorVersion < 23
                && definition.nodes() != null
                && definition.nodes().stream().anyMatch(CanvasDefinitionUpgrader::isSpatialAnalysisNode)) {
            invalid("SPATIAL_CLIP 和 SPATIAL_AGGREGATE 从 Canvas 1.23 开始支持");
        }
        if (schemaMinorVersion < 24
                && definition.nodes() != null
                && definition.nodes().stream().anyMatch(CanvasDefinitionUpgrader::isShapefileOutput)) {
            invalid("SHAPEFILE 文件输出从 Canvas 1.24 开始支持");
        }
        if (schemaMinorVersion < 24
                && definition.nodes() != null
                && definition.nodes().stream().anyMatch(CanvasDefinitionUpgrader::isJdbcQueryInput)) {
            invalid("JDBC_QUERY_INPUT 从 Canvas 1.24 开始支持");
        }
        if (schemaMinorVersion < 24
                && definition.nodes() != null
                && definition.nodes().stream().anyMatch(CanvasDefinitionUpgrader::isJdbcOutputUpsert)) {
            invalid("JDBC_OUTPUT UPSERT 从 Canvas 1.24 开始支持");
        }
        if (schemaMinorVersion < 25
                && definition.nodes() != null
                && definition.nodes().stream().anyMatch(CanvasDefinitionUpgrader::isGeoParquetOutput)) {
            invalid("GEOPARQUET 文件输出从 Canvas 1.25 开始支持");
        }
        if (schemaMinorVersion < 25
                && definition.nodes() != null
                && definition.nodes().stream().anyMatch(CanvasDefinitionUpgrader::isGeoJsonOutput)) {
            invalid("GEOJSON 文件输出从 Canvas 1.25 开始支持");
        }
        if (schemaMinorVersion < 26
                && definition.nodes() != null
                && definition.nodes().stream().anyMatch(node -> node.nodeType() == CanvasNodeType.SPATIAL_SERVICE_INPUT)) {
            invalid("SPATIAL_SERVICE_INPUT 从 Canvas 1.26 开始支持");
        }
    }

    public CanvasDefinition upgradeToCurrent(CanvasDefinition definition) {
        requireSupportedSource(definition);
        if (definition.effectiveSchemaMinorVersion() == CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION) {
            return definition;
        }
        return new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION,
                definition.nodes(),
                definition.edges()
        );
    }

    private static boolean isModelNode(CanvasNodeDefinition node) {
        return node instanceof ModelInputNodeDefinition
                || node instanceof ModelOutputNodeDefinition;
    }

    private static boolean isRenameNode(CanvasNodeDefinition node) {
        return node instanceof RenameNodeDefinition;
    }

    private static boolean isStreamJoinNode(CanvasNodeDefinition node) {
        return node instanceof StreamJoinNodeDefinition;
    }

    private static boolean isFileDatasetNode(CanvasNodeDefinition node) {
        return node instanceof FileDatasetInputNodeDefinition;
    }

    private static boolean isKafkaNode(CanvasNodeDefinition node) {
        return node instanceof KafkaInputNodeDefinition
                || node instanceof KafkaOutputNodeDefinition;
    }

    private static boolean isFileOutputNode(CanvasNodeDefinition node) {
        return node instanceof FileOutputNodeDefinition;
    }

    private static boolean isFilterNode(CanvasNodeDefinition node) {
        return node instanceof FilterNodeDefinition;
    }

    private static boolean isSelectColumnsNode(CanvasNodeDefinition node) {
        return node instanceof SelectColumnsNodeDefinition;
    }

    private static boolean isDeriveColumnsNode(CanvasNodeDefinition node) {
        return node instanceof DeriveColumnsNodeDefinition;
    }

    private static boolean isTypeCastNode(CanvasNodeDefinition node) {
        return node instanceof TypeCastNodeDefinition;
    }

    private static boolean isAggregateNode(CanvasNodeDefinition node) {
        return node instanceof AggregateNodeDefinition;
    }

    private static boolean isUnionNode(CanvasNodeDefinition node) {
        return node instanceof UnionNodeDefinition;
    }

    private static boolean isDeduplicateNode(CanvasNodeDefinition node) {
        return node instanceof DeduplicateNodeDefinition;
    }

    private static boolean isNullHandlingNode(CanvasNodeDefinition node) {
        return node instanceof NullHandlingNodeDefinition;
    }

    private static boolean isValueMappingNode(CanvasNodeDefinition node) {
        return node instanceof ValueMappingNodeDefinition;
    }

    private static boolean isWindowNode(CanvasNodeDefinition node) {
        return node instanceof WindowNodeDefinition;
    }

    private static boolean isTopNNode(CanvasNodeDefinition node) {
        return node instanceof TopNNodeDefinition;
    }

    private static boolean isMaskFieldsNode(CanvasNodeDefinition node) {
        return node instanceof MaskFieldsNodeDefinition;
    }

    private static boolean isJsonExtractNode(CanvasNodeDefinition node) {
        return node instanceof JsonExtractNodeDefinition;
    }

    private static boolean isSpatialNode(CanvasNodeDefinition node) {
        return node instanceof SpatialTransformNodeDefinition
                || node instanceof SpatialJoinNodeDefinition;
    }

    private static boolean isSpatialFoundationNode(CanvasNodeDefinition node) {
        return node instanceof GeometryConstructNodeDefinition
                || node instanceof GeometryValidateNodeDefinition
                || node instanceof SpatialMeasureNodeDefinition
                || node instanceof GeometrySerializeNodeDefinition;
    }

    private static boolean isSpatialEnrichmentNode(CanvasNodeDefinition node) {
        return node instanceof GeometryRepairNodeDefinition
                || node instanceof GeometryBufferNodeDefinition
                || node instanceof GeometryExplodeNodeDefinition;
    }

    private static boolean isSpatialAnalysisNode(CanvasNodeDefinition node) {
        return node instanceof SpatialClipNodeDefinition
                || node instanceof SpatialAggregateNodeDefinition;
    }

    private static boolean isShapefileOutput(CanvasNodeDefinition node) {
        return node instanceof FileOutputNodeDefinition output
                && output.configuration() != null
                && output.configuration().formatOptions() instanceof FileOutputFormatOptions.Shapefile;
    }

    private static boolean isJdbcQueryInput(CanvasNodeDefinition node) {
        return node instanceof JdbcQueryInputNodeDefinition;
    }

    private static boolean isJdbcOutputUpsert(CanvasNodeDefinition node) {
        return node instanceof JdbcOutputNodeDefinition output
                && output.configuration() != null
                && output.configuration().writeMode() == JdbcWriteMode.UPSERT;
    }

    private static boolean isGeoParquetOutput(CanvasNodeDefinition node) {
        return node instanceof FileOutputNodeDefinition output
                && output.configuration() != null
                && output.configuration().formatOptions() instanceof FileOutputFormatOptions.GeoParquet;
    }

    private static boolean isGeoJsonOutput(CanvasNodeDefinition node) {
        return node instanceof FileOutputNodeDefinition output
                && output.configuration() != null
                && output.configuration().formatOptions() instanceof FileOutputFormatOptions.GeoJson;
    }

    private static void invalid(String message) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
