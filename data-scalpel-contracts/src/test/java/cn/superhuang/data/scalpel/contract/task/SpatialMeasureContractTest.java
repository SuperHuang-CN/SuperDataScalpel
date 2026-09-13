package cn.superhuang.data.scalpel.contract.task;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SpatialMeasureContractTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void outputUnitsRoundTripWithoutChangingAbsentLegacySemantics() {
        SpatialMeasurement.Area legacyArea = mapper.readValue(
                "{\"kind\":\"AREA\",\"geometryColumnName\":\"shape\",\"mode\":\"PLANAR\",\"outputColumnName\":\"area\"}",
                SpatialMeasurement.Area.class);
        SpatialMeasurement.Length legacyLength = mapper.readValue(
                "{\"kind\":\"LENGTH\",\"geometryColumnName\":\"shape\",\"mode\":\"SPHEROID\",\"outputColumnName\":\"length\"}",
                SpatialMeasurement.Length.class);
        assertNull(legacyArea.outputUnit());
        assertNull(legacyLength.outputUnit());

        for (SpatialMeasurement measurement : new SpatialMeasurement[]{
                new SpatialMeasurement.Area(
                        "shape", SpatialMeasureMode.SPHEROID, "area_ha",
                        SpatialAreaUnit.HECTARES),
                new SpatialMeasurement.Length(
                        "shape", SpatialMeasureMode.SPHEROID, "length_km",
                        SpatialDistanceUnit.KILOMETERS),
                new SpatialMeasurement.Perimeter(
                        "shape", SpatialMeasureMode.PLANAR, "perimeter_ft",
                        SpatialDistanceUnit.FEET),
                new SpatialMeasurement.Distance(
                        "left_shape", "right_shape", SpatialMeasureMode.PLANAR,
                        "distance_source", SpatialDistanceUnit.SOURCE_CRS_UNIT)
        }) {
            SpatialMeasurement restored = mapper.readValue(
                    mapper.writeValueAsString(measurement), SpatialMeasurement.class);
            assertEquals(measurement, restored);
        }

        assertThrows(Exception.class, () -> mapper.readValue(
                "{\"kind\":\"AREA\",\"mode\":\"PLANAR\",\"outputUnit\":\"METERS\"}",
                SpatialMeasurement.class));
        assertThrows(Exception.class, () -> mapper.readValue(
                "{\"kind\":\"LENGTH\",\"mode\":\"SPHEROID\",\"outputUnit\":\"HECTARES\"}",
                SpatialMeasurement.class));
    }
}
