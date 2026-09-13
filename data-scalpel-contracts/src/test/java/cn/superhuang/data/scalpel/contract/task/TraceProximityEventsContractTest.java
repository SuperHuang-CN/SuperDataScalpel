package cn.superhuang.data.scalpel.contract.task;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TraceProximityEventsContractTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void configurationAndNodeRoundTripAtCanvas473() {
        TraceProximityEventsConfiguration configuration = configuration(
                List.of(new TraceProximityEntityOfInterest("A-01", 1_787_572_800_000L)),
                List.of("building", "floor"));
        TraceProximityEventsNodeDefinition node = new TraceProximityEventsNodeDefinition(
                UUID.randomUUID().toString(), "追踪邻近事件",
                new CanvasNodeLayout(1d, 2d, 392d, 232d), configuration);

        CanvasNodeDefinition restored = mapper.readValue(
                mapper.writeValueAsString(node), CanvasNodeDefinition.class);

        assertEquals(node, restored);
        assertEquals(73, CanvasNodeType.TRACE_PROXIMITY_EVENTS.introducedInMinorVersion());
        assertEquals(77, CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION);
    }

    @Test
    void entityAndAttributeListsAreDefensivelyCopied() {
        List<TraceProximityEntityOfInterest> entities = new ArrayList<>();
        entities.add(new TraceProximityEntityOfInterest("A-01", null));
        List<String> attributes = new ArrayList<>();
        attributes.add("building");

        TraceProximityEventsConfiguration configuration = configuration(entities, attributes);
        entities.clear();
        attributes.clear();

        assertEquals(1, configuration.entitiesOfInterest().size());
        assertEquals(List.of("building"), configuration.attributeMatchColumns());
    }

    private static TraceProximityEventsConfiguration configuration(
            List<TraceProximityEntityOfInterest> entities,
            List<String> attributes
    ) {
        return new TraceProximityEventsConfiguration(
                "observations", "shape", "device_id", "observed_at",
                SpatialDistanceMethod.GEODESIC, 15d, SpatialDistanceUnit.METERS,
                2L, SpatialGroupByProximityTemporalUnit.MONTHS,
                TraceProximityInterestSource.ENTITY_IDS, entities,
                "", "", null, 5, attributes, true,
                "trace_events", "trace_tracks", "trace_from_id", "trace_to_id",
                "trace_depth", "trace_duration_minutes", "trace_event_time");
    }
}
