package cn.superhuang.data.scalpel.contract.task;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record TaskDefinition(
        @NotNull TaskType type,
        @NotNull @Valid CanvasDefinition definition,
        CanvasExecutionMode executionMode
) {
    public TaskDefinition {
        executionMode = executionMode == null ? CanvasExecutionMode.BATCH : executionMode;
    }

    public TaskDefinition(TaskType type, CanvasDefinition definition) {
        this(type, definition, CanvasExecutionMode.BATCH);
    }
}
