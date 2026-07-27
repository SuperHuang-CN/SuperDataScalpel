package cn.superhuang.data.scalpel.filegdb.internal;

import cn.superhuang.data.scalpel.filegdb.FileGdbErrorCode;
import cn.superhuang.data.scalpel.filegdb.FileGdbException;
import cn.superhuang.data.scalpel.filegdb.FileGdbReadLimits;
import cn.superhuang.data.scalpel.filegdb.model.FileGdbFeature;
import cn.superhuang.data.scalpel.filegdb.model.FileGdbField;
import cn.superhuang.data.scalpel.filegdb.model.geometry.FileGdbGeometry;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Decodes one table payload according to its immutable field definition. */
final class GdbRecordReader {
    private static final BigDecimal FILEGDB_TO_UNIX_DAYS = BigDecimal.valueOf(25_569);
    private static final BigDecimal MILLIS_PER_DAY = BigDecimal.valueOf(86_400_000L);

    private GdbRecordReader() {
    }

    static FileGdbFeature read(
            int oid,
            ByteBuffer payload,
            GdbTableDefinition definition,
            FileGdbReadLimits limits) {
        String context = definition.physicalName() + " OID " + oid + " record";
        BoundedBufferReader reader = new BoundedBufferReader(
                payload,
                context,
                FileGdbErrorCode.MALFORMED_HEADER);
        int nullableBytes = (definition.nullableFieldCount() + 7) / 8;
        byte[] nullBitmap = reader.readBytes(nullableBytes);
        int nullableBit = 0;
        Map<String, Object> attributes = new LinkedHashMap<>();
        FileGdbGeometry geometry = null;
        for (GdbFieldDefinition definitionField : definition.fields()) {
            FileGdbField field = definitionField.publicField();
            boolean isNull = false;
            if (field.nullable()) {
                int byteIndex = nullableBit >>> 3;
                int mask = 1 << (nullableBit & 7);
                nullableBit++;
                isNull = (nullBitmap[byteIndex] & mask) != 0;
            }
            if (definitionField.shape()) {
                if (!isNull) {
                    int length = reader.readVarUIntAsInt("geometry byte length", limits.maxRecordBytes());
                    geometry = GdbGeometryReader.read(
                            ByteBuffer.wrap(reader.readBytes(length)),
                            definition,
                            limits,
                            oid);
                }
                continue;
            }
            Object value = isNull ? null : readValue(reader, field, oid, limits);
            attributes.put(field.name(), value);
        }
        reader.requireFullyConsumed();
        return new FileGdbFeature(oid, attributes, geometry);
    }

    private static Object readValue(
            BoundedBufferReader reader,
            FileGdbField field,
            int oid,
            FileGdbReadLimits limits) {
        return switch (field.type()) {
            case INT16 -> reader.readShort();
            case INT32 -> reader.readInt();
            case FLOAT32 -> reader.readFloat();
            case FLOAT64 -> reader.readDouble();
            case STRING, XML -> {
                int length = reader.readVarUIntAsInt("string byte length", limits.maxStringBytes());
                yield reader.readUtf8(length, limits.maxStringBytes());
            }
            case TIMESTAMP -> timestamp(reader.readDouble(), reader);
            case OID -> oid;
            case BINARY -> {
                int length = reader.readVarUIntAsInt("binary byte length", limits.maxBinaryBytes());
                yield reader.readBytes(length);
            }
            case UUID, GUID -> uuid(reader.readBytes(16));
            case SHAPE -> throw reader.malformed("attempted to decode shape as an attribute");
        };
    }

    private static Instant timestamp(double fileGdbDays, BoundedBufferReader reader) {
        if (!Double.isFinite(fileGdbDays)) {
            throw reader.malformed("contains a non-finite timestamp");
        }
        try {
            long epochMillis = BigDecimal.valueOf(fileGdbDays)
                    .subtract(FILEGDB_TO_UNIX_DAYS)
                    .multiply(MILLIS_PER_DAY)
                    .setScale(0, RoundingMode.HALF_UP)
                    .longValueExact();
            return Instant.ofEpochMilli(epochMillis);
        } catch (ArithmeticException | DateTimeException exception) {
            throw new FileGdbException(
                    FileGdbErrorCode.MALFORMED_HEADER,
                    "FileGDB timestamp is outside the supported Instant range",
                    exception);
        }
    }

    private static UUID uuid(byte[] bytes) {
        byte[] standard = {
                bytes[3], bytes[2], bytes[1], bytes[0],
                bytes[5], bytes[4],
                bytes[7], bytes[6],
                bytes[8], bytes[9],
                bytes[10], bytes[11], bytes[12], bytes[13], bytes[14], bytes[15]
        };
        ByteBuffer buffer = ByteBuffer.wrap(standard);
        return new UUID(buffer.getLong(), buffer.getLong());
    }
}
