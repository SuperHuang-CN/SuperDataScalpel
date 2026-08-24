package cn.superhuang.data.scalpel.contract.task;

/**
 * Trusted execution-scoped values that a Canvas expression may reference.
 * Values are resolved only by the Task Engine, never supplied by the Canvas client.
 */
public enum CanvasRuntimeValue {
    EXECUTION_ID,
    EXECUTION_STARTED_AT
}
