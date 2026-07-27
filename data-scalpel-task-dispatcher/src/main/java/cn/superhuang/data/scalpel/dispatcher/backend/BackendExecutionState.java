package cn.superhuang.data.scalpel.dispatcher.backend;

public enum BackendExecutionState {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    UNKNOWN
}
