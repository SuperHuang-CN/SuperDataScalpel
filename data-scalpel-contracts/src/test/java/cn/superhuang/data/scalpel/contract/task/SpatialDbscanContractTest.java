package cn.superhuang.data.scalpel.contract.task;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

class SpatialDbscanContractTest {
    private final ObjectMapper mapper = new ObjectMapper();
    @Test void activeAndInactiveOptionsRoundTrip() {
        for (var mode : SpatialDbscanOptions.Mode.values()) {
            var options = new SpatialDbscanOptions(mode, "event_time", 30L, SpatialDurationUnit.MINUTES);
            var c = new SpatialPointClusterConfiguration("points", "shape", "id", SpatialDistanceMethod.PLANAR,
                    new SpatialPointClusterParameters.Dbscan(100, SpatialDistanceUnit.METERS, 5), "clusters", "cluster", "noise", options);
            assertEquals(c, mapper.readValue(mapper.writeValueAsString(c), SpatialPointClusterConfiguration.class));
        }
    }
    @Test void oldConstructorAndJsonDoNotActivateNewSemantics() {
        var c = new SpatialPointClusterConfiguration("", "", "", null, null, "", "", "");
        assertNull(c.dbscan());
        assertNull(mapper.readValue("{}", SpatialPointClusterConfiguration.class).dbscan());
    }
    @Test void incompleteBusinessDraftsAreDistinctFromMalformedStructure() {
        var draft = mapper.readValue("{\"mode\":null,\"searchDuration\":0}", SpatialDbscanOptions.class);
        assertNull(draft.mode()); assertFalse(draft.usesTime());
        for (String raw : java.util.List.of("[]", "{\"mode\":\"WINDOWS\"}", "{\"searchDurationUnit\":\"MONTHS\"}"))
            assertThrows(Exception.class, () -> mapper.readValue(raw, SpatialDbscanOptions.class));
    }
}
