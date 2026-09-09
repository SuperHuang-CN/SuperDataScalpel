package cn.superhuang.data.scalpel.contract.task;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SpatialUnitContractTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test void everyUnitRoundTripsWithoutChangingOldUnitNames() {
        for (var unit : SpatialDistanceUnit.values())
            assertEquals(unit, mapper.readValue(mapper.writeValueAsString(unit), SpatialDistanceUnit.class));
        for (var unit : SpatialAreaUnit.values())
            assertEquals(unit, mapper.readValue(mapper.writeValueAsString(unit), SpatialAreaUnit.class));
        assertEquals(0, SpatialDistanceUnit.FEET.introducedInMinorVersion());
        assertEquals(36, SpatialDistanceUnit.FEET_US.introducedInMinorVersion());
        assertEquals(36, SpatialAreaUnit.ACRES_US.introducedInMinorVersion());
        assertThrows(Exception.class, () -> mapper.readValue("\"FeetInt\"", SpatialDistanceUnit.class));
    }

    @Test void allTypedPathsIncludingInactiveSettingsRequire36() {
        check("GEOMETRY_SIMPLIFY", "{\"toleranceUnit\":\"YARDS\"}", "toleranceUnit");
        check("SPATIAL_NEAREST", """
                {"nearestCount":1,"includeUnmatched":false,"maximumDistanceUnit":"FEET_US","distanceOutputUnit":"MILES_US",
                 "matching":{"semantics":"LEGACY_KNN","connectionLines":{"enabled":false,"maximumGeodesicSegmentLengthUnit":"NAUTICAL_MILES_US"}}}
                """, "maximumDistanceUnit", "distanceOutputUnit", "matching.connectionLines.maximumGeodesicSegmentLengthUnit");
        check("SPATIAL_SUMMARIZE_WITHIN", "{\"includeEmptyAreas\":false,\"lengthUnit\":\"YARDS_US\",\"areaUnit\":\"SQUARE_YARDS\"}", "lengthUnit", "areaUnit");
        check("SPATIAL_BIN_AGGREGATE", "{\"binSize\":0,\"includeEmptyBins\":false,\"binSizeUnit\":\"YARDS\"}", "binSizeUnit");
        check("SPATIAL_POINT_CLUSTER", "{\"parameters\":{\"algorithm\":\"DBSCAN\",\"searchDistance\":0,\"minimumFeatures\":2,\"searchDistanceUnit\":\"FEET_US\"}}", "parameters.searchDistanceUnit");
        check("TRACK_RECONSTRUCT", """
                {"boundaries":{"maximumDistanceGapUnit":"YARDS"},"reconstruction":{"semantics":"LEGACY_POINTS",
                 "pathGeometry":{"mode":"LEGACY_VERTEX_LINE","maximumGeodesicSegmentLengthUnit":"MILES_US"}}}
                """, "boundaries.maximumDistanceGapUnit", "reconstruction.pathGeometry.maximumGeodesicSegmentLengthUnit");
        check("TRACK_FIND_DWELL", """
                {"distanceThreshold":0,"minimumDuration":0,"distanceThresholdUnit":"YARDS_US",
                 "boundaries":{"maximumDistanceGapUnit":"FEET_US"},"rangeOptions":{"resultMode":"ALL_FEATURES","meanDistanceUnit":"MILES_US"}}
                """, "boundaries.maximumDistanceGapUnit", "distanceThresholdUnit", "rangeOptions.meanDistanceUnit");
        check("TRACK_DETECT_INCIDENTS", "{\"boundaries\":{\"maximumDistanceGapUnit\":\"YARDS\"}}", "boundaries.maximumDistanceGapUnit");
        check("TRACK_MOTION_STATISTICS", """
                {"historyPoints":1,"boundaries":{"maximumDistanceGapUnit":"FEET_US"},"idleDistanceThresholdUnit":"YARDS_US",
                 "windowOptions":{"distanceUnit":"YARDS","inputElevationUnit":"FEET_US","elevationUnit":"MILES_US"},
                 "metrics":[{"kind":"DISTANCE","outputUnit":"NAUTICAL_MILES_US"},{"kind":"ELEVATION_CHANGE","outputUnit":"YARDS_US"}]}
                """, "boundaries.maximumDistanceGapUnit", "idleDistanceThresholdUnit", "windowOptions.distanceUnit",
                "windowOptions.inputElevationUnit", "windowOptions.elevationUnit", "metrics[0].outputUnit", "metrics[1].outputUnit");
    }

    @Test void ordinaryStringsAndLegacyUnitsDoNotTriggerAGate() {
        var n = node("GEOMETRY_SIMPLIFY", "{\"sourceTableName\":\"FEET_US\",\"toleranceUnit\":\"FEET\"}");
        assertTrue(CanvasSpatialUnitVersions.unsupportedPaths(n, 35).isEmpty());
    }

    private void check(String type, String configuration, String... paths) {
        var node = node(type, configuration);
        assertEquals(List.of(paths).stream().map(p -> "configuration." + p).toList(), CanvasSpatialUnitVersions.unsupportedPaths(node, 35), type);
        assertTrue(CanvasSpatialUnitVersions.unsupportedPaths(node, 36).isEmpty());
        assertEquals(node, mapper.readValue(mapper.writeValueAsString(node), CanvasNodeDefinition.class));
    }

    private CanvasNodeDefinition node(String type, String configuration) {
        return mapper.readValue("{\"type\":\"" + type + "\",\"configuration\":" + configuration + "}", CanvasNodeDefinition.class);
    }
}
