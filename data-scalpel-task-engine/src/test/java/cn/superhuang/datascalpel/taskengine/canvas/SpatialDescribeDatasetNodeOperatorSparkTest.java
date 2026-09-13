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
import cn.superhuang.data.scalpel.contract.task.SpatialDescribeDatasetConfiguration;
import cn.superhuang.data.scalpel.contract.task.SpatialDescribeDatasetNodeDefinition;
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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.locationtech.jts.geom.Geometry;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SpatialDescribeDatasetNodeOperatorSparkTest {
    private SparkSession spark;

    @BeforeAll
    void startSpark() {
        spark = SedonaSparkSupport.initialize(SedonaSparkSupport.builder()
                .master("local[1]").appName("spatial-describe-dataset-test")
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
    void profilesFieldsDescriptionSampleAndExtentWithoutChangingInputOrder() {
        SparkCanvasTable source = sourceTable(List.of(
                RowFactory.create("alpha", 1d, 10, true, LocalDate.of(2024, 1, 1), "POINT (0 0)"),
                RowFactory.create("beta", 3d, null, false, LocalDate.of(2024, 1, 3), "POINT (4 5)"),
                RowFactory.create("gamma", null, 20, true, LocalDate.of(2024, 1, 2), null)
        ));
        RecordingIssueSink issues = new RecordingIssueSink();
        String group = UUID.randomUUID().toString();
        spark.sparkContext().setJobGroup(group, "describe dataset planning", false);
        CanvasNodeOperationResult result;
        try {
            result = operator(configuration(2, true), source, issues);
            result.propagatedTables().values().forEach(table -> table.dataset().queryExecution().analyzed());
            assertEquals(0, spark.sparkContext().statusTracker().getJobIdsForGroup(group).length);
        } finally {
            spark.sparkContext().clearJobGroup();
        }

        assertFalse(issues.hasErrors(), issues::toString);
        assertEquals(List.of("events", "field_statistics", "dataset_description", "sample_rows", "extent_polygon"),
                new ArrayList<>(result.propagatedTables().keySet()));

        List<Row> statistics = result.propagatedTables().get("field_statistics").dataset().collectAsList();
        assertEquals(List.of("id", "amount", "score", "active", "event_date"),
                statistics.stream().map(row -> row.<String>getAs("field_name")).toList());
        Row amount = field(statistics, "amount");
        assertEquals(2L, amount.<Number>getAs("non_null_count").longValue());
        assertEquals(1L, amount.<Number>getAs("null_count").longValue());
        assertEquals(4d, amount.<Number>getAs("numeric_sum").doubleValue(), 1e-12);
        assertEquals(2d, amount.<Number>getAs("numeric_mean").doubleValue(), 1e-12);
        assertEquals(1d, amount.<Number>getAs("numeric_standard_deviation").doubleValue(), 1e-12);
        assertEquals("alpha", field(statistics, "id").getAs("any_value"));
        assertEquals(172_800_000L,
                field(statistics, "event_date").<Number>getAs("temporal_range_millis").longValue());

        Row description = result.propagatedTables().get("dataset_description").dataset().head();
        assertEquals(3L, description.<Number>getAs("record_count").longValue());
        assertEquals(6, description.<Number>getAs("field_count").intValue());
        assertEquals(2L, description.<Number>getAs("geometry_non_empty_count").longValue());
        assertEquals(1L, description.<Number>getAs("geometry_null_or_empty_count").longValue());
        assertEquals(4d, description.<Number>getAs("extent_x_maximum").doubleValue(), 1e-12);
        assertEquals(5d, description.<Number>getAs("extent_y_maximum").doubleValue(), 1e-12);
        assertEquals("event_date", description.getAs("event_time_column_name"));
        assertTrue(description.<String>getAs("description_json").contains("\"record_count\":3"));

        SparkCanvasTable sample = result.propagatedTables().get("sample_rows");
        assertEquals(2, sample.dataset().collectAsList().size());
        assertEquals("event_date", sample.schema().eventTimeColumn());
        assertEquals(source.schema().columns(), sample.schema().columns());

        Row extent = result.propagatedTables().get("extent_polygon").dataset().head();
        Geometry polygon = extent.getAs("geometry");
        assertEquals(0d, polygon.getEnvelopeInternal().getMinX(), 1e-12);
        assertEquals(4d, polygon.getEnvelopeInternal().getMaxX(), 1e-12);
        assertEquals(0d, polygon.getEnvelopeInternal().getMinY(), 1e-12);
        assertEquals(5d, polygon.getEnvelopeInternal().getMaxY(), 1e-12);
    }

    @Test
    void emptySourceStillDescribesEveryScalarFieldAndProducesNoExtent() {
        SparkCanvasTable source = sourceTable(List.of());
        RecordingIssueSink issues = new RecordingIssueSink();

        CanvasNodeOperationResult result = operator(configuration(0, true), source, issues);

        assertFalse(issues.hasErrors(), issues::toString);
        List<Row> statistics = result.propagatedTables().get("field_statistics").dataset().collectAsList();
        assertEquals(5, statistics.size());
        assertTrue(statistics.stream().allMatch(row -> row.<Number>getAs("non_null_count").longValue() == 0L));
        assertTrue(statistics.stream().allMatch(row -> row.<Number>getAs("null_count").longValue() == 0L));
        assertNull(field(statistics, "amount").getAs("numeric_mean"));
        Row description = result.propagatedTables().get("dataset_description").dataset().head();
        assertEquals(0L, description.<Number>getAs("record_count").longValue());
        assertNull(description.getAs("extent_x_minimum"));
        assertTrue(result.propagatedTables().get("extent_polygon").dataset().collectAsList().isEmpty());
    }

    @Test
    void rejectsExtentWithoutGeometryAndCaseInsensitiveResultNameConflicts() {
        SparkCanvasTable source = sourceTable(List.of());
        SpatialDescribeDatasetConfiguration configuration = new SpatialDescribeDatasetConfiguration(
                "events", "", "EVENTS", "description", 0, "", true, "extent");
        RecordingIssueSink issues = new RecordingIssueSink();

        CanvasNodeOperationResult result = operator(configuration, source, issues);

        assertTrue(issues.hasErrors());
        assertTrue(issues.toString().contains("DESCRIBE_DATASET_EXTENT_GEOMETRY_REQUIRED"));
        assertTrue(issues.toString().contains("DUPLICATE_TABLE_NAME"));
        assertTrue(result.propagatedTables().isEmpty());
    }

    @Test
    void previewHasCompleteLineageForAllFourResultsWithoutSubmittingSparkJobs() {
        SparkCanvasTable source = markInput(sourceTable(List.of()), "events");
        SpatialDescribeDatasetConfiguration configuration = configuration(100, true);
        String jobGroup = "describe-dataset-preview-" + UUID.randomUUID();
        spark.sparkContext().setJobGroup(jobGroup, "describe dataset preview", false);
        Map<String, TaskLineageEvidence.Flow> flows = new LinkedHashMap<>();
        try {
            RecordingIssueSink issues = new RecordingIssueSink();
            CanvasNodeOperationResult result = new SpatialDescribeDatasetNodeOperator().apply(
                    new SpatialDescribeDatasetNodeDefinition(
                            UUID.randomUUID().toString(), "描述数据集",
                            new CanvasNodeLayout(0d, 0d, 376d, 232d), configuration),
                    new LinkedHashMap<>(Map.of("events", source)), previewContext(issues));
            assertFalse(issues.hasErrors(), issues::toString);
            for (String tableName : List.of("field_statistics", "dataset_description",
                    "sample_rows", "extent_polygon")) {
                flows.put(tableName, analyzeLineage(
                        result.propagatedTables().get(tableName), tableName));
            }
            assertEquals(0, spark.sparkContext().statusTracker()
                    .getJobIdsForGroup(jobGroup).length);
        } finally {
            spark.sparkContext().clearJobGroup();
        }

        flows.values().forEach(SpatialDescribeDatasetNodeOperatorSparkTest::assertComplete);
        TaskLineageEvidence.Flow statistics = flows.get("field_statistics");
        for (String output : List.of("field_name", "field_type", "non_null_count", "null_count",
                "numeric_mean", "temporal_range_millis")) {
            assertEdge(statistics, output, "events", "amount");
            assertEdge(statistics, output, "events", "event_date");
        }
        TaskLineageEvidence.Flow description = flows.get("dataset_description");
        for (String output : List.of("dataset_name", "record_count", "field_count",
                "geometry_non_empty_count", "event_time_minimum", "description_json")) {
            assertEdge(description, output, "events", "id");
            assertEdge(description, output, "events", "shape");
        }
        TaskLineageEvidence.Flow sample = flows.get("sample_rows");
        for (CanvasColumnSchema column : source.schema().columns()) {
            assertEdge(sample, column.name(), "events", column.name());
        }
        assertEdge(flows.get("extent_polygon"), "geometry", "events", "shape");
    }

    @Test
    void profilesTwentyThousandRowsAndKeepsMaximumSampleBounded() {
        Dataset<Row> dataset = spark.range(20_000).select(
                functions.concat(functions.lit("E"), functions.col("id")).alias("id"),
                functions.col("id").cast("double").alias("amount"),
                functions.pmod(functions.col("id"), functions.lit(100)).cast("integer").alias("score"),
                functions.pmod(functions.col("id"), functions.lit(2)).equalTo(0).alias("active"),
                functions.expr("date_add(DATE '2024-01-01', CAST(pmod(id, 365) AS INT))")
                        .alias("event_date"),
                st_constructors.ST_Point(
                        functions.pmod(functions.col("id"), functions.lit(1_000)).cast("double"),
                        functions.floor(functions.col("id").divide(1_000d))).alias("shape"));
        SparkCanvasTable source = new SparkCanvasTable(new CanvasTableSchema(
                "events", null, sourceColumns(), CanvasDatasetKind.BOUNDED, "event_date", null), dataset);
        RecordingIssueSink issues = new RecordingIssueSink();

        CanvasNodeOperationResult result = operator(configuration(10_000, true), source, issues);

        assertFalse(issues.hasErrors(), issues::toString);
        List<Row> statistics = result.propagatedTables().get("field_statistics")
                .dataset().collectAsList();
        assertEquals(5, statistics.size());
        assertEquals(20_000L, field(statistics, "amount")
                .<Number>getAs("non_null_count").longValue());
        assertEquals(20_000L, result.propagatedTables().get("dataset_description")
                .dataset().head().<Number>getAs("record_count").longValue());
        assertEquals(10_000L, result.propagatedTables().get("sample_rows")
                .dataset().count());
        assertEquals(1L, result.propagatedTables().get("extent_polygon")
                .dataset().count());
    }

    private CanvasNodeOperationResult operator(
            SpatialDescribeDatasetConfiguration configuration,
            SparkCanvasTable source,
            RecordingIssueSink issues
    ) {
        return new SpatialDescribeDatasetNodeOperator().apply(
                new SpatialDescribeDatasetNodeDefinition(UUID.randomUUID().toString(), "描述数据集",
                        new CanvasNodeLayout(0d, 0d, 376d, 232d), configuration),
                new LinkedHashMap<>(Map.of("events", source)), runtimeContext(issues));
    }

    private static SpatialDescribeDatasetConfiguration configuration(int sampleSize, boolean extent) {
        return new SpatialDescribeDatasetConfiguration(
                "events", "shape", "field_statistics", "dataset_description",
                sampleSize, "sample_rows", extent, "extent_polygon");
    }

    private SparkCanvasTable sourceTable(List<Row> rows) {
        List<CanvasColumnSchema> rawColumns = List.of(
                column("id", PlatformDataType.STRING),
                column("amount", PlatformDataType.DOUBLE),
                column("score", PlatformDataType.INTEGER),
                column("active", PlatformDataType.BOOLEAN),
                column("event_date", PlatformDataType.DATE),
                column("wkt", PlatformDataType.STRING)
        );
        CanvasTableSchema rawSchema = new CanvasTableSchema("raw_events", null, rawColumns);
        SparkCanvasTable raw = new SparkCanvasTable(rawSchema,
                spark.createDataFrame(rows, SparkTypeMapper.toStructType(rawColumns)));
        RecordingIssueSink issues = new RecordingIssueSink();
        CanvasNodeOperationResult constructed = new GeometryConstructNodeOperator().apply(
                new GeometryConstructNodeDefinition(UUID.randomUUID().toString(), "构造 Geometry",
                        new CanvasNodeLayout(0d, 0d, 320d, 180d),
                        new GeometryConstructConfiguration("raw_events", "constructed_events", "shape",
                                new GeometryConstructSource.Wkt("wkt"), geometryType())),
                Map.of("raw_events", raw), previewContext(issues));
        assertFalse(issues.hasErrors(), issues::toString);
        Dataset<Row> dataset = constructed.propagatedTables().get("constructed_events").dataset()
                .select("id", "amount", "score", "active", "event_date", "shape");
        List<CanvasColumnSchema> columns = sourceColumns();
        return new SparkCanvasTable(new CanvasTableSchema(
                "events", null, columns, CanvasDatasetKind.BOUNDED, "event_date", null), dataset);
    }

    private CanvasNodeOperationContext previewContext(RecordingIssueSink issues) {
        return new CanvasNodeOperationContext(spark,
                MetadataIndex.create(new MetadataSnapshot(List.of(), List.of())),
                issues, new SchemaOnlyCanvasNodeDataAccess(spark));
    }

    private CanvasNodeOperationContext runtimeContext(RecordingIssueSink issues) {
        return new CanvasNodeOperationContext(spark,
                MetadataIndex.create(new MetadataSnapshot(List.of(), List.of())),
                issues, new SchemaOnlyCanvasNodeDataAccess(spark), CanvasExecutionMode.BATCH,
                CanvasRuntimeValues.execution(UUID.randomUUID(), Instant.now()));
    }

    private static List<CanvasColumnSchema> sourceColumns() {
        return List.of(
                column("id", PlatformDataType.STRING),
                column("amount", PlatformDataType.DOUBLE),
                column("score", PlatformDataType.INTEGER),
                column("active", PlatformDataType.BOOLEAN),
                column("event_date", PlatformDataType.DATE),
                new CanvasColumnSchema("shape", PlatformDataType.GEOMETRY, null, null, null,
                        true, null, false, false, null, geometryType())
        );
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

    private TaskLineageEvidence.Flow analyzeLineage(SparkCanvasTable table, String tableName) {
        var outputAsset = new TaskLineageEvidence.Asset(
                "output", TaskLineageEvidence.AssetRole.OUTPUT, TaskLineageEvidence.AssetKind.JDBC_TABLE,
                null, TaskLineageEvidence.WriteMode.APPEND, null, null, UUID.randomUUID(),
                null, null, tableName, null, null, tableName);
        var candidate = new CatalystLineageOutputCandidate(
                "flow", "output-node", "JDBC_OUTPUT", "write", table.dataset(), outputAsset,
                table.schema().columns().stream().map(column ->
                        new CatalystLineageOutputCandidate.TargetField(
                                "out:" + column.name(), null, column.name(), column.name(),
                                TaskLineageEvidence.OutputEffect.WRITTEN_UNKNOWN_SOURCE)).toList());
        return new CatalystLineageAnalyzer().analyze(List.of(candidate)).flows().getFirst();
    }

    private static void assertComplete(TaskLineageEvidence.Flow flow) {
        assertEquals(TaskLineageEvidence.Coverage.FIELD_COMPLETE, flow.coverage(),
                () -> flow.warnings().toString());
        assertTrue(flow.fields().stream().noneMatch(field ->
                        field.outputEffect()
                                == TaskLineageEvidence.OutputEffect.WRITTEN_UNKNOWN_SOURCE),
                () -> flow.fields().toString());
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

    private static GeometryTypeDefinition geometryType() {
        return new GeometryTypeDefinition(GeometryKind.POINT,
                new CrsReference("EPSG", 3857), CoordinateDimension.XY);
    }

    private static CanvasColumnSchema column(String name, PlatformDataType type) {
        return new CanvasColumnSchema(name, type, null, null, null, true,
                null, false, false, null, null);
    }

    private static Row field(List<Row> rows, String name) {
        return rows.stream().filter(row -> name.equals(row.getAs("field_name"))).findFirst().orElseThrow();
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
