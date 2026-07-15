package cn.superhuang.data.scalpel.business.model.domain;

/** Lifecycle of a persisted physical-table change plan. */
public enum DataModelPhysicalChangeStatus {
    PLANNED,
    APPLYING,
    SUCCEEDED,
    FAILED,
    PARTIAL,
    CANCELLED,
    SUPERSEDED
}
