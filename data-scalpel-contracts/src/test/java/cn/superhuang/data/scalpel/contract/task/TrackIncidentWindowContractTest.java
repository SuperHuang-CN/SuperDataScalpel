package cn.superhuang.data.scalpel.contract.task;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TrackIncidentWindowContractTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test void preservesWindowOrderAndHalfOpenOffsetsInJson() {
        var windows = new ArrayList<>(List.of(new TrackIncidentWindow("past","speed",TrackIncidentWindow.Kind.MEAN,-5,0),
                new TrackIncidentWindow("next","speed",TrackIncidentWindow.Kind.FIRST,1,2)));
        var c = new TrackDetectIncidentsConfiguration("events",null,List.of("track"),"time",null,null,null,null,
                null,"out","id","flag","start","end","duration",null,TrackIncidentSemantics.CONDITION_LIFECYCLE,"status",List.of(),windows);
        windows.clear(); assertEquals(2,c.conditionWindows().size());
        assertThrows(UnsupportedOperationException.class, () -> c.conditionWindows().clear());
        assertEquals(c,mapper.readValue(mapper.writeValueAsString(c),TrackDetectIncidentsConfiguration.class));
    }

    @Test void oldConstructorsAndMissingOrNullOptionsKeepNoWindows() {
        assertEquals(List.of(),mapper.readValue("{}",TrackDetectIncidentsConfiguration.class).conditionWindows());
        assertEquals(List.of(),mapper.readValue("{\"conditionWindows\":null}",TrackDetectIncidentsConfiguration.class).conditionWindows());
        var old = new TrackDetectIncidentsConfiguration("",null,List.of(),"",null,null,null,null,null,"","","","","","",null);
        assertEquals(List.of(),old.conditionWindows());
        var lifecycle = new TrackDetectIncidentsConfiguration("",null,List.of(),"",null,null,null,null,null,"","","","","","",null,
                TrackIncidentSemantics.CONDITION_LIFECYCLE,"status",List.of());
        assertEquals(List.of(),lifecycle.conditionWindows());
    }

    @Test void rejectsMalformedShapesAndUnknownFunctionsButKeepsBlankDrafts() {
        for (String windows : List.of("{}","[null]","[1]","[{\"kind\":\"SCRIPT\"}]","[{\"startOffset\":2147483648}]"))
            assertThrows(Exception.class, () -> mapper.readValue("{\"conditionWindows\":" + windows + "}",TrackDetectIncidentsConfiguration.class));
        var c = mapper.readValue("{\"conditionWindows\":[{\"bindingName\":\"\",\"sourceColumnName\":\"\",\"kind\":null,\"startOffset\":null,\"endOffset\":null}]}",TrackDetectIncidentsConfiguration.class);
        assertEquals("",c.conditionWindows().getFirst().bindingName());
    }
}
