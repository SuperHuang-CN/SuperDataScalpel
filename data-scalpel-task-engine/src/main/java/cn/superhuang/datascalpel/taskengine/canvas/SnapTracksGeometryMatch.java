package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.SpatialDistanceMethod;
import net.sf.geographiclib.Geodesic;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.linearref.LengthIndexedLine;
import org.locationtech.jts.operation.distance.DistanceOp;

/** Exact candidate projection used by Snap Tracks after the distributed spatial candidate join. */
final class SnapTracksGeometryMatch {
    private SnapTracksGeometryMatch() {
    }

    static Match solve(
            Point point,
            LineString line,
            SpatialDistanceMethod method,
            double sourceUnitsPerMetre
    ) {
        if (point == null || point.isEmpty() || line == null || line.isEmpty()) return null;
        if (method == SpatialDistanceMethod.GEODESIC) return geodesic(point, line);
        Coordinate snappedCoordinate = DistanceOp.nearestPoints(line, point)[0];
        GeometryFactory factory = line.getFactory();
        Point snapped = factory.createPoint(snappedCoordinate.copy());
        snapped.setSRID(line.getSRID());
        double sourceLength = line.getLength();
        double sourceIndex = new LengthIndexedLine(line).project(snappedCoordinate);
        double distanceMetres = point.distance(snapped) / sourceUnitsPerMetre;
        double lengthMetres = sourceLength / sourceUnitsPerMetre;
        double fraction = sourceLength == 0d ? 0d : clamp(sourceIndex / sourceLength);
        return checked(snapped, distanceMetres, fraction, lengthMetres);
    }

    private static Match geodesic(Point point, LineString line) {
        Wgs84NearestMatch.Match nearest = Wgs84NearestMatch.solve(point, line);
        if (nearest == null) return null;
        Coordinate witness = nearest.witnesses().getCoordinateN(1);
        Point snapped = line.getFactory().createPoint(witness.copy());
        snapped.setSRID(4326);
        Coordinate[] coordinates = line.getCoordinates();
        double[] lengths = new double[Math.max(0, coordinates.length - 1)];
        double total = 0d;
        for (int index = 0; index < lengths.length; index++) {
            lengths[index] = inverse(coordinates[index], coordinates[index + 1]);
            total += lengths[index];
        }
        double selectedOffset = 0d;
        double smallestResidual = Double.POSITIVE_INFINITY;
        double prefix = 0d;
        for (int index = 0; index < lengths.length; index++) {
            double from = inverse(coordinates[index], witness);
            double to = inverse(witness, coordinates[index + 1]);
            double residual = Math.abs(from + to - lengths[index]);
            if (residual < smallestResidual) {
                smallestResidual = residual;
                selectedOffset = prefix + Math.min(from, lengths[index]);
            }
            prefix += lengths[index];
        }
        double fraction = total == 0d ? 0d : clamp(selectedOffset / total);
        return checked(snapped, nearest.distanceMetres(), fraction, total);
    }

    private static double inverse(Coordinate first, Coordinate second) {
        return Geodesic.WGS84.Inverse(first.y, first.x, second.y, second.x).s12;
    }

    private static Match checked(Point point, double distanceMetres, double fraction, double lengthMetres) {
        if (!Double.isFinite(distanceMetres) || distanceMetres < 0d
                || !Double.isFinite(fraction) || fraction < 0d || fraction > 1d
                || !Double.isFinite(lengthMetres) || lengthMetres < 0d) {
            throw new IllegalArgumentException("SNAP_TRACKS_MATCH_GEOMETRY_INVALID");
        }
        return new Match(point, distanceMetres, fraction, lengthMetres);
    }

    private static double clamp(double value) {
        return Math.max(0d, Math.min(1d, value));
    }

    record Match(Point snappedPoint, double distanceMetres, double fraction, double lineLengthMetres) {
    }
}
