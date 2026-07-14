package cn.superhuang.data.scalpel.business.model.domain;

/** Lifecycle of a metadata-only data model. Publishing does not operate a physical table in version one. */
public enum DataModelStatus {

    DRAFT,
    PUBLISHED,
    DISABLED
}
