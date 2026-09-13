package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

/**
 * Trusted execution-scoped values that a Canvas expression may reference.
 * Values are resolved only by the Task Engine, never supplied by the Canvas client.
 */
@JsonClassDescription("Task Engine 注入的执行级常量：EXECUTION_ID 是当前执行 UUID 的 STRING；EXECUTION_STARTED_AT 是一次执行入口捕获的 UTC TIMESTAMP。同一 Attempt 的所有表和行取值相同，流任务中不会随微批变化。")
public enum CanvasRuntimeValue {
    EXECUTION_ID,
    EXECUTION_STARTED_AT
}
