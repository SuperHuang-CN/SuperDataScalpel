package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.*;
import cn.superhuang.data.scalpel.contract.type.CoordinateDimension;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.io.WKTReader;
import static org.junit.jupiter.api.Assertions.*;

class UnaryGeometrySupportTest {
    private Geometry read(String wkt) throws Exception { var g = new WKTReader().read(wkt); g.setSRID(3857); return g; }
    private Geometry derive(String wkt, GeometryDeriveKind kind) throws Exception {
        return UnaryGeometrySupport.derive(read(wkt), kind, GeometryUnaryPolicy.OUTPUT_XY, CoordinateDimension.XYZM);
    }
    private void dimension(Geometry geometry, CoordinateDimension dimension) {
        boolean z = dimension == CoordinateDimension.XYZ || dimension == CoordinateDimension.XYZM;
        boolean m = dimension == CoordinateDimension.XYM || dimension == CoordinateDimension.XYZM;
        geometry.apply(new CoordinateSequenceFilter() {
            public void filter(CoordinateSequence sequence, int i) {
                assertEquals(z, sequence.hasZ()); assertEquals(m, sequence.hasM());
                if (z) assertTrue(Double.isFinite(sequence.getZ(i)));
                if (m) assertTrue(Double.isFinite(sequence.getM(i)));
            }
            public boolean isDone() { return false; }
            public boolean isGeometryChanged() { return false; }
        });
    }
    @Test void derivedPointSemanticsAndDegenerateKindsAreExplicit() throws Exception {
        String hole = "POLYGON ((0 0, 10 0, 10 10, 0 10, 0 0), (3 3, 3 7, 7 7, 7 3, 3 3))";
        assertFalse(read(hole).contains(derive(hole, GeometryDeriveKind.CENTROID)));
        assertTrue(read(hole).contains(derive(hole, GeometryDeriveKind.POINT_ON_SURFACE)));
        assertEquals("Point", derive("POINT (1 2)", GeometryDeriveKind.ENVELOPE).getGeometryType());
        assertEquals("LineString", derive("LINESTRING (0 0, 0 1)", GeometryDeriveKind.ENVELOPE).getGeometryType());
        assertEquals("LineString", derive("MULTIPOINT ((0 0), (1 0), (2 0))", GeometryDeriveKind.CONVEX_HULL).getGeometryType());
        assertTrue(derive("LINESTRING (0 0, 1 0, 0 0)", GeometryDeriveKind.BOUNDARY).isEmpty());
        assertTrue(derive("POINT (1 2)", GeometryDeriveKind.BOUNDARY).isEmpty());
    }
    @Test void convexHullBoundaryAndBothSimplifiersPreserveRealZM() throws Exception {
        for (var d : CoordinateDimension.values()) {
            String tag = switch (d) { case XY -> ""; case XYZ -> " Z"; case XYM -> " M"; case XYZM -> " ZM"; };
            String extra = d == CoordinateDimension.XY ? "" : d == CoordinateDimension.XYZM ? " 7 9" : " 7";
            var input = read("LINESTRING" + tag + " (0 0" + extra + ", 1 0.01" + extra + ", 2 0" + extra + ")");
            var copy = input.copy();
            for (var kind : new GeometryDeriveKind[]{GeometryDeriveKind.CONVEX_HULL, GeometryDeriveKind.BOUNDARY})
                dimension(UnaryGeometrySupport.derive(input, kind, GeometryUnaryPolicy.PRESERVE_DIMENSION, d), d);
            for (var algorithm : GeometrySimplifyAlgorithm.values()) {
                if (algorithm == GeometrySimplifyAlgorithm.DOUGLAS_PEUCKER && (d == CoordinateDimension.XYM || d == CoordinateDimension.XYZM)) {
                    assertThrows(IllegalArgumentException.class, () -> UnaryGeometrySupport.simplify(input, algorithm, 0.1, GeometryUnaryPolicy.PRESERVE_DIMENSION, d));
                    continue;
                }
                var result = assertDoesNotThrow(() -> UnaryGeometrySupport.simplify(input, algorithm, 0.1, GeometryUnaryPolicy.PRESERVE_DIMENSION, d), d + " " + algorithm);
                dimension(result, d); assertEquals(2, result.getNumPoints());
                assertEquals(3857, result.getSRID());
            }
            for (var kind : GeometryDeriveKind.values()) dimension(UnaryGeometrySupport.derive(input, kind, GeometryUnaryPolicy.OUTPUT_XY, d), CoordinateDimension.XY);
            assertTrue(input.equalsExact(copy));
        }
    }
    @Test void nullEmptyAndCollectionsDoNotBecomeFailedRowsOrInventedPoints() throws Exception {
        for (var kind : GeometryDeriveKind.values()) {
            assertNull(UnaryGeometrySupport.derive(null, kind, GeometryUnaryPolicy.OUTPUT_XY, CoordinateDimension.XY));
            for (String wkt : new String[]{"POINT EMPTY", "LINESTRING EMPTY", "POLYGON EMPTY", "MULTIPOINT EMPTY"})
                assertTrue(derive(wkt, kind).isEmpty());
        }
        for (var algorithm : GeometrySimplifyAlgorithm.values()) {
            assertNull(UnaryGeometrySupport.simplify(null, algorithm, 1, GeometryUnaryPolicy.OUTPUT_XY, CoordinateDimension.XY));
            assertTrue(UnaryGeometrySupport.simplify(read("POLYGON EMPTY"), algorithm, 1, GeometryUnaryPolicy.OUTPUT_XY, CoordinateDimension.XY).isEmpty());
            var collection = UnaryGeometrySupport.simplify(read("GEOMETRYCOLLECTION (POINT (0 0), LINESTRING (1 1, 2 2, 3 3))"), algorithm, 1, GeometryUnaryPolicy.OUTPUT_XY, CoordinateDimension.XY);
            assertEquals(2, collection.getNumGeometries());
        }
        assertEquals("GEOMETRY_UNARY_KIND_UNSUPPORTED", assertThrows(IllegalArgumentException.class,
                () -> derive("GEOMETRYCOLLECTION (POINT (1 2))", GeometryDeriveKind.BOUNDARY)).getMessage());
        assertEquals("Point", derive("GEOMETRYCOLLECTION (POINT (1 2))", GeometryDeriveKind.CENTROID).getGeometryType());
    }
    @Test void invalidInputsAndUnavailableComputedZMFailWithoutRepair() throws Exception {
        var invalid = read("POLYGON ((0 0, 2 2, 0 2, 2 0, 0 0))");
        for (var kind : GeometryDeriveKind.values())
            assertEquals("GEOMETRY_UNARY_INPUT_INVALID", assertThrows(IllegalArgumentException.class,
                    () -> UnaryGeometrySupport.derive(invalid, kind, GeometryUnaryPolicy.OUTPUT_XY, CoordinateDimension.XY)).getMessage());
        for (var algorithm : GeometrySimplifyAlgorithm.values())
            assertEquals("GEOMETRY_UNARY_INPUT_INVALID", assertThrows(IllegalArgumentException.class,
                    () -> UnaryGeometrySupport.simplify(invalid, algorithm, 1, GeometryUnaryPolicy.OUTPUT_XY, CoordinateDimension.XY)).getMessage());
        assertEquals("GEOMETRY_UNARY_DIMENSION_UNSUPPORTED", assertThrows(IllegalArgumentException.class,
                () -> UnaryGeometrySupport.derive(read("POLYGON Z ((0 0 1, 2 0 2, 2 2 3, 0 0 1))"),
                        GeometryDeriveKind.CENTROID, GeometryUnaryPolicy.PRESERVE_DIMENSION, CoordinateDimension.XYZ)).getMessage());
    }

