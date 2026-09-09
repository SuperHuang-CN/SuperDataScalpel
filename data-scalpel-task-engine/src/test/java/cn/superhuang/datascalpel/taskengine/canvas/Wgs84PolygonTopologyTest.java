package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.datascalpel.taskengine.canvas.TrackGeodesicAreaBoundary.Vertex;
import cn.superhuang.datascalpel.taskengine.canvas.Wgs84PolygonRegion.Location;
import cn.superhuang.datascalpel.taskengine.canvas.Wgs84SegmentDistance.Budget;
import cn.superhuang.datascalpel.taskengine.canvas.Wgs84SegmentDistance.Position;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.io.WKTReader;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class Wgs84PolygonTopologyTest {
    private static final GeometryFactory FACTORY=new GeometryFactory(new PrecisionModel(),4326);
    private static Geometry read(String wkt) throws Exception { return new WKTReader(FACTORY).read(wkt); }

    @Test void sourceRegionDoesNotDependOnRenderingResolutionOrPlanarContainment() throws Exception {
        Geometry shape=read("POLYGON ((-45 60,45 60,45 65,-45 65,-45 60),(-2 70,-2 71,2 71,2 70,-2 70))");
        assertFalse(shape.isValid(),"the original longitude/latitude chord polygon has its hole outside");
        for (Geometry ordered : List.of(shape,shape.reverse())) {
            // Source topology and distance no longer accept a rendering step. Rendering
            // can exceed its own capacity without invalidating this continuous region.
            failure("TRACK_AREA_VERTEX_LIMIT_EXCEEDED",()->TrackGeodesicAreaGeometry.footprint(ordered,null,false,Double.MIN_VALUE));
            var region=Wgs84PolygonRegion.prepare(ordered);
            assertEquals(Location.INSIDE,region.locate(new Position(10,69)));
            assertEquals(Location.OUTSIDE,region.locate(new Position(0,70.5)));
            assertEquals(Location.OUTSIDE,region.locate(new Position(0,60)));
        }
    }

    @Test void holesMustBeInsideTheirShellAndCannotNest() throws Exception {
        for (String wkt : List.of(
                "POLYGON ((0 0,4 0,4 4,0 4,0 0),(5 1,6 1,6 2,5 2,5 1))",
                "POLYGON ((0 0,4 0,4 4,0 4,0 0),(1 1,3 1,3 3,1 3,1 1),(1.5 1.5,2 1.5,2 2,1.5 2,1.5 1.5))",
                "POLYGON ((0 0,4 0,4 4,0 4,0 0),(-1 -1,5 -1,5 5,-1 5,-1 -1))")) {
            invalidBothOrders(read(wkt));
        }
    }

    @Test void sharedVerticesCannotHideRingCrossings() throws Exception {
        Polygon shape=(Polygon)read("POLYGON ((0 0,4 0,0 4,0 0),(2 0,2 -1,-1 -1,-1 2,0 2,1 1,2 0))");
        var rings=List.of(vertices(shape.getExteriorRing()),vertices(shape.getInteriorRingN(0)));
        // Neither a strict edge crossing nor a shared edge: both crossings occur at
        // exact contacts. The open arcs between contacts must be checked independently.
        assertDoesNotThrow(()->Wgs84PolygonArcCheck.validate(rings,new Vertex(1,1)));
        invalidBothOrders(shape);
    }

    @Test void severalRingsAtTheSameContactFormAStarNotADisconnectingCycle() throws Exception {
        for (String wkt : List.of(
                "POLYGON ((0 0,10 0,10 10,0 10,0 0),(5 5,2 5,3 7,5 5),(5 5,7 7,8 5,5 5),(5 5,6 2,4 2,5 5))",
                "POLYGON ((0 0,4 0,4 4,0 4,0 0),(0 2,1 3,1 2.25,0 2),(0 2,1 1.75,1 1,0 2))")) {
            Geometry shape=read(wkt);
            for (Geometry ordered : List.of(shape,shape.reverse()))
                assertDoesNotThrow(()->TrackGeodesicPolygon.prepareSource(ordered));
        }
    }

    @Test void cyclesOfHoleContactsAndShellToShellCutsDisconnectTheInterior() throws Exception {
        for (String wkt : List.of(
                "POLYGON ((0 0,4 0,4 4,0 4,0 0),(0 2,1 1,2 2,1 3,0 2),(2 2,3 1,4 2,3 3,2 2))",
                "POLYGON ((-1 -1,5 -1,5 5,-1 5,-1 -1),(0 2,2 4,4 2,3 2.5,1 2.5,0 2),(0 2,1 1.5,3 1.5,4 2,2 0,0 2))")) {
            invalidBothOrders(read(wkt));
        }
    }

    @Test void multipartContainmentPermitsAnIslandInAHoleButNotInFilledArea() throws Exception {
        var island=read("MULTIPOLYGON (((0 0,6 0,6 6,0 6,0 0),(1 1,5 1,5 5,1 5,1 1)),((2 2,4 2,4 4,2 4,2 2)))");
        var reordered=FACTORY.createMultiPolygon(new Polygon[]{(Polygon)island.getGeometryN(1),(Polygon)island.getGeometryN(0)});
        for (Geometry shape : List.of(island,island.reverse(),reordered))
            assertDoesNotThrow(()->TrackGeodesicPolygon.prepareSource(shape));
        invalidBothOrders(read("MULTIPOLYGON (((0 0,6 0,6 6,0 6,0 0)),((2 2,4 2,4 4,2 4,2 2)))"));
    }

    @Test void normalizedDatelineContactsAndSourceSnapshotsRemainIndependent() throws Exception {
        Geometry shape=read("POLYGON ((180 0,-176 0,-176 4,180 4,180 0),(-180 2,-179 1,-179 3,-180 2))");
        Geometry original=shape.copy();
        var source=TrackGeodesicPolygon.prepareSource(shape);
        assertTrue(original.equalsExact(shape));
        assertThrows(UnsupportedOperationException.class,()->source.rings().clear());
        assertThrows(UnsupportedOperationException.class,()->source.rings().getFirst().clear());
        shape.apply((CoordinateFilter)coordinate->coordinate.x=Double.NaN);
        assertTrue(source.rings().stream().flatMap(List::stream).allMatch(vertex->Double.isFinite(vertex.longitude())));
    }

    @Test void topologyWorkAndCoordinateGuardsAreStillEnforcedWithoutRendering() throws Exception {
        Polygon shape=(Polygon)read("POLYGON ((0 0,4 0,4 4,0 4,0 0),(1 1,2 1,2 2,1 2,1 1))");
        failure("GEODESIC_DISTANCE_WORK_LIMIT_EXCEEDED",()->Wgs84PolygonTopology.validate(
                List.of(vertices(shape.getExteriorRing()),vertices(shape.getInteriorRingN(0))),List.of(1),new Budget(1)));
        shape.setSRID(3857);
        failure("GEODESIC_DISTANCE_COORDINATE_INVALID",()->Wgs84PolygonRegion.prepare(shape));
        var invalid=read("POLYGON Z ((0 0 0,1 0 0,1 1 0,0 1 0,0 0 0))");
        failure("GEODESIC_DISTANCE_COORDINATE_INVALID",()->Wgs84PolygonRegion.prepare(invalid));
    }

    private static List<Vertex> vertices(LineString ring) {
        var result=new ArrayList<Vertex>();
        for (Coordinate coordinate : ring.getCoordinates()) result.add(TrackGeodesicAreaGeometry.vertex(coordinate));
        return result;
    }
    private static void invalidBothOrders(Geometry shape) {
        for (Geometry ordered : List.of(shape,shape.reverse())) {
            failure("TRACK_AREA_GEOMETRY_INVALID",()->TrackGeodesicPolygon.prepareSource(ordered));
            failure("GEODESIC_DISTANCE_GEOMETRY_INVALID",()->Wgs84PolygonRegion.prepare(ordered));
        }
    }
    private static void failure(String code,org.junit.jupiter.api.function.Executable action) {
        var error=assertThrows(IllegalArgumentException.class,action);
        assertEquals(code,error.getMessage()); assertNull(error.getCause());
    }
}
