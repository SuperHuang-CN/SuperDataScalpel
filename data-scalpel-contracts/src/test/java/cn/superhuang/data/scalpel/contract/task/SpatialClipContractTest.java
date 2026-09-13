package cn.superhuang.data.scalpel.contract.task;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpatialClipContractTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void geometryPolicyRoundTripsWithoutChangingMissingLegacySemantics() {
        SpatialClipConfiguration missing = mapper.readValue("""
                {
                  "sourceTableName":"roads",
                  "maskTableName":"districts",
                  "outputTableName":"clipped",
                  "sourceGeometryColumnName":"shape",
                  "maskGeometryColumnName":"boundary",
                  "outputColumnName":"clipped_shape"
                }
                """, SpatialClipConfiguration.class);
        assertNull(missing.geometryPolicy());
        assertFalse(missing.usesSourceFamily());
        assertNull(missing.maskCombination());
        assertFalse(missing.dissolvesMasks());

        for (SpatialClipGeometryPolicy policy : SpatialClipGeometryPolicy.values()) {
            SpatialClipConfiguration configuration = new SpatialClipConfiguration(
                    "roads", "districts", "clipped", "shape", "boundary",
                    "clipped_shape", policy);
            SpatialClipConfiguration restored = mapper.readValue(
                    mapper.writeValueAsString(configuration), SpatialClipConfiguration.class);
            assertEquals(configuration, restored);
            assertEquals(policy == SpatialClipGeometryPolicy.SOURCE_FAMILY_2D,
                    restored.usesSourceFamily());
        }

        SpatialClipConfiguration legacyConstructor = new SpatialClipConfiguration(
                "roads", "districts", "clipped", "shape", "boundary", "clipped_shape");
        assertNull(legacyConstructor.geometryPolicy());
        assertNull(legacyConstructor.maskCombination());
        assertThrows(Exception.class, () -> mapper.readValue(
                "{\"geometryPolicy\":\"AUTO\"}", SpatialClipConfiguration.class));
        assertTrue(mapper.writeValueAsString(missing).contains("\"geometryPolicy\":null"));
    }

    @Test
    void maskCombinationRoundTripsAndKeepsMissingDefinitionsPairwise() {
        for (SpatialClipMaskCombination combination : SpatialClipMaskCombination.values()) {
            SpatialClipConfiguration configuration = new SpatialClipConfiguration(
                    "roads", "districts", "clipped", "shape", "boundary",
                    "clipped_shape", SpatialClipGeometryPolicy.SOURCE_FAMILY_2D,
                    combination);
            SpatialClipConfiguration restored = mapper.readValue(
                    mapper.writeValueAsString(configuration), SpatialClipConfiguration.class);
            assertEquals(configuration, restored);
            assertEquals(combination == SpatialClipMaskCombination.DISSOLVE_ALL,
                    restored.dissolvesMasks());
        }
        assertThrows(Exception.class, () -> mapper.readValue(
                "{\"maskCombination\":\"AUTO\"}", SpatialClipConfiguration.class));
    }
}
