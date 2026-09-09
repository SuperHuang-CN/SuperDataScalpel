package cn.superhuang.data.scalpel.contract.task;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

class UnaryGeometryContractTest {
    private final ObjectMapper mapper = new ObjectMapper();
    @Test void policiesRoundTripAndOldConstructorsStayLegacy() {
        for (var policy : GeometryUnaryPolicy.values()) {
            var c = new GeometrySimplifyConfiguration("", "", "", "", null, null, null, policy);
            assertEquals(c, mapper.readValue(mapper.writeValueAsString(c), GeometrySimplifyConfiguration.class));
            var d = new GeometryDerivation("", null, "", "", policy);
            assertEquals(d, mapper.readValue(mapper.writeValueAsString(d), GeometryDerivation.class));
        }
        assertNull(new GeometrySimplifyConfiguration("", "", "", "", null, 0, null).geometryPolicy());
        assertNull(new GeometryDerivation("", null, "", "").geometryPolicy());
        assertNull(mapper.readValue("{}", GeometrySimplifyConfiguration.class).tolerance());
        assertThrows(Exception.class, () -> mapper.readValue("{\"geometryPolicy\":\"AUTO\"}", GeometryDerivation.class));
    }
}
