package cn.superhuang.datascalpel.taskengine.runner;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasLiteral;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeLayout;
import cn.superhuang.data.scalpel.contract.task.CanvasTableOrigin;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.ColumnDerivation;
import cn.superhuang.data.scalpel.contract.task.ColumnExpression;
import cn.superhuang.data.scalpel.contract.task.DeriveColumnsConfiguration;
import cn.superhuang.data.scalpel.contract.task.DeriveColumnsNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.DeriveColumnsOperation;
import cn.superhuang.data.scalpel.contract.task.FileOutputConfiguration;
import cn.superhuang.data.scalpel.contract.task.FileOutputConflictPolicy;
import cn.superhuang.data.scalpel.contract.task.FileOutputFormatOptions;
import cn.superhuang.data.scalpel.contract.task.FileOutputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.FileOutputWrite;
import cn.superhuang.data.scalpel.contract.task.GeoParquetCompressionCodec;
import cn.superhuang.data.scalpel.contract.task.GeoParquetCoveringMode;
import cn.superhuang.data.scalpel.contract.task.MetadataSnapshot;
import cn.superhuang.data.scalpel.contract.task.LiteralExpression;
import cn.superhuang.data.scalpel.contract.task.ProcessorOutput;
import cn.superhuang.data.scalpel.contract.task.RuntimeValueExpression;
import cn.superhuang.data.scalpel.contract.task.CanvasRuntimeValue;
import cn.superhuang.data.scalpel.contract.type.CoordinateDimension;
import cn.superhuang.data.scalpel.contract.type.CrsReference;
import cn.superhuang.data.scalpel.contract.type.GeometryKind;
import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.datascalpel.taskengine.compiler.MetadataIndex;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CanvasTaskExecutorSummaryTest {
    @Test
    void describeDatasetSummaryContainsConfigurationButNeverProfileResults() {
        var configuration = new cn.superhuang.data.scalpel.contract.task.SpatialDescribeDatasetConfiguration(
                "events", "shape", "field_statistics", "dataset_description",
                37, "sample_rows", true, "extent_polygon");
        var node = new cn.superhuang.data.scalpel.contract.task.SpatialDescribeDatasetNodeDefinition(
                UUID.randomUUID().toString(), "描述数据集",
                new CanvasNodeLayout(0d, 0d, 376d, 232d), configuration);

        String summary = CanvasTaskExecutor.nodeSummary(node, EMPTY_METADATA);

        assertTrue(summary.contains("sourceTable=events"));
        assertTrue(summary.contains("geometryColumn=shape"));
        assertTrue(summary.contains("statisticsTable=field_statistics"));
        assertTrue(summary.contains("descriptionTable=dataset_description"));
        assertTrue(summary.contains("sampleSize=37"));
        assertTrue(summary.contains("extentOutput=true"));
        assertFalse(summary.contains("description_json"));
        assertFalse(summary.contains("record_count"));
    }

    @Test
    void similarLocationsSummaryKeepsOnlySafeCountsAndModes() {
        var configuration = new cn.superhuang.data.scalpel.contract.task.SpatialSimilarLocationsConfiguration(
                "references", "private_reference_id", "private_reference_geometry",
                new cn.superhuang.data.scalpel.contract.task.CanvasFieldPredicate(
                        "private_filter_field", cn.superhuang.data.scalpel.contract.task.FilterOperator.EQUALS,
                        List.of(new CanvasLiteral(PlatformDataType.STRING, "private_filter_value"))),
                "candidates", "private_candidate_id", "private_candidate_geometry", null,
                List.of(new cn.superhuang.data.scalpel.contract.task.SpatialSimilarLocationsAnalysisField(
                        "private_analysis", "private_analysis_output")),
                List.of(new cn.superhuang.data.scalpel.contract.task.SpatialSimilarLocationsAppendField(
                        "private_append", "private_append_output")),
                cn.superhuang.data.scalpel.contract.task.SpatialSimilarLocationsMatchMethod.ATTRIBUTE_VALUES,
                cn.superhuang.data.scalpel.contract.task.SpatialSimilarLocationsResultMode.BOTH,
                17, "similar_locations", "private_geometry_output", "private_location_type",
                "private_simrank", "private_dsimrank", "private_simindex", "private_cosimindex",
                "private_labelrank", "private_reference_output", "private_search_output");
        var node = new cn.superhuang.data.scalpel.contract.task.SpatialSimilarLocationsNodeDefinition(
                UUID.randomUUID().toString(), "查找相似位置",
                new CanvasNodeLayout(0d, 0d, 392d, 244d), configuration);

        String summary = CanvasTaskExecutor.nodeSummary(node, EMPTY_METADATA);

        assertTrue(summary.contains("referenceTable=references"));
        assertTrue(summary.contains("candidateTable=candidates"));
        assertTrue(summary.contains("analysisFieldCount=1"));
        assertTrue(summary.contains("appendFieldCount=1"));
        assertTrue(summary.contains("matchMethod=ATTRIBUTE_VALUES"));
        assertTrue(summary.contains("resultMode=BOTH"));
        assertTrue(summary.contains("resultCountPerSide=17"));
        assertTrue(summary.contains("outputTable=similar_locations"));
        assertFalse(summary.contains("private_"));
    }

    @Test
    void enrichFromGridSummaryCountsFieldsWithoutExposingFieldMappings() {
        var configuration = new cn.superhuang.data.scalpel.contract.task.SpatialEnrichFromGridConfiguration(
                "points", "point_shape", "grid", "grid_shape", "grid_id",
                List.of(new cn.superhuang.data.scalpel.contract.task.SpatialEnrichFromGridField(
                        "private_source_attribute", "private_output_attribute")),
                "enriched_points");
        var node = new cn.superhuang.data.scalpel.contract.task.SpatialEnrichFromGridNodeDefinition(
                UUID.randomUUID().toString(), "格网丰富",
                new CanvasNodeLayout(0d, 0d, 384d, 232d), configuration);

        String summary = CanvasTaskExecutor.nodeSummary(node, EMPTY_METADATA);

        assertTrue(summary.contains("pointTable=points"));
        assertTrue(summary.contains("gridTable=grid"));
        assertTrue(summary.contains("enrichFieldCount=1"));
        assertTrue(summary.contains("outputTable=enriched_points"));
        assertFalse(summary.contains("private_source_attribute"));
        assertFalse(summary.contains("private_output_attribute"));
    }

    @Test
    void multiVariableGridSummaryCountsConfigurationWithoutExposingFieldsFiltersOrDistances() {
        var filter = new cn.superhuang.data.scalpel.contract.task.CanvasFieldPredicate(
                "private_filter_field",
                cn.superhuang.data.scalpel.contract.task.FilterOperator.EQUALS,
                List.of(new CanvasLiteral(PlatformDataType.STRING, "private_filter_literal")));
        var variable = new cn.superhuang.data.scalpel.contract.task.SpatialMultiVariableGridVariable(
                UUID.randomUUID().toString(), "facilities", "private_geometry",
                cn.superhuang.data.scalpel.contract.task.SpatialMultiVariableGridVariableKind.ATTRIBUTE_OF_NEAREST,
                "private_attribute", null, null,
                314159d, cn.superhuang.data.scalpel.contract.task.SpatialDistanceUnit.METERS,
                filter, "private_output_field");
        var configuration = new cn.superhuang.data.scalpel.contract.task.SpatialMultiVariableGridConfiguration(
                List.of(variable), cn.superhuang.data.scalpel.contract.task.SpatialDensityBinShape.SQUARE,
                271828d, cn.superhuang.data.scalpel.contract.task.SpatialDistanceUnit.METERS,
                "multi_grid", "private_bin_id", "private_bin_geometry");
        var node = new cn.superhuang.data.scalpel.contract.task.SpatialMultiVariableGridNodeDefinition(
                UUID.randomUUID().toString(), "多变量格网",
                new CanvasNodeLayout(0d, 0d, 384d, 244d), configuration);

        String summary = CanvasTaskExecutor.nodeSummary(node, EMPTY_METADATA);

        assertTrue(summary.contains("variableCount=1"));
        assertTrue(summary.contains("sourceTableCount=1"));
        assertTrue(summary.contains("filteredVariableCount=1"));
        assertTrue(summary.contains("outputTable=multi_grid"));
        for (String privateValue : List.of(
                "private_filter_field", "private_filter_literal", "private_geometry",
                "private_attribute", "private_output_field", "private_bin_id",
                "private_bin_geometry", "314159", "271828")) {
            assertFalse(summary.contains(privateValue));
        }
    }

    @Test
    void incidentWindowsSummaryContainsOnlyCountsNotBindingsOrConditionValues() throws Exception {
        var c = new com.fasterxml.jackson.databind.ObjectMapper().readValue("""
                {"sourceTableName":"events","outputTableName":"incidents","trackIdColumns":[],"boundaries":{},
                 "startCondition":{"kind":"PREDICATE","columnName":"private_alias","operator":"GREATER_THAN",
                   "values":[{"dataType":"DOUBLE","value":"987654321"}]},
                 "conditionWindows":[{"bindingName":"private_alias","sourceColumnName":"private_field","kind":"MEAN","startOffset":-512345,"endOffset":0},
                   {"bindingName":"private_speed","source":"TRACK_SPEED","kind":"MAX","startOffset":-4,"endOffset":1},
                   {"bindingName":"private_acceleration","source":"TRACK_ACCELERATION","kind":"MIN","startOffset":-2,"endOffset":2}],
                 "conditionScalars":[{"bindingName":"private_elapsed","source":"TRACK_DURATION"},
                   {"bindingName":"private_previous_x","source":"TRACK_POINT_X_AT","offset":-765432}]}
                """, cn.superhuang.data.scalpel.contract.task.TrackDetectIncidentsConfiguration.class);
        var node = new cn.superhuang.data.scalpel.contract.task.TrackDetectIncidentsNodeDefinition(UUID.randomUUID().toString(),"事件",
                new CanvasNodeLayout(0d,0d,360d,216d),c);
        String summary = CanvasTaskExecutor.nodeSummary(node,EMPTY_METADATA);
        assertTrue(summary.contains("conditionWindowCount=3"));
        assertTrue(summary.contains("trackDistanceWindowCount=0"));
        assertTrue(summary.contains("trackSpeedWindowCount=1"));
        assertTrue(summary.contains("trackAccelerationWindowCount=1"));
        assertTrue(summary.contains("conditionScalarCount=2"));
        assertTrue(summary.contains("pointCoordinateScalarCount=1"));
        for (String value : List.of("private_alias","private_field","private_speed","private_acceleration",
                "private_elapsed","private_previous_x","987654321","512345","765432")) {
            assertFalse(summary.contains(value));
        }
    }

    @Test void hdbscanSummaryCountsDiagnosticsWithoutExposingTheirNamesOrInactiveTime() {
        var c = new cn.superhuang.data.scalpel.contract.task.SpatialPointClusterConfiguration("points", "shape", "identity",
                cn.superhuang.data.scalpel.contract.task.SpatialDistanceMethod.PLANAR,
                new cn.superhuang.data.scalpel.contract.task.SpatialPointClusterParameters.Hdbscan(5), "clusters", "cluster_id", "noise",
                new cn.superhuang.data.scalpel.contract.task.SpatialDbscanOptions(cn.superhuang.data.scalpel.contract.task.SpatialDbscanOptions.Mode.LINEAR,
                        "private-time", 314159L, cn.superhuang.data.scalpel.contract.task.SpatialDurationUnit.SECONDS),
                new cn.superhuang.data.scalpel.contract.task.SpatialHdbscanOptions("private-probability", "private-outlier", "private-exemplar", "private-stability"));
        var node = new cn.superhuang.data.scalpel.contract.task.SpatialPointClusterNodeDefinition(UUID.randomUUID().toString(), "聚类",
                new CanvasNodeLayout(0d,0d,352d,216d),c);
        String summary = CanvasTaskExecutor.nodeSummary(node, EMPTY_METADATA);
        assertTrue(summary.contains("diagnosticCount=4")); assertTrue(summary.contains("dbscanMode=INACTIVE"));
        assertFalse(summary.contains("private-")); assertFalse(summary.contains("314159"));
    }

    @Test
    void spatialDissolveSummaryCountsOnlyActiveStatistics() {
        var statistic = new cn.superhuang.data.scalpel.contract.task.SpatialAggregateStatistic(
                UUID.randomUUID().toString(),
                cn.superhuang.data.scalpel.contract.task.SpatialAggregateStatisticKind.SUM,
                "private_source_value",
                "private_output_sum");
        for (boolean enabled : List.of(false, true)) {
            var configuration = new cn.superhuang.data.scalpel.contract.task.SpatialAggregateConfiguration(
                    "parcels",
                    "districts",
                    List.of(),
                    List.of(new cn.superhuang.data.scalpel.contract.task.SpatialAggregation(
                            cn.superhuang.data.scalpel.contract.task.SpatialAggregationKind.UNION,
                            "shape",
                            "district_shape")),
                    new cn.superhuang.data.scalpel.contract.task.SpatialAggregateDissolveOptions(
                            enabled,
                            false,
                            "private_count",
                            List.of(statistic),
                            cn.superhuang.data.scalpel.contract.task.SpatialAggregateDissolveGroupingMode.CONNECTED_COMPONENTS));
            var node = new cn.superhuang.data.scalpel.contract.task.SpatialAggregateNodeDefinition(
                    UUID.randomUUID().toString(),
                    "Dissolve",
                    new CanvasNodeLayout(0d, 0d, 352d, 224d),
                    configuration);

            String summary = CanvasTaskExecutor.nodeSummary(node, EMPTY_METADATA);

            assertTrue(summary.contains("dissolve=" + enabled));
            assertTrue(summary.contains("dissolveGrouping="
                    + (enabled ? "CONNECTED_COMPONENTS" : "n/a")));
            assertTrue(summary.contains("summaryCount=" + (enabled ? 1 : 0)));
            assertFalse(summary.contains("private_"));
        }
    }

    @Test
    void unionMergeLayersSummaryContainsOnlyModeAndRuleCounts() {
        var configuration = new cn.superhuang.data.scalpel.contract.task.UnionConfiguration(
                List.of("base", "merge"),
                "merged",
                cn.superhuang.data.scalpel.contract.task.UnionMode.ALL,
                List.of(new cn.superhuang.data.scalpel.contract.task.UnionMergeTable(
                        "merge",
                        List.of(
                                new cn.superhuang.data.scalpel.contract.task.UnionMergeFieldRule(
                                        "private_source",
                                        cn.superhuang.data.scalpel.contract.task.UnionMergeFieldAction.MATCH,
                                        "private_target"),
                                new cn.superhuang.data.scalpel.contract.task.UnionMergeFieldRule(
                                        "private_removed",
                                        cn.superhuang.data.scalpel.contract.task.UnionMergeFieldAction.REMOVE,
                                        null)
                        )
                ))
        );
        var node = new cn.superhuang.data.scalpel.contract.task.UnionNodeDefinition(
                UUID.randomUUID().toString(),
                "Merge Layers",
                new CanvasNodeLayout(0d, 0d, 336d, 204d),
                configuration
        );

        String summary = CanvasTaskExecutor.nodeSummary(node, EMPTY_METADATA);

        assertTrue(summary.contains("mergeLayers=true"));
        assertTrue(summary.contains("configuredMergeTableCount=1"));
        assertTrue(summary.contains("mergeFieldRuleCount=2"));
        assertFalse(summary.contains("private_"));
    }

    @Test
    void spatialJoinSummaryRecordsOnlyProjectionCount() {
        var configuration = new cn.superhuang.data.scalpel.contract.task.SpatialJoinConfiguration(
                "orders",
                "districts",
                "orders_with_district",
                cn.superhuang.data.scalpel.contract.task.JoinType.LEFT,
                List.of(new cn.superhuang.data.scalpel.contract.task.SpatialJoinCondition(
                        "private_order_geometry",
                        cn.superhuang.data.scalpel.contract.task.SpatialPredicate.WITHIN,
                        "private_district_geometry")),
                List.of(new cn.superhuang.data.scalpel.contract.task.JoinCondition(
                        "private_tenant_id",
                        cn.superhuang.data.scalpel.contract.task.JoinOperator.EQUALS,
                        "private_tenant_id")),
                List.of(
                        new cn.superhuang.data.scalpel.contract.task.JoinOutputColumn(
                                cn.superhuang.data.scalpel.contract.task.JoinOutputColumnSource.LEFT,
                                "private_customer_name", "customer_name", true),
                        new cn.superhuang.data.scalpel.contract.task.JoinOutputColumn(
                                cn.superhuang.data.scalpel.contract.task.JoinOutputColumnSource.RIGHT,
                                "private_district_name", "district_name", false)),
                cn.superhuang.data.scalpel.contract.task.SpatialJoinOperation.JOIN_ONE_TO_MANY);
        var node = new cn.superhuang.data.scalpel.contract.task.SpatialJoinNodeDefinition(
                UUID.randomUUID().toString(),
                "空间连接",
                new CanvasNodeLayout(0d, 0d, 368d, 224d),
                configuration);

        String summary = CanvasTaskExecutor.nodeSummary(node, EMPTY_METADATA);

        assertTrue(summary.contains("conditionCount=1"));
        assertTrue(summary.contains("joinType=LEFT"));
        assertTrue(summary.contains("attributeConditionCount=1"));
        assertTrue(summary.contains("outputFieldCount=1"));
        assertTrue(summary.contains("joinOperation=JOIN_ONE_TO_MANY"));
        assertFalse(summary.contains("private_"));
    }

    @Test
    void spatialJoinOneToOneSummaryRecordsOnlyModeStrategyAndCounts() {
        var configuration = new cn.superhuang.data.scalpel.contract.task.SpatialJoinConfiguration(
                "targets",
                "districts",
                "targets_joined",
                cn.superhuang.data.scalpel.contract.task.JoinType.INNER,
                List.of(new cn.superhuang.data.scalpel.contract.task.SpatialJoinCondition(
                        "private_target_geometry",
                        cn.superhuang.data.scalpel.contract.task.SpatialPredicate.WITHIN,
                        "private_join_geometry")),
                List.of(),
                List.of(new cn.superhuang.data.scalpel.contract.task.JoinOutputColumn(
                        cn.superhuang.data.scalpel.contract.task.JoinOutputColumnSource.RIGHT,
                        "private_district_id", "district_id", true)),
                cn.superhuang.data.scalpel.contract.task.SpatialJoinOperation.JOIN_ONE_TO_ONE,
                new cn.superhuang.data.scalpel.contract.task.SpatialJoinOneToOneOptions(
                        cn.superhuang.data.scalpel.contract.task.SpatialJoinOneToOneMode.KEEP_ONE,
                        "private_join_count",
                        List.of(),
                        new cn.superhuang.data.scalpel.contract.task.SpatialJoinKeepRule(
                                cn.superhuang.data.scalpel.contract.task.SpatialJoinKeepStrategy.LARGEST,
                                "private_score",
                                List.of(new cn.superhuang.data.scalpel.contract.task.SortField(
                                        "private_id",
                                        cn.superhuang.data.scalpel.contract.task.SortDirection.ASC,
                                        cn.superhuang.data.scalpel.contract.task.NullOrdering.LAST))
                        )
                )
        );
        var node = new cn.superhuang.data.scalpel.contract.task.SpatialJoinNodeDefinition(
                UUID.randomUUID().toString(),
                "一对一空间连接",
                new CanvasNodeLayout(0d, 0d, 368d, 224d),
                configuration
        );

        String summary = CanvasTaskExecutor.nodeSummary(node, EMPTY_METADATA);

        assertTrue(summary.contains("joinOperation=JOIN_ONE_TO_ONE"));
        assertTrue(summary.contains("oneToOneMode=KEEP_ONE"));
        assertTrue(summary.contains("keepStrategy=LARGEST"));
        assertTrue(summary.contains("stableOrderCount=1"));
        assertFalse(summary.contains("private_"));
    }

    @Test
    void spatialJoinTemporalSummaryRecordsOnlyRelationship() {
        var configuration = new cn.superhuang.data.scalpel.contract.task.SpatialJoinConfiguration(
                "targets",
                "districts",
                "targets_joined",
                cn.superhuang.data.scalpel.contract.task.JoinType.INNER,
                List.of(new cn.superhuang.data.scalpel.contract.task.SpatialJoinCondition(
                        "shape",
                        cn.superhuang.data.scalpel.contract.task.SpatialPredicate.WITHIN,
                        "boundary")),
                List.of(),
                List.of(new cn.superhuang.data.scalpel.contract.task.JoinOutputColumn(
                        cn.superhuang.data.scalpel.contract.task.JoinOutputColumnSource.LEFT,
                        "target_id", "target_id", true)),
                cn.superhuang.data.scalpel.contract.task.SpatialJoinOperation.JOIN_ONE_TO_MANY,
                null,
                new cn.superhuang.data.scalpel.contract.task.SpatialJoinTemporalCondition(
                        cn.superhuang.data.scalpel.contract.task.SpatialJoinTemporalRelationship.NEAR,
                        "private_target_start",
                        "private_target_end",
                        "private_join_start",
                        "private_join_end",
                        314159L,
                        cn.superhuang.data.scalpel.contract.task.SpatialDurationUnit.SECONDS)
        );
        var node = new cn.superhuang.data.scalpel.contract.task.SpatialJoinNodeDefinition(
                UUID.randomUUID().toString(),
                "时空连接",
                new CanvasNodeLayout(0d, 0d, 368d, 224d),
                configuration
        );

        String summary = CanvasTaskExecutor.nodeSummary(node, EMPTY_METADATA);

        assertTrue(summary.contains("temporalRelationship=NEAR"));
        assertFalse(summary.contains("private_"));
        assertFalse(summary.contains("314159"));
    }

    @Test
    void spatialJoinNearSummaryDoesNotExposeThresholdsOrFieldNames() {
        var configuration = new cn.superhuang.data.scalpel.contract.task.SpatialJoinConfiguration(
                "targets",
                "districts",
                "targets_joined",
                cn.superhuang.data.scalpel.contract.task.JoinType.INNER,
                List.of(),
                List.of(),
                List.of(new cn.superhuang.data.scalpel.contract.task.JoinOutputColumn(
                        cn.superhuang.data.scalpel.contract.task.JoinOutputColumnSource.LEFT,
                        "target_id", "target_id", true)),
                cn.superhuang.data.scalpel.contract.task.SpatialJoinOperation.JOIN_ONE_TO_MANY,
                null,
                null,
                new cn.superhuang.data.scalpel.contract.task.SpatialJoinSpatialNearCondition(
                        "private_target_geometry",
                        "private_join_geometry",
                        cn.superhuang.data.scalpel.contract.task.SpatialDistanceMethod.GEODESIC,
                        314159d,
                        cn.superhuang.data.scalpel.contract.task.SpatialDistanceUnit.METERS),
                new cn.superhuang.data.scalpel.contract.task.SpatialJoinDistanceOutput(
                        true,
                        "private_spatial_distance",
                        cn.superhuang.data.scalpel.contract.task.SpatialDistanceUnit.KILOMETERS,
                        "private_temporal_gap",
                        cn.superhuang.data.scalpel.contract.task.SpatialDurationUnit.SECONDS)
        );
        var node = new cn.superhuang.data.scalpel.contract.task.SpatialJoinNodeDefinition(
                UUID.randomUUID().toString(),
                "空间 Near",
                new CanvasNodeLayout(0d, 0d, 368d, 224d),
                configuration
        );

        String summary = CanvasTaskExecutor.nodeSummary(node, EMPTY_METADATA);

        assertTrue(summary.contains("spatialNear=GEODESIC"));
        assertTrue(summary.contains("spatialNearUnit=METERS"));
        assertTrue(summary.contains("distanceOutput=true"));
        assertTrue(summary.contains("spatialDistanceOutputUnit=KILOMETERS"));
        for (String privateValue : List.of(
                "314159", "private_target_geometry", "private_join_geometry",
                "private_spatial_distance", "private_temporal_gap")) {
            assertFalse(summary.contains(privateValue), summary);
        }
    }

    private static final UUID DATA_SOURCE_ID = UUID.fromString("901e8938-bc1d-4bfd-91ec-d26bca38e8f6");
    private static final MetadataIndex EMPTY_METADATA = MetadataIndex.create(
            new MetadataSnapshot(List.of(), List.of()));
    private static final CanvasTableSchema SOURCE_TABLE = new CanvasTableSchema(
            "district_orders",
            CanvasTableOrigin.jdbc(DATA_SOURCE_ID, "district_orders"),
            List.of(new CanvasColumnSchema(
                    "geom",
                    PlatformDataType.GEOMETRY,
                    null,
                    null,
                    null,
                    true,
                    null,
                    false,
                    false,
                    null,
                    new GeometryTypeDefinition(
                            GeometryKind.POLYGON,
                            CrsReference.epsg(4326),
                            CoordinateDimension.XY))));

    @Test
    void spatialFileOutputSummaryIncludesAllWritesWithoutRuntimeValues() {
        String geoParquet = summary(new FileOutputFormatOptions.GeoParquet(
                "geom",
                GeoParquetCompressionCodec.SNAPPY,
                GeoParquetCoveringMode.ROW_BBOX));
        String geoJson = summary(new FileOutputFormatOptions.GeoJson(
                "district_orders",
                "geom",
                null,
                true));

        for (String summary : List.of(geoParquet, geoJson)) {
            assertTrue(summary.contains("writeCount=1"));
            assertTrue(summary.contains("district_orders"));
            assertTrue(summary.contains("exports/district-orders"));
            assertTrue(summary.contains("conflictPolicies=FAIL_IF_EXISTS"));
            assertFalse(summary.contains("116.397"));
            assertFalse(summary.contains("secret district"));
            assertFalse(summary.contains("s3://"));
        }
    }

    @Test
    void multiTableDeriveSummaryUsesGlobalAndOperationRules() {
        ColumnDerivation global = new ColumnDerivation(
                "source_record_hash",
                new LiteralExpression(new CanvasLiteral(PlatformDataType.STRING, "do-not-log")));
        DeriveColumnsNodeDefinition node = new DeriveColumnsNodeDefinition(
                "20761935-b98f-4be9-8fd2-24f8ea1d0323",
                "派生字段",
                new CanvasNodeLayout(0D, 0D, 352D, 224D),
                new DeriveColumnsConfiguration(
                        List.of(global),
                        List.of(
                                deriveOperation("orders", "order_hash"),
                                deriveOperation("customers", "customer_hash"),
                                new DeriveColumnsOperation(
                                        UUID.randomUUID().toString(),
                                        "products",
                                        new ProcessorOutput.ReplaceSource("products"),
                                        List.of()))));

        String summary = CanvasTaskExecutor.nodeSummary(node, EMPTY_METADATA);

        assertTrue(summary.contains("tableCount=3"));
        assertTrue(summary.contains("globalDerivationCount=1"));
        assertTrue(summary.contains("localDerivationCount=2"));
        assertTrue(summary.contains("effectiveDerivationCount=5"));
        assertTrue(summary.contains("source_record_hash"));
        assertFalse(summary.contains("do-not-log"));
    }

    @Test
    void deriveSummaryRecordsRuntimeValueNamesWithoutResolvingThem() {
        DeriveColumnsNodeDefinition node = new DeriveColumnsNodeDefinition(
                "20761935-b98f-4be9-8fd2-24f8ea1d0323",
                "派生字段",
                new CanvasNodeLayout(0D, 0D, 352D, 224D),
                new DeriveColumnsConfiguration(
                        List.of(new ColumnDerivation(
                                "etl_batch_id",
                                new RuntimeValueExpression(CanvasRuntimeValue.EXECUTION_ID))),
                        List.of(deriveOperation("orders", "order_hash"))));

        String summary = CanvasTaskExecutor.nodeSummary(node, EMPTY_METADATA);

        assertTrue(summary.contains("expressionKinds=COLUMN,RUNTIME_VALUE"));
        assertTrue(summary.contains("runtimeValues=EXECUTION_ID"));
        assertFalse(summary.contains("00000000-0000-0000-0000-000000000000"));
    }

    private static DeriveColumnsOperation deriveOperation(String tableName, String targetColumn) {
        return new DeriveColumnsOperation(
                UUID.randomUUID().toString(),
                tableName,
                new ProcessorOutput.ReplaceSource(tableName),
                List.of(new ColumnDerivation(
                        targetColumn,
                        new ColumnExpression("id"))));
    }

    private static String summary(FileOutputFormatOptions formatOptions) {
        FileOutputNodeDefinition node = new FileOutputNodeDefinition(
                "a62f3130-c220-4ca7-b901-42f0fc80bb15",
                "空间文件输出",
                new CanvasNodeLayout(0D, 0D, 260D, 120D),
                new FileOutputConfiguration(
                        DATA_SOURCE_ID.toString(),
                        List.of(new FileOutputWrite(
                                UUID.randomUUID().toString(),
                                "district_orders",
                                "exports/district-orders",
                                FileOutputConflictPolicy.FAIL_IF_EXISTS,
                                formatOptions))));
        return CanvasTaskExecutor.nodeSummary(node, EMPTY_METADATA, List.of(SOURCE_TABLE));
    }
}
