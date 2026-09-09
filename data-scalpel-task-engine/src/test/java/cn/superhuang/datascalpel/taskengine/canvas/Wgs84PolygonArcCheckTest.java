package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.datascalpel.taskengine.canvas.TrackGeodesicAreaBoundary.Vertex;
import net.sf.geographiclib.Geodesic;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.io.WKTReader;

import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class Wgs84PolygonArcCheckTest {
    private static final GeometryFactory FACTORY=new GeometryFactory(new PrecisionModel(),4326);
    private static Geometry read(String wkt) throws Exception { return new WKTReader(FACTORY).read(wkt); }
    private static Vertex v(double longitude,double latitude) { return new Vertex(longitude,latitude); }
    private static List<Vertex> ring(Vertex... vertices) {
        var result=new ArrayList<>(List.of(vertices)); result.add(result.getFirst()); return result;
    }

    @Test void continuousCrossingIsRejectedIndependentlyOfRenderSamplingLength() throws Exception {
        var ring=ring(v(-45,60),v(45,60),v(0,70),v(0,65));
        failure("TRACK_AREA_GEOMETRY_INVALID",()->Wgs84PolygonArcCheck.validate(List.of(ring),v(0,65)));
        var source=read("POLYGON ((-45 60,45 60,0 70,0 65,-45 60))");
        for (double step : new double[]{1000,10_000,10_000_000})
            failure("TRACK_AREA_GEOMETRY_INVALID",()->TrackGeodesicAreaGeometry.footprint(source,null,false,step));
    }

    @Test void aRingCannotOverlapItselfOrTouchANonAdjacentVertex() {
        var backtrack=ring(v(0,0),v(2,0),v(1,0),v(1,1));
        failure("TRACK_AREA_GEOMETRY_INVALID",()->Wgs84PolygonArcCheck.validate(List.of(backtrack),v(1,0)));
        var touch=ring(v(0,0),v(2,0),v(2,2),v(1,1),v(0,2),v(1,1));
        failure("TRACK_AREA_GEOMETRY_INVALID",()->Wgs84PolygonArcCheck.validate(List.of(touch),v(1,1)));
    }

    @Test void distinctRingPointContactIsNotAutomaticallyRejectedAsACrossing() throws Exception {
        var source=read("POLYGON ((0 0,4 0,4 4,0 4,0 0),(0 2,1 1,1 3,0 2))");
        Geometry original=source.copy();
        for (Geometry ordered : List.of(source,source.reverse())) for (double step : new double[]{1000,10_000,10_000_000}) {
            var footprint=TrackGeodesicAreaGeometry.footprint(ordered,null,false,step);
            assertTrue(footprint.area().isValid());
        }
        assertTrue(original.equalsExact(source),"contact noding must not change the input Geometry");
        var overlapping=read("POLYGON ((0 0,4 0,4 4,0 4,0 0),(0 1,1 2,0 3,0 1))");
        failure("TRACK_AREA_GEOMETRY_INVALID",()->TrackGeodesicAreaGeometry.footprint(overlapping,null,false,10_000));
    }

    @Test void confirmedContactsAreInsertedInArcOrderWithoutMovingOrDuplicatingVertices() {
        var shell=ring(v(0,0),v(0,4),v(4,4),v(4,0));
        var upper=ring(v(0,3),v(1,2.5),v(1,3.5));
        var lower=ring(v(0,1),v(1,0.5),v(1,1.5));
        var rings=List.of(shell,upper,lower);
        var noded=Wgs84PolygonArcCheck.validate(rings,v(2,2));
        assertEquals(List.of(v(0,0),v(0,1),v(0,3),v(0,4),v(4,4),v(4,0),v(0,0)),noded.getFirst());
        assertEquals(upper,noded.get(1)); assertEquals(lower,noded.get(2)); assertEquals(5,shell.size());
        assertThrows(UnsupportedOperationException.class,()->noded.getFirst().add(v(0,2)));
        assertEquals(noded,Wgs84PolygonArcCheck.validate(noded,v(2,2)),"noding must be idempotent");
    }

    @Test void validEquatorialDatelineVertexAndMultipartContactsSurviveSampling() throws Exception {
        for (String wkt : List.of(
                "POLYGON ((0 0,4 0,4 4,0 4,0 0),(2 0,3 1,1 1,2 0))",
                "POLYGON ((180 0,-176 0,-176 4,180 4,180 0),(-180 2,-179 1,-179 3,-180 2))",
                "POLYGON ((0 0,4 0,4 4,0 4,0 0),(0 0,1 0.2,0.2 1,0 0))",
                "POLYGON ((10.123 20.456,14.123 20.456,14.123 24.456,10.123 24.456,10.123 20.456),(10.123 20.456,11.123 20.656,10.323 21.456,10.123 20.456))",
                "POLYGON ((0 0,4 0,4 4,0 4,0 0),(0 3,1 2.5,1 3.5,0 3),(0 1,1 0.5,1 1.5,0 1))",
                "MULTIPOLYGON (((0 0,4 0,4 4,0 4,0 0)),((0 2,-1 1,-1 3,0 2)))")) {
            Geometry source=read(wkt);
            for (Geometry ordered : List.of(source,source.reverse())) for (double step : new double[]{1000,10_000,10_000_000})
                assertTrue(TrackGeodesicAreaGeometry.footprint(ordered,null,false,step).area().isValid(),wkt);
        }
    }

    @Test void nodingDoesNotPermitHolesToDisconnectThePolygonInterior() throws Exception {
        Geometry source=read("POLYGON ((0 0,4 0,4 4,0 4,0 0),(0 2,1 1,2 2,1 3,0 2),(2 2,3 1,4 2,3 3,2 2))");
        for (Geometry ordered : List.of(source,source.reverse())) for (double step : new double[]{1000,10_000,10_000_000})
            failure("TRACK_AREA_GEOMETRY_INVALID",()->TrackGeodesicAreaGeometry.footprint(ordered,null,false,step));
    }

    @Test void unresolvedNonAxisOverlapPropagatesWithoutCoordinatesInsteadOfBeingSnapped() {
        var line=Geodesic.WGS84.InverseLine(0,0,1,1); var middle=line.Position(line.Distance()/2);
        var points=ring(v(0,0),v(1,1),v(middle.lon2,middle.lat2),v(0,1));
        failure("GEODESIC_TOPOLOGY_PRECISION_NOT_REACHED",()->Wgs84PolygonArcCheck.validate(List.of(points),v(0.5,0.5)));
        Geometry source=FACTORY.createPolygon(points.stream().map(Vertex::coordinate).toArray(Coordinate[]::new));
        failure("GEODESIC_TOPOLOGY_PRECISION_NOT_REACHED",()->TrackGeodesicAreaGeometry.footprint(source,null,false,10_000));
        failure("GEODESIC_TOPOLOGY_PRECISION_NOT_REACHED",()->Wgs84PolygonRegion.prepare(source));
    }

    @Test void conservativeSpatialBroadPhaseAvoidsAllPairsOnALargeSimpleRing() {
        var points=new ArrayList<Vertex>();
        for (int i=0;i<1024;i++) {
            var point=Geodesic.WGS84.Direct(60,179.9,i*360d/1024,50_000);
            points.add(v(point.lon2,point.lat2));
        }
        points.add(points.getFirst());
        // 1024 edges have 523,776 unordered pairs, above the shared 250,000 budget even
        // before inverse bearings. A valid pass requires broad-phase candidate pruning.
        assertDoesNotThrow(()->Wgs84PolygonArcCheck.validate(List.of(points),v(179.9,60)));
    }

    private static void failure(String code,org.junit.jupiter.api.function.Executable action) {
        var error=assertThrows(IllegalArgumentException.class,action); assertEquals(code,error.getMessage()); assertNull(error.getCause());
    }
}
