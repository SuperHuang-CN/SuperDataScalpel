package cn.superhuang.datascalpel.taskengine.runner;

import cn.superhuang.data.scalpel.contract.type.CoordinateDimension;
import cn.superhuang.data.scalpel.contract.type.CrsReference;
import cn.superhuang.data.scalpel.contract.type.GeometryKind;
import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;
import cn.superhuang.data.scalpel.shapefile.model.ShapefileEnvelope;
import cn.superhuang.data.scalpel.shapefile.model.geometry.ShapefileCoordinateSequence;
import cn.superhuang.data.scalpel.shapefile.model.geometry.ShapefilePoint;
import cn.superhuang.data.scalpel.shapefile.model.geometry.ShapefilePolygon;
import cn.superhuang.data.scalpel.filegdb.model.geometry.FileGdbPoint;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FileDatasetGeometryConverterTest {

    @Test
    void convertsAnXyPointAndSetsTheStableEpsgCode() {
        Point point = assertInstanceOf(Point.class, FileDatasetGeometryConverter.convert(
                new ShapefilePoint(120.5, 30.25, null, null),
                geometry(GeometryKind.POINT, CoordinateDimension.XY)
        ));

        assertEquals(4326, point.getSRID());
        assertEquals(120.5, point.getX());
        assertEquals(30.25, point.getY());
    }

    @Test
    void constructsAMultiPolygonAndAssignsCounterClockwiseHoles() {
        ShapefileCoordinateSequence coordinates = new ShapefileCoordinateSequence(
                new double[]{0, 0, 10, 10, 0, 2, 8, 8, 2, 2},
                new double[]{0, 10, 10, 0, 0, 2, 2, 8, 8, 2},
                null,
                null
        );
        ShapefilePolygon source = new ShapefilePolygon(
                new ShapefileEnvelope(0, 0, 10, 10, 0, 0, 0, 0),
                new int[]{5, 5},
                coordinates
        );

        MultiPolygon polygon = assertInstanceOf(MultiPolygon.class, FileDatasetGeometryConverter.convert(
                source,
                geometry(GeometryKind.MULTIPOLYGON, CoordinateDimension.XY)
        ));

        assertEquals(1, polygon.getNumGeometries());
        assertEquals(1, ((org.locationtech.jts.geom.Polygon) polygon.getGeometryN(0)).getNumInteriorRing());
        assertEquals(64.0, polygon.getArea());
    }

    @Test
    void rejectsDimensionsThatTheFirstSpatialFilePhaseCannotExecute() {
        assertThrows(IllegalArgumentException.class, () -> FileDatasetGeometryConverter.convert(
                new ShapefilePoint(1, 2, 3.0, null),
                geometry(GeometryKind.POINT, CoordinateDimension.XYZ)
        ));
    }

    @Test
    void rejectsActualDimensionsThatWouldOtherwiseBeSilentlyDropped() {
        assertThrows(IllegalArgumentException.class, () -> FileDatasetGeometryConverter.convert(
                new ShapefilePoint(1, 2, 3.0, null),
                geometry(GeometryKind.POINT, CoordinateDimension.XY)
        ));
        assertThrows(IllegalArgumentException.class, () -> FileDatasetGeometryConverter.convert(
                new FileGdbPoint(1, 2, null, 4.0),
                geometry(GeometryKind.POINT, CoordinateDimension.XY)
        ));
    }

    @Test
    void convertsFileGdbPointsWithoutSerializingGeometryAsJson() {
        Point point = assertInstanceOf(Point.class, FileDatasetGeometryConverter.convert(
                new FileGdbPoint(116.4, 39.9, null, null),
                geometry(GeometryKind.POINT, CoordinateDimension.XY)
        ));

        assertEquals(4326, point.getSRID());
        assertEquals(116.4, point.getX());
        assertEquals(39.9, point.getY());
    }

    private static GeometryTypeDefinition geometry(GeometryKind kind, CoordinateDimension dimension) {
        return new GeometryTypeDefinition(kind, CrsReference.epsg(4326), dimension);
    }
}
