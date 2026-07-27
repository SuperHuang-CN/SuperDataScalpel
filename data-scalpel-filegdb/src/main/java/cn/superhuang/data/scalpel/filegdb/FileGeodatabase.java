package cn.superhuang.data.scalpel.filegdb;

import cn.superhuang.data.scalpel.filegdb.internal.GdbCatalogReader;
import cn.superhuang.data.scalpel.filegdb.internal.GdbLayerHandle;
import cn.superhuang.data.scalpel.filegdb.internal.LocalFileGdbSource;
import cn.superhuang.data.scalpel.filegdb.model.FileGdbLayer;
import cn.superhuang.data.scalpel.filegdb.model.FileGdbSchema;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Entry point for read-only access to one unpacked File Geodatabase source.
 * A database instance and its cursors are not thread-safe; independent instances may read the same data.
 */
public final class FileGeodatabase implements AutoCloseable {
    private final FileGdbSource source;
    private final FileGdbSourceInfo sourceInfo;
    private final List<FileGdbLayer> layers;
    private final Map<String, GdbLayerHandle> handlesById;
    private final Set<FileGdbFeatureCursor> cursors = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    private boolean closed;

    private FileGeodatabase(FileGdbSource source, GdbCatalogReader.OpenedCatalog catalog) {
        this.source = source;
        this.sourceInfo = catalog.sourceInfo();
        LinkedHashMap<String, GdbLayerHandle> handles = new LinkedHashMap<>();
        for (GdbLayerHandle handle : catalog.layers()) {
            if (handles.put(handle.layer().id(), handle) != null) {
                throw new FileGdbException(FileGdbErrorCode.MALFORMED_HEADER, "Duplicate FileGDB layer ID");
            }
        }
        this.handlesById = Map.copyOf(handles);
        this.layers = catalog.layers().stream().map(GdbLayerHandle::layer).toList();
    }

    public static FileGeodatabase open(Path directory) {
        return open(directory, FileGdbOpenOptions.defaults());
    }

    public static FileGeodatabase open(Path directory, FileGdbOpenOptions options) {
        Objects.requireNonNull(options, "options");
        return open(LocalFileGdbSource.open(directory, options), options);
    }

    public static FileGeodatabase open(FileGdbSource source) {
        return open(source, FileGdbOpenOptions.defaults());
    }

    public static FileGeodatabase open(FileGdbSource source, FileGdbOpenOptions options) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(options, "options");
        try {
            return new FileGeodatabase(source, GdbCatalogReader.open(source, options));
        } catch (RuntimeException exception) {
            try {
                source.close();
            } catch (RuntimeException closeFailure) {
                exception.addSuppressed(closeFailure);
            }
            throw exception;
        }
    }

    public FileGdbSourceInfo sourceInfo() {
        ensureOpen();
        return sourceInfo;
    }

    public List<FileGdbLayer> layers() {
        ensureOpen();
        return layers;
    }

    public FileGdbSchema schema(String layerId) {
        ensureOpen();
        return handle(layerId).schema();
    }

    public FileGdbFeatureCursor openCursor(String layerId, FileGdbReadOptions readOptions) {
        ensureOpen();
        Objects.requireNonNull(readOptions, "readOptions");
        GdbLayerHandle handle = handle(layerId);
        final FileGdbFeatureCursor[] holder = new FileGdbFeatureCursor[1];
        FileGdbFeatureCursor cursor = handle.openCursor(
                readOptions.limit(),
                () -> cursors.remove(holder[0]));
        holder[0] = cursor;
        cursors.add(cursor);
        return cursor;
    }

    private GdbLayerHandle handle(String layerId) {
        Objects.requireNonNull(layerId, "layerId");
        GdbLayerHandle handle = handlesById.get(layerId);
        if (handle == null) {
            throw new IllegalArgumentException("Unknown FileGDB layer ID: " + layerId);
        }
        return handle;
    }

    private void ensureOpen() {
        if (closed) {
            throw new FileGdbException(FileGdbErrorCode.CLOSED, "File Geodatabase is closed");
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        RuntimeException failure = null;
        for (FileGdbFeatureCursor cursor : List.copyOf(cursors)) {
            try {
                cursor.close();
            } catch (RuntimeException exception) {
                if (failure == null) {
                    failure = exception;
                } else {
                    failure.addSuppressed(exception);
                }
            }
        }
        cursors.clear();
        try {
            source.close();
        } catch (RuntimeException exception) {
            if (failure == null) {
                failure = exception;
            } else {
                failure.addSuppressed(exception);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }
}
