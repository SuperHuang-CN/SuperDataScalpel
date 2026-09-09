package cn.superhuang.data.scalpel.contract.task;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SpatialDurationUnitContractTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test void everyFixedDurationRoundTripsAndCalendarOnlyUnitsAreRejected() {
        for (var unit : SpatialDurationUnit.values())
            assertEquals(unit, mapper.readValue(mapper.writeValueAsString(unit), SpatialDurationUnit.class));
        for (String value : List.of("MONTHS", "YEARS", "Weeks"))
            assertThrows(Exception.class, () -> mapper.readValue("\"" + value + "\"", SpatialDurationUnit.class));
    }

    @Test void allFixedDurationPathsIncludingInactiveDraftsRequire47() {
        for (String type : List.of("SPATIAL_BIN_AGGREGATE", "SPATIAL_SUMMARIZE_WITHIN"))
            check(type, "{" + (type.equals("SPATIAL_BIN_AGGREGATE") ? "\"binSize\":0,\"includeEmptyBins\":false," : "\"includeEmptyAreas\":false,")
                    + "\"temporalSlicing\":{\"interval\":1,\"intervalUnit\":\"WEEKS\",\"repeatIntervalUnit\":\"WEEKS\",\"calendar\":{\"mode\":\"CALENDAR\",\"intervalUnit\":\"MONTHS\"}}}",
                    "temporalSlicing.intervalUnit", "temporalSlicing.repeatIntervalUnit");
        check("SPATIAL_POINT_CLUSTER", "{\"parameters\":{\"algorithm\":\"HDBSCAN\",\"minimumFeatures\":2},\"dbscan\":{\"mode\":\"SPATIAL\",\"searchDurationUnit\":\"WEEKS\"}}", "dbscan.searchDurationUnit");
        check("TRACK_RECONSTRUCT", "{\"boundaries\":{\"maximumTimeGapUnit\":\"WEEKS\"}}", "boundaries.maximumTimeGapUnit");
        check("TRACK_FIND_DWELL", "{\"distanceThreshold\":0,\"minimumDuration\":0,\"minimumDurationUnit\":\"WEEKS\",\"boundaries\":{\"maximumTimeGapUnit\":\"WEEKS\"},\"rangeOptions\":{\"durationUnit\":\"WEEKS\"}}",
                "boundaries.maximumTimeGapUnit", "minimumDurationUnit", "rangeOptions.durationUnit");
        check("TRACK_DETECT_INCIDENTS", "{\"incidentDurationUnit\":\"WEEKS\",\"boundaries\":{\"maximumTimeGapUnit\":\"WEEKS\"}}",
                "boundaries.maximumTimeGapUnit", "incidentDurationUnit");
        check("TRACK_MOTION_STATISTICS", """
                {"historyPoints":1,"motionSemantics":"LEGACY_LAG","boundaries":{"maximumTimeGapUnit":"WEEKS"},
                 "windowOptions":{"durationUnit":"WEEKS","idleTimeThresholdUnit":"WEEKS"},
                 "metrics":[{"kind":"DURATION","outputUnit":"WEEKS"},{"kind":"DISTANCE","outputUnit":"METERS"}]}
                """, "boundaries.maximumTimeGapUnit", "windowOptions.durationUnit", "windowOptions.idleTimeThresholdUnit", "metrics[0].outputUnit");
    }

    @Test void calendarWeeksOrdinaryStringsAndOldUnitsDoNotRequire47() {
        for (var node : List.of(
                node("SPATIAL_BIN_AGGREGATE", "{\"binSize\":0,\"includeEmptyBins\":false,\"sourceTableName\":\"WEEKS\",\"temporalSlicing\":{\"interval\":1,\"intervalUnit\":\"DAYS\",\"calendar\":{\"mode\":\"CALENDAR\",\"intervalUnit\":\"WEEKS\",\"repeatIntervalUnit\":\"WEEKS\"}}}"),
                node("TRACK_RECONSTRUCT", "{\"boundaries\":{\"maximumTimeGapUnit\":\"DAYS\",\"fixedTimeBoundary\":{\"unit\":\"WEEKS\"}}}"),
                node("TRACK_MOTION_STATISTICS", "{\"historyPoints\":1,\"sourceTableName\":\"WEEKS\",\"metrics\":[{\"kind\":\"DURATION\",\"outputUnit\":\"DAYS\"}]}")))
            assertTrue(CanvasSpatialUnitVersions.unsupportedDurationPaths(node, 46).isEmpty());
        assertTrue(CanvasSpatialUnitVersions.unsupportedDurationPaths(null, 46).isEmpty());
    }

    private void check(String type, String config, String... paths) {
        var node = node(type, config);
        assertEquals(List.of(paths).stream().map(p -> "configuration." + p).toList(), CanvasSpatialUnitVersions.unsupportedDurationPaths(node, 46), type);
        assertTrue(CanvasSpatialUnitVersions.unsupportedDurationPaths(node, 47).isEmpty());
        assertEquals(node, mapper.readValue(mapper.writeValueAsString(node), CanvasNodeDefinition.class));
    }

    private CanvasNodeDefinition node(String type, String config) {
        return mapper.readValue("{\"type\":\"" + type + "\",\"configuration\":" + config + "}", CanvasNodeDefinition.class);
    }
}
