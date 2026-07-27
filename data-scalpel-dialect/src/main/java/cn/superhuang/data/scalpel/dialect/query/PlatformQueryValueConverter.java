package cn.superhuang.data.scalpel.dialect.query;

import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.contract.type.PlatformTypeDefinition;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

/** Strict JSON-to-platform scalar conversion shared by Admin previews and Engine requests. */
public final class PlatformQueryValueConverter {

    private PlatformQueryValueConverter() {
    }

    public static Object convert(Object raw, PlatformTypeDefinition definition) {
        if (definition == null) {
            throw new IllegalArgumentException("Platform type definition is required");
        }
        if (raw == null) return null;
        if (raw instanceof java.util.Map<?, ?> || raw instanceof java.util.Collection<?> || raw.getClass().isArray()) {
            throw new IllegalArgumentException("Expected a scalar value");
        }
        Object converted = switch (definition.type()) {
            case BOOLEAN -> booleanValue(raw);
            case BYTE -> exactInteger(raw).byteValueExact();
            case SHORT -> exactInteger(raw).shortValueExact();
            case INTEGER -> exactInteger(raw).intValueExact();
            case LONG -> exactInteger(raw).longValueExact();
            case FLOAT -> finiteFloat(raw);
            case DOUBLE -> finiteDouble(raw);
            case DECIMAL -> decimal(raw, definition);
            case STRING -> string(raw, definition.length());
            case DATE -> raw instanceof LocalDate value ? value : LocalDate.parse(String.valueOf(raw));
            case TIMESTAMP -> timestamp(raw);
            case TIMESTAMP_NTZ -> raw instanceof LocalDateTime value ? value : LocalDateTime.parse(String.valueOf(raw));
            case BINARY -> throw new IllegalArgumentException("BINARY SQL parameters are not supported");
            case GEOMETRY -> throw new IllegalArgumentException("GEOMETRY SQL parameters are not supported");
        };
        return converted;
    }

    private static boolean booleanValue(Object raw) {
        if (raw instanceof Boolean value) return value;
        if ("true".equalsIgnoreCase(String.valueOf(raw))) return true;
        if ("false".equalsIgnoreCase(String.valueOf(raw))) return false;
        throw new IllegalArgumentException("Expected a boolean value");
    }

    private static BigDecimal exactInteger(Object raw) {
        try {
            BigDecimal value = raw instanceof BigDecimal decimal ? decimal : new BigDecimal(String.valueOf(raw));
            return value.setScale(0);
        } catch (ArithmeticException | NumberFormatException exception) {
            throw new IllegalArgumentException("Expected an integer value", exception);
        }
    }

    private static float finiteFloat(Object raw) {
        try {
            float value = Float.parseFloat(String.valueOf(raw));
            if (!Float.isFinite(value)) throw new IllegalArgumentException("Expected a finite FLOAT value");
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Expected a FLOAT value", exception);
        }
    }

    private static double finiteDouble(Object raw) {
        try {
            double value = Double.parseDouble(String.valueOf(raw));
            if (!Double.isFinite(value)) throw new IllegalArgumentException("Expected a finite DOUBLE value");
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Expected a DOUBLE value", exception);
        }
    }

    private static BigDecimal decimal(Object raw, PlatformTypeDefinition definition) {
        try {
            BigDecimal value = raw instanceof BigDecimal decimal ? decimal : new BigDecimal(String.valueOf(raw));
            int fractionalDigits = Math.max(0, value.scale());
            int integerDigits = Math.max(0, value.precision() - value.scale());
            int maximumIntegerDigits = definition.precision() - definition.scale();
            if (fractionalDigits > definition.scale() || integerDigits > maximumIntegerDigits) {
                throw new IllegalArgumentException("DECIMAL value exceeds precision or scale");
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Expected a DECIMAL value", exception);
        }
    }

    private static String string(Object raw, Integer maximumLength) {
        String value = String.valueOf(raw);
        if (maximumLength != null && value.length() > maximumLength) {
            throw new IllegalArgumentException("STRING value exceeds maximum length " + maximumLength);
        }
        return value;
    }

    private static Object timestamp(Object raw) {
        if (raw instanceof OffsetDateTime || raw instanceof Instant) return raw;
        String value = String.valueOf(raw);
        try {
            return OffsetDateTime.parse(value);
        } catch (java.time.format.DateTimeParseException ignored) {
            try {
                return Instant.parse(value);
            } catch (java.time.format.DateTimeParseException exception) {
                throw new IllegalArgumentException("Expected an ISO timestamp with an offset", exception);
            }
        }
    }
}
