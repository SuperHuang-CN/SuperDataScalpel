package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasDatasetKind;
import cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeLayout;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.GeometryConstructConfiguration;
import cn.superhuang.data.scalpel.contract.task.GeometryConstructNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.GeometryConstructSource;
import cn.superhuang.data.scalpel.contract.task.MetadataSnapshot;
import cn.superhuang.data.scalpel.contract.task.SpatialEnrichFromGridConfiguration;
import cn.superhuang.data.scalpel.contract.task.SpatialEnrichFromGridField;
import cn.superhuang.data.scalpel.contract.task.SpatialEnrichFromGridNodeDefinition;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SpatialEnrichFromGridNodeOperatorSparkTest {
    private SparkSession spark;

    @BeforeAll
    void startSpark() {
        spark = SedonaSparkSupport.initialize(SedonaSparkSupport.builder()
                .master("local[1]").appName("spatial-enrich-from-grid-test")
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
    void enrichesEachPointOnceKeepsUnmatchedAndUsesGridIdOnBoundary() {
        SparkCanvasTable points = geometryTable(
                "points_raw", "points", GeometryKind.POINT,
                List.of(stringColumn("point_id"), stringColumn("wkt")),
                List.of(
                        RowFactory.create("inside", "POINT (5 5)"),
                        RowFactory.create("boundary", "POINT (10 5)"),
                        RowFactory.create("outside", "POINT (25 5)")));
        SparkCanvasTable grid = geometryTable(
                "grid_raw", "grid", GeometryKind.POLYGON,
                List.of(stringColumn("bin_id"), stringColumn("category"), longColumn("population"),
                        stringColumn("wkt")),
                List.of(
                        RowFactory.create("A", "west", 10L,
                                "POLYGON ((0 0, 10 0, 10 10, 0 10, 0 0))"),
                        RowFactory.create("B", "east", 20L,
                                "POLYGON ((10 0, 20 0, 20 10, 10 10, 10 0))")));
        SpatialEnrichFromGridConfiguration configuration = new SpatialEnrichFromGridConfiguration(
                "points", "shape", "grid", "shape", "bin_id",
                List.of(
                        new SpatialEnrichFromGridField("category", "grid_category"),
                        new SpatialEnrichFromGridField("population", "grid_population")),
                "enriched_points");
        Map<String, SparkCanvasTable> inputs = new LinkedHashMap<>();
        inputs.put("points", points);
        inputs.put("grid", grid);
        RecordingIssueSink issues = new RecordingIssueSink();

        CanvasNodeOperationResult result = new SpatialEnrichFromGridNodeOperator().apply(
                new SpatialEnrichFromGridNodeDefinition(id(), "格网丰富", layout(), configuration),
                inputs, context(issues));

        assertFalse(issues.hasErrors(), issues::toString);
        assertEquals(List.of("points", "grid", "enriched_points"),
                new ArrayList<>(result.propagatedTables().keySet()));
        SparkCanvasTable output = result.propagatedTables().get("enriched_points");
        List<Row> rows = output.dataset().orderBy("point_id").collectAsList();
        assertEquals(3, rows.size());
        assertEquals("west", rows.getFirst().getAs("grid_category"));
        assertEquals(10L, ((Number) rows.getFirst().getAs("grid_population")).longValue());
        assertEquals("west", rows.get(1).getAs("grid_category"));
        assertNull(rows.getLast().getAs("grid_category"));
        assertTrue(output.schema().columns().getLast().nullable());
    }

    @Test
    void pointAndGridFieldsHaveCompleteAndTruthfulFieldLineage() {
        SparkCanvasTable points = markInput(geometryTable(
                "lineage_points_raw", "points", GeometryKind.POINT,
                List.of(stringColumn("point_id"), stringColumn("wkt")),
                List.of(RowFactory.create("p1", "POINT (5 5)"))), "points");
        SparkCanvasTable grid = markInput(geometryTable(
                "lineage_grid_raw", "grid", GeometryKind.POLYGON,
                List.of(stringColumn("bin_id"), stringColumn("category"),
                        longColumn("population"), stringColumn("wkt")),
                List.of(RowFactory.create("A", "west", 10L,
                        "POLYGON ((0 0, 10 0, 10 10, 0 10, 0 0))"))), "grid");
        SpatialEnrichFromGridConfiguration configuration = configuration();
        RecordingIssueSink issues = new RecordingIssueSink();

        CanvasNodeOperationResult result = new SpatialEnrichFromGridNodeOperator().apply(
                new SpatialEnrichFromGridNodeDefinition(id(), "格网丰富", layout(), configuration),
                Map.of("points", points, "grid", grid), context(issues));

        assertFalse(issues.hasErrors(), issues::toString);
        TaskLineageEvidence.Flow flow = analyzeLineage(
                result.propagatedTables().get("enriched_points"));
        assertEquals(TaskLineageEvidence.Coverage.FIELD_COMPLETE, flow.coverage(),
                () -> flow.warnings().toString());
        assertTrue(flow.fields().stream().noneMatch(field ->
                        field.outputEffect() == TaskLineageEvidence.OutputEffect.WRITTEN_UNKNOWN_SOURCE),
                () -> flow.fields().toString());
        assertEdge(flow, "point_id", "points", "point_id");
        assertEdge(flow, "shape", "points", "shape");
        assertEdge(flow, "grid_category", "grid", "category");
        assertEdge(flow, "grid_population", "grid", "population");
    }

    @Test
    void twentyThousandPointPreviewIsLazyAndExecutionAvoidsSpatialNestedLoopJoin() {
        SparkCanvasTable points = generatedPointTable("points", 20_000);
        SparkCanvasTable grid = generatedGridTable("grid", 20_000);
        SpatialEnrichFromGridConfiguration configuration = configuration();

        String previewGroup = "enrich-from-grid-preview-" + id();
        spark.sparkContext().setJobGroup(previewGroup, "enrich from grid preview", false);
        try {
            RecordingIssueSink previewIssues = new RecordingIssueSink();
            CanvasNodeOperationResult preview = new SpatialEnrichFromGridNodeOperator().apply(
                    new SpatialEnrichFromGridNodeDefinition(id(), "格网丰富", layout(), configuration),
                    Map.of("points", points, "grid", grid), context(previewIssues, true));
            assertFalse(previewIssues.hasErrors(), previewIssues::toString);
            preview.propagatedTables().get("enriched_points").dataset().queryExecution().analyzed();
            assertEquals(0, spark.sparkContext().statusTracker().getJobIdsForGroup(previewGroup).length);
        } finally {
            spark.sparkContext().clearJobGroup();
        }

        RecordingIssueSink executionIssues = new RecordingIssueSink();
        CanvasNodeOperationResult result = new SpatialEnrichFromGridNodeOperator().apply(
                new SpatialEnrichFromGridNodeDefinition(id(), "格网丰富", layout(), configuration),
                Map.of("points", points, "grid", grid), context(executionIssues, false));
        assertFalse(executionIssues.hasErrors(), executionIssues::toString);
        Dataset<Row> output = result.propagatedTables().get("enriched_points").dataset();
        Row summary = output.agg(
                functions.count(output.col("point_id")).alias("row_count"),
                functions.sum(output.col("grid_population")).alias("population_sum"))
                .first();
        assertEquals(20_000L, ((Number) summary.getAs("row_count")).longValue());
        assertEquals(199_990_000L, ((Number) summary.getAs("population_sum")).longValue());

        String plan = output.queryExecution().executedPlan().toString();
        String normalizedPlan = plan.toLowerCase(Locale.ROOT);
        assertTrue(normalizedPlan.contains("join"), plan);
        assertFalse(plan.contains("CollectLimit") || normalizedPlan.contains("collect_list"), plan);
        assertFalse(plan.contains("CartesianProduct") || plan.contains("BroadcastNestedLoopJoin"), plan);
    }

    private static SpatialEnrichFromGridConfiguration configuration() {
        return new SpatialEnrichFromGridConfiguration(
                "points", "shape", "grid", "shape", "bin_id",
                List.of(
                        new SpatialEnrichFromGridField("category", "grid_category"),
                        new SpatialEnrichFromGridField("population", "grid_population")),
                "enriched_points");
    }

    private SparkCanvasTable generatedPointTable(String name, long size) {
        Dataset<Row> data = spark.range(size).select(
                functions.col("id").alias("point_id"),
                st_functions.ST_SetSRID(st_constructors.ST_Point(
                        functions.pmod(functions.col("id"), functions.lit(200L))
                                .multiply(10d).plus(5d).cast("double"),
                        functions.floor(functions.col("id").divide(200d))
                                .multiply(10d).plus(5d)),
                        functions.lit(3857)).alias("shape"));
        return new SparkCanvasTable(new CanvasTableSchema(
                name, null, List.of(longColumn("point_id"), geometryColumn(GeometryKind.POINT)),
                CanvasDatasetKind.BOUNDED, null, null), data);
    }

    private SparkCanvasTable generatedGridTable(String name, long size) {
        var x = functions.pmod(functions.col("id"), functions.lit(200L)).multiply(10d);
        var y = functions.floor(functions.col("id").divide(200d)).multiply(10d);
        Dataset<Row> data = spark.range(size).select(
                functions.concat(functions.lit("cell_"), functions.col("id")).alias("bin_id"),
                functions.concat(functions.lit("category_"), functions.col("id")).alias("category"),
                functions.col("id").alias("population"),
                st_functions.ST_SetSRID(st_constructors.ST_PolygonFromEnvelope(
                        x, y, x.plus(10d), y.plus(10d)), functions.lit(3857)).alias("shape"));
        return new SparkCanvasTable(new CanvasTableSchema(
                name, null, List.of(stringColumn("bin_id"), stringColumn("category"),
                longColumn("population"), geometryColumn(GeometryKind.POLYGON)),
                CanvasDatasetKind.BOUNDED, null, null), data);
    }

    private SparkCanvasTable markInput(SparkCanvasTable table, String key) {
        var asset = new TaskLineageEvidence.Asset(
                key, TaskLineageEvidence.AssetRole.INPUT, TaskLineageEvidence.AssetKind.JDBC_TABLE,
                null, null, null, null, UUID.randomUUID(), null, null, key, null, null, key);
        var fields = new LinkedHashMap<String, CatalystLineageMetadata.InputField>();
        for (CanvasColumnSchema column : table.schema().columns()) {
            fields.put(column.name(), new CatalystLineageMetadata.InputField(
                    key + ":" + column.name(), null));
        }
        return new SparkCanvasTable(table.schema(), CatalystLineageMetadata.markInput(
                table.dataset(), key + "-node", asset, fields));
    }

    private TaskLineageEvidence.Flow analyzeLineage(SparkCanvasTable table) {
        var outputAsset = new TaskLineageEvidence.Asset(
                "output", TaskLineageEvidence.AssetRole.OUTPUT, TaskLineageEvidence.AssetKind.JDBC_TABLE,
                null, TaskLineageEvidence.WriteMode.APPEND, null, null, UUID.randomUUID(),
                null, null, "enriched_points", null, null, "enriched_points");
        var candidate = new CatalystLineageOutputCandidate(
                "flow", "output-node", "JDBC_OUTPUT", "write", table.dataset(), outputAsset,
                table.schema().columns().stream().map(column ->
                        new CatalystLineageOutputCandidate.TargetField(
                                "out:" + column.name(), null, column.name(), column.name(),
                                TaskLineageEvidence.OutputEffect.WRITTEN_UNKNOWN_SOURCE)).toList());
        return new CatalystLineageAnalyzer().analyze(List.of(candidate)).flows().getFirst();
    }

    private static void assertEdge(
            TaskLineageEvidence.Flow flow,
            String target,
            String sourceTable,
            String sourceColumn
    ) {
        assertTrue(flow.fieldEdges().stream().anyMatch(edge ->
                        edge.target().localFieldKey().equals("out:" + target)
                                && edge.source().localFieldKey().equals(
                                sourceTable + ":" + sourceColumn)),
                () -> sourceTable + "." + sourceColumn + " -> " + target
                        + " missing in " + flow.fieldEdges());
    }

    private SparkCanvasTable geometryTable(
            String rawName,
            String outputName,
            GeometryKind kind,
            List<CanvasColumnSchema> columns,
            List<Row> rows
    ) {
        SparkCanvasTable raw = table(rawName, columns, rows);
        RecordingIssueSink issues = new RecordingIssueSink();
        CanvasNodeOperationResult result = new GeometryConstructNodeOperator().apply(
                new GeometryConstructNodeDefinition(id(), "构造", layout(),
                        new GeometryConstructConfiguration(rawName, outputName, "shape",
                                new GeometryConstructSource.Wkt("wkt"),
                                new GeometryTypeDefinition(kind,
                                        new CrsReference("EPSG", 3857), CoordinateDimension.XY))),
                Map.of(rawName, raw), context(issues));
        assertFalse(issues.hasErrors(), issues::toString);
        return result.propagatedTables().get(outputName);
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

    private static CanvasColumnSchema longColumn(String name) {
        return column(name, PlatformDataType.LONG);
    }

    private static CanvasColumnSchema geometryColumn(GeometryKind kind) {
        return new CanvasColumnSchema(
                "shape", PlatformDataType.GEOMETRY, null, null, null, false,
                null, false, false, null,
                new GeometryTypeDefinition(kind,
                        new CrsReference("EPSG", 3857), CoordinateDimension.XY));
    }

    private static CanvasColumnSchema column(String name, PlatformDataType type) {
        return new CanvasColumnSchema(name, type, null, null, null, true,
                null, false, false, null, null);
    }

    private static CanvasNodeLayout layout() {
        return new CanvasNodeLayout(0d, 0d, 384d, 232d);
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
