package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.*;
import cn.superhuang.data.scalpel.contract.type.CoordinateDimension;
import cn.superhuang.data.scalpel.contract.type.GeometryKind;
import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.geom.impl.CoordinateArraySequenceFactory;
import org.locationtech.jts.simplify.DouglasPeuckerSimplifier;
import org.locationtech.jts.simplify.TopologyPreservingSimplifier;

/** Checked one-feature operations, shared by the two unary nodes; never repairs, projects or runs Spark actions. */
final class UnaryGeometrySupport {
    private UnaryGeometrySupport() { }
    static boolean checked(GeometryUnaryPolicy policy) { return policy != null && policy != GeometryUnaryPolicy.LEGACY; }
    static CoordinateDimension dimension(GeometryUnaryPolicy policy, CoordinateDimension source) {
        return policy == GeometryUnaryPolicy.OUTPUT_XY ? CoordinateDimension.XY : source;
    }
    static void validateSource(CanvasColumnSchema column, String path, CanvasNodeIssueSink issues) {
        if (column.geometry() == null) issues.error("GEOMETRY_TYPE_DEFINITION_REQUIRED", "来源缺少 Geometry 类型定义", path);
        else if (!"EPSG".equals(column.geometry().crs().authority()))
            issues.error("UNSUPPORTED_GEOMETRY_CRS", "当前仅支持明确的 EPSG CRS", path);
    }
    static void validate(GeometryTypeDefinition source, GeometryUnaryPolicy policy, GeometryDeriveKind kind,
            String path, CanvasNodeIssueSink issues) {
        if (source == null || !checked(policy)) return;
        if (kind == GeometryDeriveKind.BOUNDARY && source.kind() == GeometryKind.GEOMETRYCOLLECTION)
            issues.error("GEOMETRY_UNARY_KIND_UNSUPPORTED", "集合的拓扑边界未定义，请先提取具体几何类型", path + ".sourceColumnName");
        if (source.dimension() != CoordinateDimension.XY && policy == GeometryUnaryPolicy.PRESERVE_DIMENSION
                && (kind == GeometryDeriveKind.CENTROID || kind == GeometryDeriveKind.POINT_ON_SURFACE || kind == GeometryDeriveKind.ENVELOPE))
            issues.error("GEOMETRY_UNARY_DIMENSION_UNSUPPORTED", "该函数不能可靠派生 Z/M，请显式选择输出 XY", path + ".geometryPolicy");
        if (source.dimension() != CoordinateDimension.XY && policy == GeometryUnaryPolicy.OUTPUT_XY)
            issues.warning("GEOMETRY_UNARY_OUTPUT_XY", "仅派生结果输出 XY；来源 Geometry 的 Z/M 保留", path + ".geometryPolicy");
    }

    static Geometry derive(Geometry input, GeometryDeriveKind kind, GeometryUnaryPolicy policy, CoordinateDimension sourceDimension) {
        if (input == null) return null;
        requireValid(input);
        if (kind == GeometryDeriveKind.BOUNDARY && input.getGeometryType().equals("GeometryCollection"))
            throw new IllegalArgumentException("GEOMETRY_UNARY_KIND_UNSUPPORTED");
        var dimension = dimension(policy, sourceDimension);
        Geometry source = coordinates(input, dimension);
        Geometry result = switch (kind) {
            case CENTROID -> source.getCentroid();
            case POINT_ON_SURFACE -> source.getInteriorPoint();
            case ENVELOPE -> source.getEnvelope();
            case CONVEX_HULL -> source.convexHull();
            case BOUNDARY -> source.getBoundary();
        };
        return coordinates(result, dimension);
    }

    static Geometry simplify(Geometry input, GeometrySimplifyAlgorithm algorithm, double tolerance,
            GeometryUnaryPolicy policy, CoordinateDimension sourceDimension) {
        if (input == null) return null;
        requireValid(input);
        var dimension = dimension(policy, sourceDimension);
        Geometry source = coordinates(input, dimension);
        Geometry result;
        if (algorithm == GeometrySimplifyAlgorithm.TOPOLOGY_PRESERVING) {
            result = TopologyPreservingSimplifier.simplify(source, tolerance);
        } else {
            var simplifier = new DouglasPeuckerSimplifier(source);
            simplifier.setDistanceTolerance(tolerance);
            // The normal DP area-repair pass may create vertices with undefined Z/M.
            // Checked semantics instead report an invalid result and retain valid Empty.
            simplifier.setEnsureValid(false);
            result = simplifier.getResultGeometry();
        }
        if (!result.isValid()) throw new IllegalArgumentException("GEOMETRY_SIMPLIFY_RESULT_INVALID");
        return coordinates(result, dimension);
    }

