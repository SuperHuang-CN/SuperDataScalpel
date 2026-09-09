package cn.superhuang.data.scalpel.contract.service;

/** Availability of the current runtime pool, independent of registration/synchronization state. */
public enum JdbcPoolMonitorStatus {
    AVAILABLE,
    NOT_LOADED,
    UNSUPPORTED
}
