package cn.superhuang.data.scalpel.filegdb;

/**
 * A single unpacked File Geodatabase directory exposed as named random-access objects.
 * A source is owned and closed by {@link FileGeodatabase} after it is passed to {@code open}.
 */
public interface FileGdbSource extends AutoCloseable {
    FileGdbSourceInfo info();

    boolean exists(String fileName);

    FileGdbRandomAccessObject open(String fileName);

    @Override
    void close();
}
