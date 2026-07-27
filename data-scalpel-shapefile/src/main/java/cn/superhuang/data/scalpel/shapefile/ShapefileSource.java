package cn.superhuang.data.scalpel.shapefile;

/**
 * One unpacked Shapefile component set exposed as fixed-role random-access objects.
 * The source owns every object it opens and is itself owned by {@link ShapefileDataset}
 * after it is passed to an {@code open} method. Source and object close operations must be idempotent.
 */
public interface ShapefileSource extends AutoCloseable {
    ShapefileSourceInfo info();

    boolean exists(ShapefileComponent component);

    ShapefileRandomAccessObject open(ShapefileComponent component);

    @Override
    void close();
}
