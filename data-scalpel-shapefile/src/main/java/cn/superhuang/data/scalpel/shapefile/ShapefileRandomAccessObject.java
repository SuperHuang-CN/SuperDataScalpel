package cn.superhuang.data.scalpel.shapefile;

import java.nio.ByteBuffer;

/** Position-independent access to one physical Shapefile component. */
public interface ShapefileRandomAccessObject extends AutoCloseable {
    long size();

    /**
     * Reads bytes starting at {@code position} into {@code target}.
     *
     * @return the number of bytes read, {@code -1} at end of input, or zero only when the target has no remaining bytes
     */
    int read(long position, ByteBuffer target);

    @Override
    void close();
}
