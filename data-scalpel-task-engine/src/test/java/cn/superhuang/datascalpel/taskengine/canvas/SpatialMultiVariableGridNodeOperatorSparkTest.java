package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasDatasetKind;
import cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode;
import cn.superhuang.data.scalpel.contract.task.CanvasFieldPredicate;
import cn.superhuang.data.scalpel.contract.task.CanvasLiteral;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeLayout;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.FilterOperator;
import cn.superhuang.data.scalpel.contract.task.GeometryConstructConfiguration;
import cn.superhuang.data.scalpel.contract.task.GeometryConstructNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.GeometryConstructSource;
import cn.superhuang.data.scalpel.contract.task.MetadataSnapshot;
import cn.superhuang.data.scalpel.contract.task.SpatialDensityBinShape;
import cn.superhuang.data.scalpel.contract.task.SpatialDistanceUnit;
import cn.superhuang.data.scalpel.contract.task.SpatialMultiVariableGridConfiguration;
import cn.superhuang.data.scalpel.contract.task.SpatialMultiVariableGridNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.SpatialMultiVariableGridStatisticKind;
import cn.superhuang.data.scalpel.contract.task.SpatialMultiVariableGridVariable;
import cn.superhuang.data.scalpel.contract.task.SpatialMultiVariableGridVariableKind;
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
class SpatialMultiVariableGridNodeOperatorSparkTest {
    private SparkSession spark;

