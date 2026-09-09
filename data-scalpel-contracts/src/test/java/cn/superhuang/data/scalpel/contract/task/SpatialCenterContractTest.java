package cn.superhuang.data.scalpel.contract.task;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SpatialCenterContractTest {
    @Test void explicitFeatureProjectionCopiesAndRoundTripsDrafts() {
        var fields = new java.util.ArrayList<>(List.of(new SpatialCenterFeatureColumn("name", "", true), new SpatialCenterFeatureColumn("missing", "old", false)));
        var analysis = new SpatialCenterDispersionAnalysis("", SpatialCenterDispersionKind.CENTRAL_FEATURE, "shape", null, "result", fields);
        fields.clear(); assertEquals(2, analysis.centralFeatureColumns().size());
        assertThrows(UnsupportedOperationException.class, () -> analysis.centralFeatureColumns().clear());
        var mapper = new ObjectMapper();
        assertEquals(analysis, mapper.readValue(mapper.writeValueAsString(analysis), SpatialCenterDispersionAnalysis.class));
        assertNull(new SpatialCenterDispersionAnalysis("", null, "", null, "").centralFeatureColumns());
        assertNull(mapper.readValue("{}", SpatialCenterDispersionAnalysis.class).centralFeatureColumns());
        assertThrows(Exception.class, () -> mapper.readValue("{\"centralFeatureColumns\":{}}", SpatialCenterDispersionAnalysis.class));
        assertThrows(Exception.class, () -> mapper.readValue("{\"centralFeatureColumns\":[{\"sourceColumnName\":\"id\",\"outputColumnName\":\"id\"}]}", SpatialCenterDispersionAnalysis.class));
    }
    @Test void independentResultsRoundTripWithoutChangingOldConstructors() {
        var mapper = new ObjectMapper();
        var old = new SpatialCenterDispersionConfiguration("", "", null, List.of(), null, List.of(), "");
        assertFalse(old.separateResults()); assertNull(old.resultMode());
        assertNull(new SpatialCenterDispersionAnalysis("", null, "", null).outputTableName());
        for (var mode : SpatialCenterResultMode.values()) {
            var c = new SpatialCenterDispersionConfiguration("source", "shape", "id", List.of("group"), "weight",
                    List.of(new SpatialCenterDispersionAnalysis("id", SpatialCenterDispersionKind.MEAN_CENTER, "shape", null, "mean")), "wide", mode);
            assertEquals(c, mapper.readValue(mapper.writeValueAsString(c), SpatialCenterDispersionConfiguration.class));
        }
        assertNull(mapper.readValue("{}", SpatialCenterDispersionConfiguration.class).resultMode());
        assertThrows(Exception.class, () -> mapper.readValue("{\"resultMode\":\"AUTO\"}", SpatialCenterDispersionConfiguration.class));
    }
}