    @Test void topologyPreservingDoesNotGuaranteeCrossFeatureCoverage() throws Exception {
        var left = read("POLYGON ((-5 0, 0 0, 1 1, -1 2, 1 3, 0 4, -5 4, -5 0))");
        var right = read("POLYGON ((0 4, 1 3, -1 2, 1 1, 0 0, 5 0, 5 4, 0 4))");
        var a = UnaryGeometrySupport.simplify(left, GeometrySimplifyAlgorithm.TOPOLOGY_PRESERVING, 1.1, GeometryUnaryPolicy.OUTPUT_XY, CoordinateDimension.XY);
        var b = UnaryGeometrySupport.simplify(right, GeometrySimplifyAlgorithm.TOPOLOGY_PRESERVING, 1.1, GeometryUnaryPolicy.OUTPUT_XY, CoordinateDimension.XY);
        assertTrue(a.isValid() && b.isValid());
        assertTrue(a.intersection(b).getArea() > 0 || a.union(b).symDifference(left.union(right)).getArea() > 0,
                "individually valid simplified features can have overlaps or gaps");
    }

    @Test void multipartsAndPolygonRingsHaveExplicitDimensionalBehavior() throws Exception {
        for (String wkt : new String[]{"POINT ZM (1 2 3 4)", "MULTIPOINT ZM ((1 2 3 4), (2 3 4 5))",
                "POLYGON ZM ((0 0 1 2, 4 0 1 2, 4 4 1 2, 0 4 1 2, 0 0 1 2), (1 1 1 2, 1 2 1 2, 2 2 1 2, 2 1 1 2, 1 1 1 2))",
                "MULTILINESTRING ZM ((0 0 1 2, 1 1 2 3), (2 2 3 4, 3 3 4 5))",
                "MULTIPOLYGON ZM (((0 0 1 2, 4 0 1 2, 4 4 1 2, 0 0 1 2)))"}) {
            var input = read(wkt);
            for (var kind : new GeometryDeriveKind[]{GeometryDeriveKind.CONVEX_HULL, GeometryDeriveKind.BOUNDARY})
                dimension(UnaryGeometrySupport.derive(input, kind, GeometryUnaryPolicy.PRESERVE_DIMENSION, CoordinateDimension.XYZM), CoordinateDimension.XYZM);
            dimension(UnaryGeometrySupport.simplify(input, GeometrySimplifyAlgorithm.TOPOLOGY_PRESERVING, 0.1,
                    GeometryUnaryPolicy.PRESERVE_DIMENSION, CoordinateDimension.XYZM), CoordinateDimension.XYZM);
        }
    }

