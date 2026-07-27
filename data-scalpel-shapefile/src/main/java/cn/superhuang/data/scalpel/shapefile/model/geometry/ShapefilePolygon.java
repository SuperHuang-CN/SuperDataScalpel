package cn.superhuang.data.scalpel.shapefile.model.geometry;

import cn.superhuang.data.scalpel.shapefile.model.ShapefileEnvelope;
import java.util.Arrays;
import java.util.Objects;

/** Polygon rings and coordinates exactly as stored, without orientation or topology repair. */
public final class ShapefilePolygon implements ShapefileGeometry {
    private final ShapefileEnvelope envelope;
    private final int[] ringPointCounts;
    private final ShapefileCoordinateSequence coordinates;

    public ShapefilePolygon(
            ShapefileEnvelope envelope,
            int[] ringPointCounts,
            ShapefileCoordinateSequence coordinates) {
        this.envelope = Objects.requireNonNull(envelope, "envelope");
        this.ringPointCounts = Objects.requireNonNull(ringPointCounts, "ringPointCounts").clone();
        this.coordinates = Objects.requireNonNull(coordinates, "coordinates");
        ShapefilePolyline.validateParts(this.ringPointCounts, coordinates.size(), 4);
    }

    public ShapefileEnvelope envelope() { return envelope; }
    public int ringCount() { return ringPointCounts.length; }
    public int ringPointCount(int index) { return ringPointCounts[index]; }
    public int[] ringPointCounts() { return ringPointCounts.clone(); }
    public ShapefileCoordinateSequence coordinates() { return coordinates; }
    @Override public boolean hasZ() { return coordinates.hasZ(); }
    @Override public boolean hasM() { return coordinates.hasM(); }

    @Override
    public boolean equals(Object candidate) {
        return candidate instanceof ShapefilePolygon other
                && envelope.equals(other.envelope)
                && Arrays.equals(ringPointCounts, other.ringPointCounts)
                && coordinates.equals(other.coordinates);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * envelope.hashCode() + Arrays.hashCode(ringPointCounts)) + coordinates.hashCode();
    }
}
