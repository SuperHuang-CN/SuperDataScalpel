package cn.superhuang.datascalpel.taskengine.runner;

import cn.superhuang.data.scalpel.contract.type.CoordinateDimension;
import cn.superhuang.data.scalpel.contract.type.CrsReference;
import cn.superhuang.data.scalpel.contract.type.GeometryKind;
import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.Point;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GeoJsonGeometryConverterTest {

    @Test
    void convertsXyPointWithoutSwappingCoordinatesAndAssignsConfiguredSrid() {
        Point point = assertInstanceOf(Point.class, GeoJsonGeometryConverter.convert(
                Map.of("type", "Point", "coordinates", List.of(116.3, 39.9)),
                geometry(GeometryKind.POINT, 4490)
        ));

        assertEquals(4490, point.getSRID());
        assertEquals(116.3, point.getX());
        assertEquals(39.9, point.getY());
    }

    @Test
    void convertsGeometryCollectionWithTheDeclaredGeometrySchema() {
        GeometryCollection collection = assertInstanceOf(GeometryCollection.class, GeoJsonGeometryConverter.convert(
                Map.of(
                        "type", "GeometryCollection",
                        "geometries", List.of(
                                Map.of("type", "Point", "coordinates", List.of(0, 0)),
                                Map.of("type", "LineString", "coordinates", List.of(List.of(0, 0), List.of(1, 1)))
                        )
                ),
                geometry(GeometryKind.GEOMETRYCOLLECTION, 4326)
        ));

        assertEquals(2, collection.getNumGeometries());
        assertEquals(4326, collection.getSRID());
    }

    @Test
    void rejectsNonXyCoordinatesAndSchemaKindMismatches() {
        assertThrows(IllegalArgumentException.class, () -> GeoJsonGeometryConverter.convert(
                Map.of("type", "Point", "coordinates", List.of(120, 30, 8)),
                geometry(GeometryKind.POINT, 4326)
        ));
        assertThrows(IllegalArgumentException.class, () -> GeoJsonGeometryConverter.convert(
                Map.of("type", "Point", "coordinates", List.of(120, 30)),
                geometry(GeometryKind.POLYGON, 4326)
        ));
    }

    private static GeometryTypeDefinition geometry(GeometryKind kind, int epsgCode) {
        return new GeometryTypeDefinition(kind, CrsReference.epsg(epsgCode), CoordinateDimension.XY);
    }
}
