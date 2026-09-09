package cn.superhuang.data.scalpel.contract.task;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TrackConfigurationTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void motionWindowContractCopiesArraysAndPreservesLegacyConstructors() {
        var stats = new ArrayList<>(java.util.Arrays.stream(TrackMotionStatistic.values())
                .map(kind -> new TrackMotionWindowStatistic(java.util.UUID.randomUUID().toString(), kind, kind.name())).toList());
        var options = new TrackMotionWindowOptions(3, List.of("sequence"), stats, SpatialDistanceUnit.METERS,
                SpatialDurationUnit.SECONDS, SpatialSpeedUnit.METERS_PER_SECOND,
                SpatialAccelerationUnit.METERS_PER_SECOND_SQUARED, "height", SpatialDistanceUnit.FEET,
                SpatialDistanceUnit.METERS, 30d, SpatialDurationUnit.SECONDS);
        stats.clear();
        assertEquals(31, options.statistics().size());
        assertEquals(options, mapper.readValue(mapper.writeValueAsString(options), TrackMotionWindowOptions.class));
        assertThrows(UnsupportedOperationException.class, () -> options.orderByColumns().add("more"));
        var old = new TrackMotionStatisticsConfiguration("", "", List.of(), "", null,
                new TrackBoundaryConfiguration(null, null, null, null), 1, null, null, List.of(), "");
        assertFalse(old.usesObservationWindow()); assertNull(old.windowOptions());
        var current = mapper.readValue("{\"historyPoints\":1,\"motionSemantics\":\"OBSERVATION_WINDOW\",\"windowOptions\":{\"observationCount\":3}}",
                TrackMotionStatisticsConfiguration.class);
        assertTrue(current.usesObservationWindow());
        assertEquals(List.of(), current.windowOptions().statistics());
        assertThrows(Exception.class, () -> mapper.readValue("{\"historyPoints\":1,\"motionSemantics\":\"OTHER\"}", TrackMotionStatisticsConfiguration.class));
        assertThrows(Exception.class, () -> mapper.readValue("{\"statistics\":{}}", TrackMotionWindowOptions.class));
    }

    @Test
    void roundTripsLifecycleAndCalendarBoundaryWithDefensiveOrderingCopy() {
        var order = new ArrayList<>(List.of("event_id"));
        var configuration = new TrackDetectIncidentsConfiguration("events", null, List.of("track"), "time", null,
                new TrackBoundaryConfiguration(null, null, null, null,
                        new TrackFixedTimeBoundary(1, TrackTimeBoundaryUnit.MONTHS, "2024-01-31T12:00:00Z", "UTC")),
                null, null, TrackIncidentResultMode.ALL_EVENTS, "incidents", "id", "flag", "start", "end", "duration",
                SpatialDurationUnit.MILLISECONDS, TrackIncidentSemantics.CONDITION_LIFECYCLE, "status", order);
        order.clear();
        assertEquals(List.of("event_id"), configuration.orderByColumns());
        assertTrue(configuration.usesLifecycleOptions());
        assertEquals(configuration, mapper.readValue(mapper.writeValueAsString(configuration), TrackDetectIncidentsConfiguration.class));
    }

    @Test
    void absentOptionsAndConvenienceConstructorsKeepLegacyMeaning() {
        var decoded = mapper.readValue("{}", TrackDetectIncidentsConfiguration.class);
        assertEquals(TrackIncidentSemantics.LEGACY, decoded.effectiveIncidentSemantics());
        assertEquals(List.of(), decoded.orderByColumns());
        assertFalse(decoded.usesLifecycleOptions());
        var legacy = new TrackDetectIncidentsConfiguration("", null, List.of(), "", null,
                new TrackBoundaryConfiguration(null, null, null, null), null, null, null,
                "", "", "", "", "", "", SpatialDurationUnit.SECONDS);
        assertEquals(TrackIncidentSemantics.LEGACY, legacy.effectiveIncidentSemantics());
        assertNull(legacy.boundaries().fixedTimeBoundary());
    }

    @Test
    void rejectsUnknownStrategiesAndNonArrayOrder() {
        assertThrows(Exception.class, () -> mapper.readValue("{\"incidentSemantics\":\"UNKNOWN\"}", TrackDetectIncidentsConfiguration.class));
        assertThrows(Exception.class, () -> mapper.readValue("{\"orderByColumns\":{}}", TrackDetectIncidentsConfiguration.class));
        assertThrows(Exception.class, () -> mapper.readValue("{\"unit\":\"QUARTER\"}", TrackFixedTimeBoundary.class));
    }

    @Test
    void roundTripsDwellRangeModesAndKeepsAbsentOptionsLegacy() {
        var order = new ArrayList<>(List.of("sequence"));
        for (var mode : TrackDwellResultMode.values()) {
            var range = new TrackDwellRangeOptions(mode, order, SpatialDurationUnit.MILLISECONDS,
                    "mean_distance", SpatialDistanceUnit.METERS, "is_dwell");
            assertEquals(range, mapper.readValue(mapper.writeValueAsString(range), TrackDwellRangeOptions.class));
            assertThrows(UnsupportedOperationException.class, () -> range.orderByColumns().add("other"));
        }
        var copied = new TrackDwellRangeOptions(TrackDwellResultMode.ALL_FEATURES, order, null, "", null, "");
        order.clear();
        assertEquals(List.of("sequence"), copied.orderByColumns());
        var old = mapper.readValue("{\"distanceThreshold\":1,\"minimumDuration\":1}", TrackFindDwellConfiguration.class);
        assertFalse(old.usesReferenceCenter());
        assertNull(old.rangeOptions());
        var current = mapper.readValue("{\"distanceThreshold\":1,\"minimumDuration\":1,\"dwellSemantics\":\"REFERENCE_CENTER\",\"rangeOptions\":{\"resultMode\":\"ALL_FEATURES\"}}",
                TrackFindDwellConfiguration.class);
        assertTrue(current.usesReferenceCenter());
        assertEquals(List.of(), current.rangeOptions().orderByColumns());
        assertEquals(current, mapper.readValue(mapper.writeValueAsString(current), TrackFindDwellConfiguration.class));
        assertThrows(Exception.class, () -> mapper.readValue("{\"resultMode\":\"BAD\"}", TrackDwellRangeOptions.class));
        assertThrows(Exception.class, () -> mapper.readValue("{\"rangeOptions\":[]}", TrackFindDwellConfiguration.class));
    }
}
