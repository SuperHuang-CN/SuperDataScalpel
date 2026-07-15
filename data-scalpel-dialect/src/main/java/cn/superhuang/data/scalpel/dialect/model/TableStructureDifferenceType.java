package cn.superhuang.data.scalpel.dialect.model;

public enum TableStructureDifferenceType {
    MISSING_COLUMN,
    EXTRA_COLUMN,
    TYPE_MISMATCH,
    LENGTH_MISMATCH,
    PRECISION_MISMATCH,
    NULLABILITY_MISMATCH,
    PRIMARY_KEY_MISMATCH,
    STORAGE_CONFIGURATION_MISMATCH
}
