package cn.superhuang.datascalpel.taskengine.contract;



public enum TaskExecutionState {
    ACCEPTED,
    RUNNING,
    CANCEL_REQUESTED,
    SUCCESS,
    FAILED,
    TIMED_OUT,
    CANCELLED;

    public boolean terminal() {
        return this == SUCCESS || this == FAILED || this == TIMED_OUT || this == CANCELLED;
    }
}
