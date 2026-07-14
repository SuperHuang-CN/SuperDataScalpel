package cn.superhuang.data.scalpel.search;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.UUID;

final class SearchValueConverter {

    private SearchValueConverter() {
    }

    static Object convert(String field, Class<?> fieldType, String rawValue) {
        Class<?> type = SearchFieldResolver.normalizePrimitive(fieldType);
        try {
            if (type == String.class) return rawValue;
            if (type == Character.class && rawValue.length() == 1) return rawValue.charAt(0);
            if (type == Boolean.class) return booleanValue(rawValue);
            if (type == Byte.class) return Byte.valueOf(rawValue);
            if (type == Short.class) return Short.valueOf(rawValue);
            if (type == Integer.class) return Integer.valueOf(rawValue);
            if (type == Long.class) return Long.valueOf(rawValue);
            if (type == Float.class) return finiteFloat(rawValue);
            if (type == Double.class) return finiteDouble(rawValue);
            if (type == BigDecimal.class) return new BigDecimal(rawValue);
            if (type == BigInteger.class) return new BigInteger(rawValue);
            if (type == UUID.class) return UUID.fromString(rawValue);
            if (type.isEnum()) return enumValue(type, rawValue);
            if (type == LocalDate.class) return LocalDate.parse(rawValue);
            if (type == LocalDateTime.class) return LocalDateTime.parse(rawValue);
            if (type == Instant.class) return Instant.parse(rawValue);
            if (type == OffsetDateTime.class) return OffsetDateTime.parse(rawValue);
            if (type == ZonedDateTime.class) return ZonedDateTime.parse(rawValue);
            if (Date.class.isAssignableFrom(type)) return Date.from(Instant.parse(rawValue));
        } catch (RuntimeException exception) {
            throw new InvalidSearchRequestException("search value is invalid for field: " + field, exception);
        }
        throw new InvalidSearchRequestException("search field is not supported: " + field);
    }

    private static Boolean booleanValue(String rawValue) {
        if ("true".equalsIgnoreCase(rawValue)) return true;
        if ("false".equalsIgnoreCase(rawValue)) return false;
        throw new IllegalArgumentException("boolean values must be true or false");
    }

    private static Float finiteFloat(String rawValue) {
        Float value = Float.valueOf(rawValue);
        if (!Float.isFinite(value)) throw new IllegalArgumentException("value must be finite");
        return value;
    }

    private static Double finiteDouble(String rawValue) {
        Double value = Double.valueOf(rawValue);
        if (!Double.isFinite(value)) throw new IllegalArgumentException("value must be finite");
        return value;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Enum<?> enumValue(Class<?> type, String rawValue) {
        return Enum.valueOf((Class<? extends Enum>) type, rawValue);
    }
}
