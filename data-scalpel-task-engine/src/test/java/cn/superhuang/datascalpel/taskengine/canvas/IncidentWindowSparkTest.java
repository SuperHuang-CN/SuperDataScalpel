package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.*;
import cn.superhuang.data.scalpel.contract.type.CoordinateDimension;
import cn.superhuang.data.scalpel.contract.type.CrsReference;
import cn.superhuang.data.scalpel.contract.type.GeometryKind;
import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;
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

    @Test void trackDistanceWindowsAggregateHalfOpenCumulativeGeodesicMetres() {
        var source = distanceTable(List.of(row(1,0,10d),row(1,1,20d),row(1,2,30d)),4326);
        var windows = List.of(
                new TrackIncidentWindow("travelled", null, TrackIncidentWindow.Kind.SUM,
                        -1, 2, TrackIncidentWindow.Source.TRACK_DISTANCE),
                new TrackIncidentWindow("current", null, TrackIncidentWindow.Kind.FIRST,
                        0, 1, TrackIncidentWindow.Source.TRACK_DISTANCE),
                new TrackIncidentWindow("count", null, TrackIncidentWindow.Kind.COUNT,
                        -1, 2, TrackIncidentWindow.Source.TRACK_DISTANCE));
        var c = withPoint(config(windows,"travelled",null), "shape");
        var rows = bindings(source,c,new Issues()).dataset().orderBy("time").collectAsList();
        assertEquals(111_319.49d, rows.getFirst().<Double>getAs("travelled"), 1d);
        assertEquals(333_958.47d, rows.get(1).<Double>getAs("travelled"), 3d);
        assertEquals(333_958.47d, rows.getLast().<Double>getAs("travelled"), 3d);
        assertEquals(0d, rows.getFirst().<Double>getAs("current"), 0d);
        assertEquals(111_319.49d, rows.get(1).<Double>getAs("current"), 1d);
        assertEquals(222_638.98d, rows.getLast().<Double>getAs("current"), 2d);
        assertEquals(List.of(2L,3L,2L), rows.stream().map(row -> row.<Long>getAs("count")).toList());
    }

    @Test void trackSpeedWindowsUseHalfOpenObservationValuesInMetresPerSecond() {
        var source = distanceTable(List.of(
                RowFactory.create(1L,stamp(0),10d,0L),
                RowFactory.create(1L,stamp(60),20d,1L),
                RowFactory.create(1L,stamp(120),30d,2L)),4326);
        var windows = List.of(
                new TrackIncidentWindow("velocity", null, TrackIncidentWindow.Kind.MEAN,
                        -1, 2, TrackIncidentWindow.Source.TRACK_SPEED),
                new TrackIncidentWindow("current", null, TrackIncidentWindow.Kind.FIRST,
                        0, 1, TrackIncidentWindow.Source.TRACK_SPEED),
                new TrackIncidentWindow("count", null, TrackIncidentWindow.Kind.COUNT,
                        -1, 2, TrackIncidentWindow.Source.TRACK_SPEED));
        var rows = bindings(source,withPoint(config(windows,"velocity",null),"shape"),new Issues())
                .dataset().orderBy("time").collectAsList();
        double speed = 111_319.49d / 60d;
        assertEquals(speed / 2d, rows.getFirst().<Double>getAs("velocity"), 0.1d);
        assertEquals(speed * 2d / 3d, rows.get(1).<Double>getAs("velocity"), 0.1d);
        assertEquals(speed, rows.getLast().<Double>getAs("velocity"), 0.1d);
        assertEquals(0d, rows.getFirst().<Double>getAs("current"), 0d);
        assertEquals(speed, rows.get(1).<Double>getAs("current"), 0.1d);
        assertEquals(speed, rows.getLast().<Double>getAs("current"), 0.1d);
        assertEquals(List.of(2L,3L,2L), rows.stream().map(row -> row.<Long>getAs("count")).toList());
    }

    @Test void trackAccelerationWindowsUseHalfOpenObservationValuesInMetresPerSecondSquared() {
        var source = distanceTable(List.of(
                RowFactory.create(1L,stamp(0),10d,0L),
                RowFactory.create(1L,stamp(60),20d,1L),
                RowFactory.create(1L,stamp(120),30d,2L)),4326);
        var windows = List.of(
                new TrackIncidentWindow("acceleration", null, TrackIncidentWindow.Kind.MEAN,
                        -1, 2, TrackIncidentWindow.Source.TRACK_ACCELERATION),
                new TrackIncidentWindow("current", null, TrackIncidentWindow.Kind.FIRST,
                        0, 1, TrackIncidentWindow.Source.TRACK_ACCELERATION),
                new TrackIncidentWindow("count", null, TrackIncidentWindow.Kind.COUNT,
                        -1, 2, TrackIncidentWindow.Source.TRACK_ACCELERATION));
        var rows = bindings(source,withPoint(config(windows,"acceleration",null),"shape"),new Issues())
                .dataset().orderBy("time").collectAsList();
        double acceleration = 111_319.49d / 3_600d;
        assertEquals(acceleration / 2d, rows.getFirst().<Double>getAs("acceleration"), 0.01d);
        assertEquals(acceleration / 3d, rows.get(1).<Double>getAs("acceleration"), 0.01d);
        assertEquals(acceleration / 2d, rows.getLast().<Double>getAs("acceleration"), 0.01d);
        assertEquals(0d, rows.getFirst().<Double>getAs("current"), 0d);
        assertEquals(acceleration, rows.get(1).<Double>getAs("current"), 0.01d);
        assertEquals(0d, rows.getLast().<Double>getAs("current"), 0.01d);
        assertEquals(List.of(2L,3L,2L), rows.stream().map(row -> row.<Long>getAs("count")).toList());
    }

    @Test void trackMotionWindowsRequireWgs84XyPointGeometry() {
        var window = new TrackIncidentWindow("travelled", null, TrackIncidentWindow.Kind.SUM,
                -1, 1, TrackIncidentWindow.Source.TRACK_DISTANCE);
        var noGeometry = config(List.of(window),"travelled",null);
        var missingIssues = new Issues();
        assertTrue(apply(table(List.of()),noGeometry,missingIssues).propagatedTables().isEmpty());
        assertTrue(missingIssues.codes.contains("TRACK_INCIDENT_DISTANCE_WINDOW_REQUIRES_GEOMETRY"));

        var projected = withPoint(noGeometry,"shape");
        var projectedIssues = new Issues();
        assertTrue(apply(distanceTable(List.of(),3857),projected,projectedIssues).propagatedTables().isEmpty());
        assertTrue(projectedIssues.codes.contains("GEODESIC_REQUIRES_WGS84_XY"));

        var speedWindow = new TrackIncidentWindow("velocity", null, TrackIncidentWindow.Kind.MEAN,
                -1, 1, TrackIncidentWindow.Source.TRACK_SPEED);
        var speedIssues = new Issues();
        assertTrue(apply(table(List.of()),config(List.of(speedWindow),"velocity",null),speedIssues)
                .propagatedTables().isEmpty());
        assertTrue(speedIssues.codes.contains("TRACK_INCIDENT_SPEED_WINDOW_REQUIRES_GEOMETRY"));

        var accelerationWindow = new TrackIncidentWindow("acceleration", null, TrackIncidentWindow.Kind.MAX,
                -1, 1, TrackIncidentWindow.Source.TRACK_ACCELERATION);
        var accelerationIssues = new Issues();
        assertTrue(apply(table(List.of()),config(List.of(accelerationWindow),"acceleration",null),accelerationIssues)
                .propagatedTables().isEmpty());
        assertTrue(accelerationIssues.codes.contains("TRACK_INCIDENT_ACCELERATION_WINDOW_REQUIRES_GEOMETRY"));
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

    @Test void trackScalarsUseEpochMillisAndResetAtPreparedSegments() {
        var source = table(List.of(row(1,0,10d),row(1,1,20d),row(1,2,30d),row(1,3,40d)));
        var boundary = new TrackFixedTimeBoundary(2,TrackTimeBoundaryUnit.SECONDS,"2026-01-01T00:00:00","UTC");
        var scalars = List.of(
                new TrackIncidentScalar("track_start",TrackIncidentScalar.Source.TRACK_START_TIME),
                new TrackIncidentScalar("elapsed",TrackIncidentScalar.Source.TRACK_DURATION),
                new TrackIncidentScalar("current_time",TrackIncidentScalar.Source.TRACK_CURRENT_TIME),
                new TrackIncidentScalar("track_index",TrackIncidentScalar.Source.TRACK_INDEX));
        var rows = bindings(source,withScalars(config(List.of(),"elapsed",boundary),scalars),new Issues())
                .dataset().orderBy("time").collectAsList();
        long epoch = Instant.parse("2026-01-01T00:00:00Z").toEpochMilli();
        assertEquals(List.of(epoch,epoch,epoch + 2_000L,epoch + 2_000L),
                rows.stream().map(row -> row.<Long>getAs("track_start")).toList());
        assertEquals(List.of(0L,1_000L,0L,1_000L),
                rows.stream().map(row -> row.<Long>getAs("elapsed")).toList());
        assertEquals(List.of(epoch,epoch + 1_000L,epoch + 2_000L,epoch + 3_000L),
                rows.stream().map(row -> row.<Long>getAs("current_time")).toList());
        assertEquals(List.of(0L,1L,0L,1L),
                rows.stream().map(row -> row.<Long>getAs("track_index")).toList());
    }

    @Test void trackPointCoordinateScalarsReadRelativeObservationsWithinPreparedSegments() {
        var source = distanceTable(List.of(row(1,0,10d),row(1,1,20d),row(1,2,30d),row(1,3,40d)),4326);
        var boundary = new TrackFixedTimeBoundary(2,TrackTimeBoundaryUnit.SECONDS,
                "2026-01-01T00:00:00","UTC");
        var scalars = List.of(
                new TrackIncidentScalar("previous_x",TrackIncidentScalar.Source.TRACK_POINT_X_AT,-1),
                new TrackIncidentScalar("current_x",TrackIncidentScalar.Source.TRACK_POINT_X_AT,0),
                new TrackIncidentScalar("next_y",TrackIncidentScalar.Source.TRACK_POINT_Y_AT,1));
        var configuration = withScalars(withPoint(config(List.of(),"current_x",boundary),"shape"),scalars);
        var rows = bindings(source,configuration,new Issues()).dataset().orderBy("time").collectAsList();
        assertEquals(Arrays.asList(null,0d,null,2d),
                rows.stream().map(row -> row.<Double>getAs("previous_x")).toList());
        assertEquals(List.of(0d,1d,2d,3d),
                rows.stream().map(row -> row.<Double>getAs("current_x")).toList());
        assertEquals(Arrays.asList(0d,null,0d,null),
                rows.stream().map(row -> row.<Double>getAs("next_y")).toList());
        assertEquals(PlatformDataType.DOUBLE,CanvasNodeSupport.columns(bindings(
                source,configuration,new Issues()).schema()).get("current_x").fieldType());
    }

    @Test void trackPointCoordinateScalarsRequirePointGeometryAndOffset() {
        var missingGeometry = withScalars(config(List.of(),"speed",null),List.of(
                new TrackIncidentScalar("previous_x",TrackIncidentScalar.Source.TRACK_POINT_X_AT,-1)));
        var geometryIssues = new Issues();
        assertNull(bindingsOrNull(table(List.of()),missingGeometry,geometryIssues));
        assertTrue(geometryIssues.codes.contains("TRACK_INCIDENT_POINT_COORDINATE_REQUIRES_GEOMETRY"));

        var missingOffset = withScalars(withPoint(config(List.of(),"speed",null),"shape"),List.of(
                new TrackIncidentScalar("previous_x",TrackIncidentScalar.Source.TRACK_POINT_X_AT,null)));
        var offsetIssues = new Issues();
        assertNull(bindingsOrNull(distanceTable(List.of(),4326),missingOffset,offsetIssues));
        assertTrue(offsetIssues.codes.contains("TRACK_INCIDENT_POINT_COORDINATE_OFFSET_REQUIRED"));
    }

    @Test void trackScalarBindingsCannotShadowColumnsWindowsOrEachOther() {
        var source = table(List.of());
        for (var scalars : List.of(
                List.of(new TrackIncidentScalar("speed",TrackIncidentScalar.Source.TRACK_INDEX)),
                List.of(new TrackIncidentScalar("metric",null)),
                List.of(new TrackIncidentScalar("duplicate",TrackIncidentScalar.Source.TRACK_INDEX),
                        new TrackIncidentScalar("DUPLICATE",TrackIncidentScalar.Source.TRACK_DURATION)))) {
            var issues = new Issues();
            assertNull(bindingsOrNull(source,withScalars(config(List.of(),"speed",null),scalars),issues));
            assertTrue(issues.paths.stream().anyMatch(path -> path.startsWith("configuration.conditionScalars")));
        }
        var issues = new Issues();
        assertNull(bindingsOrNull(source,withScalars(config(List.of(
                window("metric",TrackIncidentWindow.Kind.MEAN,-1,1)),"speed",null),
                List.of(new TrackIncidentScalar("METRIC",TrackIncidentScalar.Source.TRACK_INDEX))),issues));
        assertTrue(issues.hasErrors());
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

    @Test void trackMotionConditionLineageResolvesToGeometryAndTimeWithoutUnknownSources() {
        var raw = distanceTable(List.of(),4326);
        var asset = new TaskLineageEvidence.Asset("input",TaskLineageEvidence.AssetRole.INPUT,
                TaskLineageEvidence.AssetKind.JDBC_TABLE,null,null,null,null,UUID.randomUUID(),null,null,
                "events",null,null,"events");
        var fields = new LinkedHashMap<String,CatalystLineageMetadata.InputField>();
        raw.schema().columns().forEach(col -> fields.put(col.name(),new CatalystLineageMetadata.InputField("input:" + col.name(),null)));
        var source = new SparkCanvasTable(raw.schema(),CatalystLineageMetadata.markInput(raw.dataset(),"input-node",asset,fields));
        var distance = new TrackIncidentWindow("travelled",null,TrackIncidentWindow.Kind.SUM,
                -1,2,TrackIncidentWindow.Source.TRACK_DISTANCE);
        var issues = new Issues();
        var output = apply(source,withPoint(config(List.of(distance),"travelled",null),"shape"),issues)
                .propagatedTables().get("incidents");
        assertFalse(issues.hasErrors(),issues::toString);
        var target = new TaskLineageEvidence.Asset("output",TaskLineageEvidence.AssetRole.OUTPUT,
                TaskLineageEvidence.AssetKind.JDBC_TABLE,null,TaskLineageEvidence.WriteMode.APPEND,null,null,
                UUID.randomUUID(),null,null,"incidents",null,null,"incidents");
        var candidate = new CatalystLineageOutputCandidate("flow","output-node","JDBC_OUTPUT","write",output.dataset(),target,
                output.schema().columns().stream().map(col -> new CatalystLineageOutputCandidate.TargetField("out:" + col.name(),null,
                        col.name(),col.name(),TaskLineageEvidence.OutputEffect.WRITTEN_UNKNOWN_SOURCE)).toList());
        var flow = new CatalystLineageAnalyzer().analyze(List.of(candidate)).flows().getFirst();
        assertEquals(TaskLineageEvidence.Coverage.FIELD_COMPLETE,flow.coverage(),() -> flow.warnings().toString());
        assertTrue(flow.fieldEdges().stream().anyMatch(edge -> edge.target().localFieldKey().equals("out:flag")
                && edge.source().localFieldKey().equals("input:shape")));
        assertTrue(flow.fieldEdges().stream().allMatch(edge -> edge.source().localFieldKey().startsWith("input:")));

        var speed = new TrackIncidentWindow("velocity",null,TrackIncidentWindow.Kind.MEAN,
                -1,2,TrackIncidentWindow.Source.TRACK_SPEED);
        var speedIssues = new Issues();
        var speedOutput = apply(source,withPoint(config(List.of(speed),"velocity",null),"shape"),speedIssues)
                .propagatedTables().get("incidents");
        assertFalse(speedIssues.hasErrors(),speedIssues::toString);
        var speedCandidate = new CatalystLineageOutputCandidate("speed-flow","output-node","JDBC_OUTPUT","write",
                speedOutput.dataset(),target,speedOutput.schema().columns().stream().map(col ->
                new CatalystLineageOutputCandidate.TargetField("speed-out:" + col.name(),null,col.name(),col.name(),
                        TaskLineageEvidence.OutputEffect.WRITTEN_UNKNOWN_SOURCE)).toList());
        var speedFlow = new CatalystLineageAnalyzer().analyze(List.of(speedCandidate)).flows().getFirst();
        assertEquals(TaskLineageEvidence.Coverage.FIELD_COMPLETE,speedFlow.coverage(),() -> speedFlow.warnings().toString());
        assertTrue(speedFlow.fieldEdges().stream().anyMatch(edge -> edge.target().localFieldKey().equals("speed-out:flag")
                && edge.source().localFieldKey().equals("input:shape")));
        assertTrue(speedFlow.fieldEdges().stream().anyMatch(edge -> edge.target().localFieldKey().equals("speed-out:flag")
                && edge.source().localFieldKey().equals("input:time")));

        var acceleration = new TrackIncidentWindow("acceleration",null,TrackIncidentWindow.Kind.MAX,
                -1,2,TrackIncidentWindow.Source.TRACK_ACCELERATION);
        var accelerationIssues = new Issues();
        var accelerationOutput = apply(source,withPoint(config(List.of(acceleration),"acceleration",null),"shape"),
                accelerationIssues).propagatedTables().get("incidents");
        assertFalse(accelerationIssues.hasErrors(),accelerationIssues::toString);
        var accelerationCandidate = new CatalystLineageOutputCandidate("acceleration-flow","output-node","JDBC_OUTPUT","write",
                accelerationOutput.dataset(),target,accelerationOutput.schema().columns().stream().map(col ->
                new CatalystLineageOutputCandidate.TargetField("acceleration-out:" + col.name(),null,col.name(),col.name(),
                        TaskLineageEvidence.OutputEffect.WRITTEN_UNKNOWN_SOURCE)).toList());
        var accelerationFlow = new CatalystLineageAnalyzer().analyze(List.of(accelerationCandidate)).flows().getFirst();
        assertEquals(TaskLineageEvidence.Coverage.FIELD_COMPLETE,accelerationFlow.coverage(),
                () -> accelerationFlow.warnings().toString());
        assertTrue(accelerationFlow.fieldEdges().stream().anyMatch(edge ->
                edge.target().localFieldKey().equals("acceleration-out:flag")
                        && edge.source().localFieldKey().equals("input:shape")));
        assertTrue(accelerationFlow.fieldEdges().stream().anyMatch(edge ->
                edge.target().localFieldKey().equals("acceleration-out:flag")
                        && edge.source().localFieldKey().equals("input:time")));
    }

    @Test void trackScalarConditionLineageResolvesToOriginalTimeWithoutUnknownSources() {
        var raw = table(List.of());
        var asset = new TaskLineageEvidence.Asset("input",TaskLineageEvidence.AssetRole.INPUT,
                TaskLineageEvidence.AssetKind.JDBC_TABLE,null,null,null,null,UUID.randomUUID(),null,null,
                "events",null,null,"events");
        var fields = new LinkedHashMap<String,CatalystLineageMetadata.InputField>();
        raw.schema().columns().forEach(col -> fields.put(col.name(),
                new CatalystLineageMetadata.InputField("input:" + col.name(),null)));
        var source = new SparkCanvasTable(raw.schema(),
                CatalystLineageMetadata.markInput(raw.dataset(),"input-node",asset,fields));
        var configuration = withScalars(config(List.of(),"elapsed",null),List.of(
                new TrackIncidentScalar("elapsed",TrackIncidentScalar.Source.TRACK_DURATION)));
        var issues = new Issues();
        var output = apply(source,configuration,issues).propagatedTables().get("incidents");
        assertFalse(issues.hasErrors(),issues::toString);
        var target = new TaskLineageEvidence.Asset("output",TaskLineageEvidence.AssetRole.OUTPUT,
                TaskLineageEvidence.AssetKind.JDBC_TABLE,null,TaskLineageEvidence.WriteMode.APPEND,null,null,
                UUID.randomUUID(),null,null,"incidents",null,null,"incidents");
        var candidate = new CatalystLineageOutputCandidate("scalar-flow","output-node","JDBC_OUTPUT","write",
                output.dataset(),target,output.schema().columns().stream().map(col ->
                new CatalystLineageOutputCandidate.TargetField("scalar-out:" + col.name(),null,col.name(),col.name(),
                        TaskLineageEvidence.OutputEffect.WRITTEN_UNKNOWN_SOURCE)).toList());
        var flow = new CatalystLineageAnalyzer().analyze(List.of(candidate)).flows().getFirst();
        assertEquals(TaskLineageEvidence.Coverage.FIELD_COMPLETE,flow.coverage(),() -> flow.warnings().toString());
        assertTrue(flow.fieldEdges().stream().allMatch(edge -> edge.source().localFieldKey().startsWith("input:")));
        assertTrue(flow.fieldEdges().stream().anyMatch(edge -> edge.target().localFieldKey().equals("scalar-out:flag")
                && edge.source().localFieldKey().equals("input:time")));
    }

    @Test void pointCoordinateConditionLineageResolvesToOriginalGeometryWithoutUnknownSources() {
        var raw = distanceTable(List.of(),4326);
        var asset = new TaskLineageEvidence.Asset("input",TaskLineageEvidence.AssetRole.INPUT,
                TaskLineageEvidence.AssetKind.JDBC_TABLE,null,null,null,null,UUID.randomUUID(),null,null,
                "events",null,null,"events");
        var fields = new LinkedHashMap<String,CatalystLineageMetadata.InputField>();
        raw.schema().columns().forEach(col -> fields.put(col.name(),
                new CatalystLineageMetadata.InputField("input:" + col.name(),null)));
        var source = new SparkCanvasTable(raw.schema(),
                CatalystLineageMetadata.markInput(raw.dataset(),"input-node",asset,fields));
        var configuration = withScalars(withPoint(config(List.of(),"previous_x",null),"shape"),List.of(
                new TrackIncidentScalar("previous_x",TrackIncidentScalar.Source.TRACK_POINT_X_AT,-1)));
        var issues = new Issues();
        var output = apply(source,configuration,issues).propagatedTables().get("incidents");
        assertFalse(issues.hasErrors(),issues::toString);
        var target = new TaskLineageEvidence.Asset("output",TaskLineageEvidence.AssetRole.OUTPUT,
                TaskLineageEvidence.AssetKind.JDBC_TABLE,null,TaskLineageEvidence.WriteMode.APPEND,null,null,
                UUID.randomUUID(),null,null,"incidents",null,null,"incidents");
        var candidate = new CatalystLineageOutputCandidate("coordinate-flow","output-node","JDBC_OUTPUT","write",
                output.dataset(),target,output.schema().columns().stream().map(col ->
                new CatalystLineageOutputCandidate.TargetField("coordinate-out:" + col.name(),null,col.name(),col.name(),
                        TaskLineageEvidence.OutputEffect.WRITTEN_UNKNOWN_SOURCE)).toList());
        var flow = new CatalystLineageAnalyzer().analyze(List.of(candidate)).flows().getFirst();
        assertEquals(TaskLineageEvidence.Coverage.FIELD_COMPLETE,flow.coverage(),() -> flow.warnings().toString());
        assertTrue(flow.fieldEdges().stream().allMatch(edge -> edge.source().localFieldKey().startsWith("input:")));
        assertTrue(flow.fieldEdges().stream().anyMatch(edge ->
                edge.target().localFieldKey().equals("coordinate-out:flag")
                        && edge.source().localFieldKey().equals("input:shape")));
    }

    @Test void everyLifecycleOutputFieldHasCompleteInputLineageInBothResultModes() {
        var raw = table(List.of(
                row(1, 0, 10d), row(1, 1, 20d), row(1, 2, 30d), row(1, 3, 5d)));
        var inputAsset = new TaskLineageEvidence.Asset(
                "incident-input", TaskLineageEvidence.AssetRole.INPUT,
                TaskLineageEvidence.AssetKind.JDBC_TABLE, null, null, null, null,
                UUID.randomUUID(), null, null, "events", null, null, "events");
        var inputFields = new LinkedHashMap<String, CatalystLineageMetadata.InputField>();
        raw.schema().columns().forEach(column -> inputFields.put(column.name(),
                new CatalystLineageMetadata.InputField("incident-input:" + column.name(), null)));
        var source = new SparkCanvasTable(raw.schema(), CatalystLineageMetadata.markInput(
                raw.dataset(), "incident-input-node", inputAsset, inputFields));

        for (TrackIncidentResultMode mode : TrackIncidentResultMode.values()) {
            var base = config(List.of(window("past_mean", TrackIncidentWindow.Kind.MEAN, -2, 1)),
                    "past_mean", null);
            var configuration = new TrackDetectIncidentsConfiguration(
                    base.sourceTableName(), base.pointGeometryColumnName(), base.trackIdColumns(),
                    base.timeColumnName(), base.distanceMethod(), base.boundaries(), base.startCondition(),
                    base.endCondition(), mode, base.outputTableName(), base.incidentIdColumnName(),
                    base.incidentFlagColumnName(), base.incidentStartTimeColumnName(),
                    base.incidentEndTimeColumnName(), base.incidentDurationColumnName(),
                    base.incidentDurationUnit(), base.incidentSemantics(), base.incidentStatusColumnName(),
                    base.orderByColumns(), base.conditionWindows(), base.conditionScalars());
            var issues = new Issues();
            var output = apply(source, configuration, issues).propagatedTables().get("incidents");
            assertFalse(issues.hasErrors(), issues::toString);
            var outputAsset = new TaskLineageEvidence.Asset(
                    "incident-output-" + mode, TaskLineageEvidence.AssetRole.OUTPUT,
                    TaskLineageEvidence.AssetKind.JDBC_TABLE, null,
                    TaskLineageEvidence.WriteMode.APPEND, null, null, UUID.randomUUID(),
                    null, null, "incidents", null, null, "incidents");
            String outputKey = outputAsset.localAssetKey();
            var candidate = new CatalystLineageOutputCandidate(
                    "incident-flow-" + mode, "output-node", "JDBC_OUTPUT", "write-" + mode,
                    output.dataset(), outputAsset,
                    output.schema().columns().stream().map(column ->
                            new CatalystLineageOutputCandidate.TargetField(
                                    outputKey + ":" + column.name(), null, column.name(), column.name(),
                                    TaskLineageEvidence.OutputEffect.WRITTEN_UNKNOWN_SOURCE)).toList());

            var flow = new CatalystLineageAnalyzer().analyze(List.of(candidate)).flows().getFirst();

            assertEquals(TaskLineageEvidence.Coverage.FIELD_COMPLETE, flow.coverage(),
                    () -> mode + ": " + flow.warnings());
            assertTrue(flow.fields().stream().filter(field -> field.localAssetKey().equals(outputKey))
                    .noneMatch(field -> field.outputEffect()
                            == TaskLineageEvidence.OutputEffect.WRITTEN_UNKNOWN_SOURCE));
            for (CanvasColumnSchema sourceField : raw.schema().columns()) {
                assertTrue(flow.fieldEdges().stream().anyMatch(edge ->
                                edge.target().localFieldKey().equals(outputKey + ":" + sourceField.name())
                                        && edge.source().localFieldKey().equals(
                                        "incident-input:" + sourceField.name())
                                        && edge.derivationType()
                                        == TaskLineageEvidence.DerivationType.DIRECT),
                        () -> mode + " should copy " + sourceField.name() + " directly");
            }
            for (String derived : List.of("incident", "flag", "status", "start", "end", "duration")) {
                assertTrue(flow.fieldEdges().stream().anyMatch(edge ->
                                edge.target().localFieldKey().equals(outputKey + ":" + derived)
                                        && edge.source().localFieldKey().startsWith("incident-input:")),
                        () -> mode + " should trace " + derived + " to an input field");
            }
        }
    }

    @Test void largeLifecycleTrackStaysLazyAndProducesOneRowPerObservation() {
        int observationCount = 20_000;
        Dataset<Row> data = spark.range(observationCount).select(
                functions.lit(1L).alias("track"),
                functions.to_timestamp(functions.from_unixtime(functions.col("id"))).alias("time"),
                functions.lit(20d).alias("speed"),
                functions.col("id").alias("event.id"));
        var schema = new CanvasTableSchema("events", null, List.of(
                column("track", PlatformDataType.LONG),
                column("time", PlatformDataType.TIMESTAMP),
                column("speed", PlatformDataType.DOUBLE),
                column("event.id", PlatformDataType.LONG)));
        var source = new SparkCanvasTable(schema, data);
        String group = "large-incident-preflight-" + UUID.randomUUID();
        spark.sparkContext().setJobGroup(group, "large incident preflight", false);
        SparkCanvasTable output;
        try {
            var issues = new Issues();
            output = apply(source, config(List.of(), "speed", null), issues)
                    .propagatedTables().get("incidents");
            assertFalse(issues.hasErrors(), issues::toString);
            output.dataset().queryExecution().analyzed();
            assertEquals(0, spark.sparkContext().statusTracker().getJobIdsForGroup(group).length);
        } finally {
            spark.sparkContext().clearJobGroup();
        }

        assertEquals(observationCount, output.dataset().count());
        Row last = output.dataset().orderBy(functions.col("`event.id`").desc()).head();
        assertEquals(true, last.getAs("flag"));
        assertEquals("OnGoing", last.getAs("status"));
        assertEquals(observationCount - 1d, (Double) last.getAs("duration"), 0d);
        String plan = output.dataset().queryExecution().executedPlan().toString();
        assertFalse(plan.contains("CollectLimit") || plan.contains("collect_list"), plan);
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
        var result = bindingsOrNull(source,c,issues);
        assertFalse(issues.hasErrors(), issues::toString); return result;
    }
    private SparkCanvasTable bindingsOrNull(SparkCanvasTable source, TrackDetectIncidentsConfiguration c, Issues issues) {
        var track = TrackNodeSupport.prepare(source,c.pointGeometryColumnName(),false,c.trackIdColumns(),c.timeColumnName(),
                c.distanceMethod(),c.boundaries(),issues,"configuration",c.orderByColumns());
        return IncidentWindowPlan.prepare(c,source,track,issues);
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
    private TrackDetectIncidentsConfiguration withPoint(TrackDetectIncidentsConfiguration c, String point) {
        return new TrackDetectIncidentsConfiguration(c.sourceTableName(),point,c.trackIdColumns(),c.timeColumnName(),
                c.distanceMethod(),c.boundaries(),c.startCondition(),c.endCondition(),c.resultMode(),c.outputTableName(),
                c.incidentIdColumnName(),c.incidentFlagColumnName(),c.incidentStartTimeColumnName(),c.incidentEndTimeColumnName(),
                c.incidentDurationColumnName(),c.incidentDurationUnit(),c.incidentSemantics(),c.incidentStatusColumnName(),
                c.orderByColumns(),c.conditionWindows());
    }
    private TrackDetectIncidentsConfiguration withScalars(
            TrackDetectIncidentsConfiguration c, List<TrackIncidentScalar> scalars
    ) {
        return new TrackDetectIncidentsConfiguration(c.sourceTableName(),c.pointGeometryColumnName(),c.trackIdColumns(),
                c.timeColumnName(),c.distanceMethod(),c.boundaries(),c.startCondition(),c.endCondition(),c.resultMode(),
                c.outputTableName(),c.incidentIdColumnName(),c.incidentFlagColumnName(),c.incidentStartTimeColumnName(),
                c.incidentEndTimeColumnName(),c.incidentDurationColumnName(),c.incidentDurationUnit(),
                c.incidentSemantics(),c.incidentStatusColumnName(),c.orderByColumns(),c.conditionWindows(),scalars);
    }
    private SparkCanvasTable table(List<Row> rows) {
        var schema = new CanvasTableSchema("events",null,List.of(column("track",PlatformDataType.LONG),column("time",PlatformDataType.TIMESTAMP),
                column("speed",PlatformDataType.DOUBLE),column("event.id",PlatformDataType.LONG)));
        return new SparkCanvasTable(schema,spark.createDataFrame(rows,SparkTypeMapper.toStructType(schema.columns())).repartition(2));
    }
    private SparkCanvasTable distanceTable(List<Row> rows, int epsg) {
        var source = table(rows);
        var data = source.dataset().withColumn("shape", functions.expr(
                "ST_SetSRID(ST_Point(CAST(`event.id` AS DOUBLE), 0D), " + epsg + ")"));
        var columns = new ArrayList<>(source.schema().columns());
        columns.add(new CanvasColumnSchema("shape",PlatformDataType.GEOMETRY,null,null,null,true,null,false,false,null,
                new GeometryTypeDefinition(GeometryKind.POINT,new CrsReference("EPSG",epsg),CoordinateDimension.XY)));
        return new SparkCanvasTable(new CanvasTableSchema("events",null,columns),data);
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
