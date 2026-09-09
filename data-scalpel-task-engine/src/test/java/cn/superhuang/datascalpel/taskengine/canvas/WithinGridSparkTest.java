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
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WithinGridSparkTest {
    SparkSession spark;
    @BeforeAll void start() {
        spark = SedonaSparkSupport.initialize(SedonaSparkSupport.builder().master("local[1]").appName("within-grid")
                .config("spark.ui.enabled", false).config("spark.driver.host", "127.0.0.1").config("spark.driver.bindAddress", "127.0.0.1")
                .config("spark.sql.shuffle.partitions", 1).getOrCreate());
    }
    @AfterAll void stop() { if (spark != null) spark.stop(); }

    @Test void linesAreIntersectedAcrossCellsRatherThanAssignedByCentroid() {
        var c = config(SpatialBinShape.SQUARE, null, List.of(stat(SpatialWithinStatisticKind.COUNT, null),
                stat(SpatialWithinStatisticKind.LENGTH_WITHIN, null), stat(SpatialWithinStatisticKind.SUM, "amount")), null);
        var rows = run(table(GeometryKind.LINESTRING, "LINESTRING (5 5, 15 5)"), c).get("summary").dataset().collectAsList();
        assertEquals(2, rows.size());
        for (var r : rows) { assertEquals(1L, r.<Long>getAs("count")); assertEquals(5d, r.<Double>getAs("length_within"), 1e-9); assertEquals(50d, r.<Double>getAs("sum"), 1e-9); }
    }

    @Test void polygonsApportionAgainstWholeSourceAndEmitOnlyPositiveAreaScopeCells() {
        var rows = run(table(GeometryKind.POLYGON, "POLYGON ((0 0,20 0,20 10,0 10,0 0))"),
                config(SpatialBinShape.SQUARE, null, List.of(stat(SpatialWithinStatisticKind.COUNT, null),
                        stat(SpatialWithinStatisticKind.AREA_WITHIN, null), stat(SpatialWithinStatisticKind.SUM, "amount")), null))
                .get("summary").dataset().collectAsList();
        assertEquals(2, rows.size());
        for (var r : rows) { assertEquals(100d, r.<Double>getAs("area_within"), 1e-9); assertEquals(50d, r.<Double>getAs("sum"), 1e-9); }
    }

    @Test void pointBoundariesHaveOneOwnerAndStableIdsAcrossScopes() {
        var source = table(GeometryKind.POINT, "POINT (0 0)", "POINT (10 0)", "POINT (20 0)");
        var data = run(source, config(SpatialBinShape.SQUARE, null, List.of(stat(SpatialWithinStatisticKind.COUNT, null)), null));
        var rows = data.get("summary").dataset().collectAsList();
        assertEquals(3L, rows.stream().mapToLong(r -> r.<Long>getAs("count")).sum());
        var explicit = run(source, config(SpatialBinShape.SQUARE, new SpatialPlanarGridOptions.Extent(
                SpatialPlanarGridOptions.ExtentMode.EXPLICIT_BOUNDS, -10d,-10d,30d,10d), List.of(stat(SpatialWithinStatisticKind.COUNT, null)), null));
        var occupied = explicit.get("summary").dataset().filter("count > 0").collectAsList();
        assertEquals(ids(rows), ids(occupied));
        assertSame(source, data.get("features"));
    }

    @Test void hexagonsUseAcrossFlatsDistanceAndPreserveIntersectedLength() {
        var rows = run(table(GeometryKind.LINESTRING, "LINESTRING (-10 0,10 0)"), config(SpatialBinShape.HEXAGON, null,
                List.of(stat(SpatialWithinStatisticKind.COUNT, null), stat(SpatialWithinStatisticKind.LENGTH_WITHIN, null)), null))
                .get("summary").dataset().collectAsList();
        assertEquals(20d, rows.stream().mapToDouble(r -> r.<Double>getAs("length_within")).sum(), 1e-8);
        for (var row : rows) {
            org.locationtech.jts.geom.Geometry g = row.getAs("bin_geometry");
            assertEquals(10d, g.getEnvelopeInternal().getHeight(), 1e-8);
        }
    }

    @Test void emptyInputCanFillExplicitGridButCannotInventDataExtent() {
        var source = table(GeometryKind.POINT);
        var e = new SpatialPlanarGridOptions.Extent(SpatialPlanarGridOptions.ExtentMode.EXPLICIT_BOUNDS, 0d,0d,20d,10d);
        var rows = run(source, config(SpatialBinShape.SQUARE, e, List.of(stat(SpatialWithinStatisticKind.COUNT, null)), null))
                .get("summary").dataset().collectAsList();
        assertEquals(2, rows.size()); rows.forEach(r -> assertEquals(0L, r.<Long>getAs("count")));
        assertTrue(run(source, config(SpatialBinShape.SQUARE, null, List.of(stat(SpatialWithinStatisticKind.COUNT, null)), null))
                .get("summary").dataset().collectAsList().isEmpty());
    }

    @Test void linkedResultsUseGeneratedStableKeyAndNeverExposeInternalAreaTable() {
        var source = table(GeometryKind.POINT, "POINT (1 1)", "POINT (2 2)");
        var group = new SpatialGroupSummary("label", false, true, null, null, "percentage");
        var c = config(SpatialBinShape.SQUARE, null, List.of(stat(SpatialWithinStatisticKind.COUNT, null)), group);
        var output = run(source, c);
        assertEquals(List.of("features", "summary", "groups"), new ArrayList<>(output.keySet()));
        var main = output.get("summary").dataset().collectAsList(); var groups = output.get("groups").dataset().collectAsList();
        assertEquals(1, main.size()); assertEquals(1, groups.size());
        assertEquals(main.getFirst().<String>getAs("area_id"), groups.getFirst().<String>getAs("area_id"));
        assertEquals(100d, groups.getFirst().<Double>getAs("percentage"), 1e-9);
        assertEquals("old_area_key", c.groupResult().areaKeyColumnName());
    }

    @Test void compilerBuildsLazyPlansWithoutActionsAndReportsGridDraftErrors() {
        var source = table(GeometryKind.POINT);
        String job = UUID.randomUUID().toString(); spark.sparkContext().setJobGroup(job, "within preflight", false);
        run(source, config(SpatialBinShape.SQUARE, null, List.of(stat(SpatialWithinStatisticKind.COUNT, null)), null))
                .get("summary").dataset().queryExecution().analyzed();
        assertEquals(0, spark.sparkContext().statusTracker().getJobIdsForGroup(job).length); spark.sparkContext().clearJobGroup();
        var base = config(SpatialBinShape.SQUARE, null, List.of(stat(SpatialWithinStatisticKind.COUNT, null)), null);
        var r = new SpatialWithinRegions(SpatialWithinRegions.Mode.PLANAR_GRID, SpatialBinShape.H3, 0d, null, null, "", "");
        var invalid = new SpatialSummarizeWithinConfiguration("", "", "features", "shape", true, SpatialDistanceMethod.PLANAR,
                SpatialDistanceUnit.METERS, SpatialAreaUnit.SQUARE_METERS, List.of(), base.statistics(), null, null, "summary", null, r);
        var issues = new Issues(); assertTrue(apply(source, invalid, issues).propagatedTables().isEmpty());
        assertTrue(issues.paths.stream().allMatch(p -> p.startsWith("configuration.regions")));
    }

    @Test void generatedRegionsRetainGeometryAndMeasureLineageWithoutInventingPhysicalAreaAssets() {
        var raw = table(GeometryKind.LINESTRING);
        var asset = new TaskLineageEvidence.Asset("input",TaskLineageEvidence.AssetRole.INPUT,TaskLineageEvidence.AssetKind.JDBC_TABLE,
                null,null,null,null,UUID.randomUUID(),null,null,"features",null,null,"features");
        var fields = new LinkedHashMap<String,CatalystLineageMetadata.InputField>();
        raw.schema().columns().forEach(c -> fields.put(c.name(),new CatalystLineageMetadata.InputField("input:" + c.name(),null)));
        var source = new SparkCanvasTable(raw.schema(),CatalystLineageMetadata.markInput(raw.dataset(),"input-node",asset,fields));
        for (var shape : List.of(SpatialBinShape.SQUARE,SpatialBinShape.HEXAGON)) for (boolean explicit : List.of(false,true)) {
            var extent = explicit ? new SpatialPlanarGridOptions.Extent(SpatialPlanarGridOptions.ExtentMode.EXPLICIT_BOUNDS,0d,0d,20d,10d) : null;
            var out = run(source,config(shape,extent,List.of(stat(SpatialWithinStatisticKind.COUNT,null),stat(SpatialWithinStatisticKind.LENGTH_WITHIN,null),
                    stat(SpatialWithinStatisticKind.SUM,"amount")),null)).get("summary");
            var target = new TaskLineageEvidence.Asset("output",TaskLineageEvidence.AssetRole.OUTPUT,TaskLineageEvidence.AssetKind.JDBC_TABLE,
                    null,TaskLineageEvidence.WriteMode.APPEND,null,null,UUID.randomUUID(),null,null,"summary",null,null,"summary");
            var candidate = new CatalystLineageOutputCandidate("flow","out","JDBC_OUTPUT","write",out.dataset(),target,
                    out.schema().columns().stream().map(c -> new CatalystLineageOutputCandidate.TargetField("out:"+c.name(),null,c.name(),c.name(),TaskLineageEvidence.OutputEffect.WRITTEN_UNKNOWN_SOURCE)).toList());
            var flow = new CatalystLineageAnalyzer().analyze(List.of(candidate)).flows().getFirst();
            assertEquals(TaskLineageEvidence.Coverage.FIELD_COMPLETE,flow.coverage(),() -> flow.warnings().toString());
            assertTrue(flow.fieldEdges().stream().anyMatch(e -> e.target().localFieldKey().equals("out:sum") && e.source().localFieldKey().equals("input:amount")));
            if (!explicit) assertTrue(flow.fieldEdges().stream().anyMatch(e -> e.target().localFieldKey().equals("out:bin_geometry") && e.source().localFieldKey().equals("input:shape")));
        }
    }

    @Test void temporalLinkedGridFillsOnlyActualWindowsAndNeverCreatesEmptyGroups() {
        var raw = table(GeometryKind.POINT,"POINT (1 1)","POINT (11 1)");
        var columns = new ArrayList<>(raw.schema().columns());
        columns.add(new CanvasColumnSchema("event_time",PlatformDataType.TIMESTAMP,null,null,null,false,null,false,false,null,null));
        var data = raw.dataset().withColumn("event_time",functions.to_timestamp(functions.when(raw.dataset().col("wkt").equalTo("POINT (1 1)"),
                functions.lit("2026-01-01 00:00:02")).otherwise(functions.lit("2026-01-01 00:00:12"))));
        var source = new SparkCanvasTable(new CanvasTableSchema("features",null,columns,CanvasDatasetKind.BOUNDED,null,null),data);
        var base = config(SpatialBinShape.SQUARE,new SpatialPlanarGridOptions.Extent(SpatialPlanarGridOptions.ExtentMode.EXPLICIT_BOUNDS,0d,0d,20d,10d),
                List.of(stat(SpatialWithinStatisticKind.COUNT,null)),new SpatialGroupSummary("label",false,true,null,null,"percentage"));
        var time = new SpatialTemporalSlicing("event_time",10,SpatialDurationUnit.SECONDS,null,null,"1970-01-01T00:00:00Z","UTC","start","end");
        var c = new SpatialSummarizeWithinConfiguration(base.areaTableName(),base.areaGeometryColumnName(),"features","shape",true,
                base.distanceMethod(),base.lengthUnit(),base.areaUnit(),base.areaOutputColumns(),base.statistics(),base.groupSummary(),time,
                "summary",base.groupResult(),base.regions());
        var result = run(source,c);
        var main = result.get("summary").dataset().collectAsList(); var groups = result.get("groups").dataset().collectAsList();
        assertEquals(4,main.size()); assertEquals(2,groups.size());
        assertEquals(2L,main.stream().mapToLong(r -> r.<Long>getAs("count")).sum());
        for (var g : groups) assertTrue(main.stream().anyMatch(m -> m.getAs("area_id").equals(g.getAs("area_id"))
                && m.getAs("start").equals(g.getAs("start")) && m.getAs("end").equals(g.getAs("end"))));
    }

    @Test void dataDependentGridLimitAndMalformedGeometryFailSafelyAtExecution() {
        var c = config(SpatialBinShape.SQUARE,null,List.of(stat(SpatialWithinStatisticKind.COUNT,null)),null);
        var error = assertThrows(Exception.class,() -> run(table(GeometryKind.POINT,"POINT (0 0)","POINT (20000000 0)"),c)
                .get("summary").dataset().collectAsList());
        assertTrue(error.getMessage().contains("SPATIAL_GRID_CELL_LIMIT_EXCEEDED"));
        var invalid = assertThrows(Exception.class,() -> run(table(GeometryKind.POLYGON,"POLYGON ((0 0,10 10,10 0,0 10,0 0))"),c)
                .get("summary").dataset().collectAsList());
        assertTrue(invalid.getMessage().contains("SPATIAL_GRID_GEOMETRY_INVALID"));
    }

    private Set<String> ids(List<Row> rows) { var result = new HashSet<String>(); for (var r : rows) if (r.<Long>getAs("count") > 0) result.add(r.getAs("bin_id")); return result; }
    private SpatialWithinStatistic stat(SpatialWithinStatisticKind kind, String field) {
        return new SpatialWithinStatistic(UUID.randomUUID().toString(), kind, field, kind.name().toLowerCase(Locale.ROOT),
                field == null ? null : SpatialWithinValueTreatment.APPORTION_TOTAL, null);
    }
    private SpatialSummarizeWithinConfiguration config(SpatialBinShape shape, SpatialPlanarGridOptions.Extent extent,
            List<SpatialWithinStatistic> stats, SpatialGroupSummary group) {
        var regions = new SpatialWithinRegions(SpatialWithinRegions.Mode.PLANAR_GRID, shape, 10d, SpatialDistanceUnit.METERS,
                new SpatialPlanarGridOptions(0d,0d,extent), "bin_id", "bin_geometry");
        var linked = group == null ? null : new SpatialWithinGroupResult("old_area_key", "area_id", "groups", "group_value", null,null,null,null);
        return new SpatialSummarizeWithinConfiguration("old_areas", "old_geometry", "features", "shape", true,
                SpatialDistanceMethod.PLANAR, SpatialDistanceUnit.METERS, SpatialAreaUnit.SQUARE_METERS, List.of(), stats, group, null, "summary", linked, regions);
    }
    private Map<String, SparkCanvasTable> run(SparkCanvasTable source, SpatialSummarizeWithinConfiguration c) {
        var issues = new Issues(); var result = apply(source, c, issues); assertFalse(issues.hasErrors(), issues.codes.toString()); return result.propagatedTables();
    }
    private CanvasNodeOperationResult apply(SparkCanvasTable source, SpatialSummarizeWithinConfiguration c, Issues issues) {
        return new SpatialSummarizeWithinNodeOperator().apply(new SpatialSummarizeWithinNodeDefinition(UUID.randomUUID().toString(), "汇总", new CanvasNodeLayout(0d,0d,376d,224d), c),
                Map.of("features", source), new CanvasNodeOperationContext(spark, MetadataIndex.create(new MetadataSnapshot(List.of(),List.of())), issues,
                        new SchemaOnlyCanvasNodeDataAccess(spark), CanvasExecutionMode.BATCH, CanvasRuntimeValues.forPreview()));
    }
    private SparkCanvasTable table(GeometryKind kind, String... wkts) {
        var columns = new ArrayList<>(List.of(new CanvasColumnSchema("wkt", PlatformDataType.STRING, 1000,null,null,true,null,false,false,null,null),
                new CanvasColumnSchema("amount", PlatformDataType.DOUBLE,null,null,null,false,null,false,false,null,null),
                new CanvasColumnSchema("label", PlatformDataType.STRING,50,null,null,false,null,false,false,null,null)));
        var raw = spark.createDataFrame(Arrays.stream(wkts).map(w -> RowFactory.create(w,100d,"road")).toList(), SparkTypeMapper.toStructType(columns));
        var data = raw.withColumn("shape", st_functions.ST_SetSRID(st_constructors.ST_GeomFromWKT(raw.col("wkt")), functions.lit(3857)));
        columns.add(new CanvasColumnSchema("shape", PlatformDataType.GEOMETRY,null,null,null,true,null,false,false,null,
                new GeometryTypeDefinition(kind,new CrsReference("EPSG",3857),CoordinateDimension.XY)));
        return new SparkCanvasTable(new CanvasTableSchema("features",null,columns,CanvasDatasetKind.BOUNDED,null,null),data);
    }
    private static class Issues implements CanvasNodeIssueSink {
        final List<String> codes = new ArrayList<>(), paths = new ArrayList<>();
        public void error(String code,String message,String path) { codes.add(code); paths.add(path); }
        public void warning(String code,String message,String path) { }
        public boolean hasErrors() { return !codes.isEmpty(); }
    }
}
