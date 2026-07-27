package cn.superhuang.data.scalpel.filegdb.model.geometry;

import cn.superhuang.data.scalpel.filegdb.model.FileGdbEnvelope;
import java.util.Arrays;
import java.util.Objects;

/** Polyline paths and coordinates exactly as stored, without direction or topology repair. */
public final class FileGdbPolyline implements FileGdbGeometry {
    private final FileGdbEnvelope envelope;
    private final int[] pathPointCounts;
    private final FileGdbCoordinateSequence coordinates;

    public FileGdbPolyline(
            FileGdbEnvelope envelope,
            int[] pathPointCounts,
            FileGdbCoordinateSequence coordinates) {
        this.envelope = Objects.requireNonNull(envelope, "envelope");
        this.pathPointCounts = Objects.requireNonNull(pathPointCounts, "pathPointCounts").clone();
        this.coordinates = Objects.requireNonNull(coordinates, "coordinates");
        long total = 0;
        for (int count : this.pathPointCounts) {
            if (count <= 0) {
                throw new IllegalArgumentException("path point counts must be positive");
            }
            total += count;
        }
        if (total != coordinates.size()) {
            throw new IllegalArgumentException("path point counts must cover the coordinate sequence");
        }
    }

    public FileGdbEnvelope envelope() {
        return envelope;
    }

    public int pathCount() {
        return pathPointCounts.length;
    }

    public int pathPointCount(int pathIndex) {
        return pathPointCounts[pathIndex];
    }

    public int[] pathPointCounts() {
        return pathPointCounts.clone();
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
        if (!(candidate instanceof FileGdbPolyline other)) {
            return false;
        }
        return envelope.equals(other.envelope)
                && Arrays.equals(pathPointCounts, other.pathPointCounts)
                && coordinates.equals(other.coordinates);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * envelope.hashCode() + Arrays.hashCode(pathPointCounts))
                + coordinates.hashCode();
    }
}
