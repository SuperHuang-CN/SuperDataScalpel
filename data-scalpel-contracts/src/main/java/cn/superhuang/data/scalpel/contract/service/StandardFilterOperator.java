package cn.superhuang.data.scalpel.contract.service;

import java.util.Arrays;

/** Wire-compatible operators of the former standard table service. */
public enum StandardFilterOperator {
    EQ("="),
    NE("!="),
    GT(">"),
    GE(">="),
    LT("<"),
    LE("<="),
    IN("in"),
    NOT_IN("not in"),
    BETWEEN("between"),
    NOT_BETWEEN("not between"),
    LIKE("like"),
    NOT_LIKE("not like"),
    IS_NULL("is null"),
    IS_NOT_NULL("is not null"),
    IS_EMPTY("is empty"),
    IS_NOT_EMPTY("is not empty");

    private final String value;

    StandardFilterOperator(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static StandardFilterOperator fromValue(String value) {
        return Arrays.stream(values())
                .filter(operator -> operator.value.equalsIgnoreCase(value == null ? "" : value.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported filter operator: " + value));
    }
}
