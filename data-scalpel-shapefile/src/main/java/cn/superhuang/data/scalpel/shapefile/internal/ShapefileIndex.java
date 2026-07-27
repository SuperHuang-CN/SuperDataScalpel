package cn.superhuang.data.scalpel.shapefile.internal;

import cn.superhuang.data.scalpel.shapefile.ShapefileErrorCode;
import cn.superhuang.data.scalpel.shapefile.ShapefileException;
import cn.superhuang.data.scalpel.shapefile.ShapefileReadLimits;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Validated random access to fixed-size SHX entries. */
public final class ShapefileIndex {
    private final RandomAccessObjectReader shx;
    private final long recordCount;

    private ShapefileIndex(RandomAccessObjectReader shx, long recordCount) {
        this.shx = shx;
        this.recordCount = recordCount;
    }

    public static ShapefileIndex open(
            RandomAccessObjectReader shx,
            RandomAccessObjectReader shp,
            ShapefileReadLimits limits) {
        long entriesBytes = shx.size() - ShapefileHeaderReader.HEADER_BYTES;
        if (entriesBytes < 0 || entriesBytes % 8 != 0) {
            throw new ShapefileException(
                    ShapefileErrorCode.MALFORMED_HEADER, "SHX contains an incomplete index entry");
        }
        long count = entriesBytes / 8;
        if (count > limits.maxIndexRecords()) {
            throw new ShapefileException(
                    ShapefileErrorCode.LIMIT_EXCEEDED, "SHX record count exceeds the configured limit");
        }
        ShapefileIndex index = new ShapefileIndex(shx, count);
        long previousEnd = ShapefileHeaderReader.HEADER_BYTES;
        for (long slot = 0; slot < count; slot++) {
            Entry entry = index.entry(slot);
            if (entry.contentLength() > limits.maxRecordBytes()) {
                throw new ShapefileException(
                        ShapefileErrorCode.LIMIT_EXCEEDED, "SHP record exceeds the configured byte limit");
            }
            if (entry.recordOffset() < ShapefileHeaderReader.HEADER_BYTES
                    || entry.recordOffset() < previousEnd) {
                throw new ShapefileException(
                        ShapefileErrorCode.INVALID_OFFSET, "SHX record offsets overlap or move backwards");
            }
            long recordEnd = checkedAdd(entry.recordOffset(), checkedAdd(8, entry.contentLength()));
            if (recordEnd > shp.size()) {
                throw new ShapefileException(
                        ShapefileErrorCode.INVALID_OFFSET, "SHX record points beyond the SHP component");
            }
            ByteBuffer recordHeader = shp.read(entry.recordOffset(), 8, ByteOrder.BIG_ENDIAN);
            recordHeader.getInt();
            long declaredContentLength = wordsToBytes(Integer.toUnsignedLong(recordHeader.getInt()));
            if (declaredContentLength != entry.contentLength()) {
                throw new ShapefileException(
                        ShapefileErrorCode.RECORD_MISMATCH,
                        "SHP record content length does not match its SHX index entry");
            }
            previousEnd = recordEnd;
        }
        return index;
    }

    public long recordCount() {
        return recordCount;
    }

    public Entry entry(long slot) {
        if (slot < 0 || slot >= recordCount) {
            throw new IndexOutOfBoundsException("SHX slot is outside the index");
        }
        long position = checkedAdd(ShapefileHeaderReader.HEADER_BYTES, Math.multiplyExact(slot, 8L));
        ByteBuffer buffer = shx.read(position, 8, ByteOrder.BIG_ENDIAN);
        long offset = wordsToBytes(Integer.toUnsignedLong(buffer.getInt()));
        long contentLength = wordsToBytes(Integer.toUnsignedLong(buffer.getInt()));
        return new Entry(offset, contentLength);
    }

    private static long wordsToBytes(long words) {
        try {
            return Math.multiplyExact(words, 2L);
        } catch (ArithmeticException exception) {
            throw new ShapefileException(
                    ShapefileErrorCode.INVALID_OFFSET, "SHX word offset overflows", exception);
        }
    }

    private static long checkedAdd(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException exception) {
            throw new ShapefileException(
                    ShapefileErrorCode.INVALID_OFFSET, "SHX record range overflows", exception);
        }
    }

    public record Entry(long recordOffset, long contentLength) {
    }
}
