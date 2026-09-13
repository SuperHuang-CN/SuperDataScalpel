package cn.superhuang.data.scalpel.contract.task;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SnapTracksContractTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void configurationAndNodeRoundTripAtCanvas474() {
        SnapTracksConfiguration configuration = configuration(
                List.of("vehicle_id"), List.of("sequence"),
                List.of(new SnapTracksLineField("road_class", "matched_road_class")));
        SnapTracksNodeDefinition node = new SnapTracksNodeDefinition(
                UUID.randomUUID().toString(), "吸附轨迹",
                new CanvasNodeLayout(1d, 2d, 392d, 232d), configuration);

        CanvasNodeDefinition restored = mapper.readValue(
                mapper.writeValueAsString(node), CanvasNodeDefinition.class);

        assertEquals(node, restored);
        assertEquals(74, CanvasNodeType.SNAP_TRACKS.introducedInMinorVersion());
        assertEquals(77, CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION);
    }

    @Test
    void listConfigurationIsDefensivelyCopiedAndEnumsAreStrict() {
        List<String> trackIds = new ArrayList<>(List.of("vehicle_id"));
        List<String> order = new ArrayList<>(List.of("sequence"));
        List<SnapTracksLineField> lineFields = new ArrayList<>(List.of(
                new SnapTracksLineField("road_class", "matched_road_class")));
        SnapTracksConfiguration configuration = configuration(trackIds, order, lineFields);
        trackIds.clear();
        order.clear();
        lineFields.clear();

        assertEquals(List.of("vehicle_id"), configuration.trackIdColumns());
        assertEquals(List.of("sequence"), configuration.orderByColumns());
        assertEquals(1, configuration.lineFields().size());
        assertThrows(UnsupportedOperationException.class, configuration.lineFields()::clear);
        assertThrows(Exception.class, () -> mapper.readValue("{\"outputMode\":\"AUTO\"}",
                SnapTracksConfiguration.class));
    }

    private static SnapTracksConfiguration configuration(
            List<String> trackIds,
            List<String> order,
            List<SnapTracksLineField> lineFields
    ) {
        return new SnapTracksConfiguration(
                "observations", "shape", trackIds, "observed_at", order,
                "roads", "shape", "road_id", "from_node", "to_node",
                30d, SpatialDistanceUnit.METERS, SpatialDistanceMethod.GEODESIC,
                new TrackBoundaryConfiguration(null, null, null, null, null),
                new SnapTracksDirectionMatching("direction", "F", "B", "A", "N"),
                lineFields, SnapTracksOutputMode.ALL_FEATURES, "snapped_tracks",
                "snapped_shape", "matched_road_id", "match_status",
                "original_x", "original_y", "match_x", "match_y", "match_distance");
    }
}
