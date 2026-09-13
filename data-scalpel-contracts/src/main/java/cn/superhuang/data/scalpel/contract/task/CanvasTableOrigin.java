package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.UUID;

@JsonClassDescription("Canvas 逻辑表的来源定位快照；kind 决定数据源、模型、文件表、主题或外部资源字段的有效组合。")
public record CanvasTableOrigin(
        @JsonPropertyDescription("表来源种类的稳定标识，决定数据源、模型、文件表或其他资源定位字段如何解释。")
        String kind,
        @JsonPropertyDescription("数据源 UUID。")
        UUID dataSourceId,
        @JsonPropertyDescription("JDBC/JDBC_INCREMENTAL 的物理表名、JDBC_QUERY 的输出逻辑表名、HTTP_API/SPATIAL_SERVICE 的资源 UUID 字符串或 KAFKA/TDENGINE_TMQ 的来源表名；其他 kind 为空。")
        String tableName,
        @JsonPropertyDescription("模型 UUID。")
        UUID modelId,
        @JsonPropertyDescription("模型稳定编码。")
        String modelCode,
        @JsonPropertyDescription("生成该表来源元数据时捕获的模型字段结构版本，从 1 开始；仅 kind=MODEL 时有值，不保证仍是模型最新版本。")
        Integer modelSchemaVersion,
        @JsonPropertyDescription("文件数据表 UUID。")
        UUID fileDatasetTableId,
        @JsonPropertyDescription("Kafka 或 TDengine 主题名。")
        String topicName,
        @JsonPropertyDescription("数据库 Catalog；不适用时为空。")
        String catalogName,
        @JsonPropertyDescription("TDengine 超级表名。")
        String supertableName
) {
    public static CanvasTableOrigin jdbc(UUID dataSourceId, String tableName) {
        return new CanvasTableOrigin("JDBC", dataSourceId, tableName, null, null, null, null, null, null, null);
    }

    public static CanvasTableOrigin jdbcIncremental(UUID dataSourceId, String tableName) {
        return new CanvasTableOrigin(
                "JDBC_INCREMENTAL", dataSourceId, tableName,
                null, null, null, null, null, null, null
        );
    }

    public static CanvasTableOrigin jdbcQuery(UUID dataSourceId, String outputTableName) {
        return new CanvasTableOrigin("JDBC_QUERY", dataSourceId, outputTableName, null, null, null, null, null, null, null);
    }

    public static CanvasTableOrigin model(UUID modelId, String modelCode, int schemaVersion) {
        return new CanvasTableOrigin("MODEL", null, null, modelId, modelCode, schemaVersion, null, null, null, null);
    }

    public static CanvasTableOrigin httpApi(UUID dataSourceId, UUID resourceId) {
        return new CanvasTableOrigin("HTTP_API", dataSourceId, resourceId.toString(), null, null, null, null, null, null, null);
    }

    public static CanvasTableOrigin spatialService(UUID dataSourceId, UUID resourceId) {
        return new CanvasTableOrigin("SPATIAL_SERVICE", dataSourceId, resourceId.toString(), null, null, null, null, null, null, null);
    }

    public static CanvasTableOrigin kafka(UUID dataSourceId, String topic) {
        return new CanvasTableOrigin("KAFKA", dataSourceId, topic, null, null, null, null, null, null, null);
    }

    public static CanvasTableOrigin tdEngineTmq(
            UUID dataSourceId,
            String topicName,
            String catalogName,
            String supertableName
    ) {
        return new CanvasTableOrigin(
                "TDENGINE_TMQ", dataSourceId, supertableName, null, null, null, null,
                topicName, catalogName, supertableName
        );
    }

    public static CanvasTableOrigin fileDataset(UUID fileDatasetTableId) {
        return new CanvasTableOrigin("FILE_DATASET", null, null, null, null, null, fileDatasetTableId, null, null, null);
    }
}
