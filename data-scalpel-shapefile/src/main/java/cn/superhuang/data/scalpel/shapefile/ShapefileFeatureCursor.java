package cn.superhuang.data.scalpel.shapefile;

import cn.superhuang.data.scalpel.shapefile.model.ShapefileFeature;
import java.util.Iterator;

/** Closeable, bounded iterator over active Shapefile/DBF records. */
public interface ShapefileFeatureCursor extends Iterator<ShapefileFeature>, AutoCloseable {
    @Override
    void close();
}
