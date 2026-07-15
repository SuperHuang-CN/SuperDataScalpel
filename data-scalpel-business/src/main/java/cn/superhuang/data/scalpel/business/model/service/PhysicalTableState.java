package cn.superhuang.data.scalpel.business.model.service;

/** Live result of comparing a model definition with its target physical table. */
public enum PhysicalTableState {
    NOT_FOUND,
    MATCHED,
    DRIFTED,
    UNREACHABLE,
    UNSUPPORTED
}
