package cn.superhuang.data.scalpel.contract.task;

import java.util.List;

public record TrackMotionWindowOptions(
        Integer observationCount,
        List<String> orderByColumns,
        List<TrackMotionWindowStatistic> statistics,
        SpatialDistanceUnit distanceUnit,
        SpatialDurationUnit durationUnit,
        SpatialSpeedUnit speedUnit,
        SpatialAccelerationUnit accelerationUnit,
        String elevationColumnName,
        SpatialDistanceUnit inputElevationUnit,
        SpatialDistanceUnit elevationUnit,
        Double idleTimeThreshold,
        SpatialDurationUnit idleTimeThresholdUnit
) {
    public TrackMotionWindowOptions {
        orderByColumns = orderByColumns == null ? List.of() : List.copyOf(orderByColumns);
        statistics = statistics == null ? List.of() : List.copyOf(statistics);
    }
}
