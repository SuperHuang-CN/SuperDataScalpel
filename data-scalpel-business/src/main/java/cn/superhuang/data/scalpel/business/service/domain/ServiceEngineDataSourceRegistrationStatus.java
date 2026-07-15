package cn.superhuang.data.scalpel.business.service.domain;

/** Admin control-plane state of one data source synchronized to one Engine. */
public enum ServiceEngineDataSourceRegistrationStatus {
    PENDING,
    READY,
    OUTDATED,
    FAILED
}
