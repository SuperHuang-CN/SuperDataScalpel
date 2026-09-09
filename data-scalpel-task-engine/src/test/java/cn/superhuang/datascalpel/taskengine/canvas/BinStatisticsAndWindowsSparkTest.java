package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.*;
import cn.superhuang.data.scalpel.contract.type.*;
import cn.superhuang.datascalpel.taskengine.compiler.MetadataIndex;
import cn.superhuang.datascalpel.taskengine.compiler.lineage.CatalystLineageAnalyzer;
import cn.superhuang.datascalpel.taskengine.compiler.lineage.CatalystLineageMetadata;
import cn.superhuang.datascalpel.taskengine.compiler.lineage.CatalystLineageOutputCandidate;
import cn.superhuang.datascalpel.taskengine.spark.SedonaSparkSupport;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;
import cn.superhuang.datascalpel.taskengine.spark.SparkTypeMapper;
import org.apache.spark.sql.*;
import org.apache.spark.sql.sedona_sql.expressions.st_constructors;
import org.apache.spark.sql.sedona_sql.expressions.st_functions;
import org.junit.jupiter.api.*;
import java.sql.Timestamp;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BinStatisticsAndWindowsSparkTest {
    private SparkSession spark;
    private static final CanvasNodeLayout LAYOUT = new CanvasNodeLayout(0d, 0d, 360d, 224d);

    @BeforeAll void start() {
        spark = SedonaSparkSupport.initialize(SedonaSparkSupport.builder().master("local[1]").appName("bin-statistics-windows")
                .config("spark.ui.enabled", false).config("spark.driver.host", "127.0.0.1").config("spark.driver.bindAddress", "127.0.0.1")
                .config("spark.sql.shuffle.partitions", 1).config("spark.sql.session.timeZone", "UTC").getOrCreate());
    }
    @AfterAll void stop() { if (spark != null) spark.stop(); }

    @Test void calendarBinsProduceRealMonthBoundariesOnceAndOnlyObservedWindows() {
        var points = geometry("points", 3857, GeometryKind.POINT, List.of(field("event_time", PlatformDataType.TIMESTAMP)),
                List.of(RowFactory.create(Timestamp.from(java.time.Instant.parse("2024-03-15T00:00:00Z")), "POINT (0 0)"),
                        RowFactory.create(null, "POINT (0 0)")));
        var time = CalendarTimeWindowsTest.config("2024-01-31T00:00:00Z", "UTC", 2, 1L,
                SpatialCalendarWindowOptions.Unit.MONTHS, SpatialCalendarWindowOptions.Unit.MONTHS);
        for (boolean empty : List.of(false, true)) {
            var rows = bins(points, SpatialBinShape.SQUARE, List.of(stat(SpatialBinStatisticKind.COUNT, null, "count")), empty, time)
                    .dataset().orderBy("start").collectAsList();
            assertEquals(2, rows.size());
            assertEquals(List.of("2024-01-31T00:00:00Z", "2024-02-29T00:00:00Z"), rows.stream().map(r -> r.<Timestamp>getAs("start").toInstant().toString()).toList());
            assertEquals(List.of("2024-03-31T00:00:00Z", "2024-04-30T00:00:00Z"), rows.stream().map(r -> r.<Timestamp>getAs("end").toInstant().toString()).toList());
            rows.forEach(r -> assertEquals(1L, r.<Long>getAs("count")));
        }
        var none = geometry("points", 3857, GeometryKind.POINT, List.of(field("event_time", PlatformDataType.TIMESTAMP)), List.of());
        assertEquals(0, scopedBins(none, scopedConfig(SpatialBinShape.SQUARE,
                new SpatialPlanarGridOptions(0d, 0d, extent(0, 0, 20, 20)), true, time)).dataset().count());
    }

    @Test void calendarWithinRespectsDstAndKeepsEmptyAreaWindowsWithoutInventingDates() {
        var points = geometry("summaries", 3857, GeometryKind.POINT, List.of(field("event_time", PlatformDataType.TIMESTAMP)),
                List.of(RowFactory.create(Timestamp.from(java.time.Instant.parse("2024-03-10T12:00:00Z")), "POINT (0 0)")));
        var areas = geometry("areas", 3857, GeometryKind.POLYGON, List.of(field("id", PlatformDataType.LONG)), List.of(
                RowFactory.create(1L, "POLYGON ((-1 -1, 2 -1, 2 2, -1 2, -1 -1))"),
                RowFactory.create(2L, "POLYGON ((20 20, 30 20, 30 30, 20 30, 20 20))")));
        var time = CalendarTimeWindowsTest.config("2024-01-01T00:00:00-05:00", "America/New_York", 1, null,
                SpatialCalendarWindowOptions.Unit.DAYS, null);
        var config = new SpatialSummarizeWithinConfiguration("areas", "shape", "summaries", "shape", true,
                SpatialDistanceMethod.PLANAR, SpatialDistanceUnit.METERS, SpatialAreaUnit.SQUARE_METERS,
                List.of(new JoinOutputColumn(JoinOutputColumnSource.LEFT, "id", "id", true)),
                List.of(new SpatialWithinStatistic(UUID.randomUUID().toString(), SpatialWithinStatisticKind.COUNT, null, "count")), null, time, "within");
        var issues = new Issues();
        var result = new SpatialSummarizeWithinNodeOperator().apply(new SpatialSummarizeWithinNodeDefinition(UUID.randomUUID().toString(), "区域", LAYOUT, config),
                Map.of("areas", areas, "summaries", points), context(issues));
        assertFalse(issues.hasErrors(), issues::toString);
        var rows = result.propagatedTables().get("within").dataset().orderBy("id").collectAsList();
        assertEquals(List.of(1L, 0L), rows.stream().map(r -> r.<Long>getAs("count")).toList());
        rows.forEach(r -> assertEquals(23 * 3600_000L, r.<Timestamp>getAs("end").getTime() - r.<Timestamp>getAs("start").getTime()));
    }

    @Test void calendarLineageRemainsCompleteForOccupiedAndEmptyBins() {
        var time = CalendarTimeWindowsTest.config("2024-01-31T00:00:00Z", "UTC", 2, 1L,
                SpatialCalendarWindowOptions.Unit.MONTHS, SpatialCalendarWindowOptions.Unit.MONTHS);
        for (boolean empty : List.of(false, true)) assertCompleteBinLineage(SpatialBinShape.SQUARE, empty, 1, null, time);
    }

    @Test void calendarUdfUsesMicrosecondsRegardlessOfSparkJavaDatetimeApi() {
        String previous = spark.conf().get("spark.sql.datetime.java8API.enabled");
        try {
            spark.conf().set("spark.sql.datetime.java8API.enabled", true);
            var issues = new Issues();
            var time = CalendarTimeWindowsTest.config("2024-01-31T00:00:00Z", "UTC", 1, null, SpatialCalendarWindowOptions.Unit.MONTHS, null);
            var parameters = SpatialTemporalSupport.validateAndResolve(time, "time", issues);
            assertFalse(issues.hasErrors());
            long micros = CalendarTimeWindows.micros(java.time.Instant.parse("2024-03-15T00:00:00Z"));
            var source = spark.range(1).select(functions.timestamp_micros(functions.lit(micros)).alias("event_time"));
            var row = SpatialTemporalSupport.addWindows(source, source.col("event_time"), parameters, "start", "end").head();
            assertEquals(java.time.Instant.parse("2024-02-29T00:00:00Z"), row.getAs("start"));
            assertEquals(java.time.Instant.parse("2024-03-31T00:00:00Z"), row.getAs("end"));
        } finally { spark.conf().set("spark.sql.datetime.java8API.enabled", previous); }
    }

    @Test void calendarCandidateLimitFailsLazilyThroughSparkWithoutObservedTimeInCause() {
        var issues = new Issues();
        var time = CalendarTimeWindowsTest.config("2024-01-31T00:00:00Z", "UTC", 1, 1L,
                SpatialCalendarWindowOptions.Unit.MONTHS, SpatialCalendarWindowOptions.Unit.SECONDS);
        var parameters = SpatialTemporalSupport.validateAndResolve(time, "time", issues);
        assertFalse(issues.hasErrors());
        long micros = CalendarTimeWindows.micros(java.time.Instant.parse("2024-03-15T00:00:00Z"));
        var source = spark.range(1).select(functions.timestamp_micros(functions.lit(micros)).alias("event_time"));
        var plan = SpatialTemporalSupport.addWindows(source, source.col("event_time"), parameters, "start", "end");
        var error = assertThrows(Exception.class, plan::collectAsList);
        assertTrue(error.getMessage().contains("SPATIAL_CALENDAR_WINDOW_LIMIT_EXCEEDED"));
        Throwable cause = error; while (cause.getCause() != null) cause = cause.getCause();
        assertEquals("SPATIAL_CALENDAR_WINDOW_LIMIT_EXCEEDED", cause.getMessage());
    }

    @Test void linkedWithinCalendarMainAndGroupResultsShareWindowIdentityAndTimeLineage() {
        var points = geometry("summaries", 3857, GeometryKind.POINT,
                List.of(field("event_time", PlatformDataType.TIMESTAMP), field("category", PlatformDataType.STRING)),
                List.of(RowFactory.create(Timestamp.from(java.time.Instant.parse("2024-03-15T00:00:00Z")), "a", "POINT (0 0)"),
                        RowFactory.create(Timestamp.from(java.time.Instant.parse("2024-03-15T00:00:00Z")), "b", "POINT (0 0)")));
        var areas = geometry("areas", 3857, GeometryKind.POLYGON, List.of(field("id", PlatformDataType.LONG)),
                List.of(RowFactory.create(1L, "POLYGON ((-1 -1, 2 -1, 2 2, -1 2, -1 -1))")));
        Map<String, SparkCanvasTable> inputs = new LinkedHashMap<>();
        for (var table : List.of(areas, points)) {
            String key = table.schema().name();
            var asset = new TaskLineageEvidence.Asset(key, TaskLineageEvidence.AssetRole.INPUT, TaskLineageEvidence.AssetKind.JDBC_TABLE,
                    null, null, null, null, UUID.randomUUID(), null, null, key, null, null, key);
            var fields = new LinkedHashMap<String, CatalystLineageMetadata.InputField>();
            for (var column : table.schema().columns()) fields.put(column.name(), new CatalystLineageMetadata.InputField(key + ":" + column.name(), null));
            inputs.put(key, new SparkCanvasTable(table.schema(), CatalystLineageMetadata.markInput(table.dataset(), key + "-node", asset, fields)));
        }
        var time = CalendarTimeWindowsTest.config("2024-01-31T00:00:00Z", "UTC", 2, 1L,
                SpatialCalendarWindowOptions.Unit.MONTHS, SpatialCalendarWindowOptions.Unit.MONTHS);
        var config = new SpatialSummarizeWithinConfiguration("areas", "shape", "summaries", "shape", true,
                SpatialDistanceMethod.PLANAR, SpatialDistanceUnit.METERS, SpatialAreaUnit.SQUARE_METERS,
                List.of(new JoinOutputColumn(JoinOutputColumnSource.LEFT, "id", "original_id", true)),
                List.of(new SpatialWithinStatistic(UUID.randomUUID().toString(), SpatialWithinStatisticKind.COUNT, null, "count")),
                new SpatialGroupSummary("category", false, false, "minority", "majority", "percentage"), time, "within",
                new SpatialWithinGroupResult("id", "area_id", "groups", "category", null, null, null, null));
        var issues = new Issues();
        var result = new SpatialSummarizeWithinNodeOperator().apply(new SpatialSummarizeWithinNodeDefinition(UUID.randomUUID().toString(), "区域", LAYOUT, config), inputs, context(issues));
        assertFalse(issues.hasErrors(), issues::toString);
        for (String name : List.of("within", "groups")) {
            var table = result.propagatedTables().get(name);
            var rows = table.dataset().collectAsList(); assertEquals(name.equals("within") ? 2 : 4, rows.size());
            rows.forEach(r -> assertEquals(name.equals("within") ? 2L : 1L, r.<Long>getAs("count")));
            var output = new TaskLineageEvidence.Asset(name, TaskLineageEvidence.AssetRole.OUTPUT, TaskLineageEvidence.AssetKind.JDBC_TABLE,
                    null, TaskLineageEvidence.WriteMode.APPEND, null, null, UUID.randomUUID(), null, null, name, null, null, name);
            var candidate = new CatalystLineageOutputCandidate(name, "output-node", "JDBC_OUTPUT", "write", table.dataset(), output,
                    table.schema().columns().stream().map(c -> new CatalystLineageOutputCandidate.TargetField(name + ":" + c.name(), null, c.name(), c.name(), TaskLineageEvidence.OutputEffect.WRITTEN_UNKNOWN_SOURCE)).toList());
            var flow = new CatalystLineageAnalyzer().analyze(List.of(candidate)).flows().getFirst();
            assertEquals(TaskLineageEvidence.Coverage.FIELD_COMPLETE, flow.coverage(), () -> flow.warnings().toString());
            for (String field : List.of("start", "end")) assertTrue(flow.fieldEdges().stream().anyMatch(e ->
                    e.target().localFieldKey().equals(name + ":" + field) && e.source().localFieldKey().equals("summaries:event_time")), name + ":" + field);
        }
    }

    @Test void explicitSquareScopeUsesShiftedOriginAndHalfOpenPointBoundsWithoutClipping() {
        var points = geometry("points", 3857, GeometryKind.POINT, List.of(), List.of(
                RowFactory.create("POINT (-5 -5)"), RowFactory.create("POINT (5 5)"),
                RowFactory.create("POINT (14.999 14.999)"), RowFactory.create("POINT (15 5)"),
                RowFactory.create("POINT (5 15)"), RowFactory.create("POINT (-5.001 0)")));
        var grid = new SpatialPlanarGridOptions(-5d, -5d, extent(-5, -5, 15, 15));
        var output = scopedBins(points, scopedConfig(SpatialBinShape.SQUARE, grid, true, null));
        var rows = output.dataset().orderBy("bin_id").collectAsList();
        assertEquals(4, rows.size());
        assertEquals(List.of(1L, 0L, 0L, 2L), rows.stream().map(r -> r.<Long>getAs("count")).toList());
        for (Row row : rows) {
            org.locationtech.jts.geom.Geometry g = row.getAs("bin_shape");
            assertEquals(100d, g.getArea(), 1e-9); assertEquals(3857, g.getSRID());
            assertTrue(row.<String>getAs("bin_id").startsWith("SQUARE:" + PlanarGridSupport.identity(grid, SpatialBinShape.SQUARE, 10, 3857).substring(7)));
        }
        var narrower = new SpatialPlanarGridOptions(-5d, -5d, extent(-4, -4, 14, 14));
        var other = scopedBins(points, scopedConfig(SpatialBinShape.SQUARE, narrower, true, null)).dataset().orderBy("bin_id").collectAsList();
        assertEquals(rows.stream().map(r -> r.<String>getAs("bin_id")).toList(), other.stream().map(r -> r.<String>getAs("bin_id")).toList());
        assertEquals(-5d, other.getFirst().<org.locationtech.jts.geom.Geometry>getAs("bin_shape").getEnvelopeInternal().getMinX());
    }

    @Test void explicitEmptyExtentProducesCellsWithoutInventingTimeWindows() {
        var empty = geometry("points", 3857, GeometryKind.POINT, List.of(field("event_time", PlatformDataType.TIMESTAMP)), List.of());
        var grid = new SpatialPlanarGridOptions(0d, 0d, extent(0, 0, 20, 10));
        var result = scopedBins(empty, scopedConfig(SpatialBinShape.SQUARE, grid, true, null));
        var rows = result.dataset().collectAsList(); assertEquals(2, rows.size());
        rows.forEach(r -> assertEquals(0L, r.<Long>getAs("count")));
        assertEquals(0, scopedBins(empty, scopedConfig(SpatialBinShape.SQUARE, grid, false, null)).dataset().count());
        assertEquals(0, scopedBins(empty, scopedConfig(SpatialBinShape.SQUARE, grid, true, gaps())).dataset().count());
        var observed = geometry("points", 3857, GeometryKind.POINT, List.of(field("event_time", PlatformDataType.TIMESTAMP)),
                List.of(RowFactory.create(Timestamp.from(java.time.Instant.ofEpochSecond(2)), "POINT (1 1)"),
                        RowFactory.create(Timestamp.from(java.time.Instant.ofEpochSecond(12)), "POINT (100 100)")));
        var timed = scopedBins(observed, scopedConfig(SpatialBinShape.SQUARE, grid, true, gaps())).dataset().collectAsList();
        assertEquals(2, timed.size());
        timed.forEach(r -> assertEquals(2000, r.<Timestamp>getAs("start").getTime()));
    }

    @Test void explicitHexExtentHasCompleteShapesAndConsistentShiftedMembership() throws Exception {
        var empty = geometry("points", 3857, GeometryKind.POINT, List.of(), List.of());
        var grid = new SpatialPlanarGridOptions(123d, -87d, extent(118, -92, 128, -82));
        var cells = scopedBins(empty, scopedConfig(SpatialBinShape.HEXAGON, grid, true, null)).dataset().collectAsList();
        assertEquals(1, cells.size());
        org.locationtech.jts.geom.Geometry hex = cells.getFirst().getAs("bin_shape");
        assertEquals(123, hex.getCentroid().getX(), 1e-9); assertEquals(-87, hex.getCentroid().getY(), 1e-9);
        assertEquals(150 * Math.sqrt(3), hex.getArea(), 1e-8);
        var wide = new SpatialPlanarGridOptions(123d, -87d, extent(90, -120, 160, -50));
        var all = scopedBins(empty, scopedConfig(SpatialBinShape.HEXAGON, wide, true, null)).dataset().collectAsList();
        var box = new org.locationtech.jts.io.WKTReader().read("POLYGON ((90 -120,160 -120,160 -50,90 -50,90 -120))");
        for (Row row : all) {
            org.locationtech.jts.geom.Geometry shape = row.getAs("bin_shape");
            assertTrue(shape.intersection(box).getArea() > 0); assertEquals(hex.getArea(), shape.getArea(), 1e-8);
        }
        var points = geometry("points", 3857, GeometryKind.POINT, List.of(), List.of(RowFactory.create("POINT (123 -87)")));
        var occupied = scopedBins(points, scopedConfig(SpatialBinShape.HEXAGON, wide, false, null)).dataset().head();
        assertEquals(cells.getFirst().<String>getAs("bin_id"), occupied.<String>getAs("bin_id"));
    }

    @Test void hexBoundaryPointCountsAreTheSameWithOrWithoutEmptyCells() {
        // The min-X boundary goes through a hex vertex. Tie rounding can select the outside cell.
        var grid = new SpatialPlanarGridOptions(0d, 0d, extent(10, 0, 40, 40));
        var points = geometry("points", 3857, GeometryKind.POINT, List.of(), List.of(
                RowFactory.create("POINT (10 0)"), RowFactory.create("POINT (10 17.32050807568877)"),
                RowFactory.create("POINT (25 25.98076211353316)"), RowFactory.create("POINT (39.999 39.999)")));
        var occupied = scopedBins(points, scopedConfig(SpatialBinShape.HEXAGON, grid, false, null)).dataset().collectAsList();
        var expanded = scopedBins(points, scopedConfig(SpatialBinShape.HEXAGON, grid, true, null)).dataset().collectAsList();
        assertEquals(4, occupied.stream().mapToLong(r -> r.<Long>getAs("count")).sum());
        assertEquals(4, expanded.stream().mapToLong(r -> r.<Long>getAs("count")).sum());
        for (var row : occupied) assertTrue(expanded.stream().anyMatch(e -> e.<String>getAs("bin_id").equals(row.<String>getAs("bin_id"))
                && e.<Long>getAs("count").equals(row.<Long>getAs("count"))));
        double edgeY = -5 * Math.sqrt(3);
        var edgeGrid = new SpatialPlanarGridOptions(0d, 0d, extent(-20, edgeY, 20, 40));
        var edge = geometry("points", 3857, GeometryKind.POINT, List.of(), List.of(RowFactory.create("POINT (0 " + edgeY + ")")));
        var edgeBins = scopedBins(edge, scopedConfig(SpatialBinShape.HEXAGON, edgeGrid, true, null)).dataset().collectAsList();
        assertEquals(1, edgeBins.stream().mapToLong(r -> r.<Long>getAs("count")).sum());
    }

    @Test void planarScopeDraftsAndCandidateLimitsFailBeforePropagationButH3IgnoresInactiveOptions() {
        var empty = geometry("points", 3857, GeometryKind.POINT, List.of(), List.of());
        var invalid = List.of(new SpatialPlanarGridOptions(null, 0d, null),
                new SpatialPlanarGridOptions(0d, Double.NaN, null), new SpatialPlanarGridOptions(0d, 0d, extent(5, 0, 5, 10)),
                new SpatialPlanarGridOptions(0d, 0d, extent(0, 0, 1e8, 1e8)));
        for (var grid : invalid) {
            var issues = new Issues();
            var result = new SpatialBinAggregateNodeOperator().apply(node(scopedConfig(SpatialBinShape.SQUARE, grid, true, null)), Map.of("points", empty), context(issues));
            assertTrue(issues.hasErrors()); assertTrue(result.propagatedTables().isEmpty());
            assertTrue(issues.paths.stream().allMatch(p -> p.startsWith("configuration.planarGrid")));
        }
        var sphere = geometry("points", 4326, GeometryKind.POINT, List.of(), List.of(RowFactory.create("POINT (0 0)")));
        assertEquals(1, scopedBins(sphere, scopedConfig(SpatialBinShape.H3, invalid.getFirst(), false, null)).dataset().count());
        assertNotEquals(PlanarGridSupport.identity(new SpatialPlanarGridOptions(0d, 0d, null), SpatialBinShape.SQUARE, 10, 3857),
                PlanarGridSupport.identity(new SpatialPlanarGridOptions(1d, 0d, null), SpatialBinShape.SQUARE, 10, 3857));
        assertNotEquals(PlanarGridSupport.identity(new SpatialPlanarGridOptions(0d, 0d, null), SpatialBinShape.SQUARE, 10, 3857),
                PlanarGridSupport.identity(new SpatialPlanarGridOptions(0d, 0d, null), SpatialBinShape.SQUARE, 20, 3857));
    }

    private SpatialPlanarGridOptions.Extent extent(double minX, double minY, double maxX, double maxY) {
        return new SpatialPlanarGridOptions.Extent(SpatialPlanarGridOptions.ExtentMode.EXPLICIT_BOUNDS, minX, minY, maxX, maxY);
    }
    private SpatialBinAggregateConfiguration scopedConfig(SpatialBinShape shape, SpatialPlanarGridOptions grid, boolean empty, SpatialTemporalSlicing time) {
        var base = config(shape, List.of(stat(SpatialBinStatisticKind.COUNT, null, "count")), empty, time);
        return new SpatialBinAggregateConfiguration(base.sourceTableName(), base.pointGeometryColumnName(), base.binShape(), base.binSize(),
                base.binSizeUnit(), base.includeEmptyBins(), base.statistics(), base.groupSummary(), base.temporalSlicing(), base.outputTableName(),
                base.binIdColumnName(), base.binGeometryColumnName(), base.binSizeSemantics(), base.h3(), grid);
    }
    private SparkCanvasTable scopedBins(SparkCanvasTable points, SpatialBinAggregateConfiguration c) {
        var issues = new Issues();
        var result = new SpatialBinAggregateNodeOperator().apply(node(c), Map.of("points", points), context(issues));
        assertFalse(issues.hasErrors(), issues::toString); assertSame(points, result.propagatedTables().get("points"));
        return result.propagatedTables().get("bins");
    }

    @Test void countsNonNullValuesAndSamplesStringsAcrossAllGridShapes() {
        for (var shape : SpatialBinShape.values()) {
            var points = geometry("points", shape == SpatialBinShape.H3 ? 4326 : 3857, GeometryKind.POINT,
                    List.of(field("label", PlatformDataType.STRING), field("value", PlatformDataType.INTEGER)), List.of(
                    RowFactory.create(null, null, "POINT (0 0)"), RowFactory.create("", 1, "POINT (0 0)"),
                    RowFactory.create("sample", 2, "POINT (0 0)"), RowFactory.create("sample", 3, "POINT (0 0)")));
            var statistics = List.of(stat(SpatialBinStatisticKind.COUNT, null, "count"), stat(SpatialBinStatisticKind.COUNT_FIELD, "label", "labels"),
                    stat(SpatialBinStatisticKind.COUNT_FIELD, "value", "values"), stat(SpatialBinStatisticKind.ANY, "label", "sample"));
            var result = bins(points, shape, statistics, false, null);
            var row = result.dataset().head();
            assertEquals(4L, row.<Long>getAs("count")); assertEquals(3L, row.<Long>getAs("labels")); assertEquals(3L, row.<Long>getAs("values"));
            assertTrue(Set.of("", "sample").contains(row.<String>getAs("sample")));
            assertEquals(PlatformDataType.LONG, result.schema().columns().stream().filter(c -> c.name().equals("labels")).findFirst().orElseThrow().fieldType());
            assertEquals(PlatformDataType.STRING, result.schema().columns().stream().filter(c -> c.name().equals("sample")).findFirst().orElseThrow().fieldType());
        }
    }

    @Test void emptyBinsAndAllNullGroupsHaveZeroFieldCountsAndNullSamples() {
        var points = geometry("points", 3857, GeometryKind.POINT, List.of(field("label", PlatformDataType.STRING)), List.of(
                RowFactory.create(null, "POINT (1 1)"), RowFactory.create(null, "POINT (21 1)")));
        var result = bins(points, SpatialBinShape.SQUARE, List.of(stat(SpatialBinStatisticKind.COUNT, null, "count"),
                stat(SpatialBinStatisticKind.COUNT_FIELD, "label", "labels"), stat(SpatialBinStatisticKind.ANY, "label", "sample")), true, null);
        var rows = result.dataset().orderBy("bin_id").collectAsList();
        assertEquals(3, rows.size()); assertEquals(List.of(1L, 0L, 1L), rows.stream().map(r -> r.<Long>getAs("count")).toList());
        for (var row : rows) { assertEquals(0L, row.<Long>getAs("labels")); assertNull(row.getAs("sample")); }
    }

    @Test void businessNamesCannotOverwriteBinInternalsOrStatistics() {
        for (var shape : SpatialBinShape.values()) {
            var points = geometry("points", shape == SpatialBinShape.H3 ? 4326 : 3857, GeometryKind.POINT, List.of(
                    field("__datascalpel_bin_x", PlatformDataType.STRING), field("__datascalpel_bin_group_count", PlatformDataType.INTEGER),
                    field("__datascalpel_bin_group_value", PlatformDataType.STRING), field("__datascalpel_bin_window_start", PlatformDataType.TIMESTAMP)),
                    List.of(RowFactory.create("sample", 7, "group", Timestamp.from(java.time.Instant.ofEpochSecond(5)), "POINT (0 0)")));
            var time = new SpatialTemporalSlicing("__datascalpel_bin_window_start", 10, SpatialDurationUnit.SECONDS, 5L,
                    SpatialDurationUnit.SECONDS, null, "UTC", "start", "end");
            var statistics = List.of(stat(SpatialBinStatisticKind.COUNT, null, "__datascalpel_bin_id"),
                    stat(SpatialBinStatisticKind.SUM, "__datascalpel_bin_group_count", "__datascalpel_bin_geometry"),
                    stat(SpatialBinStatisticKind.ANY, "__datascalpel_bin_x", "__datascalpel_bin_window_start"));
            var group = new SpatialGroupSummary("__datascalpel_bin_group_value", true, true,
                    "__datascalpel_bin_min", "__datascalpel_bin_group_count", "__datascalpel_bin_max");
            var c = new SpatialBinAggregateConfiguration("points", "shape", shape, 10, SpatialDistanceUnit.METERS, false,
                    statistics, group, time, "bins", "bin_id", "bin_shape", null,
                    shape == SpatialBinShape.H3 ? new SpatialH3Options(SpatialH3Options.Mode.RESOLUTION, 6) : null);
            var issues = new Issues();
            var result = new SpatialBinAggregateNodeOperator().apply(node(c), Map.of("points", points), context(issues));
            assertFalse(issues.hasErrors(), issues::toString);
            assertSame(points, result.propagatedTables().get("points"));
            var rows = result.propagatedTables().get("bins").dataset().collectAsList();
            assertEquals(2, rows.size());
            for (var row : rows) {
                assertEquals(1L, row.<Long>getAs("__datascalpel_bin_id"));
                assertEquals(7L, row.<Long>getAs("__datascalpel_bin_geometry"));
                assertEquals("sample", row.getAs("__datascalpel_bin_window_start"));
                assertEquals("group", row.getAs("__datascalpel_bin_group_value"));
                assertEquals(true, row.getAs("__datascalpel_bin_min"));
                assertEquals(true, row.getAs("__datascalpel_bin_group_count"));
                assertEquals(100d, row.<Double>getAs("__datascalpel_bin_max"));
            }
        }
    }

    @Test void reportsSparkPromotedIntegerAndDecimalAggregateTypes() {
        var decimal = new CanvasColumnSchema("amount", PlatformDataType.DECIMAL, null, 12, 2, true, null, false, false, null);
        var points = geometry("points", 3857, GeometryKind.POINT, List.of(field("value", PlatformDataType.INTEGER), decimal), List.of(
                RowFactory.create(Integer.MAX_VALUE, new java.math.BigDecimal("1.25"), "POINT (0 0)"),
                RowFactory.create(1, new java.math.BigDecimal("2.25"), "POINT (0 0)")));
        var result = bins(points, SpatialBinShape.SQUARE, List.of(stat(SpatialBinStatisticKind.COUNT, null, "count"),
                stat(SpatialBinStatisticKind.SUM, "value", "total"), stat(SpatialBinStatisticKind.SUM, "amount", "money"),
                stat(SpatialBinStatisticKind.MEAN, "amount", "average")), false, null);
        var row = result.dataset().head(); assertEquals(2147483648L, row.<Long>getAs("total"));
        assertEquals(new java.math.BigDecimal("3.50"), row.getAs("money"));
        for (String name : List.of("total", "money", "average")) {
            var column = result.schema().columns().stream().filter(c -> c.name().equals(name)).findFirst().orElseThrow();
            assertEquals(result.dataset().schema().apply(name).dataType(), SparkTypeMapper.toDataType(column));
        }
    }

    @Test void rejectsInvalidFieldStatisticsAtTheExactRulePath() {
        var points = geometry("points", 3857, GeometryKind.POINT, List.of(field("value", PlatformDataType.INTEGER)), List.of());
        for (var item : List.of(stat(SpatialBinStatisticKind.ANY, "value", "sample"), stat(SpatialBinStatisticKind.COUNT_FIELD, "missing", "non_null"))) {
            var issues = new Issues();
            var result = new SpatialBinAggregateNodeOperator().apply(node(config(SpatialBinShape.SQUARE, List.of(stat(SpatialBinStatisticKind.COUNT, null, "count"), item), false, null)),
                    Map.of("points", points), context(issues));
            assertTrue(issues.hasErrors()); assertTrue(result.propagatedTables().isEmpty());
            assertTrue(issues.paths.contains("configuration.statistics[1].sourceColumnName"));
            assertTrue(issues.codes.contains(item.kind() == SpatialBinStatisticKind.ANY ? "STRING_COLUMN_REQUIRED" : "COLUMN_NOT_FOUND"));
        }
    }

    @Test void binGapsExcludeInactiveObservationsBeforeEmptyScopeCreation() {
        var time = gaps();
        for (var shape : List.of(SpatialBinShape.SQUARE, SpatialBinShape.HEXAGON, SpatialBinShape.H3)) {
            var points = geometry("points", shape == SpatialBinShape.H3 ? 4326 : 3857, GeometryKind.POINT,
                    List.of(field("event_time", PlatformDataType.TIMESTAMP)), gapRows());
            var result = bins(points, shape, List.of(stat(SpatialBinStatisticKind.COUNT, null, "count")), shape != SpatialBinShape.H3, time);
            var rows = result.dataset().orderBy("start").collectAsList();
            assertEquals(3, rows.size());
            assertEquals(List.of(1L, 2L, 2L), rows.stream().map(r -> r.<Long>getAs("count")).toList());
            assertEquals(List.of(-8000L, 2000L, 12000L), rows.stream().map(r -> r.<Timestamp>getAs("start").getTime()).toList());
            for (var row : rows) assertEquals(3000L, row.<Timestamp>getAs("end").getTime() - row.<Timestamp>getAs("start").getTime());
        }
    }

    @Test void withinGapsUseOnlyActiveWindowsIncludingEmptyAreas() {
        var points = geometry("summaries", 3857, GeometryKind.POINT, List.of(field("event_time", PlatformDataType.TIMESTAMP)), gapRows());
        var areas = geometry("areas", 3857, GeometryKind.POLYGON, List.of(field("id", PlatformDataType.LONG)), List.of(
                RowFactory.create(1L, "POLYGON ((-1 -1, 2 -1, 2 2, -1 2, -1 -1))"),
                RowFactory.create(2L, "POLYGON ((20 20, 30 20, 30 30, 20 30, 20 20))")));
        var c = new SpatialSummarizeWithinConfiguration("areas", "shape", "summaries", "shape", true,
                SpatialDistanceMethod.PLANAR, SpatialDistanceUnit.METERS, SpatialAreaUnit.SQUARE_METERS,
                List.of(new JoinOutputColumn(JoinOutputColumnSource.LEFT, "id", "id", true)),
                List.of(new SpatialWithinStatistic(UUID.randomUUID().toString(), SpatialWithinStatisticKind.COUNT, null, "count")), null, gaps(), "within");
        var issues = new Issues();
        var result = new SpatialSummarizeWithinNodeOperator().apply(new SpatialSummarizeWithinNodeDefinition(UUID.randomUUID().toString(), "区域", LAYOUT, c),
                Map.of("areas", areas, "summaries", points), context(issues));
        assertFalse(issues.hasErrors(), issues::toString);
        var rows = result.propagatedTables().get("within").dataset().orderBy("id", "start").collectAsList();
        assertEquals(6, rows.size()); assertEquals(List.of(1L, 2L, 2L, 0L, 0L, 0L), rows.stream().map(r -> r.<Long>getAs("count")).toList());
    }

    @Test void overlappingAndContiguousWindowsKeepTheirExistingSemantics() {
        var points = geometry("points", 3857, GeometryKind.POINT, List.of(field("event_time", PlatformDataType.TIMESTAMP)),
                List.of(RowFactory.create(Timestamp.from(java.time.Instant.ofEpochSecond(5)), "POINT (0 0)")));
        for (long repeat : List.of(5L, 10L)) {
            var time = new SpatialTemporalSlicing("event_time", 10, SpatialDurationUnit.SECONDS, repeat, SpatialDurationUnit.SECONDS, null, "UTC", "start", "end");
            var rows = bins(points, SpatialBinShape.SQUARE, List.of(stat(SpatialBinStatisticKind.COUNT, null, "count")), false, time).dataset().orderBy("start").collectAsList();
            assertEquals(repeat == 5 ? 2 : 1, rows.size());
            for (var row : rows) { assertEquals(1L, row.<Long>getAs("count")); assertEquals(10000L, row.<Timestamp>getAs("end").getTime() - row.<Timestamp>getAs("start").getTime()); }
        }
    }

    @Test void fieldStatisticsAndTimeWindowsRetainCompleteLineage() {
        for (var shape : List.of(SpatialBinShape.SQUARE, SpatialBinShape.HEXAGON, SpatialBinShape.H3)) {
            for (boolean empty : shape == SpatialBinShape.H3 ? List.of(false) : List.of(false, true)) {
                for (long repeat : List.of(5L, 10L, 15L)) assertCompleteBinLineage(shape, empty, repeat);
            }
        }
    }

    private void assertCompleteBinLineage(SpatialBinShape shape, boolean empty, long repeat) {
        assertCompleteBinLineage(shape, empty, repeat, null);
    }

    @Test void explicitAndDataBoundsKeepProvableLineageWithoutReadingPoints() {
        for (var shape : List.of(SpatialBinShape.SQUARE, SpatialBinShape.HEXAGON)) {
            for (boolean empty : List.of(false, true)) {
                assertCompleteBinLineage(shape, empty, 5, new SpatialPlanarGridOptions(5d, -5d, extent(-20, -20, 20, 20)));
                assertCompleteBinLineage(shape, empty, 5, new SpatialPlanarGridOptions(5d, -5d, null));
            }
        }
    }

    @Test void shiftedGridRejectsUnrepresentableCoordinatesAndExcessiveDataScopeLazily() {
        var grid = new SpatialPlanarGridOptions(0d, 0d, null);
        var invalid = geometry("points", 3857, GeometryKind.POINT, List.of(), List.of(RowFactory.create("POINT (1e100 0)")));
        var invalidResult = scopedBins(invalid, scopedConfig(SpatialBinShape.SQUARE, grid, false, null));
        var badPoint = assertThrows(Exception.class, () -> invalidResult.dataset().collectAsList());
        assertTrue(badPoint.getMessage().contains("SPATIAL_GRID_POINT_INVALID"));
        var huge = geometry("points", 3857, GeometryKind.POINT, List.of(), List.of(RowFactory.create("POINT (0 0)"), RowFactory.create("POINT (100000000 100000000)")));
        var hugeResult = scopedBins(huge, scopedConfig(SpatialBinShape.SQUARE, grid, true, null));
        var badScope = assertThrows(Exception.class, () -> hugeResult.dataset().collectAsList());
        assertTrue(badScope.getMessage().contains("SPATIAL_GRID_CELL_LIMIT_EXCEEDED"));
    }

    private void assertCompleteBinLineage(SpatialBinShape shape, boolean empty, long repeat, SpatialPlanarGridOptions grid) {
        assertCompleteBinLineage(shape, empty, repeat, grid, null);
    }

    private void assertCompleteBinLineage(SpatialBinShape shape, boolean empty, long repeat, SpatialPlanarGridOptions grid, SpatialTemporalSlicing calendar) {
        var raw = geometry("points", shape == SpatialBinShape.H3 ? 4326 : 3857, GeometryKind.POINT, List.of(field("label", PlatformDataType.STRING), field("event_time", PlatformDataType.TIMESTAMP)), List.of());
        var inputAsset = new TaskLineageEvidence.Asset("input", TaskLineageEvidence.AssetRole.INPUT, TaskLineageEvidence.AssetKind.JDBC_TABLE,
                null, null, null, null, UUID.randomUUID(), null, null, "points", null, null, "points");
        var fields = new LinkedHashMap<String, CatalystLineageMetadata.InputField>();
        raw.schema().columns().forEach(c -> fields.put(c.name(), new CatalystLineageMetadata.InputField("input:" + c.name(), null)));
        var source = new SparkCanvasTable(raw.schema(), CatalystLineageMetadata.markInput(raw.dataset(), "input-node", inputAsset, fields));
        var time = calendar != null ? calendar : new SpatialTemporalSlicing("event_time", 10, SpatialDurationUnit.SECONDS, repeat, SpatialDurationUnit.SECONDS, null, "UTC", "start", "end");
        var config = config(shape, List.of(stat(SpatialBinStatisticKind.COUNT, null, "count"),
                stat(SpatialBinStatisticKind.COUNT_FIELD, "label", "labels"), stat(SpatialBinStatisticKind.ANY, "label", "sample")), empty, time);
        if (grid != null) config = new SpatialBinAggregateConfiguration(config.sourceTableName(), config.pointGeometryColumnName(), config.binShape(), config.binSize(),
                config.binSizeUnit(), config.includeEmptyBins(), config.statistics(), config.groupSummary(), config.temporalSlicing(), config.outputTableName(),
                config.binIdColumnName(), config.binGeometryColumnName(), config.binSizeSemantics(), config.h3(), grid);
        var table = scopedBins(source, config);
        var output = new TaskLineageEvidence.Asset("out", TaskLineageEvidence.AssetRole.OUTPUT, TaskLineageEvidence.AssetKind.JDBC_TABLE,
                null, TaskLineageEvidence.WriteMode.APPEND, null, null, UUID.randomUUID(), null, null, "bins", null, null, "bins");
        var candidate = new CatalystLineageOutputCandidate("flow", "output-node", "JDBC_OUTPUT", "write", table.dataset(), output,
                table.schema().columns().stream().map(c -> new CatalystLineageOutputCandidate.TargetField("out:" + c.name(), null, c.name(), c.name(), TaskLineageEvidence.OutputEffect.WRITTEN_UNKNOWN_SOURCE)).toList());
        var flow = new CatalystLineageAnalyzer().analyze(List.of(candidate)).flows().getFirst();
        assertEquals(TaskLineageEvidence.Coverage.FIELD_COMPLETE, flow.coverage(), () -> shape + "/empty=" + empty + "/" + repeat + ": " + flow.warnings());
        for (var mapping : Map.of("labels", "label", "sample", "label", "start", "event_time", "end", "event_time").entrySet()) {
            assertTrue(flow.fieldEdges().stream().anyMatch(e -> e.target().localFieldKey().equals("out:" + mapping.getKey()) && e.source().localFieldKey().equals("input:" + mapping.getValue())), mapping.toString());
        }
    }

    @Test void withinOverlappingWindowsDoNotCrossMultiplyStartAndEnd() {
        var points = geometry("summaries", 3857, GeometryKind.POINT, List.of(field("event_time", PlatformDataType.TIMESTAMP)),
                List.of(RowFactory.create(Timestamp.from(java.time.Instant.ofEpochSecond(5)), "POINT (0 0)")));
        var areas = geometry("areas", 3857, GeometryKind.POLYGON, List.of(field("id", PlatformDataType.LONG)), List.of(
                RowFactory.create(1L, "POLYGON ((-1 -1, 2 -1, 2 2, -1 2, -1 -1))"),
                RowFactory.create(2L, "POLYGON ((20 20, 30 20, 30 30, 20 30, 20 20))")));
        for (boolean empty : List.of(false, true)) {
            var time = new SpatialTemporalSlicing("event_time", 10, SpatialDurationUnit.SECONDS, 5L, SpatialDurationUnit.SECONDS, null, "UTC", "start", "end");
            var config = new SpatialSummarizeWithinConfiguration("areas", "shape", "summaries", "shape", empty,
                    SpatialDistanceMethod.PLANAR, SpatialDistanceUnit.METERS, SpatialAreaUnit.SQUARE_METERS,
                    List.of(new JoinOutputColumn(JoinOutputColumnSource.LEFT, "id", "id", true)),
                    List.of(new SpatialWithinStatistic(UUID.randomUUID().toString(), SpatialWithinStatisticKind.COUNT, null, "count")), null, time, "within");
            var issues = new Issues();
            var result = new SpatialSummarizeWithinNodeOperator().apply(new SpatialSummarizeWithinNodeDefinition(UUID.randomUUID().toString(), "区域", LAYOUT, config),
                    Map.of("areas", areas, "summaries", points), context(issues));
            assertFalse(issues.hasErrors(), issues::toString);
            var rows = result.propagatedTables().get("within").dataset().orderBy("id", "start").collectAsList();
            assertEquals(empty ? List.of(1L, 1L, 0L, 0L) : List.of(1L, 1L), rows.stream().map(r -> r.<Long>getAs("count")).toList());
            for (var row : rows) assertEquals(10000L, row.<Timestamp>getAs("end").getTime() - row.<Timestamp>getAs("start").getTime());
        }
    }

    private SpatialTemporalSlicing gaps() { return new SpatialTemporalSlicing("event_time", 3, SpatialDurationUnit.SECONDS, 10L,
            SpatialDurationUnit.SECONDS, "1970-01-01T00:00:02Z", "UTC", "start", "end"); }
    private List<Row> gapRows() {
        var rows = new ArrayList<Row>();
        for (String time : List.of("1969-12-31 23:59:52", "1969-12-31 23:59:55", "1970-01-01 00:00:02", "1970-01-01 00:00:04.999999",
                "1970-01-01 00:00:05", "1970-01-01 00:00:12", "1970-01-01 00:00:14.999999", "1970-01-01 00:00:15", "1970-01-01 00:00:39"))
            rows.add(RowFactory.create(Timestamp.from(java.time.Instant.parse(time.replace(' ', 'T') + "Z")), "POINT (0 0)"));
        rows.add(RowFactory.create(null, "POINT (0 0)")); return rows;
    }
    private SparkCanvasTable bins(SparkCanvasTable points, SpatialBinShape shape, List<SpatialBinStatistic> stats, boolean empty, SpatialTemporalSlicing time) {
        var issues = new Issues();
        var result = new SpatialBinAggregateNodeOperator().apply(node(config(shape, stats, empty, time)), Map.of("points", points), context(issues));
        assertFalse(issues.hasErrors(), issues::toString); assertSame(points, result.propagatedTables().get("points"));
        return result.propagatedTables().get("bins");
    }
    private SpatialBinAggregateConfiguration config(SpatialBinShape shape, List<SpatialBinStatistic> stats, boolean empty, SpatialTemporalSlicing time) {
        return new SpatialBinAggregateConfiguration("points", "shape", shape, 10, SpatialDistanceUnit.METERS, empty, stats, null, time, "bins", "bin_id", "bin_shape", null,
                shape == SpatialBinShape.H3 ? new SpatialH3Options(SpatialH3Options.Mode.RESOLUTION, 6) : null);
    }
    private SpatialBinAggregateNodeDefinition node(SpatialBinAggregateConfiguration c) { return new SpatialBinAggregateNodeDefinition(UUID.randomUUID().toString(), "格网", LAYOUT, c); }
    private SpatialBinStatistic stat(SpatialBinStatisticKind kind, String source, String output) { return new SpatialBinStatistic(UUID.randomUUID().toString(), kind, source, output); }
    private CanvasColumnSchema field(String name, PlatformDataType type) { return new CanvasColumnSchema(name, type, type == PlatformDataType.STRING ? 100 : null, null, null, true, null, false, false, null); }
    private SparkCanvasTable geometry(String name, int epsg, GeometryKind kind, List<CanvasColumnSchema> fields, List<Row> rows) {
        var columns = new ArrayList<>(fields); columns.add(field("wkt", PlatformDataType.STRING));
        var raw = spark.createDataFrame(rows, SparkTypeMapper.toStructType(columns));
        var data = raw.withColumn("shape", st_functions.ST_SetSRID(st_constructors.ST_GeomFromWKT(raw.col("wkt")), functions.lit(epsg)));
        columns.add(new CanvasColumnSchema("shape", PlatformDataType.GEOMETRY, null, null, null, true, null, false, false, null,
                new GeometryTypeDefinition(kind, new CrsReference("EPSG", epsg), CoordinateDimension.XY)));
        return new SparkCanvasTable(new CanvasTableSchema(name, null, columns, CanvasDatasetKind.BOUNDED, null, null), data);
    }
    private CanvasNodeOperationContext context(Issues issues) { return new CanvasNodeOperationContext(spark, MetadataIndex.create(new MetadataSnapshot(List.of(), List.of())), issues, new SchemaOnlyCanvasNodeDataAccess(spark)); }
    private static class Issues implements CanvasNodeIssueSink {
        final List<String> codes = new ArrayList<>(), paths = new ArrayList<>();
        public void error(String code, String message, String path) { codes.add(code); paths.add(path); }
        public void warning(String code, String message, String path) { }
        public boolean hasErrors() { return !codes.isEmpty(); }
        public String toString() { return codes.toString(); }
    }
}
