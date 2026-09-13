package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.*;
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
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.RowFactory;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.functions;
import org.apache.spark.sql.sedona_sql.expressions.st_constructors;
import org.apache.spark.sql.sedona_sql.expressions.st_functions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SpatialHotSpotsNodeOperatorSparkTest {
    private SparkSession spark;

    @BeforeAll
    void startSpark() {
        spark = SedonaSparkSupport.initialize(SedonaSparkSupport.builder()
                .master("local[1]").appName("spatial-hot-spots-test")
                .config("spark.ui.enabled", "false")
                .config("spark.driver.host", "127.0.0.1")
                .config("spark.driver.bindAddress", "127.0.0.1")
                .config("spark.sql.shuffle.partitions", "1").getOrCreate());
    }

    @AfterAll
    void stopSpark() {
        if (spark != null) spark.stop();
    }

    @Test
    void calculatesGiStarOnCompleteSquareGridAndPreservesSource() {
        List<Row> rows = new ArrayList<>();
        for (int index = 0; index < 10; index++) rows.add(RowFactory.create("POINT (0.5 0.5)"));
        rows.add(RowFactory.create("POINT (1.5 0.5)"));
        rows.add(RowFactory.create("POINT (2.5 0.5)"));
        SparkCanvasTable raw = table("raw", List.of(stringColumn("wkt")), rows);
        RecordingIssueSink constructIssues = new RecordingIssueSink();
        CanvasNodeOperationResult constructed = new GeometryConstructNodeOperator().apply(
                new GeometryConstructNodeDefinition(id(), "构造", layout(),
                        new GeometryConstructConfiguration("raw", "points", "shape",
                                new GeometryConstructSource.Wkt("wkt"),
                                new GeometryTypeDefinition(GeometryKind.POINT,
                                        new CrsReference("EPSG", 3857), CoordinateDimension.XY))),
                Map.of("raw", raw), context(constructIssues));
        assertFalse(constructIssues.hasErrors(), constructIssues::toString);

        SpatialHotSpotsConfiguration configuration = new SpatialHotSpotsConfiguration(
                "points", "shape", SpatialHotSpotAnalysisSource.POINT_COUNT, null,
                1d, SpatialDistanceUnit.METERS, 1.1d, SpatialDistanceUnit.METERS,
                null, SpatialHotSpotMultipleTesting.FDR_BH,
                "hot_spots", "bin_id", "bin_geometry", "point_count", "analysis_value",
                "gi_z_score", "gi_p_value", "gi_adjusted_p_value", "gi_bin");
        RecordingIssueSink issues = new RecordingIssueSink();
        CanvasNodeOperationResult result = new SpatialHotSpotsNodeOperator().apply(
                new SpatialHotSpotsNodeDefinition(id(), "热点", layout(), configuration),
                Map.of("points", constructed.propagatedTables().get("points")), context(issues));

        assertFalse(issues.hasErrors(), issues::toString);
        assertTrue(result.propagatedTables().containsKey("points"));
        SparkCanvasTable hotSpots = result.propagatedTables().get("hot_spots");
        List<Row> output = hotSpots.dataset().orderBy("bin_id").collectAsList();
        assertEquals(3, output.size());
        assertEquals(List.of(10L, 1L, 1L), output.stream()
                .map(row -> (Long) row.getAs("point_count")).toList());
        assertEquals(10d, ((Number) output.getFirst().getAs("analysis_value")).doubleValue(), 1e-12);
        assertEquals(Math.sqrt(0.5d), ((Number) output.getFirst().getAs("gi_z_score")).doubleValue(), 1e-9);
        for (Row row : output) {
            double p = ((Number) row.getAs("gi_p_value")).doubleValue();
            double adjusted = ((Number) row.getAs("gi_adjusted_p_value")).doubleValue();
            assertTrue(p >= 0d && p <= 1d);
            assertTrue(adjusted + 1e-12 >= p && adjusted <= 1d);
        }
        assertEquals(GeometryKind.POLYGON, hotSpots.schema().columns().get(1).geometry().kind());
    }

    @Test
    void countAndFieldSumTimeSlicedOutputsHaveCompleteAndTruthfulFieldLineage() {
        SparkCanvasTable raw = generatedPointTable(0);
        var inputAsset = new TaskLineageEvidence.Asset(
                "input", TaskLineageEvidence.AssetRole.INPUT, TaskLineageEvidence.AssetKind.JDBC_TABLE,
                null, null, null, null, UUID.randomUUID(), null, null, "points", null, null, "points");
        var inputFields = new LinkedHashMap<String, CatalystLineageMetadata.InputField>();
        for (CanvasColumnSchema column : raw.schema().columns()) {
            inputFields.put(column.name(), new CatalystLineageMetadata.InputField("input:" + column.name(), null));
        }
        SparkCanvasTable source = new SparkCanvasTable(raw.schema(), CatalystLineageMetadata.markInput(
                raw.dataset(), "input-node", inputAsset, inputFields));
        SpatialTemporalSlicing temporal = new SpatialTemporalSlicing(
                "event_time", 10, SpatialDurationUnit.SECONDS, 5L, SpatialDurationUnit.SECONDS,
                null, "UTC", "window_start", "window_end");

        for (SpatialHotSpotAnalysisSource analysisSource : SpatialHotSpotAnalysisSource.values()) {
            SpatialHotSpotMultipleTesting multipleTesting =
                    analysisSource == SpatialHotSpotAnalysisSource.POINT_COUNT
                            ? SpatialHotSpotMultipleTesting.FDR_BH : SpatialHotSpotMultipleTesting.NONE;
            SpatialHotSpotsConfiguration configuration = configuration(
                    analysisSource, multipleTesting, temporal);
            RecordingIssueSink issues = new RecordingIssueSink();
            CanvasNodeOperationResult result = new SpatialHotSpotsNodeOperator().apply(
                    new SpatialHotSpotsNodeDefinition(id(), "热点", layout(), configuration),
                    Map.of("points", source), context(issues));
            assertFalse(issues.hasErrors(), issues::toString);

            SparkCanvasTable hotSpots = result.propagatedTables().get("hot_spots");
            var outputAsset = new TaskLineageEvidence.Asset(
                    "output", TaskLineageEvidence.AssetRole.OUTPUT, TaskLineageEvidence.AssetKind.JDBC_TABLE,
                    null, TaskLineageEvidence.WriteMode.APPEND, null, null, UUID.randomUUID(),
                    null, null, "hot_spots", null, null, "hot_spots");
            var candidate = new CatalystLineageOutputCandidate(
                    "flow", "output-node", "JDBC_OUTPUT", "write", hotSpots.dataset(), outputAsset,
                    hotSpots.schema().columns().stream().map(column ->
                            new CatalystLineageOutputCandidate.TargetField(
                                    "out:" + column.name(), null, column.name(), column.name(),
                                    TaskLineageEvidence.OutputEffect.WRITTEN_UNKNOWN_SOURCE)).toList());
            TaskLineageEvidence.Flow flow = new CatalystLineageAnalyzer()
                    .analyze(List.of(candidate)).flows().getFirst();

            assertEquals(TaskLineageEvidence.Coverage.FIELD_COMPLETE, flow.coverage(),
                    () -> analysisSource + ": " + flow.warnings());
            assertTrue(flow.fields().stream().noneMatch(field ->
                    field.outputEffect() == TaskLineageEvidence.OutputEffect.WRITTEN_UNKNOWN_SOURCE),
                    () -> analysisSource + ": " + flow.fields());
            for (String target : List.of(
                    "bin_id", "bin_geometry", "point_count", "analysis_value",
                    "gi_z_score", "gi_p_value", "gi_adjusted_p_value", "gi_bin")) {
                assertEdge(flow, target, "shape", analysisSource);
            }
            for (String target : List.of("window_start", "window_end")) {
                assertEdge(flow, target, "event_time", analysisSource);
            }
            if (analysisSource == SpatialHotSpotAnalysisSource.FIELD_SUM) {
                for (String target : List.of(
                        "analysis_value", "gi_z_score", "gi_p_value",
                        "gi_adjusted_p_value", "gi_bin")) {
                    assertEdge(flow, target, "quantity", analysisSource);
                }
            }
        }
    }

    @Test
    void twentyThousandPointPreviewIsLazyAndFieldSumExecutesWithoutDriverCollection() {
        SparkCanvasTable source = generatedPointTable(20_000);
        SpatialHotSpotsConfiguration configuration = configuration(
                SpatialHotSpotAnalysisSource.FIELD_SUM,
                SpatialHotSpotMultipleTesting.FDR_BH, null);

        String previewGroup = "hot-spots-preview-" + id();
        spark.sparkContext().setJobGroup(previewGroup, "hot spots preview", false);
        try {
            RecordingIssueSink previewIssues = new RecordingIssueSink();
            CanvasNodeOperationResult preview = new SpatialHotSpotsNodeOperator().apply(
                    new SpatialHotSpotsNodeDefinition(id(), "热点", layout(), configuration),
                    Map.of("points", source), context(previewIssues, true));
            assertFalse(previewIssues.hasErrors(), previewIssues::toString);
            preview.propagatedTables().get("hot_spots").dataset().queryExecution().analyzed();
            assertEquals(0, spark.sparkContext().statusTracker().getJobIdsForGroup(previewGroup).length);
        } finally {
            spark.sparkContext().clearJobGroup();
        }

        RecordingIssueSink executionIssues = new RecordingIssueSink();
        CanvasNodeOperationResult result = new SpatialHotSpotsNodeOperator().apply(
                new SpatialHotSpotsNodeDefinition(id(), "热点", layout(), configuration),
                Map.of("points", source), context(executionIssues, false));
        assertFalse(executionIssues.hasErrors(), executionIssues::toString);
        Dataset<Row> hotSpots = result.propagatedTables().get("hot_spots").dataset();
        assertEquals(20_000L, hotSpots.count());

        String plan = hotSpots.queryExecution().executedPlan().toString();
        String normalizedPlan = plan.toLowerCase(Locale.ROOT);
        assertTrue(normalizedPlan.contains("aggregate"), plan);
        assertTrue(normalizedPlan.contains("generate"), plan);
        assertFalse(plan.contains("CollectLimit") || normalizedPlan.contains("collect_list"), plan);
        assertFalse(plan.contains("CartesianProduct") || plan.contains("BroadcastNestedLoopJoin"), plan);
    }

    private SpatialHotSpotsConfiguration configuration(
            SpatialHotSpotAnalysisSource analysisSource,
            SpatialHotSpotMultipleTesting multipleTesting,
            SpatialTemporalSlicing temporal
    ) {
        return new SpatialHotSpotsConfiguration(
                "points", "shape", analysisSource,
                analysisSource == SpatialHotSpotAnalysisSource.FIELD_SUM ? "quantity" : null,
                10d, SpatialDistanceUnit.METERS, 11d, SpatialDistanceUnit.METERS,
                temporal, multipleTesting,
                "hot_spots", "bin_id", "bin_geometry", "point_count", "analysis_value",
                "gi_z_score", "gi_p_value", "gi_adjusted_p_value", "gi_bin");
    }

    private SparkCanvasTable generatedPointTable(long size) {
        Dataset<Row> data = spark.range(size).select(
                functions.col("id"),
                functions.pmod(functions.col("id"), functions.lit(17L)).plus(1d)
                        .cast("double").alias("quantity"),
                functions.expr("timestamp_seconds(id)").alias("event_time"),
                st_functions.ST_SetSRID(st_constructors.ST_Point(
                        functions.pmod(functions.col("id"), functions.lit(200L))
                                .multiply(10d).plus(5d).cast("double"),
                        functions.floor(functions.col("id").divide(200d))
                                .multiply(10d).plus(5d)),
                        functions.lit(3857)).alias("shape"));
        CanvasColumnSchema geometry = new CanvasColumnSchema(
                "shape", PlatformDataType.GEOMETRY, null, null, null, false,
                null, false, false, null,
                new GeometryTypeDefinition(GeometryKind.POINT,
                        new CrsReference("EPSG", 3857), CoordinateDimension.XY));
        List<CanvasColumnSchema> columns = List.of(
                column("id", PlatformDataType.LONG),
                column("quantity", PlatformDataType.DOUBLE),
                column("event_time", PlatformDataType.TIMESTAMP),
                geometry);
        return new SparkCanvasTable(new CanvasTableSchema(
                "points", null, columns, CanvasDatasetKind.BOUNDED, null, null), data);
    }

    private static void assertEdge(
            TaskLineageEvidence.Flow flow,
            String target,
            String source,
            SpatialHotSpotAnalysisSource analysisSource
    ) {
        assertTrue(flow.fieldEdges().stream().anyMatch(edge ->
                        edge.target().localFieldKey().equals("out:" + target)
                                && edge.source().localFieldKey().equals("input:" + source)),
                () -> analysisSource + ": " + source + " -> " + target
                        + " missing in " + flow.fieldEdges());
    }

    private CanvasNodeOperationContext context(RecordingIssueSink issues) {
        return new CanvasNodeOperationContext(spark,
                MetadataIndex.create(new MetadataSnapshot(List.of(), List.of())),
                issues, new SchemaOnlyCanvasNodeDataAccess(spark));
    }

    private CanvasNodeOperationContext context(RecordingIssueSink issues, boolean preview) {
        return new CanvasNodeOperationContext(
                spark, MetadataIndex.create(new MetadataSnapshot(List.of(), List.of())),
                issues, new SchemaOnlyCanvasNodeDataAccess(spark), CanvasExecutionMode.BATCH,
                preview ? CanvasRuntimeValues.forPreview()
                        : CanvasRuntimeValues.execution(UUID.randomUUID(), Instant.now()));
    }

    private SparkCanvasTable table(String name, List<CanvasColumnSchema> columns, List<Row> rows) {
        CanvasTableSchema schema = new CanvasTableSchema(name, null, columns,
                CanvasDatasetKind.BOUNDED, null, null);
        return new SparkCanvasTable(schema, spark.createDataFrame(rows, SparkTypeMapper.toStructType(columns)));
    }

    private static CanvasColumnSchema stringColumn(String name) {
        return new CanvasColumnSchema(name, PlatformDataType.STRING, null, null, null, true,
                null, false, false, null, null);
    }

    private static CanvasColumnSchema column(String name, PlatformDataType type) {
        return new CanvasColumnSchema(name, type, null, null, null, true,
                null, false, false, null, null);
    }

    private static CanvasNodeLayout layout() {
        return new CanvasNodeLayout(0d, 0d, 376d, 224d);
    }

    private static String id() {
        return UUID.randomUUID().toString();
    }

    private static final class RecordingIssueSink implements CanvasNodeIssueSink {
        private final List<String> codes = new ArrayList<>();
        private boolean errors;

        @Override
        public void error(String code, String message, String path) {
            codes.add(code + "@" + path);
            errors = true;
        }

        @Override
        public void warning(String code, String message, String path) {
            codes.add(code + "@" + path);
        }

        @Override
        public boolean hasErrors() {
            return errors;
        }

        @Override
        public String toString() {
            return codes.toString();
        }
    }
}
