package cn.superhuang.data.scalpel.contract.task;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpatialDensityContractTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void configurationAndNodeRoundTripAtCanvas468() {
        SpatialDensityConfiguration configuration = new SpatialDensityConfiguration(
                "points", "shape",
                List.of(new SpatialDensityField(UUID.randomUUID().toString(), "population", "population_density")),
                SpatialDensityWeighting.KERNEL, SpatialDensityBinShape.HEXAGON,
                1d, SpatialDistanceUnit.KILOMETERS,
                2d, SpatialDistanceUnit.KILOMETERS,
                SpatialAreaUnit.SQUARE_KILOMETERS,
                null, "density", "bin_id", "bin_geometry", "point_density");
        SpatialDensityNodeDefinition node = new SpatialDensityNodeDefinition(
                UUID.randomUUID().toString(), "计算密度", new CanvasNodeLayout(1d, 2d, 368d, 224d), configuration);

        CanvasNodeDefinition restored = mapper.readValue(
                mapper.writeValueAsString(node), CanvasNodeDefinition.class);

        assertEquals(node, restored);
        assertEquals(68, CanvasNodeType.SPATIAL_DENSITY.introducedInMinorVersion());
        assertEquals(77, CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION);
    }
}