    @Test void douglasPeuckerDoesNotSilentlyRepairAHoleOutsideTheSimplifiedShell() throws Exception {
        var source = read("POLYGON ((0 0, 10 0, 10 10, 6 10, 6 12, 4 12, 4 10, 0 10, 0 0), (2 2, 5 11, 8 2, 2 2))");
        assertTrue(source.isValid());
        assertEquals("GEOMETRY_SIMPLIFY_RESULT_INVALID", assertThrows(IllegalArgumentException.class,
                () -> UnaryGeometrySupport.simplify(source, GeometrySimplifyAlgorithm.DOUGLAS_PEUCKER, 2.1,
                        GeometryUnaryPolicy.OUTPUT_XY, CoordinateDimension.XY)).getMessage());
        var preserved = UnaryGeometrySupport.simplify(source, GeometrySimplifyAlgorithm.TOPOLOGY_PRESERVING, 2.1,
                GeometryUnaryPolicy.OUTPUT_XY, CoordinateDimension.XY);
        assertTrue(preserved.isValid());
        assertEquals(1, ((Polygon) preserved).getNumInteriorRing());
    }

    @Test void toleranceBoundsSimpleLineDisplacementAndValidCollapseStaysEmpty() throws Exception {
        var line = read("LINESTRING (0 0, 1 0.1, 2 -0.1, 3 0)");
        for (var algorithm : GeometrySimplifyAlgorithm.values()) {
            var simplified = UnaryGeometrySupport.simplify(line, algorithm, 0.2, GeometryUnaryPolicy.OUTPUT_XY, CoordinateDimension.XY);
            assertEquals(2, simplified.getNumPoints());
            for (var coordinate : line.getCoordinates()) assertTrue(line.getFactory().createPoint(coordinate).distance(simplified) <= 0.2);
        }
        var triangle = read("POLYGON ((0 0, 1 0, 0 1, 0 0))");
        assertTrue(UnaryGeometrySupport.simplify(triangle, GeometrySimplifyAlgorithm.DOUGLAS_PEUCKER, 10,
                GeometryUnaryPolicy.OUTPUT_XY, CoordinateDimension.XY).isEmpty());
        assertFalse(UnaryGeometrySupport.simplify(triangle, GeometrySimplifyAlgorithm.TOPOLOGY_PRESERVING, 10,
                GeometryUnaryPolicy.OUTPUT_XY, CoordinateDimension.XY).isEmpty());
        assertEquals(5000d, SpatialDistanceSupport.resolve(5, SpatialDistanceUnit.KILOMETERS,
                new cn.superhuang.data.scalpel.contract.type.CrsReference("EPSG", 3857)).sourceCrsValue());
        assertFalse(SpatialDistanceSupport.resolve(5, SpatialDistanceUnit.METERS,
                new cn.superhuang.data.scalpel.contract.type.CrsReference("EPSG", 4326)).valid());
        assertTrue(SpatialDistanceSupport.resolve(0.1, SpatialDistanceUnit.SOURCE_CRS_UNIT,
                new cn.superhuang.data.scalpel.contract.type.CrsReference("EPSG", 4326)).angular());
    }

