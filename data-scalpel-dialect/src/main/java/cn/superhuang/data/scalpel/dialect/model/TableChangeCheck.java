package cn.superhuang.data.scalpel.dialect.model;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Structured precondition for a controlled physical-table change. */
public record TableChangeCheck(
        TableChangeCheckType type,
        List<String> columnNames,
        Integer lengthLimit,
        Integer precisionLimit,
        Integer scaleLimit,
        TableStructureFingerprint expectedFingerprint,
        String description
) {
    public TableChangeCheck {
        if (type == null) {
            throw new IllegalArgumentException("Change check type is required");
        }
        columnNames = normalizeColumnNames(columnNames);
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Change check description is required");
        }
        description = description.trim();

        switch (type) {
            case STRUCTURE_FINGERPRINT_MATCH -> {
                require(expectedFingerprint != null, "Structure fingerprint is required");
                require(columnNames.isEmpty() && lengthLimit == null && precisionLimit == null && scaleLimit == null,
                        "Structure fingerprint check only accepts a fingerprint");
            }
            case TABLE_EMPTY, NO_EXTERNAL_DEPENDENCIES, NO_REBUILD_DEPENDENCIES, DATABASE_RUNTIME_SUPPORTED ->
                    require(columnNames.isEmpty() && lengthLimit == null && precisionLimit == null && scaleLimit == null
                                    && expectedFingerprint == null,
                            type + " check does not accept column or numeric parameters");
            case COLUMNS_HAVE_NO_NULLS, COLUMNS_ARE_UNIQUE -> {
                require(!columnNames.isEmpty(), type + " check requires column names");
                require(lengthLimit == null && precisionLimit == null && scaleLimit == null && expectedFingerprint == null,
                        type + " check only accepts column names");
            }
            case MAX_STRING_LENGTH -> {
                require(columnNames.size() == 1, "Maximum string length check requires exactly one column");
                require(lengthLimit != null && lengthLimit > 0, "Maximum string length must be positive");
                require(precisionLimit == null && scaleLimit == null && expectedFingerprint == null,
                        "Maximum string length check accepts only one column and length");
            }
            case DECIMAL_VALUES_FIT -> {
                require(columnNames.size() == 1, "Decimal fit check requires exactly one column");
                require(precisionLimit != null && precisionLimit > 0, "Decimal precision must be positive");
                require(scaleLimit != null && scaleLimit >= 0 && scaleLimit <= precisionLimit,
                        "Decimal scale must be between zero and precision");
                require(lengthLimit == null && expectedFingerprint == null,
                        "Decimal fit check accepts only one column, precision, and scale");
            }
        }
    }

    private static List<String> normalizeColumnNames(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        Set<String> normalized = new HashSet<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Check column name is required");
            }
            String normalizedName = value.trim().toLowerCase(Locale.ROOT);
            if (!normalized.add(normalizedName)) {
                throw new IllegalArgumentException("Duplicate check column name: " + value);
            }
        }
        return values.stream().map(String::trim).toList();
    }

    private static void require(boolean expression, String message) {
        if (!expression) {
            throw new IllegalArgumentException(message);
        }
    }
}
