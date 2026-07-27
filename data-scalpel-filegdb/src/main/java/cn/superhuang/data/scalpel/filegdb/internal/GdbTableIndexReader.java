package cn.superhuang.data.scalpel.filegdb.internal;

import cn.superhuang.data.scalpel.filegdb.FileGdbErrorCode;
import cn.superhuang.data.scalpel.filegdb.FileGdbException;
import cn.superhuang.data.scalpel.filegdb.FileGdbReadLimits;
import cn.superhuang.data.scalpel.filegdb.FileGdbSource;
import java.nio.ByteBuffer;

/** Random access to .gdbtablx record slots. */
final class GdbTableIndexReader implements AutoCloseable {
    private static final int HEADER_BYTES = 16;
    private static final int SLOTS_PER_PAGE = 1_024;

    private final LittleEndianRandomAccessReader channel;
    private final String role;
    private final int pageCount;
    private final int declaredRowCount;
    private final int offsetWidth;
    private final int slotCount;

    private GdbTableIndexReader(
            LittleEndianRandomAccessReader channel,
            String role,
            int pageCount,
            int declaredRowCount,
            int offsetWidth,
            int slotCount) {
        this.channel = channel;
        this.role = role;
        this.pageCount = pageCount;
        this.declaredRowCount = declaredRowCount;
        this.offsetWidth = offsetWidth;
        this.slotCount = slotCount;
    }

    static GdbTableIndexReader open(
            FileGdbSource source,
            String fileName,
            String physicalName,
            FileGdbReadLimits limits) {
        String role = physicalName + ".gdbtablx";
        LittleEndianRandomAccessReader channel = LittleEndianRandomAccessReader.open(source, fileName, role, limits);
        try {
            if (channel.size() < HEADER_BYTES) {
                throw new FileGdbException(FileGdbErrorCode.TRUNCATED_INPUT, role + " has no complete index header");
            }
            ByteBuffer header = channel.read(0, HEADER_BYTES);
            int version = header.getInt();
            int pageCount = header.getInt();
            int declaredRows = header.getInt();
            int offsetWidth = header.getInt();
            if (version != 3) {
                throw new FileGdbException(FileGdbErrorCode.UNSUPPORTED_FORMAT, role + " has unsupported version " + version);
            }
            if (pageCount < 0 || declaredRows < 0) {
                throw new FileGdbException(FileGdbErrorCode.MALFORMED_HEADER, role + " has negative counts");
            }
            if (offsetWidth < 4 || offsetWidth > 6) {
                throw new FileGdbException(
                        FileGdbErrorCode.UNSUPPORTED_FORMAT,
                        role + " uses unsupported record-offset width " + offsetWidth);
            }
            final int slotCount;
            final long indexBytes;
            try {
                slotCount = Math.multiplyExact(pageCount, SLOTS_PER_PAGE);
                indexBytes = Math.addExact(HEADER_BYTES, Math.multiplyExact((long) slotCount, offsetWidth));
            } catch (ArithmeticException exception) {
                throw new FileGdbException(FileGdbErrorCode.MALFORMED_HEADER, role + " count overflow", exception);
            }
            if (declaredRows > slotCount) {
                throw new FileGdbException(FileGdbErrorCode.MALFORMED_HEADER, role + " row count exceeds its slot count");
            }
            if (channel.size() < indexBytes) {
                throw new FileGdbException(FileGdbErrorCode.TRUNCATED_INPUT, role + " has fewer slots than declared");
            }
            return new GdbTableIndexReader(channel, role, pageCount, declaredRows, offsetWidth, slotCount);
        } catch (RuntimeException exception) {
            channel.close();
            throw exception;
        }
    }

    int pageCount() {
        return pageCount;
    }

    int declaredRowCount() {
        return declaredRowCount;
    }

    int offsetWidth() {
        return offsetWidth;
    }

    int slotCount() {
        return slotCount;
    }

    long recordOffset(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= slotCount) {
            throw new FileGdbException(FileGdbErrorCode.INVALID_OFFSET, role + " slot index is out of range");
        }
        final long position;
        try {
            position = Math.addExact(HEADER_BYTES, Math.multiplyExact((long) slotIndex, offsetWidth));
        } catch (ArithmeticException exception) {
            throw new FileGdbException(FileGdbErrorCode.INVALID_OFFSET, role + " slot offset overflow", exception);
        }
        ByteBuffer bytes = channel.read(position, offsetWidth);
        long offset = 0;
        for (int index = 0; index < offsetWidth; index++) {
            offset |= (long) (bytes.get() & 0xff) << (index * 8);
        }
        return offset;
    }

    @Override
    public void close() {
        channel.close();
    }
}