    @Test void largeSmoothLinesRetainToleranceAndOriginalCoordinates() {
        var factory = new GeometryFactory(new PrecisionModel(), 3857);
        var coordinates = new Coordinate[50_001];
        for (int i = 0; i < coordinates.length; i++) {
            coordinates[i] = new Coordinate(i * 0.1, Math.sin(i * 0.002), i * 0.01);
        }
        var source = factory.createLineString(coordinates);
        var original = source.copy();
        for (var algorithm : GeometrySimplifyAlgorithm.values()) {
            var result = UnaryGeometrySupport.simplify(source, algorithm, 0.05,
                    GeometryUnaryPolicy.PRESERVE_DIMENSION, CoordinateDimension.XYZ);
            assertTrue(result.isValid());
            assertTrue(result.getNumPoints() < source.getNumPoints() / 10);
            assertEquals(source.getCoordinateN(0), result.getCoordinates()[0]);
            assertEquals(source.getCoordinateN(source.getNumPoints() - 1), result.getCoordinates()[result.getNumPoints() - 1]);
            dimension(result, CoordinateDimension.XYZ);
            // A monotone-X source permits one linear pass over the output segments.
            Coordinate[] simplified = result.getCoordinates();
            int segment = 1;
            for (var point : coordinates) {
                while (segment < simplified.length - 1 && point.x > simplified[segment].x) segment++;
                assertTrue(new LineSegment(simplified[segment - 1], simplified[segment]).distance(point) <= 0.05 + 1e-12);
            }
            assertTrue(source.equalsExact(original));
        }
    }

    @Test void longAlternatingLineDoesNotExhaustTheJavaCallStack() {
        var factory = new GeometryFactory(new PrecisionModel(), 3857);
        var coordinates = new Coordinate[8_001];
        for (int i = 0; i < coordinates.length; i++) coordinates[i] = new Coordinate(i, i % 2);
        var source = factory.createLineString(coordinates);
        var result = UnaryGeometrySupport.simplify(source, GeometrySimplifyAlgorithm.DOUGLAS_PEUCKER, 0.1,
                GeometryUnaryPolicy.OUTPUT_XY, CoordinateDimension.XY);
        assertTrue(result.equalsExact(source));
    }
}
