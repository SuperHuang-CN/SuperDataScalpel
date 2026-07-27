package cn.superhuang.data.scalpel.filegdb.internal;

import cn.superhuang.data.scalpel.filegdb.FileGdbErrorCode;
import cn.superhuang.data.scalpel.filegdb.FileGdbException;
import cn.superhuang.data.scalpel.filegdb.FileGdbFeatureCursor;
import cn.superhuang.data.scalpel.filegdb.FileGdbReadLimits;
import cn.superhuang.data.scalpel.filegdb.model.FileGdbFeature;
import java.util.NoSuchElementException;

/** Resource-owning implementation of the public bounded cursor. */
final class GdbFeatureCursorImpl implements FileGdbFeatureCursor {
    private final GdbTableDefinition definition;
    private final GdbTableIndexReader indexReader;
    private final GdbTableReader tableReader;
    private final FileGdbReadLimits limits;
    private final int limit;
    private final Runnable closeCallback;
    private int nextSlot;
    private int returned;
    private FileGdbFeature prefetched;
    private boolean exhausted;
    private boolean explicitlyClosed;
    private boolean resourcesClosed;

    GdbFeatureCursorImpl(
            GdbTableDefinition definition,
            GdbTableIndexReader indexReader,
            GdbTableReader tableReader,
            FileGdbReadLimits limits,
            int limit,
            Runnable closeCallback) {
        this.definition = definition;
        this.indexReader = indexReader;
        this.tableReader = tableReader;
        this.limits = limits;
        this.limit = limit;
        this.closeCallback = closeCallback;
        if (indexReader.slotCount() > limits.maxIndexSlotsPerCursor()) {
            closeResources();
            throw new FileGdbException(
                    FileGdbErrorCode.LIMIT_EXCEEDED,
                    definition.physicalName() + " index exceeds the configured cursor slot limit");
        }
    }

    @Override
    public boolean hasNext() {
        if (explicitlyClosed) {
            throw new FileGdbException(FileGdbErrorCode.CLOSED, "FileGDB cursor is closed");
        }
        if (exhausted) {
            return false;
        }
        if (prefetched != null) {
            return true;
        }
        if (returned >= limit) {
            exhaust();
            return false;
        }
        try {
            while (nextSlot < indexReader.slotCount()) {
                int slot = nextSlot++;
                long offset = indexReader.recordOffset(slot);
                if (offset == 0) {
                    continue;
                }
                int oid = Math.addExact(slot, 1);
                prefetched = GdbRecordReader.read(
                        oid,
                        tableReader.readRecord(offset, oid),
                        definition,
                        limits);
                return true;
            }
            exhaust();
            return false;
        } catch (RuntimeException exception) {
            closeResources();
            exhausted = true;
            throw exception;
        }
    }

    @Override
    public FileGdbFeature next() {
        if (!hasNext()) {
            throw new NoSuchElementException("No more FileGDB features");
        }
        FileGdbFeature result = prefetched;
        prefetched = null;
        returned++;
        return result;
    }

    @Override
    public void close() {
        if (explicitlyClosed) {
            return;
        }
        explicitlyClosed = true;
        prefetched = null;
        closeResources();
    }

    private void exhaust() {
        exhausted = true;
        prefetched = null;
        closeResources();
    }

    private void closeResources() {
        if (resourcesClosed) {
            return;
        }
        resourcesClosed = true;
        RuntimeException failure = null;
        try {
            tableReader.close();
        } catch (RuntimeException exception) {
            failure = exception;
        }
        try {
            indexReader.close();
        } catch (RuntimeException exception) {
            if (failure == null) {
                failure = exception;
            } else {
                failure.addSuppressed(exception);
            }
        } finally {
            closeCallback.run();
        }
        if (failure != null) {
            throw failure;
        }
    }
}
