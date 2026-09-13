package cn.superhuang.data.scalpel.contract.task;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpatialHotSpotsContractTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void configurationAndNodeRoundTripAtCanvas469() {
        SpatialHotSpotsConfiguration configuration = new SpatialHotSpotsConfiguration(
                "points", "shape", SpatialHotSpotAnalysisSource.FIELD_SUM, "incidents",
                1d, SpatialDistanceUnit.KILOMETERS,
                2d, SpatialDistanceUnit.KILOMETERS,
                null, SpatialHotSpotMultipleTesting.FDR_BH,
                "hot_spots", "bin_id", "bin_geometry", "point_count", "analysis_value",
                "gi_z_score", "gi_p_value", "gi_adjusted_p_value", "gi_bin");
        SpatialHotSpotsNodeDefinition node = new SpatialHotSpotsNodeDefinition(
                UUID.randomUUID().toString(), "寻找热点", new CanvasNodeLayout(1d, 2d, 376d, 224d), configuration);

        CanvasNodeDefinition restored = mapper.readValue(
                mapper.writeValueAsString(node), CanvasNodeDefinition.class);

        assertEquals(node, restored);
        assertEquals(69, CanvasNodeType.SPATIAL_HOT_SPOTS.introducedInMinorVersion());
        assertEquals(77, CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION);
    }
}
