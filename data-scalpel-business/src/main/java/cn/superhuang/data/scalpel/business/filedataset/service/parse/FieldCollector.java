package cn.superhuang.data.scalpel.business.filedataset.service.parse;

import cn.superhuang.data.scalpel.dialect.model.LogicalType;
import cn.superhuang.data.scalpel.contract.type.PlatformTypeDefinition;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Scans all rows while retaining a bounded ordered preview and merging field types conservatively. */
final class FieldCollector {

    private static final Pattern INTEGER = Pattern.compile("[+-]?(0|[1-9][0-9]*)");
    private static final Pattern DECIMAL = Pattern.compile("[+-]?(?:0|[1-9][0-9]*)\\.[0-9]+(?:[eE][+-]?[0-9]+)?");

    private final LinkedHashMap<String, FieldState> fieldStates = new LinkedHashMap<>();
    private final List<Map<String, Object>> rows = new ArrayList<>();
    private final int previewLimit;
    private long rowCount;

    FieldCollector() {
        this(Integer.MAX_VALUE);
    }

    FieldCollector(int previewLimit) {
        if (previewLimit < 0) {
            throw new IllegalArgumentException("预览记录数不能小于零");
        }
        this.previewLimit = previewLimit;
    }

    void ensureField(String name) {
        fieldStates.computeIfAbsent(name, ignored -> new FieldState());
    }

    void addRow(Map<String, Object> values, Map<String, LogicalType> valueTypes) {
        Map<String, Object> row = rows.size() < previewLimit ? new LinkedHashMap<>() : null;
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            String name = entry.getKey();
            FieldState state = fieldStates.computeIfAbsent(name, ignored -> new FieldState());
            state.presentCount++;
            Object value = entry.getValue();
            if (value == null) {
                state.nullable = true;
            } else {
                LogicalType candidate = valueTypes.get(name);
                state.logicalType = merge(state.logicalType, candidate);
                state.observeDecimal(value, candidate);
            }
            if (row != null) {
                row.put(name, value);
            }
        }
        if (row != null) {
            rows.add(row);
        }
        rowCount++;
    }

    List<FileDatasetParser.Field> fields() {
        List<FileDatasetParser.Field> fields = new ArrayList<>(fieldStates.size());
        int sortOrder = 0;
        for (Map.Entry<String, FieldState> entry : fieldStates.entrySet()) {
            FieldState state = entry.getValue();
            fields.add(new FileDatasetParser.Field(
                    entry.getKey(), sortOrder++, state.typeDefinition(),
                    state.nullable || state.presentCount < rowCount
            ));
        }
        return fields;
    }

    List<Map<String, Object>> rows() {
        return List.copyOf(rows);
    }

    long rowCount() {
        return rowCount;
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
        private int decimalPrecision;
        private int decimalScale;

        private void observeDecimal(Object value, LogicalType candidate) {
            if (candidate != LogicalType.DECIMAL && candidate != LogicalType.INTEGER) {
                return;
            }
            BigDecimal decimal;
            try {
                decimal = value instanceof BigDecimal bigDecimal
                        ? bigDecimal
                        : new BigDecimal(String.valueOf(value).trim());
            } catch (RuntimeException ignored) {
                return;
            }
            int scale = Math.max(0, decimal.scale());
            int precision = decimal.precision() + Math.max(0, -decimal.scale());
            decimalPrecision = Math.max(decimalPrecision, Math.max(1, precision));
            decimalScale = Math.max(decimalScale, scale);
        }

        private PlatformTypeDefinition typeDefinition() {
            LogicalType resolved = logicalType == null ? LogicalType.STRING : logicalType;
            if (resolved != LogicalType.DECIMAL) {
                return FileDatasetTypeDefinitions.fromLogicalType(resolved);
            }
            int scale = decimalScale;
            int precision = Math.max(decimalPrecision, scale + 1);
            return precision == 0
                    ? FileDatasetTypeDefinitions.fromLogicalType(LogicalType.DECIMAL)
                    : FileDatasetTypeDefinitions.decimal(precision, scale);
        }
    }
}
