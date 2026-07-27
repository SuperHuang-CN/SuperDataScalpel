package cn.superhuang.data.scalpel.filegdb.model.geometry;

/** One FileGDB point. Null Z or M means that the dimension is absent from the schema. */
public record FileGdbPoint(double x, double y, Double z, Double m) implements FileGdbGeometry {
    @Override
    public boolean hasZ() {
        return z != null;
    }

    @Override
    public boolean hasM() {
        return m != null;
    }
}
