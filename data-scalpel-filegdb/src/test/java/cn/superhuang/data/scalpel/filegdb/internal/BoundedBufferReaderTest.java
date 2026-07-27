package cn.superhuang.data.scalpel.filegdb.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import cn.superhuang.data.scalpel.filegdb.FileGdbErrorCode;
import cn.superhuang.data.scalpel.filegdb.FileGdbException;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

class BoundedBufferReaderTest {
    @Test
    void readsUnsignedAndSignedVariableIntegers() {
        BoundedBufferReader unsigned = reader(0x00, 0x7f, 0x80, 0x01, 0xac, 0x02);
        assertEquals(0, unsigned.readVarUInt());
        assertEquals(127, unsigned.readVarUInt());
        assertEquals(128, unsigned.readVarUInt());
        assertEquals(300, unsigned.readVarUInt());
        unsigned.requireFullyConsumed();

        BoundedBufferReader signed = reader(0x00, 0x01, 0x41, 0xff, 0x7f, 0x80, 0x01, 0xc0, 0x01);
        assertEquals(0, signed.readVarInt());
        assertEquals(1, signed.readVarInt());
        assertEquals(-1, signed.readVarInt());
        assertEquals(-8_191, signed.readVarInt());
        assertEquals(64, signed.readVarInt());
        assertEquals(-64, signed.readVarInt());
        signed.requireFullyConsumed();
    }

    @Test
    void rejectsTruncatedOverlongAndOverflowingVariableIntegers() {
        assertEquals(
                FileGdbErrorCode.TRUNCATED_INPUT,
                assertThrows(FileGdbException.class, () -> reader(0x80).readVarUInt()).code());

        FileGdbException overlong = assertThrows(
                FileGdbException.class,
                () -> reader(0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x00)
                        .readVarUInt());
        assertEquals(FileGdbErrorCode.MALFORMED_HEADER, overlong.code());

        FileGdbException signedOverflow = assertThrows(
                FileGdbException.class,
                () -> reader(0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x02)
                        .readVarInt());
        assertEquals(FileGdbErrorCode.MALFORMED_HEADER, signedOverflow.code());
    }

    @Test
    void rejectsMalformedUtf8InsteadOfReplacingBytes() {
        FileGdbException exception = assertThrows(
                FileGdbException.class,
                () -> reader(0xc3, 0x28).readUtf8(2, 10));
        assertEquals(FileGdbErrorCode.MALFORMED_HEADER, exception.code());
    }

    private static BoundedBufferReader reader(int... values) {
        byte[] bytes = new byte[values.length];
        for (int index = 0; index < values.length; index++) {
            bytes[index] = (byte) values[index];
        }
        return new BoundedBufferReader(
                ByteBuffer.wrap(bytes),
                "test value",
                FileGdbErrorCode.MALFORMED_HEADER);
    }
}
