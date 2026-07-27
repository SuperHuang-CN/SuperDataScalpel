package cn.superhuang.data.scalpel.business.task.canvas;

import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.List;
import cn.superhuang.data.scalpel.contract.httpapi.HttpApiContracts;

/** Stable, X6-independent definition contract for one Spark Canvas task. */
public record CanvasDefinition(
        int schemaVersion,
        Integer schemaMinorVersion,
        List<CanvasNodeDefinition> nodes,
        List<CanvasEdgeDefinition> edges
) {

    public static final int CURRENT_SCHEMA_VERSION = 1;
    public static final int CURRENT_SCHEMA_MINOR_VERSION = 5;
    public static final int LEGACY_SCHEMA_MINOR_VERSION = 0;

    public CanvasDefinition {
        schemaMinorVersion = schemaMinorVersion == null
                ? LEGACY_SCHEMA_MINOR_VERSION
                : schemaMinorVersion;
    }

    public static CanvasDefinition empty() {
        return new CanvasDefinition(
                CURRENT_SCHEMA_VERSION,
                CURRENT_SCHEMA_MINOR_VERSION,
                List.of(),
                List.of()
        );
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = ModelInputNodeDefinition.class, name = "MODEL_INPUT"),
            @JsonSubTypes.Type(value = JdbcInputNodeDefinition.class, name = "JDBC_INPUT"),
            @JsonSubTypes.Type(value = FileDatasetInputNodeDefinition.class, name = "FILE_DATASET_INPUT"),
            @JsonSubTypes.Type(value = HttpApiInputNodeDefinition.class, name = "HTTP_API_INPUT"),
            @JsonSubTypes.Type(value = KafkaInputNodeDefinition.class, name = "KAFKA_INPUT"),
            @JsonSubTypes.Type(value = JoinNodeDefinition.class, name = "JOIN"),
            @JsonSubTypes.Type(value = StreamJoinNodeDefinition.class, name = "STREAM_JOIN"),
            @JsonSubTypes.Type(value = RenameNodeDefinition.class, name = "RENAME"),
            @JsonSubTypes.Type(value = ModelOutputNodeDefinition.class, name = "MODEL_OUTPUT"),
            @JsonSubTypes.Type(value = JdbcOutputNodeDefinition.class, name = "JDBC_OUTPUT"),
            @JsonSubTypes.Type(value = KafkaOutputNodeDefinition.class, name = "KAFKA_OUTPUT")
    })
    public sealed interface CanvasNodeDefinition
            permits ModelInputNodeDefinition, JdbcInputNodeDefinition, FileDatasetInputNodeDefinition,
                    HttpApiInputNodeDefinition,
                    KafkaInputNodeDefinition, JoinNodeDefinition, StreamJoinNodeDefinition,
                    RenameNodeDefinition, ModelOutputNodeDefinition, JdbcOutputNodeDefinition,
                    KafkaOutputNodeDefinition {

        String id();

        String name();

        CanvasNodeLayout layout();

        @JsonIgnore
        CanvasNodeType nodeType();
    }

    public enum CanvasNodeType {
        MODEL_INPUT,
        JDBC_INPUT,
        FILE_DATASET_INPUT,
        HTTP_API_INPUT,
        KAFKA_INPUT,
        JOIN,
        STREAM_JOIN,
        RENAME,
        MODEL_OUTPUT,
        JDBC_OUTPUT,
        KAFKA_OUTPUT
    }

    public record ModelInputNodeDefinition(
            String id,
            String name,
            CanvasNodeLayout layout,
            ModelInputConfiguration configuration
    ) implements CanvasNodeDefinition {
        @Override
        public CanvasNodeType nodeType() {
            return CanvasNodeType.MODEL_INPUT;
        }
    }

    public record JdbcInputNodeDefinition(
            String id,
            String name,
            CanvasNodeLayout layout,
            JdbcInputConfiguration configuration
    ) implements CanvasNodeDefinition {
        @Override
        public CanvasNodeType nodeType() {
            return CanvasNodeType.JDBC_INPUT;
        }
    }

    public record FileDatasetInputNodeDefinition(
            String id,
            String name,
            CanvasNodeLayout layout,
            FileDatasetInputConfiguration configuration
    ) implements CanvasNodeDefinition {
        @Override
        public CanvasNodeType nodeType() {
            return CanvasNodeType.FILE_DATASET_INPUT;
        }
    }

    public record HttpApiInputNodeDefinition(
            String id,
            String name,
            CanvasNodeLayout layout,
            HttpApiInputConfiguration configuration
    ) implements CanvasNodeDefinition {
        @Override
        public CanvasNodeType nodeType() {
            return CanvasNodeType.HTTP_API_INPUT;
        }
    }

    public record KafkaInputNodeDefinition(
            String id,
            String name,
            CanvasNodeLayout layout,
            KafkaInputConfiguration configuration
    ) implements CanvasNodeDefinition {
        @Override
        public CanvasNodeType nodeType() {
            return CanvasNodeType.KAFKA_INPUT;
        }
    }

    public record JoinNodeDefinition(
            String id,
            String name,
            CanvasNodeLayout layout,
            JoinConfiguration configuration
    ) implements CanvasNodeDefinition {
        @Override
        public CanvasNodeType nodeType() {
            return CanvasNodeType.JOIN;
        }
    }

    public record StreamJoinNodeDefinition(
            String id,
            String name,
            CanvasNodeLayout layout,
            StreamJoinConfiguration configuration
    ) implements CanvasNodeDefinition {
        @Override
        public CanvasNodeType nodeType() {
            return CanvasNodeType.STREAM_JOIN;
        }
    }

    public record RenameNodeDefinition(
            String id,
            String name,
            CanvasNodeLayout layout,
            RenameConfiguration configuration
    ) implements CanvasNodeDefinition {
        @Override
        public CanvasNodeType nodeType() {
            return CanvasNodeType.RENAME;
        }
    }

    public record JdbcOutputNodeDefinition(
            String id,
            String name,
            CanvasNodeLayout layout,
            JdbcOutputConfiguration configuration
    ) implements CanvasNodeDefinition {
        @Override
        public CanvasNodeType nodeType() {
            return CanvasNodeType.JDBC_OUTPUT;
        }
    }

    public record ModelOutputNodeDefinition(
            String id,
            String name,
            CanvasNodeLayout layout,
            ModelOutputConfiguration configuration
    ) implements CanvasNodeDefinition {
        @Override
        public CanvasNodeType nodeType() {
            return CanvasNodeType.MODEL_OUTPUT;
        }
    }

    public record KafkaOutputNodeDefinition(
            String id,
            String name,
            CanvasNodeLayout layout,
            KafkaOutputConfiguration configuration
    ) implements CanvasNodeDefinition {
        @Override
        public CanvasNodeType nodeType() {
            return CanvasNodeType.KAFKA_OUTPUT;
        }
    }

    public record CanvasNodeLayout(double x, double y, double width, double height) {
    }

    public record CanvasEdgeDefinition(String id, String sourceNodeId, String targetNodeId) {
    }

    public record JdbcInputConfiguration(String dataSourceId, String tableName) {
    }

    public record FileDatasetInputConfiguration(String fileDatasetTableId) {
    }

    public record HttpApiInputConfiguration(
            String dataSourceId,
            String resourceId,
            String outputTableName,
            List<HttpApiContracts.RuntimeParameter> runtimeParameters
    ) {
        public HttpApiInputConfiguration {
            runtimeParameters = runtimeParameters == null ? List.of() : List.copyOf(runtimeParameters);
        }
    }

    public record ModelInputConfiguration(String modelId) {
    }

    public record KafkaInputConfiguration(
            String dataSourceId,
            String topic,
            KafkaValueSchema valueSchema,
            String outputTableName,
            KafkaStartingOffsets startingOffsets
    ) {
    }

    public record KafkaValueSchema(List<KafkaValueColumn> columns) {
        public KafkaValueSchema {
            columns = columns == null ? List.of() : List.copyOf(columns);
        }
    }

    public record KafkaValueColumn(
            String name,
            PlatformDataType fieldType,
            Integer length,
            Integer precision,
            Integer scale,
            boolean nullable,
            String comment
    ) {
    }

    public enum KafkaStartingOffsets {
        EARLIEST,
        LATEST
    }

    public record JoinConfiguration(
            String leftTableName,
            String rightTableName,
            String outputTableName,
            JoinType joinType,
            List<JoinCondition> conditions
    ) {
    }

    public enum JoinType {
        INNER,
        LEFT,
        RIGHT,
        FULL
    }

    public record StreamJoinConfiguration(
            String leftTableName,
            String rightTableName,
            String outputTableName,
            StreamJoinType joinType,
            List<JoinCondition> conditions
    ) {
    }

    public enum StreamJoinType {
        INNER,
        LEFT
    }

    public record JoinCondition(String leftColumnName, JoinOperator operator, String rightColumnName) {
    }

    public enum JoinOperator {
        EQUALS
    }

    public record RenameConfiguration(
            String sourceTableName,
            String outputTableName,
            List<RenameColumnMapping> columnMappings
    ) {
    }

    public record RenameColumnMapping(String sourceColumnName, String targetColumnName) {
    }

    public record JdbcOutputConfiguration(
            String sourceTableName,
            String dataSourceId,
            String targetTableName,
            JdbcWriteMode writeMode,
            ColumnMappingMode columnMappingMode,
            List<JdbcColumnMapping> columnMappings
    ) {
    }

    public record ModelOutputConfiguration(
            String sourceTableName,
            String targetModelId,
            JdbcWriteMode writeMode,
            ColumnMappingMode columnMappingMode,
            List<JdbcColumnMapping> columnMappings
    ) {
    }

    public record KafkaOutputConfiguration(
            String sourceTableName,
            String dataSourceId,
            String topic,
            KafkaValueSchema valueSchema,
            String keyColumnName,
            ColumnMappingMode columnMappingMode,
            List<JdbcColumnMapping> columnMappings
    ) {
    }

    public enum JdbcWriteMode {
        APPEND,
        OVERWRITE
    }

    public enum ColumnMappingMode {
        BY_NAME,
        EXPLICIT
    }

    public record JdbcColumnMapping(String sourceColumnName, String targetColumnName) {
    }
}
