package cn.superhuang.data.scalpel.filegdb;

import cn.superhuang.data.scalpel.filegdb.model.FileGdbFeature;
import java.util.Iterator;

/** Forward-only feature cursor that owns its table and index file handles. */
public interface FileGdbFeatureCursor extends Iterator<FileGdbFeature>, AutoCloseable {
    @Override
    void close();
}
