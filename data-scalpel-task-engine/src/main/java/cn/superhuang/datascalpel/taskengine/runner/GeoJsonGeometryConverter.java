package cn.superhuang.datascalpel.taskengine.runner;

import cn.superhuang.data.scalpel.contract.type.CoordinateDimension;
import cn.superhuang.data.scalpel.contract.type.GeometryKind;
import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateXY;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.MultiPoint;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;

import java.util.List;
import java.util.Map;

/** Converts one validated GeoJSON geometry map to the exact logical Geometry column type. */
final class GeoJsonGeometryConverter {

    private GeoJsonGeometryConverter() {
    }

    static Geometry convert(Object source, GeometryTypeDefinition expected) {
        if (source == null) {
            return null;
        }
        if (!(source instanceof Map<?, ?> geometry)) {
            throw invalid("Geometry 运行时值必须是对象");
        }
        Geometry result = geometry(geometry, factory(expected));
        if (!result.isValid()) {
            throw invalid("Geometry 拓扑无效");
        }
        return requireExpectedKind(result, expected.kind());
    }

    private static GeometryFactory factory(GeometryTypeDefinition expected) {
        if (expected == null) {
            throw invalid("Geometry Schema 缺失");
        }
        if (!"EPSG".equals(expected.crs().authority())) {
            throw invalid("文件 Geometry 只支持 EPSG 坐标系");
        }
        if (expected.dimension() != CoordinateDimension.XY) {
            throw invalid("GeoJSON 第一阶段只支持 XY 坐标");
        }
        return new GeometryFactory(new PrecisionModel(), expected.crs().code());
    }

    private static Geometry geometry(Map<?, ?> source, GeometryFactory factory) {
        String type = string(source.get("type"), "Geometry type 缺失或无效");
        return switch (type) {
            case "Point" -> factory.createPoint(position(source.get("coordinates"), "coordinates"));
            case "LineString" -> factory.createLineString(line(source.get("coordinates"), "coordinates"));
            case "Polygon" -> polygon(source.get("coordinates"), "coordinates", factory);
            case "MultiPoint" -> multiPoint(source.get("coordinates"), "coordinates", factory);
            case "MultiLineString" -> multiLineString(source.get("coordinates"), "coordinates", factory);
            case "MultiPolygon" -> multiPolygon(source.get("coordinates"), "coordinates", factory);
            case "GeometryCollection" -> geometryCollection(source.get("geometries"), "geometries", factory);
            default -> throw invalid("不支持的 Geometry type：" + type);
        };
    }

    private static Point[] points(Object source, String path, GeometryFactory factory) {
        List<?> values = list(source, path);
        requireNonEmpty(values, path);
        Point[] result = new Point[values.size()];
        for (int index = 0; index < values.size(); index++) {
            result[index] = factory.createPoint(position(values.get(index), path + "[" + index + "]"));
        }
        return result;
    }

    private static MultiPoint multiPoint(Object source, String path, GeometryFactory factory) {
        return factory.createMultiPoint(points(source, path, factory));
    }

    private static MultiLineString multiLineString(Object source, String path, GeometryFactory factory) {
        List<?> values = list(source, path);
        requireNonEmpty(values, path);
        LineString[] result = new LineString[values.size()];
        for (int index = 0; index < values.size(); index++) {
            result[index] = factory.createLineString(line(values.get(index), path + "[" + index + "]"));
        }
        return factory.createMultiLineString(result);
    }

    private static MultiPolygon multiPolygon(Object source, String path, GeometryFactory factory) {
        List<?> values = list(source, path);
        requireNonEmpty(values, path);
        Polygon[] result = new Polygon[values.size()];
        for (int index = 0; index < values.size(); index++) {
            result[index] = polygon(values.get(index), path + "[" + index + "]", factory);
        }
        return factory.createMultiPolygon(result);
    }

    private static org.locationtech.jts.geom.GeometryCollection geometryCollection(
            Object source,
            String path,
            GeometryFactory factory
    ) {
        List<?> values = list(source, path);
        Geometry[] result = new Geometry[values.size()];
        for (int index = 0; index < values.size(); index++) {
            if (!(values.get(index) instanceof Map<?, ?> geometry)) {
                throw invalid(path + "[" + index + "] 必须是 Geometry 对象");
            }
            result[index] = geometry(geometry, factory);
        }
        return factory.createGeometryCollection(result);
    }

    private static Polygon polygon(Object source, String path, GeometryFactory factory) {
        List<?> rings = list(source, path);
        requireNonEmpty(rings, path);
        LinearRing shell = ring(rings.getFirst(), path + "[0]", factory);
        LinearRing[] holes = new LinearRing[rings.size() - 1];
        for (int index = 1; index < rings.size(); index++) {
            holes[index - 1] = ring(rings.get(index), path + "[" + index + "]", factory);
        }
        return factory.createPolygon(shell, holes);
    }

    private static LinearRing ring(Object source, String path, GeometryFactory factory) {
        Coordinate[] coordinates = line(source, path);
        if (coordinates.length < 4) {
            throw invalid(path + " 面环至少需要四个坐标");
        }
        if (!coordinates[0].equals2D(coordinates[coordinates.length - 1])) {
            throw invalid(path + " 面环必须闭合");
        }
        return factory.createLinearRing(coordinates);
    }

    private static Coordinate[] line(Object source, String path) {
        List<?> positions = list(source, path);
        if (positions.size() < 2) {
            throw invalid(path + " 线至少需要两个坐标");
        }
        Coordinate[] result = new Coordinate[positions.size()];
        for (int index = 0; index < positions.size(); index++) {
            result[index] = position(positions.get(index), path + "[" + index + "]");
        }
        return result;
    }

    private static Coordinate position(Object source, String path) {
        List<?> values = list(source, path);
        if (values.size() != 2) {
            throw invalid(path + " 坐标必须恰好包含 X 和 Y 两个值");
        }
        return new CoordinateXY(number(values.getFirst(), path + "[0]"), number(values.getLast(), path + "[1]"));
    }

    private static double number(Object value, String path) {
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())) {
            throw invalid(path + " 必须是有限数值");
        }
        return number.doubleValue();
    }

    private static List<?> list(Object value, String path) {
        if (value instanceof List<?> values) {
            return values;
        }
        throw invalid(path + " 必须是数组");
    }

    private static void requireNonEmpty(List<?> values, String path) {
        if (values.isEmpty()) {
            throw invalid(path + " 不能为空");
        }
    }

    private static String string(Object value, String message) {
        if (value instanceof String text) return text;
        throw invalid(message);
    }

    private static Geometry requireExpectedKind(Geometry geometry, GeometryKind expected) {
        boolean matches = switch (expected) {
            case GEOMETRY -> true;
            case POINT -> geometry instanceof Point;
            case LINESTRING -> geometry instanceof LineString;
            case POLYGON -> geometry instanceof Polygon;
            case MULTIPOINT -> geometry instanceof MultiPoint;
            case MULTILINESTRING -> geometry instanceof MultiLineString;
            case MULTIPOLYGON -> geometry instanceof MultiPolygon;
            case GEOMETRYCOLLECTION -> geometry instanceof org.locationtech.jts.geom.GeometryCollection;
        };
        if (!matches) {
            throw invalid("Geometry 类型与逻辑 Schema 不一致");
        }
        return geometry;
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException("FILE_GEOJSON_GEOMETRY_INVALID: " + message);
    }
}
