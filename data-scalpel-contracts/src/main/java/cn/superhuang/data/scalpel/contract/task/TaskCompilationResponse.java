package cn.superhuang.data.scalpel.contract.task;

import java.util.List;
import java.util.UUID;

/** Stable Task Engine compilation response exposed through Admin. */
public record TaskCompilationResponse(
        UUID requestId,
        TaskType taskType,
        boolean valid,
        long durationMs,
        String sparkApplicationId,
        List<CompilationIssue> canvasIssues,
        List<NodeCompilationResult> nodeResults
) {
}
