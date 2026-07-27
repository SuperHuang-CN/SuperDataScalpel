package cn.superhuang.data.scalpel.shapefile.model.geometry;

import cn.superhuang.data.scalpel.shapefile.model.ShapefileEnvelope;
import java.util.Objects;

/** MultiPoint coordinates exactly as stored. */
public record ShapefileMultiPoint(
        ShapefileEnvelope envelope,
        ShapefileCoordinateSequence coordinates) implements ShapefileGeometry {
    public ShapefileMultiPoint {
        Objects.requireNonNull(envelope, "envelope");
        Objects.requireNonNull(coordinates, "coordinates");
    }

    @Override
    public boolean hasZ() {
        return coordinates.hasZ();
    }

    @Override
    public boolean hasM() {
        return coordinates.hasM();
    }
}
