package cn.superhuang.data.scalpel.shapefile.model.geometry;

import cn.superhuang.data.scalpel.shapefile.model.ShapefileEnvelope;
import java.util.Arrays;
import java.util.Objects;

/** Polyline parts and coordinates exactly as stored. */
public final class ShapefilePolyline implements ShapefileGeometry {
    private final ShapefileEnvelope envelope;
    private final int[] partPointCounts;
    private final ShapefileCoordinateSequence coordinates;

    public ShapefilePolyline(
            ShapefileEnvelope envelope,
            int[] partPointCounts,
            ShapefileCoordinateSequence coordinates) {
        this.envelope = Objects.requireNonNull(envelope, "envelope");
        this.partPointCounts = Objects.requireNonNull(partPointCounts, "partPointCounts").clone();
        this.coordinates = Objects.requireNonNull(coordinates, "coordinates");
        validateParts(this.partPointCounts, coordinates.size(), 2);
    }

    public ShapefileEnvelope envelope() { return envelope; }
    public int partCount() { return partPointCounts.length; }
    public int partPointCount(int index) { return partPointCounts[index]; }
    public int[] partPointCounts() { return partPointCounts.clone(); }
    public ShapefileCoordinateSequence coordinates() { return coordinates; }
    @Override public boolean hasZ() { return coordinates.hasZ(); }
    @Override public boolean hasM() { return coordinates.hasM(); }

    @Override
    public boolean equals(Object candidate) {
        return candidate instanceof ShapefilePolyline other
                && envelope.equals(other.envelope)
                && Arrays.equals(partPointCounts, other.partPointCounts)
                && coordinates.equals(other.coordinates);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * envelope.hashCode() + Arrays.hashCode(partPointCounts)) + coordinates.hashCode();
    }

    static void validateParts(int[] counts, int pointCount, int minimum) {
        long total = 0;
        for (int count : counts) {
            if (count < minimum) {
                throw new IllegalArgumentException("each part contains too few points");
            }
            total += count;
        }
        if (total != pointCount) {
            throw new IllegalArgumentException("part point counts must cover the coordinate sequence");
        }
    }
}
