package cn.superhuang.data.scalpel.shapefile.model.geometry;

/** One point; null Z/M means the dimension is absent from this record. */
public record ShapefilePoint(double x, double y, Double z, Double m) implements ShapefileGeometry {
    @Override
    public boolean hasZ() {
        return z != null;
    }

    @Override
    public boolean hasM() {
        return m != null;
    }
}
