package cn.superhuang.data.scalpel.contract.task;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpatialDescribeDatasetContractTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void configurationAndNodeRoundTripAtCanvas476() {
        SpatialDescribeDatasetConfiguration configuration = new SpatialDescribeDatasetConfiguration(
                "city_events", "shape", "city_field_statistics", "city_description",
                100, "city_sample", true, "city_extent");
        SpatialDescribeDatasetNodeDefinition node = new SpatialDescribeDatasetNodeDefinition(
                UUID.randomUUID().toString(), "描述数据集",
                new CanvasNodeLayout(1d, 2d, 376d, 232d), configuration);

        CanvasNodeDefinition restored = mapper.readValue(
                mapper.writeValueAsString(node), CanvasNodeDefinition.class);

        assertEquals(node, restored);
        assertEquals(76, CanvasNodeType.SPATIAL_DESCRIBE_DATASET.introducedInMinorVersion());
        assertEquals(77, CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION);
    }
}
