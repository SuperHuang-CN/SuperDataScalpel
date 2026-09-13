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
class SpatialDensityNodeOperatorSparkTest {
    private SparkSession spark;

    @BeforeAll
    void startSpark() {
        spark = SedonaSparkSupport.initialize(SedonaSparkSupport.builder()
                .master("local[1]").appName("spatial-density-test")
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
    void calculatesUniformPointAndQuantityDensityAndPreservesSource() {
        SparkCanvasTable raw = table("raw", List.of(
                stringColumn("wkt"), doubleColumn("quantity")),
                List.of(RowFactory.create("POINT (0.5 0.5)", 2d), RowFactory.create("POINT (0.5 0.5)", null)));
        RecordingIssueSink constructIssues = new RecordingIssueSink();
        CanvasNodeOperationResult constructed = new GeometryConstructNodeOperator().apply(
                new GeometryConstructNodeDefinition(id(), "构造", layout(),
                        new GeometryConstructConfiguration("raw", "points", "shape",
                                new GeometryConstructSource.Wkt("wkt"),
                                new GeometryTypeDefinition(GeometryKind.POINT,
                                        new CrsReference("EPSG", 3857), CoordinateDimension.XY))),
                Map.of("raw", raw), context(constructIssues));
        assertFalse(constructIssues.hasErrors(), constructIssues::toString);

        SpatialDensityConfiguration configuration = new SpatialDensityConfiguration(
                "points", "shape",
                List.of(new SpatialDensityField(id(), "quantity", "quantity_density")),
                SpatialDensityWeighting.UNIFORM, SpatialDensityBinShape.SQUARE,
                1d, SpatialDistanceUnit.METERS, 2d, SpatialDistanceUnit.METERS,
                SpatialAreaUnit.SQUARE_METERS, null,
                "density", "bin_id", "bin_geometry", "point_density");
        RecordingIssueSink issues = new RecordingIssueSink();
        CanvasNodeOperationResult result = new SpatialDensityNodeOperator().apply(
                new SpatialDensityNodeDefinition(id(), "密度", layout(), configuration),
                Map.of("points", constructed.propagatedTables().get("points")), context(issues));

        assertFalse(issues.hasErrors(), issues::toString);
        assertTrue(result.propagatedTables().containsKey("points"));
        SparkCanvasTable density = result.propagatedTables().get("density");
        Row center = density.dataset().filter("bin_id like '%:0:0'").head();
        assertEquals(2d / (Math.PI * 4d), ((Number) center.getAs("point_density")).doubleValue(), 1e-9);
        assertEquals(2d / (Math.PI * 4d), ((Number) center.getAs("quantity_density")).doubleValue(), 1e-9);
        assertEquals(GeometryKind.POLYGON, density.schema().columns().get(1).geometry().kind());
    }

    @Test
    void uniformAndKernelTimeSlicedOutputsHaveCompleteAndTruthfulFieldLineage() {
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

        for (SpatialDensityWeighting weighting : SpatialDensityWeighting.values()) {
            SpatialDensityBinShape binShape = weighting == SpatialDensityWeighting.UNIFORM
                    ? SpatialDensityBinShape.SQUARE : SpatialDensityBinShape.HEXAGON;
            SpatialDensityConfiguration configuration = configuration(weighting, binShape, temporal);
            RecordingIssueSink issues = new RecordingIssueSink();
            CanvasNodeOperationResult result = new SpatialDensityNodeOperator().apply(
                    new SpatialDensityNodeDefinition(id(), "密度", layout(), configuration),
                    Map.of("points", source), context(issues));
            assertFalse(issues.hasErrors(), issues::toString);

            SparkCanvasTable density = result.propagatedTables().get("density");
            var outputAsset = new TaskLineageEvidence.Asset(
                    "output", TaskLineageEvidence.AssetRole.OUTPUT, TaskLineageEvidence.AssetKind.JDBC_TABLE,
                    null, TaskLineageEvidence.WriteMode.APPEND, null, null, UUID.randomUUID(),
                    null, null, "density", null, null, "density");
            var candidate = new CatalystLineageOutputCandidate(
                    "flow", "output-node", "JDBC_OUTPUT", "write", density.dataset(), outputAsset,
                    density.schema().columns().stream().map(column ->
                            new CatalystLineageOutputCandidate.TargetField(
                                    "out:" + column.name(), null, column.name(), column.name(),
                                    TaskLineageEvidence.OutputEffect.WRITTEN_UNKNOWN_SOURCE)).toList());
            TaskLineageEvidence.Flow flow = new CatalystLineageAnalyzer()
                    .analyze(List.of(candidate)).flows().getFirst();

            assertEquals(TaskLineageEvidence.Coverage.FIELD_COMPLETE, flow.coverage(),
                    () -> weighting + ": " + flow.warnings());
            assertTrue(flow.fields().stream().noneMatch(field ->
                    field.outputEffect() == TaskLineageEvidence.OutputEffect.WRITTEN_UNKNOWN_SOURCE),
                    () -> weighting + ": " + flow.fields());
            for (String target : List.of("bin_id", "bin_geometry", "point_density")) {
                assertEdge(flow, target, "shape", weighting);
            }
            for (String target : List.of("window_start", "window_end")) {
                assertEdge(flow, target, "event_time", weighting);
            }
            assertEdge(flow, "quantity_density", "quantity", weighting);
            assertEdge(flow, "quantity_density", "shape", weighting);
        }
    }

    @Test
    void twentyThousandPointPreviewIsLazyAndKernelDensityExecutesWithoutDriverCollection() {
        SparkCanvasTable source = generatedPointTable(20_000);
        SpatialDensityConfiguration configuration = configuration(
                SpatialDensityWeighting.KERNEL, SpatialDensityBinShape.HEXAGON, null);

        String previewGroup = "density-preview-" + id();
        spark.sparkContext().setJobGroup(previewGroup, "density preview", false);
        try {
            RecordingIssueSink previewIssues = new RecordingIssueSink();
            CanvasNodeOperationResult preview = new SpatialDensityNodeOperator().apply(
                    new SpatialDensityNodeDefinition(id(), "密度", layout(), configuration),
                    Map.of("points", source), context(previewIssues, true));
            assertFalse(previewIssues.hasErrors(), previewIssues::toString);
            preview.propagatedTables().get("density").dataset().queryExecution().analyzed();
            assertEquals(0, spark.sparkContext().statusTracker().getJobIdsForGroup(previewGroup).length);
        } finally {
            spark.sparkContext().clearJobGroup();
        }

        RecordingIssueSink executionIssues = new RecordingIssueSink();
        CanvasNodeOperationResult result = new SpatialDensityNodeOperator().apply(
                new SpatialDensityNodeDefinition(id(), "密度", layout(), configuration),
                Map.of("points", source), context(executionIssues, false));
        assertFalse(executionIssues.hasErrors(), executionIssues::toString);
        Dataset<Row> density = result.propagatedTables().get("density").dataset();
        assertTrue(density.count() > 0);

        String plan = density.queryExecution().executedPlan().toString();
        String normalizedPlan = plan.toLowerCase(Locale.ROOT);
        assertTrue(normalizedPlan.contains("aggregate"), plan);
        assertTrue(normalizedPlan.contains("generate"), plan);
        assertFalse(plan.contains("CollectLimit") || normalizedPlan.contains("collect_list"), plan);
        assertFalse(plan.contains("CartesianProduct") || plan.contains("BroadcastNestedLoopJoin"), plan);
    }

    private SpatialDensityConfiguration configuration(
            SpatialDensityWeighting weighting,
            SpatialDensityBinShape binShape,
            SpatialTemporalSlicing temporal
    ) {
        return new SpatialDensityConfiguration(
                "points", "shape",
                List.of(new SpatialDensityField(id(), "quantity", "quantity_density")),
                weighting, binShape,
                10d, SpatialDistanceUnit.METERS, 11d, SpatialDistanceUnit.METERS,
                SpatialAreaUnit.SQUARE_METERS, temporal,
                "density", "bin_id", "bin_geometry", "point_density");
    }

    private SparkCanvasTable generatedPointTable(long size) {
        Dataset<Row> data = spark.range(size).select(
                functions.col("id"),
                functions.col("id").plus(1d).cast("double").alias("quantity"),
                functions.expr("timestamp_seconds(id)").alias("event_time"),
                st_functions.ST_SetSRID(st_constructors.ST_Point(
                        functions.pmod(functions.col("id"), functions.lit(200L)).multiply(100d).cast("double"),
                        functions.floor(functions.col("id").divide(200d)).multiply(100d)),
                        functions.lit(3857)).alias("shape"));
        CanvasColumnSchema geometry = new CanvasColumnSchema(
                "shape", PlatformDataType.GEOMETRY, null, null, null, false,
                null, false, false, null,
                new GeometryTypeDefinition(GeometryKind.POINT,
                        new CrsReference("EPSG", 3857), CoordinateDimension.XY));
        List<CanvasColumnSchema> columns = List.of(
                column("id", PlatformDataType.LONG),
                doubleColumn("quantity"),
                column("event_time", PlatformDataType.TIMESTAMP),
                geometry);
        return new SparkCanvasTable(new CanvasTableSchema(
                "points", null, columns, CanvasDatasetKind.BOUNDED, null, null), data);
    }

    private static void assertEdge(
            TaskLineageEvidence.Flow flow,
            String target,
            String source,
            SpatialDensityWeighting weighting
    ) {
        assertTrue(flow.fieldEdges().stream().anyMatch(edge ->
                        edge.target().localFieldKey().equals("out:" + target)
                                && edge.source().localFieldKey().equals("input:" + source)),
                () -> weighting + ": " + source + " -> " + target + " missing in " + flow.fieldEdges());
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
        return column(name, PlatformDataType.STRING);
    }

    private static CanvasColumnSchema doubleColumn(String name) {
        return column(name, PlatformDataType.DOUBLE);
    }

    private static CanvasColumnSchema column(String name, PlatformDataType type) {
        return new CanvasColumnSchema(name, type, null, null, null, true,
                null, false, false, null, null);
    }

    private static CanvasNodeLayout layout() {
        return new CanvasNodeLayout(0d, 0d, 368d, 224d);
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
