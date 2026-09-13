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
import cn.superhuang.data.scalpel.contract.task.SpatialDistanceMethod;
import cn.superhuang.data.scalpel.contract.task.SpatialDistanceUnit;
import cn.superhuang.data.scalpel.contract.task.SpatialGroupByProximityTemporalUnit;
import cn.superhuang.data.scalpel.contract.task.TraceProximityEntityOfInterest;
import cn.superhuang.data.scalpel.contract.task.TraceProximityEventsConfiguration;
import cn.superhuang.data.scalpel.contract.task.TraceProximityEventsNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.TraceProximityInterestSource;
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

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TraceProximityEventsNodeOperatorSparkTest {
    private SparkSession spark;

    @BeforeAll
    void startSpark() {
        spark = SedonaSparkSupport.initialize(SedonaSparkSupport.builder()
                .master("local[1]").appName("trace-proximity-events-test")
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
    void tracesFirstContactsByDepthAndOutputsTracksWithoutInvalidObservations() {
        List<CanvasColumnSchema> columns = List.of(
                stringColumn("device_id"), stringColumn("region"),
                timestampColumn("observed_at"), stringColumn("wkt"));
        SparkCanvasTable raw = table("raw_observations", columns, List.of(
                row("A", "north", "2026-01-01 00:00:00", "POINT (0 0)"),
                row("A", "north", "2026-01-01 00:30:00", "POINT (50 50)"),
                row("B", "north", "2026-01-01 00:05:00", "POINT (0.5 0)"),
                row("B", "north", "2026-01-01 00:06:00", "POINT (0.6 0)"),
                row("B", "north", "2026-01-01 00:20:00", "POINT (20 20)"),
                row("C", "north", "2026-01-01 00:10:00", "POINT (1 0)"),
                row("C", "north", "2026-01-01 00:12:00", "POINT (10 10)"),
                row("D", "south", "2026-01-01 00:05:00", "POINT (0.3 0)"),
                row("E", "north", "2026-01-01 00:05:00", null),
                row("F", "north", null, "POINT (0.2 0)"),
                row(null, "north", "2026-01-01 00:05:00", "POINT (0.2 0)")));
        RecordingIssueSink constructIssues = new RecordingIssueSink();
        SparkCanvasTable observations = new GeometryConstructNodeOperator().apply(
                new GeometryConstructNodeDefinition(id(), "构造", layout(),
                        new GeometryConstructConfiguration(
                                "raw_observations", "observations", "shape",
                                new GeometryConstructSource.Wkt("wkt"),
                                new GeometryTypeDefinition(
                                        GeometryKind.POINT, new CrsReference("EPSG", 3857),
                                        CoordinateDimension.XY))),
                Map.of("raw_observations", raw), previewContext(constructIssues))
                .propagatedTables().get("observations");
        assertFalse(constructIssues.hasErrors(), constructIssues::toString);

        TraceProximityEventsConfiguration configuration = new TraceProximityEventsConfiguration(
                "observations", "shape", "device_id", "observed_at",
                SpatialDistanceMethod.PLANAR, 1.1d, SpatialDistanceUnit.SOURCE_CRS_UNIT,
                6L, SpatialGroupByProximityTemporalUnit.MINUTES,
                TraceProximityInterestSource.ENTITY_IDS,
                List.of(new TraceProximityEntityOfInterest("A", null)),
                "", "", null, 3, List.of("region"), true,
                "trace_events", "trace_tracks", "trace_from_id", "trace_to_id",
                "trace_depth", "trace_duration_minutes", "trace_event_time");
        RecordingIssueSink issues = new RecordingIssueSink();
        Map<String, SparkCanvasTable> inputs = new LinkedHashMap<>();
        inputs.put("observations", observations);

        CanvasNodeOperationResult result = new TraceProximityEventsNodeOperator().apply(
                new TraceProximityEventsNodeDefinition(
                        id(), "邻近事件追踪", layout(), configuration),
                inputs, runtimeContext(issues));

        assertFalse(issues.hasErrors(), issues::toString);
        assertEquals(List.of("observations", "trace_events", "trace_tracks"),
                new ArrayList<>(result.propagatedTables().keySet()));

        List<Row> eventRows = result.propagatedTables().get("trace_events")
                .dataset().orderBy("trace_depth").collectAsList();
        assertEquals(2, eventRows.size());
        assertEquals("A", eventRows.getFirst().getAs("trace_from_id"));
        assertEquals("B", eventRows.getFirst().getAs("trace_to_id"));
        assertEquals(1L, ((Number) eventRows.getFirst().getAs("trace_depth")).longValue());
        assertEquals(1d, ((Number) eventRows.getFirst()
                .getAs("trace_duration_minutes")).doubleValue());
        assertEquals(Timestamp.valueOf("2026-01-01 00:05:00"),
                eventRows.getFirst().getAs("trace_event_time"));
        assertEquals("B", eventRows.getLast().getAs("trace_from_id"));
        assertEquals("C", eventRows.getLast().getAs("trace_to_id"));
        assertEquals(2L, ((Number) eventRows.getLast().getAs("trace_depth")).longValue());

        List<Row> trackRows = result.propagatedTables().get("trace_tracks")
                .dataset().orderBy("device_id", "observed_at").collectAsList();
        Map<String, List<Row>> tracks = trackRows.stream().collect(Collectors.groupingBy(
                row -> row.getAs("device_id"), LinkedHashMap::new, Collectors.toList()));
        assertEquals(List.of("A", "B", "C"), new ArrayList<>(tracks.keySet()));
        assertEquals(2, tracks.get("A").size());
        assertEquals(3, tracks.get("B").size());
        assertEquals(2, tracks.get("C").size());
        assertEquals(0L, depth(tracks.get("A").getFirst()));
        assertEquals(1L, depth(tracks.get("B").getFirst()));
        assertEquals(2L, depth(tracks.get("C").getFirst()));
    }

    @Test
    void readsEntitiesOfInterestFromAnUpstreamTableAndUsesTheirEarliestStartTime() {
        List<CanvasColumnSchema> observationColumns = List.of(
                stringColumn("device_id"), timestampColumn("observed_at"), stringColumn("wkt"));
        SparkCanvasTable raw = table("raw_table_interest_observations", observationColumns, List.of(
                RowFactory.create("A", Timestamp.valueOf("2026-01-01 00:00:00"), "POINT (0 0)"),
                RowFactory.create("B", Timestamp.valueOf("2026-01-01 00:05:00"), "POINT (0.5 0)")));
        RecordingIssueSink constructIssues = new RecordingIssueSink();
        SparkCanvasTable observations = new GeometryConstructNodeOperator().apply(
                new GeometryConstructNodeDefinition(id(), "构造", layout(),
                        new GeometryConstructConfiguration(
                                "raw_table_interest_observations", "table_interest_observations", "shape",
                                new GeometryConstructSource.Wkt("wkt"),
                                new GeometryTypeDefinition(
                                        GeometryKind.POINT, new CrsReference("EPSG", 3857),
                                        CoordinateDimension.XY))),
                Map.of("raw_table_interest_observations", raw), previewContext(constructIssues))
                .propagatedTables().get("table_interest_observations");
        assertFalse(constructIssues.hasErrors(), constructIssues::toString);

        SparkCanvasTable interests = table("trace_interests",
                List.of(stringColumn("interest_id"), timestampColumn("start_at")), List.of(
                        RowFactory.create("A", Timestamp.valueOf("2026-01-01 00:04:00")),
                        RowFactory.create("A", Timestamp.valueOf("2026-01-01 00:10:00")),
                        RowFactory.create(null, Timestamp.valueOf("2026-01-01 00:00:00"))));
        TraceProximityEventsConfiguration configuration = new TraceProximityEventsConfiguration(
                "table_interest_observations", "shape", "device_id", "observed_at",
                SpatialDistanceMethod.PLANAR, 1.1d, SpatialDistanceUnit.SOURCE_CRS_UNIT,
                6L, SpatialGroupByProximityTemporalUnit.MINUTES,
                TraceProximityInterestSource.TABLE, List.of(),
                "trace_interests", "interest_id", "start_at", 1, List.of(), false,
                "table_interest_events", "unused_tracks", "trace_from_id", "trace_to_id",
                "trace_depth", "trace_duration_minutes", "trace_event_time");
        Map<String, SparkCanvasTable> inputs = new LinkedHashMap<>();
        inputs.put("table_interest_observations", observations);
        inputs.put("trace_interests", interests);
        RecordingIssueSink issues = new RecordingIssueSink();

        CanvasNodeOperationResult result = new TraceProximityEventsNodeOperator().apply(
                new TraceProximityEventsNodeDefinition(id(), "上游表起始实体", layout(), configuration),
                inputs, runtimeContext(issues));

        assertFalse(issues.hasErrors(), issues::toString);
        assertEquals(List.of("table_interest_observations", "trace_interests", "table_interest_events"),
                new ArrayList<>(result.propagatedTables().keySet()));
        List<Row> events = result.propagatedTables().get("table_interest_events")
                .dataset().collectAsList();
        assertEquals(1, events.size());
        assertEquals("A", events.getFirst().getAs("trace_from_id"));
        assertEquals("B", events.getFirst().getAs("trace_to_id"));
        assertEquals(1L, ((Number) events.getFirst().getAs("trace_depth")).longValue());
    }

    @Test
    void previewHasCompleteCollectiveLineageFromObservationsAndInterestTableWithoutJobs() {
        List<CanvasColumnSchema> observationColumns = List.of(
                stringColumn("device_id"), stringColumn("region"),
                timestampColumn("observed_at"), stringColumn("wkt"));
        SparkCanvasTable raw = table("lineage_raw", observationColumns, List.of());
        RecordingIssueSink constructIssues = new RecordingIssueSink();
        SparkCanvasTable observations = new GeometryConstructNodeOperator().apply(
                new GeometryConstructNodeDefinition(id(), "构造", layout(),
                        new GeometryConstructConfiguration(
                                "lineage_raw", "observations", "shape",
                                new GeometryConstructSource.Wkt("wkt"),
                                new GeometryTypeDefinition(
                                        GeometryKind.POINT, new CrsReference("EPSG", 3857),
                                        CoordinateDimension.XY))),
                Map.of("lineage_raw", raw), previewContext(constructIssues))
                .propagatedTables().get("observations");
        assertFalse(constructIssues.hasErrors(), constructIssues::toString);
        observations = markInput(observations, "observations");
        SparkCanvasTable interests = markInput(table(
                "trace_interests",
                List.of(stringColumn("interest_id"), timestampColumn("start_at")),
                List.of()), "interests");
        TraceProximityEventsConfiguration configuration = new TraceProximityEventsConfiguration(
                "observations", "shape", "device_id", "observed_at",
                SpatialDistanceMethod.PLANAR, 1.1d, SpatialDistanceUnit.SOURCE_CRS_UNIT,
                6L, SpatialGroupByProximityTemporalUnit.MINUTES,
                TraceProximityInterestSource.TABLE, List.of(),
                "trace_interests", "interest_id", "start_at", 3, List.of("region"), true,
                "trace_events", "trace_tracks", "trace_from_id", "trace_to_id",
                "trace_depth", "trace_duration_minutes", "trace_event_time");
        Map<String, SparkCanvasTable> inputs = new LinkedHashMap<>();
        inputs.put("observations", observations);
        inputs.put("trace_interests", interests);

        String jobGroup = "trace-proximity-preview-" + id();
        spark.sparkContext().setJobGroup(jobGroup, "trace proximity preview", false);
        TaskLineageEvidence.Flow eventFlow;
        TaskLineageEvidence.Flow trackFlow;
        try {
            RecordingIssueSink issues = new RecordingIssueSink();
            CanvasNodeOperationResult result = new TraceProximityEventsNodeOperator().apply(
                    new TraceProximityEventsNodeDefinition(
                            id(), "邻近事件追踪", layout(), configuration),
                    inputs, previewContext(issues));
            assertFalse(issues.hasErrors(), issues::toString);
            eventFlow = analyzeLineage(result.propagatedTables().get("trace_events"),
                    "trace_events");
            trackFlow = analyzeLineage(result.propagatedTables().get("trace_tracks"),
                    "trace_tracks");
            assertEquals(0, spark.sparkContext().statusTracker()
                    .getJobIdsForGroup(jobGroup).length);
        } finally {
            spark.sparkContext().clearJobGroup();
        }

        assertComplete(eventFlow);
        assertComplete(trackFlow);
        for (CanvasColumnSchema column : observations.schema().columns()) {
            assertEdge(eventFlow, column.name(), "observations", column.name());
            assertEdge(trackFlow, column.name(), "observations", column.name());
        }
        for (String output : List.of("trace_from_id", "trace_to_id", "trace_depth",
                "trace_duration_minutes", "trace_event_time")) {
            for (String source : List.of("shape", "device_id", "observed_at", "region")) {
                assertEdge(eventFlow, output, "observations", source);
            }
            assertEdge(eventFlow, output, "interests", "interest_id");
            assertEdge(eventFlow, output, "interests", "start_at");
        }
        for (String source : List.of("shape", "device_id", "observed_at", "region")) {
            assertEdge(trackFlow, "trace_depth", "observations", source);
        }
        assertEdge(trackFlow, "trace_depth", "interests", "interest_id");
        assertEdge(trackFlow, "trace_depth", "interests", "start_at");
    }

    @Test
    void twentyThousandObservationsProduceTenThousandFirstContacts() {
        int observationCount = 20_000;
        int pairCount = observationCount / 2;
        Dataset<Row> observationsData = spark.range(observationCount).select(
                functions.when(functions.col("id").lt(pairCount),
                                functions.concat(functions.lit("S"),
                                        functions.col("id").cast("string")))
                        .otherwise(functions.concat(functions.lit("T"),
                                functions.col("id").minus(pairCount).cast("string")))
                        .alias("device_id"),
                functions.lit(Timestamp.valueOf("2026-01-01 00:00:00"))
                        .cast("timestamp").alias("observed_at"),
                st_functions.ST_SetSRID(st_constructors.ST_Point(
                                functions.pmod(functions.col("id"), functions.lit(pairCount))
                                        .multiply(10d)
                                        .plus(functions.when(functions.col("id").geq(pairCount),
                                                functions.lit(0.5d)).otherwise(functions.lit(0d)))
                                        .cast("double"),
                                functions.lit(0d)),
                        functions.lit(3857)).alias("shape"));
        CanvasColumnSchema geometry = new CanvasColumnSchema(
                "shape", PlatformDataType.GEOMETRY, null, null, null, false,
                null, false, false, null,
                new GeometryTypeDefinition(GeometryKind.POINT,
                        new CrsReference("EPSG", 3857), CoordinateDimension.XY));
        SparkCanvasTable observations = new SparkCanvasTable(
                new CanvasTableSchema("observations", null,
                        List.of(stringColumn("device_id"), timestampColumn("observed_at"), geometry),
                        CanvasDatasetKind.BOUNDED, null, null),
                observationsData);
        Dataset<Row> interestsData = spark.range(pairCount).select(
                functions.concat(functions.lit("S"), functions.col("id").cast("string"))
                        .alias("interest_id"));
        SparkCanvasTable interests = new SparkCanvasTable(
                new CanvasTableSchema("trace_interests", null,
                        List.of(stringColumn("interest_id")),
                        CanvasDatasetKind.BOUNDED, null, null),
                interestsData);
        TraceProximityEventsConfiguration configuration = new TraceProximityEventsConfiguration(
                "observations", "shape", "device_id", "observed_at",
                SpatialDistanceMethod.PLANAR, 1d, SpatialDistanceUnit.SOURCE_CRS_UNIT,
                0L, SpatialGroupByProximityTemporalUnit.SECONDS,
                TraceProximityInterestSource.TABLE, List.of(),
                "trace_interests", "interest_id", null, 1, List.of(), false,
                "trace_events", "unused_tracks", "trace_from_id", "trace_to_id",
                "trace_depth", "trace_duration_minutes", "trace_event_time");
        Map<String, SparkCanvasTable> inputs = new LinkedHashMap<>();
        inputs.put("observations", observations);
        inputs.put("trace_interests", interests);
        RecordingIssueSink issues = new RecordingIssueSink();

        CanvasNodeOperationResult result = new TraceProximityEventsNodeOperator().apply(
                new TraceProximityEventsNodeDefinition(
                        id(), "邻近事件追踪", layout(), configuration),
                inputs, runtimeContext(issues));

        assertFalse(issues.hasErrors(), issues::toString);
        Dataset<Row> events = result.propagatedTables().get("trace_events").dataset();
        Row summary = events.agg(
                functions.count(functions.lit(1)).alias("event_count"),
                functions.countDistinct(events.col("trace_to_id")).alias("entity_count"),
                functions.sum(events.col("trace_depth")).alias("depth_sum")).first();
        assertEquals((long) pairCount, ((Number) summary.getAs("event_count")).longValue());
        assertEquals((long) pairCount, ((Number) summary.getAs("entity_count")).longValue());
        assertEquals((long) pairCount, ((Number) summary.getAs("depth_sum")).longValue());
    }

    private static long depth(Row row) {
        return ((Number) row.getAs("trace_depth")).longValue();
    }

    private static Row row(String id, String region, String timestamp, String wkt) {
        return RowFactory.create(id, region,
                timestamp == null ? null : Timestamp.valueOf(timestamp), wkt);
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

    private CanvasNodeOperationContext previewContext(RecordingIssueSink issues) {
        return new CanvasNodeOperationContext(
                spark, MetadataIndex.create(new MetadataSnapshot(List.of(), List.of())),
                issues, new SchemaOnlyCanvasNodeDataAccess(spark));
    }

    private CanvasNodeOperationContext runtimeContext(RecordingIssueSink issues) {
        return new CanvasNodeOperationContext(
                spark, MetadataIndex.create(new MetadataSnapshot(List.of(), List.of())),
                issues, new SchemaOnlyCanvasNodeDataAccess(spark), CanvasExecutionMode.BATCH,
                CanvasRuntimeValues.execution(UUID.randomUUID(), Instant.now()));
    }

    private SparkCanvasTable table(
            String name,
            List<CanvasColumnSchema> columns,
            List<Row> rows
    ) {
        CanvasTableSchema schema = new CanvasTableSchema(
                name, null, columns, CanvasDatasetKind.BOUNDED, null, null);
        return new SparkCanvasTable(schema,
                spark.createDataFrame(rows, SparkTypeMapper.toStructType(columns)));
    }

    private static CanvasColumnSchema stringColumn(String name) {
        return column(name, PlatformDataType.STRING);
    }

    private static CanvasColumnSchema timestampColumn(String name) {
        return column(name, PlatformDataType.TIMESTAMP);
    }

    private static CanvasColumnSchema column(String name, PlatformDataType type) {
        return new CanvasColumnSchema(name, type, null, null, null, true,
                null, false, false, null, null);
    }

    private static CanvasNodeLayout layout() {
        return new CanvasNodeLayout(0d, 0d, 392d, 232d);
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
