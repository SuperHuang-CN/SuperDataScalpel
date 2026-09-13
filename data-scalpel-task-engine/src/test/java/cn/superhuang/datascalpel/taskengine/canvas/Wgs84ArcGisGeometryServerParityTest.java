package cn.superhuang.datascalpel.taskengine.canvas;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.io.WKTReader;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Offline truth table captured from the ArcGIS Online GeometryServer distance operation on 2026-09-12.
 * Requests used EPSG:4326, geodesic=true and distanceUnit=9001 (metres). This verifies the shared
 * geometry-distance semantics; it is not a substitute for an Enterprise Find Nearest job comparison.
 */
class Wgs84ArcGisGeometryServerParityTest {
    private static final GeometryFactory FACTORY=new GeometryFactory(new PrecisionModel(),4326);

    @Test
    void matchesOfficialGeodesicDistanceSamplesForLinesRegionsHolesAndGlobalParts() throws Exception {
        assertDistance("POINT (0.007 0.01)","LINESTRING (-0.02 0,0.06 0)",1105.7427583286865,0.001);
        assertDistance("LINESTRING (179 -1,-179 1)","LINESTRING (179 1,-179 -1)",0,0);
        assertDistance("POINT (0.005 0.005)","POLYGON ((0 0,0.01 0,0.01 0.01,0 0.01,0 0))",0,0);
        assertDistance("POINT (0.005 0.005)",
                "POLYGON ((0 0,0.01 0,0.01 0.01,0 0.01,0 0),(0.004 0.004,0.006 0.004,0.006 0.006,0.004 0.006,0.004 0.004))",
                110.57427575225489,0.001);
        assertDistance("POINT (-169.5 0.5)",
                "MULTIPOLYGON (((-170 0,-169 0,-169 1,-170 1,-170 0)),((10 0,11 0,11 1,10 1,10 0)))",0,0);
    }

    private static void assertDistance(String first,String second,double expected,double tolerance) throws Exception {
        Wgs84SegmentDistance.Result result=Wgs84GeometryDistance.nearest(read(first),read(second),0.0001);
        assertEquals(expected,result.distanceMetres(),tolerance);
    }

    private static Geometry read(String wkt) throws Exception {
        return new WKTReader(FACTORY).read(wkt);
    }
}
