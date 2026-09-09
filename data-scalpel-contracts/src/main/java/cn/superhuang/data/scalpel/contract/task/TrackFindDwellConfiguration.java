package cn.superhuang.data.scalpel.contract.task;

import java.util.List;

public record TrackFindDwellConfiguration(
        String sourceTableName,
        String pointGeometryColumnName,
        List<String> trackIdColumns,
        String timeColumnName,
        SpatialDistanceMethod distanceMethod,
        double distanceThreshold,
        SpatialDistanceUnit distanceThresholdUnit,
        double minimumDuration,
        SpatialDurationUnit minimumDurationUnit,
        TrackBoundaryConfiguration boundaries,
        List<TrackSummaryStatistic> summaryStatistics,
        DwellGeometryKind outputGeometryKind,
        String outputTableName,
        String dwellIdColumnName,
        String startTimeColumnName,
        String endTimeColumnName,
        String durationColumnName,
        String pointCountColumnName,
        String outputGeometryColumnName,
        TrackDwellSemantics dwellSemantics,
        TrackDwellRangeOptions rangeOptions
) {
    public static final int MAX_TRACK_ID_COLUMNS = 8;
    public static final int MAX_SUMMARY_STATISTICS = 32;

    public TrackFindDwellConfiguration {
        trackIdColumns = trackIdColumns == null ? null : List.copyOf(trackIdColumns);
        summaryStatistics = summaryStatistics == null ? null : List.copyOf(summaryStatistics);
    }

    public boolean usesReferenceCenter() { return dwellSemantics == TrackDwellSemantics.REFERENCE_CENTER; }

    public TrackFindDwellConfiguration(String sourceTableName, String pointGeometryColumnName,
            List<String> trackIdColumns, String timeColumnName, SpatialDistanceMethod distanceMethod,
            double distanceThreshold, SpatialDistanceUnit distanceThresholdUnit, double minimumDuration,
            SpatialDurationUnit minimumDurationUnit, TrackBoundaryConfiguration boundaries,
            List<TrackSummaryStatistic> summaryStatistics, DwellGeometryKind outputGeometryKind,
            String outputTableName, String dwellIdColumnName, String startTimeColumnName, String endTimeColumnName,
            String durationColumnName, String pointCountColumnName, String outputGeometryColumnName) {
        this(sourceTableName, pointGeometryColumnName, trackIdColumns, timeColumnName, distanceMethod,
                distanceThreshold, distanceThresholdUnit, minimumDuration, minimumDurationUnit, boundaries,
                summaryStatistics, outputGeometryKind, outputTableName, dwellIdColumnName, startTimeColumnName,
                endTimeColumnName, durationColumnName, pointCountColumnName, outputGeometryColumnName, null, null);
    }
}
