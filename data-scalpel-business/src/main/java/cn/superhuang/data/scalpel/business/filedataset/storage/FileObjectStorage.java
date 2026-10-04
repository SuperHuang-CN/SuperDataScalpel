package cn.superhuang.data.scalpel.business.filedataset.storage;

import cn.superhuang.data.scalpel.filegdb.FileGeodatabase;
import cn.superhuang.data.scalpel.shapefile.ShapefileComponent;
import cn.superhuang.data.scalpel.shapefile.ShapefileDataset;
import cn.superhuang.data.scalpel.shapefile.ShapefileOpenOptions;

import java.io.InputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.Objects;
import java.util.Set;

/** Internal storage for platform-managed file-dataset contents. */
public interface FileObjectStorage {

    StoredFileObject store(String objectKey, InputStream inputStream, long contentLength, String contentType);

    /** Applies a per-call upload budget where supported, without changing ordinary file uploads. */
    default StoredFileObject store(String objectKey, InputStream inputStream, long contentLength,
                                  String contentType, Duration timeout) {
        return store(objectKey, inputStream, contentLength, contentType);
    }

    FileObjectContent open(String objectKey);

    /**
     * Bounds opening the response where supported. The returned stream still needs its own read
     * deadline and abort handling; this timeout does not bound consumption of the response body.
     */
    default FileObjectContent open(String objectKey, Duration timeout) {
        return open(objectKey);
    }

    void delete(String objectKey);

    /** Deletes every object below an immutable materialized directory prefix. */
    default void deletePrefix(String prefix) {
        throw new FileStorageException("当前对象存储不支持目录前缀删除", null);
    }

    /** Shares one cleanup budget across listing and deleting the whole prefix where supported. */
    default void deletePrefix(String prefix, Duration timeout) {
        deletePrefix(prefix);
    }

    /** Opens an unpacked FileGDB below an immutable materialized directory prefix. */
    default FileGeodatabase openFileGeodatabase(String prefix) {
        throw new FileStorageException("当前对象存储不支持 GDB 目录读取", null);
    }

    default FileGeodatabase openFileGeodatabase(String prefix, cn.superhuang.data.scalpel.filegdb.FileGdbOpenOptions options) {
        throw new FileStorageException("当前对象存储不支持带读取限额的 GDB 目录读取", null);
    }

    /** Opens one canonical, immutable Shapefile component set below a materialized prefix. */
    default ShapefileDataset openShapefile(
            String prefix,
            Set<ShapefileComponent> components,
            ShapefileOpenOptions options
    ) {
        throw new FileStorageException("当前对象存储不支持 SHP 组件集读取", null);
    }

    record StoredFileObject(String eTag) {
    }

    final class FileObjectContent implements AutoCloseable {

        private final InputStream inputStream;
        private final long contentLength;
        private final String contentType;
        private final Runnable abortAction;

        public FileObjectContent(InputStream inputStream, long contentLength, String contentType) {
            this(inputStream, contentLength, contentType, () -> closeInputStream(inputStream));
        }

        public FileObjectContent(InputStream inputStream, long contentLength, String contentType, Runnable abortAction) {
            this.inputStream = Objects.requireNonNull(inputStream, "inputStream");
            this.contentLength = contentLength;
            this.contentType = contentType;
            this.abortAction = Objects.requireNonNull(abortAction, "abortAction");
        }

        public InputStream inputStream() {
            return inputStream;
        }

        public long contentLength() {
            return contentLength;
        }

        public String contentType() {
            return contentType;
        }

        /** Stops an incomplete remote response without first draining its remaining bytes. */
        public void abort() {
            abortAction.run();
        }

        @Override
        public void close() throws IOException {
            inputStream.close();
        }

        private static void closeInputStream(InputStream inputStream) {
            try {
                inputStream.close();
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }
    }
}
