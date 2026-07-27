package cn.superhuang.data.scalpel.business.task.execution.domain;

public enum TaskExecutionOutboxState {
    PENDING,
    PUBLISHING,
    PUBLISHED,
    FAILED
}
