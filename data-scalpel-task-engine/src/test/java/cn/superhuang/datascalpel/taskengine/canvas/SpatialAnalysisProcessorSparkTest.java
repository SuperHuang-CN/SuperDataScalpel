package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.*;
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
import org.apache.spark.sql.Row;
import org.apache.spark.sql.RowFactory;
import org.apache.spark.sql.SparkSession;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.locationtech.jts.geom.Geometry;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SpatialAnalysisProcessorSparkTest {
    private static final CanvasNodeLayout LAYOUT = new CanvasNodeLayout(0d, 0d, 320d, 200d);
    private SparkSession spark;

    @BeforeAll
    void startSpark() {
        spark = SedonaSparkSupport.initialize(SedonaSparkSupport.builder()
                .master("local[1]")
                .appName("spatial-analysis-processor-test")
                .config("spark.ui.enabled", "false")
                .config("spark.driver.host", "127.0.0.1")
                .config("spark.driver.bindAddress", "127.0.0.1")
                .config("spark.sql.shuffle.partitions", "1")
                .config("spark.sql.caseSensitive", "true")
                .getOrCreate());
    }

    @AfterAll
    void stopSpark() {
        if (spark != null) spark.stop();
    }

    @Test
    void derivesAndSimplifiesGeometryWithoutReplacingTheSource() {
        SparkCanvasTable parcels = geometryTable(
                "parcels_raw", "parcels", "shape", GeometryKind.POLYGON, 3857,
                List.of(longColumn("id"), stringColumn("wkt")),
                List.of(RowFactory.create(1L, "POLYGON ((0 0, 0 4, 4 4, 4 0, 0 0))")));
        RecordingIssueSink deriveIssues = new RecordingIssueSink();
        CanvasNodeOperationResult derived = new GeometryDeriveNodeOperator().apply(
                new GeometryDeriveNodeDefinition(id(), "派生", LAYOUT,
                        new GeometryDeriveConfiguration("parcels", "parcels_derived", List.of(
                                new GeometryDerivation(id(), GeometryDeriveKind.CENTROID, "shape", "center"),
                                new GeometryDerivation(id(), GeometryDeriveKind.ENVELOPE, "shape", "envelope")))),
                Map.of("parcels", parcels), context(deriveIssues));

        Row derivedRow = derived.propagatedTables().get("parcels_derived").dataset().head();
        assertFalse(deriveIssues.hasErrors(), deriveIssues::toString);
        assertTrue(derived.propagatedTables().containsKey("parcels"));
        assertEquals("Point", ((Geometry) derivedRow.getAs("center")).getGeometryType());
        assertEquals(16d, ((Geometry) derivedRow.getAs("envelope")).getArea(), 0.000001d);

        RecordingIssueSink simplifyIssues = new RecordingIssueSink();
        CanvasNodeOperationResult simplified = new GeometrySimplifyNodeOperator().apply(
                new GeometrySimplifyNodeDefinition(id(), "简化", LAYOUT,
                        new GeometrySimplifyConfiguration(
                                "parcels", "shape", "parcels_simplified", "simple_shape",
                                GeometrySimplifyAlgorithm.TOPOLOGY_PRESERVING, 0.1,
                                SpatialDistanceUnit.SOURCE_CRS_UNIT)),
                Map.of("parcels", parcels), context(simplifyIssues));

        assertFalse(simplifyIssues.hasErrors(), simplifyIssues::toString);
        assertFalse(((Geometry) simplified.propagatedTables().get("parcels_simplified")
                .dataset().head().getAs("simple_shape")).isEmpty());
    }

    @Test
    void findsNearestCandidateWithDeterministicDistanceAndRank() {
        SparkCanvasTable sources = geometryTable(
                "sources_raw", "sources", "shape", GeometryKind.POINT, 3857,
                List.of(longColumn("source_id"), stringColumn("wkt")),
                List.of(RowFactory.create(1L, "POINT (0 0)")));
        SparkCanvasTable candidates = geometryTable(
                "candidates_raw", "candidates", "shape", GeometryKind.POINT, 3857,
                List.of(longColumn("candidate_id"), stringColumn("wkt")),
                List.of(RowFactory.create(10L, "POINT (1 0)"), RowFactory.create(20L, "POINT (5 0)")));
        RecordingIssueSink issues = new RecordingIssueSink();

        CanvasNodeOperationResult result = new SpatialNearestNodeOperator().apply(
                new SpatialNearestNodeDefinition(id(), "最近", LAYOUT,
                        new SpatialNearestConfiguration(
                                "sources", "shape", "candidates", "shape", "candidate_id",
                                SpatialDistanceMethod.PLANAR, 1, null, null, false,
                                "nearest", "distance", SpatialDistanceUnit.METERS, "rank",
                                List.of(
                                        output(JoinOutputColumnSource.LEFT, "source_id", "source_id"),
                                        output(JoinOutputColumnSource.RIGHT, "candidate_id", "candidate_id")))),
                tables(sources, candidates), context(issues));

        Row row = result.propagatedTables().get("nearest").dataset().head();
        assertFalse(issues.hasErrors(), issues::toString);
        assertEquals(10L, row.getLong(row.fieldIndex("candidate_id")));
        assertEquals(1d, ((Number) row.getAs("distance")).doubleValue(), 0.000001d);
        assertEquals(1, row.getInt(row.fieldIndex("rank")));
    }

    @Test
    void summarizesWithinAndOverlaysBoundedLayers() {
        SparkCanvasTable areas = geometryTable(
                "areas_raw", "areas", "shape", GeometryKind.POLYGON, 3857,
                List.of(longColumn("area_id"), stringColumn("wkt")),
                List.of(RowFactory.create(1L, "POLYGON ((0 0, 0 10, 10 10, 10 0, 0 0))")));
        SparkCanvasTable points = geometryTable(
                "points_raw", "points", "shape", GeometryKind.POINT, 3857,
                List.of(longColumn("point_id"), stringColumn("wkt")),
                List.of(
                        RowFactory.create(1L, "POINT (1 1)"),
                        RowFactory.create(2L, "POINT (2 2)"),
                        RowFactory.create(3L, "POINT (20 20)")));
        RecordingIssueSink summarizeIssues = new RecordingIssueSink();
        CanvasNodeOperationResult summarized = new SpatialSummarizeWithinNodeOperator().apply(
                new SpatialSummarizeWithinNodeDefinition(id(), "区域汇总", LAYOUT,
                        new SpatialSummarizeWithinConfiguration(
                                "areas", "shape", "points", "shape", true,
                                SpatialDistanceMethod.PLANAR, SpatialDistanceUnit.METERS,
                                SpatialAreaUnit.SQUARE_METERS,
                                List.of(output(JoinOutputColumnSource.LEFT, "area_id", "area_id")),
                                List.of(new SpatialWithinStatistic(
                                        id(), SpatialWithinStatisticKind.COUNT, null, "point_count")),
                                null, null, "within_summary")),
                tables(areas, points), context(summarizeIssues));

        Row summary = summarized.propagatedTables().get("within_summary").dataset().head();
        assertFalse(summarizeIssues.hasErrors(), summarizeIssues::toString);
        assertEquals(2L, summary.getLong(summary.fieldIndex("point_count")));

        SparkCanvasTable right = geometryTable(
                "right_raw", "right", "shape", GeometryKind.POLYGON, 3857,
                List.of(longColumn("right_id"), stringColumn("wkt")),
                List.of(RowFactory.create(2L, "POLYGON ((5 5, 5 15, 15 15, 15 5, 5 5))")));
        RecordingIssueSink overlayIssues = new RecordingIssueSink();
        CanvasNodeOperationResult overlay = new SpatialOverlayNodeOperator().apply(
                new SpatialOverlayNodeDefinition(id(), "叠加", LAYOUT,
                        new SpatialOverlayConfiguration(
                                "areas", "shape", "right", "shape",
                                SpatialOverlayOperation.INTERSECTION, "overlay", "overlay_shape",
                                List.of(
                                        output(JoinOutputColumnSource.LEFT, "area_id", "area_id"),
                                        output(JoinOutputColumnSource.RIGHT, "right_id", "right_id")))),
                tables(areas, right), context(overlayIssues));

        Geometry intersection = overlay.propagatedTables().get("overlay").dataset().head().getAs("overlay_shape");
        assertFalse(overlayIssues.hasErrors(), overlayIssues::toString);
        assertEquals(25d, intersection.getArea(), 0.000001d);
    }

    @Test
    void reconstructsTracksAndCalculatesMotionMetrics() {
        SparkCanvasTable tracks = geometryTable(
                "tracks_raw", "tracks", "shape", GeometryKind.POINT, 3857,
                List.of(longColumn("track_id"), timestampColumn("event_time"), stringColumn("wkt")),
                List.of(
                        RowFactory.create(1L, time("2026-01-01 00:00:00"), "POINT (0 0)"),
                        RowFactory.create(1L, time("2026-01-01 00:00:10"), "POINT (3 4)")));
        TrackBoundaryConfiguration noBoundaries = new TrackBoundaryConfiguration(null, null, null, null);
        RecordingIssueSink reconstructIssues = new RecordingIssueSink();
        CanvasNodeOperationResult reconstructed = new TrackReconstructNodeOperator().apply(
                new TrackReconstructNodeDefinition(id(), "重建轨迹", LAYOUT,
                        new TrackReconstructConfiguration(
                                "tracks", "shape", List.of("track_id"), "event_time",
                                SpatialDistanceMethod.PLANAR, noBoundaries, List.of(),
                                "track_lines", "track_shape", "start_time", "end_time", "point_count")),
                Map.of("tracks", tracks), context(reconstructIssues));

        Row lineRow = reconstructed.propagatedTables().get("track_lines").dataset().head();
        assertFalse(reconstructIssues.hasErrors(), reconstructIssues::toString);
        assertEquals(2L, lineRow.getLong(lineRow.fieldIndex("point_count")));
        assertEquals(5d, ((Geometry) lineRow.getAs("track_shape")).getLength(), 0.000001d);

        RecordingIssueSink motionIssues = new RecordingIssueSink();
        CanvasNodeOperationResult motion = new TrackMotionStatisticsNodeOperator().apply(
                new TrackMotionStatisticsNodeDefinition(id(), "运动统计", LAYOUT,
                        new TrackMotionStatisticsConfiguration(
                                "tracks", "shape", List.of("track_id"), "event_time",
                                SpatialDistanceMethod.PLANAR, noBoundaries, 1, null, null,
                                List.of(
                                        new TrackMotionMetric.Distance(id(), "distance", SpatialDistanceUnit.METERS),
                                        new TrackMotionMetric.Duration(id(), "duration", SpatialDurationUnit.SECONDS),
                                        new TrackMotionMetric.Speed(id(), "speed", SpatialSpeedUnit.METERS_PER_SECOND)),
                                "track_motion")),
                Map.of("tracks", tracks), context(motionIssues));

        List<Row> motionRows = motion.propagatedTables().get("track_motion").dataset()
                .orderBy("event_time").collectAsList();
        assertFalse(motionIssues.hasErrors(), motionIssues::toString);
        assertNull(motionRows.getFirst().getAs("distance"));
        assertEquals(5d, ((Number) motionRows.getLast().getAs("distance")).doubleValue(), 0.000001d);
        assertEquals(10d, ((Number) motionRows.getLast().getAs("duration")).doubleValue(), 0.000001d);
        assertEquals(0.5d, ((Number) motionRows.getLast().getAs("speed")).doubleValue(), 0.000001d);
    }

    @Test
    void findsDwellsAndDetectsIncidents() {
        SparkCanvasTable dwellPoints = geometryTable(
                "dwells_raw", "dwells", "shape", GeometryKind.POINT, 3857,
                List.of(longColumn("track_id"), timestampColumn("event_time"), stringColumn("wkt")),
                List.of(
                        RowFactory.create(1L, time("2026-01-01 00:00:00"), "POINT (0 0)"),
                        RowFactory.create(1L, time("2026-01-01 00:10:00"), "POINT (1 0)"),
                        RowFactory.create(1L, time("2026-01-01 00:20:00"), "POINT (2 0)")));
        TrackBoundaryConfiguration noBoundaries = new TrackBoundaryConfiguration(null, null, null, null);
        RecordingIssueSink dwellIssues = new RecordingIssueSink();
        CanvasNodeOperationResult dwells = new TrackFindDwellNodeOperator().apply(
                new TrackFindDwellNodeDefinition(id(), "驻留", LAYOUT,
                        new TrackFindDwellConfiguration(
                                "dwells", "shape", List.of("track_id"), "event_time",
                                SpatialDistanceMethod.PLANAR, 2d, SpatialDistanceUnit.METERS,
                                20d, SpatialDurationUnit.MINUTES, noBoundaries, List.of(),
                                DwellGeometryKind.CENTROID, "dwell_result", "dwell_id",
                                "start_time", "end_time", "duration", "point_count", "dwell_shape")),
                Map.of("dwells", dwellPoints), context(dwellIssues));

        Row dwell = dwells.propagatedTables().get("dwell_result").dataset().head();
        assertFalse(dwellIssues.hasErrors(), dwellIssues::toString);
        assertEquals(3L, dwell.getLong(dwell.fieldIndex("point_count")));
        assertEquals(20d, ((Number) dwell.getAs("duration")).doubleValue(), 0.000001d);

        SparkCanvasTable events = table(
                new CanvasTableSchema("events", null, List.of(
                        longColumn("track_id"), timestampColumn("event_time"), doubleColumn("speed")),
                        CanvasDatasetKind.BOUNDED, null, null),
                List.of(
                        RowFactory.create(1L, time("2026-01-01 00:00:00"), 0d),
                        RowFactory.create(1L, time("2026-01-01 00:01:00"), 10d),
                        RowFactory.create(1L, time("2026-01-01 00:02:00"), 20d),
                        RowFactory.create(1L, time("2026-01-01 00:03:00"), 5d)));
        RecordingIssueSink incidentIssues = new RecordingIssueSink();
        CanvasNodeOperationResult incidents = new TrackDetectIncidentsNodeOperator().apply(
                new TrackDetectIncidentsNodeDefinition(id(), "事件", LAYOUT,
                        new TrackDetectIncidentsConfiguration(
                                "events", null, List.of("track_id"), "event_time", null, noBoundaries,
                                predicate("speed", FilterOperator.GREATER_THAN, "9"),
                                predicate("speed", FilterOperator.LESS_THAN, "6"),
                                TrackIncidentResultMode.INCIDENTS_ONLY, "incident_result",
                                "incident_id", "is_incident", "incident_start", "incident_end",
                                "incident_duration", SpatialDurationUnit.MINUTES)),
                Map.of("events", events), context(incidentIssues));

        List<Row> incidentRows = incidents.propagatedTables().get("incident_result")
                .dataset().orderBy("event_time").collectAsList();
        assertFalse(incidentIssues.hasErrors(), incidentIssues::toString);
        assertEquals(3, incidentRows.size());
        assertTrue(incidentRows.stream().allMatch(row -> row.getBoolean(row.fieldIndex("is_incident"))));
        assertEquals(2d, ((Number) incidentRows.getFirst().getAs("incident_duration")).doubleValue(), 0.000001d);
    }

    @Test
    void incidentLifecycleClosesWhenStartBecomesFalseAndReportsElapsedDuration() {
        List<Row> rows = lifecycleRows(null, TrackIncidentResultMode.ALL_EVENTS,
                0d, 10d, 15d, 20d, 40d, 10d, 12d, -2d, -12d);
        assertEquals(List.of(false, false, false, true, true, false, false, false, false),
                rows.stream().map(row -> row.<Boolean>getAs("is_incident")).toList());
        assertEquals(java.util.Arrays.asList(null, null, null, "Started", "OnGoing", "Ended", null, null, null),
                rows.stream().map(row -> row.<String>getAs("status")).toList());
        assertEquals(0d, ((Number) rows.get(3).getAs("duration")).doubleValue());
        assertEquals(1d, ((Number) rows.get(4).getAs("duration")).doubleValue());
        assertEquals(2d, ((Number) rows.get(5).getAs("duration")).doubleValue());
        assertEquals(rows.get(3).<String>getAs("incident_id"), rows.get(5).<String>getAs("incident_id"));
        assertNull(rows.get(6).getAs("incident_id"));
    }

    @Test
    void incidentLifecycleExplicitEndDoesNotRestartOnRepeatedStartOrIncludeEndRow() {
        CanvasFieldPredicate end = predicate("speed", FilterOperator.LESS_THAN, "0");
        List<Row> rows = lifecycleRows(end, TrackIncidentResultMode.ALL_EVENTS,
                0d, 10d, 15d, 20d, 40d, 10d, 12d, -2d, -12d, 30d);
        assertEquals(List.of(false, false, false, true, true, true, true, false, false, true),
                rows.stream().map(row -> row.<Boolean>getAs("is_incident")).toList());
        assertEquals("Ended", rows.get(7).getAs("status"));
        assertEquals("Started", rows.get(9).getAs("status"));
        assertEquals(rows.get(3).<String>getAs("incident_id"), rows.get(6).<String>getAs("incident_id"));
        assertFalse(rows.get(3).<String>getAs("incident_id").equals(rows.get(9).getAs("incident_id")));
        assertNull(rows.get(9).getAs("incident_end"));
        assertEquals(4, lifecycleRows(end, TrackIncidentResultMode.INCIDENTS_ONLY,
                0d, 10d, 15d, 20d, 40d, 10d, 12d, -2d, -12d).size());
    }

    @Test
    void incidentLifecycleEndWinsAndNullConditionsAreNotTrue() {
        List<Row> rows = lifecycleRows(predicate("speed", FilterOperator.GREATER_THAN, "30"),
                TrackIncidentResultMode.ALL_EVENTS, 40d, 20d, 10d, 25d, 40d, 20d);
        assertEquals(java.util.Arrays.asList(null, "Started", "OnGoing", "OnGoing", "Ended", "Started"),
                rows.stream().map(row -> row.<String>getAs("status")).toList());
        List<Row> nullRows = lifecycleRows(null, TrackIncidentResultMode.ALL_EVENTS, 20d, null, 20d);
        assertEquals(List.of("Started", "Ended", "Started"),
                nullRows.stream().map(row -> row.<String>getAs("status")).toList());
    }

    @Test
    void incidentLifecycleUsesTieBreakersAndRejectsAmbiguousObservationsLazily() {
        SparkCanvasTable events = incidentEvents(List.of(
                RowFactory.create(1L, time("2026-01-01 00:00:00"), 20d, 2L),
                RowFactory.create(1L, time("2026-01-01 00:00:00"), 10d, 1L),
                RowFactory.create(1L, null, 40d, 3L)));
        SparkCanvasTable result = incidentResult(events, null, TrackIncidentResultMode.ALL_EVENTS, List.of("event.id"));
        List<Row> rows = result.dataset().orderBy(org.apache.spark.sql.functions.col("`event.id`")).collectAsList();
        assertEquals(2, rows.size());
        assertNull(rows.getFirst().getAs("status"));
        assertEquals("Started", rows.getLast().getAs("status"));
        // A schema-only plan succeeds; only evaluating real duplicate observations raises this error.
        SparkCanvasTable ambiguous = incidentResult(events, null, TrackIncidentResultMode.ALL_EVENTS, List.of());
        Exception failure = assertThrows(Exception.class, () -> ambiguous.dataset().collectAsList());
        assertTrue(failure.toString().contains("TRACK_OBSERVATION_ORDER_NOT_UNIQUE"));
        assertEquals(0, incidentResult(incidentEvents(List.of()), null,
                TrackIncidentResultMode.ALL_EVENTS, List.of()).dataset().collectAsList().size());
    }

    private List<Row> lifecycleRows(CanvasFilterCondition end, TrackIncidentResultMode mode, Double... speeds) {
        List<Row> rows = new ArrayList<>();
        for (int index = 0; index < speeds.length; index++) {
            rows.add(RowFactory.create(1L, Timestamp.valueOf(java.time.LocalDateTime.of(2026, 1, 1, 0, 0)
                    .plusMinutes(index)), speeds[index], (long) index));
        }
        return incidentResult(incidentEvents(rows), end, mode, List.of()).dataset()
                .orderBy("event_time").collectAsList();
    }

    @Test
    void fixedTimeBoundaryResetsIncidentStateEvenWhenAdjacentGapIsSmall() {
        SparkCanvasTable events = incidentEvents(List.of(
                RowFactory.create(1L, time("2026-01-01 23:59:00"), 20d, 1L),
                RowFactory.create(1L, time("2026-01-02 00:00:00"), 30d, 2L)));
        RecordingIssueSink issues = new RecordingIssueSink();
        var configuration = new TrackDetectIncidentsConfiguration("events", null, List.of("track_id"), "event_time",
                null, new TrackBoundaryConfiguration(10d, SpatialDurationUnit.MINUTES, null, null,
                    new TrackFixedTimeBoundary(1, TrackTimeBoundaryUnit.DAYS, "2026-01-01T00:00:00", spark.conf().get("spark.sql.session.timeZone"))),
                predicate("speed", FilterOperator.GREATER_THAN, "15"), null, TrackIncidentResultMode.ALL_EVENTS,
                "incidents", "incident_id", "is_incident", "incident_start", "incident_end", "duration",
                SpatialDurationUnit.MINUTES, TrackIncidentSemantics.CONDITION_LIFECYCLE, "status", List.of());
        CanvasNodeOperationResult result = new TrackDetectIncidentsNodeOperator().apply(
                new TrackDetectIncidentsNodeDefinition(id(), "事件", LAYOUT, configuration), Map.of("events", events), context(issues));
        assertFalse(issues.hasErrors(), issues::toString);
        List<Row> rows = result.propagatedTables().get("incidents").dataset().orderBy("event_time").collectAsList();
        assertEquals(List.of("Started", "Started"), rows.stream().map(row -> row.<String>getAs("status")).toList());
        assertEquals(0d, ((Number) rows.getLast().getAs("duration")).doubleValue());
        assertFalse(rows.getFirst().<String>getAs("incident_id").equals(rows.getLast().getAs("incident_id")));
    }

    private SparkCanvasTable incidentEvents(List<Row> rows) {
        return table(new CanvasTableSchema("events", null, List.of(longColumn("track_id"),
                TrackNodeSupport.timestampColumn("event_time", true), TrackNodeSupport.doubleColumn("speed", true),
                longColumn("event.id")), CanvasDatasetKind.BOUNDED, null, null), rows);
    }

    private SparkCanvasTable incidentResult(SparkCanvasTable events, CanvasFilterCondition end,
                                            TrackIncidentResultMode mode, List<String> order) {
        RecordingIssueSink issues = new RecordingIssueSink();
        CanvasNodeOperationResult result = new TrackDetectIncidentsNodeOperator().apply(
                new TrackDetectIncidentsNodeDefinition(id(), "事件", LAYOUT,
                        new TrackDetectIncidentsConfiguration("events", null, List.of("track_id"), "event_time",
                                null, new TrackBoundaryConfiguration(null, null, null, null),
                                predicate("speed", FilterOperator.GREATER_THAN, "15"), end, mode, "incidents",
                                "incident_id", "is_incident", "incident_start", "incident_end", "duration",
                                SpatialDurationUnit.MINUTES, TrackIncidentSemantics.CONDITION_LIFECYCLE, "status", order)),
                Map.of("events", events), context(issues));
        assertFalse(issues.hasErrors(), issues::toString);
        assertTrue(result.propagatedTables().containsKey("events"));
        return result.propagatedTables().get("incidents");
    }

    @Test
    void aggregatesHexBinsUsingTrueHexCoordinateRounding() {
        SparkCanvasTable points = geometryTable(
                "hex_raw", "hex_points", "shape", GeometryKind.POINT, 3857,
                List.of(longColumn("point_id"), stringColumn("wkt")),
                List.of(RowFactory.create(1L, "POINT (1.4 0.8)")));
        RecordingIssueSink issues = new RecordingIssueSink();
        CanvasNodeOperationResult result = new SpatialBinAggregateNodeOperator().apply(
                new SpatialBinAggregateNodeDefinition(id(), "六边形聚合", LAYOUT,
                        new SpatialBinAggregateConfiguration(
                                "hex_points", "shape", SpatialBinShape.HEXAGON, 1d,
                                SpatialDistanceUnit.SOURCE_CRS_UNIT, false,
                                List.of(new SpatialBinStatistic(
                                        id(), SpatialBinStatisticKind.COUNT, null, "point_count")),
                                null, null, "hex_bins", "bin_id", "bin_shape")),
                Map.of("hex_points", points), context(issues));

        Row row = result.propagatedTables().get("hex_bins").dataset().head();
        Geometry hexagon = row.getAs("bin_shape");
        assertFalse(issues.hasErrors(), issues::toString);
        assertEquals("HEXAGON:1:0", row.getString(row.fieldIndex("bin_id")));
        assertEquals(1.5d, hexagon.getCentroid().getX(), 0.000001d);
        assertEquals(Math.sqrt(3d) / 2d, hexagon.getCentroid().getY(), 0.000001d);
    }

    @Test
    void clustersPointsAndPreservesNoiseRows() {
        SparkCanvasTable points = geometryTable(
                "cluster_raw", "cluster_points", "shape", GeometryKind.POINT, 3857,
                List.of(longColumn("point_id"), stringColumn("wkt")),
                List.of(
                        RowFactory.create(1L, "POINT (0 0)"),
                        RowFactory.create(2L, "POINT (1 0)"),
                        RowFactory.create(3L, "POINT (10 0)")));
        RecordingIssueSink issues = new RecordingIssueSink();
        CanvasNodeOperationResult result = new SpatialPointClusterNodeOperator().apply(
                new SpatialPointClusterNodeDefinition(id(), "点聚类", LAYOUT,
                        new SpatialPointClusterConfiguration(
                                "cluster_points", "shape", "point_id", SpatialDistanceMethod.PLANAR,
                                new SpatialPointClusterParameters.Dbscan(
                                        1.5d, SpatialDistanceUnit.SOURCE_CRS_UNIT, 2),
                                "clusters", "cluster_id", "is_noise")),
                Map.of("cluster_points", points), new CanvasNodeOperationContext(spark, context(issues).metadataIndex(), issues,
                        context(issues).dataAccess(), CanvasExecutionMode.BATCH, CanvasRuntimeValues.execution(UUID.randomUUID(), java.time.Instant.now())));

        List<Row> rows = result.propagatedTables().get("clusters").dataset()
                .orderBy("point_id").collectAsList();
        assertFalse(issues.hasErrors(), issues::toString);
        assertEquals(3, rows.size());
        assertFalse(rows.get(0).isNullAt(rows.get(0).fieldIndex("cluster_id")));
        assertFalse(rows.get(1).isNullAt(rows.get(1).fieldIndex("cluster_id")));
        assertTrue(rows.get(2).isNullAt(rows.get(2).fieldIndex("cluster_id")));
        assertTrue(rows.get(2).getBoolean(rows.get(2).fieldIndex("is_noise")));
    }

    @Test
    void h3AggregatesOccupiedCellsAtExplicitAndApproximateResolutions() {
        var points = geometryTable("h3_raw", "points", "shape", GeometryKind.POINT, 4326,
                List.of(longColumn("id"), stringColumn("wkt")), List.of(
                RowFactory.create(1L, "POINT (0 0)"), RowFactory.create(2L, "POINT (0 0)"),
                RowFactory.create(3L, "POINT (120 30)"), RowFactory.create(4L, "POINT EMPTY")));
        for (var option : List.of(new SpatialH3Options(SpatialH3Options.Mode.RESOLUTION, 0),
                new SpatialH3Options(SpatialH3Options.Mode.RESOLUTION, 15),
                new SpatialH3Options(SpatialH3Options.Mode.APPROXIMATE_SIZE, 4))) {
            var c = h3Config(option, false, null, null, List.of(new SpatialBinStatistic(id(), SpatialBinStatisticKind.COUNT, null, "count")));
            var issues = new RecordingIssueSink();
            var result = new SpatialBinAggregateNodeOperator().apply(new SpatialBinAggregateNodeDefinition(id(), "H3", LAYOUT, c), Map.of("points", points), context(issues));
            assertFalse(issues.hasErrors(), issues::toString);
            assertEquals(points, result.propagatedTables().get("points"));
            var bins = result.propagatedTables().get("bins");
            assertEquals(GeometryKind.MULTIPOLYGON, bins.schema().columns().get(1).geometry().kind());
            int resolution = option.mode() == SpatialH3Options.Mode.RESOLUTION ? option.resolution() : 8;
            assertTrue(bins.schema().columns().getFirst().comment().contains("H3 分辨率 " + resolution));
            var rows = bins.dataset().orderBy("count").collectAsList();
            assertEquals(2, rows.size()); assertEquals(1L, rows.getFirst().<Long>getAs("count")); assertEquals(2L, rows.getLast().<Long>getAs("count"));
            for (var row : rows) {
                assertEquals(resolution, H3GridSupport.core().getResolution(row.<String>getAs("bin_id")));
                Geometry geometry = row.getAs("bin_shape"); assertTrue(geometry.isValid()); assertEquals(4326, geometry.getSRID());
            }
        }
    }

    @Test
    void h3PreservesCollidingSourceNamesAndPartitionsGroupPercentagesByTime() {
        var points = geometryTable("h3_groups_raw", "points", "shape", GeometryKind.POINT, 4326,
                List.of(stringColumn("__datascalpel_bin_id"), longColumn("__datascalpel_bin_matched"), timestampColumn("event_time"), stringColumn("wkt")), List.of(
                RowFactory.create("A", 2L, Timestamp.valueOf("2025-01-01 00:00:01"), "POINT (0 0)"),
                RowFactory.create("B", 4L, Timestamp.valueOf("2025-01-01 00:00:02"), "POINT (0 0)"),
                RowFactory.create("A", 8L, Timestamp.valueOf("2025-01-01 00:00:12"), "POINT (0 0)")));
        var time = new SpatialTemporalSlicing("event_time", 10, SpatialDurationUnit.SECONDS, null, null, null, "UTC", "start", "end");
        var group = new SpatialGroupSummary("__datascalpel_bin_id", false, true, null, null, "pct");
        var c = h3Config(new SpatialH3Options(SpatialH3Options.Mode.RESOLUTION, 6), false, group, time,
                List.of(new SpatialBinStatistic(id(), SpatialBinStatisticKind.COUNT, null, "count"),
                        new SpatialBinStatistic(id(), SpatialBinStatisticKind.SUM, "__datascalpel_bin_matched", "total")));
        var issues = new RecordingIssueSink();
        var result = new SpatialBinAggregateNodeOperator().apply(new SpatialBinAggregateNodeDefinition(id(), "H3", LAYOUT, c), Map.of("points", points), context(issues));
        assertFalse(issues.hasErrors(), issues::toString);
        var rows = result.propagatedTables().get("bins").dataset().orderBy("start", "__datascalpel_bin_id").collectAsList();
        assertEquals(3, rows.size());
        assertEquals(List.of(2d, 4d, 8d), rows.stream().map(r -> ((Number) r.getAs("total")).doubleValue()).toList());
        assertEquals(List.of(50d, 50d, 100d), rows.stream().map(r -> r.<Double>getAs("pct")).toList());
        assertEquals(10_000L, rows.getFirst().<Timestamp>getAs("end").getTime() - rows.getFirst().<Timestamp>getAs("start").getTime());
    }

    @Test
    void h3RejectsUnsupportedModesWithoutExecutingAndHandlesEmptyInput() {
        var options = new SpatialH3Options(SpatialH3Options.Mode.RESOLUTION, 6);
        for (int epsg : List.of(4326, 3857)) {
            var points = geometryTable("h3_empty_raw", "points", "shape", GeometryKind.POINT, epsg,
                    List.of(stringColumn("wkt")), List.of());
            for (boolean empty : List.of(false, true)) {
                var issues = new RecordingIssueSink();
                var result = new SpatialBinAggregateNodeOperator().apply(new SpatialBinAggregateNodeDefinition(id(), "H3", LAYOUT,
                        h3Config(options, empty, null, null, List.of(new SpatialBinStatistic(id(), SpatialBinStatisticKind.COUNT, null, "count")))),
                        Map.of("points", points), context(issues));
                if (epsg == 4326 && !empty) {
                    assertFalse(issues.hasErrors(), issues::toString); assertEquals(0, result.propagatedTables().get("bins").dataset().count());
                } else {
                    assertTrue(issues.hasErrors()); assertTrue(result.propagatedTables().isEmpty());
                    assertTrue(issues.codes.contains(epsg == 3857 ? "SPATIAL_H3_WGS84_REQUIRED" : "SPATIAL_H3_EMPTY_BINS_UNSUPPORTED"));
                }
            }
        }
    }

    private SpatialBinAggregateConfiguration h3Config(SpatialH3Options h3, boolean empty, SpatialGroupSummary group,
            SpatialTemporalSlicing time, List<SpatialBinStatistic> stats) {
        return new SpatialBinAggregateConfiguration("points", "shape", SpatialBinShape.H3, 1000,
                SpatialDistanceUnit.METERS, empty, stats, group, time, "bins", "bin_id", "bin_shape", null, h3);
    }

    @Test void h3OmitsNullGeometryAndFailsLazilyOnInvalidCoordinates() {
        var points = geometryTable("h3_nullable_raw", "points", "shape", GeometryKind.POINT, 4326,
                List.of(longColumn("id"), stringColumn("wkt")), List.of(RowFactory.create(1L, "POINT (0 0)"), RowFactory.create(2L, "POINT (181 0)")));
        var c = h3Config(new SpatialH3Options(SpatialH3Options.Mode.RESOLUTION, 6), false, null, null,
                List.of(new SpatialBinStatistic(id(), SpatialBinStatisticKind.COUNT, null, "count")));
        var issues = new RecordingIssueSink();
        var invalid = new SpatialBinAggregateNodeOperator().apply(new SpatialBinAggregateNodeDefinition(id(), "H3", LAYOUT, c), Map.of("points", points), context(issues));
        assertFalse(issues.hasErrors(), issues::toString); // No action or coordinate scan during planning.
        var failure = assertThrows(Exception.class, () -> invalid.propagatedTables().get("bins").dataset().collectAsList());
        boolean safeCode = false;
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if ("SPATIAL_H3_POINT_INVALID".equals(cause.getMessage())) safeCode = true;
        }
        assertTrue(safeCode);
        var nullableData = points.dataset().withColumn("shape", org.apache.spark.sql.functions.when(
                points.dataset().col("id").equalTo(1), points.dataset().col("shape")));
        var nullable = new SparkCanvasTable(points.schema(), nullableData);
        var nullIssues = new RecordingIssueSink();
        var result = new SpatialBinAggregateNodeOperator().apply(new SpatialBinAggregateNodeDefinition(id(), "H3", LAYOUT, c), Map.of("points", nullable), context(nullIssues));
        assertFalse(nullIssues.hasErrors(), nullIssues::toString);
        assertEquals(1L, result.propagatedTables().get("bins").dataset().head().<Long>getAs("count"));
    }

    @Test void h3LineageResolvesCellGeometryAndStatisticsWithoutReadingRows() {
        var raw = geometryTable("h3_lineage_raw", "points", "shape", GeometryKind.POINT, 4326,
                List.of(longColumn("id"), stringColumn("wkt")), List.of());
        var inputAsset = new TaskLineageEvidence.Asset("input", TaskLineageEvidence.AssetRole.INPUT, TaskLineageEvidence.AssetKind.JDBC_TABLE,
                null, null, null, null, UUID.randomUUID(), null, null, "points", null, null, "points");
        var fields = new LinkedHashMap<String, CatalystLineageMetadata.InputField>();
        for (var column : raw.schema().columns()) fields.put(column.name(), new CatalystLineageMetadata.InputField("input:" + column.name(), null));
        var source = new SparkCanvasTable(raw.schema(), CatalystLineageMetadata.markInput(raw.dataset(), "input-node", inputAsset, fields));
        var c = h3Config(new SpatialH3Options(SpatialH3Options.Mode.RESOLUTION, 6), false, null, null,
                List.of(new SpatialBinStatistic(id(), SpatialBinStatisticKind.COUNT, null, "count"), new SpatialBinStatistic(id(), SpatialBinStatisticKind.SUM, "id", "total")));
        var issues = new RecordingIssueSink();
        var result = new SpatialBinAggregateNodeOperator().apply(new SpatialBinAggregateNodeDefinition(id(), "H3", LAYOUT, c), Map.of("points", source), context(issues));
        assertFalse(issues.hasErrors(), issues::toString);
        var table = result.propagatedTables().get("bins");
        var output = new TaskLineageEvidence.Asset("out", TaskLineageEvidence.AssetRole.OUTPUT, TaskLineageEvidence.AssetKind.JDBC_TABLE,
                null, TaskLineageEvidence.WriteMode.APPEND, null, null, UUID.randomUUID(), null, null, "bins", null, null, "bins");
        var candidate = new CatalystLineageOutputCandidate("flow", "output-node", "JDBC_OUTPUT", "write", table.dataset(), output,
                table.schema().columns().stream().map(column -> new CatalystLineageOutputCandidate.TargetField("out:" + column.name(), null,
                        column.name(), column.name(), TaskLineageEvidence.OutputEffect.WRITTEN_UNKNOWN_SOURCE)).toList());
        var flow = new CatalystLineageAnalyzer().analyze(List.of(candidate)).flows().getFirst();
        assertEquals(TaskLineageEvidence.Coverage.FIELD_COMPLETE, flow.coverage(), () -> flow.warnings().toString());
        for (var column : List.of("bin_id", "bin_shape", "count")) {
            assertTrue(flow.fieldEdges().stream().anyMatch(e -> e.target().localFieldKey().equals("out:" + column) && e.source().localFieldKey().equals("input:shape")), column);
        }
        assertTrue(flow.fieldEdges().stream().anyMatch(e -> e.target().localFieldKey().equals("out:total") && e.source().localFieldKey().equals("input:id")));
    }

    @Test
    void hexFlatToFlatSizeMatchesOfficialAreaAndEquivalentLegacyGeometry() {
        SparkCanvasTable points = geometryTable("hex_size_raw", "hex_size_points", "shape", GeometryKind.POINT, 3857,
                List.of(longColumn("point_id"), stringColumn("wkt")),
                List.of(RowFactory.create(1L, "POINT (0 0)"), RowFactory.create(2L, "POINT (-1600 0)")));
        List<SpatialBinStatistic> statistics = List.of(new SpatialBinStatistic(id(), SpatialBinStatisticKind.COUNT, null, "count"));
        var legacy = new SpatialBinAggregateConfiguration("hex_size_points", "shape", SpatialBinShape.HEXAGON,
                1000 / Math.sqrt(3d), SpatialDistanceUnit.METERS, true, statistics, null, null, "bins", "id", "shape");
        var modern = new SpatialBinAggregateConfiguration("hex_size_points", "shape", SpatialBinShape.HEXAGON,
                1000, SpatialDistanceUnit.METERS, true, statistics, null, null, "bins", "id", "shape",
                SpatialBinSizeSemantics.HEXAGON_FLAT_TO_FLAT);
        List<List<Row>> results = new ArrayList<>();
        for (var configuration : List.of(legacy, modern)) {
            RecordingIssueSink issues = new RecordingIssueSink();
            var result = new SpatialBinAggregateNodeOperator().apply(new SpatialBinAggregateNodeDefinition(id(), "格网", LAYOUT, configuration),
                    Map.of("hex_size_points", points), context(issues));
            assertFalse(issues.hasErrors(), issues::toString);
            results.add(result.propagatedTables().get("bins").dataset().orderBy("id").collectAsList());
        }
        assertEquals(results.getFirst().size(), results.getLast().size());
        assertTrue(results.getFirst().size() > 2); // Includes unoccupied bins, which must use the same converted size.
        for (int index = 0; index < results.getFirst().size(); index++) {
            Row before = results.getFirst().get(index);
            Row after = results.getLast().get(index);
            Geometry geometry = after.getAs("shape");
            assertEquals(before.<String>getAs("id"), after.<String>getAs("id"));
            assertEquals(before.<Long>getAs("count"), after.<Long>getAs("count"));
            assertTrue(geometry.equalsExact(before.getAs("shape"), 1e-8));
            assertEquals(1000d, geometry.getEnvelopeInternal().getHeight(), 1e-8);
            assertEquals(Math.sqrt(3d) / 2 * 1_000_000d, geometry.getArea(), 1e-6);
        }
    }

    @Test
    void calculatesCenterAndKeepsEmptyGlobalCentralFeatureRow() {
        List<CanvasColumnSchema> rawColumns = List.of(
                longColumn("point_id"), stringColumn("wkt"));
        SparkCanvasTable points = geometryTable(
                "center_raw", "center_points", "shape", GeometryKind.POINT, 3857,
                rawColumns,
                List.of(RowFactory.create(1L, "POINT (0 0)"), RowFactory.create(2L, "POINT (2 0)")));
        List<SpatialCenterDispersionAnalysis> analyses = List.of(
                new SpatialCenterDispersionAnalysis(id(), SpatialCenterDispersionKind.MEAN_CENTER,
                        "mean_center", null),
                new SpatialCenterDispersionAnalysis(id(), SpatialCenterDispersionKind.CENTRAL_FEATURE,
                        "central_feature", null),
                new SpatialCenterDispersionAnalysis(id(), SpatialCenterDispersionKind.STANDARD_DISTANCE,
                        "standard_distance", 1),
                new SpatialCenterDispersionAnalysis(id(), SpatialCenterDispersionKind.DIRECTIONAL_ELLIPSE,
                        "ellipse", 1));
        RecordingIssueSink issues = new RecordingIssueSink();
        CanvasNodeOperationResult result = new SpatialCenterDispersionNodeOperator().apply(
                new SpatialCenterDispersionNodeDefinition(id(), "中心与离散", LAYOUT,
                        new SpatialCenterDispersionConfiguration(
                                "center_points", "shape", "point_id", List.of(), null,
                                analyses, "center_result")),
                Map.of("center_points", points), context(issues));

        Row row = result.propagatedTables().get("center_result").dataset().head();
        Geometry mean = row.getAs("mean_center");
        assertFalse(issues.hasErrors(), issues::toString);
        assertEquals(1d, mean.getCoordinate().x, 0.000001d);
        assertEquals("Point", ((Geometry) row.getAs("central_feature")).getGeometryType());
        assertEquals("Polygon", ((Geometry) row.getAs("standard_distance")).getGeometryType());
        assertEquals("Polygon", ((Geometry) row.getAs("ellipse")).getGeometryType());

        SparkCanvasTable empty = geometryTable(
                "empty_center_raw", "empty_center_points", "shape", GeometryKind.POINT, 3857,
                rawColumns, List.of());
        RecordingIssueSink emptyIssues = new RecordingIssueSink();
        CanvasNodeOperationResult emptyResult = new SpatialCenterDispersionNodeOperator().apply(
                new SpatialCenterDispersionNodeDefinition(id(), "空中心", LAYOUT,
                        new SpatialCenterDispersionConfiguration(
                                "empty_center_points", "shape", "point_id", List.of(), null,
                                List.of(new SpatialCenterDispersionAnalysis(
                                        id(), SpatialCenterDispersionKind.CENTRAL_FEATURE,
                                        "central_feature", null)), "empty_center_result")),
                Map.of("empty_center_points", empty), context(emptyIssues));

        List<Row> emptyRows = emptyResult.propagatedTables().get("empty_center_result")
                .dataset().collectAsList();
        assertFalse(emptyIssues.hasErrors(), emptyIssues::toString);
        assertEquals(1, emptyRows.size());
        assertNull(emptyRows.getFirst().getAs("central_feature"));
    }

    @Test
    void invalidSummarizeDraftReturnsIssuesInsteadOfThrowing() {
        SparkCanvasTable areas = geometryTable(
                "draft_area_raw", "draft_areas", "shape", GeometryKind.POLYGON, 3857,
                List.of(longColumn("area_id"), stringColumn("wkt")),
                List.of(RowFactory.create(1L, "POLYGON ((0 0, 0 1, 1 1, 1 0, 0 0))")));
        RecordingIssueSink issues = new RecordingIssueSink();

        CanvasNodeOperationResult result = new SpatialSummarizeWithinNodeOperator().apply(
                new SpatialSummarizeWithinNodeDefinition(id(), "未完成汇总", LAYOUT,
                        new SpatialSummarizeWithinConfiguration(
                                "draft_areas", "shape", "missing", "shape", false,
                                SpatialDistanceMethod.PLANAR, SpatialDistanceUnit.METERS,
                                SpatialAreaUnit.SQUARE_METERS, List.of(
                                output(JoinOutputColumnSource.LEFT, "area_id", "area_id")),
                                null, null, null, "draft_result")),
                Map.of("draft_areas", areas), context(issues));

        assertTrue(issues.hasErrors());
        assertTrue(result.propagatedTables().isEmpty());
    }

    @Test
    void referenceCenterDwellProjectsAllFourModesAndKeepsUnselectedTables() {
        SparkCanvasTable points = geometryTable("dwell_range_raw", "range_points", "shape", GeometryKind.POINT, 3857,
                List.of(longColumn("track_id"), timestampColumn("event_time"), stringColumn("wkt")),
                List.of(RowFactory.create(1L, time("2026-01-01 00:30:00"), "POINT (100 0)"),
                        RowFactory.create(1L, time("2026-01-01 00:20:00"), "POINT (2 0)"),
                        RowFactory.create(1L, time("2026-01-01 00:00:00"), "POINT (0 0)"),
                        RowFactory.create(1L, time("2026-01-01 00:10:00"), "POINT (1 0)")));
        String referenceId = null;
        for (TrackDwellResultMode mode : TrackDwellResultMode.values()) {
            RecordingIssueSink issues = new RecordingIssueSink();
            var config = new TrackFindDwellConfiguration("range_points", "shape", List.of("track_id"), "event_time",
                    SpatialDistanceMethod.PLANAR, 2, SpatialDistanceUnit.METERS, 20, SpatialDurationUnit.MINUTES,
                    new TrackBoundaryConfiguration(null, null, null, null), List.of(), null, "result", "dwell_id",
                    "__datascalpel_track_segment", "end_time", "duration", "point_count", "dwell_shape",
                    TrackDwellSemantics.REFERENCE_CENTER, new TrackDwellRangeOptions(mode, List.of(), SpatialDurationUnit.SECONDS,
                    "__datascalpel_dwell_collected", SpatialDistanceUnit.METERS, "is_dwell"));
            var result = new TrackFindDwellNodeOperator().apply(new TrackFindDwellNodeDefinition(id(), "驻留", LAYOUT, config),
                    Map.of("range_points", points), context(issues));
            assertFalse(issues.hasErrors(), issues::toString);
            assertTrue(points == result.propagatedTables().get("range_points"));
            var output = result.propagatedTables().get("result");
            var rows = output.dataset().collectAsList();
            boolean features = mode == TrackDwellResultMode.ALL_FEATURES || mode == TrackDwellResultMode.DWELL_FEATURES;
            assertEquals(features ? (mode == TrackDwellResultMode.ALL_FEATURES ? 4 : 3) : 1, rows.size());
            if (features) {
                assertEquals(3, rows.stream().filter(r -> (Boolean) r.getAs("is_dwell")).count());
                for (Row row : rows) if (!(Boolean) row.getAs("is_dwell")) assertNull(row.getAs("dwell_id"));
            } else {
                Row row = rows.getFirst();
                assertEquals(1200d, ((Number) row.getAs("duration")).doubleValue(), 1e-9);
                assertEquals(1d, ((Number) row.getAs("__datascalpel_dwell_collected")).doubleValue(), 1e-9);
                assertEquals(3L, (Long) row.getAs("point_count"));
                org.locationtech.jts.geom.Geometry shape = row.getAs("dwell_shape");
                assertEquals(mode == TrackDwellResultMode.MEAN_CENTERS ? "Point" : "LineString", shape.getGeometryType());
                assertEquals(3857, shape.getSRID());
            }
            String currentId = rows.stream().filter(r -> r.getAs("dwell_id") != null).findFirst().orElseThrow().getAs("dwell_id");
            if (referenceId == null) referenceId = currentId;
            else assertEquals(referenceId, currentId);
            assertNull(output.schema().watermarkDelay());
        }
    }

    @Test
    void referenceDwellEmptyInputIsTypedAndInvalidOrderDoesNotPropagate() {
        var empty = geometryTable("empty_dwell_raw", "empty_dwell", "shape", GeometryKind.POINT, 3857,
                List.of(longColumn("track_id"), timestampColumn("event_time"), stringColumn("wkt")), List.of());
        for (var mode : TrackDwellResultMode.values()) {
            var config = new TrackFindDwellConfiguration("empty_dwell", "shape", List.of("track_id"), "event_time",
                    SpatialDistanceMethod.PLANAR, 2, SpatialDistanceUnit.METERS, 20, SpatialDurationUnit.MINUTES,
                    new TrackBoundaryConfiguration(null, null, null, null), List.of(), null, "result", "dwell_id",
                    "start_time", "end_time", "duration", "point_count", "dwell_shape",
                    TrackDwellSemantics.REFERENCE_CENTER, new TrackDwellRangeOptions(mode, List.of(), SpatialDurationUnit.MILLISECONDS,
                    "mean_distance", SpatialDistanceUnit.METERS, "is_dwell"));
            var issues = new RecordingIssueSink();
            var result = new TrackFindDwellNodeOperator().apply(new TrackFindDwellNodeDefinition(id(), "驻留", LAYOUT, config),
                    Map.of("empty_dwell", empty), context(issues));
            assertFalse(issues.hasErrors(), issues::toString);
            assertEquals(0, result.propagatedTables().get("result").dataset().collectAsList().size());
        }
        var invalid = new TrackFindDwellConfiguration("empty_dwell", "shape", List.of("track_id"), "event_time",
                SpatialDistanceMethod.PLANAR, 2, SpatialDistanceUnit.METERS, 20, SpatialDurationUnit.MINUTES,
                new TrackBoundaryConfiguration(null, null, null, null), List.of(), null, "result", "dwell_id",
                "", "", "", "", "", TrackDwellSemantics.REFERENCE_CENTER,
                new TrackDwellRangeOptions(TrackDwellResultMode.ALL_FEATURES, List.of("missing"), null, "", null, "is_dwell"));
        var errors = new RecordingIssueSink();
        var result = new TrackFindDwellNodeOperator().apply(new TrackFindDwellNodeDefinition(id(), "驻留", LAYOUT, invalid),
                Map.of("empty_dwell", empty), context(errors));
        assertTrue(errors.hasErrors());
        assertTrue(result.propagatedTables().isEmpty());
    }

    @Test
    void observationWindowCalculatesAllEightGroupsAndUsesIndependentElevationUnits() {
        var points = motionPoints(List.of(
                RowFactory.create(1L, time("2026-01-01 00:00:07"), 4L, 20d, "POINT (3 4)"),
                RowFactory.create(1L, time("2026-01-01 00:00:00"), 1L, 0d, "POINT (0 0)"),
                RowFactory.create(1L, time("2026-01-01 00:00:01"), 2L, 10d, "POINT (3 0)"),
                RowFactory.create(1L, time("2026-01-01 00:00:03"), 3L, 20d, "POINT (3 4)")));
        var issues = new RecordingIssueSink();
        var result = new TrackMotionStatisticsNodeOperator().apply(
                new TrackMotionStatisticsNodeDefinition(id(), "运动", LAYOUT, motionConfig(3)),
                Map.of("motion_points", points), context(issues));
        assertFalse(issues.hasErrors(), issues::toString);
        var output = result.propagatedTables().get("motion_result");
        var rows = output.dataset().orderBy("sequence").collectAsList();
        assertEquals(points.schema().columns().size() + 31, output.schema().columns().size());
        Row first = rows.getFirst(), third = rows.get(2), fourth = rows.get(3);
        assertNull(first.getAs("speed")); assertNull(first.getAs("tot_distance")); assertNull(first.getAs("idling"));
        assertEquals(4d, (Double) third.getAs("distance"), 1e-9);
        assertEquals(7d, (Double) third.getAs("tot_distance"), 1e-9);
        assertEquals(7d / 3, (Double) third.getAs("avg_speed"), 1e-9);
        assertEquals(2d, (Double) third.getAs("min_speed"), 1e-9);
        assertEquals(3d, (Double) third.getAs("max_speed"), 1e-9);
        assertEquals(-0.5, (Double) third.getAs("acceleration"), 1e-9);
        assertEquals(6.096, (Double) third.getAs("elevation"), 1e-9);
        assertEquals(3.048, (Double) third.getAs("avg_elevation"), 1e-9);
        assertEquals(0.762, (Double) third.getAs("slope"), 1e-9);
        assertEquals(0d, (Double) third.getAs("bearing"), 1e-9);
        assertEquals(true, fourth.getAs("idling"));
        assertEquals(4d, (Double) fourth.getAs("tot_idle_time"), 1e-9);
        assertEquals(100d * 4 / 6, (Double) fourth.getAs("pct_idle_time"), 1e-9);
        assertEquals(4d, (Double) fourth.getAs("tot_distance"), 1e-9); // Oldest incoming segment is outside the window.
        assertNull(fourth.getAs("slope")); assertNull(fourth.getAs("bearing"));
        assertNull(output.schema().watermarkDelay());
    }

    @Test
    void windowOneKeepsInstantaneousMetricsButNoSegmentSummariesAndZeroTimeDoesNotDivide() {
        var points = motionPoints(List.of(
                RowFactory.create(1L, time("2026-01-01 00:00:00"), 1L, 0d, "POINT (0 0)"),
                RowFactory.create(1L, time("2026-01-01 00:00:01"), 2L, 0d, "POINT (3 0)"),
                RowFactory.create(1L, time("2026-01-01 00:00:01"), 3L, 0d, "POINT (3 4)")));
        var issues = new RecordingIssueSink();
        var result = new TrackMotionStatisticsNodeOperator().apply(
                new TrackMotionStatisticsNodeDefinition(id(), "运动", LAYOUT, motionConfig(1)),
                Map.of("motion_points", points), context(issues));
        assertFalse(issues.hasErrors(), issues::toString);
        var rows = result.propagatedTables().get("motion_result").dataset().orderBy("sequence").collectAsList();
        assertEquals(3d, (Double) rows.get(1).getAs("speed"), 1e-9);
        assertNull(rows.get(1).getAs("tot_distance"));
        assertEquals(4d, (Double) rows.get(2).getAs("distance"), 1e-9);
        assertEquals(0d, (Double) rows.get(2).getAs("duration"), 1e-9);
        assertNull(rows.get(2).getAs("speed")); assertNull(rows.get(2).getAs("acceleration"));
        assertNull(rows.get(2).getAs("idling"));
    }

    @Test
    void motionWindowAcceptsTypedEmptyInput() {
        var points = motionPoints(List.of());
        var issues = new RecordingIssueSink();
        var result = new TrackMotionStatisticsNodeOperator().apply(
                new TrackMotionStatisticsNodeDefinition(id(), "运动", LAYOUT, motionConfig(3)),
                Map.of("motion_points", points), context(issues));
        assertFalse(issues.hasErrors(), issues::toString);
        assertTrue(result.propagatedTables().get("motion_result").dataset().collectAsList().isEmpty());
    }

    @Test
    void extendedNearestUnitsControlThresholdAndOutputIndependently() {
        var sources = geometryTable("unit_sources", "sources", "shape", GeometryKind.POINT, 3857,
                List.of(longColumn("source_id"), stringColumn("wkt")), List.of(RowFactory.create(1L, "POINT (0 0)")));
        var candidates = geometryTable("unit_candidates", "candidates", "shape", GeometryKind.POINT, 3857,
                List.of(longColumn("candidate_id"), stringColumn("wkt")), List.of(RowFactory.create(2L, "POINT (0.3048003 0)")));
        for (var unit : List.of(SpatialDistanceUnit.FEET, SpatialDistanceUnit.FEET_US)) {
            var issues = new RecordingIssueSink();
            var c = new SpatialNearestConfiguration("sources", "shape", "candidates", "shape", "candidate_id",
                    SpatialDistanceMethod.PLANAR, 1, 1d, unit, false, "nearest", "distance", SpatialDistanceUnit.YARDS_US, "rank",
                    List.of(output(JoinOutputColumnSource.LEFT, "source_id", "source_id")),
                    new SpatialNearestMatching(SpatialNearestMatchSemantics.EXACT_DISTANCE, "source_id", null));
            var result = new SpatialNearestNodeOperator().apply(new SpatialNearestNodeDefinition(id(), "单位", LAYOUT, c), tables(sources, candidates), context(issues));
            assertFalse(issues.hasErrors(), issues::toString);
            var rows = result.propagatedTables().get("nearest").dataset().collectAsList();
            assertEquals(unit == SpatialDistanceUnit.FEET_US ? 1 : 0, rows.size());
            if (!rows.isEmpty()) assertEquals(0.3048003 / (3600d / 3937), ((Number) rows.getFirst().getAs("distance")).doubleValue(), 1e-9);
        }
    }

    @Test
    void withinConvertsClippedAreasAndLengthsUsingSelectedUnits() {
        var areas = geometryTable("unit_areas", "areas", "shape", GeometryKind.POLYGON, 3857,
                List.of(longColumn("area_id"), stringColumn("wkt")),
                List.of(RowFactory.create(1L, "POLYGON ((0 0,0 10,10 10,10 0,0 0))")));
        for (boolean polygon : List.of(false, true)) {
            var summary = geometryTable("unit_summary", "summary", "shape", polygon ? GeometryKind.POLYGON : GeometryKind.LINESTRING, 3857,
                    List.of(longColumn("id"), stringColumn("wkt")), List.of(RowFactory.create(2L,
                            polygon ? "POLYGON ((0 0,0 20,10 20,10 0,0 0))" : "LINESTRING (0 5,20 5)")));
            var issues = new RecordingIssueSink();
            var c = new SpatialSummarizeWithinConfiguration("areas", "shape", "summary", "shape", true,
                    SpatialDistanceMethod.PLANAR, SpatialDistanceUnit.YARDS_US, SpatialAreaUnit.ACRES_US,
                    List.of(output(JoinOutputColumnSource.LEFT, "area_id", "area_id")),
                    List.of(new SpatialWithinStatistic(id(), polygon ? SpatialWithinStatisticKind.AREA_WITHIN : SpatialWithinStatisticKind.LENGTH_WITHIN, null, "measure")),
                    null, null, "within_units");
            var result = new SpatialSummarizeWithinNodeOperator().apply(new SpatialSummarizeWithinNodeDefinition(id(), "单位", LAYOUT, c), tables(areas, summary), context(issues));
            assertFalse(issues.hasErrors(), issues::toString);
            double expected = polygon ? 100d / 4046.872609874252 : 10d / (3600d / 3937);
            assertEquals(expected, (Double) result.propagatedTables().get("within_units").dataset().head().getAs("measure"), 1e-9);
        }
    }

    @Test
    void motionConvertsSurveyElevationAndDistanceWithoutChangingSpeedUnits() {
        var points = motionPoints(List.of(
                RowFactory.create(1L, time("2026-01-01 00:00:00"), 1L, 0d, "POINT (0 0)"),
                RowFactory.create(1L, time("2026-01-01 00:00:01"), 2L, 3d, "POINT (3 0)")));
        var c = motionConfig(3); var w = c.windowOptions();
        var window = new TrackMotionWindowOptions(w.observationCount(), w.orderByColumns(), w.statistics(),
                SpatialDistanceUnit.YARDS, w.durationUnit(), w.speedUnit(), w.accelerationUnit(), w.elevationColumnName(),
                SpatialDistanceUnit.FEET_US, SpatialDistanceUnit.YARDS_US, w.idleTimeThreshold(), w.idleTimeThresholdUnit());
        var configuration = new TrackMotionStatisticsConfiguration(c.sourceTableName(), c.pointGeometryColumnName(), c.trackIdColumns(),
                c.timeColumnName(), c.distanceMethod(), c.boundaries(), c.historyPoints(), c.idleDistanceThreshold(),
                SpatialDistanceUnit.YARDS_US, c.metrics(), c.outputTableName(), c.motionSemantics(), window);
        var issues = new RecordingIssueSink();
        var result = new TrackMotionStatisticsNodeOperator().apply(new TrackMotionStatisticsNodeDefinition(id(), "单位", LAYOUT, configuration),
                Map.of("motion_points", points), context(issues));
        assertFalse(issues.hasErrors(), issues::toString);
        Row row = result.propagatedTables().get("motion_result").dataset().orderBy("sequence").collectAsList().getLast();
        assertEquals(3d / 0.9144d, (Double) row.getAs("distance"), 1e-9);
        assertEquals(1d, (Double) row.getAs("elevation"), 1e-9);
        assertEquals(3d, (Double) row.getAs("speed"), 1e-9);
        assertEquals(1200d / 3937, (Double) row.getAs("slope"), 1e-9);
    }

    private SparkCanvasTable motionPoints(List<Row> rows) {
        return geometryTable("motion_raw", "motion_points", "shape", GeometryKind.POINT, 3857,
                List.of(longColumn("track_id"),
                        new CanvasColumnSchema("event_time", PlatformDataType.TIMESTAMP, null, null, null, true, null, false, false, null),
                        longColumn("sequence"),
                        new CanvasColumnSchema("altitude", PlatformDataType.DOUBLE, null, null, null, true, null, false, false, null),
                        new CanvasColumnSchema("wkt", PlatformDataType.STRING, null, null, null, true, null, false, false, null)), rows);
    }

    @Test
    void incompleteWindowAndDwellDraftsReportIssuesRatherThanNullMapAccess() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var motion = mapper.readValue("{\"motionSemantics\":\"OBSERVATION_WINDOW\"}", TrackMotionStatisticsConfiguration.class);
        var motionIssues = new RecordingIssueSink();
        var motionResult = new TrackMotionStatisticsNodeOperator().apply(
                new TrackMotionStatisticsNodeDefinition(id(), "运动", LAYOUT, motion), Map.of(), context(motionIssues));
        assertTrue(motionIssues.hasErrors()); assertTrue(motionResult.propagatedTables().isEmpty());
        var dwell = mapper.readValue("{\"dwellSemantics\":\"REFERENCE_CENTER\"}", TrackFindDwellConfiguration.class);
        var dwellIssues = new RecordingIssueSink();
        var dwellResult = new TrackFindDwellNodeOperator().apply(
                new TrackFindDwellNodeDefinition(id(), "驻留", LAYOUT, dwell), Map.of(), context(dwellIssues));
        assertTrue(dwellIssues.hasErrors()); assertTrue(dwellResult.propagatedTables().isEmpty());
    }

    @Test
    void motionUsesWgs84DistanceAndBearingAcrossDatelineWithIndependentElevation() {
        var source = geometryTable("motion_geo_raw", "motion_points", "shape", GeometryKind.POINT, 4326,
                List.of(longColumn("track_id"), timestampColumn("event_time"), longColumn("sequence"), doubleColumn("altitude"), stringColumn("wkt")),
                List.of(RowFactory.create(1L, time("2026-01-01 00:00:00"), 1L, 0d, "POINT (179.99 0)"),
                        RowFactory.create(1L, time("2026-01-01 00:01:00"), 2L, 0d, "POINT (-179.99 0)")));
        var c = motionConfig(3);
        var geodesic = new TrackMotionStatisticsConfiguration(c.sourceTableName(), c.pointGeometryColumnName(), c.trackIdColumns(),
                c.timeColumnName(), SpatialDistanceMethod.GEODESIC, c.boundaries(), c.historyPoints(), c.idleDistanceThreshold(),
                c.idleDistanceThresholdUnit(), c.metrics(), c.outputTableName(), c.motionSemantics(), c.windowOptions());
        var issues = new RecordingIssueSink();
        var result = new TrackMotionStatisticsNodeOperator().apply(new TrackMotionStatisticsNodeDefinition(id(), "运动", LAYOUT, geodesic),
                Map.of("motion_points", source), context(issues));
        assertFalse(issues.hasErrors(), issues::toString);
        var row = result.propagatedTables().get("motion_result").dataset().orderBy("sequence").collectAsList().get(1);
        assertEquals(2226.3898, (Double) row.getAs("distance"), 0.001);
        assertEquals(90d, (Double) row.getAs("bearing"), 1e-8);
        assertEquals(0d, (Double) row.getAs("elevation"), 1e-8);
    }

    @Test
    void motionIdleUsesStrictDoubleThresholdsAndFixedBoundariesResetWindows() {
        var points = motionPoints(List.of(
                RowFactory.create(1L, time("2026-01-01 00:00:00"), 1L, 0d, "POINT (0 0)"),
                RowFactory.create(1L, time("2026-01-01 00:00:04"), 2L, 0d, "POINT (1 0)"),
                RowFactory.create(1L, time("2026-01-01 00:00:07"), 3L, 0d, "POINT (1 0)"),
                RowFactory.create(1L, time("2026-01-01 00:00:11"), 4L, 0d, "POINT (1 0)")));
        var c = motionConfig(3);
        var issues = new RecordingIssueSink();
        var output = new TrackMotionStatisticsNodeOperator().apply(new TrackMotionStatisticsNodeDefinition(id(), "运动", LAYOUT, c),
                Map.of("motion_points", points), context(issues)).propagatedTables().get("motion_result");
        var rows = output.dataset().orderBy("sequence").collectAsList();
        assertFalse(issues.hasErrors(), issues::toString);
        assertEquals(false, rows.get(1).getAs("idling")); // Distance equals tolerance.
        assertEquals(false, rows.get(2).getAs("idling")); // Time equals tolerance.
        assertEquals(true, rows.get(3).getAs("idling"));
        var boundaries = new TrackBoundaryConfiguration(null, null, null, null,
                new TrackFixedTimeBoundary(5, TrackTimeBoundaryUnit.SECONDS, null, null));
        var split = new TrackMotionStatisticsConfiguration(c.sourceTableName(), c.pointGeometryColumnName(), c.trackIdColumns(),
                c.timeColumnName(), c.distanceMethod(), boundaries, c.historyPoints(), c.idleDistanceThreshold(),
                c.idleDistanceThresholdUnit(), c.metrics(), c.outputTableName(), c.motionSemantics(), c.windowOptions());
        var splitIssues = new RecordingIssueSink();
        var splitOutput = new TrackMotionStatisticsNodeOperator().apply(new TrackMotionStatisticsNodeDefinition(id(), "运动", LAYOUT, split),
                Map.of("motion_points", points), context(splitIssues)).propagatedTables().get("motion_result");
        var splitRows = splitOutput.dataset().orderBy("sequence").collectAsList();
        assertFalse(splitIssues.hasErrors(), splitIssues::toString);
        assertNull(splitRows.get(2).getAs("duration")); assertNull(splitRows.get(2).getAs("tot_distance"));
        assertNull(splitRows.get(3).getAs("duration"));
    }

    @Test
    void motionRejectsIncompleteGroupsAndUnknownElevationUnitsBeforePropagation() {
        var source = motionPoints(List.of());
        var c = motionConfig(3);
        var o = c.windowOptions();
        var invalidOptions = new TrackMotionWindowOptions(3, List.of("missing_order"),
                List.of(new TrackMotionWindowStatistic(id(), TrackMotionStatistic.ELEVATION, "height")),
                o.distanceUnit(), o.durationUnit(), o.speedUnit(), o.accelerationUnit(), "altitude",
                SpatialDistanceUnit.SOURCE_CRS_UNIT, o.elevationUnit(), null, null);
        var invalid = new TrackMotionStatisticsConfiguration(c.sourceTableName(), c.pointGeometryColumnName(), c.trackIdColumns(),
                c.timeColumnName(), c.distanceMethod(), c.boundaries(), 0, null, null, List.of(), c.outputTableName(),
                c.motionSemantics(), invalidOptions);
        var issues = new RecordingIssueSink();
        var result = new TrackMotionStatisticsNodeOperator().apply(new TrackMotionStatisticsNodeDefinition(id(), "运动", LAYOUT, invalid),
                Map.of("motion_points", source), context(issues));
        assertTrue(issues.codes.contains("TRACK_MOTION_GROUP_INCOMPLETE"));
        assertTrue(issues.codes.contains("TRACK_ELEVATION_UNIT_REQUIRED"));
        assertTrue(issues.codes.contains("COLUMN_NOT_FOUND"));
        assertTrue(result.propagatedTables().isEmpty());
    }

    @Test
    void motionMissingGeometryDoesNotBridgeObservationsOrDiluteSpeedAverages() {
        var source = motionPoints(List.of(
                RowFactory.create(1L, time("2026-01-01 00:00:00"), 1L, 0d, "POINT (0 0)"),
                RowFactory.create(1L, time("2026-01-01 00:00:01"), 2L, Double.NaN, "POINT (3 0)"),
                RowFactory.create(1L, time("2026-01-01 00:00:03"), 3L, 20d, null),
                RowFactory.create(1L, time("2026-01-01 00:00:04"), 4L, Double.POSITIVE_INFINITY, "POINT (3 4)"),
                RowFactory.create(1L, time("2026-01-01 00:00:06"), 5L, 30d, "POINT EMPTY"),
                RowFactory.create(1L, time("2026-01-01 00:00:07"), 6L, 40d, "POINT (6 4)"),
                RowFactory.create(1L, time("2026-01-01 00:00:08"), 7L, 50d, "POINT (9 4)")));
        var issues = new RecordingIssueSink();
        var result = new TrackMotionStatisticsNodeOperator().apply(new TrackMotionStatisticsNodeDefinition(id(), "缺失", LAYOUT, motionConfig(3)),
                Map.of("motion_points", source), context(issues));
        assertFalse(issues.hasErrors(), issues::toString);
        var rows = result.propagatedTables().get("motion_result").dataset().orderBy("sequence").collectAsList();
        assertEquals(7, rows.size());
        for (int index : new int[]{2, 3, 4, 5}) {
            assertNull(rows.get(index).getAs("distance"));
            assertNull(rows.get(index).getAs("speed"));
            assertNull(rows.get(index).getAs("idling"));
            assertNull(rows.get(index).getAs("bearing"));
        }
        assertEquals(3d, (Double) rows.get(2).getAs("tot_distance"));
        assertEquals(3d, (Double) rows.get(2).getAs("tot_duration"));
        assertEquals(3d, (Double) rows.get(2).getAs("avg_speed"));
        assertNull(rows.get(3).getAs("tot_distance"));
        assertNull(rows.get(3).getAs("avg_speed"));
        assertEquals(3d, (Double) rows.get(3).getAs("tot_duration"));
        assertNull(rows.get(1).getAs("elevation"));
        assertNull(rows.get(3).getAs("elevation"));
        assertEquals(6.096, (Double) rows.get(3).getAs("avg_elevation"), 1e-12);
        assertTrue(Double.isNaN((Double) rows.get(1).getAs("altitude")));
        assertEquals(Double.POSITIVE_INFINITY, (Double) rows.get(3).getAs("altitude"));
        assertEquals(3d, (Double) rows.getLast().getAs("distance"));
        assertEquals(3d, (Double) rows.getLast().getAs("avg_speed"));
        assertNull(rows.getLast().getAs("acceleration"));
        assertTrue(result.propagatedTables().get("motion_points") == source);
    }

    @Test
    void motionWindowTwoExcludesPriorAccelerationButRetainsInstantaneousAcceleration() {
        var source = motionPoints(List.of(
                RowFactory.create(1L, time("2026-01-01 00:00:00"), 1L, 0d, "POINT (0 0)"),
                RowFactory.create(1L, time("2026-01-01 00:00:01"), 2L, 0d, "POINT (1 0)"),
                RowFactory.create(1L, time("2026-01-01 00:00:02"), 3L, 0d, "POINT (3 0)"),
                RowFactory.create(1L, time("2026-01-01 00:00:03"), 4L, 0d, "POINT (6 0)")));
        var issues = new RecordingIssueSink();
        var result = new TrackMotionStatisticsNodeOperator().apply(new TrackMotionStatisticsNodeDefinition(id(), "窗口", LAYOUT, motionConfig(2)),
                Map.of("motion_points", source), context(issues));
        assertFalse(issues.hasErrors(), issues::toString);
        var row = result.propagatedTables().get("motion_result").dataset().orderBy("sequence").collectAsList().getLast();
        assertEquals(3d, (Double) row.getAs("tot_distance"));
        assertEquals(3d, (Double) row.getAs("avg_speed"));
        assertEquals(1d, (Double) row.getAs("acceleration"));
        assertNull(row.getAs("min_acceleration"));
        assertNull(row.getAs("max_acceleration"));
    }

    @Test
    void motionNullTimesAndTrackBoundariesRemainIndependentAfterShuffle() {
        var original = motionPoints(List.of(
                RowFactory.create(1L, time("2026-01-01 00:00:00"), 1L, 0d, "POINT (0 0)"),
                RowFactory.create(1L, null, 2L, 0d, "POINT (100 0)"),
                RowFactory.create(1L, time("2026-01-01 00:00:02"), 3L, 0d, "POINT (3 0)"),
                RowFactory.create(2L, time("2026-01-01 00:00:02"), 3L, 0d, "POINT (1000 0)")));
        var source = new SparkCanvasTable(original.schema(), original.dataset().repartition(3));
        var issues = new RecordingIssueSink();
        String group = "motion-context-" + id();
        spark.sparkContext().setJobGroup(group, "motion zero-job preflight", false);
        CanvasNodeOperationResult result;
        try {
            result = new TrackMotionStatisticsNodeOperator().apply(new TrackMotionStatisticsNodeDefinition(id(), "时间", LAYOUT, motionConfig(3)),
                    Map.of("motion_points", source), context(issues));
            result.propagatedTables().get("motion_result").dataset().queryExecution().analyzed();
            assertEquals(0, spark.sparkContext().statusTracker().getJobIdsForGroup(group).length);
        } finally { spark.sparkContext().clearJobGroup(); }
        assertFalse(issues.hasErrors(), issues::toString);
        var rows = result.propagatedTables().get("motion_result").dataset().orderBy("track_id", "sequence").collectAsList();
        assertEquals(3, rows.size());
        assertEquals(3d, (Double) rows.get(1).getAs("distance"));
        assertEquals(2d, (Double) rows.get(1).getAs("duration"));
        assertNull(rows.get(2).getAs("duration"));
        assertNull(rows.get(2).getAs("tot_distance"));
    }

    @Test
    void fixedWeekSlicingDoesNotShiftAtDstWhileCalendarWeekDoes() {
        var source = motionPoints(List.of(RowFactory.create(1L, Timestamp.from(java.time.Instant.parse("2024-03-11T04:30:00Z")),
                1L, 0d, "POINT (0 0)")));
        var issues = new RecordingIssueSink();
        var fixed = new SpatialTemporalSlicing("event_time", 1, SpatialDurationUnit.WEEKS, null, null,
                "2024-03-04T00:00:00-05:00", "America/New_York", "start", "end");
        var calendar = new SpatialTemporalSlicing("event_time", 1, SpatialDurationUnit.WEEKS, null, null,
                fixed.referenceTime(), fixed.timeZone(), "start", "end", new SpatialCalendarWindowOptions(
                        SpatialCalendarWindowOptions.Mode.CALENDAR, SpatialCalendarWindowOptions.Unit.WEEKS, null));
        var rows = new java.util.ArrayList<Row>();
        for (var slicing : List.of(fixed, calendar)) {
            String group = "week-slicing-" + id();
            spark.sparkContext().setJobGroup(group, "week slicing preflight", false);
            org.apache.spark.sql.Dataset<Row> result;
            try {
                var params = SpatialTemporalSupport.validateAndResolve(slicing, "configuration.temporalSlicing", issues);
                assertFalse(issues.hasErrors(), issues::toString);
                result = SpatialTemporalSupport.addWindows(source.dataset(), source.dataset().col("event_time"), params, "start", "end");
                result.queryExecution().analyzed();
                assertEquals(0, spark.sparkContext().statusTracker().getJobIdsForGroup(group).length);
            } finally { spark.sparkContext().clearJobGroup(); }
            var collected = result.select("start", "end").collectAsList();
            assertEquals(1, collected.size()); rows.add(collected.getFirst());
        }
        assertEquals(java.time.Instant.parse("2024-03-04T05:00:00Z"), rows.get(0).getTimestamp(0).toInstant());
        assertEquals(java.time.Instant.parse("2024-03-11T05:00:00Z"), rows.get(0).getTimestamp(1).toInstant());
        assertEquals(java.time.Instant.parse("2024-03-11T04:00:00Z"), rows.get(1).getTimestamp(0).toInstant());
        assertEquals(java.time.Instant.parse("2024-03-18T04:00:00Z"), rows.get(1).getTimestamp(1).toInstant());
    }

    @Test
    void motionFixedWeeksApplyToDurationIdleAndTrackGapWithoutJobsDuringPreflight() {
        var source = motionPoints(List.of(
                RowFactory.create(1L, time("2026-01-01 00:00:00"), 1L, 0d, "POINT (0 0)"),
                RowFactory.create(1L, time("2026-01-08 00:00:00"), 2L, 0d, "POINT (0 0)"),
                RowFactory.create(1L, time("2026-01-15 00:00:00"), 3L, 0d, "POINT (0 0)"),
                RowFactory.create(1L, time("2026-01-29 00:00:01"), 4L, 0d, "POINT (0 0)")));
        var c = motionConfig(3); var w = c.windowOptions();
        var configuration = new TrackMotionStatisticsConfiguration(c.sourceTableName(), c.pointGeometryColumnName(), c.trackIdColumns(), c.timeColumnName(),
                c.distanceMethod(), new TrackBoundaryConfiguration(2d, SpatialDurationUnit.WEEKS, null, null), c.historyPoints(),
                c.idleDistanceThreshold(), c.idleDistanceThresholdUnit(), c.metrics(), c.outputTableName(), c.motionSemantics(),
                new TrackMotionWindowOptions(w.observationCount(), w.orderByColumns(), w.statistics(), w.distanceUnit(), SpatialDurationUnit.WEEKS,
                        w.speedUnit(), w.accelerationUnit(), w.elevationColumnName(), w.inputElevationUnit(), w.elevationUnit(), 0.5, SpatialDurationUnit.WEEKS));
        var issues = new RecordingIssueSink();
        String group = "motion-weeks-" + id();
        spark.sparkContext().setJobGroup(group, "fixed duration preflight", false);
        CanvasNodeOperationResult result;
        try {
            result = new TrackMotionStatisticsNodeOperator().apply(new TrackMotionStatisticsNodeDefinition(id(), "周", LAYOUT, configuration),
                    Map.of("motion_points", source), context(issues));
            assertFalse(issues.hasErrors(), issues::toString);
            result.propagatedTables().get("motion_result").dataset().queryExecution().analyzed();
            assertEquals(0, spark.sparkContext().statusTracker().getJobIdsForGroup(group).length);
        } finally { spark.sparkContext().clearJobGroup(); }
        var rows = result.propagatedTables().get("motion_result").dataset().orderBy("sequence").collectAsList();
        assertEquals(4, rows.size());
        assertEquals(1d, rows.get(1).<Double>getAs("duration"));
        assertEquals(2d, rows.get(2).<Double>getAs("tot_duration"));
        assertEquals(2d, rows.get(2).<Double>getAs("tot_idle_time"));
        assertNull(rows.get(3).getAs("duration"));
        org.junit.jupiter.api.Assertions.assertSame(source, result.propagatedTables().get("motion_points"));
    }

    private TrackMotionStatisticsConfiguration motionConfig(int window) {
        List<TrackMotionWindowStatistic> statistics = java.util.Arrays.stream(TrackMotionStatistic.values())
                .map(kind -> new TrackMotionWindowStatistic(id(), kind, kind.name().toLowerCase(java.util.Locale.ROOT))).toList();
        return new TrackMotionStatisticsConfiguration("motion_points", "shape", List.of("track_id"), "event_time",
                SpatialDistanceMethod.PLANAR, new TrackBoundaryConfiguration(null, null, null, null), 0, 1d, SpatialDistanceUnit.METERS,
                List.of(), "motion_result", TrackMotionSemantics.OBSERVATION_WINDOW,
                new TrackMotionWindowOptions(window, List.of("sequence"), statistics, SpatialDistanceUnit.METERS, SpatialDurationUnit.SECONDS,
                        SpatialSpeedUnit.METERS_PER_SECOND, SpatialAccelerationUnit.METERS_PER_SECOND_SQUARED,
                        "altitude", SpatialDistanceUnit.FEET, SpatialDistanceUnit.METERS, 3d, SpatialDurationUnit.SECONDS));
    }

    @Test
    void withinSeparatesRawApportionedAndWeightedValuesAndIgnoresMissingWeights() {
        var areas = withinAreas();
        var parcels = geometryTable("allocation_raw", "summaries", "shape", GeometryKind.POLYGON, 3857,
                List.of(new CanvasColumnSchema("amount", PlatformDataType.DOUBLE, null, null, null, true,
                                null, false, false, null, null),
                        new CanvasColumnSchema("category", PlatformDataType.STRING, null, null, null, true,
                                null, false, false, null, null), stringColumn("wkt")), List.of(
                        RowFactory.create(100d, "housing", "POLYGON ((0 0, 20 0, 20 10, 0 10, 0 0))"),
                        RowFactory.create(40d, "housing", "POLYGON ((1 1, 3 1, 3 3, 1 3, 1 1))"),
                        RowFactory.create(null, null, "POLYGON ((5 5, 6 5, 6 6, 5 6, 5 5))")));
        var statistics = List.of(
                new SpatialWithinStatistic(id(), SpatialWithinStatisticKind.COUNT, null, "features"),
                new SpatialWithinStatistic(id(), SpatialWithinStatisticKind.COUNT_FIELD, "amount", "nonnull_count"),
                new SpatialWithinStatistic(id(), SpatialWithinStatisticKind.ANY, "category", "sample"),
                new SpatialWithinStatistic(id(), SpatialWithinStatisticKind.SUM, "amount", "raw_sum"),
                new SpatialWithinStatistic(id(), SpatialWithinStatisticKind.SUM, "amount", "allocated_sum",
                        SpatialWithinValueTreatment.APPORTION_TOTAL, SpatialWithinWeighting.NONE),
                new SpatialWithinStatistic(id(), SpatialWithinStatisticKind.MEAN, "amount", "weighted_mean",
                        SpatialWithinValueTreatment.ORIGINAL_VALUE, SpatialWithinWeighting.INTERSECTION_FRACTION),
                new SpatialWithinStatistic(id(), SpatialWithinStatisticKind.MEAN, "amount", "allocated_mean",
                        SpatialWithinValueTreatment.APPORTION_TOTAL, null),
                new SpatialWithinStatistic(id(), SpatialWithinStatisticKind.MIN, "amount", "__datascalpel_within_stat_0"));
        var issues = new RecordingIssueSink();
        var result = new SpatialSummarizeWithinNodeOperator().apply(withinNode(statistics), tables(areas, parcels), context(issues));
        assertFalse(issues.hasErrors(), issues::toString);
        assertEquals(List.of("areas", "summaries", "within"), new ArrayList<>(result.propagatedTables().keySet()));
        var rows = result.propagatedTables().get("within").dataset().orderBy("area_id").collectAsList();
        Row row = rows.getFirst();
        assertEquals(3L, (Long) row.getAs("features"));
        assertEquals(2L, (Long) row.getAs("nonnull_count"));
        assertEquals("housing", row.getAs("sample"));
        assertEquals(140d, (Double) row.getAs("raw_sum"), 1e-9);
        assertEquals(90d, (Double) row.getAs("allocated_sum"), 1e-9);
        assertEquals(60d, (Double) row.getAs("weighted_mean"), 1e-9);
        assertEquals(45d, (Double) row.getAs("allocated_mean"), 1e-9);
        assertEquals(40d, (Double) row.getAs("__datascalpel_within_stat_0"), 1e-9);
        assertEquals(0L, (Long) rows.get(1).getAs("features"));
        assertEquals(0L, (Long) rows.get(1).getAs("nonnull_count"));
        assertNull(rows.get(1).getAs("sample"));
        assertNull(rows.get(1).getAs("weighted_mean"));
        assertNull(rows.get(1).getAs("allocated_sum"));
    }

    @Test
    void withinLineAllocationHandlesDegenerateSourcesAndTypedEmptyInput() {
        var fields = List.of(doubleColumn("amount"), stringColumn("wkt"));
        var lines = geometryTable("allocation_lines_raw", "summaries", "shape", GeometryKind.LINESTRING, 3857,
                fields, List.of(RowFactory.create(100d, "LINESTRING (0 2, 20 2)"),
                        RowFactory.create(500d, "LINESTRING (2 2, 2 2)")));
        var statistics = List.of(
                new SpatialWithinStatistic(id(), SpatialWithinStatisticKind.SUM, "amount", "allocated",
                        SpatialWithinValueTreatment.APPORTION_TOTAL, null),
                new SpatialWithinStatistic(id(), SpatialWithinStatisticKind.MEAN, "amount", "weighted",
                        null, SpatialWithinWeighting.INTERSECTION_FRACTION));
        var issues = new RecordingIssueSink();
        var result = new SpatialSummarizeWithinNodeOperator().apply(withinNode(statistics), tables(withinAreas(), lines), context(issues));
        assertFalse(issues.hasErrors(), issues::toString);
        var first = result.propagatedTables().get("within").dataset().orderBy("area_id").head();
        assertEquals(50d, (Double) first.getAs("allocated"), 1e-9);
        assertEquals(100d, (Double) first.getAs("weighted"), 1e-9);
        var empty = geometryTable("allocation_empty_raw", "summaries", "shape", GeometryKind.LINESTRING, 3857, fields, List.of());
        var emptyIssues = new RecordingIssueSink();
        var emptyResult = new SpatialSummarizeWithinNodeOperator().apply(withinNode(statistics), tables(withinAreas(), empty), context(emptyIssues));
        assertFalse(emptyIssues.hasErrors(), emptyIssues::toString);
        assertEquals(2, emptyResult.propagatedTables().get("within").dataset().collectAsList().size());
        assertNull(emptyResult.propagatedTables().get("within").dataset().head().getAs("weighted"));
    }

    @Test
    void withinRejectsUndefinedCombinationsAndPointsWithoutPropagatingPartialResults() {
        var points = geometryTable("allocation_points_raw", "summaries", "shape", GeometryKind.POINT, 3857,
                List.of(doubleColumn("amount"), stringColumn("wkt")), List.of(RowFactory.create(100d, "POINT (1 1)")));
        var statistics = List.of(
                new SpatialWithinStatistic(id(), SpatialWithinStatisticKind.COUNT, null, "valid_count"),
                new SpatialWithinStatistic(id(), SpatialWithinStatisticKind.VARIANCE, "amount", "invalid",
                        SpatialWithinValueTreatment.APPORTION_TOTAL, SpatialWithinWeighting.INTERSECTION_FRACTION));
        var issues = new RecordingIssueSink();
        var result = new SpatialSummarizeWithinNodeOperator().apply(withinNode(statistics), tables(withinAreas(), points), context(issues));
        assertTrue(issues.codes.contains("SPATIAL_WITHIN_SHAPE_WEIGHT_UNSUPPORTED"));
        assertTrue(issues.codes.contains("SPATIAL_WITHIN_STATISTIC_COMBINATION_UNSUPPORTED"));
        assertTrue(result.propagatedTables().isEmpty());
        var missingIssues = new RecordingIssueSink();
        var missing = new SpatialSummarizeWithinNodeDefinition(id(), "草稿", LAYOUT,
                new SpatialSummarizeWithinConfiguration(null, null, null, null, true, null, null, null,
                        List.of(), List.of(), null, null, null));
        assertTrue(new SpatialSummarizeWithinNodeOperator().apply(missing, Map.of(), context(missingIssues)).propagatedTables().isEmpty());
        assertTrue(missingIssues.hasErrors());
    }

    @Test
    void withinGeodesicAllocationIsDimensionlessAndIndependentOfDisplayUnits() {
        var areas = geometryTable("allocation_geo_areas", "areas", "shape", GeometryKind.POLYGON, 4326,
                List.of(longColumn("area_id"), stringColumn("wkt")),
                List.of(RowFactory.create(1L, "POLYGON ((-1 -1, 1 -1, 1 1, -1 1, -1 -1))")));
        var lines = geometryTable("allocation_geo_lines", "summaries", "shape", GeometryKind.LINESTRING, 4326,
                List.of(doubleColumn("amount"), stringColumn("wkt")),
                List.of(RowFactory.create(100d, "LINESTRING (0 0, 2 0)")));
        var statistics = List.of(new SpatialWithinStatistic(id(), SpatialWithinStatisticKind.SUM, "amount", "allocated",
                SpatialWithinValueTreatment.APPORTION_TOTAL, null));
        for (var unit : List.of(SpatialDistanceUnit.METERS, SpatialDistanceUnit.MILES)) {
            var node = new SpatialSummarizeWithinNodeDefinition(id(), "测地汇总", LAYOUT,
                    new SpatialSummarizeWithinConfiguration("areas", "shape", "summaries", "shape", true,
                            SpatialDistanceMethod.GEODESIC, unit, SpatialAreaUnit.SQUARE_KILOMETERS,
                            List.of(output(JoinOutputColumnSource.LEFT, "area_id", "area_id")), statistics,
                            null, null, "within"));
            var issues = new RecordingIssueSink();
            var result = new SpatialSummarizeWithinNodeOperator().apply(node, tables(areas, lines), context(issues));
            assertFalse(issues.hasErrors(), issues::toString);
            assertEquals(50d, (Double) result.propagatedTables().get("within").dataset().head().getAs("allocated"), 1e-6);
        }
    }

    @Test
    void withinLinkedGroupsUseShapeSharesAndSeparateMainStatistics() {
        var areas = geometryTable("linked_areas", "areas", "shape", GeometryKind.POLYGON, 3857,
                List.of(longColumn("area_id"), stringColumn("label"), stringColumn("wkt")), List.of(
                        RowFactory.create(1L, "same", "POLYGON ((0 0, 10 0, 10 10, 0 10, 0 0))"),
                        RowFactory.create(2L, "same", "POLYGON ((0 0, 10 0, 10 10, 0 10, 0 0))"),
                        RowFactory.create(3L, "same", "POLYGON ((30 30, 40 30, 40 40, 30 40, 30 30))")));
        var lines = geometryTable("linked_lines", "summaries", "shape", GeometryKind.LINESTRING, 3857,
                List.of(doubleColumn("amount"), stringColumn("category"), stringColumn("wkt")), List.of(
                        RowFactory.create(100d, "long", "LINESTRING (0 1, 4 1)"),
                        RowFactory.create(200d, "long", "LINESTRING (0 2, 5 2)"),
                        RowFactory.create(0d, "short", "LINESTRING (0 3, 1 3)")));
        var node = withinLinkedNode("label", null);
        var issues = new RecordingIssueSink();
        var result = new SpatialSummarizeWithinNodeOperator().apply(node, tables(areas, lines), context(issues));
        assertFalse(issues.hasErrors(), issues::toString);
        assertEquals(List.of("areas", "summaries", "within", "within_groups"), new ArrayList<>(result.propagatedTables().keySet()));
        var main = result.propagatedTables().get("within").dataset().orderBy("summary_area_id").collectAsList();
        assertEquals(3, main.size());
        assertEquals(3L, (Long) main.getFirst().getAs("features"));
        assertEquals(100d, (Double) main.getFirst().getAs("mean_amount"), 1e-9);
        assertEquals("long", main.getFirst().getAs("majority"));
        assertEquals("short", main.getFirst().getAs("minority"));
        assertEquals(90d, (Double) main.getFirst().getAs("majority_pct"), 1e-9);
        assertEquals(10d, (Double) main.getFirst().getAs("minority_pct"), 1e-9);
        assertEquals(0L, (Long) main.get(2).getAs("features"));
        assertNull(main.get(2).getAs("majority"));
        var groupTable = result.propagatedTables().get("within_groups");
        assertTrue(groupTable.schema().columns().stream().noneMatch(c -> c.fieldType() == PlatformDataType.GEOMETRY));
        var groups = groupTable.dataset().orderBy("summary_area_id", "group_value").collectAsList();
        assertEquals(4, groups.size());
        assertEquals(1L, (Long) groups.getFirst().getAs("summary_area_id"));
        assertEquals(150d, (Double) groups.getFirst().getAs("mean_amount"), 1e-9);
        assertEquals(90d, (Double) groups.getFirst().getAs("group_pct"), 1e-9);
        assertEquals(10d, (Double) groups.get(1).getAs("group_pct"), 1e-9);
    }

    @Test
    void withinLinkedGroupsRetainRealNullGroupsAndApplyStableTieOrderAndTimeWindows() {
        var points = geometryTable("linked_points", "summaries", "shape", GeometryKind.POINT, 3857,
                List.of(doubleColumn("amount"),
                        new CanvasColumnSchema("category", PlatformDataType.STRING, null, null, null, true, null, false, false, null, null),
                        timestampColumn("event_time"), stringColumn("wkt")), List.of(
                        RowFactory.create(1d, "b", Timestamp.valueOf("2024-01-01 00:00:01"), "POINT (1 1)"),
                        RowFactory.create(3d, "a", Timestamp.valueOf("2024-01-01 00:00:02"), "POINT (2 2)"),
                        RowFactory.create(5d, null, Timestamp.valueOf("2024-01-01 00:00:03"), "POINT (3 3)"),
                        RowFactory.create(7d, "b", Timestamp.valueOf("2024-01-01 00:00:11"), "POINT (1 1)")));
        var time = new SpatialTemporalSlicing("event_time", 10, SpatialDurationUnit.SECONDS, null, null,
                null, "UTC", "window_start", "window_end");
        var issues = new RecordingIssueSink();
        var result = new SpatialSummarizeWithinNodeOperator().apply(withinLinkedNode("area_id", time), tables(withinAreas(), points), context(issues));
        assertFalse(issues.hasErrors(), issues::toString);
        var main = result.propagatedTables().get("within").dataset().orderBy("summary_area_id", "window_start").collectAsList();
        assertEquals(4, main.size());
        assertEquals("a", main.getFirst().getAs("majority"));
        assertEquals("a", main.getFirst().getAs("minority"));
        assertEquals("b", main.get(1).getAs("majority"));
        assertEquals(100d / 3d, (Double) main.getFirst().getAs("majority_pct"), 1e-9);
        var groups = result.propagatedTables().get("within_groups").dataset().collectAsList();
        assertEquals(4, groups.size());
        assertEquals(1, groups.stream().filter(row -> row.getAs("group_value") == null).count());
        assertTrue(groups.stream().allMatch(row -> ((Long) row.getAs("summary_area_id")) == 1L));
        var config = withinLinkedNode("area_id", time).configuration();
        var saved = config.groupResult();
        var legacyConfig = new SpatialSummarizeWithinConfiguration("areas", "shape", "summaries", "shape", true,
                config.distanceMethod(), config.lengthUnit(), config.areaUnit(), config.areaOutputColumns(), config.statistics(),
                new SpatialGroupSummary("category", true, true, "old_min", "old_max", "old_pct"), time, "within",
                new SpatialWithinGroupResult(saved.areaKeyColumnName(), saved.areaKeyOutputColumnName(), saved.outputTableName(),
                        saved.groupValueColumnName(), saved.minorityValueColumnName(), saved.majorityValueColumnName(),
                        saved.minorityPercentageColumnName(), saved.majorityPercentageColumnName(), SpatialWithinGroupResultMode.LEGACY_FLAT));
        var legacyIssues = new RecordingIssueSink();
        var legacy = new SpatialSummarizeWithinNodeOperator().apply(new SpatialSummarizeWithinNodeDefinition(id(), "旧分组", LAYOUT, legacyConfig),
                tables(withinAreas(), points), context(legacyIssues));
        assertFalse(legacyIssues.hasErrors(), legacyIssues::toString);
        assertFalse(legacy.propagatedTables().containsKey("within_groups"));
        assertEquals(6, legacy.propagatedTables().get("within").dataset().collectAsList().size());
    }

    @Test
    void withinLinkedAreaKeysFailLazilyWithoutDisclosingValues() {
        var badAreas = geometryTable("linked_bad_areas", "areas", "shape", GeometryKind.POLYGON, 3857,
                List.of(longColumn("area_id"), stringColumn("wkt")), List.of(
                        RowFactory.create(999999L, "POLYGON ((0 0, 10 0, 10 10, 0 10, 0 0))"),
                        RowFactory.create(999999L, "POLYGON ((0 0, 5 0, 5 5, 0 5, 0 0))")));
        var points = geometryTable("linked_key_points", "summaries", "shape", GeometryKind.POINT, 3857,
                List.of(doubleColumn("amount"), stringColumn("category"), stringColumn("wkt")),
                List.of(RowFactory.create(1d, "x", "POINT (1 1)")));
        var issues = new RecordingIssueSink();
        var result = new SpatialSummarizeWithinNodeOperator().apply(withinLinkedNode("area_id", null), tables(badAreas, points), context(issues));
        assertFalse(issues.hasErrors(), issues::toString);
        var error = assertThrows(Exception.class, () -> result.propagatedTables().get("within_groups").dataset().collectAsList());
        assertTrue(error.toString().contains("SPATIAL_WITHIN_AREA_KEY_INVALID"));
        assertFalse(error.toString().contains("999999"));
        var nullKeyAreas = geometryTable("linked_null_key_areas", "areas", "shape", GeometryKind.POLYGON, 3857,
                List.of(new CanvasColumnSchema("area_id", PlatformDataType.LONG, null, null, null, true, null, false, false, null, null), stringColumn("wkt")),
                List.of(RowFactory.create(null, "POLYGON ((0 0, 10 0, 10 10, 0 10, 0 0))")));
        var nullIssues = new RecordingIssueSink();
        var nullResult = new SpatialSummarizeWithinNodeOperator().apply(withinLinkedNode("area_id", null), tables(nullKeyAreas, points), context(nullIssues));
        assertFalse(nullIssues.hasErrors(), nullIssues::toString);
        var nullError = assertThrows(Exception.class, () -> nullResult.propagatedTables().get("within").dataset().head());
        assertTrue(nullError.toString().contains("SPATIAL_WITHIN_AREA_KEY_INVALID"));
    }

    @Test
    void withinLinkedGroupsMeasurePolygonAreaAndCountClippedMultiPoints() {
        for (GeometryKind kind : List.of(GeometryKind.POLYGON, GeometryKind.MULTIPOINT)) {
            var records = kind == GeometryKind.POLYGON ? List.of(
                    RowFactory.create(1d, "a", "POLYGON ((0 0, 9 0, 9 10, 0 10, 0 0))"),
                    RowFactory.create(1d, "b", "POLYGON ((9 0, 10 0, 10 10, 9 10, 9 0))")) : List.of(
                    RowFactory.create(1d, "a", "MULTIPOINT ((1 1), (2 2), (3 3), (20 20))"),
                    RowFactory.create(1d, "b", "MULTIPOINT ((4 4))"));
            var source = geometryTable("linked_shape_raw", "summaries", "shape", kind, 3857,
                    List.of(doubleColumn("amount"), stringColumn("category"), stringColumn("wkt")), records);
            var issues = new RecordingIssueSink();
            var result = new SpatialSummarizeWithinNodeOperator().apply(withinLinkedNode("area_id", null), tables(withinAreas(), source), context(issues));
            assertFalse(issues.hasErrors(), issues::toString);
            var main = result.propagatedTables().get("within").dataset().orderBy("summary_area_id").head();
            assertEquals(kind == GeometryKind.POLYGON ? 90d : 75d, (Double) main.getAs("majority_pct"), 1e-8);
        }
    }

    @Test
    void withinLinkedGroupsRejectConflictsBeforePropagatingEitherTable() {
        var source = geometryTable("linked_invalid_source", "summaries", "shape", GeometryKind.POINT, 3857,
                List.of(doubleColumn("amount"), stringColumn("category"), stringColumn("wkt")), List.of());
        var original = withinLinkedNode("area_id", null).configuration();
        var invalid = new SpatialSummarizeWithinConfiguration(original.areaTableName(), original.areaGeometryColumnName(),
                original.summaryTableName(), original.summaryGeometryColumnName(), true, original.distanceMethod(),
                original.lengthUnit(), original.areaUnit(), original.areaOutputColumns(), original.statistics(),
                original.groupSummary(), null, "within", new SpatialWithinGroupResult("missing_key", "features", "areas", "group_value",
                "minority", "majority", "minority_pct", "majority_pct"));
        var issues = new RecordingIssueSink();
        var result = new SpatialSummarizeWithinNodeOperator().apply(new SpatialSummarizeWithinNodeDefinition(id(), "无效分组", LAYOUT, invalid),
                tables(withinAreas(), source), context(issues));
        assertTrue(issues.codes.containsAll(List.of("COLUMN_NOT_FOUND", "DUPLICATE_TABLE_NAME", "DUPLICATE_COLUMN_NAME")));
        assertTrue(result.propagatedTables().isEmpty());
    }

    @Test
    void overlayFiveModesPreserveGeometryAndMissingSideAttributes() {
        var left = overlayLayer("left", GeometryKind.POLYGON, "POLYGON ((0 0, 10 0, 10 10, 0 10, 0 0))");
        var right = overlayLayer("right", GeometryKind.POLYGON, "POLYGON ((5 0, 15 0, 15 10, 5 10, 5 0))");
        double[] areas = {50, 50, 150, 100, 100};
        int[] counts = {1, 1, 3, 2, 2};
        for (var mode : SpatialOverlayOperation.values()) {
            var issues = new RecordingIssueSink();
            var result = new SpatialOverlayNodeOperator().apply(overlayNode(mode, SpatialOverlayGeometryPolicy.FAMILY_2D),
                    tables(left, right), context(issues));
            assertFalse(issues.hasErrors(), issues::toString);
            assertEquals(List.of("left", "right", "overlay"), new ArrayList<>(result.propagatedTables().keySet()));
            var output = result.propagatedTables().get("overlay");
            assertEquals(CanvasDatasetKind.BOUNDED, output.schema().datasetKind());
            assertNull(output.schema().eventTimeColumn());
            assertNull(output.schema().watermarkDelay());
            var rows = output.dataset().collectAsList();
            assertEquals(counts[mode.ordinal()], rows.size(), mode.toString());
            assertEquals(areas[mode.ordinal()], rows.stream().mapToDouble(row -> ((Geometry) row.getAs("result_shape")).getArea()).sum(), 1e-8);
            for (Row row : rows) {
                Geometry geometry = row.getAs("result_shape");
                assertEquals("MultiPolygon", geometry.getGeometryType());
                assertEquals(3857, geometry.getSRID());
            }
            assertEquals(GeometryKind.MULTIPOLYGON, output.schema().columns().getLast().geometry().kind());
            assertEquals(CoordinateDimension.XY, output.schema().columns().getLast().geometry().dimension());
            if (mode == SpatialOverlayOperation.IDENTITY) {
                assertFalse(output.schema().columns().getFirst().nullable());
                assertTrue(output.schema().columns().get(1).nullable());
                assertEquals(1, rows.stream().filter(row -> row.getAs("right_id") == null).count());
                assertTrue(rows.stream().allMatch(row -> row.getAs("left_id") != null));
            }
            if (mode == SpatialOverlayOperation.UNION || mode == SpatialOverlayOperation.SYMMETRICAL_DIFFERENCE) {
                assertTrue(output.schema().columns().getFirst().nullable());
                assertTrue(output.schema().columns().get(1).nullable());
                assertEquals(1, rows.stream().filter(row -> row.getAs("left_id") == null).count());
            }
        }
    }

    @Test
    void overlayEraseUnionsOverlappingMasksAndPreservesHolesAndSeparateFeatures() {
        var left = overlayLayer("left", GeometryKind.POLYGON,
                "POLYGON ((0 0, 10 0, 10 10, 0 10, 0 0))",
                "POLYGON ((0 0, 10 0, 10 10, 0 10, 0 0))");
        var right = overlayLayer("right", GeometryKind.POLYGON,
                "POLYGON ((0 0, 6 0, 6 10, 0 10, 0 0))",
                "POLYGON ((4 0, 8 0, 8 10, 4 10, 4 0))");
        var issues = new RecordingIssueSink();
        var result = new SpatialOverlayNodeOperator().apply(overlayNode(SpatialOverlayOperation.ERASE, SpatialOverlayGeometryPolicy.FAMILY_2D),
                tables(left, right), context(issues));
        assertFalse(issues.hasErrors(), issues::toString);
        var rows = result.propagatedTables().get("overlay").dataset().collectAsList();
        assertEquals(2, rows.size());
        assertTrue(rows.stream().allMatch(row -> Math.abs(((Geometry) row.getAs("result_shape")).getArea() - 20) < 1e-8));
        var hole = overlayLayer("right", GeometryKind.POLYGON, "POLYGON ((2 2, 8 2, 8 8, 2 8, 2 2))");
        var holeResult = new SpatialOverlayNodeOperator().apply(overlayNode(SpatialOverlayOperation.ERASE, SpatialOverlayGeometryPolicy.FAMILY_2D),
                tables(left, hole), context(new RecordingIssueSink()));
        Geometry difference = holeResult.propagatedTables().get("overlay").dataset().head().getAs("result_shape");
        assertEquals(64d, difference.getArea(), 1e-8);
        assertEquals(1, ((org.locationtech.jts.geom.Polygon) difference.getGeometryN(0)).getNumInteriorRing());
    }

    @Test
    void overlayFamilyFiltersLowerDimensionalContactsButKeepsLegacyBehavior() {
        var left = overlayLayer("left", GeometryKind.POLYGON, "POLYGON ((0 0, 1 0, 1 1, 0 1, 0 0))");
        var right = overlayLayer("right", GeometryKind.POLYGON, "POLYGON ((1 0, 2 0, 2 1, 1 1, 1 0))");
        var operator = new SpatialOverlayNodeOperator();
        var current = operator.apply(overlayNode(SpatialOverlayOperation.INTERSECTION, SpatialOverlayGeometryPolicy.FAMILY_2D),
                tables(left, right), context(new RecordingIssueSink()));
        assertEquals(0, current.propagatedTables().get("overlay").dataset().count());
        var old = operator.apply(overlayNode(SpatialOverlayOperation.INTERSECTION, null),
                tables(left, right), context(new RecordingIssueSink()));
        Geometry oldContact = old.propagatedTables().get("overlay").dataset().head().getAs("result_shape");
        assertEquals("LineString", oldContact.getGeometryType());
        assertEquals(GeometryKind.GEOMETRY, old.propagatedTables().get("overlay").schema().columns().getLast().geometry().kind());
        var line = overlayLayer("left", GeometryKind.LINESTRING, "LINESTRING (-1 0.5, 3 0.5)");
        var mixed = operator.apply(overlayNode(SpatialOverlayOperation.IDENTITY, null), tables(line, right), context(new RecordingIssueSink()));
        assertEquals(4d, mixed.propagatedTables().get("overlay").dataset().collectAsList().stream()
                .mapToDouble(row -> ((Geometry) row.getAs("result_shape")).getLength()).sum(), 1e-8);
        assertEquals(GeometryKind.MULTILINESTRING, mixed.propagatedTables().get("overlay").schema().columns().getLast().geometry().kind());
    }

    @Test
    void overlayValidatesAllFamilyPairsWithoutReadingAnyData() {
        var kinds = List.of(GeometryKind.POINT, GeometryKind.LINESTRING, GeometryKind.POLYGON);
        for (var mode : SpatialOverlayOperation.values()) {
            for (int left = 1; left <= 3; left++) for (int right = 1; right <= 3; right++) {
                boolean supported = switch (mode) {
                    case INTERSECTION -> true;
                    case ERASE, SYMMETRICAL_DIFFERENCE -> left == right;
                    case UNION -> left == 3 && right == 3;
                    case IDENTITY -> left == right || right == 3;
                };
                var issues = new RecordingIssueSink();
                var result = new SpatialOverlayNodeOperator().apply(overlayNode(mode, SpatialOverlayGeometryPolicy.FAMILY_2D),
                        tables(overlayLayer("left", kinds.get(left - 1)), overlayLayer("right", kinds.get(right - 1))), context(issues));
                assertEquals(!supported, issues.hasErrors(), mode + " " + left + " " + right + issues);
                assertEquals(!supported, result.propagatedTables().isEmpty());
                if (!supported) assertTrue(issues.codes.contains("SPATIAL_OVERLAY_GEOMETRY_COMBINATION_UNSUPPORTED"));
            }
        }
        var issues = new RecordingIssueSink();
        new SpatialOverlayNodeOperator().apply(new SpatialOverlayNodeDefinition(id(), "草稿", LAYOUT,
                new SpatialOverlayConfiguration(null, null, null, null, null, null, null, List.of())), Map.of(), context(issues));
        assertTrue(issues.codes.contains("REQUIRED_CONFIGURATION"));
    }

    @Test
    void overlaySupportsPointDifferencesAndDisjointIdentityWithoutInventingRightAttributes() {
        var left = overlayLayer("left", GeometryKind.MULTIPOINT, "MULTIPOINT ((0 0), (1 1), (2 2))");
        var right = overlayLayer("right", GeometryKind.POINT, "POINT (1 1)");
        var result = new SpatialOverlayNodeOperator().apply(overlayNode(SpatialOverlayOperation.SYMMETRICAL_DIFFERENCE, SpatialOverlayGeometryPolicy.FAMILY_2D),
                tables(left, right), context(new RecordingIssueSink()));
        var rows = result.propagatedTables().get("overlay").dataset().collectAsList();
        assertEquals(1, rows.size());
        Geometry geometry = rows.getFirst().getAs("result_shape");
        assertEquals("MultiPoint", geometry.getGeometryType());
        assertEquals(2, geometry.getNumPoints());
        assertNull(rows.getFirst().getAs("right_id"));
        var outside = overlayLayer("right", GeometryKind.POINT, "POINT (100 100)");
        var identity = new SpatialOverlayNodeOperator().apply(overlayNode(SpatialOverlayOperation.IDENTITY, SpatialOverlayGeometryPolicy.FAMILY_2D),
                tables(left, outside), context(new RecordingIssueSink()));
        var row = identity.propagatedTables().get("overlay").dataset().head();
        assertEquals(3, ((Geometry) row.getAs("result_shape")).getNumPoints());
        assertNull(row.getAs("right_id"));
    }

    @Test
    void overlaySkipsNullAndEmptyAndFailsInvalidGeometryOnlyWhenConsumed() {
        var left = overlayLayer("left", GeometryKind.POLYGON, "POLYGON ((0 0, 2 0, 2 2, 0 2, 0 0))");
        var empty = overlayLayer("right", GeometryKind.POLYGON, "POLYGON EMPTY");
        var schema = empty.schema();
        var nullAndEmpty = new SparkCanvasTable(schema, empty.dataset().withColumn("shape",
                org.apache.spark.sql.functions.when(org.apache.spark.sql.functions.col("id").equalTo(1), org.apache.spark.sql.functions.lit(null))
                        .otherwise(org.apache.spark.sql.functions.col("shape"))).unionByName(empty.dataset()));
        var result = new SpatialOverlayNodeOperator().apply(overlayNode(SpatialOverlayOperation.IDENTITY, SpatialOverlayGeometryPolicy.FAMILY_2D),
                tables(left, nullAndEmpty), context(new RecordingIssueSink()));
        assertEquals(4d, ((Geometry) result.propagatedTables().get("overlay").dataset().head().getAs("result_shape")).getArea(), 1e-8);
        var bad = overlayLayer("right", GeometryKind.POLYGON, "POLYGON ((0 0, 2 2, 0 2, 2 0, 0 0))");
        var issues = new RecordingIssueSink();
        var invalid = new SpatialOverlayNodeOperator().apply(overlayNode(SpatialOverlayOperation.INTERSECTION, SpatialOverlayGeometryPolicy.FAMILY_2D),
                tables(left, bad), context(issues));
        assertFalse(issues.hasErrors(), issues::toString);
        var error = assertThrows(Exception.class, () -> invalid.propagatedTables().get("overlay").dataset().collectAsList());
        assertTrue(error.toString().contains("SPATIAL_OVERLAY_INVALID_GEOMETRY"));
    }

    private SparkCanvasTable overlayLayer(String name, GeometryKind kind, String... geometries) {
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < geometries.length; i++) rows.add(RowFactory.create((long) i + 1, geometries[i]));
        return geometryTable(name + "_raw", name, "shape", kind, 3857,
                List.of(longColumn("id"), stringColumn("wkt")), rows);
    }

    @Test
    void reconstructOrderedSegmentsHonorAllThreeBoundaryOwnershipModes() {
        var source = reconstructPoints(List.of(
                RowFactory.create("a", 1L, time("2026-01-01 00:00:00"), 1d, "POINT (0 0)"),
                RowFactory.create("a", 2L, time("2026-01-01 01:00:00"), 1d, "POINT (1 0)"),
                RowFactory.create("a", 3L, time("2026-01-01 02:00:00"), 1d, "POINT (2 0)"),
                RowFactory.create("a", 4L, time("2026-01-01 05:00:00"), 10d, "POINT (5 0)"),
                RowFactory.create("a", 5L, time("2026-01-01 06:00:00"), 10d, "POINT (6 0)")));
        for (var mode : TrackSplitBoundaryOption.values()) {
            var issues = new RecordingIssueSink();
            var options = new TrackReconstructOptions(null, List.of("seq"), mode, null);
            var result = new TrackReconstructNodeOperator().apply(reconstructNode(options,
                    new TrackBoundaryConfiguration(2d, SpatialDurationUnit.HOURS, null, null)), Map.of("observations", source), context(issues));
            assertFalse(issues.hasErrors(), issues::toString);
            var rows = result.propagatedTables().get("rebuilt").dataset().orderBy("start").collectAsList();
            assertEquals(2, rows.size());
            assertEquals(mode == TrackSplitBoundaryOption.FINISH_LAST ? 4L : 3L, (Long) rows.get(0).getAs("n"));
            assertEquals(mode == TrackSplitBoundaryOption.START_NEXT ? 3L : 2L, (Long) rows.get(1).getAs("n"));
            assertEquals(mode == TrackSplitBoundaryOption.FINISH_LAST ? 10d : 1d, (Double) rows.get(0).getAs("last_value"));
            assertEquals(mode == TrackSplitBoundaryOption.START_NEXT ? 1d : 10d, (Double) rows.get(1).getAs("first_value"));
            assertEquals(mode == TrackSplitBoundaryOption.GAP ? 3d : 6d, rows.stream()
                    .mapToDouble(row -> ((Geometry) row.getAs("line")).getLength()).sum(), 1e-8);
            assertEquals(List.of("observations", "rebuilt"), new ArrayList<>(result.propagatedTables().keySet()));
        }
    }

    @Test
    void reconstructExpressionsUseTrackWindowsAndNeverBridgeFixedPeriods() {
        var source = reconstructPoints(List.of(
                RowFactory.create("a", 1L, time("2026-01-01 22:00:00"), 1d, "POINT (0 0)"),
                RowFactory.create("a", 2L, time("2026-01-01 23:00:00"), 1d, "POINT (1 0)"),
                RowFactory.create("a", 3L, time("2026-01-02 00:00:00"), 10d, "POINT (2 0)"),
                RowFactory.create("a", 4L, time("2026-01-02 01:00:00"), 10d, "POINT (3 0)")));
        var rule = new TrackSplitExpression("prior * 2 < value", List.of(new TrackFieldWindowBinding("prior", "value", -1)));
        var noGap = new TrackBoundaryConfiguration(null, null, null, null);
        var issues = new RecordingIssueSink();
        var result = new TrackReconstructNodeOperator().apply(reconstructNode(new TrackReconstructOptions(null, List.of("seq"), TrackSplitBoundaryOption.GAP, rule), noGap),
                Map.of("observations", source), context(issues));
        assertFalse(issues.hasErrors(), issues::toString);
        assertEquals(2, result.propagatedTables().get("rebuilt").dataset().count());
        var daily = new TrackFixedTimeBoundary(1, TrackTimeBoundaryUnit.DAYS, "2026-01-01T00:00:00", spark.conf().get("spark.sql.session.timeZone"));
        for (var mode : TrackSplitBoundaryOption.values()) {
            var fixedIssues = new RecordingIssueSink();
            var options = new TrackReconstructOptions(null, List.of("seq"), mode, new TrackSplitExpression("prior IS NULL", rule.bindings()));
            var fixed = new TrackReconstructNodeOperator().apply(reconstructNode(options,
                    new TrackBoundaryConfiguration(null, null, null, null, daily)), Map.of("observations", source), context(fixedIssues));
            assertFalse(fixedIssues.hasErrors(), fixedIssues::toString);
            var rows = fixed.propagatedTables().get("rebuilt").dataset().collectAsList();
            assertEquals(2, rows.size());
            assertTrue(rows.stream().allMatch(row -> (Long) row.getAs("n") == 2L));
            assertEquals(2d, rows.stream().mapToDouble(row -> ((Geometry) row.getAs("line")).getLength()).sum(), 1e-8);
        }
    }

    @Test
    void reconstructUsesOneOrderForCoordinatesAndFirstLastAndSkipsSingleObservations() {
        var source = reconstructPoints(List.of(
                RowFactory.create("a", 2L, time("2026-01-01 00:00:00"), 20d, "POINT (2 0)"),
                RowFactory.create("a", 1L, time("2026-01-01 00:00:00"), 10d, "POINT (1 0)"),
                RowFactory.create("a", 3L, time("2026-01-01 00:00:00"), 30d, "POINT (3 0)"),
                RowFactory.create("b", 1L, time("2026-01-01 00:00:00"), 99d, "POINT (100 0)")));
        var noGap = new TrackBoundaryConfiguration(null, null, null, null);
        var options = new TrackReconstructOptions(null, List.of("seq"), null, null);
        var issues = new RecordingIssueSink();
        var result = new TrackReconstructNodeOperator().apply(reconstructNode(options, noGap), Map.of("observations", source), context(issues));
        assertFalse(issues.hasErrors(), issues::toString);
        var rows = result.propagatedTables().get("rebuilt").dataset().collectAsList();
        assertEquals(1, rows.size());
        assertEquals(10d, (Double) rows.getFirst().getAs("first_value"));
        assertEquals(30d, (Double) rows.getFirst().getAs("last_value"));
        Geometry line = rows.getFirst().getAs("line");
        assertEquals(1d, line.getCoordinates()[0].x);
        assertEquals(3d, line.getCoordinates()[2].x);
        var ambiguous = new TrackReconstructNodeOperator().apply(reconstructNode(new TrackReconstructOptions(null, List.of(), null, null), noGap),
                Map.of("observations", source), context(new RecordingIssueSink()));
        var error = assertThrows(Exception.class, () -> ambiguous.propagatedTables().get("rebuilt").dataset().head());
        assertTrue(error.toString().contains("TRACK_OBSERVATION_ORDER_NOT_UNIQUE"));
    }

    @Test
    void reconstructRejectsInvalidSplitBeforePropagationAndPreservesInactiveDraft() {
        var source = reconstructPoints(List.of());
        var noGap = new TrackBoundaryConfiguration(null, null, null, null);
        for (String expression : List.of("1", "rand() > 0.5", "unknown_field > 1", "SELECT true", "lag(value) OVER (ORDER BY value) > 1")) {
            var issues = new RecordingIssueSink();
            var result = new TrackReconstructNodeOperator().apply(reconstructNode(new TrackReconstructOptions(null, List.of(), null,
                    new TrackSplitExpression(expression, List.of())), noGap), Map.of("observations", source), context(issues));
            assertTrue(issues.codes.contains("INVALID_TRACK_SPLIT_EXPRESSION"), expression + issues);
            assertTrue(result.propagatedTables().isEmpty());
        }
        var inactiveIssues = new RecordingIssueSink();
        var inactive = new TrackReconstructNodeOperator().apply(reconstructNode(new TrackReconstructOptions(null, List.of(), null,
                new TrackSplitExpression("", List.of(new TrackFieldWindowBinding("", "", null)), false)), noGap), Map.of("observations", source), context(inactiveIssues));
        assertFalse(inactiveIssues.hasErrors(), inactiveIssues::toString);
        assertEquals(0, inactive.propagatedTables().get("rebuilt").dataset().count());
    }

    @Test
    void reconstructGeodesicPathChangesOnlyGeometryNotObservationStatistics() {
        var source = geodesicTrack(List.of(
                RowFactory.create("A", 1L, Timestamp.valueOf("2026-01-01 00:00:00"), 10d, "POINT (170 60)"),
                RowFactory.create("A", 2L, Timestamp.valueOf("2026-01-01 00:01:00"), 20d, "POINT (-170 60)")));
        var options = new TrackReconstructOptions(null, List.of("seq"), null, null,
                new TrackPathGeometryOptions(null, 100d, SpatialDistanceUnit.KILOMETERS));
        var issues = new RecordingIssueSink();
        var result = new TrackReconstructNodeOperator().apply(reconstructPathNode(options, SpatialDistanceMethod.GEODESIC),
                Map.of("observations", source), context(issues));
        assertFalse(issues.hasErrors(), issues::toString);
        assertEquals(List.of("observations", "rebuilt"), new ArrayList<>(result.propagatedTables().keySet()));
        var table = result.propagatedTables().get("rebuilt");
        assertEquals(GeometryKind.MULTILINESTRING, table.schema().columns().getLast().geometry().kind());
        var row = table.dataset().first();
        assertEquals(2L, ((Number) row.getAs("n")).longValue());
        assertEquals(10d, ((Number) row.getAs("first_value")).doubleValue());
        assertEquals(20d, ((Number) row.getAs("last_value")).doubleValue());
        assertEquals(Timestamp.valueOf("2026-01-01 00:00:00"), row.getAs("start"));
        assertEquals(Timestamp.valueOf("2026-01-01 00:01:00"), row.getAs("end"));
        Geometry line = row.getAs("line");
        assertEquals("MultiLineString", line.getGeometryType());
        assertEquals(2, line.getNumGeometries());
        assertEquals(4326, line.getSRID());
        assertTrue(line.getNumPoints() > 10);
        var legacyIssues = new RecordingIssueSink();
        var legacy = new TrackReconstructNodeOperator().apply(reconstructPathNode(new TrackReconstructOptions(null, List.of(), null, null),
                SpatialDistanceMethod.GEODESIC), Map.of("observations", source), context(legacyIssues));
        assertFalse(legacyIssues.hasErrors(), legacyIssues::toString);
        Geometry old = legacy.propagatedTables().get("rebuilt").dataset().first().getAs("line");
        assertEquals("LineString", old.getGeometryType());
        assertEquals(2, old.getNumPoints());
    }

    @Test
    void reconstructPlanarMethodPathPreservesActualZAndIgnoresRetainedDensification() {
        var base = reconstructPoints(List.of(
                RowFactory.create("A", 1L, Timestamp.valueOf("2026-01-01 00:00:00"), 10d, "POINT (0 0)"),
                RowFactory.create("A", 2L, Timestamp.valueOf("2026-01-01 00:01:00"), 20d, "POINT (2 0)")));
        var columns = base.schema().columns().stream().map(column -> column.geometry() == null ? column :
                new CanvasColumnSchema(column.name(), column.fieldType(), null, null, null, column.nullable(), null, false, false, null,
                        new GeometryTypeDefinition(GeometryKind.POINT, column.geometry().crs(), CoordinateDimension.XYZ))).toList();
        var data = base.dataset().withColumn("shape", org.apache.spark.sql.sedona_sql.expressions.st_functions.ST_Force3D(
                org.apache.spark.sql.functions.col("shape"), org.apache.spark.sql.functions.lit(30d)));
        var source = new SparkCanvasTable(new CanvasTableSchema("observations", null, columns, CanvasDatasetKind.BOUNDED, null, null), data);
        var issues = new RecordingIssueSink();
        var options = new TrackReconstructOptions(null, List.of(), null, null, new TrackPathGeometryOptions(null, -1d, null));
        var result = new TrackReconstructNodeOperator().apply(reconstructPathNode(options, SpatialDistanceMethod.PLANAR), Map.of("observations", source), context(issues));
        assertFalse(issues.hasErrors(), issues::toString);
        var table = result.propagatedTables().get("rebuilt");
        Geometry line = table.dataset().first().getAs("line");
        assertEquals("MultiLineString", line.getGeometryType());
        assertEquals(2, line.getNumPoints());
        assertEquals(30d, line.getCoordinate().getZ());
        assertEquals(CoordinateDimension.XYZ, table.schema().columns().getLast().geometry().dimension());
        assertEquals(3857, line.getSRID());
    }

    @Test
    void reconstructRejectsInvalidActiveStepButPreservesInactiveDrafts() {
        var source = geodesicTrack(List.of());
        for (var path : List.of(new TrackPathGeometryOptions(null, null, null), new TrackPathGeometryOptions(null, 0d, SpatialDistanceUnit.METERS),
                new TrackPathGeometryOptions(null, Double.MAX_VALUE, SpatialDistanceUnit.KILOMETERS),
                new TrackPathGeometryOptions(null, 10d, SpatialDistanceUnit.SOURCE_CRS_UNIT))) {
            var issues = new RecordingIssueSink();
            var result = new TrackReconstructNodeOperator().apply(reconstructPathNode(new TrackReconstructOptions(null, List.of(), null, null, path),
                    SpatialDistanceMethod.GEODESIC), Map.of("observations", source), context(issues));
            assertTrue(issues.codes.contains("INVALID_TRACK_GEODESIC_SEGMENT_LENGTH"));
            assertTrue(result.propagatedTables().isEmpty());
        }
        var issues = new RecordingIssueSink();
        var result = new TrackReconstructNodeOperator().apply(reconstructPathNode(new TrackReconstructOptions(null, List.of(), null, null,
                new TrackPathGeometryOptions(TrackPathGeometryMode.LEGACY_VERTEX_LINE, -1d, null)), SpatialDistanceMethod.GEODESIC),
                Map.of("observations", source), context(issues));
        assertFalse(issues.hasErrors(), issues::toString);
        assertEquals(0, result.propagatedTables().get("rebuilt").dataset().count());
    }

    private SparkCanvasTable geodesicTrack(List<Row> rows) {
        return geometryTable("observations_raw", "observations", "shape", GeometryKind.POINT, 4326,
                List.of(stringColumn("track"), longColumn("seq"), timestampColumn("event_time"), doubleColumn("value"), stringColumn("wkt")), rows);
    }

    private TrackReconstructNodeDefinition reconstructPathNode(TrackReconstructOptions options, SpatialDistanceMethod method) {
        var c = reconstructNode(options, new TrackBoundaryConfiguration(null, null, null, null)).configuration();
        return new TrackReconstructNodeDefinition(id(), "路径重建", LAYOUT, new TrackReconstructConfiguration(c.sourceTableName(), c.pointGeometryColumnName(),
                c.trackIdColumns(), c.timeColumnName(), method, c.boundaries(), c.summaryStatistics(), c.outputTableName(),
                c.outputGeometryColumnName(), c.startTimeColumnName(), c.endTimeColumnName(), c.pointCountColumnName(), options));
    }

    private SparkCanvasTable reconstructPoints(List<Row> rows) {
        return geometryTable("observations_raw", "observations", "shape", GeometryKind.POINT, 3857,
                List.of(stringColumn("track"), longColumn("seq"), timestampColumn("event_time"), doubleColumn("value"), stringColumn("wkt")), rows);
    }

    @Test
    void unaryGeometryPoliciesPreserveActualDimensionsThroughSpark() {
        for (var dimension : CoordinateDimension.values()) {
            String tag = switch (dimension) { case XY -> ""; case XYZ -> " Z"; case XYM -> " M"; case XYZM -> " ZM"; };
            String extra = dimension == CoordinateDimension.XY ? "" : dimension == CoordinateDimension.XYZM ? " 7 9" : " 7";
            var source = unaryTable(dimension, 3857, "LINESTRING" + tag + " (0 0" + extra + ", 1 0.01" + extra + ", 2 0" + extra + ")");
            var issues = new RecordingIssueSink();
            var derive = new GeometryDeriveNodeDefinition(id(), "派生", LAYOUT, new GeometryDeriveConfiguration("unary", "derived", List.of(
                    new GeometryDerivation(id(), GeometryDeriveKind.CONVEX_HULL, "shape", "hull", GeometryUnaryPolicy.PRESERVE_DIMENSION),
                    new GeometryDerivation(id(), GeometryDeriveKind.BOUNDARY, "shape", "boundary", GeometryUnaryPolicy.PRESERVE_DIMENSION),
                    new GeometryDerivation(id(), GeometryDeriveKind.CENTROID, "shape", "center", GeometryUnaryPolicy.OUTPUT_XY))));
            var result = new GeometryDeriveNodeOperator().apply(derive, Map.of("unary", source), context(issues));
            assertFalse(issues.hasErrors(), issues::toString);
            var table = result.propagatedTables().get("derived");
            var row = table.dataset().first();
            Geometry center = row.getAs("center");
            assertTrue(Double.isNaN(center.getCoordinate().getZ()));
            assertTrue(Double.isNaN(center.getCoordinate().getM()));
            Geometry hull = row.getAs("hull");
            if (dimension == CoordinateDimension.XYZ || dimension == CoordinateDimension.XYZM) assertEquals(7d, hull.getCoordinate().getZ());
            if (dimension == CoordinateDimension.XYM || dimension == CoordinateDimension.XYZM) assertEquals(dimension == CoordinateDimension.XYM ? 7d : 9d, hull.getCoordinate().getM());
            assertEquals(CoordinateDimension.XY, table.schema().columns().getLast().geometry().dimension());
            var simplifyIssues = new RecordingIssueSink();
            var simplified = new GeometrySimplifyNodeOperator().apply(new GeometrySimplifyNodeDefinition(id(), "简化", LAYOUT,
                    new GeometrySimplifyConfiguration("unary", "shape", "simple", "result", GeometrySimplifyAlgorithm.TOPOLOGY_PRESERVING,
                            0.1, SpatialDistanceUnit.METERS, GeometryUnaryPolicy.PRESERVE_DIMENSION)), Map.of("unary", source), context(simplifyIssues));
            assertFalse(simplifyIssues.hasErrors(), simplifyIssues::toString);
            Geometry geometry = simplified.propagatedTables().get("simple").dataset().first().getAs("result");
            assertEquals(2, geometry.getNumPoints());
            if (dimension == CoordinateDimension.XYZ || dimension == CoordinateDimension.XYZM) assertEquals(7d, geometry.getCoordinate().getZ());
            if (dimension == CoordinateDimension.XYM || dimension == CoordinateDimension.XYZM) assertEquals(dimension == CoordinateDimension.XYM ? 7d : 9d, geometry.getCoordinate().getM());
            assertEquals(dimension, simplified.propagatedTables().get("simple").schema().columns().getLast().geometry().dimension());
        }
    }

    @Test
    void unaryDimensionRestrictionsAreCompileIssuesNotPartialResults() {
        var source = unaryTable(CoordinateDimension.XYZM, 3857, "POINT ZM (1 2 3 4)");
        for (var kind : List.of(GeometryDeriveKind.CENTROID, GeometryDeriveKind.POINT_ON_SURFACE, GeometryDeriveKind.ENVELOPE)) {
            var issues = new RecordingIssueSink();
            var node = new GeometryDeriveNodeDefinition(id(), "派生", LAYOUT, new GeometryDeriveConfiguration("unary", "derived", List.of(
                    new GeometryDerivation(id(), GeometryDeriveKind.CONVEX_HULL, "shape", "hull", GeometryUnaryPolicy.PRESERVE_DIMENSION),
                    new GeometryDerivation(id(), kind, "shape", "computed", GeometryUnaryPolicy.PRESERVE_DIMENSION))));
            assertTrue(new GeometryDeriveNodeOperator().apply(node, Map.of("unary", source), context(issues)).propagatedTables().isEmpty());
            assertTrue(issues.codes.contains("GEOMETRY_UNARY_DIMENSION_UNSUPPORTED"));
        }
        var issues = new RecordingIssueSink();
        var node = new GeometrySimplifyNodeDefinition(id(), "简化", LAYOUT, new GeometrySimplifyConfiguration("unary", "shape", "simple", "result",
                GeometrySimplifyAlgorithm.DOUGLAS_PEUCKER, 1d, SpatialDistanceUnit.METERS, GeometryUnaryPolicy.PRESERVE_DIMENSION));
        assertTrue(new GeometrySimplifyNodeOperator().apply(node, Map.of("unary", source), context(issues)).propagatedTables().isEmpty());
        assertTrue(issues.codes.contains("GEOMETRY_UNARY_DIMENSION_UNSUPPORTED"));
    }

    @Test
    void unaryInvalidInputFailsLazilyAndKeepsNullEmptyRowsAndStreamingSchema() {
        var invalid = unaryTable(CoordinateDimension.XY, 3857, "POLYGON ((0 0, 2 2, 0 2, 2 0, 0 0))");
        var node = new GeometryDeriveNodeDefinition(id(), "派生", LAYOUT, new GeometryDeriveConfiguration("unary", "derived", List.of(
                new GeometryDerivation(id(), GeometryDeriveKind.CENTROID, "shape", "center", GeometryUnaryPolicy.OUTPUT_XY))));
        var issues = new RecordingIssueSink();
        var planned = new GeometryDeriveNodeOperator().apply(node, Map.of("unary", invalid), context(issues));
        assertFalse(issues.hasErrors(), issues::toString);
        assertThrows(Exception.class, () -> planned.propagatedTables().get("derived").dataset().collectAsList());
        var empty = unaryTable(CoordinateDimension.XY, 3857, "POINT EMPTY");
        var data = empty.dataset().withColumn("event_time", org.apache.spark.sql.functions.current_timestamp());
        List<CanvasColumnSchema> columns = new ArrayList<>(empty.schema().columns()); columns.add(timestampColumn("event_time"));
        var stream = new SparkCanvasTable(new CanvasTableSchema("unary", null, columns, CanvasDatasetKind.UNBOUNDED, "event_time", "10 seconds"), data);
        var emptyIssues = new RecordingIssueSink();
        var table = new GeometryDeriveNodeOperator().apply(node, Map.of("unary", stream), context(emptyIssues)).propagatedTables().get("derived");
        assertEquals(CanvasDatasetKind.UNBOUNDED, table.schema().datasetKind());
        assertEquals("event_time", table.schema().eventTimeColumn()); assertEquals("10 seconds", table.schema().watermarkDelay());
        Geometry result = table.dataset().first().getAs("center"); assertTrue(result.isEmpty());
        var nullData = empty.dataset().withColumn("shape", org.apache.spark.sql.functions.lit(null).cast(empty.dataset().schema().apply("shape").dataType()));
        var nullIssues = new RecordingIssueSink();
        var nullResult = new GeometryDeriveNodeOperator().apply(node, Map.of("unary", new SparkCanvasTable(empty.schema(), nullData)), context(nullIssues));
        assertNull(nullResult.propagatedTables().get("derived").dataset().first().getAs("center"));
    }

    @Test
    void simplifyRejectsToleranceUnderflowBeforeAnySparkJob() {
        var source = unaryTable(CoordinateDimension.XY, 3857, "LINESTRING (0 0, 1 1, 2 0)");
        String group = "simplify-underflow-" + id();
        spark.sparkContext().setJobGroup(group, "simplify underflow preflight", false);
        try {
            for (var policy : GeometryUnaryPolicy.values()) {
                var issues = new RecordingIssueSink();
                var result = new GeometrySimplifyNodeOperator().apply(new GeometrySimplifyNodeDefinition(id(), "简化", LAYOUT,
                        new GeometrySimplifyConfiguration("unary", "shape", "simple", "result", GeometrySimplifyAlgorithm.DOUGLAS_PEUCKER,
                                Double.MIN_VALUE, SpatialDistanceUnit.FEET, policy)), Map.of("unary", source), context(issues));
                assertTrue(issues.codes.contains("INVALID_GEOMETRY_SIMPLIFY_TOLERANCE"), issues::toString);
                assertTrue(issues.errorPaths.contains("configuration.tolerance"));
                assertTrue(result.propagatedTables().isEmpty());
            }
            assertEquals(0, spark.sparkContext().statusTracker().getJobIdsForGroup(group).length);
        } finally {
            spark.sparkContext().clearJobGroup();
        }
    }

    @Test
    void simplifyEquivalentUnitsProduceTheSameGeometryInSourceCoordinates() {
        for (int epsg : new int[]{3857, 2263}) {
            var source = unaryTable(CoordinateDimension.XY, epsg, "LINESTRING (0 0, 1 0.1, 2 -0.1, 3 0)");
            for (var algorithm : GeometrySimplifyAlgorithm.values()) {
                Geometry expected = null;
                for (var unit : SpatialDistanceUnit.values()) {
                    double factor = SpatialDistanceSupport.sourceUnitsPerConfiguredUnit(unit, new CrsReference("EPSG", epsg)).sourceCrsValue();
                    double configured = 0.2 / factor;
                    var issues = new RecordingIssueSink();
                    var result = new GeometrySimplifyNodeOperator().apply(new GeometrySimplifyNodeDefinition(id(), "简化", LAYOUT,
                            new GeometrySimplifyConfiguration("unary", "shape", "simple", "result", algorithm,
                                    configured, unit, GeometryUnaryPolicy.PRESERVE_DIMENSION)), Map.of("unary", source), context(issues));
                    assertFalse(issues.hasErrors(), issues::toString);
                    var row = result.propagatedTables().get("simple").dataset().first();
                    Geometry geometry = row.getAs("result");
                    if (expected == null) expected = geometry;
                    assertTrue(expected.equalsExact(geometry), epsg + " " + algorithm + " " + unit);
                    assertEquals(2, geometry.getNumPoints());
                    assertEquals(epsg, geometry.getSRID());
                    assertEquals(4, ((Geometry) row.getAs("shape")).getNumPoints());
                    assertTrue(result.propagatedTables().get("unary") == source);
                }
            }
        }
    }

    @Test
    void simplifyAngularToleranceUsesActualSourceUnitsAndRetainsRows() {
        for (int epsg : new int[]{4326, 4490, 4807}) {
            var source = unaryTable(CoordinateDimension.XY, epsg, "LINESTRING (0 0, 1 0.1, 2 -0.1, 3 0)");
            for (var algorithm : GeometrySimplifyAlgorithm.values()) {
                var issues = new RecordingIssueSink();
                var result = new GeometrySimplifyNodeOperator().apply(new GeometrySimplifyNodeDefinition(id(), "简化", LAYOUT,
                        new GeometrySimplifyConfiguration("unary", "shape", "simple", "result", algorithm,
                                0.2, SpatialDistanceUnit.SOURCE_CRS_UNIT, GeometryUnaryPolicy.OUTPUT_XY)), Map.of("unary", source), context(issues));
                assertFalse(issues.hasErrors(), issues::toString);
                assertTrue(issues.codes.contains("GEOMETRY_SIMPLIFY_USES_ANGULAR_UNITS"));
                var rows = result.propagatedTables().get("simple").dataset().collectAsList();
                assertEquals(1, rows.size());
                Geometry geometry = rows.getFirst().getAs("result");
                assertEquals(2, geometry.getNumPoints());
                assertEquals(3d, geometry.getCoordinates()[1].x);
                assertEquals(epsg, geometry.getSRID());
            }
        }
    }

    @Test
    void simplifyRejectsInvalidToleranceButAcceptsRepresentablePositiveValues() {
        var source = unaryTable(CoordinateDimension.XY, 3857, "LINESTRING (0 0, 1 1, 2 0)");
        for (Double value : new Double[]{null, 0d, -1d, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            var issues = new RecordingIssueSink();
            var result = new GeometrySimplifyNodeOperator().apply(new GeometrySimplifyNodeDefinition(id(), "简化", LAYOUT,
                    new GeometrySimplifyConfiguration("unary", "shape", "simple", "result", GeometrySimplifyAlgorithm.TOPOLOGY_PRESERVING,
                            value, SpatialDistanceUnit.METERS, GeometryUnaryPolicy.PRESERVE_DIMENSION)), Map.of("unary", source), context(issues));
            assertTrue(result.propagatedTables().isEmpty());
            assertTrue(issues.codes.contains("INVALID_GEOMETRY_SIMPLIFY_TOLERANCE"), issues::toString);
            assertTrue(issues.errorPaths.contains("configuration.tolerance"));
        }
        var overflowIssues = new RecordingIssueSink();
        var overflow = new GeometrySimplifyNodeOperator().apply(new GeometrySimplifyNodeDefinition(id(), "简化", LAYOUT,
                new GeometrySimplifyConfiguration("unary", "shape", "simple", "result", GeometrySimplifyAlgorithm.TOPOLOGY_PRESERVING,
                        Double.MAX_VALUE, SpatialDistanceUnit.KILOMETERS, GeometryUnaryPolicy.PRESERVE_DIMENSION)), Map.of("unary", source), context(overflowIssues));
        assertTrue(overflow.propagatedTables().isEmpty());
        assertTrue(overflowIssues.codes.contains("SPATIAL_DISTANCE_UNIT_UNSUPPORTED"));
        var tinyIssues = new RecordingIssueSink();
        var tiny = new GeometrySimplifyNodeOperator().apply(new GeometrySimplifyNodeDefinition(id(), "简化", LAYOUT,
                new GeometrySimplifyConfiguration("unary", "shape", "simple", "result", GeometrySimplifyAlgorithm.TOPOLOGY_PRESERVING,
                        Double.MIN_VALUE, SpatialDistanceUnit.METERS, GeometryUnaryPolicy.PRESERVE_DIMENSION)), Map.of("unary", source), context(tinyIssues));
        assertFalse(tinyIssues.hasErrors(), tinyIssues::toString);
        assertTrue(tiny.propagatedTables().containsKey("simple"));
    }

    private SparkCanvasTable unaryTable(CoordinateDimension dimension, int epsg, String wkt) {
        var base = table(new CanvasTableSchema("unary", null, List.of(longColumn("id"), stringColumn("wkt")), CanvasDatasetKind.BOUNDED, null, null), List.of(RowFactory.create(1L, wkt)));
        var data = base.dataset().withColumn("shape", org.apache.spark.sql.sedona_sql.expressions.st_functions.ST_SetSRID(
                org.apache.spark.sql.functions.expr("ST_GeomFromWKT(wkt)"), org.apache.spark.sql.functions.lit(epsg)));
        List<CanvasColumnSchema> columns = new ArrayList<>(base.schema().columns());
        columns.add(new CanvasColumnSchema("shape", PlatformDataType.GEOMETRY, null, null, null, true, null, false, false, null,
                new GeometryTypeDefinition(GeometryKind.GEOMETRY, new CrsReference("EPSG", epsg), dimension)));
        return new SparkCanvasTable(new CanvasTableSchema("unary", null, columns, CanvasDatasetKind.BOUNDED, null, null), data);
    }

    @Test
    void exactNearestRecoversEveryTieAndKeepsCoincidentSourcesIndependent() {
        var source = geometryTable("source_raw", "source", "shape", GeometryKind.POINT, 3857,
                List.of(longColumn("id"), stringColumn("wkt")), List.of(RowFactory.create(1L, "POINT (0 0)"), RowFactory.create(2L, "POINT (0 0)")));
        var candidates = geometryTable("candidate_raw", "candidate", "shape", GeometryKind.POINT, 3857,
                List.of(longColumn("id"), stringColumn("wkt")), List.of(RowFactory.create(30L, "POINT (1 0)"), RowFactory.create(20L, "POINT (0 1)"), RowFactory.create(10L, "POINT (-1 0)")));
        var issues = new RecordingIssueSink();
        var result = new SpatialNearestNodeOperator().apply(exactNearest(SpatialDistanceMethod.PLANAR, 1, null, true, true),
                tables(source, candidates), context(issues));
        assertFalse(issues.hasErrors(), issues::toString);
        var rows = result.propagatedTables().get("nearest").dataset().orderBy("source_id").collectAsList();
        assertEquals(2, rows.size());
        for (Row row : rows) { assertEquals(10L, (Long) row.getAs("candidate_id")); assertEquals(1, (Integer) row.getAs("rank")); }
        var lines = result.propagatedTables().get("connections").dataset().collectAsList();
        assertEquals(2, lines.size());
        for (Row row : lines) {
            Geometry line = row.getAs("connection");
            assertEquals("MultiLineString", line.getGeometryType());
            assertEquals(1d, line.getLength(), 1e-9);
        }
        String plan = result.propagatedTables().get("nearest").dataset().queryExecution().executedPlan().toString();
        assertTrue(plan.contains("KNN") || plan.contains("Knn"), plan);
        assertTrue(plan.contains("DistanceJoin") || plan.contains("BroadcastIndexJoin"), plan);
        assertFalse(plan.contains("CartesianProduct"), plan);
    }

    @Test
    void exactNearestRadiusAndUnmatchedHaveOneSharedMatchingRelation() {
        var source = geometryTable("source_raw", "source", "shape", GeometryKind.POINT, 3857,
                List.of(longColumn("id"), stringColumn("wkt")), List.of(RowFactory.create(1L, "POINT (0 0)"), RowFactory.create(2L, "POINT (100 100)")));
        var candidate = geometryTable("candidate_raw", "candidate", "shape", GeometryKind.LINESTRING, 3857,
                List.of(longColumn("id"), stringColumn("wkt")), List.of(RowFactory.create(10L, "LINESTRING (2 -100, 2 100)")));
        var issues = new RecordingIssueSink();
        var result = new SpatialNearestNodeOperator().apply(exactNearest(SpatialDistanceMethod.PLANAR, 3, 2d, true, true), tables(source, candidate), context(issues));
        assertFalse(issues.hasErrors(), issues::toString);
        var rows = result.propagatedTables().get("nearest").dataset().orderBy("source_id").collectAsList();
        assertEquals(2, rows.size()); assertEquals(2d, ((Number) rows.getFirst().getAs("distance")).doubleValue());
        assertNull(rows.getLast().getAs("distance")); assertNull(rows.getLast().getAs("candidate_id"));
        var lines = result.propagatedTables().get("connections").dataset().collectAsList();
        assertEquals(1, lines.size());
        Geometry line = lines.getFirst().getAs("connection");
        assertEquals(2d, line.getLength(), 1e-9); assertEquals(0d, line.getCoordinates()[1].y, 1e-9);
        assertEquals(List.of("source", "candidate", "nearest", "connections"), new ArrayList<>(result.propagatedTables().keySet()));
        String plan = result.propagatedTables().get("nearest").dataset().queryExecution().executedPlan().toString();
        assertTrue(plan.contains("DistanceJoin") || plan.contains("BroadcastIndexJoin"), plan);
        assertFalse(plan.contains("CartesianProduct") || plan.contains("BroadcastNestedLoopJoin"), plan);
    }

    @Test
    void exactNearestGeodesicDatelineDistancesAgreeWithConnectionSegments() {
        var source = nearestTable("source", 4326, GeometryKind.POINT, List.of(RowFactory.create(1L, "POINT (179 30)")));
        var candidate = nearestTable("candidate", 4326, GeometryKind.POINT,
                List.of(RowFactory.create(10L, "POINT (-179 30)"), RowFactory.create(20L, "POINT (170 30)")));
        for (Double radius : new Double[]{250000d, null}) {
            var issues = new RecordingIssueSink();
            var result = new SpatialNearestNodeOperator().apply(exactNearest(SpatialDistanceMethod.GEODESIC, 1, radius, true, true), tables(source, candidate), context(issues));
            assertFalse(issues.hasErrors(), issues::toString);
            var row = result.propagatedTables().get("nearest").dataset().head();
            assertEquals(10L, (Long) row.getAs("candidate_id"));
            double expected = net.sf.geographiclib.Geodesic.WGS84.Inverse(30, 179, 30, -179).s12;
            assertEquals(expected, ((Number) row.getAs("distance")).doubleValue(), 1e-6);
            Geometry line = result.propagatedTables().get("connections").dataset().head().getAs("connection");
            assertEquals(2, line.getNumGeometries());
            double length = 0;
            for (int part = 0; part < line.getNumGeometries(); part++) {
                var coordinates = line.getGeometryN(part).getCoordinates();
                for (int i = 1; i < coordinates.length; i++) {
                    assertTrue(Math.abs(coordinates[i].x - coordinates[i - 1].x) < 180);
                    length += net.sf.geographiclib.Geodesic.WGS84.Inverse(coordinates[i - 1].y, coordinates[i - 1].x, coordinates[i].y, coordinates[i].x).s12;
                }
            }
            assertEquals(expected, length, 1e-5);
        }
    }

    @Test
    void exactNearestRetainsNullAndEmptySourcesWithoutInventingConnections() {
        var source = nearestTable("source", 3857, GeometryKind.POINT,
                List.of(RowFactory.create(1L, null), RowFactory.create(2L, "POINT EMPTY"), RowFactory.create(3L, "POINT (0 0)")));
        for (var candidates : List.of(List.<Row>of(), List.of(RowFactory.create(10L, null)), List.of(RowFactory.create(10L, "POINT (0 0)")))) {
            var candidate = nearestTable("candidate", 3857, GeometryKind.POINT, candidates);
            var issues = new RecordingIssueSink();
            var result = new SpatialNearestNodeOperator().apply(exactNearest(SpatialDistanceMethod.PLANAR, 2, 10d, true, true), tables(source, candidate), context(issues));
            assertFalse(issues.hasErrors(), issues::toString);
            var rows = result.propagatedTables().get("nearest").dataset().orderBy("source_id").collectAsList();
            assertEquals(3, rows.size()); assertNull(rows.getFirst().getAs("distance")); assertNull(rows.get(1).getAs("distance"));
            var lines = result.propagatedTables().get("connections").dataset().collectAsList();
            assertEquals(candidates.size() == 1 && candidates.getFirst().get(1) != null ? 1 : 0, lines.size());
            if (!lines.isEmpty()) assertEquals(0d, ((Geometry) lines.getFirst().getAs("connection")).getLength());
        }
    }

    @Test
    void exactNearestRejectsRealDuplicateAndNullIdsLazily() {
        for (boolean badSource : new boolean[]{true, false}) {
            for (List<Row> bad : List.of(List.of(RowFactory.create(1L, "POINT (0 0)"), RowFactory.create(1L, "POINT (1 0)")), List.of(RowFactory.create(null, "POINT (0 0)")))) {
                var good = List.of(RowFactory.create(10L, "POINT (0 0)"));
                var source = nearestTable("source", 3857, GeometryKind.POINT, badSource ? bad : good);
                var candidate = nearestTable("candidate", 3857, GeometryKind.POINT, badSource ? good : bad);
                var issues = new RecordingIssueSink();
                var result = new SpatialNearestNodeOperator().apply(exactNearest(SpatialDistanceMethod.PLANAR, 1, 10d, true, false), tables(source, candidate), context(issues));
                assertFalse(issues.hasErrors(), issues::toString);
                var failure = assertThrows(Exception.class, () -> result.propagatedTables().get("nearest").dataset().collectAsList());
                assertTrue(failure.toString().contains(badSource ? "SPATIAL_NEAREST_SOURCE_ID_INVALID" : "SPATIAL_NEAREST_CANDIDATE_ID_INVALID"), failure::toString);
            }
        }
    }

    @Test
    void exactNearestRejectsNonpointGeodesicInSchemaAndAtRuntime() {
        var source = nearestTable("source", 4326, GeometryKind.POINT, List.of(RowFactory.create(1L, "POINT (0 0)")));
        for (GeometryKind kind : List.of(GeometryKind.LINESTRING, GeometryKind.GEOMETRY)) {
            var candidate = nearestTable("candidate", 4326, kind, List.of(RowFactory.create(10L, "LINESTRING (0 0, 1 1)")));
            var issues = new RecordingIssueSink();
            var result = new SpatialNearestNodeOperator().apply(exactNearest(SpatialDistanceMethod.GEODESIC, 1, 10d, true, false), tables(source, candidate), context(issues));
            if (kind == GeometryKind.LINESTRING) assertTrue(issues.toString().contains("GEODESIC_NEAREST_REQUIRES_POINTS"));
            else {
                assertFalse(issues.hasErrors(), issues::toString);
                assertTrue(assertThrows(Exception.class, () -> result.propagatedTables().get("nearest").dataset().collectAsList()).toString().contains("GEODESIC_NEAREST_REQUIRES_POINTS"));
            }
        }
    }

    @Test
    void exactNearestWorksWhenInputsShareOneDatasetAndNamesResembleInternalFields() {
        var source = nearestTable("source", 3857, GeometryKind.POINT, List.of(RowFactory.create(1L, "POINT (0 0)"), RowFactory.create(2L, "POINT (1 1)")));
        var candidate = new SparkCanvasTable(new CanvasTableSchema("candidate", null, source.schema().columns(), CanvasDatasetKind.BOUNDED, null, null), source.dataset());
        var issues = new RecordingIssueSink();
        var result = new SpatialNearestNodeOperator().apply(exactNearest(SpatialDistanceMethod.PLANAR, 1, null, false, false), tables(source, candidate), context(issues));
        assertFalse(issues.hasErrors(), issues::toString);
        var rows = result.propagatedTables().get("nearest").dataset().collectAsList();
        assertEquals(2, rows.size());
        for (Row row : rows) { assertEquals((Long) row.getAs("source_id"), (Long) row.getAs("candidate_id")); assertEquals(0d, ((Number) row.getAs("distance")).doubleValue()); }
    }

    @Test
    void exactNearestValidatesActiveConnectionsAndPreservesInactiveDrafts() {
        var source = nearestTable("source", 4326, GeometryKind.POINT, List.of());
        var candidate = nearestTable("candidate", 4326, GeometryKind.POINT, List.of());
        var valid = exactNearest(SpatialDistanceMethod.GEODESIC, 1, 10d, true, true).configuration();
        var options = List.of(
                new SpatialNearestMatching(null, "", valid.matching().connectionLines()),
                new SpatialNearestMatching(null, "missing", valid.matching().connectionLines()),
                new SpatialNearestMatching(null, "id", new SpatialNearestConnectionLines(true, "source", "connection", 10d, SpatialDistanceUnit.KILOMETERS)),
                new SpatialNearestMatching(null, "id", new SpatialNearestConnectionLines(true, "connections", "DISTANCE", 10d, SpatialDistanceUnit.KILOMETERS)),
                new SpatialNearestMatching(null, "id", new SpatialNearestConnectionLines(true, "", "", null, null)),
                new SpatialNearestMatching(null, "id", new SpatialNearestConnectionLines(true, "connections", "connection", 10d, SpatialDistanceUnit.SOURCE_CRS_UNIT)));
        for (var matching : options) {
            var issues = new RecordingIssueSink();
            var result = new SpatialNearestNodeOperator().apply(withNearestMatching(valid, matching), tables(source, candidate), context(issues));
            assertTrue(issues.hasErrors()); assertTrue(result.propagatedTables().isEmpty());
            assertTrue(issues.errorPaths.stream().anyMatch(path -> path.startsWith("configuration.matching")), issues::toString);
        }
        for (var matching : List.of(
                new SpatialNearestMatching(null, "id", new SpatialNearestConnectionLines(false, "source", "distance", -1d, null)),
                new SpatialNearestMatching(SpatialNearestMatchSemantics.LEGACY_KNN, "missing", new SpatialNearestConnectionLines(true, "source", "distance", -1d, null)))) {
            var issues = new RecordingIssueSink();
            var result = new SpatialNearestNodeOperator().apply(withNearestMatching(valid, matching), tables(source, candidate), context(issues));
            assertFalse(issues.hasErrors(), issues::toString);
            assertEquals(List.of("source", "candidate", "nearest"), new ArrayList<>(result.propagatedTables().keySet()));
        }
    }

    @Test
    void exactNearestPreservesProjectedEmptyGeometryOnUnmatchedSources() {
        var source = nearestTable("source", 3857, GeometryKind.POINT, List.of(RowFactory.create(1L, "POINT EMPTY")));
        var candidate = nearestTable("candidate", 3857, GeometryKind.POINT, List.of(RowFactory.create(10L, "POINT (0 0)")));
        var c = exactNearest(SpatialDistanceMethod.PLANAR, 1, 10d, true, false).configuration();
        var columns = new ArrayList<>(c.outputColumns()); columns.add(output(JoinOutputColumnSource.LEFT, "shape", "source_shape"));
        var node = new SpatialNearestNodeDefinition(id(), "最近邻", LAYOUT, new SpatialNearestConfiguration(c.sourceTableName(), c.sourceGeometryColumnName(),
                c.candidateTableName(), c.candidateGeometryColumnName(), c.candidateIdColumnName(), c.distanceMethod(), c.nearestCount(), c.maximumDistance(),
                c.maximumDistanceUnit(), c.includeUnmatched(), c.outputTableName(), c.distanceColumnName(), c.distanceOutputUnit(), c.rankColumnName(), columns, c.matching()));
        var issues = new RecordingIssueSink();
        var result = new SpatialNearestNodeOperator().apply(node, tables(source, candidate), context(issues));
        assertFalse(issues.hasErrors(), issues::toString);
        Row row = result.propagatedTables().get("nearest").dataset().head();
        assertNotNull(row.getAs("source_shape")); assertTrue(((Geometry) row.getAs("source_shape")).isEmpty());
        assertNull(row.getAs("candidate_id"));
    }

    private SpatialNearestNodeDefinition withNearestMatching(SpatialNearestConfiguration c, SpatialNearestMatching matching) {
        return new SpatialNearestNodeDefinition(id(), "最近邻", LAYOUT, new SpatialNearestConfiguration(c.sourceTableName(), c.sourceGeometryColumnName(),
                c.candidateTableName(), c.candidateGeometryColumnName(), c.candidateIdColumnName(), c.distanceMethod(), c.nearestCount(), c.maximumDistance(),
                c.maximumDistanceUnit(), c.includeUnmatched(), c.outputTableName(), c.distanceColumnName(), c.distanceOutputUnit(), c.rankColumnName(), c.outputColumns(), matching));
    }

    @Test
    void centerSeparateResultsPreserveOriginalLinesAndNativeIdTieOrder() {
        var source = nearestTable("source", 3857, GeometryKind.LINESTRING, List.of(
                RowFactory.create(10L, "LINESTRING (0 0, 2 0)"), RowFactory.create(2L, "LINESTRING (0 -1, 2 1)")));
        var analyses = List.of(new SpatialCenterDispersionAnalysis(id(), SpatialCenterDispersionKind.MEAN_CENTER, "shape", null, "mean"),
                new SpatialCenterDispersionAnalysis(id(), SpatialCenterDispersionKind.MEDIAN_CENTER, "shape", null, "median"),
                new SpatialCenterDispersionAnalysis(id(), SpatialCenterDispersionKind.CENTRAL_FEATURE, "shape", null, "central"),
                new SpatialCenterDispersionAnalysis(id(), SpatialCenterDispersionKind.DIRECTIONAL_ELLIPSE, "shape", 1, "ellipse"));
        var node = new SpatialCenterDispersionNodeDefinition(id(), "中心", LAYOUT, new SpatialCenterDispersionConfiguration("source", "shape", "id", List.of(), null, analyses, "ignored", SpatialCenterResultMode.ANALYSIS_TABLES));
        var issues = new RecordingIssueSink();
        var result = new SpatialCenterDispersionNodeOperator().apply(node, Map.of("source", source), context(issues));
        assertFalse(issues.hasErrors(), issues::toString);
        assertEquals(List.of("source", "mean", "median", "central", "ellipse"), new ArrayList<>(result.propagatedTables().keySet()));
        Row selected = result.propagatedTables().get("central").dataset().head();
        assertEquals(2L, (Long) selected.getAs("id")); assertEquals("LINESTRING (0 -1, 2 1)", ((Geometry) selected.getAs("shape")).toText());
        assertEquals(GeometryKind.LINESTRING, result.propagatedTables().get("central").schema().columns().getLast().geometry().kind());
        for (String name : List.of("mean", "median")) assertEquals("POINT (1 0)", ((Geometry) result.propagatedTables().get(name).dataset().head().getAs("shape")).toText());
        assertTrue(((Geometry) result.propagatedTables().get("ellipse").dataset().head().getAs("shape")).isEmpty());
        assertTrue(source == result.propagatedTables().get("source"));
    }

    @Test
    void centerSeparateMeanAndMedianDoNotRequireAnUnusedFeatureId() {
        var source = nearestTable("source", 3857, GeometryKind.POINT, List.of(RowFactory.create(1L, "POINT (0 0)"), RowFactory.create(2L, "POINT (2 0)"), RowFactory.create(3L, "POINT (0 2)")));
        var analyses = List.of(new SpatialCenterDispersionAnalysis(id(), SpatialCenterDispersionKind.MEDIAN_CENTER, "median", null, "result"));
        var node = new SpatialCenterDispersionNodeDefinition(id(), "中心", LAYOUT, new SpatialCenterDispersionConfiguration("source", "shape", "missing", List.of(), null, analyses, "", SpatialCenterResultMode.ANALYSIS_TABLES));
        var issues = new RecordingIssueSink(); var result = new SpatialCenterDispersionNodeOperator().apply(node, Map.of("source", source), context(issues));
        assertFalse(issues.hasErrors(), issues::toString);
        var median = (org.locationtech.jts.geom.Point) result.propagatedTables().get("result").dataset().head().getAs("median");
        assertEquals(1 - 1 / Math.sqrt(3), median.getX(), 1e-8);
    }

    @Test
    void centerSeparateWeightsNullGroupsAndEmptyInputsHaveDefinedResults() {
        var original = nearestTable("source", 3857, GeometryKind.POINT, List.of(RowFactory.create(1L, "POINT (0 0)"), RowFactory.create(2L, "POINT (4 0)"), RowFactory.create(3L, null)));
        var schema = new ArrayList<>(original.schema().columns());
        schema.add(new CanvasColumnSchema("weight", PlatformDataType.DOUBLE, null, null, null, true, null, false, false, null));
        schema.add(new CanvasColumnSchema("__datascalpel_center_points", PlatformDataType.STRING, null, null, null, true, null, false, false, null));
        for (String weights : List.of("CASE WHEN id=1 THEN 1D WHEN id=2 THEN 3D ELSE CAST(NULL AS DOUBLE) END", "0D", "CAST(NULL AS DOUBLE)", "-1D")) {
            var data = original.dataset().withColumn("weight", org.apache.spark.sql.functions.expr(weights))
                    .withColumn("__datascalpel_center_points", org.apache.spark.sql.functions.lit(null).cast("string"));
            var source = new SparkCanvasTable(new CanvasTableSchema("source", null, schema, CanvasDatasetKind.BOUNDED, null, null), data);
            var node = new SpatialCenterDispersionNodeDefinition(id(), "中心", LAYOUT, new SpatialCenterDispersionConfiguration("source", "shape", null,
                    List.of("__datascalpel_center_points"), "weight", List.of(new SpatialCenterDispersionAnalysis(id(), SpatialCenterDispersionKind.MEAN_CENTER, "center", null, "result")), "", SpatialCenterResultMode.ANALYSIS_TABLES));
            var issues = new RecordingIssueSink(); var result = new SpatialCenterDispersionNodeOperator().apply(node, Map.of("source", source), context(issues));
            assertFalse(issues.hasErrors(), issues::toString);
            if (weights.equals("-1D")) assertTrue(assertThrows(Exception.class, () -> result.propagatedTables().get("result").dataset().collectAsList()).toString().contains("SPATIAL_CENTER_WEIGHT_INVALID"));
            else {
                var rows = result.propagatedTables().get("result").dataset().collectAsList();
                if (weights.startsWith("CASE")) { assertEquals(1, rows.size()); assertEquals("POINT (3 0)", ((Geometry) rows.getFirst().getAs("center")).toText()); assertNull(rows.getFirst().getAs("__datascalpel_center_points")); }
                else assertTrue(rows.isEmpty());
            }
        }
        var source = nearestTable("source", 3857, GeometryKind.POINT, List.of());
        var node = centerForTest(List.of(new SpatialCenterDispersionAnalysis(id(), SpatialCenterDispersionKind.MEAN_CENTER, "shape", null, "result")));
        var issues = new RecordingIssueSink(); var result = new SpatialCenterDispersionNodeOperator().apply(node, Map.of("source", source), context(issues));
        assertFalse(issues.hasErrors(), issues::toString); assertEquals(0, result.propagatedTables().get("result").dataset().count());
    }

    @Test
    void centerSeparateInvalidIdsAndExcessiveGroupsFailLazily() {
        var node = centerForTest(List.of(new SpatialCenterDispersionAnalysis(id(), SpatialCenterDispersionKind.CENTRAL_FEATURE, "shape", null, "result")));
        for (List<Row> rows : List.of(List.of(RowFactory.create(1L, "POINT (0 0)"), RowFactory.create(1L, "POINT (1 0)")), List.of(RowFactory.create(null, "POINT (0 0)")))) {
            var source = nearestTable("source", 3857, GeometryKind.POINT, rows);
            var issues = new RecordingIssueSink(); var result = new SpatialCenterDispersionNodeOperator().apply(node, Map.of("source", source), context(issues));
            assertFalse(issues.hasErrors(), issues::toString);
            assertTrue(assertThrows(Exception.class, () -> result.propagatedTables().get("result").dataset().collectAsList()).toString().contains("SPATIAL_CENTER_FEATURE_ID_INVALID"));
        }
        var rows = new ArrayList<Row>(); for (long i = 0; i <= CenterGeometryStatistics.MAX_CENTRAL_FEATURES; i++) rows.add(RowFactory.create(i, "POINT (0 0)"));
        var source = nearestTable("source", 3857, GeometryKind.POINT, rows);
        var issues = new RecordingIssueSink(); var result = new SpatialCenterDispersionNodeOperator().apply(node, Map.of("source", source), context(issues));
        assertFalse(issues.hasErrors(), issues::toString);
        assertTrue(assertThrows(Exception.class, () -> result.propagatedTables().get("result").dataset().collectAsList()).toString().contains("SPATIAL_CENTER_GROUP_LIMIT_EXCEEDED"));
    }

    @Test
    void centerSeparateChecksEveryOutputBeforePropagatingAnyResult() {
        var source = nearestTable("source", 3857, GeometryKind.POINT, List.of());
        for (String target : List.of("source", "result", "")) {
            var node = centerForTest(List.of(new SpatialCenterDispersionAnalysis(id(), SpatialCenterDispersionKind.MEAN_CENTER, "shape", null, "result"),
                    new SpatialCenterDispersionAnalysis(id(), SpatialCenterDispersionKind.MEDIAN_CENTER, "shape", null, target)));
            var issues = new RecordingIssueSink(); var result = new SpatialCenterDispersionNodeOperator().apply(node, Map.of("source", source), context(issues));
            assertTrue(issues.hasErrors()); assertTrue(result.propagatedTables().isEmpty());
            assertTrue(issues.errorPaths.contains("configuration.analyses[1].outputTableName"));
        }
    }

    @Test
    void centerZeroWeightOutliersStayCandidatesWithoutDistortingAnyAnalysis() {
        var original = nearestTable("source", 3857, GeometryKind.POINT, List.of(
                RowFactory.create(0L, "POINT (1e200 1e200)"), RowFactory.create(1L, "POINT (0 0)"),
                RowFactory.create(2L, "POINT (2 0)"), RowFactory.create(3L, "POINT (2 2)"),
                RowFactory.create(4L, "POINT (0 2)"), RowFactory.create(5L, "POINT (1 1)")));
        var columns = new ArrayList<>(original.schema().columns());
        columns.add(doubleColumn("weight"));
        var data = original.dataset().withColumn("weight", org.apache.spark.sql.functions.expr("CASE WHEN id BETWEEN 1 AND 4 THEN 1D ELSE 0D END")).repartition(3);
        var source = new SparkCanvasTable(new CanvasTableSchema("source", null, columns, CanvasDatasetKind.BOUNDED, null, null), data);
        var analyses = List.of(
                new SpatialCenterDispersionAnalysis(id(), SpatialCenterDispersionKind.MEAN_CENTER, "shape", null, "mean"),
                new SpatialCenterDispersionAnalysis(id(), SpatialCenterDispersionKind.MEDIAN_CENTER, "shape", null, "median"),
                new SpatialCenterDispersionAnalysis(id(), SpatialCenterDispersionKind.DIRECTIONAL_ELLIPSE, "shape", 1, "ellipse"),
                new SpatialCenterDispersionAnalysis(id(), SpatialCenterDispersionKind.STANDARD_DISTANCE, "shape", 1, "distance"),
                new SpatialCenterDispersionAnalysis(id(), SpatialCenterDispersionKind.CENTRAL_FEATURE, "shape", null, "central",
                        List.of(new SpatialCenterFeatureColumn("id", "selected_id", true), new SpatialCenterFeatureColumn("weight", "selected_weight", true))));
        var node = new SpatialCenterDispersionNodeDefinition(id(), "中心", LAYOUT,
                new SpatialCenterDispersionConfiguration("source", "shape", "id", List.of(), "weight", analyses, "", SpatialCenterResultMode.ANALYSIS_TABLES));
        var issues = new RecordingIssueSink();
        String group = "center-zero-weight-" + id();
        spark.sparkContext().setJobGroup(group, "center preflight", false);
        CanvasNodeOperationResult result;
        try {
            result = new SpatialCenterDispersionNodeOperator().apply(node, Map.of("source", source), context(issues));
            for (var table : result.propagatedTables().values()) table.dataset().queryExecution().analyzed();
            assertEquals(0, spark.sparkContext().statusTracker().getJobIdsForGroup(group).length);
        } finally {
            spark.sparkContext().clearJobGroup();
        }
        assertFalse(issues.hasErrors(), issues::toString);
        assertEquals(List.of("source", "mean", "median", "ellipse", "distance", "central"), new ArrayList<>(result.propagatedTables().keySet()));
        assertTrue(result.propagatedTables().get("source") == source);
        for (String name : List.of("mean", "median")) {
            var rows = result.propagatedTables().get(name).dataset().collectAsList();
            assertEquals(1, rows.size());
            assertEquals("POINT (1 1)", ((Geometry) rows.getFirst().getAs("shape")).toText());
        }
        for (String name : List.of("ellipse", "distance")) {
            Geometry shape = result.propagatedTables().get(name).dataset().first().getAs("shape");
            assertTrue(shape.isValid());
            assertFalse(shape.isEmpty());
            assertEquals(2 * Math.sqrt(2), shape.getEnvelopeInternal().getWidth(), 1e-12);
            assertEquals(2 * Math.sqrt(2), shape.getEnvelopeInternal().getHeight(), 1e-12);
        }
        var central = result.propagatedTables().get("central").dataset().first();
        assertEquals(5L, (Long) central.getAs("selected_id"));
        assertEquals(0d, (Double) central.getAs("selected_weight"));
        assertEquals("POINT (1 1)", ((Geometry) central.getAs("shape")).toText());
    }

    private SpatialCenterDispersionNodeDefinition centerForTest(List<SpatialCenterDispersionAnalysis> analyses) {
        return new SpatialCenterDispersionNodeDefinition(id(), "中心", LAYOUT, new SpatialCenterDispersionConfiguration("source", "shape", "id", List.of(), null, analyses, "", SpatialCenterResultMode.ANALYSIS_TABLES));
    }

    @Test
    void centerProjectionPreservesOneWholeRecordAndRenamesEventTime() {
        var raw = nearestTable("source", 3857, GeometryKind.POINT, List.of(RowFactory.create(1L, "POINT (0 0)"),
                RowFactory.create(2L, "POINT (1 0)"), RowFactory.create(3L, "POINT (9 0)"), RowFactory.create(2L, null)));
        var data = raw.dataset().withColumn("summary", org.apache.spark.sql.functions.expr("CASE WHEN id=2 THEN CAST(NULL AS STRING) ELSE 'other' END"))
                .withColumn("amount", org.apache.spark.sql.functions.expr("CAST(id * 1.25 AS DECIMAL(12,2))"))
                .withColumn("observed", org.apache.spark.sql.functions.expr("timestamp_seconds(id)"))
                .withColumn("group", org.apache.spark.sql.functions.lit(null).cast("string"));
        var schema = new ArrayList<>(raw.schema().columns());
        schema.add(stringColumn("summary")); schema.add(stringColumn("group"));
        schema.add(new CanvasColumnSchema("amount", PlatformDataType.DECIMAL, null, 12, 2, true, null, false, false, null));
        schema.add(new CanvasColumnSchema("observed", PlatformDataType.TIMESTAMP, null, null, null, true, null, false, false, null));
        var source = new SparkCanvasTable(new CanvasTableSchema("source", null, schema, CanvasDatasetKind.BOUNDED, "observed", "10 seconds"), data);
        var fields = List.of(new SpatialCenterFeatureColumn("summary", "label", true), new SpatialCenterFeatureColumn("amount", "value", true),
                new SpatialCenterFeatureColumn("id", "selected_id", true), new SpatialCenterFeatureColumn("observed", "time", true),
                new SpatialCenterFeatureColumn("missing", "obsolete", false));
        var analysis = new SpatialCenterDispersionAnalysis(id(), SpatialCenterDispersionKind.CENTRAL_FEATURE, "geometry", null, "result", fields);
        var node = new SpatialCenterDispersionNodeDefinition(id(), "中央", LAYOUT, new SpatialCenterDispersionConfiguration("source", "shape", "id", List.of("group"), null, List.of(analysis), "", SpatialCenterResultMode.ANALYSIS_TABLES));
        var issues = new RecordingIssueSink(); var result = new SpatialCenterDispersionNodeOperator().apply(node, Map.of("source", source), context(issues));
        assertFalse(issues.hasErrors(), issues::toString);
        var table = result.propagatedTables().get("result");
        assertEquals(List.of("label", "value", "selected_id", "time", "geometry"), table.schema().columns().stream().map(CanvasColumnSchema::name).toList());
        var rows = table.dataset().collectAsList(); assertEquals(1, rows.size());
        Row selected = rows.getFirst(); assertNull(selected.getAs("label")); assertEquals(new java.math.BigDecimal("2.50"), selected.getAs("value"));
        assertEquals(2L, (Long) selected.getAs("selected_id")); assertEquals(Timestamp.from(java.time.Instant.ofEpochSecond(2)), selected.getAs("time"));
        assertEquals("POINT (1 0)", ((Geometry) selected.getAs("geometry")).toText());
        assertEquals("time", table.schema().eventTimeColumn()); assertNull(table.schema().watermarkDelay());
        assertTrue(source == result.propagatedTables().get("source"));
        var withoutTime = new SpatialCenterDispersionAnalysis(id(), SpatialCenterDispersionKind.CENTRAL_FEATURE, "geometry", null, "result",
                fields.stream().map(f -> f.sourceColumnName().equals("observed") ? new SpatialCenterFeatureColumn(f.sourceColumnName(), f.outputColumnName(), false) : f).toList());
        var excludedIssues = new RecordingIssueSink();
        var excluded = new SpatialCenterDispersionNodeOperator().apply(centerForTest(List.of(withoutTime)), Map.of("source", source), context(excludedIssues)).propagatedTables().get("result");
        assertFalse(excludedIssues.hasErrors(), excludedIssues::toString);
        assertNull(excluded.schema().eventTimeColumn()); assertNull(excluded.schema().watermarkDelay());
        assertFalse(List.of(excluded.dataset().columns()).contains("time"));
    }

    @Test
    void centerProjectionCanExcludeAllAttributesAndRejectsEveryActiveConflict() {
        var source = nearestTable("source", 3857, GeometryKind.POINT, List.of(RowFactory.create(1L, "POINT (0 0)")));
        var empty = new SpatialCenterDispersionAnalysis(id(), SpatialCenterDispersionKind.CENTRAL_FEATURE, "shape", null, "result", List.of());
        var issues = new RecordingIssueSink();
        var table = new SpatialCenterDispersionNodeOperator().apply(centerForTest(List.of(empty)), Map.of("source", source), context(issues)).propagatedTables().get("result");
        assertFalse(issues.hasErrors(), issues::toString); assertEquals(1, table.dataset().first().size()); assertNull(table.schema().eventTimeColumn());
        for (var fields : List.of(List.of(new SpatialCenterFeatureColumn("missing", "value", true)),
                List.of(new SpatialCenterFeatureColumn("id", "SHAPE", true)),
                List.of(new SpatialCenterFeatureColumn("id", "a", true), new SpatialCenterFeatureColumn("id", "b", true)),
                List.of(new SpatialCenterFeatureColumn("id", "a", true), new SpatialCenterFeatureColumn("shape", "A", true)),
                List.of(new SpatialCenterFeatureColumn("id", "", true)))) {
            var analysis = new SpatialCenterDispersionAnalysis(id(), SpatialCenterDispersionKind.CENTRAL_FEATURE, "shape", null, "result", fields);
            var errors = new RecordingIssueSink();
            var result = new SpatialCenterDispersionNodeOperator().apply(centerForTest(List.of(analysis)), Map.of("source", source), context(errors));
            assertTrue(errors.hasErrors()); assertTrue(result.propagatedTables().isEmpty());
            assertTrue(errors.errorPaths.stream().anyMatch(p -> p.startsWith("configuration.analyses[0].centralFeatureColumns[")));
        }
        var inactive = new SpatialCenterDispersionAnalysis(id(), SpatialCenterDispersionKind.MEAN_CENTER, "shape", null, "result", List.of(new SpatialCenterFeatureColumn("missing", "", true)));
        var noErrors = new RecordingIssueSink();
        var result = new SpatialCenterDispersionNodeOperator().apply(centerForTest(List.of(inactive)), Map.of("source", source), context(noErrors));
        assertFalse(noErrors.hasErrors(), noErrors::toString); assertEquals(1, result.propagatedTables().get("result").dataset().first().size());
    }

    @Test
    void centerProjectionLineageKeepsOriginalFieldsDirectAndResultsIndependent() {
        var raw = nearestTable("source", 3857, GeometryKind.POINT, List.of());
        var inputAsset = new TaskLineageEvidence.Asset("input", TaskLineageEvidence.AssetRole.INPUT, TaskLineageEvidence.AssetKind.JDBC_TABLE,
                null, null, null, null, UUID.randomUUID(), null, null, "source", null, null, "source");
        var marked = CatalystLineageMetadata.markInput(raw.dataset(), "input-node", inputAsset,
                Map.of("id", new CatalystLineageMetadata.InputField("input:id", null), "shape", new CatalystLineageMetadata.InputField("input:shape", null)));
        var source = new SparkCanvasTable(raw.schema(), marked);
        var central = new SpatialCenterDispersionAnalysis(id(), SpatialCenterDispersionKind.CENTRAL_FEATURE, "shape", null, "central",
                List.of(new SpatialCenterFeatureColumn("id", "selectedId", true)));
        var mean = new SpatialCenterDispersionAnalysis(id(), SpatialCenterDispersionKind.MEAN_CENTER, "shape", null, "mean");
        var issues = new RecordingIssueSink();
        var result = new SpatialCenterDispersionNodeOperator().apply(centerForTest(List.of(central, mean)), Map.of("source", source), context(issues));
        assertFalse(issues.hasErrors(), issues::toString);
        var candidates = new ArrayList<CatalystLineageOutputCandidate>();
        for (String name : List.of("central", "mean")) {
            var table = result.propagatedTables().get(name);
            var asset = new TaskLineageEvidence.Asset("out:" + name, TaskLineageEvidence.AssetRole.OUTPUT, TaskLineageEvidence.AssetKind.JDBC_TABLE,
                    null, TaskLineageEvidence.WriteMode.APPEND, null, null, UUID.randomUUID(), null, null, name, null, null, name);
            candidates.add(new CatalystLineageOutputCandidate("flow:" + name, "output-node", "JDBC_OUTPUT", "write:" + name, table.dataset(), asset,
                    table.schema().columns().stream().map(c -> new CatalystLineageOutputCandidate.TargetField("out:" + c.name(), null, c.name(), c.name(), TaskLineageEvidence.OutputEffect.WRITTEN_UNKNOWN_SOURCE)).toList()));
        }
        var lineage = new CatalystLineageAnalyzer().analyze(candidates);
        assertEquals(2, lineage.flows().size());
        var centralFlow = lineage.flows().getFirst();
        assertEquals(TaskLineageEvidence.Coverage.FIELD_COMPLETE, centralFlow.coverage(), () -> centralFlow.warnings().toString());
        for (var mapping : Map.of("selectedId", "id", "shape", "shape").entrySet()) {
            var edges = centralFlow.fieldEdges().stream().filter(e -> e.target().localFieldKey().equals("out:" + mapping.getKey())).toList();
            assertEquals(1, edges.size()); assertEquals("input:" + mapping.getValue(), edges.getFirst().source().localFieldKey());
            assertEquals(TaskLineageEvidence.DerivationType.DIRECT, edges.getFirst().derivationType());
        }
        for (var flow : lineage.flows()) assertTrue(flow.fieldEdges().stream().allMatch(e -> e.target().localAssetKey().equals(flow.outputAsset().localAssetKey())));
        assertTrue(lineage.flows().getLast().fieldEdges().stream().anyMatch(e -> e.source().localFieldKey().equals("input:shape") && e.derivationType() != TaskLineageEvidence.DerivationType.DIRECT));
    }

    @Test
    void centerProjectionErrorsKeepOriginalAnalysisIndicesAfterInvalidEntries() {
        var source = nearestTable("source", 3857, GeometryKind.POINT, List.of());
        var invalid = new SpatialCenterDispersionAnalysis(id(), null, "shape", null, "incomplete");
        var central = new SpatialCenterDispersionAnalysis(id(), SpatialCenterDispersionKind.CENTRAL_FEATURE, "shape", null, "result",
                List.of(new SpatialCenterFeatureColumn("missing", "value", true)));
        var issues = new RecordingIssueSink();
        var result = new SpatialCenterDispersionNodeOperator().apply(centerForTest(List.of(invalid, central)), Map.of("source", source), context(issues));
        assertTrue(result.propagatedTables().isEmpty());
        assertTrue(issues.errorPaths.contains("configuration.analyses[1].centralFeatureColumns[0].sourceColumnName"));
    }

    @Test
    void centerSeparatePolygonsUseOneCentroidPerFeatureNotAreaWeightedVertices() {
        String small = "POLYGON ((0 0, 2 0, 2 2, 0 2, 0 0))";
        String large = "POLYGON ((2 0, 22 0, 22 2, 2 2, 2 0))";
        var source = nearestTable("source", 3857, GeometryKind.POLYGON, List.of(RowFactory.create(10L, small), RowFactory.create(2L, large)));
        var node = centerForTest(List.of(new SpatialCenterDispersionAnalysis(id(), SpatialCenterDispersionKind.MEAN_CENTER, "shape", null, "mean"),
                new SpatialCenterDispersionAnalysis(id(), SpatialCenterDispersionKind.CENTRAL_FEATURE, "shape", null, "central")));
        var issues = new RecordingIssueSink(); var result = new SpatialCenterDispersionNodeOperator().apply(node, Map.of("source", source), context(issues));
        assertFalse(issues.hasErrors(), issues::toString);
        assertEquals("POINT (6.5 1)", ((Geometry) result.propagatedTables().get("mean").dataset().head().getAs("shape")).toText());
        var central = result.propagatedTables().get("central").dataset().head();
        assertEquals(2L, (Long) central.getAs("id")); assertEquals(large, ((Geometry) central.getAs("shape")).toText());
        assertEquals(GeometryKind.POLYGON, result.propagatedTables().get("central").schema().columns().getLast().geometry().kind());
    }

    private SparkCanvasTable nearestTable(String name, int epsg, GeometryKind kind, List<Row> rows) {
        var idColumn = new CanvasColumnSchema("id", PlatformDataType.LONG, null, null, null, true, null, false, false, null);
        var wktColumn = new CanvasColumnSchema("wkt", PlatformDataType.STRING, null, null, null, true, null, false, false, null);
        var base = table(new CanvasTableSchema(name, null, List.of(idColumn, wktColumn), CanvasDatasetKind.BOUNDED, null, null), rows);
        var data = base.dataset().withColumn("shape", org.apache.spark.sql.functions.expr("ST_SetSRID(ST_GeomFromWKT(wkt), " + epsg + ")"))
                .withColumn("__nearest_distance", org.apache.spark.sql.functions.lit("original"));
        var columns = new ArrayList<>(base.schema().columns());
        columns.add(new CanvasColumnSchema("shape", PlatformDataType.GEOMETRY, null, null, null, true, null, false, false, null,
                new GeometryTypeDefinition(kind, new CrsReference("EPSG", epsg), CoordinateDimension.XY)));
        columns.add(stringColumn("__nearest_distance"));
        return new SparkCanvasTable(new CanvasTableSchema(name, null, columns, CanvasDatasetKind.BOUNDED, null, null), data);
    }

    private SpatialNearestNodeDefinition exactNearest(SpatialDistanceMethod method, int count, Double radius, boolean unmatched, boolean lines) {
        return new SpatialNearestNodeDefinition(id(), "最近位置", LAYOUT, new SpatialNearestConfiguration(
                "source", "shape", "candidate", "shape", "id", method, count, radius, radius == null ? null : SpatialDistanceUnit.METERS,
                unmatched, "nearest", "distance", SpatialDistanceUnit.METERS, "rank", List.of(output(JoinOutputColumnSource.LEFT, "id", "source_id"),
                output(JoinOutputColumnSource.RIGHT, "id", "candidate_id")), new SpatialNearestMatching(null, "id",
                new SpatialNearestConnectionLines(lines, "connections", "connection", 10d, SpatialDistanceUnit.KILOMETERS))));
    }

    @Test
    void unaryPoliciesConstructRealStreamingPlansWithoutStartingQueries() {
        var data = spark.readStream().format("rate").load().withWatermark("timestamp", "10 seconds")
                .withColumn("shape", org.apache.spark.sql.functions.expr("ST_SetSRID(ST_Point(value, value), 3857)"));
        var geometry = new CanvasColumnSchema("shape", PlatformDataType.GEOMETRY, null, null, null, true, null, false, false, null,
                new GeometryTypeDefinition(GeometryKind.POINT, new CrsReference("EPSG", 3857), CoordinateDimension.XY));
        var source = new SparkCanvasTable(new CanvasTableSchema("events", null, List.of(timestampColumn("timestamp"), longColumn("value"), geometry),
                CanvasDatasetKind.UNBOUNDED, "timestamp", "10 seconds"), data);
        var issues = new RecordingIssueSink();
        var derive = new GeometryDeriveNodeDefinition(id(), "派生", LAYOUT, new GeometryDeriveConfiguration("events", "derived", List.of(
                new GeometryDerivation(id(), GeometryDeriveKind.CENTROID, "shape", "center", GeometryUnaryPolicy.PRESERVE_DIMENSION))));
        var result = new GeometryDeriveNodeOperator().apply(derive, Map.of("events", source), context(issues));
        var simplify = new GeometrySimplifyNodeDefinition(id(), "简化", LAYOUT, new GeometrySimplifyConfiguration("derived", "center", "simple", "result",
                GeometrySimplifyAlgorithm.TOPOLOGY_PRESERVING, 1d, SpatialDistanceUnit.METERS, GeometryUnaryPolicy.PRESERVE_DIMENSION));
        var output = new GeometrySimplifyNodeOperator().apply(simplify, result.propagatedTables(), context(issues));
        assertFalse(issues.hasErrors(), issues::toString);
        assertTrue(output.propagatedTables().get("simple").dataset().isStreaming());
        assertEquals("timestamp", output.propagatedTables().get("simple").schema().eventTimeColumn());
        assertEquals("10 seconds", output.propagatedTables().get("simple").schema().watermarkDelay());
        assertEquals(0, spark.streams().active().length);
    }

    @Test
    void reconstructSkipsNullTimeAndGeometryAndKeepsRepeatedObservations() {
        var base = reconstructPoints(List.of(
                RowFactory.create("a", 1L, time("2026-01-01 00:00:00"), 10d, "POINT (1 1)"),
                RowFactory.create("a", 2L, time("2026-01-01 01:00:00"), 20d, "POINT (1 1)"),
                RowFactory.create("a", 3L, time("2026-01-01 02:00:00"), 100d, "POINT (10 10)"),
                RowFactory.create("a", 4L, time("2026-01-01 03:00:00"), 100d, "POINT (10 10)")));
        var data = base.dataset().withColumn("shape", org.apache.spark.sql.functions.when(org.apache.spark.sql.functions.col("seq").equalTo(3),
                        org.apache.spark.sql.functions.lit(null)).otherwise(org.apache.spark.sql.functions.col("shape")))
                .withColumn("event_time", org.apache.spark.sql.functions.when(org.apache.spark.sql.functions.col("seq").equalTo(4),
                        org.apache.spark.sql.functions.lit(null)).otherwise(org.apache.spark.sql.functions.col("event_time")));
        var source = new SparkCanvasTable(base.schema(), data);
        var issues = new RecordingIssueSink();
        var result = new TrackReconstructNodeOperator().apply(reconstructNode(new TrackReconstructOptions(null, List.of("seq"), null, null),
                new TrackBoundaryConfiguration(null, null, null, null)), Map.of("observations", source), context(issues));
        assertFalse(issues.hasErrors(), issues::toString);
        var row = result.propagatedTables().get("rebuilt").dataset().head();
        assertEquals(2L, (Long) row.getAs("n"));
        assertEquals(20d, (Double) row.getAs("last_value"));
        assertEquals(0d, ((Geometry) row.getAs("line")).getLength());
    }

    @Test
    void reconstructBindingNamesCannotOverwriteFieldsAndMayUseInternalLookingAliases() {
        var source = reconstructPoints(List.of(
                RowFactory.create("a", 1L, time("2026-01-01 00:00:00"), 1d, "POINT (0 0)"),
                RowFactory.create("a", 2L, time("2026-01-01 01:00:00"), 2d, "POINT (1 0)")));
        var noGap = new TrackBoundaryConfiguration(null, null, null, null);
        for (var binding : List.of(new TrackFieldWindowBinding("value", "value", -1),
                new TrackFieldWindowBinding("prior", "absent", -1), new TrackFieldWindowBinding("prior", "value", 1001))) {
            var issues = new RecordingIssueSink();
            var result = new TrackReconstructNodeOperator().apply(reconstructNode(new TrackReconstructOptions(null, List.of(), null,
                    new TrackSplitExpression("true", List.of(binding))), noGap), Map.of("observations", source), context(issues));
            assertTrue(issues.hasErrors());
            assertTrue(result.propagatedTables().isEmpty());
        }
        var issues = new RecordingIssueSink();
        var result = new TrackReconstructNodeOperator().apply(reconstructNode(new TrackReconstructOptions(null, List.of(), null,
                new TrackSplitExpression("__datascalpel_track_segment > value", List.of(new TrackFieldWindowBinding("__datascalpel_track_segment", "value", -1)))), noGap),
                Map.of("observations", source), context(issues));
        assertFalse(issues.hasErrors(), issues::toString);
        assertEquals(1, result.propagatedTables().get("rebuilt").dataset().count());
    }

    private TrackReconstructNodeDefinition reconstructNode(TrackReconstructOptions options, TrackBoundaryConfiguration boundaries) {
        return new TrackReconstructNodeDefinition(id(), "有序重建", LAYOUT,
                new TrackReconstructConfiguration("observations", "shape", List.of("track"), "event_time", SpatialDistanceMethod.PLANAR,
                        boundaries, List.of(new TrackSummaryStatistic(id(), TrackSummaryStatisticKind.FIRST, "value", "first_value"),
                        new TrackSummaryStatistic(id(), TrackSummaryStatisticKind.LAST, "value", "last_value")),
                        "rebuilt", "line", "start", "end", "n", options));
    }

    @Test
    void overlayProducesActualXYAndHandlesRelatedDatasetsWithoutAmbiguousSelfJoin() {
        var base = overlayLayer("left", GeometryKind.POLYGON, "POLYGON ((0 0, 2 0, 2 2, 0 2, 0 0))");
        List<CanvasColumnSchema> columns = base.schema().columns().stream().map(column -> column.geometry() == null ? column :
                new CanvasColumnSchema(column.name(), column.fieldType(), null, null, null, column.nullable(), null, false, false, null,
                        new GeometryTypeDefinition(column.geometry().kind(), column.geometry().crs(), CoordinateDimension.XYZ))).toList();
        var xyzData = base.dataset().withColumn("shape", org.apache.spark.sql.sedona_sql.expressions.st_functions.ST_Force3D(
                org.apache.spark.sql.functions.col("shape"), org.apache.spark.sql.functions.lit(30d)));
        var left = new SparkCanvasTable(new CanvasTableSchema("left", null, columns, CanvasDatasetKind.BOUNDED, null, null), xyzData);
        var right = new SparkCanvasTable(new CanvasTableSchema("right", null, columns, CanvasDatasetKind.BOUNDED, null, null), xyzData);
        var issues = new RecordingIssueSink();
        var result = new SpatialOverlayNodeOperator().apply(overlayNode(SpatialOverlayOperation.IDENTITY, SpatialOverlayGeometryPolicy.FAMILY_2D),
                tables(left, right), context(issues));
        assertFalse(issues.hasErrors(), issues::toString);
        assertTrue(issues.codes.contains("SPATIAL_OVERLAY_OUTPUT_XY"));
        var rows = result.propagatedTables().get("overlay").dataset().collectAsList();
        assertEquals(1, rows.size());
        Geometry geometry = rows.getFirst().getAs("result_shape");
        assertEquals(4d, geometry.getArea(), 1e-8);
        assertTrue(Double.isNaN(geometry.getCoordinate().getZ()));
        Geometry unchanged = left.dataset().head().getAs("shape");
        assertEquals(30d, unchanged.getCoordinate().getZ());
        // Old cross-family ERASE remains a valid extension rather than becoming a new compile error.
        var line = overlayLayer("left", GeometryKind.LINESTRING, "LINESTRING (-1 1, 3 1)");
        var legacyIssues = new RecordingIssueSink();
        var legacy = new SpatialOverlayNodeOperator().apply(overlayNode(SpatialOverlayOperation.ERASE, null),
                tables(line, new SparkCanvasTable(new CanvasTableSchema("right", null, base.schema().columns(), CanvasDatasetKind.BOUNDED, null, null), base.dataset())),
                context(legacyIssues));
        assertFalse(legacyIssues.hasErrors(), legacyIssues::toString);
        assertEquals(2d, ((Geometry) legacy.propagatedTables().get("overlay").dataset().head().getAs("result_shape")).getLength(), 1e-8);
    }

    private SpatialOverlayNodeDefinition overlayNode(SpatialOverlayOperation mode, SpatialOverlayGeometryPolicy policy) {
        var columns = new ArrayList<JoinOutputColumn>();
        columns.add(output(JoinOutputColumnSource.LEFT, "id", "left_id"));
        if (mode != SpatialOverlayOperation.ERASE) columns.add(output(JoinOutputColumnSource.RIGHT, "id", "right_id"));
        return new SpatialOverlayNodeDefinition(id(), "五模式叠加", LAYOUT,
                new SpatialOverlayConfiguration("left", "shape", "right", "shape", mode, "overlay", "result_shape", columns, policy));
    }

    private SpatialSummarizeWithinNodeDefinition withinLinkedNode(String areaOutput, SpatialTemporalSlicing time) {
        return new SpatialSummarizeWithinNodeDefinition(id(), "关联组汇总", LAYOUT,
                new SpatialSummarizeWithinConfiguration("areas", "shape", "summaries", "shape", true,
                        SpatialDistanceMethod.PLANAR, SpatialDistanceUnit.METERS, SpatialAreaUnit.SQUARE_METERS,
                        List.of(output(JoinOutputColumnSource.LEFT, areaOutput, areaOutput)), List.of(
                        new SpatialWithinStatistic(id(), SpatialWithinStatisticKind.COUNT, null, "features"),
                        new SpatialWithinStatistic(id(), SpatialWithinStatisticKind.MEAN, "amount", "mean_amount")),
                        new SpatialGroupSummary("category", true, true, null, null, "group_pct"), time, "within",
                        new SpatialWithinGroupResult("area_id", "summary_area_id", "within_groups", "group_value",
                                "minority", "majority", "minority_pct", "majority_pct")));
    }

    @Test
    void withinWeightedDispersionUsesPublishedFormulaWithMissingZeroAndEmptyMatches() {
        var lines = geometryTable("weighted_variance_raw", "summaries", "shape", GeometryKind.LINESTRING, 3857,
                List.of(new CanvasColumnSchema("amount", PlatformDataType.DOUBLE, null, null, null, true, null, false, false, null, null), stringColumn("wkt")),
                List.of(RowFactory.create(1000d, "LINESTRING (0 1,20 1)"), // p=1/2
                        RowFactory.create(600d, "LINESTRING (0 2,15 2)"), // p=2/3
                        RowFactory.create(null, "LINESTRING (0 3,10 3)"),
                        RowFactory.create(Double.NaN, "LINESTRING (0 4,10 4)"),
                        RowFactory.create(Double.POSITIVE_INFINITY, "LINESTRING (0 5,10 5)"),
                        RowFactory.create(1000000d, "LINESTRING (10 6,20 6)"), // boundary-only p=0
                        RowFactory.create(1000000d, "LINESTRING (1 7,1 7)"))); // undefined whole length
        var statistics = List.of(
                new SpatialWithinStatistic(id(), SpatialWithinStatisticKind.VARIANCE, "amount", "variance", null, SpatialWithinWeighting.INTERSECTION_FRACTION),
                new SpatialWithinStatistic(id(), SpatialWithinStatisticKind.STDDEV, "amount", "deviation", null, SpatialWithinWeighting.INTERSECTION_FRACTION));
        var issues = new RecordingIssueSink();
        var result = new SpatialSummarizeWithinNodeOperator().apply(withinNode(statistics), tables(withinAreas(), lines), context(issues));
        assertFalse(issues.hasErrors(), issues::toString);
        var output = result.propagatedTables().get("within");
        var rows = output.dataset().orderBy("area_id").collectAsList();
        assertEquals(3_840_000d / 49, (Double) rows.getFirst().getAs("variance"), 1e-8);
        assertEquals(Math.sqrt(3_840_000d / 49), (Double) rows.getFirst().getAs("deviation"), 1e-8);
        assertNull(rows.getLast().getAs("variance")); assertNull(rows.getLast().getAs("deviation"));
        assertEquals(PlatformDataType.DOUBLE, output.schema().columns().getLast().fieldType());
        assertTrue(output.schema().columns().getLast().nullable());
        assertEquals(List.of("areas", "summaries", "within"), new ArrayList<>(result.propagatedTables().keySet()));
    }

    @Test
    void withinWeightedVarianceWorksInMainAndGroupResultsWithoutAveragingGroupVariances() {
        var source = geometryTable("weighted_group_raw", "summaries", "shape", GeometryKind.POLYGON, 3857,
                List.of(doubleColumn("amount"), stringColumn("category"), stringColumn("wkt")),
                List.of(RowFactory.create(1000d, "a", "POLYGON ((0 0,20 0,20 10,0 10,0 0))"),
                        RowFactory.create(600d, "a", "POLYGON ((0 0,15 0,15 10,0 10,0 0))"),
                        RowFactory.create(200d, "b", "POLYGON ((1 1,2 1,2 2,1 2,1 1))")));
        var base = withinLinkedNode("area_id", null).configuration();
        var statistics = List.of(new SpatialWithinStatistic(id(), SpatialWithinStatisticKind.VARIANCE, "amount", "variance", null, SpatialWithinWeighting.INTERSECTION_FRACTION));
        var c = new SpatialSummarizeWithinConfiguration(base.areaTableName(), base.areaGeometryColumnName(), base.summaryTableName(),
                base.summaryGeometryColumnName(), base.includeEmptyAreas(), base.distanceMethod(), base.lengthUnit(), base.areaUnit(),
                base.areaOutputColumns(), statistics, base.groupSummary(), base.temporalSlicing(), base.outputTableName(), base.groupResult());
        var issues = new RecordingIssueSink();
        var result = new SpatialSummarizeWithinNodeOperator().apply(new SpatialSummarizeWithinNodeDefinition(id(), "加权方差", LAYOUT, c),
                tables(withinAreas(), source), context(issues));
        assertFalse(issues.hasErrors(), issues::toString);
        double sumW = 0.5 + 2d / 3 + 1, mean = 1100 / sumW;
        double m2 = 0.5 * Math.pow(1000 - mean, 2) + 2d / 3 * Math.pow(600 - mean, 2) + Math.pow(200 - mean, 2);
        var main = result.propagatedTables().get("within").dataset().orderBy("summary_area_id").collectAsList();
        assertEquals(m2 / (sumW * 2d / 3), (Double) main.getFirst().getAs("variance"), 1e-8);
        var groups = result.propagatedTables().get("within_groups").dataset().orderBy("group_value").collectAsList();
        assertEquals(2, groups.size());
        assertEquals(3_840_000d / 49, (Double) groups.getFirst().getAs("variance"), 1e-8);
        assertNull(groups.getLast().getAs("variance"));
    }

    @Test
    void weightedVarianceUsesDistributedPartialAggregationAndRemainsLazy() {
        var frame = spark.range(0, 100, 1, 4).select(
                org.apache.spark.sql.functions.col("id").multiply(2d).plus(1e12).alias("x"),
                org.apache.spark.sql.functions.lit(0.5d).alias("weight"));
        var result = frame.agg(WithinWeightedVariance.expression(frame.col("x"), frame.col("weight")).alias("variance"));
        assertEquals(3366.666666666667d, (Double) result.head().getAs("variance"), 1e-7);
        var empty = frame.limit(0).agg(WithinWeightedVariance.expression(frame.col("x"), frame.col("weight")).alias("variance"));
        assertNull(empty.head().getAs("variance"));
    }

    @Test
    void weightedVarianceLineageContainsNumericAndBothGeometrySourcesWithoutActions() {
        var areas = withinAreas();
        var source = geometryTable("variance_lineage_raw", "summaries", "shape", GeometryKind.POLYGON, 3857,
                List.of(doubleColumn("amount"), stringColumn("category"), stringColumn("wkt")), List.of());
        Map<String, SparkCanvasTable> inputs = new LinkedHashMap<>();
        for (var table : List.of(areas, source)) {
            String key = table.schema().name();
            var asset = new TaskLineageEvidence.Asset(key, TaskLineageEvidence.AssetRole.INPUT, TaskLineageEvidence.AssetKind.JDBC_TABLE,
                    null, null, null, null, UUID.randomUUID(), null, null, key, null, null, key);
            var fields = new LinkedHashMap<String, CatalystLineageMetadata.InputField>();
            for (var column : table.schema().columns()) fields.put(column.name(), new CatalystLineageMetadata.InputField(key + ":" + column.name(), null));
            inputs.put(key, new SparkCanvasTable(table.schema(), CatalystLineageMetadata.markInput(table.dataset(), key + "-node", asset, fields)));
        }
        var base = withinLinkedNode("area_id", null).configuration();
        var statistic = new SpatialWithinStatistic(id(), SpatialWithinStatisticKind.VARIANCE, "amount", "variance", null, SpatialWithinWeighting.INTERSECTION_FRACTION);
        var config = new SpatialSummarizeWithinConfiguration(base.areaTableName(), base.areaGeometryColumnName(), base.summaryTableName(), base.summaryGeometryColumnName(),
                base.includeEmptyAreas(), base.distanceMethod(), base.lengthUnit(), base.areaUnit(), base.areaOutputColumns(), List.of(statistic),
                base.groupSummary(), base.temporalSlicing(), base.outputTableName(), base.groupResult());
        var issues = new RecordingIssueSink();
        var result = new SpatialSummarizeWithinNodeOperator().apply(new SpatialSummarizeWithinNodeDefinition(id(), "方差血缘", LAYOUT, config), inputs, context(issues));
        assertFalse(issues.hasErrors(), issues::toString);
        for (String name : List.of("within", "within_groups")) {
            var table = result.propagatedTables().get(name);
            var asset = new TaskLineageEvidence.Asset(name, TaskLineageEvidence.AssetRole.OUTPUT, TaskLineageEvidence.AssetKind.JDBC_TABLE,
                    null, TaskLineageEvidence.WriteMode.APPEND, null, null, UUID.randomUUID(), null, null, name, null, null, name);
            var candidate = new CatalystLineageOutputCandidate(name, "output-node", "JDBC_OUTPUT", "write", table.dataset().select("variance"), asset,
                    List.of(new CatalystLineageOutputCandidate.TargetField(name + ":variance", null, "variance", "variance", TaskLineageEvidence.OutputEffect.WRITTEN_UNKNOWN_SOURCE)));
            var flow = new CatalystLineageAnalyzer().analyze(List.of(candidate)).flows().getFirst();
            assertEquals(TaskLineageEvidence.Coverage.FIELD_COMPLETE, flow.coverage(), () -> flow.warnings().toString());
            for (String field : List.of("summaries:amount", "summaries:shape", "areas:shape"))
                assertTrue(flow.fieldEdges().stream().anyMatch(edge -> edge.source().localFieldKey().equals(field)
                        && edge.derivationType() == TaskLineageEvidence.DerivationType.AGGREGATED), field);
        }
    }

    private SparkCanvasTable withinAreas() {
        return geometryTable("allocation_areas_raw", "areas", "shape", GeometryKind.POLYGON, 3857,
                List.of(longColumn("area_id"), stringColumn("wkt")), List.of(
                        RowFactory.create(1L, "POLYGON ((0 0, 10 0, 10 10, 0 10, 0 0))"),
                        RowFactory.create(2L, "POLYGON ((30 30, 40 30, 40 40, 30 40, 30 30))")));
    }

    private SpatialSummarizeWithinNodeDefinition withinNode(List<SpatialWithinStatistic> statistics) {
        return new SpatialSummarizeWithinNodeDefinition(id(), "区域汇总", LAYOUT,
                new SpatialSummarizeWithinConfiguration("areas", "shape", "summaries", "shape", true,
                        SpatialDistanceMethod.PLANAR, SpatialDistanceUnit.METERS, SpatialAreaUnit.SQUARE_METERS,
                        List.of(output(JoinOutputColumnSource.LEFT, "area_id", "area_id")), statistics,
                        null, null, "within"));
    }

    private SparkCanvasTable geometryTable(
            String rawName,
            String outputName,
            String geometryColumnName,
            GeometryKind kind,
            int epsg,
            List<CanvasColumnSchema> rawColumns,
            List<Row> rows
    ) {
        SparkCanvasTable raw = table(
                new CanvasTableSchema(rawName, null, rawColumns, CanvasDatasetKind.BOUNDED, null, null),
                rows);
        RecordingIssueSink issues = new RecordingIssueSink();
        CanvasNodeOperationResult constructed = new GeometryConstructNodeOperator().apply(
                new GeometryConstructNodeDefinition(id(), "构造 Geometry", LAYOUT,
                        new GeometryConstructConfiguration(
                                rawName, outputName, geometryColumnName,
                                new GeometryConstructSource.Wkt("wkt"), geometryType(kind, epsg))),
                Map.of(rawName, raw), context(issues));
        assertFalse(issues.hasErrors(), issues::toString);
        return constructed.propagatedTables().get(outputName);
    }

    private CanvasNodeOperationContext context(RecordingIssueSink issues) {
        return new CanvasNodeOperationContext(
                spark,
                MetadataIndex.create(new MetadataSnapshot(List.of(), List.of())),
                issues,
                new SchemaOnlyCanvasNodeDataAccess(spark));
    }

    private SparkCanvasTable table(CanvasTableSchema schema, List<Row> rows) {
        return new SparkCanvasTable(
                schema,
                spark.createDataFrame(rows, SparkTypeMapper.toStructType(schema.columns())));
    }

    private static Map<String, SparkCanvasTable> tables(SparkCanvasTable first, SparkCanvasTable second) {
        Map<String, SparkCanvasTable> result = new LinkedHashMap<>();
        result.put(first.schema().name(), first);
        result.put(second.schema().name(), second);
        return result;
    }

    private static JoinOutputColumn output(
            JoinOutputColumnSource side,
            String source,
            String target
    ) {
        return new JoinOutputColumn(side, source, target, true);
    }

    private static CanvasFieldPredicate predicate(String column, FilterOperator operator, String value) {
        return new CanvasFieldPredicate(
                column, operator, List.of(new CanvasLiteral(PlatformDataType.DOUBLE, value)));
    }

    private static CanvasColumnSchema longColumn(String name) {
        return column(name, PlatformDataType.LONG);
    }

    private static CanvasColumnSchema doubleColumn(String name) {
        return column(name, PlatformDataType.DOUBLE);
    }

    private static CanvasColumnSchema stringColumn(String name) {
        return column(name, PlatformDataType.STRING);
    }

    private static CanvasColumnSchema timestampColumn(String name) {
        return column(name, PlatformDataType.TIMESTAMP);
    }

    private static CanvasColumnSchema column(String name, PlatformDataType type) {
        return new CanvasColumnSchema(
                name, type, null, null, null, false,
                null, false, false, null, null);
    }

    private static GeometryTypeDefinition geometryType(GeometryKind kind, int epsg) {
        return new GeometryTypeDefinition(
                kind, new CrsReference("EPSG", epsg), CoordinateDimension.XY);
    }

    private static Timestamp time(String value) {
        return Timestamp.valueOf(value);
    }

    private static String id() {
        return UUID.randomUUID().toString();
    }

    private static final class RecordingIssueSink implements CanvasNodeIssueSink {
        private final List<String> codes = new ArrayList<>();
        private final List<String> errorPaths = new ArrayList<>();
        private boolean errors;

        @Override
        public void error(String code, String message, String path) {
            codes.add(code);
            errorPaths.add(path);
            errors = true;
        }

        @Override
        public void warning(String code, String message, String path) {
            codes.add(code);
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
