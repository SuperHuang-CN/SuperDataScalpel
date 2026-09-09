package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.SpatialBinAggregateConfiguration;
import cn.superhuang.data.scalpel.contract.task.SpatialH3Options;
import com.uber.h3core.H3Core;
import com.uber.h3core.LengthUnit;
import net.sf.geographiclib.Geodesic;
import net.sf.geographiclib.GeodesicMask;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.geom.util.AffineTransformation;
import org.locationtech.jts.operation.union.UnaryUnionOp;

import java.util.ArrayList;
import java.util.List;

/** H3 owns assignment; spherical boundary rendering does not determine point membership. */
final class H3GridSupport {
    private static final GeometryFactory FACTORY = new GeometryFactory(new PrecisionModel(), 4326);
    private static final Geodesic SPHERE = new Geodesic(6371007.180918475, 0);
    private static final int POSITION = GeodesicMask.STANDARD | GeodesicMask.LONG_UNROLL;
    private H3GridSupport() { }

    private static class Holder {
        static final H3Core CORE = create();
        private static H3Core create() {
            try { return H3Core.newInstance(); }
            catch (Exception | LinkageError error) { throw new IllegalStateException("SPATIAL_H3_RUNTIME_UNAVAILABLE", error); }
        }
    }
    static H3Core core() { return Holder.CORE; }

    static double averageDiameter(int resolution) {
        return Math.sqrt(3d) * core().getHexagonEdgeLengthAvg(resolution, LengthUnit.m);
    }

    static int nearestResolution(double metres) {
        if (!Double.isFinite(metres) || metres <= 0) throw new IllegalArgumentException("INVALID_SPATIAL_H3_SIZE");
        int best = 0;
        double difference = Math.abs(averageDiameter(0) - metres);
        for (int r = 1; r <= 15; r++) {
            double next = Math.abs(averageDiameter(r) - metres);
            if (next < difference) { difference = next; best = r; }
        }
        return best;
    }

    static Integer resolve(SpatialBinAggregateConfiguration c, CanvasNodeIssueSink issues) {
        if (c.h3() == null || c.h3().mode() == null) {
            issues.error("SPATIAL_H3_MODE_REQUIRED", "请选择 H3 分辨率或近似尺寸模式", "configuration.h3.mode"); return null;
        }
        if (c.h3().mode() == SpatialH3Options.Mode.RESOLUTION) {
            Integer r = c.h3().resolution();
            if (r == null || r < 0 || r > 15) {
                issues.error("INVALID_SPATIAL_H3_RESOLUTION", "H3 分辨率必须为 0 到 15 的整数", "configuration.h3.resolution"); return null;
            }
            return r;
        }
        double metres = c.binSize() * SpatialDistanceSupport.metresPerConfiguredUnit(c.binSizeUnit());
        if (!Double.isFinite(metres) || metres <= 0) {
            issues.error("INVALID_SPATIAL_H3_SIZE", "H3 近似尺寸必须是有效线性距离，不能使用来源 CRS 的度数", "configuration.binSizeUnit"); return null;
        }
        int r = nearestResolution(metres);
        issues.warning("SPATIAL_H3_RESOLUTION_SELECTED", "按平均对边距离选择 H3 分辨率 " + r + "；不同位置的实际格网尺寸并不相同", "configuration.h3.mode");
        return r;
    }

    static String cell(Geometry geometry, int resolution) {
        if (geometry == null || geometry.isEmpty()) return null;
        if (!(geometry instanceof Point) || !geometry.isValid()) throw new IllegalArgumentException("SPATIAL_H3_POINT_INVALID");
        Coordinate p = geometry.getCoordinate();
        if (!Double.isFinite(p.x) || !Double.isFinite(p.y) || p.x < -180 || p.x > 180 || p.y < -90 || p.y > 90)
            throw new IllegalArgumentException("SPATIAL_H3_POINT_INVALID");
        // Exactly +/-180 and arbitrary longitudes at a pole describe the same point.
        double longitude = Math.abs(p.y) == 90 ? 0 : p.x == 180 ? -180 : p.x;
        return core().latLngToCellAddress(p.y, longitude, resolution);
    }

