package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.*;
import cn.superhuang.data.scalpel.contract.type.*;
import cn.superhuang.datascalpel.taskengine.compiler.MetadataIndex;
import cn.superhuang.datascalpel.taskengine.compiler.lineage.*;
import cn.superhuang.datascalpel.taskengine.spark.*;
import org.apache.spark.sql.*;
import org.apache.spark.sql.sedona_sql.expressions.st_constructors;
import org.apache.spark.sql.sedona_sql.expressions.st_functions;
import org.junit.jupiter.api.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PointDbscanSparkTest {
    private SparkSession spark;
    @BeforeAll void start() {
        spark = SedonaSparkSupport.initialize(SedonaSparkSupport.builder().master("local[1]").appName("point-dbscan")
                .config("spark.ui.enabled", false).config("spark.driver.host", "127.0.0.1").config("spark.driver.bindAddress", "127.0.0.1")
                .config("spark.sql.shuffle.partitions", 1).config("spark.sql.session.timeZone", "UTC").getOrCreate());
    }
    @AfterAll void stop() { if (spark != null) spark.stop(); }

    @Test void hdbscanProducesDiagnosticsWhileRetainingOriginalFieldsAndIgnoringHiddenTime() {
        var source = table(3857, List.of(row(1L,null,0), row(2L,0L,.1), row(3L,0L,10), row(4L,0L,10.2), row(5L,0L,30)));
        var issues = new Issues(); var c = hdbscan(new SpatialHdbscanOptions("probability", "outlier", "exemplar", "stability"));
        var result = apply(source, c, issues, false);
        assertFalse(issues.hasErrors(), issues::toString); assertSame(source, result.propagatedTables().get("points"));
        var output = result.propagatedTables().get("clusters");
        var rows = output.dataset().orderBy("feature_id").collectAsList();
        assertEquals(5, rows.size()); assertEquals(2, clusters(rows).size());
        assertEquals(13, output.schema().columns().size()); assertEquals(CanvasDatasetKind.BOUNDED, output.schema().datasetKind());
        for (Row row : rows) {
            assertEquals("original-id", row.getAs("id")); assertEquals("original-src", row.getAs("src")); assertEquals("original-dst", row.getAs("dst"));
            assertTrue(Double.isFinite(row.getAs("probability"))); assertTrue(Double.isFinite(row.getAs("outlier")));
        }
        assertNull(rows.getFirst().getAs("time")); assertTrue(rows.getLast().<Boolean>getAs("noise"));
        assertEquals(0d, rows.getLast().<Double>getAs("probability")); assertNull(rows.getLast().getAs("stability"));
    }

    @Test void hdbscanValidatesDiagnosticsAndActualPointIdentityWithoutLeakingInputValues() {
        var source = table(3857, List.of());
        for (var options : Arrays.asList(null, new SpatialHdbscanOptions("", "o", "e", "s"), new SpatialHdbscanOptions("ID", "o", "e", "s"),
                new SpatialHdbscanOptions("p", "P", "e", "s"))) {
            var issues = new Issues(); assertTrue(apply(source, hdbscan(options), issues, true).propagatedTables().isEmpty());
            assertTrue(issues.hasErrors()); assertTrue(issues.paths.stream().anyMatch(path -> path.startsWith("configuration.hdbscan")));
        }
        var c = hdbscan(new SpatialHdbscanOptions("p", "o", "e", "s"));
        for (var rows : List.of(List.of(row(1L,0L,0), row(1L,0L,1)), List.of(row(null,0L,0)))) {
            var error = assertThrows(Exception.class, () -> run(table(3857, rows), c));
            assertTrue(error.getMessage().contains("SPATIAL_CLUSTER_FEATURE_ID_INVALID"));
        }
        var invalid = table(3857, List.of(RowFactory.create(1L,null,"id","src","dst","LINESTRING (0 0,1 1)")));
        var error = assertThrows(Exception.class, () -> run(invalid, c));
        assertTrue(error.getMessage().contains("SPATIAL_CLUSTER_POINT_INVALID"));
        var excluded = table(3857, List.of(RowFactory.create(1L,null,"id","src","dst","POINT EMPTY"),RowFactory.create(2L,null,"id","src","dst",null)));
        assertTrue(run(excluded,c).isEmpty());
        var singleton = run(table(3857,List.of(row(3L,null,0))),c); assertEquals(1,singleton.size()); assertTrue(singleton.getFirst().<Boolean>getAs("noise"));
    }

    private SpatialPointClusterConfiguration hdbscan(SpatialHdbscanOptions options) {
        return new SpatialPointClusterConfiguration("points", "shape", "feature_id", SpatialDistanceMethod.PLANAR,
                new SpatialPointClusterParameters.Hdbscan(2), "clusters", "cluster", "noise",
                new SpatialDbscanOptions(SpatialDbscanOptions.Mode.LINEAR,"missing_time",-1L,null), options);
    }

    @Test void linearFixedWeekThresholdIsInclusiveWithoutExtendingBeyondSevenDays() {
        var c = config(SpatialDbscanOptions.Mode.LINEAR, 1, 2, 1L);
        var weeks = new SpatialPointClusterConfiguration(c.sourceTableName(), c.pointGeometryColumnName(), c.featureIdColumnName(), c.distanceMethod(),
                c.parameters(), c.outputTableName(), c.clusterIdColumnName(), c.noiseColumnName(),
                new SpatialDbscanOptions(SpatialDbscanOptions.Mode.LINEAR, "time", 1L, SpatialDurationUnit.WEEKS));
        var rows = run(table(3857, List.of(row(1L, 0L, 0), row(2L, 604800L, 0), row(3L, 1209601L, 0))), weeks);
        assertEquals(3, rows.size()); assertNotNull(rows.getFirst().getAs("cluster"));
        assertEquals(rows.get(0).<Long>getAs("cluster"), rows.get(1).<Long>getAs("cluster"));
        assertNull(rows.get(2).getAs("cluster"));
    }

    @Test void linearConnectsAcrossArbitraryTimeBucketsAndSeparatesFarTimesAtSameLocation() {
        var c = config(SpatialDbscanOptions.Mode.LINEAR, 1, 2, 10L);
        var chain = table(3857, List.of(row(1L, 0L, 0), row(2L, 9L, 0), row(3L, 18L, 0), row(4L, 27L, 0)));
        var rows = run(new SparkCanvasTable(chain.schema(), chain.dataset().repartition(3)), c); assertEquals(4, rows.size()); assertEquals(1, clusters(rows).size()); assertTrue(rows.stream().noneMatch(r -> r.getBoolean(r.fieldIndex("noise"))));
        var separated = table(3857, List.of(row(1L, 0L, 0), row(2L, 1L, 0), row(3L, 100L, 0), row(4L, 101L, 0)));
        assertEquals(2, clusters(run(separated, c)).size());
        assertEquals(1, clusters(run(separated, config(SpatialDbscanOptions.Mode.SPATIAL, 1, 2, 10L))).size());
    }

    @Test void borderPointCannotJoinTwoCoreComponentsAndOriginalReservedColumnsSurvive() {
        var points = new ArrayList<Row>();
        double[] x = {-2.1, -2, -1.9, -1, 0, 1, 1.9, 2, 2.1};
        for (int i = 0; i < x.length; i++) points.add(row((long) i, 0L, x[i]));
        var rows = run(table(3857, points), config(SpatialDbscanOptions.Mode.SPATIAL, 1, 4, null));
        assertEquals(9, rows.size()); assertEquals(2, clusters(rows).size());
        assertNotEquals(rows.get(1).<Long>getAs("cluster"), rows.get(7).<Long>getAs("cluster"));
        assertFalse(rows.get(4).getBoolean(rows.get(4).fieldIndex("noise")));
        for (var row : rows) { assertEquals("original-id", row.getAs("id")); assertEquals("original-src", row.getAs("src")); assertEquals("original-dst", row.getAs("dst")); }
    }

    @Test void includesSelfAndDuplicatePositionsAndHandlesEmptyOrNoCoreInputs() {
        var points = table(3857, List.of(row(1L, 0L, 0), row(2L, 0L, 0), row(3L, 0L, 0)));
        assertEquals(1, clusters(run(points, config(SpatialDbscanOptions.Mode.SPATIAL, 1, 3, null))).size());
        var noise = run(points, config(SpatialDbscanOptions.Mode.SPATIAL, 1, 4, null));
        assertEquals(3, noise.size()); noise.forEach(r -> { assertNull(r.getAs("cluster")); assertTrue(r.getBoolean(r.fieldIndex("noise"))); });
        assertTrue(run(table(3857, List.of()), config(SpatialDbscanOptions.Mode.SPATIAL, 1, 2, null)).isEmpty());
    }

    @Test void exactDistanceAndDurationThresholdsAreInclusiveAndNullTimeIsExcluded() {
        var rows = run(table(3857, List.of(row(1L, 0L, 0), row(2L, 10L, 1), row(3L, 21L, 1), row(4L, null, 0))),
                config(SpatialDbscanOptions.Mode.LINEAR, 1, 2, 10L));
        assertEquals(3, rows.size()); assertEquals(rows.get(0).<Long>getAs("cluster"), rows.get(1).<Long>getAs("cluster"));
        assertNull(rows.get(2).getAs("cluster"));
    }

    @Test void geodesicNeighborsCrossDateLineButMustAlsoPassTimePredicate() {
        var original = config(SpatialDbscanOptions.Mode.LINEAR, 30_000, 2, 10L);
        var c = new SpatialPointClusterConfiguration("points", "shape", "feature_id", SpatialDistanceMethod.GEODESIC,
                new SpatialPointClusterParameters.Dbscan(30_000, SpatialDistanceUnit.METERS, 2), "clusters", "cluster", "noise", original.dbscan());
        var rows = run(table(4326, List.of(row(1L, 0L, 179.9), row(2L, 9L, -179.9), row(3L, 100L, 179.9))), c);
        assertEquals(rows.get(0).<Long>getAs("cluster"), rows.get(1).<Long>getAs("cluster")); assertNotNull(rows.get(0).getAs("cluster")); assertNull(rows.get(2).getAs("cluster"));
    }

    @Test void invalidIdentityFailsDuringExecutionForExplicitAndLegacyAlgorithms() {
        for (var mode : List.of(SpatialDbscanOptions.Mode.SPATIAL, SpatialDbscanOptions.Mode.LEGACY_SPATIAL)) {
            for (var rows : List.of(List.of(row(1L, 0L, 0), row(1L, 1L, 1)), List.of(row(null, 0L, 0)))) {
                var error = assertThrows(Exception.class, () -> run(table(3857, rows), config(mode, 1, 2, null)));
                assertTrue(error.getMessage().contains("SPATIAL_CLUSTER_FEATURE_ID_INVALID"));
            }
        }
    }

    @Test void skipsEmptyAndNullPointsButRejectsMalformedActualPointsSafely() {
        var empty = table(3857, List.of(RowFactory.create(1L, Timestamp.from(Instant.EPOCH), "id", "src", "dst", "POINT EMPTY"),
                RowFactory.create(2L, Timestamp.from(Instant.EPOCH), "id", "src", "dst", null), row(3L, 0L, 0)));
        var rows = run(empty, config(SpatialDbscanOptions.Mode.SPATIAL, 1, 2, null)); assertEquals(1, rows.size()); assertEquals(3L, rows.getFirst().<Long>getAs("feature_id"));
        var invalid = table(3857, List.of(RowFactory.create(1L, Timestamp.from(Instant.EPOCH), "id", "src", "dst", "LINESTRING (0 0, 1 1)")));
        var error = assertThrows(Exception.class, () -> run(invalid, config(SpatialDbscanOptions.Mode.SPATIAL, 1, 2, null)));
        assertTrue(error.getMessage().contains("SPATIAL_CLUSTER_POINT_INVALID"));
        Throwable cause = error; while (cause.getCause() != null) cause = cause.getCause();
        assertEquals("SPATIAL_CLUSTER_POINT_INVALID", cause.getMessage());
    }

    @Test void preflightDoesNotSubmitJobsAndPreservesCollectiveFieldLineage() {
        for (var configuration : List.of(config(SpatialDbscanOptions.Mode.LINEAR, 1, 2, 10L), hdbscan(new SpatialHdbscanOptions("p","o","e","s")))) {
        var raw = table(3857, List.of());
        var asset = new TaskLineageEvidence.Asset("input", TaskLineageEvidence.AssetRole.INPUT, TaskLineageEvidence.AssetKind.JDBC_TABLE,
                null, null, null, null, UUID.randomUUID(), null, null, "points", null, null, "points");
        var fields = new LinkedHashMap<String, CatalystLineageMetadata.InputField>();
        raw.schema().columns().forEach(c -> fields.put(c.name(), new CatalystLineageMetadata.InputField("input:" + c.name(), null)));
        var source = new SparkCanvasTable(raw.schema(), CatalystLineageMetadata.markInput(raw.dataset(), "input-node", asset, fields));
        String group = UUID.randomUUID().toString(); spark.sparkContext().setJobGroup(group, "preflight", false);
        var before = spark.sparkContext().getCheckpointDir();
        var issues = new Issues();
        var result = apply(source, configuration, issues, true);
        assertFalse(issues.hasErrors(), issues::toString);
        var table = result.propagatedTables().get("clusters"); table.dataset().queryExecution().analyzed();
        var output = new TaskLineageEvidence.Asset("output", TaskLineageEvidence.AssetRole.OUTPUT, TaskLineageEvidence.AssetKind.JDBC_TABLE,
                null, TaskLineageEvidence.WriteMode.APPEND, null, null, UUID.randomUUID(), null, null, "clusters", null, null, "clusters");
        var candidate = new CatalystLineageOutputCandidate("flow", "output-node", "JDBC_OUTPUT", "write", table.dataset(), output,
                table.schema().columns().stream().map(c -> new CatalystLineageOutputCandidate.TargetField("out:" + c.name(), null, c.name(), c.name(), TaskLineageEvidence.OutputEffect.WRITTEN_UNKNOWN_SOURCE)).toList());
        var flow = new CatalystLineageAnalyzer().analyze(List.of(candidate)).flows().getFirst();
        assertEquals(TaskLineageEvidence.Coverage.FIELD_COMPLETE, flow.coverage(), () -> flow.warnings().toString());
        for (CanvasColumnSchema column : raw.schema().columns()) {
            var direct = flow.fieldEdges().stream().filter(edge -> edge.target().localFieldKey().equals("out:" + column.name())
                    && edge.source().localFieldKey().equals("input:" + column.name())).toList();
            assertEquals(1, direct.size(), column.name());
            assertEquals(TaskLineageEvidence.DerivationType.DIRECT, direct.getFirst().derivationType(), column.name());
        }
        for (String target : configuration.hdbscan() == null ? List.of("cluster", "noise") : List.of("cluster", "noise", "p", "o", "e", "s")) {
            for (String field : configuration.hdbscan() == null ? List.of("shape", "time", "feature_id") : List.of("shape", "feature_id"))
                assertTrue(flow.fieldEdges().stream().anyMatch(e -> e.target().localFieldKey().equals("out:" + target) && e.source().localFieldKey().equals("input:" + field)), target + ":" + field);
            if (configuration.hdbscan() != null) assertFalse(flow.fieldEdges().stream().anyMatch(e -> e.target().localFieldKey().equals("out:" + target) && e.source().localFieldKey().equals("input:time")));
        }
        assertEquals(0, spark.sparkContext().statusTracker().getJobIdsForGroup(group).length);
        assertEquals(before, spark.sparkContext().getCheckpointDir()); spark.sparkContext().clearJobGroup();
        }
    }

    @Test void twentyThousandPointPreviewIsLazyAndSpatialDbscanExecutesWithoutDriverCollection() {
        SparkCanvasTable source = largePointTable(20_000);
        var configuration = config(SpatialDbscanOptions.Mode.SPATIAL, .25, 2, null);
        String previewGroup = "point-cluster-preview-" + UUID.randomUUID();
        spark.sparkContext().setJobGroup(previewGroup, "point cluster preview", false);
        try {
            var issues = new Issues();
            var preview = apply(source, configuration, issues, true);
            assertFalse(issues.hasErrors(), issues::toString);
            preview.propagatedTables().get("clusters").dataset().queryExecution().analyzed();
            assertEquals(0, spark.sparkContext().statusTracker().getJobIdsForGroup(previewGroup).length);
        } finally {
            spark.sparkContext().clearJobGroup();
        }

        var issues = new Issues();
        var result = apply(source, configuration, issues, false);
        assertFalse(issues.hasErrors(), issues::toString);
        Dataset<Row> clusters = result.propagatedTables().get("clusters").dataset();
        assertEquals(20_000, clusters.count());
        assertEquals(20_000, clusters.filter("noise").count());
        assertEquals(0, clusters.filter("cluster IS NOT NULL").count());
    }

    @Test void invalidTimeAndWrongGeometryMetadataAreStableCompilerIssuesNotExceptions() {
        var source = table(3857, List.of());
        for (var options : List.of(new SpatialDbscanOptions(null, "", null, null),
                new SpatialDbscanOptions(SpatialDbscanOptions.Mode.LINEAR, "missing", 1L, SpatialDurationUnit.SECONDS),
                new SpatialDbscanOptions(SpatialDbscanOptions.Mode.LINEAR, "id", 1L, SpatialDurationUnit.SECONDS),
                new SpatialDbscanOptions(SpatialDbscanOptions.Mode.LINEAR, "time", 0L, null))) {
            var base = config(SpatialDbscanOptions.Mode.SPATIAL, 1, 2, null);
            var c = new SpatialPointClusterConfiguration(base.sourceTableName(), "shape", "feature_id", base.distanceMethod(), base.parameters(), "clusters", "cluster", "noise", options);
            var issues = new Issues(); assertTrue(apply(source, c, issues, true).propagatedTables().isEmpty()); assertTrue(issues.hasErrors());
            assertTrue(issues.paths.stream().allMatch(p -> p.startsWith("configuration.dbscan")));
        }
        var wrong = new SpatialPointClusterConfiguration("points", "id", "feature_id", SpatialDistanceMethod.GEODESIC,
                new SpatialPointClusterParameters.Dbscan(1, SpatialDistanceUnit.METERS, 2), "clusters", null, null);
        var issues = new Issues(); assertTrue(apply(source, wrong, issues, true).propagatedTables().isEmpty()); assertTrue(issues.codes.contains("SPATIAL_POINT_XY_REQUIRED"));
    }

    private SpatialPointClusterConfiguration config(SpatialDbscanOptions.Mode mode, double radius, int minimum, Long duration) {
        return new SpatialPointClusterConfiguration("points", "shape", "feature_id", SpatialDistanceMethod.PLANAR,
                new SpatialPointClusterParameters.Dbscan(radius, SpatialDistanceUnit.SOURCE_CRS_UNIT, minimum), "clusters", "cluster", "noise",
                new SpatialDbscanOptions(mode, "time", duration, SpatialDurationUnit.SECONDS));
    }
    private Set<Long> clusters(List<Row> rows) { var ids = new HashSet<Long>(); for (var row : rows) if (row.getAs("cluster") != null) ids.add(row.getAs("cluster")); return ids; }
    private List<Row> run(SparkCanvasTable source, SpatialPointClusterConfiguration c) {
        var issues = new Issues(); var result = apply(source, c, issues, false); assertFalse(issues.hasErrors(), issues::toString);
        return result.propagatedTables().get("clusters").dataset().orderBy("feature_id").collectAsList();
    }
    private CanvasNodeOperationResult apply(SparkCanvasTable source, SpatialPointClusterConfiguration c, Issues issues, boolean preview) {
        var context = new CanvasNodeOperationContext(spark, MetadataIndex.create(new MetadataSnapshot(List.of(), List.of())), issues,
                new SchemaOnlyCanvasNodeDataAccess(spark), CanvasExecutionMode.BATCH,
                preview ? CanvasRuntimeValues.forPreview() : CanvasRuntimeValues.execution(UUID.randomUUID(), Instant.now()));
        return new SpatialPointClusterNodeOperator().apply(new SpatialPointClusterNodeDefinition(UUID.randomUUID().toString(), "聚类", new CanvasNodeLayout(0d,0d,352d,216d), c), Map.of("points", source), context);
    }
    private Row row(Long id, Long seconds, double x) { return RowFactory.create(id, seconds == null ? null : Timestamp.from(Instant.ofEpochSecond(seconds)), "original-id", "original-src", "original-dst", "POINT (" + x + " 0)"); }
    private SparkCanvasTable table(int epsg, List<Row> rows) {
        var columns = new ArrayList<>(List.of(field("feature_id", PlatformDataType.LONG), field("time", PlatformDataType.TIMESTAMP), field("id", PlatformDataType.STRING), field("src", PlatformDataType.STRING), field("dst", PlatformDataType.STRING), field("wkt", PlatformDataType.STRING)));
        var raw = spark.createDataFrame(rows, SparkTypeMapper.toStructType(columns));
        var data = raw.withColumn("shape", st_functions.ST_SetSRID(st_constructors.ST_GeomFromWKT(raw.col("wkt")), functions.lit(epsg)));
        columns.add(new CanvasColumnSchema("shape", PlatformDataType.GEOMETRY, null, null, null, true, null, false, false, null,
                new GeometryTypeDefinition(GeometryKind.POINT, new CrsReference("EPSG", epsg), CoordinateDimension.XY)));
        return new SparkCanvasTable(new CanvasTableSchema("points", null, columns, CanvasDatasetKind.BOUNDED, null, null), data);
    }
    private SparkCanvasTable largePointTable(long size) {
        Dataset<Row> data = spark.range(size).select(
                        functions.col("id").alias("feature_id"),
                        functions.expr("timestamp_seconds(id)").alias("time"),
                        functions.lit("original-id").alias("id"),
                        functions.lit("original-src").alias("src"),
                        functions.lit("original-dst").alias("dst"),
                        functions.concat(functions.lit("POINT ("), functions.col("id").multiply(2).cast("string"), functions.lit(" 0)")).alias("wkt"))
                .withColumn("shape", st_functions.ST_SetSRID(st_constructors.ST_Point(functions.col("feature_id").multiply(2).cast("double"), functions.lit(0d)), functions.lit(3857)));
        var columns = new ArrayList<>(List.of(field("feature_id", PlatformDataType.LONG), field("time", PlatformDataType.TIMESTAMP),
                field("id", PlatformDataType.STRING), field("src", PlatformDataType.STRING), field("dst", PlatformDataType.STRING), field("wkt", PlatformDataType.STRING)));
        columns.add(new CanvasColumnSchema("shape", PlatformDataType.GEOMETRY, null, null, null, true, null, false, false, null,
                new GeometryTypeDefinition(GeometryKind.POINT, new CrsReference("EPSG", 3857), CoordinateDimension.XY)));
        return new SparkCanvasTable(new CanvasTableSchema("points", null, columns, CanvasDatasetKind.BOUNDED, null, null), data);
    }
    private CanvasColumnSchema field(String name, PlatformDataType type) { return new CanvasColumnSchema(name, type, type == PlatformDataType.STRING ? 100 : null, null, null, true, null, false, false, null); }
    private static class Issues implements CanvasNodeIssueSink {
        final List<String> codes = new ArrayList<>(), paths = new ArrayList<>();
        public void error(String code, String message, String path) { codes.add(code); paths.add(path); }
        public void warning(String code, String message, String path) { }
        public boolean hasErrors() { return !codes.isEmpty(); }
        public String toString() { return codes.toString(); }
    }
}
