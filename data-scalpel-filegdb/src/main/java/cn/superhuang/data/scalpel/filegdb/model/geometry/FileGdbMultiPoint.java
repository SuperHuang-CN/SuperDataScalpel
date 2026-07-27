package cn.superhuang.data.scalpel.filegdb.model.geometry;

import cn.superhuang.data.scalpel.filegdb.model.FileGdbEnvelope;
import java.util.Objects;

/** MultiPoint coordinates exactly as stored, without sorting or deduplication. */
public final class FileGdbMultiPoint implements FileGdbGeometry {
    private final FileGdbEnvelope envelope;
    private final FileGdbCoordinateSequence coordinates;

    public FileGdbMultiPoint(
            FileGdbEnvelope envelope,
            FileGdbCoordinateSequence coordinates) {
        this.envelope = Objects.requireNonNull(envelope, "envelope");
        this.coordinates = Objects.requireNonNull(coordinates, "coordinates");
    }

    public FileGdbEnvelope envelope() {
        return envelope;
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
        if (!(candidate instanceof FileGdbMultiPoint other)) {
            return false;
        }
        return envelope.equals(other.envelope) && coordinates.equals(other.coordinates);
    }

    @Override
    public int hashCode() {
        return 31 * envelope.hashCode() + coordinates.hashCode();
    }
}