    @BeforeAll
    void startSpark() {
        spark = SedonaSparkSupport.initialize(SedonaSparkSupport.builder()
                .master("local[1]").appName("spatial-multi-variable-grid-test")
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
    void calculatesIndependentVariablesAcrossSourcesAndPreservesInputTables() {
        SparkCanvasTable facilities = geometryTable(
                "facilities_raw", "facilities",
                List.of(stringColumn("label"), stringColumn("status"), stringColumn("wkt")),
                List.of(
                        RowFactory.create("A", "active", "POINT (2 2)"),
                        RowFactory.create("B", "active", "POINT (22 2)"),
                        RowFactory.create("C", "inactive", "POINT (42 2)")));
        SparkCanvasTable events = geometryTable(
                "events_raw", "events",
                List.of(doubleColumn("amount"), stringColumn("wkt")),
                List.of(
                        RowFactory.create(2d, "POINT (2 2)"),
                        RowFactory.create(3d, "POINT (3 3)"),
                        RowFactory.create(7d, "POINT (22 2)")));
        List<SpatialMultiVariableGridVariable> variables = List.of(
                variable("facilities", SpatialMultiVariableGridVariableKind.DISTANCE_TO_NEAREST,
                        null, null, null, 10d, null, "nearest_distance"),
                variable("facilities", SpatialMultiVariableGridVariableKind.ATTRIBUTE_OF_NEAREST,
                        "label", null, null, 10d, null, "nearest_label"),
                variable("events", SpatialMultiVariableGridVariableKind.ATTRIBUTE_SUMMARY_OF_RELATED,
                        null, SpatialMultiVariableGridStatisticKind.COUNT, null,
                        null, null, "event_count"),
                variable("events", SpatialMultiVariableGridVariableKind.ATTRIBUTE_SUMMARY_OF_RELATED,
                        null, SpatialMultiVariableGridStatisticKind.SUM, "amount",
                        null, null, "amount_sum"),
                variable("facilities", SpatialMultiVariableGridVariableKind.ATTRIBUTE_SUMMARY_OF_RELATED,
                        null, SpatialMultiVariableGridStatisticKind.COUNT, null,
                        null, activeFilter(), "active_count"),
                variable("facilities", SpatialMultiVariableGridVariableKind.DISTANCE_TO_NEAREST,
                        null, null, null, 1d, null, "too_far_distance"));
        SpatialMultiVariableGridConfiguration configuration = new SpatialMultiVariableGridConfiguration(
                variables, SpatialDensityBinShape.SQUARE, 10d, SpatialDistanceUnit.METERS,
                "grid", "bin_id", "bin_geometry");
        Map<String, SparkCanvasTable> inputs = new LinkedHashMap<>();
        inputs.put("facilities", facilities);
        inputs.put("events", events);
        RecordingIssueSink issues = new RecordingIssueSink();

        CanvasNodeOperationResult result = new SpatialMultiVariableGridNodeOperator().apply(
                new SpatialMultiVariableGridNodeDefinition(id(), "多变量格网", layout(), configuration),
                inputs, context(issues));

        assertFalse(issues.hasErrors(), issues::toString);
        assertEquals(List.of("facilities", "events", "grid"),
                new ArrayList<>(result.propagatedTables().keySet()));
        SparkCanvasTable grid = result.propagatedTables().get("grid");
        List<Row> rows = grid.dataset().orderBy("bin_id").collectAsList();
        assertEquals(5, rows.size());
        Row first = rows.getFirst();
        assertEquals("SQUARE:0:0", first.getAs("bin_id"));
        assertEquals(Math.sqrt(18d), ((Number) first.getAs("nearest_distance")).doubleValue(), 1e-9);
        assertEquals("A", first.getAs("nearest_label"));
        assertEquals(2L, ((Number) first.getAs("event_count")).longValue());
        assertEquals(5d, ((Number) first.getAs("amount_sum")).doubleValue(), 1e-9);
        assertEquals(1L, ((Number) first.getAs("active_count")).longValue());
        assertNull(first.getAs("too_far_distance"));
        Row empty = rows.get(1);
        assertEquals(0L, ((Number) empty.getAs("event_count")).longValue());
        assertNull(empty.getAs("amount_sum"));
        Row last = rows.getLast();
        assertEquals("SQUARE:4:0", last.getAs("bin_id"));
        assertEquals("C", last.getAs("nearest_label"));
        assertEquals(0L, ((Number) last.getAs("active_count")).longValue());
        assertEquals(GeometryKind.POLYGON, grid.schema().columns().get(1).geometry().kind());
        assertEquals(PlatformDataType.LONG, grid.schema().columns().get(4).fieldType());
        assertEquals(PlatformDataType.DOUBLE, grid.schema().columns().get(5).fieldType());
    }

    @Test
    void allVariableKindsAndStatisticsHaveCompleteAndTruthfulFieldLineage() {
        SparkCanvasTable facilities = markInput(generatedPointTable("facilities", 0), "facilities");
        SparkCanvasTable events = markInput(generatedPointTable("events", 0), "events");
        List<SpatialMultiVariableGridVariable> variables = new ArrayList<>();
        variables.add(variable("facilities", SpatialMultiVariableGridVariableKind.DISTANCE_TO_NEAREST,
                null, null, null, 10d, null, "nearest_distance"));
        variables.add(variable("facilities", SpatialMultiVariableGridVariableKind.ATTRIBUTE_OF_NEAREST,
                "label", null, null, 10d, null, "nearest_label"));
        variables.add(variable("events", SpatialMultiVariableGridVariableKind.ATTRIBUTE_SUMMARY_OF_RELATED,
                null, SpatialMultiVariableGridStatisticKind.COUNT, null,
                null, null, "event_count"));
        for (SpatialMultiVariableGridStatisticKind statistic : List.of(
                SpatialMultiVariableGridStatisticKind.SUM,
                SpatialMultiVariableGridStatisticKind.MEAN,
                SpatialMultiVariableGridStatisticKind.MIN,
                SpatialMultiVariableGridStatisticKind.MAX,
                SpatialMultiVariableGridStatisticKind.RANGE,
                SpatialMultiVariableGridStatisticKind.STDDEV,
                SpatialMultiVariableGridStatisticKind.VARIANCE)) {
            variables.add(variable("events", SpatialMultiVariableGridVariableKind.ATTRIBUTE_SUMMARY_OF_RELATED,
                    null, statistic, "amount", null, null,
                    "amount_" + statistic.name().toLowerCase(Locale.ROOT)));
        }
        variables.add(variable("facilities", SpatialMultiVariableGridVariableKind.ATTRIBUTE_SUMMARY_OF_RELATED,
                null, SpatialMultiVariableGridStatisticKind.ANY, "label",
                null, null, "any_label"));
        SpatialMultiVariableGridConfiguration configuration = configuration(variables);
        Map<String, SparkCanvasTable> inputs = new LinkedHashMap<>();
        inputs.put("facilities", facilities);
        inputs.put("events", events);
        RecordingIssueSink issues = new RecordingIssueSink();

        CanvasNodeOperationResult result = new SpatialMultiVariableGridNodeOperator().apply(
                new SpatialMultiVariableGridNodeDefinition(id(), "多变量格网", layout(), configuration),
                inputs, context(issues));

        assertFalse(issues.hasErrors(), issues::toString);
        SparkCanvasTable grid = result.propagatedTables().get("grid");
        TaskLineageEvidence.Flow flow = analyzeLineage(grid);
        assertEquals(TaskLineageEvidence.Coverage.FIELD_COMPLETE, flow.coverage(),
                () -> flow.warnings().toString());
        assertTrue(flow.fields().stream().noneMatch(field ->
                field.outputEffect() == TaskLineageEvidence.OutputEffect.WRITTEN_UNKNOWN_SOURCE),
                () -> flow.fields().toString());
        assertEdge(flow, "bin_id", "facilities", "shape");
        assertEdge(flow, "bin_geometry", "events", "shape");
        assertEdge(flow, "nearest_distance", "facilities", "shape");
        assertEdge(flow, "nearest_label", "facilities", "label");
        assertEdge(flow, "event_count", "events", "shape");
        for (SpatialMultiVariableGridStatisticKind statistic : List.of(
                SpatialMultiVariableGridStatisticKind.SUM,
                SpatialMultiVariableGridStatisticKind.MEAN,
                SpatialMultiVariableGridStatisticKind.MIN,
                SpatialMultiVariableGridStatisticKind.MAX,
                SpatialMultiVariableGridStatisticKind.RANGE,
                SpatialMultiVariableGridStatisticKind.STDDEV,
                SpatialMultiVariableGridStatisticKind.VARIANCE)) {
            assertEdge(flow, "amount_" + statistic.name().toLowerCase(Locale.ROOT), "events", "amount");
        }
        assertEdge(flow, "any_label", "facilities", "label");
    }

    @Test
    void twentyThousandPointPreviewIsLazyAndVariablesExecuteWithoutDriverCollection() {
        SparkCanvasTable points = generatedPointTable("points", 20_000);
        List<SpatialMultiVariableGridVariable> variables = List.of(
                variable("points", SpatialMultiVariableGridVariableKind.DISTANCE_TO_NEAREST,
                        null, null, null, 8d, null, "nearest_distance"),
                variable("points", SpatialMultiVariableGridVariableKind.ATTRIBUTE_OF_NEAREST,
                        "id", null, null, 8d, null, "nearest_id"),
                variable("points", SpatialMultiVariableGridVariableKind.ATTRIBUTE_SUMMARY_OF_RELATED,
                        null, SpatialMultiVariableGridStatisticKind.COUNT, null,
                        null, null, "point_count"),
                variable("points", SpatialMultiVariableGridVariableKind.ATTRIBUTE_SUMMARY_OF_RELATED,
                        null, SpatialMultiVariableGridStatisticKind.SUM, "amount",
                        null, null, "amount_sum"));
        SpatialMultiVariableGridConfiguration configuration = configuration(variables);

        String previewGroup = "multi-variable-grid-preview-" + id();
        spark.sparkContext().setJobGroup(previewGroup, "multi variable grid preview", false);
        try {
            RecordingIssueSink previewIssues = new RecordingIssueSink();
            CanvasNodeOperationResult preview = new SpatialMultiVariableGridNodeOperator().apply(
                    new SpatialMultiVariableGridNodeDefinition(id(), "多变量格网", layout(), configuration),
                    Map.of("points", points), context(previewIssues, true));
            assertFalse(previewIssues.hasErrors(), previewIssues::toString);
            preview.propagatedTables().get("grid").dataset().queryExecution().analyzed();
            assertEquals(0, spark.sparkContext().statusTracker().getJobIdsForGroup(previewGroup).length);
        } finally {
            spark.sparkContext().clearJobGroup();
        }

        RecordingIssueSink executionIssues = new RecordingIssueSink();
        CanvasNodeOperationResult result = new SpatialMultiVariableGridNodeOperator().apply(
                new SpatialMultiVariableGridNodeDefinition(id(), "多变量格网", layout(), configuration),
                Map.of("points", points), context(executionIssues, false));
        assertFalse(executionIssues.hasErrors(), executionIssues::toString);
        Dataset<Row> grid = result.propagatedTables().get("grid").dataset();
        assertEquals(20_000L, grid.count());

        String plan = grid.queryExecution().executedPlan().toString();
        String normalizedPlan = plan.toLowerCase(Locale.ROOT);
        assertTrue(normalizedPlan.contains("aggregate"), plan);
        assertTrue(normalizedPlan.contains("generate"), plan);
        assertFalse(plan.contains("CollectLimit") || normalizedPlan.contains("collect_list"), plan);
        assertFalse(plan.contains("CartesianProduct") || plan.contains("BroadcastNestedLoopJoin"), plan);
    }

    private SpatialMultiVariableGridConfiguration configuration(
            List<SpatialMultiVariableGridVariable> variables
    ) {
        return new SpatialMultiVariableGridConfiguration(
                variables, SpatialDensityBinShape.SQUARE, 10d, SpatialDistanceUnit.METERS,
                "grid", "bin_id", "bin_geometry");
    }

    private SparkCanvasTable generatedPointTable(String name, long size) {
        Dataset<Row> data = spark.range(size).select(
                functions.col("id"),
                functions.concat(functions.lit("L"), functions.col("id")).alias("label"),
                functions.col("id").plus(1d).cast("double").alias("amount"),
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
                stringColumn("label"),
                doubleColumn("amount"),
                geometry);
        return new SparkCanvasTable(new CanvasTableSchema(
                name, null, columns, CanvasDatasetKind.BOUNDED, null, null), data);
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
                null, null, "grid", null, null, "grid");
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

    private CanvasFieldPredicate activeFilter() {
        return new CanvasFieldPredicate("status", FilterOperator.EQUALS,
                List.of(new CanvasLiteral(PlatformDataType.STRING, "active")));
    }

    private SpatialMultiVariableGridVariable variable(
            String sourceTable,
            SpatialMultiVariableGridVariableKind kind,
            String attribute,
            SpatialMultiVariableGridStatisticKind statistic,
            String statisticColumn,
            Double distance,
            CanvasFieldPredicate filter,
            String outputColumn
    ) {
        return new SpatialMultiVariableGridVariable(
                id(), sourceTable, "shape", kind, attribute, statistic, statisticColumn,
                distance, distance == null ? null : SpatialDistanceUnit.METERS, filter, outputColumn);
    }

    private SparkCanvasTable geometryTable(
            String rawName,
            String outputName,
            List<CanvasColumnSchema> columns,
            List<Row> rows
    ) {
        SparkCanvasTable raw = table(rawName, columns, rows);
        RecordingIssueSink issues = new RecordingIssueSink();
        CanvasNodeOperationResult result = new GeometryConstructNodeOperator().apply(
                new GeometryConstructNodeDefinition(id(), "构造", layout(),
                        new GeometryConstructConfiguration(rawName, outputName, "shape",
                                new GeometryConstructSource.Wkt("wkt"),
                                new GeometryTypeDefinition(GeometryKind.POINT,
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

    private static CanvasColumnSchema doubleColumn(String name) {
        return column(name, PlatformDataType.DOUBLE);
    }

    private static CanvasColumnSchema column(String name, PlatformDataType type) {
        return new CanvasColumnSchema(name, type, null, null, null, true,
                null, false, false, null, null);
    }

    private static CanvasNodeLayout layout() {
        return new CanvasNodeLayout(0d, 0d, 384d, 244d);
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
