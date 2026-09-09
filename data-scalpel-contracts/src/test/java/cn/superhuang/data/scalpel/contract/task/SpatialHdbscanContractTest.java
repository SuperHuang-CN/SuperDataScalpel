package cn.superhuang.data.scalpel.contract.task;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpatialHdbscanContractTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test void roundTripsDiagnosticsAndPreservesOldConvenienceConstructors() throws Exception {
        var options = new SpatialHdbscanOptions("p", "o", "e", "s");
        var c = new SpatialPointClusterConfiguration("points", "shape", "id", SpatialDistanceMethod.PLANAR,
                new SpatialPointClusterParameters.Hdbscan(2), "clusters", "cluster", "noise", null, options);
        assertEquals(c, mapper.readValue(mapper.writeValueAsString(c), SpatialPointClusterConfiguration.class));
        assertNull(new SpatialPointClusterConfiguration("", "", "", null, null, "", "", "").hdbscan());
        assertNull(new SpatialPointClusterConfiguration("", "", "", null, null, "", "", "", null).hdbscan());
        assertNull(mapper.readValue("{}", SpatialPointClusterConfiguration.class).hdbscan());
    }

    @Test void rejectsMalformedDiagnosticObjectsButAllowsIncompleteStrings() throws Exception {
        assertThrows(Exception.class, () -> mapper.readValue("{\"hdbscan\":[]}", SpatialPointClusterConfiguration.class));
        assertThrows(Exception.class, () -> mapper.readValue("{\"hdbscan\":{\"probabilityColumnName\":[]}}", SpatialPointClusterConfiguration.class));
        assertEquals("", mapper.readValue("{\"hdbscan\":{\"probabilityColumnName\":\"\"}}", SpatialPointClusterConfiguration.class).hdbscan().probabilityColumnName());
    }
}
