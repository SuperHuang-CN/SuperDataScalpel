package cn.superhuang.data.scalpel.filegdb;

import java.nio.ByteBuffer;

/** Position-independent access to one physical file in an unpacked File Geodatabase. */
public interface FileGdbRandomAccessObject extends AutoCloseable {
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
