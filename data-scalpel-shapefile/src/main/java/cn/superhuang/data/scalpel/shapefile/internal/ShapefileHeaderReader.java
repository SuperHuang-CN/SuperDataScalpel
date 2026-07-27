package cn.superhuang.data.scalpel.shapefile.internal;

import cn.superhuang.data.scalpel.shapefile.ShapefileErrorCode;
import cn.superhuang.data.scalpel.shapefile.ShapefileException;
import cn.superhuang.data.scalpel.shapefile.model.ShapefileEnvelope;
import cn.superhuang.data.scalpel.shapefile.model.ShapefileShapeType;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class ShapefileHeaderReader {
    public static final int HEADER_BYTES = 100;

    private ShapefileHeaderReader() {
    }

    public static ShapefileHeader read(RandomAccessObjectReader reader, String role) {
        if (reader.size() < HEADER_BYTES) {
            throw new ShapefileException(
                    ShapefileErrorCode.TRUNCATED_INPUT, role + " is shorter than its fixed header");
        }
        ByteBuffer buffer = reader.read(0, HEADER_BYTES, ByteOrder.BIG_ENDIAN);
        if (buffer.getInt(0) != 9994) {
            throw new ShapefileException(ShapefileErrorCode.MALFORMED_HEADER, role + " file code is invalid");
        }
        for (int offset = 4; offset <= 20; offset += 4) {
            if (buffer.getInt(offset) != 0) {
                throw new ShapefileException(
                        ShapefileErrorCode.MALFORMED_HEADER, role + " reserved header values must be zero");
            }
        }
        long fileWords = Integer.toUnsignedLong(buffer.getInt(24));
        long declaredLength;
        try {
            declaredLength = Math.multiplyExact(fileWords, 2L);
        } catch (ArithmeticException exception) {
            throw new ShapefileException(
                    ShapefileErrorCode.MALFORMED_HEADER, role + " file length overflows", exception);
        }
        if (declaredLength != reader.size()) {
            throw new ShapefileException(
                    ShapefileErrorCode.MALFORMED_HEADER, role + " declared file length does not match the component");
        }
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        if (buffer.getInt(28) != 1000) {
            throw new ShapefileException(ShapefileErrorCode.MALFORMED_HEADER, role + " version is not 1000");
        }
        ShapefileShapeType type = ShapefileShapeType.fromCode(buffer.getInt(32));
        ShapefileEnvelope envelope = new ShapefileEnvelope(
                buffer.getDouble(36), buffer.getDouble(44),
                buffer.getDouble(52), buffer.getDouble(60),
                buffer.getDouble(68), buffer.getDouble(76),
                buffer.getDouble(84), buffer.getDouble(92));
        return new ShapefileHeader(declaredLength, type, envelope);
    }
}
