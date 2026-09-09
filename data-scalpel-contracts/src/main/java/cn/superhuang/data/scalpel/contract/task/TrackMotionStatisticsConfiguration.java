package cn.superhuang.data.scalpel.contract.task;

import java.util.List;

public record TrackMotionStatisticsConfiguration(
        String sourceTableName,
        String pointGeometryColumnName,
        List<String> trackIdColumns,
        String timeColumnName,
        SpatialDistanceMethod distanceMethod,
        TrackBoundaryConfiguration boundaries,
        int historyPoints,
        Double idleDistanceThreshold,
        SpatialDistanceUnit idleDistanceThresholdUnit,
        List<TrackMotionMetric> metrics,
        String outputTableName,
        TrackMotionSemantics motionSemantics,
        TrackMotionWindowOptions windowOptions
) {
    public static final int MAX_TRACK_ID_COLUMNS = 8;
    public static final int MAX_HISTORY_POINTS = 100;
    public static final int MAX_METRICS = 16;

    public TrackMotionStatisticsConfiguration {
        trackIdColumns = trackIdColumns == null ? null : List.copyOf(trackIdColumns);
        metrics = metrics == null ? null : List.copyOf(metrics);
    }

    public boolean usesObservationWindow() { return motionSemantics == TrackMotionSemantics.OBSERVATION_WINDOW; }

    public TrackMotionStatisticsConfiguration(String sourceTableName, String pointGeometryColumnName,
            List<String> trackIdColumns, String timeColumnName, SpatialDistanceMethod distanceMethod,
            TrackBoundaryConfiguration boundaries, int historyPoints, Double idleDistanceThreshold,
            SpatialDistanceUnit idleDistanceThresholdUnit, List<TrackMotionMetric> metrics, String outputTableName) {
        this(sourceTableName, pointGeometryColumnName, trackIdColumns, timeColumnName, distanceMethod, boundaries,
                historyPoints, idleDistanceThreshold, idleDistanceThresholdUnit, metrics, outputTableName, null, null);
    }
}
