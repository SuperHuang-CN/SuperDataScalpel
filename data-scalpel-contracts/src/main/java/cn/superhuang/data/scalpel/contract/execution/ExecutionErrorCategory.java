package cn.superhuang.data.scalpel.contract.execution;

/** Stable, user-safe category for an execution failure. */
public enum ExecutionErrorCategory {
    CONFIGURATION,
    CONNECTION,
    AUTHENTICATION,
    PERMISSION,
    SCHEMA,
    CONSTRAINT,
    TIMEOUT,
    CANCELLED,
    RESOURCE,
    EXTERNAL_SYSTEM,
    INTERNAL
}
