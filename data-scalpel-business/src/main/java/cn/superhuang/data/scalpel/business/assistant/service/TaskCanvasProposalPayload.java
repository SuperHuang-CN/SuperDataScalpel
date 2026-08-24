package cn.superhuang.data.scalpel.business.assistant.service;

import cn.superhuang.data.scalpel.contract.task.CanvasDefinition;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TaskCanvasProposalPayload(
        TaskCanvasPlan plan,
        CanvasDefinition definition,
        List<AssistantTaskCanvasQueryService.ResourceFingerprint> resources,
        ExistingTaskSnapshot existingTask,
        TaskCanvasPlan.NewTaskDraft newTaskDraft,
        String summary,
        List<String> assumptions,
        List<String> needsUserInput,
        int nodeCount,
        int edgeCount
) {
    public TaskCanvasProposalPayload {
        resources = resources == null ? List.of() : List.copyOf(resources);
        assumptions = assumptions == null ? List.of() : List.copyOf(assumptions);
        needsUserInput = needsUserInput == null ? List.of() : List.copyOf(needsUserInput);
    }

    public record ExistingTaskSnapshot(
            UUID taskId,
            String taskName,
            int definitionVersion,
            Instant definitionUpdatedAt,
            Instant taskUpdatedAt,
            boolean configured
    ) {}
}
