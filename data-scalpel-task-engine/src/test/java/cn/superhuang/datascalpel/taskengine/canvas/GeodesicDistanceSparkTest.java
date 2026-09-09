package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.datascalpel.taskengine.spark.SedonaSparkSupport;
import org.apache.spark.sql.*;
import org.apache.spark.sql.api.java.UDF2;
import org.apache.spark.sql.api.java.UDF3;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructType;
import org.junit.jupiter.api.*;
import org.locationtech.jts.geom.*;

import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GeodesicDistanceSparkTest {
    private SparkSession spark;
    @BeforeAll void start() {
        spark=SedonaSparkSupport.initialize(SedonaSparkSupport.builder().master("local[1]").appName("geodesic-distance")
                .config("spark.ui.enabled",false).config("spark.driver.host","127.0.0.1").config("spark.driver.bindAddress","127.0.0.1")
                .config("spark.sql.shuffle.partitions",1).getOrCreate());
    }
    @AfterAll void stop() { if (spark!=null) spark.stop(); }

    private Dataset<Row> source(Row... rows) {
        var schema=new StructType().add("id",DataTypes.IntegerType,false)
                .add("firstWkt",DataTypes.StringType,true).add("secondWkt",DataTypes.StringType,true);
        return spark.createDataFrame(List.of(rows),schema).selectExpr("id",
                "ST_SetSRID(ST_GeomFromWKT(firstWkt),4326) AS first", "ST_SetSRID(ST_GeomFromWKT(secondWkt),4326) AS second");
    }

    private Dataset<Row> distancePlan(Dataset<Row> source) {
        var geometryType=source.schema().apply("first").dataType();
        var resultType=new StructType().add("distance",DataTypes.DoubleType,false).add("lower",DataTypes.DoubleType,false)
                .add("witnesses",geometryType,false);
        var expression=functions.udf((UDF2<Geometry,Geometry,Row>) (first,second) -> {
            var result=Wgs84GeometryDistance.nearest(first,second,0.0001);
            if (result==null) return null;
            var factory=new GeometryFactory(new PrecisionModel(),4326);
            // Keep the witnesses together with the distance from the same search result.
            var witnesses=factory.createLineString(new Coordinate[]{
                    new CoordinateXY(result.first().longitude(),result.first().latitude()),
                    new CoordinateXY(result.second().longitude(),result.second().latitude())});
            return RowFactory.create(result.distanceMetres(),result.lowerBoundMetres(),witnesses);
        },resultType);
        return source.withColumn("nearest",expression.apply(source.col("first"),source.col("second")));
    }

    @Test void analysisIsLazyAndExecutorsReturnDistanceAndActualNearestLocationsTogether() {
        var source=source(RowFactory.create(1,"POINT (0.007 0.01)","LINESTRING (-0.02 0,0.06 0)"),
                RowFactory.create(2,"LINESTRING (179.99 0,-179.99 0)","POINT (180 0)"),
                RowFactory.create(3,null,"POINT (0 0)"));
        String job=UUID.randomUUID().toString(); spark.sparkContext().setJobGroup(job,"geodesic distance preflight",false);
        Dataset<Row> plan;
        try {
            plan=distancePlan(source.repartition(2)); plan.queryExecution().analyzed(); plan.schema();
            assertEquals(0,spark.sparkContext().statusTracker().getJobIdsForGroup(job).length);
        } finally { spark.sparkContext().clearJobGroup(); }
        var rows=plan.orderBy("id").collectAsList(); assertEquals(3,rows.size());
        Row interior=rows.get(0).getAs("nearest"), crossing=rows.get(1).getAs("nearest");
        assertTrue(interior.getDouble(0)>1000); assertTrue(interior.getDouble(0)<1200);
        assertTrue(interior.getDouble(0)-interior.getDouble(1)<=0.01);
        LineString witnesses=interior.getAs(2); assertEquals(4326,witnesses.getSRID());
        assertEquals(0.007,witnesses.getCoordinateN(1).x,0.00001); assertEquals(0,witnesses.getCoordinateN(1).y,1e-12);
        assertTrue(crossing.getDouble(0)<0.01); assertNull(rows.get(2).getAs("nearest"));
    }

    @Test void polygonContainmentAndHoleDistanceAreEvaluatedOnExecutorsWithoutPlanningJobs() {
        var raw=source(RowFactory.create(1,"POINT (0.005 0.005)","POLYGON ((0 0,0.01 0,0.01 0.01,0 0.01,0 0))"),
                RowFactory.create(2,"POINT (0.005 0.005)","POLYGON ((0 0,0.01 0,0.01 0.01,0 0.01,0 0),(0.004 0.004,0.006 0.004,0.006 0.006,0.004 0.006,0.004 0.004))"));
        String job=UUID.randomUUID().toString(); spark.sparkContext().setJobGroup(job,"geodesic area distance preflight",false);
        Dataset<Row> plan;
        try {
            plan=distancePlan(raw.repartition(2)); plan.queryExecution().analyzed(); plan.schema();
            assertEquals(0,spark.sparkContext().statusTracker().getJobIdsForGroup(job).length);
        } finally { spark.sparkContext().clearJobGroup(); }
        var rows=plan.orderBy("id").collectAsList();
        Row inside=rows.getFirst().getAs("nearest"), hole=rows.getLast().getAs("nearest");
        assertEquals(0,inside.getDouble(0)); assertTrue(hole.getDouble(0)>100); assertTrue(hole.getDouble(0)<112);
        LineString common=inside.getAs(2); assertTrue(common.getCoordinateN(0).equals2D(common.getCoordinateN(1)));
        assertTrue(hole.getDouble(0)-hole.getDouble(1)<=0.0001);
    }

    @Test void invalidDataFailsAtConsumptionWithSafeCodeNotDuringPlanning() {
        var source=source(RowFactory.create(1,"POINT (0 0)","LINESTRING (0 0,181 0)"));
        var plan=distancePlan(source); plan.queryExecution().analyzed();
        var error=assertThrows(Exception.class,plan::collectAsList);
        boolean matched=false;
        for (Throwable cause=error;cause!=null;cause=cause.getCause()) {
            if (cause instanceof IllegalArgumentException && "GEODESIC_DISTANCE_COORDINATE_INVALID".equals(cause.getMessage())) {
                matched=true; assertNull(cause.getCause());
            }
        }
        assertTrue(matched);
    }

    @Test void exactShellHoleContactRemainsACommonZeroDistanceWitnessOnExecutors() {
        var raw=source(RowFactory.create(1,"POINT (0 2)","POLYGON ((0 0,4 0,4 4,0 4,0 0),(0 2,1 1,1 3,0 2))"));
        var plan=lazyPlan(raw.repartition(2));
        Row result=plan.collectAsList().getFirst().getAs("nearest");
        assertEquals(0,result.getDouble(0)); assertEquals(0,result.getDouble(1));
        LineString witnesses=result.getAs(2);
        assertTrue(witnesses.getCoordinateN(0).equals2D(new CoordinateXY(0,2)));
        assertTrue(witnesses.getCoordinateN(1).equals2D(witnesses.getCoordinateN(0)));
    }

    @Test void continuousCrossingsAndUnresolvedContactsFailSafelyOnlyAtConsumption() {
        assertSafeConsumptionFailure("POLYGON ((-45 60,45 60,0 70,0 65,-45 60))","GEODESIC_DISTANCE_GEOMETRY_INVALID");
        var line=net.sf.geographiclib.Geodesic.WGS84.InverseLine(0,0,1,1);
        var middle=line.Position(line.Distance()/2);
        var factory=new GeometryFactory(new PrecisionModel(),4326);
        var uncertain=factory.createPolygon(new Coordinate[]{new CoordinateXY(0,0),new CoordinateXY(1,1),
                new CoordinateXY(middle.lon2,middle.lat2),new CoordinateXY(0,1),new CoordinateXY(0,0)});
        assertSafeConsumptionFailure(uncertain.toText(),"GEODESIC_TOPOLOGY_PRECISION_NOT_REACHED");
    }

    private Dataset<Row> lazyPlan(Dataset<Row> source) {
        String job=UUID.randomUUID().toString(); spark.sparkContext().setJobGroup(job,"continuous geodesic preflight",false);
        try {
            var plan=distancePlan(source); plan.queryExecution().analyzed(); plan.schema();
            assertEquals(0,spark.sparkContext().statusTracker().getJobIdsForGroup(job).length);
            return plan;
        } finally { spark.sparkContext().clearJobGroup(); }
    }

    @Test void continuousRegionContainmentAndConnectivityAreEnforcedOnExecutors() {
        var plan=lazyPlan(source(RowFactory.create(1,"POINT (10 69)",
                "POLYGON ((-45 60,45 60,45 65,-45 65,-45 60),(-2 70,-2 71,2 71,2 70,-2 70))")));
        Row result=plan.collectAsList().getFirst().getAs("nearest");
        assertEquals(0,result.getDouble(0)); assertEquals(0,result.getDouble(1));
        assertSafeConsumptionFailure("POLYGON ((0 0,4 0,4 4,0 4,0 0),(0 2,1 1,2 2,1 3,0 2),(2 2,3 1,4 2,3 3,2 2))",
                "GEODESIC_DISTANCE_GEOMETRY_INVALID");
        assertSafeConsumptionFailure("POLYGON ((0 0,4 0,0 4,0 0),(2 0,2 -1,-1 -1,-1 2,0 2,1 1,2 0))",
                "GEODESIC_DISTANCE_GEOMETRY_INVALID");
    }

    private void assertSafeConsumptionFailure(String polygon,String code) {
        var plan=lazyPlan(source(RowFactory.create(1,"POINT (0 0)",polygon)));
        var error=assertThrows(Exception.class,plan::collectAsList);
        boolean matched=false;
        for (Throwable cause=error;cause!=null;cause=cause.getCause()) {
            if (cause instanceof IllegalArgumentException && code.equals(cause.getMessage())) {
                matched=true; assertNull(cause.getCause());
            }
        }
        assertTrue(matched,"expected safe code: "+code);
    }

    private Dataset<Row> thresholdPlan(Dataset<Row> source) {
        var predicate=functions.udf((UDF3<Geometry,Geometry,Double,Boolean>) (first,second,threshold)->
                threshold==null ? null : Wgs84GeometryDistance.withinDistance(first,second,threshold),DataTypes.BooleanType);
        return source.withColumn("within",predicate.apply(source.col("first"),source.col("second"),source.col("threshold")));
    }

    @Test void perRowDistanceThresholdsAreLazyAndResolveBothSidesOfACloseCutoff() {
        double exact=net.sf.geographiclib.Geodesic.WGS84.Inverse(0.01,0.007,0,0.007).s12;
        var raw=source(RowFactory.create(1,"POINT (0.007 0.01)","LINESTRING (-0.02 0,0.06 0)"),
                RowFactory.create(2,"POINT (0.007 0.01)","LINESTRING (-0.02 0,0.06 0)"),
                RowFactory.create(3,null,"POINT (0 0)"),RowFactory.create(4,"POINT (0 0)","POINT (0 0)"),
                RowFactory.create(5,"POINT (0.005 0.005)","POLYGON ((0 0,0.01 0,0.01 0.01,0 0.01,0 0),(0.004 0.004,0.006 0.004,0.006 0.006,0.004 0.006,0.004 0.004))"))
                .withColumn("threshold",functions.when(functions.col("id").equalTo(1),exact+0.001)
                        .when(functions.col("id").equalTo(2),exact-0.001).when(functions.col("id").equalTo(4),0d).otherwise(100d));
        String job=UUID.randomUUID().toString(); spark.sparkContext().setJobGroup(job,"distance cutoff preflight",false);
        Dataset<Row> plan;
        try {
            plan=thresholdPlan(raw.repartition(2)); plan.queryExecution().analyzed(); plan.schema();
            assertEquals(0,spark.sparkContext().statusTracker().getJobIdsForGroup(job).length);
        } finally { spark.sparkContext().clearJobGroup(); }
        var rows=plan.orderBy("id").collectAsList();
        assertEquals(Boolean.TRUE,rows.get(0).getAs("within")); assertEquals(Boolean.FALSE,rows.get(1).getAs("within"));
        assertNull(rows.get(2).getAs("within")); assertEquals(Boolean.TRUE,rows.get(3).getAs("within"));
        assertEquals(Boolean.FALSE,rows.get(4).getAs("within"));
    }

    @Test void unresolvedCutoffFailsAtConsumptionInsteadOfReturningAMadeUpBoolean() {
        double exact=net.sf.geographiclib.Geodesic.WGS84.Inverse(0,0,0.01,0.01).s12;
        var raw=source(RowFactory.create(1,"POINT (0 0)","POINT (0.01 0.01)"))
                .withColumn("threshold",functions.lit(exact));
        String job=UUID.randomUUID().toString(); spark.sparkContext().setJobGroup(job,"unresolved distance cutoff preflight",false);
        Dataset<Row> plan;
        try {
            plan=thresholdPlan(raw); plan.queryExecution().analyzed(); plan.schema();
            assertEquals(0,spark.sparkContext().statusTracker().getJobIdsForGroup(job).length);
        } finally { spark.sparkContext().clearJobGroup(); }
        var error=assertThrows(Exception.class,plan::collectAsList);
        boolean matched=false;
        for (Throwable cause=error;cause!=null;cause=cause.getCause()) {
            if (cause instanceof IllegalArgumentException && "GEODESIC_DISTANCE_PRECISION_NOT_REACHED".equals(cause.getMessage())) {
                matched=true; assertNull(cause.getCause());
            }
        }
        assertTrue(matched);
    }

    @Test void zeroThresholdUsesContinuousIntersectionEvidenceOnExecutors() {
        var raw=source(RowFactory.create(1,"LINESTRING (-45 60,45 60)","LINESTRING (0 65,0 70)"),
                RowFactory.create(2,"LINESTRING (-45 60,45 60)","LINESTRING (0 59,0 61)"),
                RowFactory.create(3,"POINT (180 0)","LINESTRING (179 0,-179 0)"),
                RowFactory.create(4,"POLYGON ((-3 -1,3 -1,3 1,-3 1,-3 -1))","POLYGON ((-1 -3,1 -3,1 3,-1 3,-1 -3))"),
                RowFactory.create(5,null,"POINT (0 0)"),
                RowFactory.create(6,"POINT (0 0)","MULTILINESTRING ((-2 -1,-2 1),(2 -1,2 1))"),
                RowFactory.create(7,"LINESTRING (-90 80,90 80)","LINESTRING (0 85,180 85)"))
                .withColumn("threshold",functions.lit(0d));
        String job=UUID.randomUUID().toString(); spark.sparkContext().setJobGroup(job,"topological zero preflight",false);
        Dataset<Row> plan;
        try {
            plan=thresholdPlan(raw.repartition(2)); plan.queryExecution().analyzed(); plan.schema();
            assertEquals(0,spark.sparkContext().statusTracker().getJobIdsForGroup(job).length);
        } finally { spark.sparkContext().clearJobGroup(); }
        var rows=plan.orderBy("id").collectAsList();
        assertEquals(Boolean.TRUE,rows.get(0).getAs("within")); assertEquals(Boolean.FALSE,rows.get(1).getAs("within"));
        assertEquals(Boolean.TRUE,rows.get(2).getAs("within")); assertEquals(Boolean.TRUE,rows.get(3).getAs("within"));
        assertNull(rows.get(4).getAs("within")); assertEquals(Boolean.FALSE,rows.get(5).getAs("within"));
        assertEquals(Boolean.TRUE,rows.get(6).getAs("within"));
    }

    @Test void multipartHierarchyRunsOnExecutorsWithoutQuadraticPairInitializationOrPlanningJobs() {
        var factory=new GeometryFactory(new PrecisionModel(),4326);
        var left=new LineString[1025]; var right=new LineString[1025];
        for (int i=0;i<1024;i++) {
            double x=-150+i*0.1;
            left[i]=factory.createLineString(new Coordinate[]{new CoordinateXY(x,-40),new CoordinateXY(x+0.001,-40)});
            right[i]=factory.createLineString(new Coordinate[]{new CoordinateXY(x,40),new CoordinateXY(x+0.001,40)});
        }
        left[1024]=factory.createLineString(new Coordinate[]{new CoordinateXY(-0.02,0),new CoordinateXY(0.06,0)});
        right[1024]=factory.createLineString(new Coordinate[]{new CoordinateXY(0.007,0.0001),new CoordinateXY(0.007,0.0002)});
        String a=factory.createMultiLineString(left).toText(), b=factory.createMultiLineString(right).toText();
        var raw=source(RowFactory.create(1,a,b),RowFactory.create(2,b,a)).repartition(2);
        var plan=lazyPlan(raw);
        double expected=net.sf.geographiclib.Geodesic.WGS84.Inverse(0,0.007,0.0001,0.007).s12;
        for (Row row : plan.collectAsList()) {
            Row result=row.getAs("nearest");
            assertEquals(expected,result.getDouble(0),0.0001);
            assertTrue(result.getDouble(1)<=expected+Wgs84SegmentDistance.ROUNDOFF_METRES);
            assertTrue(result.getDouble(0)+Wgs84SegmentDistance.ROUNDOFF_METRES-result.getDouble(1)<=0.0001);
            LineString witnesses=result.getAs(2);
            assertEquals(result.getDouble(0),net.sf.geographiclib.Geodesic.WGS84.Inverse(
                    witnesses.getCoordinateN(0).y,witnesses.getCoordinateN(0).x,
                    witnesses.getCoordinateN(1).y,witnesses.getCoordinateN(1).x).s12,1e-8);
        }
        var thresholds=raw.withColumn("threshold",functions.when(functions.col("id").equalTo(1),12d).otherwise(10d));
        String job=UUID.randomUUID().toString(); spark.sparkContext().setJobGroup(job,"hierarchical threshold preflight",false);
        Dataset<Row> filtered;
        try {
            filtered=thresholdPlan(thresholds); filtered.queryExecution().analyzed(); filtered.schema();
            assertEquals(0,spark.sparkContext().statusTracker().getJobIdsForGroup(job).length);
        } finally { spark.sparkContext().clearJobGroup(); }
        var rows=filtered.orderBy("id").collectAsList();
        assertEquals(Boolean.TRUE,rows.get(0).getAs("within")); assertEquals(Boolean.FALSE,rows.get(1).getAs("within"));
    }
}
