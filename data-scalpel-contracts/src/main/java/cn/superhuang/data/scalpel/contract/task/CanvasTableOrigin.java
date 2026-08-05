package cn.superhuang.data.scalpel.contract.task;

import java.util.UUID;

public record CanvasTableOrigin(
        String kind,
        UUID dataSourceId,
        String tableName,
        UUID modelId,
        String modelCode,
        Integer modelSchemaVersion,
        UUID fileDatasetTableId
) {
    public static CanvasTableOrigin jdbc(UUID dataSourceId, String tableName) {
        return new CanvasTableOrigin("JDBC", dataSourceId, tableName, null, null, null, null);
    }

    public static CanvasTableOrigin jdbcQuery(UUID dataSourceId, String outputTableName) {
        return new CanvasTableOrigin("JDBC_QUERY", dataSourceId, outputTableName, null, null, null, null);
    }

    public static CanvasTableOrigin model(UUID modelId, String modelCode, int schemaVersion) {
        return new CanvasTableOrigin("MODEL", null, null, modelId, modelCode, schemaVersion, null);
    }

    public static CanvasTableOrigin httpApi(UUID dataSourceId, UUID resourceId) {
        return new CanvasTableOrigin("HTTP_API", dataSourceId, resourceId.toString(), null, null, null, null);
    }

    public static CanvasTableOrigin spatialService(UUID dataSourceId, UUID resourceId) {
        return new CanvasTableOrigin("SPATIAL_SERVICE", dataSourceId, resourceId.toString(), null, null, null, null);
    }

    public static CanvasTableOrigin kafka(UUID dataSourceId, String topic) {
        return new CanvasTableOrigin("KAFKA", dataSourceId, topic, null, null, null, null);
    }

    public static CanvasTableOrigin fileDataset(UUID fileDatasetTableId) {
        return new CanvasTableOrigin("FILE_DATASET", null, null, null, null, null, fileDatasetTableId);
    }
}
