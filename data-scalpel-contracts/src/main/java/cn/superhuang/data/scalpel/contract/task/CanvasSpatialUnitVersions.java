package cn.superhuang.data.scalpel.contract.task;

import java.util.ArrayList;
import java.util.List;

/** Version requirements for the explicitly typed unit fields, including inactive drafts. */
public final class CanvasSpatialUnitVersions {
    private CanvasSpatialUnitVersions() { }

    /** Fixed-duration weeks are distinct from the pre-existing calendar-week enum. */
    public static List<String> unsupportedDurationPaths(CanvasNodeDefinition node, int minorVersion) {
        if (minorVersion >= 47 || node == null) return List.of();
        List<String> paths = new ArrayList<>();
        switch (node) {
            case SpatialBinAggregateNodeDefinition n when n.configuration() != null -> durationSlicing(paths, n.configuration().temporalSlicing());
            case SpatialSummarizeWithinNodeDefinition n when n.configuration() != null -> durationSlicing(paths, n.configuration().temporalSlicing());
            case SpatialDensityNodeDefinition n when n.configuration() != null -> durationSlicing(paths, n.configuration().temporalSlicing());
            case SpatialHotSpotsNodeDefinition n when n.configuration() != null -> durationSlicing(paths, n.configuration().temporalSlicing());
            case SpatialPointClusterNodeDefinition n when n.configuration() != null -> {
                if (n.configuration().dbscan() != null) duration(paths, "dbscan.searchDurationUnit", n.configuration().dbscan().searchDurationUnit());
            }
            case TrackReconstructNodeDefinition n when n.configuration() != null -> durationBoundary(paths, n.configuration().boundaries());
            case TrackFindDwellNodeDefinition n when n.configuration() != null -> {
                var c = n.configuration(); durationBoundary(paths, c.boundaries());
                duration(paths, "minimumDurationUnit", c.minimumDurationUnit());
                if (c.rangeOptions() != null) duration(paths, "rangeOptions.durationUnit", c.rangeOptions().durationUnit());
            }
            case TrackDetectIncidentsNodeDefinition n when n.configuration() != null -> {
                durationBoundary(paths, n.configuration().boundaries());
                duration(paths, "incidentDurationUnit", n.configuration().incidentDurationUnit());
            }
            case TrackMotionStatisticsNodeDefinition n when n.configuration() != null -> {
                var c = n.configuration(); durationBoundary(paths, c.boundaries());
                if (c.windowOptions() != null) {
                    duration(paths, "windowOptions.durationUnit", c.windowOptions().durationUnit());
                    duration(paths, "windowOptions.idleTimeThresholdUnit", c.windowOptions().idleTimeThresholdUnit());
                }
                if (c.metrics() != null) for (int i = 0; i < c.metrics().size(); i++) {
                    if (c.metrics().get(i) instanceof TrackMotionMetric.Duration d) duration(paths, "metrics[" + i + "].outputUnit", d.outputUnit());
                }
            }
            default -> { }
        }
        return List.copyOf(paths);
    }

    private static void durationSlicing(List<String> paths, SpatialTemporalSlicing slicing) {
        if (slicing == null) return;
        duration(paths, "temporalSlicing.intervalUnit", slicing.intervalUnit());
        duration(paths, "temporalSlicing.repeatIntervalUnit", slicing.repeatIntervalUnit());
    }

    private static void durationBoundary(List<String> paths, TrackBoundaryConfiguration boundary) {
        if (boundary != null) duration(paths, "boundaries.maximumTimeGapUnit", boundary.maximumTimeGapUnit());
    }

    private static void duration(List<String> paths, String path, SpatialDurationUnit unit) {
        if (unit == SpatialDurationUnit.WEEKS) paths.add("configuration." + path);
    }

