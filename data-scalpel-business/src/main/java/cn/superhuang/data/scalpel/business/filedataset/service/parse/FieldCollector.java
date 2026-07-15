package cn.superhuang.data.scalpel.business.filedataset.service.parse;

import cn.superhuang.data.scalpel.dialect.model.LogicalType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Collects a small ordered sample while merging field types conservatively. */
final class FieldCollector {

    private static final Pattern INTEGER = Pattern.compile("[+-]?(0|[1-9][0-9]*)");
    private static final Pattern DECIMAL = Pattern.compile("[+-]?(?:0|[1-9][0-9]*)\\.[0-9]+(?:[eE][+-]?[0-9]+)?");

    private final LinkedHashMap<String, FieldState> fieldStates = new LinkedHashMap<>();
    private final List<Map<String, Object>> rows = new ArrayList<>();

    void ensureField(String name) {
        fieldStates.computeIfAbsent(name, ignored -> new FieldState());
    }

    void addRow(Map<String, Object> values, Map<String, LogicalType> valueTypes) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            String name = entry.getKey();
            FieldState state = fieldStates.computeIfAbsent(name, ignored -> new FieldState());
            state.presentCount++;
            Object value = entry.getValue();
            if (value == null) {
                state.nullable = true;
            } else {
                state.logicalType = merge(state.logicalType, valueTypes.get(name));
            }
            row.put(name, value);
        }
        rows.add(row);
    }

    List<FileDatasetParser.Field> fields() {
        int rowCount = rows.size();
        List<FileDatasetParser.Field> fields = new ArrayList<>(fieldStates.size());
        int sortOrder = 0;
        for (Map.Entry<String, FieldState> entry : fieldStates.entrySet()) {
            FieldState state = entry.getValue();
            fields.add(new FileDatasetParser.Field(
                    entry.getKey(), sortOrder++, state.logicalType == null ? LogicalType.STRING : state.logicalType,
                    state.nullable || state.presentCount < rowCount
            ));
        }
        return fields;
    }

    List<Map<String, Object>> rows() {
        return List.copyOf(rows);
    }

    static LogicalType textType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if ("true".equalsIgnoreCase(trimmed) || "false".equalsIgnoreCase(trimmed)) {
            return LogicalType.BOOLEAN;
        }
        if (INTEGER.matcher(trimmed).matches()) {
            try {
                Long.parseLong(trimmed);
                return LogicalType.INTEGER;
            } catch (NumberFormatException ignored) {
                return LogicalType.DECIMAL;
            }
        }
        if (DECIMAL.matcher(trimmed).matches()) {
            try {
                new BigDecimal(trimmed);
                return LogicalType.DECIMAL;
            } catch (NumberFormatException ignored) {
                return LogicalType.STRING;
            }
        }
        if (isDate(trimmed)) {
            return LogicalType.DATE;
        }
        if (isDateTime(trimmed)) {
            return LogicalType.DATETIME;
        }
        return LogicalType.STRING;
    }

    static LogicalType objectType(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean) {
            return LogicalType.BOOLEAN;
        }
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
            return LogicalType.INTEGER;
        }
        if (value instanceof Number) {
            return LogicalType.DECIMAL;
        }
        if (value instanceof List<?>) {
            return LogicalType.ARRAY;
        }
        if (value instanceof Map<?, ?>) {
            return LogicalType.JSON;
        }
        return textType(String.valueOf(value));
    }

    private static LogicalType merge(LogicalType current, LogicalType candidate) {
        if (candidate == null) {
            return current;
        }
        if (current == null || current == candidate) {
            return candidate;
        }
        if ((current == LogicalType.INTEGER && candidate == LogicalType.DECIMAL)
                || (current == LogicalType.DECIMAL && candidate == LogicalType.INTEGER)) {
            return LogicalType.DECIMAL;
        }
        return LogicalType.STRING;
    }

    private static boolean isDate(String value) {
        if (!value.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return false;
        }
        try {
            LocalDate.parse(value);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static boolean isDateTime(String value) {
        if (!(value.contains("T") || value.contains(" "))) {
            return false;
        }
        try {
            OffsetDateTime.parse(value);
            return true;
        } catch (RuntimeException ignored) {
            try {
                LocalDateTime.parse(value.replace(' ', 'T'));
                return true;
            } catch (RuntimeException ignoredAgain) {
                return false;
            }
        }
    }

    private static final class FieldState {
        private LogicalType logicalType;
        private int presentCount;
        private boolean nullable;
    }
}
