package cn.superhuang.data.scalpel.contract.task;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SpatialNearestContractTest {
    private final ObjectMapper mapper = new ObjectMapper();
    @Test void matchingRoundTripsWithoutChangingAbsentLegacySemantics() {
        var old = new SpatialNearestConfiguration("", "", "", "", "", null, 1, null, null, false, "", "distance", SpatialDistanceUnit.METERS, null, List.of());
        assertNull(old.matching()); assertFalse(old.usesExactMatching()); assertFalse(old.outputsConnectionLines());
        assertNull(mapper.readValue("{\"nearestCount\":1,\"includeUnmatched\":false}", SpatialNearestConfiguration.class).matching());
        assertNull(mapper.readValue("{\"nearestCount\":1,\"includeUnmatched\":false,\"matching\":null}", SpatialNearestConfiguration.class).matching());
        for (var mode : new SpatialNearestMatchSemantics[]{null, SpatialNearestMatchSemantics.EXACT_DISTANCE, SpatialNearestMatchSemantics.LEGACY_KNN}) {
            var matching = new SpatialNearestMatching(mode, "id", new SpatialNearestConnectionLines(true, "lines", "shape", 10d, SpatialDistanceUnit.KILOMETERS));
            var current = new SpatialNearestConfiguration("", "", "", "", "", null, 1, null, null, false, "", "distance", SpatialDistanceUnit.METERS, null, List.of(), matching);
            assertEquals(current, mapper.readValue(mapper.writeValueAsString(current), SpatialNearestConfiguration.class));
            assertEquals(mode != SpatialNearestMatchSemantics.LEGACY_KNN, current.usesExactMatching());
            assertEquals(mode != SpatialNearestMatchSemantics.LEGACY_KNN, current.outputsConnectionLines());
        }
        assertThrows(Exception.class, () -> mapper.readValue("{\"matching\":{\"semantics\":\"AUTO\"}}", SpatialNearestConfiguration.class));
    }
}
