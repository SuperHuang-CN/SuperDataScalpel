package cn.superhuang.datascalpel.taskengine.runner;

import cn.superhuang.data.scalpel.contract.type.CoordinateDimension;
import cn.superhuang.data.scalpel.contract.type.GeometryKind;
import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;
import cn.superhuang.data.scalpel.filegdb.model.geometry.FileGdbCoordinateSequence;
import cn.superhuang.data.scalpel.filegdb.model.geometry.FileGdbGeometry;
import cn.superhuang.data.scalpel.filegdb.model.geometry.FileGdbMultiPoint;
import cn.superhuang.data.scalpel.filegdb.model.geometry.FileGdbPoint;
import cn.superhuang.data.scalpel.filegdb.model.geometry.FileGdbPolygon;
import cn.superhuang.data.scalpel.filegdb.model.geometry.FileGdbPolyline;
import cn.superhuang.data.scalpel.shapefile.model.geometry.ShapefileCoordinateSequence;
import cn.superhuang.data.scalpel.shapefile.model.geometry.ShapefileGeometry;
import cn.superhuang.data.scalpel.shapefile.model.geometry.ShapefileMultiPoint;
import cn.superhuang.data.scalpel.shapefile.model.geometry.ShapefilePoint;
import cn.superhuang.data.scalpel.shapefile.model.geometry.ShapefilePolygon;
import cn.superhuang.data.scalpel.shapefile.model.geometry.ShapefilePolyline;
import org.locationtech.jts.algorithm.Orientation;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateXY;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Strict XY conversion from the project's bounded file readers into Sedona's JTS representation. */
final class FileDatasetGeometryConverter {

    private FileDatasetGeometryConverter() {
    }

    static Geometry convert(FileGdbGeometry source, GeometryTypeDefinition expected) {
        if (source == null) {
            return null;
        }
        requireSourceDimension(source.hasZ(), source.hasM(), expected, "GDB");
        GeometryFactory factory = factory(expected);
        Geometry geometry = switch (source) {
            case FileGdbPoint point -> factory.createPoint(coordinate(point.x(), point.y()));
            case FileGdbMultiPoint multiPoint -> factory.createMultiPointFromCoords(
                    coordinates(sequence(multiPoint.coordinates()), 0, multiPoint.coordinates().size())
            );
            case FileGdbPolyline polyline -> multiLineString(
                    factory, sequence(polyline.coordinates()), polyline.pathPointCounts()
            );
            case FileGdbPolygon polygon -> multiPolygon(
                    factory, sequence(polygon.coordinates()), polygon.ringPointCounts()
            );
        };
        return requireExpectedKind(geometry, expected.kind());
    }

    static Geometry convert(ShapefileGeometry source, GeometryTypeDefinition expected) {
        if (source == null) {
            return null;
        }
        requireSourceDimension(source.hasZ(), source.hasM(), expected, "SHP");
        GeometryFactory factory = factory(expected);
        Geometry geometry = switch (source) {
            case ShapefilePoint point -> factory.createPoint(coordinate(point.x(), point.y()));
            case ShapefileMultiPoint multiPoint -> factory.createMultiPointFromCoords(
                    coordinates(sequence(multiPoint.coordinates()), 0, multiPoint.coordinates().size())
            );
            case ShapefilePolyline polyline -> multiLineString(
                    factory, sequence(polyline.coordinates()), polyline.partPointCounts()
            );
            case ShapefilePolygon polygon -> multiPolygon(
                    factory, sequence(polygon.coordinates()), polygon.ringPointCounts()
            );
        };
        return requireExpectedKind(geometry, expected.kind());
    }

    private static CoordinateDimension dimension(boolean hasZ, boolean hasM) {
        if (hasZ && hasM) return CoordinateDimension.XYZM;
        if (hasZ) return CoordinateDimension.XYZ;
        return hasM ? CoordinateDimension.XYM : CoordinateDimension.XY;
    }

    private static void requireSourceDimension(
            boolean hasZ,
            boolean hasM,
            GeometryTypeDefinition expected,
            String format
    ) {
        if (expected == null || expected.dimension() != dimension(hasZ, hasM)) {
            throw new IllegalArgumentException(
                    "FILE_GEOMETRY_DIMENSION_UNSUPPORTED: " + format + " Geometry 维度无法转换"
            );
        }
    }

    private static GeometryFactory factory(GeometryTypeDefinition expected) {
        if (expected == null) {
            throw new IllegalArgumentException("FILE_GEOMETRY_SCHEMA_REQUIRED: Geometry Schema 缺失");
        }
        if (!"EPSG".equals(expected.crs().authority())) {
            throw new IllegalArgumentException("UNSUPPORTED_GEOMETRY_CRS: 文件 Geometry 只支持 EPSG");
        }
        if (expected.dimension() != CoordinateDimension.XY) {
            throw new IllegalArgumentException("UNSUPPORTED_GEOMETRY_DIMENSION: 文件 Geometry 第一阶段只支持 XY");
        }
        return new GeometryFactory(new PrecisionModel(), expected.crs().code());
    }

