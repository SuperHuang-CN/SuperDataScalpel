package cn.superhuang.data.scalpel.business.model.domain;

/** States whether a model creates a new table or explicitly binds an existing one. */
public enum PhysicalTableMode {
    MANAGED,
    EXTERNAL
}
