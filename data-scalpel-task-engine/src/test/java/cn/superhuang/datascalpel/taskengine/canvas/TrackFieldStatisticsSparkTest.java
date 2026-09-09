package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.*;
import cn.superhuang.data.scalpel.contract.type.*;
import cn.superhuang.datascalpel.taskengine.compiler.MetadataIndex;
import cn.superhuang.datascalpel.taskengine.spark.SedonaSparkSupport;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;
import cn.superhuang.datascalpel.taskengine.spark.SparkTypeMapper;
import org.apache.spark.sql.*;
import org.apache.spark.sql.sedona_sql.expressions.st_constructors;
import org.apache.spark.sql.sedona_sql.expressions.st_functions;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TrackFieldStatisticsSparkTest {
    private SparkSession spark;
    private static final CanvasNodeLayout LAYOUT = new CanvasNodeLayout(0d, 0d, 360d, 224d);
    private static final TrackBoundaryConfiguration BOUNDARIES = new TrackBoundaryConfiguration(null, null, null, null);

    @BeforeAll void start() {
        spark = SedonaSparkSupport.initialize(SedonaSparkSupport.builder().master("local[1]").appName("track-field-statistics")
                .config("spark.ui.enabled", false).config("spark.driver.host", "127.0.0.1").config("spark.driver.bindAddress", "127.0.0.1")
                .config("spark.sql.shuffle.partitions", 1).config("spark.sql.session.timeZone", "UTC").getOrCreate());
    }
    @AfterAll void stop() { if (spark != null) spark.stop(); }

    @Test void fieldsAndPromotedTypesAreCorrectInLegacyAndCurrentTrackAndDwellPlans() {
        var statistics = List.of(stat(TrackSummaryStatisticKind.COUNT, null, "count"),
                stat(TrackSummaryStatisticKind.COUNT_FIELD, "label", "labels"), stat(TrackSummaryStatisticKind.COUNT_FIELD, "amount", "amounts"),
                stat(TrackSummaryStatisticKind.ANY, "label", "sample"), stat(TrackSummaryStatisticKind.SUM, "value", "total"),
                stat(TrackSummaryStatisticKind.SUM, "amount", "money"), stat(TrackSummaryStatisticKind.MEAN, "amount", "average"),
                stat(TrackSummaryStatisticKind.FIRST, "label", "first"), stat(TrackSummaryStatisticKind.LAST, "label", "last"));
        for (boolean current : List.of(false, true)) {
            for (boolean dwell : List.of(false, true)) {
                var output = apply(source(false, false), node(dwell, current, statistics, TrackDwellResultMode.MEAN_CENTERS));
                var row = output.dataset().head();
                assertEquals(4L, row.<Long>getAs("count")); assertEquals(2L, row.<Long>getAs("labels"));
                assertEquals(2L, row.<Long>getAs("amounts")); assertTrue(Set.of("", "sample").contains(row.<String>getAs("sample")));
                assertEquals(2147483648L, row.<Long>getAs("total")); assertEquals(new BigDecimal("3.50"), row.getAs("money"));
                assertNull(row.getAs("first")); assertNull(row.getAs("last"));
                for (String name : List.of("total", "money", "average", "labels", "sample")) {
                    var column = output.schema().columns().stream().filter(c -> c.name().equals(name)).findFirst().orElseThrow();
                    assertEquals(output.dataset().schema().apply(name).dataType(), SparkTypeMapper.toStructType(List.of(column)).apply(name).dataType());
                }
            }
        }
    }

    @Test void emptyInputsAndAllNullGroupsDoNotFabricateSamples() {
        var statistics = List.of(stat(TrackSummaryStatisticKind.COUNT_FIELD, "label", "labels"), stat(TrackSummaryStatisticKind.ANY, "label", "sample"),
                stat(TrackSummaryStatisticKind.STDDEV, "value", "deviation"), stat(TrackSummaryStatisticKind.VARIANCE, "value", "variance"));
        for (boolean dwell : List.of(false, true)) {
            var definition = node(dwell, true, statistics, TrackDwellResultMode.MEAN_CENTERS);
            assertEquals(0, apply(source(true, true), definition).dataset().count());
            var row = apply(source(false, true), definition).dataset().head();
            assertEquals(0L, row.<Long>getAs("labels")); assertNull(row.getAs("sample"));
            assertNull(row.getAs("deviation")); assertNull(row.getAs("variance"));
        }
    }

    @Test void dwellAnyAcceptsNumericButReconstructAnyRejectsIt() {
        var statistics = List.of(stat(TrackSummaryStatisticKind.ANY, "amount", "sample"));
        for (boolean current : List.of(false, true)) {
            var output = apply(source(false, false), node(true, current, statistics, TrackDwellResultMode.CONVEX_HULLS));
            var sample = output.dataset().head().<BigDecimal>getAs("sample");
            assertTrue(Set.of(new BigDecimal("1.25"), new BigDecimal("2.25")).contains(sample));
            assertEquals(PlatformDataType.DECIMAL, output.schema().columns().stream().filter(c -> c.name().equals("sample")).findFirst().orElseThrow().fieldType());
            var issues = invalid(source(false, false), node(false, current, statistics, TrackDwellResultMode.MEAN_CENTERS));
            assertTrue(issues.codes.contains("TRACK_SUMMARY_ANY_FIELD_NOT_SUPPORTED"));
            assertTrue(issues.paths.contains("configuration.summaryStatistics[0].sourceColumnName"));
        }
    }

    @Test void fieldValidationDoesNotReturnPartialResults() {
        for (String field : List.of("missing", "", "time", "shape")) {
            var issues = invalid(source(false, false), node(true, true, List.of(stat(TrackSummaryStatisticKind.COUNT, null, "count"),
                    stat(TrackSummaryStatisticKind.ANY, field, "sample")), TrackDwellResultMode.MEAN_CENTERS));
            assertTrue(issues.paths.contains("configuration.summaryStatistics[1].sourceColumnName"));
        }
    }

    @Test void pointOutputsKeepInactiveSummaryDraftsWithoutAddingSummaryColumns() {
        for (var mode : List.of(TrackDwellResultMode.DWELL_FEATURES, TrackDwellResultMode.ALL_FEATURES)) {
            var table = apply(source(false, false), node(true, true, List.of(stat(TrackSummaryStatisticKind.ANY, "missing", "inactive")), mode));
            assertEquals(4, table.dataset().count());
            assertFalse(Arrays.asList(table.dataset().columns()).contains("inactive"));
            assertTrue(Arrays.asList(table.dataset().columns()).contains("label"));
        }
    }

    private CanvasNodeDefinition node(boolean dwell, boolean current, List<TrackSummaryStatistic> statistics, TrackDwellResultMode mode) {
        if (dwell) return new TrackFindDwellNodeDefinition(UUID.randomUUID().toString(), "驻留", LAYOUT,
                new TrackFindDwellConfiguration("points", "shape", List.of("track"), "time", SpatialDistanceMethod.PLANAR,
                        10, SpatialDistanceUnit.METERS, 1, SpatialDurationUnit.SECONDS, BOUNDARIES, statistics, DwellGeometryKind.CENTROID,
                        "result", "dwell_id", "start", "end", "duration", "points", "geometry",
                        current ? TrackDwellSemantics.REFERENCE_CENTER : null,
                        current ? new TrackDwellRangeOptions(mode, List.of(), SpatialDurationUnit.MILLISECONDS, "mean_distance", SpatialDistanceUnit.METERS, "is_dwell") : null));
        return new TrackReconstructNodeDefinition(UUID.randomUUID().toString(), "重建", LAYOUT,
                new TrackReconstructConfiguration("points", "shape", List.of("track"), "time", SpatialDistanceMethod.PLANAR, BOUNDARIES,
                        statistics, "result", "geometry", "start", "end", "points", current
                        ? new TrackReconstructOptions(TrackReconstructSemantics.ORDERED_SEGMENTS, List.of(), TrackSplitBoundaryOption.GAP, null) : null));
    }
    private SparkCanvasTable apply(SparkCanvasTable source, CanvasNodeDefinition node) {
        var issues = new Issues(); var result = operator(node).apply(node, Map.of("points", source), context(issues));
        assertFalse(issues.hasErrors(), issues::toString); assertSame(source, result.propagatedTables().get("points"));
        return result.propagatedTables().get("result");
    }
    private Issues invalid(SparkCanvasTable source, CanvasNodeDefinition node) {
        var issues = new Issues(); var result = operator(node).apply(node, Map.of("points", source), context(issues));
        assertTrue(issues.hasErrors()); assertTrue(result.propagatedTables().isEmpty()); return issues;
    }
    private CanvasNodeOperator operator(CanvasNodeDefinition node) { return node instanceof TrackFindDwellNodeDefinition ? new TrackFindDwellNodeOperator() : new TrackReconstructNodeOperator(); }
    private TrackSummaryStatistic stat(TrackSummaryStatisticKind kind, String source, String output) { return new TrackSummaryStatistic(UUID.randomUUID().toString(), kind, source, output); }
    private CanvasColumnSchema field(String name, PlatformDataType type) { return new CanvasColumnSchema(name, type, type == PlatformDataType.STRING ? 100 : null, null, null, true, null, false, false, null); }
    private SparkCanvasTable source(boolean empty, boolean allNull) {
        var columns = new ArrayList<>(List.of(field("track", PlatformDataType.STRING), field("time", PlatformDataType.TIMESTAMP),
                field("label", PlatformDataType.STRING), field("value", PlatformDataType.INTEGER),
                new CanvasColumnSchema("amount", PlatformDataType.DECIMAL, null, 12, 2, true, null, false, false, null), field("wkt", PlatformDataType.STRING)));
        List<Row> rows = new ArrayList<>();
        if (!empty) for (int i = 0; i < 4; i++) rows.add(RowFactory.create("A", Timestamp.from(Instant.ofEpochSecond(i)),
                allNull || i == 0 || i == 3 ? null : i == 1 ? "" : "sample", allNull || i == 0 || i == 3 ? null : i == 1 ? Integer.MAX_VALUE : 1,
                allNull || i == 0 || i == 3 ? null : new BigDecimal(i == 1 ? "1.25" : "2.25"), "POINT (" + i + " 0)"));
        var raw = spark.createDataFrame(rows, SparkTypeMapper.toStructType(columns));
        var data = raw.withColumn("shape", st_functions.ST_SetSRID(st_constructors.ST_GeomFromWKT(raw.col("wkt")), functions.lit(3857)));
        columns.add(new CanvasColumnSchema("shape", PlatformDataType.GEOMETRY, null, null, null, true, null, false, false, null,
                new GeometryTypeDefinition(GeometryKind.POINT, new CrsReference("EPSG", 3857), CoordinateDimension.XY)));
        return new SparkCanvasTable(new CanvasTableSchema("points", null, columns, CanvasDatasetKind.BOUNDED, null, null), data);
    }
    private CanvasNodeOperationContext context(Issues issues) { return new CanvasNodeOperationContext(spark, MetadataIndex.create(new MetadataSnapshot(List.of(), List.of())), issues, new SchemaOnlyCanvasNodeDataAccess(spark)); }
    private static class Issues implements CanvasNodeIssueSink {
        final List<String> codes = new ArrayList<>(), paths = new ArrayList<>();
        public void error(String code, String message, String path) { codes.add(code); paths.add(path); }
        public void warning(String code, String message, String path) { }
        public boolean hasErrors() { return !codes.isEmpty(); }
        public String toString() { return codes + " " + paths; }
    }
}
