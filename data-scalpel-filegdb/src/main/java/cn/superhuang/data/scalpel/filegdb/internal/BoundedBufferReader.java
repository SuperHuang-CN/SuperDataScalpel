package cn.superhuang.data.scalpel.filegdb.internal;

import cn.superhuang.data.scalpel.filegdb.FileGdbErrorCode;
import cn.superhuang.data.scalpel.filegdb.FileGdbException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * Bounds-checked reader for one already bounded header, record or geometry blob.
 * Variable-integer behavior was independently re-expressed from the Apache-2.0 FileGDB-master
 * reference listed in this module's third-party notice.
 */
final class BoundedBufferReader {
    private final ByteBuffer buffer;
    private final String context;
    private final FileGdbErrorCode malformedCode;

    BoundedBufferReader(ByteBuffer source, String context, FileGdbErrorCode malformedCode) {
        this.buffer = source.slice().order(ByteOrder.LITTLE_ENDIAN);
        this.context = context;
        this.malformedCode = malformedCode;
    }

    int remaining() {
        return buffer.remaining();
    }

    int position() {
        return buffer.position();
    }

    int readUnsignedByte() {
        return readByte() & 0xff;
    }

    byte readByte() {
        requireRemaining(1);
        return buffer.get();
    }

    int peekUnsignedByte() {
        requireRemaining(1);
        return buffer.get(buffer.position()) & 0xff;
    }

    short readShort() {
        requireRemaining(Short.BYTES);
        return buffer.getShort();
    }

    int readUnsignedShort() {
        return readShort() & 0xffff;
    }

    int readInt() {
        requireRemaining(Integer.BYTES);
        return buffer.getInt();
    }

    long readLong() {
        requireRemaining(Long.BYTES);
        return buffer.getLong();
    }

    float readFloat() {
        requireRemaining(Float.BYTES);
        return buffer.getFloat();
    }

    double readDouble() {
        requireRemaining(Double.BYTES);
        return buffer.getDouble();
    }

    byte[] readBytes(int length) {
        requireLength(length);
        requireRemaining(length);
        byte[] result = new byte[length];
        buffer.get(result);
        return result;
    }

    void skip(int length) {
        requireLength(length);
        requireRemaining(length);
        buffer.position(buffer.position() + length);
    }

    String readUtf16Characters(int characterCount, int maximumBytes) {
        if (characterCount < 0) {
            throw malformed("negative UTF-16 character count");
        }
        final int byteCount;
        try {
            byteCount = Math.multiplyExact(characterCount, Character.BYTES);
        } catch (ArithmeticException exception) {
            throw new FileGdbException(FileGdbErrorCode.LIMIT_EXCEEDED, context + " UTF-16 length overflow", exception);
        }
        if (byteCount > maximumBytes) {
            throw new FileGdbException(FileGdbErrorCode.LIMIT_EXCEEDED, context + " string exceeds the configured limit");
        }
        return decode(readBytes(byteCount), StandardCharsets.UTF_16LE, "UTF-16LE");
    }

    String readUtf8(int byteCount, int maximumBytes) {
        if (byteCount > maximumBytes) {
            throw new FileGdbException(FileGdbErrorCode.LIMIT_EXCEEDED, context + " string exceeds the configured limit");
        }
        return decode(readBytes(byteCount), StandardCharsets.UTF_8, "UTF-8");
    }

    long readVarUInt() {
        long value = 0;
        int shift = 0;
        for (int index = 0; index < 10; index++) {
            int current = readUnsignedByte();
            long group = current & 0x7fL;
            if (shift == 63 && group > 0) {
                throw malformed("unsigned variable integer exceeds signed long range");
            }
            if (shift > 63 && group != 0) {
                throw malformed("unsigned variable integer overflow");
            }
            value |= group << shift;
            if ((current & 0x80) == 0) {
                return value;
            }
            shift += 7;
        }
        throw malformed("unterminated unsigned variable integer");
    }

    long readVarInt() {
        int first = readUnsignedByte();
        boolean negative = (first & 0x40) != 0;
        long magnitude = first & 0x3fL;
        int shift = 6;
        int current = first;
        for (int index = 1; (current & 0x80) != 0; index++) {
            if (index >= 10) {
                throw malformed("unterminated signed variable integer");
            }
            current = readUnsignedByte();
            long group = current & 0x7fL;
            if ((shift > 62 && group != 0) || (shift == 62 && group > 1)) {
                throw malformed("signed variable integer overflow");
            }
            magnitude |= group << shift;
            shift += 7;
        }
        return negative ? -magnitude : magnitude;
    }

    int readVarUIntAsInt(String valueRole, int maximum) {
        long value = readVarUInt();
        if (value > maximum) {
            throw new FileGdbException(
                    FileGdbErrorCode.LIMIT_EXCEEDED,
                    context + " " + valueRole + " exceeds the configured limit");
        }
        return (int) value;
    }

    void requireFullyConsumed() {
        if (buffer.hasRemaining()) {
            throw malformed("contains " + buffer.remaining() + " unconsumed bytes");
        }
    }

    FileGdbException malformed(String detail) {
        return new FileGdbException(malformedCode, context + " " + detail);
    }

    private String decode(byte[] bytes, Charset charset, String name) {
        try {
            return charset.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw new FileGdbException(malformedCode, context + " contains invalid " + name + " text", exception);
        }
    }

    private void requireRemaining(int length) {
        try {
            if (buffer.remaining() < length) {
                throw new BufferUnderflowException();
            }
        } catch (BufferUnderflowException exception) {
            throw new FileGdbException(
                    FileGdbErrorCode.TRUNCATED_INPUT,
                    context + " ended before its declared structure",
                    exception);
        }
    }

    private void requireLength(int length) {
        if (length < 0) {
            throw malformed("contains a negative byte length");
        }
    }
}
