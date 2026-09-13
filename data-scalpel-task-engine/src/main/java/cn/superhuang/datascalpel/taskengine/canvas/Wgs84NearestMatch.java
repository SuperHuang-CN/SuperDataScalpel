package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.datascalpel.taskengine.canvas.Wgs84SegmentDistance.Result;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateXY;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.PrecisionModel;

/**
 * One geodesic solve result shared by nearest filtering, ranking and connection rendering.
 * The attained distance is never replaced by an ECEF, centroid or rendered-line distance.
 */
final class Wgs84NearestMatch {
    static final double SOLVER_TOLERANCE_METRES = 0.0001;

    private Wgs84NearestMatch() { }

    static Match solve(Geometry source, Geometry candidate) {
        Result result = Wgs84GeometryDistance.nearest(source, candidate, SOLVER_TOLERANCE_METRES);
        if (result == null) return null;
        double distance = result.distanceMetres();
        double lower = result.lowerBoundMetres();
        double upper = distance + Wgs84SegmentDistance.ROUNDOFF_METRES;
        if (!Double.isFinite(distance) || !Double.isFinite(lower) || !Double.isFinite(upper)
                || distance < 0 || lower < 0 || lower > upper) {
            throw new IllegalArgumentException("SPATIAL_NEAREST_DISTANCE_INVALID");
        }

        GeometryFactory factory = new GeometryFactory(new PrecisionModel(), 4326);
        LineString witnesses = factory.createLineString(new Coordinate[]{
                new CoordinateXY(result.first().longitude(), result.first().latitude()),
                new CoordinateXY(result.second().longitude(), result.second().latitude())
        });
        boolean provenZero = distance == 0 && lower == 0
                && result.first().equals(result.second());
        // Point and MultiPoint pairs are finite discrete sets. Their branch-and-bound search
        // continues until every pair that could improve or tie the winner has been visited.
        boolean exactDistance = provenZero || result.exactMinimum();
        return new Match(distance, exactDistance ? distance : lower, exactDistance ? distance : upper,
                witnesses, provenZero, exactDistance);
    }

    /** True minimum lies in [lowerBoundMetres, upperBoundMetres]. */
    record Match(
            double distanceMetres,
            double lowerBoundMetres,
            double upperBoundMetres,
            LineString witnesses,
            boolean provenZero,
            boolean exactDistance
    ) { }
}
