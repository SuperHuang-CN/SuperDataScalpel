package cn.superhuang.data.scalpel.dispatcher.domain;

public enum DispatcherExecutionState {
    QUEUED,
    SUBMITTING,
    SUBMITTED,
    RUNNING,
    CANCEL_REQUESTED,
    SUCCESS,
    FAILED,
    TIMED_OUT,
    CANCELLED,
    STOPPED,
    LOST;

    public boolean terminal() {
        return this == SUCCESS || this == FAILED || this == TIMED_OUT || this == CANCELLED
                || this == STOPPED || this == LOST;
    }

    public boolean inFlight() {
        return this == SUBMITTING || this == SUBMITTED || this == RUNNING || this == CANCEL_REQUESTED;
    }
}
