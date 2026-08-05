package cn.superhuang.data.scalpel.contract.task;

public record SpatialClipConfiguration(
        String sourceTableName,
        String maskTableName,
        String outputTableName,
        String sourceGeometryColumnName,
        String maskGeometryColumnName,
        String outputColumnName
) {
}
