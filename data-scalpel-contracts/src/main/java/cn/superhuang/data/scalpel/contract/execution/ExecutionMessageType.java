package cn.superhuang.data.scalpel.contract.execution;

public enum ExecutionMessageType {
    SUBMIT_EXECUTION,
    CANCEL_EXECUTION,
    START_STREAMING_EXECUTION,
    STOP_STREAMING_EXECUTION,
    RUNNER_STARTED,
    RUNNER_RESULT_AVAILABLE,
    RUNNER_FAILED,
    RUNNER_STREAMING_STARTED,
    RUNNER_STREAMING_PROGRESS,
    RUNNER_STREAMING_STOPPED,
    EXECUTION_ACCEPTED,
    EXECUTION_REJECTED,
    EXECUTION_SUBMITTED,
    EXECUTION_RUNNING,
    EXECUTION_SUCCEEDED,
    EXECUTION_FAILED,
    EXECUTION_TIMED_OUT,
    EXECUTION_CANCELLED,
    STREAMING_PROGRESS,
    EXECUTION_STOPPED,
    EXECUTION_LOST;

    public boolean isDispatcherEvent() {
        return switch (this) {
            case EXECUTION_ACCEPTED,
                 EXECUTION_REJECTED,
                 EXECUTION_SUBMITTED,
                 EXECUTION_RUNNING,
                 EXECUTION_SUCCEEDED,
                 EXECUTION_FAILED,
                 EXECUTION_TIMED_OUT,
                 EXECUTION_CANCELLED,
                 STREAMING_PROGRESS,
                 EXECUTION_STOPPED,
                 EXECUTION_LOST -> true;
            case SUBMIT_EXECUTION,
                 CANCEL_EXECUTION,
                 START_STREAMING_EXECUTION,
                 STOP_STREAMING_EXECUTION,
                 RUNNER_STARTED,
                 RUNNER_RESULT_AVAILABLE,
                 RUNNER_FAILED,
                 RUNNER_STREAMING_STARTED,
                 RUNNER_STREAMING_PROGRESS,
                 RUNNER_STREAMING_STOPPED -> false;
        };
    }
}
