package cn.superhuang.data.scalpel.filegdb.internal;

import cn.superhuang.data.scalpel.filegdb.FileGdbErrorCode;
import cn.superhuang.data.scalpel.filegdb.FileGdbException;
import cn.superhuang.data.scalpel.filegdb.FileGdbRandomAccessObject;
import cn.superhuang.data.scalpel.filegdb.FileGdbReadLimits;
import cn.superhuang.data.scalpel.filegdb.FileGdbSource;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Position-independent, little-endian random access with deterministic short-read failures. */
final class LittleEndianRandomAccessReader implements AutoCloseable {
    private final FileGdbRandomAccessObject object;
    private final String role;
    private final long size;
    private boolean closed;

    private LittleEndianRandomAccessReader(FileGdbRandomAccessObject object, String role, long size) {
        this.object = object;
        this.role = role;
        this.size = size;
    }

    static LittleEndianRandomAccessReader open(
            FileGdbSource source,
            String fileName,
            String role,
            FileGdbReadLimits limits) {
        FileGdbRandomAccessObject object = source.open(fileName);
        try {
            long size = object.size();
            if (size < 0) {
                throw new FileGdbException(FileGdbErrorCode.MALFORMED_HEADER, role + " has a negative size");
            }
            if (size > limits.maxTableFileBytes()) {
                throw new FileGdbException(
                        FileGdbErrorCode.LIMIT_EXCEEDED,
                        role + " exceeds the configured file-size limit");
            }
            return new LittleEndianRandomAccessReader(object, role, size);
        } catch (RuntimeException exception) {
            try {
                object.close();
            } catch (RuntimeException closeFailure) {
                exception.addSuppressed(closeFailure);
            }
            throw exception;
        }
    }

    long size() {
        ensureOpen();
        return size;
    }

    ByteBuffer read(long position, int length) {
        if (length < 0) {
            throw new FileGdbException(FileGdbErrorCode.INVALID_OFFSET, role + " has a negative read length");
        }
        requireRange(position, length);
        ByteBuffer target = ByteBuffer.allocate(length).order(ByteOrder.LITTLE_ENDIAN);
        readFully(position, target);
        target.flip();
        return target;
    }

    void readFully(long position, ByteBuffer target) {
        ensureOpen();
        int requested = target.remaining();
        requireRange(position, requested);
        long cursor = position;
        try {
            while (target.hasRemaining()) {
                int count = object.read(cursor, target);
                if (count < 0) {
                    throw new FileGdbException(
                            FileGdbErrorCode.TRUNCATED_INPUT,
                            role + " ended before the requested bytes were read");
                }
                if (count == 0) {
                    throw new FileGdbException(
                            FileGdbErrorCode.IO_ERROR,
                            role + " made no progress while reading");
                }
                cursor = Math.addExact(cursor, count);
            }
        } catch (FileGdbException exception) {
            throw exception;
        } catch (ArithmeticException exception) {
            throw new FileGdbException(FileGdbErrorCode.INVALID_OFFSET, role + " read offset overflow", exception);
        }
    }

    int readInt(long position) {
        return read(position, Integer.BYTES).getInt();
    }

    long readLong(long position) {
        return read(position, Long.BYTES).getLong();
    }

    private void requireRange(long position, long length) {
        if (position < 0 || length < 0) {
            throw new FileGdbException(FileGdbErrorCode.INVALID_OFFSET, role + " has a negative byte range");
        }
        final long end;
        try {
            end = Math.addExact(position, length);
        } catch (ArithmeticException exception) {
            throw new FileGdbException(FileGdbErrorCode.INVALID_OFFSET, role + " byte range overflow", exception);
        }
        if (end > size) {
            throw new FileGdbException(
                    FileGdbErrorCode.TRUNCATED_INPUT,
                    role + " is shorter than its declared structure");
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new FileGdbException(FileGdbErrorCode.CLOSED, role + " is closed");
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        object.close();
    }
}
