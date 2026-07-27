package cn.superhuang.data.scalpel.shapefile.internal;

import cn.superhuang.data.scalpel.shapefile.ShapefileErrorCode;
import cn.superhuang.data.scalpel.shapefile.ShapefileException;
import cn.superhuang.data.scalpel.shapefile.model.ShapefileField;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/** Reads one DBF record at a position derived from its physical slot. */
public final class DbfRecordReader {
    private final RandomAccessObjectReader dbf;
    private final DbfTableDefinition definition;

    public DbfRecordReader(RandomAccessObjectReader dbf, DbfTableDefinition definition) {
        this.dbf = dbf;
        this.definition = definition;
    }

    Record read(long slot) {
        if (slot < 0 || slot >= definition.recordCount()) {
            throw new IndexOutOfBoundsException("DBF slot is outside the table");
        }
        long position;
        try {
            position = Math.addExact(
                    definition.headerLength(), Math.multiplyExact(slot, (long) definition.recordLength()));
        } catch (ArithmeticException exception) {
            throw new ShapefileException(
                    ShapefileErrorCode.INVALID_OFFSET, "DBF record offset overflows", exception);
        }
        ByteBuffer buffer = dbf.read(position, definition.recordLength(), ByteOrder.BIG_ENDIAN);
        int marker = Byte.toUnsignedInt(buffer.get(0));
        if (marker == 0x2A) {
            return new Record(true, Map.of());
        }
        if (marker != 0x20) {
            throw new ShapefileException(
                    ShapefileErrorCode.RECORD_MISMATCH,
                    "DBF record " + (slot + 1) + " has an invalid activity marker");
        }
        Map<String, Object> attributes = new LinkedHashMap<>();
        for (DbfFieldDefinition field : definition.fields()) {
            attributes.put(field.field().name(), readValue(buffer.array(), field, slot + 1));
        }
        return new Record(false, attributes);
    }

    private Object readValue(byte[] record, DbfFieldDefinition definition, long recordNumber) {
        ShapefileField field = definition.field();
        int start = definition.offset();
        int end = start + field.length();
        try {
            return switch (definition.physicalType()) {
                case 'C' -> characterValue(record, start, end);
                case 'N' -> numericValue(record, start, end, field.decimalCount() == 0);
                case 'F' -> numericValue(record, start, end, false);
                case 'L' -> logicalValue(record, start, end);
                case 'D' -> dateValue(record, start, end);
                default -> throw new IllegalStateException("unsupported field reached the record reader");
            };
        } catch (ShapefileException exception) {
            throw new ShapefileException(
                    exception.errorCode(),
                    "DBF field " + field.name() + " is invalid in record " + recordNumber,
                    exception);
        } catch (RuntimeException exception) {
            throw new ShapefileException(
                    ShapefileErrorCode.RECORD_MISMATCH,
                    "DBF field " + field.name() + " is invalid in record " + recordNumber,
                    exception);
        }
    }

    private String characterValue(byte[] record, int start, int end) {
        while (end > start && record[end - 1] == 0x20) {
            end--;
        }
        if (end == start) {
            return null;
        }
        return DbfSchemaReader.decode(
                record, start, end - start, definition.charset(), "DBF character value");
    }

    private static Object numericValue(byte[] record, int start, int end, boolean integer) {
        String value = asciiTrimmed(record, start, end);
        if (value.isEmpty()) {
            return null;
        }
        return integer ? new BigInteger(value) : new BigDecimal(value);
    }

    private static Boolean logicalValue(byte[] record, int start, int end) {
        String value = asciiTrimmed(record, start, end).toUpperCase(java.util.Locale.ROOT);
        return switch (value) {
            case "", "?" -> null;
            case "T", "Y" -> true;
            case "F", "N" -> false;
            default -> throw new IllegalArgumentException("invalid DBF logical value");
        };
    }

    private static LocalDate dateValue(byte[] record, int start, int end) {
        String value = asciiTrimmed(record, start, end);
        if (value.isEmpty()) {
            return null;
        }
        if (value.length() != 8 || !value.chars().allMatch(Character::isDigit)) {
            throw new IllegalArgumentException("invalid DBF date value");
        }
        try {
            return LocalDate.of(
                    Integer.parseInt(value.substring(0, 4)),
                    Integer.parseInt(value.substring(4, 6)),
                    Integer.parseInt(value.substring(6, 8)));
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException("invalid DBF date value", exception);
        }
    }

    private static String asciiTrimmed(byte[] record, int start, int end) {
        while (start < end && record[start] == 0x20) {
            start++;
        }
        while (end > start && record[end - 1] == 0x20) {
            end--;
        }
        return new String(record, start, end - start, StandardCharsets.US_ASCII);
    }

    record Record(boolean deleted, Map<String, Object> attributes) {
    }
}
