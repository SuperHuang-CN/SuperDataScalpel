package cn.superhuang.data.scalpel.filegdb.model.geometry;

import cn.superhuang.data.scalpel.filegdb.model.FileGdbEnvelope;
import java.util.Arrays;
import java.util.Objects;

/** Polygon rings and coordinates exactly as stored, without orientation or topology repair. */
public final class FileGdbPolygon implements FileGdbGeometry {
    private final FileGdbEnvelope envelope;
    private final int[] ringPointCounts;
    private final FileGdbCoordinateSequence coordinates;

    public FileGdbPolygon(
            FileGdbEnvelope envelope,
            int[] ringPointCounts,
            FileGdbCoordinateSequence coordinates) {
        this.envelope = Objects.requireNonNull(envelope, "envelope");
        this.ringPointCounts = Objects.requireNonNull(ringPointCounts, "ringPointCounts").clone();
        this.coordinates = Objects.requireNonNull(coordinates, "coordinates");
        long total = 0;
        for (int count : this.ringPointCounts) {
            if (count <= 0) {
                throw new IllegalArgumentException("ring point counts must be positive");
            }
            total += count;
        }
        if (total != coordinates.size()) {
            throw new IllegalArgumentException("ring point counts must cover the coordinate sequence");
        }
    }

    public FileGdbEnvelope envelope() {
        return envelope;
    }

    public int ringCount() {
        return ringPointCounts.length;
    }

    public int ringPointCount(int ringIndex) {
        return ringPointCounts[ringIndex];
    }

    public int[] ringPointCounts() {
        return ringPointCounts.clone();
    }

    public FileGdbCoordinateSequence coordinates() {
        return coordinates;
    }

    @Override
    public boolean hasZ() {
        return coordinates.hasZ();
    }

    @Override
    public boolean hasM() {
        return coordinates.hasM();
    }

    @Override
    public boolean equals(Object candidate) {
        if (this == candidate) {
            return true;
        }
        if (!(candidate instanceof FileGdbPolygon other)) {
            return false;
        }
        return envelope.equals(other.envelope)
                && Arrays.equals(ringPointCounts, other.ringPointCounts)
                && coordinates.equals(other.coordinates);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * envelope.hashCode() + Arrays.hashCode(ringPointCounts))
                + coordinates.hashCode();
    }
}
