package cn.superhuang.data.scalpel.business.task.service;

import cn.superhuang.data.scalpel.contract.task.*;
import cn.superhuang.data.scalpel.contract.type.CrsReference;
import cn.superhuang.data.scalpel.contract.type.CoordinateDimension;
import cn.superhuang.data.scalpel.contract.type.GeometryKind;
import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.contract.type.PlatformTypeDefinition;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CanvasDefinitionValidatorTest {
    private final CanvasDefinitionUpgrader upgrader = new CanvasDefinitionUpgrader();
    private final CanvasDefinitionValidator validator = new CanvasDefinitionValidator(upgrader);

    @Test void spatialClipExplicitGeometryPolicyRequires51WhileNullKeepsLegacySemantics() {
        for (SpatialClipGeometryPolicy policy : java.util.Arrays.asList(
                null,
                SpatialClipGeometryPolicy.SOURCE_FAMILY_2D,
                SpatialClipGeometryPolicy.LEGACY_ANY_DIMENSION)) {
            var configuration = new SpatialClipConfiguration(
                    "roads", "districts", "clipped",
                    "shape", "boundary", "clipped_shape", policy);
            var node = new SpatialClipNodeDefinition(
                    UUID.randomUUID().toString(), "Clip",
                    new CanvasNodeLayout(0d, 0d, 368d, 216d), configuration);
            var old = new CanvasDefinition(4, 50, List.of(node), List.of());
            if (policy == null) {
                assertDoesNotThrow(() -> validator.validate(old));
            } else {
                var error = assertThrows(ResponseStatusException.class,
                        () -> validator.validate(old));
                org.junit.jupiter.api.Assertions.assertTrue(error.getReason()
                        .contains("SPATIAL_CLIP_GEOMETRY_POLICY_REQUIRE_SCHEMA_VERSION"));
                org.junit.jupiter.api.Assertions.assertTrue(error.getReason()
                        .contains("configuration.geometryPolicy"));
            }
            assertDoesNotThrow(() -> validator.validate(
                    new CanvasDefinition(4, 51, List.of(node), List.of())));
        }
    }

    @Test void spatialClipExplicitMaskCombinationRequires77BeforeUpgradeWhileNullKeepsLegacySemantics() {
        for (SpatialClipMaskCombination combination : java.util.Arrays.asList(
                null,
                SpatialClipMaskCombination.DISSOLVE_ALL,
                SpatialClipMaskCombination.PAIRWISE)) {
            var configuration = new SpatialClipConfiguration(
                    "roads", "districts", "clipped",
                    "shape", "boundary", "clipped_shape",
                    SpatialClipGeometryPolicy.SOURCE_FAMILY_2D, combination);
            var node = new SpatialClipNodeDefinition(
                    UUID.randomUUID().toString(), "Clip",
                    new CanvasNodeLayout(0d, 0d, 368d, 216d), configuration);
            var old = new CanvasDefinition(4, 76, List.of(node), List.of());
            if (combination == null) {
                assertDoesNotThrow(() -> upgrader.upgradeToCurrent(old));
                assertDoesNotThrow(() -> validator.validate(old));
            } else {
                var upgradeError = assertThrows(ResponseStatusException.class,
                        () -> upgrader.upgradeToCurrent(old));
                org.junit.jupiter.api.Assertions.assertTrue(upgradeError.getReason()
                        .contains("SPATIAL_CLIP_MASK_COMBINATION_REQUIRE_SCHEMA_VERSION"));
                org.junit.jupiter.api.Assertions.assertTrue(upgradeError.getReason()
                        .contains("configuration.maskCombination"));

                var validationError = assertThrows(ResponseStatusException.class,
                        () -> validator.validate(old));
                org.junit.jupiter.api.Assertions.assertTrue(validationError.getReason()
                        .contains("SPATIAL_CLIP_MASK_COMBINATION_REQUIRE_SCHEMA_VERSION"));
            }
            assertDoesNotThrow(() -> validator.validate(
                    new CanvasDefinition(4, 77, List.of(node), List.of())));
        }
    }

    @Test void spatialMeasureExplicitUnitsRequire50WhileNullKeepsLegacySemantics() {
        for (SpatialDistanceUnit unit : java.util.Arrays.asList(
                null, SpatialDistanceUnit.KILOMETERS, SpatialDistanceUnit.FEET_US)) {
            var measurement = new SpatialMeasurement.Length(
                    "shape", SpatialMeasureMode.SPHEROID, "length", unit);
            var configuration = new SpatialMeasureConfiguration(
                    "source", "output", List.of(measurement));
            var node = new SpatialMeasureNodeDefinition(
                    UUID.randomUUID().toString(), "Measure",
                    new CanvasNodeLayout(0d, 0d, 344d, 216d), configuration);
            var old = new CanvasDefinition(4, 49, List.of(node), List.of());
            if (unit == null) {
                assertDoesNotThrow(() -> validator.validate(old));
            } else {
                var error = assertThrows(ResponseStatusException.class, () -> validator.validate(old));
                org.junit.jupiter.api.Assertions.assertTrue(error.getReason()
                        .contains("SPATIAL_MEASURE_UNIT_REQUIRE_SCHEMA_VERSION"));
                org.junit.jupiter.api.Assertions.assertTrue(error.getReason()
                        .contains("configuration.measurements[0].outputUnit"));
            }
            assertDoesNotThrow(() -> validator.validate(
                    new CanvasDefinition(4, 50, List.of(node), List.of())));
        }
    }

    @Test void geometryBufferExplicitUnitsRequire49WhileNullKeepsLegacySemantics() {
        for (SpatialDistanceUnit unit : java.util.Arrays.asList(null, SpatialDistanceUnit.METERS, SpatialDistanceUnit.FEET_US)) {
            var configuration = new GeometryBufferConfiguration("", "", "", "", 1d,
                    SpatialMeasureMode.SPHEROID, unit);
            var node = new GeometryBufferNodeDefinition(UUID.randomUUID().toString(), "Buffer",
                    new CanvasNodeLayout(0d, 0d, 320d, 188d), configuration);
            var old = new CanvasDefinition(4, 48, List.of(node), List.of());
            if (unit == null) {
                assertDoesNotThrow(() -> validator.validate(old));
            } else {
                var error = assertThrows(ResponseStatusException.class, () -> validator.validate(old));
                org.junit.jupiter.api.Assertions.assertTrue(error.getReason().contains("GEOMETRY_BUFFER_UNIT_REQUIRE_SCHEMA_VERSION"));
                org.junit.jupiter.api.Assertions.assertTrue(error.getReason().contains("configuration.distanceUnit"));
            }
            assertDoesNotThrow(() -> validator.validate(new CanvasDefinition(4, 49, List.of(node), List.of())));
        }
    }

    @Test void geometryBufferDistanceSourcesRequire52WhileMissingFieldsKeepLegacySemantics() {
        for (GeometryBufferDistanceSource source : java.util.Arrays.asList(
                null,
                GeometryBufferDistanceSource.CONSTANT,
                GeometryBufferDistanceSource.FIELD,
                GeometryBufferDistanceSource.EXPRESSION)) {
            var configuration = new GeometryBufferConfiguration(
                    "", "", "", "", 1d, SpatialMeasureMode.PLANAR, null,
                    source,
                    source == GeometryBufferDistanceSource.FIELD ? "radius" : null,
                    source == GeometryBufferDistanceSource.EXPRESSION ? "radius * 2" : null);
            var node = new GeometryBufferNodeDefinition(UUID.randomUUID().toString(), "Buffer",
                    new CanvasNodeLayout(0d, 0d, 320d, 188d), configuration);
            var old = new CanvasDefinition(4, 51, List.of(node), List.of());
            if (source == null) {
                assertDoesNotThrow(() -> validator.validate(old));
            } else {
                var error = assertThrows(ResponseStatusException.class, () -> validator.validate(old));
                org.junit.jupiter.api.Assertions.assertTrue(error.getReason()
                        .contains("GEOMETRY_BUFFER_DISTANCE_SOURCE_REQUIRE_SCHEMA_VERSION"));
                org.junit.jupiter.api.Assertions.assertTrue(error.getReason()
                        .contains("configuration.distanceSource"));
            }
            assertDoesNotThrow(() -> validator.validate(
                    new CanvasDefinition(4, 52, List.of(node), List.of())));
        }
    }

    @Test
    void spatialAggregateDissolveRequires53IncludingInactiveDrafts() {
        var dissolve = new SpatialAggregateDissolveOptions(
                false,
                true,
                "saved_count",
                List.of(new SpatialAggregateStatistic(
                        "saved-draft-id",
                        SpatialAggregateStatisticKind.SUM,
                        "saved_value",
                        "saved_sum"))
        );
        var explicit = new SpatialAggregateNodeDefinition(
                UUID.randomUUID().toString(),
                "Dissolve",
                new CanvasNodeLayout(0d, 0d, 352d, 224d),
                new SpatialAggregateConfiguration(
                        "parcels", "districts", List.of(), List.of(), dissolve)
        );
        var legacy = new SpatialAggregateNodeDefinition(
                UUID.randomUUID().toString(),
                "Legacy aggregate",
                new CanvasNodeLayout(0d, 0d, 352d, 224d),
                new SpatialAggregateConfiguration(
                        "parcels", "districts", List.of(), List.of())
        );

        var error = assertThrows(ResponseStatusException.class, () -> validator.validate(
                new CanvasDefinition(4, 52, List.of(explicit), List.of())));
        org.junit.jupiter.api.Assertions.assertTrue(error.getReason()
                .contains("SPATIAL_AGGREGATE_DISSOLVE_REQUIRE_SCHEMA_VERSION"));
        org.junit.jupiter.api.Assertions.assertTrue(error.getReason()
                .contains("configuration.dissolve"));
        assertDoesNotThrow(() -> validator.validate(
                new CanvasDefinition(4, 53, List.of(explicit), List.of())));
        assertDoesNotThrow(() -> validator.validate(
                new CanvasDefinition(4, 52, List.of(legacy), List.of())));
    }

    @Test
    void spatialAggregateDissolveGroupingRequires61IncludingInactiveDrafts() {
        var dissolve = new SpatialAggregateDissolveOptions(
                false,
                true,
                "saved_count",
                List.of(),
                SpatialAggregateDissolveGroupingMode.CONNECTED_COMPONENTS
        );
        var node = new SpatialAggregateNodeDefinition(
                UUID.randomUUID().toString(),
                "Connected Dissolve",
                new CanvasNodeLayout(0d, 0d, 352d, 224d),
                new SpatialAggregateConfiguration(
                        "parcels", "districts", List.of(), List.of(), dissolve));

        var error = assertThrows(ResponseStatusException.class, () -> validator.validate(
                new CanvasDefinition(4, 60, List.of(node), List.of())));
        org.junit.jupiter.api.Assertions.assertTrue(error.getReason()
                .contains("SPATIAL_DISSOLVE_GROUPING_MODE_REQUIRE_SCHEMA_VERSION"));
        org.junit.jupiter.api.Assertions.assertTrue(error.getReason()
                .contains("configuration.dissolve.groupingMode"));
        assertDoesNotThrow(() -> validator.validate(
                new CanvasDefinition(4, 61, List.of(node), List.of())));
    }

    @Test
    void unionMergeLayersRequires62WhileLegacyNullRemainsCompatible() {
        var explicit = new UnionNodeDefinition(
                UUID.randomUUID().toString(), "Merge Layers",
                new CanvasNodeLayout(0d, 0d, 336d, 204d),
                new UnionConfiguration(
                        List.of("base", "merge"), "merged", UnionMode.ALL,
                        List.of(new UnionMergeTable("merge", List.of(
                                new UnionMergeFieldRule(
                                        "status", UnionMergeFieldAction.MATCH, "code")
                        )))
                )
        );
        var legacy = new UnionNodeDefinition(
                UUID.randomUUID().toString(), "Legacy Union",
                new CanvasNodeLayout(0d, 0d, 336d, 204d),
                new UnionConfiguration(
                        List.of("base", "merge"), "merged", UnionMode.ALL)
        );

        var error = assertThrows(ResponseStatusException.class, () -> validator.validate(
                new CanvasDefinition(4, 61, List.of(explicit), List.of())));
        org.junit.jupiter.api.Assertions.assertTrue(error.getReason()
                .contains("UNION_MERGE_LAYERS_REQUIRE_SCHEMA_VERSION"));
        assertDoesNotThrow(() -> validator.validate(
                new CanvasDefinition(4, 62, List.of(explicit), List.of())));
        assertDoesNotThrow(() -> validator.validate(
                new CanvasDefinition(4, 61, List.of(legacy), List.of())));
    }

    @Test
    void spatialJoinOutputProjectionRequires54WhileLegacyNullRemainsCompatible() {
        var columns = List.of(new JoinOutputColumn(
                JoinOutputColumnSource.RIGHT, "id", "districts_id", true));
        var explicit = spatialJoinNode(columns);
        var legacy = spatialJoinNode(null);

        var error = assertThrows(ResponseStatusException.class, () -> validator.validate(
                new CanvasDefinition(4, 53, List.of(explicit), List.of())));
        org.junit.jupiter.api.Assertions.assertTrue(error.getReason()
                .contains("SPATIAL_JOIN_OUTPUT_COLUMNS_REQUIRE_SCHEMA_VERSION"));
        org.junit.jupiter.api.Assertions.assertTrue(error.getReason()
                .contains("configuration.outputColumns"));
        assertDoesNotThrow(() -> validator.validate(
                new CanvasDefinition(4, 54, List.of(explicit), List.of())));
        assertDoesNotThrow(() -> validator.validate(
                new CanvasDefinition(4, 53, List.of(legacy), List.of())));
    }

    @Test
    void spatialJoinAttributeConditionsRequire55WhileLegacyNullRemainsCompatible() {
        var attributeConditions = List.of(new JoinCondition(
                "tenant_id", JoinOperator.EQUALS, "tenant_id"));
        var explicit = spatialJoinNode(null, attributeConditions);
        var legacy = spatialJoinNode(null, null);

        var error = assertThrows(ResponseStatusException.class, () -> validator.validate(
                new CanvasDefinition(4, 54, List.of(explicit), List.of())));
        org.junit.jupiter.api.Assertions.assertTrue(error.getReason()
                .contains("SPATIAL_JOIN_ATTRIBUTE_CONDITIONS_REQUIRE_SCHEMA_VERSION"));
        org.junit.jupiter.api.Assertions.assertTrue(error.getReason()
                .contains("configuration.attributeConditions"));
        assertDoesNotThrow(() -> validator.validate(
                new CanvasDefinition(4, 55, List.of(explicit), List.of())));
        assertDoesNotThrow(() -> validator.validate(
                new CanvasDefinition(4, 54, List.of(legacy), List.of())));
    }

    @Test
    void spatialJoinKeepAllRequires56WhileInnerRemainsCompatible() {
        var keepAll = spatialJoinNode(null, null, JoinType.LEFT);
        var inner = spatialJoinNode(null, null, JoinType.INNER);

        var error = assertThrows(ResponseStatusException.class, () -> validator.validate(
                new CanvasDefinition(4, 55, List.of(keepAll), List.of())));
        org.junit.jupiter.api.Assertions.assertTrue(error.getReason()
                .contains("SPATIAL_JOIN_KEEP_ALL_REQUIRE_SCHEMA_VERSION"));
        org.junit.jupiter.api.Assertions.assertTrue(error.getReason()
                .contains("configuration.joinType"));
        assertDoesNotThrow(() -> validator.validate(
                new CanvasDefinition(4, 56, List.of(keepAll), List.of())));
        assertDoesNotThrow(() -> validator.validate(
                new CanvasDefinition(4, 55, List.of(inner), List.of())));
    }

    @Test
    void spatialJoinExplicitOperationRequires57WhileNullRemainsCompatible() {
        var explicit = spatialJoinNode(
                null, null, JoinType.INNER, SpatialJoinOperation.JOIN_ONE_TO_MANY);
        var legacy = spatialJoinNode(null, null, JoinType.INNER, null);

        var error = assertThrows(ResponseStatusException.class, () -> validator.validate(
                new CanvasDefinition(4, 56, List.of(explicit), List.of())));
        org.junit.jupiter.api.Assertions.assertTrue(error.getReason()
                .contains("SPATIAL_JOIN_OPERATION_REQUIRE_SCHEMA_VERSION"));
        org.junit.jupiter.api.Assertions.assertTrue(error.getReason()
                .contains("configuration.joinOperation"));
        assertDoesNotThrow(() -> validator.validate(
                new CanvasDefinition(4, 57, List.of(explicit), List.of())));
        assertDoesNotThrow(() -> validator.validate(
                new CanvasDefinition(4, 56, List.of(legacy), List.of())));
    }

    @Test
    void spatialJoinOneToOneRequires58IncludingInactiveDrafts() {
        var oneToOne = new SpatialJoinOneToOneOptions(
                SpatialJoinOneToOneMode.SUMMARIZE_MATCHES,
                "join_count",
                List.of(),
                null
        );
        var active = spatialJoinNode(
                null, null, JoinType.INNER, SpatialJoinOperation.JOIN_ONE_TO_ONE, oneToOne);
        var inactiveDraft = spatialJoinNode(
                null, null, JoinType.INNER, SpatialJoinOperation.JOIN_ONE_TO_MANY, oneToOne);
        var legacy = spatialJoinNode(
                null, null, JoinType.INNER, SpatialJoinOperation.JOIN_ONE_TO_MANY, null);

        for (var node : List.of(active, inactiveDraft)) {
            var error = assertThrows(ResponseStatusException.class, () -> validator.validate(
                    new CanvasDefinition(4, 57, List.of(node), List.of())));
            org.junit.jupiter.api.Assertions.assertTrue(error.getReason()
                    .contains("SPATIAL_JOIN_ONE_TO_ONE_REQUIRE_SCHEMA_VERSION"));
            org.junit.jupiter.api.Assertions.assertTrue(error.getReason()
                    .contains("configuration.oneToOne"));
            assertDoesNotThrow(() -> validator.validate(
                    new CanvasDefinition(4, 58, List.of(node), List.of())));
        }
        assertDoesNotThrow(() -> validator.validate(
                new CanvasDefinition(4, 57, List.of(legacy), List.of())));
    }

    @Test
    void spatialJoinTemporalConditionRequires59IncludingIncompleteDrafts() {
        var temporal = new SpatialJoinTemporalCondition(
                SpatialJoinTemporalRelationship.INTERSECTS,
                "target_start", "target_end", "join_start", "join_end", null, null);
        var incompleteDraft = new SpatialJoinTemporalCondition(
                SpatialJoinTemporalRelationship.NEAR,
                "", null, "", null, null, null);

        for (var condition : List.of(temporal, incompleteDraft)) {
            var node = spatialJoinNode(
                    null, null, JoinType.INNER, null, null, condition);
            var error = assertThrows(ResponseStatusException.class, () -> validator.validate(
                    new CanvasDefinition(4, 58, List.of(node), List.of())));
            org.junit.jupiter.api.Assertions.assertTrue(error.getReason()
                    .contains("SPATIAL_JOIN_TEMPORAL_CONDITION_REQUIRE_SCHEMA_VERSION"));
            org.junit.jupiter.api.Assertions.assertTrue(error.getReason()
                    .contains("configuration.temporalCondition"));
        }
        assertDoesNotThrow(() -> validator.validate(new CanvasDefinition(
                4, 59, List.of(spatialJoinNode(
                null, null, JoinType.INNER, null, null, temporal)), List.of())));
        assertDoesNotThrow(() -> validator.validate(new CanvasDefinition(
                4, 59, List.of(spatialJoinNode(
                null, null, JoinType.INNER, null, null, incompleteDraft)), List.of())));
        assertDoesNotThrow(() -> validator.validate(new CanvasDefinition(
                4, 58, List.of(spatialJoinNode(null)), List.of())));
    }

    @Test
    void spatialJoinNearAndDistanceOutputRequire60IncludingInactiveDrafts() {
        var near = new SpatialJoinSpatialNearCondition(
                "", "", SpatialDistanceMethod.GEODESIC,
                null, null);
        var output = new SpatialJoinDistanceOutput(
                false, "", null, "", null);
        var configuration = new SpatialJoinConfiguration(
                "orders", "districts", "matched", JoinType.INNER,
                List.of(), List.of(), List.of(),
                SpatialJoinOperation.JOIN_ONE_TO_MANY,
                null, null, near, output);
        var node = new SpatialJoinNodeDefinition(
                UUID.randomUUID().toString(), "Near",
                new CanvasNodeLayout(0d, 0d, 368d, 224d), configuration);

        var error = assertThrows(ResponseStatusException.class, () -> validator.validate(
                new CanvasDefinition(4, 59, List.of(node), List.of())));
        org.junit.jupiter.api.Assertions.assertTrue(error.getReason()
                .contains("SPATIAL_JOIN_NEAR_REQUIRE_SCHEMA_VERSION"));
        assertDoesNotThrow(() -> validator.validate(
                new CanvasDefinition(4, 60, List.of(node), List.of())));
    }

    private static SpatialJoinNodeDefinition spatialJoinNode(List<JoinOutputColumn> outputColumns) {
        return spatialJoinNode(outputColumns, null);
    }

    private static SpatialJoinNodeDefinition spatialJoinNode(
            List<JoinOutputColumn> outputColumns,
            List<JoinCondition> attributeConditions
    ) {
        return spatialJoinNode(outputColumns, attributeConditions, JoinType.INNER);
    }

    private static SpatialJoinNodeDefinition spatialJoinNode(
            List<JoinOutputColumn> outputColumns,
            List<JoinCondition> attributeConditions,
            JoinType joinType
    ) {
        return spatialJoinNode(outputColumns, attributeConditions, joinType, null);
    }

    private static SpatialJoinNodeDefinition spatialJoinNode(
            List<JoinOutputColumn> outputColumns,
            List<JoinCondition> attributeConditions,
            JoinType joinType,
            SpatialJoinOperation joinOperation
    ) {
        return spatialJoinNode(
                outputColumns, attributeConditions, joinType, joinOperation, null);
    }

    private static SpatialJoinNodeDefinition spatialJoinNode(
            List<JoinOutputColumn> outputColumns,
            List<JoinCondition> attributeConditions,
            JoinType joinType,
            SpatialJoinOperation joinOperation,
            SpatialJoinOneToOneOptions oneToOne
    ) {
        return spatialJoinNode(
                outputColumns, attributeConditions, joinType, joinOperation, oneToOne, null);
    }

    private static SpatialJoinNodeDefinition spatialJoinNode(
            List<JoinOutputColumn> outputColumns,
            List<JoinCondition> attributeConditions,
            JoinType joinType,
            SpatialJoinOperation joinOperation,
            SpatialJoinOneToOneOptions oneToOne,
            SpatialJoinTemporalCondition temporalCondition
    ) {
        return new SpatialJoinNodeDefinition(
                UUID.randomUUID().toString(),
                "空间连接",
                new CanvasNodeLayout(0d, 0d, 368d, 224d),
                new SpatialJoinConfiguration(
                        "orders", "districts", "orders_with_district", joinType,
                        List.of(new SpatialJoinCondition(
                                "shape", SpatialPredicate.WITHIN, "boundary")),
                        attributeConditions,
                        outputColumns,
                        joinOperation,
                        oneToOne,
                        temporalCondition)
        );
    }

    @Test void fixedWeekDraftsRequire47WithPrecisePath() {
        var c = new ObjectMapper().readValue("{\"boundaries\":{\"maximumTimeGapUnit\":\"WEEKS\"}}", TrackReconstructConfiguration.class);
        var node = new TrackReconstructNodeDefinition(UUID.randomUUID().toString(),"轨迹",new CanvasNodeLayout(0d,0d,360d,216d),c);
        var legacy = new CanvasDefinition(4,46,List.of(node),List.of());
        var error = assertThrows(ResponseStatusException.class, () -> validator.validate(legacy));
        org.junit.jupiter.api.Assertions.assertTrue(error.getReason().contains("SPATIAL_DURATION_WEEKS_REQUIRE_SCHEMA_VERSION"));
        org.junit.jupiter.api.Assertions.assertTrue(error.getReason().contains("configuration.boundaries.maximumTimeGapUnit"));
        assertThrows(ResponseStatusException.class, () -> upgrader.upgradeToCurrent(legacy));
        assertDoesNotThrow(() -> validator.validate(new CanvasDefinition(4,47,List.of(node),List.of())));
    }

    @Test void nearestGeodesicGeometryRequires48EvenInLegacyMatchingDraft() {
        for (var mode : java.util.Arrays.asList(null, SpatialNearestGeodesicGeometryMode.POINT_ONLY,
                SpatialNearestGeodesicGeometryMode.GEOMETRY)) {
            var configuration = new ObjectMapper().readValue("{\"nearestCount\":1,\"includeUnmatched\":false}",
                    SpatialNearestConfiguration.class);
            configuration = new SpatialNearestConfiguration(configuration.sourceTableName(), configuration.sourceGeometryColumnName(),
                    configuration.candidateTableName(), configuration.candidateGeometryColumnName(), configuration.candidateIdColumnName(),
                    configuration.distanceMethod(), configuration.nearestCount(), configuration.maximumDistance(),
                    configuration.maximumDistanceUnit(), configuration.includeUnmatched(), configuration.outputTableName(),
                    configuration.distanceColumnName(), configuration.distanceOutputUnit(), configuration.rankColumnName(),
                    List.of(), new SpatialNearestMatching(SpatialNearestMatchSemantics.LEGACY_KNN, "", null, mode));
            var node = new SpatialNearestNodeDefinition(UUID.randomUUID().toString(), "最近邻",
                    new CanvasNodeLayout(0d, 0d, 368d, 216d), configuration);
            var old = new CanvasDefinition(4, 47, List.of(node), List.of());
            if (mode == SpatialNearestGeodesicGeometryMode.GEOMETRY) {
                var error = assertThrows(ResponseStatusException.class, () -> validator.validate(old));
                org.junit.jupiter.api.Assertions.assertTrue(error.getReason().contains("SPATIAL_NEAREST_GEODESIC_GEOMETRY_REQUIRE_SCHEMA_VERSION"));
                org.junit.jupiter.api.Assertions.assertTrue(error.getReason().contains("configuration.matching.geodesicGeometryMode"));
                assertThrows(ResponseStatusException.class, () -> upgrader.upgradeToCurrent(old));
            } else {
                assertDoesNotThrow(() -> validator.validate(old));
                assertDoesNotThrow(() -> upgrader.upgradeToCurrent(old));
            }
            assertDoesNotThrow(() -> validator.validate(new CanvasDefinition(4, 48, List.of(node), List.of())));
        }
    }

    @Test void incidentWindowDraftsAreVersionGatedButBusinessErrorsCanBeSaved() {
        for (var semantics : TrackIncidentSemantics.values()) {
            var c = new TrackDetectIncidentsConfiguration("",null,List.of(),"",null,
                    new TrackBoundaryConfiguration(null,null,null,null),new CanvasFilterGroup(FilterGroupOperator.AND,List.of()),null,
                    TrackIncidentResultMode.ALL_EVENTS,"","","","","","",SpatialDurationUnit.SECONDS,semantics,"",List.of(),
                    List.of(new TrackIncidentWindow("","",null,null,null)));
            var node = new TrackDetectIncidentsNodeDefinition(UUID.randomUUID().toString(),"事件",new CanvasNodeLayout(0d,0d,360d,216d),c);
            var legacy = new CanvasDefinition(4,45,List.of(node),List.of());
            assertThrows(ResponseStatusException.class, () -> validator.validate(legacy));
            assertThrows(ResponseStatusException.class, () -> upgrader.upgradeToCurrent(legacy));
            assertDoesNotThrow(() -> validator.validate(new CanvasDefinition(4,46,List.of(node),List.of())));
        }
    }

    @Test void incidentPointCoordinateDraftsRequire67ButMayKeepBusinessErrors() {
        for (var semantics : TrackIncidentSemantics.values()) {
            var configuration = new TrackDetectIncidentsConfiguration("", null, List.of(), "", null,
                    new TrackBoundaryConfiguration(null, null, null, null),
                    new CanvasFilterGroup(FilterGroupOperator.AND, List.of()), null,
                    TrackIncidentResultMode.ALL_EVENTS, "", "", "", "", "", "",
                    SpatialDurationUnit.SECONDS, semantics, "", List.of(), List.of(),
                    List.of(new TrackIncidentScalar("", TrackIncidentScalar.Source.TRACK_POINT_X_AT, null)));
            var node = new TrackDetectIncidentsNodeDefinition(UUID.randomUUID().toString(), "事件",
                    new CanvasNodeLayout(0d, 0d, 360d, 216d), configuration);

            var old = assertThrows(ResponseStatusException.class,
                    () -> validator.validate(new CanvasDefinition(4, 66, List.of(node), List.of())));
            org.junit.jupiter.api.Assertions.assertTrue(old.getReason()
                    .contains("TRACK_INCIDENT_POINT_COORDINATES_REQUIRE_SCHEMA_VERSION"));
            assertThrows(ResponseStatusException.class, () -> upgrader.upgradeToCurrent(
                    new CanvasDefinition(4, 66, List.of(node), List.of())));
            assertDoesNotThrow(() -> validator.validate(new CanvasDefinition(4, 67, List.of(node), List.of())));
        }
    }

    @Test void hdbscanOptionsRequire45EvenOnDbscanDrafts() {
        for (SpatialHdbscanOptions options : java.util.Arrays.asList(null, new SpatialHdbscanOptions("", "", "", ""))) {
            var c = new SpatialPointClusterConfiguration("", "", "", SpatialDistanceMethod.PLANAR,
                    new SpatialPointClusterParameters.Dbscan(0, null, 0), "", "", "", null, options);
            var node = new SpatialPointClusterNodeDefinition(UUID.randomUUID().toString(), "聚类", new CanvasNodeLayout(0d,0d,352d,216d), c);
            var old = new CanvasDefinition(4,44,List.of(node),List.of());
            if (options == null) {
                assertDoesNotThrow(() -> validator.validate(old));
                assertDoesNotThrow(() -> upgrader.upgradeToCurrent(old));
            } else {
                assertThrows(ResponseStatusException.class, () -> validator.validate(old));
                assertThrows(ResponseStatusException.class, () -> upgrader.upgradeToCurrent(old));
            }
            assertDoesNotThrow(() -> validator.validate(new CanvasDefinition(4,45,List.of(node),List.of())));
        }
    }

    @Test void hdbscanFieldNamesMayBeInvalidBusinessDraftsButMustBeStrings() {
        for (var options : List.of(new SpatialHdbscanOptions("", "same", "same", ""), new SpatialHdbscanOptions(null, "o", "e", "s"))) {
            var c = new SpatialPointClusterConfiguration("", "", "", SpatialDistanceMethod.PLANAR,
                    new SpatialPointClusterParameters.Hdbscan(0), "", "", "", null, options);
            var node = new SpatialPointClusterNodeDefinition(UUID.randomUUID().toString(), "聚类", new CanvasNodeLayout(0d,0d,352d,216d), c);
            var definition = new CanvasDefinition(4,45,List.of(node),List.of());
            if (options.probabilityColumnName() == null) assertThrows(ResponseStatusException.class, () -> validator.validate(definition));
            else assertDoesNotThrow(() -> validator.validate(definition));
        }
    }

    @Test void geodesicAreaDraftRequires44EvenWhenInactive() {
        for (TrackGeodesicAreaOptions boundary : java.util.Arrays.asList(null,new TrackGeodesicAreaOptions(-1d,null))) {
            var options=new TrackReconstructOptions(TrackReconstructSemantics.LEGACY_POINTS,List.of(),null,null,null,
                    new TrackAreaGeometryOptions(false,TrackBufferMode.NONE,null,null,null,List.of(),boundary));
            var c=new TrackReconstructConfiguration("","",List.of(),"",null,new TrackBoundaryConfiguration(null,null,null,null),
                    List.of(),"","","","","",options);
            var node=new TrackReconstructNodeDefinition(UUID.randomUUID().toString(),"重建",new CanvasNodeLayout(0d,0d,352d,216d),c);
            var old=new CanvasDefinition(4,43,List.of(node),List.of());
            if(boundary==null) assertDoesNotThrow(()->validator.validate(old));
            else {
                var error=assertThrows(ResponseStatusException.class,()->validator.validate(old));
                org.junit.jupiter.api.Assertions.assertTrue(error.getReason().contains("TRACK_GEODESIC_AREA_REQUIRE_SCHEMA_VERSION"));
                assertThrows(ResponseStatusException.class,()->upgrader.upgradeToCurrent(old));
            }
            assertDoesNotThrow(()->validator.validate(new CanvasDefinition(4,44,List.of(node),List.of())));
        }
    }

    @Test
    void acceptsCompleteAndIncompleteModelIdentifiersButRejectsMalformedIdentifiers() {
        String inputId = UUID.randomUUID().toString();
        String outputId = UUID.randomUUID().toString();
        CanvasDefinition valid = definition(inputId, outputId, UUID.randomUUID().toString(), "");
        CanvasDefinition incomplete = definition(inputId, outputId, "", "");
        CanvasDefinition malformedInput = definition(inputId, outputId, "not-a-uuid", "");
        CanvasDefinition malformedOutput = definition(inputId, outputId, "", "not-a-uuid");

        assertDoesNotThrow(() -> validator.validate(valid));
        assertDoesNotThrow(() -> validator.validate(incomplete));
        assertThrows(ResponseStatusException.class, () -> validator.validate(malformedInput));
        assertThrows(ResponseStatusException.class, () -> validator.validate(malformedOutput));
    }

    @Test
    void acceptsCurrentMajorWithMissingMinorAndNormalizesItToTheCurrentWriterVersion() {
        CanvasDefinition source = new ObjectMapper().readValue(
                "{\"schemaVersion\":" + CanvasDefinition.CURRENT_SCHEMA_VERSION
                        + ",\"nodes\":[],\"edges\":[]}",
                CanvasDefinition.class
        );

        assertDoesNotThrow(() -> validator.validate(source));
        CanvasDefinition upgraded = upgrader.upgradeToCurrent(source);

        assertEquals(0, source.effectiveSchemaMinorVersion());
        assertEquals(CanvasDefinition.CURRENT_SCHEMA_VERSION, upgraded.schemaVersion());
        assertEquals(CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION, upgraded.schemaMinorVersion());
    }

    @Test
    void acceptsAnIncompleteSqlTransformDraftOnlyFromCanvasFourDotOne() {
        SqlTransformNodeDefinition sqlTransform = new SqlTransformNodeDefinition(
                UUID.randomUUID().toString(),
                "SQL 处理",
                layout(),
                new SqlTransformConfiguration(null, null)
        );
        CanvasDefinition current = new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION,
                List.of(sqlTransform),
                List.of()
        );
        CanvasDefinition legacy = new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                CanvasDefinition.LEGACY_SCHEMA_MINOR_VERSION,
                List.of(sqlTransform),
                List.of()
        );

        assertEquals("", sqlTransform.configuration().outputTableName());
        assertEquals("", sqlTransform.configuration().sql());
        assertDoesNotThrow(() -> validator.validate(current));
        assertThrows(ResponseStatusException.class, () -> validator.validate(legacy));
        assertThrows(ResponseStatusException.class, () -> upgrader.upgradeToCurrent(legacy));
    }

    @Test
    void acceptsCurrentCanvasVersionAndRejectsOtherMajorsAndFutureMinors() {
        CanvasDefinition current = definition(
                UUID.randomUUID().toString(), UUID.randomUUID().toString(), "", "");
        CanvasDefinition previousMajor = new CanvasDefinition(1, 28, current.nodes(), current.edges());
        CanvasDefinition futureMinor = new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION + 1,
                List.of(),
                List.of());
        CanvasDefinition futureMajor = new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION + 1, 0, List.of(), List.of());

        assertDoesNotThrow(() -> validator.validate(current));
        assertThrows(ResponseStatusException.class, () -> validator.validate(previousMajor));
        assertThrows(ResponseStatusException.class, () -> validator.validate(futureMinor));
        assertThrows(ResponseStatusException.class, () -> validator.validate(futureMajor));
    }

    @Test
    void acceptsEpochTimestampUnitsOnlyFromCanvasFourDotTwoAndOnlyForTimestampTargets() {
        TypeCastNodeDefinition validNode = epochTimestampTypeCast(PlatformDataType.TIMESTAMP);
        CanvasDefinition current = new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION,
                List.of(validNode),
                List.of()
        );
        CanvasDefinition fourDotOne = new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                1,
                List.of(validNode),
                List.of()
        );
        TypeCastNodeDefinition invalidTarget = epochTimestampTypeCast(PlatformDataType.STRING);

        assertDoesNotThrow(() -> validator.validate(current));
        assertThrows(ResponseStatusException.class, () -> validator.validate(fourDotOne));
        assertThrows(ResponseStatusException.class, () -> upgrader.upgradeToCurrent(fourDotOne));
        assertThrows(ResponseStatusException.class, () -> validator.validate(new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION,
                List.of(invalidTarget),
                List.of()
        )));
    }

    @Test
    void acceptsStringTemporalParsingOnlyFromCanvasFourDotThree() {
        TypeCastNodeDefinition validNode = new TypeCastNodeDefinition(
                UUID.randomUUID().toString(),
                "字符串时间转换",
                layout(),
                new TypeCastConfiguration(List.of(new TypeCastOperation(
                        UUID.randomUUID().toString(),
                        "events",
                        new ProcessorOutput.CreateNewTable("typed_events"),
                        List.of(new ColumnTypeCast(
                                "created_at_text",
                                new PlatformTypeDefinition(PlatformDataType.TIMESTAMP, null, null, null, null),
                                CastFailureStrategy.SET_NULL,
                                null,
                                new StringTemporalParseOptions(
                                        "yyyy-MM-dd HH:mm:ss",
                                        StringTimestampZoneMode.SOURCE_TIME_ZONE,
                                        "Asia/Shanghai"
                                )
                        ))
                )))
        );
        CanvasDefinition current = new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION,
                List.of(validNode),
                List.of()
        );
        CanvasDefinition fourDotTwo = new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                2,
                List.of(validNode),
                List.of()
        );

        assertDoesNotThrow(() -> validator.validate(current));
        assertThrows(ResponseStatusException.class, () -> validator.validate(fourDotTwo));
        assertThrows(ResponseStatusException.class, () -> upgrader.upgradeToCurrent(fourDotTwo));
    }

    @Test
    void rejectsNonzeroMinorAndAcceptsModelOutputUpsertInThreeDotZero() {
        ModelOutputNodeDefinition output = new ModelOutputNodeDefinition(
                UUID.randomUUID().toString(),
                "模型 UPSERT",
                layout(),
                new ModelOutputConfiguration(
                        "orders",
                        UUID.randomUUID().toString(),
                        JdbcWriteMode.UPSERT,
                        List.of(new JdbcColumnMapping("id", "id"))
                )
        );
        CanvasDefinition previous = new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION, 2, List.of(output), List.of());
        CanvasDefinition current = new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION,
                List.of(output),
                List.of());

        assertThrows(ResponseStatusException.class, () -> upgrader.upgradeToCurrent(previous));
        assertDoesNotThrow(() -> validator.validate(current));
    }

    @Test
    void acceptsRenameInTwoDotZeroAndRejectsThePreviousMajor() {
        CanvasDefinition current = new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION,
                List.of(new RenameNodeDefinition(
                        UUID.randomUUID().toString(),
                        "重命名",
                        new CanvasNodeLayout(0d, 0d, 240d, 120d),
                        new RenameConfiguration(
                                "orders",
                                "source_orders",
                                List.of(new RenameColumnMapping("id", "order_id"))
                        )
                )),
                List.of()
        );
        CanvasDefinition previous = new CanvasDefinition(1, 28, current.nodes(), current.edges());

        assertDoesNotThrow(() -> validator.validate(current));
        assertThrows(ResponseStatusException.class, () -> validator.validate(previous));
    }

    @Test
    void acceptsKafkaInlineSchemaInTwoDotZeroWithoutPersistingAModelReference() {
        CanvasDefinition current = new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION,
                List.of(new KafkaInputNodeDefinition(
                        UUID.randomUUID().toString(),
                        "事件输入",
                        new CanvasNodeLayout(0d, 0d, 240d, 120d),
                        new KafkaInputConfiguration(
                                UUID.randomUUID().toString(),
                                "order-events",
                                new KafkaValueSchema(List.of(
                                        new KafkaValueColumn(
                                                "event_id",
                                                PlatformDataType.LONG,
                                                null,
                                                null,
                                                null,
                                                false,
                                                "事件 ID"
                                        )
                                )),
                                "order_events",
                                KafkaStartingOffsets.LATEST
                        )
                )),
                List.of()
        );

        assertDoesNotThrow(() -> validator.validate(current));
        assertThrows(
                ResponseStatusException.class,
                () -> validator.validate(new CanvasDefinition(1, 28, current.nodes(), current.edges()))
        );
        String serialized = new ObjectMapper().writeValueAsString(current);
        assertFalse(serialized.contains("valueModelId"));
        assertFalse(serialized.contains("modelId"));
    }

    @Test
    void acceptsKafkaInputFormatsOnlyFromFourDotFourAndNormalizesLegacyDefaults() {
        KafkaInputNodeDefinition textInput = new KafkaInputNodeDefinition(
                UUID.randomUUID().toString(),
                "文本事件输入",
                layout(),
                new KafkaInputConfiguration(
                        UUID.randomUUID().toString(),
                        "text-events",
                        new KafkaValueSchema(List.of()),
                        "text_events",
                        KafkaStartingOffsets.LATEST,
                        10,
                        KafkaInputValueFormat.TEXT,
                        List.of(KafkaInputMetadataField.KEY, KafkaInputMetadataField.OFFSET)
                )
        );
        CanvasDefinition current = new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION,
                List.of(textInput),
                List.of()
        );
        CanvasDefinition previous = new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                3,
                List.of(textInput),
                List.of()
        );

        assertDoesNotThrow(() -> validator.validate(current));
        assertThrows(ResponseStatusException.class, () -> validator.validate(previous));

        KafkaInputNodeDefinition legacyInput = new KafkaInputNodeDefinition(
                textInput.id(),
                "JSON 事件输入",
                layout(),
                new KafkaInputConfiguration(
                        UUID.randomUUID().toString(),
                        "json-events",
                        new KafkaValueSchema(List.of(new KafkaValueColumn(
                                "event_id", PlatformDataType.LONG,
                                null, null, null, false, null
                        ))),
                        "json_events",
                        KafkaStartingOffsets.LATEST,
                        10
                )
        );
        CanvasDefinition upgraded = upgrader.upgradeToCurrent(new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                3,
                List.of(legacyInput),
                List.of()
        ));
        KafkaInputConfiguration normalized = ((KafkaInputNodeDefinition) upgraded.nodes().getFirst())
                .configuration();
        assertEquals(KafkaInputValueFormat.JSON, normalized.valueFormat());
        assertEquals(List.of(), normalized.metadataFields());
    }

    @Test
    void rejectsGeometryInKafkaInlineSchema() {
        CanvasDefinition definition = new CanvasDefinition(
                1,
                5,
                List.of(new KafkaInputNodeDefinition(
                        UUID.randomUUID().toString(),
                        "空间事件输入",
                        new CanvasNodeLayout(0d, 0d, 240d, 120d),
                        new KafkaInputConfiguration(
                                UUID.randomUUID().toString(),
                                "spatial-events",
                                new KafkaValueSchema(List.of(
                                        new KafkaValueColumn(
                                                "shape",
                                                PlatformDataType.GEOMETRY,
                                                null,
                                                null,
                                                null,
                                                true,
                                                null
                                        )
                                )),
                                "spatial_events",
                                KafkaStartingOffsets.LATEST
                        )
                )),
                List.of()
        );

        assertThrows(ResponseStatusException.class, () -> validator.validate(definition));
    }

    @Test
    void enforcesIntroducedMinorVersionForAdvancedProcessors() {
        assertIntroducedAt(
                new NullHandlingNodeDefinition(
                        UUID.randomUUID().toString(),
                        "空值处理",
                        layout(),
                        new NullHandlingConfiguration(
                                "orders",
                                "orders_cleaned",
                                List.of(new DropNullRowsRule(
                                        List.of("order_id"),
                                        NullMatchMode.ANY_NULL
                                ))
                        )
                ),
                14
        );
        assertIntroducedAt(
                new ValueMappingNodeDefinition(
                        UUID.randomUUID().toString(),
                        "值映射",
                        layout(),
                        new ValueMappingConfiguration(
                                "orders",
                                "orders_standardized",
                                List.of(new ValueMappingRule(
                                        "status",
                                        List.of(new ValueMappingEntry(
                                                new CanvasLiteral(PlatformDataType.STRING, "P"),
                                                new CanvasLiteral(PlatformDataType.STRING, "PAID")
                                        )),
                                        ValueMappingUnmatchedStrategy.KEEP,
                                        null
                                ))
                        )
                ),
                15
        );
        assertIntroducedAt(
                new WindowNodeDefinition(
                        UUID.randomUUID().toString(),
                        "窗口计算",
                        layout(),
                        new WindowConfiguration(
                                "orders",
                                "orders_windowed",
                                List.of(),
                                List.of(new SortField(
                                        "created_at",
                                        SortDirection.ASC,
                                        NullOrdering.LAST
                                )),
                                List.of(new WindowFunctionItem.RowNumber("row_number"))
                        )
                ),
                16
        );
        assertIntroducedAt(
                new TopNNodeDefinition(
                        UUID.randomUUID().toString(),
                        "Top N",
                        layout(),
                        new TopNConfiguration(
                                "orders",
                                "top_orders",
                                List.of(),
                                List.of(new SortField(
                                        "amount",
                                        SortDirection.DESC,
                                        NullOrdering.LAST
                                )),
                                10,
                                TopNTieStrategy.EXACT
                        )
                ),
                17
        );
        assertIntroducedAt(
                new JsonExtractNodeDefinition(
                        UUID.randomUUID().toString(),
                        "JSON 提取",
                        layout(),
                        new JsonExtractConfiguration(
                                "orders",
                                "orders_enriched",
                                "payload",
                                List.of(new JsonExtraction(
                                        "$.customer.id",
                                        "customer_id",
                                        PlatformTypeDefinition.of(PlatformDataType.STRING)
                                )),
                                JsonExtractFailureStrategy.ERROR
                        )
                ),
                19
        );
        assertIntroducedAt(
                new SpatialTransformNodeDefinition(
                        UUID.randomUUID().toString(),
                        "空间转换",
                        layout(),
                        new SpatialTransformConfiguration(
                                "orders",
                                "orders_3857",
                                "location",
                                new CrsReference("EPSG", 3857)
                        )
                ),
                20
        );
        assertIntroducedAt(
                new SpatialJoinNodeDefinition(
                        UUID.randomUUID().toString(),
                        "空间连接",
                        layout(),
                        new SpatialJoinConfiguration(
                                "orders",
                                "regions",
                                "orders_with_region",
                                JoinType.INNER,
                                List.of(new SpatialJoinCondition(
                                        "location",
                                        SpatialPredicate.WITHIN,
                                        "boundary"
                                ))
                        )
                ),
                20
        );
        GeometryTypeDefinition point4326 = new GeometryTypeDefinition(
                GeometryKind.POINT,
                new CrsReference("EPSG", 4326),
                CoordinateDimension.XY
        );
        assertIntroducedAt(
                new GeometryConstructNodeDefinition(
                        UUID.randomUUID().toString(),
                        "Geometry 构造",
                        layout(),
                        new GeometryConstructConfiguration(
                                "raw",
                                "geometry_table",
                                "shape",
                                new GeometryConstructSource.Wkt("wkt"),
                                point4326
                        )
                ),
                21
        );
        assertIntroducedAt(
                new GeometryValidateNodeDefinition(
                        UUID.randomUUID().toString(),
                        "Geometry 校验",
                        layout(),
                        new GeometryValidateConfiguration(
                                "geometry_table",
                                "validated",
                                "shape",
                                "is_valid",
                                null
                        )
                ),
                21
        );
        assertIntroducedAt(
                new SpatialMeasureNodeDefinition(
                        UUID.randomUUID().toString(),
                        "空间测量",
                        layout(),
                        new SpatialMeasureConfiguration(
                                "geometry_table",
                                "measured",
                                List.of(new SpatialMeasurement.X("shape", "x"))
                        )
                ),
                21
        );
        assertIntroducedAt(
                new GeometrySerializeNodeDefinition(
                        UUID.randomUUID().toString(),
                        "Geometry 序列化",
                        layout(),
                        new GeometrySerializeConfiguration(
                                "geometry_table",
                                "serialized",
                                "shape",
                                "wkt",
                                GeometrySerializationFormat.WKT
                        )
                ),
                21
        );
        assertIntroducedAt(
                new GeometryRepairNodeDefinition(
                        UUID.randomUUID().toString(),
                        "Geometry 修复",
                        layout(),
                        new GeometryRepairConfiguration(
                                "geometry_table", "repaired", "shape", "repaired_shape")
                ),
                22
        );
        assertIntroducedAt(
                new GeometryBufferNodeDefinition(
                        UUID.randomUUID().toString(),
                        "Geometry Buffer",
                        layout(),
                        new GeometryBufferConfiguration(
                                "geometry_table", "buffered", "shape", "buffer_shape",
                                100d, SpatialMeasureMode.PLANAR)
                ),
                22
        );
        assertIntroducedAt(
                new GeometryExplodeNodeDefinition(
                        UUID.randomUUID().toString(),
                        "Geometry 拆分",
                        layout(),
                        new GeometryExplodeConfiguration(
                                "geometry_table", "parts", "shape", "part", "part_index")
                ),
                22
        );
        assertIntroducedAt(
                new SpatialClipNodeDefinition(
                        UUID.randomUUID().toString(),
                        "空间裁剪",
                        layout(),
                        new SpatialClipConfiguration(
                                "roads", "districts", "district_roads",
                                "centerline", "boundary", "clipped_centerline")
                ),
                23
        );
        assertIntroducedAt(
                new SpatialAggregateNodeDefinition(
                        UUID.randomUUID().toString(),
                        "空间聚合",
                        layout(),
                        new SpatialAggregateConfiguration(
                                "parcels",
                                "district_geometry",
                                List.of("district_code"),
                                List.of(new SpatialAggregation(
                                        SpatialAggregationKind.UNION,
                                        "boundary",
                                        "district_boundary"
                                ))
                        )
                ),
                23
        );
    }

    @Test
    void acceptsShapefileOutputInTheCurrentCanvasVersionAndRejectsThePreviousMajor() {
        FileOutputNodeDefinition shapefileOutput = new FileOutputNodeDefinition(
                UUID.randomUUID().toString(),
                "行政区 Shapefile 输出",
                layout(),
                new FileOutputConfiguration(
                        "districts",
                        UUID.randomUUID().toString(),
                        "exports/districts",
                        FileOutputConflictPolicy.FAIL_IF_EXISTS,
                        new FileOutputFormatOptions.Shapefile(
                                "districts",
                                ShapefilePackageMode.ZIP,
                                "geom",
                                ShapefileShapeType.POLYGON,
                                List.of(new ShapefileAttributeMapping(
                                        "district_code", "DIST_CODE", 64
                                ))
                        )
                )
        );

        assertDoesNotThrow(() -> validator.validate(new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION,
                List.of(shapefileOutput), List.of())));
        assertThrows(ResponseStatusException.class, () -> validator.validate(new CanvasDefinition(
                1, 24, List.of(shapefileOutput), List.of())));
    }

    @Test
    void acceptsGeoParquetAndGeoJsonOutputInTheCurrentCanvasVersion() {
        FileOutputNodeDefinition geoParquet = fileOutput(new FileOutputFormatOptions.GeoParquet(
                "geom", GeoParquetCompressionCodec.SNAPPY, GeoParquetCoveringMode.ROW_BBOX));
        FileOutputNodeDefinition geoJson = fileOutput(new FileOutputFormatOptions.GeoJson(
                "districts", "geom", "district_id", false));

        assertDoesNotThrow(() -> validator.validate(new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION,
                List.of(geoParquet, geoJson), List.of())));
        assertThrows(ResponseStatusException.class, () -> validator.validate(new CanvasDefinition(
                1, 25, List.of(geoParquet), List.of())));
        assertThrows(ResponseStatusException.class, () -> validator.validate(new CanvasDefinition(
                1, 25, List.of(geoJson), List.of())));
    }

    @Test
    void requiresTheCurrentCanvasMajorForSnapshotSyncOutputMappingProtocol() {
        SnapshotDeletePolicy keep = new SnapshotDeletePolicy(
                SnapshotTargetOnlyAction.KEEP, null, null);
        JdbcSnapshotSyncOutputNodeDefinition jdbc = new JdbcSnapshotSyncOutputNodeDefinition(
                UUID.randomUUID().toString(),
                "JDBC 快照同步",
                layout(),
                new JdbcSnapshotSyncOutputConfiguration(
                        "source", "", "target", List.of("id"),
                        List.of(new JdbcColumnMapping("id", "id")), keep)
        );
        ModelSnapshotSyncOutputNodeDefinition model = new ModelSnapshotSyncOutputNodeDefinition(
                UUID.randomUUID().toString(),
                "模型快照同步",
                layout(),
                new ModelSnapshotSyncOutputConfiguration(
                        "source", "", List.of("id"),
                        List.of(new JdbcColumnMapping("id", "id")), keep)
        );

        assertThrows(ResponseStatusException.class, () -> validator.validate(new CanvasDefinition(
                1, 27, List.of(jdbc), List.of())));
        assertThrows(ResponseStatusException.class, () -> validator.validate(new CanvasDefinition(
                1, 27, List.of(model), List.of())));
        assertDoesNotThrow(() -> validator.validate(new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION,
                List.of(jdbc, model), List.of())));
    }

    @Test
    void acceptsJdbcQueryInputAndExplicitOutputMappingInCanvasTwo() {
        CanvasColumnSchema orderId = new CanvasColumnSchema(
                "order_id", PlatformDataType.LONG, null, null, null,
                false, null, false, false, "订单 ID"
        );
        JdbcQueryInputNodeDefinition queryInput = new JdbcQueryInputNodeDefinition(
                UUID.randomUUID().toString(),
                "订单查询输入",
                layout(),
                new JdbcQueryInputConfiguration(
                        UUID.randomUUID().toString(),
                        "SELECT order_id FROM orders",
                        "query_orders",
                        "a".repeat(64),
                        List.of(orderId)
                )
        );
        JdbcOutputNodeDefinition upsertOutput = new JdbcOutputNodeDefinition(
                UUID.randomUUID().toString(),
                "订单 UPSERT",
                layout(),
                new JdbcOutputConfiguration(
                        "query_orders",
                        UUID.randomUUID().toString(),
                        "orders",
                        JdbcWriteMode.UPSERT,
                        List.of(new JdbcColumnMapping("order_id", "order_id")),
                        List.of("order_id")
                )
        );

        assertDoesNotThrow(() -> validator.validate(new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION,
                List.of(queryInput), List.of())));
        assertDoesNotThrow(() -> validator.validate(new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION,
                List.of(queryInput, upsertOutput), List.of())));
        assertThrows(ResponseStatusException.class, () -> validator.validate(new CanvasDefinition(
                1, 23, List.of(queryInput), List.of())));
        assertThrows(ResponseStatusException.class, () -> validator.validate(new CanvasDefinition(
                1, 27, List.of(upsertOutput), List.of())));
    }

    @Test
    void rejectsPreOneDotTwentyEightJdbcOutputDefinitions() {
        String json = """
                {
                  "schemaVersion": 1,
                  "schemaMinorVersion": 27,
                  "nodes": [{
                    "id": "11111111-1111-4111-8111-111111111111",
                    "type": "JDBC_OUTPUT",
                    "name": "旧 JDBC 输出",
                    "layout": {"x": 0, "y": 0, "width": 240, "height": 120},
                    "configuration": {
                      "sourceTableName": "orders",
                      "dataSourceId": "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
                      "targetTableName": "orders",
                      "writeMode": "APPEND",
                      "columnMappings": [{
                        "sourceColumnName": "order_id",
                        "targetColumnName": "order_id"
                      }]
                    }
                  }],
                  "edges": []
                }
                """;

        CanvasDefinition parsed = new ObjectMapper().readValue(json, CanvasDefinition.class);
        JdbcOutputNodeDefinition output = (JdbcOutputNodeDefinition) parsed.nodes().getFirst();

        assertEquals(List.of(), output.configuration().upsertKeyColumns());
        assertThrows(ResponseStatusException.class, () -> validator.validate(parsed));
    }

    @Test
    void validatesRuntimeValuesInGlobalAndPerTableDerivations() {
        DeriveColumnsNodeDefinition valid = new DeriveColumnsNodeDefinition(
                UUID.randomUUID().toString(), "派生技术字段", layout(),
                new DeriveColumnsConfiguration(
                        List.of(new ColumnDerivation(
                                "etl_batch_id",
                                new RuntimeValueExpression(CanvasRuntimeValue.EXECUTION_ID))),
                        List.of(new DeriveColumnsOperation(
                                UUID.randomUUID().toString(), "orders",
                                new ProcessorOutput.ReplaceSource("orders"),
                                List.of(new ColumnDerivation(
                                        "etl_loaded_at",
                                        new RuntimeValueExpression(CanvasRuntimeValue.EXECUTION_STARTED_AT)))))));
        DeriveColumnsNodeDefinition invalid = new DeriveColumnsNodeDefinition(
                UUID.randomUUID().toString(), "缺少运行时变量", layout(),
                new DeriveColumnsConfiguration(List.of(), List.of(new DeriveColumnsOperation(
                        UUID.randomUUID().toString(), "orders",
                        new ProcessorOutput.ReplaceSource("orders"),
                        List.of(new ColumnDerivation(
                                "etl_batch_id", new RuntimeValueExpression(null)))))));

        assertDoesNotThrow(() -> validator.validate(new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION,
                List.of(valid), List.of())));
        assertThrows(ResponseStatusException.class, () -> validator.validate(new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION,
                List.of(invalid), List.of())));
    }

    private static FileOutputNodeDefinition fileOutput(FileOutputFormatOptions formatOptions) {
        return new FileOutputNodeDefinition(
                UUID.randomUUID().toString(),
                "空间文件输出",
                layout(),
                new FileOutputConfiguration(
                        "districts",
                        UUID.randomUUID().toString(),
                        "exports/districts",
                        FileOutputConflictPolicy.FAIL_IF_EXISTS,
                        formatOptions
                )
        );
    }

    private void assertIntroducedAt(CanvasNodeDefinition node, int schemaMinorVersion) {
        CanvasDefinition introduced = new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION,
                List.of(node),
                List.of()
        );
        CanvasDefinition previous = new CanvasDefinition(
                1,
                schemaMinorVersion,
                List.of(node),
                List.of()
        );

        assertDoesNotThrow(() -> validator.validate(introduced));
        assertThrows(ResponseStatusException.class, () -> validator.validate(previous));
    }

    private static CanvasNodeLayout layout() {
        return new CanvasNodeLayout(0d, 0d, 240d, 120d);
    }

    private static TypeCastNodeDefinition epochTimestampTypeCast(PlatformDataType targetType) {
        return new TypeCastNodeDefinition(
                UUID.randomUUID().toString(),
                "Epoch 时间戳转换",
                layout(),
                new TypeCastConfiguration(List.of(new TypeCastOperation(
                        UUID.randomUUID().toString(),
                        "events",
                        new ProcessorOutput.CreateNewTable("typed_events"),
                        List.of(new ColumnTypeCast(
                                "event_time",
                                new PlatformTypeDefinition(targetType, null, null, null, null),
                                CastFailureStrategy.FAIL,
                                EpochTimestampUnit.MILLISECONDS
                        ))
                )))
        );
    }

    private static CanvasDefinition definition(
            String inputId,
            String outputId,
            String inputModelId,
            String outputModelId
    ) {
        return new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION,
                List.of(
                        new ModelInputNodeDefinition(
                                inputId,
                                "模型输入",
                                new CanvasNodeLayout(0d, 0d, 240d, 120d),
                                new ModelInputConfiguration(inputModelId)
                        ),
                        new ModelOutputNodeDefinition(
                                outputId,
                                "模型输出",
                                new CanvasNodeLayout(320d, 0d, 240d, 120d),
                                new ModelOutputConfiguration(
                                        "orders",
                                        outputModelId,
                                        null,
                                        List.of()
                                )
                        )
                ),
                List.of(new CanvasEdgeDefinition(
                        UUID.randomUUID().toString(),
                        inputId,
                        outputId
                ))
        );
    }
}
