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
import cn.superhuang.data.scalpel.contract.task.SnapTracksConfiguration;
import cn.superhuang.data.scalpel.contract.task.SnapTracksDirectionMatching;
import cn.superhuang.data.scalpel.contract.task.SnapTracksLineField;
import cn.superhuang.data.scalpel.contract.task.SnapTracksNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.SnapTracksOutputMode;
import cn.superhuang.data.scalpel.contract.task.SpatialDistanceMethod;
import cn.superhuang.data.scalpel.contract.task.SpatialDistanceUnit;
import cn.superhuang.data.scalpel.contract.task.SpatialDurationUnit;
import cn.superhuang.data.scalpel.contract.task.TrackBoundaryConfiguration;
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
import org.apache.spark.sql.Column;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SnapTracksNodeOperatorSparkTest {
    private static final GeometryTypeDefinition WEB_MERCATOR = new GeometryTypeDefinition(
            GeometryKind.POINT, new CrsReference("EPSG", 3857), CoordinateDimension.XY);
    private static final GeometryTypeDefinition WGS84 = new GeometryTypeDefinition(
            GeometryKind.POINT, new CrsReference("EPSG", 4326), CoordinateDimension.XY);

    private SparkSession spark;

    @BeforeAll
    void startSpark() {
        spark = SedonaSparkSupport.initialize(SedonaSparkSupport.builder()
                .master("local[1]").appName("snap-tracks-test")
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
    void matchesConnectedPlanarLinesAndProjectsNetworkFields() {
        SparkCanvasTable points = points("points", WEB_MERCATOR, List.of(
                point("T", "2026-01-01 00:00:00", 1L, "POINT (1 0)"),
                point("T", "2026-01-01 00:01:00", 2L, "POINT (9 0)"),
                point("T", "2026-01-01 00:02:00", 3L, "POINT (11 0)")));
        SparkCanvasTable lines = lines("roads", WEB_MERCATOR, List.of(
                line("L1", "A", "B", "A", "main", "LINESTRING (0 0, 10 0)"),
                line("L2", "B", "C", "A", "connected", "LINESTRING (10 1, 20 1)"),
                line("L3", "X", "Y", "A", "disconnected", "LINESTRING (10 0, 20 0)")));

        List<Row> rows = execute(points, lines, configuration(
                        "points", "roads", "snapped", SnapTracksOutputMode.ALL_FEATURES,
                        SpatialDistanceMethod.PLANAR, 2d, SpatialDistanceUnit.SOURCE_CRS_UNIT,
                        null, null, List.of(new SnapTracksLineField("road_name", "matched_road_name"))))
                .dataset().orderBy("sequence").collectAsList();

        assertEquals(3, rows.size());
        assertEquals(List.of("L1", "L1", "L2"), rows.stream()
                .map(row -> row.<String>getAs("matched_line_id")).toList());
        assertEquals("connected", rows.getLast().getAs("matched_road_name"));
        assertEquals("M", rows.getLast().getAs("match_status"));
        assertEquals(1d, ((Number) rows.getLast().getAs("match_distance")).doubleValue(), 1e-9);
    }

    @Test
    void respectsForwardDirectionAndTrackGapBoundaries() {
        SparkCanvasTable reversePoints = points("reverse_points", WEB_MERCATOR, List.of(
                point("T", "2026-01-01 00:00:00", 1L, "POINT (8 0)"),
                point("T", "2026-01-01 00:01:00", 2L, "POINT (2 0)")));
        SparkCanvasTable directedLines = lines("directed_roads", WEB_MERCATOR, List.of(
                line("L1", "A", "B", "F", "one-way", "LINESTRING (0 0, 10 0)")));
        SnapTracksDirectionMatching direction = new SnapTracksDirectionMatching(
                "direction", "F", "B", "A", "N");

        List<Row> reverse = execute(reversePoints, directedLines, configuration(
                        "reverse_points", "directed_roads", "reverse_result",
                        SnapTracksOutputMode.ALL_FEATURES, SpatialDistanceMethod.PLANAR,
                        1d, SpatialDistanceUnit.SOURCE_CRS_UNIT, null, direction, List.of()))
                .dataset().orderBy("sequence").collectAsList();
        assertEquals("M", reverse.getFirst().getAs("match_status"));
        assertEquals("U", reverse.getLast().getAs("match_status"));
        assertNull(reverse.getLast().getAs("matched_line_id"));

        SparkCanvasTable gapPoints = points("gap_points", WEB_MERCATOR, List.of(
                point("T", "2026-01-01 00:00:00", 1L, "POINT (1 0)"),
                point("T", "2026-01-01 00:01:00", 2L, "POINT (2 0)"),
                point("T", "2026-01-01 00:10:00", 3L, "POINT (3 0)")));
        TrackBoundaryConfiguration boundaries = new TrackBoundaryConfiguration(
                5d, SpatialDurationUnit.MINUTES, null, null, null);
        List<Row> gap = execute(gapPoints, directedLines, configuration(
                        "gap_points", "directed_roads", "gap_result",
                        SnapTracksOutputMode.ALL_FEATURES, SpatialDistanceMethod.PLANAR,
                        1d, SpatialDistanceUnit.SOURCE_CRS_UNIT, boundaries, direction, List.of()))
                .dataset().orderBy("sequence").collectAsList();
        assertEquals(List.of("M", "M", "U"), gap.stream()
                .map(row -> row.<String>getAs("match_status")).toList());
    }

    @Test
    void allFeaturesKeepsUnmatchedAndMatchedFeaturesDropsThem() {
        SparkCanvasTable points = points("unmatched_points", WEB_MERCATOR, List.of(
                point("A", "2026-01-01 00:00:00", 1L, "POINT (100 0)"),
                point("A", "2026-01-01 00:01:00", 2L, "POINT (101 0)"),
                point("B", "2026-01-01 00:00:00", 1L, "POINT (5 0)")));
        SparkCanvasTable lines = lines("single_road", WEB_MERCATOR, List.of(
                line("L1", "A", "B", "A", "main", "LINESTRING (0 0, 10 0)")));

        List<Row> all = execute(points, lines, configuration(
                        "unmatched_points", "single_road", "all_result",
                        SnapTracksOutputMode.ALL_FEATURES, SpatialDistanceMethod.PLANAR,
                        1d, SpatialDistanceUnit.SOURCE_CRS_UNIT, null, null, List.of()))
                .dataset().collectAsList();
        List<Row> matched = execute(points, lines, configuration(
                        "unmatched_points", "single_road", "matched_result",
                        SnapTracksOutputMode.MATCHED_FEATURES, SpatialDistanceMethod.PLANAR,
                        1d, SpatialDistanceUnit.SOURCE_CRS_UNIT, null, null, List.of()))
                .dataset().collectAsList();

        assertEquals(3, all.size());
        assertTrue(all.stream().allMatch(row -> "U".equals(row.getAs("match_status"))));
        assertTrue(matched.isEmpty());
    }

    @Test
    void rejectsMoreThanThirtyTwoCandidatesForOneObservation() {
        SparkCanvasTable points = points("crowded_points", WEB_MERCATOR, List.of(
                point("T", "2026-01-01 00:00:00", 1L, "POINT (5 0)"),
                point("T", "2026-01-01 00:01:00", 2L, "POINT (6 0)")));
        List<Row> lineRows = new ArrayList<>();
        for (int index = 0; index < 33; index++) {
            lineRows.add(line("L" + index, "A" + index, "B" + index, "A", "road-" + index,
                    "LINESTRING (0 0, 10 0)"));
        }
        SparkCanvasTable lines = lines("crowded_roads", WEB_MERCATOR, lineRows);
        SparkCanvasTable result = execute(points, lines, configuration(
                "crowded_points", "crowded_roads", "crowded_result",
                SnapTracksOutputMode.ALL_FEATURES, SpatialDistanceMethod.PLANAR,
                1d, SpatialDistanceUnit.SOURCE_CRS_UNIT, null, null, List.of()));

        RuntimeException failure = assertThrows(RuntimeException.class,
                () -> result.dataset().collectAsList());
        assertTrue(failure.toString().contains("SNAP_TRACKS_CANDIDATE_COUNT_EXCEEDED")
                || causeMessages(failure).contains("SNAP_TRACKS_CANDIDATE_COUNT_EXCEEDED"));
    }

    @Test
    void rejectsNullOrDuplicateLineIdsAndNullNetworkNodes() {
        SparkCanvasTable points = points("network_validation_points", WEB_MERCATOR, List.of(
                point("T", "2026-01-01 00:00:00", 1L, "POINT (1 0)"),
                point("T", "2026-01-01 00:01:00", 2L, "POINT (2 0)")));

        assertRuntimeCode(execute(points, lines("null_id_roads", WEB_MERCATOR, List.of(
                        line(null, "A", "B", "A", "null-id", "LINESTRING (0 0, 10 0)"))),
                configuration("network_validation_points", "null_id_roads", "null_id_result",
                        SnapTracksOutputMode.ALL_FEATURES, SpatialDistanceMethod.PLANAR,
                        1d, SpatialDistanceUnit.SOURCE_CRS_UNIT, null, null, List.of())),
                "SNAP_TRACKS_LINE_ID_INVALID");

        assertRuntimeCode(execute(points, lines("duplicate_id_roads", WEB_MERCATOR, List.of(
                        line("L1", "A", "B", "A", "first", "LINESTRING (0 0, 10 0)"),
                        line("L1", "B", "C", "A", "second", "LINESTRING (10 0, 20 0)"))),
                configuration("network_validation_points", "duplicate_id_roads", "duplicate_id_result",
                        SnapTracksOutputMode.ALL_FEATURES, SpatialDistanceMethod.PLANAR,
                        1d, SpatialDistanceUnit.SOURCE_CRS_UNIT, null, null, List.of())),
                "SNAP_TRACKS_LINE_ID_DUPLICATE");

        assertRuntimeCode(execute(points, lines("null_node_roads", WEB_MERCATOR, List.of(
                        line("L1", null, "B", "A", "null-node", "LINESTRING (0 0, 10 0)"))),
                configuration("network_validation_points", "null_node_roads", "null_node_result",
                        SnapTracksOutputMode.ALL_FEATURES, SpatialDistanceMethod.PLANAR,
                        1d, SpatialDistanceUnit.SOURCE_CRS_UNIT, null, null, List.of())),
                "SNAP_TRACKS_NETWORK_NODE_INVALID");
    }

    @Test
    void matchesAcrossTheDateLineWithGeodesicDistance() {
        SparkCanvasTable points = points("wgs_points", WGS84, List.of(
                point("T", "2026-01-01 00:00:00", 1L, "POINT (179.5 0)"),
                point("T", "2026-01-01 00:01:00", 2L, "POINT (-179.5 0)")));
        SparkCanvasTable lines = lines("wgs_roads", WGS84, List.of(
                line("L1", "A", "B", "A", "date-line",
                        "LINESTRING (179 0, -179 0)")));

        List<Row> rows = execute(points, lines, configuration(
                        "wgs_points", "wgs_roads", "wgs_result",
                        SnapTracksOutputMode.ALL_FEATURES, SpatialDistanceMethod.GEODESIC,
                        1d, SpatialDistanceUnit.KILOMETERS, null, null, List.of()))
                .dataset().orderBy("sequence").collectAsList();

        assertEquals(List.of("M", "M"), rows.stream()
                .map(row -> row.<String>getAs("match_status")).toList());
        assertTrue(rows.stream().allMatch(row -> ((Number) row.getAs("match_distance")).doubleValue() < 1d));
    }

    @Test
    void previewHasCompletePointAndNetworkLineageWithoutSubmittingSparkJobs() {
        SparkCanvasTable points = markInput(points("points", WEB_MERCATOR, List.of()), "points");
        SparkCanvasTable lines = markInput(lines("roads", WEB_MERCATOR, List.of()), "roads");
        SnapTracksConfiguration configuration = configuration(
                "points", "roads", "snapped", SnapTracksOutputMode.ALL_FEATURES,
                SpatialDistanceMethod.PLANAR, 2d, SpatialDistanceUnit.SOURCE_CRS_UNIT,
                null, new SnapTracksDirectionMatching("direction", "F", "B", "A", "N"),
                List.of(new SnapTracksLineField("road_name", "matched_road_name")));
        Map<String, SparkCanvasTable> inputs = new LinkedHashMap<>();
        inputs.put("points", points);
        inputs.put("roads", lines);

        String jobGroup = "snap-tracks-preview-" + id();
        spark.sparkContext().setJobGroup(jobGroup, "snap tracks preview", false);
        TaskLineageEvidence.Flow flow;
        try {
            RecordingIssueSink issues = new RecordingIssueSink();
            CanvasNodeOperationResult result = new SnapTracksNodeOperator().apply(
                    new SnapTracksNodeDefinition(id(), "吸附轨迹", layout(), configuration),
                    inputs, previewContext(issues));
            assertFalse(issues.hasErrors(), issues::toString);
            flow = analyzeLineage(result.propagatedTables().get("snapped"));
            assertEquals(0, spark.sparkContext().statusTracker()
                    .getJobIdsForGroup(jobGroup).length);
        } finally {
            spark.sparkContext().clearJobGroup();
        }

        assertEquals(TaskLineageEvidence.Coverage.FIELD_COMPLETE, flow.coverage(),
                () -> flow.warnings().toString());
        assertTrue(flow.fields().stream().noneMatch(field ->
                        field.outputEffect()
                                == TaskLineageEvidence.OutputEffect.WRITTEN_UNKNOWN_SOURCE),
                () -> flow.fields().toString());
        for (CanvasColumnSchema column : points.schema().columns()) {
            assertEdge(flow, column.name(), "points", column.name());
        }
        for (String output : List.of("matched_road_name", "matched_line_id", "match_status",
                "snapped_shape", "match_x", "match_y", "match_distance")) {
            for (String source : List.of("shape", "track_id", "observed_at", "sequence")) {
                assertEdge(flow, output, "points", source);
            }
            for (String source : List.of("shape", "line_id", "from_node", "to_node", "direction")) {
                assertEdge(flow, output, "roads", source);
            }
        }
        assertEdge(flow, "matched_road_name", "roads", "road_name");
        assertEdge(flow, "original_x", "points", "shape");
        assertEdge(flow, "original_y", "points", "shape");
    }

    @Test
    void twentyThousandObservationsMatchAcrossOneThousandIndependentTracks() {
        int trackCount = 1_000;
        int observationsPerTrack = 20;
        int observationCount = trackCount * observationsPerTrack;
        Column trackNumber = functions.floor(
                functions.col("id").divide(observationsPerTrack)).cast("long");
        Column sequence = functions.pmod(
                functions.col("id"), functions.lit(observationsPerTrack));
        Dataset<Row> pointData = spark.range(observationCount).select(
                functions.concat(functions.lit("T"), trackNumber.cast("string"))
                        .alias("track_id"),
                functions.expr("timestamp_seconds(id)").alias("observed_at"),
                sequence.cast("long").alias("sequence"),
                st_functions.ST_SetSRID(st_constructors.ST_Point(
                                trackNumber.multiply(100d).plus(sequence).cast("double"),
                                functions.lit(0d)),
                        functions.lit(3857)).alias("shape"));
        SparkCanvasTable points = new SparkCanvasTable(
                new CanvasTableSchema("points", null,
                        List.of(stringColumn("track_id"), timestampColumn("observed_at"),
                                longColumn("sequence"), geometryColumn("shape", GeometryKind.POINT)),
                        CanvasDatasetKind.BOUNDED, null, null),
                pointData);

        Column lineStart = functions.col("id").multiply(100L);
        Column lineWkt = functions.concat(
                functions.lit("LINESTRING ("), lineStart.cast("string"), functions.lit(" 0, "),
                lineStart.plus(20L).cast("string"), functions.lit(" 0)"));
        Dataset<Row> lineData = spark.range(trackCount).select(
                functions.concat(functions.lit("L"), functions.col("id").cast("string"))
                        .alias("line_id"),
                functions.concat(functions.lit("N"), functions.col("id").cast("string"))
                        .alias("from_node"),
                functions.concat(functions.lit("N"), functions.col("id").plus(1L).cast("string"))
                        .alias("to_node"),
                functions.lit("A").alias("direction"),
                functions.concat(functions.lit("road-"), functions.col("id").cast("string"))
                        .alias("road_name"),
                st_functions.ST_SetSRID(st_constructors.ST_GeomFromWKT(lineWkt),
                        functions.lit(3857)).alias("shape"));
        SparkCanvasTable lines = new SparkCanvasTable(
                new CanvasTableSchema("roads", null,
                        List.of(stringColumn("line_id"), stringColumn("from_node"),
                                stringColumn("to_node"), stringColumn("direction"),
                                stringColumn("road_name"),
                                geometryColumn("shape", GeometryKind.LINESTRING)),
                        CanvasDatasetKind.BOUNDED, null, null),
                lineData);
        SnapTracksConfiguration configuration = configuration(
                "points", "roads", "snapped", SnapTracksOutputMode.ALL_FEATURES,
                SpatialDistanceMethod.PLANAR, 1d, SpatialDistanceUnit.SOURCE_CRS_UNIT,
                null, null, List.of());

        Dataset<Row> output = execute(points, lines, configuration).dataset();
        Row summary = output.agg(
                functions.count(functions.lit(1)).alias("row_count"),
                functions.sum(functions.when(output.col("match_status").equalTo("M"), 1L)
                        .otherwise(0L)).alias("matched_count"),
                functions.countDistinct(output.col("matched_line_id")).alias("line_count"))
                .first();
        assertEquals((long) observationCount, ((Number) summary.getAs("row_count")).longValue());
        assertEquals((long) observationCount, ((Number) summary.getAs("matched_count")).longValue());
        assertEquals((long) trackCount, ((Number) summary.getAs("line_count")).longValue());
    }

    private SparkCanvasTable execute(
            SparkCanvasTable points,
            SparkCanvasTable lines,
            SnapTracksConfiguration configuration
    ) {
        Map<String, SparkCanvasTable> inputs = new LinkedHashMap<>();
        inputs.put(points.schema().name(), points);
        inputs.put(lines.schema().name(), lines);
        RecordingIssueSink issues = new RecordingIssueSink();
        CanvasNodeOperationResult result = new SnapTracksNodeOperator().apply(
                new SnapTracksNodeDefinition(id(), "吸附轨迹", layout(), configuration),
                inputs, runtimeContext(issues));
        assertFalse(issues.hasErrors(), issues::toString);
        return result.propagatedTables().get(configuration.outputTableName());
    }

    private SparkCanvasTable points(
            String name,
            GeometryTypeDefinition geometry,
            List<Row> rows
    ) {
        SparkCanvasTable raw = table(name + "_raw", List.of(
                stringColumn("track_id"), timestampColumn("observed_at"),
                longColumn("sequence"), stringColumn("wkt")), rows);
        return construct(raw, name, "shape", geometry);
    }

    private SparkCanvasTable lines(
            String name,
            GeometryTypeDefinition pointGeometry,
            List<Row> rows
    ) {
        SparkCanvasTable raw = table(name + "_raw", List.of(
                stringColumn("line_id"), stringColumn("from_node"), stringColumn("to_node"),
                stringColumn("direction"), stringColumn("road_name"), stringColumn("wkt")), rows);
        GeometryTypeDefinition geometry = new GeometryTypeDefinition(
                GeometryKind.LINESTRING, pointGeometry.crs(), pointGeometry.dimension());
        return construct(raw, name, "shape", geometry);
    }

    private SparkCanvasTable construct(
            SparkCanvasTable raw,
            String output,
            String geometryColumn,
            GeometryTypeDefinition geometry
    ) {
        RecordingIssueSink issues = new RecordingIssueSink();
        CanvasNodeOperationResult result = new GeometryConstructNodeOperator().apply(
                new GeometryConstructNodeDefinition(id(), "构造 Geometry", layout(),
                        new GeometryConstructConfiguration(
                                raw.schema().name(), output, geometryColumn,
                                new GeometryConstructSource.Wkt("wkt"), geometry)),
                Map.of(raw.schema().name(), raw), previewContext(issues));
        assertFalse(issues.hasErrors(), issues::toString);
        return result.propagatedTables().get(output);
    }

    private static SnapTracksConfiguration configuration(
            String pointTable,
            String lineTable,
            String outputTable,
            SnapTracksOutputMode outputMode,
            SpatialDistanceMethod distanceMethod,
            double distance,
            SpatialDistanceUnit distanceUnit,
            TrackBoundaryConfiguration boundaries,
            SnapTracksDirectionMatching direction,
            List<SnapTracksLineField> lineFields
    ) {
        return new SnapTracksConfiguration(
                pointTable, "shape", List.of("track_id"), "observed_at", List.of("sequence"),
                lineTable, "shape", "line_id", "from_node", "to_node",
                distance, distanceUnit, distanceMethod,
                boundaries == null
                        ? new TrackBoundaryConfiguration(null, null, null, null, null)
                        : boundaries,
                direction, lineFields,
                outputMode, outputTable, "snapped_shape", "matched_line_id", "match_status",
                "original_x", "original_y", "match_x", "match_y", "match_distance");
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

    private static Row point(String track, String time, long sequence, String wkt) {
        return RowFactory.create(track, Timestamp.valueOf(time), sequence, wkt);
    }

    private static Row line(
            String id, String from, String to, String direction, String roadName, String wkt
    ) {
        return RowFactory.create(id, from, to, direction, roadName, wkt);
    }

    private static CanvasColumnSchema stringColumn(String name) {
        return column(name, PlatformDataType.STRING);
    }

    private static CanvasColumnSchema timestampColumn(String name) {
        return column(name, PlatformDataType.TIMESTAMP);
    }

    private static CanvasColumnSchema longColumn(String name) {
        return column(name, PlatformDataType.LONG);
    }

    private static CanvasColumnSchema geometryColumn(String name, GeometryKind kind) {
        return new CanvasColumnSchema(
                name, PlatformDataType.GEOMETRY, null, null, null, false,
                null, false, false, null,
                new GeometryTypeDefinition(kind, new CrsReference("EPSG", 3857),
                        CoordinateDimension.XY));
    }

    private static CanvasColumnSchema column(String name, PlatformDataType type) {
        return new CanvasColumnSchema(name, type, null, null, null, true,
                null, false, false, null, null);
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
                null, null, "snapped", null, null, "snapped");
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

    private static String causeMessages(Throwable failure) {
        StringBuilder result = new StringBuilder();
        for (Throwable current = failure; current != null; current = current.getCause()) {
            result.append(current.getMessage()).append('\n');
        }
        return result.toString();
    }

    private static void assertRuntimeCode(SparkCanvasTable result, String code) {
        RuntimeException failure = assertThrows(RuntimeException.class,
                () -> result.dataset().collectAsList());
        assertTrue(failure.toString().contains(code) || causeMessages(failure).contains(code));
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
