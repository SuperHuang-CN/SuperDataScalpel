package cn.superhuang.datascalpel.taskengine.runner;

import cn.superhuang.data.scalpel.contract.type.CoordinateDimension;
import cn.superhuang.data.scalpel.contract.type.CrsReference;
import cn.superhuang.data.scalpel.contract.type.GeometryKind;
import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;
import cn.superhuang.data.scalpel.shapefile.model.ShapefileEnvelope;
import cn.superhuang.data.scalpel.shapefile.model.ShapefileField;
import cn.superhuang.data.scalpel.shapefile.model.ShapefileFieldType;
import cn.superhuang.data.scalpel.shapefile.model.geometry.ShapefileCoordinateSequence;
import cn.superhuang.data.scalpel.shapefile.model.geometry.ShapefilePoint;
import cn.superhuang.data.scalpel.shapefile.model.geometry.ShapefilePolygon;
import cn.superhuang.data.scalpel.filegdb.model.geometry.FileGdbPoint;
import cn.superhuang.data.scalpel.filegdb.model.FileGdbField;
import cn.superhuang.data.scalpel.filegdb.model.FileGdbFieldType;
import cn.superhuang.data.scalpel.filegdb.model.FileGdbLayerType;
import cn.superhuang.data.scalpel.filegdb.model.FileGdbSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.shapefile.model.ShapefileSchema;
import cn.superhuang.data.scalpel.shapefile.model.ShapefileShapeType;
import cn.superhuang.data.scalpel.shapefile.model.ShapefileSpatialReference;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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

    @Test
    void rejectsRuntimeCrsDriftBeforeReadingFeatures() {
        ShapefileSchema source = new ShapefileSchema(
                ShapefileShapeType.POINT,
                0,
                List.of(),
                new ShapefileEnvelope(0, 0, 0, 0, 0, 0, 0, 0),
                StandardCharsets.UTF_8,
                new ShapefileSpatialReference("GEOGCS[\"WGS 84\",AUTHORITY[\"EPSG\",\"4326\"]]")
        );
        CanvasColumnSchema expected = new CanvasColumnSchema(
                "_geometry", PlatformDataType.GEOMETRY,
                null, null, null, true, null, false, false, null,
                new GeometryTypeDefinition(
                        GeometryKind.POINT, CrsReference.epsg(3857), CoordinateDimension.XY
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> FileDatasetGeometryConverter.validateSchema(source, List.of(expected))
        );
    }

    @Test
    void rejectsShapefileGeometryFieldNameDriftBeforeReadingFeatures() {
        ShapefileSchema source = new ShapefileSchema(
                ShapefileShapeType.POINT,
                0,
                List.of(new ShapefileField(
                        "_geometry", ShapefileFieldType.STRING, 32, 0, true, 0
                )),
                new ShapefileEnvelope(0, 0, 0, 0, 0, 0, 0, 0),
                StandardCharsets.UTF_8,
                new ShapefileSpatialReference("GEOGCS[\"WGS 84\",AUTHORITY[\"EPSG\",\"4326\"]]")
        );
        CanvasColumnSchema staleGeometryColumn = new CanvasColumnSchema(
                "_geometry", PlatformDataType.GEOMETRY,
                null, null, null, true, null, false, false, null,
                geometry(GeometryKind.POINT, CoordinateDimension.XY)
        );

        assertThrows(
                FileDatasetSpatialSchemaDriftException.class,
                () -> FileDatasetGeometryConverter.validateSchema(
                        source,
                        List.of(
                                scalar("_geometry", PlatformDataType.STRING, 32, true),
                                staleGeometryColumn
                        )
                )
        );
    }

    @Test
    void normalizesFileSpatialDriftWithoutLosingTheStableCodeOrNodeIdentity() {
        FileDatasetReadException failure = FileDatasetBatchReaderRegistry.normalizeFailure(
                "file-node-id",
                "行政区文件输入",
                new RuntimeException(new FileDatasetSpatialSchemaDriftException("private object key"))
        );

        assertEquals("SPATIAL_SCHEMA_DRIFT", failure.code());
        assertEquals("file-node-id", failure.nodeId());
        assertEquals("行政区文件输入", failure.nodeName());
        assertEquals("文件 Geometry Schema 与任务定义不一致", failure.getMessage());
    }

    @Test
    void rejectsShapefileScalarFieldDriftBeforeReadingFeatures() {
        ShapefileSchema source = new ShapefileSchema(
                ShapefileShapeType.POINT,
                0,
                List.of(new ShapefileField(
                        "region_name", ShapefileFieldType.STRING, 32, 0, true, 0
                )),
                new ShapefileEnvelope(0, 0, 0, 0, 0, 0, 0, 0),
                StandardCharsets.UTF_8,
                null
        );
        CanvasColumnSchema staleScalar = scalar("old_region_name", PlatformDataType.STRING, 32, true);
        CanvasColumnSchema geometry = new CanvasColumnSchema(
                "_geometry", PlatformDataType.GEOMETRY,
                null, null, null, true, null, false, false, null,
                geometry(GeometryKind.POINT, CoordinateDimension.XY)
        );

        assertThrows(
                FileDatasetSpatialSchemaDriftException.class,
                () -> FileDatasetGeometryConverter.validateSchema(source, List.of(staleScalar, geometry))
        );
    }

    @Test
    void rejectsFileGdbScalarTypeDriftBeforeReadingFeatures() {
        FileGdbSchema source = new FileGdbSchema(
                "layer-1",
                "points",
                FileGdbLayerType.POINT,
                List.of(
                        new FileGdbField("region_id", "Region ID", FileGdbFieldType.INT32, false, 0),
                        new FileGdbField("SHAPE", "Shape", FileGdbFieldType.SHAPE, true, 0)
                ),
                null
        );
        CanvasColumnSchema staleScalar = scalar("region_id", PlatformDataType.INTEGER, null, false);
        CanvasColumnSchema geometry = new CanvasColumnSchema(
                "SHAPE", PlatformDataType.GEOMETRY,
                null, null, null, true, null, false, false, null,
                geometry(GeometryKind.POINT, CoordinateDimension.XY)
        );

        assertThrows(
                FileDatasetSpatialSchemaDriftException.class,
                () -> FileDatasetGeometryConverter.validateSchema(source, List.of(staleScalar, geometry))
        );
    }

    @Test
    void acceptsAConfirmedEpsgWhenFileGdbHasNoEmbeddedSpatialReference() {
        FileGdbSchema source = new FileGdbSchema(
                "layer-1",
                "points",
                FileGdbLayerType.POINT,
                List.of(new FileGdbField("SHAPE", "Shape", FileGdbFieldType.SHAPE, true, 0)),
                null
        );
        CanvasColumnSchema expected = new CanvasColumnSchema(
                "SHAPE", PlatformDataType.GEOMETRY,
                null, null, null, true, null, false, false, null,
                geometry(GeometryKind.POINT, CoordinateDimension.XY)
        );

        assertDoesNotThrow(() -> FileDatasetGeometryConverter.validateSchema(source, List.of(expected)));
    }

    private static GeometryTypeDefinition geometry(GeometryKind kind, CoordinateDimension dimension) {
        return new GeometryTypeDefinition(kind, CrsReference.epsg(4326), dimension);
    }

    private static CanvasColumnSchema scalar(
            String name,
            PlatformDataType type,
            Integer length,
            boolean nullable
    ) {
        return new CanvasColumnSchema(
                name, type, length, null, null, nullable,
                null, false, false, null
        );
    }
}
