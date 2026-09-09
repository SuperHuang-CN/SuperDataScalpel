package cn.superhuang.data.scalpel.contract.task;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SpatialOverlayContractTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void roundTripsFiveModesWithExplicitPolicyAndDefensiveProjection() {
        for (var mode : SpatialOverlayOperation.values()) {
            var columns = new ArrayList<>(List.of(new JoinOutputColumn(JoinOutputColumnSource.LEFT, "id", "id", true)));
            var config = new SpatialOverlayConfiguration("left", "shape", "right", "shape", mode, "result", "shape", columns,
                    SpatialOverlayGeometryPolicy.FAMILY_2D);
            columns.clear();
            assertEquals(1, config.outputColumns().size());
            assertThrows(UnsupportedOperationException.class, () -> config.outputColumns().clear());
            assertEquals(config, mapper.readValue(mapper.writeValueAsString(config), SpatialOverlayConfiguration.class));
            assertTrue(config.requiresFamilyGeometryVersion());
        }
    }

    @Test
    void legacyConstructorAndMissingPolicyDoNotActivateNewSemantics() {
        var old = new SpatialOverlayConfiguration("", "", "", "", SpatialOverlayOperation.ERASE, "", "", List.of());
        assertFalse(old.usesFamilyGeometry());
        assertFalse(old.requiresFamilyGeometryVersion());
        var missing = mapper.readValue("{\"operation\":\"UNION\",\"outputColumns\":[]}", SpatialOverlayConfiguration.class);
        assertFalse(missing.usesFamilyGeometry());
        var newMode = mapper.readValue("{\"operation\":\"IDENTITY\",\"outputColumns\":[]}", SpatialOverlayConfiguration.class);
        assertTrue(newMode.usesFamilyGeometry());
        assertTrue(newMode.requiresFamilyGeometryVersion());
        assertThrows(Exception.class, () -> mapper.readValue("{\"geometryPolicy\":\"AUTO\"}", SpatialOverlayConfiguration.class));
    }
}
