package cn.superhuang.data.scalpel.contract.task;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpatialGroupByProximityContractTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void configurationAndNodeRoundTripAtCanvas472() {
        SpatialGroupByProximityConfiguration configuration =
                new SpatialGroupByProximityConfiguration(
                        "events", "shape",
                        SpatialGroupByProximitySpatialRelationship.NEAR_PLANAR,
                        500d, SpatialDistanceUnit.METERS,
                        new SpatialGroupByProximityTemporalCondition(
                                SpatialGroupByProximityTemporalRelationship.NEAR,
                                "started_at", "ended_at", 10L,
                                SpatialGroupByProximityTemporalUnit.MINUTES),
                        List.of(
                                new SpatialGroupByProximityAttributeCondition(
                                        "region", SpatialGroupByProximityAttributeRelationship.EQUALS,
                                        null),
                                new SpatialGroupByProximityAttributeCondition(
                                        "accuracy",
                                        SpatialGroupByProximityAttributeRelationship
                                                .ABSOLUTE_DIFFERENCE_AT_MOST,
                                        2d)),
                        "group_id", "event_groups");
        SpatialGroupByProximityNodeDefinition node =
                new SpatialGroupByProximityNodeDefinition(
                        UUID.randomUUID().toString(), "按邻近分组",
                        new CanvasNodeLayout(1d, 2d, 384d, 224d), configuration);

        CanvasNodeDefinition restored = mapper.readValue(
                mapper.writeValueAsString(node), CanvasNodeDefinition.class);

        assertEquals(node, restored);
        assertEquals(72, CanvasNodeType.SPATIAL_GROUP_BY_PROXIMITY.introducedInMinorVersion());
        assertEquals(77, CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION);
    }

    @Test
    void attributeConditionsAreDefensivelyCopied() {
        List<SpatialGroupByProximityAttributeCondition> attributes = new ArrayList<>();
        attributes.add(new SpatialGroupByProximityAttributeCondition(
                "region", SpatialGroupByProximityAttributeRelationship.EQUALS, null));
        SpatialGroupByProximityConfiguration configuration =
                new SpatialGroupByProximityConfiguration(
                        "events", "shape",
                        SpatialGroupByProximitySpatialRelationship.INTERSECTS,
                        null, null, null, attributes, "group_id", "groups");

        attributes.clear();

        assertEquals(1, configuration.attributeConditions().size());
    }
}
