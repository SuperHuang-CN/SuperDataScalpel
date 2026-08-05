package cn.superhuang.data.scalpel.contract.task;

public record GeometryBufferConfiguration(
        String sourceTableName,
        String outputTableName,
        String geometryColumnName,
        String outputColumnName,
        double distance,
        SpatialMeasureMode mode
) {
}
