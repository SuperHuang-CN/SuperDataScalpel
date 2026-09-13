package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.*;
import cn.superhuang.data.scalpel.contract.type.*;
import cn.superhuang.datascalpel.taskengine.compiler.MetadataIndex;
import cn.superhuang.datascalpel.taskengine.compiler.lineage.*;
import cn.superhuang.datascalpel.taskengine.spark.*;
import org.apache.spark.sql.*;
import org.apache.spark.sql.sedona_sql.expressions.st_constructors;
import org.junit.jupiter.api.*;
import org.locationtech.jts.geom.*;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TrackAreaSparkTest {
    private SparkSession spark;
    private static final TrackBoundaryConfiguration NONE = new TrackBoundaryConfiguration(null,null,null,null);
    @BeforeAll void start() {
        spark = SedonaSparkSupport.initialize(SedonaSparkSupport.builder().master("local[1]").appName("track-area")
                .config("spark.ui.enabled", false).config("spark.driver.host", "127.0.0.1").config("spark.driver.bindAddress", "127.0.0.1")
                .config("spark.sql.shuffle.partitions", 1).config("spark.sql.session.timeZone", "UTC").getOrCreate());
    }
    @AfterAll void stop() { if (spark != null) spark.stop(); }

    @Test void varyingBuffersConnectOnlyAdjacentObservationsAndKeepActualStatistics() {
        var source = source(GeometryKind.POINT, row(0,"POINT (0 0)",1d), row(1,"POINT (10 0)",2d), row(2,"POINT (10 10)",1d));
        var output = run(source, config(area(TrackBufferMode.FIELD, null),NONE,TrackSplitBoundaryOption.GAP));
        var row = output.dataset().head(); Geometry geometry = row.getAs("geometry");
        assertInstanceOf(MultiPolygon.class,geometry); assertEquals(3857,geometry.getSRID());
        assertTrue(geometry.covers(new GeometryFactory().createPoint(new Coordinate(5,0))));
        assertFalse(geometry.covers(new GeometryFactory().createPoint(new Coordinate(4,6))), "must not make a whole-track convex hull");
        assertEquals(-2d,geometry.getEnvelopeInternal().getMinY(),1e-8);
        assertEquals(3L,row.<Long>getAs("observations")); assertEquals(4d,row.<Double>getAs("total"),1e-8);
        assertEquals(GeometryKind.MULTIPOLYGON, output.schema().columns().getLast().geometry().kind());
        assertEquals(CanvasDatasetKind.BOUNDED,output.schema().datasetKind()); assertNull(output.schema().watermarkDelay());
    }

    @Test void singletonAreaIsKeptButLineRemainsEmptyAndInactiveBufferIsIgnored() {
        var source = source(GeometryKind.POINT,row(0,"POINT (0 0)",1d));
        assertEquals(1,run(source,config(area(TrackBufferMode.FIELD,null),NONE,TrackSplitBoundaryOption.GAP)).dataset().count());
        assertEquals(0,run(source,config(new TrackAreaGeometryOptions(false,TrackBufferMode.EXPRESSION,null,"invalid sql",null),
                NONE,TrackSplitBoundaryOption.GAP)).dataset().count());
        assertEquals(0,run(source(GeometryKind.POINT),config(area(TrackBufferMode.FIELD,null),NONE,TrackSplitBoundaryOption.GAP)).dataset().count());
    }

    @Test void polygonsProduceAConnectingAreaAndSingletonHolesAreNotDestroyed() {
        var c = config(area(TrackBufferMode.NONE,null),NONE,TrackSplitBoundaryOption.GAP);
        var output = run(source(GeometryKind.POLYGON,row(0,"POLYGON ((0 0,2 0,2 2,0 2,0 0))",1d),
                row(1,"POLYGON ((8 0,10 0,10 2,8 2,8 0))",1d)),c);
        assertEquals(20d,output.dataset().head().<Geometry>getAs("geometry").getArea(),1e-8);
        var hole = run(source(GeometryKind.POLYGON,row(0,"POLYGON ((0 0,4 0,4 4,0 4,0 0),(1 1,1 3,3 3,3 1,1 1))",1d)),c);
        assertEquals(12d,hole.dataset().head().<Geometry>getAs("geometry").getArea(),1e-8);
    }

    @Test void gapsUseUnbufferedObservationsAndSharedEndpointsCountAsRealMembers() {
        var source = source(GeometryKind.POINT,row(0,"POINT (0 0)",100d),row(1,"POINT (1 0)",100d),row(10,"POINT (10 0)",100d));
        var boundary = new TrackBoundaryConfiguration(null,null,5d,SpatialDistanceUnit.METERS);
        for (var option : TrackSplitBoundaryOption.values()) {
            var rows = run(source,config(area(TrackBufferMode.FIELD,null),boundary,option)).dataset().orderBy("start").collectAsList();
            assertEquals(2,rows.size(),"overlapping buffers must not remove a distance split");
            assertEquals(option == TrackSplitBoundaryOption.FINISH_LAST ? 3L : 2L,rows.getFirst().<Long>getAs("observations"));
            assertEquals(option == TrackSplitBoundaryOption.START_NEXT ? 2L : 1L,rows.getLast().<Long>getAs("observations"));
            assertEquals(option == TrackSplitBoundaryOption.GAP ? 300d : 400d,
                    rows.stream().mapToDouble(r -> r.<Double>getAs("total")).sum(),1e-8);
        }
    }

    @Test void numericExpressionIsRowLocalAndNeverRunsDuringPreflight() {
        var source = source(GeometryKind.POINT,row(0,"POINT (0 0)",2d));
        String job = UUID.randomUUID().toString(); spark.sparkContext().setJobGroup(job,"area preflight",false);
        var output = run(source,config(area(TrackBufferMode.EXPRESSION,"radius * 2"),NONE,TrackSplitBoundaryOption.GAP));
        output.dataset().queryExecution().analyzed();
        assertEquals(0,spark.sparkContext().statusTracker().getJobIdsForGroup(job).length);
        spark.sparkContext().clearJobGroup();
        assertEquals(8d,output.dataset().head().<Geometry>getAs("geometry").getEnvelopeInternal().getWidth(),1e-8);
        for (String expression : List.of("radius; select 1", "'text'", "rand()", "sum(radius)", "explode(array(radius, radius))", "lag(radius) over (order by time)")) {
            var issues = invalid(source,config(area(TrackBufferMode.EXPRESSION,expression),NONE,TrackSplitBoundaryOption.GAP));
            assertTrue(issues.codes.contains("INVALID_TRACK_BUFFER_EXPRESSION"),issues.toString());
        }
    }

    @Test void fixedPeriodsNeverBridgeAreaObservationsAndInvalidObservationsAreExcluded() {
        var boundary = new TrackBoundaryConfiguration(null,null,null,null,
                new TrackFixedTimeBoundary(5,TrackTimeBoundaryUnit.SECONDS,"1970-01-01T00:00:00Z","UTC"));
        var source = source(GeometryKind.POINT,row(0,"POINT (0 0)",1d),row(10,"POINT (10 0)",1d),
                row(20,"POINT EMPTY",1d),row(30,null,1d),RowFactory.create("A",null,1d,"POINT (40 0)"));
        for (var option : TrackSplitBoundaryOption.values()) {
            var rows = run(source,config(area(TrackBufferMode.FIELD,null),boundary,option)).dataset().collectAsList();
            assertEquals(2,rows.size()); rows.forEach(r -> assertEquals(1L,r.<Long>getAs("observations")));
        }
    }

    @Test void badDistancesFailLazilyWithSafeCodesAndDoNotDropObservations() {
        for (Double radius : Arrays.asList(null,-1d,0d,Double.NaN,Double.POSITIVE_INFINITY)) {
            var source = source(GeometryKind.POINT,row(0,"POINT (0 0)",radius));
            var output = run(source,config(area(TrackBufferMode.FIELD,null),NONE,TrackSplitBoundaryOption.GAP));
            var error = assertThrows(Exception.class, () -> output.dataset().collectAsList());
            assertTrue(causes(error).contains("TRACK_BUFFER_DISTANCE_INVALID"));
        }
    }

    @Test void unsupportedModesAndGeometryAreExplicitAndNeverPropagatePartialPlans() {
        var source = source(GeometryKind.POINT);
        assertTrue(invalid(source,config(area(TrackBufferMode.NONE,null),NONE,TrackSplitBoundaryOption.GAP)).codes.contains("TRACK_POINT_BUFFER_REQUIRED"));
        var c = config(area(TrackBufferMode.FIELD,null),NONE,TrackSplitBoundaryOption.GAP);
        var geodesic = new TrackReconstructConfiguration(c.sourceTableName(),c.pointGeometryColumnName(),c.trackIdColumns(),c.timeColumnName(),
                SpatialDistanceMethod.GEODESIC,c.boundaries(),c.summaryStatistics(),c.outputTableName(),c.outputGeometryColumnName(),
                c.startTimeColumnName(),c.endTimeColumnName(),c.pointCountColumnName(),c.reconstruction());
        assertTrue(invalid(source,geodesic).codes.contains("REQUIRED_CONFIGURATION"));
        assertTrue(invalid(source,geodesic).codes.contains("GEODESIC_REQUIRES_WGS84_XY"));
        assertTrue(invalid(source(GeometryKind.LINESTRING),c).codes.contains("TRACK_AREA_GEOMETRY_REQUIRED"));
    }

    @Test void geodesicDiskPrimitiveRunsLazilyOnExecutorsWithIndependentRadii() {
        // Exercise the geometry primitive, not the still-disabled geodesic area Operator.
        var raw = source(GeometryKind.POINT, row(0,"POINT (179.8 70)",100_000d),
                row(1,"POINT (179.8 70)",200_000d)).dataset();
        var geometryType = raw.schema().apply("shape").dataType();
        var disk = functions.udf((org.apache.spark.sql.api.java.UDF2<Geometry, Double, Geometry>) (shape, radius) ->
                TrackGeodesicDisk.sample((Point) shape, radius, 20_000).render(), geometryType);
        String job = UUID.randomUUID().toString(); spark.sparkContext().setJobGroup(job,"geodesic footprint preflight",false);
        Dataset<Row> plan;
        try {
            plan = raw.withColumn("disk",disk.apply(functions.expr("ST_SetSRID(shape,4326)"),raw.col("radius")));
            plan.queryExecution().analyzed(); plan.schema();
            assertEquals(0,spark.sparkContext().statusTracker().getJobIdsForGroup(job).length);
        } finally { spark.sparkContext().clearJobGroup(); }
        var rows = plan.orderBy("time").collectAsList(); assertEquals(2,rows.size());
        Geometry small = rows.getFirst().getAs("disk"), large = rows.getLast().getAs("disk");
        assertInstanceOf(MultiPolygon.class,small); assertInstanceOf(MultiPolygon.class,large);
        assertEquals(4326,small.getSRID()); assertEquals(4326,large.getSRID());
        assertTrue(large.covers(small));
        assertFalse(large.covers(new GeometryFactory().createPoint(new CoordinateXY(0,70))));
    }

    @Test void geodesicConnectionPrimitiveAnalyzesWithoutJobsAndRunsOnExecutors() {
        var raw = source(GeometryKind.POINT,row(0,"POINT (-45 60)",50_000d)).dataset();
        var geometryType = raw.schema().apply("shape").dataType();
        var connection = functions.udf((org.apache.spark.sql.api.java.UDF3<Geometry,Geometry,Double,Geometry>) (left,right,radius) -> {
            var a = new TrackGeodesicAreaBoundary.Vertex(left.getCoordinate().x,left.getCoordinate().y);
            var b = new TrackGeodesicAreaBoundary.Vertex(right.getCoordinate().x,right.getCoordinate().y);
            var vertices = new ArrayList<>(TrackGeodesicDisk.sample((Point)left,radius,25_000).boundary());
            vertices.addAll(TrackGeodesicDisk.sample((Point)right,radius,25_000).boundary());
            return TrackGeodesicHull.build(vertices,TrackGeodesicHull.midpoint(a,b),25_000).render();
        },geometryType);
        String job = UUID.randomUUID().toString(); spark.sparkContext().setJobGroup(job,"geodesic connection preflight",false);
        Dataset<Row> plan;
        try {
            plan = raw.withColumn("connection",connection.apply(functions.expr("ST_SetSRID(shape,4326)"),
                    functions.expr("ST_SetSRID(ST_Point(45D,60D),4326)"),raw.col("radius")));
            plan.queryExecution().analyzed(); plan.schema();
            assertEquals(0,spark.sparkContext().statusTracker().getJobIdsForGroup(job).length);
        } finally { spark.sparkContext().clearJobGroup(); }
        Geometry area = plan.head().getAs("connection"); assertInstanceOf(MultiPolygon.class,area);
        assertTrue(area.isValid()); assertEquals(4326,area.getSRID());
        var midpoint = TrackGeodesicHull.midpoint(new TrackGeodesicAreaBoundary.Vertex(-45,60),
                new TrackGeodesicAreaBoundary.Vertex(45,60));
        assertTrue(area.covers(new GeometryFactory().createPoint(midpoint.coordinate())));
        assertFalse(area.covers(new GeometryFactory().createPoint(new CoordinateXY(0,60))));
    }

    @Test void geodesicPolygonFootprintsRunOnExecutorsWithoutExecutingDuringAnalysis() {
        var raw = source(GeometryKind.MULTIPOLYGON,
                row(0,"MULTIPOLYGON (((179 -1,-179 -1,-179 1,179 1,179 -1),(179.5 -0.5,179.5 0.5,-179.5 0.5,-179.5 -0.5,179.5 -0.5)))",10_000d),
                row(1,"MULTIPOLYGON (((0 0,0.01 0,0.01 0.01,0 0.01,0 0)),((0.03 0,0.04 0,0.04 0.01,0.03 0.01,0.03 0)))",200d)).dataset();
        var geometryType = raw.schema().apply("shape").dataType();
        var footprint = functions.udf((org.apache.spark.sql.api.java.UDF2<Geometry,Double,Geometry>) (shape,radius) ->
                TrackGeodesicAreaGeometry.footprint(shape,radius,true,1000).area(),geometryType);
        String job = UUID.randomUUID().toString(); spark.sparkContext().setJobGroup(job,"geodesic polygon preflight",false);
        Dataset<Row> plan;
        try {
            plan = raw.withColumn("footprint",footprint.apply(functions.expr("ST_SetSRID(shape,4326)"),raw.col("radius")));
            plan.queryExecution().analyzed(); plan.schema();
            assertEquals(0,spark.sparkContext().statusTracker().getJobIdsForGroup(job).length);
        } finally { spark.sparkContext().clearJobGroup(); }
        var rows = plan.orderBy("time").collectAsList(); assertEquals(2,rows.size());
        Geometry dateline = rows.getFirst().getAs("footprint"), multipart = rows.getLast().getAs("footprint");
        for (Geometry geometry : List.of(dateline,multipart)) {
            assertInstanceOf(MultiPolygon.class,geometry); assertTrue(geometry.isValid()); assertEquals(4326,geometry.getSRID());
        }
        var factory = new GeometryFactory();
        assertTrue(dateline.covers(factory.createPoint(new CoordinateXY(179.2,0))));
        assertFalse(dateline.covers(factory.createPoint(new CoordinateXY(180,0))));
        assertFalse(dateline.covers(factory.createPoint(new CoordinateXY(0,0))));
        assertEquals(2,multipart.getNumGeometries());
        assertFalse(multipart.covers(factory.createPoint(new CoordinateXY(0.02,0.005))));
    }

    @Test void geodesicPolygonAssemblyKeepsObservationOrderInsideTheExecutor() {
        var raw = source(GeometryKind.POLYGON,row(0,"POLYGON ((0 0,0.1 0,0.1 0.1,0 0.1,0 0))",100d)).dataset();
        var geometryType = raw.schema().apply("shape").dataType();
        var connect = functions.udf((org.apache.spark.sql.api.java.UDF3<Geometry,Geometry,Geometry,Geometry>) (a,b,c) ->
                TrackGeodesicAreaGeometry.connect(List.of(
                        TrackGeodesicAreaGeometry.footprint(a,null,false,5000),
                        TrackGeodesicAreaGeometry.footprint(b,null,false,5000),
                        TrackGeodesicAreaGeometry.footprint(c,null,false,5000)),5000),geometryType);
        String job = UUID.randomUUID().toString(); spark.sparkContext().setJobGroup(job,"geodesic polygon assembly preflight",false);
        Dataset<Row> plan;
        try {
            plan = raw.withColumn("area",connect.apply(functions.expr("ST_SetSRID(shape,4326)"),
                    functions.expr("ST_SetSRID(ST_GeomFromWKT('POLYGON ((2 0,2.1 0,2.1 0.1,2 0.1,2 0))'),4326)"),
                    functions.expr("ST_SetSRID(ST_GeomFromWKT('POLYGON ((2 2,2.1 2,2.1 2.1,2 2.1,2 2))'),4326)")));
            plan.queryExecution().analyzed(); plan.schema();
            assertEquals(0,spark.sparkContext().statusTracker().getJobIdsForGroup(job).length);
        } finally { spark.sparkContext().clearJobGroup(); }
        Geometry result = plan.head().getAs("area");
        assertInstanceOf(MultiPolygon.class,result); assertTrue(result.isValid()); assertEquals(4326,result.getSRID());
        var factory = new GeometryFactory();
        assertTrue(result.covers(factory.createPoint(new CoordinateXY(1,0.05))));
        assertTrue(result.covers(factory.createPoint(new CoordinateXY(2.05,1))));
        assertFalse(result.covers(factory.createPoint(new CoordinateXY(1,1))),"must not make a whole-track hull");
    }

    @Test void geodesicFootprintStructSurvivesShuffleAndOrderedAggregation() {
        var raw = source(GeometryKind.POLYGON,
                row(2,"POLYGON ((2 2,2.1 2,2.1 2.1,2 2.1,2 2))",null),
                row(0,"POLYGON ((0 0,0.1 0,0.1 0.1,0 0.1,0 0))",null),
                row(1,"POLYGON ((2 0,2.1 0,2.1 0.1,2 0.1,2 0))",null),
                RowFactory.create("B",Timestamp.from(Instant.ofEpochSecond(0)),null,
                        "POLYGON ((179 -1,-179 -1,-179 1,179 1,179 -1),(179.5 -0.5,179.5 0.5,-179.5 0.5,-179.5 -0.5,179.5 -0.5))")).dataset();
        var geometryType = raw.schema().apply("shape").dataType();
        String job = UUID.randomUUID().toString(); spark.sparkContext().setJobGroup(job,"geodesic struct preflight",false);
        Dataset<Row> plan;
        try {
            var prepared = raw.withColumn("footprint",TrackGeodesicAreaColumns.footprint(
                    functions.expr("ST_SetSRID(shape,4326)"),raw.col("radius"),false,5000,geometryType)).repartition(2);
            var grouped = prepared.groupBy("track").agg(functions.collect_list(functions.struct(
                    prepared.col("time"),prepared.col("footprint"))).alias("observations"),functions.count(functions.lit(1)).alias("count"));
            Column ordered = functions.transform(functions.array_sort(grouped.col("observations"),
                    (left,right) -> functions.when(left.getField("time").lt(right.getField("time")),functions.lit(-1))
                            .when(left.getField("time").gt(right.getField("time")),functions.lit(1)).otherwise(functions.lit(0))),
                    entry -> entry.getField("footprint"));
            plan = grouped.select(grouped.col("track"),grouped.col("count"),
                    TrackGeodesicAreaColumns.connect(ordered,5000,geometryType).alias("area"));
            plan.queryExecution().analyzed(); plan.schema();
            assertEquals(0,spark.sparkContext().statusTracker().getJobIdsForGroup(job).length);
        } finally { spark.sparkContext().clearJobGroup(); }
        var rows = plan.orderBy("track").collectAsList(); assertEquals(2,rows.size());
        assertEquals(3L,rows.getFirst().getLong(1)); assertEquals(1L,rows.getLast().getLong(1));
        Geometry connection = rows.getFirst().getAs("area"), singleton = rows.getLast().getAs("area");
        var factory = new GeometryFactory();
        assertTrue(connection.covers(factory.createPoint(new CoordinateXY(1,0.05))));
        assertTrue(connection.covers(factory.createPoint(new CoordinateXY(2.05,1))));
        assertFalse(connection.covers(factory.createPoint(new CoordinateXY(1,1))));
        assertTrue(singleton.covers(factory.createPoint(new CoordinateXY(179.2,0))));
        assertFalse(singleton.covers(factory.createPoint(new CoordinateXY(180,0))));
        assertFalse(singleton.covers(factory.createPoint(new CoordinateXY(0,0))));
        for (Geometry area : List.of(connection,singleton)) {
            assertInstanceOf(MultiPolygon.class,area); assertTrue(area.isValid()); assertEquals(4326,area.getSRID());
        }
    }

    @Test void geodesicStructKeepsPolarRenderClosuresOutOfItsTrueVerticesAndPreservesEachRadius() {
        var raw = source(GeometryKind.POINT,row(0,"POINT (0 89)",200_000d),row(1,"POINT (0 89)",300_000d)).dataset();
        var geometryType = raw.schema().apply("shape").dataType();
        var prepared = raw.withColumn("footprint",TrackGeodesicAreaColumns.footprint(
                functions.expr("ST_SetSRID(shape,4326)"),raw.col("radius"),true,10_000,geometryType)).repartition(2);
        var rows = prepared.withColumn("area",TrackGeodesicAreaColumns.connect(
                functions.array(prepared.col("footprint")),10_000,geometryType)).orderBy("time").collectAsList();
        assertEquals(2,rows.size());
        for (Row row : rows) {
            Row footprint = row.getAs("footprint");
            assertEquals(0d,footprint.getDouble(0)); assertEquals(89d,footprint.getDouble(1));
            List<Row> vertices = footprint.getList(2);
            assertTrue(vertices.stream().noneMatch(vertex -> Math.abs(vertex.getDouble(1))==90));
            Geometry area = row.getAs("area");
            assertTrue(Arrays.stream(area.getCoordinates()).anyMatch(coordinate -> coordinate.y==90));
            assertTrue(area.equalsTopo(footprint.getAs(3)));
            assertEquals(4326,area.getSRID()); assertTrue(area.isValid());
        }
        Geometry small = rows.getFirst().getAs("area"), large = rows.getLast().getAs("area");
        assertTrue(large.covers(small));
    }

    @Test void geometryLineageIncludesBothOriginalGeometryAndBufferField() {
        for(boolean geodesic:List.of(false,true)) {
        var raw = geodesic ? wgs84(GeometryKind.POINT) : source(GeometryKind.POINT);
        var asset = new TaskLineageEvidence.Asset("input",TaskLineageEvidence.AssetRole.INPUT,TaskLineageEvidence.AssetKind.JDBC_TABLE,
                null,null,null,null,UUID.randomUUID(),null,null,"events",null,null,"events");
        var fields = new LinkedHashMap<String,CatalystLineageMetadata.InputField>();
        raw.schema().columns().forEach(c -> fields.put(c.name(),new CatalystLineageMetadata.InputField("input:"+c.name(),null)));
        var source = new SparkCanvasTable(raw.schema(),CatalystLineageMetadata.markInput(raw.dataset(),"input-node",asset,fields));
        for (var options : List.of(area(TrackBufferMode.FIELD,null),history("coalesce(history,radius)",List.of(binding("history",-3,-1,TrackSummaryStatisticKind.MEAN))))) {
        var out = run(source,geodesic ? geodesic(options,NONE,TrackSplitBoundaryOption.GAP,200d) : config(options,NONE,TrackSplitBoundaryOption.GAP));
        var target = new TaskLineageEvidence.Asset("output",TaskLineageEvidence.AssetRole.OUTPUT,TaskLineageEvidence.AssetKind.JDBC_TABLE,
                null,TaskLineageEvidence.WriteMode.APPEND,null,null,UUID.randomUUID(),null,null,"result",null,null,"result");
        var candidate = new CatalystLineageOutputCandidate("flow","out","JDBC_OUTPUT","write",out.dataset(),target,
                out.schema().columns().stream().map(c -> new CatalystLineageOutputCandidate.TargetField("out:"+c.name(),null,c.name(),c.name(),TaskLineageEvidence.OutputEffect.WRITTEN_UNKNOWN_SOURCE)).toList());
        var flow = new CatalystLineageAnalyzer().analyze(List.of(candidate)).flows().getFirst();
        assertEquals(TaskLineageEvidence.Coverage.FIELD_COMPLETE,flow.coverage(),() -> flow.warnings().toString());
        for (String field : List.of("shape","radius")) assertTrue(flow.fieldEdges().stream().anyMatch(e ->
                e.target().localFieldKey().equals("out:geometry") && e.source().localFieldKey().equals("input:"+field)));
        }
        }
    }

    @Test void everyReconstructOutputFieldHasCompleteInputLineage() {
        var raw = source(GeometryKind.POINT,row(0,"POINT (0 0)",2d),row(1,"POINT (10 0)",4d));
        var asset = new TaskLineageEvidence.Asset("input",TaskLineageEvidence.AssetRole.INPUT,
                TaskLineageEvidence.AssetKind.JDBC_TABLE,null,null,null,null,UUID.randomUUID(),null,null,
                "events",null,null,"events");
        var fields = new LinkedHashMap<String,CatalystLineageMetadata.InputField>();
        raw.schema().columns().forEach(column -> fields.put(column.name(),
                new CatalystLineageMetadata.InputField("input:" + column.name(),null)));
        var source = new SparkCanvasTable(raw.schema(),
                CatalystLineageMetadata.markInput(raw.dataset(),"input-node",asset,fields));
        var base = config(area(TrackBufferMode.FIELD,null),NONE,TrackSplitBoundaryOption.GAP);
        var configuration = new TrackReconstructConfiguration(base.sourceTableName(),base.pointGeometryColumnName(),
                base.trackIdColumns(),base.timeColumnName(),base.distanceMethod(),base.boundaries(),List.of(
                new TrackSummaryStatistic(UUID.randomUUID().toString(),TrackSummaryStatisticKind.COUNT,null,"summary_count"),
                new TrackSummaryStatistic(UUID.randomUUID().toString(),TrackSummaryStatisticKind.COUNT_FIELD,"radius","radius_count"),
                new TrackSummaryStatistic(UUID.randomUUID().toString(),TrackSummaryStatisticKind.SUM,"radius","total"),
                new TrackSummaryStatistic(UUID.randomUUID().toString(),TrackSummaryStatisticKind.MEAN,"radius","mean_radius"),
                new TrackSummaryStatistic(UUID.randomUUID().toString(),TrackSummaryStatisticKind.FIRST,"wkt","first_wkt"),
                new TrackSummaryStatistic(UUID.randomUUID().toString(),TrackSummaryStatisticKind.LAST,"wkt","last_wkt")),
                base.outputTableName(),base.outputGeometryColumnName(),base.startTimeColumnName(),base.endTimeColumnName(),
                base.pointCountColumnName(),base.reconstruction());
        var output = run(source,configuration);
        var target = new TaskLineageEvidence.Asset("output",TaskLineageEvidence.AssetRole.OUTPUT,
                TaskLineageEvidence.AssetKind.JDBC_TABLE,null,TaskLineageEvidence.WriteMode.APPEND,null,null,
                UUID.randomUUID(),null,null,"result",null,null,"result");
        var candidate = new CatalystLineageOutputCandidate("flow","out","JDBC_OUTPUT","write",output.dataset(),target,
                output.schema().columns().stream().map(column -> new CatalystLineageOutputCandidate.TargetField(
                        "out:" + column.name(),null,column.name(),column.name(),
                        TaskLineageEvidence.OutputEffect.WRITTEN_UNKNOWN_SOURCE)).toList());
        var flow = new CatalystLineageAnalyzer().analyze(List.of(candidate)).flows().getFirst();

        assertEquals(TaskLineageEvidence.Coverage.FIELD_COMPLETE,flow.coverage(),() -> flow.warnings().toString());
        assertTrue(flow.fieldEdges().stream().allMatch(edge -> edge.source().localFieldKey().startsWith("input:")));
        assertTrue(flow.fields().stream().filter(field -> field.localAssetKey().equals("output"))
                .noneMatch(field -> field.outputEffect() == TaskLineageEvidence.OutputEffect.WRITTEN_UNKNOWN_SOURCE));
        Map<String,String> expectedSources = Map.ofEntries(
                Map.entry("track","track"),Map.entry("start","time"),Map.entry("end","time"),
                Map.entry("observations","time"),Map.entry("summary_count","time"),
                Map.entry("radius_count","radius"),Map.entry("total","radius"),Map.entry("mean_radius","radius"),
                Map.entry("first_wkt","wkt"),Map.entry("last_wkt","wkt"),Map.entry("geometry","shape"));
        expectedSources.forEach((targetField,sourceField) -> assertTrue(flow.fieldEdges().stream().anyMatch(edge ->
                edge.target().localFieldKey().equals("out:" + targetField)
                        && edge.source().localFieldKey().equals("input:" + sourceField)),
                () -> targetField + " should depend on " + sourceField));
        assertTrue(flow.fieldEdges().stream().filter(edge -> edge.target().localFieldKey().equals("out:track"))
                .allMatch(edge -> edge.derivationType() == TaskLineageEvidence.DerivationType.DIRECT));
        assertTrue(flow.fieldEdges().stream().filter(edge -> !edge.target().localFieldKey().equals("out:track"))
                .allMatch(edge -> edge.derivationType() == TaskLineageEvidence.DerivationType.AGGREGATED));
    }

    @Test void largeSingleTrackStaysLazyAndBuildsOneExecutorAggregatedPath() {
        int observationCount = 20_000;
        Dataset<Row> data = spark.range(observationCount).select(
                functions.lit("A").alias("track"),
                functions.to_timestamp(functions.from_unixtime(functions.col("id"))).alias("time"),
                functions.lit(1d).alias("radius"),
                functions.expr("ST_SetSRID(ST_Point(CAST(id AS DOUBLE), 0D), 3857)").alias("shape"));
        List<CanvasColumnSchema> columns = List.of(field("track",PlatformDataType.STRING),
                field("time",PlatformDataType.TIMESTAMP),field("radius",PlatformDataType.DOUBLE),
                new CanvasColumnSchema("shape",PlatformDataType.GEOMETRY,null,null,null,false,null,false,false,null,
                        new GeometryTypeDefinition(GeometryKind.POINT,new CrsReference("EPSG",3857),CoordinateDimension.XY)));
        var source = new SparkCanvasTable(new CanvasTableSchema("events",null,columns,
                CanvasDatasetKind.BOUNDED,null,null),data);
        var configuration = new TrackReconstructConfiguration("events","shape",List.of("track"),"time",
                SpatialDistanceMethod.PLANAR,NONE,List.of(new TrackSummaryStatistic(UUID.randomUUID().toString(),
                TrackSummaryStatisticKind.SUM,"radius","total")),"result","geometry","start","end","observations",
                new TrackReconstructOptions(TrackReconstructSemantics.ORDERED_SEGMENTS,List.of(),
                        TrackSplitBoundaryOption.GAP,null));
        String job = UUID.randomUUID().toString();
        spark.sparkContext().setJobGroup(job,"large track preflight",false);
        SparkCanvasTable output;
        try {
            output = run(source,configuration);
            output.dataset().queryExecution().analyzed();
            assertEquals(0,spark.sparkContext().statusTracker().getJobIdsForGroup(job).length);
        } finally {
            spark.sparkContext().clearJobGroup();
        }
        Row row = output.dataset().head();
        assertEquals(observationCount,row.<Long>getAs("observations"));
        assertEquals((double) observationCount,row.<Double>getAs("total"));
        Geometry geometry = row.getAs("geometry");
        assertEquals(observationCount,geometry.getNumPoints());
        assertEquals(observationCount - 1d,geometry.getLength(),1e-8);
    }

    @Test void historyRadiusIsComputedBeforeGapsAndDoesNotLeakBindingColumns() {
        var source = source(GeometryKind.POINT,row(0,"POINT (0 0)",2d),row(1,"POINT (100 0)",4d),row(2,"POINT (200 0)",8d));
        var options = history("coalesce(history, radius)",List.of(binding("history",-3,-1,TrackSummaryStatisticKind.MEAN)));
        var c = config(options,new TrackBoundaryConfiguration(null,null,50d,SpatialDistanceUnit.METERS),TrackSplitBoundaryOption.GAP);
        var out = run(source,c);
        var rows = out.dataset().orderBy("start").collectAsList();
        assertEquals(3,rows.size());
        assertEquals(List.of(4d,4d,6d),rows.stream().map(r -> r.<Geometry>getAs("geometry").getEnvelopeInternal().getWidth()).toList());
        assertFalse(Arrays.asList(out.dataset().columns()).contains("history"));
        assertEquals(source.schema().columns().size(),source.dataset().schema().fieldNames().length);
    }

    @Test void bufferWindowsNeverCrossTrackOrFixedPeriodAndSharedEndpointsKeepRadius() {
        var source = source(GeometryKind.POINT,row(0,"POINT (0 0)",2d),row(1,"POINT (10 0)",4d),row(10,"POINT (20 0)",8d),
                RowFactory.create("B",Timestamp.from(Instant.ofEpochSecond(2)),32d,"POINT (30 0)"));
        var boundaries = new TrackBoundaryConfiguration(null,null,5d,SpatialDistanceUnit.METERS,
                new TrackFixedTimeBoundary(5,TrackTimeBoundaryUnit.SECONDS,"1970-01-01T00:00:00Z","UTC"));
        var c = config(history("coalesce(history,radius)",List.of(binding("history",-3,-1,TrackSummaryStatisticKind.MEAN))),boundaries,TrackSplitBoundaryOption.FINISH_LAST);
        var rows = run(source,c).dataset().orderBy("track","start").collectAsList();
        assertEquals(4,rows.size());
        assertEquals(2L,rows.get(0).<Long>getAs("observations"));
        assertEquals(4d,rows.get(0).<Geometry>getAs("geometry").getEnvelopeInternal().getHeight(),1e-8);
        assertEquals(4d,rows.get(1).<Geometry>getAs("geometry").getEnvelopeInternal().getWidth(),1e-8);
        assertEquals(16d,rows.get(2).<Geometry>getAs("geometry").getEnvelopeInternal().getWidth(),1e-8);
        assertEquals(64d,rows.get(3).<Geometry>getAs("geometry").getEnvelopeInternal().getWidth(),1e-8);
    }

    @Test void numericWindowFunctionsHaveExplicitNullAndEmptyFrameSemantics() {
        var source = source(GeometryKind.POINT,row(0,"POINT (0 0)",null),row(1,"POINT (0 0)",2d),row(2,"POINT (0 0)",4d),row(3,"POINT (0 0)",null));
        var kinds = List.of(TrackSummaryStatisticKind.COUNT_FIELD,TrackSummaryStatisticKind.SUM,TrackSummaryStatisticKind.MEAN,
                TrackSummaryStatisticKind.MIN,TrackSummaryStatisticKind.MAX,TrackSummaryStatisticKind.RANGE,TrackSummaryStatisticKind.STDDEV,
                TrackSummaryStatisticKind.VARIANCE,TrackSummaryStatisticKind.FIRST,TrackSummaryStatisticKind.LAST);
        var bindings = kinds.stream().map(k -> binding(k.name().toLowerCase(Locale.ROOT),-1,1,k)).toList();
        var c = config(history("1",bindings),NONE,TrackSplitBoundaryOption.GAP);
        var issues = new Issues();
        var output = TrackBufferWindowPlan.prepare(source,c,issues).dataset().orderBy("time").collectAsList();
        assertFalse(issues.hasErrors(),issues::toString); var r = output.get(1);
        assertEquals(2L,r.<Long>getAs("count_field"));assertEquals(6d,r.<Double>getAs("sum"));assertEquals(3d,r.<Double>getAs("mean"));
        assertEquals(2d,r.<Double>getAs("min"));assertEquals(4d,r.<Double>getAs("max"));assertEquals(2d,r.<Double>getAs("range"));
        assertEquals(Math.sqrt(2),r.<Double>getAs("stddev"),1e-8);assertEquals(2d,r.<Double>getAs("variance"),1e-8);
        assertNull(r.getAs("first"));assertEquals(4d,r.<Double>getAs("last"));
        var empty = config(history("1",kinds.stream().map(k -> binding(k.name().toLowerCase(Locale.ROOT),10,10,k)).toList()),NONE,TrackSplitBoundaryOption.GAP);
        var first = TrackBufferWindowPlan.prepare(source,empty,new Issues()).dataset().head();
        for (var kind : kinds) if (kind == TrackSummaryStatisticKind.COUNT_FIELD) assertEquals(0L,first.<Long>getAs("count_field"));
        else assertNull(first.getAs(kind.name().toLowerCase(Locale.ROOT)));
    }

    @Test void windowDraftValidationUsesIndexedPathsAndCannotReferenceOtherBindingsOrInternalColumns() {
        var source = source(GeometryKind.POINT);
        var bad = List.of(new TrackBufferWindowBinding("radius","radius",-1,0,TrackSummaryStatisticKind.MEAN),
                new TrackBufferWindowBinding("bad","other",-1,0,TrackSummaryStatisticKind.MEAN),
                new TrackBufferWindowBinding("bad","radius",2,-1,TrackSummaryStatisticKind.MEAN),
                new TrackBufferWindowBinding("text","wkt",-1,0,TrackSummaryStatisticKind.MEAN),
                new TrackBufferWindowBinding("value","radius",-1001,0,TrackSummaryStatisticKind.ANY),
                new TrackBufferWindowBinding("__datascalpel_unsafe","radius",-1,0,TrackSummaryStatisticKind.MEAN));
        var issues = invalid(source,config(history("radius",bad),NONE,TrackSplitBoundaryOption.GAP));
        assertTrue(issues.paths.stream().allMatch(p -> p.startsWith("configuration.reconstruction.areaGeometry.windowBindings[")));
        for (var code : List.of("DUPLICATE_COLUMN_NAME","COLUMN_NOT_FOUND","TRACK_BUFFER_WINDOW_NUMERIC_REQUIRED","INVALID_TRACK_BUFFER_WINDOW")) assertTrue(issues.codes.contains(code));
        var tooMany = java.util.stream.IntStream.range(0,33).mapToObj(i -> binding("history"+i,-1,0,TrackSummaryStatisticKind.MEAN)).toList();
        assertTrue(invalid(source,config(history("radius",tooMany),NONE,TrackSplitBoundaryOption.GAP)).codes.contains("TRACK_BUFFER_WINDOW_COUNT_EXCEEDED"));
        assertTrue(invalid(source,config(history("__datascalpel_track_segment",List.of()),NONE,TrackSplitBoundaryOption.GAP)).codes.contains("INVALID_TRACK_BUFFER_EXPRESSION"));
        var inactive = new TrackAreaGeometryOptions(true,TrackBufferMode.FIELD,"radius","bad expression",SpatialDistanceUnit.METERS,bad);
        run(source,config(inactive,NONE,TrackSplitBoundaryOption.GAP));
    }

    @Test void windowPreflightIsLazyAndMissingHistoryRequiresAnExplicitFallback() {
        var source = source(GeometryKind.POINT,row(0,"POINT (0 0)",2d));
        var c = config(history("history",List.of(binding("history",-3,-1,TrackSummaryStatisticKind.MEAN))),NONE,TrackSplitBoundaryOption.GAP);
        String job=UUID.randomUUID().toString();spark.sparkContext().setJobGroup(job,"buffer window preflight",false);
        var out = run(source,c); out.dataset().queryExecution().analyzed();
        assertEquals(0,spark.sparkContext().statusTracker().getJobIdsForGroup(job).length);spark.sparkContext().clearJobGroup();
        assertTrue(causes(assertThrows(Exception.class, () -> out.dataset().head())).contains("TRACK_BUFFER_DISTANCE_INVALID"));
    }

    @Test void bufferAndSplitBindingsHaveSeparateScopesAndKeepTheSameObservationIdentity() {
        var source = source(GeometryKind.POINT,row(0,"POINT (0 0)",2d),row(1,"POINT (10 0)",4d),row(2,"POINT (20 0)",8d));
        var base = config(history("coalesce(history,radius)",List.of(binding("history",-3,-1,TrackSummaryStatisticKind.MEAN))),NONE,TrackSplitBoundaryOption.GAP);
        var c = new TrackReconstructConfiguration(base.sourceTableName(),base.pointGeometryColumnName(),base.trackIdColumns(),base.timeColumnName(),base.distanceMethod(),
                base.boundaries(),base.summaryStatistics(),base.outputTableName(),base.outputGeometryColumnName(),base.startTimeColumnName(),base.endTimeColumnName(),base.pointCountColumnName(),
                new TrackReconstructOptions(TrackReconstructSemantics.ORDERED_SEGMENTS,List.of(),TrackSplitBoundaryOption.GAP,
                        new TrackSplitExpression("history > 3",List.of(new TrackFieldWindowBinding("history","radius",-1))),null,base.reconstruction().areaGeometry()));
        var rows = run(source,c).dataset().orderBy("start").collectAsList();
        assertEquals(2,rows.size());assertEquals(2L,rows.getFirst().<Long>getAs("observations"));
        assertEquals(6d,rows.getLast().<Geometry>getAs("geometry").getEnvelopeInternal().getWidth(),1e-8);
    }

    @Test void bufferWindowsUseDeclaredTieBreakersAndRejectAmbiguousObservationOrder() {
        var source = source(GeometryKind.POINT,row(0,"POINT (10 0)",4d),row(0,"POINT (0 0)",2d));
        var base = config(history("coalesce(history,radius)",List.of(binding("history",-3,-1,TrackSummaryStatisticKind.MEAN))),
                new TrackBoundaryConfiguration(null,null,5d,SpatialDistanceUnit.METERS),TrackSplitBoundaryOption.GAP);
        var c = new TrackReconstructConfiguration(base.sourceTableName(),base.pointGeometryColumnName(),base.trackIdColumns(),base.timeColumnName(),base.distanceMethod(),
                base.boundaries(),base.summaryStatistics(),base.outputTableName(),base.outputGeometryColumnName(),base.startTimeColumnName(),base.endTimeColumnName(),base.pointCountColumnName(),
                new TrackReconstructOptions(TrackReconstructSemantics.ORDERED_SEGMENTS,List.of("radius"),TrackSplitBoundaryOption.GAP,null,null,base.reconstruction().areaGeometry()));
        var rows = run(source,c).dataset().collectAsList();assertEquals(2,rows.size());
        rows.forEach(r -> assertEquals(4d,r.<Geometry>getAs("geometry").getEnvelopeInternal().getWidth(),1e-8));
        var ambiguous = run(source,base);
        assertTrue(causes(assertThrows(Exception.class, () -> ambiguous.dataset().head())).contains("TRACK_OBSERVATION_ORDER_NOT_UNIQUE"));
    }

    @Test void geodesicAreaOperatorIsLazyAndUsesOriginalGapsWithSharedFootprints() {
        var source = wgs84(GeometryKind.POINT, row(0,"POINT (0 0)",2000d),row(1,"POINT (0.001 0)",2000d),
                row(10,"POINT (0.01 0)",2000d));
        var boundary = new TrackBoundaryConfiguration(null,null,500d,SpatialDistanceUnit.METERS);
        for (var option : TrackSplitBoundaryOption.values()) {
            String job=UUID.randomUUID().toString();spark.sparkContext().setJobGroup(job,"geodesic area plan",false);
            SparkCanvasTable output;
            try {
                output=run(source,geodesic(area(TrackBufferMode.FIELD,null),boundary,option,200d));
                output.dataset().queryExecution().analyzed();
                assertEquals(0,spark.sparkContext().statusTracker().getJobIdsForGroup(job).length);
            } finally { spark.sparkContext().clearJobGroup(); }
            var rows=output.dataset().orderBy("start").collectAsList();
            assertEquals(2,rows.size(),"overlapping buffers must not suppress original-observation gaps");
            assertEquals(option==TrackSplitBoundaryOption.FINISH_LAST ? 3L:2L,rows.getFirst().<Long>getAs("observations"));
            assertEquals(option==TrackSplitBoundaryOption.START_NEXT ? 2L:1L,rows.getLast().<Long>getAs("observations"));
            for(var row:rows) {
                Geometry geometry=row.getAs("geometry");assertInstanceOf(MultiPolygon.class,geometry);
                assertTrue(geometry.isValid());assertEquals(4326,geometry.getSRID());
            }
            assertEquals(option==TrackSplitBoundaryOption.GAP ? 6000d:8000d,
                    rows.stream().mapToDouble(row->row.<Double>getAs("total")).sum());
            assertEquals(GeometryKind.MULTIPOLYGON,output.schema().columns().getLast().geometry().kind());
        }
    }

    @Test void geodesicPolygonGapsUseRegionsNotCentroidsOrRenderedChords() {
        // First two filled regions overlap, despite very different centroids.
        var source=wgs84(GeometryKind.POLYGON,
                row(0,"POLYGON ((0 0,0.1 0,0.1 0.01,0 0.01,0 0))",1d),
                row(1,"POLYGON ((0.09 0,0.2 0,0.2 0.01,0.09 0.01,0.09 0))",1d),
                row(2,"POLYGON ((0.3 0,0.31 0,0.31 0.01,0.3 0.01,0.3 0))",1d));
        var rows=run(source,geodesic(area(TrackBufferMode.NONE,null),
                new TrackBoundaryConfiguration(null,null,1d,SpatialDistanceUnit.METERS),TrackSplitBoundaryOption.GAP,1000d))
                .dataset().orderBy("start").collectAsList();
        assertEquals(2,rows.size());assertEquals(2L,rows.getFirst().<Long>getAs("observations"));
        // Its hole is above the raw WKT shell, but inside the true geodesic shell.
        var valid=wgs84(GeometryKind.POLYGON,row(0,
                "POLYGON ((-45 60,45 60,45 65,-45 65,-45 60),(-2 70,-2 71,2 71,2 70,-2 70))",1d));
        Geometry original=valid.dataset().head().getAs("shape");assertFalse(original.isValid());
        Geometry actual=run(valid,geodesic(area(TrackBufferMode.NONE,null),NONE,TrackSplitBoundaryOption.GAP,20_000d))
                .dataset().head().getAs("geometry");
        assertTrue(actual.isValid());
        assertTrue(java.util.stream.IntStream.range(0,actual.getNumGeometries())
                .anyMatch(i->((Polygon)actual.getGeometryN(i)).getNumInteriorRing()>0));
    }

    @Test void geodesicFixedPeriodsAndBufferWindowsKeepTheirObservationScope() {
        var boundary=new TrackBoundaryConfiguration(null,null,null,null,
                new TrackFixedTimeBoundary(5,TrackTimeBoundaryUnit.SECONDS,"1970-01-01T00:00:00Z","UTC"));
        var source=wgs84(GeometryKind.POINT,row(0,"POINT (179.99 70)",1000d),
                row(10,"POINT (179.99 70)",2000d),row(20,"POINT EMPTY",5d),row(30,null,5d));
        var options=history("coalesce(history,radius)",List.of(binding("history",-3,-1,TrackSummaryStatisticKind.MEAN)));
        for(var option:TrackSplitBoundaryOption.values()) {
            var rows=run(source,geodesic(options,boundary,option,200d)).dataset().orderBy("start").collectAsList();
            assertEquals(2,rows.size());rows.forEach(row->assertEquals(1L,row.<Long>getAs("observations")));
            Geometry small=rows.getFirst().getAs("geometry"),large=rows.getLast().getAs("geometry");
            assertEquals(2,small.getNumGeometries());assertEquals(2,large.getNumGeometries());
            assertTrue(large.getArea()>small.getArea()*3,"buffer history must reset at the fixed period");
        }
    }

    @Test void geodesicSamplingIsExplicitAndInactiveLineOrAreaValuesCannotAffectResults() {
        var source=wgs84(GeometryKind.POINT,row(0,"POINT (0 0)",100d));
        var c=geodesic(area(TrackBufferMode.FIELD,null),NONE,TrackSplitBoundaryOption.GAP,-1d);
        assertTrue(invalid(source,c).codes.contains("INVALID_TRACK_GEODESIC_SEGMENT_LENGTH"));
        var good=geodesic(area(TrackBufferMode.FIELD,null),NONE,TrackSplitBoundaryOption.GAP,200d);
        var old=good.reconstruction();
        var withBadLine=new TrackReconstructConfiguration(good.sourceTableName(),good.pointGeometryColumnName(),good.trackIdColumns(),
                good.timeColumnName(),good.distanceMethod(),good.boundaries(),good.summaryStatistics(),good.outputTableName(),
                good.outputGeometryColumnName(),good.startTimeColumnName(),good.endTimeColumnName(),good.pointCountColumnName(),
                new TrackReconstructOptions(old.semantics(),old.orderByColumns(),old.splitBoundaryOption(),old.splitExpression(),
                        new TrackPathGeometryOptions(TrackPathGeometryMode.METHOD_PATH,-1d,null),old.areaGeometry()));
        assertTrue(run(source,good).dataset().head().<Geometry>getAs("geometry")
                .equalsExact(run(source,withBadLine).dataset().head().getAs("geometry")));
        var inactive=new TrackAreaGeometryOptions(true,TrackBufferMode.FIELD,"radius",null,SpatialDistanceUnit.METERS,List.of(),
                new TrackGeodesicAreaOptions(-1d,null));
        assertEquals(1,run(source(GeometryKind.POINT,row(0,"POINT (0 0)",1d)),config(inactive,NONE,TrackSplitBoundaryOption.GAP)).dataset().count());
    }

    @Test void sharedGeodesicEndpointsRetainTheirPreSplitWindowRadius() {
        var source=wgs84(GeometryKind.POINT,row(0,"POINT (0 0)",100d),row(1,"POINT (0.001 0)",200d),
                row(10,"POINT (0.01 0)",400d));
        var options=history("coalesce(history,radius)",List.of(binding("history",-3,-1,TrackSummaryStatisticKind.MEAN)));
        var observations=source.dataset().orderBy("time").collectAsList();
        var expectedFootprints=new ArrayList<TrackGeodesicAreaGeometry.Footprint>();
        double[] radii={100d,100d,150d};
        for(int i=0;i<3;i++) expectedFootprints.add(TrackGeodesicAreaGeometry.footprint(observations.get(i).getAs("shape"),radii[i],true,20d));
        for(var option:TrackSplitBoundaryOption.values()) {
            var rows=run(source,geodesic(options,new TrackBoundaryConfiguration(null,null,500d,SpatialDistanceUnit.METERS),option,20d))
                    .dataset().orderBy("start").collectAsList();
            assertEquals(2,rows.size());
            var first=expectedFootprints.subList(0,option==TrackSplitBoundaryOption.FINISH_LAST ? 3:2);
            var last=expectedFootprints.subList(option==TrackSplitBoundaryOption.START_NEXT ? 1:2,3);
            assertTrue(TrackGeodesicAreaGeometry.connect(first,20d).equalsTopo(rows.getFirst().getAs("geometry")));
            assertTrue(TrackGeodesicAreaGeometry.connect(last,20d).equalsTopo(rows.getLast().getAs("geometry")));
        }
    }

    @Test void invalidGeodesicSourcesFailOnConsumptionWithoutExposingCoordinates() {
        for(String wkt:List.of("POLYGON ((0 0,1 1,1 0,0 1,0 0))","POLYGON ((181 0,182 0,182 1,181 1,181 0))")) {
            var output=run(wgs84(GeometryKind.POLYGON,row(0,wkt,1d)),geodesic(area(TrackBufferMode.NONE,null),NONE,TrackSplitBoundaryOption.GAP,200d));
            var message=causes(assertThrows(Exception.class,()->output.dataset().head()));
            assertTrue(message.contains("TRACK_AREA_GEOMETRY_INVALID") || message.contains("TRACK_GEODESIC_COORDINATE_INVALID"));
            assertFalse(message.contains(wkt));
        }
    }

    private TrackReconstructConfiguration geodesic(TrackAreaGeometryOptions area,TrackBoundaryConfiguration boundaries,
            TrackSplitBoundaryOption option,double step) {
        var c=config(new TrackAreaGeometryOptions(area.enabled(),area.bufferMode(),area.bufferField(),area.bufferExpression(),
                area.bufferUnit(),area.windowBindings(),new TrackGeodesicAreaOptions(step,SpatialDistanceUnit.METERS)),boundaries,option);
        return new TrackReconstructConfiguration(c.sourceTableName(),c.pointGeometryColumnName(),c.trackIdColumns(),c.timeColumnName(),
                SpatialDistanceMethod.GEODESIC,c.boundaries(),c.summaryStatistics(),c.outputTableName(),c.outputGeometryColumnName(),
                c.startTimeColumnName(),c.endTimeColumnName(),c.pointCountColumnName(),c.reconstruction());
    }

    private SparkCanvasTable wgs84(GeometryKind kind,Row... rows) {
        var source=source(kind,rows);
        var columns=new ArrayList<>(source.schema().columns());
        columns.set(columns.size()-1,new CanvasColumnSchema("shape",PlatformDataType.GEOMETRY,null,null,null,true,null,false,false,null,
                new GeometryTypeDefinition(kind,new CrsReference("EPSG",4326),CoordinateDimension.XY)));
        return new SparkCanvasTable(new CanvasTableSchema("events",null,columns,CanvasDatasetKind.BOUNDED,null,null),
                source.dataset().withColumn("shape",functions.expr("ST_SetSRID(shape,4326)")));
    }

    private TrackBufferWindowBinding binding(String name,int start,int end,TrackSummaryStatisticKind kind) {
        return new TrackBufferWindowBinding(name,"radius",start,end,kind);
    }
    private TrackAreaGeometryOptions history(String expression,List<TrackBufferWindowBinding> bindings) {
        return new TrackAreaGeometryOptions(true,TrackBufferMode.EXPRESSION,null,expression,SpatialDistanceUnit.METERS,bindings);
    }

    private TrackAreaGeometryOptions area(TrackBufferMode mode,String expression) {
        return new TrackAreaGeometryOptions(true,mode,"radius",expression,SpatialDistanceUnit.METERS);
    }
    private TrackReconstructConfiguration config(TrackAreaGeometryOptions area,TrackBoundaryConfiguration boundaries,TrackSplitBoundaryOption option) {
        return new TrackReconstructConfiguration("events","shape",List.of("track"),"time",SpatialDistanceMethod.PLANAR,boundaries,
                List.of(new TrackSummaryStatistic(UUID.randomUUID().toString(),TrackSummaryStatisticKind.SUM,"radius","total")),
                "result","geometry","start","end","observations",new TrackReconstructOptions(
                        TrackReconstructSemantics.ORDERED_SEGMENTS,List.of(),option,null,null,area));
    }
    private SparkCanvasTable run(SparkCanvasTable source,TrackReconstructConfiguration c) {
        var issues = new Issues(); var result = apply(source,c,issues); assertFalse(issues.hasErrors(),issues::toString);
        assertSame(source,result.propagatedTables().get("events")); assertEquals(List.of("events","result"),new ArrayList<>(result.propagatedTables().keySet()));
        return result.propagatedTables().get("result");
    }
    private Issues invalid(SparkCanvasTable source,TrackReconstructConfiguration c) {
        var issues = new Issues(); assertTrue(apply(source,c,issues).propagatedTables().isEmpty()); assertTrue(issues.hasErrors()); return issues;
    }
    private CanvasNodeOperationResult apply(SparkCanvasTable source,TrackReconstructConfiguration c,Issues issues) {
        return new TrackReconstructNodeOperator().apply(new TrackReconstructNodeDefinition(UUID.randomUUID().toString(),"重建",
                        new CanvasNodeLayout(0d,0d,352d,216d),c),Map.of("events",source),
                new CanvasNodeOperationContext(spark,MetadataIndex.create(new MetadataSnapshot(List.of(),List.of())),issues,new SchemaOnlyCanvasNodeDataAccess(spark)));
    }
    private Row row(int time,String wkt,Double radius) { return RowFactory.create("A",Timestamp.from(Instant.ofEpochSecond(time)),radius,wkt); }
    private CanvasColumnSchema field(String name,PlatformDataType type) { return new CanvasColumnSchema(name,type,type == PlatformDataType.STRING ? 100 : null,null,null,true,null,false,false,null); }
    private SparkCanvasTable source(GeometryKind kind,Row... rows) {
        var columns = new ArrayList<>(List.of(field("track",PlatformDataType.STRING),field("time",PlatformDataType.TIMESTAMP),field("radius",PlatformDataType.DOUBLE),field("wkt",PlatformDataType.STRING)));
        var raw = spark.createDataFrame(Arrays.asList(rows),SparkTypeMapper.toStructType(columns));
        var data = raw.withColumn("shape",st_constructors.ST_GeomFromWKT(raw.col("wkt")));
        columns.add(new CanvasColumnSchema("shape",PlatformDataType.GEOMETRY,null,null,null,true,null,false,false,null,
                new GeometryTypeDefinition(kind,new CrsReference("EPSG",3857),CoordinateDimension.XY)));
        return new SparkCanvasTable(new CanvasTableSchema("events",null,columns,CanvasDatasetKind.BOUNDED,null,null),data);
    }
    private String causes(Throwable error) { var out = new StringBuilder(); for (Throwable e=error;e!=null;e=e.getCause()) out.append(e.getMessage()); return out.toString(); }
    private static class Issues implements CanvasNodeIssueSink {
        final List<String> codes=new ArrayList<>(),paths=new ArrayList<>();
        public void error(String code,String message,String path) { codes.add(code);paths.add(path); }
        public void warning(String code,String message,String path) { }
        public boolean hasErrors() { return !codes.isEmpty(); }
        public String toString() { return codes+" "+paths; }
    }
}