    public static List<String> unsupportedPaths(CanvasNodeDefinition node, int minorVersion) {
        if (minorVersion >= 36 || node == null) return List.of();
        List<String> paths = new ArrayList<>();
        switch (node) {
            case GeometrySimplifyNodeDefinition n when n.configuration() != null ->
                    distance(paths, "toleranceUnit", n.configuration().toleranceUnit());
            case SpatialNearestNodeDefinition n when n.configuration() != null -> {
                var c = n.configuration();
                distance(paths, "maximumDistanceUnit", c.maximumDistanceUnit());
                distance(paths, "distanceOutputUnit", c.distanceOutputUnit());
                if (c.matching() != null && c.matching().connectionLines() != null)
                    distance(paths, "matching.connectionLines.maximumGeodesicSegmentLengthUnit", c.matching().connectionLines().maximumGeodesicSegmentLengthUnit());
            }
            case SpatialSummarizeWithinNodeDefinition n when n.configuration() != null -> {
                distance(paths, "lengthUnit", n.configuration().lengthUnit());
                var area = n.configuration().areaUnit();
                if (area != null && area.introducedInMinorVersion() > minorVersion) paths.add("configuration.areaUnit");
            }
            case SpatialBinAggregateNodeDefinition n when n.configuration() != null ->
                    distance(paths, "binSizeUnit", n.configuration().binSizeUnit());
            case SpatialDensityNodeDefinition n when n.configuration() != null -> {
                distance(paths, "binSizeUnit", n.configuration().binSizeUnit());
                distance(paths, "radiusUnit", n.configuration().radiusUnit());
                var area = n.configuration().areaUnit();
                if (area != null && area.introducedInMinorVersion() > minorVersion) paths.add("configuration.areaUnit");
            }
            case SpatialHotSpotsNodeDefinition n when n.configuration() != null -> {
                distance(paths, "binSizeUnit", n.configuration().binSizeUnit());
                distance(paths, "neighborhoodDistanceUnit", n.configuration().neighborhoodDistanceUnit());
            }
            case SpatialPointClusterNodeDefinition n when n.configuration() != null -> {
                if (n.configuration().parameters() instanceof SpatialPointClusterParameters.Dbscan dbscan)
                    distance(paths, "parameters.searchDistanceUnit", dbscan.searchDistanceUnit());
            }
            case TrackReconstructNodeDefinition n when n.configuration() != null -> {
                var c = n.configuration(); boundaries(paths, c.boundaries());
                if (c.reconstruction() != null && c.reconstruction().pathGeometry() != null)
                    distance(paths, "reconstruction.pathGeometry.maximumGeodesicSegmentLengthUnit", c.reconstruction().pathGeometry().maximumGeodesicSegmentLengthUnit());
            }
            case TrackFindDwellNodeDefinition n when n.configuration() != null -> {
                var c = n.configuration(); boundaries(paths, c.boundaries());
                distance(paths, "distanceThresholdUnit", c.distanceThresholdUnit());
                if (c.rangeOptions() != null) distance(paths, "rangeOptions.meanDistanceUnit", c.rangeOptions().meanDistanceUnit());
            }
            case TrackDetectIncidentsNodeDefinition n when n.configuration() != null -> boundaries(paths, n.configuration().boundaries());
            case TrackMotionStatisticsNodeDefinition n when n.configuration() != null -> {
                var c = n.configuration(); boundaries(paths, c.boundaries());
                distance(paths, "idleDistanceThresholdUnit", c.idleDistanceThresholdUnit());
                if (c.windowOptions() != null) {
                    var w = c.windowOptions();
                    distance(paths, "windowOptions.distanceUnit", w.distanceUnit());
                    distance(paths, "windowOptions.inputElevationUnit", w.inputElevationUnit());
                    distance(paths, "windowOptions.elevationUnit", w.elevationUnit());
                }
                if (c.metrics() != null) for (int i = 0; i < c.metrics().size(); i++) {
                    var metric = c.metrics().get(i);
                    if (metric instanceof TrackMotionMetric.Distance d) distance(paths, "metrics[" + i + "].outputUnit", d.outputUnit());
                    if (metric instanceof TrackMotionMetric.ElevationChange e) distance(paths, "metrics[" + i + "].outputUnit", e.outputUnit());
                }
            }
            default -> { }
        }
        return List.copyOf(paths);
    }

    private static void boundaries(List<String> paths, TrackBoundaryConfiguration boundaries) {
        if (boundaries != null) distance(paths, "boundaries.maximumDistanceGapUnit", boundaries.maximumDistanceGapUnit());
    }

    private static void distance(List<String> paths, String path, SpatialDistanceUnit unit) {
        if (unit != null && unit.introducedInMinorVersion() == 36) paths.add("configuration." + path);
    }
}
