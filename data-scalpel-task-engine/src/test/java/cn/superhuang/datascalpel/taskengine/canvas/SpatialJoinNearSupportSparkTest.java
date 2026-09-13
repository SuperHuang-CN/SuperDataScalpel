package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.*;
import cn.superhuang.data.scalpel.contract.type.*;
import cn.superhuang.datascalpel.taskengine.compiler.MetadataIndex;
import cn.superhuang.datascalpel.taskengine.spark.SedonaSparkSupport;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SpatialJoinNearSupportSparkTest {
    private SparkSession spark;

    @BeforeAll
    void startSpark() {
        spark = SedonaSparkSupport.initialize(SedonaSparkSupport.builder()
                .master("local[1]")
                .appName("spatial-join-near-support-test")
                .config("spark.ui.enabled", "false")
                .config("spark.driver.host", "127.0.0.1")
                .config("spark.driver.bindAddress", "127.0.0.1")
                .config("spark.sql.caseSensitive", "true")
                .getOrCreate());
    }

    @AfterAll
    void stopSpark() {
        if (spark != null) spark.stop();
    }

    @Test
    void planarNearCanBeTheOnlySpatialConditionAndOutputsConvertedDistance() {
        Map<String, SparkCanvasTable> inputs = inputs(
                "POINT (0 0)", GeometryKind.POINT,
                "POINT (3 4)", GeometryKind.POINT,
                CrsReference.epsg(3857), false);
        SpatialJoinSpatialNearCondition near = new SpatialJoinSpatialNearCondition(
                "shape", "shape", SpatialDistanceMethod.PLANAR,
                5d, SpatialDistanceUnit.METERS);
        SpatialJoinDistanceOutput output = new SpatialJoinDistanceOutput(
                true, "distance_km", SpatialDistanceUnit.KILOMETERS,
                "time_gap", SpatialDurationUnit.SECONDS);

        Row row = execute(inputs, configuration(
                JoinType.INNER, List.of(), near, null, output,
                SpatialJoinOperation.JOIN_ONE_TO_MANY)).getFirst();

        assertEquals(1L, row.getLong(row.fieldIndex("left_id")));
        assertEquals(10L, row.getLong(row.fieldIndex("right_id")));
        assertEquals(new BigDecimal("0.005000000000"), row.getDecimal(
                row.fieldIndex("distance_km")));
    }

    @Test
    void geodesicNearUsesRealGeometryPositionsInsteadOfCentroids() {
        Map<String, SparkCanvasTable> inputs = inputs(
                "LINESTRING (0 0, 1 0)", GeometryKind.LINESTRING,
                "POINT (1.005 0)", GeometryKind.POINT,
                CrsReference.epsg(4326), false);
        SpatialJoinSpatialNearCondition near = new SpatialJoinSpatialNearCondition(
                "shape", "shape", SpatialDistanceMethod.GEODESIC,
                1d, SpatialDistanceUnit.KILOMETERS);
        SpatialJoinDistanceOutput output = new SpatialJoinDistanceOutput(
                true, "distance_m", SpatialDistanceUnit.METERS,
                "time_gap", SpatialDurationUnit.SECONDS);

        Row row = execute(inputs, configuration(
                JoinType.INNER, List.of(), near, null, output,
                SpatialJoinOperation.JOIN_ONE_TO_MANY)).getFirst();
        double distance = row.getDecimal(row.fieldIndex("distance_m")).doubleValue();

        assertTrue(distance > 500d && distance < 600d, String.valueOf(distance));
    }

    @Test
    void geodesicNearUsesSpatialCandidateRecallInsteadOfCartesianComparison() {
        Map<String, SparkCanvasTable> inputs = inputs(
                "LINESTRING (0 0, 1 0)", GeometryKind.LINESTRING,
                "POINT (1.005 0)", GeometryKind.POINT,
                CrsReference.epsg(4326), false);
        SpatialJoinSpatialNearCondition near = new SpatialJoinSpatialNearCondition(
                "shape", "shape", SpatialDistanceMethod.GEODESIC,
                1d, SpatialDistanceUnit.KILOMETERS);
        SpatialJoinConfiguration configuration = configuration(
                JoinType.INNER, List.of(), near, null, null,
                SpatialJoinOperation.JOIN_ONE_TO_MANY);
        RecordingIssueSink issues = new RecordingIssueSink();

        CanvasNodeOperationResult result = new SpatialJoinNodeOperator().apply(
                definition(configuration), inputs, context(issues));
        String plan = result.propagatedTables().get("matched").dataset()
                .queryExecution().executedPlan().toString();

        assertFalse(issues.hasErrors(), () -> issues.codes.toString());
        assertFalse(plan.contains("CartesianProduct"), plan);
        assertFalse(plan.contains("BroadcastNestedLoopJoin"), plan);
    }

    @Test
    void leftJoinKeepsUnmatchedTargetAndNearOutputsRemainNull() {
        Map<String, SparkCanvasTable> inputs = inputs(
                "POINT (0 0)", GeometryKind.POINT,
                "POINT (30 40)", GeometryKind.POINT,
                CrsReference.epsg(3857), false);
        SpatialJoinSpatialNearCondition near = new SpatialJoinSpatialNearCondition(
                "shape", "shape", SpatialDistanceMethod.PLANAR,
                5d, SpatialDistanceUnit.METERS);
        SpatialJoinDistanceOutput output = new SpatialJoinDistanceOutput(
                true, "distance_m", SpatialDistanceUnit.METERS,
                "time_gap", SpatialDurationUnit.SECONDS);

        Row row = execute(inputs, configuration(
                JoinType.LEFT, List.of(), near, null, output,
                SpatialJoinOperation.JOIN_ONE_TO_MANY)).getFirst();

        assertTrue(row.isNullAt(row.fieldIndex("right_id")));
        assertTrue(row.isNullAt(row.fieldIndex("distance_m")));
    }

    @Test
    void combinesSpatialAndTemporalNearAndOutputsBothDistances() {
        Map<String, SparkCanvasTable> inputs = inputs(
                "POINT (0 0)", GeometryKind.POINT,
                "POINT (3 4)", GeometryKind.POINT,
                CrsReference.epsg(3857), true);
        SpatialJoinSpatialNearCondition near = new SpatialJoinSpatialNearCondition(
                "shape", "shape", SpatialDistanceMethod.PLANAR,
                5d, SpatialDistanceUnit.METERS);
        SpatialJoinTemporalCondition temporal = new SpatialJoinTemporalCondition(
                SpatialJoinTemporalRelationship.NEAR_BEFORE,
                "start_time", "end_time", "start_time", "end_time",
                2L, SpatialDurationUnit.SECONDS);
        SpatialJoinDistanceOutput output = new SpatialJoinDistanceOutput(
                true, "distance_m", SpatialDistanceUnit.METERS,
                "time_gap_ms", SpatialDurationUnit.MILLISECONDS);

        Row row = execute(inputs, configuration(
                JoinType.INNER, List.of(), near, temporal, output,
                SpatialJoinOperation.JOIN_ONE_TO_MANY)).getFirst();

        assertEquals(new BigDecimal("5.000000000000"), row.getDecimal(
                row.fieldIndex("distance_m")));
        assertEquals(new BigDecimal("1500.000000000000"), row.getDecimal(
                row.fieldIndex("time_gap_ms")));
    }

    @Test
    void validatesRequiredSpatialConditionUnitsOutputModeAndNamesBeforePlanning() {
        Map<String, SparkCanvasTable> projected = inputs(
                "POINT (0 0)", GeometryKind.POINT,
                "POINT (3 4)", GeometryKind.POINT,
                CrsReference.epsg(3857), false);
        assertIssue(projected, configuration(
                JoinType.INNER, List.of(), null, null, null,
                SpatialJoinOperation.JOIN_ONE_TO_MANY),
                "SPATIAL_JOIN_SPATIAL_CONDITION_REQUIRED");

        Map<String, SparkCanvasTable> wgs84 = inputs(
                "POINT (0 0)", GeometryKind.POINT,
                "POINT (0.01 0)", GeometryKind.POINT,
                CrsReference.epsg(4326), false);
        SpatialJoinSpatialNearCondition invalidNear = new SpatialJoinSpatialNearCondition(
                "shape", "shape", SpatialDistanceMethod.GEODESIC,
                1d, SpatialDistanceUnit.SOURCE_CRS_UNIT);
        assertIssue(wgs84, configuration(
                JoinType.INNER, List.of(), invalidNear, null, null,
                SpatialJoinOperation.JOIN_ONE_TO_MANY),
                "SPATIAL_DISTANCE_UNIT_UNSUPPORTED");

        SpatialJoinSpatialNearCondition validNear = new SpatialJoinSpatialNearCondition(
                "shape", "shape", SpatialDistanceMethod.GEODESIC,
                1d, SpatialDistanceUnit.KILOMETERS);
        SpatialJoinDistanceOutput duplicate = new SpatialJoinDistanceOutput(
                true, "left_id", SpatialDistanceUnit.METERS,
                "time_gap", SpatialDurationUnit.SECONDS);
        assertIssue(wgs84, configuration(
                JoinType.INNER, List.of(), validNear, null, duplicate,
                SpatialJoinOperation.JOIN_ONE_TO_MANY),
                "DUPLICATE_COLUMN_NAME");
        assertIssue(wgs84, configuration(
                JoinType.INNER, List.of(), validNear, null, duplicate,
                SpatialJoinOperation.JOIN_ONE_TO_ONE),
                "SPATIAL_JOIN_DISTANCE_OUTPUT_REQUIRES_ONE_TO_MANY");
    }

    private List<Row> execute(
            Map<String, SparkCanvasTable> inputs,
            SpatialJoinConfiguration configuration
    ) {
        RecordingIssueSink issues = new RecordingIssueSink();
        CanvasNodeOperationResult result = new SpatialJoinNodeOperator().apply(
                definition(configuration), inputs, context(issues));
        assertFalse(issues.hasErrors(), () -> issues.codes.toString());
        return result.propagatedTables().get("matched").dataset().collectAsList();
    }

    private void assertIssue(
            Map<String, SparkCanvasTable> inputs,
            SpatialJoinConfiguration configuration,
            String expectedCode
    ) {
        RecordingIssueSink issues = new RecordingIssueSink();
        new SpatialJoinNodeOperator().apply(definition(configuration), inputs, context(issues));
        assertTrue(issues.codes.contains(expectedCode), () -> issues.codes.toString());
    }

    private SpatialJoinConfiguration configuration(
            JoinType joinType,
            List<SpatialJoinCondition> conditions,
            SpatialJoinSpatialNearCondition near,
            SpatialJoinTemporalCondition temporal,
            SpatialJoinDistanceOutput output,
            SpatialJoinOperation operation
    ) {
        return new SpatialJoinConfiguration(
                "targets", "joins", "matched", joinType, conditions,
                List.of(),
                List.of(
                        new JoinOutputColumn(
                                JoinOutputColumnSource.LEFT, "left_id", "left_id", true),
                        new JoinOutputColumn(
                                JoinOutputColumnSource.RIGHT, "right_id", "right_id", true)),
                operation,
                operation == SpatialJoinOperation.JOIN_ONE_TO_ONE
                        ? new SpatialJoinOneToOneOptions(
                        SpatialJoinOneToOneMode.KEEP_ONE,
                        "join_count", List.of(),
                        new SpatialJoinKeepRule(
                                SpatialJoinKeepStrategy.FIRST, null,
                                List.of(new SortField(
                                        "right_id", SortDirection.ASC, NullOrdering.LAST))))
                        : null,
                temporal,
                near,
                output
        );
    }

    private SpatialJoinNodeDefinition definition(SpatialJoinConfiguration configuration) {
        return new SpatialJoinNodeDefinition(
                UUID.randomUUID().toString(), "空间 Near",
                new CanvasNodeLayout(0d, 0d, 368d, 224d), configuration);
    }

    private Map<String, SparkCanvasTable> inputs(
            String leftWkt,
            GeometryKind leftKind,
            String rightWkt,
            GeometryKind rightKind,
            CrsReference crs,
            boolean temporal
    ) {
        String leftTime = temporal
                ? ", TIMESTAMP '2024-01-01 00:00:00.000000' AS start_time"
                + ", TIMESTAMP '2024-01-01 00:00:01.000000' AS end_time" : "";
        String rightTime = temporal
                ? ", TIMESTAMP '2024-01-01 00:00:02.500000' AS start_time"
                + ", TIMESTAMP '2024-01-01 00:00:03.000000' AS end_time" : "";
        var left = spark.sql("SELECT 1L AS left_id, ST_SetSRID(ST_GeomFromWKT('"
                + leftWkt + "'), " + crs.code() + ") AS shape" + leftTime);
        var right = spark.sql("SELECT 10L AS right_id, ST_SetSRID(ST_GeomFromWKT('"
                + rightWkt + "'), " + crs.code() + ") AS shape" + rightTime);
        return Map.of(
                "targets", new SparkCanvasTable(schema(
                        "targets", "left_id", leftKind, crs, temporal), left),
                "joins", new SparkCanvasTable(schema(
                        "joins", "right_id", rightKind, crs, temporal), right));
    }

    private static CanvasTableSchema schema(
            String name,
            String id,
            GeometryKind kind,
            CrsReference crs,
            boolean temporal
    ) {
        List<CanvasColumnSchema> columns = new ArrayList<>();
        columns.add(column(id, PlatformDataType.LONG));
        columns.add(new CanvasColumnSchema(
                "shape", PlatformDataType.GEOMETRY,
                null, null, null, false, null, false, false, null,
                new GeometryTypeDefinition(kind, crs, CoordinateDimension.XY)));
        if (temporal) {
            columns.add(column("start_time", PlatformDataType.TIMESTAMP));
            columns.add(column("end_time", PlatformDataType.TIMESTAMP));
        }
        return new CanvasTableSchema(name, null, columns);
    }

    private static CanvasColumnSchema column(String name, PlatformDataType type) {
        return new CanvasColumnSchema(
                name, type, null, null, null, false,
                null, false, false, null);
    }

    private CanvasNodeOperationContext context(RecordingIssueSink issues) {
        return new CanvasNodeOperationContext(
                spark,
                MetadataIndex.create(new MetadataSnapshot(List.of(), List.of())),
                issues,
                new SchemaOnlyCanvasNodeDataAccess(spark));
    }

    private static final class RecordingIssueSink implements CanvasNodeIssueSink {
        private final List<String> codes = new ArrayList<>();
        private boolean hasErrors;

        @Override
        public void error(String code, String message, String path) {
            hasErrors = true;
            codes.add(code);
        }

        @Override
        public void warning(String code, String message, String path) {
            codes.add(code);
        }

        @Override
        public boolean hasErrors() {
            return hasErrors;
        }
    }
}