    static void validateSimplify(GeometryTypeDefinition source, GeometryUnaryPolicy policy, GeometrySimplifyAlgorithm algorithm,
            CanvasNodeIssueSink issues) {
        validate(source, policy, null, "configuration", issues);
        if (source != null && policy == GeometryUnaryPolicy.PRESERVE_DIMENSION && algorithm == GeometrySimplifyAlgorithm.DOUGLAS_PEUCKER
                && (source.dimension() == CoordinateDimension.XYM || source.dimension() == CoordinateDimension.XYZM))
            issues.error("GEOMETRY_UNARY_DIMENSION_UNSUPPORTED", "Douglas-Peucker 无法保留 M，请选择单要素拓扑保持或显式输出 XY", "configuration.geometryPolicy");
    }

    private static void requireValid(Geometry input) {
        if (!input.isValid()) throw new IllegalArgumentException("GEOMETRY_UNARY_INPUT_INVALID");
    }

    /** Rebuild sequences, including Empty, so WKB dimension is real rather than metadata-only. */
    private static Geometry coordinates(Geometry input, CoordinateDimension dimension) {
        boolean z = dimension == CoordinateDimension.XYZ || dimension == CoordinateDimension.XYZM;
        boolean m = dimension == CoordinateDimension.XYM || dimension == CoordinateDimension.XYZM;
        int size = 2 + (z ? 1 : 0) + (m ? 1 : 0);
        var factory = new GeometryFactory(input.getPrecisionModel(), input.getSRID(), CoordinateArraySequenceFactory.instance());
        Geometry result = copyGeometry(input, factory, sequence -> {
                var output = factory.getCoordinateSequenceFactory().create(sequence.size(), size, m ? 1 : 0);
                for (int i = 0; i < sequence.size(); i++) {
                    double x = sequence.getX(i), y = sequence.getY(i);
                    if (!Double.isFinite(x) || !Double.isFinite(y)) throw new IllegalArgumentException("GEOMETRY_UNARY_INPUT_INVALID");
                    output.setOrdinate(i, 0, x); output.setOrdinate(i, 1, y);
                    if (z) {
                        if (!Double.isFinite(sequence.getZ(i))) throw new IllegalArgumentException("GEOMETRY_UNARY_DIMENSION_UNSUPPORTED");
                        output.setOrdinate(i, 2, sequence.getZ(i));
                    }
                    if (m) {
                        if (!Double.isFinite(sequence.getM(i))) throw new IllegalArgumentException("GEOMETRY_UNARY_DIMENSION_UNSUPPORTED");
                        output.setOrdinate(i, size - 1, sequence.getM(i));
                    }
                }
                return output;
        });
        result.setSRID(input.getSRID());
        return result;
    }

    private static Geometry copyGeometry(Geometry input, GeometryFactory factory,
            java.util.function.UnaryOperator<CoordinateSequence> copy) {
        if (input instanceof Point point) return factory.createPoint(copy.apply(point.getCoordinateSequence()));
        if (input instanceof LinearRing ring) return factory.createLinearRing(copy.apply(ring.getCoordinateSequence()));
        if (input instanceof LineString line) return factory.createLineString(copy.apply(line.getCoordinateSequence()));
        if (input instanceof Polygon polygon) {
            var holes = new LinearRing[polygon.getNumInteriorRing()];
            for (int i = 0; i < holes.length; i++) holes[i] = (LinearRing) copyGeometry(polygon.getInteriorRingN(i), factory, copy);
            return factory.createPolygon((LinearRing) copyGeometry(polygon.getExteriorRing(), factory, copy), holes);
        }
        Geometry[] parts = new Geometry[input.getNumGeometries()];
        for (int i = 0; i < parts.length; i++) parts[i] = copyGeometry(input.getGeometryN(i), factory, copy);
        if (input instanceof MultiPoint) return factory.createMultiPoint(java.util.Arrays.copyOf(parts, parts.length, Point[].class));
        if (input instanceof MultiLineString) return factory.createMultiLineString(java.util.Arrays.copyOf(parts, parts.length, LineString[].class));
        if (input instanceof MultiPolygon) return factory.createMultiPolygon(java.util.Arrays.copyOf(parts, parts.length, Polygon[].class));
        return factory.createGeometryCollection(parts);
    }
}
