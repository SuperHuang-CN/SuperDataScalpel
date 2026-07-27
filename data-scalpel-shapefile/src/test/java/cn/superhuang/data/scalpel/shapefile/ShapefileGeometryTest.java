package cn.superhuang.data.scalpel.shapefile;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.superhuang.data.scalpel.shapefile.model.ShapefileEnvelope;
import cn.superhuang.data.scalpel.shapefile.model.ShapefileShapeType;
import cn.superhuang.data.scalpel.shapefile.model.geometry.ShapefileGeometry;
import cn.superhuang.data.scalpel.shapefile.model.geometry.ShapefileMultiPoint;
import cn.superhuang.data.scalpel.shapefile.model.geometry.ShapefilePoint;
import cn.superhuang.data.scalpel.shapefile.model.geometry.ShapefilePolygon;
import cn.superhuang.data.scalpel.shapefile.model.geometry.ShapefilePolyline;
import cn.superhuang.data.scalpel.shapefile.testutil.TestShapefileBuilder;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ShapefileGeometryTest {
    private static final ShapefileEnvelope ENVELOPE =
            new ShapefileEnvelope(0, 0, 10, 10, 0, 20, -1.0E39, 100);

    @TempDir
    Path temporaryDirectory;

    @Test
    void readsEverySupportedNonNullShapeVariant() throws Exception {
        for (ShapefileShapeType type : List.of(
                ShapefileShapeType.POINT,
                ShapefileShapeType.POINT_Z,
                ShapefileShapeType.POINT_M,
                ShapefileShapeType.MULTIPOINT,
                ShapefileShapeType.MULTIPOINT_Z,
                ShapefileShapeType.MULTIPOINT_M,
                ShapefileShapeType.POLYLINE,
                ShapefileShapeType.POLYLINE_Z,
                ShapefileShapeType.POLYLINE_M,
                ShapefileShapeType.POLYGON,
                ShapefileShapeType.POLYGON_Z,
                ShapefileShapeType.POLYGON_M)) {
            ShapefileGeometry geometry = readFirst(
                    "variant-" + type.name().toLowerCase(java.util.Locale.ROOT),
                    type,
                    geometryFor(type));
            assertEquals(type.hasZ(), geometry.hasZ(), type::name);
            assertEquals(type.hasM(), geometry.hasM(), type::name);
        }
    }

    @Test
    void readsPointZAndNormalizesNoDataMeasure() throws Exception {
        ShapefilePoint point = assertInstanceOf(ShapefilePoint.class, readFirst(
                "pointz", ShapefileShapeType.POINT_Z,
                TestShapefileBuilder.point(ShapefileShapeType.POINT_Z, 1, 2, 3.5, -1.0E39)));
        assertEquals(3.5, point.z());
        assertTrue(Double.isNaN(point.m()));
    }

    @Test
    void readsMultiPointM() throws Exception {
        ShapefileMultiPoint geometry = assertInstanceOf(ShapefileMultiPoint.class, readFirst(
                "multipoint", ShapefileShapeType.MULTIPOINT_M,
                TestShapefileBuilder.multiPoint(
                        ShapefileShapeType.MULTIPOINT_M,
                        new double[] {1, 3}, new double[] {2, 4}, null, new double[] {5, -1.0E39})));
        assertArrayEquals(new double[] {1, 3}, geometry.coordinates().xValues());
        assertEquals(5, geometry.coordinates().m(0));
        assertTrue(Double.isNaN(geometry.coordinates().m(1)));
    }

    @Test
    void readsMultipartPolylineZWithoutOptionalMeasureBlock() throws Exception {
        ShapefilePolyline geometry = assertInstanceOf(ShapefilePolyline.class, readFirst(
                "linez", ShapefileShapeType.POLYLINE_Z,
                TestShapefileBuilder.multipart(
                        ShapefileShapeType.POLYLINE_Z,
                        new int[] {2, 2},
                        new double[] {0, 1, 2, 3}, new double[] {0, 1, 2, 3},
                        new double[] {10, 11, 12, 13}, null)));
        assertArrayEquals(new int[] {2, 2}, geometry.partPointCounts());
        assertTrue(geometry.hasZ());
        assertFalse(geometry.hasM());
    }

    @Test
    void readsPolygonMWithoutChangingRingOrder() throws Exception {
        double[] x = {0, 4, 4, 0, 0, 1, 1, 2, 2, 1};
        double[] y = {0, 0, 4, 4, 0, 1, 2, 2, 1, 1};
        ShapefilePolygon geometry = assertInstanceOf(ShapefilePolygon.class, readFirst(
                "polygonm", ShapefileShapeType.POLYGON_M,
                TestShapefileBuilder.multipart(
                        ShapefileShapeType.POLYGON_M,
                        new int[] {5, 5}, x, y, null,
                        new double[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10})));
        assertArrayEquals(new int[] {5, 5}, geometry.ringPointCounts());
        assertArrayEquals(x, geometry.coordinates().xValues());
        assertTrue(geometry.hasM());
    }

    @Test
    void coordinateAndPartArraysAreDefensivelyCopied() {
        double[] x = {0, 1};
        double[] y = {2, 3};
        var sequence = new cn.superhuang.data.scalpel.shapefile.model.geometry.ShapefileCoordinateSequence(
                x, y, null, null);
        x[0] = 99;
        y[0] = 99;
        assertEquals(0, sequence.x(0));
        assertEquals(2, sequence.y(0));
        double[] exposed = sequence.xValues();
        exposed[0] = 88;
        assertEquals(0, sequence.x(0));

        int[] counts = {2};
        ShapefilePolyline line = new ShapefilePolyline(ENVELOPE, counts, sequence);
        counts[0] = 99;
        int[] exposedCounts = line.partPointCounts();
        exposedCounts[0] = 99;
        assertEquals(2, line.partPointCount(0));
    }

    private static byte[] geometryFor(ShapefileShapeType type) {
        double[] z = type.hasZ() ? new double[] {10, 11, 12, 13, 14} : null;
        double[] m = type.hasM() ? new double[] {20, 21, 22, 23, 24} : null;
        return switch (type) {
            case POINT, POINT_Z, POINT_M -> TestShapefileBuilder.point(
                    type, 1, 2, type.hasZ() ? 3.0 : null, type.hasM() ? 4.0 : null);
            case MULTIPOINT, MULTIPOINT_Z, MULTIPOINT_M -> TestShapefileBuilder.multiPoint(
                    type,
                    new double[] {0, 1},
                    new double[] {2, 3},
                    type.hasZ() ? new double[] {10, 11} : null,
                    type.hasM() ? new double[] {20, 21} : null);
            case POLYLINE, POLYLINE_Z, POLYLINE_M -> TestShapefileBuilder.multipart(
                    type,
                    new int[] {2, 2},
                    new double[] {0, 1, 2, 3},
                    new double[] {0, 1, 2, 3},
                    type.hasZ() ? java.util.Arrays.copyOf(z, 4) : null,
                    type.hasM() ? java.util.Arrays.copyOf(m, 4) : null);
            case POLYGON, POLYGON_Z, POLYGON_M -> TestShapefileBuilder.multipart(
                    type,
                    new int[] {5},
                    new double[] {0, 4, 4, 0, 0},
                    new double[] {0, 0, 4, 4, 0},
                    z,
                    m);
            case NULL, MULTIPATCH -> throw new IllegalArgumentException("not a readable non-null variant");
        };
    }

    private ShapefileGeometry readFirst(String name, ShapefileShapeType type, byte[] geometry) throws Exception {
        Path shp = new TestShapefileBuilder(temporaryDirectory, name, type, ENVELOPE)
                .field("Id", 'N', 6, 0)
                .record(geometry, false, "1")
                .write();
        try (ShapefileDataset dataset = ShapefileDataset.open(shp);
                ShapefileFeatureCursor cursor = dataset.openCursor(ShapefileReadOptions.limit(1))) {
            return cursor.next().geometry();
        }
    }
}