    static MultiPolygon boundary(String address) {
        if (address == null) return null;
        // Native loading failures are configuration failures, not geometry failures.
        core();
        try { return renderBoundary(address); }
        catch (RuntimeException error) {
            // JTS/native exception text can contain coordinates or cell identifiers.
            // Do not attach it as a cause that Spark could print before Runner redaction.
            throw new IllegalArgumentException("SPATIAL_H3_BOUNDARY_INVALID");
        }
    }

    private static MultiPolygon renderBoundary(String address) {
        var vertices = core().cellToBoundary(address);
        List<Coordinate> ring = new ArrayList<>();
        var first = vertices.getFirst();
        ring.add(new Coordinate(first.lng, first.lat));
        for (int i = 0; i < vertices.size(); i++) {
            var a = vertices.get(i); var b = vertices.get((i + 1) % vertices.size());
            double startLongitude = ring.getLast().x;
            var inverse = SPHERE.Inverse(a.lat, startLongitude, b.lat, b.lng);
            var line = SPHERE.Line(a.lat, startLongitude, inverse.azi1);
            int samples = Math.max(1, (int) Math.ceil(inverse.s12 / 25000d));
            for (int j = 1; j <= samples; j++) {
                double distance = inverse.s12 * j / samples;
                var p = line.Position(distance, POSITION);
                // Insert the actual spherical meridian crossing before rectangular clipping.
                Coordinate previous = ring.getLast();
                int oldSlab = (int) Math.floor((previous.x + 180) / 360);
                int newSlab = (int) Math.floor((p.lon2 + 180) / 360);
                if (oldSlab != newSlab) {
                    double cut = p.lon2 > previous.x ? 180 + 360d * oldSlab : -180 + 360d * oldSlab;
                    double low = inverse.s12 * (j - 1) / samples, high = distance;
                    for (int iteration = 0; iteration < 55; iteration++) {
                        double mid = (low + high) / 2;
                        double lon = line.Position(mid, POSITION).lon2;
                        if ((lon < cut) == (p.lon2 > previous.x)) low = mid; else high = mid;
                    }
                    ring.add(new Coordinate(cut, line.Position((low + high) / 2, POSITION).lat2));
                }
                ring.add(new Coordinate(p.lon2, p.lat2));
            }
        }
        Coordinate start = ring.getFirst(), end = ring.getLast();
        if (Math.abs(end.x - start.x) > 180) {
            double pole = core().cellToLatLng(address).lat >= 0 ? 90 : -90;
            ring.add(new Coordinate(end.x, pole)); ring.add(new Coordinate(start.x, pole));
            ring.add(start.copy());
        } else ring.set(ring.size() - 1, start.copy());
        Geometry unwrapped = FACTORY.createPolygon(ring.toArray(Coordinate[]::new));
        List<Geometry> parts = new ArrayList<>();
        Envelope envelope = unwrapped.getEnvelopeInternal();
        int from = (int) Math.floor((envelope.getMinX() + 180) / 360), to = (int) Math.floor((envelope.getMaxX() + 180) / 360);
        for (int slab = from; slab <= to; slab++) {
            Geometry box = FACTORY.toGeometry(new Envelope(-180 + 360d * slab, 180 + 360d * slab, -90, 90));
            Geometry clipped = unwrapped.intersection(box);
            if (!clipped.isEmpty() && clipped.getDimension() == 2) parts.add(AffineTransformation.translationInstance(-360d * slab, 0).transform(clipped));
        }
        Geometry merged = UnaryUnionOp.union(parts, FACTORY);
        List<Polygon> polygons = new ArrayList<>(); collectPolygons(merged, polygons);
        MultiPolygon result = FACTORY.createMultiPolygon(polygons.toArray(Polygon[]::new));
        if (result.isEmpty() || !result.isValid()) throw new IllegalArgumentException("SPATIAL_H3_BOUNDARY_INVALID");
        return result;
    }

    private static void collectPolygons(Geometry geometry, List<Polygon> output) {
        if (geometry instanceof Polygon polygon) output.add(polygon);
        else if (geometry instanceof GeometryCollection collection)
            for (int i = 0; i < collection.getNumGeometries(); i++) collectPolygons(collection.getGeometryN(i), output);
    }
}