    private static MultiLineString multiLineString(
            GeometryFactory factory,
            CoordinateSource source,
            int[] pointCounts
    ) {
        LineString[] lines = new LineString[pointCounts.length];
        int offset = 0;
        for (int index = 0; index < pointCounts.length; index++) {
            int count = pointCounts[index];
            if (count < 2) {
                throw new IllegalArgumentException("FILE_GEOMETRY_INVALID: 线部件至少需要两个点");
            }
            lines[index] = factory.createLineString(coordinates(source, offset, count));
            offset += count;
        }
        requireCovered(source, offset);
        return factory.createMultiLineString(lines);
    }

    private static MultiPolygon multiPolygon(
            GeometryFactory factory,
            CoordinateSource source,
            int[] pointCounts
    ) {
        List<LinearRing> shells = new ArrayList<>();
        List<LinearRing> holes = new ArrayList<>();
        int offset = 0;
        for (int count : pointCounts) {
            if (count < 4) {
                throw new IllegalArgumentException("FILE_GEOMETRY_INVALID: 面环至少需要四个点");
            }
            Coordinate[] coordinates = coordinates(source, offset, count);
            if (!coordinates[0].equals2D(coordinates[coordinates.length - 1])) {
                throw new IllegalArgumentException("FILE_GEOMETRY_INVALID: 面环没有闭合");
            }
            LinearRing ring = factory.createLinearRing(coordinates);
            if (Orientation.isCCW(coordinates)) {
                holes.add(ring);
            } else {
                shells.add(ring);
            }
            offset += count;
        }
        requireCovered(source, offset);
        if (shells.isEmpty() && !holes.isEmpty()) {
            throw new IllegalArgumentException("FILE_GEOMETRY_TOPOLOGY_INVALID: 面没有顺时针外环");
        }

        List<ShellWithHoles> groups = shells.stream()
                .map(shell -> new ShellWithHoles(shell, factory.createPolygon(shell)))
                .toList();
        for (LinearRing hole : holes) {
            Point representative = factory.createPoint(hole.getCoordinateN(0));
            ShellWithHoles owner = groups.stream()
                    .filter(group -> group.shellPolygon().covers(representative))
                    .min(Comparator.comparingDouble(group -> group.shellPolygon().getArea()))
                    .orElseThrow(() -> new IllegalArgumentException(
                            "FILE_GEOMETRY_TOPOLOGY_INVALID: 内环不属于任何外环"
                    ));
            owner.holes().add(hole);
        }
        Polygon[] polygons = groups.stream()
                .map(group -> factory.createPolygon(
                        group.shell(), group.holes().toArray(LinearRing[]::new)
                ))
                .toArray(Polygon[]::new);
        MultiPolygon result = factory.createMultiPolygon(polygons);
        if (!result.isValid()) {
            throw new IllegalArgumentException("FILE_GEOMETRY_TOPOLOGY_INVALID: 面拓扑无效");
        }
        return result;
    }

    private static Geometry requireExpectedKind(Geometry geometry, GeometryKind expected) {
        boolean matches = switch (expected) {
            case GEOMETRY -> true;
            case POINT -> geometry instanceof org.locationtech.jts.geom.Point;
            case LINESTRING -> geometry instanceof org.locationtech.jts.geom.LineString;
            case POLYGON -> geometry instanceof org.locationtech.jts.geom.Polygon;
            case MULTIPOINT -> geometry instanceof org.locationtech.jts.geom.MultiPoint;
            case MULTILINESTRING -> geometry instanceof MultiLineString;
            case MULTIPOLYGON -> geometry instanceof MultiPolygon;
            case GEOMETRYCOLLECTION -> geometry instanceof org.locationtech.jts.geom.GeometryCollection;
        };
        if (!matches) {
            throw new IllegalArgumentException(
                    "FILE_GEOMETRY_KIND_UNSUPPORTED: Geometry 类型无法转换为 " + expected
            );
        }
        return geometry;
    }

    private static Coordinate[] coordinates(CoordinateSource source, int offset, int count) {
        Coordinate[] values = new Coordinate[count];
        for (int index = 0; index < count; index++) {
            values[index] = coordinate(source.x(offset + index), source.y(offset + index));
        }
        return values;
    }

    private static Coordinate coordinate(double x, double y) {
        if (!Double.isFinite(x) || !Double.isFinite(y)) {
            throw new IllegalArgumentException("FILE_GEOMETRY_INVALID: Geometry 坐标不是有限数值");
        }
        return new CoordinateXY(x, y);
    }

    private static void requireCovered(CoordinateSource source, int consumed) {
        if (consumed != source.size()) {
            throw new IllegalArgumentException("FILE_GEOMETRY_INVALID: Geometry 部件没有覆盖全部坐标");
        }
    }

    private static CoordinateSource sequence(FileGdbCoordinateSequence source) {
        return new CoordinateSource() {
            @Override public int size() { return source.size(); }
            @Override public double x(int index) { return source.x(index); }
            @Override public double y(int index) { return source.y(index); }
        };
    }

    private static CoordinateSource sequence(ShapefileCoordinateSequence source) {
        return new CoordinateSource() {
            @Override public int size() { return source.size(); }
            @Override public double x(int index) { return source.x(index); }
            @Override public double y(int index) { return source.y(index); }
        };
    }

    private interface CoordinateSource {
        int size();
        double x(int index);
        double y(int index);
    }

    private record ShellWithHoles(
            LinearRing shell,
            Polygon shellPolygon,
            List<LinearRing> holes
    ) {
        private ShellWithHoles(LinearRing shell, Polygon shellPolygon) {
            this(shell, shellPolygon, new ArrayList<>());
        }
    }
}
