package cn.superhuang.data.scalpel.contract.task;

public record SpatialNearestConnectionLines(
        Boolean enabled,
        String outputTableName,
        String geometryColumnName,
        Double maximumGeodesicSegmentLength,
        SpatialDistanceUnit maximumGeodesicSegmentLengthUnit
) {
    public boolean active() { return Boolean.TRUE.equals(enabled); }
}
