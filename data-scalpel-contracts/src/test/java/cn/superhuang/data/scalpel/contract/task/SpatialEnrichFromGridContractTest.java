package cn.superhuang.data.scalpel.contract.task;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpatialEnrichFromGridContractTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void configurationAndNodeRoundTripAtCanvas471() {
        SpatialEnrichFromGridConfiguration configuration = new SpatialEnrichFromGridConfiguration(
                "incidents", "shape", "city_grid", "bin_geometry", "bin_id",
                List.of(
                        new SpatialEnrichFromGridField("nearest_hospital", "nearest_hospital"),
                        new SpatialEnrichFromGridField("population_sum", "grid_population_sum")),
                "enriched_incidents");
        SpatialEnrichFromGridNodeDefinition node = new SpatialEnrichFromGridNodeDefinition(
                UUID.randomUUID().toString(), "从多变量格网丰富",
                new CanvasNodeLayout(1d, 2d, 376d, 224d), configuration);

        CanvasNodeDefinition restored = mapper.readValue(
                mapper.writeValueAsString(node), CanvasNodeDefinition.class);

        assertEquals(node, restored);
        assertEquals(71, CanvasNodeType.SPATIAL_ENRICH_FROM_GRID.introducedInMinorVersion());
        assertEquals(77, CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION);
    }

    @Test
    void enrichmentFieldsAreDefensivelyCopied() {
        List<SpatialEnrichFromGridField> fields = new ArrayList<>();
        fields.add(new SpatialEnrichFromGridField("population", "grid_population"));
        SpatialEnrichFromGridConfiguration configuration = new SpatialEnrichFromGridConfiguration(
                "points", "shape", "grid", "bin_geometry", "bin_id", fields, "enriched");

        fields.clear();

        assertEquals(List.of(new SpatialEnrichFromGridField("population", "grid_population")),
                configuration.enrichFields());
    }
}
