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
    private final int[] physicalPages;

    private GdbTableIndexReader(
            LittleEndianRandomAccessReader channel,
            String role,
            int pageCount,
            int declaredRowCount,
            int offsetWidth,
            int slotCount,
            int[] physicalPages) {
        this.channel = channel;
        this.role = role;
        this.pageCount = pageCount;
        this.declaredRowCount = declaredRowCount;
        this.offsetWidth = offsetWidth;
        this.slotCount = slotCount;
        this.physicalPages = physicalPages;
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
            int slotCount;
            final long indexBytes;
            try {
                slotCount = Math.multiplyExact(pageCount, SLOTS_PER_PAGE);
                indexBytes = Math.addExact(HEADER_BYTES, Math.multiplyExact((long) slotCount, offsetWidth));
            } catch (ArithmeticException exception) {
                throw new FileGdbException(FileGdbErrorCode.MALFORMED_HEADER, role + " count overflow", exception);
            }
            if (channel.size() < indexBytes) {
                throw new FileGdbException(FileGdbErrorCode.TRUNCATED_INPUT, role + " has fewer slots than declared");
            }
            int[] physicalPages = null;
            if (channel.size() > indexBytes) {
                if (channel.size() - indexBytes < 16) {
                    throw new FileGdbException(FileGdbErrorCode.TRUNCATED_INPUT, role + " has an incomplete block-map header");
                }
                ByteBuffer trailer = channel.read(indexBytes, 16);
                long bitmapWords = Integer.toUnsignedLong(trailer.getInt());
                int logicalPages = trailer.getInt();
                int presentPages = trailer.getInt();
                trailer.getInt(); // Leading nonzero bitmap words; mapping is determined by the bitmap itself.
                if (logicalPages < 0 || presentPages != pageCount
                        || (bitmapWords == 0 && logicalPages != pageCount)) {
                    throw new FileGdbException(FileGdbErrorCode.MALFORMED_HEADER, role + " has an inconsistent block map");
                }
                if (bitmapWords != 0) {
                    long logicalSlots = (long) logicalPages * SLOTS_PER_PAGE;
                    if (logicalSlots > limits.maxIndexSlotsPerCursor() || logicalSlots > Integer.MAX_VALUE) {
                        throw new FileGdbException(FileGdbErrorCode.LIMIT_EXCEEDED, role + " sparse index exceeds the configured slot limit");
                    }
                    int bitmapBytes = (logicalPages + 7) / 8;
                    if (bitmapWords * 4 < bitmapBytes) {
                        throw new FileGdbException(FileGdbErrorCode.MALFORMED_HEADER, role + " block map is too small");
                    }
                    if (channel.size() - indexBytes - 16 < bitmapWords * 4) {
                        throw new FileGdbException(FileGdbErrorCode.TRUNCATED_INPUT, role + " has a truncated block map");
                    }
                    ByteBuffer bitmap = channel.read(indexBytes + 16, bitmapBytes);
                    physicalPages = new int[logicalPages];
                    int physicalPage = 0;
                    for (int page = 0; page < logicalPages; page++) {
                        physicalPages[page] = (bitmap.get(page / 8) & (1 << (page % 8))) == 0
                                ? -1 : physicalPage++;
                    }
                    if (physicalPage != pageCount) {
                        throw new FileGdbException(FileGdbErrorCode.MALFORMED_HEADER, role + " block map disagrees with the stored page count");
                    }
                    slotCount = (int) logicalSlots;
                }
            }
            if (declaredRows > slotCount) {
                throw new FileGdbException(FileGdbErrorCode.MALFORMED_HEADER, role + " row count exceeds its slot count");
            }
            return new GdbTableIndexReader(channel, role, pageCount, declaredRows, offsetWidth, slotCount, physicalPages);
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

    /** Counts occupied index slots without reading or decoding feature records. */
    long countPresentRecords() {
        long count = 0;
        int pageBytes = Math.multiplyExact(SLOTS_PER_PAGE, offsetWidth);
        for (int page = 0; page < pageCount; page++) {
            long position = HEADER_BYTES + (long) page * pageBytes;
            ByteBuffer offsets = channel.read(position, pageBytes);
            for (int slot = 0; slot < SLOTS_PER_PAGE; slot++) {
                boolean present = false;
                for (int offsetByte = 0; offsetByte < offsetWidth; offsetByte++) {
                    present |= offsets.get() != 0;
                }
                if (present) {
                    count++;
                }
            }
        }
        return count;
    }

    long recordOffset(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= slotCount) {
            throw new FileGdbException(FileGdbErrorCode.INVALID_OFFSET, role + " slot index is out of range");
        }
        final long position;
        try {
            long physicalSlot = slotIndex;
            if (physicalPages != null) {
                int page = physicalPages[slotIndex / SLOTS_PER_PAGE];
                if (page < 0) {
                    return 0;
                }
                physicalSlot = (long) page * SLOTS_PER_PAGE + slotIndex % SLOTS_PER_PAGE;
            }
            position = Math.addExact(HEADER_BYTES, Math.multiplyExact(physicalSlot, offsetWidth));
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
