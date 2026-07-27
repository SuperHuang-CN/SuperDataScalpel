package cn.superhuang.data.scalpel.contract.execution;

/** Stable execution phase used by result artifacts, events and task-run diagnostics. */
public enum ExecutionFailurePhase {
    PREPARE,
    READ,
    PROCESS,
    WRITE,
    DELIVERY,
    DISPATCH
}
