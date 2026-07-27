package cn.superhuang.data.scalpel.shapefile.internal;

import cn.superhuang.data.scalpel.shapefile.ShapefileErrorCode;
import cn.superhuang.data.scalpel.shapefile.ShapefileException;
import cn.superhuang.data.scalpel.shapefile.ShapefileRandomAccessObject;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Position-independent, bounded reads from one source component. */
public final class RandomAccessObjectReader implements AutoCloseable {
    private final String role;
    private final ShapefileRandomAccessObject object;
    private final long size;
    private boolean closed;

    public RandomAccessObjectReader(ShapefileRandomAccessObject object, String role) {
        this.object = object;
        this.role = role;
        this.size = object.size();
        if (size < 0) {
            throw new ShapefileException(
                    ShapefileErrorCode.INVALID_SOURCE,
                    role + " component declares a negative size");
        }
    }

    public long size() {
        ensureOpen();
        return size;
    }

    public ByteBuffer read(long position, int length, ByteOrder order) {
        ensureOpen();
        if (position < 0 || length < 0 || position > size - length) {
            throw new ShapefileException(
                    ShapefileErrorCode.TRUNCATED_INPUT, role + " read exceeds the component boundary");
        }
        ByteBuffer target = ByteBuffer.allocate(length).order(order);
        long offset = position;
        try {
            while (target.hasRemaining()) {
                int read = object.read(offset, target);
                if (read < 0) {
                    throw new ShapefileException(
                            ShapefileErrorCode.TRUNCATED_INPUT, role + " ended before the declared structure");
                }
                if (read == 0) {
                    throw new ShapefileException(
                            ShapefileErrorCode.IO_ERROR, role + " read made no progress");
                }
                offset = Math.addExact(offset, read);
            }
        } catch (ShapefileException exception) {
            throw exception;
        } catch (ArithmeticException exception) {
            throw new ShapefileException(
                    ShapefileErrorCode.INVALID_OFFSET, role + " read offset overflow", exception);
        } catch (RuntimeException exception) {
            throw new ShapefileException(
                    ShapefileErrorCode.IO_ERROR, "Cannot read Shapefile " + role + " component", exception);
        }
        return target.flip();
    }

    private void ensureOpen() {
        if (closed) {
            throw new ShapefileException(ShapefileErrorCode.CLOSED, role + " component is closed");
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
