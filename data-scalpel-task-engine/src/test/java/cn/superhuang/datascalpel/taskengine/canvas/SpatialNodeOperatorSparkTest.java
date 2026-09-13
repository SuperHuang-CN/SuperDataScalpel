package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasDatasetKind;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeLayout;
import cn.superhuang.data.scalpel.contract.task.CanvasTableOrigin;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.GeometryConstructConfiguration;
import cn.superhuang.data.scalpel.contract.task.GeometryConstructNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.GeometryConstructSource;
import cn.superhuang.data.scalpel.contract.task.GeometryBufferConfiguration;
import cn.superhuang.data.scalpel.contract.task.GeometryBufferDistanceSource;
import cn.superhuang.data.scalpel.contract.task.GeometryBufferNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.GeometryExplodeConfiguration;
import cn.superhuang.data.scalpel.contract.task.GeometryExplodeNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.GeometryRepairConfiguration;
import cn.superhuang.data.scalpel.contract.task.GeometryRepairNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.GeometrySerializationFormat;
import cn.superhuang.data.scalpel.contract.task.GeometrySerializeConfiguration;
import cn.superhuang.data.scalpel.contract.task.GeometrySerializeNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.GeometryValidateConfiguration;
import cn.superhuang.data.scalpel.contract.task.GeometryValidateNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.JoinType;
import cn.superhuang.data.scalpel.contract.task.JoinCondition;
import cn.superhuang.data.scalpel.contract.task.JoinOperator;
import cn.superhuang.data.scalpel.contract.task.JoinOutputColumn;
import cn.superhuang.data.scalpel.contract.task.JoinOutputColumnSource;
import cn.superhuang.data.scalpel.contract.task.MetadataSnapshot;
import cn.superhuang.data.scalpel.contract.task.SpatialJoinCondition;
import cn.superhuang.data.scalpel.contract.task.SpatialJoinConfiguration;
import cn.superhuang.data.scalpel.contract.task.SpatialJoinKeepRule;
import cn.superhuang.data.scalpel.contract.task.SpatialJoinKeepStrategy;
import cn.superhuang.data.scalpel.contract.task.SpatialJoinOneToOneMode;
import cn.superhuang.data.scalpel.contract.task.SpatialJoinOneToOneOptions;
import cn.superhuang.data.scalpel.contract.task.SpatialJoinOperation;
import cn.superhuang.data.scalpel.contract.task.SpatialJoinNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.SpatialJoinSummaryStatistic;
import cn.superhuang.data.scalpel.contract.task.SpatialJoinSummaryStatisticKind;
import cn.superhuang.data.scalpel.contract.task.SortDirection;
import cn.superhuang.data.scalpel.contract.task.SortField;
import cn.superhuang.data.scalpel.contract.task.NullOrdering;
import cn.superhuang.data.scalpel.contract.task.SpatialClipConfiguration;
import cn.superhuang.data.scalpel.contract.task.SpatialClipGeometryPolicy;
import cn.superhuang.data.scalpel.contract.task.SpatialClipMaskCombination;
import cn.superhuang.data.scalpel.contract.task.SpatialClipNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.SpatialAggregateConfiguration;
import cn.superhuang.data.scalpel.contract.task.SpatialAggregateDissolveOptions;
import cn.superhuang.data.scalpel.contract.task.SpatialAggregateDissolveGroupingMode;
import cn.superhuang.data.scalpel.contract.task.SpatialAggregateNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.SpatialAggregateStatistic;
import cn.superhuang.data.scalpel.contract.task.SpatialAggregateStatisticKind;
import cn.superhuang.data.scalpel.contract.task.SpatialAggregation;
import cn.superhuang.data.scalpel.contract.task.SpatialAggregationKind;
import cn.superhuang.data.scalpel.contract.task.SpatialAreaUnit;
import cn.superhuang.data.scalpel.contract.task.SpatialMeasureConfiguration;
import cn.superhuang.data.scalpel.contract.task.SpatialDistanceUnit;
import cn.superhuang.data.scalpel.contract.task.SpatialMeasureMode;
import cn.superhuang.data.scalpel.contract.task.SpatialMeasureNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.SpatialMeasurement;
import cn.superhuang.data.scalpel.contract.task.SpatialPredicate;
import cn.superhuang.data.scalpel.contract.task.SpatialTransformConfiguration;
import cn.superhuang.data.scalpel.contract.task.SpatialTransformNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.UnionConfiguration;
import cn.superhuang.data.scalpel.contract.task.UnionMergeFieldAction;
import cn.superhuang.data.scalpel.contract.task.UnionMergeFieldRule;
import cn.superhuang.data.scalpel.contract.task.UnionMergeTable;
import cn.superhuang.data.scalpel.contract.task.UnionMode;
import cn.superhuang.data.scalpel.contract.task.UnionNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.TaskLineageEvidence;
import cn.superhuang.data.scalpel.contract.type.CoordinateDimension;
import cn.superhuang.data.scalpel.contract.type.CrsReference;
import cn.superhuang.data.scalpel.contract.type.GeometryKind;
import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.datascalpel.taskengine.compiler.MetadataIndex;
import cn.superhuang.datascalpel.taskengine.compiler.lineage.CatalystLineageAnalyzer;
import cn.superhuang.datascalpel.taskengine.compiler.lineage.CatalystLineageMetadata;
import cn.superhuang.datascalpel.taskengine.compiler.lineage.CatalystLineageOutputCandidate;
import cn.superhuang.datascalpel.taskengine.spark.SedonaSparkSupport;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;
import cn.superhuang.datascalpel.taskengine.spark.SparkTypeMapper;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.RowFactory;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.scheduler.SparkListener;
import org.apache.spark.scheduler.SparkListenerJobStart;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.io.WKBWriter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SpatialNodeOperatorSparkTest {
    private static final CanvasNodeLayout LAYOUT = new CanvasNodeLayout(0d, 0d, 240d, 120d);
    private SparkSession spark;

    @BeforeAll
    void startSpark() {
        spark = SedonaSparkSupport.initialize(SedonaSparkSupport.builder()
                .master("local[1]")
                .appName("spatial-node-operator-test")
                .config("spark.ui.enabled", "false")
                .config("spark.driver.host", "127.0.0.1")
                .config("spark.driver.bindAddress", "127.0.0.1")
                .config("spark.sql.caseSensitive", "true")
                .getOrCreate());
    }

    @AfterAll
    void stopSpark() {
        if (spark != null) {
            spark.stop();
        }
    }

    @Test
    void transformsGeometryCrsAndPreservesOtherColumns() {
        SparkCanvasTable source = table(
                "orders",
                List.of(
                        scalar("order_id"),
                        geometry("location", GeometryKind.POINT, 4326)
                )
        );
        RecordingIssueSink issues = new RecordingIssueSink();

        CanvasNodeOperationResult result = new SpatialTransformNodeOperator().apply(
                new SpatialTransformNodeDefinition(
                        UUID.randomUUID().toString(),
                        "转换坐标系",
                        LAYOUT,
                        new SpatialTransformConfiguration(
                                "orders",
                                "orders_3857",
                                "location",
                                new CrsReference("EPSG", 3857)
                        )
                ),
                Map.of("orders", source),
                context(issues)
        );

        assertFalse(issues.hasErrors(), () -> issues.codes.toString());
        CanvasTableSchema transformed = result.propagatedTables().get("orders_3857").schema();
        assertEquals(List.of("order_id", "location"),
                transformed.columns().stream().map(CanvasColumnSchema::name).toList());
        assertEquals(
                new CrsReference("EPSG", 3857),
                transformed.columns().get(1).geometry().crs()
        );
        assertTrue(result.propagatedTables().containsKey("orders"));
    }

    @Test
    void buildsAllSupportedSpatialPredicatesAsInnerJoins() {
        SparkCanvasTable orders = table(
                "orders",
                List.of(
                        scalar("order_id"),
                        geometry("location", GeometryKind.POINT, 4326)
                )
        );
        SparkCanvasTable regions = table(
                "regions",
                List.of(
                        scalar("region_id"),
                        geometry("boundary", GeometryKind.POLYGON, 4326)
                )
        );

        for (SpatialPredicate predicate : SpatialPredicate.values()) {
            RecordingIssueSink issues = new RecordingIssueSink();
            String outputTableName = "joined_" + predicate.name().toLowerCase();
            CanvasNodeOperationResult result = new SpatialJoinNodeOperator().apply(
                    new SpatialJoinNodeDefinition(
                            UUID.randomUUID().toString(),
                            predicate.name(),
                            LAYOUT,
                            new SpatialJoinConfiguration(
                                    "orders",
                                    "regions",
                                    outputTableName,
                                    JoinType.INNER,
                                    List.of(new SpatialJoinCondition(
                                            "location",
                                            predicate,
                                            "boundary"
                                    ))
                            )
                    ),
                    Map.of("orders", orders, "regions", regions),
                    context(issues)
            );

            assertFalse(issues.hasErrors(), () -> predicate + ": " + issues.codes);
            assertEquals(
                    List.of("order_id", "location", "region_id", "boundary"),
                    result.propagatedTables().get(outputTableName).schema().columns().stream()
                            .map(CanvasColumnSchema::name)
                            .toList()
            );
        }
    }

    @Test
    void rejectsSpatialJoinAcrossDifferentCrs() {
        SparkCanvasTable orders = table(
                "orders",
                List.of(geometry("location", GeometryKind.POINT, 4326))
        );
        SparkCanvasTable regions = table(
                "regions",
                List.of(geometry("boundary", GeometryKind.POLYGON, 3857))
        );
        RecordingIssueSink issues = new RecordingIssueSink();

        CanvasNodeOperationResult result = new SpatialJoinNodeOperator().apply(
                new SpatialJoinNodeDefinition(
                        UUID.randomUUID().toString(),
                        "空间连接",
                        LAYOUT,
                        new SpatialJoinConfiguration(
                                "orders",
                                "regions",
                                "joined",
                                JoinType.INNER,
                                List.of(new SpatialJoinCondition(
                                        "location",
                                        SpatialPredicate.WITHIN,
                                        "boundary"
                                ))
                        )
                ),
                Map.of("orders", orders, "regions", regions),
                context(issues)
        );

        assertTrue(issues.codes.contains("SPATIAL_CRS_MISMATCH"));
        assertTrue(result.propagatedTables().isEmpty());
    }

    @Test
    void projectsSpatialJoinFieldsWithExplicitSidesNamesAndOrderWithoutStartingAJob() {
        SparkCanvasTable orders = table(
                "orders",
                List.of(
                        scalar("id"),
                        stringColumn("name", true),
                        geometry("shape", GeometryKind.POINT, 4326)
                )
        );
        SparkCanvasTable districts = table(
                "districts",
                List.of(
                        scalar("id"),
                        stringColumn("name", true),
                        geometry("shape", GeometryKind.POLYGON, 4326)
                )
        );
        RecordingIssueSink issues = new RecordingIssueSink();
        AtomicInteger jobsStarted = new AtomicInteger();
        spark.sparkContext().addSparkListener(new SparkListener() {
            @Override
            public void onJobStart(SparkListenerJobStart jobStart) {
                jobsStarted.incrementAndGet();
            }
        });

        CanvasNodeOperationResult result = new SpatialJoinNodeOperator().apply(
                new SpatialJoinNodeDefinition(
                        UUID.randomUUID().toString(),
                        "显式输出空间连接",
                        LAYOUT,
                        new SpatialJoinConfiguration(
                                "orders",
                                "districts",
                                "orders_with_district",
                                JoinType.INNER,
                                List.of(new SpatialJoinCondition(
                                        "shape", SpatialPredicate.WITHIN, "shape")),
                                List.of(
                                        new JoinOutputColumn(
                                                JoinOutputColumnSource.RIGHT,
                                                "id",
                                                "districts_id",
                                                true),
                                        new JoinOutputColumn(
                                                JoinOutputColumnSource.LEFT,
                                                "id",
                                                "order_id",
                                                true),
                                        new JoinOutputColumn(
                                                JoinOutputColumnSource.RIGHT,
                                                "name",
                                                "districts_name",
                                                false),
                                        new JoinOutputColumn(
                                                JoinOutputColumnSource.LEFT,
                                                "shape",
                                                "order_shape",
                                                true),
                                        new JoinOutputColumn(
                                                JoinOutputColumnSource.RIGHT,
                                                "shape",
                                                "district_shape",
                                                true)
                                )
                        )
                ),
                Map.of("orders", orders, "districts", districts),
                context(issues)
        );

        assertFalse(issues.hasErrors(), () -> issues.codes.toString());
        assertEquals(
                List.of("districts_id", "order_id", "order_shape", "district_shape"),
                result.propagatedTables().get("orders_with_district").schema().columns().stream()
                        .map(CanvasColumnSchema::name)
                        .toList()
        );
        assertEquals(0, jobsStarted.get(), "Spatial Join analysis must not start a Spark job");
    }

    @Test
    void combinesSpatialAndAttributeConditionsWithOrdinaryEqualsSemantics() {
        CanvasTableSchema orderSchema = new CanvasTableSchema(
                "orders",
                null,
                List.of(
                        scalar("id"),
                        stringColumn("tenant_id", false),
                        geometry("shape", GeometryKind.POINT, 4326)
                )
        );
        CanvasTableSchema districtSchema = new CanvasTableSchema(
                "districts",
                null,
                List.of(
                        scalar("region_id"),
                        stringColumn("tenant_id", false),
                        geometry("boundary", GeometryKind.POLYGON, 4326)
                )
        );
        SparkCanvasTable orders = new SparkCanvasTable(orderSchema, spark.sql("""
                SELECT 1L AS id, 'A' AS tenant_id, ST_GeomFromWKT('POINT (1 1)') AS shape
                UNION ALL
                SELECT 2L AS id, 'B' AS tenant_id, ST_GeomFromWKT('POINT (2 2)') AS shape
                """));
        SparkCanvasTable districts = new SparkCanvasTable(districtSchema, spark.sql("""
                SELECT 10L AS region_id, 'A' AS tenant_id,
                       ST_GeomFromWKT('POLYGON ((0 0, 0 3, 3 3, 3 0, 0 0))') AS boundary
                """));
        RecordingIssueSink issues = new RecordingIssueSink();

        CanvasNodeOperationResult result = new SpatialJoinNodeOperator().apply(
                new SpatialJoinNodeDefinition(
                        UUID.randomUUID().toString(),
                        "空间和属性连接",
                        LAYOUT,
                        new SpatialJoinConfiguration(
                                "orders",
                                "districts",
                                "orders_with_district",
                                JoinType.INNER,
                                List.of(new SpatialJoinCondition(
                                        "shape", SpatialPredicate.WITHIN, "boundary")),
                                List.of(new JoinCondition(
                                        "tenant_id", JoinOperator.EQUALS, "tenant_id")),
                                List.of(
                                        new JoinOutputColumn(
                                                JoinOutputColumnSource.LEFT, "id", "id", true),
                                        new JoinOutputColumn(
                                                JoinOutputColumnSource.RIGHT,
                                                "region_id",
                                                "region_id",
                                                true)
                                )
                        )
                ),
                Map.of("orders", orders, "districts", districts),
                context(issues)
        );

        assertFalse(issues.hasErrors(), () -> issues.codes.toString());
        List<Row> rows = result.propagatedTables().get("orders_with_district")
                .dataset().collectAsList();
        assertEquals(1, rows.size());
        assertEquals(1L, rows.getFirst().getLong(0));
        assertEquals(10L, rows.getFirst().getLong(1));
    }

    @Test
    void leftSpatialJoinKeepsAllTargetFeaturesAndMakesJoinFieldsNullable() {
        CanvasTableSchema targetSchema = new CanvasTableSchema(
                "targets",
                null,
                List.of(
                        scalar("target_id"),
                        geometry("shape", GeometryKind.POINT, 4326)
                )
        );
        CanvasTableSchema joinSchema = new CanvasTableSchema(
                "districts",
                null,
                List.of(
                        scalar("district_id"),
                        geometry("boundary", GeometryKind.POLYGON, 4326)
                )
        );
        SparkCanvasTable targets = new SparkCanvasTable(targetSchema, spark.sql("""
                SELECT 1L AS target_id, ST_GeomFromWKT('POINT (1 1)') AS shape
                UNION ALL
                SELECT 2L AS target_id, ST_GeomFromWKT('POINT (10 10)') AS shape
                """));
        SparkCanvasTable districts = new SparkCanvasTable(joinSchema, spark.sql("""
                SELECT 10L AS district_id,
                       ST_GeomFromWKT('POLYGON ((0 0, 0 3, 3 3, 3 0, 0 0))') AS boundary
                UNION ALL
                SELECT 11L AS district_id,
                       ST_GeomFromWKT('POLYGON ((0 0, 0 2, 2 2, 2 0, 0 0))') AS boundary
                """));
        RecordingIssueSink issues = new RecordingIssueSink();

        CanvasNodeOperationResult result = new SpatialJoinNodeOperator().apply(
                new SpatialJoinNodeDefinition(
                        UUID.randomUUID().toString(),
                        "保留全部目标要素",
                        LAYOUT,
                        new SpatialJoinConfiguration(
                                "targets",
                                "districts",
                                "targets_with_district",
                                JoinType.LEFT,
                                List.of(new SpatialJoinCondition(
                                        "shape", SpatialPredicate.WITHIN, "boundary")),
                                null,
                                List.of(
                                        new JoinOutputColumn(
                                                JoinOutputColumnSource.LEFT,
                                                "target_id",
                                                "target_id",
                                                true),
                                        new JoinOutputColumn(
                                                JoinOutputColumnSource.RIGHT,
                                                "district_id",
                                                "district_id",
                                                true)
                                ),
                                SpatialJoinOperation.JOIN_ONE_TO_MANY
                        )
                ),
                Map.of("targets", targets, "districts", districts),
                context(issues)
        );

        List<Row> rows = result.propagatedTables().get("targets_with_district")
                .dataset().orderBy("target_id", "district_id").collectAsList();
        CanvasTableSchema resultSchema = result.propagatedTables()
                .get("targets_with_district").schema();

        assertFalse(issues.hasErrors(), () -> issues.codes.toString());
        assertEquals(3, rows.size());
        assertEquals(10L, rows.getFirst().getLong(1));
        assertEquals(11L, rows.get(1).getLong(1));
        assertTrue(rows.getLast().isNullAt(1));
        assertFalse(resultSchema.columns().getFirst().nullable());
        assertTrue(resultSchema.columns().getLast().nullable());
    }

    @Test
    void rejectsRightAndFullSpatialJoins() {
        SparkCanvasTable targets = table(
                "targets",
                List.of(scalar("target_id"), geometry("shape", GeometryKind.POINT, 4326))
        );
        SparkCanvasTable districts = table(
                "districts",
                List.of(scalar("district_id"), geometry("boundary", GeometryKind.POLYGON, 4326))
        );

        for (JoinType unsupported : List.of(JoinType.RIGHT, JoinType.FULL)) {
            RecordingIssueSink issues = new RecordingIssueSink();
            new SpatialJoinNodeOperator().apply(
                    new SpatialJoinNodeDefinition(
                            UUID.randomUUID().toString(),
                            unsupported.name(),
                            LAYOUT,
                            new SpatialJoinConfiguration(
                                    "targets",
                                    "districts",
                                    "joined",
                                    unsupported,
                                    List.of(new SpatialJoinCondition(
                                            "shape", SpatialPredicate.WITHIN, "boundary"))
                            )
                    ),
                    Map.of("targets", targets, "districts", districts),
                    context(issues)
            );
            assertTrue(issues.codes.contains("SPATIAL_JOIN_TYPE_UNSUPPORTED"),
                    () -> unsupported + ": " + issues.codes);
        }
    }

    @Test
    void validatesSpatialJoinAttributeConditions() {
        SparkCanvasTable orders = table(
                "orders",
                List.of(scalar("tenant_id"), geometry("shape", GeometryKind.POINT, 4326))
        );
        SparkCanvasTable districts = table(
                "districts",
                List.of(scalar("tenant_id"), geometry("shape", GeometryKind.POLYGON, 4326))
        );
        Map<String, SparkCanvasTable> inputs = Map.of("orders", orders, "districts", districts);

        for (var invalid : List.of(
                new AttributeProblem(
                        List.of(
                                new JoinCondition("tenant_id", JoinOperator.EQUALS, "tenant_id"),
                                new JoinCondition("tenant_id", JoinOperator.EQUALS, "tenant_id")),
                        "DUPLICATE_JOIN_CONDITION"),
                new AttributeProblem(
                        List.of(new JoinCondition("shape", JoinOperator.EQUALS, "shape")),
                        "GEOMETRY_FIELD_OPERATION_UNSUPPORTED"),
                new AttributeProblem(
                        List.of(new JoinCondition("missing", JoinOperator.EQUALS, "tenant_id")),
                        "COLUMN_NOT_FOUND"),
                new AttributeProblem(
                        java.util.stream.IntStream.range(0, 9)
                                .mapToObj(ignored -> new JoinCondition(
                                        "tenant_id", JoinOperator.EQUALS, "tenant_id"))
                                .toList(),
                        "SPATIAL_JOIN_ATTRIBUTE_CONDITION_LIMIT_EXCEEDED")
        )) {
            RecordingIssueSink issues = new RecordingIssueSink();
            new SpatialJoinNodeOperator().apply(
                    spatialJoinDefinition(List.of(
                            new JoinOutputColumn(
                                    JoinOutputColumnSource.LEFT, "tenant_id", "tenant_id", true)),
                            invalid.conditions()),
                    inputs,
                    context(issues)
            );
            assertTrue(issues.codes.contains(invalid.expectedCode()), () -> issues.codes.toString());
        }
    }

    private record AttributeProblem(List<JoinCondition> conditions, String expectedCode) {
    }

    @Test
    void validatesSpatialJoinProjectionAndKeepsLegacyDuplicateRejection() {
        SparkCanvasTable orders = table(
                "orders",
                List.of(scalar("id"), geometry("shape", GeometryKind.POINT, 4326))
        );
        SparkCanvasTable districts = table(
                "districts",
                List.of(scalar("id"), geometry("shape", GeometryKind.POLYGON, 4326))
        );
        Map<String, SparkCanvasTable> inputs = Map.of("orders", orders, "districts", districts);

        RecordingIssueSink legacyIssues = new RecordingIssueSink();
        new SpatialJoinNodeOperator().apply(
                spatialJoinDefinition(null),
                inputs,
                context(legacyIssues)
        );
        assertTrue(legacyIssues.codes.contains("DUPLICATE_COLUMN_NAME"));

        for (var invalid : List.of(
                new ProjectionProblem(
                        List.of(
                                new JoinOutputColumn(JoinOutputColumnSource.LEFT, "id", "left_id", true),
                                new JoinOutputColumn(JoinOutputColumnSource.LEFT, "id", "second_id", true)),
                        "DUPLICATE_JOIN_OUTPUT_SOURCE"),
                new ProjectionProblem(
                        List.of(
                                new JoinOutputColumn(JoinOutputColumnSource.LEFT, "id", "ID", true),
                                new JoinOutputColumn(JoinOutputColumnSource.RIGHT, "id", "id", true)),
                        "DUPLICATE_COLUMN_NAME"),
                new ProjectionProblem(
                        List.of(new JoinOutputColumn(
                                JoinOutputColumnSource.RIGHT, "missing", "missing", true)),
                        "COLUMN_NOT_FOUND")
        )) {
            RecordingIssueSink issues = new RecordingIssueSink();
            new SpatialJoinNodeOperator().apply(
                    spatialJoinDefinition(invalid.columns()),
                    inputs,
                    context(issues)
            );
            assertTrue(issues.codes.contains(invalid.expectedCode()), () -> issues.codes.toString());
        }
    }

    private SpatialJoinNodeDefinition spatialJoinDefinition(List<JoinOutputColumn> outputColumns) {
        return spatialJoinDefinition(outputColumns, null);
    }

    private SpatialJoinNodeDefinition spatialJoinDefinition(
            List<JoinOutputColumn> outputColumns,
            List<JoinCondition> attributeConditions
    ) {
        return new SpatialJoinNodeDefinition(
                UUID.randomUUID().toString(),
                "空间连接",
                LAYOUT,
                new SpatialJoinConfiguration(
                        "orders",
                        "districts",
                        "orders_with_district",
                        JoinType.INNER,
                        List.of(new SpatialJoinCondition(
                                "shape", SpatialPredicate.WITHIN, "shape")),
                        attributeConditions,
                        outputColumns)
        );
    }

    private record ProjectionProblem(List<JoinOutputColumn> columns, String expectedCode) {
    }

    @Test
    void summarizesAllOneToOneMatchesAndKeepsUnmatchedTargets() {
        Map<String, SparkCanvasTable> inputs = spatialJoinOneToOneInputs();
        SpatialJoinOneToOneOptions oneToOne = new SpatialJoinOneToOneOptions(
                SpatialJoinOneToOneMode.SUMMARIZE_MATCHES,
                "join_count",
                List.of(
                        spatialJoinStatistic(SpatialJoinSummaryStatisticKind.SUM, "amount", "amount_sum"),
                        spatialJoinStatistic(SpatialJoinSummaryStatisticKind.MIN, "amount", "amount_min"),
                        spatialJoinStatistic(SpatialJoinSummaryStatisticKind.MAX, "amount", "amount_max"),
                        spatialJoinStatistic(SpatialJoinSummaryStatisticKind.MEAN, "amount", "amount_mean"),
                        spatialJoinStatistic(SpatialJoinSummaryStatisticKind.STDDEV, "amount", "amount_stddev")
                ),
                null
        );
        RecordingIssueSink issues = new RecordingIssueSink();

        CanvasNodeOperationResult result = new SpatialJoinNodeOperator().apply(
                spatialJoinOneToOneDefinition(
                        JoinType.LEFT,
                        List.of(
                                new JoinOutputColumn(
                                        JoinOutputColumnSource.LEFT,
                                        "target_id", "target_id", true),
                                new JoinOutputColumn(
                                        JoinOutputColumnSource.LEFT,
                                        "shape", "shape", true)
                        ),
                        oneToOne
                ),
                inputs,
                context(issues)
        );

        List<Row> rows = result.propagatedTables().get("targets_joined")
                .dataset().orderBy("target_id").collectAsList();
        assertFalse(issues.hasErrors(), () -> issues.codes.toString());
        assertEquals(List.of(
                        "target_id", "shape", "join_count", "amount_sum", "amount_min",
                        "amount_max", "amount_mean", "amount_stddev"),
                result.propagatedTables().get("targets_joined").schema().columns().stream()
                        .map(CanvasColumnSchema::name).toList());
        assertEquals(2, rows.size());
        Row matched = rows.getFirst();
        assertEquals(3L, matched.<Number>getAs("join_count").longValue());
        assertEquals(10d, matched.<Number>getAs("amount_sum").doubleValue(), 0.000001d);
        assertEquals(4d, matched.<Number>getAs("amount_min").doubleValue(), 0.000001d);
        assertEquals(6d, matched.<Number>getAs("amount_max").doubleValue(), 0.000001d);
        assertEquals(5d, matched.<Number>getAs("amount_mean").doubleValue(), 0.000001d);
        assertEquals(Math.sqrt(2d), matched.<Number>getAs("amount_stddev").doubleValue(), 0.000001d);
        Row unmatched = rows.getLast();
        assertEquals(0L, unmatched.<Number>getAs("join_count").longValue());
        assertNull(unmatched.getAs("amount_sum"));
        assertNull(unmatched.getAs("amount_stddev"));
    }

    @Test
    void keepsOneMatchWithArcGisStrategiesAndExplicitStableOrdering() {
        Map<String, SparkCanvasTable> inputs = spatialJoinOneToOneInputs();
        record Example(
                SpatialJoinKeepStrategy strategy,
                String primary,
                SortDirection stableDirection,
                long expectedDistrictId
        ) { }
        for (Example example : List.of(
                new Example(SpatialJoinKeepStrategy.FIRST, null, SortDirection.ASC, 10L),
                new Example(SpatialJoinKeepStrategy.LARGEST, "score", SortDirection.DESC, 11L),
                new Example(SpatialJoinKeepStrategy.SMALLEST, "score", SortDirection.ASC, 12L),
                new Example(SpatialJoinKeepStrategy.NEWEST, "updated_at", SortDirection.ASC, 11L),
                new Example(SpatialJoinKeepStrategy.OLDEST, "updated_at", SortDirection.ASC, 12L)
        )) {
            SpatialJoinKeepRule keepRule = new SpatialJoinKeepRule(
                    example.strategy(),
                    example.primary(),
                    List.of(new SortField(
                            "district_id", example.stableDirection(), NullOrdering.LAST))
            );
            RecordingIssueSink issues = new RecordingIssueSink();
            CanvasNodeOperationResult result = new SpatialJoinNodeOperator().apply(
                    spatialJoinOneToOneDefinition(
                            JoinType.INNER,
                            List.of(
                                    new JoinOutputColumn(
                                            JoinOutputColumnSource.LEFT,
                                            "target_id", "target_id", true),
                                    new JoinOutputColumn(
                                            JoinOutputColumnSource.RIGHT,
                                            "district_id", "district_id", true)
                            ),
                            new SpatialJoinOneToOneOptions(
                                    SpatialJoinOneToOneMode.KEEP_ONE,
                                    "join_count",
                                    List.of(),
                                    keepRule
                            )
                    ),
                    inputs,
                    context(issues)
            );

            Row selected = result.propagatedTables().get("targets_joined").dataset().head();
            assertFalse(issues.hasErrors(), () -> example.strategy() + ": " + issues.codes);
            assertEquals(example.expectedDistrictId(), selected.<Number>getAs("district_id").longValue(),
                    example.strategy().name());
        }
    }

    @Test
    void failsInsteadOfChoosingArbitrarilyWhenKeepOrderStillTies() {
        Map<String, SparkCanvasTable> inputs = new LinkedHashMap<>(spatialJoinOneToOneInputs());
        CanvasTableSchema districtsSchema = inputs.get("districts").schema();
        inputs.put("districts", new SparkCanvasTable(districtsSchema, spark.sql("""
                SELECT 10L AS district_id, 9D AS score, 4D AS amount,
                       TIMESTAMP '2024-01-01 00:00:00' AS updated_at,
                       ST_GeomFromWKT('POLYGON ((0 0, 0 3, 3 3, 3 0, 0 0))') AS boundary
                UNION ALL
                SELECT 10L AS district_id, 9D AS score, 6D AS amount,
                       TIMESTAMP '2024-01-01 00:00:00' AS updated_at,
                       ST_GeomFromWKT('POLYGON ((0 0, 0 3, 3 3, 3 0, 0 0))') AS boundary
                """)));
        SpatialJoinKeepRule keepRule = new SpatialJoinKeepRule(
                SpatialJoinKeepStrategy.LARGEST,
                "score",
                List.of(new SortField("district_id", SortDirection.ASC, NullOrdering.LAST))
        );
        CanvasNodeOperationResult result = new SpatialJoinNodeOperator().apply(
                spatialJoinOneToOneDefinition(
                        JoinType.INNER,
                        List.of(new JoinOutputColumn(
                                JoinOutputColumnSource.RIGHT,
                                "district_id", "district_id", true)),
                        new SpatialJoinOneToOneOptions(
                                SpatialJoinOneToOneMode.KEEP_ONE,
                                "join_count",
                                List.of(),
                                keepRule
                        )
                ),
                inputs,
                context(new RecordingIssueSink())
        );

        Throwable failure = assertThrows(
                Throwable.class,
                () -> result.propagatedTables().get("targets_joined").dataset().collectAsList()
        );
        assertTrue(exceptionMessages(failure).contains("SPATIAL_JOIN_KEEP_ORDER_NOT_UNIQUE"),
                () -> exceptionMessages(failure));
    }

    @Test
    void validatesOneToOneSummaryProjectionStatisticsAndKeepRuleTypes() {
        Map<String, SparkCanvasTable> inputs = spatialJoinOneToOneInputs();
        List<OneToOneProblem> invalid = List.of(
                new OneToOneProblem(
                        List.of(new JoinOutputColumn(
                                JoinOutputColumnSource.RIGHT,
                                "district_id", "district_id", true)),
                        new SpatialJoinOneToOneOptions(
                                SpatialJoinOneToOneMode.SUMMARIZE_MATCHES,
                                "join_count", List.of(), null),
                        "SPATIAL_JOIN_SUMMARY_RIGHT_FIELD_UNSUPPORTED"),
                new OneToOneProblem(
                        List.of(new JoinOutputColumn(
                                JoinOutputColumnSource.LEFT,
                                "target_id", "target_id", true)),
                        new SpatialJoinOneToOneOptions(
                                SpatialJoinOneToOneMode.SUMMARIZE_MATCHES,
                                "join_count",
                                List.of(spatialJoinStatistic(
                                        SpatialJoinSummaryStatisticKind.SUM,
                                        "label", "label_sum")),
                                null),
                        "NUMERIC_COLUMN_REQUIRED"),
                new OneToOneProblem(
                        List.of(new JoinOutputColumn(
                                JoinOutputColumnSource.LEFT,
                                "target_id", "target_id", true)),
                        new SpatialJoinOneToOneOptions(
                                SpatialJoinOneToOneMode.KEEP_ONE,
                                "join_count",
                                List.of(),
                                new SpatialJoinKeepRule(
                                        SpatialJoinKeepStrategy.NEWEST,
                                        "score",
                                        List.of(new SortField(
                                                "district_id",
                                                SortDirection.ASC,
                                                NullOrdering.LAST)))),
                        "TEMPORAL_COLUMN_REQUIRED"),
                new OneToOneProblem(
                        List.of(new JoinOutputColumn(
                                JoinOutputColumnSource.LEFT,
                                "target_id", "target_id", true)),
                        new SpatialJoinOneToOneOptions(
                                SpatialJoinOneToOneMode.KEEP_ONE,
                                "join_count",
                                List.of(),
                                new SpatialJoinKeepRule(
                                        SpatialJoinKeepStrategy.FIRST,
                                        null,
                                        List.of())),
                        "SPATIAL_JOIN_STABLE_ORDER_REQUIRED")
        );
        for (OneToOneProblem problem : invalid) {
            RecordingIssueSink issues = new RecordingIssueSink();
            new SpatialJoinNodeOperator().apply(
                    spatialJoinOneToOneDefinition(
                            JoinType.INNER, problem.outputColumns(), problem.options()),
                    inputs,
                    context(issues)
            );
            assertTrue(issues.codes.contains(problem.expectedCode()),
                    () -> problem.expectedCode() + ": " + issues.codes);
        }
    }

    private record OneToOneProblem(
            List<JoinOutputColumn> outputColumns,
            SpatialJoinOneToOneOptions options,
            String expectedCode
    ) { }

    private Map<String, SparkCanvasTable> spatialJoinOneToOneInputs() {
        CanvasTableSchema targetsSchema = new CanvasTableSchema(
                "targets",
                null,
                List.of(
                        scalar("target_id"),
                        geometry("shape", GeometryKind.POINT, 4326)
                )
        );
        CanvasTableSchema districtsSchema = new CanvasTableSchema(
                "districts",
                null,
                List.of(
                        scalar("district_id"),
                        typedColumn("score", PlatformDataType.DOUBLE, true),
                        typedColumn("amount", PlatformDataType.DOUBLE, true),
                        typedColumn("updated_at", PlatformDataType.TIMESTAMP, true),
                        stringColumn("label", true),
                        geometry("boundary", GeometryKind.POLYGON, 4326)
                )
        );
        Map<String, SparkCanvasTable> inputs = new LinkedHashMap<>();
        inputs.put("targets", new SparkCanvasTable(targetsSchema, spark.sql("""
                SELECT 1L AS target_id, ST_GeomFromWKT('POINT (1 1)') AS shape
                UNION ALL
                SELECT 2L AS target_id, ST_GeomFromWKT('POINT (10 10)') AS shape
                """)));
        inputs.put("districts", new SparkCanvasTable(districtsSchema, spark.sql("""
                SELECT 10L AS district_id, 9D AS score, 4D AS amount,
                       TIMESTAMP '2024-01-01 00:00:00' AS updated_at, 'A' AS label,
                       ST_GeomFromWKT('POLYGON ((0 0, 0 3, 3 3, 3 0, 0 0))') AS boundary
                UNION ALL
                SELECT 11L AS district_id, 9D AS score, 6D AS amount,
                       TIMESTAMP '2025-01-01 00:00:00' AS updated_at, 'B' AS label,
                       ST_GeomFromWKT('POLYGON ((0 0, 0 2, 2 2, 2 0, 0 0))') AS boundary
                UNION ALL
                SELECT 12L AS district_id, 4D AS score, CAST(NULL AS DOUBLE) AS amount,
                       TIMESTAMP '2023-01-01 00:00:00' AS updated_at, 'C' AS label,
                       ST_GeomFromWKT('POLYGON ((0 0, 0 4, 4 4, 4 0, 0 0))') AS boundary
                """)));
        return inputs;
    }

    private SpatialJoinNodeDefinition spatialJoinOneToOneDefinition(
            JoinType joinType,
            List<JoinOutputColumn> outputColumns,
            SpatialJoinOneToOneOptions oneToOne
    ) {
        return new SpatialJoinNodeDefinition(
                UUID.randomUUID().toString(),
                "一对一空间连接",
                LAYOUT,
                new SpatialJoinConfiguration(
                        "targets",
                        "districts",
                        "targets_joined",
                        joinType,
                        List.of(new SpatialJoinCondition(
                                "shape", SpatialPredicate.WITHIN, "boundary")),
                        null,
                        outputColumns,
                        SpatialJoinOperation.JOIN_ONE_TO_ONE,
                        oneToOne
                )
        );
    }

    private static SpatialJoinSummaryStatistic spatialJoinStatistic(
            SpatialJoinSummaryStatisticKind kind,
            String source,
            String output
    ) {
        return new SpatialJoinSummaryStatistic(
                UUID.randomUUID().toString(), kind, source, output);
    }

    @Test
    void reportsUnsupportedGeometryCrsAndDimensionBeforeSparkAnalysis() {
        CanvasColumnSchema unsupported = new CanvasColumnSchema(
                "location",
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
                        GeometryKind.POINT,
                        new CrsReference("ESRI", 102100),
                        CoordinateDimension.XYZ
                )
        );
        RecordingIssueSink issues = new RecordingIssueSink();

        CanvasNodeSupport.validateSupportedGeometry(
                List.of(unsupported),
                "configuration.tableName",
                issues
        );

        assertTrue(issues.codes.contains("UNSUPPORTED_GEOMETRY_CRS"));
        assertTrue(issues.codes.contains("UNSUPPORTED_GEOMETRY_DIMENSION"));
    }

    @Test
    void constructsValidatesMeasuresAndSerializesGeometry() {
        SparkCanvasTable source = table(
                new CanvasTableSchema(
                        "raw",
                        CanvasTableOrigin.jdbc(UUID.randomUUID(), "raw"),
                        List.of(
                                scalar("id"),
                                stringColumn("wkt", false)
                        ),
                        CanvasDatasetKind.UNBOUNDED,
                        null,
                        null
                ),
                List.of(RowFactory.create(1L, "POLYGON ((0 0, 0 1, 1 1, 1 0, 0 0))"))
        );
        RecordingIssueSink constructIssues = new RecordingIssueSink();
        CanvasNodeOperationResult constructed = new GeometryConstructNodeOperator().apply(
                new GeometryConstructNodeDefinition(
                        UUID.randomUUID().toString(),
                        "Geometry 构造",
                        LAYOUT,
                        new GeometryConstructConfiguration(
                                "raw",
                                "geometry_table",
                                "shape",
                                new GeometryConstructSource.Wkt("wkt"),
                                geometryType(GeometryKind.POLYGON, 4326)
                        )
                ),
                Map.of("raw", source),
                context(constructIssues)
        );

        assertFalse(constructIssues.hasErrors(), () -> constructIssues.codes.toString());
        SparkCanvasTable geometryTable = constructed.propagatedTables().get("geometry_table");
        assertNotNull(geometryTable);
        assertTrue(constructed.propagatedTables().containsKey("raw"));
        assertEquals(CanvasDatasetKind.UNBOUNDED, geometryTable.schema().datasetKind());
        assertEquals(PlatformDataType.GEOMETRY, geometryTable.schema().columns().get(2).fieldType());
        assertFalse(geometryTable.schema().columns().get(2).nullable());
        assertNull(geometryTable.schema().origin());

        RecordingIssueSink validateIssues = new RecordingIssueSink();
        CanvasNodeOperationResult validated = new GeometryValidateNodeOperator().apply(
                new GeometryValidateNodeDefinition(
                        UUID.randomUUID().toString(),
                        "Geometry 校验",
                        LAYOUT,
                        new GeometryValidateConfiguration(
                                "geometry_table",
                                "validated",
                                "shape",
                                "is_valid",
                                "invalid_reason"
                        )
                ),
                constructed.propagatedTables(),
                context(validateIssues)
        );
        Row validatedRow = validated.propagatedTables().get("validated").dataset().head();
        CanvasTableSchema validatedSchema = validated.propagatedTables().get("validated").schema();
        assertFalse(validateIssues.hasErrors(), () -> validateIssues.codes.toString());
        assertTrue(validatedRow.getBoolean(validatedRow.fieldIndex("is_valid")));
        assertTrue(validatedRow.isNullAt(validatedRow.fieldIndex("invalid_reason")));
        assertFalse(validatedSchema.columns().get(3).nullable());
        assertTrue(validatedSchema.columns().get(4).nullable());
        assertNull(validatedSchema.origin());

        RecordingIssueSink measureIssues = new RecordingIssueSink();
        CanvasNodeOperationResult measured = new SpatialMeasureNodeOperator().apply(
                new SpatialMeasureNodeDefinition(
                        UUID.randomUUID().toString(),
                        "空间测量",
                        LAYOUT,
                        new SpatialMeasureConfiguration(
                                "geometry_table",
                                "measured",
                                List.of(new SpatialMeasurement.Area(
                                        "shape",
                                        SpatialMeasureMode.PLANAR,
                                        "area"
                                ))
                        )
                ),
                constructed.propagatedTables(),
                context(measureIssues)
        );
        Row measuredRow = measured.propagatedTables().get("measured").dataset().head();
        assertFalse(measureIssues.hasErrors(), () -> measureIssues.codes.toString());
        assertTrue(measureIssues.codes.contains("PLANAR_MEASURE_USES_ANGULAR_UNITS"));
        assertEquals(1d, measuredRow.getDouble(measuredRow.fieldIndex("area")), 0.000001d);
        assertNull(measured.propagatedTables().get("measured").schema().origin());

        RecordingIssueSink serializeIssues = new RecordingIssueSink();
        CanvasNodeOperationResult serialized = new GeometrySerializeNodeOperator().apply(
                new GeometrySerializeNodeDefinition(
                        UUID.randomUUID().toString(),
                        "Geometry 序列化",
                        LAYOUT,
                        new GeometrySerializeConfiguration(
                                "geometry_table",
                                "serialized",
                                "shape",
                                "shape_wkt",
                                GeometrySerializationFormat.WKT
                        )
                ),
                constructed.propagatedTables(),
                context(serializeIssues)
        );
        Row serializedRow = serialized.propagatedTables().get("serialized").dataset().head();
        assertFalse(serializeIssues.hasErrors(), () -> serializeIssues.codes.toString());
        assertTrue(serializedRow.getString(serializedRow.fieldIndex("shape_wkt"))
                .startsWith("POLYGON"));
        assertFalse(serialized.propagatedTables().get("serialized")
                .schema().columns().get(3).nullable());
        assertNull(serialized.propagatedTables().get("serialized").schema().origin());
    }

    @Test
    void constructsGeometryFromWktWkbGeoJsonAndNumericCoordinates() {
        GeometryFactory geometryFactory = new GeometryFactory();
        byte[] wkb = new WKBWriter().write(
                geometryFactory.createPoint(new Coordinate(1d, 2d))
        );
        SparkCanvasTable source = table(
                new CanvasTableSchema(
                        "raw",
                        null,
                        List.of(
                                stringColumn("wkt", false),
                                typedColumn("wkb", PlatformDataType.BINARY, false),
                                stringColumn("geojson", false),
                                typedColumn("x", PlatformDataType.INTEGER, false),
                                typedColumn("y", PlatformDataType.LONG, false)
                        ),
                        CanvasDatasetKind.BOUNDED,
                        null,
                        null
                ),
                List.of(RowFactory.create(
                        "POINT (1 2)",
                        wkb,
                        "{\"type\":\"Point\",\"coordinates\":[1,2]}",
                        1,
                        2L
                ))
        );
        List<GeometryConstructSource> sources = List.of(
                new GeometryConstructSource.Wkt("wkt"),
                new GeometryConstructSource.Wkb("wkb"),
                new GeometryConstructSource.GeoJson("geojson"),
                new GeometryConstructSource.PointFromXy("x", "y")
        );

        for (int index = 0; index < sources.size(); index++) {
            RecordingIssueSink issues = new RecordingIssueSink();
            String outputTableName = "geometry_" + index;
            CanvasNodeOperationResult result = new GeometryConstructNodeOperator().apply(
                    new GeometryConstructNodeDefinition(
                            UUID.randomUUID().toString(),
                            "Geometry 构造",
                            LAYOUT,
                            new GeometryConstructConfiguration(
                                    "raw",
                                    outputTableName,
                                    "shape",
                                    sources.get(index),
                                    geometryType(GeometryKind.POINT, 4326)
                            )
                    ),
                    Map.of("raw", source),
                    context(issues)
            );

            Geometry geometry = result.propagatedTables().get(outputTableName)
                    .dataset().head().getAs("shape");
            assertFalse(issues.hasErrors(), () -> issues.codes.toString());
            assertEquals("Point", geometry.getGeometryType());
            assertEquals(4326, geometry.getSRID());
            assertEquals(1d, geometry.getCoordinate().getX(), 0.000001d);
            assertEquals(2d, geometry.getCoordinate().getY(), 0.000001d);
        }
    }

    @Test
    void calculatesAllSpatialMeasurementKindsInPlanarAndSpheroidModes() {
        SparkCanvasTable current = table(
                new CanvasTableSchema(
                        "raw",
                        null,
                        List.of(
                                stringColumn("polygon_wkt", false),
                                stringColumn("line_wkt", false),
                                stringColumn("point_a_wkt", false),
                                stringColumn("point_b_wkt", false)
                        ),
                        CanvasDatasetKind.BOUNDED,
                        null,
                        null
                ),
                List.of(RowFactory.create(
                        "POLYGON ((0 0, 0 1, 1 1, 1 0, 0 0))",
                        "LINESTRING (0 0, 0 1)",
                        "POINT (0 0)",
                        "POINT (0 1)"
                ))
        );
        String currentName = "raw";
        String[][] geometries = {
                {"polygon_wkt", "polygon", "POLYGON"},
                {"line_wkt", "line", "LINESTRING"},
                {"point_a_wkt", "point_a", "POINT"},
                {"point_b_wkt", "point_b", "POINT"}
        };
        for (int index = 0; index < geometries.length; index++) {
            String outputName = "geometry_step_" + index;
            GeometryKind kind = GeometryKind.valueOf(geometries[index][2]);
            RecordingIssueSink issues = new RecordingIssueSink();
            CanvasNodeOperationResult result = new GeometryConstructNodeOperator().apply(
                    new GeometryConstructNodeDefinition(
                            UUID.randomUUID().toString(),
                            "Geometry 构造",
                            LAYOUT,
                            new GeometryConstructConfiguration(
                                    currentName,
                                    outputName,
                                    geometries[index][1],
                                    new GeometryConstructSource.Wkt(geometries[index][0]),
                                    geometryType(kind, 4326)
                            )
                    ),
                    Map.of(currentName, current),
                    context(issues)
            );
            assertFalse(issues.hasErrors(), () -> issues.codes.toString());
            current = result.propagatedTables().get(outputName);
            currentName = outputName;
        }

        List<SpatialMeasurement> measurements = List.of(
                new SpatialMeasurement.Area(
                        "polygon", SpatialMeasureMode.PLANAR, "area_planar"),
                new SpatialMeasurement.Length(
                        "line", SpatialMeasureMode.PLANAR, "length_planar"),
                new SpatialMeasurement.Perimeter(
                        "polygon", SpatialMeasureMode.PLANAR, "perimeter_planar"),
                new SpatialMeasurement.Distance(
                        "point_a", "point_b", SpatialMeasureMode.PLANAR, "distance_planar"),
                new SpatialMeasurement.X("point_b", "point_x"),
                new SpatialMeasurement.Y("point_b", "point_y"),
                new SpatialMeasurement.Area(
                        "polygon", SpatialMeasureMode.SPHEROID, "area_spheroid"),
                new SpatialMeasurement.Length(
                        "line", SpatialMeasureMode.SPHEROID, "length_spheroid"),
                new SpatialMeasurement.Perimeter(
                        "polygon", SpatialMeasureMode.SPHEROID, "perimeter_spheroid"),
                new SpatialMeasurement.Distance(
                        "point_a", "point_b", SpatialMeasureMode.SPHEROID,
                        "distance_spheroid")
        );
        RecordingIssueSink issues = new RecordingIssueSink();
        CanvasNodeOperationResult result = new SpatialMeasureNodeOperator().apply(
                new SpatialMeasureNodeDefinition(
                        UUID.randomUUID().toString(),
                        "空间测量",
                        LAYOUT,
                        new SpatialMeasureConfiguration(currentName, "measured", measurements)
                ),
                Map.of(currentName, current),
                context(issues)
        );
        SparkCanvasTable measured = result.propagatedTables().get("measured");
        Row row = measured.dataset().head();

        assertFalse(issues.hasErrors(), () -> issues.codes.toString());
        assertEquals(1d, row.getDouble(row.fieldIndex("area_planar")), 0.000001d);
        assertEquals(1d, row.getDouble(row.fieldIndex("length_planar")), 0.000001d);
        assertEquals(4d, row.getDouble(row.fieldIndex("perimeter_planar")), 0.000001d);
        assertEquals(1d, row.getDouble(row.fieldIndex("distance_planar")), 0.000001d);
        assertEquals(0d, row.getDouble(row.fieldIndex("point_x")), 0.000001d);
        assertEquals(1d, row.getDouble(row.fieldIndex("point_y")), 0.000001d);
        assertTrue(row.getDouble(row.fieldIndex("area_spheroid")) > 12_000_000_000d);
        assertTrue(row.getDouble(row.fieldIndex("area_spheroid")) < 13_000_000_000d);
        assertTrue(row.getDouble(row.fieldIndex("length_spheroid")) > 100_000d);
        assertTrue(row.getDouble(row.fieldIndex("length_spheroid")) < 112_000d);
        assertTrue(row.getDouble(row.fieldIndex("perimeter_spheroid")) > 400_000d);
        assertTrue(row.getDouble(row.fieldIndex("perimeter_spheroid")) < 450_000d);
        assertTrue(row.getDouble(row.fieldIndex("distance_spheroid")) > 100_000d);
        assertTrue(row.getDouble(row.fieldIndex("distance_spheroid")) < 112_000d);
        assertEquals(
                measurements.stream().map(SpatialMeasurement::outputColumnName).toList(),
                measured.schema().columns().stream()
                        .skip(measured.schema().columns().size() - measurements.size())
                        .map(CanvasColumnSchema::name)
                        .toList()
        );
        assertTrue(measured.schema().columns().stream()
                .skip(measured.schema().columns().size() - measurements.size())
                .allMatch(CanvasColumnSchema::nullable));
    }

    @Test
    void convertsSpatialMeasurementOutputUnitsWithoutChangingLegacyDefaults() {
        SparkCanvasTable polygon = constructWktTable(
                "POLYGON ((0 0, 0 1, 1 1, 1 0, 0 0))",
                GeometryKind.POLYGON,
                "polygon_4326"
        );
        RecordingIssueSink transformIssues = new RecordingIssueSink();
        CanvasNodeOperationResult transformed = new SpatialTransformNodeOperator().apply(
                new SpatialTransformNodeDefinition(
                        UUID.randomUUID().toString(),
                        "投影 Polygon",
                        LAYOUT,
                        new SpatialTransformConfiguration(
                                "polygon_4326", "polygon_3857", "shape",
                                new CrsReference("EPSG", 3857))
                ),
                Map.of("polygon_4326", polygon),
                context(transformIssues)
        );
        assertFalse(transformIssues.hasErrors(), () -> transformIssues.codes.toString());

        RecordingIssueSink planarIssues = new RecordingIssueSink();
        CanvasNodeOperationResult planar = new SpatialMeasureNodeOperator().apply(
                new SpatialMeasureNodeDefinition(
                        UUID.randomUUID().toString(),
                        "平面面积单位",
                        LAYOUT,
                        new SpatialMeasureConfiguration(
                                "polygon_3857",
                                "planar_measured",
                                List.of(new SpatialMeasurement.Area(
                                        "shape", SpatialMeasureMode.PLANAR, "area_km2",
                                        SpatialAreaUnit.SQUARE_KILOMETERS))
                        )
                ),
                Map.of("polygon_3857", transformed.propagatedTables().get("polygon_3857")),
                context(planarIssues)
        );
        double planarSquareKilometres = planar.propagatedTables().get("planar_measured")
                .dataset().head().getAs("area_km2");
        assertFalse(planarIssues.hasErrors(), () -> planarIssues.codes.toString());
        assertTrue(planarSquareKilometres > 12_000d);
        assertTrue(planarSquareKilometres < 13_000d);

        SparkCanvasTable line = constructWktTable(
                "LINESTRING (0 0, 0 1)", GeometryKind.LINESTRING, "line_4326");
        RecordingIssueSink spheroidIssues = new RecordingIssueSink();
        CanvasNodeOperationResult spheroid = new SpatialMeasureNodeOperator().apply(
                new SpatialMeasureNodeDefinition(
                        UUID.randomUUID().toString(),
                        "椭球长度单位",
                        LAYOUT,
                        new SpatialMeasureConfiguration(
                                "line_4326",
                                "spheroid_measured",
                                List.of(
                                        new SpatialMeasurement.Length(
                                                "shape", SpatialMeasureMode.SPHEROID,
                                                "length_km", SpatialDistanceUnit.KILOMETERS),
                                        new SpatialMeasurement.Length(
                                                "shape", SpatialMeasureMode.SPHEROID,
                                                "legacy_metres")
                                )
                        )
                ),
                Map.of("line_4326", line),
                context(spheroidIssues)
        );
        Row spheroidRow = spheroid.propagatedTables().get("spheroid_measured").dataset().head();
        assertFalse(spheroidIssues.hasErrors(), () -> spheroidIssues.codes.toString());
        assertTrue(spheroidRow.<Double>getAs("length_km") > 100d);
        assertTrue(spheroidRow.<Double>getAs("length_km") < 112d);
        assertEquals(
                spheroidRow.<Double>getAs("length_km") * 1000d,
                spheroidRow.<Double>getAs("legacy_metres"),
                0.001d
        );

        for (SpatialMeasurement invalid : List.of(
                new SpatialMeasurement.Area(
                        "shape", SpatialMeasureMode.PLANAR, "area_m2",
                        SpatialAreaUnit.SQUARE_METERS),
                new SpatialMeasurement.Length(
                        "shape", SpatialMeasureMode.SPHEROID, "length_source",
                        SpatialDistanceUnit.SOURCE_CRS_UNIT))) {
            SparkCanvasTable source = invalid instanceof SpatialMeasurement.Area ? polygon : line;
            String sourceName = invalid instanceof SpatialMeasurement.Area
                    ? "polygon_4326" : "line_4326";
            RecordingIssueSink invalidIssues = new RecordingIssueSink();
            new SpatialMeasureNodeOperator().apply(
                    new SpatialMeasureNodeDefinition(
                            UUID.randomUUID().toString(),
                            "非法单位组合",
                            LAYOUT,
                            new SpatialMeasureConfiguration(
                                    sourceName, "invalid_measure", List.of(invalid))
                    ),
                    Map.of(sourceName, source),
                    context(invalidIssues)
            );
            assertTrue(invalidIssues.codes.contains("SPATIAL_MEASURE_OUTPUT_UNIT_UNSUPPORTED"));
        }
    }

    @Test
    void serializesGeometryAsWktWkbAndGeoJson() {
        SparkCanvasTable source = table(
                new CanvasTableSchema(
                        "raw",
                        null,
                        List.of(stringColumn("wkt", false)),
                        CanvasDatasetKind.BOUNDED,
                        null,
                        null
                ),
                List.of(RowFactory.create("POINT (1 2)"))
        );
        CanvasNodeOperationResult constructed = new GeometryConstructNodeOperator().apply(
                new GeometryConstructNodeDefinition(
                        UUID.randomUUID().toString(),
                        "Geometry 构造",
                        LAYOUT,
                        new GeometryConstructConfiguration(
                                "raw", "geometry_table", "shape",
                                new GeometryConstructSource.Wkt("wkt"),
                                geometryType(GeometryKind.POINT, 4326)
                        )
                ),
                Map.of("raw", source),
                context(new RecordingIssueSink())
        );
        SparkCanvasTable geometryTable = constructed.propagatedTables().get("geometry_table");

        for (GeometrySerializationFormat format : GeometrySerializationFormat.values()) {
            RecordingIssueSink issues = new RecordingIssueSink();
            String outputTableName = "serialized_" + format.name().toLowerCase();
            CanvasNodeOperationResult result = new GeometrySerializeNodeOperator().apply(
                    new GeometrySerializeNodeDefinition(
                            UUID.randomUUID().toString(),
                            "Geometry 序列化",
                            LAYOUT,
                            new GeometrySerializeConfiguration(
                                    "geometry_table",
                                    outputTableName,
                                    "shape",
                                    "serialized_value",
                                    format
                            )
                    ),
                    Map.of("geometry_table", geometryTable),
                    context(issues)
            );
            SparkCanvasTable serialized = result.propagatedTables().get(outputTableName);
            Object value = serialized.dataset().head().getAs("serialized_value");

            assertFalse(issues.hasErrors(), () -> issues.codes.toString());
            if (format == GeometrySerializationFormat.WKB) {
                assertTrue(value instanceof byte[]);
                assertEquals(PlatformDataType.BINARY,
                        serialized.schema().columns().getLast().fieldType());
            } else {
                assertTrue(value instanceof String);
                assertEquals(PlatformDataType.STRING,
                        serialized.schema().columns().getLast().fieldType());
                assertTrue(((String) value).toUpperCase().contains("POINT"));
            }
        }
    }

    @Test
    void failsMalformedGeometryAndDeclaredKindMismatchAtRuntime() {
        SparkCanvasTable source = table(
                new CanvasTableSchema(
                        "raw",
                        null,
                        List.of(
                                stringColumn("malformed", false),
                                stringColumn("point", false)
                        ),
                        CanvasDatasetKind.BOUNDED,
                        null,
                        null
                ),
                List.of(RowFactory.create("NOT_A_GEOMETRY", "POINT (1 2)"))
        );

        CanvasNodeOperationResult malformed = new GeometryConstructNodeOperator().apply(
                new GeometryConstructNodeDefinition(
                        UUID.randomUUID().toString(),
                        "Geometry 构造",
                        LAYOUT,
                        new GeometryConstructConfiguration(
                                "raw", "malformed_output", "shape",
                                new GeometryConstructSource.Wkt("malformed"),
                                geometryType(GeometryKind.POINT, 4326)
                        )
                ),
                Map.of("raw", source),
                context(new RecordingIssueSink())
        );
        CanvasNodeOperationResult kindMismatch = new GeometryConstructNodeOperator().apply(
                new GeometryConstructNodeDefinition(
                        UUID.randomUUID().toString(),
                        "Geometry 构造",
                        LAYOUT,
                        new GeometryConstructConfiguration(
                                "raw", "kind_output", "shape",
                                new GeometryConstructSource.Wkt("point"),
                                geometryType(GeometryKind.POLYGON, 4326)
                        )
                ),
                Map.of("raw", source),
                context(new RecordingIssueSink())
        );

        assertThrows(
                Exception.class,
                () -> malformed.propagatedTables().get("malformed_output").dataset().head()
        );
        Exception mismatch = assertThrows(
                Exception.class,
                () -> kindMismatch.propagatedTables().get("kind_output").dataset().head()
        );
        assertTrue(exceptionMessages(mismatch).contains("GEOMETRY_CONSTRUCT_KIND_MISMATCH"));
    }

    @Test
    void propagatesNullAcrossSpatialFoundationProcessors() {
        SparkCanvasTable source = table(
                new CanvasTableSchema(
                        "raw",
                        null,
                        List.of(stringColumn("wkt", true)),
                        CanvasDatasetKind.BOUNDED,
                        null,
                        null
                ),
                java.util.Collections.singletonList(RowFactory.create((Object) null))
        );
        CanvasNodeOperationResult constructed = new GeometryConstructNodeOperator().apply(
                new GeometryConstructNodeDefinition(
                        UUID.randomUUID().toString(),
                        "Geometry 构造",
                        LAYOUT,
                        new GeometryConstructConfiguration(
                                "raw",
                                "geometry_table",
                                "shape",
                                new GeometryConstructSource.Wkt("wkt"),
                                geometryType(GeometryKind.POINT, 4326)
                        )
                ),
                Map.of("raw", source),
                context(new RecordingIssueSink())
        );
        SparkCanvasTable geometryTable = constructed.propagatedTables().get("geometry_table");
        assertTrue(geometryTable.dataset().head().isNullAt(1));

        CanvasNodeOperationResult validated = new GeometryValidateNodeOperator().apply(
                new GeometryValidateNodeDefinition(
                        UUID.randomUUID().toString(),
                        "Geometry 校验",
                        LAYOUT,
                        new GeometryValidateConfiguration(
                                "geometry_table", "validated", "shape", "is_valid", "reason")
                ),
                constructed.propagatedTables(),
                context(new RecordingIssueSink())
        );
        Row validatedRow = validated.propagatedTables().get("validated").dataset().head();
        assertTrue(validatedRow.isNullAt(validatedRow.fieldIndex("is_valid")));
        assertTrue(validatedRow.isNullAt(validatedRow.fieldIndex("reason")));

        CanvasNodeOperationResult measured = new SpatialMeasureNodeOperator().apply(
                new SpatialMeasureNodeDefinition(
                        UUID.randomUUID().toString(),
                        "空间测量",
                        LAYOUT,
                        new SpatialMeasureConfiguration(
                                "geometry_table",
                                "measured",
                                List.of(new SpatialMeasurement.X("shape", "x"))
                        )
                ),
                constructed.propagatedTables(),
                context(new RecordingIssueSink())
        );
        Row measuredRow = measured.propagatedTables().get("measured").dataset().head();
        assertTrue(measuredRow.isNullAt(measuredRow.fieldIndex("x")));

        CanvasNodeOperationResult serialized = new GeometrySerializeNodeOperator().apply(
                new GeometrySerializeNodeDefinition(
                        UUID.randomUUID().toString(),
                        "Geometry 序列化",
                        LAYOUT,
                        new GeometrySerializeConfiguration(
                                "geometry_table", "serialized", "shape", "shape_wkt",
                                GeometrySerializationFormat.WKT)
                ),
                constructed.propagatedTables(),
                context(new RecordingIssueSink())
        );
        Row serializedRow = serialized.propagatedTables().get("serialized").dataset().head();
        assertTrue(serializedRow.isNullAt(serializedRow.fieldIndex("shape_wkt")));
    }

    @Test
    void diagnosesInvalidGeometryWithoutDroppingOrFailingTheRow() {
        SparkCanvasTable source = table(
                new CanvasTableSchema(
                        "raw",
                        null,
                        List.of(stringColumn("wkt", false)),
                        CanvasDatasetKind.BOUNDED,
                        null,
                        null
                ),
                List.of(RowFactory.create(
                        "POLYGON ((0 0, 2 2, 0 2, 2 0, 0 0))"
                ))
        );
        CanvasNodeOperationResult constructed = new GeometryConstructNodeOperator().apply(
                new GeometryConstructNodeDefinition(
                        UUID.randomUUID().toString(),
                        "Geometry 构造",
                        LAYOUT,
                        new GeometryConstructConfiguration(
                                "raw",
                                "geometry_table",
                                "shape",
                                new GeometryConstructSource.Wkt("wkt"),
                                geometryType(GeometryKind.POLYGON, 4326)
                        )
                ),
                Map.of("raw", source),
                context(new RecordingIssueSink())
        );
        RecordingIssueSink issues = new RecordingIssueSink();
        CanvasNodeOperationResult validated = new GeometryValidateNodeOperator().apply(
                new GeometryValidateNodeDefinition(
                        UUID.randomUUID().toString(),
                        "Geometry 校验",
                        LAYOUT,
                        new GeometryValidateConfiguration(
                                "geometry_table", "validated", "shape", "is_valid", "reason")
                ),
                constructed.propagatedTables(),
                context(issues)
        );

        List<Row> rows = validated.propagatedTables().get("validated").dataset().collectAsList();

        assertFalse(issues.hasErrors(), () -> issues.codes.toString());
        assertEquals(1, rows.size());
        assertFalse(rows.getFirst().getBoolean(rows.getFirst().fieldIndex("is_valid")));
        assertNotNull(rows.getFirst().getString(rows.getFirst().fieldIndex("reason")));
    }

    @Test
    void repairsBuffersAndExplodesGeometryWithStableSchemas() {
        SparkCanvasTable invalidPolygon = constructWktTable(
                "POLYGON ((0 0, 2 2, 0 2, 2 0, 0 0))",
                GeometryKind.POLYGON,
                "invalid_polygon"
        );
        RecordingIssueSink repairIssues = new RecordingIssueSink();
        CanvasNodeOperationResult repaired = new GeometryRepairNodeOperator().apply(
                new GeometryRepairNodeDefinition(
                        UUID.randomUUID().toString(),
                        "Geometry 修复",
                        LAYOUT,
                        new GeometryRepairConfiguration(
                                "invalid_polygon", "repaired", "shape", "repaired_shape")
                ),
                Map.of("invalid_polygon", invalidPolygon),
                context(repairIssues)
        );
        SparkCanvasTable repairedTable = repaired.propagatedTables().get("repaired");
        Geometry repairedGeometry = repairedTable.dataset().head().getAs("repaired_shape");

        assertFalse(repairIssues.hasErrors(), () -> repairIssues.codes.toString());
        assertTrue(repairedGeometry.isValid());
        assertEquals(4326, repairedGeometry.getSRID());
        assertEquals(GeometryKind.GEOMETRY,
                repairedTable.schema().columns().getLast().geometry().kind());
        assertNull(repairedTable.schema().origin());

        SparkCanvasTable point = constructWktTable(
                "POINT (0 0)", GeometryKind.POINT, "point_table");
        for (SpatialMeasureMode mode : SpatialMeasureMode.values()) {
            RecordingIssueSink bufferIssues = new RecordingIssueSink();
            String outputName = "buffered_" + mode.name().toLowerCase();
            CanvasNodeOperationResult buffered = new GeometryBufferNodeOperator().apply(
                    new GeometryBufferNodeDefinition(
                            UUID.randomUUID().toString(),
                            "Geometry Buffer",
                            LAYOUT,
                            new GeometryBufferConfiguration(
                                    "point_table",
                                    outputName,
                                    "shape",
                                    "buffer_shape",
                                    mode == SpatialMeasureMode.SPHEROID ? 1000d : 1d,
                                    mode
                            )
                    ),
                    Map.of("point_table", point),
                    context(bufferIssues)
            );
            SparkCanvasTable bufferedTable = buffered.propagatedTables().get(outputName);
            Geometry bufferGeometry = bufferedTable.dataset().head().getAs("buffer_shape");

            assertFalse(bufferIssues.hasErrors(), () -> bufferIssues.codes.toString());
            assertEquals("MultiPolygon", bufferGeometry.getGeometryType());
            assertFalse(bufferGeometry.isEmpty());
            assertEquals(4326, bufferGeometry.getSRID());
            assertEquals(GeometryKind.MULTIPOLYGON,
                    bufferedTable.schema().columns().getLast().geometry().kind());
            if (mode == SpatialMeasureMode.PLANAR) {
                assertTrue(bufferIssues.codes.contains("PLANAR_BUFFER_USES_ANGULAR_UNITS"));
            }
        }

        RecordingIssueSink explicitUnitIssues = new RecordingIssueSink();
        CanvasNodeOperationResult kilometreBuffer = new GeometryBufferNodeOperator().apply(
                new GeometryBufferNodeDefinition(
                        UUID.randomUUID().toString(),
                        "Geometry Buffer with unit",
                        LAYOUT,
                        new GeometryBufferConfiguration(
                                "point_table", "buffered_kilometre", "shape", "buffer_shape",
                                1d, SpatialMeasureMode.SPHEROID, SpatialDistanceUnit.KILOMETERS)
                ),
                Map.of("point_table", point),
                context(explicitUnitIssues)
        );
        Geometry kilometreGeometry = kilometreBuffer.propagatedTables().get("buffered_kilometre")
                .dataset().head().getAs("buffer_shape");
        assertFalse(explicitUnitIssues.hasErrors(), () -> explicitUnitIssues.codes.toString());
        assertTrue(kilometreGeometry.getEnvelopeInternal().getWidth() > 0.01d);
        assertTrue(kilometreGeometry.getEnvelopeInternal().getWidth() < 0.03d);

        for (var invalid : List.of(
                new GeometryBufferConfiguration("point_table", "invalid_planar", "shape", "buffer_shape",
                        1d, SpatialMeasureMode.PLANAR, SpatialDistanceUnit.METERS),
                new GeometryBufferConfiguration("point_table", "invalid_spheroid", "shape", "buffer_shape",
                        1d, SpatialMeasureMode.SPHEROID, SpatialDistanceUnit.SOURCE_CRS_UNIT))) {
            RecordingIssueSink invalidUnitIssues = new RecordingIssueSink();
            new GeometryBufferNodeOperator().apply(
                    new GeometryBufferNodeDefinition(UUID.randomUUID().toString(), "Invalid unit", LAYOUT, invalid),
                    Map.of("point_table", point), context(invalidUnitIssues));
            assertTrue(invalidUnitIssues.codes.contains("SPATIAL_DISTANCE_UNIT_UNSUPPORTED"));
        }

        SparkCanvasTable multiPolygon = constructWktTable(
                "MULTIPOLYGON (((0 0, 0 1, 1 1, 1 0, 0 0)), "
                        + "((2 2, 2 3, 3 3, 3 2, 2 2)))",
                GeometryKind.MULTIPOLYGON,
                "multi_polygon"
        );
        RecordingIssueSink explodeIssues = new RecordingIssueSink();
        CanvasNodeOperationResult exploded = new GeometryExplodeNodeOperator().apply(
                new GeometryExplodeNodeDefinition(
                        UUID.randomUUID().toString(),
                        "Geometry 拆分",
                        LAYOUT,
                        new GeometryExplodeConfiguration(
                                "multi_polygon", "parts", "shape", "part", "part_index")
                ),
                Map.of("multi_polygon", multiPolygon),
                context(explodeIssues)
        );
        SparkCanvasTable parts = exploded.propagatedTables().get("parts");
        List<Row> partRows = parts.dataset().orderBy("part_index").collectAsList();

        assertFalse(explodeIssues.hasErrors(), () -> explodeIssues.codes.toString());
        assertEquals(2, partRows.size());
        assertEquals(List.of(0, 1), partRows.stream().map(row -> row.getAs("part_index")).toList());
        assertTrue(partRows.stream().map(row -> (Geometry) row.getAs("part"))
                .allMatch(geometry -> "Polygon".equals(geometry.getGeometryType())
                        && geometry.getSRID() == 4326));
        assertEquals(GeometryKind.POLYGON,
                parts.schema().columns().get(parts.schema().columns().size() - 2).geometry().kind());
        assertEquals(PlatformDataType.INTEGER, parts.schema().columns().getLast().fieldType());
        assertTrue(parts.schema().columns().getLast().nullable());
        assertNull(parts.schema().origin());
    }

    @Test
    void clipsSourceGeometryWithPolygonMasksAndKeepsOnlyNonEmptyMatches() {
        SparkCanvasTable sourceRaw = table(
                new CanvasTableSchema(
                        "source_raw",
                        null,
                        List.of(scalar("feature_id"), stringColumn("wkt", true)),
                        CanvasDatasetKind.BOUNDED,
                        null,
                        null
                ),
                List.of(
                        RowFactory.create(1L, "LINESTRING (-1 1, 3 1)"),
                        RowFactory.create(2L, "LINESTRING (10 10, 11 11)"),
                        RowFactory.create(3L, null),
                        RowFactory.create(4L, "LINESTRING EMPTY")
                )
        );
        SparkCanvasTable maskRaw = table(
                new CanvasTableSchema(
                        "mask_raw",
                        null,
                        List.of(scalar("mask_id"), stringColumn("wkt", false)),
                        CanvasDatasetKind.BOUNDED,
                        null,
                        null
                ),
                List.of(
                        RowFactory.create(10L, "POLYGON ((0 0, 0 2, 2 2, 2 0, 0 0))"),
                        RowFactory.create(11L, "POLYGON ((1 0, 1 2, 3 2, 3 0, 1 0))")
                )
        );
        CanvasNodeOperationResult sourceConstructed = new GeometryConstructNodeOperator().apply(
                new GeometryConstructNodeDefinition(
                        UUID.randomUUID().toString(),
                        "来源 Geometry",
                        LAYOUT,
                        new GeometryConstructConfiguration(
                                "source_raw", "source", "shape",
                                new GeometryConstructSource.Wkt("wkt"),
                                geometryType(GeometryKind.LINESTRING, 4326)
                        )
                ),
                Map.of("source_raw", sourceRaw),
                context(new RecordingIssueSink())
        );
        CanvasNodeOperationResult maskConstructed = new GeometryConstructNodeOperator().apply(
                new GeometryConstructNodeDefinition(
                        UUID.randomUUID().toString(),
                        "Mask Geometry",
                        LAYOUT,
                        new GeometryConstructConfiguration(
                                "mask_raw", "mask", "boundary",
                                new GeometryConstructSource.Wkt("wkt"),
                                geometryType(GeometryKind.POLYGON, 4326)
                        )
                ),
                Map.of("mask_raw", maskRaw),
                context(new RecordingIssueSink())
        );
        Map<String, SparkCanvasTable> inputs = new LinkedHashMap<>();
        inputs.put("source", sourceConstructed.propagatedTables().get("source"));
        inputs.put("mask", maskConstructed.propagatedTables().get("mask"));
        RecordingIssueSink issues = new RecordingIssueSink();

        CanvasNodeOperationResult clipped = new SpatialClipNodeOperator().apply(
                new SpatialClipNodeDefinition(
                        UUID.randomUUID().toString(),
                        "空间裁剪",
                        LAYOUT,
                        new SpatialClipConfiguration(
                                "source", "mask", "clipped",
                                "shape", "boundary", "clipped_shape")
                ),
                inputs,
                context(issues)
        );

        SparkCanvasTable clippedTable = clipped.propagatedTables().get("clipped");
        List<Row> rows = clippedTable.dataset().collectAsList();
        assertFalse(issues.hasErrors(), () -> issues.codes.toString());
        assertEquals(2, rows.size());
        assertTrue(rows.stream().allMatch(row -> row.getLong(row.fieldIndex("feature_id")) == 1L));
        assertTrue(rows.stream().map(row -> (Geometry) row.getAs("clipped_shape"))
                .allMatch(geometry -> !geometry.isEmpty() && geometry.getSRID() == 4326));
        assertEquals(
                List.of("feature_id", "wkt", "shape", "clipped_shape"),
                clippedTable.schema().columns().stream().map(CanvasColumnSchema::name).toList()
        );
        assertEquals(GeometryKind.GEOMETRY,
                clippedTable.schema().columns().getLast().geometry().kind());
        assertFalse(clippedTable.schema().columns().getLast().nullable());
        assertNull(clippedTable.schema().origin());
    }

    @Test
    void clipSourceFamilyFiltersBoundaryContactsAndProducesStableMultiKinds() {
        SparkCanvasTable mask = constructWktTable(
                "POLYGON ((0 0, 0 2, 2 2, 2 0, 0 0))",
                GeometryKind.POLYGON,
                "mask");

        SparkCanvasTable touchingLine = constructWktTable(
                "LINESTRING (-1 0, 0 0)", GeometryKind.LINESTRING, "source");
        RecordingIssueSink familyTouchIssues = new RecordingIssueSink();
        SparkCanvasTable familyTouch = clip(
                touchingLine, mask, SpatialClipGeometryPolicy.SOURCE_FAMILY_2D,
                familyTouchIssues).propagatedTables().get("clipped");
        assertFalse(familyTouchIssues.hasErrors(), () -> familyTouchIssues.codes.toString());
        assertEquals(0, familyTouch.dataset().count());
        assertEquals(GeometryKind.MULTILINESTRING,
                familyTouch.schema().columns().getLast().geometry().kind());
        assertEquals(CoordinateDimension.XY,
                familyTouch.schema().columns().getLast().geometry().dimension());

        RecordingIssueSink legacyTouchIssues = new RecordingIssueSink();
        SparkCanvasTable legacyTouch = clip(
                touchingLine, mask, null, legacyTouchIssues)
                .propagatedTables().get("clipped");
        List<Row> legacyRows = legacyTouch.dataset().collectAsList();
        assertFalse(legacyTouchIssues.hasErrors(), () -> legacyTouchIssues.codes.toString());
        assertEquals(1, legacyRows.size());
        assertEquals("Point", ((Geometry) legacyRows.getFirst().getAs("clipped_shape"))
                .getGeometryType());
        assertEquals(GeometryKind.GEOMETRY,
                legacyTouch.schema().columns().getLast().geometry().kind());

        for (var example : List.of(
                new ClipFamilyExample(
                        GeometryKind.POINT,
                        "POINT (1 1)",
                        GeometryKind.MULTIPOINT,
                        "MultiPoint"),
                new ClipFamilyExample(
                        GeometryKind.LINESTRING,
                        "LINESTRING (-1 1, 3 1)",
                        GeometryKind.MULTILINESTRING,
                        "MultiLineString"),
                new ClipFamilyExample(
                        GeometryKind.POLYGON,
                        "POLYGON ((-1 -1, -1 1, 1 1, 1 -1, -1 -1))",
                        GeometryKind.MULTIPOLYGON,
                        "MultiPolygon")
        )) {
            SparkCanvasTable source = constructWktTable(
                    example.wkt(), example.sourceKind(), "source");
            RecordingIssueSink issues = new RecordingIssueSink();
            SparkCanvasTable clipped = clip(
                    source, mask, SpatialClipGeometryPolicy.SOURCE_FAMILY_2D, issues)
                    .propagatedTables().get("clipped");
            Geometry geometry = clipped.dataset().head().getAs("clipped_shape");
            assertFalse(issues.hasErrors(), () -> issues.codes.toString());
            assertEquals(example.outputKind(), clipped.schema().columns().getLast().geometry().kind());
            assertEquals(example.geometryType(), geometry.getGeometryType());
            assertEquals(4326, geometry.getSRID());
        }

        SparkCanvasTable concreteLine = constructWktTable(
                "LINESTRING (-1 1, 3 1)", GeometryKind.LINESTRING, "source");
        SparkCanvasTable generic = new SparkCanvasTable(
                new CanvasTableSchema(
                        "source",
                        null,
                        List.of(
                                stringColumn("wkt", false),
                                geometry("shape", GeometryKind.GEOMETRY, 4326)),
                        CanvasDatasetKind.BOUNDED,
                        null,
                        null),
                concreteLine.dataset());
        RecordingIssueSink genericIssues = new RecordingIssueSink();
        new SpatialClipNodeOperator().apply(
                new SpatialClipNodeDefinition(
                        UUID.randomUUID().toString(),
                        "空间裁剪",
                        LAYOUT,
                        new SpatialClipConfiguration(
                                "source", "mask", "clipped",
                                "shape", "shape", "clipped_shape",
                                SpatialClipGeometryPolicy.SOURCE_FAMILY_2D)
                ),
                Map.of("source", generic, "mask", mask),
                context(genericIssues)
        );
        assertTrue(genericIssues.codes.contains("SPATIAL_CLIP_SOURCE_KIND_UNSUPPORTED"));
    }

    @Test
    void clipDissolvesOverlappingMasksPerSourceWithoutCollapsingIdenticalRows() {
        SparkCanvasTable source = constructWktRows(
                "clip_dissolve_source_raw", "source", "feature_id",
                GeometryKind.POLYGON,
                List.of(
                        RowFactory.create(1L,
                                "POLYGON ((0 0, 0 2, 3 2, 3 0, 0 0))"),
                        RowFactory.create(1L,
                                "POLYGON ((0 0, 0 2, 3 2, 3 0, 0 0))")));
        SparkCanvasTable mask = constructWktRows(
                "clip_dissolve_mask_raw", "mask", "mask_id",
                GeometryKind.POLYGON,
                List.of(
                        RowFactory.create(10L,
                                "POLYGON ((0 0, 0 2, 2 2, 2 0, 0 0))"),
                        RowFactory.create(11L,
                                "POLYGON ((1 0, 1 2, 3 2, 3 0, 1 0))")));
        RecordingIssueSink issues = new RecordingIssueSink();

        SparkCanvasTable output = clip(
                source, mask, SpatialClipGeometryPolicy.SOURCE_FAMILY_2D,
                SpatialClipMaskCombination.DISSOLVE_ALL, issues)
                .propagatedTables().get("clipped");
        List<Row> rows = output.dataset().collectAsList();

        assertFalse(issues.hasErrors(), () -> issues.codes.toString());
        assertEquals(2, rows.size(),
                "相同内容的两条来源记录必须依靠内部行标识分别保留");
        assertTrue(rows.stream().allMatch(row ->
                ((Geometry) row.getAs("clipped_shape")).getGeometryType()
                        .equals("MultiPolygon")));
        assertTrue(rows.stream().allMatch(row -> Math.abs(
                ((Geometry) row.getAs("clipped_shape")).getArea() - 6d) < 0.000001d),
                "重叠 Mask 应先融合，重叠区域不能被重复计算");
    }

    @Test
    void clipDissolvesSeparatedMasksIntoOneMultiGeometry() {
        SparkCanvasTable source = constructWktRows(
                "clip_multi_source_raw", "source", "feature_id",
                GeometryKind.POLYGON,
                List.of(RowFactory.create(1L,
                        "POLYGON ((0 0, 0 1, 5 1, 5 0, 0 0))")));
        SparkCanvasTable mask = constructWktRows(
                "clip_multi_mask_raw", "mask", "mask_id",
                GeometryKind.POLYGON,
                List.of(
                        RowFactory.create(10L,
                                "POLYGON ((0 0, 0 1, 1 1, 1 0, 0 0))"),
                        RowFactory.create(11L,
                                "POLYGON ((3 0, 3 1, 4 1, 4 0, 3 0))")));
        RecordingIssueSink issues = new RecordingIssueSink();

        List<Row> rows = clip(
                source, mask, SpatialClipGeometryPolicy.SOURCE_FAMILY_2D,
                SpatialClipMaskCombination.DISSOLVE_ALL, issues)
                .propagatedTables().get("clipped").dataset().collectAsList();
        Geometry geometry = rows.getFirst().getAs("clipped_shape");

        assertFalse(issues.hasErrors(), () -> issues.codes.toString());
        assertEquals(1, rows.size());
        assertEquals("MultiPolygon", geometry.getGeometryType());
        assertEquals(2, geometry.getNumGeometries());
        assertEquals(2d, geometry.getArea(), 0.000001d);
        assertEquals(4326, geometry.getSRID());
    }

    @Test
    void clipDissolvePreviewIsLazyUsesSpatialJoinAndKeepsCompleteLineage() {
        SparkCanvasTable source = markLineageInput(constructWktRows(
                "clip_lineage_source_raw", "source", "feature_id",
                GeometryKind.POLYGON,
                List.of(RowFactory.create(1L,
                        "POLYGON ((0 0, 0 2, 3 2, 3 0, 0 0))"))), "source");
        SparkCanvasTable mask = markLineageInput(constructWktRows(
                "clip_lineage_mask_raw", "mask", "mask_id",
                GeometryKind.POLYGON,
                List.of(RowFactory.create(10L,
                        "POLYGON ((0 0, 0 2, 2 2, 2 0, 0 0))"))), "mask");
        String jobGroup = "clip-dissolve-preview-" + UUID.randomUUID();
        spark.sparkContext().setJobGroup(jobGroup, "clip dissolve preview", false);
        SparkCanvasTable output;
        try {
            RecordingIssueSink issues = new RecordingIssueSink();
            output = clip(
                    source, mask, SpatialClipGeometryPolicy.SOURCE_FAMILY_2D,
                    SpatialClipMaskCombination.DISSOLVE_ALL, issues)
                    .propagatedTables().get("clipped");
            assertFalse(issues.hasErrors(), () -> issues.codes.toString());
            output.dataset().queryExecution().analyzed();
            String plan = output.dataset().queryExecution().executedPlan().toString();
            assertTrue(plan.contains("RangeJoin") || plan.contains("BroadcastIndexJoin"), plan);
            assertFalse(plan.contains("CartesianProduct")
                    || plan.contains("BroadcastNestedLoopJoin"), plan);
            assertEquals(0,
                    spark.sparkContext().statusTracker().getJobIdsForGroup(jobGroup).length,
                    "Compiler/Preview 只能构造惰性计划，不能启动 Spark Job");
        } finally {
            spark.sparkContext().clearJobGroup();
        }

        assertEquals(
                List.of("feature_id", "wkt", "shape", "clipped_shape"),
                output.schema().columns().stream().map(CanvasColumnSchema::name).toList());
        TaskLineageEvidence.Flow flow = analyzeLineage(output);
        assertEquals(TaskLineageEvidence.Coverage.FIELD_COMPLETE, flow.coverage(),
                () -> flow.warnings().toString());
        assertTrue(flow.fields().stream().noneMatch(field ->
                        field.outputEffect()
                                == TaskLineageEvidence.OutputEffect.WRITTEN_UNKNOWN_SOURCE),
                () -> flow.fields().toString());
        assertTrue(flow.fieldEdges().stream().anyMatch(edge ->
                edge.target().localFieldKey().equals("out:clipped_shape")
                        && edge.source().localFieldKey().equals("source:shape")));
        assertTrue(flow.fieldEdges().stream().anyMatch(edge ->
                edge.target().localFieldKey().equals("out:clipped_shape")
                        && edge.source().localFieldKey().equals("mask:shape")));
    }

    @Test
    void aggregatesGeometryWithStableGroupingNullAndEmptySemantics() {
        SparkCanvasTable raw = table(
                new CanvasTableSchema(
                        "raw",
                        null,
                        List.of(stringColumn("district", false), stringColumn("wkt", true)),
                        CanvasDatasetKind.BOUNDED,
                        null,
                        null
                ),
                List.of(
                        RowFactory.create("A", "POLYGON ((0 0, 0 2, 2 2, 2 0, 0 0))"),
                        RowFactory.create("A", "POLYGON ((1 1, 1 3, 3 3, 3 1, 1 1))"),
                        RowFactory.create("A", null),
                        RowFactory.create("B", "POLYGON EMPTY"),
                        RowFactory.create("B", "POLYGON ((0 0, 0 1, 1 1, 1 0, 0 0))"),
                        RowFactory.create("C", null),
                        RowFactory.create("D", "POLYGON EMPTY")
                )
        );
        CanvasNodeOperationResult constructed = new GeometryConstructNodeOperator().apply(
                new GeometryConstructNodeDefinition(
                        UUID.randomUUID().toString(),
                        "Geometry 构造",
                        LAYOUT,
                        new GeometryConstructConfiguration(
                                "raw", "parcels", "shape",
                                new GeometryConstructSource.Wkt("wkt"),
                                geometryType(GeometryKind.POLYGON, 4326)
                        )
                ),
                Map.of("raw", raw),
                context(new RecordingIssueSink())
        );
        RecordingIssueSink issues = new RecordingIssueSink();
        List<SpatialAggregation> aggregations = List.of(
                new SpatialAggregation(SpatialAggregationKind.UNION, "shape", "union_shape"),
                new SpatialAggregation(
                        SpatialAggregationKind.INTERSECTION, "shape", "intersection_shape"),
                new SpatialAggregation(SpatialAggregationKind.COLLECT, "shape", "collect_shape"),
                new SpatialAggregation(SpatialAggregationKind.ENVELOPE, "shape", "envelope_shape")
        );

        CanvasNodeOperationResult aggregated = new SpatialAggregateNodeOperator().apply(
                new SpatialAggregateNodeDefinition(
                        UUID.randomUUID().toString(),
                        "空间聚合",
                        LAYOUT,
                        new SpatialAggregateConfiguration(
                                "parcels", "districts", List.of("district"), aggregations)
                ),
                constructed.propagatedTables(),
                context(issues)
        );

        SparkCanvasTable output = aggregated.propagatedTables().get("districts");
        Map<String, Row> rows = new LinkedHashMap<>();
        for (Row row : output.dataset().collectAsList()) {
            rows.put(row.getString(row.fieldIndex("district")), row);
        }
        assertFalse(issues.hasErrors(), () -> issues.codes.toString());
        assertEquals(4, rows.size());
        assertEquals(7d, ((Geometry) rows.get("A").getAs("union_shape")).getArea(), 0.000001d);
        assertEquals(1d,
                ((Geometry) rows.get("A").getAs("intersection_shape")).getArea(), 0.000001d);
        assertFalse(((Geometry) rows.get("A").getAs("collect_shape")).isEmpty());
        assertEquals(9d,
                ((Geometry) rows.get("A").getAs("envelope_shape")).getArea(), 0.000001d);
        assertTrue(((Geometry) rows.get("B").getAs("intersection_shape")).isEmpty());
        assertTrue(rows.get("C").isNullAt(rows.get("C").fieldIndex("union_shape")));
        assertTrue(rows.get("C").isNullAt(rows.get("C").fieldIndex("intersection_shape")));
        assertTrue(rows.get("C").isNullAt(rows.get("C").fieldIndex("collect_shape")));
        assertTrue(rows.get("C").isNullAt(rows.get("C").fieldIndex("envelope_shape")));
        assertTrue(((Geometry) rows.get("D").getAs("union_shape")).isEmpty());
        assertTrue(((Geometry) rows.get("D").getAs("intersection_shape")).isEmpty());
        assertTrue(rows.get("D").isNullAt(rows.get("D").fieldIndex("envelope_shape")));
        for (Row row : rows.values()) {
            for (String name : List.of(
                    "union_shape", "intersection_shape", "collect_shape", "envelope_shape")) {
                if (!row.isNullAt(row.fieldIndex(name))) {
                    assertEquals(4326, ((Geometry) row.getAs(name)).getSRID());
                }
            }
        }
        assertEquals(
                List.of(
                        "district", "union_shape", "intersection_shape",
                        "collect_shape", "envelope_shape"),
                output.schema().columns().stream().map(CanvasColumnSchema::name).toList()
        );
        assertTrue(output.schema().columns().subList(1, 5).stream().allMatch(column ->
                column.fieldType() == PlatformDataType.GEOMETRY
                        && column.geometry().kind() == GeometryKind.GEOMETRY
                        && column.nullable()));
        assertEquals(CanvasDatasetKind.BOUNDED, output.schema().datasetKind());
        assertNull(output.schema().eventTimeColumn());
        assertNull(output.schema().watermarkDelay());
    }

    @Test
    void dissolvesAllAsMultipartWithCountAndAllSummaryStatisticsWithoutStartingAJob()
            throws Exception {
        SparkCanvasTable raw = table(
                new CanvasTableSchema(
                        "raw_dissolve",
                        null,
                        List.of(
                                stringColumn("wkt", true),
                                typedColumn("amount", PlatformDataType.LONG, true),
                                typedColumn("score", PlatformDataType.DOUBLE, true),
                                stringColumn("label", true)
                        ),
                        CanvasDatasetKind.BOUNDED,
                        null,
                        null
                ),
                List.of(
                        RowFactory.create(
                                "POLYGON ((0 0, 0 1, 1 1, 1 0, 0 0))", 1L, 1d, "stable"),
                        RowFactory.create(
                                "POLYGON ((2 0, 2 1, 3 1, 3 0, 2 0))", 2L, 2d, "stable"),
                        RowFactory.create(null, null, 3d, "stable")
                )
        );
        SparkCanvasTable parcels = new GeometryConstructNodeOperator().apply(
                new GeometryConstructNodeDefinition(
                        UUID.randomUUID().toString(),
                        "Geometry 构造",
                        LAYOUT,
                        new GeometryConstructConfiguration(
                                "raw_dissolve", "dissolve_source", "shape",
                                new GeometryConstructSource.Wkt("wkt"),
                                geometryType(GeometryKind.POLYGON, 4326)
                        )
                ),
                Map.of("raw_dissolve", raw),
                context(new RecordingIssueSink())
        ).propagatedTables().get("dissolve_source");
        List<SpatialAggregateStatistic> statistics = List.of(
                statistic(SpatialAggregateStatisticKind.COUNT_FIELD, "amount", "amount_count"),
                statistic(SpatialAggregateStatisticKind.SUM, "amount", "amount_sum"),
                statistic(SpatialAggregateStatisticKind.MEAN, "amount", "amount_mean"),
                statistic(SpatialAggregateStatisticKind.MIN, "amount", "amount_min"),
                statistic(SpatialAggregateStatisticKind.MAX, "amount", "amount_max"),
                statistic(SpatialAggregateStatisticKind.RANGE, "amount", "amount_range"),
                statistic(SpatialAggregateStatisticKind.STDDEV, "score", "score_stddev"),
                statistic(SpatialAggregateStatisticKind.VARIANCE, "score", "score_variance"),
                statistic(SpatialAggregateStatisticKind.ANY, "label", "any_label")
        );
        RecordingIssueSink issues = new RecordingIssueSink();
        spark.sparkContext().listenerBus().waitUntilEmpty(10_000);
        AtomicInteger jobsStarted = new AtomicInteger();
        spark.sparkContext().addSparkListener(new SparkListener() {
            @Override
            public void onJobStart(SparkListenerJobStart jobStart) {
                jobsStarted.incrementAndGet();
            }
        });

        CanvasNodeOperationResult result = new SpatialAggregateNodeOperator().apply(
                new SpatialAggregateNodeDefinition(
                        UUID.randomUUID().toString(),
                        "Dissolve All",
                        LAYOUT,
                        new SpatialAggregateConfiguration(
                                "dissolve_source",
                                "dissolved",
                                List.of(),
                                List.of(new SpatialAggregation(
                                        SpatialAggregationKind.UNION,
                                        "shape",
                                        "dissolved_shape")),
                                new SpatialAggregateDissolveOptions(
                                        true, true, "feature_count", statistics)
                        )
                ),
                Map.of("dissolve_source", parcels),
                context(issues)
        );
        spark.sparkContext().listenerBus().waitUntilEmpty(10_000);

        assertFalse(issues.hasErrors(), () -> issues.codes.toString());
        assertEquals(0, jobsStarted.get(), "Dissolve analysis must not start a Spark job");
        SparkCanvasTable dissolved = result.propagatedTables().get("dissolved");
        Row row = dissolved.dataset().head();
        Geometry geometry = row.getAs("dissolved_shape");
        assertEquals("MultiPolygon", geometry.getGeometryType());
        assertEquals(4326, geometry.getSRID());
        assertEquals(2d, geometry.getArea(), 0.000001d);
        assertEquals(3L, (Long) row.getAs("feature_count"));
        assertEquals(2L, (Long) row.getAs("amount_count"));
        assertEquals(3L, (Long) row.getAs("amount_sum"));
        assertEquals(1.5d, (Double) row.getAs("amount_mean"), 0.000001d);
        assertEquals(1L, (Long) row.getAs("amount_min"));
        assertEquals(2L, (Long) row.getAs("amount_max"));
        assertEquals(1L, (Long) row.getAs("amount_range"));
        assertEquals(1d, (Double) row.getAs("score_stddev"), 0.000001d);
        assertEquals(1d, (Double) row.getAs("score_variance"), 0.000001d);
        assertEquals("stable", row.getAs("any_label"));
        assertEquals(
                List.of(
                        "dissolved_shape", "feature_count", "amount_count", "amount_sum",
                        "amount_mean", "amount_min", "amount_max", "amount_range",
                        "score_stddev", "score_variance", "any_label"),
                dissolved.schema().columns().stream().map(CanvasColumnSchema::name).toList()
        );
        assertEquals(PlatformDataType.LONG, dissolved.schema().columns().get(1).fieldType());
        assertEquals(PlatformDataType.DOUBLE, dissolved.schema().columns().get(4).fieldType());
    }

    @Test
    void dissolvesListIntoSinglePartsAndOmitsGroupsWithoutResultGeometry() {
        SparkCanvasTable raw = table(
                new CanvasTableSchema(
                        "raw_dissolve_list",
                        null,
                        List.of(
                                stringColumn("district", false),
                                stringColumn("wkt", true),
                                typedColumn("amount", PlatformDataType.LONG, false)
                        ),
                        CanvasDatasetKind.BOUNDED,
                        null,
                        null
                ),
                List.of(
                        RowFactory.create(
                                "A", "POLYGON ((0 0, 0 1, 1 1, 1 0, 0 0))", 1L),
                        RowFactory.create(
                                "A", "POLYGON ((2 0, 2 1, 3 1, 3 0, 2 0))", 2L),
                        RowFactory.create("A", null, 3L),
                        RowFactory.create("B", "POLYGON EMPTY", 4L),
                        RowFactory.create("C", null, 5L)
                )
        );
        SparkCanvasTable parcels = new GeometryConstructNodeOperator().apply(
                new GeometryConstructNodeDefinition(
                        UUID.randomUUID().toString(),
                        "Geometry 构造",
                        LAYOUT,
                        new GeometryConstructConfiguration(
                                "raw_dissolve_list", "dissolve_list_source", "shape",
                                new GeometryConstructSource.Wkt("wkt"),
                                geometryType(GeometryKind.POLYGON, 4326)
                        )
                ),
                Map.of("raw_dissolve_list", raw),
                context(new RecordingIssueSink())
        ).propagatedTables().get("dissolve_list_source");
        RecordingIssueSink issues = new RecordingIssueSink();

        CanvasNodeOperationResult result = new SpatialAggregateNodeOperator().apply(
                new SpatialAggregateNodeDefinition(
                        UUID.randomUUID().toString(),
                        "Dissolve List",
                        LAYOUT,
                        new SpatialAggregateConfiguration(
                                "dissolve_list_source",
                                "district_parts",
                                List.of("district"),
                                List.of(new SpatialAggregation(
                                        SpatialAggregationKind.UNION,
                                        "shape",
                                        "district_shape")),
                                new SpatialAggregateDissolveOptions(
                                        true,
                                        false,
                                        "feature_count",
                                        List.of(statistic(
                                                SpatialAggregateStatisticKind.SUM,
                                                "amount",
                                                "amount_sum")))
                        )
                ),
                Map.of("dissolve_list_source", parcels),
                context(issues)
        );

        assertFalse(issues.hasErrors(), () -> issues.codes.toString());
        List<Row> rows = result.propagatedTables().get("district_parts")
                .dataset().collectAsList();
        assertEquals(2, rows.size());
        assertTrue(rows.stream().allMatch(row -> "A".equals(row.getAs("district"))));
        assertTrue(rows.stream().allMatch(row -> Long.valueOf(3L)
                .equals(row.getAs("feature_count"))));
        assertTrue(rows.stream().allMatch(row -> Long.valueOf(6L)
                .equals(row.getAs("amount_sum"))));
        for (Row row : rows) {
            Geometry part = row.getAs("district_shape");
            assertEquals("Polygon", part.getGeometryType());
            assertEquals(4326, part.getSRID());
            assertEquals(1d, part.getArea(), 0.000001d);
        }
    }

    @Test
    void dissolvesTransitiveSpatialConnectionsWithoutCollectingFeaturesOnDriver() {
        SparkCanvasTable raw = table(
                new CanvasTableSchema(
                        "raw_connected_dissolve",
                        null,
                        List.of(
                                stringColumn("wkt", true),
                                typedColumn("amount", PlatformDataType.LONG, false)
                        ),
                        CanvasDatasetKind.BOUNDED,
                        null,
                        null
                ),
                List.of(
                        RowFactory.create("POLYGON ((0 0, 0 2, 2 2, 2 0, 0 0))", 1L),
                        RowFactory.create("POLYGON ((2 0, 2 2, 4 2, 4 0, 2 0))", 2L),
                        RowFactory.create("POLYGON ((4 0, 4 2, 6 2, 6 0, 4 0))", 3L),
                        RowFactory.create("POLYGON ((10 0, 10 1, 11 1, 11 0, 10 0))", 4L),
                        RowFactory.create(null, 100L),
                        RowFactory.create("POLYGON EMPTY", 200L)
                )
        );
        SparkCanvasTable parcels = new GeometryConstructNodeOperator().apply(
                new GeometryConstructNodeDefinition(
                        UUID.randomUUID().toString(),
                        "Geometry 构造",
                        LAYOUT,
                        new GeometryConstructConfiguration(
                                "raw_connected_dissolve", "connected_source", "shape",
                                new GeometryConstructSource.Wkt("wkt"),
                                geometryType(GeometryKind.POLYGON, 4326)
                        )
                ),
                Map.of("raw_connected_dissolve", raw),
                context(new RecordingIssueSink())
        ).propagatedTables().get("connected_source");
        RecordingIssueSink issues = new RecordingIssueSink();

        CanvasNodeOperationResult result = new SpatialAggregateNodeOperator().apply(
                new SpatialAggregateNodeDefinition(
                        UUID.randomUUID().toString(),
                        "Connected Dissolve",
                        LAYOUT,
                        new SpatialAggregateConfiguration(
                                "connected_source",
                                "connected_result",
                                List.of(),
                                List.of(new SpatialAggregation(
                                        SpatialAggregationKind.UNION,
                                        "shape",
                                        "dissolved_shape")),
                                new SpatialAggregateDissolveOptions(
                                        true,
                                        true,
                                        "feature_count",
                                        List.of(statistic(
                                                SpatialAggregateStatisticKind.SUM,
                                                "amount",
                                                "amount_sum")),
                                        SpatialAggregateDissolveGroupingMode.CONNECTED_COMPONENTS)
                        )
                ),
                Map.of("connected_source", parcels),
                runtimeContext(issues)
        );

        assertFalse(issues.hasErrors(), () -> issues.codes.toString());
        List<Row> rows = result.propagatedTables().get("connected_result")
                .dataset()
                .orderBy("feature_count")
                .collectAsList();
        assertEquals(2, rows.size());
        assertEquals(1L, (Long) rows.get(0).getAs("feature_count"));
        assertEquals(4L, (Long) rows.get(0).getAs("amount_sum"));
        assertEquals(1d, ((Geometry) rows.get(0).getAs("dissolved_shape")).getArea(), 0.000001d);
        assertEquals(3L, (Long) rows.get(1).getAs("feature_count"));
        assertEquals(6L, (Long) rows.get(1).getAs("amount_sum"));
        Geometry connected = rows.get(1).getAs("dissolved_shape");
        assertEquals(12d, connected.getArea(), 0.000001d);
        assertEquals(4326, connected.getSRID());
    }

    @Test
    void validatesConnectedDissolveGroupingAndPolygonInputBeforeGraphExecution() {
        SparkCanvasTable lineSource = table(
                "line_source",
                List.of(
                        stringColumn("district", false),
                        geometry("shape", GeometryKind.LINESTRING, 4326)
                )
        );
        RecordingIssueSink issues = new RecordingIssueSink();

        new SpatialAggregateNodeOperator().apply(
                new SpatialAggregateNodeDefinition(
                        UUID.randomUUID().toString(),
                        "Invalid Connected Dissolve",
                        LAYOUT,
                        new SpatialAggregateConfiguration(
                                "line_source",
                                "connected_result",
                                List.of("district"),
                                List.of(new SpatialAggregation(
                                        SpatialAggregationKind.UNION,
                                        "shape",
                                        "dissolved_shape")),
                                new SpatialAggregateDissolveOptions(
                                        true,
                                        true,
                                        "feature_count",
                                        List.of(),
                                        SpatialAggregateDissolveGroupingMode.CONNECTED_COMPONENTS)
                        )
                ),
                Map.of("line_source", lineSource),
                runtimeContext(issues)
        );

        assertTrue(issues.codes.contains(
                "SPATIAL_DISSOLVE_CONNECTED_GROUP_FIELDS_NOT_ALLOWED"));
        assertTrue(issues.codes.contains(
                "SPATIAL_DISSOLVE_CONNECTED_REQUIRES_POLYGON"));
    }

    @Test
    void rejectsInvalidDissolveShapeStatisticsAndOutputNames() {
        SparkCanvasTable source = table(
                "source",
                List.of(
                        geometry("shape", GeometryKind.POLYGON, 4326),
                        typedColumn("amount", PlatformDataType.LONG, true),
                        stringColumn("label", true)
                )
        );
        String duplicateId = UUID.randomUUID().toString();
        List<SpatialAggregateStatistic> invalidStatistics = List.of(
                new SpatialAggregateStatistic(
                        "not-a-uuid", SpatialAggregateStatisticKind.COUNT_FIELD,
                        "missing", "COUNT_OUT"),
                new SpatialAggregateStatistic(
                        duplicateId, SpatialAggregateStatisticKind.ANY,
                        "amount", "duplicate"),
                new SpatialAggregateStatistic(
                        duplicateId, SpatialAggregateStatisticKind.SUM,
                        "label", "DUPLICATE")
        );
        RecordingIssueSink issues = new RecordingIssueSink();

        new SpatialAggregateNodeOperator().apply(
                new SpatialAggregateNodeDefinition(
                        UUID.randomUUID().toString(),
                        "Invalid Dissolve",
                        LAYOUT,
                        new SpatialAggregateConfiguration(
                                "source",
                                "invalid_dissolve",
                                List.of(),
                                List.of(
                                        new SpatialAggregation(
                                                SpatialAggregationKind.UNION,
                                                "shape",
                                                "geometry_one"),
                                        new SpatialAggregation(
                                                SpatialAggregationKind.UNION,
                                                "shape",
                                                "geometry_two")),
                                new SpatialAggregateDissolveOptions(
                                        true, false, "count_out", invalidStatistics)
                        )
                ),
                Map.of("source", source),
                context(issues)
        );

        for (String code : List.of(
                "SPATIAL_DISSOLVE_REQUIRES_SINGLE_UNION",
                "INVALID_SPATIAL_DISSOLVE_STATISTIC_ID",
                "DUPLICATE_SPATIAL_DISSOLVE_STATISTIC_ID",
                "COLUMN_NOT_FOUND",
                "STRING_COLUMN_REQUIRED",
                "NUMERIC_COLUMN_REQUIRED",
                "DUPLICATE_COLUMN_NAME")) {
            assertTrue(issues.codes.contains(code), () -> code + ": " + issues.codes);
        }

        List<SpatialAggregateStatistic> tooMany = new ArrayList<>();
        for (int index = 0;
             index <= SpatialAggregateDissolveOptions.MAX_SUMMARY_STATISTICS;
             index++) {
            tooMany.add(statistic(
                    SpatialAggregateStatisticKind.COUNT_FIELD,
                    "amount",
                    "count_" + index));
        }
        RecordingIssueSink limitIssues = new RecordingIssueSink();
        new SpatialAggregateNodeOperator().apply(
                new SpatialAggregateNodeDefinition(
                        UUID.randomUUID().toString(),
                        "Too Many Dissolve Statistics",
                        LAYOUT,
                        new SpatialAggregateConfiguration(
                                "source",
                                "too_many_statistics",
                                List.of(),
                                List.of(new SpatialAggregation(
                                        SpatialAggregationKind.UNION,
                                        "shape",
                                        "dissolved_shape")),
                                new SpatialAggregateDissolveOptions(
                                        true, false, "feature_count", tooMany)
                        )
                ),
                Map.of("source", source),
                context(limitIssues)
        );
        assertTrue(limitIssues.codes.contains("SPATIAL_DISSOLVE_STATISTIC_LIMIT_EXCEEDED"));
    }

    @Test
    void preservesNullAndEmptyRowsWhenExplodingGeometry() {
        SparkCanvasTable raw = table(
                new CanvasTableSchema(
                        "raw",
                        null,
                        List.of(scalar("id"), stringColumn("wkt", true)),
                        CanvasDatasetKind.BOUNDED,
                        null,
                        null
                ),
                List.of(
                        RowFactory.create(1L, null),
                        RowFactory.create(2L, "MULTIPOLYGON EMPTY")
                )
        );
        CanvasNodeOperationResult constructed = new GeometryConstructNodeOperator().apply(
                new GeometryConstructNodeDefinition(
                        UUID.randomUUID().toString(),
                        "Geometry 构造",
                        LAYOUT,
                        new GeometryConstructConfiguration(
                                "raw", "geometry_table", "shape",
                                new GeometryConstructSource.Wkt("wkt"),
                                geometryType(GeometryKind.MULTIPOLYGON, 4326)
                        )
                ),
                Map.of("raw", raw),
                context(new RecordingIssueSink())
        );
        CanvasNodeOperationResult exploded = new GeometryExplodeNodeOperator().apply(
                new GeometryExplodeNodeDefinition(
                        UUID.randomUUID().toString(),
                        "Geometry 拆分",
                        LAYOUT,
                        new GeometryExplodeConfiguration(
                                "geometry_table", "parts", "shape", "part", "part_index")
                ),
                constructed.propagatedTables(),
                context(new RecordingIssueSink())
        );

        List<Row> rows = exploded.propagatedTables().get("parts").dataset()
                .orderBy("id").collectAsList();

        assertEquals(2, rows.size());
        assertTrue(rows.stream().allMatch(row -> row.isNullAt(row.fieldIndex("part"))));
        assertTrue(rows.stream().allMatch(row -> row.isNullAt(row.fieldIndex("part_index"))));
    }

    @Test
    void reportsStableSpatialFoundationConfigurationErrors() {
        SparkCanvasTable source = table(
                "source",
                List.of(
                        scalar("not_text"),
                        geometry("web_mercator", GeometryKind.POINT, 3857)
                )
        );
        RecordingIssueSink constructIssues = new RecordingIssueSink();
        new GeometryConstructNodeOperator().apply(
                new GeometryConstructNodeDefinition(
                        UUID.randomUUID().toString(),
                        "Geometry 构造",
                        LAYOUT,
                        new GeometryConstructConfiguration(
                                "source",
                                "constructed",
                                "shape",
                                new GeometryConstructSource.Wkt("not_text"),
                                geometryType(GeometryKind.POINT, 4326)
                        )
                ),
                Map.of("source", source),
                context(constructIssues)
        );
        assertTrue(constructIssues.codes.contains("GEOMETRY_CONSTRUCT_SOURCE_TYPE_MISMATCH"));

        RecordingIssueSink measureIssues = new RecordingIssueSink();
        new SpatialMeasureNodeOperator().apply(
                new SpatialMeasureNodeDefinition(
                        UUID.randomUUID().toString(),
                        "空间测量",
                        LAYOUT,
                        new SpatialMeasureConfiguration(
                                "source",
                                "measured",
                                List.of(new SpatialMeasurement.Distance(
                                        "web_mercator",
                                        "web_mercator",
                                        SpatialMeasureMode.SPHEROID,
                                        "distance"
                                ))
                        )
                ),
                Map.of("source", source),
                context(measureIssues)
        );
        assertTrue(measureIssues.codes.contains("SPHEROID_MEASURE_REQUIRES_WGS84"));

        RecordingIssueSink serializeIssues = new RecordingIssueSink();
        new GeometrySerializeNodeOperator().apply(
                new GeometrySerializeNodeDefinition(
                        UUID.randomUUID().toString(),
                        "GeoJSON",
                        LAYOUT,
                        new GeometrySerializeConfiguration(
                                "source",
                                "serialized",
                                "web_mercator",
                                "geojson",
                                GeometrySerializationFormat.GEOJSON
                        )
                ),
                Map.of("source", source),
                context(serializeIssues)
        );
        assertTrue(serializeIssues.codes.contains("GEOJSON_REQUIRES_WGS84"));

        RecordingIssueSink bufferIssues = new RecordingIssueSink();
        new GeometryBufferNodeOperator().apply(
                new GeometryBufferNodeDefinition(
                        UUID.randomUUID().toString(),
                        "Geometry Buffer",
                        LAYOUT,
                        new GeometryBufferConfiguration(
                                "source",
                                "buffered",
                                "web_mercator",
                                "buffer_geometry",
                                0d,
                                SpatialMeasureMode.SPHEROID
                        )
                ),
                Map.of("source", source),
                context(bufferIssues)
        );
        assertTrue(bufferIssues.codes.contains("INVALID_GEOMETRY_BUFFER_DISTANCE"));
        assertTrue(bufferIssues.codes.contains("SPHEROID_BUFFER_REQUIRES_WGS84"));
    }

    private SparkCanvasTable constructWktTable(
            String wkt,
            GeometryKind kind,
            String outputTableName
    ) {
        SparkCanvasTable raw = table(
                new CanvasTableSchema(
                        "raw",
                        null,
                        List.of(stringColumn("wkt", false)),
                        CanvasDatasetKind.BOUNDED,
                        null,
                        null
                ),
                List.of(RowFactory.create(wkt))
        );
        CanvasNodeOperationResult result = new GeometryConstructNodeOperator().apply(
                new GeometryConstructNodeDefinition(
                        UUID.randomUUID().toString(),
                        "Geometry 构造",
                        LAYOUT,
                        new GeometryConstructConfiguration(
                                "raw", outputTableName, "shape",
                                new GeometryConstructSource.Wkt("wkt"),
                                geometryType(kind, 4326)
                        )
                ),
                Map.of("raw", raw),
                context(new RecordingIssueSink())
        );
        return result.propagatedTables().get(outputTableName);
    }

    private SparkCanvasTable constructWktRows(
            String rawTableName,
            String outputTableName,
            String idColumnName,
            GeometryKind kind,
            List<Row> rows
    ) {
        SparkCanvasTable raw = table(
                new CanvasTableSchema(
                        rawTableName,
                        null,
                        List.of(scalar(idColumnName), stringColumn("wkt", false)),
                        CanvasDatasetKind.BOUNDED,
                        null,
                        null),
                rows);
        RecordingIssueSink issues = new RecordingIssueSink();
        CanvasNodeOperationResult result = new GeometryConstructNodeOperator().apply(
                new GeometryConstructNodeDefinition(
                        UUID.randomUUID().toString(), "Geometry 构造", LAYOUT,
                        new GeometryConstructConfiguration(
                                rawTableName, outputTableName, "shape",
                                new GeometryConstructSource.Wkt("wkt"),
                                geometryType(kind, 4326))),
                Map.of(rawTableName, raw), context(issues));
        assertFalse(issues.hasErrors(), () -> issues.codes.toString());
        return result.propagatedTables().get(outputTableName);
    }

    @Test
    void geometryBufferSupportsPositiveFieldAndExpressionDistancesWithoutExposingNullRows() {
        SparkCanvasTable raw = table(
                new CanvasTableSchema(
                        "raw_buffer_distance",
                        null,
                        List.of(
                                stringColumn("wkt", false),
                                typedColumn("radius", PlatformDataType.DOUBLE, true)
                        ),
                        CanvasDatasetKind.BOUNDED,
                        null,
                        null
                ),
                List.of(
                        RowFactory.create("POINT (0 0)", 1d),
                        RowFactory.create("POINT (10 0)", 2d),
                        RowFactory.create("POINT (20 0)", null)
                )
        );
        SparkCanvasTable source = new GeometryConstructNodeOperator().apply(
                new GeometryConstructNodeDefinition(
                        UUID.randomUUID().toString(), "构造", LAYOUT,
                        new GeometryConstructConfiguration(
                                "raw_buffer_distance", "buffer_source", "shape",
                                new GeometryConstructSource.Wkt("wkt"),
                                geometryType(GeometryKind.POINT, 4326)
                        )
                ),
                Map.of("raw_buffer_distance", raw),
                context(new RecordingIssueSink())
        ).propagatedTables().get("buffer_source");

        for (var sourceMode : List.of(
                new GeometryBufferConfiguration(
                        "buffer_source", "field_buffer", "shape", "buffer_shape",
                        100d, SpatialMeasureMode.PLANAR, SpatialDistanceUnit.SOURCE_CRS_UNIT,
                        GeometryBufferDistanceSource.FIELD, "radius", null),
                new GeometryBufferConfiguration(
                        "buffer_source", "expression_buffer", "shape", "buffer_shape",
                        100d, SpatialMeasureMode.PLANAR, SpatialDistanceUnit.SOURCE_CRS_UNIT,
                        GeometryBufferDistanceSource.EXPRESSION, null, "radius * 0.5")
        )) {
            RecordingIssueSink issues = new RecordingIssueSink();
            SparkCanvasTable output = new GeometryBufferNodeOperator().apply(
                    new GeometryBufferNodeDefinition(
                            UUID.randomUUID().toString(), "动态 Buffer", LAYOUT, sourceMode),
                    Map.of("buffer_source", source),
                    context(issues)
            ).propagatedTables().get(sourceMode.outputTableName());
            assertFalse(issues.hasErrors(), () -> issues.codes.toString());
            List<Row> rows = output.dataset().orderBy("wkt").collectAsList();
            Geometry first = rows.get(0).getAs("buffer_shape");
            Geometry second = rows.get(1).getAs("buffer_shape");
            assertEquals(sourceMode.effectiveDistanceSource() == GeometryBufferDistanceSource.FIELD
                    ? 2d : 1d, first.getEnvelopeInternal().getWidth(), 1e-9);
            assertEquals(sourceMode.effectiveDistanceSource() == GeometryBufferDistanceSource.FIELD
                    ? 4d : 2d, second.getEnvelopeInternal().getWidth(), 1e-9);
            assertNull(rows.get(2).getAs("buffer_shape"));
        }
    }

    @Test
    void geometryBufferRejectsNonNumericOrNonScalarSourcesAndFailsOnInvalidRowValues() {
        SparkCanvasTable point = constructWktTable(
                "POINT (0 0)", GeometryKind.POINT, "buffer_source");
        List<CanvasColumnSchema> invalidValueColumns = new ArrayList<>(point.schema().columns());
        invalidValueColumns.add(typedColumn("radius", PlatformDataType.DOUBLE, false));
        CanvasTableSchema schema = new CanvasTableSchema(
                "buffer_source", null,
                invalidValueColumns,
                point.schema().datasetKind(), null, null);
        SparkCanvasTable invalidValue = new SparkCanvasTable(
                schema,
                point.dataset().withColumn("radius", org.apache.spark.sql.functions.lit(0d))
        );

        for (var invalid : List.of(
                new GeometryBufferConfiguration(
                        "buffer_source", "bad_field", "shape", "buffer_shape",
                        1d, SpatialMeasureMode.PLANAR, SpatialDistanceUnit.SOURCE_CRS_UNIT,
                        GeometryBufferDistanceSource.FIELD, "wkt", null),
                new GeometryBufferConfiguration(
                        "buffer_source", "bad_expression", "shape", "buffer_shape",
                        1d, SpatialMeasureMode.PLANAR, SpatialDistanceUnit.SOURCE_CRS_UNIT,
                        GeometryBufferDistanceSource.EXPRESSION, null, "sum(radius)")
        )) {
            RecordingIssueSink issues = new RecordingIssueSink();
            new GeometryBufferNodeOperator().apply(
                    new GeometryBufferNodeDefinition(UUID.randomUUID().toString(), "非法距离", LAYOUT, invalid),
                    Map.of("buffer_source", invalidValue), context(issues));
            assertTrue(issues.codes.contains(invalid.effectiveDistanceSource() == GeometryBufferDistanceSource.FIELD
                    ? "INVALID_GEOMETRY_BUFFER_DISTANCE_SOURCE"
                    : "INVALID_GEOMETRY_BUFFER_EXPRESSION"));
        }

        RecordingIssueSink runtimeIssues = new RecordingIssueSink();
        CanvasNodeOperationResult result = new GeometryBufferNodeOperator().apply(
                new GeometryBufferNodeDefinition(
                        UUID.randomUUID().toString(), "非法逐行距离", LAYOUT,
                        new GeometryBufferConfiguration(
                                "buffer_source", "runtime_invalid", "shape", "buffer_shape",
                                1d, SpatialMeasureMode.PLANAR, SpatialDistanceUnit.SOURCE_CRS_UNIT,
                                GeometryBufferDistanceSource.FIELD, "radius", null)
                ),
                Map.of("buffer_source", invalidValue), context(runtimeIssues));
        assertFalse(runtimeIssues.hasErrors(), () -> runtimeIssues.codes.toString());
        Exception failure = assertThrows(Exception.class,
                () -> result.propagatedTables().get("runtime_invalid").dataset().collectAsList());
        assertTrue(exceptionMessages(failure).contains("GEOMETRY_BUFFER_DISTANCE_VALUE_INVALID"));
    }

    @Test
    void unionMergeLayersMatchesRenamesRemovesAndPadsMissingFields() {
        SparkCanvasTable base = table(
                new CanvasTableSchema("base", null, List.of(
                        typedColumn("id", PlatformDataType.LONG, false),
                        stringColumn("name", true)
                )),
                List.of(RowFactory.create(1L, "base-row"))
        );
        SparkCanvasTable merge = table(
                new CanvasTableSchema("merge", null, List.of(
                        typedColumn("source_id", PlatformDataType.INTEGER, false),
                        stringColumn("description", true),
                        stringColumn("discarded", true)
                )),
                List.of(RowFactory.create(2, "merge-row", "secret"))
        );
        RecordingIssueSink issues = new RecordingIssueSink();
        Map<String, SparkCanvasTable> inputs = new LinkedHashMap<>();
        inputs.put("base", base);
        inputs.put("merge", merge);

        CanvasNodeOperationResult result = new UnionNodeOperator().apply(
                new UnionNodeDefinition(
                        UUID.randomUUID().toString(), "Merge Layers", LAYOUT,
                        new UnionConfiguration(
                                List.of("base", "merge"),
                                "merged",
                                UnionMode.ALL,
                                List.of(new UnionMergeTable("merge", List.of(
                                        new UnionMergeFieldRule(
                                                "source_id", UnionMergeFieldAction.MATCH, "id"),
                                        new UnionMergeFieldRule(
                                                "description", UnionMergeFieldAction.RENAME, "detail"),
                                        new UnionMergeFieldRule(
                                                "discarded", UnionMergeFieldAction.REMOVE, null)
                                )))
                        )
                ),
                inputs,
                context(issues)
        );

        assertFalse(issues.hasErrors(), () -> issues.codes.toString());
        SparkCanvasTable output = result.propagatedTables().get("merged");
        assertEquals(
                List.of("id", "name", "detail"),
                output.schema().columns().stream().map(CanvasColumnSchema::name).toList()
        );
        List<Row> rows = output.dataset().orderBy("id").collectAsList();
        assertEquals(1L, rows.get(0).<Long>getAs("id").longValue());
        assertEquals("base-row", rows.get(0).getAs("name"));
        assertNull(rows.get(0).getAs("detail"));
        assertEquals(2L, rows.get(1).<Long>getAs("id").longValue());
        assertNull(rows.get(1).getAs("name"));
        assertEquals("merge-row", rows.get(1).getAs("detail"));
    }

    @Test
    void unionMergeLayersExtendsSchemaAcrossMultipleLayersAndMatchesLaterFields() {
        SparkCanvasTable base = table(
                new CanvasTableSchema("base", null, List.of(
                        typedColumn("id", PlatformDataType.LONG, false),
                        stringColumn("base_only", true)
                )),
                List.of(RowFactory.create(1L, "base"))
        );
        SparkCanvasTable firstMerge = table(
                new CanvasTableSchema("first_merge", null, List.of(
                        typedColumn("id", PlatformDataType.INTEGER, false),
                        stringColumn("shared_extra", true)
                )),
                List.of(RowFactory.create(2, "first"))
        );
        SparkCanvasTable secondMerge = table(
                new CanvasTableSchema("second_merge", null, List.of(
                        typedColumn("id", PlatformDataType.SHORT, false),
                        stringColumn("shared_extra", true),
                        typedColumn("second_only", PlatformDataType.BOOLEAN, true)
                )),
                List.of(RowFactory.create((short) 3, "second", true))
        );
        Map<String, SparkCanvasTable> inputs = new LinkedHashMap<>();
        inputs.put("base", base);
        inputs.put("first_merge", firstMerge);
        inputs.put("second_merge", secondMerge);
        RecordingIssueSink issues = new RecordingIssueSink();

        CanvasNodeOperationResult result = new UnionNodeOperator().apply(
                new UnionNodeDefinition(
                        UUID.randomUUID().toString(), "Merge Layers", LAYOUT,
                        new UnionConfiguration(
                                List.of("base", "first_merge", "second_merge"),
                                "merged", UnionMode.ALL, List.of())
                ),
                inputs,
                context(issues)
        );

        assertFalse(issues.hasErrors(), () -> issues.codes.toString());
        SparkCanvasTable output = result.propagatedTables().get("merged");
        assertEquals(
                List.of("id", "base_only", "shared_extra", "second_only"),
                output.schema().columns().stream().map(CanvasColumnSchema::name).toList()
        );
        List<Row> rows = output.dataset().orderBy("id").collectAsList();
        assertEquals("first", rows.get(1).getAs("shared_extra"));
        assertEquals("second", rows.get(2).getAs("shared_extra"));
        assertEquals(true, rows.get(2).getAs("second_only"));
    }

    @Test
    void unionMergeLayersPropagatesMatchedStreamingEventTime() {
        SparkCanvasTable base = table(new CanvasTableSchema(
                "base",
                null,
                List.of(typedColumn("event_time", PlatformDataType.TIMESTAMP, false)),
                CanvasDatasetKind.UNBOUNDED,
                "event_time",
                "10 minutes"
        ), List.of());
        SparkCanvasTable merge = table(new CanvasTableSchema(
                "merge",
                null,
                List.of(typedColumn("occurred_at", PlatformDataType.TIMESTAMP, false)),
                CanvasDatasetKind.UNBOUNDED,
                "occurred_at",
                "10 minutes"
        ), List.of());
        RecordingIssueSink issues = new RecordingIssueSink();

        CanvasNodeOperationResult result = new UnionNodeOperator().apply(
                new UnionNodeDefinition(
                        UUID.randomUUID().toString(), "Streaming Merge", LAYOUT,
                        new UnionConfiguration(
                                List.of("base", "merge"),
                                "merged",
                                UnionMode.ALL,
                                List.of(new UnionMergeTable("merge", List.of(
                                        new UnionMergeFieldRule(
                                                "occurred_at",
                                                UnionMergeFieldAction.MATCH,
                                                "event_time")
                                )))
                        )
                ),
                new LinkedHashMap<>(Map.of("base", base, "merge", merge)),
                context(issues)
        );

        assertFalse(issues.hasErrors(), () -> issues.codes.toString());
        CanvasTableSchema output = result.propagatedTables().get("merged").schema();
        assertEquals(CanvasDatasetKind.UNBOUNDED, output.datasetKind());
        assertEquals("event_time", output.eventTimeColumn());
        assertEquals("10 minutes", output.watermarkDelay());
    }

    @Test
    void unionMergeLayersRejectsCaseInsensitiveDuplicateInputColumns() {
        SparkCanvasTable base = table("base", List.of(
                typedColumn("ID", PlatformDataType.LONG, false),
                typedColumn("id", PlatformDataType.LONG, false)
        ));
        SparkCanvasTable merge = table("merge", List.of(
                typedColumn("id", PlatformDataType.LONG, false)
        ));
        RecordingIssueSink issues = new RecordingIssueSink();

        new UnionNodeOperator().apply(
                new UnionNodeDefinition(
                        UUID.randomUUID().toString(), "Ambiguous Merge", LAYOUT,
                        new UnionConfiguration(
                                List.of("base", "merge"), "merged", UnionMode.ALL, List.of())
                ),
                new LinkedHashMap<>(Map.of("base", base, "merge", merge)),
                context(issues)
        );

        assertTrue(issues.codes.contains("DUPLICATE_COLUMN_NAME"));
    }

    @Test
    void unionMergeLayersRejectsStringNumericMatchesAndGeometryMismatch() {
        SparkCanvasTable base = table("base", List.of(
                typedColumn("id", PlatformDataType.LONG, false),
                geometry("shape", GeometryKind.POINT, 4326)
        ));
        SparkCanvasTable merge = table("merge", List.of(
                stringColumn("code", false),
                geometry("geometry", GeometryKind.POINT, 3857)
        ));
        RecordingIssueSink issues = new RecordingIssueSink();
        Map<String, SparkCanvasTable> inputs = new LinkedHashMap<>();
        inputs.put("base", base);
        inputs.put("merge", merge);

        new UnionNodeOperator().apply(
                new UnionNodeDefinition(
                        UUID.randomUUID().toString(), "Invalid Merge", LAYOUT,
                        new UnionConfiguration(
                                List.of("base", "merge"), "merged", UnionMode.ALL,
                                List.of(new UnionMergeTable("merge", List.of(
                                        new UnionMergeFieldRule(
                                                "code", UnionMergeFieldAction.MATCH, "id"),
                                        new UnionMergeFieldRule(
                                                "geometry", UnionMergeFieldAction.MATCH, "shape")
                                )))
                        )
                ),
                inputs,
                context(issues)
        );

        assertTrue(issues.codes.contains("UNION_MATCH_TYPE_MISMATCH"));
        assertTrue(issues.codes.contains("SPATIAL_SCHEMA_MISMATCH"));
        assertEquals(
                1L,
                issues.codes.stream()
                        .filter("SPATIAL_SCHEMA_MISMATCH"::equals)
                        .count(),
                "同一个 Geometry Match 错误只应产生一条诊断"
        );
    }

    private CanvasNodeOperationResult clip(
            SparkCanvasTable source,
            SparkCanvasTable mask,
            SpatialClipGeometryPolicy policy,
            RecordingIssueSink issues
    ) {
        return clip(source, mask, policy, null, issues);
    }

    private CanvasNodeOperationResult clip(
            SparkCanvasTable source,
            SparkCanvasTable mask,
            SpatialClipGeometryPolicy policy,
            SpatialClipMaskCombination maskCombination,
            RecordingIssueSink issues
    ) {
        Map<String, SparkCanvasTable> inputs = new LinkedHashMap<>();
        inputs.put("source", source);
        inputs.put("mask", mask);
        return new SpatialClipNodeOperator().apply(
                new SpatialClipNodeDefinition(
                        UUID.randomUUID().toString(),
                        "空间裁剪",
                        LAYOUT,
                        new SpatialClipConfiguration(
                                "source", "mask", "clipped",
                                "shape", "shape", "clipped_shape", policy,
                                maskCombination)
                ),
                inputs,
                context(issues)
        );
    }

    private SparkCanvasTable markLineageInput(SparkCanvasTable table, String key) {
        TaskLineageEvidence.Asset asset = new TaskLineageEvidence.Asset(
                key, TaskLineageEvidence.AssetRole.INPUT,
                TaskLineageEvidence.AssetKind.JDBC_TABLE,
                null, null, null, null, UUID.randomUUID(),
                null, null, key, null, null, key);
        Map<String, CatalystLineageMetadata.InputField> fields = new LinkedHashMap<>();
        for (CanvasColumnSchema column : table.schema().columns()) {
            fields.put(column.name(), new CatalystLineageMetadata.InputField(
                    key + ":" + column.name(), null));
        }
        return new SparkCanvasTable(table.schema(), CatalystLineageMetadata.markInput(
                table.dataset(), key + "-node", asset, fields));
    }

    private TaskLineageEvidence.Flow analyzeLineage(SparkCanvasTable table) {
        TaskLineageEvidence.Asset outputAsset = new TaskLineageEvidence.Asset(
                "output", TaskLineageEvidence.AssetRole.OUTPUT,
                TaskLineageEvidence.AssetKind.JDBC_TABLE,
                null, TaskLineageEvidence.WriteMode.APPEND, null, null,
                UUID.randomUUID(), null, null, "clipped", null, null, "clipped");
        CatalystLineageOutputCandidate candidate = new CatalystLineageOutputCandidate(
                "flow", "output-node", "JDBC_OUTPUT", "write",
                table.dataset(), outputAsset,
                table.schema().columns().stream().map(column ->
                        new CatalystLineageOutputCandidate.TargetField(
                                "out:" + column.name(), null,
                                column.name(), column.name(),
                                TaskLineageEvidence.OutputEffect.WRITTEN_UNKNOWN_SOURCE))
                        .toList());
        return new CatalystLineageAnalyzer().analyze(List.of(candidate))
                .flows().getFirst();
    }

    private record ClipFamilyExample(
            GeometryKind sourceKind,
            String wkt,
            GeometryKind outputKind,
            String geometryType
    ) { }

    private CanvasNodeOperationContext context(RecordingIssueSink issues) {
        return new CanvasNodeOperationContext(
                spark,
                MetadataIndex.create(new MetadataSnapshot(List.of(), List.of())),
                issues,
                new SchemaOnlyCanvasNodeDataAccess(spark)
        );
    }

    private CanvasNodeOperationContext runtimeContext(RecordingIssueSink issues) {
        return new CanvasNodeOperationContext(
                spark,
                MetadataIndex.create(new MetadataSnapshot(List.of(), List.of())),
                issues,
                new SchemaOnlyCanvasNodeDataAccess(spark),
                cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode.BATCH,
                CanvasRuntimeValues.execution(UUID.randomUUID(), Instant.EPOCH)
        );
    }

    private SparkCanvasTable table(String name, List<CanvasColumnSchema> columns) {
        CanvasTableSchema schema = new CanvasTableSchema(name, null, columns);
        return table(schema, List.of());
    }

    private SparkCanvasTable table(CanvasTableSchema schema, List<Row> rows) {
        return new SparkCanvasTable(
                schema,
                spark.createDataFrame(rows, SparkTypeMapper.toStructType(schema.columns()))
        );
    }

    private static CanvasColumnSchema scalar(String name) {
        return new CanvasColumnSchema(
                name,
                PlatformDataType.LONG,
                null,
                null,
                null,
                false,
                null,
                false,
                false,
                null
        );
    }

    private static CanvasColumnSchema stringColumn(String name, boolean nullable) {
        return typedColumn(name, PlatformDataType.STRING, nullable);
    }

    private static CanvasColumnSchema typedColumn(
            String name,
            PlatformDataType type,
            boolean nullable
    ) {
        return new CanvasColumnSchema(
                name,
                type,
                null,
                null,
                null,
                nullable,
                null,
                false,
                false,
                null
        );
    }

    private static CanvasColumnSchema geometry(String name, GeometryKind kind, int epsgCode) {
        return new CanvasColumnSchema(
                name,
                PlatformDataType.GEOMETRY,
                null,
                null,
                null,
                true,
                null,
                false,
                false,
                null,
                geometryType(kind, epsgCode)
        );
    }

    private static GeometryTypeDefinition geometryType(GeometryKind kind, int epsgCode) {
        return new GeometryTypeDefinition(
                kind,
                new CrsReference("EPSG", epsgCode),
                CoordinateDimension.XY
        );
    }

    private static SpatialAggregateStatistic statistic(
            SpatialAggregateStatisticKind kind,
            String sourceColumnName,
            String outputColumnName
    ) {
        return new SpatialAggregateStatistic(
                UUID.randomUUID().toString(), kind, sourceColumnName, outputColumnName);
    }

    private static String exceptionMessages(Throwable throwable) {
        StringBuilder messages = new StringBuilder();
        Throwable current = throwable;
        while (current != null) {
            if (current.getMessage() != null) messages.append(current.getMessage()).append('\n');
            current = current.getCause();
        }
        return messages.toString();
    }

    private static final class RecordingIssueSink implements CanvasNodeIssueSink {
        private final List<String> codes = new ArrayList<>();
        private boolean errors;

        @Override
        public void error(String code, String message, String path) {
            codes.add(code);
            errors = true;
        }

        @Override
        public void warning(String code, String message, String path) {
            codes.add(code);
        }

        @Override
        public boolean hasErrors() {
            return errors;
        }
    }
}
