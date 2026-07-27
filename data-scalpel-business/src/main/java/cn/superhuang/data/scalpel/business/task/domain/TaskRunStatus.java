package cn.superhuang.data.scalpel.business.task.domain;

public enum TaskRunStatus {
    QUEUED,
    RUNNING,
    CANCEL_REQUESTED,
    STOP_REQUESTED,
    STOPPED,
    SUCCESS,
    FAILED,
    TIMED_OUT,
    CANCELLED,
    SKIPPED
}
