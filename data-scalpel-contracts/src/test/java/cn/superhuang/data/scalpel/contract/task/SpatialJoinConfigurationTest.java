package cn.superhuang.data.scalpel.contract.task;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpatialJoinConfigurationTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void roundTripsOutputProjectionAndAttributeConditionsAndDefensivelyCopiesArrays() throws Exception {
        List<JoinOutputColumn> outputColumns = new ArrayList<>();
        outputColumns.add(new JoinOutputColumn(
                JoinOutputColumnSource.LEFT, "id", "id", true));
        outputColumns.add(new JoinOutputColumn(
                JoinOutputColumnSource.RIGHT, "id", "districts_id", true));
        List<JoinCondition> attributeConditions = new ArrayList<>();
        attributeConditions.add(new JoinCondition("tenant_id", JoinOperator.EQUALS, "tenant_id"));
        SpatialJoinConfiguration source = new SpatialJoinConfiguration(
                "orders",
                "districts",
                "orders_with_district",
                JoinType.LEFT,
                List.of(new SpatialJoinCondition("shape", SpatialPredicate.WITHIN, "boundary")),
                attributeConditions,
                outputColumns,
                SpatialJoinOperation.JOIN_ONE_TO_MANY
        );
        attributeConditions.clear();
        outputColumns.clear();

        SpatialJoinConfiguration parsed = objectMapper.readValue(
                objectMapper.writeValueAsString(source),
                SpatialJoinConfiguration.class
        );

        assertEquals(source, parsed);
        assertEquals(JoinType.LEFT, parsed.joinType());
        assertEquals(1, parsed.attributeConditions().size());
        assertEquals(2, parsed.outputColumns().size());
        assertEquals(SpatialJoinOperation.JOIN_ONE_TO_MANY, parsed.joinOperation());
        assertEquals(SpatialJoinOperation.JOIN_ONE_TO_MANY, parsed.effectiveJoinOperation());
    }

    @Test
    void legacyConstructorAndMissingProjectionKeepLegacyNull() throws Exception {
        SpatialJoinConfiguration legacy = new SpatialJoinConfiguration(
                "orders",
                "districts",
                "orders_with_district",
                JoinType.INNER,
                List.of(new SpatialJoinCondition("shape", SpatialPredicate.WITHIN, "boundary"))
        );
        SpatialJoinConfiguration missing = objectMapper.readValue("""
                {
                  "leftTableName": "orders",
                  "rightTableName": "districts",
                  "outputTableName": "orders_with_district",
                  "joinType": "INNER",
                  "conditions": []
                }
                """, SpatialJoinConfiguration.class);
        SpatialJoinConfiguration explicitNull = objectMapper.readValue("""
                {
                  "leftTableName": "orders",
                  "rightTableName": "districts",
                  "outputTableName": "orders_with_district",
                  "joinType": "INNER",
                  "conditions": [],
                  "joinOperation": null
                }
                """, SpatialJoinConfiguration.class);

        assertNull(legacy.outputColumns());
        assertNull(legacy.attributeConditions());
        assertNull(missing.outputColumns());
        assertNull(missing.attributeConditions());
        assertNull(legacy.joinOperation());
        assertNull(missing.joinOperation());
        assertNull(explicitNull.joinOperation());
        assertEquals(SpatialJoinOperation.JOIN_ONE_TO_MANY, legacy.effectiveJoinOperation());
        assertEquals(SpatialJoinOperation.JOIN_ONE_TO_MANY, missing.effectiveJoinOperation());
        assertEquals(SpatialJoinOperation.JOIN_ONE_TO_MANY, explicitNull.effectiveJoinOperation());
    }

    @Test
    void rejectsUnknownOutputColumnSide() {
        String json = """
                {
                  "leftTableName": "orders",
                  "rightTableName": "districts",
                  "outputTableName": "orders_with_district",
                  "joinType": "INNER",
                  "conditions": [],
                  "outputColumns": [{
                    "sourceSide": "MIDDLE",
                    "sourceColumnName": "id",
                    "outputColumnName": "id",
                    "included": true
                  }]
                }
                """;

        assertThrows(Exception.class, () -> objectMapper.readValue(json, SpatialJoinConfiguration.class));
    }

    @Test
    void roundTripsOneToOneOptionsAndDefensivelyCopiesNestedArrays() throws Exception {
        List<SpatialJoinSummaryStatistic> statistics = new ArrayList<>();
        statistics.add(new SpatialJoinSummaryStatistic(
                "11111111-1111-4111-8111-111111111111",
                SpatialJoinSummaryStatisticKind.MEAN,
                "amount",
                "amount_mean"
        ));
        List<SortField> stableOrder = new ArrayList<>();
        stableOrder.add(new SortField("district_id", SortDirection.ASC, NullOrdering.LAST));
        SpatialJoinConfiguration source = new SpatialJoinConfiguration(
                "orders",
                "districts",
                "orders_with_district",
                JoinType.LEFT,
                List.of(new SpatialJoinCondition("shape", SpatialPredicate.WITHIN, "boundary")),
                List.of(),
                List.of(new JoinOutputColumn(
                        JoinOutputColumnSource.LEFT, "id", "id", true)),
                SpatialJoinOperation.JOIN_ONE_TO_ONE,
                new SpatialJoinOneToOneOptions(
                        SpatialJoinOneToOneMode.KEEP_ONE,
                        "join_count",
                        statistics,
                        new SpatialJoinKeepRule(
                                SpatialJoinKeepStrategy.LARGEST,
                                "amount",
                                stableOrder
                        )
                )
        );
        statistics.clear();
        stableOrder.clear();

        SpatialJoinConfiguration parsed = objectMapper.readValue(
                objectMapper.writeValueAsString(source),
                SpatialJoinConfiguration.class
        );

        assertEquals(source, parsed);
        assertEquals(1, parsed.oneToOne().summaryStatistics().size());
        assertEquals(1, parsed.oneToOne().keepRule().stableOrder().size());
        assertEquals(SpatialJoinOperation.JOIN_ONE_TO_ONE, parsed.effectiveJoinOperation());
    }

    @Test
    void rejectsUnsupportedFutureJoinOperation() {
        String json = """
                {
                  "leftTableName": "orders",
                  "rightTableName": "districts",
                  "outputTableName": "orders_with_district",
                  "joinType": "INNER",
                  "conditions": [],
                  "joinOperation": "JOIN_MANY_TO_ONE"
                }
                """;

        assertThrows(Exception.class, () -> objectMapper.readValue(json, SpatialJoinConfiguration.class));
    }

    @Test
    void roundTripsTemporalConditionAndKeepsMissingOrNullCompatible() throws Exception {
        SpatialJoinTemporalCondition temporal = new SpatialJoinTemporalCondition(
                SpatialJoinTemporalRelationship.NEAR_BEFORE,
                "target_start",
                "target_end",
                "join_start",
                null,
                15L,
                SpatialDurationUnit.MINUTES
        );
        SpatialJoinConfiguration source = new SpatialJoinConfiguration(
                "orders",
                "districts",
                "orders_with_district",
                JoinType.INNER,
                List.of(new SpatialJoinCondition(
                        "shape", SpatialPredicate.WITHIN, "boundary")),
                List.of(),
                List.of(new JoinOutputColumn(
                        JoinOutputColumnSource.LEFT, "id", "id", true)),
                SpatialJoinOperation.JOIN_ONE_TO_MANY,
                null,
                temporal
        );

        SpatialJoinConfiguration parsed = objectMapper.readValue(
                objectMapper.writeValueAsString(source), SpatialJoinConfiguration.class);
        SpatialJoinConfiguration missing = objectMapper.readValue("""
                {
                  "leftTableName": "orders",
                  "rightTableName": "districts",
                  "outputTableName": "orders_with_district",
                  "joinType": "INNER",
                  "conditions": []
                }
                """, SpatialJoinConfiguration.class);
        SpatialJoinConfiguration explicitNull = objectMapper.readValue("""
                {
                  "leftTableName": "orders",
                  "rightTableName": "districts",
                  "outputTableName": "orders_with_district",
                  "joinType": "INNER",
                  "conditions": [],
                  "temporalCondition": null
                }
                """, SpatialJoinConfiguration.class);

        assertEquals(source, parsed);
        assertEquals(temporal, parsed.temporalCondition());
        assertTrue(parsed.temporalCondition().usesNearDistance());
        assertNull(missing.temporalCondition());
        assertNull(explicitNull.temporalCondition());
    }

    @Test
    void reportsWhichTemporalRelationshipsUseNearDistanceAndRejectsUnknownRelationship() {
        for (SpatialJoinTemporalRelationship relationship
                : SpatialJoinTemporalRelationship.values()) {
            SpatialJoinTemporalCondition condition = new SpatialJoinTemporalCondition(
                    relationship, "left_time", null, "right_time", null,
                    1L, SpatialDurationUnit.SECONDS);
            boolean expected = relationship == SpatialJoinTemporalRelationship.NEAR
                    || relationship == SpatialJoinTemporalRelationship.NEAR_BEFORE
                    || relationship == SpatialJoinTemporalRelationship.NEAR_AFTER;
            assertEquals(expected, condition.usesNearDistance(), relationship.name());
        }
        SpatialJoinTemporalCondition incomplete = new SpatialJoinTemporalCondition(
                null, "left_time", null, "right_time", null,
                null, null);
        assertFalse(incomplete.usesNearDistance());

        String unknown = """
                {
                  "relationship": "AROUND",
                  "leftStartColumnName": "left_time",
                  "leftEndColumnName": null,
                  "rightStartColumnName": "right_time",
                  "rightEndColumnName": null,
                  "nearDistance": 1,
                  "nearDistanceUnit": "SECONDS"
                }
                """;
        assertThrows(Exception.class,
                () -> objectMapper.readValue(unknown, SpatialJoinTemporalCondition.class));
    }

    @Test
    void roundTripsSpatialNearAndDistanceOutputAndKeepsMissingValuesCompatible() throws Exception {
        SpatialJoinSpatialNearCondition near = new SpatialJoinSpatialNearCondition(
                "shape", "boundary", SpatialDistanceMethod.GEODESIC,
                2.5d, SpatialDistanceUnit.KILOMETERS);
        SpatialJoinDistanceOutput distanceOutput = new SpatialJoinDistanceOutput(
                true, "spatial_distance", SpatialDistanceUnit.METERS,
                "time_difference", SpatialDurationUnit.SECONDS);
        SpatialJoinConfiguration source = new SpatialJoinConfiguration(
                "orders", "districts", "matched", JoinType.LEFT, List.of(),
                List.of(), List.of(new JoinOutputColumn(
                JoinOutputColumnSource.LEFT, "id", "id", true)),
                SpatialJoinOperation.JOIN_ONE_TO_MANY, null,
                new SpatialJoinTemporalCondition(
                        SpatialJoinTemporalRelationship.NEAR,
                        "start_time", null, "start_time", null,
                        5L, SpatialDurationUnit.MINUTES),
                near, distanceOutput);

        SpatialJoinConfiguration parsed = objectMapper.readValue(
                objectMapper.writeValueAsString(source), SpatialJoinConfiguration.class);
        SpatialJoinConfiguration missing = objectMapper.readValue("""
                {
                  "leftTableName":"orders",
                  "rightTableName":"districts",
                  "outputTableName":"matched",
                  "joinType":"INNER",
                  "conditions":[]
                }
                """, SpatialJoinConfiguration.class);

        assertEquals(source, parsed);
        assertEquals(near, parsed.spatialNear());
        assertEquals(distanceOutput, parsed.distanceOutput());
        assertNull(missing.spatialNear());
        assertNull(missing.distanceOutput());
    }
}
