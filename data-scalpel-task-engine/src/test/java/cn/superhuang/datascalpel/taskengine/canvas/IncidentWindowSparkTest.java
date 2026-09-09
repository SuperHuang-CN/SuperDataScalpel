package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.*;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.datascalpel.taskengine.compiler.MetadataIndex;
import cn.superhuang.datascalpel.taskengine.compiler.lineage.*;
import cn.superhuang.datascalpel.taskengine.spark.*;
import org.apache.spark.sql.*;
import org.junit.jupiter.api.*;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IncidentWindowSparkTest {
    private SparkSession spark;
    @BeforeAll void start() {
        spark = SedonaSparkSupport.initialize(SedonaSparkSupport.builder().master("local[2]").appName("incident-window")
                .config("spark.ui.enabled", false).config("spark.driver.host", "127.0.0.1").config("spark.driver.bindAddress", "127.0.0.1")
                .config("spark.sql.shuffle.partitions", 2).config("spark.sql.session.timeZone", "UTC").getOrCreate());
    }
    @AfterAll void stop() { if (spark != null) spark.stop(); }

    @Test void officialHalfOpenExamplesIncludeFutureAndClipAtTrackEnds() {
        var source = table(List.of(row(1,0,10d), row(1,1,20d), row(1,2,30d), row(1,3,40d), row(1,4,50d)));
        var c = config(List.of(window("mean", TrackIncidentWindow.Kind.MEAN, -1, 2),
                window("past", TrackIncidentWindow.Kind.MEAN, -5, 0),
                window("next", TrackIncidentWindow.Kind.FIRST, 1, 2),
                window("count", TrackIncidentWindow.Kind.COUNT, -1, 2)), "mean", null);
        var rows = bindings(source, c, new Issues()).dataset().orderBy("time").collectAsList();
        assertEquals(List.of(15d,20d,30d,40d,45d), rows.stream().map(r -> r.<Double>getAs("mean")).toList());
        assertEquals(Arrays.asList(null,10d,15d,20d,25d), rows.stream().map(r -> r.<Double>getAs("past")).toList());
        assertEquals(Arrays.asList(20d,30d,40d,50d,null), rows.stream().map(r -> r.<Double>getAs("next")).toList());
        assertEquals(List.of(2L,3L,3L,3L,2L), rows.stream().map(r -> r.<Long>getAs("count")).toList());
    }

    @Test void allFunctionsHaveExplicitNullEmptyAndPopulationSemantics() {
        var source = table(Arrays.asList(row(1,0,null), row(1,1,20d), row(1,2,40d)));
        var windows = Arrays.stream(TrackIncidentWindow.Kind.values()).map(k -> window(k.name(), k, -1, 2)).toList();
        var rows = bindings(source, config(windows,"MEAN",null), new Issues()).dataset().orderBy("time").collectAsList();
        Row row = rows.get(1);
        assertEquals(2L, row.<Long>getAs("COUNT")); assertEquals(60d, row.<Double>getAs("SUM"));
        assertEquals(30d, row.<Double>getAs("MEAN")); assertEquals(20d, row.<Double>getAs("MIN"));
        assertEquals(40d, row.<Double>getAs("MAX")); assertNull(row.getAs("FIRST"));
        assertEquals(40d, row.<Double>getAs("LAST")); assertEquals(10d, row.<Double>getAs("STDDEV_POP"));
        assertEquals(100d, row.<Double>getAs("VARIANCE_POP"));
        var emptyWindows = Arrays.stream(TrackIncidentWindow.Kind.values()).map(k -> window(k.name(), k, -3, 0)).toList();
        Row first = bindings(source, config(emptyWindows,"MEAN",null), new Issues()).dataset().orderBy("time").first();
        for (var item : emptyWindows) {
            if (item.kind() == TrackIncidentWindow.Kind.COUNT) assertEquals(0L, first.<Long>getAs(item.bindingName()));
            else assertNull(first.getAs(item.bindingName()), item.bindingName());
        }
    }

    @Test void windowsResetAtTrackAndFixedBoundariesAndUseStableSameTimeOrder() {
        var source = table(List.of(row(1,0,10d), row(2,0,90d), row(1,1,30d), row(1,2,50d), row(1,3,70d)));
        var boundary = new TrackFixedTimeBoundary(2, TrackTimeBoundaryUnit.SECONDS,"2026-01-01T00:00:00", "UTC");
        var c = config(List.of(window("past", TrackIncidentWindow.Kind.MEAN,-5,0)),"past", boundary);
        var rows = bindings(source,c,new Issues()).dataset().filter("track = 1").orderBy("time").collectAsList();
        assertEquals(Arrays.asList(null,10d,null,50d), rows.stream().map(r -> r.<Double>getAs("past")).toList());
        var duplicateTimes = table(List.of(RowFactory.create(1L, stamp(0), 10d, 1L), RowFactory.create(1L, stamp(0), 20d, 2L)));
        var ordered = bindings(duplicateTimes, config(List.of(window("previous",TrackIncidentWindow.Kind.FIRST,-1,0)), "previous", null), new Issues())
                .dataset().orderBy(functions.col("`event.id`")).collectAsList();
        assertNull(ordered.getFirst().getAs("previous")); assertEquals(10d, ordered.getLast().<Double>getAs("previous"));
    }

    @Test void predicatesUseBindingsWithoutLeakingTemporaryColumnsOrStartingCompileJobs() {
        var source = table(List.of(row(1,0,10d), row(1,1,20d), row(1,2,40d), row(1,3,50d)));
        var c = config(List.of(window("past",TrackIncidentWindow.Kind.MEAN,-2,0)), "past", null);
        var issues = new Issues();
        String group = UUID.randomUUID().toString(); spark.sparkContext().setJobGroup(group, "zero job incident planning", false);
        var output = apply(source,c,issues);
        assertFalse(issues.hasErrors(), issues::toString);
        output.propagatedTables().get("incidents").dataset().queryExecution().analyzed();
        assertEquals(0, spark.sparkContext().statusTracker().getJobIdsForGroup(group).length);
        spark.sparkContext().clearJobGroup();
        assertSame(source, output.propagatedTables().get("events"));
        var result = output.propagatedTables().get("incidents");
        assertFalse(result.schema().columns().stream().anyMatch(col -> col.name().equals("past") || col.name().startsWith("__datascalpel")));
        assertEquals(source.schema().columns().size() + 6, result.dataset().columns().length);
        var rows = result.dataset().orderBy("time").collectAsList();
        assertEquals(List.of(false,false,false,true), rows.stream().map(r -> r.<Boolean>getAs("flag")).toList());
        assertEquals("Started", rows.getLast().getAs("status"));
        assertEquals(0d, rows.getLast().<Double>getAs("duration"));
    }

    @Test void invalidBindingsCannotShadowInputsNestWindowsOrReturnPartialResults() {
        var source = table(List.of());
        var invalid = List.of(new TrackIncidentWindow("speed","speed",TrackIncidentWindow.Kind.MEAN,-1,0),
                window("space name",TrackIncidentWindow.Kind.MEAN,-1,0), window("m",TrackIncidentWindow.Kind.MEAN,0,0),
                window("m",TrackIncidentWindow.Kind.MEAN,Integer.MIN_VALUE,0), window("m",null,-1,0),
                new TrackIncidentWindow("m","missing",TrackIncidentWindow.Kind.MEAN,-1,0));
        for (var item : invalid) {
            var issues = new Issues();
            assertTrue(apply(source,config(List.of(item),"speed",null),issues).propagatedTables().isEmpty());
            assertTrue(issues.hasErrors()); assertTrue(issues.paths.stream().anyMatch(path -> path.startsWith("configuration.conditionWindows[0]")));
        }
        for (var second : List.of(window("M",TrackIncidentWindow.Kind.MEAN,-1,0),
                new TrackIncidentWindow("other","m",TrackIncidentWindow.Kind.MEAN,-1,0))) {
            var issues = new Issues();
            assertTrue(apply(source,config(List.of(window("m",TrackIncidentWindow.Kind.MEAN,-1,0),second),"speed",null),issues)
                    .propagatedTables().isEmpty());
            assertTrue(issues.hasErrors());
        }
    }

    @Test void conditionWindowLineageResolvesToOriginalFieldsNotTemporaryBindingNames() {
        var raw = table(List.of());
        var asset = new TaskLineageEvidence.Asset("input",TaskLineageEvidence.AssetRole.INPUT,TaskLineageEvidence.AssetKind.JDBC_TABLE,
                null,null,null,null,UUID.randomUUID(),null,null,"events",null,null,"events");
        var fields = new LinkedHashMap<String,CatalystLineageMetadata.InputField>();
        raw.schema().columns().forEach(col -> fields.put(col.name(),new CatalystLineageMetadata.InputField("input:" + col.name(),null)));
        var source = new SparkCanvasTable(raw.schema(),CatalystLineageMetadata.markInput(raw.dataset(),"input-node",asset,fields));
        var issues = new Issues();
        var output = apply(source,config(List.of(window("past",TrackIncidentWindow.Kind.MEAN,-5,0)),"past",null),issues)
                .propagatedTables().get("incidents");
        assertFalse(issues.hasErrors(),issues::toString);
        var target = new TaskLineageEvidence.Asset("output",TaskLineageEvidence.AssetRole.OUTPUT,TaskLineageEvidence.AssetKind.JDBC_TABLE,
                null,TaskLineageEvidence.WriteMode.APPEND,null,null,UUID.randomUUID(),null,null,"incidents",null,null,"incidents");
        var candidate = new CatalystLineageOutputCandidate("flow","output-node","JDBC_OUTPUT","write",output.dataset(),target,
                output.schema().columns().stream().map(col -> new CatalystLineageOutputCandidate.TargetField("out:" + col.name(),null,
                        col.name(),col.name(),TaskLineageEvidence.OutputEffect.WRITTEN_UNKNOWN_SOURCE)).toList());
        var flow = new CatalystLineageAnalyzer().analyze(List.of(candidate)).flows().getFirst();
        assertEquals(TaskLineageEvidence.Coverage.FIELD_COMPLETE,flow.coverage(),() -> flow.warnings().toString());
        assertTrue(flow.fieldEdges().stream().allMatch(edge -> edge.source().localFieldKey().startsWith("input:")));
        for (String name : List.of("flag","status","duration")) {
            assertTrue(flow.fieldEdges().stream().anyMatch(edge -> edge.target().localFieldKey().equals("out:" + name)
                    && edge.source().localFieldKey().equals("input:speed")),name);
        }
    }

    @Test void legacyModeRetainsButDoesNotEvaluateInactiveInvalidBindings() {
        var source = table(List.of(row(1,0,20d),row(1,1,30d)));
        var c = config(List.of(new TrackIncidentWindow("not a name","missing",null,null,null)),"speed",null);
        var legacy = new TrackDetectIncidentsConfiguration(c.sourceTableName(),c.pointGeometryColumnName(),c.trackIdColumns(),
                c.timeColumnName(),c.distanceMethod(),c.boundaries(),c.startCondition(),c.endCondition(),c.resultMode(),c.outputTableName(),
                c.incidentIdColumnName(),c.incidentFlagColumnName(),c.incidentStartTimeColumnName(),c.incidentEndTimeColumnName(),
                c.incidentDurationColumnName(),c.incidentDurationUnit(),TrackIncidentSemantics.LEGACY,c.incidentStatusColumnName(),
                c.orderByColumns(),c.conditionWindows());
        var issues = new Issues(); var output = apply(source,legacy,issues).propagatedTables().get("incidents");
        assertFalse(issues.hasErrors(),issues::toString);
        assertEquals(2,output.dataset().count());
        assertFalse(output.dataset().queryExecution().analyzed().toString().contains("not a name"));
    }

    @Test void aggregateSchemasUseAnalyzedNumericPromotionNotSourceTypeGuesses() {
        var raw = table(List.of(row(1,0,10d),row(1,1,20d)));
        for (String cast : List.of("int","decimal(12,2)")) {
            var data = raw.dataset().withColumn("speed",functions.col("speed").cast(cast));
            var source = new SparkCanvasTable(new CanvasTableSchema("events",null,SparkTypeMapper.fromStructType(data.schema(),List.of())),data);
            var result = bindings(source,config(List.of(window("sum",TrackIncidentWindow.Kind.SUM,-1,1),
                    window("mean",TrackIncidentWindow.Kind.MEAN,-1,1)),"sum",null),new Issues());
            var columns = CanvasNodeSupport.columns(result.schema());
            if (cast.equals("int")) {
                assertEquals(PlatformDataType.LONG,columns.get("sum").fieldType());
                assertEquals(PlatformDataType.DOUBLE,columns.get("mean").fieldType());
            } else {
                assertEquals(PlatformDataType.DECIMAL,columns.get("sum").fieldType());
                assertEquals(22,columns.get("sum").precision()); assertEquals(2,columns.get("sum").scale());
                assertEquals(16,columns.get("mean").precision()); assertEquals(6,columns.get("mean").scale());
            }
        }
    }

    private SparkCanvasTable bindings(SparkCanvasTable source, TrackDetectIncidentsConfiguration c, Issues issues) {
        var track = TrackNodeSupport.prepare(source,null,false,c.trackIdColumns(),c.timeColumnName(),null,c.boundaries(),issues,"configuration",c.orderByColumns());
        var result = IncidentWindowPlan.prepare(c,source,track,issues);
        assertFalse(issues.hasErrors(), issues::toString); return result;
    }

    private CanvasNodeOperationResult apply(SparkCanvasTable source, TrackDetectIncidentsConfiguration c, Issues issues) {
        return new TrackDetectIncidentsNodeOperator().apply(new TrackDetectIncidentsNodeDefinition(UUID.randomUUID().toString(),"事件",
                new CanvasNodeLayout(0d,0d,360d,216d),c), Map.of("events",source), new CanvasNodeOperationContext(spark,
                MetadataIndex.create(new MetadataSnapshot(List.of(),List.of())),issues,new SchemaOnlyCanvasNodeDataAccess(spark)));
    }

    private TrackDetectIncidentsConfiguration config(List<TrackIncidentWindow> windows, String condition, TrackFixedTimeBoundary fixed) {
        return new TrackDetectIncidentsConfiguration("events",null,List.of("track"),"time",null,
                new TrackBoundaryConfiguration(null,null,null,null,fixed),
                new CanvasFieldPredicate(condition,FilterOperator.GREATER_THAN,List.of(new CanvasLiteral(PlatformDataType.DOUBLE,"15"))),
                null,TrackIncidentResultMode.ALL_EVENTS,"incidents","incident","flag","start","end","duration",
                SpatialDurationUnit.SECONDS,TrackIncidentSemantics.CONDITION_LIFECYCLE,"status",List.of("event.id"),windows);
    }
    private TrackIncidentWindow window(String name, TrackIncidentWindow.Kind kind, int start, int end) {
        return new TrackIncidentWindow(name,"speed",kind,start,end);
    }
    private SparkCanvasTable table(List<Row> rows) {
        var schema = new CanvasTableSchema("events",null,List.of(column("track",PlatformDataType.LONG),column("time",PlatformDataType.TIMESTAMP),
                column("speed",PlatformDataType.DOUBLE),column("event.id",PlatformDataType.LONG)));
        return new SparkCanvasTable(schema,spark.createDataFrame(rows,SparkTypeMapper.toStructType(schema.columns())).repartition(2));
    }
    private CanvasColumnSchema column(String name, PlatformDataType type) { return new CanvasColumnSchema(name,type,null,null,null,true,null,false,false,null); }
    private Row row(long track, int time, Double speed) { return RowFactory.create(track,stamp(time),speed,(long)time); }
    private Timestamp stamp(int seconds) { return Timestamp.from(Instant.parse("2026-01-01T00:00:00Z").plusSeconds(seconds)); }
    private static final class Issues implements CanvasNodeIssueSink {
        private final List<String> codes = new ArrayList<>(), paths = new ArrayList<>();
        @Override public void error(String code, String message, String path) { codes.add(code); paths.add(path); }
        @Override public void warning(String code, String message, String path) { }
        @Override public boolean hasErrors() { return !codes.isEmpty(); }
        @Override public String toString() { return codes.toString(); }
    }
}
