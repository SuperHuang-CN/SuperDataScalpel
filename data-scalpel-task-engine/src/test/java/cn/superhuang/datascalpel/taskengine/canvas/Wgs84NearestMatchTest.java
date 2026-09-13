package cn.superhuang.datascalpel.taskengine.canvas;

import net.sf.geographiclib.Geodesic;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.io.WKTReader;

import static org.junit.jupiter.api.Assertions.*;

class Wgs84NearestMatchTest {
    private static final GeometryFactory FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    @Test
    void nonpointMatchKeepsDistanceBoundsAndActualWitnessesTogether() throws Exception {
        var match = Wgs84NearestMatch.solve(read("POINT (0.007 0.01)"),
                read("LINESTRING (-0.02 0,0.06 0)"));
        assertNotNull(match);
        assertTrue(match.lowerBoundMetres() <= match.distanceMetres());
        assertTrue(match.upperBoundMetres() >= match.distanceMetres());
        assertTrue(match.upperBoundMetres() - match.lowerBoundMetres()
                <= Wgs84NearestMatch.SOLVER_TOLERANCE_METRES);
        var first = match.witnesses().getCoordinateN(0);
        var second = match.witnesses().getCoordinateN(1);
        assertEquals(match.distanceMetres(), Geodesic.WGS84.Inverse(
                first.y, first.x, second.y, second.x).s12, 1e-8);
        assertEquals(0.007, second.x, 0.00001);
        assertEquals(0, second.y, 1e-12);
        assertFalse(match.provenZero());
    }

    @Test
    void containmentAndVerifiedLocalCrossingCarryExplicitCommonWitnesses() throws Exception {
        Geometry area = read("POLYGON ((0 0,0.01 0,0.01 0.01,0 0.01,0 0))");
        var contained = Wgs84NearestMatch.solve(read("POINT (0.005 0.005)"), area);
        assertEquals(0, contained.distanceMetres());
        assertEquals(0, contained.lowerBoundMetres());
        assertTrue(contained.provenZero());
        assertTrue(contained.witnesses().getCoordinateN(0)
                .equals2D(contained.witnesses().getCoordinateN(1)));

        var crossing = Wgs84NearestMatch.solve(
                read("POLYGON ((-0.003 -0.0005,0.003 -0.0005,0.003 0.0005,-0.003 0.0005,-0.003 -0.0005))"),
                read("POLYGON ((-0.0005 -0.003,0.0005 -0.003,0.0005 0.003,-0.0005 0.003,-0.0005 -0.003))"));
        assertEquals(0, crossing.lowerBoundMetres());
        assertEquals(0, crossing.distanceMetres());
        assertTrue(crossing.provenZero());
        assertTrue(crossing.witnesses().getCoordinateN(0)
                .equals2D(crossing.witnesses().getCoordinateN(1)));
    }

    @Test
    void multipointVisitsEveryPotentialWinnerAndReturnsAnExactDiscreteMinimum() throws Exception {
        var match = Wgs84NearestMatch.solve(read("MULTIPOINT ((0 0),(1 0))"),
                read("MULTIPOINT ((0.5 1),(20 20))"));
        assertNotNull(match);
        assertTrue(match.exactDistance());
        assertEquals(match.distanceMetres(),match.lowerBoundMetres());
        assertEquals(match.distanceMetres(),match.upperBoundMetres());
        assertEquals(Geodesic.WGS84.Inverse(0,0,1,0.5).s12,match.distanceMetres(),1e-8);
    }

    @Test
    void nullEmptyAndInvalidInputsRetainSafeDistanceSemantics() throws Exception {
        assertNull(Wgs84NearestMatch.solve(null, read("POINT (0 0)")));
        assertNull(Wgs84NearestMatch.solve(read("POINT EMPTY"), read("POINT (0 0)")));
        var failure = assertThrows(IllegalArgumentException.class,
                () -> Wgs84NearestMatch.solve(read("LINESTRING (0 0,181 0)"), read("POINT (0 0)")));
        assertEquals("GEODESIC_DISTANCE_COORDINATE_INVALID", failure.getMessage());
        assertNull(failure.getCause());
    }

    private static Geometry read(String wkt) throws Exception {
        return new WKTReader(FACTORY).read(wkt);
    }
}
