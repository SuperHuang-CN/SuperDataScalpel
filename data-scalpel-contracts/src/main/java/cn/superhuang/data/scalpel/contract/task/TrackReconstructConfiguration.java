package cn.superhuang.data.scalpel.contract.task;

import java.util.List;

public record TrackReconstructConfiguration(
        String sourceTableName,
        String pointGeometryColumnName,
        List<String> trackIdColumns,
        String timeColumnName,
        SpatialDistanceMethod distanceMethod,
        TrackBoundaryConfiguration boundaries,
        List<TrackSummaryStatistic> summaryStatistics,
        String outputTableName,
        String outputGeometryColumnName,
        String startTimeColumnName,
        String endTimeColumnName,
        String pointCountColumnName,
        TrackReconstructOptions reconstruction
) {
    public static final int MAX_TRACK_ID_COLUMNS = 8;
    public static final int MAX_SUMMARY_STATISTICS = 32;

    public TrackReconstructConfiguration {
        trackIdColumns = trackIdColumns == null ? null : List.copyOf(trackIdColumns);
        summaryStatistics = summaryStatistics == null ? null : List.copyOf(summaryStatistics);
    }

    public TrackReconstructConfiguration(String sourceTableName, String pointGeometryColumnName,
            List<String> trackIdColumns, String timeColumnName, SpatialDistanceMethod distanceMethod,
            TrackBoundaryConfiguration boundaries, List<TrackSummaryStatistic> summaryStatistics,
            String outputTableName, String outputGeometryColumnName, String startTimeColumnName,
            String endTimeColumnName, String pointCountColumnName) {
        this(sourceTableName, pointGeometryColumnName, trackIdColumns, timeColumnName, distanceMethod,
                boundaries, summaryStatistics, outputTableName, outputGeometryColumnName,
                startTimeColumnName, endTimeColumnName, pointCountColumnName, null);
    }

    public boolean usesOrderedReconstruction() {
        return reconstruction != null && reconstruction.semantics() != TrackReconstructSemantics.LEGACY_POINTS;
    }

    public boolean usesMethodPath() {
        return usesOrderedReconstruction() && !usesAreaGeometry() && reconstruction.pathGeometry() != null
                && reconstruction.pathGeometry().mode() != TrackPathGeometryMode.LEGACY_VERTEX_LINE;
    }

    public boolean usesAreaGeometry() {
        return usesOrderedReconstruction() && reconstruction.areaGeometry() != null
                && reconstruction.areaGeometry().active();
    }
}
