package cn.superhuang.data.scalpel.shapefile.internal;

import cn.superhuang.data.scalpel.shapefile.ShapefileErrorCode;
import cn.superhuang.data.scalpel.shapefile.ShapefileException;
import java.nio.ByteBuffer;

/** Bounds-checking facade over one little-endian SHP record content buffer. */
final class BoundedRecordBuffer {
    private final ByteBuffer buffer;
    private final long recordNumber;

    BoundedRecordBuffer(ByteBuffer buffer, long recordNumber) {
        this.buffer = buffer;
        this.recordNumber = recordNumber;
    }

    int remaining() {
        return buffer.remaining();
    }

    int readInt(String role) {
        require(4, role);
        return buffer.getInt();
    }

    double readDouble(String role) {
        require(8, role);
        return buffer.getDouble();
    }

    void requireConsumed() {
        if (buffer.hasRemaining()) {
            throw malformed("contains unexpected trailing bytes");
        }
    }

    void require(int length, String role) {
        if (length < 0 || buffer.remaining() < length) {
            throw new ShapefileException(
                    ShapefileErrorCode.TRUNCATED_INPUT,
                    "SHP record " + recordNumber + " is truncated while reading " + role);
        }
    }

    void require(long length, String role) {
        if (length < 0 || length > Integer.MAX_VALUE) {
            throw new ShapefileException(
                    ShapefileErrorCode.LIMIT_EXCEEDED,
                    "SHP record " + recordNumber + " " + role + " cannot be materialized safely");
        }
        require((int) length, role);
    }

    ShapefileException malformed(String detail) {
        return new ShapefileException(
                ShapefileErrorCode.RECORD_MISMATCH, "SHP record " + recordNumber + " " + detail);
    }
}
