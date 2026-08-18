package cn.superhuang.data.scalpel.contract.quality;

public enum ModelQualityRuleType {
    NOT_NULL,
    UNIQUE,
    VALUE_RANGE,
    STRING_LENGTH,
    DICTIONARY_MEMBERSHIP,
    ROW_COUNT,
    FRESHNESS,
    GEOMETRY_VALID,
    GEOMETRY_NON_EMPTY,
    FORMAT_PATTERN,
    CONDITIONAL_NOT_NULL,
    FIELD_COMPARISON,
    REFERENCE_EXISTS
}
