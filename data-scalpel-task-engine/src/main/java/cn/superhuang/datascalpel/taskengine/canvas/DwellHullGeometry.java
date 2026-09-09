package cn.superhuang.datascalpel.taskengine.canvas;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.util.AffineTransformation;

import java.util.ArrayList;
import java.util.List;

/** A local longitude-unwrapped hull, split at the dateline; not an Esri spherical-hull equivalence claim. */
final class DwellHullGeometry {
    private DwellHullGeometry() { }

    static Geometry hull(Geometry points, boolean geodesic) {
        if (points == null || points.isEmpty()) return points;
        if (!geodesic) return points.convexHull();
        double reference = DwellRangeAssignment.meanCenter(points, true).getX();
        Coordinate[] coordinates = points.getCoordinates();
        for (Coordinate coordinate : coordinates) {
            while (coordinate.x - reference > 180) coordinate.x -= 360;
            while (coordinate.x - reference < -180) coordinate.x += 360;
        }
        Geometry hull = points.getFactory().createMultiPointFromCoords(coordinates).convexHull();
        Envelope envelope = hull.getEnvelopeInternal();
        int first = (int) Math.floor((envelope.getMinX() + 180) / 360);
        int last = (int) Math.floor((envelope.getMaxX() + 180) / 360);
        if (first == last) return AffineTransformation.translationInstance(-first * 360d, 0).transform(hull);
        List<Geometry> parts = new ArrayList<>();
        for (int index = first; index <= last; index++) {
            Geometry strip = points.getFactory().toGeometry(new Envelope(-180 + index * 360d, 180 + index * 360d, -90, 90));
            Geometry part = hull.intersection(strip);
            if (!part.isEmpty()) parts.add(AffineTransformation.translationInstance(-index * 360d, 0).transform(part));
        }
        return points.getFactory().buildGeometry(parts);
    }
}
