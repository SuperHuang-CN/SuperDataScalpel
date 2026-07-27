package cn.superhuang.data.scalpel.shapefile.internal;

import cn.superhuang.data.scalpel.shapefile.ShapefileErrorCode;
import cn.superhuang.data.scalpel.shapefile.ShapefileException;
import cn.superhuang.data.scalpel.shapefile.ShapefileOpenOptions;
import cn.superhuang.data.scalpel.shapefile.model.ShapefileField;
import cn.superhuang.data.scalpel.shapefile.model.ShapefileFieldType;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Parses a dBASE III-compatible DBF schema used by a Shapefile. */
public final class DbfSchemaReader {
    private static final int BASE_HEADER_BYTES = 32;
    private static final int FIELD_DESCRIPTOR_BYTES = 32;

    private DbfSchemaReader() {
    }

    public static DbfTableDefinition read(
            RandomAccessObjectReader dbf,
            RandomAccessObjectReader cpg,
            ShapefileOpenOptions options) {
        if (dbf.size() < BASE_HEADER_BYTES + 1L) {
            throw new ShapefileException(
                    ShapefileErrorCode.TRUNCATED_INPUT, "DBF is shorter than its fixed header");
        }
        ByteBuffer header = dbf.read(0, BASE_HEADER_BYTES, ByteOrder.LITTLE_ENDIAN);
        int version = Byte.toUnsignedInt(header.get(0));
        if (version != 0x03 && version != 0x04 && version != 0x05) {
            throw new ShapefileException(
                    ShapefileErrorCode.UNSUPPORTED_FORMAT,
                    "DBF version " + version + " is not supported; memo-capable variants are excluded");
        }
        long recordCount = Integer.toUnsignedLong(header.getInt(4));
        int headerLength = Short.toUnsignedInt(header.getShort(8));
        int recordLength = Short.toUnsignedInt(header.getShort(10));
        int languageDriverId = Byte.toUnsignedInt(header.get(29));
        if (headerLength < BASE_HEADER_BYTES + 1
                || (headerLength - BASE_HEADER_BYTES - 1) % FIELD_DESCRIPTOR_BYTES != 0
                || recordLength < 1) {
            throw new ShapefileException(
                    ShapefileErrorCode.MALFORMED_HEADER, "DBF header or record length is invalid");
        }
        int fieldCount = (headerLength - BASE_HEADER_BYTES - 1) / FIELD_DESCRIPTOR_BYTES;
        if (fieldCount > options.readLimits().maxFields()) {
            throw new ShapefileException(
                    ShapefileErrorCode.LIMIT_EXCEEDED, "DBF field count exceeds the configured limit");
        }
        if (dbf.size() < headerLength) {
            throw new ShapefileException(
                    ShapefileErrorCode.TRUNCATED_INPUT, "DBF header exceeds the component boundary");
        }
        Charset charset = DbfCharsetResolver.resolve(cpg, languageDriverId, options);
        List<DbfFieldDefinition> fields = new ArrayList<>(fieldCount);
        Set<String> names = new HashSet<>();
        int fieldOffset = 1;
        for (int index = 0; index < fieldCount; index++) {
            long descriptorOffset = BASE_HEADER_BYTES + (long) index * FIELD_DESCRIPTOR_BYTES;
            ByteBuffer descriptor = dbf.read(descriptorOffset, FIELD_DESCRIPTOR_BYTES, ByteOrder.LITTLE_ENDIAN);
            int nameLength = 0;
            while (nameLength < 11 && descriptor.get(nameLength) != 0) {
                nameLength++;
            }
            String name = decode(descriptor.array(), 0, nameLength, charset, "DBF field name").stripTrailing();
            if (name.isBlank() || !names.add(name)) {
                throw new ShapefileException(
                        ShapefileErrorCode.MALFORMED_HEADER, "DBF field names must be non-blank and unique");
            }
            char physicalType = (char) Byte.toUnsignedInt(descriptor.get(11));
            int length = Byte.toUnsignedInt(descriptor.get(16));
            int decimals = Byte.toUnsignedInt(descriptor.get(17));
            if (length <= 0 || decimals > length) {
                throw new ShapefileException(
                        ShapefileErrorCode.MALFORMED_HEADER, "DBF field " + name + " has invalid length metadata");
            }
            ShapefileFieldType type = fieldType(physicalType, decimals, name);
            ShapefileField field = new ShapefileField(name, type, length, decimals, true, index);
            fields.add(new DbfFieldDefinition(field, physicalType, fieldOffset));
            try {
                fieldOffset = Math.addExact(fieldOffset, length);
            } catch (ArithmeticException exception) {
                throw new ShapefileException(
                        ShapefileErrorCode.MALFORMED_HEADER, "DBF record layout overflows", exception);
            }
        }
        if (Byte.toUnsignedInt(dbf.read(headerLength - 1L, 1, ByteOrder.BIG_ENDIAN).get()) != 0x0D) {
            throw new ShapefileException(
                    ShapefileErrorCode.MALFORMED_HEADER, "DBF field descriptors are not terminated by 0x0D");
        }
        if (fieldOffset != recordLength) {
            throw new ShapefileException(
                    ShapefileErrorCode.MALFORMED_HEADER, "DBF fields do not fill the declared record length");
        }
        long recordsBytes;
        long minimumSize;
        try {
            recordsBytes = Math.multiplyExact(recordCount, (long) recordLength);
            minimumSize = Math.addExact(headerLength, recordsBytes);
        } catch (ArithmeticException exception) {
            throw new ShapefileException(
                    ShapefileErrorCode.MALFORMED_HEADER, "DBF record range overflows", exception);
        }
        if (minimumSize > dbf.size()) {
            throw new ShapefileException(
                    ShapefileErrorCode.TRUNCATED_INPUT, "DBF records exceed the component boundary");
        }
        return new DbfTableDefinition(recordCount, headerLength, recordLength, charset, fields);
    }

    private static ShapefileFieldType fieldType(char type, int decimals, String name) {
        return switch (type) {
            case 'C' -> ShapefileFieldType.STRING;
            case 'N' -> decimals == 0 ? ShapefileFieldType.INTEGER : ShapefileFieldType.DECIMAL;
            case 'F' -> ShapefileFieldType.DECIMAL;
            case 'L' -> ShapefileFieldType.BOOLEAN;
            case 'D' -> ShapefileFieldType.DATE;
            default -> throw new ShapefileException(
                    ShapefileErrorCode.UNSUPPORTED_FORMAT,
                    "DBF field " + name + " uses unsupported type " + type);
        };
    }

    static String decode(byte[] bytes, int offset, int length, Charset charset, String role) {
        try {
            CharBuffer value = charset.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes, offset, length));
            return value.toString();
        } catch (CharacterCodingException exception) {
            throw new ShapefileException(
                    ShapefileErrorCode.INVALID_ENCODING, role + " is not valid " + charset.name(), exception);
        }
    }
}
