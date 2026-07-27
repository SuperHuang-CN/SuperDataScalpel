package cn.superhuang.data.scalpel.contract.task;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Stable Admin-to-Task-Engine compilation request contract. */
public record TaskCompilationRequest(
        @NotNull UUID requestId,
        @NotNull @Valid TaskDefinition task,
        @NotNull MetadataSnapshot metadataSnapshot
) {
}
