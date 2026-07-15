package cn.superhuang.data.scalpel.business.model.domain;

/** Lifecycle of a model whose physical table must be structurally ready before publishing. */
public enum DataModelStatus {

    DRAFT,
    PUBLISHED,
    DISABLED
}
