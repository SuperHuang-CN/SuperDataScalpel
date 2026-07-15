package cn.superhuang.data.scalpel.dialect.model;

/** Stable codes behind human-readable reasons returned by a dialect change plan. */
public enum TableChangeReasonCode {
    DATA_PRECHECK_REQUIRED,
    POSSIBLE_DATA_LOSS,
    DATA_CONVERSION_REQUIRED,
    KEY_CONSTRAINT_CHANGE,
    STORAGE_LAYOUT_CHANGE,
    DDL_TRANSACTION_LIMITATION,
    RUNTIME_CONFIGURATION_UNSUPPORTED,
    OPERATION_UNSUPPORTED,
    PHYSICAL_TYPE_UNSUPPORTED,
    EXTERNAL_DEPENDENCY
}
