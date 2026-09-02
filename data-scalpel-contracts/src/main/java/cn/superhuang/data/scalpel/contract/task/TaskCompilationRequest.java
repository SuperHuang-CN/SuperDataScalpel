package cn.superhuang.data.scalpel.contract.task;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;
import cn.superhuang.data.scalpel.contract.execution.CanvasTrialSpec;

/** Stable Admin-to-Task-Engine compilation request contract. */
public record TaskCompilationRequest(
        @NotNull UUID requestId,
        @NotNull @Valid TaskDefinition task,
        @NotNull MetadataSnapshot metadataSnapshot,
        CanvasTrialSpec canvasTrial
) {
    public TaskCompilationRequest(UUID requestId, TaskDefinition task, MetadataSnapshot metadataSnapshot) {
        this(requestId, task, metadataSnapshot, null);
    }
}
