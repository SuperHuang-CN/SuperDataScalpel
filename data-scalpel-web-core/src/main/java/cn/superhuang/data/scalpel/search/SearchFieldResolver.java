package cn.superhuang.data.scalpel.search;

import jakarta.persistence.Transient;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

final class SearchFieldResolver {

    private SearchFieldResolver() {
    }

    static ResolvedField resolve(Class<?> entityType, String name) {
        if (name == null || !name.matches("[A-Za-z_$][A-Za-z0-9_$]*")) {
            throw invalid(name);
        }
        Field field = findField(entityType, name);
        if (field == null || isExcluded(field) || !isSupportedScalar(field.getType())) {
            throw invalid(name);
        }
        return new ResolvedField(name, normalizePrimitive(field.getType()));
    }

    static boolean isText(Class<?> type) {
        return normalizePrimitive(type) == String.class;
    }

    static boolean isComparable(Class<?> type) {
        Class<?> normalized = normalizePrimitive(type);
        return Number.class.isAssignableFrom(normalized)
                || normalized == BigDecimal.class
                || normalized == BigInteger.class
                || normalized == LocalDate.class
                || normalized == LocalDateTime.class
                || normalized == Instant.class
                || normalized == OffsetDateTime.class
                || normalized == ZonedDateTime.class
                || Date.class.isAssignableFrom(normalized);
    }

    static Class<?> normalizePrimitive(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == boolean.class) return Boolean.class;
        if (type == byte.class) return Byte.class;
        if (type == short.class) return Short.class;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == float.class) return Float.class;
        if (type == double.class) return Double.class;
        if (type == char.class) return Character.class;
        return type;
    }

    private static Field findField(Class<?> entityType, String name) {
        for (Class<?> type = entityType; type != null && type != Object.class; type = type.getSuperclass()) {
            try {
                return type.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                // Mapped superclasses are part of the supported entity surface.
            }
        }
        return null;
    }

    private static boolean isExcluded(Field field) {
        int modifiers = field.getModifiers();
        return Modifier.isStatic(modifiers)
                || Modifier.isTransient(modifiers)
                || field.isSynthetic()
                || field.isAnnotationPresent(Transient.class)
                || field.getType().isArray()
                || Iterable.class.isAssignableFrom(field.getType())
                || Map.class.isAssignableFrom(field.getType());
    }

    private static boolean isSupportedScalar(Class<?> type) {
        Class<?> normalized = normalizePrimitive(type);
        return normalized == String.class
                || normalized == Character.class
                || normalized == Boolean.class
                || Number.class.isAssignableFrom(normalized)
                || normalized == BigDecimal.class
                || normalized == BigInteger.class
                || normalized == UUID.class
                || normalized.isEnum()
                || normalized == LocalDate.class
                || normalized == LocalDateTime.class
                || normalized == Instant.class
                || normalized == OffsetDateTime.class
                || normalized == ZonedDateTime.class
                || Date.class.isAssignableFrom(normalized);
    }

    private static InvalidSearchRequestException invalid(String name) {
        return new InvalidSearchRequestException("search field is not supported: " + name);
    }

    record ResolvedField(String name, Class<?> type) {
    }
}
