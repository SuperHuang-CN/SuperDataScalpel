package cn.superhuang.data.scalpel.contract.task;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GeometryBufferContractTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void distanceSourcesRoundTripWithoutChangingMissingLegacySemantics() {
        GeometryBufferConfiguration legacy = mapper.readValue("""
                {
                  "sourceTableName":"sites",
                  "outputTableName":"service_areas",
                  "geometryColumnName":"shape",
                  "outputColumnName":"buffer_shape",
                  "distance":100,
                  "mode":"PLANAR"
                }
                """, GeometryBufferConfiguration.class);
        assertNull(legacy.distanceSource());
        assertNull(legacy.distanceFieldName());
        assertNull(legacy.distanceExpression());
        assertEquals(GeometryBufferDistanceSource.CONSTANT, legacy.effectiveDistanceSource());

        for (GeometryBufferConfiguration configuration : new GeometryBufferConfiguration[]{
                new GeometryBufferConfiguration(
                        "sites", "field_buffer", "shape", "buffer_shape",
                        100d, SpatialMeasureMode.PLANAR, SpatialDistanceUnit.METERS,
                        GeometryBufferDistanceSource.FIELD, "radius", "radius * 2"),
                new GeometryBufferConfiguration(
                        "sites", "expression_buffer", "shape", "buffer_shape",
                        100d, SpatialMeasureMode.SPHEROID, SpatialDistanceUnit.KILOMETERS,
                        GeometryBufferDistanceSource.EXPRESSION, "saved_radius", "coalesce(radius, 1)")
        }) {
            GeometryBufferConfiguration restored = mapper.readValue(
                    mapper.writeValueAsString(configuration), GeometryBufferConfiguration.class);
            assertEquals(configuration, restored);
        }

        assertThrows(Exception.class, () -> mapper.readValue(
                "{\"distanceSource\":\"AUTO\"}", GeometryBufferConfiguration.class));
    }
}
