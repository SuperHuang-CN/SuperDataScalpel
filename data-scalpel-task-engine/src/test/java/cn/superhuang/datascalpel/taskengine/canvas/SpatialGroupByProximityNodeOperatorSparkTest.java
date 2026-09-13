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
import cn.superhuang.data.scalpel.contract.task.SpatialDistanceUnit;
import cn.superhuang.data.scalpel.contract.task.SpatialGroupByProximityAttributeCondition;
import cn.superhuang.data.scalpel.contract.task.SpatialGroupByProximityAttributeRelationship;
import cn.superhuang.data.scalpel.contract.task.SpatialGroupByProximityConfiguration;
import cn.superhuang.data.scalpel.contract.task.SpatialGroupByProximityNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.SpatialGroupByProximitySpatialRelationship;
import cn.superhuang.data.scalpel.contract.task.SpatialGroupByProximityTemporalCondition;
import cn.superhuang.data.scalpel.contract.task.SpatialGroupByProximityTemporalRelationship;
import cn.superhuang.data.scalpel.contract.task.SpatialGroupByProximityTemporalUnit;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SpatialGroupByProximityNodeOperatorSparkTest {
    private SparkSession spark;

    @BeforeAll
    void startSpark() {
        spark = SedonaSparkSupport.initialize(SedonaSparkSupport.builder()
                .master("local[1]").appName("spatial-group-by-proximity-test")
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
    void formsTransitiveGroupsWithTemporalAndAttributeConstraintsAndKeepsSingletons() {
        List<CanvasColumnSchema> columns = List.of(
                stringColumn("feature_id"), stringColumn("region"),
                doubleColumn("accuracy"), timestampColumn("observed_at"),
                stringColumn("wkt"));
        SparkCanvasTable raw = table("raw_events", columns, List.of(
                row("A", "north", 1d, "2026-01-01 00:00:00", "POINT (0 0)"),
                row("B", "north", 2d, "2026-01-01 00:05:00", "POINT (1 0)"),
                row("C", "north", 3d, "2026-01-01 00:10:00", "POINT (2 0)"),
                row("D", "north", 1d, "2026-01-01 00:00:00", "POINT (20 0)"),
                row("E", "south", 1d, "2026-01-01 00:00:00", "POINT (0.5 0)")));
        RecordingIssueSink constructIssues = new RecordingIssueSink();
        SparkCanvasTable events = new GeometryConstructNodeOperator().apply(
                new GeometryConstructNodeDefinition(id(), "构造", layout(),
                        new GeometryConstructConfiguration(
                                "raw_events", "events", "shape",
                                new GeometryConstructSource.Wkt("wkt"),
                                new GeometryTypeDefinition(
                                        GeometryKind.POINT, new CrsReference("EPSG", 3857),
                                        CoordinateDimension.XY))),
                Map.of("raw_events", raw), previewContext(constructIssues))
                .propagatedTables().get("events");
        assertFalse(constructIssues.hasErrors(), constructIssues::toString);

        SpatialGroupByProximityConfiguration configuration =
                new SpatialGroupByProximityConfiguration(
                        "events", "shape",
                        SpatialGroupByProximitySpatialRelationship.NEAR_PLANAR,
                        1.1d, SpatialDistanceUnit.SOURCE_CRS_UNIT,
                        new SpatialGroupByProximityTemporalCondition(
                                SpatialGroupByProximityTemporalRelationship.NEAR,
                                "observed_at", null, 6L,
                                SpatialGroupByProximityTemporalUnit.MINUTES),
                        List.of(
                                new SpatialGroupByProximityAttributeCondition(
                                        "region", SpatialGroupByProximityAttributeRelationship.EQUALS,
                                        null),
                                new SpatialGroupByProximityAttributeCondition(
                                        "accuracy",
                                        SpatialGroupByProximityAttributeRelationship
                                                .ABSOLUTE_DIFFERENCE_AT_MOST,
                                        1.1d)),
                        "group_id", "event_groups");
        RecordingIssueSink issues = new RecordingIssueSink();
        Map<String, SparkCanvasTable> inputs = new LinkedHashMap<>();
        inputs.put("events", events);

        CanvasNodeOperationResult result = new SpatialGroupByProximityNodeOperator().apply(
                new SpatialGroupByProximityNodeDefinition(
                        id(), "邻近分组", layout(), configuration),
                inputs, runtimeContext(issues));

        assertFalse(issues.hasErrors(), issues::toString);
        assertEquals(List.of("events", "event_groups"),
                new ArrayList<>(result.propagatedTables().keySet()));
        List<Row> rows = result.propagatedTables().get("event_groups")
                .dataset().orderBy("feature_id").collectAsList();
        assertEquals(5, rows.size());
        long first = ((Number) rows.getFirst().getAs("group_id")).longValue();
        assertEquals(first, ((Number) rows.get(1).getAs("group_id")).longValue());
        assertEquals(first, ((Number) rows.get(2).getAs("group_id")).longValue());
        assertNotEquals(first, ((Number) rows.get(3).getAs("group_id")).longValue());
        assertNotEquals(first, ((Number) rows.get(4).getAs("group_id")).longValue());
        assertFalse(result.propagatedTables().get("event_groups")
                .schema().columns().getLast().nullable());
    }

    @Test
    void previewHasCompleteCollectiveFieldLineageWithoutSubmittingSparkJobs() {
        List<CanvasColumnSchema> columns = List.of(
                stringColumn("feature_id"), stringColumn("region"),
                doubleColumn("accuracy"), timestampColumn("observed_at"),
                stringColumn("wkt"));
        SparkCanvasTable raw = table("lineage_raw", columns, List.of());
        RecordingIssueSink constructIssues = new RecordingIssueSink();
        SparkCanvasTable events = new GeometryConstructNodeOperator().apply(
                new GeometryConstructNodeDefinition(id(), "构造", layout(),
                        new GeometryConstructConfiguration(
                                "lineage_raw", "events", "shape",
                                new GeometryConstructSource.Wkt("wkt"),
                                new GeometryTypeDefinition(
                                        GeometryKind.POINT, new CrsReference("EPSG", 3857),
                                        CoordinateDimension.XY))),
                Map.of("lineage_raw", raw), previewContext(constructIssues))
                .propagatedTables().get("events");
        assertFalse(constructIssues.hasErrors(), constructIssues::toString);
        events = markInput(events, "events");
        SpatialGroupByProximityConfiguration configuration = fullConfiguration();

        String jobGroup = "group-by-proximity-preview-" + id();
        spark.sparkContext().setJobGroup(jobGroup, "group by proximity preview", false);
        TaskLineageEvidence.Flow flow;
        try {
            RecordingIssueSink issues = new RecordingIssueSink();
            CanvasNodeOperationResult result = new SpatialGroupByProximityNodeOperator().apply(
                    new SpatialGroupByProximityNodeDefinition(
                            id(), "邻近分组", layout(), configuration),
                    Map.of("events", events), previewContext(issues));
            assertFalse(issues.hasErrors(), issues::toString);
            flow = analyzeLineage(result.propagatedTables().get("event_groups"));
            assertEquals(0, spark.sparkContext().statusTracker()
                    .getJobIdsForGroup(jobGroup).length);
        } finally {
            spark.sparkContext().clearJobGroup();
        }

        assertEquals(TaskLineageEvidence.Coverage.FIELD_COMPLETE, flow.coverage(),
                () -> flow.warnings().toString());
        assertTrue(flow.fields().stream().noneMatch(field ->
                        field.outputEffect() == TaskLineageEvidence.OutputEffect.WRITTEN_UNKNOWN_SOURCE),
                () -> flow.fields().toString());
        for (String source : List.of("shape", "observed_at", "region", "accuracy")) {
            assertEdge(flow, "group_id", "events", source);
        }
        for (CanvasColumnSchema column : events.schema().columns()) {
            assertEdge(flow, column.name(), "events", column.name());
        }
    }

    @Test
    void twentyThousandIsolatedPointsRemainDistinctWithoutDriverCollection() {
        SparkCanvasTable points = generatedPointTable("points", 20_000);
        SpatialGroupByProximityConfiguration configuration =
                new SpatialGroupByProximityConfiguration(
                        "points", "shape",
                        SpatialGroupByProximitySpatialRelationship.INTERSECTS,
                        null, null, null, List.of(),
                        "group_id", "point_groups");
        RecordingIssueSink issues = new RecordingIssueSink();

        CanvasNodeOperationResult result = new SpatialGroupByProximityNodeOperator().apply(
                new SpatialGroupByProximityNodeDefinition(
                        id(), "邻近分组", layout(), configuration),
                Map.of("points", points), runtimeContext(issues));

        assertFalse(issues.hasErrors(), issues::toString);
        Dataset<Row> output = result.propagatedTables().get("point_groups").dataset();
        Row summary = output.agg(
                functions.count(output.col("feature_id")).alias("row_count"),
                functions.countDistinct(output.col("group_id")).alias("group_count"),
                functions.count(output.col("group_id")).alias("non_null_groups"))
                .first();
        assertEquals(20_000L, ((Number) summary.getAs("row_count")).longValue());
        assertEquals(20_000L, ((Number) summary.getAs("group_count")).longValue());
        assertEquals(20_000L, ((Number) summary.getAs("non_null_groups")).longValue());
    }

    private static SpatialGroupByProximityConfiguration fullConfiguration() {
        return new SpatialGroupByProximityConfiguration(
                "events", "shape",
                SpatialGroupByProximitySpatialRelationship.NEAR_PLANAR,
                1.1d, SpatialDistanceUnit.SOURCE_CRS_UNIT,
                new SpatialGroupByProximityTemporalCondition(
                        SpatialGroupByProximityTemporalRelationship.NEAR,
                        "observed_at", null, 6L,
                        SpatialGroupByProximityTemporalUnit.MINUTES),
                List.of(
                        new SpatialGroupByProximityAttributeCondition(
                                "region", SpatialGroupByProximityAttributeRelationship.EQUALS,
                                null),
                        new SpatialGroupByProximityAttributeCondition(
                                "accuracy",
                                SpatialGroupByProximityAttributeRelationship
                                        .ABSOLUTE_DIFFERENCE_AT_MOST,
                                1.1d)),
                "group_id", "event_groups");
    }

    private SparkCanvasTable generatedPointTable(String name, long size) {
        Dataset<Row> data = spark.range(size).select(
                functions.col("id").alias("feature_id"),
                st_functions.ST_SetSRID(st_constructors.ST_Point(
                        functions.col("id").multiply(10d).cast("double"),
                        functions.lit(0d)), functions.lit(3857)).alias("shape"));
        CanvasColumnSchema geometry = new CanvasColumnSchema(
                "shape", PlatformDataType.GEOMETRY, null, null, null, false,
                null, false, false, null,
                new GeometryTypeDefinition(GeometryKind.POINT,
                        new CrsReference("EPSG", 3857), CoordinateDimension.XY));
        CanvasTableSchema schema = new CanvasTableSchema(
                name, null, List.of(column("feature_id", PlatformDataType.LONG), geometry),
                CanvasDatasetKind.BOUNDED, null, null);
        return new SparkCanvasTable(schema, data);
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
                null, null, "event_groups", null, null, "event_groups");
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

    private static Row row(
            String id,
            String region,
            double accuracy,
            String timestamp,
            String wkt
    ) {
        return RowFactory.create(id, region, accuracy, Timestamp.valueOf(timestamp), wkt);
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

    private static CanvasColumnSchema doubleColumn(String name) {
        return column(name, PlatformDataType.DOUBLE);
    }

    private static CanvasColumnSchema timestampColumn(String name) {
        return column(name, PlatformDataType.TIMESTAMP);
    }

    private static CanvasColumnSchema column(String name, PlatformDataType type) {
        return new CanvasColumnSchema(name, type, null, null, null, true,
                null, false, false, null, null);
    }

    private static CanvasNodeLayout layout() {
        return new CanvasNodeLayout(0d, 0d, 384d, 224d);
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
