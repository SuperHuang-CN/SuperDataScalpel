package cn.superhuang.data.scalpel.contract.task;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TrackReconstructContractTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test void geodesicAreaSamplingIsOptionalTypedAndIndependentOfLineSettings() {
        assertNull(new TrackAreaGeometryOptions(true,TrackBufferMode.NONE,null,null,null).geodesicBoundary());
        assertNull(mapper.readValue("{}",TrackAreaGeometryOptions.class).geodesicBoundary());
        var area=new TrackAreaGeometryOptions(false,TrackBufferMode.NONE,null,null,null,List.of(),
                new TrackGeodesicAreaOptions(100d,SpatialDistanceUnit.METERS));
        assertEquals(area,mapper.readValue(mapper.writeValueAsString(area),TrackAreaGeometryOptions.class));
        for(String raw:List.of("[]","true","{\"maximumSegmentLengthUnit\":\"AUTO\"}"))
            assertThrows(Exception.class,()->mapper.readValue("{\"geodesicBoundary\":"+raw+"}",TrackAreaGeometryOptions.class));
    }

    @Test void areaOptionsAreOptionalAndPreserveInactiveBufferBranches() {
        assertNull(mapper.readValue("{}",TrackReconstructOptions.class).areaGeometry());
        assertNull(new TrackReconstructOptions(null,List.of(),null,null,null).areaGeometry());
        for (var mode : TrackBufferMode.values()) {
            var area = new TrackAreaGeometryOptions(false,mode,"radius","radius * 2",SpatialDistanceUnit.METERS);
            var c = new TrackReconstructOptions(null,List.of(),null,null,null,area);
            assertEquals(c,mapper.readValue(mapper.writeValueAsString(c),TrackReconstructOptions.class));
            assertFalse(area.active());
        }
        assertThrows(Exception.class, () -> mapper.readValue("{\"areaGeometry\":{\"bufferMode\":\"AUTO\"}}",TrackReconstructOptions.class));
        assertThrows(Exception.class, () -> mapper.readValue("{\"areaGeometry\":[]}",TrackReconstructOptions.class));
    }

    @Test void bufferWindowBindingsRoundTripAndCopyTheirInputList() {
        var bindings = new ArrayList<>(List.of(new TrackBufferWindowBinding("history","radius",-3,-1,TrackSummaryStatisticKind.MEAN)));
        var options = new TrackAreaGeometryOptions(false,TrackBufferMode.FIELD,"radius","history",SpatialDistanceUnit.METERS,bindings);
        bindings.clear();assertEquals(1,options.windowBindings().size());
        assertEquals(options,mapper.readValue(mapper.writeValueAsString(options),TrackAreaGeometryOptions.class));
        assertThrows(UnsupportedOperationException.class, () -> options.windowBindings().clear());
        assertTrue(mapper.readValue("{}",TrackAreaGeometryOptions.class).windowBindings().isEmpty());
        assertTrue(mapper.readValue("{\"windowBindings\":null}",TrackAreaGeometryOptions.class).windowBindings().isEmpty());
        assertThrows(Exception.class, () -> mapper.readValue("{\"windowBindings\":[{\"statistic\":\"AUTO\"}]}",TrackAreaGeometryOptions.class));
    }

    @Test
    void pathOptionsRoundTripWithoutOptingOldOrInactiveDefinitionsIn() {
        var path = new TrackPathGeometryOptions(TrackPathGeometryMode.METHOD_PATH, 10d, SpatialDistanceUnit.KILOMETERS);
        var options = new TrackReconstructOptions(null, List.of(), null, null, path);
        assertEquals(options, mapper.readValue(mapper.writeValueAsString(options), TrackReconstructOptions.class));
        assertNull(new TrackReconstructOptions(null, null, null, null).pathGeometry());
        assertNull(mapper.readValue("{}", TrackReconstructOptions.class).pathGeometry());
        assertThrows(Exception.class, () -> mapper.readValue("{\"pathGeometry\":{\"mode\":\"AUTO\"}}", TrackReconstructOptions.class));
        for (String semantics : List.of("ORDERED_SEGMENTS", "LEGACY_POINTS")) {
            var config = mapper.readValue("{\"reconstruction\":{\"semantics\":\"" + semantics + "\",\"pathGeometry\":{}}}", TrackReconstructConfiguration.class);
            assertEquals(semantics.equals("ORDERED_SEGMENTS"), config.usesMethodPath());
        }
    }

    @Test
    void roundTripsOptionsAndPreservesDisabledExpressions() {
        var fields = new ArrayList<>(List.of("seq"));
        var bindings = new ArrayList<>(List.of(new TrackFieldWindowBinding("prior", "speed", -1)));
        var rule = new TrackSplitExpression("prior * 2 < speed", bindings, false);
        var options = new TrackReconstructOptions(TrackReconstructSemantics.ORDERED_SEGMENTS, fields, TrackSplitBoundaryOption.START_NEXT, rule);
        fields.clear(); bindings.clear();
        assertEquals(options, mapper.readValue(mapper.writeValueAsString(options), TrackReconstructOptions.class));
        assertEquals(1, options.orderByColumns().size());
        assertEquals(1, rule.bindings().size());
        assertFalse(rule.active());
        assertThrows(UnsupportedOperationException.class, () -> options.orderByColumns().clear());
        assertTrue(new TrackSplitExpression("true", List.of()).active());
    }

    @Test
    void oldDefinitionsDoNotChangeBehaviorAndUnknownOptionsFailParsing() {
        var old = new TrackReconstructConfiguration("", "", List.of(), "", null, null, List.of(), "", "", "", "", "");
        assertNull(old.reconstruction());
        assertFalse(old.usesOrderedReconstruction());
        assertEquals(old, mapper.readValue(mapper.writeValueAsString(old), TrackReconstructConfiguration.class));
        assertThrows(Exception.class, () -> mapper.readValue("{\"splitBoundaryOption\":\"AUTO\"}", TrackReconstructOptions.class));
        assertThrows(Exception.class, () -> mapper.readValue("{\"bindings\":[true]}", TrackSplitExpression.class));
    }

    @Test
    void permitsScalarExpressionButNeverSqlOrUserWindowMembership() {
        assertNull(TrackSplitExpressionPolicy.findViolation("prior * 2 < speed AND name <> 'OVER'"));
        assertNull(TrackSplitExpressionPolicy.findViolation("unix_timestamp(event_time) - unix_timestamp(previous_time) > 60"));
        for (String expression : List.of("select true", "true; false", "true --comment", "lag(x) over (order by y)", ""))
            assertNotNull(TrackSplitExpressionPolicy.findViolation(expression));
        assertNull(FilterSqlExpressionPolicy.findViolation("lag(x) over (order by y) > 1"));
    }
}
