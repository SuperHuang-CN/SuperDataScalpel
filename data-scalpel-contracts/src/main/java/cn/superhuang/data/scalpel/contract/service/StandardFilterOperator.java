package cn.superhuang.data.scalpel.contract.service;

/** Stable logical and comparison operators of the standard table service V1 protocol. */
public enum StandardFilterOperator {
    AND,
    OR,
    EQ,
    NE,
    GT,
    GTE,
    LT,
    LTE,
    IN,
    NOT_IN,
    BETWEEN,
    NOT_BETWEEN,
    LIKE,
    NOT_LIKE,
    IS_NULL,
    IS_NOT_NULL,
    IS_EMPTY,
    IS_NOT_EMPTY
}
