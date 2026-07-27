package cn.superhuang.data.scalpel.filegdb.internal;

import cn.superhuang.data.scalpel.filegdb.FileGdbErrorCode;
import cn.superhuang.data.scalpel.filegdb.FileGdbException;
import cn.superhuang.data.scalpel.filegdb.FileGdbReadLimits;
import cn.superhuang.data.scalpel.filegdb.FileGdbSource;
import java.nio.ByteBuffer;

/** Reads length-prefixed table records after an index slot has supplied their absolute offset. */
final class GdbTableReader implements AutoCloseable {
    private final LittleEndianRandomAccessReader channel;
    private final GdbTableDefinition definition;
    private final FileGdbReadLimits limits;

    private GdbTableReader(
            LittleEndianRandomAccessReader channel,
            GdbTableDefinition definition,
            FileGdbReadLimits limits) {
        this.channel = channel;
        this.definition = definition;
        this.limits = limits;
    }

    static GdbTableReader open(
            FileGdbSource source,
            GdbTableDefinition definition,
            FileGdbReadLimits limits) {
        LittleEndianRandomAccessReader channel = LittleEndianRandomAccessReader.open(
                source,
                definition.tableFileName(),
                definition.physicalName() + ".gdbtable",
                limits);
        return new GdbTableReader(channel, definition, limits);
    }

    ByteBuffer readRecord(long offset, int oid) {
        String context = definition.physicalName() + " OID " + oid;
        if (offset <= 0 || offset > channel.size() - Integer.BYTES) {
            throw new FileGdbException(FileGdbErrorCode.INVALID_OFFSET, context + " has an invalid record offset");
        }
        int length = channel.readInt(offset);
        if (length < 0) {
            throw new FileGdbException(FileGdbErrorCode.MALFORMED_HEADER, context + " has a negative record length");
        }
        if (length > limits.maxRecordBytes()) {
            throw new FileGdbException(FileGdbErrorCode.LIMIT_EXCEEDED, context + " exceeds the record-size limit");
        }
        if (definition.largestRecordBytes() > 0 && length > definition.largestRecordBytes()) {
            throw new FileGdbException(
                    FileGdbErrorCode.MALFORMED_HEADER,
                    context + " exceeds the table's declared largest record");
        }
        final long payloadOffset;
        try {
            payloadOffset = Math.addExact(offset, Integer.BYTES);
        } catch (ArithmeticException exception) {
            throw new FileGdbException(FileGdbErrorCode.INVALID_OFFSET, context + " record offset overflow", exception);
        }
        return channel.read(payloadOffset, length);
    }

    @Override
    public void close() {
        channel.close();
    }
}
