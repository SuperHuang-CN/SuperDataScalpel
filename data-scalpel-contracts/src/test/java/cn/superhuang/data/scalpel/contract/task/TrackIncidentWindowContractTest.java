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
        var scalars = new ArrayList<>(List.of(new TrackIncidentScalar(
                "elapsed", TrackIncidentScalar.Source.TRACK_DURATION)));
        var c = new TrackDetectIncidentsConfiguration("events",null,List.of("track"),"time",null,null,null,null,
                null,"out","id","flag","start","end","duration",null,TrackIncidentSemantics.CONDITION_LIFECYCLE,
                "status",List.of(),windows,scalars);
        windows.clear(); assertEquals(2,c.conditionWindows().size());
        scalars.clear(); assertEquals(1,c.conditionScalars().size());
        assertThrows(UnsupportedOperationException.class, () -> c.conditionWindows().clear());
        assertThrows(UnsupportedOperationException.class, () -> c.conditionScalars().clear());
        assertEquals(c,mapper.readValue(mapper.writeValueAsString(c),TrackDetectIncidentsConfiguration.class));
    }

    @Test void oldConstructorsAndMissingOrNullOptionsKeepNoWindows() throws Exception {
        assertEquals(List.of(),mapper.readValue("{}",TrackDetectIncidentsConfiguration.class).conditionWindows());
        assertEquals(List.of(),mapper.readValue("{\"conditionWindows\":null}",TrackDetectIncidentsConfiguration.class).conditionWindows());
        assertEquals(List.of(),mapper.readValue("{}",TrackDetectIncidentsConfiguration.class).conditionScalars());
        assertEquals(List.of(),mapper.readValue("{\"conditionScalars\":null}",TrackDetectIncidentsConfiguration.class).conditionScalars());
        var old = new TrackDetectIncidentsConfiguration("",null,List.of(),"",null,null,null,null,null,"","","","","","",null);
        assertEquals(List.of(),old.conditionWindows());
        assertEquals(List.of(),old.conditionScalars());
        var lifecycle = new TrackDetectIncidentsConfiguration("",null,List.of(),"",null,null,null,null,null,"","","","","","",null,
                TrackIncidentSemantics.CONDITION_LIFECYCLE,"status",List.of());
        assertEquals(List.of(),lifecycle.conditionWindows());
        assertEquals(List.of(),lifecycle.conditionScalars());
        var oldWindow = mapper.readValue("{\"bindingName\":\"past\",\"sourceColumnName\":\"speed\",\"kind\":\"MEAN\",\"startOffset\":-5,\"endOffset\":0}", TrackIncidentWindow.class);
        assertEquals(TrackIncidentWindow.Source.FIELD, oldWindow.effectiveSource());
    }

    @Test void trackScalarsRoundTripWithStableSources() throws Exception {
        for (var source : TrackIncidentScalar.Source.values()) {
            var scalar = new TrackIncidentScalar("metric", source);
            assertEquals(scalar, mapper.readValue(mapper.writeValueAsString(scalar), TrackIncidentScalar.class));
        }
        var x = new TrackIncidentScalar("previous_x", TrackIncidentScalar.Source.TRACK_POINT_X_AT, -1);
        assertTrue(x.isPointCoordinate());
        assertEquals(x, mapper.readValue(mapper.writeValueAsString(x), TrackIncidentScalar.class));
        assertNull(mapper.readValue("{\"bindingName\":\"index\",\"source\":\"TRACK_INDEX\"}",
                TrackIncidentScalar.class).offset());
        assertThrows(Exception.class, () -> mapper.readValue(
                "{\"bindingName\":\"metric\",\"source\":\"SCRIPT\"}", TrackIncidentScalar.class));
    }

    @Test void roundTripsTrackMotionSourcesWithoutAField() throws Exception {
        var distance = new TrackIncidentWindow("travelled", null, TrackIncidentWindow.Kind.SUM,
                -1, 2, TrackIncidentWindow.Source.TRACK_DISTANCE);
        assertEquals(distance, mapper.readValue(mapper.writeValueAsString(distance), TrackIncidentWindow.class));
        assertEquals(TrackIncidentWindow.Source.TRACK_DISTANCE, distance.effectiveSource());
        var speed = new TrackIncidentWindow("velocity", null, TrackIncidentWindow.Kind.MEAN,
                -1, 2, TrackIncidentWindow.Source.TRACK_SPEED);
        assertEquals(speed, mapper.readValue(mapper.writeValueAsString(speed), TrackIncidentWindow.class));
        assertEquals(TrackIncidentWindow.Source.TRACK_SPEED, speed.effectiveSource());
        var acceleration = new TrackIncidentWindow("acceleration", null, TrackIncidentWindow.Kind.MAX,
                -1, 2, TrackIncidentWindow.Source.TRACK_ACCELERATION);
        assertEquals(acceleration, mapper.readValue(mapper.writeValueAsString(acceleration), TrackIncidentWindow.class));
        assertEquals(TrackIncidentWindow.Source.TRACK_ACCELERATION, acceleration.effectiveSource());
        assertThrows(Exception.class, () -> mapper.readValue("{\"source\":\"TRACK_GEOMETRY\"}", TrackIncidentWindow.class));
    }

    @Test void rejectsMalformedShapesAndUnknownFunctionsButKeepsBlankDrafts() {
        for (String windows : List.of("{}","[null]","[1]","[{\"kind\":\"SCRIPT\"}]","[{\"startOffset\":2147483648}]"))
            assertThrows(Exception.class, () -> mapper.readValue("{\"conditionWindows\":" + windows + "}",TrackDetectIncidentsConfiguration.class));
        var c = mapper.readValue("{\"conditionWindows\":[{\"bindingName\":\"\",\"sourceColumnName\":\"\",\"kind\":null,\"startOffset\":null,\"endOffset\":null}]}",TrackDetectIncidentsConfiguration.class);
        assertEquals("",c.conditionWindows().getFirst().bindingName());
    }
}
